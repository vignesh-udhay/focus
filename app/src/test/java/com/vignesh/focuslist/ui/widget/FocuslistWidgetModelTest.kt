package com.vignesh.focuslist.ui.widget

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TodayBand
import com.vignesh.focuslist.data.local.WidgetCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class FocuslistWidgetModelTest {

    private val today = LocalDate.of(2026, 9, 8)
    private val now = LocalDateTime.of(2026, 9, 8, 10, 0)
    private val createdAt = Instant.parse("2026-09-01T09:00:00Z")

    private fun task(
        id: String,
        scheduledDate: LocalDate? = today,
        reminderAt: LocalDateTime? = null,
        completedAt: Instant? = null
    ) = Task(
        id = id,
        title = "Task $id",
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        reminderAt = reminderAt,
        completedAt = completedAt
    )

    // --- the bands, per D-049 ---------------------------------------------------

    /**
     * The widget's rows arrive grouped and labelled.
     *
     * This is the whole of what D-049 added. The rows were always in this order,
     * because `todayTasks` sorts them into it, but the widget presented one run
     * and left the user to infer where the boundaries were. The lead card used to
     * explain the top of that run; D-048 deleted it and left nothing.
     */
    @Test
    fun theRowsArriveInTodaysBands() {
        val body = model(
            listOf(
                task("late", scheduledDate = today.minusDays(2)),
                task("untimed"),
                task("timed", reminderAt = now.plusHours(3))
            )
        ).body as WidgetBody.Sections

        assertEquals(
            listOf(TodayBand.OVERDUE, TodayBand.NO_TIME_SET, TodayBand.LATER_TODAY),
            body.sections.map { it.band }
        )
        assertEquals(
            listOf(listOf("late"), listOf("untimed"), listOf("timed")),
            body.sections.map { section -> section.rows.map { it.task.id } }
        )
    }

    /** An absent band produces no heading, rather than an empty one to explain. */
    @Test
    fun aBandWithNothingInItDoesNotAppear() {
        val body = model(listOf(task("a"), task("b"))).body as WidgetBody.Sections

        assertEquals(listOf(TodayBand.NO_TIME_SET), body.sections.map { it.band })
        assertEquals(listOf("a", "b"), body.sections.single().rows.map { it.task.id })
    }

    /**
     * Finished work stays off the home screen.
     *
     * On Today the Completed band is a disclosure the user can open. A widget has
     * nothing cheap to open into, so the band is dropped rather than drawn shut.
     */
    @Test
    fun completedWorkIsNotABandOnTheWidget() {
        val body = model(
            listOf(task("a"), task("done", completedAt = createdAt))
        ).body as WidgetBody.Sections

        assertEquals(listOf(TodayBand.NO_TIME_SET), body.sections.map { it.band })
        assertEquals(listOf("a"), body.sections.single().rows.map { it.task.id })
    }

    /** Every outstanding task reaches the widget, however many there are. */
    @Test
    fun everyOutstandingTaskIsHandedToTheList() {
        val tasks = (1..40).map { index -> task("t$index") }

        val body = model(tasks).body as WidgetBody.Sections

        assertEquals(40, body.sections.sumOf { it.rows.size })
        assertEquals(tasks.map { it.id }, body.sections.flatMap { s -> s.rows.map { it.task.id } })
    }

    // --- the states either side of the bands -------------------------------------

    @Test
    fun emptyAndFinishedDaysRemainDistinct() {
        assertEquals(WidgetBody.NothingScheduled, model(emptyList()).body)
        assertEquals(
            WidgetBody.EverythingDone,
            model(listOf(task("a", completedAt = createdAt))).body
        )
        assertEquals(
            WidgetBody.NothingScheduled,
            model(
                listOf(
                    task("old", scheduledDate = today.minusDays(1), completedAt = createdAt)
                )
            ).body
        )
    }

    @Test
    fun aFullDayIsNeverAnEmptyBody() {
        val body = model((1..40).map { index -> task("t$index") }).body

        assertTrue(body is WidgetBody.Sections)
    }

    // --- completion evidence, per D-031 and D-049 --------------------------------

    /**
     * The checked row holds its own place, in its own band.
     *
     * D-031 keeps it on screen because a row that vanishes on tap erases the only
     * evidence of what the user just did. Completing it would otherwise move it
     * to the Completed band, which the widget does not draw at all, so it would
     * vanish twice over. D-049 shows it as though it were unfinished instead,
     * which is what returns it to where it was without recording an index.
     */
    @Test
    fun theJustCompletedTaskStaysCheckedInItsOwnBand() {
        val body = focuslistWidgetModel(
            tasks = listOf(
                task("late", scheduledDate = today.minusDays(1)),
                task("a"),
                task("done", completedAt = createdAt),
                task("b")
            ),
            today = today,
            completion = WidgetCompletion("done"),
            zoneId = ZoneOffset.UTC
        ).body as WidgetBody.Sections

        val untimed = body.sections.single { it.band == TodayBand.NO_TIME_SET }
        assertEquals(listOf("a", "done", "b"), untimed.rows.map { it.task.id })
        assertTrue(untimed.rows.single { it.task.id == "done" }.justCompleted)
        assertTrue(untimed.rows.filterNot { it.task.id == "done" }.none { it.justCompleted })
    }

    /**
     * An overdue task checked from the widget stays under Overdue.
     *
     * The band is derived from the task, so presenting it as unfinished has to
     * put it back where it came from rather than somewhere plausible.
     */
    @Test
    fun aJustCompletedOverdueTaskStaysUnderOverdue() {
        val body = focuslistWidgetModel(
            tasks = listOf(
                task("late", scheduledDate = today.minusDays(1), completedAt = createdAt),
                task("a")
            ),
            today = today,
            completion = WidgetCompletion("late"),
            zoneId = ZoneOffset.UTC
        ).body as WidgetBody.Sections

        assertEquals(
            listOf(TodayBand.OVERDUE, TodayBand.NO_TIME_SET),
            body.sections.map { it.band }
        )
        assertTrue(body.sections.first().rows.single().justCompleted)
    }

    /** Evidence for a task that is no longer completed marks nothing. */
    @Test
    fun staleEvidenceDoesNotCheckAnOutstandingRow() {
        val body = focuslistWidgetModel(
            tasks = listOf(task("a"), task("b")),
            today = today,
            completion = WidgetCompletion("a"),
            zoneId = ZoneOffset.UTC
        ).body as WidgetBody.Sections

        assertTrue(body.sections.flatMap { it.rows }.none { it.justCompleted })
    }

    private fun model(tasks: List<Task>) = focuslistWidgetModel(
        tasks = tasks,
        today = today,
        completion = null,
        zoneId = ZoneOffset.UTC
    )
}
