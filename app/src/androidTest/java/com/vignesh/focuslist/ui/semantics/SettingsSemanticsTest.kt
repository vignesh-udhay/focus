package com.vignesh.focuslist.ui.semantics

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vignesh.focuslist.core.design.ThemePreference
import com.vignesh.focuslist.data.local.AppearancePreferences
import com.vignesh.focuslist.ui.settings.SettingsScreen
import com.vignesh.focuslist.ui.theme.FocuslistTheme
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
            FocuslistTheme(dynamicColor = false) {
                SettingsScreen(
                    preferences = AppearancePreferences(),
                    onDynamicColorChange = {},
                    onThemeChange = {},
                    onOpenReminderHealth = {},
                    onOpenBackup = {},
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
            FocuslistTheme(dynamicColor = false) {
                SettingsScreen(
                    preferences = AppearancePreferences(),
                    onDynamicColorChange = { dynamicColor = it },
                    onThemeChange = { selectedTheme = it },
                    onOpenReminderHealth = {},
                    onOpenBackup = {},
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
}

