package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * The three axes `docs/decisions.md` D-027 added to a recurrence rule.
 *
 * `RecurrenceTest` covers what a rule could already do, and every one of its
 * assertions still holds, because an interval of one with no weekdays and no end
 * is the old four-value enum. This is what is new: how many periods a step
 * covers, which days inside a week count, and when the series stops.
 */
class RecurrenceRuleTest {

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private val createdAt: Instant = Instant.parse("2026-09-02T09:00:00Z")

    /** Anchored on a Wednesday, so the weekday cases have a fixed reference. */
    private val wednesday = date("2026-09-02")

    private val chore = Task(
        id = "chore",
        title = "Water the plants",
        createdAt = createdAt,
        scheduledDate = wednesday,
        recurrence = Recurrence(RecurrenceUnit.DAILY),
        completedAt = createdAt
    )

    // --- the interval ---------------------------------------------------------

    @Test
    fun `every third day steps three days, not one`() {
        val rule = Recurrence(RecurrenceUnit.DAILY, interval = 3)

        assertEquals(
            date("2026-09-05"),
            rule.nextOccurrence(anchor = wednesday, after = wednesday)
        )
    }

    @Test
    fun `every other week steps a fortnight`() {
        val rule = Recurrence(RecurrenceUnit.WEEKLY, interval = 2)

        assertEquals(
            date("2026-09-16"),
            rule.nextOccurrence(anchor = wednesday, after = wednesday)
        )
    }

    @Test
    fun `every other month steps two months`() {
        val rule = Recurrence(RecurrenceUnit.MONTHLY, interval = 2)

        assertEquals(
            date("2026-11-02"),
            rule.nextOccurrence(anchor = wednesday, after = wednesday)
        )
    }

    /**
     * The thing an interval gets wrong if it is treated as a period.
     *
     * Occurrences of "every three days" from the 1st are the 4th, 7th, 10th and
     * 13th. Finishing on the 10th has to land on the 13th: a rule that stepped
     * one period past the day it was finished would land on the 11th, which is
     * not an occurrence of anything.
     */
    @Test
    fun `finishing late lands on the block, not on the day after`() {
        val rule = Recurrence(RecurrenceUnit.DAILY, interval = 3)

        assertEquals(
            date("2026-09-13"),
            rule.nextOccurrence(anchor = date("2026-09-01"), after = date("2026-09-10"))
        )
    }

    /** Interleaved with the month-end clamp, which the interval must not break. */
    @Test
    fun `every other month from the 31st still finds a 31st`() {
        val rule = Recurrence(RecurrenceUnit.MONTHLY, interval = 2)

        assertEquals(
            date("2026-03-31"),
            rule.nextOccurrence(anchor = date("2026-01-31"), after = date("2026-01-31"))
        )
    }

    /**
     * Storage can hold anything, and a zero would step nowhere forever.
     *
     * Read as one rather than rejected, on the terms `TaskConverters` states:
     * throwing while reading the database is the worse outcome.
     */
    @Test
    fun `an interval below one behaves as one`() {
        listOf(0, -4).forEach { broken ->
            assertEquals(
                "interval $broken",
                date("2026-09-03"),
                Recurrence(RecurrenceUnit.DAILY, interval = broken)
                    .nextOccurrence(anchor = wednesday, after = wednesday)
            )
        }
    }

    // --- the weekday set ------------------------------------------------------

    private val mondayWednesdayFriday = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.FRIDAY
    )

    private fun weekly(interval: Int = 1, days: Set<DayOfWeek> = mondayWednesdayFriday) =
        Recurrence(RecurrenceUnit.WEEKLY, interval = interval, weekdays = days)

    /**
     * The one thing the weekday set does that a period cannot.
     *
     * Anchored on a Wednesday, the next occurrence is the Friday of the same
     * week. A weekly rule without days would say the following Wednesday.
     */
    @Test
    fun `the next selected day can be inside the same week`() {
        assertEquals(
            date("2026-09-04"),
            weekly().nextOccurrence(anchor = wednesday, after = wednesday)
        )
    }

    @Test
    fun `after the last selected day it moves to the next week`() {
        assertEquals(
            date("2026-09-07"),
            weekly().nextOccurrence(anchor = wednesday, after = date("2026-09-04"))
        )
    }

    /**
     * An off week is skipped whole, days and all.
     *
     * Anchored in the week of Monday the 31st of August, an interval of two makes
     * the week of the 14th the next on-week. The Monday of the 7th is inside an
     * off week and is not an occurrence, however selected Monday is.
     */
    @Test
    fun `an interval skips whole weeks rather than individual days`() {
        assertEquals(
            date("2026-09-14"),
            weekly(interval = 2).nextOccurrence(anchor = wednesday, after = date("2026-09-04"))
        )
    }

    @Test
    fun `inside an on week the interval does not apply again`() {
        assertEquals(
            date("2026-09-16"),
            weekly(interval = 2).nextOccurrence(anchor = wednesday, after = date("2026-09-15"))
        )
    }

    /**
     * Clearing every chip must not leave a rule that matches no day at all.
     *
     * An empty set means the anchor's own weekday, so the rule falls back to
     * exactly what a weekly rule without days has always done.
     */
    @Test
    fun `no days selected repeats on the anchor's own weekday`() {
        assertEquals(
            Recurrence(RecurrenceUnit.WEEKLY).nextOccurrence(wednesday, wednesday),
            weekly(days = emptySet()).nextOccurrence(wednesday, wednesday)
        )
    }

    @Test
    fun `the other three units ignore a weekday set`() {
        listOf(RecurrenceUnit.DAILY, RecurrenceUnit.MONTHLY, RecurrenceUnit.YEARLY).forEach { unit ->
            assertEquals(
                unit.name,
                Recurrence(unit).nextOccurrence(wednesday, wednesday),
                Recurrence(unit, weekdays = mondayWednesdayFriday)
                    .nextOccurrence(wednesday, wednesday)
            )
        }
    }

    /** The order days were tapped in is not the order they are read in. */
    @Test
    fun `the set's iteration order does not change the answer`() {
        val backwards = linkedSetOf(DayOfWeek.FRIDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)

        assertEquals(
            weekly().nextOccurrence(wednesday, wednesday),
            weekly(days = backwards).nextOccurrence(wednesday, wednesday)
        )
    }

    /**
     * The block is calculated rather than searched, so an ancient anchor is not
     * a walk. This asserts the answer; the cost is the reason it is calculated.
     */
    @Test
    fun `a rule anchored years ago still lands on a selected day`() {
        val next = weekly(interval = 3)
            .nextOccurrence(anchor = date("2019-01-02"), after = date("2026-09-02"))

        assertTrue(next.isAfter(date("2026-09-02")))
        assertTrue(next.dayOfWeek in mondayWednesdayFriday)
    }

    /**
     * A task completed before the day it was scheduled for.
     *
     * The next occurrence follows the anchor rather than the completion, so
     * finishing Friday's copy on Wednesday does not produce a Wednesday.
     */
    @Test
    fun `completing early does not produce a day the series has passed`() {
        val friday = date("2026-09-11")

        assertEquals(
            date("2026-09-14"),
            weekly().nextOccurrence(anchor = friday, after = wednesday)
        )
    }

    /**
     * **Every weekday selected is a daily rule**, which is worth having written
     * down because the two are reachable by different routes and nothing in the
     * model says they meet.
     *
     * Asserted across a fortnight rather than on one date, so this is the two
     * rules agreeing everywhere rather than agreeing once.
     */
    @Test
    fun `all seven days at an interval of one is daily`() {
        val everyDay = weekly(days = DayOfWeek.entries.toSet())
        val daily = Recurrence(RecurrenceUnit.DAILY)

        (0L..13L).map(wednesday::plusDays).forEach { day ->
            assertEquals(
                day.toString(),
                daily.nextOccurrence(anchor = wednesday, after = day),
                everyDay.nextOccurrence(anchor = wednesday, after = day)
            )
        }
    }

    /**
     * And an interval above one is not, which is why the equivalence cannot be
     * a rule about the weekday set alone. Every other week with all seven days
     * lit is seven days on and seven days off.
     */
    @Test
    fun `all seven days at a longer interval is not daily`() {
        val fortnightly = weekly(interval = 2, days = DayOfWeek.entries.toSet())

        // The Sunday that ends the anchor's on-week; the next occurrence has to
        // clear the whole off-week rather than land on the Monday after it.
        assertEquals(
            date("2026-09-14"),
            fortnightly.nextOccurrence(anchor = wednesday, after = date("2026-09-06"))
        )
    }

    // --- the end condition ----------------------------------------------------

    private fun completing(rule: Recurrence, occurrence: Int = 1, on: LocalDate = wednesday) =
        chore.copy(recurrence = rule, occurrenceNumber = occurrence)
            .nextRecurringInstance(today = on, id = "next", createdAt = createdAt)

    @Test
    fun `a series with no end always produces the next occurrence`() {
        assertNotNull(completing(Recurrence(RecurrenceUnit.DAILY), occurrence = 900))
    }

    @Test
    fun `an end date lets through an occurrence inside it`() {
        val rule = Recurrence(
            RecurrenceUnit.DAILY,
            end = RecurrenceEnd.OnDate(date("2026-09-10"))
        )

        assertEquals(date("2026-09-03"), completing(rule)?.scheduledDate)
    }

    @Test
    fun `an end date stops the series once the next occurrence is past it`() {
        val rule = Recurrence(
            RecurrenceUnit.DAILY,
            end = RecurrenceEnd.OnDate(date("2026-09-02"))
        )

        assertNull(completing(rule))
    }

    /** The horizon is inclusive: a task may fall on the day it ends. */
    @Test
    fun `an occurrence on the end date itself is still produced`() {
        val rule = Recurrence(
            RecurrenceUnit.DAILY,
            end = RecurrenceEnd.OnDate(date("2026-09-03"))
        )

        assertEquals(date("2026-09-03"), completing(rule)?.scheduledDate)
    }

    @Test
    fun `a count lets through every occurrence before the last`() {
        val rule = Recurrence(
            RecurrenceUnit.DAILY,
            end = RecurrenceEnd.AfterOccurrences(3)
        )

        assertNotNull(completing(rule, occurrence = 1))
        assertNotNull(completing(rule, occurrence = 2))
    }

    @Test
    fun `completing the last occurrence of a count ends the series`() {
        val rule = Recurrence(
            RecurrenceUnit.DAILY,
            end = RecurrenceEnd.AfterOccurrences(3)
        )

        assertNull(completing(rule, occurrence = 3))
    }

    /** A series of one is a task that happens once and says so. */
    @Test
    fun `a count of one produces nothing at all`() {
        val rule = Recurrence(
            RecurrenceUnit.DAILY,
            end = RecurrenceEnd.AfterOccurrences(1)
        )

        assertNull(completing(rule, occurrence = 1))
    }

    /**
     * The count is carried, not recomputed.
     *
     * This is what D-019 said had to exist before an end condition could, and
     * the reason it is a stored number rather than a walk back along
     * `spawnedFromId`: deleting an earlier copy must not move the count.
     */
    @Test
    fun `each occurrence knows its place in the series`() {
        val rule = Recurrence(RecurrenceUnit.DAILY)

        assertEquals(4, completing(rule, occurrence = 3)?.occurrenceNumber)
    }

    @Test
    fun `a task nobody has repeated is the first of its series`() {
        assertEquals(1, Task(id = "a", title = "Once", createdAt = createdAt).occurrenceNumber)
    }

    /**
     * The count runs out before the calendar is consulted.
     *
     * Not an optimisation. A rule that has produced its last occurrence is over
     * whatever date would have come next, and asking the evaluator first would
     * make an exhausted series depend on arithmetic that no longer applies.
     */
    @Test
    fun `an exhausted count ends the series whatever the dates say`() {
        val rule = Recurrence(
            RecurrenceUnit.YEARLY,
            end = RecurrenceEnd.AfterOccurrences(2)
        )

        assertNull(completing(rule, occurrence = 2, on = date("2019-01-01")))
    }
}
