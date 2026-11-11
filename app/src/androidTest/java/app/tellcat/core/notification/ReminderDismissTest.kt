package app.tellcat.core.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tellcat.TellcatApplication
import app.tellcat.core.domain.Task
import app.tellcat.ui.semantics.grantRuntimePermission
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * A swipe takes a reminder out of the shade for good. `docs/decisions.md` D-125.
 *
 * Android sends the notification's delete intent when the user swipes it away,
 * and `ReminderActionReceiver` clears `inShadeSince`, so a restart does not put
 * it back. Only a device can say the intent is on the notification Android
 * holds and reaches the receiver, so the intent is taken from the shade and sent
 * as the system would.
 *
 * It writes one task to the app's own database and removes it afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ReminderDismissTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val repository = (context.applicationContext as TellcatApplication).taskRepository

    private val id = "d125-dismiss-${UUID.randomUUID()}"
    private val setFor: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    private val shown: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    private val task = Task(
        id = id,
        title = "Swipe me away",
        createdAt = shown,
        reminderAt = setFor,
        reminderDeliveredAt = shown,
        inShadeSince = shown
    )

    @Before
    fun storeTheTaskAndOpenTheChannels() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        context.ensureReminderChannel()
        context.ensureFollowUpChannel()
        runBlocking { repository.insert(task) }
        // The write starts the app's own reconcile, which takes off the shade
        // every reminder whose task it did not read (D-099). One that read the
        // tasks a moment before this insert would take this test's reminder
        // off as soon as it is posted, so the pass is given time to finish.
        SystemClock.sleep(SettleMillis)
    }

    @After
    fun leaveNothingBehind() {
        context.cancelReminder(id)
        runBlocking { repository.delete(id) }
    }

    @Test
    fun swipingTheReminderClearsTheShade() {
        context.postReminder(task)

        deleteIntentOnScreen().send()

        await("the reminder to leave the shade") { inShadeSince() == null }
    }

    @Test
    fun swipingTheFollowUpClearsTheShade() {
        context.postFollowUp(task)

        deleteIntentOnScreen().send()

        await("the follow-up to leave the shade") { inShadeSince() == null }
    }

    /** Swiping the snooze choice away dismisses the reminder it stands for. */
    @Test
    fun swipingTheSnoozeChoiceClearsTheShade() {
        context.postSnoozeOptions(task, LocalDateTime.now())

        deleteIntentOnScreen().send()

        await("the snooze choice to leave the shade") { inShadeSince() == null }
    }

    /**
     * A notification for a moment the task has left, by a snooze or a new time,
     * says nothing about the one that stands now.
     */
    @Test
    fun aStaleSwipeLeavesTheShadeAlone() {
        context.postReminder(task.copy(reminderAt = setFor.minusHours(1)))

        deleteIntentOnScreen().send()

        // Nothing to wait for, so the receiver is given time to do the wrong thing.
        SystemClock.sleep(SettleMillis)
        assertEquals(shown, inShadeSince())
    }

    private fun deleteIntentOnScreen(): PendingIntent {
        await("the notification to reach the shade") {
            manager.activeNotifications.any { it.id == task.notificationId }
        }
        val intent = manager.activeNotifications.first { it.id == task.notificationId }.notification.deleteIntent
        assertNotNull("No delete intent on the notification", intent)
        return intent
    }

    private fun inShadeSince(): Instant? = runBlocking { repository.findTask(id)?.inShadeSince }

    private fun await(expected: String, reached: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TimeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (reached()) return
            Thread.sleep(PollMillis)
        }
        fail("Timed out waiting for $expected.")
    }

    private companion object {
        const val TimeoutMillis = 5_000L
        const val PollMillis = 50L
        const val SettleMillis = 1_000L
    }
}
