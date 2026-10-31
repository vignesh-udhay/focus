package app.tellcat.core.domain

import app.tellcat.core.notification.earlyAlarmId
import app.tellcat.core.notification.parseAlarmId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One early reminder, `docs/decisions.md` D-115: when it is owed, when it is
 * not, and what the row says about it.
 */
class EarlyReminderTest {

    private val ten: LocalDateTime = LocalDateTime.of(2026, 10, 31, 10, 0)
    private val told: Instant = Instant.parse("2026-10-31T03:30:00Z")

    private val dentist = Task(
        id = "dentist",
        title = "Call the dentist",
        createdAt = Instant.parse("2026-10-20T08:00:00Z"),
        reminderAt = ten,
        earlyReminder = EarlyReminder.OneHour
    )

    // --- the choice ----------------------------------------------------------

    @Test
    fun `the three choices are fifteen minutes, an hour and a day`() {
        assertEquals(listOf(15L, 60L, 1440L), EarlyReminder.entries.map { it.minutes })
    }

    @Test
    fun `a stored value reads back as its choice, and anything else as none`() {
        assertEquals(EarlyReminder.OneHour, EarlyReminder.ofMinutes(60))
        assertNull(EarlyReminder.ofMinutes(null))
        assertNull(EarlyReminder.ofMinutes(30))
    }

    @Test
    fun `the early moment is the reminder less the choice`() {
        assertEquals(ten.minusHours(1), dentist.earlyReminderAt)
        assertEquals(ten.minusDays(1), dentist.copy(earlyReminder = EarlyReminder.OneDay).earlyReminderAt)
    }

    // --- when it is owed -----------------------------------------------------

    @Test
    fun `an early reminder still to ring is pending at its moment`() {
        assertEquals(ten.minusHours(1), dentist.pendingEarlyReminder)
    }

    @Test
    fun `none without a reminder or without a choice`() {
        assertNull(dentist.copy(reminderAt = null).pendingEarlyReminder)
        assertNull(dentist.copy(earlyReminder = null).pendingEarlyReminder)
    }

    @Test
    fun `none once told`() {
        assertNull(dentist.copy(earlyDeliveredAt = told).pendingEarlyReminder)
    }

    /** Once the reminder has spoken or been snoozed, there is nothing to warn about. */
    @Test
    fun `none once the reminder itself has rung or been snoozed`() {
        assertNull(dentist.copy(reminderDeliveredAt = told).pendingEarlyReminder)
        assertNull(dentist.copy(snoozedUntil = ten.plusHours(1)).pendingEarlyReminder)
    }

    @Test
    fun `none for a completed or deleted task`() {
        assertNull(dentist.copy(completedAt = told).pendingEarlyReminder)
        assertNull(dentist.copy(deletedAt = told).pendingEarlyReminder)
    }

    @Test
    fun `a snooze of the early one moves it, and only before the reminder`() {
        assertEquals(ten.minusMinutes(20), dentist.copy(earlySnoozedUntil = ten.minusMinutes(20)).pendingEarlyReminder)
        assertNull(dentist.copy(earlySnoozedUntil = ten).pendingEarlyReminder)
    }

    @Test
    fun `a missed early reminder is owed while its reminder is ahead, and not after`() {
        val tasks = listOf(dentist)
        assertEquals(listOf(dentist), earlyRemindersOwed(tasks, ten.minusMinutes(30)))
        assertTrue(earlyRemindersOwed(tasks, ten).isEmpty())
    }

    // --- at save -------------------------------------------------------------

    @Test
    fun `an early moment already gone at save is settled, not owed`() {
        val now = ten.minusMinutes(30)
        assertTrue(earlyHasPassed(ten, EarlyReminder.OneHour, now))
        assertEquals(told, earlyDeliveredAtSave(ten, EarlyReminder.OneHour, now, told))
    }

    @Test
    fun `an early moment still ahead at save is owed`() {
        val now = ten.minusHours(2)
        assertFalse(earlyHasPassed(ten, EarlyReminder.OneHour, now))
        assertNull(earlyDeliveredAtSave(ten, EarlyReminder.OneHour, now, told))
    }

    /** To the minute, as a reminder is: the minute the user is in has not gone. */
    @Test
    fun `an early moment in the current minute has not passed`() {
        assertFalse(earlyHasPassed(ten, EarlyReminder.OneHour, ten.minusHours(1).plusSeconds(40)))
    }

    // --- snoozing it ---------------------------------------------------------

    @Test
    fun `only snoozes that land before the reminder are offered`() {
        // At 9:00 for a 10:00 reminder: ten minutes lands before, an hour does not.
        val offered = earlySnoozeOptions(ten.minusHours(1), ten)
        assertTrue(SnoozeOption.TenMinutes in offered)
        assertFalse(SnoozeOption.OneHour in offered)
    }

    @Test
    fun `none are offered when every snooze would reach the reminder`() {
        assertTrue(earlySnoozeOptions(ten.minusMinutes(5), ten).isEmpty())
    }

    // --- repeats -------------------------------------------------------------

    @Test
    fun `the next occurrence carries the choice and owes it again`() {
        val daily = dentist.copy(
            scheduledDate = ten.toLocalDate(),
            recurrence = Recurrence(RecurrenceUnit.DAILY),
            earlyDeliveredAt = told,
            earlySnoozedUntil = ten.minusMinutes(20)
        )

        val next = daily.nextRecurringInstance(today = LocalDate.of(2026, 10, 31), id = "next", createdAt = told)!!

        assertEquals(EarlyReminder.OneHour, next.earlyReminder)
        assertNull(next.earlyDeliveredAt)
        assertNull(next.earlySnoozedUntil)
    }

    // --- the row -------------------------------------------------------------

    @Test
    fun `the row keeps the reminder's time and says the early one beside it`() {
        assertEquals(RowReminder(ten, ReminderStage.Set, alsoBefore = EarlyReminder.OneHour), dentist.rowReminder())
    }

    @Test
    fun `once told, the row is the reminder alone`() {
        assertEquals(RowReminder(ten, ReminderStage.Set), dentist.copy(earlyDeliveredAt = told).rowReminder())
    }

    // --- its alarm -----------------------------------------------------------

    @Test
    fun `the early alarm's id reads back as the task and the early flag`() {
        assertEquals("dentist" to true, parseAlarmId(earlyAlarmId("dentist")))
        assertEquals("dentist" to false, parseAlarmId("dentist"))
    }
}
