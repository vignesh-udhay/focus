package app.tellcat.core.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * One early reminder, before the reminder itself. `docs/decisions.md` D-115.
 *
 * Bills and appointments want a warning before the moment, not only at it. One,
 * never a list: the reminder sheet's "Also remind me" offers these three and
 * none, and the reminder is still the thing that matters. The early one is a
 * plain notification even when the reminder rings like an alarm (D-102), is
 * never followed up (D-108), and a snooze of it never moves the reminder.
 *
 * Pure, like the rest of this package: the clock comes in as a parameter.
 */
enum class EarlyReminder(val minutes: Long) {
    FifteenMinutes(15),
    OneHour(60),
    OneDay(24 * 60);

    /** How long before the reminder it rings. */
    val before: Duration
        get() = Duration.ofMinutes(minutes)

    companion object {

        /** The choice stored as [minutes], or null for none or for a value no build offered. */
        fun ofMinutes(minutes: Long?): EarlyReminder? = entries.firstOrNull { it.minutes == minutes }
    }
}

/** The early moment this task's reminder was set with, whatever has happened since. */
val Task.earlyReminderAt: LocalDateTime?
    get() {
        val early = earlyReminder ?: return null
        return reminderAt?.minus(early.before)
    }

/**
 * The moment the early reminder is owed, or null when none is.
 *
 * Owed only while the reminder itself is still to ring: not announced, not
 * snoozed, the task neither completed nor deleted. Once the reminder has spoken,
 * or been moved by a snooze, there is nothing left to warn about. A snooze of
 * the early one moves this and nothing else, and never to the reminder's moment
 * or after it.
 */
val Task.pendingEarlyReminder: LocalDateTime?
    get() {
        val main = reminderAt ?: return null
        if (earlyReminder == null || earlyDeliveredAt != null || snoozedUntil != null) return null
        if (!hasLiveReminder()) return null
        val at = earlySnoozedUntil ?: earlyReminderAt ?: return null
        return at.takeIf { it.isBefore(main) }
    }

/**
 * Every task whose early reminder should have an alarm at [now], soonest first.
 *
 * A missed one is still owed while its reminder is ahead, which is the phone
 * that was off at the early moment: told late, the warning still comes before
 * the moment it is about. Once the reminder's own moment has come, the
 * reminder is the one to tell, and the early one is not.
 */
fun earlyRemindersOwed(tasks: List<Task>, now: LocalDateTime): List<Task> =
    tasks.filter { task -> task.pendingEarlyReminder != null && task.reminderAt?.isAfter(now) == true }
        .sortedBy { task -> task.pendingEarlyReminder }

/**
 * Whether [early], for a reminder at [reminderAt], has already gone by at
 * [now], to the minute, as `nextReminderOccurrence` counts it. The editor says
 * so before saving, and the save does not set it.
 */
fun earlyHasPassed(reminderAt: LocalDateTime?, early: EarlyReminder?, now: LocalDateTime): Boolean {
    if (reminderAt == null || early == null) return false
    return reminderAt.minus(early.before).isBefore(now.truncatedTo(ChronoUnit.MINUTES))
}

/**
 * What a save writes for the early reminder's delivery: [nowInstant] when it
 * has already gone by, so this occurrence's is settled rather than told late,
 * and null when it is still ahead.
 *
 * The choice itself is kept either way. A repeating reminder's next occurrence
 * carries it (D-098), and an early moment that has passed for this one is
 * ahead for the next; a one-off is simply not told.
 */
fun earlyDeliveredAtSave(
    reminderAt: LocalDateTime?,
    early: EarlyReminder?,
    now: LocalDateTime,
    nowInstant: Instant
): Instant? = nowInstant.takeIf { earlyHasPassed(reminderAt, early, now) }

/**
 * The snoozes an early reminder can take: those that land before [reminderAt].
 * D-115. A snooze reaching the reminder's moment would ring after the thing it
 * warns about, so it is not offered; with none left, the notification offers
 * Done alone.
 */
fun earlySnoozeOptions(now: LocalDateTime, reminderAt: LocalDateTime): List<SnoozeOption> =
    availableSnoozeOptions(now).filter { option ->
        snoozedUntil(option, now)?.isBefore(reminderAt) == true
    }
