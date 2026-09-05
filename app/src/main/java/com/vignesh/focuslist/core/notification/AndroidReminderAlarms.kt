package com.vignesh.focuslist.core.notification

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
import com.vignesh.focuslist.FocuslistApplication
import com.vignesh.focuslist.MainActivity
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.DeliveryOutcome
import com.vignesh.focuslist.core.domain.ReminderDelivery
import com.vignesh.focuslist.core.domain.hasLiveReminder
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
 * `setExactAndAllowWhileIdle`, not the inexact call `AndroidFocusAlarms` uses.
 * Exactness is what the user was promised when they picked a time, and it is
 * free here: the exact-alarm spike found `USE_EXACT_ALARM` auto-granted on
 * Android 14 with no prompt, because reminders are this app's core function.
 * `AndAllowWhileIdle` so Doze cannot hold a reminder until the user next picks
 * up the phone, which is exactly when they no longer need telling.
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
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
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
            data = Uri.parse("focuslist://reminder/$taskId")
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
        val application = context.applicationContext as? FocuslistApplication ?: return

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
                val task = application.taskRepository.observeTasks().first()
                    .firstOrNull { it.id == taskId }

                // Completed, deleted, or its reminder cleared since the alarm
                // was set. All three mean the user is owed nothing, and none
                // of them is a delivery, so none is written down.
                if (task == null || !task.hasLiveReminder()) return@launch

                val dueAt = task.reminderAt ?: return@launch

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

                if (!context.canPostNotifications()) {
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
                context.postReminder(task)
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
        const val EXTRA_TASK_ID = "focuslist.extra.taskId"

        /** The moment this alarm was aimed at, epoch milliseconds. */
        const val EXTRA_SCHEDULED_WALL = "focuslist.extra.scheduledWall"

        /** The same moment on `SystemClock.elapsedRealtime()`. */
        const val EXTRA_SCHEDULED_ELAPSED = "focuslist.extra.scheduledElapsed"

        /** How far ahead the alarm was set, in milliseconds. */
        const val EXTRA_SCHEDULED_AHEAD = "focuslist.extra.scheduledAhead"
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
        val application = context.applicationContext as? FocuslistApplication ?: return
        val finish = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                application.reminderScheduler
                    .reconcile(application.taskRepository.observeTasks().first())
            } finally {
                finish.finish()
            }
        }
    }
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

internal const val ReminderChannelId = "focuslist.reminder"

private const val LogTag = "FocuslistReminder"
