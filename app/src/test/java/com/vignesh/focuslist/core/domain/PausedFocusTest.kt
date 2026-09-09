package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The paused session card's rule, from `docs/decisions.md` D-048.
 *
 * **This was `FocusNowTest`, and most of it tested reasons that no longer exist.**
 * D-012 gave the card three, D-035 cut one and D-048 cut the second, which took
 * the ranking and the tie-break with it. What is left is a lookup, so the tests
 * that matter are the ones that pin what does *not* draw a card: a rule this small
 * is easy to widen again by accident.
 */
class PausedFocusTest {

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

    // --- what draws the card -------------------------------------------------

    @Test
    fun aPausedSessionDrawsTheCard() {
        val tasks = listOf(
            task("other", scheduledDate = today),
            task("paused", scheduledDate = today)
        )

        assertEquals("paused", pausedFocusTask(tasks, pausedTaskId = "paused")?.id)
    }

    /**
     * The paused task need not be scheduled for today. A task focused from Inbox
     * and paused is still the thing the user was doing, and sending them back to
     * find it would be the app losing their place.
     */
    @Test
    fun aPausedTaskQualifiesWhateverListItCameFrom() {
        val tasks = listOf(task("inbox"))

        assertEquals("inbox", pausedFocusTask(tasks, pausedTaskId = "inbox")?.id)
    }

    // --- when the card stays away -------------------------------------------

    @Test
    fun noPausedSessionMeansNoCard() {
        val tasks = listOf(task("a", scheduledDate = today))

        assertNull(pausedFocusTask(tasks, pausedTaskId = null))
    }

    @Test
    fun nothingQualifiesWhenThereIsNothingToDo() {
        assertNull(pausedFocusTask(emptyList(), pausedTaskId = null))
    }

    /** A session paused on a task that has since been deleted for good. */
    @Test
    fun aPausedIdMatchingNoTaskDrawsNothing() {
        val tasks = listOf(task("a", scheduledDate = today))

        assertNull(pausedFocusTask(tasks, pausedTaskId = "gone"))
    }

    /**
     * A paused session on a task completed from somewhere else stops asserting
     * it. Otherwise the card would go on offering to resume work that is done.
     */
    @Test
    fun aPausedTaskThatWasCompletedElsewhereStopsQualifying() {
        val tasks = listOf(task("a", scheduledDate = today, completedAt = createdAt))

        assertNull(pausedFocusTask(tasks, pausedTaskId = "a"))
    }

    @Test
    fun aPausedTaskThatWasDeletedStopsQualifying() {
        val tasks = listOf(task("a", scheduledDate = today, deletedAt = createdAt))

        assertNull(pausedFocusTask(tasks, pausedTaskId = "a"))
    }

    // --- the reasons D-035 and D-048 removed ---------------------------------

    /**
     * D-048, and the point of it. A reminder that fired and was not acted on used
     * to draw the card. It named a task sitting in the Overdue band directly
     * below, under a label that already says the work is late, carrying a time the
     * row's own metadata already prints.
     */
    @Test
    fun aReminderThatHasPassedDoesNotDrawTheCard() {
        val tasks = listOf(task("a", scheduledDate = today, reminderAt = now.minusMinutes(5)))

        assertNull(pausedFocusTask(tasks, pausedTaskId = null))
    }

    @Test
    fun aReminderThatPassedOnAnEarlierDayDoesNotDrawTheCard() {
        val tasks = listOf(
            task("a", scheduledDate = today.minusDays(2), reminderAt = now.minusDays(2))
        )

        assertNull(pausedFocusTask(tasks, pausedTaskId = null))
    }

    /**
     * D-035. A task scheduled for today carrying no time was the third reason, and
     * it made the card the first row of the band below it drawn larger, explained
     * by that band's own label.
     */
    @Test
    fun aTaskScheduledForTodayWithNoTimeDoesNotDrawTheCard() {
        val tasks = listOf(task("a", scheduledDate = today))

        assertNull(pausedFocusTask(tasks, pausedTaskId = null))
    }

    /**
     * The ordinary day, and the shape of the screen almost all of the time: work
     * scheduled, some of it timed, some of it late, nothing started. No card.
     */
    @Test
    fun aFullDayWithNoSessionHasNoCard() {
        val tasks = listOf(
            task("a", scheduledDate = today),
            task("b", scheduledDate = today, reminderAt = now.minusHours(2)),
            task("c", scheduledDate = today.minusDays(3)),
            task("d", scheduledDate = today.plusDays(1))
        )

        assertNull(pausedFocusTask(tasks, pausedTaskId = null))
    }

    // --- the list keeps its task ---------------------------------------------

    /**
     * D-048's other half. The card's task stays in its band, where the row can
     * complete it and open it. D-012 took it out so it was never on screen twice,
     * which was right for a card that was a task drawn large; this card cannot
     * complete anything, so it and the row are not the same task twice.
     */
    @Test
    fun thePausedTaskStaysInItsBand() {
        val tasks = listOf(
            task("paused", scheduledDate = today),
            task("b", scheduledDate = today)
        )

        val sections = todaySections(tasks, today)

        assertEquals("paused", pausedFocusTask(tasks, pausedTaskId = "paused")?.id)
        assertEquals(listOf("paused", "b"), sections.flatMap { it.tasks }.map(Task::id))
    }

    @Test
    fun everyTaskIsInExactlyOneBand() {
        val tasks = listOf(
            task("a", scheduledDate = today),
            task("b", scheduledDate = today)
        )

        val sections = todaySections(tasks, today)

        assertEquals(listOf("a", "b"), sections.flatMap { it.tasks }.map(Task::id))
    }
}
