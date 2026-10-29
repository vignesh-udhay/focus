package app.tellcat.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * `docs/decisions.md` D-108 and D-109: when a follow-up is owed, and what a
 * row says about a reminder that was snoozed or is waiting to be posted again.
 */
class FollowUpTest {

    private val nine: LocalDateTime = LocalDateTime.of(2026, 10, 29, 9, 30)
    private val told: Instant = Instant.parse("2026-10-29T04:00:05Z")

    private val dentist = Task(
        id = "dentist",
        title = "Call the dentist",
        createdAt = Instant.parse("2026-10-20T08:00:00Z"),
        reminderAt = nine
    )

    private val waiting = dentist.copy(reminderDeliveredAt = told, followUpAt = nine.plusMinutes(10))

    // When

    @Test
    fun `a follow-up is ten minutes after the minute the reminder was announced in`() {
        assertEquals(nine.plusMinutes(10), followUpTime(nine.plusSeconds(17)))
    }

    @Test
    fun `a reminder announced just before midnight is followed up tomorrow`() {
        val lateAnnouncement = LocalDateTime.of(2026, 10, 29, 23, 55, 30)
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 5), followUpTime(lateAnnouncement))
    }

    @Test
    fun `the delay is ten minutes`() {
        assertEquals(10L, FollowUpDelay.toMinutes())
    }

    // Whether

    @Test
    fun `an announced, unanswered reminder has its follow-up pending`() {
        assertEquals(nine.plusMinutes(10), waiting.pendingFollowUp)
    }

    @Test
    fun `nothing is pending once the task is completed or deleted`() {
        assertNull(waiting.copy(completedAt = told).pendingFollowUp)
        assertNull(waiting.copy(deletedAt = told).pendingFollowUp)
    }

    /** A snooze or a new time make the reminder owed again. */
    @Test
    fun `nothing is pending once the reminder is owed again`() {
        assertNull(waiting.copy(reminderDeliveredAt = null).pendingFollowUp)
    }

    @Test
    fun `a task with no follow-up set has none pending`() {
        assertNull(dentist.copy(reminderDeliveredAt = told).pendingFollowUp)
    }

    @Test
    fun `follow-ups owed come soonest first, and only the pending ones`() {
        val later = waiting.copy(id = "later", followUpAt = nine.plusMinutes(40))
        val done = waiting.copy(id = "done", completedAt = told)

        assertEquals(
            listOf("dentist", "later"),
            followUpsOwed(listOf(later, done, waiting)).map { it.id }
        )
    }

    // What the row says

    @Test
    fun `a set reminder is its time`() {
        assertEquals(RowReminder(nine, ReminderStage.Set), dentist.rowReminder())
    }

    @Test
    fun `a pending follow-up is what the row says`() {
        assertEquals(RowReminder(nine.plusMinutes(10), ReminderStage.FollowUp), waiting.rowReminder())
    }

    @Test
    fun `a snooze still to ring is said as a snooze`() {
        val snoozed = dentist.copy(snoozedUntil = nine.plusHours(1))
        assertEquals(RowReminder(nine.plusHours(1), ReminderStage.Snoozed), snoozed.rowReminder())
    }

    /** Once it has rung at the snooze, it is simply the time it spoke. */
    @Test
    fun `a snooze that has rung is said as a set time`() {
        val rung = dentist.copy(snoozedUntil = nine.plusHours(1), reminderDeliveredAt = told)
        assertEquals(RowReminder(nine.plusHours(1), ReminderStage.Set), rung.rowReminder())
    }

    @Test
    fun `a task with no reminder says nothing`() {
        assertNull(dentist.copy(reminderAt = null).rowReminder())
    }

    // When a snooze is offered inside the app

    @Test
    fun `a reminder that has rung and is unanswered can be snoozed`() {
        assertTrue(dentist.copy(reminderDeliveredAt = told).canSnooze)
    }

    @Test
    fun `a snoozed reminder can be snoozed again`() {
        assertTrue(dentist.copy(snoozedUntil = nine.plusHours(1)).canSnooze)
    }

    @Test
    fun `a reminder still to ring, a completed task and no reminder cannot be snoozed`() {
        assertFalse(dentist.canSnooze)
        assertFalse(dentist.copy(reminderDeliveredAt = told, completedAt = told).canSnooze)
        assertFalse(dentist.copy(reminderAt = null, reminderDeliveredAt = told).canSnooze)
    }
}
