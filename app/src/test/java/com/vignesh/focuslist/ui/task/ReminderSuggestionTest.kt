package com.vignesh.focuslist.ui.task

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the reminder sheet opens before the user touches it. D-071.
 *
 * The sheet's own behaviour is proved in `ReminderSheetSemanticsTest`, which
 * needs a device to render rows. This is the arithmetic underneath it, and it
 * is the half worth walking case by case: the rounding, the two days that are
 * not today, and the hour before midnight where the rounding runs out of day.
 */
class ReminderSuggestionTest {

    @Test
    fun `an afternoon open suggests the next whole hour`() {
        assertEquals(
            LocalTime.of(15, 0),
            suggestedReminderTime(TODAY, TODAY.atTime(14, 37))
        )
    }

    /**
     * Rounding up, not an hour added. Two opens a quarter of an hour apart
     * suggest the same moment, so the answer does not slide while it is read.
     */
    @Test
    fun `two opens in the same hour suggest the same moment`() {
        assertEquals(
            suggestedReminderTime(TODAY, TODAY.atTime(14, 37)),
            suggestedReminderTime(TODAY, TODAY.atTime(14, 52))
        )
    }

    /** On the hour is already round, and still moves: 3pm cannot mean now. */
    @Test
    fun `an open exactly on the hour suggests the hour after it`() {
        assertEquals(
            LocalTime.of(16, 0),
            suggestedReminderTime(TODAY, TODAY.atTime(15, 0))
        )
    }

    /**
     * A task scheduled ahead takes the morning. The clock says nothing useful
     * about a day it is not in.
     */
    @Test
    fun `a day still ahead suggests nine in the morning`() {
        assertEquals(
            DefaultReminderTime,
            suggestedReminderTime(TODAY.plusDays(3), TODAY.atTime(14, 37))
        )
    }

    /** And so does a day already gone, which Task Details can still hand in. */
    @Test
    fun `a day already gone suggests nine in the morning`() {
        assertEquals(
            DefaultReminderTime,
            suggestedReminderTime(TODAY.minusDays(5), TODAY.atTime(14, 37))
        )
    }

    /**
     * The last hour of the day is the one branch. Midnight belongs to tomorrow,
     * which is a day the caller did not name, so the morning takes over and the
     * sheet resolves it forward from there.
     */
    @Test
    fun `the hour before midnight suggests nine in the morning`() {
        assertEquals(
            DefaultReminderTime,
            suggestedReminderTime(TODAY, TODAY.atTime(23, 50))
        )
    }

    /** One minute earlier there is still a whole hour left in the day. */
    @Test
    fun `just before eleven the next hour is still today`() {
        assertEquals(
            LocalTime.of(23, 0),
            suggestedReminderTime(TODAY, TODAY.atTime(22, 59))
        )
    }

    private companion object {

        /** The Tuesday `RemindersTest` and the sheet's own test both use. */
        val TODAY: LocalDate = LocalDate.of(2026, 9, 8)
    }
}
