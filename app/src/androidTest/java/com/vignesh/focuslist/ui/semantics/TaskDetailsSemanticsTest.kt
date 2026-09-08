package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.ui.task.TaskDetailsScreen
import com.vignesh.focuslist.ui.task.TaskListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task Details as `docs/decisions.md` D-018 rebuilt it.
 *
 * **This file was rewritten with the screen.** It used to test a bottom sheet
 * holding a draft: typed date fields, a parser confirmation line, identical
 * Clear buttons told apart by description, and Save disabled while a field was
 * unusable. None of those exist. What replaced them is a screen of picker rows
 * that commit as they are chosen.
 *
 * The contract that matters most is the one D-018 names as the price of the
 * model: **there is no Save, so every control has to write when it is chosen.**
 * These assert the write reaching storage rather than the sheet closing, since
 * a sheet that closes without writing is exactly the failure the draft used to
 * make impossible.
 *
 * Run against a real view model over fake storage, so a write travels the
 * production path.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TaskDetailsSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun withTask(
        scheduledDate: java.time.LocalDate? = TestToday,
        estimatedDurationMinutes: Int? = 45,
        recurrence: Recurrence? = null,
        notes: String? = null
    ) = FakeTaskDao(
        listOf(
            testTask(
                id = TASK_ID,
                title = TITLE,
                scheduledDate = scheduledDate,
                estimatedDurationMinutes = estimatedDurationMinutes,
                recurrence = recurrence,
                notes = notes
            )
        )
    )

    private fun setScreen(
        fontScale: Float = FontScale100,
        dao: FakeTaskDao = withTask(),
        onBack: () -> Unit = {}
    ): TaskListViewModel {
        val viewModel = testViewModel(dao)

        rule.setFocuslistContent(fontScale) {
            TaskDetailsScreen(
                taskId = TASK_ID,
                viewModel = viewModel,
                onBack = onBack,
                onOpenFocus = {}
            )
        }

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        return viewModel
    }

    /**
     * Waits for a write to reach storage, which is asynchronous.
     *
     * Read back through the view model rather than the DAO, so the assertion
     * travels the same path the screen reads and sees domain tasks rather than
     * entities.
     */
    private fun awaitTask(
        viewModel: TaskListViewModel,
        predicate: (com.vignesh.focuslist.core.domain.Task) -> Boolean
    ): com.vignesh.focuslist.core.domain.Task {
        repeat(200) {
            val task = viewModel.allTasks.value.firstOrNull { it.id == TASK_ID }
            if (task != null && predicate(task)) return task
            Thread.sleep(10)
        }
        throw AssertionError(
            "write never reached storage; holds " +
                viewModel.allTasks.value.firstOrNull { it.id == TASK_ID }
        )
    }

    // --- the three regions ---------------------------------------------------

    private fun assertTheScreenShowsItsThreeRegions(fontScale: Float) {
        setScreen(fontScale)

        // Identity: the task's own title is the heading, and there is no app
        // bar title above it. Two stacked headings, the upper one naming the
        // app, is what D-018 removed.
        rule.onNodeWithText(TITLE).assertIsDisplayed()
        rule.onAllNodesWithText(HEADING).assertCountEquals(0)

        // Plan: the label and all five rows.
        rule.onNodeWithText(PLAN).assertIsDisplayed()
        listOf(SCHEDULED, DUE_DATE, REMINDER, DURATION, REPEAT).forEach { row ->
            rule.onNodeWithText(row).assertIsDisplayed()
        }

        // Action. Scrolled to, because at 200% it is below the fold and a test
        // that only passed at 100% would not prove it is reachable at all.
        rule.onNodeWithText(START_FOCUS).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theScreenShowsItsThreeRegions_at100() =
        assertTheScreenShowsItsThreeRegions(FontScale100)

    @Test
    fun theScreenShowsItsThreeRegions_at200() =
        assertTheScreenShowsItsThreeRegions(FontScale200)

    /** A room: a back arrow, and it is the only thing in the bar. */
    @Test
    fun theBarCarriesABackArrowAndNothingElse() {
        var backs = 0
        setScreen(onBack = { backs++ })

        rule.onNodeWithContentDescription(BACK).performClick()

        assertEquals(1, backs)
    }

    /** Rows show what is set, and an unset value reads like any other. */
    @Test
    fun rowsShowTheirValues() {
        setScreen(dao = withTask(scheduledDate = TestToday, estimatedDurationMinutes = 45))

        rule.onNodeWithText(TODAY).assertIsDisplayed()
        // 45m through `durationLabel`, never "45 min".
        rule.onNodeWithText(DURATION_45).assertIsDisplayed()
        rule.onNodeWithText(NO_REPEAT).assertIsDisplayed()
    }

    // --- committing, which is the whole of D-018 -----------------------------

    /**
     * **Scheduled's clear option is not a nicety.** A task with no scheduled
     * date is an Inbox task, and without it there is no way back to the Inbox
     * from this screen. It was the missing option D-018 spent `Next week` on.
     */
    @Test
    fun clearingTheScheduledDateWritesImmediately() {
        val viewModel = setScreen(dao = withTask(scheduledDate = TestToday))

        rule.onNodeWithText(SCHEDULED).performClick()
        rule.waitUntilExactlyOneExists(hasText(NO_DATE), TIMEOUT_MILLIS)
        rule.onNodeWithText(NO_DATE).performClick()

        awaitTask(viewModel) { task -> task.scheduledDate == null }
    }

    @Test
    fun pickingADayWritesImmediately() {
        val viewModel = setScreen(dao = withTask(scheduledDate = null))

        rule.onNodeWithText(SCHEDULED).performClick()
        rule.waitUntilExactlyOneExists(hasText(TOMORROW), TIMEOUT_MILLIS)
        rule.onNodeWithText(TOMORROW).performClick()

        awaitTask(viewModel) { task -> task.scheduledDate == TestToday.plusDays(1) }
    }

    /** D-018 fixes this preset's meaning: the coming Saturday. */
    @Test
    fun thisWeekendIsTheComingSaturday() {
        val viewModel = setScreen(dao = withTask(scheduledDate = null))

        rule.onNodeWithText(SCHEDULED).performClick()
        rule.waitUntilExactlyOneExists(hasText(THIS_WEEKEND), TIMEOUT_MILLIS)
        rule.onNodeWithText(THIS_WEEKEND).performClick()

        awaitTask(viewModel) { task ->
            task.scheduledDate?.dayOfWeek == java.time.DayOfWeek.SATURDAY &&
                task.scheduledDate?.isAfter(TestToday) == true
        }
    }

    /** And this one's: the coming Friday. The two sheets differ only here. */
    @Test
    fun endOfWeekIsTheComingFriday() {
        val viewModel = setScreen(dao = withTask())

        rule.onNodeWithText(DUE_DATE).performClick()
        rule.waitUntilExactlyOneExists(hasText(END_OF_WEEK), TIMEOUT_MILLIS)
        rule.onNodeWithText(END_OF_WEEK).performClick()

        awaitTask(viewModel) { task ->
            task.dueDate?.dayOfWeek == java.time.DayOfWeek.FRIDAY
        }
    }

    @Test
    fun pickingADurationWritesImmediately() {
        val viewModel = setScreen(dao = withTask(estimatedDurationMinutes = null))

        rule.onNodeWithText(DURATION).performClick()
        rule.waitUntilExactlyOneExists(hasText(DURATION_30), TIMEOUT_MILLIS)
        rule.onNodeWithText(DURATION_30).performClick()

        awaitTask(viewModel) { task -> task.estimatedDurationMinutes == 30 }
    }

    /**
     * The Repeat row picks one of `Recurrence`'s four periods or none. The
     * board's editor, with an interval and a weekday set and an end condition,
     * is Phase 4 per D-019 and is deliberately absent.
     */
    @Test
    fun repeatPicksAPeriodAndOffersNoEditor() {
        val viewModel = setScreen(dao = withTask())

        rule.onNodeWithText(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(WEEKLY), TIMEOUT_MILLIS)

        // None of the Phase 4 editor is here.
        listOf(EVERY, DAYS, ENDS).forEach { absent ->
            rule.onAllNodesWithText(absent).assertCountEquals(0)
        }

        rule.onNodeWithText(WEEKLY).performClick()
        awaitTask(viewModel) { task -> task.recurrence == Recurrence.WEEKLY }
    }

    // --- the identity fields -------------------------------------------------

    @Test
    fun editingTheTitleCommitsOnBlur() {
        val viewModel = setScreen(dao = withTask())

        rule.onNodeWithText(TITLE).performTextReplacement(EDITED_TITLE)
        // Blur by tapping the other field, which is what a user does and the
        // only thing that actually moves focus. The field is the draft until
        // focus leaves it, so nothing is written before this.
        rule.onNodeWithText(ADD_NOTES).performClick()

        awaitTask(viewModel) { task -> task.title == EDITED_TITLE }
    }

    /**
     * **A blank title is not written.** `editTask` refuses one, so committing a
     * cleared field would drop the write silently; the field puts the stored
     * title back instead.
     */
    @Test
    fun aBlankTitleIsRefusedAndTheStoredOneReturns() {
        val viewModel = setScreen(dao = withTask())

        rule.onNodeWithText(TITLE).performTextReplacement("   ")
        rule.onNodeWithText(ADD_NOTES).performClick()

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        assertEquals(TITLE, viewModel.allTasks.value.single { it.id == TASK_ID }.title)
    }

    /**
     * **Null and blank both mean no notes**, and the view model maps the blank
     * back to null so "no notes" has one representation in storage rather than
     * two that read the same on screen.
     */
    @Test
    fun clearingNotesStoresNullRatherThanBlank() {
        val viewModel = setScreen(dao = withTask(notes = "Some note"))

        rule.onNodeWithText("Some note").performTextReplacement("  ")
        // Blur onto the title, the other field on the screen.
        rule.onNodeWithText(TITLE).performClick()

        awaitTask(viewModel) { task -> task.notes == null }
    }

    /**
     * An existing note survives an edit to any other field. `editTask` takes
     * notes as a required parameter with no default precisely so a caller
     * changing something else cannot erase a note it never asked about.
     */
    @Test
    fun aNoteSurvivesAnEditToAnotherField() {
        val viewModel = setScreen(dao = withTask(notes = "Keep me", estimatedDurationMinutes = null))

        rule.onNodeWithText(DURATION).performClick()
        rule.waitUntilExactlyOneExists(hasText(DURATION_30), TIMEOUT_MILLIS)
        rule.onNodeWithText(DURATION_30).performClick()

        awaitTask(viewModel) { task -> task.estimatedDurationMinutes == 30 }
        assertEquals("Keep me", viewModel.allTasks.value.single { it.id == TASK_ID }.notes)
    }

    /**
     * Completion is the exception and is not a field: it goes through the
     * ordinary `toggleComplete` and raises the same undo offer every list does.
     */
    @Test
    fun theCheckboxCompletesThroughTheOrdinaryPath() {
        val viewModel = setScreen()

        rule.onNodeWithContentDescription(MARK_COMPLETE).performClick()

        awaitTask(viewModel) { task -> task.isCompleted }
        assertTrue(viewModel.pendingUndo.value != null)
    }

    private companion object {
        const val TASK_ID = "1"
        const val TITLE = "Refine landing page hero"
        const val EDITED_TITLE = "Refine the hero"

        /** Published as `paneTitle` and drawn nowhere. */
        const val HEADING = "Task details"
        const val BACK = "Back"
        const val PLAN = "Plan"

        /** The notes placeholder, which is also how the field is tapped. */
        const val ADD_NOTES = "Add notes"
        const val START_FOCUS = "Start focus"

        const val SCHEDULED = "Scheduled"
        const val DUE_DATE = "Due date"
        const val REMINDER = "Reminder"
        const val DURATION = "Duration"
        const val REPEAT = "Repeat"

        const val TODAY = "Today"
        const val TOMORROW = "Tomorrow"
        const val NO_DATE = "No date"
        const val THIS_WEEKEND = "This weekend"
        const val END_OF_WEEK = "End of week"
        const val NO_REPEAT = "Doesn't repeat"
        const val WEEKLY = "Weekly"

        /** Durations read through `durationLabel`. Never "45 min". */
        const val DURATION_45 = "45m"
        const val DURATION_30 = "30m"

        /** The Phase 4 editor D-019 defers. None of it may appear. */
        const val EVERY = "Every"
        const val DAYS = "Days"
        const val ENDS = "Ends"

        const val MARK_COMPLETE = "Mark \"$TITLE\" complete"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
