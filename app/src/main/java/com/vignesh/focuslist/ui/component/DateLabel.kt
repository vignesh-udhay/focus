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
    else -> date.format(rememberDateFormat(DayMonthSkeleton, date.year != today.year))
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
        date.format(rememberDateFormat(WeekdayDayMonthSkeleton, date.year != today.year))
    }

/**
 * Weekday, month and day, arranged the way the locale arranges them.
 *
 * A skeleton rather than a literal pattern, because field order is not
 * universal: `getBestDateTimePattern` returns what the locale actually uses,
 * which a hardcoded "EEE, MMM d" would get wrong everywhere it differs.
 */
@Composable
fun rememberDayMonthFormat(): DateTimeFormatter =
    rememberDateFormat(WeekdayDayMonthSkeleton, withYear = false)

/**
 * A date formatter for [skeleton], carrying a year only when [withYear].
 *
 * **The year appears when the date is not in the current one, and never
 * otherwise.** Always showing it puts a "2026" on a task scheduled next
 * Tuesday, which is noise on every row in the app; never showing it makes a
 * repeat ending next August indistinguishable from one that ended last August.
 * The condition that separates the two is cheap to state, so it is stated once
 * and used by every date the app writes.
 *
 * Before this, `scheduledDateLabel` carried a year through `FormatStyle.MEDIUM`
 * while `sectionDateLabel` never did, so two dates a thumb apart on Upcoming
 * were formatted by different rules.
 *
 * A skeleton rather than a literal pattern, because field order is not
 * universal: `getBestDateTimePattern` returns what the locale actually uses,
 * which a hardcoded "MMM d" would get wrong everywhere it differs.
 */
@Composable
fun rememberDateFormat(skeleton: String, withYear: Boolean): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale, skeleton, withYear) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (withYear) skeleton + "y" else skeleton),
            locale
        )
    }
}

/**
 * Weekday, month, day: the three fields a day heading shows.
 *
 * Upcoming's headings are the only caller now. Today's subtitle used to share
 * it, and D-020 removed that subtitle along with the date it carried.
 */
/** A day heading: the weekday, then the month and day. */
private const val WeekdayDayMonthSkeleton = "EEEMMMd"

/** A date on a row: month and day, with no weekday to compete with them. */
private const val DayMonthSkeleton = "MMMd"

/**
 * A time of day, in the reader's own locale and clock.
 *
 * Beside the date labels because it answers the same kind of question and must
 * not drift from them. Three screens format a time inline today; this is where
 * a fourth would have gone wrong.
 */
@Composable
fun rememberTimeFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
}
