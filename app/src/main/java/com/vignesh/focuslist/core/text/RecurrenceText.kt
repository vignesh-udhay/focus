package com.vignesh.focuslist.core.text

import android.content.Context
import android.icu.text.ListFormatter
import android.icu.util.Calendar
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.RecurrenceEnd
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * How much of a rule a reader is being told.
 *
 * Three callers want three different amounts, and this replaces the boolean that
 * covered the first two. A flag that answers one question is fine; a second flag
 * would make four combinations of which three are meaningful, which is the point
 * at which the question stops being yes-or-no.
 */
enum class RecurrenceStyle {

    /**
     * The shortest true thing: "Weekly", "Mon, Wed and Fri", "Every 2 weeks".
     *
     * For the Plan row and the task row, where the value sits beside a label and
     * next to siblings reading "Today", "None" and "45m". The period is dropped
     * when days are named, because days can only belong to a weekly rule and
     * saying so twice costs a line that these rows do not have.
     */
    Compact,

    /**
     * The same rule as a phrase: "Every week on Mon, Wed and Fri".
     *
     * For the reminder notification, which is read at a glance mid-task and
     * phrases the repeat as the sentence it belongs to.
     */
    Sentence,

    /**
     * The phrase, plus when the series stops.
     *
     * For the Repeat sheet's subtitle, and only there. See [recurrenceSummary]
     * for why the horizon is in one place and not the others.
     */
    Full
}

/**
 * What a recurrence rule is called.
 *
 * One place, because the task row, the Repeat row on Task Details, the Repeat
 * sheet and the reminder notification all name the same rule and must not
 * disagree about it.
 *
 * **In `core/text` rather than beside `DateLabel` and `DurationLabel` in
 * `ui/component`**, because the reminder notification names a rule too and
 * `core/notification` must not import `ui`. Nothing in `core` does and this is
 * not the file to start with. The composable wrapper below is a convenience for
 * the screens; the plain function is what a `BroadcastReceiver`, which has no
 * composition, calls.
 *
 * **The horizon belongs to [RecurrenceStyle.Full] alone.** The sheet spells the
 * whole rule out because that is where it is being built, and because the sheet
 * is the one place on Task Details that does not commit as you go: the subtitle
 * is a preview of what Save will write. The rows are a glance. Board frame
 * 471:6668 shows the applied Plan row with no end clause while 471:6537 shows
 * the subtitle with one, so this is the board's split as well as ours.
 */
fun recurrenceSummary(
    context: Context,
    recurrence: Recurrence,
    today: LocalDate,
    style: RecurrenceStyle = RecurrenceStyle.Compact
): String {
    val shape = ruleShape(context, recurrence.asDisplayed(), style)
    val horizon =
        if (style == RecurrenceStyle.Full) horizon(context, recurrence.end, today) else null

    return if (horizon == null) {
        shape
    } else {
        context.getString(R.string.task_repeat_with_end, shape, horizon)
    }
}

/** [recurrenceSummary] where a composition is available. */
@Composable
fun recurrenceSummary(
    recurrence: Recurrence,
    today: LocalDate,
    style: RecurrenceStyle = RecurrenceStyle.Compact
): String = recurrenceSummary(LocalContext.current, recurrence, today, style)

/**
 * How often, without saying when it stops.
 *
 * The days alone when they are the whole of what makes the rule unusual, and
 * both when the interval is unusual too. "Mon, Wed and Fri" says everything
 * about a weekly rule; "Every 2 weeks" does not, and dropping either half would
 * describe a rule the task does not have.
 *
 * [RecurrenceStyle.Compact] drops the period when the days carry it, which is
 * the only difference between it and the two phrase styles.
 */
private fun ruleShape(
    context: Context,
    recurrence: Recurrence,
    style: RecurrenceStyle
): String {
    val period = intervalLabel(context, recurrence, style)

    if (!recurrence.hasWeekdays) return period

    // Standalone in the row, where the period is implied and dropped; part of a
    // phrase everywhere else. English capitalises the first and not the second.
    val periodIsImplied = style == RecurrenceStyle.Compact && recurrence.interval <= 1
    val days = dayGroupName(context, recurrence.weekdays, standalone = periodIsImplied)
        ?: weekdayList(context, recurrence.weekdays)

    return if (periodIsImplied) days else context.getString(R.string.task_repeat_on_days, period, days)
}

/**
 * The rule as it should be described, which is not always the rule as stored.
 *
 * Every weekday selected at an interval of one is a daily rule. Not a rename:
 * `RecurrenceRuleTest` asserts the two produce the same date on every day of a
 * fortnight, so "Mon, Tue, Wed, Thu, Fri, Sat and Sun" is a long way of writing
 * "Daily" and the row was spending its whole width on it.
 *
 * **The interval is load-bearing.** All seven days every *other* week is seven
 * days on and seven days off, which is not daily and is asserted not to be.
 *
 * Describing only, never stored. Normalising the saved rule would throw away the
 * chips: the user taps seven of them and reopens the sheet to find the Daily
 * unit and no Days section at all, with a day impossible to remove without
 * rebuilding the rule. The row shows what it means and the sheet shows what it
 * is, which is the same split `RecurrenceStyle` already draws.
 */
private fun Recurrence.asDisplayed(): Recurrence =
    if (unit == RecurrenceUnit.WEEKLY && interval <= 1 && weekdays.size == DaysInWeek) {
        Recurrence(unit = RecurrenceUnit.DAILY, end = end)
    } else {
        this
    }

/**
 * A name for the whole set, or null when it has none.
 *
 * Two sets are worth naming and the rest are a list. Five days is "Mon, Tue,
 * Wed, Thu, Fri and Sat" spelled out, which is nearly as long as the seven-day
 * case that started this and is not rescued by being daily.
 *
 * **Which days are the weekend is the locale's answer, not Monday-to-Friday.**
 * `android.icu.util.Calendar` carries a weekend definition per locale, and a
 * good deal of the world does not take Saturday and Sunday. Hardcoding the
 * English-speaking week would be the same mistake as joining a list with "and",
 * one screen further along.
 *
 * A day the locale calls a partial weekend counts as weekend here. ICU
 * distinguishes a Saturday that becomes the weekend at one o'clock from one that
 * is weekend throughout; a task list has no use for the hour.
 */
private fun dayGroupName(
    context: Context,
    days: Set<DayOfWeek>,
    standalone: Boolean
): String? {
    val weekend = weekendDays(context.locale())

    return when (days) {
        weekend -> context.getString(
            if (standalone) R.string.task_repeat_weekends else R.string.task_repeat_weekends_phrase
        )

        DayOfWeek.entries.toSet() - weekend -> context.getString(
            if (standalone) R.string.task_repeat_weekdays else R.string.task_repeat_weekdays_phrase
        )

        else -> null
    }
}

/**
 * The days this locale counts as weekend, whole or partial.
 *
 * **Asked rather than declared**, and asked twice per day. Android's ICU exposes
 * `isWeekend()` and not the `getDayOfWeekType` that would answer this directly,
 * and `isWeekend` is a question about an *instant*: a locale whose weekend
 * begins on Saturday afternoon answers no at breakfast and yes at supper. Both
 * ends of the day are probed and either one is enough, so a day the weekend only
 * partly covers still counts. A task list has no use for the hour.
 *
 * The reference week is a fixed Monday, so this reads the locale's rule and not
 * today's date.
 */
private fun weekendDays(locale: Locale): Set<DayOfWeek> {
    val calendar = Calendar.getInstance(locale)

    return DayOfWeek.entries
        .filter { day ->
            val date = ReferenceMonday.plusDays((day.value - 1).toLong())

            ProbeHours.any { hour ->
                calendar.set(date.year, date.monthValue - 1, date.dayOfMonth, hour, 0, 0)
                calendar.isWeekend
            }
        }
        .toSet()
}

/** A Monday, so the seven probes below walk a week in `java.time` order. */
private val ReferenceMonday: LocalDate = LocalDate.of(2024, 1, 1)

/** Just after a day begins and just before it ends, to catch a partial weekend. */
private val ProbeHours = listOf(1, 23)

private const val DaysInWeek = 7

/**
 * How often, without the days: "Weekly", or "Every 2 weeks".
 *
 * An interval of one keeps the adjective the app has always used, because
 * "Every week" beside "Every 2 weeks" would be consistent and worse: the plain
 * case is the common one and reads better named than counted.
 *
 * **Except in the phrase styles, where it is counted anyway.** `strings.xml` had
 * a second set of four names for the reminder notification and gave the reason:
 * a notification is read at a glance, mid-task, and phrases the repeat as the
 * sentence it belongs to, so it said "Every week" where the row said "Weekly".
 * That set is gone, not the distinction. The plurals below already read "Every
 * week" in their `one` case, so asking for one of anything gives the phrase form
 * without a second set of strings to keep in step.
 */
private fun intervalLabel(
    context: Context,
    recurrence: Recurrence,
    style: RecurrenceStyle
): String =
    if (recurrence.interval <= 1 && style == RecurrenceStyle.Compact) {
        context.getString(
            when (recurrence.unit) {
                RecurrenceUnit.DAILY -> R.string.task_recurrence_daily
                RecurrenceUnit.WEEKLY -> R.string.task_recurrence_weekly
                RecurrenceUnit.MONTHLY -> R.string.task_recurrence_monthly
                RecurrenceUnit.YEARLY -> R.string.task_recurrence_yearly
            }
        )
    } else {
        context.resources.getQuantityString(
            when (recurrence.unit) {
                RecurrenceUnit.DAILY -> R.plurals.task_repeat_every_days
                RecurrenceUnit.WEEKLY -> R.plurals.task_repeat_every_weeks
                RecurrenceUnit.MONTHLY -> R.plurals.task_repeat_every_months
                RecurrenceUnit.YEARLY -> R.plurals.task_repeat_every_years
            },
            recurrence.interval,
            recurrence.interval
        )
    }

/**
 * When the series stops, or null when it does not.
 *
 * The date carries its year, matching the Ends row directly below the subtitle
 * and the picker behind it. The board writes "until Aug 17" without one, and an
 * end date is the wrong place to save four characters: a horizon that is a year
 * out reads exactly like one that is not, and nothing on the screen would say
 * which it was.
 */
private fun horizon(context: Context, end: RecurrenceEnd, today: LocalDate): String? = when (end) {
    RecurrenceEnd.Never -> null

    is RecurrenceEnd.OnDate -> context.getString(
        R.string.task_repeat_until,
        recurrenceEndDate(end.date, today)
    )

    is RecurrenceEnd.AfterOccurrences -> context.resources.getQuantityString(
        R.plurals.task_repeat_for_occurrences,
        end.count,
        end.count
    )
}

/**
 * An end date, written out.
 *
 * The localised medium form, which is what `DateLabel` falls back to for any
 * date it does not name. Not "Tomorrow": an end date is a horizon rather than a
 * plan, and naming it relative to today would go stale the moment it stopped
 * being tomorrow while the rule did not change.
 */
fun recurrenceEndDate(date: LocalDate, today: LocalDate): String =
    date.format(
        DateTimeFormatter.ofPattern(
            // The same rule `DateLabel` applies: a year only when the date is
            // not in the current one. A horizon is the date most likely to
            // cross a year, and the most misleading to write bare when it does.
            if (date.year == today.year) "MMM d" else "MMM d, y"
        )
    )

/**
 * Selected weekdays in week order: "Mon, Wed and Fri".
 *
 * Sorted by [DayOfWeek.getValue] rather than by the set's iteration order, so
 * the same three days always read the same way whatever order they were tapped
 * in. Monday first, matching how the rule counts its weeks.
 *
 * **Joined by [ListFormatter], not by string concatenation.** A list is not
 * commas plus an "and" before the last item in most of the world: the separator
 * differs, the conjunction differs, and some languages put it somewhere else
 * entirely. ICU knows all of that per locale and ships with Android, so the
 * alternative was inventing a rule for English and exporting it everywhere.
 *
 * The names themselves come from `java.time` in the device's locale rather than
 * from `strings.xml`, which is seven translations this app does not have to
 * carry and cannot get wrong.
 */
fun weekdayList(context: Context, weekdays: Set<DayOfWeek>): String {
    val locale = context.locale()
    val names = weekdays.sortedBy(DayOfWeek::getValue)
        .map { day -> day.getDisplayName(TextStyle.SHORT, locale) }

    return ListFormatter.getInstance(locale).format(names)
}

/** One weekday as a single letter, for the chips. */
fun DayOfWeek.chipLabel(context: Context): String =
    getDisplayName(TextStyle.NARROW, context.locale())

/**
 * One weekday written out, for anything that cannot see where the chip sits.
 *
 * The narrow names are a single letter and two pairs of them collide in English,
 * so a screen reader announcing the chip label alone would offer "T" twice with
 * nothing to tell Tuesday from Thursday.
 */
fun DayOfWeek.fullName(context: Context): String =
    getDisplayName(TextStyle.FULL, context.locale())

/**
 * The Every row's own value: "1 week", "2 weeks".
 *
 * Counted rather than named, unlike [intervalLabel], because the row is already
 * labelled Every and a value of "Every week" beside it would say the word twice.
 * That is how the board writes it.
 */
@Composable
fun recurrenceIntervalValue(unit: RecurrenceUnit, interval: Int): String {
    val count = interval.coerceAtLeast(1)

    // `pluralStringResource` rather than `LocalContext.current.resources`, which
    // reads a `Resources` that does not recompose: change the device language
    // with this sheet open and the value would keep the old wording while every
    // `stringResource` beside it updated.
    return pluralStringResource(
        id = when (unit) {
            RecurrenceUnit.DAILY -> R.plurals.task_repeat_count_days
            RecurrenceUnit.WEEKLY -> R.plurals.task_repeat_count_weeks
            RecurrenceUnit.MONTHLY -> R.plurals.task_repeat_count_months
            RecurrenceUnit.YEARLY -> R.plurals.task_repeat_count_years
        },
        count = count,
        formatArgs = arrayOf(count)
    )
}

/**
 * The locale the app is actually displaying in.
 *
 * Read from the configuration rather than from `Locale.getDefault()`, so a
 * per-app language override is honoured.
 */
private fun Context.locale(): Locale =
    resources.configuration.locales[0] ?: Locale.getDefault()
