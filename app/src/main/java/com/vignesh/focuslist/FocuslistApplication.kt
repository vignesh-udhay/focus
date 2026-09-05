package com.vignesh.focuslist

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.room.Room
import com.vignesh.focuslist.data.local.FocuslistDatabase
import com.vignesh.focuslist.data.local.FocuslistMigrations
import com.vignesh.focuslist.data.local.debugSeedCallback
import com.vignesh.focuslist.core.notification.AndroidFocusAlarms
import com.vignesh.focuslist.core.notification.AndroidReminderAlarms
import com.vignesh.focuslist.core.notification.FocusAlarms
import com.vignesh.focuslist.core.notification.ReminderAlarms
import com.vignesh.focuslist.core.notification.ReminderScheduler
import com.vignesh.focuslist.core.time.SystemCurrentDay
import com.vignesh.focuslist.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
class FocuslistApplication : Application() {

    val database: FocuslistDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            FocuslistDatabase::class.java,
            DATABASE_NAME
        )
            // No destructive fallback. A missing migration should fail loudly
            // rather than quietly empty someone's task list.
            .addMigrations(*FocuslistMigrations)
            // Fires only when the database is first created, which on a debug
            // build is every reinstall the instrumented tests cause. A release
            // install reaches the same line and is handed nothing.
            .addCallback(debugSeedCallback(isDebuggable))
            .build()
    }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    /**
     * Whether this install is a debug build.
     *
     * Read from the manifest flag rather than `BuildConfig`, which this module
     * does not generate, and which would mean turning on a build feature for
     * one boolean.
     */
    private val isDebuggable: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

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
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(reminderAlarms) }

    /**
     * Lives as long as the process, because what it watches does.
     *
     * Never cancelled: there is no later moment at which reminders stop
     * mattering while the process is alive.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        keepRemindersInStepWithStorage()
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
                reminderScheduler.reconcile(tasks)
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
        applicationScope.launch {
            reminderScheduler.reconcile(taskRepository.observeTasks().first())
        }
    }

    companion object {
        const val DATABASE_NAME = "focuslist.db"
    }
}
