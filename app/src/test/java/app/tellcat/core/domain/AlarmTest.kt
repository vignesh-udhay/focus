package app.tellcat.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * `docs/decisions.md` D-102: the rules a ringing alarm keeps, without a phone.
 *
 * The receiver rings by [alarmMode] and the editor promises by it, so one test
 * of the function covers both. [stillRingsFor] is the whole of "one state, two
 * paths" (R-002): every way a ringing alarm should stop is a way this turns
 * false.
 */
class AlarmTest {

    private val nine: LocalDateTime = LocalDateTime.of(2026, 10, 27, 9, 0)

    private val alarm = Task(
        id = "pills",
        title = "Take the pills",
        createdAt = Instant.parse("2026-10-20T08:00:00Z"),
        reminderAt = nine,
        ringsLikeAlarm = true
    )

    // How it rings

    @Test
    fun `full screen where allowed`() {
        assertEquals(AlarmMode.FullScreen, alarmMode(alarmChannelOn = true, fullScreenAllowed = true))
    }

    @Test
    fun `a banner that rings until answered without the full-screen grant`() {
        assertEquals(AlarmMode.Banner, alarmMode(alarmChannelOn = true, fullScreenAllowed = false))
    }

    /** A hidden foreground notification would ring with nothing to stop it. */
    @Test
    fun `a plain reminder when the Alarms channel is off, whatever else is allowed`() {
        assertEquals(AlarmMode.PlainReminder, alarmMode(alarmChannelOn = false, fullScreenAllowed = true))
        assertEquals(AlarmMode.PlainReminder, alarmMode(alarmChannelOn = false, fullScreenAllowed = false))
    }

    @Test
    fun `it rings for ten minutes unanswered, Android Clock's own default`() {
        assertEquals(Duration.ofMinutes(10), AlarmRingLimit)
    }

    @Test
    fun `the alarm's one snooze is ten minutes`() {
        assertEquals(SnoozeOption.TenMinutes, AlarmSnooze)
    }

    // One state, two paths: what stops a ringing alarm

    @Test
    fun `an alarm still rings for the task it rang for`() {
        assertTrue(alarm.stillRingsFor(nine))
        // Told since: still ringing. Marking it announced is not an answer.
        assertTrue(alarm.copy(reminderDeliveredAt = Instant.parse("2026-10-27T03:30:02Z")).stillRingsFor(nine))
    }

    @Test
    fun `done from the notification stops it`() {
        assertFalse(alarm.copy(completedAt = Instant.parse("2026-10-27T03:31:00Z")).stillRingsFor(nine))
    }

    @Test
    fun `deleting the task stops it`() {
        assertFalse(alarm.copy(deletedAt = Instant.parse("2026-10-27T03:31:00Z")).stillRingsFor(nine))
        assertFalse(null.stillRingsFor(nine))
    }

    @Test
    fun `a snooze from the notification stops it, and moves both`() {
        assertFalse(alarm.copy(snoozedUntil = nine.plusMinutes(10)).stillRingsFor(nine))
    }

    @Test
    fun `moving the reminder or switching the alarm off stops it`() {
        assertFalse(alarm.copy(reminderAt = nine.plusHours(1)).stillRingsFor(nine))
        assertFalse(alarm.copy(ringsLikeAlarm = false).stillRingsFor(nine))
    }

    // Stale actions, R-002

    @Test
    fun `an action for the moment the task is due at applies`() {
        assertTrue(isCurrentAction(alarm, nine))
    }

    @Test
    fun `an action from before a snooze or a move is ignored`() {
        assertFalse(isCurrentAction(alarm.copy(snoozedUntil = nine.plusMinutes(10)), nine))
        assertFalse(isCurrentAction(alarm.copy(reminderAt = nine.plusDays(1)), nine))
    }

    @Test
    fun `an action on a completed or deleted task is ignored`() {
        assertFalse(isCurrentAction(alarm.copy(completedAt = Instant.parse("2026-10-27T03:31:00Z")), nine))
        assertFalse(isCurrentAction(alarm.copy(deletedAt = Instant.parse("2026-10-27T03:31:00Z")), nine))
        assertFalse(isCurrentAction(null, nine))
    }

    /** Posted before the rule existed: taken as current rather than refused. */
    @Test
    fun `an action that names no moment applies to an outstanding task`() {
        assertTrue(isCurrentAction(alarm, null))
    }

    // Reminder health lists the check only when it matters

    @Test
    fun `an outstanding alarm-style reminder is counted`() {
        assertTrue(hasAlarmStyleReminders(listOf(alarm)))
    }

    @Test
    fun `plain, finished and empty alarms are not`() {
        assertFalse(hasAlarmStyleReminders(listOf(alarm.copy(ringsLikeAlarm = false))))
        assertFalse(hasAlarmStyleReminders(listOf(alarm.copy(completedAt = Instant.parse("2026-10-27T03:31:00Z")))))
        assertFalse(hasAlarmStyleReminders(listOf(alarm.copy(deletedAt = Instant.parse("2026-10-27T03:31:00Z")))))
        assertFalse(hasAlarmStyleReminders(listOf(alarm.copy(reminderAt = null))))
        assertFalse(hasAlarmStyleReminders(emptyList()))
    }
}
