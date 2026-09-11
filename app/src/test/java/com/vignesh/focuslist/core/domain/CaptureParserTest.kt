package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
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

    /**
     * The meridiem written apart from its hour, which is one word more than the
     * time peel used to reach for.
     *
     * "at 3pm" is two words and "at 10 am" is three, so the peel matched the
     * trailing "10 am" and left the preposition at the end of the head. Nothing
     * there is a date, and `parseDate` only ever matches a whole candidate, so
     * the stranded "at" shadowed the day standing directly in front of it. The
     * hour was captured and tomorrow was lost.
     *
     * The two spellings of ten in the morning have to reach the same capture,
     * which is the whole of the claim here.
     */
    @Test
    fun aSpacedMeridiemDoesNotShadowTheDay() {
        val text = "Call the guy tomorrow at 10 am"
        val result = capture(text)

        assertEquals("Call the guy", result.title)
        assertEquals(tomorrow, result.date)
        assertEquals(LocalTime.of(10, 0), result.time)
        assertEquals(tomorrow.atTime(10, 0), result.reminderAt(today, morning))
        assertEquals("tomorrow at 10 am", text.substring(result.markRange!!))
    }

    /**
     * The same bug with no day involved, which is the half a reader is likelier
     * to see first: the title kept a dangling "at" and the mark started after
     * it, against the rule that the mark covers the preposition the user typed.
     */
    @Test
    fun aSpacedMeridiemMarksThePrepositionRatherThanOrphaningIt() {
        val text = "Call the dentist at 10 am"
        val result = capture(text)

        assertEquals("Call the dentist", result.title)
        assertEquals(today.atTime(10, 0), result.reminderAt(today, morning))
        assertEquals("at 10 am", text.substring(result.markRange!!))
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

    // --- the times with no digits in them, D-069 -----------------------------

    @Test
    fun theWordTimesAreUnderstood() {
        assertEquals(LocalTime.of(12, 0), parseTimeOfDay("noon"))
        assertEquals(LocalTime.of(12, 0), parseTimeOfDay("midday"))
        assertEquals(LocalTime.of(0, 0), parseTimeOfDay("midnight"))
        assertEquals(LocalTime.of(20, 0), parseTimeOfDay("tonight"))
        assertEquals(LocalTime.of(18, 0), parseTimeOfDay("this evening"))
        assertEquals(LocalTime.of(12, 0), parseTimeOfDay("at noon"))
        assertEquals(LocalTime.of(20, 0), parseTimeOfDay("TONIGHT"))
    }

    @Test
    fun aWordTimeSetsAReminderOnTheCapturingScreensDay() {
        val text = "Call the plumber tonight"
        val result = capture(text)

        assertEquals("Call the plumber", result.title)
        assertEquals(LocalTime.of(20, 0), result.time)
        assertEquals(today.atTime(20, 0), result.reminderAt(today, morning))
        assertEquals("tonight", text.substring(result.markRange!!))
    }

    @Test
    fun aTwoWordTimeIsReadWhole() {
        val text = "Water the plants this evening"
        val result = capture(text)

        assertEquals("Water the plants", result.title)
        assertEquals(LocalTime.of(18, 0), result.time)
        assertEquals("this evening", text.substring(result.markRange!!))
    }

    /** The same forward resolution D-030 gives every other time. */
    @Test
    fun aWordTimeAlreadyGoneResolvesToTomorrow() {
        val result = capture("Call the plumber tonight")

        assertEquals(
            tomorrow.atTime(20, 0),
            result.reminderAt(today, today.atTime(21, 0))
        )
    }

    // --- a reminder counted from now, D-069 ----------------------------------

    @Test
    fun theRelativeFormsAreUnderstood() {
        assertEquals(Duration.ofHours(2), parseRelativeTime("in 2 hours"))
        assertEquals(Duration.ofHours(1), parseRelativeTime("in 1 hour"))
        assertEquals(Duration.ofMinutes(30), parseRelativeTime("in 30 minutes"))
        assertEquals(Duration.ofMinutes(1), parseRelativeTime("in 1 minute"))
        assertEquals(Duration.ofHours(2), parseRelativeTime("IN 2 HOURS"))
    }

    @Test
    fun theRelativeFormsRefuseEverythingElse() {
        // Quantities and fractions, which the number table does not name.
        // D-070 made "in an hour" parse; these still do not.
        assertNull(parseRelativeTime("in half an hour"))
        // A reminder that fires as the sheet closes is not an interruption.
        assertNull(parseRelativeTime("in 0 minutes"))
        assertNull(parseRelativeTime("in -1 hours"))
        // Units the day half owns, which must keep resolving to a date.
        assertNull(parseRelativeTime("in 3 days"))
        assertNull(parseRelativeTime("in 2 weeks"))
        assertNull(parseRelativeTime("2 hours"))
        assertNull(parseRelativeTime("in 2 hours time"))
        assertNull(parseRelativeTime(""))
    }

    @Test
    fun anOffsetSetsAReminderCountedFromTheMomentOfTyping() {
        val text = "Call the plumber in 2 hours"
        val result = capture(text)

        assertEquals("Call the plumber", result.title)
        assertEquals(Duration.ofHours(2), result.offset)
        assertNull(result.time)
        assertNull(result.date)
        assertEquals(today.atTime(11, 0), result.reminderAt(today, morning))
        assertEquals("in 2 hours", text.substring(result.markRange!!))
    }

    @Test
    fun anOffsetInMinutesIsRead() {
        val result = capture("Water the plants in 30 minutes")

        assertEquals("Water the plants", result.title)
        assertEquals(Duration.ofMinutes(30), result.offset)
        assertEquals(today.atTime(9, 30), result.reminderAt(today, morning))
    }

    /** The day falls out of the arithmetic rather than being decided. */
    @Test
    fun anOffsetCrossingMidnightLandsOnTheNextDay() {
        val result = capture("Call the plumber in 2 hours")

        assertEquals(
            tomorrow.atTime(1, 0),
            result.reminderAt(today, today.atTime(23, 0))
        )
    }

    /** Two different moments asked for at once, so neither is taken. */
    @Test
    fun aDayAndAnOffsetTogetherParseToNothing() {
        val text = "Call the plumber tomorrow in 2 hours"
        val result = capture(text)

        assertEquals(text, result.title)
        assertTrue(result.isPlain)
        assertNull(result.markRange)
    }

    @Test
    fun aTitleThatIsOnlyAnOffsetStaysATitle() {
        val result = capture("in 2 hours")

        assertEquals("in 2 hours", result.title)
        assertNull(result.offset)
        assertTrue(result.isPlain)
    }

    /** The day half still owns days, which the offset must not shadow. */
    @Test
    fun inThreeDaysIsStillADateRatherThanAnOffset() {
        val result = capture("Call the plumber in 3 days")

        assertEquals("Call the plumber", result.title)
        assertEquals(today.plusDays(3), result.date)
        assertNull(result.offset)
        assertNull(result.reminderAt(today, morning))
    }

    @Test
    fun dismissingTheChipDropsAnOffsetToo() {
        val dismissed = capture("Call the plumber in 2 hours").withoutReminder()

        assertNull(dismissed.offset)
        assertNull(dismissed.reminderAt(today, morning))
        assertNull(dismissed.markRange)
        assertTrue(dismissed.isPlain)
        assertEquals("Call the plumber", dismissed.title)
    }

    // --- a day introduced by a preposition, D-069 ----------------------------

    @Test
    fun aPrepositionIsTakenWithTheDayAndTheTimeStillReads() {
        val text = "Call the plumber on friday at 3pm"
        val result = capture(text)

        assertEquals("Call the plumber", result.title)
        assertEquals(LocalDate.of(2026, 9, 11), result.date)
        assertEquals(LocalTime.of(15, 0), result.time)
        assertEquals("on friday at 3pm", text.substring(result.markRange!!))
    }


    // --- transcript forms, D-070 ---------------------------------------------

    @Test
    fun anHourSaidAsAWordIsUnderstoodWithItsMeridiem() {
        assertEquals(LocalTime.of(18, 0), parseTimeOfDay("six pm"))
        assertEquals(LocalTime.of(7, 0), parseTimeOfDay("at seven am"))
        assertEquals(LocalTime.of(12, 0), parseTimeOfDay("twelve pm"))
        assertEquals(LocalTime.of(0, 0), parseTimeOfDay("twelve am"))
    }

    /** The meridiem is still what makes an hour unambiguous, D-070. */
    @Test
    fun anHourWordWithoutAMeridiemIsRefused(): Unit {
        assertNull(parseTimeOfDay("six"))
        assertNull(parseTimeOfDay("at six"))
        // A word that is a number but not an hour on any clock.
        assertNull(parseTimeOfDay("twenty pm"))
        assertNull(parseTimeOfDay("fifteen pm"))
    }

    @Test
    fun aPunctuatedMeridiemIsFolded() {
        assertEquals(LocalTime.of(18, 0), parseTimeOfDay("6 p.m."))
        assertEquals(LocalTime.of(6, 0), parseTimeOfDay("6 a.m."))
        assertEquals(LocalTime.of(18, 0), parseTimeOfDay("at 6 p.m."))
    }

    @Test
    fun aTrailingSentenceTerminatorIsDroppedFromATime() {
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("3pm."))
        assertEquals(LocalTime.of(15, 0), parseTimeOfDay("15:00."))
        assertEquals(LocalTime.of(20, 0), parseTimeOfDay("tonight."))
        // The dot in "3.30pm" is a separator and is untouched.
        assertEquals(LocalTime.of(15, 30), parseTimeOfDay("3.30pm."))
        // A bare number stays refused whatever punctuation follows it.
        assertNull(parseTimeOfDay("7."))
    }

    @Test
    fun anOffsetAmountSaidAsAWordIsUnderstood() {
        assertEquals(Duration.ofHours(2), parseRelativeTime("in two hours"))
        assertEquals(Duration.ofMinutes(30), parseRelativeTime("in thirty minutes"))
        assertEquals(Duration.ofHours(1), parseRelativeTime("in an hour"))
        assertEquals(Duration.ofMinutes(1), parseRelativeTime("in a minute"))
        assertEquals(Duration.ofMinutes(45), parseRelativeTime("in forty-five minutes"))
        assertEquals(Duration.ofHours(2), parseRelativeTime("in two hours."))
    }

    @Test
    fun aQuantityIsStillNotANumber() {
        assertNull(parseRelativeTime("in a couple of hours"))
        assertNull(parseRelativeTime("in few minutes"))
        assertNull(parseRelativeTime("in some hours"))
    }

    /** The sentence a recognizer actually writes, end to end. */
    @Test
    fun aPunctuatedTranscriptCapturesWhole() {
        val text = "Call mum tomorrow at 6 p.m."
        val result = capture(text)

        assertEquals("Call mum", result.title)
        assertEquals(tomorrow, result.date)
        assertEquals(LocalTime.of(18, 0), result.time)
        assertEquals("tomorrow at 6 p.m.", text.substring(result.markRange!!))
    }

    @Test
    fun aSpokenHourWordCapturesWithItsDay(): Unit {
        val text = "Call mum tomorrow at six pm"
        val result = capture(text)

        assertEquals("Call mum", result.title)
        assertEquals(tomorrow, result.date)
        assertEquals(LocalTime.of(18, 0), result.time)
        assertEquals("tomorrow at six pm", text.substring(result.markRange!!))
    }

    @Test
    fun aSpokenOffsetSentenceKeepsItsPreambleInTheTitle() {
        val result = capture("Remind me to take medicine in two hours")

        // The preamble stays, deliberately: stripping it is a rewrite of the
        // start of the title, where the mark cannot show it. D-070.
        assertEquals("Remind me to take medicine", result.title)
        assertEquals(Duration.ofHours(2), result.offset)
        assertEquals(today.atTime(11, 0), result.reminderAt(today, morning))
    }

    @Test
    fun aBareAnchoredHourStaysInTheTitle() {
        val result = capture("Call mum at six")

        assertEquals("Call mum at six", result.title)
        assertTrue(result.isPlain)
    }


    /**
     * Measured on a real phone, D-070's addendum: dictating "tomorrow at six"
     * put "6:00" in the field, and reading it as 06:00 stored a 6am reminder
     * for a 6pm intention. A single-digit hour with a separator and no meridiem
     * is a spoken hour wearing digits, and it is refused on the same terms as
     * "at six". A 24-hour typist writes both digits.
     */
    @Test
    fun aSingleDigitHourWithASeparatorIsAmbiguousAndRefused(): Unit {
        assertNull(parseTimeOfDay("6:00"))
        assertNull(parseTimeOfDay("at 6:00"))
        assertNull(parseTimeOfDay("6:30"))
        assertNull(parseTimeOfDay("9.15"))
        // Both digits say which half of the day is meant.
        assertEquals(LocalTime.of(6, 0), parseTimeOfDay("06:00"))
        assertEquals(LocalTime.of(18, 0), parseTimeOfDay("18:00"))
        // A meridiem also says, whatever the hour's width.
        assertEquals(LocalTime.of(18, 0), parseTimeOfDay("6:00pm"))
    }

    @Test
    fun theMeasuredSixAmCaptureNowStaysInTheTitle() {
        val result = capture("Call mum tomorrow at 6:00")

        // Everything stays, day included: the refused hour makes the trailing
        // run not a date, and nothing is extracted from the middle of one. No
        // mark, so the field says nothing was understood, which is true.
        assertEquals("Call mum tomorrow at 6:00", result.title)
        assertTrue(result.isPlain)
        assertNull(result.markRange)
    }

}
