package app.tellcat

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.core.os.UserManagerCompat
import app.tellcat.core.notification.AlarmRingService
import app.tellcat.core.notification.RingingAlarm
import app.tellcat.ui.alarm.AlarmScreen
import app.tellcat.ui.theme.TellcatTheme
import java.time.LocalDate

/**
 * The alarm, full screen, over the lock screen. `docs/decisions.md` D-102.
 *
 * Beside [MainActivity] rather than in `ui/alarm`, for the reason that one is:
 * the notification in `core/notification` opens it, and nothing in `core`
 * imports `ui`. What it draws, [AlarmScreen], is in `ui/alarm`.
 *
 * Shown by the full-screen intent when the phone is locked or the screen is
 * off; Android shows the notification as a heads-up instead while the phone is
 * in use. It draws whatever [AlarmRingService] is ringing and closes when that
 * stops, so it can never show an alarm that is not ringing.
 *
 * **Leaving it does not stop the alarm.** Back does nothing here, and Home,
 * the power button or a tap outside only take the screen away: the service
 * keeps ringing and its notification keeps Stop and Snooze. Only those two
 * answer it.
 *
 * Always dark, whatever the app's theme. This is a screen that turns on in a
 * dark bedroom.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark bars to match the dark screen. The default follows the phone's
        // own theme, and on a light phone it drew dark icons on the dark alarm,
        // found on the owner's OnePlus.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TellcatTheme(darkTheme = true) {
                val alarm by AlarmRingService.nowRinging.collectAsState()

                // Nothing ringing, or it stopped: there is nothing to answer.
                LaunchedEffect(alarm) { if (alarm == null) finish() }

                // Back must not dismiss an alarm. Todoist's did, on one press.
                BackHandler(enabled = true) {}

                alarm?.let { ringing ->
                    AlarmScreen(
                        title = ringing.title,
                        notes = ringing.notes,
                        ringsAt = ringing.ringsAt,
                        today = LocalDate.now(),
                        onSnooze = { snooze(ringing) },
                        onStop = { send(ringing, AlarmRingService.ActionStop) }
                    )
                }
            }
        }
    }

    /**
     * Snoozing writes to the database, which before the first unlock after a
     * restart cannot be opened. There it asks for the unlock first, and snoozes
     * once it is given. After that first unlock, a locked phone can snooze.
     */
    private fun snooze(alarm: RingingAlarm) {
        if (UserManagerCompat.isUserUnlocked(this)) {
            send(alarm, AlarmRingService.ActionSnooze)
            return
        }

        getSystemService<KeyguardManager>()?.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    send(alarm, AlarmRingService.ActionSnooze)
                }
            }
        )
    }

    private fun send(alarm: RingingAlarm, action: String) {
        startService(AlarmRingService.answerIntent(this, alarm, action))
    }

    companion object {
        /** What the full-screen intent and a tap on the notification open. */
        fun intent(context: Context): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
