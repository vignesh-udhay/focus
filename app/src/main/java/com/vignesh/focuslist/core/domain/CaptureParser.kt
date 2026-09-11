package com.vignesh.focuslist.core.domain

import java.time.Duration
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
 * @param offset how long after the moment of typing the title asked to be
 * reminded, or null. D-069. Never set at the same time as [time]: both come off
 * the same single peel, and a capture naming a day cannot carry one at all.
 * @param dateRange where the day sits in the text as it was handed in, or null.
 * Indexes the original string rather than [title], which has been trimmed, so
 * the marking does not drift when there is leading whitespace.
 * @param timeRange where the time sits, on the same terms.
 */
data class CapturedTask(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val offset: Duration?,
    val dateRange: IntRange?,
    val timeRange: IntRange?
) {

    /** Whether anything was understood at all. D-011's first of three states. */
    val isPlain: Boolean
        get() = date == null && time == null && offset == null

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
        // An offset is counted from the moment of typing and names no clock
        // time, so the day it lands on falls out of the arithmetic: "in 2 hours"
        // typed at eleven at night is one in the morning tomorrow. D-069. It
        // needs no forward resolution, because a moment after now is ahead by
        // construction.
        offset?.let { return now.plus(it) }

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
    fun withoutReminder(): CapturedTask =
        copy(time = null, offset = null, timeRange = null)
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
        val candidate = text.substring(start)
        val head = text.substring(0, start)

        // "in 2 hours", which names no clock time and so reads no day. D-069.
        parseRelativeTime(candidate)?.let { offset ->
            // A day and an offset ask for two different moments and neither one
            // is obviously right, so nothing is taken. The same line also holds
            // the rule every other branch keeps: a title is never emptied.
            if (namesADay(head, today)) return plain(text)

            return CapturedTask(
                title = head.trim(),
                date = null,
                time = null,
                offset = offset,
                dateRange = null,
                timeRange = start until text.trimEnd().length
            )
        }

        val time = parseTimeOfDay(candidate) ?: continue

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
            offset = null,
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
        offset = null,
        dateRange = day.dateStart?.let { at -> at until text.trimEnd().length },
        timeRange = null
    )
}

/** A capture that parsed to nothing: all of it is the title. */
private fun plain(text: String) = CapturedTask(
    title = text.trim(),
    date = null,
    time = null,
    offset = null,
    dateRange = null,
    timeRange = null
)

/**
 * Whether [head] is a day, ends in one, or is nothing at all.
 *
 * The three things that stop an offset being read, in one question. Anything a
 * day could be hiding in makes "in 2 hours" contradictory or leaves the capture
 * with no title, and D-069 takes nothing in either case.
 */
private fun namesADay(head: String, today: LocalDate): Boolean =
    head.isBlank() ||
        parseDate(head, today) is ParsedDate.Recognized ||
        splitTrailingDate(head, today).date != null

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
 *     noon     midday   midnight  tonight   this evening
 *     six pm   at seven am        6 p.m.
 *
 * The five words carry no digits and are the ordinary way people say these
 * hours, D-069. [WordTimes] fixes what the two that need a number chosen mean.
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
    val cleaned = text.trim().lowercase(Locale.ROOT)
        // One sentence terminator, at the end only. Pixel voice typing
        // punctuates what it hears, D-070, and "6 pm." used to be refused. The
        // dot in "3.30pm" is inside the phrase and is untouched.
        .removeSuffix(".").removeSuffix("!").removeSuffix("?")
        // Some recognizers punctuate the abbreviation itself. Dotless in the
        // pattern, because the line above has already taken the final dot off
        // "6 p.m." and what is left to fold is "p.m".
        .replace("p.m", "pm").replace("a.m", "am")
        .removePrefix("at ").trim()

    WordTimes[cleaned]?.let { return it }

    // "six pm", D-070: an hour said as a word, still needing its meridiem. The
    // meridiem is what makes the hour unambiguous, exactly as with digits, so
    // "at six" stays refused: nothing in it says which of the two sixes.
    SpokenHour.matchEntire(cleaned)?.let { match ->
        val hour = spokenNumber(match.groupValues[1]) ?: return@let
        if (hour !in 1..12) return@let

        val isMorning = match.groupValues[2] == "am"
        val hourOfDay = when {
            hour == 12L -> if (isMorning) 0 else 12
            else -> if (isMorning) hour.toInt() else hour.toInt() + 12
        }

        return LocalTime.of(hourOfDay, 0)
    }

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
        val hourDigits = match.groupValues[1]
        val hour = hourDigits.toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) return null

        // A single-digit hour is not a 24-hour time anyone wrote, D-070's
        // addendum. Dictating "tomorrow at six" put "6:00" in the field, this
        // branch read it as six in the morning, and a 6am reminder for a 6pm
        // intention was measured on a real phone. A 24-hour typist writes
        // "06:00" or "18:00"; "6:00" is a spoken hour wearing digits, and it is
        // as ambiguous as "at six", so it gets the same answer. Refused, not
        // guessed.
        if (hourDigits.length < 2) return null

        return LocalTime.of(hour, minute)
    }

    return null
}

/**
 * The times people say without digits, D-069.
 *
 * Noon and midnight are not decisions. The other two are: "tonight" is eight in
 * the evening and "this evening" is six. Eight is late enough to read as the
 * evening rather than the end of the afternoon, and early enough that a reminder
 * still lands while someone is up; six keeps the two phrases apart, which they
 * are in use, because "this evening" is when the day's work stops and "tonight"
 * is after it.
 *
 * A word whose hour has gone by rolls to tomorrow like any other time, through
 * the same resolution D-030 applies, and the Reminder chip names the day it
 * landed on before anything is saved.
 */
private val WordTimes: Map<String, LocalTime> = mapOf(
    "noon" to LocalTime.NOON,
    "midday" to LocalTime.NOON,
    "midnight" to LocalTime.MIDNIGHT,
    "tonight" to LocalTime.of(20, 0),
    "this evening" to LocalTime.of(18, 0)
)

/**
 * How long after now the title asked to be reminded: "in 2 hours", "in 30
 * minutes". Null when [text] is not one of those.
 *
 * D-069. The clock forms cannot say this, and neither can the user, who does not
 * know what time it will be in two hours without working it out. Quick Add
 * exists to avoid exactly that arithmetic.
 *
 * **Number words as well as digits, since D-070.** "in two hours", "in thirty
 * minutes" and "in an hour" all read, because a speech recognizer writes the
 * words whether or not the vocabulary accepts them, and the day half accepts
 * the same table, so the branches stay learnable as one rule. "in a couple of
 * hours" is still refused: the table names numbers, not quantities.
 *
 * **At least one of whatever it counts.** "in 0 minutes" is refused rather than
 * set for the current instant, because a reminder that fires as the sheet closes
 * is not an interruption anyone asked for.
 *
 * Split on words rather than matched with a regex, which is what keeps the two
 * units and the numeral visible in one place.
 */
fun parseRelativeTime(text: String): Duration? {
    val words = text.trim().lowercase(Locale.ROOT)
        .removeSuffix(".").removeSuffix("!").removeSuffix("?")
        .split(" ").filter { it.isNotEmpty() }
    if (words.size != 3 || words[0] != "in") return null

    val amount = words[1].toLongOrNull() ?: spokenNumber(words[1]) ?: return null
    if (amount < 1) return null

    return when (words[2]) {
        "hour", "hours" -> runCatching { Duration.ofHours(amount) }.getOrNull()
        "minute", "minutes" -> runCatching { Duration.ofMinutes(amount) }.getOrNull()
        else -> null
    }
}

/**
 * An hour, optional minutes, and a required meridiem: "3pm", "3:30 pm".
 *
 * The meridiem is what makes this branch unambiguous, and requiring it is what
 * refuses a bare "7".
 */
private val TwelveHour = Regex("""^(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)$""")

/** An hour word and its meridiem, D-070: "six pm", "twelve am". */
private val SpokenHour = Regex("([a-z]+) ?(am|pm)")

/**
 * A 24-hour time, which needs its separator and both hour digits: "15:00",
 * "09.30".
 *
 * Without the separator there is nothing to say the digits are a clock rather
 * than a quantity. Without the second hour digit there is nothing to say which
 * half of the day is meant: speech recognizers write a dictated "at six" as
 * "6:00", and the branch reading that as 06:00 stored a measured 6am reminder
 * for a 6pm intention. The regex still admits one digit so the case lands here
 * and is refused for its length rather than half-matching elsewhere.
 */
private val TwentyFourHour = Regex("""^(\d{1,2})[:.](\d{2})$""")

/**
 * "at 10 am" is the longest time form, so three words is the whole of the reach.
 *
 * Two, once, on the reasoning that "at 3pm" was the longest. It is not: a
 * meridiem written apart from its hour is a word of its own. The peel matched
 * the trailing "10 am" and left the preposition at the end of the head, where
 * it shadowed any day standing in front of it, because [parseDate] matches
 * whole candidates and "tomorrow at" is not one. "Call the guy tomorrow at
 * 10 am" captured the hour and lost the day.
 *
 * Three cannot over-reach. The peel is longest-first and every candidate has to
 * match [parseTimeOfDay] entire, so the only three-word span that can match is
 * a preposition, an hour and a meridiem. "at 3pm" still wins at two.
 */
private const val MaxTimeWords = 3

/**
 * A run of non-space characters.
 *
 * Its own copy rather than `DateParser.kt`'s, which is file-private. Two
 * identical one-line regexes is cheaper than widening that file's visibility,
 * and neither is a rule anyone has to keep in step with the other.
 */
private val Word = Regex("""\S+""")
