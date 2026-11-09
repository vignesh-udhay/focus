package app.tellcat.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Move all to today's rules. `docs/decisions.md` D-127. */
class OverdueMoveTest {

    // A Thursday, at 2 PM.
    private val today = LocalDate.of(2026, 11, 5)
    private val yesterday = today.minusDays(1)
    private val now: LocalDateTime = today.atTime(14, 0)
    private val nowInstant: Instant = Instant.parse("2026-11-05T08:30:00Z")
    private val delivered: Instant = Instant.parse("2026-11-04T03:30:00Z")
    private val createdAt: Instant = Instant.parse("2026-11-01T09:00:00Z")

    private fun task(
        id: String,
        scheduledDate: LocalDate? = yesterday,
        reminderAt: LocalDateTime? = null,
        reminderDeliveredAt: Instant? = null,
        recurrence: Recurrence? = null,
        spawnedFromId: String? = null,
        completedAt: Instant? = null
    ) = Task(
        id = id,
        title = "Task $id",
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt,
        recurrence = recurrence,
        spawnedFromId = spawnedFromId,
        completedAt = completedAt
    )

    private fun Task.moved() = movedToToday(today, now, nowInstant)

    // Which tasks move

    @Test
    fun theOverdueOpenTasksMoveAndNothingElseDoes() {
        val tasks = listOf(
            task("late"),
            task("today", scheduledDate = today),
            task("tomorrow", scheduledDate = today.plusDays(1)),
            task("undated", scheduledDate = null),
            task("done", completedAt = delivered)
        )

        val plan = overdueMovePlan(tasks, today)

        assertEquals(listOf("late"), plan.move.map(Task::id))
        assertTrue(plan.stay.isEmpty())
    }

    @Test
    fun aDailyOccurrenceWhoseNextIsAlreadyOnTodayStays() {
        val daily = Recurrence(RecurrenceUnit.DAILY)
        val missed = task("missed", recurrence = daily, reminderAt = yesterday.atTime(21, 0), reminderDeliveredAt = delivered)
        val todays = task("todays", scheduledDate = today, recurrence = daily, spawnedFromId = "missed")

        val plan = overdueMovePlan(listOf(missed, todays), today)

        assertTrue(plan.move.isEmpty())
        assertEquals(listOf("missed"), plan.stay.map(Task::id))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun anOccurrenceWhoseNextIsAlsoOverdueStaysToo() {
        val daily = Recurrence(RecurrenceUnit.DAILY)
        val monday = task("monday", scheduledDate = today.minusDays(3), recurrence = daily)
        val tuesday = task("tuesday", scheduledDate = today.minusDays(2), recurrence = daily, spawnedFromId = "monday")

        val plan = overdueMovePlan(listOf(monday, tuesday), today)

        assertEquals(listOf("monday"), plan.stay.map(Task::id))
        assertEquals(listOf("tuesday"), plan.move.map(Task::id))
    }

    @Test
    fun aWeeklyOccurrenceWhoseNextIsLaterMoves() {
        val weekly = Recurrence(RecurrenceUnit.WEEKLY, weekdays = setOf(DayOfWeek.WEDNESDAY))
        val missed = task("missed", recurrence = weekly)
        val next = task("next", scheduledDate = yesterday.plusWeeks(1), recurrence = weekly, spawnedFromId = "missed")

        val plan = overdueMovePlan(listOf(missed, next), today)

        assertEquals(listOf("missed"), plan.move.map(Task::id))
    }

    @Test
    fun aDeletedNextOccurrenceDoesNotHoldItsPredecessorBack() {
        val daily = Recurrence(RecurrenceUnit.DAILY)
        val missed = task("missed", recurrence = daily)
        val gone = task("gone", scheduledDate = today, recurrence = daily, spawnedFromId = "missed")
            .copy(deletedAt = delivered)

        assertEquals(listOf("missed"), overdueMovePlan(listOf(missed, gone), today).move.map(Task::id))
    }

    // What a moved task becomes

    @Test
    fun aTaskWithNoReminderOnlyChangesDay() {
        val moved = task("a").moved()

        assertEquals(today, moved.scheduledDate)
        assertNull(moved.reminderAt)
    }

    @Test
    fun aTimeStillAheadRingsAgainOnToday() {
        val moved = task("bank", reminderAt = yesterday.atTime(18, 0), reminderDeliveredAt = delivered)
            .copy(followUpAt = yesterday.atTime(18, 10), snoozedUntil = yesterday.atTime(19, 0))
            .moved()

        assertEquals(today.atTime(18, 0), moved.reminderAt)
        assertNull(moved.reminderDeliveredAt)
        assertNull(moved.snoozedUntil)
        assertNull(moved.followUpAt)
        assertTrue(moved.hasLiveReminder())
    }

    @Test
    fun aTimeGoneIsClearedWithItsEarlyReminderAndAlarmStyle() {
        val moved = task("rent", reminderAt = yesterday.atTime(9, 0), reminderDeliveredAt = delivered)
            .copy(ringsLikeAlarm = true, earlyReminder = EarlyReminder.OneHour, earlyDeliveredAt = delivered)
            .moved()

        assertEquals(today, moved.scheduledDate)
        assertNull(moved.reminderAt)
        assertNull(moved.reminderDeliveredAt)
        assertFalse(moved.ringsLikeAlarm)
        assertNull(moved.earlyReminder)
        assertNull(moved.earlyDeliveredAt)
        assertEquals(TodayBand.NO_REMINDER, todayBandOf(moved, today))
    }

    @Test
    fun aTimeGoneThatNeverRangIsClearedToo() {
        val moved = task("owed", reminderAt = yesterday.atTime(9, 0)).moved()

        assertNull(moved.reminderAt)
        assertFalse(moved.hasLiveReminder())
    }

    @Test
    fun aSnoozeStillToRingIsKept() {
        // Rang yesterday, snoozed to this morning, rang, snoozed to 5 PM.
        val snoozed = task("rent", reminderAt = yesterday.atTime(9, 0))
            .copy(snoozedUntil = today.atTime(17, 0), followUpAt = today.atTime(9, 10))

        val moved = snoozed.moved()

        assertEquals(today.atTime(9, 0), moved.reminderAt)
        assertEquals(today.atTime(17, 0), moved.snoozedUntil)
        assertEquals(today.atTime(17, 0), moved.ringsAt)
        assertNull(moved.followUpAt)
        assertTrue(moved.hasLiveReminder())
        assertEquals(TodayBand.WITH_REMINDER, todayBandOf(moved, today))
    }

    @Test
    fun aSnoozeThatHasAlreadyRungIsNotKept() {
        val moved = task("rent", reminderAt = yesterday.atTime(9, 0), reminderDeliveredAt = delivered)
            .copy(snoozedUntil = today.atTime(17, 0))
            .moved()

        assertNull(moved.reminderAt)
        assertNull(moved.snoozedUntil)
    }

    @Test
    fun anEarlyReminderStillAheadIsOwedAndOneGoneIsSettled() {
        val evening = task("bank", reminderAt = yesterday.atTime(18, 0), reminderDeliveredAt = delivered)
            .copy(earlyReminder = EarlyReminder.OneHour, earlyDeliveredAt = delivered)
        val soon = task("soon", reminderAt = yesterday.atTime(14, 30), reminderDeliveredAt = delivered)
            .copy(earlyReminder = EarlyReminder.OneHour, earlyDeliveredAt = delivered)

        assertNull(evening.moved().earlyDeliveredAt)
        assertEquals(nowInstant, soon.moved().earlyDeliveredAt)
    }

    @Test
    fun nothingMovedIsLeftOwedInThePast() {
        val tasks = listOf(
            task("gone", reminderAt = yesterday.atTime(9, 0)),
            task("ahead", reminderAt = yesterday.atTime(18, 0), reminderDeliveredAt = delivered),
            task("snoozed", reminderAt = yesterday.atTime(9, 0)).copy(snoozedUntil = today.atTime(17, 0))
        )

        tasks.map { it.moved() }.filter { it.hasLiveReminder() }.forEach { moved ->
            assertTrue(moved.ringsAt!!.isAfter(now))
        }
    }

    @Test
    fun aMoveKeepsTheSeriesDay() {
        val monthly = task("rent", recurrence = Recurrence(RecurrenceUnit.MONTHLY))
            .copy(recurrenceAnchor = java.time.MonthDay.of(11, 4))

        assertEquals(monthly.recurrenceAnchor, monthly.moved().recurrenceAnchor)
        assertEquals(monthly.recurrence, monthly.moved().recurrence)
    }

    // Undo

    @Test
    fun undoPutsBackEverythingTheMoveWroteAndNothingElse() {
        val before = task("rent", reminderAt = yesterday.atTime(9, 0), reminderDeliveredAt = delivered)
            .copy(ringsLikeAlarm = true, earlyReminder = EarlyReminder.OneHour, earlyDeliveredAt = delivered)
        val renamedSince = before.moved().copy(title = "Pay the rent")

        val restored = restoredFromMove(renamedSince, before)

        assertEquals(before.copy(title = "Pay the rent"), restored)
    }
}
