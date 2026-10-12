package app.tellcat

import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tellcat.core.design.ThemePreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The status bar's icons follow the theme chosen in Settings, not the phone's.
 *
 * Found while shooting the store screenshots: with Dark chosen on a phone in
 * light mode, the clock and battery were drawn dark on the dark app, close to
 * invisible. `enableEdgeToEdge()` with no arguments reads the system's mode.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarsFollowThemeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as TellcatApplication

    @Before
    fun aLightPhone() {
        shell("cmd uimode night no")
        app.onboarding.complete()
    }

    @After
    fun restore() {
        app.preferences.setTheme(ThemePreference.System)
    }

    @Test
    fun darkChosenOnALightPhoneGetsLightIcons() {
        app.preferences.setTheme(ThemePreference.Dark)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(false, lightStatusBarIcons(scenario, expected = false))

            // And back, without leaving the screen, as the Settings switch does.
            instrumentation.runOnMainSync { app.preferences.setTheme(ThemePreference.Light) }
            assertEquals(true, lightStatusBarIcons(scenario, expected = true))
        }
    }

    /**
     * True when the bar is told its background is light, so it draws dark icons.
     *
     * Read until it reaches [expected] or two seconds pass: the theme change
     * lands on Compose's next frame, which `waitForIdleSync` does not wait for.
     */
    private fun lightStatusBarIcons(
        scenario: ActivityScenario<MainActivity>,
        expected: Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + 2_000
        var light: Boolean
        do {
            instrumentation.waitForIdleSync()
            light = false
            scenario.onActivity { activity ->
                light = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars
            }
            if (light == expected) return light
            Thread.sleep(50)
        } while (System.currentTimeMillis() < deadline)
        return light
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }
}
