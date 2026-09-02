package com.vignesh.focuslist.data.local

import androidx.room.TypeConverter
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.TaskPlacement
import java.time.Instant
import java.time.LocalDate

/**
 * Storage encoding for the types Room cannot persist directly.
 *
 * Every conversion is total and deterministic: no clock reads, no defaults, no
 * locale or timezone dependence.
 */
object TaskConverters {

    /** A calendar date persists as its epoch day, which carries no timezone. */
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    /**
     * A timestamp persists as epoch milliseconds.
     *
     * This truncates the sub-millisecond part of an [Instant]. Millisecond
     * resolution is ample for recording when a task was created, completed, or
     * deleted, but it does mean a round trip is only exact for instants that
     * are already millisecond-aligned.
     */
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** Placement persists by name, never by ordinal, so reordering the enum is safe. */
    @TypeConverter
    fun placementToName(value: TaskPlacement): String = value.name

    /**
     * Unknown names fall back to [TaskPlacement.INBOX].
     *
     * A task surfacing in the wrong list is recoverable; throwing while reading
     * the database is not.
     */
    @TypeConverter
    fun nameToPlacement(value: String): TaskPlacement = when (value) {
        TaskPlacement.INBOX.name -> TaskPlacement.INBOX
        TaskPlacement.ANYTIME.name -> TaskPlacement.ANYTIME
        TaskPlacement.SOMEDAY.name -> TaskPlacement.SOMEDAY
        else -> TaskPlacement.INBOX
    }

    /**
     * Recurrence persists by name, and null means the task happens once.
     *
     * Null is the same answer for a rule that was never set and for a name
     * this version does not know, on the same terms as placement above: a task
     * that stops repeating is recoverable, and throwing while reading the
     * database is not. Every task written before version 3 reads as null,
     * which is exactly what it was.
     */
    @TypeConverter
    fun recurrenceToName(value: Recurrence?): String? = value?.name

    @TypeConverter
    fun nameToRecurrence(value: String?): Recurrence? =
        Recurrence.entries.firstOrNull { it.name == value }
}
