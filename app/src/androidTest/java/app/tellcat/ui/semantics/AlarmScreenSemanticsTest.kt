package app.tellcat.ui.semantics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.ui.alarm.AlarmScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * The alarm screen, `docs/decisions.md` D-102 as amended by D-130, against what
 * the competitor study saw go wrong.
 *
 * The time shown is the reminder's (Todoist showed the clock's), and nothing
 * completes the task. Stop is a slide that acts only past the threshold; a tap
 * on it does nothing. The other answer is one plain tap button above it,
 * Snooze 10 min. The two never share a target or a gesture.
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

    private fun slide(): SemanticsNodeInteraction = rule.onNodeWithContentDescription(SLIDE)

    private fun button(label: String): SemanticsNodeInteraction =
        rule.onNode(hasText(label) and hasClickAction())

    /** From the handle at rest at the start, [fraction] of the way to the end. */
    private fun SemanticsNodeInteraction.slideBy(fraction: Float, durationMillis: Long = 400) =
        performTouchInput {
            val handle = Offset(height / 2f, centerY)
            swipe(
                start = handle,
                end = Offset(handle.x + (width - height) * fraction, centerY),
                durationMillis = durationMillis
            )
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

        button(SNOOZE).assertIsDisplayed()
        slide().assertIsDisplayed()
    }

    @Test
    fun theAlarmShowsTheTaskItsTimeTheButtonAndTheSlide_at100() = assertTheAlarmReads(FontScale100)

    @Test
    fun theAlarmShowsTheTaskItsTimeTheButtonAndTheSlide_at200() = assertTheAlarmReads(FontScale200)

    /** A half-awake "make it stop" must never complete the task. */
    @Test
    fun nothingOnTheAlarmCompletesTheTask() {
        show()

        rule.onAllNodesWithText("Done", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Complete", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    // --- The slide: always Stop ------------------------------------------------------

    @Test
    fun aTapOnTheSlideDoesNothing() {
        show()

        slide().performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(0, stopped)
        assertEquals(0, snoozed)
    }

    /** Released short of the threshold, the handle springs back and nothing happens. */
    @Test
    fun aShortSlideDoesNothing() {
        show()

        slide().slideBy(0.5f)
        rule.waitForIdle()

        assertEquals(0, stopped)
    }

    /** A quick flick short of the threshold stops no more than a slow one. */
    @Test
    fun aFastFlickShortOfTheThresholdDoesNothing() {
        show()

        slide().slideBy(0.5f, durationMillis = 40)
        rule.waitForIdle()

        assertEquals(0, stopped)
    }

    @Test
    fun slidingAllTheWayStopsIt() {
        show()

        slide().slideBy(1f)
        rule.waitForIdle()

        assertEquals(1, stopped)
        assertEquals(0, snoozed)
    }

    /** TalkBack: Stop as the slide's custom action, answered at once. */
    @Test
    fun talkBackStopsWithTheSlidesCustomAction() {
        show()

        val actions = slide().fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(STOP), actions.map { it.label })

        actions.single().action()
        assertEquals(1, stopped)
    }

    // --- The button: one plain tap --------------------------------------------------

    @Test
    fun theButtonSnoozesWithOneTap() {
        show()

        button(SNOOZE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(1, snoozed)
        assertEquals(0, stopped)
    }

    // --- The two never touch ---------------------------------------------------------

    private fun assertTheButtonClearsTheSlide(fontScale: Float) {
        show(fontScale)

        val buttonBounds = button(SNOOZE).getUnclippedBoundsInRoot()
        val slideBounds = slide().getUnclippedBoundsInRoot()
        assertEquals((slideBounds.right - slideBounds.left).value, (buttonBounds.right - buttonBounds.left).value, 0.5f)
        assertEquals(72.dp.value, (slideBounds.bottom - slideBounds.top).value, 0.5f)
        assertTrue((buttonBounds.bottom - buttonBounds.top) >= 72.dp)
        if (fontScale == FontScale100) {
            assertEquals((slideBounds.bottom - slideBounds.top).value, (buttonBounds.bottom - buttonBounds.top).value, 0.5f)
        }
        assertTrue(
            "The button and the slide are ${slideBounds.top - buttonBounds.bottom} apart, under 16dp",
            slideBounds.top - buttonBounds.bottom >= 16.dp
        )
    }

    @Test
    fun theButtonClearsTheSlideBy16dp_at100() = assertTheButtonClearsTheSlide(FontScale100)

    /** At 200% the button grows; the gap holds and the two still do not overlap. */
    @Test
    fun theButtonClearsTheSlideBy16dp_at200() = assertTheButtonClearsTheSlide(FontScale200)

    /** Dragging from the button into the track must not transfer to the slide. */
    @Test
    fun aDragStartingOnTheButtonStopsNothing() {
        show()

        button(SNOOZE).performTouchInput {
            swipe(start = center, end = Offset(width - 1f, height * 1.5f), durationMillis = 400)
        }
        rule.waitForIdle()

        assertEquals(0, stopped)
        assertEquals(0, snoozed)
    }

    /** A slide that begins on the handle never fires the button. */
    @Test
    fun aSlideNeverFiresTheButton() {
        show()

        slide().slideBy(1f)
        rule.waitForIdle()

        assertEquals(0, snoozed)
    }

    private companion object {
        const val TITLE = "Take the pills"
        const val NOTES = "With water, before breakfast."
        const val SNOOZE = "Snooze 10 min"
        const val STOP = "Stop"
        const val SLIDE = "Slide to stop"
        const val TIMEOUT_MILLIS = 5_000L

        /** A reminder at 5:30 PM, which is not the time any test runs at. */
        val RINGS_AT: LocalDateTime = LocalDateTime.of(2026, 10, 27, 17, 30)
    }
}
