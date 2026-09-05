package com.vignesh.focuslist.data.local

import com.vignesh.focuslist.core.domain.Task

/**
 * Translation between the stored and domain forms of a task.
 *
 * The mapping is a direct field copy in both directions. It reads no clock,
 * supplies no defaults, and generates no values, so every field of one form is
 * carried unchanged to the other.
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
    recurrence = recurrence,
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
    recurrence = recurrence,
    spawnedFromId = spawnedFromId,
    completedAt = completedAt,
    deletedAt = deletedAt
)
