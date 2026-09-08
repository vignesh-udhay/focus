package com.vignesh.focuslist.data.local

import androidx.room.TypeConverter
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import java.time.DayOfWeek
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
     * A recurrence period persists by name, and null means the task happens
     * once.
     *
     * Null is the same answer for a rule that was never set and for a name
     * this version does not know: a task that stops repeating is recoverable,
     * and throwing while reading the database is not. Every task written
     * before version 3 reads as null, which is exactly what it was.
     *
     * The four names are the ones version 3 wrote. `docs/decisions.md` D-027
     * kept them when the enum became `RecurrenceUnit` and the sheet started
     * labelling them Day, Week, Month and Year, so this converter reads rows
     * from every version that has ever existed without a rewrite.
     */
    @TypeConverter
    fun recurrenceUnitToName(value: RecurrenceUnit?): String? = value?.name

    @TypeConverter
    fun nameToRecurrenceUnit(value: String?): RecurrenceUnit? =
        RecurrenceUnit.entries.firstOrNull { it.name == value }

    /**
     * A weekday set persists as its names, comma separated.
     *
     * By name for the reason the period is: an ordinal column would change
     * meaning if `java.time` ever reordered, and a name is legible when someone
     * opens the database to work out why a task came back on a Thursday.
     *
     * An empty set stores as null rather than as an empty string, so a weekly
     * rule with no days named and a rule written before version 10 read the
     * same way, which is what they mean.
     */
    @TypeConverter
    fun weekdaysToNames(value: Set<DayOfWeek>?): String? =
        value?.takeIf { it.isNotEmpty() }
            ?.sortedBy(DayOfWeek::getValue)
            ?.joinToString(separator = ",", transform = DayOfWeek::name)

    /**
     * Names this version does not know are dropped, not thrown on.
     *
     * The same bargain every converter here strikes. A rule losing one of its
     * days is recoverable; an app that cannot read its own task list is not.
     */
    @TypeConverter
    fun namesToWeekdays(value: String?): Set<DayOfWeek>? =
        value?.split(",")
            ?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
            ?.toSet()
}
