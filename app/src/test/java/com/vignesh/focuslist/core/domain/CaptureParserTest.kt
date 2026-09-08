package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Quick Add reading a day and a time, from `docs/decisions.md` D-011.
 *
 * The thing worth testing hardest is what it refuses. D-011 applies the reminder
 * rather than offering it, on the grounds that a reminder not set when one was
 * expected costs the thing being missed, silently. That is only defensible while
 * the parser never invents one, so most of what follows is about text that must
 * stay in the title.
 */
class CaptureParserTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 8)
    private val tomorrow: LocalDate = today.plusDays(1)

    // Nine in the morning, so every 3pm below is still ahead and D-030's
    // forward resolution has nothing to do. The tests that want it to act
    // pass their own later clock, which is what makes them readable.
    private val morning: LocalDateTime = today.atTime(9, 0)

    private fun capture(text: String) = splitTrailingCapture(text, today)

    // --- the three states D-011 draws ----------------------------------------

    @Test
    fun nothingUnderstoodLeavesTheTitleWhole() {
        val result = capture("Call the plumber")

        assertEquals("Call the plumber", result.title)
        assertNull(result.date)
        assertNull(result.time)
        assertNull(result.markRange)
        assertTrue(result.isPlain)
    }

    @Test
    fun aDayUnderstoodSetsTheDateAndNoReminder() {
        val result = capture("Call the plumber tomorrow")

        assertEquals("Call the plumber", result.title)
        assertEquals(tomorrow, result.date)
        assertNull(result.time)
        assertNull(result.reminderAt(today, morning))
    }

    @Test
    fun aDayAndATimeSetBoth() {
        val result = capture("Call the plumber tomorrow at 3pm")

        assertEquals("Call the plumber", result.title)
        assertEquals(tomorrow, result.date)
        assertEquals(LocalTime.of(15, 0), result.time)
        assertEquals(tomorrow.atTime(15, 0), result.reminderAt(today, morning))
    }

    /**
     * A time with no day of its own rides on the capturing screen's date. Today
     * captures for today, so this is a reminder this afternoon.
     */
    @Test
    fun aTimeWithNoDayRidesOnTheCapturingScreensDate() {
        val result = capture("Call the dentist at 3pm")

        assertEquals("Call the dentist", result.title)
        assertNull(result.date)
        assertEquals(today.atTime(15, 0), result.reminderAt(today, morning))
    }

    // --- the mark, which is what the field colours ---------------------------

    @Test
    fun theMarkCoversTheDayAndTheTimeAsOneRun() {
        val text = "Call the plumber tomorrow at 3pm"
        val result = capture(text)

        assertEquals(text.indexOf("tomorrow"), result.markRange!!.first)
        // The mark is one run over the day and the time together.
        assertEquals("tomorrow at 3pm", text.substring(result.markRange!!))
    }

    @Test
    fun theMarkCoversJustTheTimeWhenThereIsNoDay() {
        val text = "Call the dentist at 3pm"
        val result = capture(text)

        assertEquals("at 3pm", text.substring(result.markRange!!))
    }

    @Test
    fun theMarkIsValidAgainstLeadingWhitespace() {
        val text = "   Call the plumber tomorrow"
        val result = capture(text)

        assertEquals("tomorrow", text.substring(result.markRange!!))
    }

    // --- dismissing the chip -------------------------------------------------

    /**
     * D-011 is specific: dismissing drops the reminder **and** unmarks those
     * words, so they stay in the title and the text and the outcome cannot
     * disagree. The earlier design failed exactly this, leaving the field
     * reading "tomorrow at 6" while the task saved without a reminder.
     */
    @Test
    fun dismissingTheChipDropsTheReminderAndKeepsTheDay() {
        val text = "Call the plumber tomorrow at 3pm"
        val dismissed = capture(text).withoutReminder()

        assertNull(dismissed.time)
        assertNull(dismissed.reminderAt(today, morning))
        // The day survives, because only the reminder has a control.
        assertEquals(tomorrow, dismissed.date)
        assertEquals("Call the plumber", dismissed.title)
        // And the mark shrinks to the day alone, so the field stops colouring
        // words that are no longer being taken.
        assertEquals("tomorrow", text.substring(dismissed.markRange!!))
    }

    @Test
    fun dismissingWithNoDayLeavesNothingMarked() {
        val text = "Call the dentist at 3pm"
        val dismissed = capture(text).withoutReminder()

        assertNull(dismissed.time)
        assertNull(dismissed.date)
        assertNull(dismissed.markRange)
        assertTrue(dismissed.isPlain)
    }

    // --- what stays in the title ---------------------------------------------

    /** The trailing-run rule, inherited whole from `date-parsing.md`. */
    @Test
    fun aDayInTheMiddleIsLeftAlone() {
        val result = capture("Ship the Monday report")

        assertEquals("Ship the Monday report", result.title)
        assertNull(result.date)
    }

    @Test
    fun aTimeInTheMiddleIsLeftAlone() {
        val result = capture("Book the 3pm slot")

        assertEquals("Book the 3pm slot", result.title)
        assertNull(result.time)
    }

    /**
     * A bare number is not a time. It is far more often part of what someone is
     * writing down, and a wrong reminder is a broken promise in either
     * direction.
     */
    @Test
    fun aBareNumberIsNotATime() {
        val result = capture("Call mum 7")

        assertEquals("Call mum 7", result.title)
        assertNull(result.time)
    }

    @Test
    fun digitsWithoutASeparatorAreNotATime() {
        val result = capture("Order 1500")

        assertEquals("Order 1500", result.title)
        assertNull(result.time)
    }

    /** A capture that is nothing but a day and a time keeps its words. */
    @Test
    fun aTitleThatIsOnlyADayAndATimeStaysATitle() {
        val result = capture("tomorrow at 3pm")

        assertEquals("tomorrow at 3pm", result.title)
        assertNull(result.date)
        assertNull(result.time)
    }

    @Test
    fun aTitleThatIsOnlyATimeStaysATitle() {
        val result = capture("3pm")

        assertEquals("3pm", result.title)
        assertNull(result.time)
    }

    @Test
    fun anEmptyTitleParsesToNothing() {
        val result = capture("")

        assertEquals("", result.title)
        assertTrue(result.isPlain)
    }

    // --- the time vocabulary --------------------------------------------------

    @Test
    fun theTwelveHourFormsAreUnderstood() {
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("3pm"))
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("3 pm"))
        assertEquals(LocalTime.of(15, 30), parseTimeOfDay("3:30pm"))
        assertEquals(LocalTime.of(15, 30), parseTimeOfDay("3.30pm"))
        assertEquals(LocalTime.of(9, 0), parseTimeOfDay("9am"))
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("at 3pm"))
    }

    @Test
    fun theTwentyFourHourFormsAreUnderstood() {
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("15:00"))
        assertEquals(LocalTime.of(9, 30), parseTimeOfDay("09:30"))
        assertEquals(LocalTime.of(23, 59), parseTimeOfDay("23:59"))
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("at 15:00"))
    }

    /** Noon and midnight are the one place the twelve-hour clock is irregular. */
    @Test
    fun noonAndMidnightAreNotOffByTwelve() {
        assertEquals(LocalTime.of(0, 0), parseTimeOfDay("12am"))
        assertEquals(LocalTime.of(12, 0), parseTimeOfDay("12pm"))
        assertEquals(LocalTime.of(0, 30), parseTimeOfDay("12:30am"))
    }

    @Test
    fun nonsenseIsRefused() {
        assertNull(parseTimeOfDay("13pm"))
        assertNull(parseTimeOfDay("0pm"))
        assertNull(parseTimeOfDay("24:00"))
        assertNull(parseTimeOfDay("15:60"))
        assertNull(parseTimeOfDay("7"))
        assertNull(parseTimeOfDay("1500"))
        assertNull(parseTimeOfDay("tomorrow"))
        assertNull(parseTimeOfDay(""))
    }

    /**
     * Case folds with `Locale.ROOT`, so a Turkish device does not turn a capital
     * I into a dotless one and stop understanding the vocabulary.
     */
    @Test
    fun caseDoesNotMatter() {
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("3PM"))
        assertEquals(LocalTime.of(9, 0), parseTimeOfDay("At 9AM"))
    }

    // --- D-030: a reminder never resolves into the past -----------------------

    /**
     * The defect this fixes, at the door nobody was looking at.
     *
     * "at 3pm" typed at 4pm used to capture a reminder for an hour ago, which
     * the scheduler then clamped to now and rang at once. `date-parsing.md`
     * already promises no supported input resolves to the past; that promise
     * covered the day and not the time riding on it.
     */
    @Test
    fun aTimeAlreadyGoneTodayResolvesToTomorrow() {
        val result = capture("Call the dentist at 3pm")

        assertEquals(
            tomorrow.atTime(15, 0),
            result.reminderAt(today, today.atTime(16, 0))
        )
    }

    /** And is left alone while it is still ahead, which is the common case. */
    @Test
    fun aTimeStillAheadTodayStaysToday() {
        val result = capture("Call the dentist at 3pm")

        assertEquals(
            today.atTime(15, 0),
            result.reminderAt(today, today.atTime(14, 59))
        )
    }

    /**
     * Naming the day does not exempt it, because the words name the task's day
     * and a reminder is independent of it. The Reminder chip reads the resolved
     * day, so a capture that moved says "Tomorrow" before it is saved.
     */
    @Test
    fun anExplicitTodayWithATimeAlreadyGoneAlsoResolvesForward() {
        val result = capture("Call the dentist today at 3pm")

        assertEquals(today, result.date)
        assertEquals(
            tomorrow.atTime(15, 0),
            result.reminderAt(today, today.atTime(16, 0))
        )
    }

    /** A day of its own that is already ahead is untouched whatever the hour. */
    @Test
    fun aNamedFutureDayIsNeverMoved() {
        val result = capture("Call the plumber tomorrow at 3pm")

        assertEquals(
            tomorrow.atTime(15, 0),
            result.reminderAt(today, today.atTime(23, 59))
        )
    }
}
