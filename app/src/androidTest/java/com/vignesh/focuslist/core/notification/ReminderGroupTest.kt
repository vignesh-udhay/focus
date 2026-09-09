package com.vignesh.focuslist.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vignesh.focuslist.core.domain.Task
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime

/**
 * What the shade actually holds, per `docs/decisions.md` D-039.
 *
 * None of this can be asserted off a device. The summary is decided by counting
 * what [NotificationManager] currently reports, so a test that mocked the
 * manager would be asserting its own arithmetic rather than the thing that goes
 * wrong: whether a posted reminder really joins the group, whether the summary
 * appears at the threshold and leaves again beneath it, and whether the summary
 * is allowed to make a noise.
 *
 * That last one is why this file exists. Grouping's failure mode is not a
 * missing header, it is the alert quietly moving onto the summary or off the
 * children, on the one notification the product promises will interrupt.
 *
 * **Everything here waits rather than reads.** Posting and cancelling are both
 * handed to the notification service and processed on its handler, so the shade
 * reaches the asserted state shortly after the call rather than during it. This
 * is not test politeness: the same asynchrony is a real bug in the production
 * path, which is why [cancelReminder] tells the summary which reminder is on
 * its way out instead of counting and hoping.
 */
@RunWith(AndroidJUnit4::class)
class ReminderGroupTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /**
     * Permission granted through the instrumentation rather than through a
     * rule, because `GrantPermissionRule` lives in `androidx.test:rules` and
     * this is the only thing in the suite that would have needed it.
     *
     * The channel matters as much. [postReminder] does not create it; its two
     * real callers do, immediately before posting. Posting to a channel that
     * does not exist fails silently on API 26 and up, so without this line
     * every assertion below reads an empty shade and the ones expecting nothing
     * pass anyway. That is the trap D-026 names: check that a probe can fail.
     */
    @Before
    fun grantNotificationsAndClearTheShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        context.ensureReminderChannel()
        clearShade()
    }

    @After
    fun leaveNothingBehind() = clearShade()

    /**
     * A single reminder keeps its summary, and that is the safe end of a real
     * trade rather than an oversight.
     *
     * Cancelling a summary cancels its children, so removing it at one reminder
     * would take that reminder with it. The summary therefore stays posted, and
     * the shade holds two entries where the user has one thing to do.
     *
     * **What this cannot assert** is what the user sees. SystemUI flattens a
     * group holding a single child and does not draw the summary over it, but
     * that is a rendering decision and `activeNotifications` reports what was
     * posted, not what was drawn. The redundancy, if a skin ever draws it, is
     * cosmetic; the alternative deletes a reminder nobody dealt with.
     */
    @Test
    fun oneReminderKeepsItsSummaryRatherThanRiskingTheReminder() {
        postReminder("a", "Water the plants")

        await("the reminder, under a summary counting one") {
            reminders().size == 1 &&
                summaryTitle() == "1 reminder"
        }
    }

    @Test
    fun twoRemindersSummonASummaryThatCountsThem() {
        postReminder("a", "Water the plants")
        postReminder("b", "Call the dentist")

        // The count alone. The app name is the system's to draw, which is why
        // the board's "Focuslist · 3 reminders" is not this string.
        await("a summary counting both") {
            reminders().size == 2 && summaryTitle() == "2 reminders"
        }
    }

    @Test
    fun theRemindersAndTheSummaryShareOneGroup() {
        postReminder("a", "Water the plants")
        postReminder("b", "Call the dentist")
        await("two reminders under a summary") { reminders().size == 2 && summary() != null }

        val tag = summary()!!.notification.group
        assertNotNull("The summary carries no group", tag)
        assertTrue(
            "A reminder was posted outside the group it is summarised by",
            reminders().all { it.notification.group == tag }
        )
    }

    /**
     * The summary must not alert. `GROUP_ALERT_CHILDREN` keeps the sound on the
     * reminders themselves, and getting it backwards is the one way grouping
     * could leave a reminder quieter than it was before the group existed.
     */
    @Test
    fun theSummaryDefersItsAlertToTheReminders() {
        postReminder("a", "Water the plants")
        postReminder("b", "Call the dentist")
        await("two reminders under a summary") { reminders().size == 2 && summary() != null }

        assertEquals(
            Notification.GROUP_ALERT_CHILDREN,
            summary()!!.notification.groupAlertBehavior
        )

        // And the reminders kept theirs, which is the half that is easy to lose.
        reminders().forEach { reminder ->
            assertEquals(
                "A reminder stopped alerting once it joined the group",
                Notification.GROUP_ALERT_ALL,
                reminder.notification.groupAlertBehavior
            )
        }
    }

    /**
     * The regression this file was worth writing for.
     *
     * The first implementation cancelled the summary as soon as fewer than two
     * reminders remained. The notification service cancels a group's children
     * along with its summary, so dealing with one reminder silently removed the
     * other, and the user was never told about work they had not touched. It
     * fails on an empty shade rather than on a stale header, which is why the
     * assertion names the surviving reminder rather than counting.
     */
    @Test
    fun dealingWithOneOfTwoLeavesTheOtherOnScreen() {
        postReminder("a", "Water the plants")
        postReminder("b", "Call the dentist")
        await("two reminders under a summary") { reminders().size == 2 && summary() != null }

        context.cancelReminder("b")

        await("the untouched reminder, still on screen, now counted as one") {
            reminders().map { it.notification.extras.getString(Notification.EXTRA_TITLE) } ==
                listOf("Water the plants") &&
                summaryTitle() == "1 reminder"
        }
    }

    @Test
    fun theLastReminderLeavingTakesTheSummaryWithIt() {
        postReminder("a", "Water the plants")
        postReminder("b", "Call the dentist")
        await("two reminders under a summary") { reminders().size == 2 && summary() != null }

        context.cancelReminder("a")
        context.cancelReminder("b")

        await("an empty shade") { posted().isEmpty() }
    }

    /**
     * The test reminder is watched, not stacked. Outside the group it can
     * neither hide behind a summary nor inflate the count of real work.
     */
    @Test
    fun theTestReminderStaysOutOfTheGroup() {
        postReminder("a", "Water the plants")
        context.postTestReminder(Instant.now())

        await("a reminder, its summary, and a test reminder beside them") {
            posted().size == 3
        }

        val test = posted().single { it.id == TestReminder.TaskId.notificationId }
        assertNull("The test reminder joined the group", test.notification.group)

        // One reminder, not two. A test that counted towards the group would
        // make the summary claim work the user does not have.
        assertEquals("The test reminder was counted as a reminder", 1, reminders().size)
        assertEquals(
            "The test reminder reached the summary count",
            "1 reminder",
            summaryTitle()
        )
    }

    // --- driving the shade ------------------------------------------------------

    /**
     * Posts a reminder, waits for it to land, then pauses.
     *
     * The pause is not politeness and waiting alone did not fix it. Every
     * reminder now costs two enqueues, itself and its summary, and
     * `NotificationManagerService` sheds posts from a package that exceeds a
     * few per second. Back to back, the summary update was shed and the count
     * stayed a step behind the shade for the full timeout, which reads exactly
     * like a counting bug and is not one. Confirmed by isolating it: the same
     * assertion passes with this pause and fails without it.
     *
     * **This is a real constraint, not a test artifact.** Several reminders
     * falling due in the same minute produce the same burst in production, and
     * the shed post can be the summary or a reminder. The summary going stale
     * is cosmetic and corrects itself on the next post or cancel. A reminder
     * being shed is not, and grouping halves the burst that fits before
     * shedding starts. `docs/decisions.md` D-039 records it as the open cost.
     */
    private fun postReminder(id: String, title: String) {
        context.postReminder(task(id, title))
        await("$title on screen") { reminders().any { it.id == id.notificationId } }
        Thread.sleep(BurstPauseMillis)
    }

    // --- reading the shade ------------------------------------------------------

    private fun posted(): List<StatusBarNotification> = manager.activeNotifications.toList()

    /**
     * Everything the shade holds is something this test posted, since
     * [grantNotificationsAndClearTheShade] empties it first. So a reminder is
     * anything that is neither the summary nor the test reminder, and the group
     * tag itself never has to be duplicated from the source.
     */
    private fun reminders(): List<StatusBarNotification> = posted().filter {
        it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
            it.id != TestReminder.TaskId.notificationId
    }

    private fun summaryTitle(): String? =
        summary()?.notification?.extras?.getString(Notification.EXTRA_TITLE)

    private fun summary(): StatusBarNotification? = posted().firstOrNull {
        it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    /**
     * Waits for the shade to reach a state, and names the state it wanted when
     * it does not. Bounded, so a wrong shade fails the assertion rather than
     * hanging the run, which is the rule `TaskListViewModelTest` arrived at for
     * the same reason.
     */
    private fun await(expected: String, reached: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TimeoutMillis

        while (SystemClock.uptimeMillis() < deadline) {
            if (reached()) return
            Thread.sleep(PollMillis)
        }

        fail("Timed out waiting for $expected. The shade holds ${describeShade()}")
    }

    /**
     * Empties the shade and lets the enqueue rate window drain.
     *
     * The rate estimator is per package and lives in the notification service,
     * so it does not reset between tests in the way the shade does. Without the
     * pause a test inherits the previous one's burst and its first summary
     * update is shed: `twoRemindersSummonASummaryThatCountsThem` passed alone
     * and failed in class order, which is the signature of shared state
     * outside the process rather than of a flaky assertion.
     */
    private fun clearShade() {
        manager.cancelAll()
        await("an empty shade") { posted().isEmpty() }
        Thread.sleep(BurstPauseMillis)
    }

    private fun describeShade(): String = posted()
        .joinToString(prefix = "[", postfix = "]") { entry ->
            val summary = entry.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            "${entry.notification.extras.getString(Notification.EXTRA_TITLE)}" +
                if (summary) " (summary)" else ""
        }
        .ifEmpty { "[]" }

    private fun task(id: String, title: String) = Task(
        id = id,
        title = title,
        createdAt = Instant.now(),
        reminderAt = LocalDateTime.now()
    )

    private companion object {
        const val TimeoutMillis = 5_000L
        const val PollMillis = 25L

        /** Comfortably outside the notification service's enqueue rate window. */
        const val BurstPauseMillis = 400L
    }
}
