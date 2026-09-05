package com.vignesh.focuslist.core.notification

import com.vignesh.focuslist.core.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The reconciliation, without a device.
 *
 * This is the whole reminder subsystem's decision-making. What actually
 * reaches `AlarmManager` is a handful of lines in `AndroidReminderAlarms`;
 * everything that could be wrong about *which* alarms should exist is here,
 * where a phone that was off for nine hours and a user who flew to another
 * timezone are both just parameters.
 */
class ReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /** 2026-09-05, 12:00 local in [zone]. */
    private val now: Instant = Instant.parse("2026-09-05T06:30:00Z")

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")

    private class RecordingAlarms : ReminderAlarms {
        val scheduled = linkedMapOf<String, Instant>()
        val cancelled = mutableListOf<String>()

        override fun schedule(taskId: String, at: Instant) {
            scheduled[taskId] = at
        }

        override fun cancel(taskId: String) {
            cancelled += taskId
        }

        override fun canScheduleExact(): Boolean = true
    }

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

    private fun scheduler(alarms: ReminderAlarms, at: ZoneId = zone, clock: Instant = now) =
        ReminderScheduler(alarms, zone = { at }, clock = { clock })

    // 1. Scheduling what is owed

    @Test
    fun `a future reminder is scheduled at its local time`() {
        val alarms = RecordingAlarms()
        // 14:00 in Kolkata is 08:30 UTC.
        val tasks = listOf(task("a", LocalDateTime.of(2026, 9, 5, 14, 0)))

        scheduler(alarms).reconcile(tasks)

        assertEquals(mapOf("a" to Instant.parse("2026-09-05T08:30:00Z")), alarms.scheduled)
    }

    @Test
    fun `a missed reminder is scheduled for now, not for the past`() {
        val alarms = RecordingAlarms()
        // 09:00 local, three hours before the current 12:00.
        val tasks = listOf(task("a", LocalDateTime.of(2026, 9, 5, 9, 0)))

        scheduler(alarms).reconcile(tasks)

        // Scheduling it at its original moment would mean it never arrives.
        assertEquals(mapOf("a" to now), alarms.scheduled)
    }

    @Test
    fun `several reminders are all scheduled`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("a", LocalDateTime.of(2026, 9, 5, 14, 0)),
            task("b", LocalDateTime.of(2026, 9, 5, 18, 0)),
            task("c", LocalDateTime.of(2026, 9, 6, 9, 0))
        )

        scheduler(alarms).reconcile(tasks)

        assertEquals(setOf("a", "b", "c"), alarms.scheduled.keys)
    }

    // 2. Cancelling what is not

    @Test
    fun `a task with no reminder is cancelled`() {
        val alarms = RecordingAlarms()

        scheduler(alarms).reconcile(listOf(task("a")))

        assertEquals(listOf("a"), alarms.cancelled)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `completing a task cancels its reminder`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("a", LocalDateTime.of(2026, 9, 5, 14, 0), completedAt = createdAt)
        )

        scheduler(alarms).reconcile(tasks)

        assertEquals(listOf("a"), alarms.cancelled)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `deleting a task cancels its reminder`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("a", LocalDateTime.of(2026, 9, 5, 14, 0), deletedAt = createdAt)
        )

        scheduler(alarms).reconcile(tasks)

        assertEquals(listOf("a"), alarms.cancelled)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `every task is either scheduled or cancelled, never both and never neither`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("future", LocalDateTime.of(2026, 9, 5, 14, 0)),
            task("missed", LocalDateTime.of(2026, 9, 5, 9, 0)),
            task("none"),
            task("done", LocalDateTime.of(2026, 9, 5, 14, 0), completedAt = createdAt)
        )

        scheduler(alarms).reconcile(tasks)

        assertEquals(setOf("future", "missed"), alarms.scheduled.keys)
        assertEquals(listOf("none", "done"), alarms.cancelled)
        assertEquals(
            emptySet<String>(),
            alarms.scheduled.keys intersect alarms.cancelled.toSet()
        )
        assertEquals(
            tasks.map { it.id }.toSet(),
            alarms.scheduled.keys + alarms.cancelled.toSet()
        )
    }

    // 3. Running it again

    @Test
    fun `reconciling twice schedules the same thing, not two things`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(task("a", LocalDateTime.of(2026, 9, 5, 14, 0)))
        val scheduler = scheduler(alarms)

        scheduler.reconcile(tasks)
        scheduler.reconcile(tasks)

        // The map is keyed by task, so a second pass overwrites rather than
        // adds. That mirrors FLAG_UPDATE_CURRENT, which is what makes running
        // this on every storage change safe.
        assertEquals(1, alarms.scheduled.size)
        assertEquals(Instant.parse("2026-09-05T08:30:00Z"), alarms.scheduled["a"])
    }

    @Test
    fun `an empty task list asks for nothing`() {
        val alarms = RecordingAlarms()

        scheduler(alarms).reconcile(emptyList())

        assertTrue(alarms.scheduled.isEmpty())
        assertTrue(alarms.cancelled.isEmpty())
    }

    // 4. The timezone, which is the reason this reruns on TIMEZONE_CHANGED

    @Test
    fun `the same reminder resolves to a different instant in a different zone`() {
        val reminder = LocalDateTime.of(2026, 9, 5, 14, 0)

        val kolkata = RecordingAlarms()
        scheduler(kolkata, at = ZoneId.of("Asia/Kolkata")).reconcile(listOf(task("a", reminder)))

        val london = RecordingAlarms()
        scheduler(london, at = ZoneId.of("Europe/London")).reconcile(listOf(task("a", reminder)))

        assertEquals(Instant.parse("2026-09-05T08:30:00Z"), kolkata.scheduled["a"])
        assertEquals(Instant.parse("2026-09-05T13:00:00Z"), london.scheduled["a"])
    }

    @Test
    fun `moving east can turn a pending reminder into a missed one`() {
        val alarms = RecordingAlarms()
        // 14:00 local. In London that is still ahead of the 06:30Z clock; in
        // Kolkata, 14:00 local has already gone by 12:00 local.
        val tasks = listOf(task("a", LocalDateTime.of(2026, 9, 5, 11, 0)))

        scheduler(alarms, at = ZoneId.of("Asia/Kolkata")).reconcile(tasks)

        // 11:00 Kolkata was an hour ago, so it is owed immediately rather than
        // dropped. This is the case a plain reschedule would silently lose.
        assertEquals(now, alarms.scheduled["a"])
    }
}
