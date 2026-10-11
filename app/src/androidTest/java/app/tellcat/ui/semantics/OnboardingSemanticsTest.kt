package app.tellcat.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.tellcat.ui.onboarding.OnboardingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun contentAndAction_areReadable_at200Percent() {
        var started = false

        rule.setTellcatContent(FontScale200) {
            OnboardingScreen(onGetStarted = { started = true })
        }

        rule.onNode(hasText("Welcome to Tellcat") and isHeading()).assertIsDisplayed()
        rule.onNodeWithText("If you write it down here, you will be told.").assertIsDisplayed()
        rule.onNodeWithText("Tellcat reminds you even when the app is closed.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Get started").assertIsDisplayed().performClick()
        assertTrue(started)
    }
}
