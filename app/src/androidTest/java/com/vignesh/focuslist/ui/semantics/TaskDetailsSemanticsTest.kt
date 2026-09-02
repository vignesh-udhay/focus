package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.ui.task.TaskDetailsSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Task Details accessibility contract, including its two choice groups.
 *
 * This is the densest surface in the app: seven fields, three of them with
 * their own clear button, and two connected button groups. The contracts that
 * matter are that every field says what it is, that the three identical
 * "Clear" buttons are told apart by description rather than by position, that
 * each choice group publishes which option is current, and that invalid input
 * is refused visibly instead of being dropped on save.
 *
 * Both groups are run at 200%, which is the scale that broke the segmented
 * button row they replaced: three labels no longer fit across a phone, and the
 * last one was pushed outside its own segment and off the field. A group that
 * wraps keeps every option on screen, and `performScrollTo` reaching it is
 * what proves it.
 *
 * Save is reached with an explicit scroll. That is the point of the sheet's
 * `verticalScroll`: at 200% the button is below the fold, and a test that only
 * passed at 100% would not prove it is reachable at all.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TaskDetailsSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setSheet(
        fontScale: Float,
        task: Task = editableTask(),
        onDismiss: () -> Unit = {},
        onSave: (Task) -> Unit = {}
    ) {
        rule.setFocuslistContent(fontScale) {
            TaskDetailsSheet(
                task = task,
                today = TestToday,
                onDismiss = onDismiss,
                onSave = onSave
            )
        }

        rule.waitUntilExactlyOneExists(hasText(HEADING), TIMEOUT_MILLIS)
    }

    private fun assertHeadingIsMarkedUp(fontScale: Float) {
        setSheet(fontScale)

        // A screen reader lands on the sheet and is told what it is.
        rule.onNode(hasText(HEADING) and isHeading()).assertExists()
    }

    @Test
    fun heading_isMarkedUp_at100() = assertHeadingIsMarkedUp(FontScale100)

    @Test
    fun heading_isMarkedUp_at200() = assertHeadingIsMarkedUp(FontScale200)

    private fun assertEveryFieldIsLabelled(fontScale: Float) {
        setSheet(fontScale)

        FIELD_LABELS.forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun everyField_isLabelled_at100() = assertEveryFieldIsLabelled(FontScale100)

    @Test
    fun everyField_isLabelled_at200() = assertEveryFieldIsLabelled(FontScale200)

    private fun assertClearButtonsAreDistinguishable(fontScale: Float) {
        setSheet(fontScale)

        // Three buttons all reading "Clear". Only the description tells a
        // screen reader which one clears what.
        CLEAR_DESCRIPTIONS.forEach { description ->
            rule.onNodeWithContentDescription(description).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun clearButtons_areDistinguishable_at100() =
        assertClearButtonsAreDistinguishable(FontScale100)

    @Test
    fun clearButtons_areDistinguishable_at200() =
        assertClearButtonsAreDistinguishable(FontScale200)

    private fun assertPlacementPublishesCurrentOption(fontScale: Float) {
        setSheet(fontScale)

        rule.onNode(hasText(ANYTIME) and isSelectable()).performScrollTo().assertIsSelected()
        rule.onNode(hasText(INBOX) and isSelectable()).assertIsNotSelected()
        rule.onNode(hasText(SOMEDAY) and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun placement_publishesCurrentOption_at100() =
        assertPlacementPublishesCurrentOption(FontScale100)

    @Test
    fun placement_publishesCurrentOption_at200() =
        assertPlacementPublishesCurrentOption(FontScale200)

    private fun assertPlacementSelectionMoves(fontScale: Float) {
        setSheet(fontScale)

        rule.onNode(hasText(SOMEDAY) and isSelectable()).performScrollTo().performClick()

        rule.onNode(hasText(SOMEDAY) and isSelectable()).assertIsSelected()
        rule.onNode(hasText(ANYTIME) and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun placementSelection_moves_at100() = assertPlacementSelectionMoves(FontScale100)

    @Test
    fun placementSelection_moves_at200() = assertPlacementSelectionMoves(FontScale200)

    private fun assertRecurrencePublishesCurrentOption(fontScale: Float) {
        setSheet(fontScale)

        // The fixture does not repeat, so Never is the answer.
        rule.onNode(hasText(NEVER) and isSelectable()).performScrollTo().assertIsSelected()
        rule.onNode(hasText(DAILY) and isSelectable()).assertIsNotSelected()
        rule.onNode(hasText(YEARLY) and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun recurrence_publishesCurrentOption_at100() =
        assertRecurrencePublishesCurrentOption(FontScale100)

    @Test
    fun recurrence_publishesCurrentOption_at200() =
        assertRecurrencePublishesCurrentOption(FontScale200)

    private fun assertEveryRecurrenceOptionIsReachable(fontScale: Float) {
        setSheet(fontScale)

        // The point of the wrap: at 200% these do not fit on one line, and
        // every one of them still has to be on screen and hittable rather
        // than clipped off the side of the field.
        RECURRENCE_OPTIONS.forEach { option ->
            rule.onNode(hasText(option) and isSelectable()).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun everyRecurrenceOption_isReachable_at100() =
        assertEveryRecurrenceOptionIsReachable(FontScale100)

    @Test
    fun everyRecurrenceOption_isReachable_at200() =
        assertEveryRecurrenceOptionIsReachable(FontScale200)

    private fun assertEveryPlacementOptionIsReachable(fontScale: Float) {
        // Composed once, outside the loop. `setContent` may be called only
        // once per activity, so setting it per option threw before the second
        // assertion ever ran. The recurrence test above is the correct shape.
        setSheet(fontScale)

        listOf(INBOX, ANYTIME, SOMEDAY).forEach { option ->
            rule.onNode(hasText(option) and isSelectable()).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun everyPlacementOption_isReachable_at100() =
        assertEveryPlacementOptionIsReachable(FontScale100)

    @Test
    fun everyPlacementOption_isReachable_at200() =
        assertEveryPlacementOptionIsReachable(FontScale200)

    private fun assertRecurrenceSelectionMovesAndSaves(fontScale: Float) {
        val saved = mutableListOf<Task>()
        setSheet(fontScale, onSave = { task -> saved += task })

        rule.onNode(hasText(WEEKLY) and isSelectable()).performScrollTo().performClick()
        rule.onNode(hasText(WEEKLY) and isSelectable()).assertIsSelected()
        rule.onNode(hasText(NEVER) and isSelectable()).assertIsNotSelected()

        rule.onNodeWithText(SAVE).performScrollTo().performClick()

        assertEquals(Recurrence.WEEKLY, saved.single().recurrence)
    }

    @Test
    fun recurrenceSelection_movesAndSaves_at100() =
        assertRecurrenceSelectionMovesAndSaves(FontScale100)

    @Test
    fun recurrenceSelection_movesAndSaves_at200() =
        assertRecurrenceSelectionMovesAndSaves(FontScale200)

    private fun assertRecurrenceCanBeTakenAway(fontScale: Float) {
        val saved = mutableListOf<Task>()
        setSheet(
            fontScale,
            task = editableTask().copy(recurrence = Recurrence.MONTHLY),
            onSave = { task -> saved += task }
        )

        rule.onNode(hasText(MONTHLY) and isSelectable()).performScrollTo().assertIsSelected()
        rule.onNode(hasText(NEVER) and isSelectable()).performScrollTo().performClick()

        rule.onNodeWithText(SAVE).performScrollTo().performClick()

        // Never is a real answer, not the absence of one.
        assertEquals(null, saved.single().recurrence)
    }

    @Test
    fun recurrence_canBeTakenAway_at100() = assertRecurrenceCanBeTakenAway(FontScale100)

    @Test
    fun recurrence_canBeTakenAway_at200() = assertRecurrenceCanBeTakenAway(FontScale200)

    private fun assertReselectingAnOptionDoesNotClearIt(fontScale: Float) {
        setSheet(fontScale)

        // A toggle button would uncheck. In a group where one option is always
        // the answer, tapping the current one has to leave it standing.
        rule.onNode(hasText(ANYTIME) and isSelectable()).performScrollTo().performClick()

        rule.onNode(hasText(ANYTIME) and isSelectable()).assertIsSelected()
    }

    @Test
    fun reselectingAnOption_doesNotClearIt_at100() =
        assertReselectingAnOptionDoesNotClearIt(FontScale100)

    @Test
    fun reselectingAnOption_doesNotClearIt_at200() =
        assertReselectingAnOptionDoesNotClearIt(FontScale200)

    private fun assertBlankTitleRefusesSave(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(TITLE_LABEL).performScrollTo().performTextClearance()

        rule.onNodeWithText(SAVE).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun blankTitle_refusesSave_at100() = assertBlankTitleRefusesSave(FontScale100)

    @Test
    fun blankTitle_refusesSave_at200() = assertBlankTitleRefusesSave(FontScale200)

    private fun assertUnreadableDateIsReportedAndRefused(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(SCHEDULED_TEXT).performScrollTo()
            .performTextReplacement("the day after the thing")

        // Said out loud, not dropped silently on save.
        rule.onNodeWithText(DATE_ERROR).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(SAVE).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun unreadableDate_isReportedAndRefused_at100() =
        assertUnreadableDateIsReportedAndRefused(FontScale100)

    @Test
    fun unreadableDate_isReportedAndRefused_at200() =
        assertUnreadableDateIsReportedAndRefused(FontScale200)

    private fun assertSaveIsReachableAndReportsTheEdit(fontScale: Float) {
        val saved = mutableListOf<Task>()
        setSheet(fontScale, onSave = { task -> saved += task })

        rule.onNode(hasText(SOMEDAY) and isSelectable()).performScrollTo().performClick()

        rule.onNodeWithText(SAVE).performScrollTo().assertIsEnabled()
        rule.onNodeWithText(SAVE).performClick()

        assertEquals(1, saved.size)
        assertEquals(TaskPlacement.SOMEDAY, saved.single().placement)
        // Everything untouched travels through unchanged.
        assertEquals(TITLE, saved.single().title)
        assertEquals(NOTES, saved.single().notes)
        assertEquals(TestToday, saved.single().scheduledDate)
        assertEquals(DURATION, saved.single().estimatedDurationMinutes)
    }

    @Test
    fun save_isReachableAndReportsTheEdit_at100() =
        assertSaveIsReachableAndReportsTheEdit(FontScale100)

    @Test
    fun save_isReachableAndReportsTheEdit_at200() =
        assertSaveIsReachableAndReportsTheEdit(FontScale200)

    private fun assertCancelDismissesWithoutSaving(fontScale: Float) {
        var dismissed = 0
        val saved = mutableListOf<Task>()
        setSheet(fontScale, onDismiss = { dismissed++ }, onSave = { task -> saved += task })

        rule.onNodeWithText(CANCEL).performScrollTo().performClick()

        assertEquals(1, dismissed)
        assertEquals(emptyList<Task>(), saved)
    }

    @Test
    fun cancel_dismissesWithoutSaving_at100() =
        assertCancelDismissesWithoutSaving(FontScale100)

    @Test
    fun cancel_dismissesWithoutSaving_at200() =
        assertCancelDismissesWithoutSaving(FontScale200)

    private fun editableTask(): Task = testTask(
        id = "1",
        title = TITLE,
        notes = NOTES,
        placement = TaskPlacement.ANYTIME,
        scheduledDate = TestToday,
        dueDate = TestToday.plusDays(3),
        estimatedDurationMinutes = DURATION
    )

    private companion object {
        const val HEADING = "Task details"
        const val TITLE = "Write the report"
        const val NOTES = "Include the appendix"
        const val DURATION = 45

        const val TITLE_LABEL = "Title"
        const val SAVE = "Save"
        const val CANCEL = "Cancel"
        const val INBOX = "Inbox"
        const val ANYTIME = "Anytime"
        const val SOMEDAY = "Someday"
        const val DATE_ERROR = "Not a date Focuslist understands"

        const val NEVER = "Never"
        const val DAILY = "Daily"
        const val WEEKLY = "Weekly"
        const val MONTHLY = "Monthly"
        const val YEARLY = "Yearly"

        val RECURRENCE_OPTIONS = listOf(NEVER, DAILY, WEEKLY, MONTHLY, YEARLY)

        /** How the sheet writes an existing date back into its field. */
        const val SCHEDULED_TEXT = "2 September 2026"

        val FIELD_LABELS = listOf(
            "Title",
            "Notes",
            "Placement",
            "Scheduled",
            "Due",
            "Estimated duration",
            "Repeats"
        )

        val CLEAR_DESCRIPTIONS = listOf(
            "Clear scheduled date",
            "Clear due date",
            "Clear estimated duration"
        )

        const val TIMEOUT_MILLIS = 5_000L
    }
}
