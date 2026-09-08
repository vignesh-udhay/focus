package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The two presets `docs/decisions.md` D-018 had to fix the meaning of.
 *
 * Neither is in the parser's vocabulary and neither is self-evident, which is
 * why the entry names them rather than leaving each reader to guess. These
 * assert the naming.
 */
class DatePresetsTest {

    /** A Monday, so a full week can be walked from a known weekday. */
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    private fun eachDayOfOneWeek(): List<LocalDate> = (0L..6L).map(monday::plusDays)

    @Test
    fun thisWeekendIsTheComingSaturday() {
        assertEquals(LocalDate.of(2026, 9, 12), thisWeekend(monday))
    }

    @Test
    fun endOfWeekIsTheComingFriday() {
        assertEquals(LocalDate.of(2026, 9, 11), endOfWeek(monday))
    }

    @Test
    fun bothLandOnTheDayTheyAreNamedFor() {
        eachDayOfOneWeek().forEach { day ->
            assertEquals(DayOfWeek.SATURDAY, thisWeekend(day).dayOfWeek)
            assertEquals(DayOfWeek.FRIDAY, endOfWeek(day).dayOfWeek)
        }
    }

    /**
     * A preset that can resolve to today is a preset that sometimes does
     * nothing, and the user has no way to tell whether it worked. Checked on
     * the two days where a non-strict rule would fail.
     */
    @Test
    fun neitherEverResolvesToTheDayItWasPressedOn() {
        eachDayOfOneWeek().forEach { day ->
            assertTrue("this weekend on $day", thisWeekend(day).isAfter(day))
            assertTrue("end of week on $day", endOfWeek(day).isAfter(day))
        }
    }

    @Test
    fun pressedOnASaturdayThisWeekendIsAWeekAway() {
        val saturday = LocalDate.of(2026, 9, 12)

        assertEquals(LocalDate.of(2026, 9, 19), thisWeekend(saturday))
    }

    @Test
    fun pressedOnAFridayEndOfWeekIsAWeekAway() {
        val friday = LocalDate.of(2026, 9, 11)

        assertEquals(LocalDate.of(2026, 9, 18), endOfWeek(friday))
    }

    /** Neither is within a week of itself by more than seven days. */
    @Test
    fun neitherIsEverMoreThanAWeekOut() {
        eachDayOfOneWeek().forEach { day ->
            assertTrue(thisWeekend(day) <= day.plusDays(7))
            assertTrue(endOfWeek(day) <= day.plusDays(7))
        }
    }

    /** Month and year boundaries are `TemporalAdjusters`' problem, not ours. */
    @Test
    fun theyCrossMonthAndYearBoundaries() {
        assertEquals(LocalDate.of(2027, 1, 2), thisWeekend(LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2026, 10, 2), endOfWeek(LocalDate.of(2026, 9, 30)))
    }
}
