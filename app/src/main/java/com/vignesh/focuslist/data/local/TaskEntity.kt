package com.vignesh.focuslist.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

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
 * [notes] arrived in schema version 2, [recurrence] in version 3, and
 * [reminderAt] in version 5, and [reminderDeliveredAt] in version 6. Where a
 * field sits in this declaration has no bearing on the stored column order,
 * and Room validates columns by name, so each migration appends its column
 * while it is declared here beside the field it belongs with.
 *
 * Version 10 turns [recurrence] from the whole rule into its period and adds
 * the four properties `docs/decisions.md` D-027 gave a rule: how many periods a
 * step covers, which weekdays inside an on-week count, and the two mutually
 * exclusive ways a series can end. The column keeps its name and its stored
 * values, so no existing row is rewritten to mean what it already meant.
 *
 * [recurrenceEndDate] and [recurrenceEndCount] hold at most one non-null value
 * between them, because `RecurrenceEnd` is one of three cases and two of them
 * carry data. Two nullable columns rather than a discriminator and a payload:
 * the third case is both of them being null, which needs nothing stored.
 */
@Entity(tableName = "tasks")
@TypeConverters(TaskConverters::class)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val createdAt: Instant,
    val scheduledDate: LocalDate?,
    val dueDate: LocalDate?,
    val reminderAt: LocalDateTime?,
    val reminderDeliveredAt: Instant?,
    val estimatedDurationMinutes: Int?,
    val recurrence: RecurrenceUnit?,
    val recurrenceInterval: Int?,
    val recurrenceWeekdays: Set<DayOfWeek>?,
    val recurrenceEndDate: LocalDate?,
    val recurrenceEndCount: Int?,
    // The default belongs to the column, not just to the migration that added
    // it. Room creates this table from scratch on a fresh install, and without
    // this the created column is `NOT NULL` with nothing to fall back on, so any
    // insert that does not name it fails. The debug seed is one such insert, and
    // it broke on the first fresh install after version 10.
    @ColumnInfo(defaultValue = "1") val occurrenceNumber: Int,
    val spawnedFromId: String?,
    val completedAt: Instant?,
    val deletedAt: Instant?
)
