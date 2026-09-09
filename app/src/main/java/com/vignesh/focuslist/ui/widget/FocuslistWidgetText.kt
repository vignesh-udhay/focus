package com.vignesh.focuslist.ui.widget

import android.content.Context
import android.text.format.DateFormat
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.text.recurrenceSummary
import com.vignesh.focuslist.ui.component.DurationLabel
import com.vignesh.focuslist.ui.component.durationLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class WidgetTaskText(
    val title: String,
    val metadata: String?,
    val duration: DurationLabel?
)

internal fun widgetTaskText(context: Context, task: Task, today: LocalDate): WidgetTaskText {
    val overdue = task.scheduledDate?.isBefore(today) == true && !task.isCompleted
    val metadata = buildList {
        if (!overdue) task.reminderAt?.let { add(it.toLocalTime().format(context.timeFormatter())) }
        if (overdue) task.scheduledDate?.let { add(it.format(context.dateFormatter(it, today))) }
        task.recurrence?.let { add(recurrenceSummary(context, it, today)) }
    }.takeIf(List<String>::isNotEmpty)?.joinToString(Separator)

    return WidgetTaskText(
        title = task.title,
        metadata = metadata,
        duration = task.estimatedDurationMinutes?.let { durationLabel(context, it) }
    )
}

private fun Context.timeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale())

private fun Context.dateFormatter(date: LocalDate, today: LocalDate): DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale(), if (date.year == today.year) "MMMd" else "MMMdy"),
        locale()
    )

private fun Context.locale(): Locale = resources.configuration.locales[0] ?: Locale.getDefault()

private const val Separator = " · "
