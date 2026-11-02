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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
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
                onSave = { onSave(it.parsed) }
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

        // Since D-121 the title scrolls above a fixed row of tools and Add, so
        // Add has to be on screen unaided even at 200%.
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

    // --- D-011 and D-121: the time, named in words and by the When tool ------------

    /** The When tool once it holds something: its name, then what it holds. */
    private fun whenIsSet() = hasContentDescription("$WHEN, ", substring = true)

    /** The When tool, set or not, found by its name and that it can be pressed. */
    private fun whenTool() = rule.onNode(hasContentDescription(WHEN, substring = true) and hasClickAction())

    /**
     * A trailing time sets a reminder. Since D-121 there is no chip: the When
     * tool holds it, and the line under the field says the day and the time in
     * words, which is what a screen reader reads the promise from.
     */
    private fun assertATimeIsNamedInWords(fontScale: Float) {
        // The real day, not the fixed one. Since D-075 the line names the day
        // the reminder rings, which the sheet resolves against the clock, and a
        // fixed day weeks behind it puts that on a date this test cannot name.
        setSheet(fontScale, today = LocalDate.now())

        field().performTextInput(TIMED_TITLE)

        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        rule.onNodeWithText("$SCHEDULED_FOR_TOMORROW at", substring = true).assertIsDisplayed()
    }

    @Test
    fun aTime_isNamedInWords_at100() = assertATimeIsNamedInWords(FontScale100)

    @Test
    fun aTime_isNamedInWords_at200() = assertATimeIsNamedInWords(FontScale200)

    /**
     * The line keeps telling the truth while the sheet is open. Typed in the
     * last seconds of 2:38, "at 2:38pm" is today's; once 2:39 starts the save
     * would put it on tomorrow, so the line has to say Tomorrow before anyone
     * presses Add.
     */
    @Test
    fun theLineTurnsToTomorrowWhenItsMinuteEnds() {
        val today = LocalDate.now()
        var now = today.atTime(14, 38, 50)
        setSheet(FontScale100, today = today, clock = { now })

        field().performTextInput("Call mom at 2:38pm")
        rule.onNodeWithText("Scheduled for Today at", substring = true).assertExists()

        now = today.atTime(14, 39, 1)
        rule.mainClock.advanceTimeBy(11_000)

        rule.onNodeWithText("Scheduled for Tomorrow at", substring = true).assertExists()
    }

    /** A button that opens the sheet, never a checked control. */
    @Test
    fun theWhenTool_isAButtonNotAToggle() {
        setSheet(FontScale100, today = LocalDate.now())
        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)

        rule.onNode(whenIsSet())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assertTouchTargetIsAtLeast48()
    }

    @Test
    fun aTime_becomesAReminderOnSave() {
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, onSave = { parsed -> saved += parsed })

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()

        val captured = saved.single()
        assertEquals(TITLE, captured.title)
        assertEquals(TODAY.plusDays(1), captured.date)
        assertEquals(LocalTime.of(15, 0), captured.time)
        assertEquals(TODAY.plusDays(1).atTime(15, 0), captured.reminderAt(TODAY, TODAY.atTime(9, 0)))
    }

    /**
     * **Clearing drops the reminder and keeps the day**, D-011's asymmetry. With
     * the chip gone (D-121) it is When's Clear that does it.
     */
    @Test
    fun clearingInWhen_dropsTheReminderAndKeepsTheDay() {
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, onSave = { parsed -> saved += parsed })

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        whenTool().performClick()
        rule.waitUntilExactlyOneExists(hasText(CLEAR), TIMEOUT_MILLIS)
        rule.onNodeWithText(CLEAR).performClick()

        // The line names the day alone again.
        rule.waitUntilExactlyOneExists(hasText(SCHEDULED_FOR_TOMORROW), TIMEOUT_MILLIS)
        rule.waitUntilStill(hasText(SAVE))

        rule.onNodeWithText(SAVE).performClick()

        val captured = saved.single()
        assertNull(captured.time)
        assertNull(captured.reminderAt(TODAY, TODAY.atTime(9, 0)))
        assertEquals(TODAY.plusDays(1), captured.date)
    }

    /**
     * A clearing cannot outlive the text it was about. Typing again is a new
     * parse and a new promise, and suppressing it would withhold a reminder the
     * user never declined.
     */
    @Test
    fun typingAgainAfterClearing_bringsTheReminderBack() {
        // The real day, for the reason [assertATimeIsNamedInWords] gives.
        setSheet(FontScale100, today = LocalDate.now())

        field().performTextInput(TIMED_TITLE)
        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        whenTool().performClick()
        rule.waitUntilExactlyOneExists(hasText(CLEAR), TIMEOUT_MILLIS)
        rule.onNodeWithText(CLEAR).performClick()
        rule.waitUntilExactlyOneExists(hasText(SCHEDULED_FOR_TOMORROW), TIMEOUT_MILLIS)
        rule.waitUntilStill(hasText(SAVE))

        // Retyping a trailing time, which is a fresh promise. Which day it rings
        // on depends on the hour the test runs; that it rings is the point.
        field().performTextInput(" at 4pm")

        rule.waitUntilExactlyOneExists(
            hasText("Scheduled for", substring = true) and hasText(" at ", substring = true),
            TIMEOUT_MILLIS
        )
    }

    // --- D-112 and D-121: When writes into the field ----------------------------------

    /** The field's text, as typed or as a tool wrote it. */
    private fun fieldText(): String =
        field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /**
     * Always on the sheet, D-121, so Add never moves as tools are set, and live
     * from the start: the owner picks a day or a repeat before typing the task
     * (D-121 as amended). Add still waits for a title. The line under the field
     * is the polite live region the chip used to be.
     */
    private fun assertTheToolsAreAlwaysThere(fontScale: Float) {
        setSheet(fontScale)

        for (tool in listOf(WHEN, REPEAT, CHECKLIST)) {
            rule.onNode(hasContentDescription(tool) and hasClickAction()).assertIsDisplayed().assertIsEnabled()
        }
        rule.onNodeWithText(SAVE).assertIsNotEnabled()

        field().performTextInput(TITLE)
        for (tool in listOf(WHEN, REPEAT, CHECKLIST)) {
            rule.onNode(hasContentDescription(tool) and hasClickAction()).assertIsEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        rule.waitUntilExactlyOneExists(hasText(SAVED_TO_TODAY), TIMEOUT_MILLIS)
        assertTrue(
            rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun theTools_areAlwaysThere_at100() = assertTheToolsAreAlwaysThere(FontScale100)

    @Test
    fun theTools_areAlwaysThere_at200() = assertTheToolsAreAlwaysThere(FontScale200)

    /** The Add button is where it was before the time was read. */
    private fun assertAddStaysPutWhenATimeIsRead(fontScale: Float) {
        setSheet(fontScale, today = LocalDate.now())

        field().performTextInput(TITLE)
        val before = rule.onNodeWithText(SAVE).getBoundsInRoot().top

        field().performTextInput(" at 3pm")
        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)

        assertEquals(before, rule.onNodeWithText(SAVE).getBoundsInRoot().top)
    }

    @Test
    fun add_staysPutWhenATimeIsRead_at100() = assertAddStaysPutWhenATimeIsRead(FontScale100)

    @Test
    fun add_staysPutWhenATimeIsRead_at200() = assertAddStaysPutWhenATimeIsRead(FontScale200)

    /**
     * When with no time chosen writes the day alone, D-121: the sheet opens on
     * the day the task would land on, Save hands it back, the field reads it as
     * a day with no reminder, and Add saves that. Focus comes back to the field.
     */
    @Test
    fun when_writesADayWithNoTimeIntoTheField() {
        val today = LocalDate.now()
        val saved = mutableListOf<CapturedTask>()
        setSheet(FontScale100, today = today, clock = { today.atTime(9, 15) }, onSave = { saved += it })

        field().performTextInput(TITLE)
        whenTool().performClick()
        rule.waitUntilExactlyOneExists(hasText(NO_TIME), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE_REMINDER).performClick()

        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        assertEquals("$TITLE today", fieldText())

        rule.waitUntil(TIMEOUT_MILLIS) {
            field().fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        field().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(fieldText().length))
        )

        rule.onNodeWithText(SAVE).performClick()
        val captured = saved.single()
        assertEquals(TITLE, captured.title)
        assertEquals(today, captured.date)
        assertNull(captured.time)
    }

    /** Choosing again edits the day and time already there, not a second pair. */
    @Test
    fun when_editsATypedReminderInPlace() {
        val today = LocalDate.now()
        setSheet(FontScale100, today = today, clock = { today.atTime(9, 15) })

        field().performTextInput("$TITLE tomorrow at 3pm")
        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        whenTool().performClick()
        rule.waitUntilExactlyOneExists(hasText(SAVE_REMINDER), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE_REMINDER).performClick()

        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        // "tomorrow at 3pm" or "tomorrow at 15:00": one run, rewritten.
        assertTrue(fieldText(), fieldText().startsWith("$TITLE tomorrow at "))
        assertEquals(fieldText(), 1, Regex(" at ").findAll(fieldText()).count())
    }

    /** The field has no words for ringing like an alarm, so the switch is not offered. */
    @Test
    fun whenFromQuickAdd_offersNoAlarmSwitch() {
        setSheet(FontScale100, today = LocalDate.now())

        field().performTextInput(TITLE)
        whenTool().performClick()
        rule.waitUntilExactlyOneExists(hasText(SAVE_REMINDER), TIMEOUT_MILLIS)

        rule.onAllNodesWithText(RING_LIKE_AN_ALARM).assertCountEquals(0)
    }

    /** When's Clear unmarks the time and keeps the words. */
    @Test
    fun clearingInWhen_unmarksTheRun() {
        val today = LocalDate.now()
        setSheet(FontScale100, today = today, clock = { today.atTime(9, 15) })

        field().performTextInput("$TITLE at 3pm")
        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        whenTool().performClick()
        rule.waitUntilExactlyOneExists(hasText(CLEAR), TIMEOUT_MILLIS)
        rule.onNodeWithText(CLEAR).performClick()

        rule.waitUntil(TIMEOUT_MILLIS) { rule.onAllNodes(whenIsSet()).fetchSemanticsNodes().isEmpty() }
        assertEquals("$TITLE at 3pm", fieldText())
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
        rule.onAllNodes(whenIsSet()).assertCountEquals(0)
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
        rule.onAllNodes(whenIsSet()).assertCountEquals(0)
        rule.onAllNodesWithText(SCHEDULED_FOR_TOMORROW, substring = true).assertCountEquals(0)
    }

    // D-101: an hour Quick Add will not read, said before saving

    private fun assertABareHourSaysNoReminderIsSet(fontScale: Float) {
        setSheet(fontScale)

        field().performTextInput("Call mom at six")

        // In the destination's place, D-101 as amended, and announced as it
        // appears: the line it stands in is the polite live region.
        rule.waitUntilExactlyOneExists(hasText(NO_MERIDIEM), TIMEOUT_MILLIS)
        rule.onNodeWithText(NO_MERIDIEM).assertIsDisplayed()
        rule.onNodeWithText(NO_MERIDIEM, useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onAllNodesWithText(SAVED_TO_TODAY).assertCountEquals(0)
        rule.onAllNodes(whenIsSet()).assertCountEquals(0)
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

        rule.waitUntilExactlyOneExists(whenIsSet(), TIMEOUT_MILLIS)
        rule.onAllNodesWithText(NO_MERIDIEM).assertCountEquals(0)
    }

    /**
     * Typing "at 6pm" passes through "at 6". The line waits for the typing to
     * stop, and when it shows it takes the destination's place, so nothing on
     * the sheet moves. The owner's trial, 2026-11-02: it used to jump.
     */
    @Test
    fun theLineWaitsForAPauseAndMovesNothing() {
        setSheet(FontScale100)
        field().performTextInput("Call mom")
        rule.waitUntilExactlyOneExists(hasText(SAVED_TO_TODAY), TIMEOUT_MILLIS)
        val addTop = rule.onNodeWithText(SAVE).fetchSemanticsNode().boundsInRoot.top

        rule.mainClock.autoAdvance = false
        field().performTextInput(" at six")
        rule.mainClock.advanceTimeBy(300)
        rule.onAllNodesWithText(NO_MERIDIEM).assertCountEquals(0)
        rule.onNodeWithText(SAVED_TO_TODAY).assertIsDisplayed()

        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText(NO_MERIDIEM).assertIsDisplayed()
        assertEquals(addTop, rule.onNodeWithText(SAVE).fetchSemanticsNode().boundsInRoot.top)
        rule.mainClock.autoAdvance = true
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
         * The tools, found by their names. What the When tool holds is read
         * through the reader's own locale, so it is asserted as "When, " plus
         * anything, and what it promises through `onSave`, where it is a value.
         */
        const val WHEN = "When"
        const val REPEAT = "Repeat"
        const val CHECKLIST = "Checklist"
        const val NO_TIME = "No time"
        const val SAVE_REMINDER = "Save reminder"
        const val CLEAR = "Clear"
        const val RING_LIKE_AN_ALARM = "Ring like an alarm"
        const val TIMEOUT_MILLIS = 5_000L

        /** Fixed, so "tomorrow" is a known date rather than whatever today is. */
        val TODAY: LocalDate = LocalDate.of(2026, 8, 31)
    }
}
