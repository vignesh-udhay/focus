package app.tellcat.ui.semantics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.domain.Recurrence
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Overdue's Move all to today, through the real screen. D-127.
 *
 * Only on Overdue, there with one task, gone when every overdue task would
 * stay, a 48dp target that says how many it moves, and a snackbar that counts.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OverdueMoveSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setToday(dao: FakeTaskDao) {
        val viewModel = testViewModel(dao)
        rule.setTellcatContent(FontScale100) {
            TodayScreen(viewModel = viewModel, onOpenTask = {})
        }
    }

    @Test
    fun oneOverdueTask_showsTheButton_sayingHowManyItMoves() {
        setToday(FakeTaskDao(listOf(testTask("1", TITLE, scheduledDate = TestToday.minusDays(1)))))

        rule.waitUntilExactlyOneExists(hasContentDescription(SPOKEN_ONE), TIMEOUT_MILLIS)
        rule.onNodeWithText(MOVE_ALL).assertIsDisplayed()
        rule.onNodeWithText(OVERDUE).assertIsDisplayed()
    }

    @Test
    fun theButton_isAFullTouchTarget() {
        setToday(FakeTaskDao(listOf(testTask("1", TITLE, scheduledDate = TestToday.minusDays(1)))))

        rule.waitUntilExactlyOneExists(hasContentDescription(SPOKEN_ONE), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(SPOKEN_ONE)
            .assertTouchHeightIsEqualTo(TellcatDimensions.TouchTargetMin)
    }

    @Test
    fun noOverdueTasks_noButton() {
        setToday(FakeTaskDao(listOf(testTask("1", TITLE, scheduledDate = TestToday))))

        rule.waitUntilExactlyOneExists(hasText(TITLE), TIMEOUT_MILLIS)
        rule.onNodeWithText(MOVE_ALL).assertDoesNotExist()
    }

    @Test
    fun everyOverdueTaskWouldStay_noButton() {
        val daily = Recurrence(RecurrenceUnit.DAILY)
        setToday(
            FakeTaskDao(
                listOf(
                    testTask("1", TITLE, scheduledDate = TestToday.minusDays(1), recurrence = daily),
                    testTask("2", TITLE, scheduledDate = TestToday, recurrence = daily)
                        .copy(spawnedFromId = "1")
                )
            )
        )

        rule.waitUntilExactlyOneExists(hasText(OVERDUE), TIMEOUT_MILLIS)
        rule.onNodeWithText(MOVE_ALL).assertDoesNotExist()
    }

    @Test
    fun tapping_movesTheTasks_andTheSnackbarCountsThem() {
        setToday(
            FakeTaskDao(
                listOf(
                    testTask("1", TITLE, scheduledDate = TestToday.minusDays(1)),
                    testTask("2", "Renew passport", scheduledDate = TestToday.minusDays(3))
                )
            )
        )

        rule.waitUntilExactlyOneExists(hasText(MOVE_ALL), TIMEOUT_MILLIS)
        rule.onNodeWithText(MOVE_ALL).performClick()

        rule.waitUntilExactlyOneExists(hasText(MOVED_TWO), TIMEOUT_MILLIS)
        rule.onNodeWithText(OVERDUE).assertDoesNotExist()
    }

    private companion object {
        const val TITLE = "Call the bank"
        const val OVERDUE = "Overdue"
        const val MOVE_ALL = "Move all to today"
        const val SPOKEN_ONE = "Move 1 overdue task to today"
        const val MOVED_TWO = "Moved 2 tasks to today"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
