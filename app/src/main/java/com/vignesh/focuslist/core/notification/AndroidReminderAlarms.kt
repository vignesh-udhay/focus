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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.vignesh.focuslist.FocuslistApplication
import com.vignesh.focuslist.MainActivity
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.hasLiveReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

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
        val alarms = alarms ?: return
        val intent = pendingIntent(taskId)

        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    override fun cancel(taskId: String) {
        alarms?.cancel(pendingIntent(taskId))
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
     */
    private fun pendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("focuslist://reminder/$taskId")
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
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

        val finish = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val task = application.taskRepository.observeTasks().first()
                    .firstOrNull { it.id == taskId }

                // Completed, deleted, or its reminder cleared since the alarm
                // was set. All three mean the user is owed nothing.
                if (task == null || !task.hasLiveReminder()) return@launch
                if (!context.canPostNotifications()) return@launch

                context.ensureReminderChannel()
                context.postReminder(taskId, task.title)
            } finally {
                finish.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "focuslist.extra.taskId"
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

/**
 * One notification per task, so several reminders at once read as several
 * things to do rather than the last one overwriting the rest.
 *
 * Deliberately plain for now. The notification frames on the design board specify the
 * collapsed and expanded layouts, the snooze choice and the Done action, and those are the
 * next slice; this posts something truthful in the meantime rather than
 * pretending the design is not there.
 */
private fun Context.postReminder(taskId: String, title: String) {
    val open = PendingIntent.getActivity(
        this,
        taskId.hashCode(),
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, ReminderChannelId)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(title)
        .setContentIntent(open)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .build()

    NotificationManagerCompat.from(this).notify(taskId.hashCode(), notification)
}

internal const val ReminderChannelId = "focuslist.reminder"
