package app.tellcat.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.RecurrenceUnit
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
    // Version 12: the task's checklist, null when it has none.
    val checklist: List<ChecklistItem>?,
    val createdAt: Instant,
    val scheduledDate: LocalDate?,
    val dueDate: LocalDate?,
    val reminderAt: LocalDateTime?,
    val reminderDeliveredAt: Instant?,
    // Version 11, D-078: a snooze, kept apart from the reminder it moves.
    val snoozedUntil: LocalDateTime?,
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
    val deletedAt: Instant?,
    // Version 13, D-102: the reminder rings like an alarm. The default is the
    // column's for the reason `occurrenceNumber` gives, and the parameter's so
    // every row written before it reads as a plain reminder.
    @ColumnInfo(defaultValue = "0") val ringsLikeAlarm: Boolean = false,
    // Version 14, D-108: when an unanswered reminder is posted once more.
    val followUpAt: LocalDateTime? = null,
    // Version 15, D-115: the early reminder, as minutes before, and its
    // delivery and snooze. Null on every row written before it: none.
    val earlyReminderMinutes: Long? = null,
    val earlyDeliveredAt: Instant? = null,
    val earlySnoozedUntil: LocalDateTime? = null,
    // Version 16, D-116: a monthly rule on a weekday of the month, as the
    // occurrence (one to four, or -1 for last) and the weekday's name. Null
    // on every row written before it: on the day of the month, as before.
    val recurrenceMonthlyOrdinal: Int? = null,
    val recurrenceMonthlyWeekday: String? = null
)
