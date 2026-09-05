package com.vignesh.focuslist.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vignesh.focuslist.MainActivity
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.MorningHour
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.SnoozeOption
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.availableSnoozeOptions
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/**
 * What a reminder looks like when it arrives.
 *
 * Two states, both drawn on the Clean Slate board. The reminder itself, with
 * Done and Snooze, and the snooze choice that replaces it in place when the
 * user taps Snooze. Replacing rather than stacking, because they are one
 * conversation about one task and two notifications would be the app talking
 * over itself.
 *
 * One notification per task, keyed on the task id, so several reminders at
 * once read as several things to do rather than the last one overwriting the
 * rest.
 */

/** The reminder, as `notify/Collapsed` and `notify/Expanded` draw it. */
internal fun Context.postReminder(task: Task) {
    val summary = task.reminderSummary(this)

    val builder = reminderBuilder(task, summary)
        .addAction(
            0,
            getString(R.string.reminder_action_done),
            actionIntent(task.id, ReminderActionReceiver.ACTION_DONE)
        )
        .addAction(
            0,
            getString(R.string.reminder_action_snooze),
            actionIntent(task.id, ReminderActionReceiver.ACTION_SNOOZE)
        )

    // The expanded form is where the notes live. Collapsed shows the title and
    // the summary line, which is what the frame draws.
    task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        builder.setStyle(
            NotificationCompat.BigTextStyle().bigText("$summary\n$notes")
        )
    }

    NotificationManagerCompat.from(this).notify(task.notificationId, builder.build())
}

/**
 * The snooze choice, as `notify/Snooze options` draws it.
 *
 * Three actions rather than the frame's four chips, because a notification
 * shows at most three. `availableSnoozeOptions` decides which three, and it is
 * asked here rather than assumed so the labels and the arithmetic cannot
 * disagree about what "this evening" means.
 */
internal fun Context.postSnoozeOptions(task: Task, now: LocalDateTime) {
    val builder = reminderBuilder(task, task.reminderSummary(this))
        .setSubText(getString(R.string.reminder_snooze_until))

    availableSnoozeOptions(now).forEach { option ->
        builder.addAction(
            0,
            snoozeLabel(option),
            actionIntent(task.id, ReminderActionReceiver.ACTION_SNOOZE_UNTIL, option)
        )
    }

    NotificationManagerCompat.from(this).notify(task.notificationId, builder.build())
}

/**
 * The test reminder, arriving.
 *
 * Deliberately plain: no Done, no Snooze, nothing to act on. There is no task
 * behind it, and offering to complete one would be the app inventing work. It
 * says what it is and how late it was, which is the only thing the person who
 * asked for it wants to know.
 *
 * The lateness is in the text rather than left for the health screen, because
 * the point of the test is that the user is looking at the phone when it
 * arrives, or at least at the notification.
 */
internal fun Context.postTestReminder(arrivedAt: Instant) {
    val builder = NotificationCompat.Builder(this, ReminderChannelId)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(getString(R.string.reminder_test_title))
        .setContentText(
            getString(
                R.string.reminder_test_body,
                formatTime(arrivedAt.atZone(ZoneId.systemDefault()).toLocalTime())
            )
        )
        .setContentIntent(openIntent(TestReminder.TaskId))
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)

    NotificationManagerCompat.from(this).notify(TestReminder.TaskId.notificationId, builder.build())
}

/** Takes the reminder off screen once it has been dealt with. */
internal fun Context.cancelReminder(taskId: String) {
    NotificationManagerCompat.from(this).cancel(taskId.notificationId)
}

// --- internals ----------------------------------------------------------------

private fun Context.reminderBuilder(task: Task, summary: String) =
    NotificationCompat.Builder(this, ReminderChannelId)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(task.title)
        .setContentText(summary)
        .setContentIntent(openIntent(task.id))
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)

/**
 * The line under the title: the time it was set for, and how often it repeats.
 *
 * The time is formatted with the device's own 12 or 24 hour preference rather
 * than a pattern of ours, because a reminder that says 3:30 PM to someone
 * whose phone says 15:30 everywhere else reads as a different app's
 * notification.
 */
private fun Task.reminderSummary(context: Context): String {
    val at = reminderAt ?: return ""
    val time = context.formatTime(at.toLocalTime())
    val repeat = recurrence?.let { context.getString(it.reminderLabel) }

    return if (repeat == null) {
        time
    } else {
        context.getString(R.string.reminder_summary_separator, time, repeat)
    }
}

private fun Context.formatTime(time: LocalTime): String {
    val moment = LocalDateTime.now().with(time).atZone(ZoneId.systemDefault()).toInstant()
    return android.text.format.DateFormat.getTimeFormat(this).format(Date.from(moment))
}

private val Recurrence.reminderLabel: Int
    get() = when (this) {
        Recurrence.DAILY -> R.string.reminder_repeat_daily
        Recurrence.WEEKLY -> R.string.reminder_repeat_weekly
        Recurrence.MONTHLY -> R.string.reminder_repeat_monthly
        Recurrence.YEARLY -> R.string.reminder_repeat_yearly
    }

private fun Context.snoozeLabel(option: SnoozeOption): String = when (option) {
    SnoozeOption.TenMinutes -> getString(R.string.reminder_snooze_ten_minutes)
    SnoozeOption.OneHour -> getString(R.string.reminder_snooze_one_hour)
    SnoozeOption.ThisEvening -> getString(R.string.reminder_snooze_this_evening)
    // The hour is in the label, so it is formatted rather than written into
    // the string, and it stays true if MorningHour ever moves.
    SnoozeOption.TomorrowMorning ->
        getString(R.string.reminder_snooze_tomorrow, formatTime(MorningHour))
}

/**
 * A distinct [PendingIntent] per task and action.
 *
 * Matching ignores extras, so the action and the option have to be in the
 * data URI. Without that, Done and Snooze on the same task would be the same
 * pending intent and the second would silently replace the first.
 */
private fun Context.actionIntent(
    taskId: String,
    action: String,
    option: SnoozeOption? = null
): PendingIntent {
    val intent = Intent(this, ReminderActionReceiver::class.java).apply {
        this.action = action
        data = Uri.parse("focuslist://reminder/$taskId/$action/${option?.name.orEmpty()}")
        putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
        option?.let { putExtra(ReminderActionReceiver.EXTRA_SNOOZE_OPTION, it.name) }
    }

    return PendingIntent.getBroadcast(
        this,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun Context.openIntent(taskId: String): PendingIntent = PendingIntent.getActivity(
    this,
    0,
    Intent(this, MainActivity::class.java)
        .setData(Uri.parse("focuslist://task/$taskId"))
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

/** One notification per task, and the same one every time it is touched. */
internal val Task.notificationId: Int get() = id.notificationId

internal val String.notificationId: Int get() = hashCode()
