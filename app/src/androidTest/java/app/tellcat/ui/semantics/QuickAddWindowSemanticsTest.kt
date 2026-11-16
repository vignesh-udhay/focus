package app.tellcat.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.waitUntilExactlyOneExists
import app.tellcat.ui.quickadd.QuickAddWindow
import app.tellcat.ui.task.QuickAddCapture
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quick Add over the home screen, from the "New task" shortcut and the
 * widget's add button. D-132.
 *
 * The window's contents only: nothing here writes to the database, so a run
 * on the owner's phone leaves the dev build's tasks as they were. What Add
 * saves is `QuickAddSaveTest`'s. The permission asks are not driven here,
 * because they raise Android's own dialog.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddWindowSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 16)

    private fun window(fontScale: Float, onSave: (QuickAddCapture) -> Unit = {}) {
        rule.setTellcatContent(fontScale) {
            QuickAddWindow(
                today = today,
                isAskingPermission = false,
                onDismiss = {},
                onSave = onSave,
                canScheduleExact = { true },
                onPermissionGranted = {}
            )
        }
        rule.waitUntilExactlyOneExists(hasText(SHEET_NAME), 5_000)
    }

    /** A task with no day goes to Today, and the line says so before Add. */
    private fun assertAnUndatedTaskSaysToday(fontScale: Float) {
        window(fontScale)
        rule.onNode(hasSetTextAction()).performTextInput("Buy milk")

        rule.onNodeWithText("Saved to Today").assertExists()
    }

    @Test
    fun anUndatedTaskSaysToday_at100() = assertAnUndatedTaskSaysToday(FontScale100)

    @Test
    fun anUndatedTaskSaysToday_at200() = assertAnUndatedTaskSaysToday(FontScale200)

    /** It is Quick Add itself: what is typed is handed over parsed, as in the app. */
    @Test
    fun doneHandsOverTheCapture() {
        var saved: QuickAddCapture? = null
        window(FontScale100) { saved = it }

        rule.onNode(hasSetTextAction()).performTextInput("Call Sam tomorrow")
        rule.onNode(hasSetTextAction()).performImeAction()

        rule.waitUntil(5_000) { saved != null }
        assertEquals("Call Sam", saved!!.parsed.title)
        assertEquals(today.plusDays(1), saved!!.parsed.date)
    }

    private companion object {
        const val SHEET_NAME = "New task"
    }
}
