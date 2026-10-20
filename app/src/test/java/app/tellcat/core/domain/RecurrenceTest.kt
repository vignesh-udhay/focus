package app.tellcat.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceTest {

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    // The next occurrence, on time

    @Test
    fun `a daily task completed on the day it was due moves to the next day`() {
        assertEquals(
            date("2026-09-03"),
            Recurrence(RecurrenceUnit.DAILY).nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    @Test
    fun `a weekly task keeps its weekday`() {
        // Anchored on a Wednesday, so every occurrence is a Wednesday.
        assertEquals(
            date("2026-09-09"),
            Recurrence(RecurrenceUnit.WEEKLY).nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    @Test
    fun `a monthly task keeps its day of the month`() {
        assertEquals(
            date("2026-10-02"),
            Recurrence(RecurrenceUnit.MONTHLY).nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    @Test
    fun `a yearly task keeps its date`() {
        assertEquals(
            date("2027-09-02"),
            Recurrence(RecurrenceUnit.YEARLY).nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-02"))
        )
    }

    // The next occurrence, late

    @Test
    fun `finishing a weekly task late skips the occurrences that have gone`() {
        // Anchored Monday the 1st, finished on Friday the 12th. The 8th has
        // already passed, so the next one still to come is the 15th.
        assertEquals(
            date("2026-06-15"),
            Recurrence(RecurrenceUnit.WEEKLY).nextOccurrence(anchor = date("2026-06-01"), after = date("2026-06-12"))
        )
    }

    @Test
    fun `finishing a daily task a week late does not bank the days that were missed`() {
        // One next occurrence, not seven copies of a task nobody did.
        assertEquals(
            date("2026-09-10"),
            Recurrence(RecurrenceUnit.DAILY).nextOccurrence(anchor = date("2026-09-02"), after = date("2026-09-09"))
        )
    }

    @Test
    fun `finishing a monthly task months late lands on the next month still ahead`() {
        assertEquals(
            date("2027-01-15"),
            Recurrence(RecurrenceUnit.MONTHLY).nextOccurrence(anchor = date("2026-09-15"), after = date("2026-12-20"))
        )
    }

    @Test
    fun `a rule anchored years ago still produces the next date`() {
        assertEquals(
            date("2026-09-03"),
            Recurrence(RecurrenceUnit.DAILY).nextOccurrence(anchor = date("2019-01-01"), after = date("2026-09-02"))
        )
    }

    // The next occurrence, early

    @Test
    fun `finishing before the day it was due still moves the task on`() {
        // Completed on the 1st a task scheduled for the 5th. The occurrence
        // being finished is the 5th, so the next one is the 6th, not the 2nd.
        assertEquals(
            date("2026-09-06"),
            Recurrence(RecurrenceUnit.DAILY).nextOccurrence(anchor = date("2026-09-05"), after = date("2026-09-01"))
        )
    }

    // Month ends

    @Test
    fun `a monthly task on the 31st is clamped in a short month but not moved off the 31st`() {
        val anchor = date("2026-01-31")

        val february = Recurrence(RecurrenceUnit.MONTHLY).nextOccurrence(anchor = anchor, after = date("2026-01-31"))
        assertEquals(date("2026-02-28"), february)

        // Measured from the anchor rather than from February, so March is the
        // 31st again rather than the 28th.
        val march = Recurrence(RecurrenceUnit.MONTHLY).nextOccurrence(anchor = anchor, after = february)
        assertEquals(date("2026-03-31"), march)
    }

    @Test
    fun `a monthly task on the 31st completed on the clamped day moves past it`() {
        // The estimate says one month, which clamps to the 28th, which is not
        // after the 28th. The answer has to be March, not February again.
        assertEquals(
            date("2026-03-31"),
            Recurrence(RecurrenceUnit.MONTHLY).nextOccurrence(anchor = date("2026-01-31"), after = date("2026-02-28"))
        )
    }

    @Test
    fun `a yearly task on the 29th of February lands on the 28th in an ordinary year`() {
        assertEquals(
            date("2025-02-28"),
            Recurrence(RecurrenceUnit.YEARLY).nextOccurrence(anchor = date("2024-02-29"), after = date("2024-02-29"))
        )
    }

    // The next instance of a whole task

    private val createdAt: Instant = Instant.parse("2026-09-02T09:00:00Z")

    private val chore = Task(
        id = "chore",
        title = "Water the plants",
        createdAt = createdAt,
        notes = "The ones on the balcony",
        scheduledDate = date("2026-09-02"),
        estimatedDurationMinutes = 5,
        recurrence = Recurrence(RecurrenceUnit.DAILY),
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
    fun `the next instance carries the reminder forward, at the same time of day`() {
        val reminding = chore.copy(
            reminderAt = LocalDateTime.of(2026, 9, 2, 8, 30),
            reminderDeliveredAt = createdAt
        )

        val next = reminding.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertEquals(LocalDateTime.of(2026, 9, 3, 8, 30), next.reminderAt)
    }

    @Test
    fun `the next instance has not been announced, however announced this one was`() {
        // The bug this exists for. Carrying the delivery record across left
        // every occurrence after the first already marked delivered, so a
        // daily reminder fired once and then went silent for good.
        val reminding = chore.copy(
            reminderAt = LocalDateTime.of(2026, 9, 2, 8, 30),
            reminderDeliveredAt = createdAt
        )

        val next = reminding.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertNull(next.reminderDeliveredAt)
        assertTrue(next.hasLiveReminder())
    }

    @Test
    fun `a reminder that is late by a week moves with the occurrence, not by a day`() {
        // The shift is the scheduled date's, so a reminder keeps whatever
        // relationship it had with the day the task is meant to be done.
        val reminding = chore.copy(reminderAt = LocalDateTime.of(2026, 9, 2, 8, 30))

        val next = reminding.nextRecurringInstance(date("2026-09-09"), "next", createdAt)!!

        assertEquals(date("2026-09-10"), next.scheduledDate)
        assertEquals(LocalDateTime.of(2026, 9, 10, 8, 30), next.reminderAt)
    }

    @Test
    fun `a task with no reminder gains none by repeating`() {
        val next = chore.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertNull(next.reminderAt)
        assertNull(next.reminderDeliveredAt)
    }

    @Test
    fun `the next instance carries everything else about the task across`() {
        val next = chore.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertEquals("Water the plants", next.title)
        assertEquals("The ones on the balcony", next.notes)
        assertEquals(5, next.estimatedDurationMinutes)
        // The rule above all, or the series would stop after one repeat.
        assertEquals(Recurrence(RecurrenceUnit.DAILY), next.recurrence)
    }

    @Test
    fun `a due date moves by as much as the scheduled date did`() {
        // Due three days after it is meant to be started, and still is.
        val task = chore.copy(
            recurrence = Recurrence(RecurrenceUnit.WEEKLY),
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
        val undated = chore.copy(scheduledDate = null, recurrence = Recurrence(RecurrenceUnit.WEEKLY))

        assertEquals(
            date("2026-09-09"),
            undated.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!.scheduledDate
        )
    }

    // D-078: a snooze moves one announcement, never the series

    @Test
    fun `a snooze does not move the next occurrence's reminder`() {
        // A daily 8:00 task snoozed to 9:00 the next morning, then done. The
        // series stays at 8:00 on the next day; it used to skip a day and stay
        // at 9:00 for good.
        val snoozed = chore.copy(
            reminderAt = LocalDateTime.of(2026, 9, 2, 8, 0),
            snoozedUntil = LocalDateTime.of(2026, 9, 3, 9, 0)
        )

        val next = snoozed.nextRecurringInstance(date("2026-09-03"), "next", createdAt)!!

        assertEquals(date("2026-09-04"), next.scheduledDate)
        assertEquals(LocalDateTime.of(2026, 9, 4, 8, 0), next.reminderAt)
        assertNull(next.snoozedUntil)
    }

    @Test
    fun `the next occurrence keeps the checklist with nothing ticked`() {
        val groceries = chore.copy(
            checklist = listOf(ChecklistItem("Milk", done = true), ChecklistItem("Eggs"))
        )

        val next = groceries.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertEquals(listOf(ChecklistItem("Milk"), ChecklistItem("Eggs")), next.checklist)
    }

    // D-038: the alarm is a property of the series, like its time

    @Test
    fun `the next occurrence of an alarm is an alarm`() {
        val alarm = chore.copy(reminderAt = LocalDateTime.of(2026, 9, 2, 8, 0), alarm = true)

        val next = alarm.nextRecurringInstance(date("2026-09-02"), "next", createdAt)!!

        assertTrue(next.ringsAsAlarm)
    }

    @Test
    fun `a next reminder that would already have gone lands on the occurrence's own day`() {
        // Moved in the list to the 4th without its reminder, which stays on the
        // 2nd. The shift would put the next one on the 3rd, already gone, and it
        // would ring the moment the task is completed.
        val moved = chore.copy(
            scheduledDate = date("2026-09-04"),
            reminderAt = LocalDateTime.of(2026, 9, 2, 8, 0)
        )

        val next = moved.nextRecurringInstance(
            today = date("2026-09-04"),
            id = "next",
            createdAt = createdAt,
            now = LocalDateTime.of(2026, 9, 4, 10, 0)
        )!!

        assertEquals(date("2026-09-05"), next.scheduledDate)
        assertEquals(LocalDateTime.of(2026, 9, 5, 8, 0), next.reminderAt)
    }

    @Test
    fun `a next reminder still ahead keeps its distance from the day`() {
        // Reminded the evening before: the relationship the user chose stays.
        val eveBefore = chore.copy(reminderAt = LocalDateTime.of(2026, 9, 1, 20, 0))

        val next = eveBefore.nextRecurringInstance(
            today = date("2026-09-02"),
            id = "next",
            createdAt = createdAt,
            now = LocalDateTime.of(2026, 9, 2, 10, 0)
        )!!

        assertEquals(LocalDateTime.of(2026, 9, 2, 20, 0), next.reminderAt)
    }
}
