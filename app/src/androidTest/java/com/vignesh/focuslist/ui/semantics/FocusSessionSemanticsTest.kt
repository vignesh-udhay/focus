package com.vignesh.focuslist.ui.semantics

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
     * [start] is what puts the session in one of the five states, reached by
     * driving the same controls the user would. There were six, and D-054 removed
     * Ready: a chosen task with no clock had no entry point left once D-048 took
     * away the card branch that opened one.
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

    private fun running(dao: FakeTaskDao, fontScale: Float = FontScale100) =
        setFocus(fontScale, dao) { model -> model.beginFocus("1") }

    private fun paused(dao: FakeTaskDao, fontScale: Float = FontScale100) =
        setFocus(fontScale, dao) { model -> model.beginFocus("1"); model.pauseFocusSession() }

    // --- the status line's spoken form ---------------------------------------

    /**
     * The clock is announced as time left or time spent, never as bare digits.
     *
     * **These two strings existed for a long time and nothing used them.**
     * `strings.xml` carried `focus_readout_remaining` and `focus_readout_elapsed`
     * under a comment saying the readout "is digits, so it carries a spoken form
     * for TalkBack", and no Kotlin ever referenced either. A screen reader was
     * given "44:37", which does not say whether that is time left or time spent,
     * and those are opposite readings of the same four digits.
     *
     * Asserted here rather than trusted, because a description that goes missing
     * again would break nothing a sighted test can see: every other assertion in
     * this file still passes with the readout unannounced. That is exactly how it
     * was lost the first time.
     */
    private fun assertTheReadoutIsSpoken(fontScale: Float) {
        paused(withEstimate(), fontScale)
        rule.waitUntilExactlyOneExists(hasContentDescription(RESUME), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(PAUSED_SPOKEN, substring = true).assertIsDisplayed()
    }

    @Test
    fun readout_isSpokenAsRemaining_at100() = assertTheReadoutIsSpoken(FontScale100)

    @Test
    fun readout_isSpokenAsRemaining_at200() = assertTheReadoutIsSpoken(FontScale200)

    /**
     * A session with no estimate counts up, so its readout is time spent. The
     * word has to follow the direction of the clock or it states the opposite of
     * what the digits mean.
     */
    @Test
    fun readout_isSpokenAsElapsed_whenThereIsNoEstimate() {
        running(withoutEstimate())
        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(ELAPSED_SUFFIX, substring = true).assertIsDisplayed()
    }

    // --- the five states ------------------------------------------------------

    /**
     * **A Ready pair used to open this section**, asserting that the state named
     * its Start focus control. D-054 removed the state and the control with it, and
     * the principle it stood for is covered by the four tests below: every state
     * names its clock control by what pressing it does, because an icon carries no
     * text and a description reading "play" would describe the drawing rather than
     * the action.
     */

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
     * match on a substring for that reason: every state's line begins with a
     * readout, and in three of the five it is ticking.
     *
     * **This used to assert Ready's whole line as an exact string**, since nothing
     * was running and the readout sat at the estimate. D-054 removed Ready, and
     * Paused is now the only frozen clock. Its digits cannot be written down in
     * advance, because they are whatever the clock read at the moment it stopped,
     * so the shape of the line is asserted instead: two fields, the readout first.
     */
    @Test
    fun pausedPutsTheClockOnTheStatusLine() {
        paused(withEstimate())

        rule.waitUntilExactlyOneExists(hasContentDescription(RESUME), TIMEOUT_MILLIS)
        rule.onNode(hasTextMatching(PAUSED_LINE)).assertIsDisplayed()
    }

    /** The status line as a whole, so a missing readout fails rather than passes. */
    private fun hasTextMatching(pattern: Regex) = SemanticsMatcher("text matches $pattern") { node ->
        node.config.getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .any { pattern.matches(it.text) }
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
        const val PAUSE = "Pause focus"
        const val RESUME = "Resume focus"
        const val ESTIMATE_STATUS = "45 min focus"
        const val REMAINING = "45 min left"
        const val NO_LIMIT = "No time limit"

        /**
         * Paused, whole: a two-field readout, then the budget. The digits are a
         * pattern rather than a literal because they are whatever the clock read
         * when it stopped, which is a fraction of a second after it started.
         */
        val PAUSED_LINE = Regex("""^\d+:\d{2} · 45 min left$""")

        /**
         * The same line as a screen reader gets it: the clock said in words, and a
         * comma where the middle dot is drawn, because a dot does not read as a
         * pause aloud.
         */
        const val PAUSED_SPOKEN = "remaining, 45 min left"

        /** Counting up has no estimate to be remaining against. */
        const val ELAPSED_SUFFIX = "elapsed"

        /** The queue's old footer, asserted absent so it cannot come back. */
        const val NEXT_PREFIX = "Next: "
        const val TIMEOUT_MILLIS = 5_000L
    }
}
