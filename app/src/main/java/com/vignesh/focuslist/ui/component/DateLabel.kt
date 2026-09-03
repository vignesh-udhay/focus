package com.vignesh.focuslist.ui.component

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
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

/**
 * A day as a section heading: "Tomorrow", or "Fri, Sep 5".
 *
 * Named where naming helps and dated where it does not, the same rule
 * [scheduledDateLabel] follows. Only tomorrow is named here: Upcoming never
 * shows today, and past tomorrow a weekday stops being easier to place than
 * the date.
 *
 * No year. A heading in a list of the next few weeks is not the place to carry
 * one, and the row beneath it does not repeat the date at all.
 */
@Composable
fun sectionDateLabel(date: LocalDate, today: LocalDate): String =
    if (date == today.plusDays(1)) {
        stringResource(R.string.task_due_tomorrow)
    } else {
        date.format(rememberDayMonthFormat())
    }

/**
 * Weekday, month and day, arranged the way the locale arranges them.
 *
 * A skeleton rather than a literal pattern, because field order is not
 * universal: `getBestDateTimePattern` returns what the locale actually uses,
 * which a hardcoded "EEE, MMM d" would get wrong everywhere it differs.
 */
@Composable
fun rememberDayMonthFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, DayMonthSkeleton),
            locale
        )
    }
}

/** Weekday, month, day: the three fields a heading and the Today subtitle show. */
private const val DayMonthSkeleton = "EEEMMMd"
