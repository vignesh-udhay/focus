package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertHasClickAction
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
import com.vignesh.focuslist.core.domain.HealthCheck
import com.vignesh.focuslist.core.domain.DeliveryOutcome
import com.vignesh.focuslist.core.domain.ReminderDelivery
import com.vignesh.focuslist.core.domain.ReminderHealthState
import com.vignesh.focuslist.ui.today.TodayScreen
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
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
     * Today with a reminder health answer, which is D-040's banner input.
     *
     * Handed in rather than produced, because the states that draw a banner are
     * a refused permission and a recorded miss, and neither can be provoked from
     * a test. The screen takes the answer as a parameter for exactly this
     * reason.
     */
    private fun setToday(
        fontScale: Float,
        dao: FakeTaskDao,
        health: ReminderHealthState?,
        onOpenHealth: () -> Unit = {},
        onDismissHealth: () -> Unit = {}
    ) {
        val viewModel = testViewModel(dao)

        rule.setFocuslistContent(fontScale) {
            TodayScreen(
                viewModel = viewModel,
                onOpenTask = {},
                reminderHealth = health,
                onOpenReminderHealth = onOpenHealth,
                onDismissReminderHealth = onDismissHealth
            )
        }
    }

    /**
     * A task with a passed reminder is a row, and the row opens it.
     *
     * **This pair of tests used to be about the card.** D-012 promoted such a task
     * out of its band and made the card clickable, because that promotion left the
     * one task the screen was built around with no route to its details from Today
     * at all. D-048 removes both halves: the reason that drew the card for a passed
     * reminder, and the promotion. So the title appears exactly once, in the list,
     * and the thing carrying the open action is the row.
     *
     * The single match is the assertion that matters. Two would mean the card came
     * back without the promotion going with it, which is the one arrangement D-048
     * rules out.
     */
    private fun assertThePassedReminderIsARow(fontScale: Float) {
        var opened: String? = null
        val viewModel = testViewModel(withOneTask())

        rule.setFocuslistContent(fontScale) {
            TodayScreen(viewModel = viewModel, onOpenTask = { opened = it })
        }

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        rule.onNodeWithText(TITLE).performClick()

        assertEquals("1", opened)
    }

    @Test
    fun passedReminder_isARow_at100() = assertThePassedReminderIsARow(FontScale100)

    @Test
    fun passedReminder_isARow_at200() = assertThePassedReminderIsARow(FontScale200)

    /**
     * Leaving Focus inserts the paused-session card above the first Today band.
     *
     * LazyColumn normally keeps the old first item's key at the same visual
     * position when an item is inserted ahead of it. At the top of Today that
     * would keep Overdue pinned and place the new card just above the viewport,
     * making the control created by leaving Focus invisible until the user
     * scrolls backwards.
     */
    @Test
    fun leavingFocus_keepsPausedSessionCardVisible() {
        val viewModel = testViewModel(withOneTask())

        rule.setFocuslistContent(FontScale100) {
            TodayScreen(viewModel = viewModel, onOpenTask = {})
        }

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)

        rule.runOnIdle {
            viewModel.beginFocus("1")
            viewModel.leaveFocusSheet()
        }

        rule.waitUntilExactlyOneExists(hasText(RESUME_FOCUS), TIMEOUT_MILLIS)
        rule.onNodeWithText(RESUME_FOCUS).assertIsDisplayed()
    }

    /** And the row says what tapping it does, in the words every row uses. */
    private fun assertTheRowNamesItsAction(fontScale: Float) {
        setToday(fontScale, withOneTask())

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        rule.onNodeWithText(TITLE)
            .assert(hasClickLabel(OPEN_TASK))
    }

    @Test
    fun taskRow_namesItsAction_at100() = assertTheRowNamesItsAction(FontScale100)

    @Test
    fun taskRow_namesItsAction_at200() = assertTheRowNamesItsAction(FontScale200)

    /**
     * One task, carrying a reminder that has passed.
     *
     * The reminder used to be what put the task in the card, and since D-048 it
     * puts the task in the Overdue band instead. Kept as the fixture because it is
     * an ordinary row with metadata on it, which is a better default for the
     * assertions below than a task with nothing set.
     */
    private fun withOneTask() = FakeTaskDao(
        listOf(
            testTask(
                id = "1",
                title = TITLE,
                scheduledDate = TestToday,
                reminderAt = TestPassedReminder
            )
        )
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

    /**
     * The banner says which check failed, not that something is wrong.
     *
     * The sentence is the health screen's own, which is the point: a user who
     * taps through must not be met by a second wording of the same fact.
     */
    private fun assertTheBannerNamesTheCause(fontScale: Float) {
        setToday(
            fontScale,
            withOneTask(),
            ReminderHealthState.ActionNeeded(HealthCheck.Notifications)
        )

        rule.onNodeWithText(BANNER_NO_NOTIFICATIONS).assertIsDisplayed()
        rule.onNodeWithText(BANNER_ACTION_LABEL).assertIsDisplayed()
    }

    @Test
    fun reminderBanner_namesTheCause_at100() = assertTheBannerNamesTheCause(FontScale100)

    @Test
    fun reminderBanner_namesTheCause_at200() = assertTheBannerNamesTheCause(FontScale200)

    /** And it is one target that goes to the screen that can fix the problem. */
    private fun assertTheBannerOpensHealth(fontScale: Float) {
        var opened = false

        setToday(
            fontScale,
            withOneTask(),
            ReminderHealthState.ActionNeeded(HealthCheck.ExactAlarms),
            onOpenHealth = { opened = true }
        )

        rule.onNodeWithText(BANNER_LATE).assertHasClickAction()
        rule.onNodeWithText(BANNER_LATE).performClick()

        assertTrue("The banner did not open Reminder health", opened)
    }

    @Test
    fun reminderBanner_opensHealth_at100() = assertTheBannerOpensHealth(FontScale100)

    @Test
    fun reminderBanner_opensHealth_at200() = assertTheBannerOpensHealth(FontScale200)

    /** A past incident can be acknowledged without losing its detail route. */
    private fun assertMissedBannerCanBeDismissed(fontScale: Float) {
        var dismissed = false

        setToday(
            fontScale,
            withOneTask(),
            missedHealth(),
            onDismissHealth = { dismissed = true }
        )

        rule.onNodeWithContentDescription(BANNER_DISMISS)
            .assertIsDisplayed()
            .performClick()

        assertTrue("The missed-reminder notice was not dismissed", dismissed)
    }

    @Test
    fun missedReminderBanner_canBeDismissed_at100() =
        assertMissedBannerCanBeDismissed(FontScale100)

    @Test
    fun missedReminderBanner_canBeDismissed_at200() =
        assertMissedBannerCanBeDismissed(FontScale200)

    /** The banner body remains the route to the full, retained incident. */
    private fun assertMissedBannerStillOpensHealth(fontScale: Float) {
        var opened = false

        setToday(
            fontScale,
            withOneTask(),
            missedHealth(),
            onOpenHealth = { opened = true }
        )

        rule.onNodeWithText(BANNER_MISSED_LABEL).performClick()

        assertTrue("The missed-reminder banner did not open Reminder health", opened)
    }

    @Test
    fun missedReminderBanner_stillOpensHealth_at100() =
        assertMissedBannerStillOpensHealth(FontScale100)

    @Test
    fun missedReminderBanner_stillOpensHealth_at200() =
        assertMissedBannerStillOpensHealth(FontScale200)

    /** An active delivery failure cannot be acknowledged away. */
    @Test
    fun actionNeededBanner_hasNoDismissAction() {
        setToday(
            FontScale100,
            withOneTask(),
            ReminderHealthState.ActionNeeded(HealthCheck.Notifications)
        )

        rule.onNodeWithContentDescription(BANNER_DISMISS).assertDoesNotExist()
    }

    /**
     * It survives the empty screen, which is the case D-040 says matters most.
     *
     * The empty state replaces the collection rather than sitting inside it, so
     * a banner written only into the list would be missing from the one screen
     * a user with no tasks and no notification permission actually sees.
     */
    private fun assertTheBannerSurvivesTheEmptyScreen(fontScale: Float) {
        setToday(
            fontScale,
            FakeTaskDao(),
            ReminderHealthState.ActionNeeded(HealthCheck.Notifications)
        )

        rule.onNodeWithText(EMPTY_HEADLINE).assertIsDisplayed()
        rule.onNodeWithText(BANNER_NO_NOTIFICATIONS).assertIsDisplayed()
    }

    @Test
    fun reminderBanner_survivesTheEmptyScreen_at100() =
        assertTheBannerSurvivesTheEmptyScreen(FontScale100)

    @Test
    fun reminderBanner_survivesTheEmptyScreen_at200() =
        assertTheBannerSurvivesTheEmptyScreen(FontScale200)

    /**
     * **A guess does not earn a banner**, which is the assertion this file
     * exists to hold on to.
     *
     * `WorthChecking` is inferred from `Build.MANUFACTURER` alone. If it ever
     * starts drawing, every OnePlus, OPPO, Realme, Xiaomi, Redmi, POCO, Samsung,
     * Huawei and Honor owner gets a permanent notice on their default screen
     * that no action of theirs can clear. D-021 and D-040 both turn on this.
     */
    @Test
    fun reminderBanner_isAbsent_forAnInferredRestriction() {
        setToday(
            FontScale100,
            withOneTask(),
            ReminderHealthState.WorthChecking(HealthCheck.BackgroundWork)
        )

        rule.onNodeWithText(BANNER_ACTION_LABEL).assertDoesNotExist()
        rule.onNodeWithText(BANNER_MISSED_LABEL).assertDoesNotExist()
    }

    /** And a healthy app says nothing at all on Today. */
    @Test
    fun reminderBanner_isAbsent_whenRemindersAreHealthy() {
        setToday(FontScale100, withOneTask(), ReminderHealthState.Ready)

        rule.onNodeWithText(BANNER_ACTION_LABEL).assertDoesNotExist()
        rule.onNodeWithText(BANNER_MISSED_LABEL).assertDoesNotExist()
    }

    /**
     * Nor before the first check has run.
     *
     * `Checking` is the state the view model holds until `refresh` completes, so
     * this is what Today draws for a frame on every cold start.
     */
    @Test
    fun reminderBanner_isAbsent_whileStillChecking() {
        setToday(FontScale100, withOneTask(), ReminderHealthState.Checking)

        rule.onNodeWithText(BANNER_ACTION_LABEL).assertDoesNotExist()
        rule.onNodeWithText(BANNER_MISSED_LABEL).assertDoesNotExist()
    }

    private fun missedHealth(): ReminderHealthState.Missed = ReminderHealthState.Missed(
        ReminderDelivery(
            id = "missed-delivery",
            taskId = "task-with-missed-reminder",
            taskTitle = "Take medication",
            dueAt = LocalDateTime.of(2026, 9, 9, 23, 15),
            scheduledWallAt = Instant.parse("2026-09-09T17:45:00Z"),
            scheduledElapsedAt = 1_000_000L,
            arrivedWallAt = Instant.parse("2026-09-09T17:48:00Z"),
            arrivedElapsedAt = 1_180_000L,
            scheduledAhead = Duration.ofHours(8),
            outcome = DeliveryOutcome.Announced
        )
    )

    private companion object {
        const val TITLE = "Write the report"
        const val ADD_TASK = "Add task"
        const val QUICK_ADD_LABEL = "New task"
        const val EMPTY_HEADLINE = "Nothing scheduled for today"
        const val EMPTY_SUPPORTING = "Tasks without a day wait in your Inbox."
        const val COMPLETED_MESSAGE = "Task completed"
        const val UNDO = "Undo"
        const val MARK_COMPLETE = "Mark \"$TITLE\" complete"
        const val MARK_INCOMPLETE = "Mark \"$TITLE\" not complete"
        const val RESUME_FOCUS = "Resume focus"

        /** D-012's Completed disclosure, which carries a count and collapses. */
        const val COMPLETED_ONE = "Completed · 1"

        /** The same action label the task rows carry, because it is the same act. */
        const val OPEN_TASK = "Open task details"

        /** D-040's banner, in the health screen's own words. */
        const val BANNER_ACTION_LABEL = "Action needed"
        const val BANNER_MISSED_LABEL = "Missed reminder"
        const val BANNER_NO_NOTIFICATIONS = "Catimo cannot show notifications"
        const val BANNER_LATE = "Reminders may arrive late"
        const val BANNER_DISMISS = "Dismiss missed reminder notice"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
