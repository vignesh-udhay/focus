package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The Focus now card's rule, from `docs/decisions.md` D-012.
 *
 * The card is an assertion about one task, so the thing worth testing is when it
 * declines to make one. Most of what follows is about the card being absent.
 */
class FocusNowTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 8)
    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 8, 10, 0)
    private val createdAt: Instant = Instant.parse("2026-09-01T09:00:00Z")

    private fun task(
        id: String,
        scheduledDate: LocalDate? = null,
        reminderAt: LocalDateTime? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = Task(
        id = id,
        title = "Task $id",
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        reminderAt = reminderAt,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    // --- the three reasons ---------------------------------------------------

    @Test
    fun aPausedSessionIsTheStrongestReason() {
        val tasks = listOf(
            task("timed", scheduledDate = today, reminderAt = now.minusHours(1)),
            task("paused", scheduledDate = today)
        )

        val card = focusNow(tasks, today, now, pausedTaskId = "paused")

        assertEquals("paused", card?.task?.id)
        assertEquals(FocusNowReason.ResumePaused, card?.reason)
    }

    /**
     * The paused task need not be scheduled for today. A task focused from Inbox
     * and paused is still the thing the user was doing, and sending them back to
     * find it would be the app losing their place.
     */
    @Test
    fun aPausedTaskQualifiesWhateverListItCameFrom() {
        val tasks = listOf(task("inbox"))

        val card = focusNow(tasks, today, now, pausedTaskId = "inbox")

        assertEquals("inbox", card?.task?.id)
    }

    @Test
    fun aReminderThatHasPassedQualifies() {
        val tasks = listOf(task("a", scheduledDate = today, reminderAt = now.minusMinutes(5)))

        val card = focusNow(tasks, today, now)

        assertEquals("a", card?.task?.id)
        assertEquals(FocusNowReason.ReminderPassed, card?.reason)
    }

    @Test
    fun aReminderStillToComeDoesNotQualify() {
        val tasks = listOf(task("a", scheduledDate = today, reminderAt = now.plusMinutes(5)))

        // It will announce itself. That is what the Later today band is for.
        assertNull(focusNow(tasks, today, now))
    }

    /** A reminder exactly now has arrived. The boundary is inclusive. */
    @Test
    fun aReminderAtThisVeryMomentQualifies() {
        val tasks = listOf(task("a", scheduledDate = today, reminderAt = now))

        assertEquals(FocusNowReason.ReminderPassed, focusNow(tasks, today, now)?.reason)
    }

    @Test
    fun aTaskScheduledForTodayWithNoTimeQualifies() {
        val tasks = listOf(task("a", scheduledDate = today))

        assertEquals(FocusNowReason.NoTimeToday, focusNow(tasks, today, now)?.reason)
    }

    // --- when the card stays away -------------------------------------------

    @Test
    fun nothingQualifiesWhenThereIsNothingToDo() {
        assertNull(focusNow(emptyList(), today, now))
    }

    /**
     * Overdue work is deliberately not a reason. It belongs in the Overdue band,
     * where it needs a decision, and promoting it would answer "what should I do
     * now" with something the user has already chosen not to do.
     */
    @Test
    fun overdueWorkDoesNotQualifyOnItsOwn() {
        val tasks = listOf(task("a", scheduledDate = today.minusDays(3)))

        assertNull(focusNow(tasks, today, now))
    }

    @Test
    fun anUpcomingTaskDoesNotQualify() {
        val tasks = listOf(task("a", scheduledDate = today.plusDays(1)))

        assertNull(focusNow(tasks, today, now))
    }

    @Test
    fun anUndatedTaskDoesNotQualify() {
        val tasks = listOf(task("a"))

        assertNull(focusNow(tasks, today, now))
    }

    @Test
    fun aCompletedTaskDoesNotQualify() {
        val tasks = listOf(task("a", scheduledDate = today, completedAt = createdAt))

        assertNull(focusNow(tasks, today, now))
    }

    @Test
    fun aDeletedTaskDoesNotQualify() {
        val tasks = listOf(task("a", scheduledDate = today, deletedAt = createdAt))

        assertNull(focusNow(tasks, today, now))
    }

    /**
     * A paused session on a task completed from somewhere else stops asserting
     * it. Otherwise the card would go on offering to resume work that is done.
     */
    @Test
    fun aPausedTaskThatWasCompletedElsewhereStopsQualifying() {
        val tasks = listOf(task("a", scheduledDate = today, completedAt = createdAt))

        assertNull(focusNow(tasks, today, now, pausedTaskId = "a"))
    }

    // --- the tie-break -------------------------------------------------------

    /** The reason decides first, so a weak reason never outranks a strong one. */
    @Test
    fun theReasonOutranksTheTime() {
        val tasks = listOf(
            // Earlier, and the weaker reason.
            task("untimed", scheduledDate = today),
            task("passed", scheduledDate = today, reminderAt = now.minusMinutes(1))
        )

        assertEquals("passed", focusNow(tasks, today, now)?.task?.id)
    }

    /** Then the earliest time, among tasks sharing a reason. */
    @Test
    fun theEarliestReminderWinsAmongPassedOnes() {
        val tasks = listOf(
            task("later", scheduledDate = today, reminderAt = now.minusMinutes(5)),
            task("earlier", scheduledDate = today, reminderAt = now.minusHours(3))
        )

        assertEquals("earlier", focusNow(tasks, today, now)?.task?.id)
    }

    /** Then the order Today already shows, which is the order given. */
    @Test
    fun theOrderGivenBreaksTheRemainingTie() {
        val tasks = listOf(
            task("first", scheduledDate = today),
            task("second", scheduledDate = today)
        )

        assertEquals("first", focusNow(tasks, today, now)?.task?.id)
    }

    /**
     * The promoted task leaves its band, so it is never on screen twice. The
     * card and the list read the same rule from the same place.
     */
    @Test
    fun thePromotedTaskLeavesItsBand() {
        val tasks = listOf(
            task("a", scheduledDate = today),
            task("b", scheduledDate = today)
        )
        val card = focusNow(tasks, today, now)!!

        val sections = todaySections(tasks, today, promotedTaskId = card.task.id)

        assertEquals(listOf("b"), sections.flatMap { it.tasks }.map(Task::id))
    }

    @Test
    fun withNoCardTheListIsWholeAgain() {
        val tasks = listOf(
            task("a", scheduledDate = today),
            task("b", scheduledDate = today)
        )

        val sections = todaySections(tasks, today, promotedTaskId = null)

        assertEquals(listOf("a", "b"), sections.flatMap { it.tasks }.map(Task::id))
    }
}
