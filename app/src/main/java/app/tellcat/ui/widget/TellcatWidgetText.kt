package app.tellcat.ui.widget

import android.content.Context
import android.text.format.DateFormat
import app.tellcat.R
import app.tellcat.ui.component.earlyReminderLabel
import app.tellcat.core.domain.ReminderStage
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.rowReminder
import app.tellcat.core.text.recurrenceSummary
import app.tellcat.ui.component.DurationLabel
import app.tellcat.ui.component.durationLabel
import app.tellcat.ui.component.shortTimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WidgetTaskText(
    val title: String,
    val metadata: String?,
    val duration: DurationLabel?
)

internal fun widgetTaskText(context: Context, task: Task, today: LocalDate): WidgetTaskText {
    val overdue = task.scheduledDate?.isBefore(today) == true && !task.isCompleted
    // The row's rule, D-075: a reminder names its day when that is not the
    // task's day, and is dropped once its own day is over. On an overdue task it
    // follows the date, as on the app's row, so one still to come is not hidden.
    //
    // And it says what the reminder will do next, as the app's row does: a
    // snooze still to ring is "Snoozed until 5:37 PM" (D-109), a pending
    // follow-up "Reminds again at 9:40 AM" (D-108). The same decision,
    // `rowReminder`, and the same words, so the widget cannot pass a snooze off
    // as the time the user set.
    val shown = task.rowReminder()?.takeUnless { it.at.toLocalDate().isBefore(today) }
    val reminder = shown?.let { next ->
        val at = next.at
        val time = at.toLocalTime().format(context.timeFormatter())
        val moment = if (at.toLocalDate() == task.scheduledDate) {
            time
        } else {
            context.getString(R.string.task_row_reminder_on_day, context.dayLabel(at.toLocalDate(), today), time)
        }
        val words = when (next.stage) {
            ReminderStage.Set -> moment
            ReminderStage.Snoozed -> context.getString(R.string.task_row_snoozed, moment)
            ReminderStage.FollowUp -> context.getString(R.string.task_row_follow_up, moment)
        }
        // The row's words, D-115.
        next.alsoBefore?.let { early ->
            context.getString(R.string.task_row_also_early, words, context.getString(earlyReminderLabel(early)))
        } ?: words
    }
    val metadata = buildList {
        if (!overdue) reminder?.let(::add)
        if (overdue) task.scheduledDate?.let { add(it.format(context.dateFormatter(it, today))) }
        if (overdue) reminder?.let(::add)
        task.recurrence?.let { add(recurrenceSummary(context, it, today)) }
    }.takeIf(List<String>::isNotEmpty)?.joinToString(Separator)

    return WidgetTaskText(
        title = task.title,
        metadata = metadata,
        duration = task.estimatedDurationMinutes?.let { durationLabel(context, it) }
    )
}

/** `scheduledDateLabel` outside composition: "Today", "Tomorrow", or the date. */
private fun Context.dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> getString(R.string.task_due_today)
    today.plusDays(1) -> getString(R.string.task_due_tomorrow)
    else -> date.format(dateFormatter(date, today))
}

// The phone's 12 or 24 hour setting, the same way the app's rows say it. D-079.
private fun Context.timeFormatter(): DateTimeFormatter = shortTimeFormatter()

private fun Context.dateFormatter(date: LocalDate, today: LocalDate): DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale(), if (date.year == today.year) "MMMd" else "MMMdy"),
        locale()
    )

private fun Context.locale(): Locale = resources.configuration.locales[0] ?: Locale.getDefault()

private const val Separator = " · "
