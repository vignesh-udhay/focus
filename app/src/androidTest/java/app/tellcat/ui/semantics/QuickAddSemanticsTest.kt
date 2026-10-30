package app.tellcat.ui.semantics

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.core.domain.CapturedTask
import app.tellcat.ui.task.QuickAddSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
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
        onSave: (CapturedTask) -> Unit = {},
        today: LocalDate = TODAY,
        clock: () -> LocalDateTime = LocalDateTime::now
    ) {
        rule.setTellcatContent(fontScale) {
            QuickAddSheet(
                today = today,
                clock = clock,
                // Today dates an undated capture. `QuickAddCaptureTest` covers
                // the Inbox side, where it does not.
                fallbackDate = today,
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
        // The real day, not the fixed one. Since D-075 the line names the day
        // the reminder rings, which the sheet resolves against the clock, and a
        // fixed day weeks behind it puts that on a date this test cannot name.
        setSheet(fontScale, today = LocalDate.now())

        field().performTextInput(TIMED_TITLE)

        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onNode(hasClickLabel(REMINDER_DISMISS)).assertIsDisplayed()
        // The day still has its own line. The two facts are separate and the
        // line never becomes the reminder's only description.
        rule.onNodeWithText(SCHEDULED_FOR_TOMORROW).assertIsDisplayed()
    }

    @Test
    fun aTime_isNamedByAChip_at100() = assertATimeIsNamedByAChip(FontScale100)

    @Test
    fun aTime_isNamedByAChip_at200() = assertATimeIsNamedByAChip(FontScale200)

    /**
     * Read as a button that removes the reminder, never as a checked control.
     * Drawn selected, the input chip announced "checked", and a double-tap
     * silently took the reminder away.
     */
    /**
     * The chip keeps telling the truth while the sheet is open. Typed in the
     * last seconds of 2:38, "at 2:38pm" is today's; once 2:39 starts the save
     * would put it on tomorrow, so the chip has to say Tomorrow before anyone
     * presses Save. It used to keep saying Today until the next keystroke.
     */
    @Test
    fun theReminderChipTurnsToTomorrowWhenItsMinuteEnds() {
        val today = LocalDate.now()
        var now = today.atTime(14, 38, 50)
        setSheet(FontScale100, today = today, clock = { now })

        field().performTextInput("Call mom at 2:38pm")
        rule.onNodeWithText("Reminder Today at", substring = true).assertExists()

        now = today.atTime(14, 39, 1)
        rule.mainClock.advanceTimeBy(11_000)

        rule.onNodeWithText("Reminder Tomorrow at", substring = true).assertExists()
    }

    @Test
    fun theReminderChip_isAButtonNotAToggle() {
        setSheet(FontScale100, today = LocalDate.now())
        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)

        rule.onNode(hasClickLabel(REMINDER_DISMISS))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
    }

    @Test
    fun aTime_becomesAReminderOnSave() {
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, onSave = { parsed -> saved += parsed })

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)
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
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onNode(hasClickLabel(REMINDER_DISMISS)).performClick()

        // The chip goes with the reminder it named.
        rule.waitUntilExactlyOneExists(hasText(SCHEDULED_FOR_TOMORROW), TIMEOUT_MILLIS)
        rule.onAllNodes(hasClickLabel(REMINDER_DISMISS)).assertCountEquals(0)

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
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onNode(hasClickLabel(REMINDER_DISMISS)).performClick()
        rule.waitUntilExactlyOneExists(hasText(SCHEDULED_FOR_TOMORROW), TIMEOUT_MILLIS)

        // Retyping the same trailing time, which is a fresh promise.
        field().performTextInput(" at 4pm")

        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)
    }

    // --- D-112: the chip that writes into the field ----------------------------

    /** The field's text, as typed or as the chip wrote it. */
    private fun fieldText(): String =
        field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** The chip, whichever state it is in, found by what a double-tap does. */
    private fun addChip() = rule.onNode(hasClickLabel(REMINDER_ADD))
    private fun changeChip() = rule.onNode(hasClickLabel(REMINDER_CHANGE))

    /**
     * Always on the sheet, D-112, so Add no longer moves when a time is read.
     * Live once there is a title, the same test Add uses.
     */
    private fun assertTheChipIsAlwaysThere(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(REMINDER_ADD).assertIsDisplayed().assertIsNotEnabled()

        field().performTextInput(TITLE)
        addChip().assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun theChip_isAlwaysThere_at100() = assertTheChipIsAlwaysThere(FontScale100)

    @Test
    fun theChip_isAlwaysThere_at200() = assertTheChipIsAlwaysThere(FontScale200)

    /** The Add button is where it was before the time was read. */
    private fun assertAddStaysPutWhenATimeIsRead(fontScale: Float) {
        setSheet(fontScale, today = LocalDate.now())

        field().performTextInput(TITLE)
        val before = rule.onNodeWithText(SAVE).getBoundsInRoot().top

        field().performTextInput(" at 3pm")
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)

        assertEquals(before, rule.onNodeWithText(SAVE).getBoundsInRoot().top)
    }

    @Test
    fun add_staysPutWhenATimeIsRead_at100() = assertAddStaysPutWhenATimeIsRead(FontScale100)

    @Test
    fun add_staysPutWhenATimeIsRead_at200() = assertAddStaysPutWhenATimeIsRead(FontScale200)

    /**
     * The whole of D-112 in one pass: the chip opens Task Details' reminder
     * sheet, its Save writes the words into the field, the field reads them as
     * a reminder, and Add saves that reminder. The sheet opens on the next whole
     * hour, D-071, which at a quarter past nine is ten.
     */
    @Test
    fun theChip_writesTheChosenReminderIntoTheField() {
        val today = LocalDate.now()
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, today = today, clock = { today.atTime(9, 15) }, onSave = { saved += it })

        field().performTextInput(TITLE)
        addChip().performClick()
        rule.waitUntilExactlyOneExists(hasText(SAVE_REMINDER), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE_REMINDER).performClick()

        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_CHANGE), TIMEOUT_MILLIS)
        // "at 10am" or "at 10:00", as this phone writes times.
        assertTrue(fieldText(), fieldText().startsWith("$TITLE at 10"))
        rule.onNodeWithText("Reminder Today at", substring = true).assertExists()

        // Focus comes back to the field, with the cursor after the words.
        rule.waitUntil(TIMEOUT_MILLIS) {
            field().fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        field().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(fieldText().length))
        )

        rule.onNodeWithText(SAVE).performClick()
        val captured = saved.single()
        assertEquals(TITLE, captured.title)
        assertEquals(today.atTime(10, 0), captured.reminderAt(today, today.atTime(9, 15)))
    }

    /** Choosing again edits the day and time already there, not a second pair. */
    @Test
    fun theChip_editsATypedReminderInPlace() {
        val today = LocalDate.now()
        setSheet(FontScale100, today = today, clock = { today.atTime(9, 15) })

        field().performTextInput("$TITLE tomorrow at 3pm")
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_CHANGE), TIMEOUT_MILLIS)
        changeChip().performClick()
        rule.waitUntilExactlyOneExists(hasText(SAVE_REMINDER), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE_REMINDER).performClick()

        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_CHANGE), TIMEOUT_MILLIS)
        // "tomorrow at 3pm" or "tomorrow at 15:00": one run, rewritten.
        assertTrue(fieldText(), fieldText().startsWith("$TITLE tomorrow at "))
        assertEquals(fieldText(), 1, Regex(" at ").findAll(fieldText()).count())
    }

    /** The field has no words for ringing like an alarm, so the switch is not offered. */
    @Test
    fun theSheetFromQuickAdd_offersNoAlarmSwitch() {
        setSheet(FontScale100, today = LocalDate.now())

        field().performTextInput(TITLE)
        addChip().performClick()
        rule.waitUntilExactlyOneExists(hasText(SAVE_REMINDER), TIMEOUT_MILLIS)

        rule.onAllNodesWithText(RING_LIKE_AN_ALARM).assertCountEquals(0)
    }

    /** The sheet's Clear does what the chip's cross does: unmarks, keeps the words. */
    @Test
    fun clearingInTheSheet_unmarksTheRunLikeTheCross() {
        val today = LocalDate.now()
        setSheet(FontScale100, today = today, clock = { today.atTime(9, 15) })

        field().performTextInput("$TITLE at 3pm")
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_CHANGE), TIMEOUT_MILLIS)
        changeChip().performClick()
        rule.waitUntilExactlyOneExists(hasText(CLEAR), TIMEOUT_MILLIS)
        rule.onNodeWithText(CLEAR).performClick()

        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_ADD), TIMEOUT_MILLIS)
        assertEquals("$TITLE at 3pm", fieldText())
    }

    /** The chip changes the reminder; only the cross removes it. */
    @Test
    fun theChipChanges_andTheCrossRemoves() {
        setSheet(FontScale100, today = LocalDate.now())

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_CHANGE), TIMEOUT_MILLIS)

        changeChip()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        rule.onNode(hasClickLabel(REMINDER_DISMISS)).assertTouchTargetIsAtLeast48()
    }

    /** The example teaches a time, D-112, and still fits one line at 200%. */
    private fun assertThePlaceholderTeachesATime(fontScale: Float) {
        setSheet(fontScale)

        rule.onNode(hasText("Try", substring = true), useUnmergedTree = true)
            .assert(hasText(" at ", substring = true))
            .assert(SemanticsMatcher("fits one line") { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.single().let { it.lineCount == 1 && !it.hasVisualOverflow }
            })
    }

    @Test
    fun thePlaceholder_teachesATime_at100() = assertThePlaceholderTeachesATime(FontScale100)

    @Test
    fun thePlaceholder_teachesATime_at200() = assertThePlaceholderTeachesATime(FontScale200)

    /** The 48dp floor, measured where taps land rather than where it draws. */
    private fun SemanticsNodeInteraction.assertTouchTargetIsAtLeast48() {
        val node = fetchSemanticsNode()
        with(node.layoutInfo.density) {
            assertTrue(node.touchBoundsInRoot.width.toDp() >= 48.dp)
            assertTrue(node.touchBoundsInRoot.height.toDp() >= 48.dp)
        }
    }

    /** Nothing understood still says where the task will land. */
    @Test
    fun aPlainTitle_saysWhereItWillBeSaved() {
        setSheet(FontScale100)

        field().performTextInput(TITLE)

        rule.waitUntilExactlyOneExists(hasText(SAVED_TO_TODAY), TIMEOUT_MILLIS)
        rule.onAllNodes(hasClickLabel(REMINDER_DISMISS)).assertCountEquals(0)
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
        rule.onAllNodes(hasClickLabel(REMINDER_DISMISS)).assertCountEquals(0)
        rule.onAllNodesWithText(SCHEDULED_FOR_TOMORROW).assertCountEquals(0)
    }

    // D-101: an hour Quick Add will not read, said before saving

    private fun assertABareHourSaysNoReminderIsSet(fontScale: Float) {
        setSheet(fontScale)

        field().performTextInput("Call mom at six")

        // Under the destination, not instead of it, and announced as it appears.
        rule.waitUntilExactlyOneExists(hasText(NO_MERIDIEM), TIMEOUT_MILLIS)
        rule.onNodeWithText(NO_MERIDIEM)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithText(SAVED_TO_TODAY).assertIsDisplayed()
        rule.onAllNodes(hasClickLabel(REMINDER_DISMISS)).assertCountEquals(0)
    }

    @Test
    fun aBareHour_saysNoReminderIsSet_at100() = assertABareHourSaysNoReminderIsSet(FontScale100)

    @Test
    fun aBareHour_saysNoReminderIsSet_at200() = assertABareHourSaysNoReminderIsSet(FontScale200)

    @Test
    fun addingTheMeridiem_takesTheLineAwayAndSetsTheReminder() {
        setSheet(FontScale100)
        field().performTextInput("Call mom at six")
        rule.waitUntilExactlyOneExists(hasText(NO_MERIDIEM), TIMEOUT_MILLIS)

        field().performTextInput(" pm")

        rule.waitUntilExactlyOneExists(hasClickLabel(REMINDER_DISMISS), TIMEOUT_MILLIS)
        rule.onAllNodesWithText(NO_MERIDIEM).assertCountEquals(0)
    }

    @Test
    fun aNumberThatIsNotAnHour_saysNothingAboutReminders() {
        setSheet(FontScale100)

        field().performTextInput("Buy 6 eggs")

        rule.waitUntilExactlyOneExists(hasText(SAVED_TO_TODAY), TIMEOUT_MILLIS)
        rule.onAllNodesWithText(NO_MERIDIEM).assertCountEquals(0)
    }

    private companion object {
        const val SHEET_NAME = "New task"
        const val SAVE = "Add task"
        const val TITLE = "Buy milk"
        const val DATED_TITLE = "Buy milk tomorrow"
        const val TIMED_TITLE = "Buy milk tomorrow at 3pm"
        const val SCHEDULED_FOR_TOMORROW = "Scheduled for Tomorrow"
        const val SAVED_TO_TODAY = "Saved to Today"
        const val NO_MERIDIEM = "No reminder set. Add am or pm to the time."

        /**
         * The chip is found by its dismiss action rather than by its label.
         * The label names the time through the reader's own locale, so
         * asserting it would fail on a device set to a 24-hour clock and would
         * be testing `DateTimeFormatter` rather than this screen. What the chip
         * promises is asserted through `onSave` instead, where it is a value.
         */
        const val REMINDER_DISMISS = "Remove reminder"
        const val REMINDER_ADD = "Add reminder"
        const val REMINDER_CHANGE = "Change reminder"
        const val SAVE_REMINDER = "Save reminder"
        const val CLEAR = "Clear"
        const val RING_LIKE_AN_ALARM = "Ring like an alarm"
        const val TIMEOUT_MILLIS = 5_000L

        /** Fixed, so "tomorrow" is a known date rather than whatever today is. */
        val TODAY: LocalDate = LocalDate.of(2026, 8, 31)
    }
}
