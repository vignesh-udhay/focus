package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.CapturedTask
import com.vignesh.focuslist.ui.task.QuickAddSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

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
        onSave: (CapturedTask) -> Unit = {}
    ) {
        rule.setFocuslistContent(fontScale) {
            QuickAddSheet(
                today = TODAY,
                // Today dates an undated capture. `QuickAddCaptureTest` covers
                // the Inbox side, where it does not.
                fallbackDate = TODAY,
                onDismiss = onDismiss,
                onSave = onSave
            )
        }

        rule.waitUntilExactlyOneExists(hasText(SHEET_NAME), TIMEOUT_MILLIS)
    }

    /**
     * The field is found by its edit action rather than by a label, because
     * since D-052 it has none: SHEET_NAME is a heading above it. That heading
     * is what names the field, so this asserts both halves.
     */
    private fun field() = rule.onNode(hasSetTextAction())

    private fun assertFieldIsNamedAndFocused(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(SHEET_NAME).assertIsDisplayed()
        // The sheet requests focus on open, so capture starts with the keyboard
        // already in the right place.
        field().assertIsFocused()
    }

    @Test
    fun field_isNamedAndFocused_at100() = assertFieldIsNamedAndFocused(FontScale100)

    @Test
    fun field_isNamedAndFocused_at200() = assertFieldIsNamedAndFocused(FontScale200)

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
        val saved = mutableListOf<CapturedTask>()
        setSheet(fontScale, onSave = { parsed -> saved += parsed })

        field().performTextInput(TITLE)

        rule.onNodeWithText(SAVE).assertIsDisplayed()
        rule.onNodeWithText(SAVE).assertIsEnabled()
        rule.onNodeWithText(SAVE).performClick()

        val captured = saved.single()
        assertEquals(TITLE, captured.title)
        assertNull(captured.date)
        assertNull(captured.time)
    }

    @Test
    fun typing_enablesSave_at100() = assertTypingEnablesSave(FontScale100)

    @Test
    fun typing_enablesSave_at200() = assertTypingEnablesSave(FontScale200)

    private fun assertADayIsNamedInText(fontScale: Float) {
        setSheet(fontScale)

        field().performTextInput(DATED_TITLE)

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
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, onSave = { parsed -> saved += parsed })

        field().performTextInput(DATED_TITLE)
        rule.onNodeWithText(SAVE).performClick()

        assertEquals(1, saved.size)
        assertEquals(TITLE, saved.single().title)
        assertEquals(TODAY.plusDays(1), saved.single().date)
        // No time was typed, so no reminder is promised.
        assertNull(saved.single().time)
    }

    // --- D-011: the time, and the chip that can take it back -----------------

    /**
     * A trailing time sets a reminder, and the chip is where it is named. The
     * chip is the reminder's only presence in text, so this is what a screen
     * reader has to be able to read the promise from.
     */
    private fun assertATimeIsNamedByAChip(fontScale: Float) {
        setSheet(fontScale)

        field().performTextInput(TIMED_TITLE)

        rule.waitUntilExactlyOneExists(hasContentDescription(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(REMINDER_DISMISS).assertIsDisplayed()
        // The day still has its own line. The two facts are separate and the
        // line never becomes the reminder's only description.
        rule.onNodeWithText(SCHEDULED_FOR_TOMORROW).assertIsDisplayed()
    }

    @Test
    fun aTime_isNamedByAChip_at100() = assertATimeIsNamedByAChip(FontScale100)

    @Test
    fun aTime_isNamedByAChip_at200() = assertATimeIsNamedByAChip(FontScale200)

    @Test
    fun aTime_becomesAReminderOnSave() {
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, onSave = { parsed -> saved += parsed })

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(hasContentDescription(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()

        val captured = saved.single()
        assertEquals(TITLE, captured.title)
        assertEquals(TODAY.plusDays(1), captured.date)
        assertEquals(LocalTime.of(15, 0), captured.time)
        assertEquals(TODAY.plusDays(1).atTime(15, 0), captured.reminderAt(TODAY, TODAY.atTime(9, 0)))
    }

    /**
     * **Dismissing has to drop the reminder and keep the day**, which is the
     * asymmetry D-011 argues for: a wrong day is quiet and cheap and is
     * corrected by typing, and a wrong reminder is a broken promise in either
     * direction, so only the reminder gets a control.
     */
    @Test
    fun dismissingTheChip_dropsTheReminderAndKeepsTheDay() {
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, onSave = { parsed -> saved += parsed })

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(hasContentDescription(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(REMINDER_DISMISS).performClick()

        // The chip goes with the reminder it named.
        rule.waitUntilExactlyOneExists(hasText(SCHEDULED_FOR_TOMORROW), TIMEOUT_MILLIS)
        rule.onAllNodesWithContentDescription(REMINDER_DISMISS).assertCountEquals(0)

        rule.onNodeWithText(SAVE).performClick()

        val captured = saved.single()
        assertNull(captured.time)
        assertNull(captured.reminderAt(TODAY, TODAY.atTime(9, 0)))
        assertEquals(TODAY.plusDays(1), captured.date)
    }

    /**
     * A dismissal cannot outlive the text it was about. Typing again is a new
     * parse and a new promise, and suppressing it would withhold a reminder the
     * user never declined.
     */
    @Test
    fun typingAgainAfterDismissing_bringsTheReminderBack() {
        setSheet(FontScale100)

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(hasContentDescription(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(REMINDER_DISMISS).performClick()
        rule.waitUntilExactlyOneExists(hasText(SCHEDULED_FOR_TOMORROW), TIMEOUT_MILLIS)

        // Retyping the same trailing time, which is a fresh promise.
        field().performTextInput(" at 4pm")

        rule.waitUntilExactlyOneExists(hasContentDescription(REMINDER_DISMISS), TIMEOUT_MILLIS)
    }

    /** Nothing understood still says where the task will land. */
    @Test
    fun aPlainTitle_saysWhereItWillBeSaved() {
        setSheet(FontScale100)

        field().performTextInput(TITLE)

        rule.waitUntilExactlyOneExists(hasText(SAVED_TO_TODAY), TIMEOUT_MILLIS)
        rule.onAllNodesWithContentDescription(REMINDER_DISMISS).assertCountEquals(0)
    }

    @Test
    fun aTitleThatIsOnlyADay_saysNothingAboutScheduling() {
        setSheet(FontScale100)

        // Nothing is taken, so there is nothing to announce, and the day stays
        // the title rather than leaving the field empty.
        field().performTextInput("tomorrow")

        rule.onNodeWithText(SAVE).assertIsEnabled()
        rule.onAllNodesWithText(SCHEDULED_FOR_TOMORROW).assertCountEquals(0)
    }

    /** And the same for a title that is nothing but a day and a time. */
    @Test
    fun aTitleThatIsOnlyADayAndATime_promisesNothing() {
        setSheet(FontScale100)

        field().performTextInput("tomorrow at 3pm")

        rule.onNodeWithText(SAVE).assertIsEnabled()
        rule.onAllNodesWithContentDescription(REMINDER_DISMISS).assertCountEquals(0)
        rule.onAllNodesWithText(SCHEDULED_FOR_TOMORROW).assertCountEquals(0)
    }

    private companion object {
        const val SHEET_NAME = "New task"
        const val SAVE = "Add task"
        const val TITLE = "Buy milk"
        const val DATED_TITLE = "Buy milk tomorrow"
        const val TIMED_TITLE = "Buy milk tomorrow at 3pm"
        const val SCHEDULED_FOR_TOMORROW = "Scheduled for Tomorrow"
        const val SAVED_TO_TODAY = "Saved to Today"

        /**
         * The chip is found by its dismiss action rather than by its label.
         * The label names the time through the reader's own locale, so
         * asserting it would fail on a device set to a 24-hour clock and would
         * be testing `DateTimeFormatter` rather than this screen. What the chip
         * promises is asserted through `onSave` instead, where it is a value.
         */
        const val REMINDER_DISMISS = "Remove reminder"
        const val TIMEOUT_MILLIS = 5_000L

        /** Fixed, so "tomorrow" is a known date rather than whatever today is. */
        val TODAY: LocalDate = LocalDate.of(2026, 8, 31)
    }
}
