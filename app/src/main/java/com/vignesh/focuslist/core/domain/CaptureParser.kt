package com.vignesh.focuslist.core.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Reading a day *and* a time off the end of a Quick Add title.
 *
 * `docs/decisions.md` D-011. A day sets the scheduled date, as it always did. A
 * time now sets a reminder, which is the part that is new.
 *
 * **Why a time is read at all**, when `date-parsing.md` deliberately excluded
 * one: its reason was stated plainly, that "a task carries a day and no time, so
 * accepting the day and dropping the hour would tell the user their 3pm was
 * understood when nothing about it was stored". That premise stopped being true
 * when Phase 1 shipped reminders. A time now has somewhere to live, and the rule
 * outlived the reason it was written for.
 *
 * This is a separate function from [splitTrailingDate] rather than a change to
 * it, because the two callers want different things. Task Details' date fields
 * take a date and only a date; reading a time there would have nowhere to put
 * it. Quick Add is the one place a title, a day and a reminder all arrive in the
 * same breath.
 */

/**
 * A Quick Add title split into what to keep and what was understood.
 *
 * The two ranges are kept apart rather than merged into one mark, and that is
 * what makes dismissing the Reminder chip possible. D-011 requires dismissing to
 * drop the reminder *and* unmark its words while leaving the day alone, so the
 * field has to be able to mark the day without the time. A single start index
 * could not: everything after it would stay coloured.
 *
 * @param title the text worth keeping. What did not parse stays here: nothing is
 * guessed, and a title is never emptied by parsing.
 * @param date the day the title named, or null. Sets the scheduled date.
 * @param time the time of day the title named, or null. Sets a reminder.
 * @param dateRange where the day sits in the text as it was handed in, or null.
 * Indexes the original string rather than [title], which has been trimmed, so
 * the marking does not drift when there is leading whitespace.
 * @param timeRange where the time sits, on the same terms.
 */
data class CapturedTask(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val dateRange: IntRange?,
    val timeRange: IntRange?
) {

    /** Whether anything was understood at all. D-011's first of three states. */
    val isPlain: Boolean
        get() = date == null && time == null

    /**
     * Everything the field should mark, as one span.
     *
     * The two ranges are adjacent whenever both exist, because the time is read
     * off the end and the day off what is left, so the union is a run rather
     * than two islands.
     */
    val markRange: IntRange?
        get() = when {
            dateRange != null && timeRange != null ->
                minOf(dateRange.first, timeRange.first)..maxOf(dateRange.last, timeRange.last)

            else -> dateRange ?: timeRange
        }

    /**
     * The moment to interrupt the user, given the day the task will be saved on.
     *
     * Null when no time was read, which is most captures. A time with no day of
     * its own rides on [defaultDate], which is the day the capturing screen was
     * going to use anyway: Today captures for today, so "call the dentist at
     * 3pm" is a reminder this afternoon.
     *
     * **Unless this afternoon has been and gone.** D-030. Typed at 4pm, that
     * same title used to capture a reminder for 3pm today, an hour in the past,
     * which the scheduler then clamped to now and rang immediately. It resolves
     * to 3pm tomorrow instead, through [nextReminderOccurrence], which is the
     * rule `date-parsing.md` already applies to the day half of the parse.
     *
     * The day the parser named cannot itself be in the past, so the only moment
     * this moves is one whose time of day has gone by. It is shown before it is
     * saved: the Reminder chip reads the resolved day and time, so a capture
     * that rolled says "Tomorrow" on the sheet the user is still looking at.
     */
    fun reminderAt(defaultDate: LocalDate, now: LocalDateTime): LocalDateTime? {
        val at = time ?: return null
        return nextReminderOccurrence(LocalDateTime.of(date ?: defaultDate, at), now)
    }

    /**
     * The same capture with the reminder dropped and its words unmarked.
     *
     * What dismissing the Reminder chip does. D-011 is specific that dismissing
     * has to unmark the same run the field already owns, "so they stay in the
     * title and the text and the outcome cannot disagree". The earlier design
     * failed exactly this test: it left the field reading "tomorrow at 6" while
     * the task saved without a reminder.
     *
     * The day survives, because only the reminder has a control. A wrong day is
     * quiet and cheap and is corrected by typing, which is why D-011 gives it no
     * chip; a wrong reminder is a broken promise in either direction.
     *
     * The time's words stay in the text and are simply no longer marked, so what
     * the field shows and what will be saved go on agreeing.
     */
    fun withoutReminder(): CapturedTask = copy(time = null, timeRange = null)
}

/**
 * Reads a trailing day and a trailing time off [text].
 *
 * Two peels, in the order the words come off the end: the time first, because it
 * is last in "tomorrow at 3pm", then the day from what is left. Each peel hands
 * its candidate to a parser whole, so nothing is extracted from the middle of a
 * phrase and the vocabularies cannot drift.
 *
 * **Only a trailing run is considered**, which is what keeps "Ship the Monday
 * report" untouched: "Monday" is not at the end. That rule is inherited from
 * `date-parsing.md` and is not relaxed here.
 *
 * **A title is never emptied.** A capture that is nothing but a day and a time
 * keeps its words, because "tomorrow at 3pm" on its own is more likely a task
 * someone meant to finish naming than a reminder about nothing. That is the same
 * rule `splitTrailingDate` applies to a bare day, extended to cover the pair.
 *
 * No duration is ever inferred. The board proposed one and D-011 removed it: it
 * is not in the typed text, so it was never parse feedback, and a silent default
 * estimate would erase the no-estimate state `focus.md` designs for.
 */
fun splitTrailingCapture(text: String, today: LocalDate): CapturedTask {
    val words = Word.findAll(text).toList()

    // The time first. Longest-first, so "at 3pm" wins over "3pm" and the mark
    // covers the preposition the user typed rather than orphaning it.
    for (count in minOf(MaxTimeWords, words.size) downTo 1) {
        val start = words[words.size - count].range.first
        val time = parseTimeOfDay(text.substring(start)) ?: continue

        val head = text.substring(0, start)

        // Nothing left to call the task: the whole capture was a time, or a day
        // and a time. It stays a title, unparsed and unmarked.
        if (head.isBlank() || parseDate(head, today) is ParsedDate.Recognized) {
            return plain(text)
        }

        // The day comes off what is left, so "tomorrow at 3pm" resolves both.
        val day = splitTrailingDate(head, today)

        return CapturedTask(
            title = day.title,
            date = day.date,
            time = time,
            dateRange = day.dateStart?.let { at -> at until head.trimEnd().length },
            timeRange = start until text.trimEnd().length
        )
    }

    // No time. The day on its own, which is what Quick Add always did.
    val day = splitTrailingDate(text, today)

    return CapturedTask(
        title = day.title,
        date = day.date,
        time = null,
        dateRange = day.dateStart?.let { at -> at until text.trimEnd().length },
        timeRange = null
    )
}

/** A capture that parsed to nothing: all of it is the title. */
private fun plain(text: String) =
    CapturedTask(text.trim(), date = null, time = null, dateRange = null, timeRange = null)

/**
 * A time of day, or null when [text] is not one.
 *
 * The whole of [text] has to match, exactly as `parseDate` requires, so nothing
 * is extracted from a longer phrase.
 *
 * Understood:
 *
 *     3pm      3 pm     3:30pm    3.30pm
 *     15:00    09:30    at 3pm    at 15:00
 *
 * **Deliberately absent: a bare number.** "7" is not read as seven o'clock, and
 * "Call mum 7" captures a title ending in a seven. A bare hour is far more often
 * part of what someone is writing down — a flat number, a quantity, a version —
 * than a time, and `date-parsing.md`'s rule that nothing is guessed applies with
 * more force here than it does to days, because a wrong reminder is a broken
 * promise in either direction.
 *
 * A 24-hour form needs its separator for the same reason: "1500" stays put.
 *
 * Matched against digits and two English words rather than a locale's own
 * formats, so the vocabulary is identical on every device, which is the rule
 * `date-parsing.md` sets for days. Folded with `Locale.ROOT`, because on a
 * Turkish device the default folds a capital I to a dotless one.
 */
fun parseTimeOfDay(text: String): LocalTime? {
    val cleaned = text.trim().lowercase(Locale.ROOT).removePrefix("at ").trim()

    TwelveHour.matchEntire(cleaned)?.let { match ->
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        if (minute > 59 || hour !in 1..12) return null

        // 12am is midnight and 12pm is noon, which is the one place the
        // twelve-hour clock does not simply add twelve.
        val isMorning = match.groupValues[3] == "am"
        val hourOfDay = when {
            hour == 12 -> if (isMorning) 0 else 12
            else -> if (isMorning) hour else hour + 12
        }

        return LocalTime.of(hourOfDay, minute)
    }

    TwentyFourHour.matchEntire(cleaned)?.let { match ->
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) return null

        return LocalTime.of(hour, minute)
    }

    return null
}

/**
 * An hour, optional minutes, and a required meridiem: "3pm", "3:30 pm".
 *
 * The meridiem is what makes this branch unambiguous, and requiring it is what
 * refuses a bare "7".
 */
private val TwelveHour = Regex("""^(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)$""")

/**
 * A 24-hour time, which needs its separator: "15:00", "09.30".
 *
 * Without one there is nothing to say the digits are a clock rather than a
 * quantity.
 */
private val TwentyFourHour = Regex("""^(\d{1,2})[:.](\d{2})$""")

/** "at 3pm" is the longest time form, so two words is the whole of the reach. */
private const val MaxTimeWords = 2

/**
 * A run of non-space characters.
 *
 * Its own copy rather than `DateParser.kt`'s, which is file-private. Two
 * identical one-line regexes is cheaper than widening that file's visibility,
 * and neither is a rule anyone has to keep in step with the other.
 */
private val Word = Regex("""\S+""")
