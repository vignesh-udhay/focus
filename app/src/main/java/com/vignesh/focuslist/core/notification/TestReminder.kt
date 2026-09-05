package com.vignesh.focuslist.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import com.vignesh.focuslist.FocuslistApplication
import com.vignesh.focuslist.core.domain.DeliveryOutcome
import com.vignesh.focuslist.core.domain.ReminderDelivery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * A reminder the user can fire on purpose, to see whether this phone delivers.
 *
 * The one part of reminder health that is not an inference. Everything else on
 * that screen reads a permission or guesses from a manufacturer name; this
 * actually schedules an alarm, waits, and reports what happened. On a device
 * that quietly throttles background work it is the fastest way for someone to
 * find out, without setting a real reminder and hoping.
 *
 * It travels the same road as a real one: `AlarmManager`, a broadcast
 * receiver, a notification, and a row in the delivery record. A test that took
 * a shortcut past any of those would be testing the shortcut.
 *
 * Thirty seconds, as `reminder/Test Reminder — Clean Slate` promises. Long
 * enough that the user can lock the phone and put it down, short enough that
 * nobody abandons the test. It is deliberately too short to count as evidence
 * that the device behaves: half a minute cannot reach Doze, and clearing a
 * warning on it would be the app marking its own homework.
 */
object TestReminder {

    val Delay: Duration = Duration.ofSeconds(30)

    /** The id the delivery record files these under. */
    const val TaskId = "focuslist.test-reminder"

    /**
     * Schedules the test, exactly as a real reminder is scheduled.
     *
     * Exact where the system allows it and inexact where it does not, so the
     * test is subject to the same demotion a real reminder would be. A test
     * that asked for special treatment would report health the user does not
     * have.
     */
    fun schedule(context: Context) {
        val alarms = context.getSystemService<AlarmManager>() ?: run {
            Log.e(LogTag, "No AlarmManager. Test reminder cannot be scheduled.")
            return
        }

        val at = Instant.now().plus(Delay)
        val intent = pendingIntent(context, at)

        if (context.applicationContext.let { it as? FocuslistApplication }
                ?.reminderAlarms?.canScheduleExact() == true
        ) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    private fun pendingIntent(context: Context, at: Instant): PendingIntent {
        val intent = Intent(context, TestReminderReceiver::class.java).apply {
            data = Uri.parse("focuslist://test-reminder")
            putExtra(TestReminderReceiver.EXTRA_SCHEDULED_WALL, at.toEpochMilli())
            putExtra(
                TestReminderReceiver.EXTRA_SCHEDULED_ELAPSED,
                SystemClock.elapsedRealtime() + (at.toEpochMilli() - System.currentTimeMillis())
            )
        }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Posts the test, and writes down how it went.
 *
 * Recorded in the same table as every real delivery, because it is the same
 * measurement. Its futurity is thirty seconds, which is under
 * `EvidenceHorizon`, so it can never clear a warning about background work. It
 * can raise one: a test that arrives two minutes late is the device telling
 * the user something, and the health screen will say so.
 */
class TestReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val arrivedWallAt = Instant.now()
        val arrivedElapsedAt = SystemClock.elapsedRealtime()
        val scheduledWallAt = Instant.ofEpochMilli(
            intent.getLongExtra(EXTRA_SCHEDULED_WALL, arrivedWallAt.toEpochMilli())
        )
        val scheduledElapsedAt = intent.getLongExtra(EXTRA_SCHEDULED_ELAPSED, arrivedElapsedAt)

        val application = context.applicationContext as? FocuslistApplication ?: return
        val finish = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val posted = context.canPostNotifications()

                if (posted) {
                    context.ensureReminderChannel()
                    context.postTestReminder(arrivedWallAt)
                } else {
                    Log.w(LogTag, "Cannot post. Test reminder fired and said nothing.")
                }

                application.reminderDeliveryRepository.record(
                    ReminderDelivery(
                        id = UUID.randomUUID().toString(),
                        taskId = TestReminder.TaskId,
                        taskTitle = context.getString(
                            com.vignesh.focuslist.R.string.reminder_test_title
                        ),
                        dueAt = scheduledWallAt.atZone(ZoneId.systemDefault()).toLocalDateTime(),
                        scheduledWallAt = scheduledWallAt,
                        scheduledElapsedAt = scheduledElapsedAt,
                        arrivedWallAt = arrivedWallAt,
                        arrivedElapsedAt = arrivedElapsedAt,
                        scheduledAhead = TestReminder.Delay,
                        outcome = if (posted) {
                            DeliveryOutcome.Announced
                        } else {
                            DeliveryOutcome.Suppressed
                        }
                    )
                )
            } finally {
                finish.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULED_WALL = "focuslist.extra.testScheduledWall"
        const val EXTRA_SCHEDULED_ELAPSED = "focuslist.extra.testScheduledElapsed"
    }
}
