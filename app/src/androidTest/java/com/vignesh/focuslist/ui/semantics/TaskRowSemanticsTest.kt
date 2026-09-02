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
 * The row carries three separate actions and a screen reader has to be able to
 * tell them apart: tapping opens details, long-pressing opens the actions menu,
 * and the checkbox completes the task. Each is announced by its own label, and
 * the checkbox's label names the task so a list of ten rows does not read as
 * ten identical "checkbox, not ticked".
 *
 * The row is exercised through [TaskListRow] rather than the bare `TaskRow`,
 * because the labels and the menu are what the screens actually compose.
 */
@RunWith(AndroidJUnit4::class)
class TaskRowSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setRow(
        fontScale: Float,
        task: Task,
        onToggleComplete: () -> Unit = {},
        onOpen: () -> Unit = {},
        onDelete: () -> Unit = {},
        onFocus: (() -> Unit)? = null
    ) {
        rule.setFocuslistContent(fontScale) {
            TaskListRow(
                task = task,
                today = TestToday,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = ListItemDefaults.segmentedColors(),
                onToggleComplete = onToggleComplete,
                onOpen = onOpen,
                onDelete = onDelete,
                onFocus = onFocus
            )
        }
    }

    private fun assertRowAnnouncesItsActions(fontScale: Float) {
        setRow(fontScale, outstanding())

        rule.onNodeWithText(TITLE).assertIsDisplayed()

        // "Double tap to open task details", not "double tap to activate".
        rule.onNode(hasClickLabel("Open task details")).assertExists()
        rule.onNode(hasLongClickLabel("Show task actions")).assertExists()
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
        rule.onNode(hasText("45 min", substring = true)).assertExists()
    }

    @Test
    fun metadata_isReadable_at100() = assertMetadataIsReadable(FontScale100)

    @Test
    fun metadata_isReadable_at200() = assertMetadataIsReadable(FontScale200)

    private fun assertLongPressOffersFocusAndDelete(fontScale: Float) {
        var deletes = 0
        var focuses = 0
        setRow(
            fontScale,
            outstanding(),
            onDelete = { deletes++ },
            onFocus = { focuses++ }
        )

        rule.onNode(hasLongClickLabel("Show task actions")).performAccessibilityLongClick()

        rule.onNodeWithText("Focus").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()

        rule.onNodeWithText("Delete").performClick()

        assertEquals(1, deletes)
        assertEquals(0, focuses)
    }

    private fun assertActionsButtonOffersTheSameMenu(fontScale: Float) {
        var deletes = 0
        setRow(fontScale, outstanding(), onDelete = { deletes++ }, onFocus = {})

        // The visible route to the same menu. Long press is kept and still
        // works, but it is a gesture with nothing on screen to suggest it, and
        // Delete and Focus live nowhere else.
        rule.onNodeWithContentDescription("Show task actions").performClick()

        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()

        assertEquals(1, deletes)
    }

    @Test
    fun actionsButton_offersTheSameMenu_at100() =
        assertActionsButtonOffersTheSameMenu(FontScale100)

    @Test
    fun actionsButton_offersTheSameMenu_at200() =
        assertActionsButtonOffersTheSameMenu(FontScale200)

    @Test
    fun longPress_offersFocusAndDelete_at100() =
        assertLongPressOffersFocusAndDelete(FontScale100)

    @Test
    fun longPress_offersFocusAndDelete_at200() =
        assertLongPressOffersFocusAndDelete(FontScale200)

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
