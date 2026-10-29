package app.tellcat.core.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * One follow-up for a reminder nobody answered. `docs/decisions.md` D-108.
 *
 * Pure, like the rest of this package: the clock comes in as a parameter, so
 * a reminder announced at 23:55 and followed up after midnight is a test here.
 */

/**
 * How long an announced reminder waits for an answer before it is posted once
 * more. Long enough to finish a sentence, short enough that the moment the
 * reminder was for has not gone.
 */
val FollowUpDelay: Duration = Duration.ofMinutes(10)

/**
 * When a reminder announced at [announcedAt] is posted once more.
 *
 * On the minute, [FollowUpDelay] after the minute it was announced in, so the
 * row can say "Reminds again at 9:40" and mean 9:40, not 9:40 and seventeen
 * seconds. The follow-up therefore comes up to a minute sooner than the full
 * delay, never later.
 */
fun followUpTime(announcedAt: LocalDateTime): LocalDateTime =
    announcedAt.truncatedTo(ChronoUnit.MINUTES).plus(FollowUpDelay)

/**
 * The moment this task's follow-up is owed, or null when none is.
 *
 * Pending only while the reminder stands announced and unanswered: completing
 * or deleting the task ends it, and so does anything that makes the reminder
 * owed again (a snooze or a new time clear [Task.reminderDeliveredAt]). Those
 * paths clear [Task.followUpAt] too; this is the second guard, so a row
 * restored or reopened with a stale value cannot follow anything up.
 */
val Task.pendingFollowUp: LocalDateTime?
    get() = followUpAt?.takeIf { !isCompleted && !isDeleted && reminderDeliveredAt != null }

/**
 * The day a notification line has to name for a reminder set for [at], or null
 * when it was today and the time alone says it. D-108, D-109.
 *
 * A follow-up posted after a phone was off overnight, or a snooze carried to
 * the next morning, would otherwise say "set for 9:30 PM" the next day with
 * nothing to say it was last night.
 */
fun dayToName(at: LocalDateTime, today: LocalDate): LocalDate? =
    at.toLocalDate().takeIf { day -> day != today }

/** Every task with a follow-up owed, soonest first. */
fun followUpsOwed(tasks: List<Task>): List<Task> =
    tasks.filter { task -> task.pendingFollowUp != null }
        .sortedBy { task -> task.pendingFollowUp }

/** Which of its states a reminder is in, for the row that shows it. */
enum class ReminderStage {

    /** Set for its time, or announced at it. */
    Set,

    /** Moved by a snooze, and not yet rung there. D-109. */
    Snoozed,

    /** Announced, unanswered, and to be posted once more. D-108. */
    FollowUp
}

/** The moment a row's reminder segment names, and what that moment is. */
data class RowReminder(val at: LocalDateTime, val stage: ReminderStage)

/**
 * What a row says about this task's reminder: when it will next speak up, and
 * why. Null for a task with no reminder.
 *
 * A follow-up wins, because it is the next thing the phone will do. A snooze
 * comes next while it is still to ring; once it has rung it is simply the time
 * the reminder spoke, as any other.
 */
fun Task.rowReminder(): RowReminder? {
    pendingFollowUp?.let { at -> return RowReminder(at, ReminderStage.FollowUp) }
    snoozedUntil?.takeIf { hasLiveReminder() }?.let { at ->
        return RowReminder(at, ReminderStage.Snoozed)
    }
    return ringsAt?.let { at -> RowReminder(at, ReminderStage.Set) }
}

/**
 * Whether this task's reminder has rung and still waits for an answer, or is
 * snoozed: the two states a snooze can be chosen from inside the app. D-109.
 */
val Task.canSnooze: Boolean
    get() = !isCompleted && !isDeleted && reminderAt != null &&
        (reminderDeliveredAt != null || snoozedUntil != null)
