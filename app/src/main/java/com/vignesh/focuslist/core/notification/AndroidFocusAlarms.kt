package com.vignesh.focuslist.core.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.vignesh.focuslist.MainActivity
import com.vignesh.focuslist.R
import java.time.Instant

/**
 * The real alarm, from the device.
 *
 * Scheduled with [AlarmManager] rather than held in a coroutine, because the
 * point of it is the case where the app is not running: a session is minutes
 * or an hour long, the user is in another app, and the process may well be
 * killed before the estimate is reached. A `delay` would die with it.
 *
 * **Inexact on purpose.** `setExactAndAllowWhileIdle` needs
 * `SCHEDULE_EXACT_ALARM`, which is user-grantable, increasingly restricted, and
 * meant for alarm clocks and calendars. A few minutes of drift on a
 * forty-five-minute estimate is honest, and it matches the deliberate
 * imprecision of the shape this notification is the audible counterpart to. An
 * estimate is a guess; announcing it to the second would claim a precision the
 * number never had.
 *
 * `setAndAllowWhileIdle` rather than `set`, so Doze cannot hold the
 * announcement until the user next picks up the phone, which is exactly when
 * they no longer need telling.
 */
class AndroidFocusAlarms(private val context: Context) : FocusAlarms {

    private val alarms = context.getSystemService<AlarmManager>()

    override fun scheduleEstimateReached(taskTitle: String, at: Instant) {
        val alarms = alarms ?: return

        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilli(),
            pendingIntent(taskTitle)
        )
    }

    override fun cancel() {
        alarms?.cancel(pendingIntent(null))
    }

    /**
     * One request code, so scheduling replaces rather than accumulates and
     * cancelling reaches whatever is outstanding.
     *
     * `FLAG_UPDATE_CURRENT` is what makes the title of a re-scheduled alarm the
     * new one. Cancelling passes no title because matching ignores extras.
     */
    private fun pendingIntent(taskTitle: String?): PendingIntent {
        val intent = Intent(context, FocusEstimateReceiver::class.java).apply {
            taskTitle?.let { putExtra(FocusEstimateReceiver.EXTRA_TASK_TITLE, it) }
        }

        return PendingIntent.getBroadcast(
            context,
            EstimateRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val EstimateRequestCode = 1
    }
}

/**
 * Posts the notification when a session's estimate is reached.
 *
 * Says nothing if the session is on screen, and nothing if the user has not
 * granted notifications. Neither is an error: the first means they can already
 * see it, and the second means they said no.
 */
class FocusEstimateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (FocusSessionVisibility.isSessionOnScreen) return
        if (!context.canPostNotifications()) return

        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: return

        context.ensureFocusChannel()

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, FocusChannelId)
            .setSmallIcon(R.drawable.ic_focus)
            .setContentTitle(context.getString(R.string.focus_estimate_reached_title))
            .setContentText(title)
            .setContentIntent(open)
            // Tapping it takes the user back to the session, which is the only
            // thing they would want from it.
            .setAutoCancel(true)
            // Not ongoing. The estimate being reached is a moment, not a state,
            // and a notification that could not be swiped away would be the app
            // refusing to be dismissed.
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(EstimateNotificationId, notification)
    }

    companion object {
        const val EXTRA_TASK_TITLE = "focuslist.extra.taskTitle"
    }
}

/**
 * The one channel.
 *
 * Created on demand rather than at startup, so a user who never runs a session
 * with an estimate never grows a channel they would then have to see in
 * settings.
 *
 * Default importance, which alerts. `PRODUCT.md` asks for calm over
 * gamification, and a silent notification would certainly be calmer, but it
 * would also be invisible to the person it exists for: someone who put the
 * phone down. A notification nobody notices is worse than no notification,
 * because it costs the permission and delivers nothing. Calm here means one
 * notification, at a moment the user asked for by setting an estimate, and
 * never again until they start another session.
 */
internal fun Context.ensureFocusChannel() {
    val manager = getSystemService<NotificationManager>() ?: return

    val channel = NotificationChannel(
        FocusChannelId,
        getString(R.string.focus_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = getString(R.string.focus_channel_description)
    }

    manager.createNotificationChannel(channel)
}

/**
 * Whether the app may post at all.
 *
 * `POST_NOTIFICATIONS` is a runtime permission from API 33. Below that the
 * grant does not exist and posting is always allowed.
 */
internal fun Context.canPostNotifications(): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

internal const val FocusChannelId = "focus.estimate"

private const val EstimateNotificationId = 1
