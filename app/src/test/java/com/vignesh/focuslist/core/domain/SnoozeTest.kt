package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * The snooze arithmetic, including the hours it is easy to get wrong.
 *
 * `ROADMAP.md` names this as a Phase 1 exit criterion, and it earns the place:
 * every one of these cases is a reminder set for a time the user did not
 * expect, which is the same failure as a reminder that never arrives, only
 * harder to notice.
 */
class SnoozeTest {

    private val morning: LocalDateTime = LocalDateTime.of(2026, 9, 5, 9, 30)

    // 1. The relative options

    @Test
    fun `ten minutes is ten minutes`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 5, 9, 40),
            snoozedUntil(SnoozeOption.TenMinutes, morning)
        )
    }

    @Test
    fun `an hour is an hour`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 5, 10, 30),
            snoozedUntil(SnoozeOption.OneHour, morning)
        )
    }

    @Test
    fun `ten minutes at eight minutes to midnight lands tomorrow`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 6, 0, 2),
            snoozedUntil(SnoozeOption.TenMinutes, LocalDateTime.of(2026, 9, 5, 23, 52))
        )
    }

    @Test
    fun `an hour on the last night of a month crosses into the next`() {
        assertEquals(
            LocalDateTime.of(2026, 10, 1, 0, 30),
            snoozedUntil(SnoozeOption.OneHour, LocalDateTime.of(2026, 9, 30, 23, 30))
        )
    }

    // 2. This evening, the one that can be unavailable

    @Test
    fun `this evening is six in the evening`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 5, 18, 0),
            snoozedUntil(SnoozeOption.ThisEvening, morning)
        )
    }

    @Test
    fun `this evening is gone once it is evening`() {
        // Not rolled to tomorrow. A chip saying "this evening" that sets
        // tomorrow is the notification lying about what the button does.
        assertNull(snoozedUntil(SnoozeOption.ThisEvening, LocalDateTime.of(2026, 9, 5, 20, 0)))
    }

    @Test
    fun `this evening is gone at exactly six, not set to the moment just arrived`() {
        assertNull(snoozedUntil(SnoozeOption.ThisEvening, LocalDateTime.of(2026, 9, 5, 18, 0)))
    }

    @Test
    fun `this evening survives a minute before six`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 5, 18, 0),
            snoozedUntil(SnoozeOption.ThisEvening, LocalDateTime.of(2026, 9, 5, 17, 59))
        )
    }

    // 3. Tomorrow

    @Test
    fun `tomorrow morning is nine, the hour the chip names`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 6, 9, 0),
            snoozedUntil(SnoozeOption.TomorrowMorning, morning)
        )
    }

    @Test
    fun `tomorrow at half past eleven at night is still the next calendar day`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 6, 9, 0),
            snoozedUntil(SnoozeOption.TomorrowMorning, LocalDateTime.of(2026, 9, 5, 23, 30))
        )
    }

    @Test
    fun `tomorrow across a year boundary`() {
        assertEquals(
            LocalDateTime.of(2027, 1, 1, 9, 0),
            snoozedUntil(SnoozeOption.TomorrowMorning, LocalDateTime.of(2026, 12, 31, 22, 0))
        )
    }

    // 4. What gets offered

    @Test
    fun `three are offered in the morning, and the far one is this evening`() {
        // Three rather than the design's four, because a notification shows at
        // most three actions.
        assertEquals(
            listOf(SnoozeOption.TenMinutes, SnoozeOption.OneHour, SnoozeOption.ThisEvening),
            availableSnoozeOptions(morning)
        )
    }

    @Test
    fun `after six the far one becomes tomorrow morning`() {
        assertEquals(
            listOf(SnoozeOption.TenMinutes, SnoozeOption.OneHour, SnoozeOption.TomorrowMorning),
            availableSnoozeOptions(LocalDateTime.of(2026, 9, 5, 21, 0))
        )
    }

    @Test
    fun `there are always exactly three, at every hour of the day`() {
        // The slot is never empty and never overflows, which is what lets the
        // notification lay out three actions without asking the time first.
        (0..23).forEach { hour ->
            val now = LocalDateTime.of(2026, 9, 5, hour, 30)
            assertEquals("at $hour:30", 3, availableSnoozeOptions(now).size)
        }
    }

    @Test
    fun `the near two are always offered, whatever the hour`() {
        (0..23).forEach { hour ->
            val offered = availableSnoozeOptions(LocalDateTime.of(2026, 9, 5, hour, 30))
            assertEquals(
                "at $hour:30",
                listOf(SnoozeOption.TenMinutes, SnoozeOption.OneHour),
                offered.take(2)
            )
        }
    }

    @Test
    fun `every offered option resolves to a time, and it is in the future`() {
        listOf(
            LocalDateTime.of(2026, 9, 5, 0, 1),
            morning,
            LocalDateTime.of(2026, 9, 5, 17, 59),
            LocalDateTime.of(2026, 9, 5, 18, 0),
            LocalDateTime.of(2026, 9, 5, 23, 59)
        ).forEach { now ->
            availableSnoozeOptions(now).forEach { option ->
                val until = snoozedUntil(option, now)
                assertEquals("$option at $now resolved to nothing", true, until != null)
                assertEquals("$option at $now is not in the future", true, until!!.isAfter(now))
            }
        }
    }
}
