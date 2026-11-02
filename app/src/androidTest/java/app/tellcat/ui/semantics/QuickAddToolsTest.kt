package app.tellcat.ui.semantics

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.core.domain.Recurrence
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.ui.task.QuickAddCapture
import app.tellcat.ui.task.QuickAddSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Quick Add's tools, `docs/decisions.md` D-121: Repeat and Checklist, which the
 * title cannot say, and the rule that a repeating task always has a day. When is
 * covered with the field it writes into, in `QuickAddSemanticsTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddToolsTest {

    @get:Rule
    val rule = createComposeRule()

    private val saved = mutableListOf<QuickAddCapture>()
    private val today: LocalDate = LocalDate.now()

    private fun sheet(fallbackDate: LocalDate?, fontScale: Float = FontScale100) {
        rule.setTellcatContent(fontScale) {
            QuickAddSheet(
                today = today,
                fallbackDate = fallbackDate,
                onDismiss = {},
                onSave = { saved += it }
            )
        }
        rule.waitUntilExactlyOneExists(hasText(SHEET_NAME), TIMEOUT_MILLIS)
    }

    private fun field() = rule.onNode(hasSetTextAction() and hasContentDescription(ITEM, substring = true).not())
    private fun fieldText(): String = field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun tool(name: String) = rule.onNode(hasContentDescription(name, substring = true) and hasClickAction())
    private fun item(number: Int) = rule.onNodeWithContentDescription("$ITEM $number")

    // --- Checklist ---------------------------------------------------------------------

    @Test
    fun enterOnAnItemAddsTheNextAndAddSavesTheLines() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")

        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        item(1).performTextInput("Milk")
        item(1).performImeAction()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 2"), TIMEOUT_MILLIS)
        item(2).performTextInput("Eggs")
        item(2).performImeAction()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 3"), TIMEOUT_MILLIS)
        item(3).performTextInput("Bread")

        rule.onNodeWithText(SAVE).performClick()

        val capture = saved.single()
        assertEquals("Groceries", capture.parsed.title)
        assertEquals(listOf("Milk", "Eggs", "Bread"), capture.checklist)
    }

    @Test
    fun backspaceOnAnEmptyItemRemovesIt() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")

        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        item(1).performTextInput("Milk")
        item(1).performImeAction()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 2"), TIMEOUT_MILLIS)

        item(2).performKeyInput { pressKey(Key.Backspace) }

        rule.waitUntilDoesNotExist(hasContentDescription("$ITEM 2"), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()
        assertEquals(listOf("Milk"), saved.single().checklist)
    }

    @Test
    fun blankLinesAreNotSaved() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")
        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)

        rule.onNodeWithText(SAVE).performClick()
        assertEquals(emptyList<String>(), saved.single().checklist)
    }

    // --- The checklist turns on and off, D-121 as amended ------------------------------------

    @Test
    fun turningTheChecklistOffHidesItsLinesAndDoesNotSaveThem() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")
        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        item(1).performTextInput("Milk")

        tool(CHECKLIST).performClick()

        rule.waitUntilDoesNotExist(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()
        assertEquals(emptyList<String>(), saved.single().checklist)
    }

    /** Off loses nothing: on again, the lines come back as they were. */
    @Test
    fun turningTheChecklistBackOnBringsItsLinesBack() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")
        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        item(1).performTextInput("Milk")

        tool(CHECKLIST).performClick()
        rule.waitUntilDoesNotExist(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        tool(CHECKLIST).performClick()

        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()
        assertEquals(listOf("Milk"), saved.single().checklist)
    }

    /** The tool no longer adds a line each tap; Enter does. */
    @Test
    fun theChecklistToolDoesNotAddLines() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")
        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)

        tool(CHECKLIST).performClick()
        tool(CHECKLIST).performClick()

        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        assertEquals(0, rule.onAllNodes(hasContentDescription("$ITEM 2")).fetchSemanticsNodes().size)
    }

    @Test
    fun aLinesCrossRemovesIt() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")
        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        item(1).performTextInput("Milk")
        item(1).performImeAction()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 2"), TIMEOUT_MILLIS)
        item(2).performTextInput("Eggs")

        rule.onNodeWithContentDescription("Remove Milk").assertTouchTargetIsAtLeast48().performClick()

        rule.waitUntilDoesNotExist(hasContentDescription("Remove Milk"), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()
        assertEquals(listOf("Eggs"), saved.single().checklist)
    }

    /** The last line removed is the checklist off. */
    @Test
    fun removingTheLastLineTurnsTheChecklistOff() {
        sheet(fallbackDate = today)
        field().performTextInput("Groceries")
        tool(CHECKLIST).performClick()
        rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)

        rule.onNodeWithContentDescription("Remove item 1").performClick()

        rule.waitUntilDoesNotExist(hasContentDescription("$ITEM 1"), TIMEOUT_MILLIS)
        rule.onNode(hasContentDescription(CHECKLIST) and hasClickAction()).assertExists()
    }

    // --- Tools before a title, D-121 as amended -------------------------------------------------

    /** When first, with nothing typed: its words go in, the cursor in front, and the title is typed there. */
    @Test
    fun whenBeforeATitleWritesTheDayWithTheTitleToCome() {
        sheet(fallbackDate = today)

        tool(WHEN).assertIsEnabled().performClick()
        rule.waitUntilExactlyOneExists(hasText(NO_TIME), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE_WHEN).performClick()

        rule.waitUntil(TIMEOUT_MILLIS) { fieldText() == " today" }
        rule.onNodeWithText(TITLE_FIRST).assertIsDisplayed()
        rule.onNodeWithText(SAVE).assertIsNotEnabled()

        field().performTextInput("Call Sam")

        rule.waitUntil(TIMEOUT_MILLIS) { fieldText() == "Call Sam today" }
        rule.onNodeWithText(SAVE).performClick()
        assertEquals("Call Sam", saved.single().parsed.title)
        assertEquals(today, saved.single().parsed.date)
    }

    /** Repeat first, from Inbox: the first day is written the same way. */
    @Test
    fun repeatBeforeATitleInInboxWritesItsFirstDay() {
        sheet(fallbackDate = null)

        tool(REPEAT).assertIsEnabled().performClick()
        rule.waitUntilExactlyOneExists(hasText(DAILY), TIMEOUT_MILLIS)
        rule.onNodeWithText(DAILY).performClick()

        rule.waitUntil(TIMEOUT_MILLIS) { fieldText() == " today" }
        field().performTextInput("Water the plants")
        rule.onNodeWithText(SAVE).performClick()

        val capture = saved.single()
        assertEquals("Water the plants", capture.parsed.title)
        assertEquals(today, capture.parsed.date)
        assertEquals(Recurrence(RecurrenceUnit.DAILY), capture.recurrence)
    }

    /** Typed, not written by a tool, "tomorrow" is still a task called Tomorrow. */
    @Test
    fun aTypedDayAloneIsStillATitle() {
        sheet(fallbackDate = today)
        field().performTextInput("tomorrow")

        rule.onNodeWithText(SAVE).assertIsEnabled().performClick()
        assertEquals("tomorrow", saved.single().parsed.title)
    }

    /** Clearing When with only its words there takes them out. */
    @Test
    fun clearingWhenBeforeATitleEmptiesTheField() {
        sheet(fallbackDate = today)
        tool(WHEN).performClick()
        rule.waitUntilExactlyOneExists(hasText(NO_TIME), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE_WHEN).performClick()
        rule.waitUntil(TIMEOUT_MILLIS) { fieldText() == " today" }

        tool(WHEN).performClick()
        rule.waitUntilExactlyOneExists(hasText(CLEAR), TIMEOUT_MILLIS)
        rule.onNodeWithText(CLEAR).performClick()

        rule.waitUntil(TIMEOUT_MILLIS) { fieldText().isEmpty() }
    }

    /** The 48dp floor, measured where taps land. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTouchTargetIsAtLeast48() = apply {
        val node = fetchSemanticsNode()
        with(node.layoutInfo.density) {
            assertTrue(node.touchBoundsInRoot.width.toDp() >= 48.dp)
            assertTrue(node.touchBoundsInRoot.height.toDp() >= 48.dp)
        }
    }

    // --- D-123: a rule that does not fall on the day moves it ----------------------------------

    /** Repeat, Custom, Month, On the last day, Save repeat. */
    private fun chooseTheLastDayInCustom() {
        tool(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(CUSTOM_ROW), TIMEOUT_MILLIS)
        rule.onNodeWithText(CUSTOM_ROW).performScrollTo().performClick()
        rule.onNode(hasText(EVERY, substring = true) and hasClickAction()).performClick()
        rule.waitUntilExactlyOneExists(hasText(MONTH), TIMEOUT_MILLIS)
        rule.onNodeWithText(MONTH).performClick()
        rule.onNodeWithText(DONE).performClick()
        rule.waitUntilExactlyOneExists(hasText(ON_THE_LAST_DAY), TIMEOUT_MILLIS)
        rule.onNodeWithText(ON_THE_LAST_DAY).performScrollTo().performClick()
        rule.onNodeWithText(SAVE_REPEAT).performScrollTo().performClick()
    }

    /** With no day typed, the task lands on this month's last day. */
    @Test
    fun theLastDayWithNoDayLandsOnThisMonthsLastDay() {
        sheet(fallbackDate = today)
        field().performTextInput("Pay rent")

        chooseTheLastDayInCustom()
        rule.waitUntilDoesNotExist(hasText(SAVE_REPEAT), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()

        val capture = saved.single()
        assertEquals(Recurrence(RecurrenceUnit.MONTHLY, monthlyLastDay = true), capture.recurrence)
        // Unwritten only when today is the month's last day, where Today puts it anyway.
        assertEquals(java.time.YearMonth.from(today).atEndOfMonth(), capture.parsed.date ?: today)
    }

    /** A typed day the rule does not fall on is rewritten, keeping its time. */
    @Test
    fun theLastDayMovesATypedDayAndKeepsItsTime() {
        sheet(fallbackDate = today)
        field().performTextInput("Pay rent tomorrow at 9am")
        rule.waitUntilExactlyOneExists(hasContentDescription("$WHEN, ", substring = true), TIMEOUT_MILLIS)

        chooseTheLastDayInCustom()
        rule.waitUntilDoesNotExist(hasText(SAVE_REPEAT), TIMEOUT_MILLIS)
        rule.onNodeWithText(SAVE).performClick()

        val monthEnd = java.time.YearMonth.from(today.plusDays(1)).atEndOfMonth()
        val capture = saved.single()
        assertEquals("Pay rent", capture.parsed.title)
        assertEquals(monthEnd.atTime(9, 0), capture.parsed.reminderAt(today, today.atTime(8, 0)))
    }

    // --- Repeat, and a repeating task always has a day -------------------------------------

    /** From Inbox, with no day typed: the rule's first day is written into the title. */
    @Test
    fun aRepeatSetInInboxWritesItsFirstDay() {
        sheet(fallbackDate = null)
        field().performTextInput("Water the plants")

        tool(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(DAILY), TIMEOUT_MILLIS)
        rule.onNodeWithText(DAILY).performClick()

        rule.waitUntil(TIMEOUT_MILLIS) { fieldText() == "Water the plants today" }
        rule.onNodeWithText(SAVE).performClick()

        val capture = saved.single()
        assertEquals(Recurrence(RecurrenceUnit.DAILY), capture.recurrence)
        assertEquals("Water the plants", capture.parsed.title)
        assertEquals(today, capture.parsed.date)
    }

    /** On Today a daily rule falls today, where the task lands anyway: nothing is written. */
    @Test
    fun aRepeatSetOnTodayThatFallsTodayWritesNothing() {
        sheet(fallbackDate = today)
        field().performTextInput("Take pills at 8am")

        tool(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(DAILY), TIMEOUT_MILLIS)
        rule.onNodeWithText(DAILY).performClick()

        rule.waitUntil(TIMEOUT_MILLIS) {
            rule.onAllNodes(hasContentDescription("$REPEAT, ", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Take pills at 8am", fieldText())
        rule.onNodeWithText(SAVE).performClick()
        assertEquals(Recurrence(RecurrenceUnit.DAILY), saved.single().recurrence)
    }

    @Test
    fun aCaptureWithNoToolsHasNoRepeatOrChecklist() {
        sheet(fallbackDate = null)
        field().performTextInput("Find a dentist")
        rule.onNodeWithText(SAVE).performClick()

        assertNull(saved.single().recurrence)
        assertEquals(emptyList<String>(), saved.single().checklist)
    }

    // --- Add stays reachable ------------------------------------------------------------------

    /** At 200% with a repeat and six lines, the title scrolls and Add stays on screen. */
    @Test
    fun addStaysOnScreenAt200WithEveryToolSet() {
        sheet(fallbackDate = today, fontScale = FontScale200)
        field().performTextInput("Pack for the trip tomorrow at 9am")

        tool(REPEAT).performClick()
        rule.waitUntilExactlyOneExists(hasText(DAILY), TIMEOUT_MILLIS)
        rule.onNodeWithText(DAILY).performClick()

        tool(CHECKLIST).performClick()
        for (number in 1..6) {
            rule.waitUntilExactlyOneExists(hasContentDescription("$ITEM $number"), TIMEOUT_MILLIS)
            item(number).performTextInput("Thing $number")
            if (number < 6) item(number).performImeAction()
        }

        rule.onNodeWithText(SAVE).assertIsDisplayed()
        rule.onNodeWithText(SAVE).performClick()
        assertEquals(6, saved.single().checklist.size)
    }

    /**
     * When shows its value in the row; a long one is cut short, never Add. Found
     * in review before any run: When was measured first and could take the row.
     */
    @Test
    fun aLongWhenNeverPushesAddOffTheRow() {
        sheet(fallbackDate = today)
        val farDay = today.plusDays(40)
        field().performTextInput("Pay the quarterly tax bill on ${farDay.dayOfMonth} ${farDay.month.name.lowercase()} at 9am")
        rule.waitUntilExactlyOneExists(hasContentDescription("When, ", substring = true), TIMEOUT_MILLIS)

        val add = rule.onNodeWithText(SAVE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Add ends at ${add.right}, the screen at ${root.right}", add.right <= root.right)
        assertTrue("Add is ${add.width} wide", add.width > 0f)
    }

    private companion object {
        const val SHEET_NAME = "New task"
        const val SAVE = "Add task"
        const val REPEAT = "Repeat"
        const val CHECKLIST = "Checklist"
        const val ITEM = "Checklist item"
        const val DAILY = "Daily"
        const val CUSTOM_ROW = "Custom"
        const val EVERY = "Every"
        const val MONTH = "Month"
        const val DONE = "Done"
        const val ON_THE_LAST_DAY = "On the last day"
        const val SAVE_REPEAT = "Save repeat"
        const val WHEN = "When"
        const val NO_TIME = "No time"
        const val SAVE_WHEN = "Save reminder"
        const val CLEAR = "Clear"
        const val TITLE_FIRST = "Now type the task in front"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
