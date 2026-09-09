package com.vignesh.focuslist.ui.widget

import com.vignesh.focuslist.core.domain.FocusNowReason
import com.vignesh.focuslist.core.domain.FocusSession
import com.vignesh.focuslist.core.domain.StoredFocusSession
import com.vignesh.focuslist.core.domain.Task
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

    /**
     * Still true, and no longer the widget's own doing. D-031 filtered this
     * reason out here; D-035 removed it from the rule, so the widget and Today
     * now decline for the same reason in the same place.
     */
    @Test
    fun ordinaryWidgetDoesNotPromoteUntimedTodayWork() {
        val model = model(listOf(task("a"), task("b")), CompactHeight)
        val body = model.body as WidgetBody.Tasks

        assertEquals(null, body.lead)
        assertEquals(listOf("a", "b"), body.rows.map { it.task.id })
    }

    @Test
    fun compactReminderWidgetKeepsOnlyTheLeadAndDisclosure() {
        val tasks = listOf(
            task("urgent", reminderAt = now.minusMinutes(5)),
            task("a"),
            task("b")
        )
        val body = model(tasks, CompactHeight).body as WidgetBody.Tasks

        assertEquals(FocusNowReason.ReminderPassed, body.lead?.reason)
        assertTrue(body.rows.isEmpty())
        assertEquals(2, body.hiddenCount)
    }

    @Test
    fun mediumPausedWidgetShowsOneOrdinaryRow() {
        val paused = StoredFocusSession(
            taskId = "paused",
            session = FocusSession(
                startedAt = createdAt,
                pausedAt = createdAt.plusSeconds(60)
            )
        )
        val body = focuslistWidgetModel(
            tasks = listOf(task("paused", scheduledDate = null), task("a"), task("b")),
            today = today,
            now = now,
            storedFocus = paused,
            completion = null,
            heightDp = MediumHeight
        ).body as WidgetBody.Tasks

        assertEquals(FocusNowReason.ResumePaused, body.lead?.reason)
        assertEquals(listOf("a"), body.rows.map { it.task.id })
        assertEquals(1, body.hiddenCount)
    }

    @Test
    fun emptyAndFinishedDaysRemainDistinct() {
        assertEquals(
            WidgetBody.NothingScheduled,
            model(emptyList(), MediumHeight).body
        )
        assertEquals(
            WidgetBody.EverythingDone,
            model(listOf(task("a", completedAt = createdAt)), MediumHeight).body
        )
        assertEquals(
            WidgetBody.NothingScheduled,
            model(
                listOf(
                    task(
                        "old",
                        scheduledDate = today.minusDays(1),
                        completedAt = createdAt
                    )
                ),
                MediumHeight
            ).body
        )
    }

    @Test
    fun justCompletedRowStaysCheckedAndSuppressesANewLead() {
        val completed = task("done", completedAt = createdAt)
        val body = focuslistWidgetModel(
            tasks = listOf(
                task("urgent", reminderAt = now.minusMinutes(5)),
                task("a"),
                completed,
                task("b")
            ),
            today = today,
            now = now,
            storedFocus = null,
            completion = WidgetCompletion("done", previousIndex = 1),
            heightDp = MediumHeight
        ).body as WidgetBody.Tasks

        assertEquals(null, body.lead)
        assertEquals(listOf("a", "done", "b"), body.rows.map { it.task.id })
        assertTrue(body.rows[1].justCompleted)
        assertEquals(1, body.hiddenCount)
    }

    @Test
    fun largeFontScaleReducesRowsInsteadOfClippingText() {
        val body = focuslistWidgetModel(
            tasks = listOf(task("a"), task("b"), task("c")),
            today = today,
            now = now,
            storedFocus = null,
            completion = null,
            heightDp = CompactHeight,
            fontScale = 2f
        ).body as WidgetBody.Tasks

        assertEquals(listOf("a"), body.rows.map { it.task.id })
        assertEquals(2, body.hiddenCount)
    }

    // --- capacity, per D-036 --------------------------------------------------

    /**
     * The eight cases the `when` over `WidgetLayout` used to enumerate.
     *
     * D-036 replaced that table with arithmetic over the reported height, and
     * this is what makes it a generalisation rather than a retune: every one of
     * its answers at the two declared sizes is the answer the table gave. A
     * change to any height constant has to come here and say which case it
     * moved.
     */
    @Test
    fun capacityReproducesTheTableItReplaced() {
        assertEquals(2, capacity(CompactHeight, hasLead = false, fontScale = 1f))
        assertEquals(0, capacity(CompactHeight, hasLead = true, fontScale = 1f))
        assertEquals(1, capacity(CompactHeight, hasLead = false, fontScale = 1.5f))
        assertEquals(0, capacity(CompactHeight, hasLead = true, fontScale = 1.5f))

        assertEquals(3, capacity(MediumHeight, hasLead = false, fontScale = 1f))
        assertEquals(1, capacity(MediumHeight, hasLead = true, fontScale = 1f))
        assertEquals(2, capacity(MediumHeight, hasLead = false, fontScale = 1.5f))
        assertEquals(0, capacity(MediumHeight, hasLead = true, fontScale = 1.5f))
    }

    /**
     * The bug D-036 was written for, reported from a phone.
     *
     * A widget dragged taller than Compact but left narrower than Medium used to
     * resolve to Compact on width alone, and Compact with a lead card is no rows
     * at all, so most of a large widget was empty container with one card
     * floating at the top. Width has no say in how many rows fit.
     */
    @Test
    fun aTallWidgetGetsRowsEvenWhenItIsTooNarrowForMedium() {
        assertEquals(2, capacity(320f, hasLead = true, fontScale = 1f))
    }

    /** A widget with no room for a single row asks for none rather than -1. */
    @Test
    fun aWidgetTooShortForAnyRowAsksForNone() {
        assertEquals(0, capacity(120f, hasLead = false, fontScale = 1f))
    }

    /**
     * Smaller system text does not buy extra rows. The layout was drawn at 1x
     * and the heights here are its heights, so scaling below it would be reading
     * space the composables never gave back.
     */
    @Test
    fun textSmallerThanNormalDoesNotAddRows() {
        assertEquals(3, capacity(MediumHeight, hasLead = false, fontScale = 0.85f))
    }

    /**
     * Rows the model decided to show, which is capacity whenever there is more
     * work than room. The lead is a passed reminder, since D-035 left that as
     * the only reason reachable without a stored session.
     */
    private fun capacity(heightDp: Float, hasLead: Boolean, fontScale: Float): Int {
        val filler = (1..6).map { index -> task("f$index") }
        val tasks =
            if (hasLead) listOf(task("lead", reminderAt = now.minusMinutes(5))) + filler
            else filler

        val body = focuslistWidgetModel(
            tasks = tasks,
            today = today,
            now = now,
            storedFocus = null,
            completion = null,
            heightDp = heightDp,
            fontScale = fontScale,
            zoneId = ZoneOffset.UTC
        ).body as WidgetBody.Tasks

        assertEquals(hasLead, body.lead != null)

        return body.rows.size
    }

    private fun model(tasks: List<Task>, heightDp: Float) = focuslistWidgetModel(
        tasks = tasks,
        today = today,
        now = now,
        storedFocus = null,
        completion = null,
        heightDp = heightDp,
        zoneId = ZoneOffset.UTC
    )

    private companion object {
        /** The two responsive sizes D-031 declares, which D-036 still keeps. */
        const val CompactHeight = 190f
        const val MediumHeight = 266f
    }
}
