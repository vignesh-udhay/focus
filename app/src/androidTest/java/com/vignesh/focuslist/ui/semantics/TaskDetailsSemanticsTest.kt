package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
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
 * The Task Details accessibility contract, including its recurrence choices.
 *
 * This is the densest surface in the app. The contracts that matter are that
 * every field says what it is, that identical "Clear" buttons are told apart
 * by description rather than by position, that recurrence publishes which
 * option is current, and that invalid input is refused visibly instead of
 * being dropped on save.
 *
 * The recurrence group is run at 200%, where its labels cannot fit across a
 * phone. A group that wraps keeps every option on screen, and
 * `performScrollTo` reaching it is what proves it.
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

    /**
     * Opens the Schedule page.
     *
     * The day, the due date, the duration and the recurrence moved off the
     * details page and behind the summary row, so a test about any of them has
     * to walk there first. The row is found by its trailing chevron's absence
     * of a label and its own text varying with the fixture, so it is reached by
     * the heading it opens.
     */
    private fun openSchedule() {
        // Matched on the day rather than the whole summary: the row also
        // carries the duration and the recurrence, so its exact text changes
        // with whatever the fixture sets.
        rule.onNode(hasText(SCHEDULE_SUMMARY_DAY, substring = true) and hasClickAction())
            .performScrollTo()
            .performClick()
        rule.waitUntilExactlyOneExists(hasText(SCHEDULE_HEADING), TIMEOUT_MILLIS)
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

        DETAILS_FIELD_LABELS.forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }

        openSchedule()

        SCHEDULE_FIELD_LABELS.forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun everyField_isLabelled_at100() = assertEveryFieldIsLabelled(FontScale100)

    @Test
    fun everyField_isLabelled_at200() = assertEveryFieldIsLabelled(FontScale200)

    private fun assertClearButtonsAreDistinguishable(fontScale: Float) {
        setSheet(fontScale)
        openSchedule()

        // Two buttons both reading "Clear". Only the description tells a
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

    private fun assertRecurrencePublishesCurrentOption(fontScale: Float) {
        setSheet(fontScale)
        openSchedule()

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
        openSchedule()

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

    private fun assertRecurrenceSelectionMovesAndSaves(fontScale: Float) {
        val saved = mutableListOf<Task>()
        setSheet(fontScale, onSave = { task -> saved += task })
        openSchedule()

        rule.onNode(hasText(WEEKLY) and isSelectable()).performScrollTo().performClick()
        rule.onNode(hasText(WEEKLY) and isSelectable()).assertIsSelected()
        rule.onNode(hasText(NEVER) and isSelectable()).assertIsNotSelected()

        // The draft survives the walk back. Nothing is written until Save.
        rule.onNodeWithContentDescription(BACK).performScrollTo().performClick()
        rule.waitUntilExactlyOneExists(hasText(HEADING), TIMEOUT_MILLIS)
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
        openSchedule()

        rule.onNode(hasText(MONTHLY) and isSelectable()).performScrollTo().assertIsSelected()
        rule.onNode(hasText(NEVER) and isSelectable()).performScrollTo().performClick()

        rule.onNodeWithContentDescription(BACK).performScrollTo().performClick()
        rule.waitUntilExactlyOneExists(hasText(HEADING), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performScrollTo().performClick()

        // Never is a real answer, not the absence of one.
        assertEquals(null, saved.single().recurrence)
    }

    @Test
    fun recurrence_canBeTakenAway_at100() = assertRecurrenceCanBeTakenAway(FontScale100)

    @Test
    fun recurrence_canBeTakenAway_at200() = assertRecurrenceCanBeTakenAway(FontScale200)

    private fun assertBlankTitleRefusesSave(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(TITLE_LABEL).performScrollTo().performTextClearance()

        rule.onNodeWithText(SAVE).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun blankTitle_refusesSave_at100() = assertBlankTitleRefusesSave(FontScale100)

    @Test
    fun blankTitle_refusesSave_at200() = assertBlankTitleRefusesSave(FontScale200)

    private fun assertDueDateIsRevealedOnDemand(fontScale: Float) {
        // No deadline, which is the ordinary case and the one the field is
        // hidden for.
        setSheet(fontScale, task = editableTask().copy(dueDate = null))
        openSchedule()

        rule.onNodeWithText(DUE_FIELD_LABEL).assertDoesNotExist()

        rule.onNodeWithText(ADD_DUE).performScrollTo().performClick()

        // Revealed, and the offer to reveal it is gone: one control, two states,
        // never both at once.
        rule.onNodeWithText(DUE_FIELD_LABEL).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(ADD_DUE).assertDoesNotExist()
    }

    @Test
    fun dueDate_isRevealedOnDemand_at100() = assertDueDateIsRevealedOnDemand(FontScale100)

    @Test
    fun dueDate_isRevealedOnDemand_at200() = assertDueDateIsRevealedOnDemand(FontScale200)

    private fun assertDueDateStartsOpenWhenSet(fontScale: Float) {
        // The fixture has one, so it is shown rather than hidden behind an
        // offer to add what is already there.
        setSheet(fontScale)
        openSchedule()

        rule.onNodeWithText(DUE_FIELD_LABEL).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(ADD_DUE).assertDoesNotExist()
    }

    @Test
    fun dueDate_startsOpenWhenSet_at100() = assertDueDateStartsOpenWhenSet(FontScale100)

    @Test
    fun dueDate_startsOpenWhenSet_at200() = assertDueDateStartsOpenWhenSet(FontScale200)

    private fun assertUnreadableDateIsReportedAndRefused(fontScale: Float) {
        setSheet(fontScale)
        openSchedule()

        rule.onNodeWithText(DUE_TEXT).performScrollTo()
            .performTextReplacement("the day after the thing")

        // Said out loud on the page that owns the field.
        rule.onNodeWithText(DATE_ERROR).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(DONE).performScrollTo().assertIsNotEnabled()

        // And still refused on the page that saves. A field the user cannot
        // currently see must not be able to let a bad value through.
        rule.onNodeWithContentDescription(BACK).performScrollTo().performClick()
        rule.waitUntilExactlyOneExists(hasText(HEADING), TIMEOUT_MILLIS)
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

        rule.onNodeWithText(SAVE).performScrollTo().assertIsEnabled()
        rule.onNodeWithText(SAVE).performClick()

        assertEquals(1, saved.size)
        assertEquals(TaskPlacement.ANYTIME, saved.single().placement)
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
        const val DATE_ERROR = "Not a date Focuslist understands"

        const val NEVER = "Never"
        const val DAILY = "Daily"
        const val WEEKLY = "Weekly"
        const val MONTHLY = "Monthly"
        const val YEARLY = "Yearly"

        val RECURRENCE_OPTIONS = listOf(NEVER, DAILY, WEEKLY, MONTHLY, YEARLY)

        const val SCHEDULE_HEADING = "Schedule"
        const val ADD_DUE = "Add due date"
        const val DUE_FIELD_LABEL = "Due"
        const val DONE = "Done"
        const val BACK = "Back to task details"
        /** How the sheet writes the fixture's existing due date into its field. */
        const val DUE_TEXT = "5 September 2026"

        /**
         * The part of the summary row that does not vary with the fixture. The
         * row states what is set rather than naming the page it opens, so its
         * full text carries the duration and the recurrence too.
         */
        const val SCHEDULE_SUMMARY_DAY = "Today"

        val DETAILS_FIELD_LABELS = listOf(
            "Title",
            "Notes"
        )

        val SCHEDULE_FIELD_LABELS = listOf(
            // Due is here because the fixture carries a deadline, so the field
            // is open. A task without one shows an offer to add it instead;
            // dueDate_isRevealedOnDemand covers that.
            "Due",
            "Estimated duration",
            "Repeats"
        )

        val CLEAR_DESCRIPTIONS = listOf(
            "Clear due date",
            "Clear estimated duration"
        )

        const val TIMEOUT_MILLIS = 5_000L
    }
}
