package app.tellcat.core.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import java.time.ZoneId
import app.tellcat.data.local.LockedDelivery
import app.tellcat.core.domain.reminderTrigger
import app.tellcat.core.domain.isDueAt
import app.tellcat.core.domain.Task
import androidx.core.os.UserManagerCompat
import app.tellcat.TellcatApplication
import app.tellcat.MainActivity
import app.tellcat.R
import app.tellcat.core.domain.DeliveryOutcome
import app.tellcat.core.domain.ReminderDelivery
import app.tellcat.core.domain.hasLiveReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The real reminder alarm, from the device.
 *
 * `setExactAndAllowWhileIdle`, not the inexact call `AndroidFocusAlarms` uses,
 * except on OnePlus. D-009 measured OxygenOS removing exactness from that call
 * despite granting the permission, and D-042 records the device evidence that
 * made the exception necessary. There, `setAlarmClock` is the only public API
 * whose delivery time Android promises never to adjust.
 *
 * If the system refuses exact alarms, this falls back to the inexact call
 * rather than dropping the reminder. Late is a poor outcome; silence is the
 * one `PRODUCT.md` calls the most severe bug in the product.
 */
class AndroidReminderAlarms(private val context: Context) : ReminderAlarms {

    private val alarms = context.getSystemService<AlarmManager>()

    override fun schedule(taskId: String, at: Instant) {
        val alarms = alarms ?: run {
            Log.e(LogTag, "No AlarmManager. Reminder for $taskId cannot be scheduled at all.")
            return
        }
        val intent = pendingIntent(taskId, at)

        if (canScheduleExact()) {
            if (usesAlarmClockDelivery(Build.MANUFACTURER)) {
                alarms.setAlarmClock(reminderAlarmClockInfo(context, at), intent)
            } else {
                alarms.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at.toEpochMilli(),
                    intent
                )
            }
        } else {
            // `AGENTS.md`: never silently swallow a scheduling failure. This is
            // a degraded promise, not a working one, and the Phase 2 health
            // screen is where it eventually has to reach the user.
            Log.w(LogTag, "Exact alarms unavailable. Reminder for $taskId scheduled inexactly.")
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    override fun cancel(taskId: String) {
        // The extras differ from whatever is outstanding, and matching ignores
        // extras, so this cancels the alarm this task actually has.
        alarms?.cancel(pendingIntent(taskId, Instant.EPOCH))
    }

    override fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarms?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

    /**
     * One [PendingIntent] per task, told apart by data rather than by request
     * code alone.
     *
     * `PendingIntent` matching ignores extras and compares the intent by
     * action, data, type, class and categories. Two task ids that happened to
     * share a hash would collide on request code alone, and one reminder would
     * silently replace another. A per-task `data` URI makes them distinct
     * whatever the hash does.
     *
     * The intent also carries the moment it was aimed at, on both clocks, so
     * the receiver can say how late it was. `AGENTS.md` requires both: a phone
     * corrects its own clock from carrier time and from NTP as a matter of
     * routine, and a correction between here and delivery lands entirely on
     * the wall clock, where it is indistinguishable from a late alarm.
     *
     * Extras survive because `FLAG_UPDATE_CURRENT` rewrites them on every
     * reschedule, and the reconciliation reschedules everything owed on every
     * task write, on boot, and on a clock change. Matching ignores extras, so
     * they cannot make two alarms collide or fail to.
     */
    private fun pendingIntent(taskId: String, at: Instant): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("tellcat://reminder/$taskId")
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_SCHEDULED_WALL, at.toEpochMilli())

            // How far away the target is from the moment it is being placed.
            // Read once and used twice: to reach the same instant on the clock
            // that cannot be corrected, and to say later whether this delivery
            // was ever exposed to the idle time the health rules care about.
            val ahead = at.toEpochMilli() - System.currentTimeMillis()

            putExtra(
                ReminderReceiver.EXTRA_SCHEDULED_ELAPSED,
                SystemClock.elapsedRealtime() + ahead
            )
            putExtra(ReminderReceiver.EXTRA_SCHEDULED_AHEAD, ahead.coerceAtLeast(0))
        }

        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Whether reminders need Android's alarm-clock delivery path on this vendor.
 *
 * Kept narrower than the OxygenOS/ColorOS family: only OnePlus has been
 * measured demoting `setExactAndAllowWhileIdle`, and the alarm-clock path has a
 * user-visible system cost. A manufacturer guess is enough to show a health
 * caution; changing how the whole device presents its next alarm needs direct
 * evidence.
 */
internal fun usesAlarmClockDelivery(manufacturer: String): Boolean =
    manufacturer.trim().equals("OnePlus", ignoreCase = true)

/**
 * What Android opens from its system-owned "next alarm" affordance.
 *
 * Shared by real and test reminders so D-042's device path cannot diverge.
 * It cannot honestly pick one task when several reminders exist, so it opens
 * the app rather than guessing. The reminder notification still opens its
 * task.
 */
internal fun reminderAlarmClockInfo(
    context: Context,
    at: Instant
): AlarmManager.AlarmClockInfo {
    val show = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return AlarmManager.AlarmClockInfo(at.toEpochMilli(), show)
}

/**
 * Posts the reminder when its alarm arrives.
 *
 * Reads the task back out of storage first, and says nothing unless it still
 * has a live reminder. That is not belt and braces, it is the same rule the
 * scheduler runs on: storage decides. An alarm is only a cache, and a cache
 * can be stale in ways cancellation does not reach. A soft-deleted task
 * disappears from the DAO's query, so the reconciliation pass never sees it to
 * cancel its alarm; this check is what stops it ringing at 6am.
 *
 * `goAsync` because the read touches disk. A receiver has roughly ten seconds,
 * which is ample for one query, and the alternative is guessing from extras
 * written whenever the alarm was set.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val application = context.applicationContext as? TellcatApplication ?: return

        // Read first, before anything slow. Both clocks are the measurement,
        // and a reading taken after a disk query would be measuring the query.
        val arrivedWallAt = Instant.now()
        val arrivedElapsedAt = SystemClock.elapsedRealtime()
        val scheduledWallAt = Instant.ofEpochMilli(
            intent.getLongExtra(EXTRA_SCHEDULED_WALL, arrivedWallAt.toEpochMilli())
        )
        val scheduledElapsedAt = intent.getLongExtra(EXTRA_SCHEDULED_ELAPSED, arrivedElapsedAt)
        // Zero by default, which reads as a delivery that tested nothing. The
        // conservative answer for an alarm placed before this was recorded.
        val scheduledAhead = Duration.ofMillis(intent.getLongExtra(EXTRA_SCHEDULED_AHEAD, 0L))

        val finish = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Before the first unlock after a restart the database cannot be
                // opened, so the reminder is told from the copy kept for this.
                // D-079.
                if (!UserManagerCompat.isUserUnlocked(context)) {
                    context.deliverWhileLocked(
                        taskId = taskId,
                        scheduledWallAt = scheduledWallAt,
                        scheduledElapsedAt = scheduledElapsedAt,
                        arrivedWallAt = arrivedWallAt,
                        arrivedElapsedAt = arrivedElapsedAt,
                        scheduledAhead = scheduledAhead
                    )
                    return@launch
                }

                val task = application.taskRepository.observeTasks().first()
                    .firstOrNull { it.id == taskId }

                // Completed, deleted, or its reminder cleared since the alarm
                // was set. All three mean the user is owed nothing, and none
                // of them is a delivery, so none is written down.
                if (task == null || !task.hasLiveReminder()) return@launch

                // The moment it was owed at, which is a snooze's when there is
                // one, so lateness is measured against what was promised. D-078.
                val dueAt = task.ringsAt ?: return@launch

                // **A stale alarm says nothing.** D-079. The reminder moved later
                // and this alarm, aimed at its old time, survived. Announcing it
                // would mark the reminder delivered and leave the real time
                // silent, so the alarms are reconciled instead and the right one
                // is set.
                if (!isDueAt(dueAt, ZoneId.systemDefault(), arrivedWallAt)) {
                    application.reconcileReminders()
                    return@launch
                }

                suspend fun record(outcome: DeliveryOutcome) {
                    application.reminderDeliveryRepository.record(
                        ReminderDelivery(
                            id = UUID.randomUUID().toString(),
                            taskId = taskId,
                            // As it reads now, which is when it fired. The
                            // task may be renamed or deleted afterwards and
                            // the record still has to name what was late.
                            taskTitle = task.title,
                            dueAt = dueAt,
                            scheduledWallAt = scheduledWallAt,
                            scheduledElapsedAt = scheduledElapsedAt,
                            arrivedWallAt = arrivedWallAt,
                            arrivedElapsedAt = arrivedElapsedAt,
                            scheduledAhead = scheduledAhead,
                            outcome = outcome
                        )
                    )
                }

                if (!context.canPostReminders()) {
                    // Deliberately left undelivered. Saying nothing is not
                    // delivering, and if the user grants notifications later
                    // the reminder is still owed and will be announced then.
                    //
                    // Written down all the same. This is the failure the user
                    // cannot see, so it is the one most worth recording.
                    Log.w(LogTag, "Cannot post. Reminder for $taskId fired and said nothing.")
                    record(DeliveryOutcome.Suppressed)
                    return@launch
                }

                context.ensureReminderChannel()
                if (!context.postReminder(task)) {
                    Log.w(LogTag, "Cannot post. Reminder permission changed for $taskId.")
                    record(DeliveryOutcome.Suppressed)
                    return@launch
                }

                record(DeliveryOutcome.Announced)

                // Only after it was actually said. This is what stops the
                // reminder being announced again on every later pass.
                application.taskRepository.markReminderDelivered(taskId, arrivedWallAt)
            } finally {
                finish.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "tellcat.extra.taskId"

        /** The moment this alarm was aimed at, epoch milliseconds. */
        const val EXTRA_SCHEDULED_WALL = "tellcat.extra.scheduledWall"

        /** The same moment on `SystemClock.elapsedRealtime()`. */
        const val EXTRA_SCHEDULED_ELAPSED = "tellcat.extra.scheduledElapsed"

        /** How far ahead the alarm was set, in milliseconds. */
        const val EXTRA_SCHEDULED_AHEAD = "tellcat.extra.scheduledAhead"
    }
}

/**
 * Rebuilds every outstanding alarm from storage.
 *
 * The receiver that makes the promise keepable. Everything `AlarmManager` was
 * holding is gone after a restart, and a clock or timezone change moves what
 * the remaining alarms mean, so all three events need the same answer:
 * reconcile from storage, which is the only thing that survived.
 *
 * `MY_PACKAGE_REPLACED` is here for the same reason, and it is the one that
 * shows up in development: updating the app cancels its alarms.
 */
class ReminderRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Exported, so anything could send it an explicit intent. Only the
        // broadcasts the manifest names lead anywhere.
        if (intent.action !in RecoveryActions) return

        val application = context.applicationContext as? TellcatApplication ?: return
        val finish = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (UserManagerCompat.isUserUnlocked(context)) {
                    application.reconcileReminders()
                } else {
                    // Before the first unlock: set the alarms from the copy, which
                    // is all that can be read. The unlock reconciles properly.
                    // D-079.
                    armFromLockedCopy(application)
                }
            } catch (error: Exception) {
                // Logged rather than thrown, which would kill the process on the
                // very boot that is meant to restore the alarms. D-078.
                Log.e(LogTag, "Recovery could not reconcile reminders.", error)
            } finally {
                finish.finish()
            }
        }
    }

    private companion object {
        val RecoveryActions = setOf(
            // Sent before the first unlock, which is the whole point. D-079.
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // A grant made anywhere, including system settings, turns alarms
            // set inexactly while it was missing into exact ones. D-078. The
            // literal, because the constant is API 31 and this runs from 29.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}

/**
 * Sets an alarm for every reminder in the copy kept for the locked phone.
 *
 * The same alarms, by the same identity, that the reconciliation sets, so the
 * first pass after the unlock replaces each one rather than adding a second.
 */
private fun armFromLockedCopy(application: TellcatApplication) {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()

    application.lockedReminders.upcoming().forEach { reminder ->
        try {
            application.reminderAlarms.schedule(
                reminder.taskId,
                reminderTrigger(reminder.ringsAt, zone, now)
            )
        } catch (error: Exception) {
            Log.e(LogTag, "Could not set the locked alarm for ${reminder.taskId}.", error)
        }
    }
}

/**
 * Tells a reminder while the phone is still locked after a restart. D-079.
 *
 * Everything comes from the copy, and what happened goes back into it: the
 * database cannot be written until the unlock, and the application applies the
 * record then, before anything reconciles. Done and Snooze ask for the unlock
 * first, because what they do needs the database.
 */
private fun Context.deliverWhileLocked(
    taskId: String,
    scheduledWallAt: Instant,
    scheduledElapsedAt: Long,
    arrivedWallAt: Instant,
    arrivedElapsedAt: Long,
    scheduledAhead: Duration
) {
    val store = (applicationContext as TellcatApplication).lockedReminders
    val reminder = store.upcoming().firstOrNull { it.taskId == taskId } ?: return

    if (!isDueAt(reminder.ringsAt, ZoneId.systemDefault(), arrivedWallAt)) return

    // Left owed rather than recorded. After the unlock it is announced as a
    // reminder whose moment passed, which is late, but it is not lost.
    if (!canPostReminders()) {
        Log.w(LogTag, "Cannot post. Locked reminder for $taskId fired and said nothing.")
        return
    }

    ensureReminderChannel()
    val task = Task(
        id = reminder.taskId,
        title = reminder.title,
        createdAt = Instant.EPOCH,
        reminderAt = reminder.ringsAt
    )
    if (!postReminder(task, requireUnlockForActions = true)) return

    store.recordLockedDelivery(
        LockedDelivery(
            taskId = reminder.taskId,
            title = reminder.title,
            ringsAt = reminder.ringsAt,
            scheduledWallAt = scheduledWallAt,
            scheduledElapsedAt = scheduledElapsedAt,
            arrivedWallAt = arrivedWallAt,
            arrivedElapsedAt = arrivedElapsedAt,
            scheduledAhead = scheduledAhead
        )
    )
}

/**
 * Whether a reminder posted now would actually be shown.
 *
 * More than the runtime permission. D-078: notifications switched off for the
 * whole app, which is the only switch below API 33, or the reminder channel set
 * to None, both let `notify` succeed and show nothing. Counting either as a
 * delivery marked the reminder told, so it was never offered again, and the
 * health check called it fine. A channel not yet created counts as allowed,
 * because it is about to be created at high importance.
 */
internal fun Context.canPostReminders(): Boolean {
    if (!canPostNotifications()) return false
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false

    val channel = getSystemService<NotificationManager>()?.getNotificationChannel(ReminderChannelId)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

/**
 * The reminder channel, separate from the focus one.
 *
 * `IMPORTANCE_HIGH` and `CATEGORY_REMINDER`, because this is the app doing the
 * one thing it exists for, at a time the user chose. The focus channel is
 * `IMPORTANCE_DEFAULT` and argues for calm; the argument does not transfer. A
 * focus estimate passing is information. A reminder is the product.
 *
 * Separate so the two can be turned down independently. Someone who finds the
 * focus estimate noisy can silence it in system settings without also
 * silencing the reminders, and a single channel would make that one switch.
 */
internal fun Context.ensureReminderChannel() {
    val manager = getSystemService<NotificationManager>() ?: return

    val channel = NotificationChannel(
        ReminderChannelId,
        getString(R.string.reminder_channel_name),
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = getString(R.string.reminder_channel_description)
    }

    manager.createNotificationChannel(channel)
}

internal const val ReminderChannelId = "tellcat.reminder"

internal const val LogTag = "TellcatReminder"
