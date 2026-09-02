package com.vignesh.focuslist.ui.semantics

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.focus.FocusScreen
import com.vignesh.focuslist.ui.task.TaskListViewModel
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

    private fun setFocus(
        fontScale: Float,
        dao: FakeTaskDao,
        beforeComposing: (TaskListViewModel) -> Unit = {}
    ) {
        val viewModel = testViewModel(dao)
        beforeComposing(viewModel)

        rule.setFocuslistContent(fontScale) {
            // A stand-in for the navigation bar. The real one is handed in the
            // same way, so whether it renders is the same question.
            FocusScreen(viewModel = viewModel, bottomBar = { Text(NAV_BAR) })
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

    // Ready

    private fun assertReadyShowsTheEstimateAndKeepsNavigation(fontScale: Float) {
        setFocus(fontScale, withQueue())

        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        // The estimate Today already shows. A screen about doing the work
        // should not be the one place the size of it is withheld.
        rule.onNodeWithText(ESTIMATE).assertIsDisplayed()
        rule.onNodeWithText(START).assertIsDisplayed()
        rule.onNodeWithText(NAV_BAR).assertIsDisplayed()
    }

    @Test
    fun ready_showsEstimateAndKeepsNavigation_at100() =
        assertReadyShowsTheEstimateAndKeepsNavigation(FontScale100)

    @Test
    fun ready_showsEstimateAndKeepsNavigation_at200() =
        assertReadyShowsTheEstimateAndKeepsNavigation(FontScale200)

    // Session

    private fun assertSessionHidesNavigationButOffersAWayOut(fontScale: Float) {
        setFocus(fontScale, withQueue())

        rule.waitUntilExactlyOneExists(hasText(START), TIMEOUT_MILLIS)
        rule.onNodeWithText(START).performClick()

        // Hiding the bar is only honest while the exit is on screen. Back
        // alone would not do: gesture navigation draws no button, and this
        // screen has taken the visible one away.
        rule.waitUntilDoesNotExist(hasText(NAV_BAR), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(STOP).assertIsDisplayed()
    }

    @Test
    fun session_hidesNavigationButOffersAWayOut_at100() =
        assertSessionHidesNavigationButOffersAWayOut(FontScale100)

    @Test
    fun session_hidesNavigationButOffersAWayOut_at200() =
        assertSessionHidesNavigationButOffersAWayOut(FontScale200)

    @Test
    fun session_namesWhatComesNext() {
        setFocus(FontScale100, withQueue())

        rule.waitUntilExactlyOneExists(hasText(START), TIMEOUT_MILLIS)
        rule.onNodeWithText(START).performClick()

        rule.waitUntilExactlyOneExists(hasText(NEXT_SECOND), TIMEOUT_MILLIS)
    }

    @Test
    fun session_saysNothingAboutNextWhenNothingFollows() {
        setFocus(FontScale100, withOneTask())

        rule.waitUntilExactlyOneExists(hasText(START), TIMEOUT_MILLIS)
        rule.onNodeWithText(START).performClick()

        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        rule.onAllNodes(hasText(NEXT_PREFIX, substring = true)).assertCountEquals(0)
    }

    @Test
    fun stopping_returnsTheNavigation() {
        setFocus(FontScale100, withQueue())

        rule.waitUntilExactlyOneExists(hasText(START), TIMEOUT_MILLIS)
        rule.onNodeWithText(START).performClick()
        rule.waitUntilDoesNotExist(hasText(NAV_BAR), TIMEOUT_MILLIS)

        rule.onNodeWithContentDescription(STOP).performClick()

        rule.waitUntilExactlyOneExists(hasText(NAV_BAR), TIMEOUT_MILLIS)
    }

    @Test
    fun aSessionStartedBeforeTheScreenOpens_survivesTheScreenOpening() {
        // The route in from a task row: the row starts the session and then
        // navigates, so Focus composes with one already running.
        //
        // Every exposed flow begins on a placeholder before storage has
        // answered, and a screen that reads that placeholder cannot tell "the
        // queue is empty" from "the queue has not loaded". Reading it as empty
        // ends the session on the way in, which is the one entry that has to
        // work.
        setFocus(FontScale100, withQueue(), beforeComposing = { it.startFocusSession() })

        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(STOP).assertIsDisplayed()
        rule.onAllNodesWithText(START).assertCountEquals(0)
    }

    // The queue running out

    @Test
    fun completing_advancesWithoutLeavingTheSession() {
        setFocus(FontScale100, withQueue())

        rule.waitUntilExactlyOneExists(hasText(START), TIMEOUT_MILLIS)
        rule.onNodeWithText(START).performClick()
        rule.waitUntilExactlyOneExists(hasText(FIRST), TIMEOUT_MILLIS)

        rule.onNodeWithText(COMPLETE).performClick()

        // The next task, still in the session: the loop continues rather than
        // dropping the user back at a list to choose again.
        rule.waitUntilExactlyOneExists(hasText(SECOND), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(STOP).assertIsDisplayed()
    }

    @Test
    fun anEmptiedQueue_endsTheSessionAndReturnsTheNavigation() {
        setFocus(FontScale100, withOneTask())

        rule.waitUntilExactlyOneExists(hasText(START), TIMEOUT_MILLIS)
        rule.onNodeWithText(START).performClick()
        rule.waitUntilDoesNotExist(hasText(NAV_BAR), TIMEOUT_MILLIS)

        rule.onNodeWithText(COMPLETE).performClick()

        // Nothing left to work on, so the mode is over whether or not it was
        // stopped. Leaving it running would strand the user on an empty screen
        // with no navigation, which is the trap the mode exists to avoid.
        rule.waitUntilExactlyOneExists(hasText(EMPTY_HEADLINE), TIMEOUT_MILLIS)
        rule.onNodeWithText(NAV_BAR).assertIsDisplayed()
    }

    private companion object {
        const val FIRST = "Review the quarterly budget"
        const val SECOND = "Call the plumber about the leak"
        const val ESTIMATE = "45 min"
        const val START = "Start"
        const val COMPLETE = "Complete"
        const val STOP = "Stop focusing"
        const val NAV_BAR = "navigation bar"
        const val NEXT_PREFIX = "Next: "
        const val NEXT_SECOND = "Next: Call the plumber about the leak"
        const val EMPTY_HEADLINE = "Nothing to focus on"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
