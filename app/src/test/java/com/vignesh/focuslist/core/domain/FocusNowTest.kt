package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The Focus now card's rule, from `docs/decisions.md` D-012 as amended by D-035.
 *
 * The card is an assertion about one task, so the thing worth testing is when it
 * declines to make one. Most of what follows is about the card being absent, and
 * D-035 made that the common case rather than the exception.
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

    // --- the two reasons -----------------------------------------------------

    @Test
    fun aPausedSessionIsTheStrongestReason() {
        val tasks = listOf(
            task("timed", scheduledDate = today, reminderAt = now.minusHours(1)),
            task("paused", scheduledDate = today)
        )

        val card = focusNow(tasks, now, pausedTaskId = "paused")

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

        val card = focusNow(tasks, now, pausedTaskId = "inbox")

        assertEquals("inbox", card?.task?.id)
    }

    @Test
    fun aReminderThatHasPassedQualifies() {
        val tasks = listOf(task("a", scheduledDate = today, reminderAt = now.minusMinutes(5)))

        val card = focusNow(tasks, now)

        assertEquals("a", card?.task?.id)
        assertEquals(FocusNowReason.ReminderPassed, card?.reason)
    }

    /**
     * A reminder that fired on an earlier day still qualifies, and this is the
     * card's most common case since D-035. The app said it would interrupt, it
     * did, and the work is still outstanding.
     */
    @Test
    fun aReminderThatPassedOnAnEarlierDayQualifies() {
        val tasks = listOf(
            task("a", scheduledDate = today.minusDays(2), reminderAt = now.minusDays(2))
        )

        assertEquals(FocusNowReason.ReminderPassed, focusNow(tasks, now)?.reason)
    }

    @Test
    fun aReminderStillToComeDoesNotQualify() {
        val tasks = listOf(task("a", scheduledDate = today, reminderAt = now.plusMinutes(5)))

        // It will announce itself. That is what the Later today band is for.
        assertNull(focusNow(tasks, now))
    }

    /** A reminder exactly now has arrived. The boundary is inclusive. */
    @Test
    fun aReminderAtThisVeryMomentQualifies() {
        val tasks = listOf(task("a", scheduledDate = today, reminderAt = now))

        assertEquals(FocusNowReason.ReminderPassed, focusNow(tasks, now)?.reason)
    }

    // --- when the card stays away -------------------------------------------

    /**
     * D-035, and the point of it. A task scheduled for today carrying no time
     * used to be a reason of its own, which made the card the first row of the
     * band below it drawn larger, explained by that band's own label, on grounds
     * equally true of every other task in the band.
     */
    @Test
    fun aTaskScheduledForTodayWithNoTimeDoesNotQualify() {
        val tasks = listOf(task("a", scheduledDate = today))

        assertNull(focusNow(tasks, now))
    }

    /**
     * The ordinary day, and the shape of the screen most of the time: work
     * scheduled, nothing timed, nothing started. No card at all.
     */
    @Test
    fun aFullDayWithNoTimesAndNoSessionHasNoCard() {
        val tasks = listOf(
            task("a", scheduledDate = today),
            task("b", scheduledDate = today),
            task("c", scheduledDate = today)
        )

        assertNull(focusNow(tasks, now))
    }

    @Test
    fun nothingQualifiesWhenThereIsNothingToDo() {
        assertNull(focusNow(emptyList(), now))
    }

    /**
     * Overdue work is not a reason on its own. It belongs in the Overdue band,
     * where it needs a decision, and promoting it would answer "what should I do
     * now" with something the user has already chosen not to do. A reminder is
     * what makes the difference, and the test above covers that case.
     */
    @Test
    fun overdueWorkDoesNotQualifyOnItsOwn() {
        val tasks = listOf(task("a", scheduledDate = today.minusDays(3)))

        assertNull(focusNow(tasks, now))
    }

    @Test
    fun anUpcomingTaskDoesNotQualify() {
        val tasks = listOf(task("a", scheduledDate = today.plusDays(1)))

        assertNull(focusNow(tasks, now))
    }

    @Test
    fun anUndatedTaskDoesNotQualify() {
        val tasks = listOf(task("a"))

        assertNull(focusNow(tasks, now))
    }

    @Test
    fun aCompletedTaskDoesNotQualify() {
        val tasks = listOf(
            task("a", scheduledDate = today, reminderAt = now.minusHours(1), completedAt = createdAt)
        )

        assertNull(focusNow(tasks, now))
    }

    @Test
    fun aDeletedTaskDoesNotQualify() {
        val tasks = listOf(
            task("a", scheduledDate = today, reminderAt = now.minusHours(1), deletedAt = createdAt)
        )

        assertNull(focusNow(tasks, now))
    }

    /**
     * A paused session on a task completed from somewhere else stops asserting
     * it. Otherwise the card would go on offering to resume work that is done.
     */
    @Test
    fun aPausedTaskThatWasCompletedElsewhereStopsQualifying() {
        val tasks = listOf(task("a", scheduledDate = today, completedAt = createdAt))

        assertNull(focusNow(tasks, now, pausedTaskId = "a"))
    }

    // --- the tie-break -------------------------------------------------------

    /**
     * The reason decides first, so a weaker reason never outranks a stronger one
     * on time. The paused task carries no time at all and still wins against a
     * reminder that passed three hours ago.
     */
    @Test
    fun theReasonOutranksTheTime() {
        val tasks = listOf(
            task("passed", scheduledDate = today, reminderAt = now.minusHours(3)),
            task("paused", scheduledDate = today)
        )

        assertEquals("paused", focusNow(tasks, now, pausedTaskId = "paused")?.task?.id)
    }

    /** Then the earliest time, among tasks sharing a reason. */
    @Test
    fun theEarliestReminderWinsAmongPassedOnes() {
        val tasks = listOf(
            task("later", scheduledDate = today, reminderAt = now.minusMinutes(5)),
            task("earlier", scheduledDate = today, reminderAt = now.minusHours(3))
        )

        assertEquals("earlier", focusNow(tasks, now)?.task?.id)
    }

    /** Then the order Today already shows, which is the order given. */
    @Test
    fun theOrderGivenBreaksTheRemainingTie() {
        val tasks = listOf(
            task("first", scheduledDate = today, reminderAt = now.minusMinutes(30)),
            task("second", scheduledDate = today, reminderAt = now.minusMinutes(30))
        )

        assertEquals("first", focusNow(tasks, now)?.task?.id)
    }

    /**
     * The promoted task leaves its band, so it is never on screen twice. The
     * card and the list read the same rule from the same place.
     */
    @Test
    fun thePromotedTaskLeavesItsBand() {
        val tasks = listOf(
            task("a", scheduledDate = today, reminderAt = now.minusMinutes(5)),
            task("b", scheduledDate = today)
        )
        val card = focusNow(tasks, now)!!

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
