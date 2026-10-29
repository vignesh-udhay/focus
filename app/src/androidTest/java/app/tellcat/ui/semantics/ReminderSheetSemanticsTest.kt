package app.tellcat.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.test.platform.app.InstrumentationRegistry
import app.tellcat.core.domain.AlarmMode
import app.tellcat.core.notification.currentAlarmMode
import org.junit.Assume.assumeTrue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.ui.task.ReminderSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The reminder sheet refuses to promise a moment that has gone.
 *
 * `docs/decisions.md` D-030, and the reason the product's highest-severity
 * behaviour gets an interface test rather than only a domain one.
 * `RemindersTest` proves `nextReminderOccurrence` picks the right moment.
 * Nothing there proves the sheet asks it, shows the answer, or stops the user
 * saving past it, and that wiring is where the original bug lived: the domain
 * was fine and the control could not express what the domain knew.
 *
 * **The clock is a parameter, which is what makes any of this assertable.** The
 * whole behaviour is about six in the evening being after nine in the morning,
 * so a sheet reading a clock it owned could only be tested by waiting until the
 * evening. Fixed here at 18:00 on the same Tuesday `RemindersTest` uses, so the
 * two read as one story.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReminderSheetSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * Renders the sheet with the clock stopped, and collects what Save writes.
     *
     * [defaultDay] is what Task Details hands over: the reminder's own day, the
     * scheduled date while it is still ahead, or today.
     */
    private fun setSheet(
        fontScale: Float = FontScale100,
        reminderAt: LocalDateTime? = null,
        defaultDay: LocalDate = TODAY
    ): MutableList<LocalDateTime> {
        val saved = mutableListOf<LocalDateTime>()

        rule.setTellcatContent(fontScale) {
            ReminderSheet(
                reminderAt = reminderAt,
                defaultDay = defaultDay,
                today = TODAY,
                onSet = { at, _ -> saved += at },
                onClear = {},
                onDismiss = {},
                now = NOW
            )
        }

        rule.waitUntilExactlyOneExists(hasText(SAVE), TIMEOUT_MILLIS)
        return saved
    }

    /** The sheet as Task Details opens it with the alarm switch, and what Save writes. D-102. */
    private fun setAlarmSheet(
        reminderAt: LocalDateTime? = null,
        ringsLikeAlarm: Boolean = false
    ): MutableList<Pair<LocalDateTime, Boolean>> {
        val saved = mutableListOf<Pair<LocalDateTime, Boolean>>()

        rule.setTellcatContent(FontScale100) {
            ReminderSheet(
                reminderAt = reminderAt,
                defaultDay = TODAY,
                today = TODAY,
                onSet = { at, alarm -> saved += at to alarm },
                onClear = {},
                onDismiss = {},
                now = NOW,
                ringsLikeAlarm = ringsLikeAlarm
            )
        }

        rule.waitUntilExactlyOneExists(hasText(SAVE), TIMEOUT_MILLIS)
        return saved
    }

    // --- D-102: ring like an alarm ---------------------------------------------

    @Test
    fun aNewReminderDoesNotRingLikeAnAlarm() {
        val saved = setAlarmSheet()

        rule.onNode(isToggleable() and hasText(ALARM)).assertIsOff()
        rule.onNodeWithText(SAVE).performClick()

        assertEquals(listOf(TODAY.atTime(19, 0) to false), saved)
    }

    /** The editor says how it will ring before the user relies on it. */
    @Test
    fun anAlarmReminderSaysHowItWillRingAndSavesAsOne() {
        val saved = setAlarmSheet(reminderAt = TODAY.atTime(21, 0), ringsLikeAlarm = true)

        rule.onNode(isToggleable() and hasText(ALARM)).assertIsOn()
        val promises = ALARM_PROMISES.sumOf { promise ->
            rule.onAllNodesWithText(promise).fetchSemanticsNodes().size
        }
        assertEquals("The editor does not say how the alarm will ring", 1, promises)

        rule.onNodeWithText(SAVE).performClick()
        assertEquals(listOf(TODAY.atTime(21, 0) to true), saved)
    }

    /**
     * R-013. Only meaningful where full screen is not yet allowed, which is a
     * fresh install on Android 14 and later; elsewhere there is nothing to ask.
     */
    @Test
    fun turningItOnWithoutFullScreenAsksWithAPictureOfTheAlarm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.currentAlarmMode() == AlarmMode.Banner)
        setAlarmSheet()

        rule.onNode(isToggleable() and hasText(ALARM)).performClick()

        rule.waitUntilExactlyOneExists(hasText(PRIMING_TITLE), TIMEOUT_MILLIS)
        rule.onNode(hasContentDescription(PRIMING_PICTURE)).assertExists()
        // The sheet's own button, beside the picture. The editor under it shows
        // an "Allow full screen" of its own in this mode, so the label alone
        // matches two.
        rule.onNode(hasText(PRIMING_ALLOW) and hasAnySibling(hasContentDescription(PRIMING_PICTURE)))
            .assertIsDisplayed()
    }

    // --- the two rows, which are the whole sheet ------------------------------

    private fun assertTheSheetIsADayAndATime(fontScale: Float) {
        setSheet(fontScale)

        rule.onNodeWithText(DAY).assertIsDisplayed()
        rule.onNodeWithText(TIME).assertIsDisplayed()
        rule.onNodeWithText(SAVE).assertIsDisplayed()
    }

    @Test
    fun theSheetIsADayAndATime_at100() = assertTheSheetIsADayAndATime(FontScale100)

    @Test
    fun theSheetIsADayAndATime_at200() = assertTheSheetIsADayAndATime(FontScale200)

    /**
     * Nothing to clear, nothing offered, on the rule `RepeatSheet` argues from:
     * a control that can do nothing is one the user cannot tell worked.
     */
    @Test
    fun clearIsNotOfferedWhenThereIsNoReminder() {
        setSheet(reminderAt = null)

        rule.onAllNodesWithText(CLEAR).assertCountEquals(0)
    }

    @Test
    fun clearIsOfferedWhenThereIsOne() {
        setSheet(reminderAt = TODAY.atTime(21, 0))

        rule.onNodeWithText(CLEAR).assertIsDisplayed()
    }

    // --- the day follows the time until the user says otherwise ---------------

    /**
     * An ordinary open, which is the case D-071 changed. Six in the evening
     * offers seven in the evening, today, rather than sending the user to
     * tomorrow morning for a reminder they asked for now.
     */
    @Test
    fun theSheetOpensOnTodayAtTheNextHour() {
        val saved = setSheet()

        rule.onNodeWithText(TODAY_LABEL).assertIsDisplayed()

        rule.onNodeWithText(SAVE).performClick()
        assertEquals(listOf(TODAY.atTime(19, 0)), saved)
    }

    /**
     * The defect, seen from the interface. A reminder already set for nine in
     * the morning, reopened at six in the evening, reads Tomorrow rather than
     * offering to ring nine hours ago.
     */
    @Test
    fun aTimeAlreadyGoneShowsTomorrow() {
        setSheet(reminderAt = TODAY.atTime(9, 0))

        rule.onNodeWithText(TOMORROW).assertIsDisplayed()
    }

    /** And Save writes exactly the day the row was showing. */
    @Test
    fun savingWritesTheResolvedMoment() {
        val saved = setSheet(reminderAt = TODAY.atTime(9, 0))

        rule.onNodeWithText(SAVE).performClick()

        assertEquals(listOf(TODAY.plusDays(1).atTime(9, 0)), saved)
    }

    /**
     * A day already past, handed in as the default, is not what gets stored.
     * This is the case that produced the bug: a task scheduled five days ago
     * could take a reminder only on that day, which then rang immediately.
     */
    @Test
    fun aDefaultDayInThePastIsNotWhatGetsSaved() {
        val saved = setSheet(defaultDay = TODAY.minusDays(5))

        rule.onNodeWithText(SAVE).performClick()

        assertEquals(listOf(TODAY.plusDays(1).atTime(9, 0)), saved)
    }

    /**
     * A time still ahead today is left on today, which is the common case and
     * the one a forward rule must not break.
     */
    @Test
    fun aTimeStillAheadTodayStaysOnToday() {
        val saved = setSheet(reminderAt = TODAY.atTime(21, 0))

        rule.onNodeWithText(TODAY_LABEL).assertIsDisplayed()

        rule.onNodeWithText(SAVE).performClick()
        assertEquals(listOf(TODAY.atTime(21, 0)), saved)
    }

    // --- a day the user chose is honoured, and refused when it cannot be kept --

    /**
     * Choosing Today for a nine o'clock reminder, at six in the evening, is a
     * request the app cannot keep. It says so and refuses, rather than storing
     * a promise it has already decided it will break, which is what TickTick
     * does and what D-030 declined to copy.
     */
    @Test
    fun choosingADayAlreadyGoneDisablesSaveAndSaysWhy() {
        setSheet(reminderAt = TODAY.atTime(9, 0))

        rule.onNodeWithText(DAY).performClick()
        rule.onNodeWithText(TODAY_LABEL).performClick()

        rule.onNodeWithText(PAST).assertIsDisplayed()
        rule.onNodeWithText(SAVE).assertIsNotEnabled()
    }

    /** And the refusal lifts on naming a day that can still be kept. */
    @Test
    fun theRefusalIsUndoneByChoosingTomorrow() {
        setSheet(reminderAt = TODAY.atTime(9, 0))

        rule.onNodeWithText(DAY).performClick()
        rule.onNodeWithText(TODAY_LABEL).performClick()
        rule.onNodeWithText(SAVE).assertIsNotEnabled()

        rule.onNodeWithText(DAY).performClick()
        rule.onNodeWithText(TOMORROW_LABEL).performClick()

        rule.onAllNodesWithText(PAST).assertCountEquals(0)
        rule.onNodeWithText(SAVE).assertIsEnabled()
    }

    // --- D-109: the snooze inside the app -------------------------------------

    /** The sheet on a reminder that has rung, and what Snooze writes. */
    private fun setSnoozeSheet(
        fontScale: Float = FontScale100,
        canSnooze: Boolean = true
    ): MutableList<LocalDateTime> {
        val snoozed = mutableListOf<LocalDateTime>()

        rule.setTellcatContent(fontScale) {
            ReminderSheet(
                reminderAt = TODAY.atTime(17, 30),
                defaultDay = TODAY,
                today = TODAY,
                onSet = { _, _ -> },
                onClear = {},
                onDismiss = {},
                now = NOW,
                canSnooze = canSnooze,
                onSnooze = { until -> snoozed += until }
            )
        }

        rule.waitUntilExactlyOneExists(hasText(SAVE), TIMEOUT_MILLIS)
        return snoozed
    }

    /** The pane's Snooze button, apart from its title, which reads the same. */
    private fun snoozeButton() = rule.onNode(hasText(SNOOZE_CONFIRM) and hasClickAction())

    @Test
    fun snoozeIsOfferedOnceTheReminderHasRung() {
        setSnoozeSheet()

        rule.onNodeWithText(SNOOZE_ENTRY).assertIsDisplayed()
    }

    @Test
    fun snoozeIsNotOfferedBeforeTheReminderHasRung() {
        setSnoozeSheet(canSnooze = false)

        rule.onAllNodesWithText(SNOOZE_ENTRY).assertCountEquals(0)
    }

    /** Six in the evening: "This evening" has gone, and is not offered. */
    private fun assertThePaneOffersWhatApplies(fontScale: Float) {
        setSnoozeSheet(fontScale)

        rule.onNodeWithText(SNOOZE_ENTRY).performClick()
        rule.waitUntilExactlyOneExists(hasText(TEN_MINUTES), TIMEOUT_MILLIS)

        rule.onNodeWithText(ONE_HOUR).assertExists()
        rule.onNode(hasText(TOMORROW_PRESET, substring = true) and hasClickAction()).assertExists()
        rule.onNodeWithText(PICK_A_TIME).assertExists()
        rule.onAllNodesWithText(THIS_EVENING).assertCountEquals(0)
        snoozeButton().assertExists()
    }

    @Test
    fun thePaneOffersWhatApplies_at100() = assertThePaneOffersWhatApplies(FontScale100)

    @Test
    fun thePaneOffersWhatApplies_at200() = assertThePaneOffersWhatApplies(FontScale200)

    @Test
    fun snoozeWaitsForAChoice() {
        setSnoozeSheet()
        rule.onNodeWithText(SNOOZE_ENTRY).performClick()
        rule.waitUntilExactlyOneExists(hasText(TEN_MINUTES), TIMEOUT_MILLIS)

        snoozeButton().assertIsNotEnabled()
        rule.onAllNodesWithText(SNOOZES_UNTIL, substring = true).assertCountEquals(0)
    }

    @Test
    fun aPresetSaysWhereItLandsAndSnoozeWritesIt() {
        val snoozed = setSnoozeSheet()
        rule.onNodeWithText(SNOOZE_ENTRY).performClick()
        rule.waitUntilExactlyOneExists(hasText(ONE_HOUR), TIMEOUT_MILLIS)

        rule.onNodeWithText(ONE_HOUR).performClick()

        rule.onNodeWithText(SNOOZES_UNTIL, substring = true).assertIsDisplayed()
        snoozeButton().assertIsEnabled().performClick()
        assertEquals(listOf(NOW.plusHours(1)), snoozed)
    }

    private companion object {

        const val SNOOZE_ENTRY = "Snooze…"
        const val SNOOZE_CONFIRM = "Snooze"
        const val TEN_MINUTES = "10 min"
        const val ONE_HOUR = "1 hour"
        const val THIS_EVENING = "This evening"
        const val TOMORROW_PRESET = "Tomorrow "
        const val PICK_A_TIME = "Pick a time…"
        const val SNOOZES_UNTIL = "Snoozes until"

        /** A Tuesday, matching `RemindersTest`. */
        val TODAY: LocalDate = LocalDate.of(2026, 9, 8)

        /**
         * Six in the evening, on the hour: the sheet suggests seven, and a nine
         * o'clock reminder handed in is behind it.
         */
        val NOW: LocalDateTime = TODAY.atTime(18, 0)

        const val TIMEOUT_MILLIS = 5_000L

        const val DAY = "Day"
        const val TIME = "Time"
        const val SAVE = "Save reminder"
        const val ALARM = "Ring like an alarm"
        val ALARM_PROMISES = listOf(
            "Rings full screen until you stop or snooze it, even on Silent.",
            "Rings as a banner until you answer. Full screen needs permission.",
            "Alarms are off for Tellcat, so this arrives as a normal reminder.",
            "Alarms & reminders is off for Tellcat, so this arrives as a normal reminder, and may be late."
        )
        const val PRIMING_TITLE = "Ring full screen"
        const val PRIMING_ALLOW = "Allow full screen"
        const val PRIMING_PICTURE = "The alarm screen, with the reminder time, the task, Snooze 10 min and Stop"
        const val CLEAR = "Clear"
        const val PAST = "That moment has already passed"

        /**
         * The row's value and the preset's label read the same word, and never
         * at the same time: the day pane replaces the rows rather than sitting
         * beside them, so a lookup finds one or the other.
         */
        const val TODAY_LABEL = "Today"
        const val TOMORROW = "Tomorrow"
        const val TOMORROW_LABEL = "Tomorrow"
    }
}
