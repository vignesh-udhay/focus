package com.vignesh.focuslist.core.domain

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * What a piece of typed text turned out to be.
 *
 * Three outcomes rather than a nullable date, because "no date" and "I did not
 * understand that" are different answers and the caller has to treat them
 * differently: one clears the field, the other is an error.
 */
sealed interface ParsedDate {

    /** The text named a day. */
    data class Recognized(val date: LocalDate) : ParsedDate

    /** There was no text. The task has no date. */
    data object Empty : ParsedDate

    /** There was text, and it is not a date this understands. */
    data object Unrecognized : ParsedDate
}

/**
 * Reads a day out of what the user typed.
 *
 * [today] is passed in rather than read from a clock, exactly as the queries in
 * `TaskQueries` take it, so relative phrases resolve deterministically and the
 * app keeps one answer to what day it is.
 *
 * The whole input has to match one of the forms below. Nothing is extracted
 * from a longer string, which is what makes "tomorrow at 3pm" unrecognized
 * rather than quietly filed as tomorrow: a task carries a day and no time, so
 * accepting the day and dropping the hour would answer a question the user did
 * not ask.
 *
 * Supported, case-insensitively and in English:
 *
 *     today                    tomorrow
 *     monday .. sunday         next monday, this monday
 *     in 3 days                in 2 weeks
 *     4 september              september 4
 *     4 september 2026         september 4 2026
 *
 * A bare weekday means the next one strictly after [today], so "monday" typed
 * on a Monday is a week away. "next monday" and "this monday" mean the same
 * thing. English usage disagrees with itself about which week those point at,
 * and one rule that is never more than seven days out beats a rule that has to
 * know when a week starts, which differs by country.
 *
 * A month and day without a year means the next time that date comes round, so
 * "2 january" typed in December is next year. With a year it means that exact
 * date and nothing is rolled.
 *
 * No supported input ever resolves to a day before [today].
 *
 * Names are matched against the English enum constants rather than
 * locale display names, so the vocabulary is the same on every device and does
 * not shift with a locale data update. Parsing is English-only by design,
 * matching the rest of the app's text.
 */
fun parseDate(text: String, today: LocalDate): ParsedDate {
    val input = normalise(text)
    if (input.isEmpty()) return ParsedDate.Empty

    return keyword(input, today)
        ?: weekday(input, today)
        ?: relative(input, today)
        ?: monthAndDay(input, today)
        ?: ParsedDate.Unrecognized
}

/**
 * Lower cases with [Locale.ROOT], drops commas, and collapses runs of
 * whitespace.
 *
 * The root locale matters: the device's own locale would map "I" to a dotless
 * "ı" on a Turkish device, and "In 2 days" would stop matching.
 */
private fun normalise(text: String): String =
    Whitespace.replace(text.lowercase(Locale.ROOT).replace(",", " "), " ").trim()

private val Whitespace = Regex("\\s+")

private fun keyword(input: String, today: LocalDate): ParsedDate? = when (input) {
    "today" -> ParsedDate.Recognized(today)
    "tomorrow" -> ParsedDate.Recognized(today.plusDays(1))
    else -> null
}

/** "monday", "next monday", "this monday": all the next one strictly ahead. */
private fun weekday(input: String, today: LocalDate): ParsedDate? {
    val name = input.removePrefix("next ").removePrefix("this ")
    val day = WeekdaysByName[name] ?: return null

    return ParsedDate.Recognized(today.with(TemporalAdjusters.next(day)))
}

private val WeekdaysByName: Map<String, DayOfWeek> =
    DayOfWeek.entries.associateBy { it.name.lowercase(Locale.ROOT) }

/** "in 3 days", "in 1 week". */
private fun relative(input: String, today: LocalDate): ParsedDate? {
    val match = Relative.matchEntire(input) ?: return null
    val amount = match.groupValues[1].toLongOrNull() ?: return ParsedDate.Unrecognized
    val weeks = match.groupValues[2].startsWith("week")

    // A number large enough to run off the end of the calendar is not a date
    // anyone meant, and is not one this can represent.
    return runCatching {
        ParsedDate.Recognized(if (weeks) today.plusWeeks(amount) else today.plusDays(amount))
    }.getOrElse { ParsedDate.Unrecognized }
}

private val Relative = Regex("in (\\d+) (days?|weeks?)")

/**
 * "4 september", "september 4", and the same two with a year.
 *
 * Both orders, because both read naturally and neither is ambiguous once the
 * month is spelled out. Purely numeric dates are deliberately absent: 03/09 is
 * March in one country and September in another, and the app has no way to
 * know which the user meant.
 */
private fun monthAndDay(input: String, today: LocalDate): ParsedDate? {
    val match = DayFirst.matchEntire(input) ?: MonthFirst.matchEntire(input) ?: return null

    val groups = match.groupValues
    val dayFirst = match.groups[1]?.value?.firstOrNull()?.isDigit() == true
    val day = (if (dayFirst) groups[1] else groups[2]).toInt()
    val month = MonthsByName[if (dayFirst) groups[2] else groups[1]] ?: return null
    val year = groups[3].takeIf { it.isNotEmpty() }?.toInt()

    // February 30th is not a date in any year, so this is not a rolling case.
    val monthDay = runCatching { MonthDay.of(month, day) }.getOrNull()
        ?: return ParsedDate.Unrecognized

    return if (year == null) {
        ParsedDate.Recognized(nextOccurrence(monthDay, today))
    } else {
        // An explicit year is taken literally. "29 february 2027" names a day
        // that does not exist, and rolling it to the 28th would silently store
        // a date the user did not type.
        runCatching { ParsedDate.Recognized(monthDay.atYearExactly(year)) }
            .getOrElse { ParsedDate.Unrecognized }
    }
}

private val DayFirst = Regex("(\\d{1,2}) ([a-z]+)(?: (\\d{4}))?")
private val MonthFirst = Regex("([a-z]+) (\\d{1,2})(?: (\\d{4}))?")

private val MonthsByName: Map<String, Month> = buildMap {
    Month.entries.forEach { month ->
        val name = month.name.lowercase(Locale.ROOT)
        put(name, month)
        put(name.take(3), month)
    }
}

/**
 * The next time [monthDay] comes round, counting today as still to come.
 *
 * Years where the date does not exist are skipped, so "29 february" typed in
 * 2026 lands on the next leap day rather than being nudged to the 28th.
 */
private fun nextOccurrence(monthDay: MonthDay, today: LocalDate): LocalDate {
    var year = today.year

    // February 29th is the only date that can be missing, and never for more
    // than three years running.
    repeat(MaxYearsAhead) {
        if (monthDay.isValidYear(year)) {
            val candidate = monthDay.atYear(year)
            if (!candidate.isBefore(today)) return candidate
        }
        year++
    }

    error("no occurrence of $monthDay within $MaxYearsAhead years of $today")
}

private const val MaxYearsAhead = 8

/** [MonthDay.atYear] adjusts February 29th to the 28th; this refuses instead. */
private fun MonthDay.atYearExactly(year: Int): LocalDate =
    if (isValidYear(year)) atYear(year) else throw DateTimeException("$this is not a date in $year")

/**
 * A quick-add title split into the text worth keeping and the day it named.
 *
 * [date] is null when the title did not end in a day, which is the ordinary
 * case: most captures are just a title.
 *
 * [dateStart] is where the day began in the text that was handed in, so the
 * field can mark the words it is about to take. It is null exactly when [date]
 * is, and indexes the original string rather than [title], which has been
 * trimmed.
 */
data class TitleWithDate(val title: String, val date: LocalDate?, val dateStart: Int?)

/**
 * Reads a day off the end of a quick-add title.
 *
 * Quick Add is one field, so the day has to arrive in the same breath as the
 * title or it does not arrive at all. "Call the plumber tomorrow" is a task
 * called "Call the plumber", scheduled tomorrow.
 *
 * Only a trailing run of words is considered, and it is handed to [parseDate]
 * whole, so everything that function refuses is refused here too. That is what
 * keeps "Write the report tomorrow at 3pm" intact: "at 3pm" is not a date,
 * "tomorrow at 3pm" is not one either, and a task carries no time of day, so
 * there is nothing here that could store the hour. The title keeps the words
 * and the user can set a day in the details sheet.
 *
 * The longest trailing match wins, so "in 2 weeks" is read as a fortnight
 * rather than stopping at "weeks".
 *
 * A title that is nothing but a day stays a title. "tomorrow" on its own is
 * more likely a task someone meant to finish naming than a dateless reminder,
 * and capturing it as an empty title would lose what they typed.
 */
fun splitTrailingDate(text: String, today: LocalDate): TitleWithDate {
    // Matched over the original string rather than a trimmed copy, so the
    // offsets stay usable for marking the words in the field.
    val words = Word.findAll(text).toList()

    // Never the whole title: something has to be left to call the task.
    for (count in minOf(MaxDateWords, words.size - 1) downTo 1) {
        val start = words[words.size - count].range.first
        val parsed = parseDate(text.substring(start), today)
        if (parsed is ParsedDate.Recognized) {
            return TitleWithDate(text.substring(0, start).trim(), parsed.date, start)
        }
    }

    return TitleWithDate(text.trim(), null, null)
}

private val Word = Regex("\\S+")

/** No form [parseDate] understands is longer than "4 september 2026". */
private const val MaxDateWords = 3
