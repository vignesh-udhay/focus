package app.tellcat.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.os.UserManagerCompat
import app.tellcat.R
import app.tellcat.core.domain.AlarmMode
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.alarmMode
import app.tellcat.AlarmActivity
import java.time.LocalDateTime

/**
 * What a reminder that rings like an alarm looks like, and the switches it
 * depends on. `docs/decisions.md` D-102.
 *
 * The sound is not here. [AlarmRingService] plays it, because Android clears an
 * insistent notification's sound when the shade is opened, which is how
 * Tasks.org's alarm went quiet in the competitor study. This notification is
 * the service's: it carries Stop and Snooze, and the full-screen intent.
 */

/** One alarm, as much of its task as ringing it needs, locked phone included. */
data class RingingAlarm(
    val taskId: String,
    val title: String,
    val notes: String?,
    /** The moment the reminder was set for, which is what the alarm shows. */
    val ringsAt: LocalDateTime
)

/** The alarm for [task], or null when it does not ring like one. */
internal fun Task.asRingingAlarm(): RingingAlarm? {
    if (!ringsLikeAlarm) return null
    val at = ringsAt ?: return null
    return RingingAlarm(taskId = id, title = title, notes = notes, ringsAt = at)
}

/**
 * Rings [task] as an alarm, and says whether it did.
 *
 * False when it is not an alarm, when the Alarms channel is off, or when
 * Android refuses to start the service; the caller then posts the normal
 * reminder, so an alarm that cannot ring still arrives.
 */
internal fun Context.ringAsAlarm(task: Task): Boolean {
    val alarm = task.asRingingAlarm() ?: return false
    ensureAlarmChannel()
    if (currentAlarmMode() == AlarmMode.PlainReminder) return false
    return AlarmRingService.start(this, alarm)
}

/** How an alarm-style reminder rings on this device right now. D-102. */
internal fun Context.currentAlarmMode(): AlarmMode =
    alarmMode(alarmChannelOn = alarmChannelOn(), fullScreenAllowed = canUseFullScreen())

/**
 * Whether the Alarms channel can show anything.
 *
 * A channel not yet created counts as on, because it is about to be created at
 * high importance, as [canPostReminders] reasons for the reminder channel.
 */
internal fun Context.alarmChannelOn(): Boolean {
    if (!canPostNotifications()) return false
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false

    val channel = getSystemService<NotificationManager>()?.getNotificationChannel(AlarmChannelId)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

/**
 * Whether Android will show the alarm full screen when the phone is locked.
 *
 * Granted at install below Android 14. From 14 Play pre-grants it only to alarm
 * and calling apps, and Tellcat is neither, so the user grants it. D-102.
 */
internal fun Context.canUseFullScreen(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: false
    } else {
        true
    }

/**
 * The Alarms channel, separate from the reminder channel so the user can turn
 * one down without the other.
 *
 * High importance, so the notification is a heads-up while the phone is in use.
 * **No sound and no vibration of its own**: the service plays and vibrates, and
 * a channel sound would play over it. Public on the lock screen, because the
 * alarm is going to be on the lock screen full size anyway.
 */
internal fun Context.ensureAlarmChannel() {
    val manager = getSystemService<NotificationManager>() ?: return
    val existing = manager.getNotificationChannel(AlarmChannelId)

    manager.createNotificationChannel(
        NotificationChannel(
            AlarmChannelId,
            getString(R.string.alarm_channel_name),
            // Refreshing an existing channel keeps what the user set.
            existing?.importance ?: NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
    )
}

/**
 * The notification the ringing service runs under.
 *
 * Stop and Snooze 10 min, never Done (R-020): a half-awake "make it stop" must
 * not complete a task or start its next occurrence. The time is the reminder's,
 * not the clock's. The full-screen intent is set only where Android allows it;
 * without it the same notification is the heads-up that rings until answered.
 */
internal fun Context.alarmNotification(alarm: RingingAlarm): Notification {
    val screen = PendingIntent.getActivity(
        this,
        0,
        AlarmActivity.intent(this),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(this, AlarmChannelId)
        .setSmallIcon(R.drawable.ic_notification_cat)
        .setContentTitle(alarm.title)
        .setContentText(getString(R.string.alarm_notification_text, formatTime(alarm.ringsAt.toLocalTime())))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .setAutoCancel(false)
        .setContentIntent(screen)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(
            NotificationCompat.Action.Builder(
                0,
                getString(R.string.alarm_snooze),
                alarmAnswerIntent(alarm, AlarmRingService.ActionSnooze)
            )
                // Snoozing writes to the database, which cannot be opened before
                // the first unlock after a restart. D-079.
                .setAuthenticationRequired(!UserManagerCompat.isUserUnlocked(this))
                .build()
        )
        .addAction(0, getString(R.string.alarm_stop), alarmAnswerIntent(alarm, AlarmRingService.ActionStop))

    if (canUseFullScreen()) builder.setFullScreenIntent(screen, true)

    return builder.build()
}

/**
 * Stop or Snooze, sent to the ringing service.
 *
 * Carries the alarm it was posted for, so an answer to an alarm that has since
 * been replaced or stopped does nothing. A service intent, because the service
 * is what is ringing and what has to stop.
 */
private fun Context.alarmAnswerIntent(alarm: RingingAlarm, action: String): PendingIntent =
    PendingIntent.getService(
        this,
        0,
        AlarmRingService.answerIntent(this, alarm, action)
            // Distinct per action, since matching ignores extras.
            .setData(Uri.parse("tellcat://alarm/${alarm.taskId}/$action")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

internal const val AlarmChannelId = "tellcat.alarm"

/**
 * The ringing service's notification. Task reminders are keyed on hashed ids,
 * so this could in principle collide, on the odds `ReminderGroupSummaryId`
 * accepts: one in 2^32.
 */
internal const val AlarmNotificationId = Int.MIN_VALUE + 1
