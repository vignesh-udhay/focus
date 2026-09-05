package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * The reminder queries, and the awkward cases they exist for.
 *
 * Most of these describe a phone that was off, throttled, or moved between
 * timezones, which is the population `docs/decisions.md` D-005 is aimed at.
 * None of them need a device: the functions take the current time as an
 * argument, so "the phone was off for nine hours" is a parameter.
 */
class RemindersTest {

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")

    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 5, 12, 0)

    private fun task(
        id: String,
        reminderAt: LocalDateTime? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = Task(
        id = id,
        title = "Task $id",
        createdAt = createdAt,
        reminderAt = reminderAt,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    // 1. hasLiveReminder

    @Test
    fun `a task with no reminder is not owed one`() {
        assertFalse(task("a").hasLiveReminder())
    }

    @Test
    fun `a task with a reminder is owed one`() {
        assertTrue(task("a", reminderAt = now).hasLiveReminder())
    }

    @Test
    fun `completing a task retires its reminder`() {
        val completed = task("a", reminderAt = now, completedAt = createdAt)

        assertFalse(completed.hasLiveReminder())
    }

    @Test
    fun `deleting a task retires its reminder`() {
        val deleted = task("a", reminderAt = now, deletedAt = createdAt)

        assertFalse(deleted.hasLiveReminder())
    }

    // 2. pendingReminders

    @Test
    fun `a reminder in the future is pending`() {
        val tasks = listOf(task("a", reminderAt = now.plusMinutes(1)))

        assertEquals(listOf("a"), pendingReminders(tasks, now).map { it.id })
    }

    @Test
    fun `a reminder in the past is not pending`() {
        val tasks = listOf(task("a", reminderAt = now.minusMinutes(1)))

        assertEquals(emptyList<String>(), pendingReminders(tasks, now).map { it.id })
    }

    @Test
    fun `pending reminders come back soonest first`() {
        val tasks = listOf(
            task("later", reminderAt = now.plusHours(3)),
            task("sooner", reminderAt = now.plusMinutes(5)),
            task("middle", reminderAt = now.plusHours(1))
        )

        assertEquals(
            listOf("sooner", "middle", "later"),
            pendingReminders(tasks, now).map { it.id }
        )
    }

    @Test
    fun `reminders at the same moment keep the order they were given`() {
        val tasks = listOf(
            task("first", reminderAt = now.plusHours(1)),
            task("second", reminderAt = now.plusHours(1))
        )

        assertEquals(listOf("first", "second"), pendingReminders(tasks, now).map { it.id })
    }

    @Test
    fun `a completed task is not scheduled, however far off its reminder was`() {
        val tasks = listOf(
            task("done", reminderAt = now.plusDays(2), completedAt = createdAt)
        )

        assertEquals(emptyList<String>(), pendingReminders(tasks, now).map { it.id })
    }

    @Test
    fun `a deleted task does not ring at 6am`() {
        val tasks = listOf(
            task("gone", reminderAt = now.plusHours(18), deletedAt = createdAt)
        )

        assertEquals(emptyList<String>(), pendingReminders(tasks, now).map { it.id })
    }

    // 3. missedReminders

    @Test
    fun `a reminder that came due while the phone was off is missed`() {
        val tasks = listOf(task("a", reminderAt = now.minusHours(9)))

        assertEquals(listOf("a"), missedReminders(tasks, now).map { it.id })
    }

    @Test
    fun `missed reminders come back oldest first`() {
        val tasks = listOf(
            task("recent", reminderAt = now.minusMinutes(5)),
            task("ancient", reminderAt = now.minusDays(2)),
            task("older", reminderAt = now.minusHours(4))
        )

        assertEquals(
            listOf("ancient", "older", "recent"),
            missedReminders(tasks, now).map { it.id }
        )
    }

    @Test
    fun `a task completed from the notification is not also missed`() {
        val tasks = listOf(
            task("done", reminderAt = now.minusHours(1), completedAt = createdAt)
        )

        assertEquals(emptyList<String>(), missedReminders(tasks, now).map { it.id })
    }

    // 4. The boundary between the two

    @Test
    fun `a reminder due exactly now counts as missed, not pending`() {
        val tasks = listOf(task("a", reminderAt = now))

        assertEquals(emptyList<String>(), pendingReminders(tasks, now).map { it.id })
        assertEquals(listOf("a"), missedReminders(tasks, now).map { it.id })
    }

    @Test
    fun `every live reminder lands in exactly one of the two lists`() {
        val tasks = listOf(
            task("past", reminderAt = now.minusHours(1)),
            task("exactly now", reminderAt = now),
            task("future", reminderAt = now.plusHours(1)),
            task("none"),
            task("done", reminderAt = now.plusHours(1), completedAt = createdAt),
            task("deleted", reminderAt = now.plusHours(1), deletedAt = createdAt)
        )

        val pending = pendingReminders(tasks, now).map { it.id }
        val missed = missedReminders(tasks, now).map { it.id }

        assertEquals(listOf("future"), pending)
        assertEquals(listOf("past", "exactly now"), missed)
        // Nothing can be scheduled twice, and nothing owed can fall between
        // the two lists and be scheduled by nobody.
        assertEquals(emptySet<String>(), pending.toSet() intersect missed.toSet())
        assertEquals(
            tasks.filter { it.hasLiveReminder() }.map { it.id }.toSet(),
            (pending + missed).toSet()
        )
    }

    // 5. What a clock or timezone change does

    @Test
    fun `a reminder dragged into the past by a timezone change reads as missed`() {
        // Set for 09:30 while the phone said one zone, read back after the
        // phone moved somewhere the local time is already past it. The stored
        // value never changed; the meaning of "now" did.
        val tasks = listOf(task("a", reminderAt = LocalDateTime.of(2026, 9, 5, 9, 30)))

        assertEquals(emptyList<String>(), missedReminders(tasks, LocalDateTime.of(2026, 9, 5, 9, 0)).map { it.id })
        assertEquals(listOf("a"), missedReminders(tasks, LocalDateTime.of(2026, 9, 5, 10, 0)).map { it.id })
    }

    @Test
    fun `an empty list produces no reminders of either kind`() {
        assertEquals(emptyList<Task>(), pendingReminders(emptyList(), now))
        assertEquals(emptyList<Task>(), missedReminders(emptyList(), now))
    }
}
