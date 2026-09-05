package com.vignesh.focuslist.data.local

import androidx.room.TypeConverter
import com.vignesh.focuslist.core.domain.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

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
     * A reminder time persists as ISO-8601 text, not as a number.
     *
     * `LocalDateTime` has no timezone, so there is no correct instant to
     * reduce it to. Encoding it as an epoch value against UTC would round
     * trip perfectly and still be a trap: the column would hold numbers that
     * look exactly like the epoch millis in [instantToEpochMillis] beside it,
     * and reading one as the other is a bug nothing would catch. Text cannot
     * be misread that way, and it is legible when someone opens the database
     * to work out why a reminder fired when it did.
     */
    @TypeConverter
    fun localDateTimeToText(value: LocalDateTime?): String? = value?.toString()

    /**
     * Unparseable text reads as no reminder.
     *
     * The same bargain the recurrence converter strikes: a task quietly
     * losing a field is recoverable, and throwing while reading the database
     * is not. This one was written to throw, and a single malformed row
     * crashed the app on every read of the task list, which is every screen.
     *
     * Losing a reminder is a serious outcome, so this is not a shrug. It is a
     * choice between one reminder lost and an app that cannot open at all,
     * with every other reminder in it unreachable.
     */
    @TypeConverter
    fun textToLocalDateTime(value: String?): LocalDateTime? =
        value?.let {
            runCatching { LocalDateTime.parse(it) }.getOrNull()
        }

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

    /**
     * Recurrence persists by name, and null means the task happens once.
     *
     * Null is the same answer for a rule that was never set and for a name
     * this version does not know: a task that stops repeating is recoverable,
     * and throwing while reading the database is not. Every task written
     * before version 3 reads as null, which is exactly what it was.
     */
    @TypeConverter
    fun recurrenceToName(value: Recurrence?): String? = value?.name

    @TypeConverter
    fun nameToRecurrence(value: String?): Recurrence? =
        Recurrence.entries.firstOrNull { it.name == value }
}
