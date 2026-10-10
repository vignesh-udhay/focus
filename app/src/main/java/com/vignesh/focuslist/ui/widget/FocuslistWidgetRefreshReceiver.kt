package com.vignesh.focuslist.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import androidx.glance.appwidget.updateAll
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Refreshes Today when the calendar or the meaning of wall-clock time moves. */
class FocuslistWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RefreshActions) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                FocuslistWidget().updateAll(context)
            } catch (error: Exception) {
                Log.e(LogTag, "The widget could not refresh.", error)
            } finally {
                // Every one of these moves when midnight is, or is midnight.
                armWidgetMidnight(context)
                pending.finish()
            }
        }
    }

    internal companion object {

        /** The widget's own midnight alarm. D-079. */
        const val ActionMidnight = "com.vignesh.focuslist.action.WIDGET_MIDNIGHT"

        val RefreshActions = setOf(
            ActionMidnight,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}

/**
 * Asks to be woken just after the next local midnight, so the widget turns over
 * to the new day. `docs/decisions.md` D-079.
 *
 * `DATE_CHANGED` was the old trigger, and it never arrived with the app closed:
 * it is not on Android's implicit-broadcast exception list, so a manifest
 * receiver does not get it, and the widget showed yesterday until a task
 * changed.
 *
 * Not a wakeup alarm, and not exact. A widget nobody is looking at can wait for
 * the screen to come on, which is when an `RTC` alarm is delivered, and that
 * costs the battery nothing. A window rather than plain `set`, which Android
 * stretched to an hour on the emulator, so the widget read yesterday until one
 * in the morning; ten minutes is the narrowest window it grants without the
 * exact-alarm permission. A few seconds past midnight, so the day has turned by
 * the time it is read.
 */
internal fun armWidgetMidnight(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
    val alarms = context.getSystemService<AlarmManager>() ?: return
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant()

    val intent = Intent(context, FocuslistWidgetRefreshReceiver::class.java)
        .setAction(FocuslistWidgetRefreshReceiver.ActionMidnight)
    val pending = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    alarms.setWindow(
        AlarmManager.RTC,
        midnight.toEpochMilli() + MidnightSlackMillis,
        MidnightWindowMillis,
        pending
    )
}

private const val MidnightSlackMillis = 5_000L

private const val MidnightWindowMillis = 10 * 60_000L

private const val LogTag = "FocuslistWidget"
