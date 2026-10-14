package app.tellcat.ui.semantics

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tellcat.core.design.ThemePreference
import app.tellcat.data.local.AppearancePreferences
import app.tellcat.ui.settings.SettingsScreen
import app.tellcat.ui.theme.TellcatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsSemanticsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Below Android 12 dynamic colour does not exist, so the switch is not drawn. */
    @Test
    fun theDynamicColorRowIsAbsentWhereTheSystemHasNone() {
        composeRule.setContent {
            TellcatTheme(dynamicColor = false) {
                SettingsScreen(
                    preferences = AppearancePreferences(),
                    onDynamicColorChange = {},
                    onThemeChange = {},
                    onOpenReminderHealth = {},
                    onOpenBackup = {},
                    onOpenPrivacyPolicy = {},
                    onBack = {},
                    dynamicColorAvailable = false
                )
            }
        }

        composeRule.onNodeWithText("Dynamic color").assertDoesNotExist()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun wholeToggleRowChangesDynamicColorAndThemeDialogCommitsOnSelection() {
        var dynamicColor: Boolean? = null
        var selectedTheme: ThemePreference? = null

        composeRule.setContent {
            TellcatTheme(dynamicColor = false) {
                SettingsScreen(
                    preferences = AppearancePreferences(),
                    onDynamicColorChange = { dynamicColor = it },
                    onThemeChange = { selectedTheme = it },
                    onOpenReminderHealth = {},
                    onOpenBackup = {},
                    onOpenPrivacyPolicy = {},
                    onBack = {}
                )
            }
        }

        // Off by default since D-077, so a tap on the row turns it on.
        composeRule.onNodeWithText("Dynamic color").performClick()
        assertEquals(true, dynamicColor)

        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Dark").assertIsDisplayed().performClick()
        assertEquals(ThemePreference.Dark, selectedTheme)
        composeRule.onNodeWithText("Choose theme").assertDoesNotExist()
    }

    /** D-083: Play wants the privacy policy reachable inside the app. */
    @Test
    fun thePrivacyPolicyRowSitsUnderDataAndOpensThePolicy() {
        var opened = false

        composeRule.setContent {
            TellcatTheme(dynamicColor = false) {
                SettingsScreen(
                    preferences = AppearancePreferences(),
                    onDynamicColorChange = {},
                    onThemeChange = {},
                    onOpenReminderHealth = {},
                    onOpenBackup = {},
                    onOpenPrivacyPolicy = { opened = true },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Privacy policy").performScrollTo()
        composeRule.onNodeWithText("tellcat.app/privacy").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy policy").performClick()
        assertEquals(true, opened)
    }
}

