package app.tellcat.core.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * The rules for a reminder that rings like an alarm. `docs/decisions.md` D-102.
 *
 * Pure, like the rest of this package: the device's answers come in as
 * parameters, so what the editor promises and what the receiver does are
 * decided by the same function and tested without a phone.
 */

/** How an alarm-style reminder will actually ring on this device, right now. */
enum class AlarmMode {

    /** Over the lock screen when the phone is locked or the screen is off. */
    FullScreen,

    /**
     * A heads-up that rings until answered. Full-screen alerts are not
     * allowed, so this is how it rings whether or not the phone is in use.
     */
    Banner,

    /**
     * As a normal reminder. The Alarms channel is switched off, and a
     * foreground service whose notification is hidden would ring with nothing
     * on screen to stop it.
     */
    PlainReminder
}

/**
 * Which [AlarmMode] applies. The editor says it in one line before the user
 * relies on it, and the receiver rings by the same answer.
 */
fun alarmMode(alarmChannelOn: Boolean, fullScreenAllowed: Boolean): AlarmMode = when {
    !alarmChannelOn -> AlarmMode.PlainReminder
    fullScreenAllowed -> AlarmMode.FullScreen
    else -> AlarmMode.Banner
}

/**
 * How long an alarm rings unanswered before it stops and leaves the normal
 * notification, as Stop does.
 *
 * Ten minutes, Android's own Clock default for "Silence after": long enough to
 * wake someone, short enough that a phone left at home does not ring for an
 * hour.
 */
val AlarmRingLimit: Duration = Duration.ofMinutes(10)

/** The alarm's one snooze, R-002. The notification keeps its three. */
val AlarmSnooze: SnoozeOption = SnoozeOption.TenMinutes

/**
 * Whether an alarm ringing for [ringingFor] still belongs to this task.
 *
 * False once the task is completed, deleted, no longer an alarm, or due at
 * another moment, which is what a snooze from the notification or an edit to
 * the time does. Every reconciliation asks this and stops an alarm that has
 * become false, so Done from the notification, a snooze from either path and
 * deleting the task all stop a ringing alarm through one rule (R-002, D-099).
 */
fun Task?.stillRingsFor(ringingFor: LocalDateTime): Boolean =
    this != null &&
        !isCompleted &&
        !isDeleted &&
        ringsLikeAlarm &&
        ringsAt == ringingFor

/**
 * Whether an action from a notification posted for [postedFor] still applies
 * to [task]. R-002.
 *
 * Every reminder and alarm action carries the moment its notification was for.
 * One that no longer matches, because the task was snoozed, moved, completed
 * or deleted since, is ignored, so an old tap cannot snooze a new time or
 * complete a task a second time. Null is a notification posted before this
 * rule existed, and is taken as current rather than refused.
 */
fun isCurrentAction(task: Task?, postedFor: LocalDateTime?): Boolean =
    task != null &&
        !task.isCompleted &&
        !task.isDeleted &&
        (postedFor == null || task.ringsAt == postedFor)

/**
 * Whether any outstanding reminder rings like an alarm. Reminder health lists
 * its alarm check only then (R-014).
 */
fun hasAlarmStyleReminders(tasks: List<Task>): Boolean =
    tasks.any { task ->
        task.ringsLikeAlarm && task.reminderAt != null && !task.isCompleted && !task.isDeleted
    }
