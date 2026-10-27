package app.tellcat.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tellcat.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import app.tellcat.ui.semantics.grantRuntimePermission
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * The move to a vibrating reminder channel, `docs/decisions.md` D-100, against
 * the real notification service.
 *
 * **Every test uses channel ids of its own.** Android brings a deleted channel
 * back with all its old settings when its id is created again. A test that
 * deleted the real reminder channel after setting it to None would leave every
 * later reminder on this device silenced, the app's and `ReminderGroupTest`'s
 * alike. So the real ids are never touched here.
 */
@RunWith(AndroidJUnit4::class)
class ReminderChannelTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    private val run = UUID.randomUUID().toString().take(8)
    private val firstId = "test.first.$run"
    private val currentId = "test.current.$run"

    @Before
    fun grantNotifications() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun leaveNothingBehind() {
        manager.cancel(PostedId)
        manager.deleteNotificationChannel(firstId)
        manager.deleteNotificationChannel(currentId)
    }

    private fun ensure() = manager.ensureReminderChannel(
        name = context.getString(R.string.reminder_channel_name),
        description = context.getString(R.string.reminder_channel_description),
        currentId = currentId,
        firstId = firstId
    )

    /** A first channel as 1.0 made it, at [importance]. */
    private fun first(importance: Int = NotificationManager.IMPORTANCE_HIGH) {
        manager.createNotificationChannel(NotificationChannel(firstId, "Reminders", importance))
    }

    @Test
    fun aNewInstallGetsAVibratingChannelAtHighImportance() {
        ensure()

        val channel = manager.getNotificationChannel(currentId)
        assertNotNull(channel)
        assertTrue("The reminder channel does not vibrate", channel.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun theFirstChannelIsCarriedAcrossVibratingAndThenDeleted() {
        first()

        ensure()

        assertTrue(manager.getNotificationChannel(currentId).shouldVibrate())
        assertNull("The first channel is still there", manager.getNotificationChannel(firstId))
    }

    @Test
    fun aFirstChannelSetToNoneStaysSilenced() {
        first(NotificationManager.IMPORTANCE_NONE)

        ensure()

        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(currentId).importance)
    }

    @Test
    fun aQuieterFirstChannelKeepsItsImportance() {
        first(NotificationManager.IMPORTANCE_LOW)

        ensure()

        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(currentId).importance)
    }

    @Test
    fun theFirstChannelStaysWhileAReminderIsOnScreenFromIt() {
        first()
        manager.notify(
            PostedId,
            NotificationCompat.Builder(context, firstId)
                .setSmallIcon(R.drawable.ic_notification_cat)
                .setContentTitle("Water the plants")
                .build()
        )
        await("the reminder on screen") { manager.activeNotifications.any { it.id == PostedId } }

        ensure()

        assertNotNull("Deleting the channel took a reminder off screen", manager.getNotificationChannel(firstId))
        assertTrue(manager.activeNotifications.any { it.id == PostedId })

        manager.cancel(PostedId)
        await("an empty shade") { manager.activeNotifications.none { it.id == PostedId } }
        ensure()

        assertNull(manager.getNotificationChannel(firstId))
    }

    @Test
    fun runningAgainKeepsWhatTheUserChose() {
        first(NotificationManager.IMPORTANCE_LOW)
        ensure()

        ensure()

        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(currentId).importance)
    }

    /** What Reminder health reads, D-078: the first channel until the move, then the current one. */
    @Test
    fun theChannelInUseIsTheFirstUntilTheMoveAndTheCurrentAfter() {
        first(NotificationManager.IMPORTANCE_NONE)

        assertEquals(firstId, manager.reminderChannelInUse(currentId, firstId)?.id)

        ensure()

        assertEquals(currentId, manager.reminderChannelInUse(currentId, firstId)?.id)
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            manager.reminderChannelInUse(currentId, firstId)?.importance
        )
    }

    private fun await(expected: String, reached: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (reached()) return
            Thread.sleep(25)
        }
        fail("Timed out waiting for $expected")
    }

    private companion object {
        const val PostedId = 0x7e57
    }
}
