package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPreferencesTest {

    @Test
    fun completionSurvivesCreatingANewStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "onboarding_preferences_test"
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit()

        val first = OnboardingPreferences(context, fileName)
        assertFalse(first.completed.value)

        first.complete()

        val reopened = OnboardingPreferences(context, fileName)
        assertTrue(reopened.completed.value)

        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
