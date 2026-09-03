package com.vignesh.focuslist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.TaskPlacement
import java.time.Instant
import java.time.LocalDate

/**
 * The stored form of a task.
 *
 * A flat mirror of the domain model. It holds storage concerns only: no
 * derived state, no presentation, no behavior.
 *
 * `isCompleted` and `isDeleted` are deliberately absent. They are derived on
 * the domain model from [completedAt] and [deletedAt], and storing them would
 * create a second source of truth that could disagree.
 *
 * Converters are registered here rather than on a database class, since no
 * database exists yet. Moving them to `@Database` later is equivalent.
 *
 * [notes] arrived in schema version 2 and [recurrence] in version 3. Where a
 * field sits in this declaration has no bearing on the stored column order,
 * and Room validates columns by name, so each migration appends its column
 * while it is declared here beside the field it belongs with.
 */
@Entity(tableName = "tasks")
@TypeConverters(TaskConverters::class)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val placement: TaskPlacement,
    val createdAt: Instant,
    val scheduledDate: LocalDate?,
    val dueDate: LocalDate?,
    val estimatedDurationMinutes: Int?,
    val recurrence: Recurrence?,
    val spawnedFromId: String?,
    val completedAt: Instant?,
    val deletedAt: Instant?
)
