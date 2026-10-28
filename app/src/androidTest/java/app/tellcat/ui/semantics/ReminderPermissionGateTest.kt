package app.tellcat.ui.semantics

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tellcat.ui.reminder.ReminderPermissionGate
import app.tellcat.ui.theme.TellcatTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exact-alarm screen closes itself when the grant is made outside it.
 *
 * Found on a OnePlus 8T on 2026-10-10: the grant was made in App info, and the
 * screen stayed up asking for a permission that was already granted, because it
 * only asked again when Settings came back through its own button.
 */
@RunWith(AndroidJUnit4::class)
class ReminderPermissionGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** So the notification dialog never comes first and hides what is tested. */
    @Before
    fun grantNotifications() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun aGrantMadeElsewhereClosesTheScreenWhenTheAppReturns() {
        var exactAllowed = false
        var grants = 0
        var dones = 0

        rule.setContent {
            TellcatTheme(dynamicColor = false) {
                ReminderPermissionGate(
                    isRequested = true,
                    canScheduleExact = { exactAllowed },
                    onPermissionGranted = { grants++ },
                    onDone = { dones++ }
                )
            }
        }
        rule.onNodeWithText(TITLE).assertIsDisplayed()

        // The user leaves, allows it in App info, and comes back.
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        exactAllowed = true
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        rule.onNodeWithText(TITLE).assertDoesNotExist()
        assertEquals("The alarms were not rebuilt exact.", 1, grants)
        assertEquals(1, dones)
    }

    @Test
    fun comingBackWithoutTheGrantKeepsAsking() {
        rule.setContent {
            TellcatTheme(dynamicColor = false) {
                ReminderPermissionGate(
                    isRequested = true,
                    canScheduleExact = { false },
                    onPermissionGranted = {},
                    onDone = {}
                )
            }
        }

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        rule.onNodeWithText(TITLE).assertIsDisplayed()
    }

    private companion object {
        const val TITLE = "Allow precise reminders"
    }
}
