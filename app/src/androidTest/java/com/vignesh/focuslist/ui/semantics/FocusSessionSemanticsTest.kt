package com.vignesh.focuslist.ui.semantics

import android.Manifest
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.focus.FocusSheet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focus's two states, and the promise that makes hiding the navigation safe.
 *
 * Ready is a destination and keeps its navigation. Session is a mode and takes
 * it away. The whole design rests on that being reversible without guessing:
 * a user who started a session must be able to see the way out, and one whose
 * queue runs dry must get the navigation back without doing anything at all.
 *
 * Runs against a real view model over fake storage, so completing a task here
 * travels the production path and the queue advances for the real reason.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FocusSessionSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * A session with an estimate asks to post notifications, so the estimate
     * can be announced when it is reached. Left ungranted, the system dialog
     * opens over the screen and every assertion after it fails against a
     * hierarchy that is no longer in front.
     *
     * The tests that pass without this are the ones whose task carries no
     * estimate, which is why the split looked like a queue-length problem and
     * was not.
     */
    @Before
    fun grantNotifications() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Composes the sheet with a session already running on [focusOn].
     *
     * That is the only way in now. Focus is opened by choosing a task, so there
     * is no state in which the sheet is on screen and nothing has been started;
     * the nav host keys its presence on the running session for exactly that
     * reason.
     */
    private fun setFocus(
        fontScale: Float,
        dao: FakeTaskDao,
        focusOn: String? = "1"
    ) {
        val viewModel = testViewModel(dao)
        if (focusOn != null) viewModel.beginFocus(focusOn)

        rule.setFocuslistContent(fontScale) {
            FocusSheet(viewModel = viewModel)
        }
    }

    private fun withQueue() = FakeTaskDao(
        listOf(
            testTask(
                id = "1",
                title = FIRST,
                scheduledDate = TestToday,
                estimatedDurationMinutes = 45
            ),
            testTask(id = "2", title = SECOND, scheduledDate = TestToday)
        )
    )

    private fun withOneTask() = FakeTaskDao(
        listOf(testTask(id = "1", title = FIRST, scheduledDate = TestToday))
    )

    // What the sheet opens on

    private fun assertOpensOnTheChosenTaskWithItsEstimate(fontScale: Float) {
        setFocus(fontScale, withQueue())

        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        // The estimate Today already shows. A screen about doing the work
        // should not be the one place the size of it is withheld.
        rule.onNodeWithText(ESTIMATE).assertIsDisplayed()
        rule.onNodeWithText(COMPLETE).assertIsDisplayed()
    }

    @Test
    fun opensOnTheChosenTaskWithItsEstimate_at100() =
        assertOpensOnTheChosenTaskWithItsEstimate(FontScale100)

    @Test
    fun opensOnTheChosenTaskWithItsEstimate_at200() =
        assertOpensOnTheChosenTaskWithItsEstimate(FontScale200)

    /** The chosen one, not the head of the queue. That is the whole point. */
    @Test
    fun opensOnTheTaskThatWasChosen_notTheHeadOfTheQueue() {
        setFocus(FontScale100, withQueue(), focusOn = "2")

        rule.waitUntilExactlyOneExists(hasText(SECOND), TIMEOUT_MILLIS)
    }

    @Test
    fun namesWhatComesNext() {
        setFocus(FontScale100, withQueue())

        rule.waitUntilExactlyOneExists(hasText(NEXT_SECOND), TIMEOUT_MILLIS)
    }

    @Test
    fun saysNothingAboutNextWhenNothingFollows() {
        setFocus(FontScale100, withOneTask())

        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        rule.onAllNodes(hasText(NEXT_PREFIX, substring = true)).assertCountEquals(0)
    }

    // The queue running through

    @Test
    fun completing_advancesWithoutLeavingTheSheet() {
        setFocus(FontScale100, withQueue())
        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)

        rule.onNodeWithText(COMPLETE).performClick()

        // The next task, still in the sheet. This is the behaviour that decided
        // the design: Focus is somewhere you keep working, not a drawer that
        // closes when one task is done.
        rule.waitUntilExactlyOneExists(hasText(SECOND), TIMEOUT_MILLIS)
        rule.onNodeWithText(COMPLETE).assertIsDisplayed()
    }

    /**
     * Finishing the last one is the moment the user has most earned being told
     * they are done, so the sheet stays and says so. It used to close itself,
     * because a running session with nothing in it hid the navigation bar; a
     * sheet hides nothing, so that reason went with the redesign.
     */
    @Test
    fun anEmptiedQueue_showsTheEmptyStateWithoutClosing() {
        setFocus(FontScale100, withOneTask())
        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)

        rule.onNodeWithText(COMPLETE).performClick()

        rule.waitUntilExactlyOneExists(hasText(EMPTY_HEADLINE), TIMEOUT_MILLIS)
    }

    private companion object {
        const val FIRST = "Review the quarterly budget"
        const val SECOND = "Call the plumber about the leak"
        const val ESTIMATE = "45 min"
        const val COMPLETE = "Complete"
        const val NEXT_PREFIX = "Next: "
        const val NEXT_SECOND = "Next: Call the plumber about the leak"
        const val EMPTY_HEADLINE = "Nothing to focus on"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
