package com.vignesh.focuslist.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vignesh.focuslist.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A scheduled date as the UI says it: "Today", "Tomorrow", or the date itself.
 *
 * Presentation, not domain: [LocalDate] carries the day, and deciding that
 * tomorrow reads as "Tomorrow" is this layer's call. Shared so that a date
 * Quick Add is about to set and the same date on a task row are never worded
 * differently.
 *
 * Only the two nearest days are named. Beyond that a weekday stops being
 * easier to place than the date, and the localized medium format is what the
 * rest of the app shows.
 */
@Composable
fun scheduledDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.task_due_today)
    today.plusDays(1) -> stringResource(R.string.task_due_tomorrow)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
