package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.today.TodayScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Today's accessibility contract, exercised through the real screen.
 *
 * The component tests in this package check each control in isolation. This one
 * checks that the screen actually composes them: a shared app bar that is a
 * heading, an empty state, a labelled add button, and a snackbar host that
 * announces itself. Isolated components can all pass while a screen forgets to
 * use one, and that is the gap this closes.
 *
 * It runs against a real [com.vignesh.focuslist.ui.task.TaskListViewModel] over
 * fake storage, so completing a task travels the whole production path and the
 * undo offer is the real one.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TodayScreenSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setToday(fontScale: Float, dao: FakeTaskDao) {
        val viewModel = testViewModel(dao)

        rule.setFocuslistContent(fontScale) {
            TodayScreen(viewModel = viewModel, onOpenTask = {})
        }
    }

    /**
     * The Focus now card opens the task it is about.
     *
     * The card used to take no click at all, on the argument that a clickable
     * container behind two controls is a target the user cannot see the edges
     * of. What that missed is D-012: the promoted task leaves the bands below,
     * so an unclickable card left the one task this screen is built around with
     * no route to its details from Today at all.
     *
     * The task is scheduled for today with no reminder, which is D-012's third
     * reason and the one that needs no clock.
     */
    private fun assertTheCardOpensItsTask(fontScale: Float) {
        var opened: String? = null
        val viewModel = testViewModel(withOneTask())

        rule.setFocuslistContent(fontScale) {
            TodayScreen(viewModel = viewModel, onOpenTask = { opened = it })
        }

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)

        // One match, which is the other half of the argument: the title is on
        // this screen once, in the card, and nowhere in the list beneath it.
        rule.onNodeWithText(TITLE).performClick()

        assertEquals("1", opened)
    }

    @Test
    fun focusNowCard_opensItsTask_at100() = assertTheCardOpensItsTask(FontScale100)

    @Test
    fun focusNowCard_opensItsTask_at200() = assertTheCardOpensItsTask(FontScale200)

    /** And says what tapping it does, since it is not a control that looks like one. */
    private fun assertTheCardNamesItsAction(fontScale: Float) {
        setToday(fontScale, withOneTask())

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        rule.onNodeWithText(TITLE)
            .assert(hasClickLabel(OPEN_TASK))
    }

    @Test
    fun focusNowCard_namesItsAction_at100() = assertTheCardNamesItsAction(FontScale100)

    @Test
    fun focusNowCard_namesItsAction_at200() = assertTheCardNamesItsAction(FontScale200)

    private fun withOneTask() = FakeTaskDao(
        listOf(testTask(id = "1", title = TITLE, scheduledDate = TestToday))
    )

    private fun assertScreenTitleIsAHeading(fontScale: Float) {
        setToday(fontScale, withOneTask())

        rule.onNode(hasText("Today") and isHeading()).assertExists()
    }

    @Test
    fun screenTitle_isAHeading_at100() = assertScreenTitleIsAHeading(FontScale100)

    @Test
    fun screenTitle_isAHeading_at200() = assertScreenTitleIsAHeading(FontScale200)

    private fun assertAddButtonIsLabelled(fontScale: Float) {
        setToday(fontScale, withOneTask())

        // The button carries no text; its icon's description is its whole name.
        // That is the regular floating action button's contract: 56dp of glyph,
        // and the label lives where a screen reader can still reach it.
        rule.onNodeWithContentDescription(ADD_TASK).assertIsDisplayed()
    }

    @Test
    fun addButton_isLabelled_at100() = assertAddButtonIsLabelled(FontScale100)

    @Test
    fun addButton_isLabelled_at200() = assertAddButtonIsLabelled(FontScale200)

    private fun assertAddButtonOpensQuickAdd(fontScale: Float) {
        setToday(fontScale, withOneTask())

        rule.onNodeWithContentDescription(ADD_TASK).performClick()

        rule.waitUntilExactlyOneExists(hasText(QUICK_ADD_LABEL), TIMEOUT_MILLIS)
        rule.onNodeWithText(QUICK_ADD_LABEL).assertIsDisplayed()
    }

    @Test
    fun addButton_opensQuickAdd_at100() = assertAddButtonOpensQuickAdd(FontScale100)

    @Test
    fun addButton_opensQuickAdd_at200() = assertAddButtonOpensQuickAdd(FontScale200)

    private fun assertEmptyStateIsReadable(fontScale: Float) {
        setToday(fontScale, FakeTaskDao())

        rule.onNodeWithText(EMPTY_HEADLINE).assertIsDisplayed()
        rule.onNodeWithText(EMPTY_SUPPORTING).assertIsDisplayed()
    }

    @Test
    fun emptyState_isReadable_at100() = assertEmptyStateIsReadable(FontScale100)

    @Test
    fun emptyState_isReadable_at200() = assertEmptyStateIsReadable(FontScale200)

    private fun assertCompletionAnnouncesItselfPolitely(fontScale: Float) {
        setToday(fontScale, withOneTask())

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(MARK_COMPLETE).performClick()

        rule.waitUntilExactlyOneExists(hasText(COMPLETED_MESSAGE), TIMEOUT_MILLIS)

        // The message has to sit inside the live region, not merely on screen.
        // A polite region is what lets a screen reader finish its sentence
        // before announcing the undo offer.
        //
        // Two match: the shared host's own region and the one Material's
        // `Snackbar` publishes inside it. `UndoSnackbarSemanticsTest` is what
        // pins the host's declaration down on its own.
        val regions = rule.onAllNodes(
            hasLiveRegion(LiveRegionMode.Polite) and
                hasAnyDescendant(hasText(COMPLETED_MESSAGE))
        ).fetchSemanticsNodes()

        assertTrue(
            "expected the completion message to sit inside a polite live region",
            regions.isNotEmpty()
        )

        rule.onNodeWithText(UNDO).assertIsDisplayed()
    }

    @Test
    fun completion_announcesItselfPolitely_at100() =
        assertCompletionAnnouncesItselfPolitely(FontScale100)

    @Test
    fun completion_announcesItselfPolitely_at200() =
        assertCompletionAnnouncesItselfPolitely(FontScale200)

    /**
     * Completing and taking it back.
     *
     * The completed task is opened before it is asserted on, because D-012 made
     * Completed a disclosure that is collapsed by default: a plain completed
     * list grows through the day and pushes live work down the screen. The row
     * exists either way, and this is the screen agreeing that it does.
     */
    private fun assertUndoReopensTheTask(fontScale: Float) {
        setToday(fontScale, withOneTask())

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(MARK_COMPLETE).performClick()

        rule.waitUntilExactlyOneExists(hasText(UNDO), TIMEOUT_MILLIS)

        // The band arrives collapsed, carrying its count.
        rule.waitUntilExactlyOneExists(hasText(COMPLETED_ONE), TIMEOUT_MILLIS)
        // Clicked by its words. The header carries an onClick *label*, which
        // names the action to a screen reader; the text is what is on screen.
        rule.onNodeWithText(COMPLETED_ONE).performClick()

        rule.waitUntilExactlyOneExists(
            hasContentDescriptionExactly(MARK_INCOMPLETE),
            TIMEOUT_MILLIS
        )
        rule.onNodeWithContentDescription(MARK_INCOMPLETE).assertIsOn()

        rule.onNodeWithText(UNDO).performClick()

        rule.waitUntilExactlyOneExists(hasContentDescriptionExactly(MARK_COMPLETE), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(MARK_COMPLETE).assertIsOff()
    }

    @Test
    fun undo_reopensTheTask_at100() = assertUndoReopensTheTask(FontScale100)

    @Test
    fun undo_reopensTheTask_at200() = assertUndoReopensTheTask(FontScale200)

    private companion object {
        const val TITLE = "Write the report"
        const val ADD_TASK = "Add task"
        const val QUICK_ADD_LABEL = "New task"
        const val EMPTY_HEADLINE = "Nothing scheduled for today"
        const val EMPTY_SUPPORTING = "Add a task when you are ready."
        const val COMPLETED_MESSAGE = "Task completed"
        const val UNDO = "Undo"
        const val MARK_COMPLETE = "Mark \"$TITLE\" complete"
        const val MARK_INCOMPLETE = "Mark \"$TITLE\" not complete"

        /** D-012's Completed disclosure, which carries a count and collapses. */
        const val COMPLETED_ONE = "Completed · 1"

        /** The same action label the task rows carry, because it is the same act. */
        const val OPEN_TASK = "Open task details"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
