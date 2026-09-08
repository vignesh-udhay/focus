package com.vignesh.focuslist.data.local

import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.RecurrenceEnd
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import com.vignesh.focuslist.core.domain.Task

/**
 * Translation between the stored and domain forms of a task.
 *
 * The mapping is a direct field copy in both directions for everything except
 * the recurrence rule. It reads no clock, supplies no defaults, and generates no
 * values, so every field of one form is carried unchanged to the other.
 *
 * The rule is the exception because `docs/decisions.md` D-027 gave it four
 * properties and storage holds them as five flat columns. Assembling them is
 * this file's job rather than the entity's, so the entity stays the flat mirror
 * its own KDoc says it is.
 */

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    createdAt = createdAt,
    notes = notes,
    scheduledDate = scheduledDate,
    dueDate = dueDate,
    reminderAt = reminderAt,
    reminderDeliveredAt = reminderDeliveredAt,
    estimatedDurationMinutes = estimatedDurationMinutes,
    recurrence = recurrence?.let(::toRule),
    occurrenceNumber = occurrenceNumber,
    spawnedFromId = spawnedFromId,
    completedAt = completedAt,
    deletedAt = deletedAt
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    createdAt = createdAt,
    scheduledDate = scheduledDate,
    dueDate = dueDate,
    reminderAt = reminderAt,
    reminderDeliveredAt = reminderDeliveredAt,
    estimatedDurationMinutes = estimatedDurationMinutes,
    recurrence = recurrence?.unit,
    recurrenceInterval = recurrence?.interval,
    recurrenceWeekdays = recurrence?.weekdays,
    recurrenceEndDate = (recurrence?.end as? RecurrenceEnd.OnDate)?.date,
    recurrenceEndCount = (recurrence?.end as? RecurrenceEnd.AfterOccurrences)?.count,
    occurrenceNumber = occurrenceNumber,
    spawnedFromId = spawnedFromId,
    completedAt = completedAt,
    deletedAt = deletedAt
)

/**
 * The rule a stored period and its four companion columns describe.
 *
 * The period is what decides a rule exists at all, which is why it is the
 * non-null argument here: the other four are properties of a rule and say
 * nothing on their own. A row written before version 10 has all four null and
 * reads as the rule it was, every period with no interval and no end.
 *
 * A missing interval is one rather than zero. Zero would be a rule that steps
 * nowhere, and no version of this app has ever written it.
 */
private fun TaskEntity.toRule(unit: RecurrenceUnit): Recurrence = Recurrence(
    unit = unit,
    interval = recurrenceInterval ?: 1,
    weekdays = recurrenceWeekdays.orEmpty(),
    end = storedEnd()
)

/**
 * The end condition the two nullable columns hold between them.
 *
 * At most one is written, and both null is [RecurrenceEnd.Never]. A row holding
 * both is not something this app writes, and the date wins there rather than the
 * read failing: an end date is the stricter of the two to honour, so reading it
 * first cannot make a series outlive what the user asked for.
 */
private fun TaskEntity.storedEnd(): RecurrenceEnd = when {
    recurrenceEndDate != null -> RecurrenceEnd.OnDate(recurrenceEndDate)
    recurrenceEndCount != null -> RecurrenceEnd.AfterOccurrences(recurrenceEndCount)
    else -> RecurrenceEnd.Never
}
