package com.vignesh.focuslist.ui.widget

import android.content.Context
import android.text.format.DateFormat
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.text.recurrenceSummary
import com.vignesh.focuslist.ui.component.DurationLabel
import com.vignesh.focuslist.ui.component.durationLabel
import com.vignesh.focuslist.ui.component.shortTimeFormatter
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
    val reminder = task.ringsAt?.takeUnless { it.toLocalDate().isBefore(today) }?.let { at ->
        val time = at.toLocalTime().format(context.timeFormatter())
        if (at.toLocalDate() == task.scheduledDate) time
        else context.getString(R.string.task_row_reminder_on_day, context.dayLabel(at.toLocalDate(), today), time)
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
