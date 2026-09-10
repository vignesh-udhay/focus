package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import com.vignesh.focuslist.ui.task.TaskDetailsScreen
import com.vignesh.focuslist.ui.task.TaskListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
     * **The dead end D-062 fixed.** A notification for a deleted task, on a
     * device holding nothing else.
     *
     * The screen used `tasks.isNotEmpty()` as its proof that the read had
     * happened, and with no other tasks that proof never arrives: empty before
     * the read and empty after it. So the screen fell through both branches of
     * its guard and drew nothing at all, with no app bar and no way back, for
     * ever. Against the unfixed code every assertion here fails.
     */
    @Test
    fun aStaleDeepLink_saysSo_ratherThanDrawingNothing() {
        var backs = 0
        val viewModel = testViewModel(FakeTaskDao(emptyList()))

        rule.setFocuslistContent(FontScale100) {
            TaskDetailsScreen(
                taskId = "deleted-task",
                viewModel = viewModel,
                onBack = { backs++ },
                onOpenFocus = {}
            )
        }

        rule.waitUntilExactlyOneExists(hasText(MISSING_HEADLINE), TIMEOUT_MILLIS)
        rule.onNodeWithText(MISSING_HEADLINE).assertIsDisplayed()

        // The bar is the part that makes it not a trap, and it was the part
        // missing in every one of these states.
        rule.onNodeWithContentDescription(BACK).assertIsDisplayed()

        // It says its piece and stays. Leaving on its own would flash a screen
        // the user tapped a notification to reach and then take it away.
        rule.runOnIdle { assertEquals(0, backs) }
    }

    /** And Back is one tap, from the button as well as from the bar. */
    @Test
    fun aStaleDeepLink_offersAWayBack() {
        var backs = 0
        val viewModel = testViewModel(FakeTaskDao(emptyList()))

        rule.setFocuslistContent(FontScale100) {
            TaskDetailsScreen(
                taskId = "deleted-task",
                viewModel = viewModel,
                onBack = { backs++ },
                onOpenFocus = {}
            )
        }

        rule.waitUntilExactlyOneExists(hasText(MISSING_ACTION), TIMEOUT_MILLIS)
        rule.onNodeWithText(MISSING_ACTION).performClick()

        rule.runOnIdle { assertEquals(1, backs) }
    }

    /**
     * The same dead end by the other route, and nobody had noticed it.
     *
     * D-034 gave every list a failed-read state and Task Details was never
     * wired to it. A read that throws leaves the list empty, which is the same
     * ambiguity, so the screen hung here too. It draws what the lists draw.
     */
    @Test
    fun aFailedRead_saysSo_onTaskDetailsToo() {
        val viewModel = testViewModel(FailingTaskDao())

        rule.setFocuslistContent(FontScale100) {
            TaskDetailsScreen(
                taskId = TASK_ID,
                viewModel = viewModel,
                onBack = {},
                onOpenFocus = {}
            )
        }

        rule.waitUntilExactlyOneExists(hasText(READ_FAILED_HEADLINE), TIMEOUT_MILLIS)
        rule.onNodeWithText(READ_FAILED_HEADLINE).assertIsDisplayed()
        rule.onNodeWithText(TRY_AGAIN).assertIsDisplayed()

        // Not the missing-task wording. The task may exist and the app could
        // not see it, which is a different claim and D-034's whole point.
        rule.onNodeWithText(MISSING_HEADLINE).assertDoesNotExist()
    }

    /**
     * A task that was on screen and then went still leaves silently. The user
     * completed or deleted it, and the list they land on carries the undo
     * offer, so an explanation would narrate their own tap back to them.
     */
    @Test
    fun aTaskDeletedWhileOpen_leavesWithoutExplaining() {
        var backs = 0
        val viewModel = setScreen(onBack = { backs++ })

        rule.runOnIdle { viewModel.deleteTask(TASK_ID) }

        rule.waitUntil(TIMEOUT_MILLIS) { backs == 1 }
        rule.onNodeWithText(MISSING_HEADLINE).assertDoesNotExist()
    }

    /**
     * Waits for a write to reach storage, which is asynchronous.
     *
     * Read back through the view model rather than the DAO, so the assertion
     * travels the same path the screen reads and sees domain tasks rather than
     * entities.
     */
    /** What the task holds right now, with no waiting. */
    private fun currentTask(
        viewModel: TaskListViewModel
    ): com.vignesh.focuslist.core.domain.Task =
        viewModel.allTasks.value.single { it.id == TASK_ID }

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

        // Action. D-037 moved both actions into a floating toolbar and they no
        // longer scroll: it is pinned, which is most of the point of it.
        // Asserted at both scales for the same reason the old scrolled
        // assertion existed, that a control reachable at 100% has not been
        // pushed off screen at 200%.
        //
        // D-064 gave Start focus its word back, so it is drawn text now and not
        // only a description. Delete keeps neither, deliberately: a trash can is
        // not ambiguous the way a play triangle was, and a word would raise a
        // rare one-way action to the weight of the screen's payoff.
        rule.onNodeWithText(START_FOCUS).assertIsDisplayed()
        rule.onNodeWithContentDescription(DELETE).assertIsDisplayed()
    }

    @Test
    fun theScreenShowsItsThreeRegions_at100() =
        assertTheScreenShowsItsThreeRegions(FontScale100)

    @Test
    fun theScreenShowsItsThreeRegions_at200() =
        assertTheScreenShowsItsThreeRegions(FontScale200)

    /**
     * `docs/decisions.md` D-064. D-037 accepted losing this word and named the
     * reversal condition: a play triangle on a screen about one task reads as
     * preview, or resume, or run, and Focus is the payoff the whole page leads
     * to. The FAB slot could not hold a label — the component pins its width and
     * height to one square value — so the action moved into the toolbar's own
     * row to get one.
     */
    @Test
    fun startFocus_isWorded_andSaysItOnce() {
        setScreen()

        rule.onNodeWithText(START_FOCUS).assertIsDisplayed()

        // The word is drawn, so the glyph beside it is decoration. Announcing
        // both would read the action out twice.
        rule.onNodeWithContentDescription(START_FOCUS).assertDoesNotExist()
    }

    /** And it still starts a session from the labelled control. */
    @Test
    fun startFocus_stillStartsASession() {
        val viewModel = setScreen()

        rule.onNodeWithText(START_FOCUS).performClick()

        rule.runOnIdle { assertNotNull(viewModel.focusSession.value) }
    }

    /**
     * `docs/decisions.md` D-068. **The word has to name the branch the tap
     * takes.** D-057 made `beginFocus` resume a session already open on this
     * task instead of restarting it, and the label was left behind, so a task
     * with a session paused at 44:45 offered "Start focus" and did not start
     * one. Against the unfixed screen this fails: the button reads Start.
     */
    @Test
    fun aSessionOnThisTask_wordsTheControlAsResume() {
        val viewModel = setScreen()

        rule.runOnIdle {
            viewModel.beginFocus(TASK_ID)
            viewModel.pauseFocusSession()
        }

        rule.waitUntilExactlyOneExists(hasText(RESUME_FOCUS), TIMEOUT_MILLIS)
        rule.onNodeWithText(RESUME_FOCUS).assertIsDisplayed()
        rule.onNodeWithText(START_FOCUS).assertDoesNotExist()
    }

    /**
     * And the other two branches keep Start, which is the half that stops this
     * becoming the same defect pointing the other way. No session is the plain
     * case; a session on *another* task resumes nothing here, and D-057's dialog
     * is what explains that tap.
     */
    @Test
    fun noSessionOnThisTask_keepsTheControlAsStart() {
        val dao = FakeTaskDao(
            listOf(
                testTask(id = TASK_ID, title = TITLE, scheduledDate = TestToday),
                testTask(id = OTHER_TASK_ID, title = OTHER_TITLE, scheduledDate = TestToday)
            )
        )
        val viewModel = setScreen(dao = dao)

        rule.onNodeWithText(START_FOCUS).assertIsDisplayed()

        rule.runOnIdle {
            viewModel.beginFocus(OTHER_TASK_ID)
            viewModel.pauseFocusSession()
        }

        rule.onNodeWithText(START_FOCUS).assertIsDisplayed()
        rule.onNodeWithText(RESUME_FOCUS).assertDoesNotExist()
    }

    /**
     * Delete keeps no word, which D-064 argues rather than inherits. A trash can
     * is not ambiguous the way a play triangle is, and labelling it would give a
     * rare one-way action the weight D-022 and D-037 both kept from it.
     */
    @Test
    fun delete_staysAnIconWithItsWordSpokenOnly() {
        setScreen()

        rule.onNodeWithContentDescription(DELETE).assertIsDisplayed()
        rule.onNodeWithText(DELETE).assertDoesNotExist()
    }

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
     * The Repeat editor, which `docs/decisions.md` D-027 built and D-019 had
     * deferred. This test used to assert the opposite, that none of Every, Days
     * or Ends appeared; the assertion is inverted rather than deleted, because
     * the three names are exactly what tells the two designs apart.
     */
    @Test
    fun repeatOffersTheEditorTheBoardDraws() {
        setScreen(dao = withTask())

        rule.onNodeWithText(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(EVERY), TIMEOUT_MILLIS)

        listOf(EVERY, DAYS, ENDS, SAVE_REPEAT).forEach { present ->
            rule.onNodeWithText(present).assertExists()
        }
    }

    /**
     * **Nothing is written until Save**, which is the one place Task Details
     * departs from D-018's commit-as-you-go rule and the sheet's own KDoc says
     * why: a rule is four fields that only mean something together, so writing
     * each tap would put half-built rules on the task.
     */
    @Test
    fun aWeekdayIsNotWrittenUntilSaveIsPressed() {
        val viewModel = setScreen(dao = withTask())

        rule.onNodeWithText(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(DAYS), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(WEDNESDAY).performClick()

        // Still nothing, one tap in.
        assertNull(currentTask(viewModel).recurrence)

        rule.onNodeWithText(SAVE_REPEAT).performClick()

        awaitTask(viewModel) { task ->
            task.recurrence?.weekdays == setOf(java.time.DayOfWeek.WEDNESDAY)
        }
    }

    /**
     * **The last selected day does not come off.** The board's weekday frame is
     * named "require >=1 selected day", and D-027 enforces it by nothing
     * happening rather than by an error: there is no failure to report, only a
     * state the rule cannot be in.
     *
     * The second tap is the assertion. Without the guard it clears the set, and
     * Save writes a weekly rule that names no day.
     */
    @Test
    fun theLastWeekdayCannotBeDeselected() {
        val viewModel = setScreen(dao = withTask())

        rule.onNodeWithText(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(DAYS), TIMEOUT_MILLIS)

        rule.onNodeWithContentDescription(WEDNESDAY).performClick()
        rule.onNodeWithContentDescription(WEDNESDAY).performClick()

        rule.onNodeWithText(SAVE_REPEAT).performClick()

        awaitTask(viewModel) { task ->
            task.recurrence?.weekdays == setOf(java.time.DayOfWeek.WEDNESDAY)
        }
    }

    /**
     * The board draws no way to stop a task repeating, and D-027 adds one. A
     * task with no rule is not offered it, because a control that can do nothing
     * is one the user cannot tell worked.
     */
    @Test
    fun clearingIsOfferedOnlyWhileTheTaskRepeats() {
        val viewModel = setScreen(
            dao = withTask(recurrence = Recurrence(RecurrenceUnit.WEEKLY))
        )

        rule.onNodeWithText(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(EVERY), TIMEOUT_MILLIS)
        rule.onAllNodes(clearRepeatAction).assertCountEquals(1)

        rule.onAllNodes(clearRepeatAction).onFirst().performClick()
        awaitTask(viewModel) { task -> task.recurrence == null }

        rule.onNodeWithText(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(EVERY), TIMEOUT_MILLIS)
        rule.onAllNodes(clearRepeatAction).assertCountEquals(0)
    }

    /**
     * The clear action, told apart from the Plan row behind the sheet.
     *
     * Once the rule is gone the row reads "Repeat  Doesn't repeat", so text
     * alone matches either. The button carries `Role.Button` and the row does
     * not, which is the only thing that separates them.
     */
    private val clearRepeatAction =
        hasText(NO_REPEAT) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

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

    /**
     * Deleting the final live task empties `allTasks`. That empty result is not
     * the repository's initial placeholder once this task has already been on
     * screen, so Task Details must leave instead of rendering a blank route.
     */
    @Test
    fun deletingTheOnlyTaskReturnsToThePreviousScreen() {
        var backs = 0
        setScreen(onBack = { backs++ })

        // One tap. The overflow it used to open is gone with D-037, which is
        // what this line stops silently passing through a menu that no longer
        // exists: without the change it would fail to find TASK_ACTIONS.
        rule.onNodeWithContentDescription(DELETE).performClick()

        rule.waitUntil(TIMEOUT_MILLIS) { backs == 1 }
        assertEquals(1, backs)
    }

    private companion object {
        const val TASK_ID = "1"
        const val TITLE = "Refine landing page hero"
        const val EDITED_TITLE = "Refine the hero"

        /** Published as `paneTitle` and drawn nowhere. */
        const val HEADING = "Task details"
        const val BACK = "Back"
        const val DELETE = "Delete"
        const val PLAN = "Plan"

        /** The notes placeholder, which is also how the field is tapped. */
        const val ADD_NOTES = "Add notes"
        const val START_FOCUS = "Start focus"
        const val RESUME_FOCUS = "Resume focus"

        /** D-068's other-task case: a session that this screen must not claim. */
        const val OTHER_TASK_ID = "2"
        const val OTHER_TITLE = "Draft the release notes"

        /** D-062's three answers when there is no task to draw. */
        const val MISSING_HEADLINE = "This task is no longer available"
        const val MISSING_ACTION = "Back to your tasks"
        const val READ_FAILED_HEADLINE = "Couldn't load your tasks"
        const val TRY_AGAIN = "Try again"

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

        /** The Repeat editor D-027 built. */
        const val EVERY = "Every"
        const val DAYS = "Days"
        const val ENDS = "Ends"
        const val SAVE_REPEAT = "Save repeat"

        /**
         * A weekday chip, by its spoken name rather than its label.
         *
         * The label is one letter and two pairs of days share theirs, so the
         * chips carry their full name as a content description and this is the
         * only way to name one unambiguously.
         */
        const val WEDNESDAY = "Wednesday"

        const val MARK_COMPLETE = "Mark \"$TITLE\" complete"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
