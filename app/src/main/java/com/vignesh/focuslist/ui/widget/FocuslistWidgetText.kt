package com.vignesh.focuslist.ui.widget

import android.content.Context
import android.text.format.DateFormat
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.FocusNow
import com.vignesh.focuslist.core.domain.FocusNowReason
import com.vignesh.focuslist.core.domain.StoredFocusSession
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

internal fun widgetLeadReason(
    context: Context,
    lead: FocusNow,
    storedFocus: StoredFocusSession?
): String = when (lead.reason) {
    FocusNowReason.ResumePaused -> {
        val session = storedFocus?.takeIf { it.taskId == lead.task.id }?.session
        val remaining = session?.remaining(
            now = session.pausedAt ?: session.startedAt,
            estimateMinutes = lead.task.estimatedDurationMinutes
        )
        if (remaining == null) {
            context.getString(R.string.today_focus_now_paused)
        } else {
            val roundedUp = ((remaining.seconds + 59L) / 60L).coerceAtLeast(0L)
            context.getString(R.string.today_focus_now_paused_remaining, roundedUp)
        }
    }

    FocusNowReason.ReminderPassed -> context.getString(
        R.string.today_focus_now_was_due_at,
        lead.task.reminderAt?.toLocalTime()?.format(context.timeFormatter()).orEmpty()
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
