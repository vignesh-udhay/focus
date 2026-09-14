package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.vignesh.focuslist.ui.onboarding.OnboardingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun contentAndAction_areReadable_at200Percent() {
        var started = false

        rule.setFocuslistContent(FontScale200) {
            OnboardingScreen(onGetStarted = { started = true })
        }

        rule.onNode(hasText("Welcome to Catimo") and isHeading()).assertIsDisplayed()
        rule.onNodeWithText("If you write it down here, you will be told.").assertIsDisplayed()
        rule.onNodeWithText("Catimo reminds you even when the app is closed.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Get started").assertIsDisplayed().performClick()
        assertTrue(started)
    }
}
