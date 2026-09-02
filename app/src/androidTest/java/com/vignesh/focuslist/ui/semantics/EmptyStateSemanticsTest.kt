package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared empty state's accessibility contract.
 *
 * An empty list has nothing for a screen reader to read except this, so both
 * lines have to survive to the semantics tree and stay on screen. At 200% the
 * two centred, wrapped strings are the thing most likely to overflow the
 * column they sit in.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStateSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertBothLinesAreReadable(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            TaskListEmptyState(headline = HEADLINE, supporting = SUPPORTING)
        }

        rule.onNodeWithText(HEADLINE).assertIsDisplayed()
        rule.onNodeWithText(SUPPORTING).assertIsDisplayed()
    }

    @Test
    fun bothLines_areReadable_at100() = assertBothLinesAreReadable(FontScale100)

    @Test
    fun bothLines_areReadable_at200() = assertBothLinesAreReadable(FontScale200)

    private fun assertHeadlineIsMarkedAsHeading(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            TaskListEmptyState(headline = HEADLINE, supporting = SUPPORTING)
        }

        // The same convention the app bar titles follow, so an empty screen
        // offers a landmark to jump to rather than two unmarked strings.
        rule.onNode(hasText(HEADLINE) and isHeading()).assertExists()

        // Only the headline. The supporting line is what the heading leads to,
        // not a second landmark competing with it.
        rule.onNode(hasText(SUPPORTING) and isHeading()).assertDoesNotExist()
    }

    @Test
    fun headline_isMarkedAsHeading_at100() = assertHeadlineIsMarkedAsHeading(FontScale100)

    @Test
    fun headline_isMarkedAsHeading_at200() = assertHeadlineIsMarkedAsHeading(FontScale200)

    private companion object {
        const val HEADLINE = "Nothing scheduled for today"
        const val SUPPORTING = "Add a task when you are ready."
    }
}
