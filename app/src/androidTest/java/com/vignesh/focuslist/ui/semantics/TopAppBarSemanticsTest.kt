package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared top app bar's accessibility contract.
 *
 * The title is the screen's name, so it is marked as a heading: that is what
 * lets a screen reader announce where the user has landed and lets them jump
 * to it by heading rather than swiping in from the first element.
 *
 * One test covers every screen because every screen draws this one component.
 * `ScreenChromeSemanticsTest` is what proves that claim.
 */
@RunWith(AndroidJUnit4::class)
class TopAppBarSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertTitleIsHeading(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            FocuslistTopAppBar(title = TITLE)
        }

        rule.onNode(hasText(TITLE) and isHeading()).assertExists()
    }

    @Test
    fun title_isMarkedAsHeading_at100() = assertTitleIsHeading(FontScale100)

    @Test
    fun title_isMarkedAsHeading_at200() = assertTitleIsHeading(FontScale200)

    private fun assertTitleIsVisible(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            FocuslistTopAppBar(title = TITLE)
        }

        // At 200% the title must still be on screen rather than clipped out of
        // the bar, which is the failure mode a semantics-only check misses.
        rule.onNodeWithText(TITLE).assertIsDisplayed()
    }

    @Test
    fun title_isDisplayed_at100() = assertTitleIsVisible(FontScale100)

    @Test
    fun title_isDisplayed_at200() = assertTitleIsVisible(FontScale200)

    private companion object {
        const val TITLE = "Today"
    }
}
