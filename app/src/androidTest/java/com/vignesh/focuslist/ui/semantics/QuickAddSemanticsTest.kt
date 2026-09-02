package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.TitleWithDate
import com.vignesh.focuslist.ui.task.QuickAddSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Quick Add's accessibility contract.
 *
 * Capture is the flow that has to work with no decisions, so the sheet is one
 * labelled field and one action. Three things have to hold for a screen reader
 * or a keyboard user: the field announces what it is, focus lands in it without
 * being hunted for, and Save is refused rather than silently doing nothing when
 * there is no title.
 *
 * A fourth once the field reads days out of the title: when it is about to take
 * words away, it has to say so in text. The colour on those words is not
 * something a screen reader announces or a colour-blind user can rely on, so
 * the supporting line is the part that carries the meaning.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setSheet(
        fontScale: Float,
        onDismiss: () -> Unit = {},
        onSave: (TitleWithDate) -> Unit = {}
    ) {
        rule.setFocuslistContent(fontScale) {
            QuickAddSheet(today = TODAY, onDismiss = onDismiss, onSave = onSave)
        }

        rule.waitUntilExactlyOneExists(hasText(FIELD_LABEL), TIMEOUT_MILLIS)
    }

    private fun assertFieldIsLabelledAndFocused(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(FIELD_LABEL).assertIsDisplayed()
        // The sheet requests focus on open, so capture starts with the keyboard
        // already in the right place.
        rule.onNodeWithText(FIELD_LABEL).assertIsFocused()
    }

    @Test
    fun field_isLabelledAndFocused_at100() = assertFieldIsLabelledAndFocused(FontScale100)

    @Test
    fun field_isLabelledAndFocused_at200() = assertFieldIsLabelledAndFocused(FontScale200)

    private fun assertSaveIsRefusedWithoutATitle(fontScale: Float) {
        setSheet(fontScale)

        // One field and one button: the sheet has no scroll of its own, so
        // Save has to be on screen unaided even at 200%.
        rule.onNodeWithText(SAVE).assertIsDisplayed()
        // Disabled rather than tappable-and-inert: a screen reader announces
        // "dimmed", which explains why nothing happens.
        rule.onNodeWithText(SAVE).assertIsNotEnabled()
    }

    @Test
    fun save_isRefusedWithoutATitle_at100() = assertSaveIsRefusedWithoutATitle(FontScale100)

    @Test
    fun save_isRefusedWithoutATitle_at200() = assertSaveIsRefusedWithoutATitle(FontScale200)

    private fun assertTypingEnablesSave(fontScale: Float) {
        val saved = mutableListOf<TitleWithDate>()
        setSheet(fontScale, onSave = { parsed -> saved += parsed })

        rule.onNodeWithText(FIELD_LABEL).performTextInput(TITLE)

        rule.onNodeWithText(SAVE).assertIsDisplayed()
        rule.onNodeWithText(SAVE).assertIsEnabled()
        rule.onNodeWithText(SAVE).performClick()

        assertEquals(listOf(TitleWithDate(TITLE, null, null)), saved)
    }

    @Test
    fun typing_enablesSave_at100() = assertTypingEnablesSave(FontScale100)

    @Test
    fun typing_enablesSave_at200() = assertTypingEnablesSave(FontScale200)

    private fun assertADayIsNamedInText(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(FIELD_LABEL).performTextInput(DATED_TITLE)

        // The words being taken are coloured, but colour is not announced and
        // not everyone sees it. This line is the accessible half of the signal.
        rule.waitUntilExactlyOneExists(hasText(SCHEDULED_FOR_TOMORROW), TIMEOUT_MILLIS)
        rule.onNodeWithText(SCHEDULED_FOR_TOMORROW).assertIsDisplayed()
    }

    @Test
    fun aDay_isNamedInText_at100() = assertADayIsNamedInText(FontScale100)

    @Test
    fun aDay_isNamedInText_at200() = assertADayIsNamedInText(FontScale200)

    @Test
    fun aDay_isTakenOffTheTitleOnSave() {
        val saved = mutableListOf<TitleWithDate>()
        setSheet(FontScale100, onSave = { parsed -> saved += parsed })

        rule.onNodeWithText(FIELD_LABEL).performTextInput(DATED_TITLE)
        rule.onNodeWithText(SAVE).performClick()

        assertEquals(
            listOf(TitleWithDate(TITLE, TODAY.plusDays(1), TITLE.length + 1)),
            saved
        )
    }

    @Test
    fun aTitleThatIsOnlyADay_saysNothingAboutScheduling() {
        setSheet(FontScale100)

        // Nothing is taken, so there is nothing to announce, and the day stays
        // the title rather than leaving the field empty.
        rule.onNodeWithText(FIELD_LABEL).performTextInput("tomorrow")

        rule.onNodeWithText(SAVE).assertIsEnabled()
        rule.onAllNodesWithText(SCHEDULED_FOR_TOMORROW).assertCountEquals(0)
    }

    private companion object {
        const val FIELD_LABEL = "New task"
        const val SAVE = "Save"
        const val TITLE = "Buy milk"
        const val DATED_TITLE = "Buy milk tomorrow"
        const val SCHEDULED_FOR_TOMORROW = "Scheduled for Tomorrow"
        const val TIMEOUT_MILLIS = 5_000L

        /** Fixed, so "tomorrow" is a known date rather than whatever today is. */
        val TODAY: LocalDate = LocalDate.of(2026, 8, 31)
    }
}
