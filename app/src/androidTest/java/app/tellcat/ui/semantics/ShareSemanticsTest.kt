package app.tellcat.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.SharedTask
import app.tellcat.ui.share.AddSharedTaskSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The "Add to Tellcat" sheet another app's share opens. D-097. */
class ShareSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val shared = SharedTask(
        title = "Weekend shop",
        notes = "From the market on Elm Street.",
        checklist = listOf(ChecklistItem("Milk"), ChecklistItem("Eggs", done = true))
    )

    private fun setSheet(fontScale: Float, onAdd: (SharedTask) -> Unit = {}) {
        rule.setTellcatContent(fontScale) {
            AddSharedTaskSheet(shared = shared, onDismiss = {}, onAdd = onAdd)
        }
    }

    private fun assertItSaysWhatArrives(fontScale: Float) {
        setSheet(fontScale)

        rule.onNode(hasText(HEADING) and isHeading()).assertIsDisplayed()
        rule.onNode(hasText(shared.title) and hasSetTextAction()).assertIsDisplayed()
        rule.onNodeWithText(SAVED_TO_INBOX).assertIsDisplayed()
        rule.onNodeWithText(shared.notes!!).assertIsDisplayed()
        rule.onNodeWithText(TWO_ITEMS).assertIsDisplayed()
        rule.onNodeWithText(ADD).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun itSaysWhatArrives_at100() = assertItSaysWhatArrives(FontScale100)

    @Test
    fun itSaysWhatArrives_at200() = assertItSaysWhatArrives(FontScale200)

    /** The title can be corrected; the notes and the list come along as they are. */
    @Test
    fun addHandsOverTheCorrectedTitle() {
        var added: SharedTask? = null
        setSheet(FontScale100) { added = it }

        rule.onNode(hasText(shared.title) and hasSetTextAction()).performTextReplacement("  Saturday shop ")
        rule.onNodeWithText(ADD).performClick()

        assertEquals(shared.copy(title = "Saturday shop"), added)
    }

    /** A task cannot do without a title, here as in Quick Add. */
    @Test
    fun aBlankTitleCannotBeAdded() {
        setSheet(FontScale100)

        rule.onNode(hasText(shared.title) and hasSetTextAction()).performTextReplacement("   ")

        rule.onNodeWithText(ADD).assertIsNotEnabled()
    }

    private companion object {
        const val HEADING = "Add to Tellcat"
        const val SAVED_TO_INBOX = "Saved to Inbox"
        const val TWO_ITEMS = "With 2 checklist items"
        const val ADD = "Add task"
    }
}
