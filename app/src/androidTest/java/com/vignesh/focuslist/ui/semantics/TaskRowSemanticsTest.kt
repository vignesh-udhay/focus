package com.vignesh.focuslist.ui.semantics

import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.ui.component.TaskListRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The task row's accessibility contract.
 *
 * The row carries two actions and a screen reader has to be able to tell them
 * apart: tapping opens details, and the checkbox completes the task. Each is
 * announced by its own label, and the checkbox's label names the task so a list
 * of ten rows does not read as ten identical "checkbox, not ticked".
 *
 * **The actions menu is gone**, so the cases that covered it went with it.
 * `docs/decisions.md` D-023 removed the trailing button and the long press; what
 * they reached lives on Task Details now, which `TaskDetailsSemanticsTest`
 * covers.
 *
 * The row is exercised through [TaskListRow] rather than the bare `TaskRow`,
 * because the labels are what the screens actually compose.
 */
@RunWith(AndroidJUnit4::class)
class TaskRowSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setRow(
        fontScale: Float,
        task: Task,
        onToggleComplete: () -> Unit = {},
        onOpen: () -> Unit = {}
    ) {
        rule.setFocuslistContent(fontScale) {
            TaskListRow(
                task = task,
                today = TestToday,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = ListItemDefaults.segmentedColors(),
                onToggleComplete = onToggleComplete,
                onOpen = onOpen
            )
        }
    }

    private fun assertRowAnnouncesItsActions(fontScale: Float) {
        setRow(fontScale, outstanding())

        rule.onNodeWithText(TITLE).assertIsDisplayed()

        // "Double tap to open task details", not "double tap to activate".
        rule.onNode(hasClickLabel("Open task details")).assertExists()
    }

    @Test
    fun row_announcesItsActions_at100() = assertRowAnnouncesItsActions(FontScale100)

    @Test
    fun row_announcesItsActions_at200() = assertRowAnnouncesItsActions(FontScale200)

    private fun assertCheckboxNamesItsTask(fontScale: Float) {
        setRow(fontScale, outstanding())

        // The task's own title, so rows never sound alike.
        rule.onNodeWithContentDescription(MARK_COMPLETE).assertIsDisplayed()
        rule.onNodeWithContentDescription(MARK_COMPLETE).assertIsOff()
    }

    @Test
    fun checkbox_namesItsTask_at100() = assertCheckboxNamesItsTask(FontScale100)

    @Test
    fun checkbox_namesItsTask_at200() = assertCheckboxNamesItsTask(FontScale200)

    private fun assertCompletedCheckboxInvertsItsLabel(fontScale: Float) {
        setRow(fontScale, completed())

        rule.onNodeWithContentDescription(MARK_INCOMPLETE).assertIsOn()
    }

    @Test
    fun completedCheckbox_invertsItsLabel_at100() =
        assertCompletedCheckboxInvertsItsLabel(FontScale100)

    @Test
    fun completedCheckbox_invertsItsLabel_at200() =
        assertCompletedCheckboxInvertsItsLabel(FontScale200)

    private fun assertCheckboxCompletesTheTask(fontScale: Float) {
        var toggles = 0
        var opens = 0
        setRow(
            fontScale,
            outstanding(),
            onToggleComplete = { toggles++ },
            onOpen = { opens++ }
        )

        rule.onNodeWithContentDescription(MARK_COMPLETE).performClick()

        assertEquals(1, toggles)
        // Completion is the checkbox's own interaction; it must not also open
        // the task, which is what the row's tap does.
        assertEquals(0, opens)
    }

    @Test
    fun checkbox_completesTheTask_at100() = assertCheckboxCompletesTheTask(FontScale100)

    @Test
    fun checkbox_completesTheTask_at200() = assertCheckboxCompletesTheTask(FontScale200)

    private fun assertTappingTheRowOpensDetails(fontScale: Float) {
        var opens = 0
        var toggles = 0
        setRow(fontScale, outstanding(), onToggleComplete = { toggles++ }, onOpen = { opens++ })

        rule.onNode(hasClickLabel("Open task details")).performClick()

        assertEquals(1, opens)
        assertEquals(0, toggles)
    }

    @Test
    fun tappingTheRow_opensDetails_at100() = assertTappingTheRowOpensDetails(FontScale100)

    @Test
    fun tappingTheRow_opensDetails_at200() = assertTappingTheRowOpensDetails(FontScale200)

    private fun assertMetadataIsReadable(fontScale: Float) {
        setRow(fontScale, outstanding().copy(estimatedDurationMinutes = 45))

        // A date reads as "Today" rather than a formatted day, and the two
        // segments have to survive to the tree as one readable line.
        rule.onNode(hasText("Today", substring = true)).assertExists()

        // **The estimate is drawn compact and spoken in full.** This asserted
        // "45 min" against both, and had been failing since `TaskListRow`
        // stopped formatting its own `%1$d min` and went through `durationLabel`
        // for the row to read 45m like the Duration sheet. The spoken form is
        // the half this file is actually about, and it was never checked: "45m"
        // read aloud is not a duration.
        rule.onNode(hasText("45m", substring = true)).assertExists()
        rule.onNodeWithContentDescription("45 minutes", substring = true).assertExists()
    }

    @Test
    fun metadata_isReadable_at100() = assertMetadataIsReadable(FontScale100)

    @Test
    fun metadata_isReadable_at200() = assertMetadataIsReadable(FontScale200)

    /**
     * `docs/decisions.md` D-059. A due date could be set on Task Details and no
     * list ever showed it again, so a user could record a deadline and never see
     * it a second time.
     *
     * The word is the point. A scheduled date and a due date both render "Today"
     * through the same helper, and on one line the two are indistinguishable
     * without it.
     */
    private fun assertDueDateReadsAsADeadline(fontScale: Float) {
        setRow(fontScale, outstanding().copy(dueDate = TestToday))

        rule.onNode(hasText("Due today", substring = true)).assertExists()
    }

    @Test
    fun dueDate_readsAsADeadline_at100() = assertDueDateReadsAsADeadline(FontScale100)

    @Test
    fun dueDate_readsAsADeadline_at200() = assertDueDateReadsAsADeadline(FontScale200)

    /**
     * A deadline that has gone by says so rather than naming the day. A date
     * three weeks past tells the user nothing they need; that it has passed is
     * the whole content, and it is also the one value on this line that is not a
     * date, which is what stops it reading as a scheduled day.
     */
    private fun assertPastDueDateReadsAsOverdue(fontScale: Float) {
        setRow(fontScale, outstanding().copy(dueDate = TestToday.minusDays(3)))

        rule.onNode(hasText("Overdue", substring = true)).assertExists()
    }

    @Test
    fun pastDueDate_readsAsOverdue_at100() = assertPastDueDateReadsAsOverdue(FontScale100)

    @Test
    fun pastDueDate_readsAsOverdue_at200() = assertPastDueDateReadsAsOverdue(FontScale200)

    /**
     * Overdue is a live state, so it ends when the task does. The Logbook is a
     * record of what was finished, and calling a deadline late after the fact is
     * the app grading work that is already done.
     */
    @Test
    fun completedTask_withAPastDueDate_readsTheDayRatherThanOverdue() {
        setRow(FontScale100, completed().copy(dueDate = TestToday.minusDays(3)))

        rule.onNode(hasText("Overdue", substring = true)).assertDoesNotExist()
        rule.onNode(hasText("Due ", substring = true)).assertExists()
    }

    /**
     * The two past-day conditions are kept apart, and D-059 says why: a task
     * that is merely late still has a reminder that has not fired. Folding them
     * into one flag would have hidden a live reminder from an overdue task.
     */
    @Test
    fun aPastDueDate_doesNotHideAReminderThatHasNotFired() {
        setRow(
            FontScale100,
            outstanding().copy(
                dueDate = TestToday.minusDays(3),
                reminderAt = TestToday.plusDays(1).atTime(18, 0)
            )
        )

        rule.onNode(hasText("Overdue", substring = true)).assertExists()
        // The minutes rather than the hour, because the row formats a time in
        // the reader’s own locale and clock: the same instant is "6:00 PM" on a
        // 12-hour device and "18:00" on a 24-hour one, and only one of those
        // contains the hour this test could name.
        rule.onNode(hasText(":00", substring = true)).assertExists()
    }

    private fun outstanding(): Task = testTask(
        id = "1",
        title = TITLE,
        scheduledDate = TestToday
    )

    private fun completed(): Task = outstanding().copy(
        completedAt = Instant.parse("2026-09-02T10:00:00Z")
    )

    private companion object {
        const val TITLE = "Write the report"
        const val MARK_COMPLETE = "Mark \"$TITLE\" complete"
        const val MARK_INCOMPLETE = "Mark \"$TITLE\" not complete"
    }
}
