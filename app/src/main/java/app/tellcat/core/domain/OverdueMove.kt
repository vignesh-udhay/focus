package app.tellcat.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Overdue's Move all to today: which tasks it moves and what each becomes.
 * `docs/decisions.md` D-127.
 */

/** The overdue tasks a move would take to today, and the ones it would leave. */
data class OverdueMovePlan(val move: List<Task>, val stay: List<Task>) {
    val isEmpty: Boolean get() = move.isEmpty()
}

/**
 * Splits Today's Overdue band into what Move all to today moves and what it
 * leaves.
 *
 * An occurrence whose next one is already on [today] or earlier stays, so a
 * repeating task is never on Today twice. D-127.
 */
fun overdueMovePlan(tasks: List<Task>, today: LocalDate): OverdueMovePlan {
    val (move, stay) = tasks
        .filter { task -> task.isMovableOverdue(today) }
        .partition { task -> !task.hasNextOccurrenceBy(today, tasks) }
    return OverdueMovePlan(move = move, stay = stay)
}

/** Open, live, and on a day before [today]: what the Overdue band holds. */
fun Task.isMovableOverdue(today: LocalDate): Boolean {
    val day = scheduledDate ?: return false
    return !isDeleted && !isCompleted && day.isBefore(today)
}

private fun Task.hasNextOccurrenceBy(today: LocalDate, tasks: List<Task>): Boolean =
    recurrence != null && tasks.any { other ->
        other.spawnedFromId == id && !other.isDeleted &&
            other.scheduledDate?.isAfter(today) == false
    }

/**
 * This task as it is once moved to [today]. D-127.
 *
 * A reminder keeps its time on today only if it will still ring: its own time
 * still ahead of [now], or a snooze still to ring. Otherwise it is cleared, so
 * the task lands in No reminder set rather than under a band that promises a
 * ring. Nothing moved is left owed in the past, so nothing rings at once.
 * Moving is an answer, so a follow-up goes either way (D-108), and so does
 * the reminder's place in the shade (D-125).
 */
fun Task.movedToToday(today: LocalDate, now: LocalDateTime, nowInstant: Instant): Task {
    val at = reminderAt ?: return copy(scheduledDate = today, followUpAt = null, inShadeSince = null)
    val onToday = today.atTime(at.toLocalTime())
    val snoozeAhead = snoozedUntil?.takeIf { until ->
        reminderDeliveredAt == null && until.isAfter(now)
    }

    return when {
        // Rings again at its time, as a new time does on Save (D-078).
        onToday.isAfter(now) -> copy(
            scheduledDate = today,
            reminderAt = onToday,
            reminderDeliveredAt = null,
            snoozedUntil = null,
            followUpAt = null,
            inShadeSince = null,
            earlyDeliveredAt = earlyDeliveredAtSave(onToday, earlyReminder, now, nowInstant),
            earlySnoozedUntil = null
        )

        // The user asked for that ring, so it stays. The early one has gone.
        snoozeAhead != null -> copy(
            scheduledDate = today,
            reminderAt = onToday,
            followUpAt = null,
            inShadeSince = null,
            earlyDeliveredAt = earlyDeliveredAtSave(onToday, earlyReminder, now, nowInstant),
            earlySnoozedUntil = null
        )

        else -> copy(
            scheduledDate = today,
            reminderAt = null,
            reminderDeliveredAt = null,
            snoozedUntil = null,
            followUpAt = null,
            inShadeSince = null,
            ringsLikeAlarm = false,
            earlyReminder = null,
            earlyDeliveredAt = null,
            earlySnoozedUntil = null
        )
    }
}

/**
 * [current] with everything [movedToToday] wrote put back as it was in
 * [before], and the rest left as it is now. Undo, D-127.
 */
fun restoredFromMove(current: Task, before: Task): Task = current.copy(
    scheduledDate = before.scheduledDate,
    reminderAt = before.reminderAt,
    reminderDeliveredAt = before.reminderDeliveredAt,
    snoozedUntil = before.snoozedUntil,
    followUpAt = before.followUpAt,
    inShadeSince = before.inShadeSince,
    ringsLikeAlarm = before.ringsLikeAlarm,
    earlyReminder = before.earlyReminder,
    earlyDeliveredAt = before.earlyDeliveredAt,
    earlySnoozedUntil = before.earlySnoozedUntil
)
