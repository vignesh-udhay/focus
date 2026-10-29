package app.tellcat

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.UserManagerCompat
import app.tellcat.core.domain.DeliveryOutcome
import app.tellcat.core.domain.ReminderAnnouncement
import app.tellcat.core.domain.ReminderDelivery
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.hasLiveReminder
import app.tellcat.core.domain.pendingFollowUp
import app.tellcat.data.local.LockedReminderStore
import app.tellcat.data.local.SchedulingRefusals
import app.tellcat.ui.widget.armWidgetMidnight
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.Room
import app.tellcat.data.local.TellcatDatabase
import app.tellcat.data.local.TellcatMigrations
import app.tellcat.data.local.TellcatPreferences
import app.tellcat.data.local.FocusSessionPreferences
import app.tellcat.data.local.OnboardingPreferences
import app.tellcat.data.local.ReminderHealthAcknowledgements
import app.tellcat.data.local.WidgetInteractionPreferences
import app.tellcat.core.notification.AndroidFocusAlarms
import app.tellcat.core.notification.AndroidReminderAlarms
import app.tellcat.core.notification.LogTag
import app.tellcat.core.notification.FocusAlarms
import app.tellcat.core.notification.ReminderAlarms
import app.tellcat.core.notification.AndroidReminderHealthChecks
import app.tellcat.core.notification.ReminderHealthChecks
import app.tellcat.core.notification.ReminderScheduler
import app.tellcat.core.notification.AlarmRingService
import app.tellcat.core.notification.ensureAlarmChannel
import app.tellcat.core.notification.ensureFollowUpChannel
import app.tellcat.core.notification.ensureReminderChannel
import app.tellcat.core.notification.withdrawSettledReminders
import app.tellcat.core.time.SystemCurrentDay
import app.tellcat.data.repository.ReminderDeliveryRepository
import app.tellcat.data.repository.BackupRepository
import app.tellcat.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import app.tellcat.ui.widget.TellcatWidget

/**
 * The application-level composition root.
 *
 * It owns one database and one repository for the life of the process, which
 * is what keeps a single connection pool alive across configuration changes.
 * Both are created lazily, so nothing touches disk until something asks for a
 * task.
 *
 * The current day is owned here too, for the same reason: one answer for the
 * whole process, alive across configuration changes, and updating while the
 * app runs rather than being fixed when a screen was built.
 *
 * Deliberately not a service locator: it holds these few objects and nothing
 * else.
 */
class TellcatApplication : Application() {

    val database: TellcatDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            TellcatDatabase::class.java,
            DATABASE_NAME
        )
            // No destructive fallback. A missing migration should fail loudly
            // rather than quietly empty someone's task list.
            .addMigrations(*TellcatMigrations)
            .build()
    }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    /** The two persisted appearance choices, observed directly by the theme. */
    val preferences: TellcatPreferences by lazy { TellcatPreferences(this) }

    /** Whether this install has passed the one first-run introduction. */
    val onboarding: OnboardingPreferences by lazy { OnboardingPreferences(this) }

    /** The one resumable Focus clock, shared with the home widget. */
    val focusSessionStore: FocusSessionPreferences by lazy { FocusSessionPreferences(this) }

    /** Completion evidence that remains on the widget until its next refresh. */
    val widgetInteractions: WidgetInteractionPreferences by lazy {
        WidgetInteractionPreferences(this)
    }

    /** The one missed-delivery notice the user has acknowledged on Today. */
    val reminderHealthAcknowledgements: ReminderHealthAcknowledgements by lazy {
        ReminderHealthAcknowledgements(this)
    }

    /** User-controlled JSON continuity, kept separate from Android cloud backup. */
    val backupRepository: BackupRepository by lazy {
        BackupRepository(database.backupDao(), preferences)
    }

    /**
     * What the app actually delivered, as opposed to what it intended to.
     *
     * Written by `ReminderReceiver` at the moment a reminder fires, and read
     * by the reminder health work. Separate from [taskRepository] because a
     * delivery outlives its task and answers a different question: not what
     * the user has to do, but whether this device can be relied on to say so.
     */
    val reminderDeliveryRepository: ReminderDeliveryRepository by lazy {
        ReminderDeliveryRepository(database.reminderDeliveryDao())
    }

    /** Eager, so the date broadcasts are being listened for from the start. */
    val currentDay: SystemCurrentDay by lazy { SystemCurrentDay(this) }

    /**
     * How a focus session reaches the user once they have left the app.
     *
     * Owned here for the same reason as the day: it is process-scoped, holds no
     * screen state, and one owner means one outstanding alarm.
     */
    val focusAlarms: FocusAlarms by lazy { AndroidFocusAlarms(this) }

    /** How a task reminder reaches the user. Exact, unlike [focusAlarms]. */
    val reminderAlarms: ReminderAlarms by lazy { AndroidReminderAlarms(this) }

    /**
     * Keeps the system's alarms agreeing with what storage says is owed.
     *
     * Public because the recovery receiver runs it too, after a restart or a
     * clock change, when this process may have only just been created.
     */
    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(
            alarms = reminderAlarms,
            // Written down rather than swallowed, per `AGENTS.md`. D-078.
            onFailure = { taskId, error ->
                Log.e(LogTag, "The system refused the alarm for $taskId.", error)
            }
        )
    }

    /**
     * Reconciles every alarm against storage, now, and returns when it is done.
     *
     * For code running in a broadcast receiver or a widget action, which writes
     * a task and then lets its process go. The collector in
     * [keepRemindersInStepWithStorage] would reconcile too, but only if the
     * process lives long enough to run it, and a frozen one does not: a snooze
     * or the next occurrence of a task ticked from the shade could be stored
     * and never scheduled. D-078.
     */
    suspend fun reconcileReminders(tasks: List<Task>? = null) {
        reconcileLock.withLock {
            // What was announced before the first unlock goes into the database
            // first, or this pass would find it owed and announce it again.
            val settled = settleLockedDeliveries()
            val current = if (settled || tasks == null) taskRepository.observeTasks().first() else tasks

            // Deleted tasks are not in `current`, and an alarm one still holds
            // is only reached by asking for them. D-099.
            val result = reminderScheduler.reconcile(current, deleted = taskRepository.deletedTasksWithReminders())
            schedulingRefusals.record(result.failures)
            lockedReminders.replaceUpcoming(result.scheduled)

            // The shade follows storage as the alarms do: a reminder for a task
            // deleted, completed or gone is no longer something to act on.
            withdrawSettledReminders(current)

            // And so does a ringing alarm: completed, deleted, snoozed from the
            // notification or moved, it stops. R-002, D-102.
            AlarmRingService.silenceIfSettled(current)
        }
    }

    /**
     * Writes down the reminders announced while the phone was locked, now that
     * the database can be opened. Returns whether there were any.
     *
     * Only where the task still rings at the moment that was announced. One
     * edited since, or completed, or deleted, is owed whatever its new state
     * says, and marking it delivered would silence it.
     */
    private suspend fun settleLockedDeliveries(): Boolean {
        val deliveries = lockedReminders.lockedDeliveries()
        if (deliveries.isEmpty()) return false

        deliveries.forEach { delivery ->
            val task = taskRepository.findTask(delivery.taskId) ?: return@forEach

            // A follow-up posted while locked: it is done, so forget it rather
            // than post it again now. D-108.
            if (delivery.followUp) {
                if (task.pendingFollowUp == delivery.ringsAt) taskRepository.clearFollowUp(task.id)
                return@forEach
            }

            if (!task.hasLiveReminder() || task.ringsAt != delivery.ringsAt) return@forEach

            // Starts a repeating task's next occurrence too, counted from when
            // it was said rather than from now. D-098.
            ReminderAnnouncement(taskRepository).record(
                delivery.taskId,
                delivery.arrivedWallAt,
                remindsAgain = delivery.remindsAgain
            )
            reminderDeliveryRepository.record(
                ReminderDelivery(
                    id = UUID.randomUUID().toString(),
                    taskId = delivery.taskId,
                    taskTitle = delivery.title,
                    dueAt = delivery.ringsAt,
                    scheduledWallAt = delivery.scheduledWallAt,
                    scheduledElapsedAt = delivery.scheduledElapsedAt,
                    arrivedWallAt = delivery.arrivedWallAt,
                    arrivedElapsedAt = delivery.arrivedElapsedAt,
                    scheduledAhead = delivery.scheduledAhead,
                    outcome = DeliveryOutcome.Announced
                )
            )
        }

        lockedReminders.clearLockedDeliveries()
        return true
    }

    /**
     * The reminders that have alarms, readable before the first unlock after a
     * restart. D-079. Written by every reconciliation.
     */
    val lockedReminders: LockedReminderStore by lazy { LockedReminderStore(this) }

    /** Whether the latest reconciliation had an alarm refused. D-079. */
    val schedulingRefusals: SchedulingRefusals by lazy { SchedulingRefusals(this) }

    /** What the platform will admit about whether a reminder can arrive. */
    val reminderHealthChecks: ReminderHealthChecks by lazy {
        AndroidReminderHealthChecks(
            context = this,
            alarms = reminderAlarms,
            refusedRecently = { schedulingRefusals.refusedRecently }
        )
    }

    /**
     * Lives as long as the process, because what it watches does.
     *
     * Never cancelled: there is no later moment at which reminders stop
     * mattering while the process is alive.
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            // A failure in one watcher is logged, not fatal. Without this an
            // exception escaping a reconciliation killed the process on every
            // start, and with it every alarm the next start would have set.
            // D-078.
            CoroutineExceptionHandler { _, error ->
                Log.e(LogTag, "A background task failed.", error)
            }
    )

    /**
     * One reconciliation at a time, so applying what was announced while locked
     * always finishes before a pass that would otherwise announce it again.
     */
    private val reconcileLock = Mutex()

    private val credentialWorkStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()

        // The vibrating reminder channel, and the move into it from 1.0's, at
        // the first start after the update rather than at the first reminder,
        // so Reminder health reads the channel reminders will use. D-100.
        // Channels are not credential storage, so this is safe before unlock.
        ensureReminderChannel()
        // The Alarms channel too, so Reminder health can read it. D-102.
        ensureAlarmChannel()
        // And the follow-up's, so it is in Android's list to turn off before
        // the first one is needed. D-108.
        ensureFollowUpChannel()

        // **Nothing in credential-encrypted storage until the user unlocks.**
        // D-079. The reminder receivers are direct-boot aware, so this process
        // can start before the first unlock after a restart, when the database
        // and every preference file cannot be opened. The watchers used to start
        // regardless, fail, and stay dead for the life of the process, so the
        // first edits after unlocking set no alarms.
        if (UserManagerCompat.isUserUnlocked(this)) {
            startCredentialWork()
        } else {
            startCredentialWorkAtUnlock()
        }
    }

    private fun startCredentialWorkAtUnlock() {
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    unregisterReceiver(this)
                    startCredentialWork()
                }
            },
            IntentFilter(Intent.ACTION_USER_UNLOCKED),
            // A system broadcast, which reaches an unexported receiver.
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** Starts everything that reads storage only an unlocked phone can open. Once. */
    private fun startCredentialWork() {
        if (!credentialWorkStarted.compareAndSet(false, true)) return
        keepRemindersInStepWithStorage()
        keepWidgetInStepWithState()
        armWidgetMidnight(this)
    }

    /**
     * Every write to a task re-runs the reconciliation, so setting, changing,
     * completing or deleting a reminder all reach `AlarmManager` without any
     * of those call sites knowing that alarms exist.
     *
     * This does mean the database opens at process start rather than when a
     * screen first asks for a task, which the laziness above was written to
     * avoid. That trade is deliberate. A reminder the app forgot to schedule
     * because nothing happened to open the database is exactly the failure
     * `docs/decisions.md` D-005 is about, and it would be invisible.
     *
     * `collectLatest`, so a burst of edits does not queue a reconciliation per
     * emission. Each pass reads the whole list, so only the newest matters.
     */
    private fun keepRemindersInStepWithStorage() {
        applicationScope.launch {
            taskRepository.observeTasks().collectLatest { tasks ->
                reconcileReminders(tasks)
            }
        }
    }

    /**
     * Keeps task, Focus and dynamic colour changes serialized behind one Glance
     * update path. The widget has no live session while Settings is open, so
     * without this the switch would only reach it at the next task write.
     */
    private fun keepWidgetInStepWithState() {
        applicationScope.launch {
            combine(
                taskRepository.observeTasks(),
                // SharedFlow has no initial value. Supplying one lets task
                // writes drive the combined stream before Focus changes.
                focusSessionStore.changes.onStart { emit(Unit) },
                preferences.state.map { it.dynamicColor }.distinctUntilChanged()
            ) { tasks, _, _ -> tasks }
                // Starting a Glance SessionWorker creates the Application too.
                // Re-enqueueing from Room's initial snapshot would cancel that
                // very session and can leave the old RemoteViews on screen.
                // Provider and clock broadcasts already request their own
                // refresh; this observer is responsible only for later writes.
                .drop(1)
                .conflate()
                .collect { tasks ->
                    // Reading it retires completion evidence when this emission
                    // is the first data change after the widget interaction.
                    widgetInteractions.completionFor(tasks, currentDay.today.value)
                    TellcatWidget().updateAll(this@TellcatApplication)
                }
        }
    }

    /**
     * Re-runs the reconciliation now, without anything having been written.
     *
     * Everything else that changes what is owed also writes to storage, and
     * the stream above carries it. Being granted permission to post does not:
     * no task changed, only whether the app is allowed to speak about them.
     *
     * A reminder whose alarm fired while the app could not post is still owed.
     * `ReminderReceiver` deliberately leaves it undelivered so that stays
     * true, but nothing was going to act on it until the next process start.
     * This is what makes the receiver's promise good at the moment the user
     * says yes, rather than the next time they happen to open the app.
     */
    fun refreshReminders() {
        applicationScope.launch { reconcileReminders() }
    }

    companion object {
        const val DATABASE_NAME = "tellcat.db"
    }
}
