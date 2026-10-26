package app.tellcat.ui.semantics

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.ui.alarm.AlarmScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * The alarm screen, `docs/decisions.md` D-102, against what the competitor
 * study saw go wrong.
 *
 * The time shown is the reminder's (Todoist showed the clock's), the answers
 * are Stop and Snooze and nothing that completes the task (R-020), and a tap
 * answers neither (Todoist's and To Do Reminder's did). TalkBack's double tap
 * still answers, because that gesture is already deliberate.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AlarmScreenSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private var snoozed = 0
    private var stopped = 0

    private fun show(fontScale: Float = FontScale100) {
        rule.setTellcatContent(fontScale) {
            AlarmScreen(
                title = TITLE,
                notes = NOTES,
                ringsAt = RINGS_AT,
                today = RINGS_AT.toLocalDate(),
                onSnooze = { snoozed++ },
                onStop = { stopped++ }
            )
        }
        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
    }

    private fun assertTheAlarmReads(fontScale: Float) {
        show(fontScale)

        rule.onNodeWithText(TITLE).assertIsDisplayed()
        rule.onNodeWithText(NOTES).assertIsDisplayed()
        // The reminder's 5:30, never the clock. Formatted by the device's own
        // 12 or 24 hour setting, so either reading of 17:30 is accepted.
        val time = rule.onAllNodesWithText("5:30", substring = true).fetchSemanticsNodes().size +
            rule.onAllNodesWithText("17:30", substring = true).fetchSemanticsNodes().size
        assertEquals("The alarm does not show the reminder's time", 1, time)

        rule.onNodeWithContentDescription(SNOOZE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithContentDescription(STOP)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun theAlarmShowsTheTaskItsTimeAndTwoAnswers_at100() = assertTheAlarmReads(FontScale100)

    @Test
    fun theAlarmShowsTheTaskItsTimeAndTwoAnswers_at200() = assertTheAlarmReads(FontScale200)

    /** R-020: a half-awake "make it stop" must never complete the task. */
    @Test
    fun nothingOnTheAlarmCompletesTheTask() {
        show()

        rule.onAllNodesWithText("Done", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Complete", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun aTapAnswersNeither() {
        show()

        rule.onNodeWithContentDescription(SNOOZE).performTouchInput { click() }
        rule.onNodeWithContentDescription(STOP).performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(0, snoozed)
        assertEquals(0, stopped)
    }

    @Test
    fun holdingStopStopsIt() {
        show()

        // Down, then the clock run past the hold, then up: the hold is timed
        // by the composition's clock, not by the gesture's timestamps.
        rule.onNodeWithContentDescription(STOP).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_LONGER_THAN_MILLIS)
        rule.onNodeWithContentDescription(STOP).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals(1, stopped)
        assertEquals(0, snoozed)
    }

    /** TalkBack's double tap is already deliberate, so it answers at once. */
    @Test
    fun talkBackAnswersWithItsOwnDoubleTap() {
        show()

        rule.onNodeWithContentDescription(SNOOZE).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, snoozed)
        assertEquals(0, stopped)
    }

    @Test
    fun aPlainClickActionIsTheOnlyNonHoldPath() {
        show()

        // performClick sends a tap, which the hold ignores; only the semantics
        // action above answers without a hold.
        rule.onNodeWithContentDescription(STOP).performClick()
        rule.waitForIdle()

        assertEquals(0, stopped)
    }

    private companion object {
        const val TITLE = "Take the pills"
        const val NOTES = "With water, before breakfast."
        const val SNOOZE = "Snooze 10 min"
        const val STOP = "Stop"
        const val TIMEOUT_MILLIS = 5_000L
        const val HOLD_LONGER_THAN_MILLIS = 1_200L

        /** A reminder at 5:30 PM, which is not the time any test runs at. */
        val RINGS_AT: LocalDateTime = LocalDateTime.of(2026, 10, 27, 17, 30)
    }
}
