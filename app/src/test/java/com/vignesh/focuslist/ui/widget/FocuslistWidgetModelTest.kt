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
     *
     * **These eight could not catch what D-041 fixed**, and it is worth knowing
     * why before trusting them. They pin the two sizes the widget used to
     * declare, which is exactly the set `LocalSize` was stuck inside: the model
     * was right and was never handed a height outside this pair. A suite that
     * only asks about the values production is trapped at will pass forever.
     * [capacityGrowsWithHeightRatherThanSteppingAtBreakpoints] is the case that
     * would have failed.
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
     * The regression D-041 exists for, at heights no breakpoint could produce.
     *
     * The reported bug was a widget with room for five rows drawing three, on a
     * model whose arithmetic was already right. `SizeMode.Responsive` reports the
     * matched declared size rather than the real one, so `heightDp` arrived as
     * 190 or 266 and nothing else, and the space above the match was left blank.
     *
     * Every height here is one the old mode could never have delivered. They
     * step one row at a time, which is the property that matters: capacity has
     * to be a function of the height rather than of which breakpoint was
     * nearest.
     */
    @Test
    fun capacityGrowsWithHeightRatherThanSteppingAtBreakpoints() {
        // 88dp of header, disclosure and bottom inset, then 48dp a row.
        assertEquals(1, capacity(140f, hasLead = false, fontScale = 1f, taskCount = 12))
        assertEquals(2, capacity(188f, hasLead = false, fontScale = 1f, taskCount = 12))
        assertEquals(3, capacity(236f, hasLead = false, fontScale = 1f, taskCount = 12))
        assertEquals(4, capacity(284f, hasLead = false, fontScale = 1f, taskCount = 12))
        assertEquals(5, capacity(332f, hasLead = false, fontScale = 1f, taskCount = 12))
        assertEquals(6, capacity(380f, hasLead = false, fontScale = 1f, taskCount = 12))

        // The reported case: five rows' worth of widget, which used to match
        // Medium and draw three.
        assertEquals(5, capacity(340f, hasLead = false, fontScale = 1f, taskCount = 12))
    }

    /**
     * A widget larger than the old ceiling keeps earning rows.
     *
     * `maxResizeHeight` was 266dp, the Medium frame reused as a limit, so this
     * height was not reachable at all before D-041 removed it. Nothing in the
     * arithmetic caps, and nothing should: the launcher decides how tall the
     * widget is and the model spends what it is given.
     */
    @Test
    fun aWidgetTallerThanTheOldCeilingIsNotCapped() {
        assertEquals(8, capacity(480f, hasLead = false, fontScale = 1f, taskCount = 12))
        assertEquals(6, capacity(480f, hasLead = true, fontScale = 1f, taskCount = 12))
    }

    /**
     * The smallest height the provider permits still shows a task.
     *
     * The hard floor is 136dp, 88 of furniture and one 48dp row.
     * `minResizeHeight` is declared at 140 rather than 136, so the narrowest
     * legal widget has a few dp of slack rather than sitting exactly on the
     * boundary. Both are asserted: the declared minimum works, and the floor is
     * where the arithmetic says it is.
     */
    @Test
    fun theDeclaredMinimumResizeHeightFitsOneRow() {
        assertEquals(1, capacity(MinResizeHeight, hasLead = false, fontScale = 1f))
        assertEquals(1, capacity(136f, hasLead = false, fontScale = 1f))
        assertEquals(0, capacity(135f, hasLead = false, fontScale = 1f))
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
     *
     * **[taskCount] has to exceed the capacity being asserted**, or this
     * measures the fixture instead of the arithmetic. Six was enough while the
     * widget could not be told it was taller than 266dp; D-041 lets it grow
     * without limit, and a tall widget with six tasks shows six rows because
     * that is all the work there is.
     */
    private fun capacity(
        heightDp: Float,
        hasLead: Boolean,
        fontScale: Float,
        taskCount: Int = 6
    ): Int {
        val filler = (1..taskCount).map { index -> task("f$index") }
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
        /**
         * The two sizes the widget used to declare responsively.
         *
         * D-041 retired the breakpoints, so these are no longer sizes the widget
         * can be told it is. They stay because the eight cases pinned against
         * them are still the table D-036 replaced, and moving a height constant
         * should still have to say which of those it moved.
         */
        const val CompactHeight = 190f
        const val MediumHeight = 266f

        /** `minResizeHeight` in `focuslist_widget_info.xml`, per D-041. */
        const val MinResizeHeight = 140f
    }
}
