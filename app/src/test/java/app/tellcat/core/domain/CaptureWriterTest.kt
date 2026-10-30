package app.tellcat.core.domain

import app.tellcat.ui.task.suggestedReminderTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Quick Add's Reminder chip writing its choice into the field, `docs/decisions.md`
 * D-112.
 *
 * The promise worth testing hardest is the round trip: whatever the chip writes,
 * the field has to read back as exactly the moment chosen and leave the title as
 * it was. If it read back as anything else, the chip and the saved reminder
 * would disagree, which is the failure D-011 exists to prevent.
 */
class CaptureWriterTest {

    // A Friday, so the week ahead runs Saturday to the next Friday.
    private val today: LocalDate = LocalDate.of(2026, 10, 30)
    private val morning: LocalDateTime = today.atTime(9, 15)

    private fun write(
        text: String,
        at: LocalDateTime,
        now: LocalDateTime = morning,
        twentyFourHour: Boolean = false,
        dayFirst: Boolean = true,
        marked: IntRange? = splitTrailingCapture(text, today).markRange
    ) = writeReminder(text, marked, at, today, now, twentyFourHour, dayFirst)

    /** Asserts [text] reads back as [title] with a reminder at [at]. */
    private fun assertReadsBack(text: String, title: String, at: LocalDateTime, now: LocalDateTime) {
        val read = splitTrailingCapture(text, today)
        assertEquals(text, title, read.title)
        assertEquals(text, at, read.reminderAt(today, now))
    }

    // --- the round trip, for everything the reminder sheet can hand over -------

    /**
     * Every day the sheet's Day pane can give, today, tomorrow and any date from
     * the picker, at the times its Time row opens on and a spread the picker can
     * reach, on both clocks and both date orders, after the titles most likely
     * to trip the parser up.
     */
    @Test
    fun everyChoiceTheSheetCanMakeReadsBackAsExactlyThatMoment() {
        val days = listOf(0L, 1, 2, 3, 6, 7, 8, 13, 30, 61, 200, 364, 365, 400, 800).map(today::plusDays)
        val clocks = listOf(today.atTime(9, 15), today.atTime(17, 40), today.atTime(23, 30))
        val titles = listOf(
            "Call the plumber",
            // Ends in a preposition the day peel would take: "on tomorrow".
            "Pass it on",
            // Ends in a day of its own, which a bare "at 9am" would join.
            "Ship monday",
            // Ends in a month and day.
            "Pay june 5",
            // Ends in the time's own preposition.
            "Meet at",
            "Room 4"
        )

        for (now in clocks) {
            val times = listOf(
                // Where the Time row opens: the next whole hour today, and nine
                // on any other day. D-071.
                suggestedReminderTime(today, now),
                suggestedReminderTime(today.plusDays(1), now),
                LocalTime.MIDNIGHT, LocalTime.of(0, 30), LocalTime.of(11, 59),
                LocalTime.NOON, LocalTime.of(12, 1), LocalTime.of(21, 45), LocalTime.of(23, 59)
            )
            for (day in days) for (time in times) {
                val at = day.atTime(time)
                // The sheet never hands over a moment already gone: a day it
                // worked out moves forward, a day the user chose disables Save.
                if (at.isBefore(now.withSecond(0).withNano(0))) continue

                for (title in titles) for (twentyFourHour in listOf(false, true)) for (dayFirst in listOf(true, false)) {
                    val case = "$title, $at, 24h=$twentyFourHour, dayFirst=$dayFirst"

                    // As the field does it: a day it marks at the end is the
                    // run the reminder replaces, so "Ship monday" becomes "Ship".
                    val marked = splitTrailingCapture(title, today).markRange
                    val kept = marked?.let { run -> title.substring(0, run.first).trim() } ?: title
                    val written = write(title, at, now, twentyFourHour, dayFirst, marked)
                    assertNotNull(case, written)
                    assertReadsBack(written!!.text, kept, at, now)

                    // And with nothing marked, which is the parser's hardest
                    // case: every word of the title has to survive what is
                    // written after it.
                    val appended = write(title, at, now, twentyFourHour, dayFirst, marked = null)
                    assertNotNull("$case, unmarked", appended)
                    assertReadsBack(appended!!.text, title, at, now)
                }
            }
        }
    }

    // --- what it writes ------------------------------------------------------

    @Test
    fun aTimeLaterTodayIsWrittenAsTheTimeAlone() {
        val written = write("Call the plumber", today.atTime(17, 0))
        assertEquals("Call the plumber at 5pm", written?.text)
        assertEquals("at 5pm", written?.phrase)
    }

    @Test
    fun tomorrowIsWrittenAsTomorrow() {
        assertEquals("Call the plumber tomorrow at 9am", write("Call the plumber", today.plusDays(1).atTime(9, 0))?.text)
    }

    @Test
    fun theRestOfTheWeekIsWrittenAsTheDaysName() {
        // Two days out to seven, the reach of "on Friday".
        assertEquals("Call the plumber on Sunday at 9am", write("Call the plumber", today.plusDays(2).atTime(9, 0))?.text)
        assertEquals("Call the plumber on Friday at 9am", write("Call the plumber", today.plusDays(7).atTime(9, 0))?.text)
    }

    @Test
    fun furtherOutIsWrittenAsTheDateInThePhonesOrder() {
        val nextWeek = today.plusDays(8).atTime(9, 0)
        assertEquals("Call the plumber on 7 November at 9am", write("Call the plumber", nextWeek, dayFirst = true)?.text)
        assertEquals("Call the plumber on November 7 at 9am", write("Call the plumber", nextWeek, dayFirst = false)?.text)
    }

    @Test
    fun aDateBeyondItsNextOccurrenceCarriesItsYear() {
        val twoYears = LocalDate.of(2028, 11, 7).atTime(9, 0)
        assertEquals("Call the plumber on 7 November 2028 at 9am", write("Call the plumber", twoYears)?.text)
    }

    @Test
    fun aTwentyFourHourPhoneGetsTwoHourDigits() {
        // "9:00" is refused on purpose, D-070, so the hour is always two digits.
        assertEquals("Call the plumber tomorrow at 09:00", write("Call the plumber", today.plusDays(1).atTime(9, 0), twentyFourHour = true)?.text)
        assertEquals("Call the plumber at 21:30", write("Call the plumber", today.atTime(21, 30), twentyFourHour = true)?.text)
    }

    /** "Pass it on tomorrow" reads as "on tomorrow" and costs the title a word. */
    @Test
    fun aShortFormTheParserWouldMisreadGivesWayToTheDate() {
        val written = write("Pass it on", today.plusDays(1).atTime(9, 0))
        assertEquals("Pass it on on 31 October at 9am", written?.text)
    }

    /**
     * "Ship monday at 5pm" would be Monday's reminder, not today's. The writer
     * checks the parse rather than trusting the mark, so with the day's words
     * kept in the title, today is said out loud.
     */
    @Test
    fun aTitleEndingInADayGetsTodaySaidOutLoud() {
        val written = write("Ship monday", today.atTime(17, 0), marked = null)
        assertEquals("Ship monday today at 5pm", written?.text)
    }

    /** The day the field marks is the run a chosen reminder replaces. */
    @Test
    fun aMarkedDayIsReplacedByTheReminderChosen() {
        assertEquals("Ship at 5pm", write("Ship monday", today.atTime(17, 0))?.text)
    }

    // --- editing what the field already holds -------------------------------

    @Test
    fun aReminderAlreadyTypedIsReplacedNotJoined() {
        val written = write("Call mum tomorrow at 3pm", today.plusDays(3).atTime(9, 0))
        assertEquals("Call mum on Monday at 9am", written?.text)
    }

    @Test
    fun aDayAlreadyTypedIsReplacedByTheReminder() {
        val written = write("Call mum tomorrow", today.plusDays(1).atTime(9, 0))
        assertEquals("Call mum tomorrow at 9am", written?.text)
    }

    @Test
    fun anOffsetAlreadyTypedIsReplaced() {
        val written = write("Call mum in 2 hours", today.atTime(17, 0))
        assertEquals("Call mum at 5pm", written?.text)
    }

    /**
     * A time dismissed with the chip's cross is no longer marked, so its words
     * belong to the title now and the new reminder goes after them.
     */
    @Test
    fun aDismissedTimeKeepsItsWordsAndTheReminderGoesAfter() {
        val dismissed = splitTrailingCapture("Call mum at 3pm", today).withoutReminder()
        val written = write("Call mum at 3pm", today.atTime(17, 0), marked = dismissed.markRange)

        assertEquals("Call mum at 3pm at 5pm", written?.text)
        assertReadsBack(written!!.text, "Call mum at 3pm", today.atTime(17, 0), morning)
    }

    /** A dismissed time after a typed day: the day stays marked but is not at the end. */
    @Test
    fun aMarkedDayBeforeADismissedTimeIsLeftAlone() {
        val dismissed = splitTrailingCapture("Call mum tomorrow at 3pm", today).withoutReminder()
        val written = write("Call mum tomorrow at 3pm", today.atTime(17, 0), marked = dismissed.markRange)

        assertReadsBack(written!!.text, "Call mum tomorrow at 3pm", today.atTime(17, 0), morning)
    }

    // --- what it will not do -------------------------------------------------

    @Test
    fun nothingIsWrittenWithoutATitle() {
        assertNull(write("", today.atTime(17, 0)))
        assertNull(write("   ", today.atTime(17, 0)))
        // A field that is only a day and a time is a title, not a reminder.
        assertNull(write("tomorrow at 3pm", today.atTime(17, 0), marked = 0..14))
    }

    @Test
    fun theMomentIsTakenToTheMinute() {
        val written = write("Call mum", today.atTime(17, 0, 42))
        assertReadsBack(written!!.text, "Call mum", today.atTime(17, 0), morning)
    }

    /**
     * D-030: a time already gone today is tomorrow's. The sheet hands over the
     * moment it resolved to, and the field says the day out loud.
     */
    @Test
    fun aTimeGoneByTodayIsWrittenForTheDayItRings() {
        val written = write("Call mum", today.plusDays(1).atTime(8, 0))
        assertEquals("Call mum tomorrow at 8am", written?.text)
    }

    // --- the time words ------------------------------------------------------

    /** Every minute of the day, on both clocks, reads back as itself. */
    @Test
    fun everyMinuteOfTheDayReadsBack() {
        for (minute in 0 until 24 * 60) {
            val time = LocalTime.of(minute / 60, minute % 60)
            for (twentyFourHour in listOf(false, true)) {
                val words = reminderTimeWords(time, twentyFourHour)
                assertEquals(words, time, parseTimeOfDay("at $words"))
            }
        }
    }

    @Test
    fun noonAndMidnightAreSaidAsTwelve() {
        assertEquals("12pm", reminderTimeWords(LocalTime.NOON, twentyFourHour = false))
        assertEquals("12am", reminderTimeWords(LocalTime.MIDNIGHT, twentyFourHour = false))
        assertEquals("00:00", reminderTimeWords(LocalTime.MIDNIGHT, twentyFourHour = true))
    }

    /** The placeholder's example is itself a capture the field reads. */
    @Test
    fun thePlaceholdersExampleReadsAsTomorrowAtNine() {
        for (twentyFourHour in listOf(false, true)) {
            val example = "Call the plumber tomorrow at ${reminderTimeWords(LocalTime.of(9, 0), twentyFourHour)}"
            assertReadsBack(example, "Call the plumber", today.plusDays(1).atTime(9, 0), morning)
        }
    }
}
