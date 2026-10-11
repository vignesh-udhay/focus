package app.tellcat.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TellcatPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "tellcat_preferences_test"

    @Before
    @After
    fun clear() {
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** D-077. A fresh install opens in the Tellcat scheme, not the wallpaper's. */
    @Test
    fun dynamicColorIsOffOnAFreshInstall() {
        assertFalse(TellcatPreferences(context, fileName).state.value.dynamicColor)
    }

    /** A user who turned it on keeps it on. */
    @Test
    fun turningItOnSurvivesCreatingANewStore() {
        TellcatPreferences(context, fileName).setDynamicColor(true)

        assertTrue(TellcatPreferences(context, fileName).state.value.dynamicColor)
    }
}
