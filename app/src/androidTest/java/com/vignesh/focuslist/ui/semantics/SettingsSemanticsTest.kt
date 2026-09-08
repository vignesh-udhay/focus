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

        composeRule.onNodeWithText("Dynamic color").performClick()
        assertEquals(false, dynamicColor)

        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Dark").assertIsDisplayed().performClick()
        assertEquals(ThemePreference.Dark, selectedTheme)
        composeRule.onNodeWithText("Choose theme").assertDoesNotExist()
    }
}

