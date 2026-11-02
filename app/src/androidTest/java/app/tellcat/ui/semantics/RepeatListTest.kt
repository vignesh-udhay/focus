package app.tellcat.ui.semantics

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.core.domain.MonthlyWeekday
import app.tellcat.core.domain.Recurrence
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.ui.task.RepeatSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The Repeat sheet's common rules, one tap each. `docs/decisions.md` D-121.
 * Opened on the 27th of November 2026, a Friday that is both the fourth and the
 * last of the month.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RepeatListTest {

    @get:Rule
    val rule = createComposeRule()

    private val lastFriday: LocalDate = LocalDate.of(2026, 11, 27)
    private val picks = mutableListOf<Recurrence?>()

    private fun sheet(selected: Recurrence? = null, fontScale: Float = FontScale100) {
        rule.setTellcatContent(fontScale) {
            RepeatSheet(
                selected = selected,
                today = lastFriday.minusDays(25),
                anchorDay = lastFriday,
                onPick = { picks += it },
                onDismiss = {}
            )
        }
        rule.waitUntilExactlyOneExists(hasText(DAILY), TIMEOUT_MILLIS)
    }

    private fun assertTheRulesAreReadFromTheDay(fontScale: Float) {
        sheet(fontScale = fontScale)

        for (label in listOf(
            DOES_NOT_REPEAT, DAILY, "Weekly on Friday", "Monthly on day 27",
            "Monthly on the fourth Friday", "Monthly on the last Friday", "Every weekday", CUSTOM
        )) {
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithText("Yearly on", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(DOES_NOT_REPEAT).assertIsSelected()
    }

    @Test
    fun theRulesAreReadFromTheDay_at100() = assertTheRulesAreReadFromTheDay(FontScale100)

    @Test
    fun theRulesAreReadFromTheDay_at200() = assertTheRulesAreReadFromTheDay(FontScale200)

    @Test
    fun oneTapSetsARule() {
        sheet()
        rule.onNodeWithText("Monthly on the last Friday").performScrollTo().performClick()

        assertEquals(
            listOf(Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(MonthlyWeekday.Last, DayOfWeek.FRIDAY))),
            picks
        )
    }

    @Test
    fun doesNotRepeatClearsTheRule() {
        sheet(selected = Recurrence(RecurrenceUnit.DAILY))
        rule.onNodeWithText(DAILY).assertIsSelected()

        rule.onNodeWithText(DOES_NOT_REPEAT).performClick()
        assertNull(picks.single())
    }

    /** Custom opens the rule as it was built before D-121, and back returns to the list. */
    @Test
    fun customOpensTheRuleAndBackReturns() {
        sheet()
        rule.onNodeWithText(CUSTOM).performScrollTo().performClick()

        rule.waitUntilExactlyOneExists(hasText(EVERY), TIMEOUT_MILLIS)
        rule.onNodeWithContentDescription(BACK_TO_LIST).performClick()

        rule.waitUntilExactlyOneExists(hasText(DAILY), TIMEOUT_MILLIS)
        assertEquals(emptyList<Recurrence?>(), picks)
    }

    /** A rule the list does not name is not hidden: Custom says it. */
    @Test
    fun aRuleTheListDoesNotNameShowsOnCustom() {
        sheet(selected = Recurrence(RecurrenceUnit.WEEKLY, interval = 2))
        rule.waitUntilExactlyOneExists(hasText(CUSTOM), TIMEOUT_MILLIS)
        rule.onNode(hasText("2 weeks", substring = true)).performScrollTo().assertIsDisplayed()
    }

    // --- D-123: the month's last day -------------------------------------------------------

    private fun sheetOn(anchor: LocalDate, selected: Recurrence? = null) {
        rule.setTellcatContent(FontScale100) {
            RepeatSheet(
                selected = selected,
                today = anchor,
                anchorDay = anchor,
                onPick = { picks += it },
                onDismiss = {}
            )
        }
        rule.waitUntilExactlyOneExists(hasText(DAILY), TIMEOUT_MILLIS)
    }

    /** On the 30th of November, which "on day 30" would not keep, the list offers it in one tap. */
    @Test
    fun onALastDayTheListOffersTheLastDay() {
        sheetOn(LocalDate.of(2026, 11, 30))
        rule.onNodeWithText(MONTHLY_LAST_DAY).performScrollTo().performClick()

        assertEquals(listOf(Recurrence(RecurrenceUnit.MONTHLY, monthlyLastDay = true)), picks)
    }

    /** Not on a 31st: "on day 31" already lands on every month's last day, D-122. */
    @Test
    fun onThe31stTheListDoesNotOfferIt() {
        sheetOn(LocalDate.of(2026, 12, 31))
        rule.onNodeWithText("Monthly on day 31").performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasText(MONTHLY_LAST_DAY)).fetchSemanticsNodes().size)
    }

    /**
     * Custom offers it under Month whatever the day, so "every month, last day"
     * is found without picking the date first. A radio choice for TalkBack.
     */
    @Test
    fun customUnderMonthAlwaysOffersTheLastDay() {
        sheetOn(LocalDate.of(2026, 11, 2), selected = Recurrence(RecurrenceUnit.MONTHLY))
        rule.onNodeWithText(CUSTOM).performScrollTo().performClick()
        rule.waitUntilExactlyOneExists(hasText(ON_THE_LAST_DAY), TIMEOUT_MILLIS)

        rule.onNodeWithText("On day 2").assertIsSelected()
        rule.onNodeWithText(ON_THE_LAST_DAY)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsNotSelected()
            .performClick()
        rule.onNodeWithText(ON_THE_LAST_DAY).assertIsSelected()
        rule.onNodeWithText("On day 2").assertIsNotSelected()

        rule.onNodeWithText(SAVE_REPEAT).performScrollTo().performClick()
        assertEquals(listOf(Recurrence(RecurrenceUnit.MONTHLY, monthlyLastDay = true)), picks)
    }

    private companion object {
        const val DOES_NOT_REPEAT = "Doesn't repeat"
        const val DAILY = "Daily"
        const val CUSTOM = "Custom"
        const val MONTHLY_LAST_DAY = "Monthly on the last day"
        const val ON_THE_LAST_DAY = "On the last day"
        const val SAVE_REPEAT = "Save repeat"
        const val EVERY = "Every"
        const val BACK_TO_LIST = "Back to the common repeats"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
