package com.vignesh.focuslist.ui.semantics

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.focus.FocusSheet
import com.vignesh.focuslist.ui.task.TaskListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focus's six states, and the promise D-015 rests on.
 *
 * `focus.md` names what this file has to cover: each state publishes its task
 * title as a heading, names its clock control by the action rather than the
 * glyph, and carries its status line as text. The way out is the sheet's own
 * dismiss action since D-032, not a control this file can name.
 * Completing ends the task. **Leaving pauses rather than stops**, which is the
 * assertion that matters most here, because the failure it guards against is a
 * silent one.
 *
 * Runs against a real view model over fake storage, so completing a task here
 * travels the production path.
 *
 * **This file used to assert a queue**: a "Next:" footer, completing advancing
 * to the following task without leaving, and an empty state when the queue ran
 * dry. D-004 removed the queue and `focus.md` removed the empty state; none of
 * those behaviours exist, and the assertions went with them.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FocusSessionSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * A session with an estimate asks to post notifications, so the estimate can
     * be announced when it is reached. Left ungranted, the system dialog opens
     * over the screen and every assertion after it fails against a hierarchy
     * that is no longer in front.
     */
    @Before
    fun grantNotifications() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun withEstimate() = FakeTaskDao(
        listOf(
            testTask(
                id = "1",
                title = FIRST,
                scheduledDate = TestToday,
                estimatedDurationMinutes = 45
            )
        )
    )

    private fun withoutEstimate() = FakeTaskDao(
        listOf(testTask(id = "1", title = FIRST, scheduledDate = TestToday))
    )

    /**
     * Composes the sheet on a state, and hands back the view model so a test can
     * assert what leaving actually did.
     *
     * [start] is what puts the session in one of the six states: Ready is a
     * chosen task with no clock, and the rest are reached by driving the same
     * controls the user would.
     */
    private fun setFocus(
        fontScale: Float,
        dao: FakeTaskDao,
        start: (TaskListViewModel) -> Unit
    ): TaskListViewModel {
        val viewModel = testViewModel(dao)
        start(viewModel)

        rule.setFocuslistContent(fontScale) {
            FocusSheet(viewModel = viewModel)
        }

        return viewModel
    }

    private fun ready(dao: FakeTaskDao, fontScale: Float = FontScale100) =
        setFocus(fontScale, dao) { model -> model.openFocus("1") }

    private fun running(dao: FakeTaskDao, fontScale: Float = FontScale100) =
        setFocus(fontScale, dao) { model -> model.beginFocus("1") }

    private fun paused(dao: FakeTaskDao, fontScale: Float = FontScale100) =
        setFocus(fontScale, dao) { model -> model.beginFocus("1"); model.pauseFocusSession() }

    // --- the six states -------------------------------------------------------

    /**
     * Every state names its clock control by what pressing it does. An icon
     * carries no text, so a description reading "play" would describe the
     * drawing rather than the action.
     */
    private fun assertStatesNameTheirControls(fontScale: Float) {
        ready(withEstimate(), fontScale)
        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(START).assertIsDisplayed()
        rule.onNodeWithText(ESTIMATE_STATUS, substring = true).assertIsDisplayed()
        rule.onNodeWithText(COMPLETE).assertIsDisplayed()
    }

    @Test
    fun readyNamesItsControls_at100() = assertStatesNameTheirControls(FontScale100)

    @Test
    fun readyNamesItsControls_at200() = assertStatesNameTheirControls(FontScale200)

    @Test
    fun runningOffersPauseAndTheEstimate() {
        running(withEstimate())

        rule.waitUntilExactlyOneExists(hasContentDescription(PAUSE), TIMEOUT_MILLIS)
        rule.onNodeWithText(ESTIMATE_STATUS, substring = true).assertIsDisplayed()
        rule.onNodeWithText(COMPLETE).assertIsDisplayed()
    }

    @Test
    fun pausedOffersResumeAndSaysWhatIsLeft() {
        paused(withEstimate())

        rule.waitUntilExactlyOneExists(hasContentDescription(RESUME), TIMEOUT_MILLIS)
        // The budget, which is the question someone deciding whether to resume
        // is actually asking. "Paused" on its own could not answer it.
        rule.onNodeWithText(REMAINING, substring = true).assertIsDisplayed()
    }

    @Test
    fun anOpenEndedSessionSaysItHasNoLimit() {
        running(withoutEstimate())

        rule.waitUntilExactlyOneExists(hasText(NO_LIMIT, substring = true), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(PAUSE).assertIsDisplayed()
    }

    @Test
    fun anOpenEndedPausedSessionOffersResumeAndStillSaysNoLimit() {
        paused(withoutEstimate())

        rule.waitUntilExactlyOneExists(hasContentDescription(RESUME), TIMEOUT_MILLIS)
        rule.onNodeWithText(NO_LIMIT, substring = true).assertIsDisplayed()
    }

    /**
     * **The clock is on the status line, and D-046 put it there.**
     *
     * It used to be its own node inside the shape. The budget assertions above
     * match on a substring for that reason: every state's line now begins with
     * a readout, and in five of the six it is ticking.
     *
     * Ready is the one state where the whole line can be asserted exactly.
     * Nothing is running, so the readout is the estimate and it does not move.
     */
    @Test
    fun readyPutsTheClockOnTheStatusLine() {
        ready(withEstimate())

        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        rule.onNodeWithText(READY_LINE).assertIsDisplayed()
    }

    // --- D-015, which is the assertion that matters most ---------------------

    /**
     * **Leaving pauses. It never stops.**
     *
     * The failure this guards is silent: stopping when the user meant to pause
     * loses the elapsed time with nothing that puts it back. Asserted against
     * the view model rather than the screen, because what went wrong would go
     * wrong behind the sheet closing.
     *
     * D-032 removed the dismiss button, so leaving is triggered through the
     * sheet's own dismiss action, which is where the drag, the scrim and the
     * back gesture all arrive. That the action exists at all is the other half
     * of this test, and what is left of "every state offers a way out": the
     * chevron used to be asserted separately and there is no longer a control
     * of this app's own to assert.
     */
    @Test
    fun leavingPausesRatherThanStopping() {
        val model = running(withEstimate())
        rule.waitUntilExactlyOneExists(hasContentDescription(PAUSE), TIMEOUT_MILLIS)

        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .onFirst()
            .performSemanticsAction(SemanticsActions.Dismiss)
        rule.waitForIdle()

        val session = model.focusSession.value
        assertNotNull("leaving discarded the session", session)
        assertTrue("leaving left the clock running", session!!.isPaused)
        // The sheet goes and the session does not, which is why the two are
        // separate facts in the view model.
        assertEquals(false, model.isFocusSheetOpen.value)
        // And the task is still chosen, or the Focus now card would have nothing
        // to point at and the paused session would be unreachable.
        assertEquals("1", model.focusedTask.value?.id)
    }

    /** Completing ends the task and closes the sheet, rather than advancing. */
    @Test
    fun completingEndsFocus() {
        val model = running(withEstimate())
        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)

        rule.onNodeWithText(COMPLETE).performClick()
        rule.waitForIdle()

        assertEquals(false, model.isFocusSheetOpen.value)
        // No next task. D-004 removed the queue, and there is nothing to advance
        // through even when another task is scheduled for the same day.
        rule.onAllNodesWithText(NEXT_PREFIX, substring = true).assertCountEquals(0)
    }

    private companion object {
        const val FIRST = "Review the quarterly budget"
        const val COMPLETE = "Complete"
        const val START = "Start focus"
        const val PAUSE = "Pause focus"
        const val RESUME = "Resume focus"
        const val ESTIMATE_STATUS = "45 min focus"
        const val REMAINING = "45 min left"
        const val NO_LIMIT = "No time limit"

        /** Ready, whole: the clock the session is about to spend, then the budget. */
        const val READY_LINE = "45:00 · 45 min focus"

        /** The queue's old footer, asserted absent so it cannot come back. */
        const val NEXT_PREFIX = "Next: "
        const val TIMEOUT_MILLIS = 5_000L
    }
}
