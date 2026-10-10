package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocuslistPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "focuslist_preferences_test"

    @Before
    @After
    fun clear() {
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** D-077. A fresh install opens in the Catimo scheme, not the wallpaper's. */
    @Test
    fun dynamicColorIsOffOnAFreshInstall() {
        assertFalse(FocuslistPreferences(context, fileName).state.value.dynamicColor)
    }

    /** A user who turned it on keeps it on. */
    @Test
    fun turningItOnSurvivesCreatingANewStore() {
        FocuslistPreferences(context, fileName).setDynamicColor(true)

        assertTrue(FocuslistPreferences(context, fileName).state.value.dynamicColor)
    }
}
