package com.vignesh.focuslist.ui.semantics

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.component.InboxMascot
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.TodayMascot
import com.vignesh.focuslist.ui.component.UpcomingMascot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared empty state's accessibility contract.
 *
 * An empty list has nothing for a screen reader to read except this, so both
 * lines have to survive to the semantics tree and stay on screen. At 200% the
 * two centred, wrapped strings are the thing most likely to overflow the
 * column they sit in.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStateSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertBothLinesAreReadable(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            TaskListEmptyState(headline = HEADLINE, supporting = SUPPORTING)
        }

        rule.onNodeWithText(HEADLINE).assertIsDisplayed()
        rule.onNodeWithText(SUPPORTING).assertIsDisplayed()
    }

    @Test
    fun bothLines_areReadable_at100() = assertBothLinesAreReadable(FontScale100)

    @Test
    fun bothLines_areReadable_at200() = assertBothLinesAreReadable(FontScale200)

    private fun assertHeadlineIsMarkedAsHeading(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            TaskListEmptyState(headline = HEADLINE, supporting = SUPPORTING)
        }

        // The same convention the app bar titles follow, so an empty screen
        // offers a landmark to jump to rather than two unmarked strings.
        rule.onNode(hasText(HEADLINE) and isHeading()).assertExists()

        // Only the headline. The supporting line is what the heading leads to,
        // not a second landmark competing with it.
        rule.onNode(hasText(SUPPORTING) and isHeading()).assertDoesNotExist()
    }

    @Test
    fun headline_isMarkedAsHeading_at100() = assertHeadlineIsMarkedAsHeading(FontScale100)

    @Test
    fun headline_isMarkedAsHeading_at200() = assertHeadlineIsMarkedAsHeading(FontScale200)

    /**
     * The illustrated variant keeps both lines on screen.
     *
     * A mascot spends height the two strings were previously free to wrap into,
     * and the column does not scroll, so at 200% this is where the headline
     * would be pushed off. Every pose is checked rather than one standing for
     * the rest: they are 102, 146 and 167dp tall, and it is Upcoming's sitting
     * dog that has the least room to spare.
     */
    private fun assertBothLinesSurviveTheIllustration(
        fontScale: Float,
        illustration: @Composable () -> Unit
    ) {
        rule.setFocuslistContent(fontScale) {
            TaskListEmptyState(
                headline = HEADLINE,
                supporting = SUPPORTING,
                illustration = illustration
            )
        }

        rule.onNodeWithText(HEADLINE).assertIsDisplayed()
        rule.onNodeWithText(SUPPORTING).assertIsDisplayed()
    }

    @Test
    fun today_bothLinesAreReadable_at100() =
        assertBothLinesSurviveTheIllustration(FontScale100) { TodayMascot() }

    @Test
    fun today_bothLinesAreReadable_at200() =
        assertBothLinesSurviveTheIllustration(FontScale200) { TodayMascot() }

    @Test
    fun inbox_bothLinesAreReadable_at100() =
        assertBothLinesSurviveTheIllustration(FontScale100) { InboxMascot() }

    @Test
    fun inbox_bothLinesAreReadable_at200() =
        assertBothLinesSurviveTheIllustration(FontScale200) { InboxMascot() }

    @Test
    fun upcoming_bothLinesAreReadable_at100() =
        assertBothLinesSurviveTheIllustration(FontScale100) { UpcomingMascot() }

    @Test
    fun upcoming_bothLinesAreReadable_at200() =
        assertBothLinesSurviveTheIllustration(FontScale200) { UpcomingMascot() }

    /**
     * And no mascot says anything.
     *
     * Each draws the fact its headline states, so describing one would put that
     * fact in TalkBack's path twice, ahead of the heading that is meant to be
     * the landmark. All three are rendered together, since one silent mascot
     * says nothing about the other two.
     */
    @Test
    fun illustrations_areNotAnnounced() {
        rule.setFocuslistContent(FontScale100) {
            Column {
                TaskListEmptyState(
                    headline = HEADLINE,
                    supporting = SUPPORTING,
                    illustration = { TodayMascot() }
                )
                InboxMascot()
                UpcomingMascot()
            }
        }

        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(0)
    }

    private companion object {
        const val HEADLINE = "Nothing scheduled for today"
        const val SUPPORTING = "Tasks without a day wait in your Inbox."
    }
}
