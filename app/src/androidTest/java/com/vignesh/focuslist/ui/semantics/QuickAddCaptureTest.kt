package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.CapturedTask
import com.vignesh.focuslist.ui.task.QuickAddSheet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Quick Add tells the user, and what commits it.
 *
 * The supporting line is the sheet's only statement about where a capture is
 * going, and it was making that statement from `parsed.date` alone. Both hosts
 * leave that null when no day is typed and then do different things with it:
 * Today dates the task, Inbox does not. So the line said "Saved to Today" on a
 * capture that stayed in Inbox. D-053.
 *
 * The Done key is the other half. The field declared `ImeAction.Done` and
 * handled none of it, so the key that looks like it commits only closed the
 * keyboard.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddCaptureTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 10)

    private val SHEET_NAME = "New task"

    private fun sheet(fallbackDate: LocalDate?, onSave: (CapturedTask) -> Unit = {}) {
        rule.setFocuslistContent(fontScale = 1f) {
            QuickAddSheet(
                today = today,
                fallbackDate = fallbackDate,
                onDismiss = {},
                onSave = onSave
            )
        }

        // The sheet animates in and auto-focuses. Waiting on its heading with a
        // timeout is how the rest of this package waits for it; a bare
        // `waitForIdle` here is what hung the run.
        rule.waitUntilExactlyOneExists(hasText(SHEET_NAME), 5_000)
    }

    private fun type(text: String) = rule.onNode(hasSetTextAction()).performTextInput(text)

    /** An undated capture from Inbox stays in Inbox, and the line now says so. */
    @Test
    fun anUndatedCaptureFromInboxNamesInbox() {
        sheet(fallbackDate = null)
        type("Call the plumber")

        rule.onNodeWithText("Saved to Inbox").assertExists()
        rule.onNodeWithText("Saved to Today").assertDoesNotExist()
    }

    /**
     * A typed day names the day rather than a list, even though a future day
     * means the task appears in Upcoming rather than Today.
     */
    @Test
    fun aTypedDayNamesTheDayAndNotAList() {
        sheet(fallbackDate = today)
        type("Call the plumber tomorrow")

        rule.onNodeWithText("Saved to Today").assertDoesNotExist()
        rule.onNodeWithText("Saved to Inbox").assertDoesNotExist()
    }

    /** Nothing is claimed about a task that has not been typed. */
    @Test
    fun anEmptyFieldSaysNothingAboutWhereAnythingGoes() {
        sheet(fallbackDate = null)

        rule.onNodeWithText("Saved to Inbox").assertDoesNotExist()
        rule.onNodeWithText("Saved to Today").assertDoesNotExist()
    }

    /** The keyboard's Done key commits, rather than only closing the keyboard. */
    @Test
    fun theDoneKeySaves() {
        var saved: CapturedTask? = null
        sheet(fallbackDate = today, onSave = { saved = it })

        type("Call the plumber")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()

        assertEquals("Call the plumber", saved?.title)
    }

    /** And it refuses the same captures the Add button refuses. */
    @Test
    fun theDoneKeyDoesNotSaveAnEmptyTitle() {
        var saves = 0
        sheet(fallbackDate = today, onSave = { saves++ })

        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()

        assertEquals(0, saves)
    }
}
