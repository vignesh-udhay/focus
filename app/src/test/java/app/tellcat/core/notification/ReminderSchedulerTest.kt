package app.tellcat.core.notification

import app.tellcat.core.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private class RecordingAlarms(private val exact: Boolean = true) : ReminderAlarms {
        val scheduled = linkedMapOf<String, Instant>()
        val cancelled = mutableListOf<String>()
        var backstop: Instant? = Instant.EPOCH

        override fun schedule(taskId: String, at: Instant) {
            scheduled[taskId] = at
        }

        override fun cancel(taskId: String) {
            cancelled += taskId
        }

        override fun scheduleBackstop(at: Instant?) {
            backstop = at
        }

        override fun canScheduleExact(): Boolean = exact
    }

    private fun task(
        id: String,
        reminderAt: LocalDateTime? = null,
        reminderDeliveredAt: Instant? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = Task(
        id = id,
        title = "Task $id",
        createdAt = createdAt,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    private fun scheduler(alarms: ReminderAlarms, at: ZoneId = zone, clock: Instant = now) =
        ReminderScheduler(alarms, zone = { at }, clock = { clock })

    /** Refuses the alarm for [refused], as Android does past its cap. */
    private class RefusingAlarms(private val refused: String) : ReminderAlarms {
        val scheduled = linkedMapOf<String, Instant>()

        override fun schedule(taskId: String, at: Instant) {
            if (taskId == refused) throw IllegalStateException("Maximum limit of concurrent alarms")
            scheduled[taskId] = at
        }

        override fun cancel(taskId: String) = Unit

        override fun scheduleBackstop(at: Instant?) = Unit

        override fun canScheduleExact(): Boolean = true
    }

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

    /**
     * D-099. The test above hands the deleted task in with the live ones, and
     * production never did: the stored stream leaves deleted rows out, so the
     * alarm was never reached. They arrive separately now.
     */
    @Test
    fun `a deleted task's alarm is cancelled though it is not among the live tasks`() {
        val alarms = RecordingAlarms()
        val deleted = listOf(task("gone", LocalDateTime.of(2026, 9, 5, 14, 0), deletedAt = createdAt))

        scheduler(alarms).reconcile(tasks = emptyList(), deleted = deleted)

        assertEquals(listOf("gone"), alarms.cancelled)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `a deleted task whose moment has passed is left alone, its alarm has already fired`() {
        val alarms = RecordingAlarms()
        // 09:00 local, three hours before the current 12:00.
        val deleted = listOf(task("old", LocalDateTime.of(2026, 9, 5, 9, 0), deletedAt = createdAt))

        scheduler(alarms).reconcile(tasks = emptyList(), deleted = deleted)

        assertTrue(alarms.cancelled.isEmpty())
    }

    @Test
    fun `a deleted task snoozed past now is cancelled at its snooze`() {
        val alarms = RecordingAlarms()
        // The reminder itself has gone by, but the snooze is still ahead and
        // holds the alarm.
        val deleted = listOf(
            task("snoozed", LocalDateTime.of(2026, 9, 5, 9, 0), deletedAt = createdAt)
                .copy(snoozedUntil = LocalDateTime.of(2026, 9, 5, 13, 0))
        )

        scheduler(alarms).reconcile(tasks = emptyList(), deleted = deleted)

        assertEquals(listOf("snoozed"), alarms.cancelled)
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

    @Test
    fun `an announced reminder is cancelled, not announced again`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("a", LocalDateTime.of(2026, 9, 5, 9, 0), reminderDeliveredAt = createdAt)
        )

        scheduler(alarms).reconcile(tasks)

        assertTrue(alarms.scheduled.isEmpty())
        assertEquals(listOf("a"), alarms.cancelled)
    }

    @Test
    fun `reconciling repeatedly after delivery never re-announces`() {
        val alarms = RecordingAlarms()
        // What every task edit, restart and clock change would do.
        val tasks = listOf(
            task("a", LocalDateTime.of(2026, 9, 5, 9, 0), reminderDeliveredAt = createdAt)
        )
        val scheduler = scheduler(alarms)

        repeat(5) { scheduler.reconcile(tasks) }

        assertTrue(alarms.scheduled.isEmpty())
        // The positive half. Without it this passes on a reconcile that does
        // nothing at all, which is the same evidence as a reconcile that
        // correctly refuses to re-announce.
        assertEquals(List(5) { "a" }, alarms.cancelled)
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

    // D-078: one refusal does not stop the rest

    @Test
    fun `an alarm the system refuses does not stop the others being scheduled`() {
        val alarms = RefusingAlarms(refused = "b")
        val failed = mutableListOf<String>()
        val tasks = listOf(
            task("a", LocalDateTime.of(2026, 9, 5, 13, 0)),
            task("b", LocalDateTime.of(2026, 9, 5, 14, 0)),
            task("c", LocalDateTime.of(2026, 9, 5, 15, 0))
        )

        val failures = ReminderScheduler(
            alarms,
            zone = { zone },
            clock = { now },
            onFailure = { id, _ -> failed += id }
        ).reconcile(tasks).failures

        assertEquals(1, failures)
        assertEquals(listOf("b"), failed)
        assertEquals(setOf("a", "c"), alarms.scheduled.keys)
    }

    @Test
    fun `the soonest reminders are scheduled first, and no more than the cap`() {
        val alarms = RecordingAlarms()
        // Given latest first, so the order scheduled is the scheduler's own.
        val tasks = (MaxScheduledReminders + 5 downTo 1).map { minutes ->
            task("t$minutes", LocalDateTime.of(2026, 9, 5, 12, 0).plusMinutes(minutes.toLong()))
        }

        scheduler(alarms).reconcile(tasks)

        assertEquals(MaxScheduledReminders, alarms.scheduled.size)
        assertEquals("t1", alarms.scheduled.keys.first())
        assertEquals("t$MaxScheduledReminders", alarms.scheduled.keys.last())
    }

    @Test
    fun `a snoozed reminder is scheduled at its snooze`() {
        val alarms = RecordingAlarms()
        val snoozed = task("a", LocalDateTime.of(2026, 9, 5, 9, 0))
            .copy(snoozedUntil = LocalDateTime.of(2026, 9, 5, 14, 0))

        scheduler(alarms).reconcile(listOf(snoozed))

        assertEquals(mapOf("a" to Instant.parse("2026-09-05T08:30:00Z")), alarms.scheduled)
    }

    // D-079: what a pass reports, for the copy kept for the locked phone

    @Test
    fun `a pass reports the reminders it gave alarms to, and not the refused ones`() {
        val alarms = RefusingAlarms(refused = "b")
        val tasks = listOf(
            task("a", LocalDateTime.of(2026, 9, 5, 13, 0)),
            task("b", LocalDateTime.of(2026, 9, 5, 14, 0)),
            task("done", LocalDateTime.of(2026, 9, 5, 15, 0), completedAt = createdAt)
        )

        val result = scheduler(alarms).reconcile(tasks)

        assertEquals(
            listOf(ScheduledReminder("a", "Task a", LocalDateTime.of(2026, 9, 5, 13, 0))),
            result.scheduled
        )
        assertEquals(1, result.failures)
    }

    /** D-102: the copy kept for a locked phone knows which reminders are alarms. */
    @Test
    fun `a pass reports which reminders ring like an alarm`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("alarm", LocalDateTime.of(2026, 9, 5, 14, 0)).copy(ringsLikeAlarm = true),
            task("plain", LocalDateTime.of(2026, 9, 5, 15, 0))
        )

        val result = scheduler(alarms).reconcile(tasks)

        assertEquals(
            mapOf("alarm" to true, "plain" to false),
            result.scheduled.associate { it.taskId to it.ringsLikeAlarm }
        )
    }

    // D-103: the backstop, for exact alarms taken away without a word

    @Test
    fun `the backstop stands a minute after the soonest future reminder`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("later", LocalDateTime.of(2026, 9, 5, 18, 0)),
            task("soon", LocalDateTime.of(2026, 9, 5, 14, 0))
        )

        scheduler(alarms).reconcile(tasks)

        // 14:00 in Kolkata is 08:30 UTC.
        assertEquals(Instant.parse("2026-09-05T08:31:00Z"), alarms.backstop)
    }

    /**
     * A missed reminder is set for now, and one that cannot be shown stays
     * missed. A backstop aimed at it would wake the phone every minute.
     */
    @Test
    fun `a missed reminder does not move the backstop`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("missed", LocalDateTime.of(2026, 9, 5, 9, 0)),
            task("future", LocalDateTime.of(2026, 9, 5, 14, 0))
        )

        scheduler(alarms).reconcile(tasks)

        assertEquals(Instant.parse("2026-09-05T08:31:00Z"), alarms.backstop)
    }

    @Test
    fun `no backstop with nothing still to come`() {
        val alarms = RecordingAlarms()
        val tasks = listOf(
            task("missed", LocalDateTime.of(2026, 9, 5, 9, 0)),
            task("told", LocalDateTime.of(2026, 9, 5, 14, 0), reminderDeliveredAt = now),
            task("none")
        )

        scheduler(alarms).reconcile(tasks)

        assertNull(alarms.backstop)
    }

    /** Without the grant every alarm is inexact already, and survives as it is. */
    @Test
    fun `no backstop without the exact-alarm grant`() {
        val alarms = RecordingAlarms(exact = false)

        scheduler(alarms).reconcile(listOf(task("a", LocalDateTime.of(2026, 9, 5, 14, 0))))

        assertNull(alarms.backstop)
    }

    @Test
    fun `a refused reminder does not get the backstop`() {
        val alarms = RefusingAlarms(refused = "soon")
        val recorded = mutableListOf<Instant?>()
        val backstopping = object : ReminderAlarms by alarms {
            override fun scheduleBackstop(at: Instant?) {
                recorded += at
            }
        }
        val tasks = listOf(
            task("soon", LocalDateTime.of(2026, 9, 5, 14, 0)),
            task("later", LocalDateTime.of(2026, 9, 5, 18, 0))
        )

        scheduler(backstopping).reconcile(tasks)

        // 18:00 in Kolkata is 12:30 UTC.
        assertEquals(listOf<Instant?>(Instant.parse("2026-09-05T12:31:00Z")), recorded)
    }
}
