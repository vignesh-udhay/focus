package app.tellcat.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * The day a Quick Add capture is scheduled for.
 *
 * Whatever the title says, or [fallbackDate] when it says nothing (D-053,
 * D-075). **A repeating task always has a day**, D-121: Repeat set without one
 * starts the series on its first day from [today], so no path saves one
 * undated.
 *
 * Said once here because two hosts save Quick Add: the lists, through
 * `TaskListViewModel`, and the window over the home screen, D-132.
 */
fun captureScheduledDate(
    parsed: CapturedTask,
    fallbackDate: LocalDate?,
    today: LocalDate,
    now: LocalDateTime,
    recurrence: Recurrence?
): LocalDate? =
    parsed.scheduledDate(fallbackDate, today, now) ?: recurrence?.firstOccurrenceFrom(today)

/**
 * A new task, as every capture makes one, or null when [title] is blank: the
 * title is the one thing a task cannot do without.
 *
 * The alarm and the early reminder belong to the reminder and are dropped
 * without one (D-129). An early reminder whose moment has already gone at
 * [now] is settled at save, as the sheet said before Save (D-115).
 */
fun newTask(
    title: String,
    scheduledDate: LocalDate?,
    reminderAt: LocalDateTime? = null,
    recurrence: Recurrence? = null,
    checklist: List<String> = emptyList(),
    ringsLikeAlarm: Boolean = false,
    earlyReminder: EarlyReminder? = null,
    now: LocalDateTime = LocalDateTime.now(),
    createdAt: Instant = Instant.now(),
    id: String = UUID.randomUUID().toString()
): Task? {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return null

    val early = earlyReminder.takeIf { reminderAt != null }

    return Task(
        id = id,
        title = trimmed,
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        reminderAt = reminderAt,
        recurrence = recurrence,
        // The day the series keeps, D-122, from the day it starts.
        recurrenceAnchor = recurrence?.anchorFor(scheduledDate),
        checklist = checklist.map { ChecklistItem(it) },
        ringsLikeAlarm = ringsLikeAlarm && reminderAt != null,
        earlyReminder = early,
        earlyDeliveredAt = earlyDeliveredAtSave(reminderAt, early, now, createdAt)
    )
}
