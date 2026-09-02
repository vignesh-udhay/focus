package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecurrenceTest {

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    // The next occurrence, on time

    @Test
    fun `a daily task completed on the day it was due moves to the next day`() {
        assertEquals(
            date("2026-09-03"),
            Recurrence.DAILY.nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    @Test
    fun `a weekly task keeps its weekday`() {
        // Anchored on a Wednesday, so every occurrence is a Wednesday.
        assertEquals(
            date("2026-09-09"),
            Recurrence.WEEKLY.nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    @Test
    fun `a monthly task keeps its day of the month`() {
        assertEquals(
            date("2026-10-02"),
            Recurrence.MONTHLY.nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    @Test
    fun `a yearly task keeps its date`() {
        assertEquals(
            date("2027-09-02"),
            Recurrence.YEARLY.nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    // The next occurrence, late

    @Test
    fun `finishing a weekly task late skips the occurrences that have gone`() {
        // Anchored Monday the 1st, finished on Friday the 12th. The 8th has
        // already passed, so the next one still to come is the 15th.
        assertEquals(
            date("2026-06-15"),
            Recurrence.WEEKLY.nextOccurrence(anchor = date("2026-06-01"), after = date("2026-06-12"))
        )
    }

    @Test
    fun `finishing a daily task a week late does not bank the days that were missed`() {
        // One next occurrence, not seven copies of a task nobody did.
        assertEquals(
            date("2026-09-10"),
            Recurrence.DAILY.nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-09"))
        )
    }

    @Test
    fun `finishing a monthly task months late lands on the next month still ahead`() {
        assertEquals(
            date("2027-01-15"),
            Recurrence.MONTHLY.nextOccurrence(anchor = date("2026-09-15"), after = date("2026-12-20"))
        )
    }

    @Test
    fun `a rule anchored years ago still produces the next date`() {
        assertEquals(
            date("2026-09-03"),
            Recurrence.DAILY.nextOccurrence(anchor = date("2019-01-01"), after = date("2026-09-02"))
        )
    }

    // The next occurrence, early

    @Test
    fun `finishing before the day it was due still moves the task on`() {
        // Completed on the 1st a task scheduled for the 5th. The occurrence
        // being finished is the 5th, so the next one is the 6th, not the 2nd.
        assertEquals(
            date("2026-09-06"),
            Recurrence.DAILY.nextOccurrence(anchor = date("2026-09-05"), after = date("2026-09-01"))
        )
    }

    // Month ends

    @Test
    fun `a monthly task on the 31st is clamped in a short month but not moved off the 31st`() {
        val anchor = date("2026-01-31")

        val february = Recurrence.MONTHLY.nextOccurrence(anchor = anchor, after = date("2026-01-31"))
        assertEquals(date("2026-02-28"), february)

        // Measured from the anchor rather than from February, so March is the
        // 31st again rather than the 28th.
        val march = Recurrence.MONTHLY.nextOccurrence(anchor = anchor, after = february)
        assertEquals(date("2026-03-31"), march)
    }

    @Test
    fun `a monthly task on the 31st completed on the clamped day moves past it`() {
        // The estimate says one month, which clamps to the 28th, which is not
        // after the 28th. The answer has to be March, not February again.
        assertEquals(
            date("2026-03-31"),
            Recurrence.MONTHLY.nextOccurrence(anchor = date("2026-01-31"), after = date("2026-02-28"))
        )
    }

    @Test
    fun `a yearly task on the 29th of February lands on the 28th in an ordinary year`() {
        assertEquals(
            date("2025-02-28"),
            Recurrence.YEARLY.nextOccurrence(anchor = date("2024-02-29"), after = date("2024-02-29"))
        )
    }

    // The next instance of a whole task

    private val createdAt: Instant = Instant.parse("2026-09-02T09:00:00Z")

    private val chore = Task(
        id = "chore",
        title = "Water the plants",
        createdAt = createdAt,
        notes = "The ones on the balcony",
        placement = TaskPlacement.ANYTIME,
        scheduledDate = date("2026-09-02"),
        estimatedDurationMinutes = 5,
        recurrence = Recurrence.DAILY,
        completedAt = createdAt
    )

    @Test
    fun `a task that does not recur has no next instance`() {
        assertNull(
            chore.copy(recurrence = null)
                .nextRecurringInstance(date("2026-09-02"), id = "next", createdAt = createdAt)
        )
    }

    @Test
    fun `the next instance is a new outstanding task on the next date`() {
        val next = chore.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertEquals("next", next.id)
        assertEquals(date("2026-09-03"), next.scheduledDate)
        assertNull(next.completedAt)
        assertNull(next.deletedAt)
    }

    @Test
    fun `the next instance carries everything else about the task across`() {
        val next = chore.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertEquals("Water the plants", next.title)
        assertEquals("The ones on the balcony", next.notes)
        assertEquals(TaskPlacement.ANYTIME, next.placement)
        assertEquals(5, next.estimatedDurationMinutes)
        // The rule above all, or the series would stop after one repeat.
        assertEquals(Recurrence.DAILY, next.recurrence)
    }

    @Test
    fun `a due date moves by as much as the scheduled date did`() {
        // Due three days after it is meant to be started, and still is.
        val task = chore.copy(
            recurrence = Recurrence.WEEKLY,
            scheduledDate = date("2026-09-02"),
            dueDate = date("2026-09-05")
        )

        val next = task.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertEquals(date("2026-09-09"), next.scheduledDate)
        assertEquals(date("2026-09-12"), next.dueDate)
    }

    @Test
    fun `a task with no due date does not gain one`() {
        assertNull(chore.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!.dueDate)
    }

    @Test
    fun `a recurring task that was never scheduled is anchored on the day it was completed`() {
        val undated = chore.copy(scheduledDate = null, recurrence = Recurrence.WEEKLY)

        assertEquals(
            date("2026-09-09"),
            undated.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!.scheduledDate
        )
    }
}
