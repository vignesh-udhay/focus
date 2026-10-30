package app.tellcat.core.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Writing a reminder into a Quick Add title, as words the field reads back.
 * `docs/decisions.md` D-112.
 *
 * Quick Add's Reminder chip opens the reminder sheet, and what the user chooses
 * there is typed into the field rather than kept beside it. The field stays the
 * only thing that sets a reminder (D-011's one mechanism), and the words it gains
 * are the syntax the user could have typed, so the chip teaches it as it goes.
 *
 * **The inverse of [splitTrailingCapture], checked against it.** Every phrase is
 * handed back to the parser before it is written, and kept only if it reads as
 * exactly the moment chosen and leaves the title as it was. A vocabulary written
 * here and a vocabulary read there could otherwise drift apart, and the first
 * sign would be a reminder at the wrong time. Here rather than in `ui` for the
 * same reason: it is the parser's vocabulary, English on every device, not
 * wording a screen chooses.
 */

/** A reminder written into a title: the whole new text, and the words added. */
data class WrittenReminder(val text: String, val phrase: String)

/**
 * [text] with a reminder for [at] written on the end, or null when no phrase the
 * parser reads would say it.
 *
 * [marked] is the run the field marks now. When it reaches the end of the text
 * it is replaced, so choosing again edits the day and time already there rather
 * than adding a second pair. A run that stops short of the end is left alone:
 * that is a time the user dismissed with the chip, D-011, and whose words they
 * chose to keep in the title.
 *
 * Phrases are tried shortest first: "at 5pm" for today, then "tomorrow at 9am",
 * "on Friday at 9am" for the rest of the week, then the date itself. A shorter
 * form loses only when the parser would read it differently, which happens when
 * the title already ends in something it takes: "Pass it on" plus "tomorrow" is
 * read as "on tomorrow" and costs the title its last word, so the date is
 * written instead.
 *
 * The moment is taken to the minute, which is what a reminder is chosen to.
 * [now] is the clock the sheet resolves against, so a time already gone today
 * fails the check rather than being written for tomorrow without saying so.
 *
 * @param twentyFourHour how the phone writes times, so the field shows "21:00"
 * on a phone that reads it that way, and "9pm" on one that does not.
 * @param dayFirst how the phone orders a day and month, "6 November" or
 * "November 6". The parser reads both.
 */
fun writeReminder(
    text: String,
    marked: IntRange?,
    at: LocalDateTime,
    today: LocalDate,
    now: LocalDateTime,
    twentyFourHour: Boolean,
    dayFirst: Boolean
): WrittenReminder? {
    val target = at.truncatedTo(ChronoUnit.MINUTES)
    val end = text.trimEnd().length
    val run = marked?.takeIf { it.last == end - 1 }
    val base = (if (run != null) text.substring(0, run.first) else text).trimEnd()

    // A reminder about nothing is not written: a title is never emptied, and a
    // field holding only a day and a time is read as a title, not a reminder.
    if (base.isBlank()) return null

    val time = reminderTimeWords(target.toLocalTime(), twentyFourHour)

    for (day in dayWords(target.toLocalDate(), today, dayFirst)) {
        val phrase = if (day == null) "at $time" else "$day at $time"
        val written = "$base $phrase"
        val read = splitTrailingCapture(written, today)

        if (read.title == base.trim() && read.reminderAt(today, now) == target) {
            return WrittenReminder(written, phrase)
        }
    }

    return null
}

/**
 * A time of day as the parser reads it: "9am", "9:30pm", "12am" on a 12-hour
 * phone, "09:00" and "21:30" on a 24-hour one.
 *
 * Two hour digits in the 24-hour form, always. `parseTimeOfDay` refuses "9:00"
 * on purpose, D-070, because a speech recognizer writes a spoken "nine" that way
 * and nothing in it says which nine.
 *
 * Also what the placeholder shows, so the example the sheet teaches is the form
 * this phone would write.
 */
fun reminderTimeWords(time: LocalTime, twentyFourHour: Boolean): String {
    if (twentyFourHour) {
        return String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)
    }

    val hour = (time.hour % 12).let { if (it == 0) 12 else it }
    val meridiem = if (time.hour < 12) "am" else "pm"

    return if (time.minute == 0) {
        "$hour$meridiem"
    } else {
        String.format(Locale.ROOT, "%d:%02d%s", hour, time.minute, meridiem)
    }
}

/**
 * The ways to say [day], best first. Null stands for no day word at all: a time
 * on its own is read as today's.
 *
 * A weekday reaches the next seven days, because `parseDate` reads "Friday" as
 * the next Friday strictly ahead. The date is always last, without its year
 * while the next time that date comes round is this one, and with it otherwise.
 */
private fun dayWords(day: LocalDate, today: LocalDate, dayFirst: Boolean): List<String?> = buildList {
    when (day) {
        today -> {
            add(null)
            add("today")
        }

        today.plusDays(1) -> add("tomorrow")

        else -> if (day.isAfter(today) && !day.isAfter(today.plusDays(7))) {
            add("on " + day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        }
    }

    val month = day.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val monthAndDay = if (dayFirst) "${day.dayOfMonth} $month" else "$month ${day.dayOfMonth}"
    add("on $monthAndDay")
    add("on $monthAndDay ${day.year}")
}
