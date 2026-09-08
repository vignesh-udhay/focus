package com.vignesh.focuslist.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class TaskConvertersTest {

    // LocalDate -> epoch day

    @Test
    fun `the epoch itself is day zero`() {
        assertEquals(0L, TaskConverters.localDateToEpochDay(LocalDate.of(1970, 1, 1)))
    }

    @Test
    fun `the day after the epoch is day one`() {
        assertEquals(1L, TaskConverters.localDateToEpochDay(LocalDate.of(1970, 1, 2)))
    }

    @Test
    fun `dates before the epoch are negative`() {
        assertEquals(-1L, TaskConverters.localDateToEpochDay(LocalDate.of(1969, 12, 31)))
    }

    // epoch day -> LocalDate

    @Test
    fun `day zero is the epoch`() {
        assertEquals(LocalDate.of(1970, 1, 1), TaskConverters.epochDayToLocalDate(0L))
    }

    @Test
    fun `a negative day decodes to a date before the epoch`() {
        assertEquals(LocalDate.of(1969, 12, 31), TaskConverters.epochDayToLocalDate(-1L))
    }

    // Null handling for dates

    @Test
    fun `a null LocalDate encodes to null`() {
        assertNull(TaskConverters.localDateToEpochDay(null))
    }

    @Test
    fun `a null epoch day decodes to null`() {
        assertNull(TaskConverters.epochDayToLocalDate(null))
    }

    // Instant -> epoch millis

    @Test
    fun `the epoch instant is zero millis`() {
        assertEquals(0L, TaskConverters.instantToEpochMillis(Instant.EPOCH))
    }

    @Test
    fun `an instant encodes to its epoch millis`() {
        assertEquals(1234L, TaskConverters.instantToEpochMillis(Instant.ofEpochMilli(1234L)))
    }

    // epoch millis -> Instant

    @Test
    fun `zero millis decodes to the epoch instant`() {
        assertEquals(Instant.EPOCH, TaskConverters.epochMillisToInstant(0L))
    }

    @Test
    fun `epoch millis decode to the matching instant`() {
        assertEquals(Instant.ofEpochMilli(1234L), TaskConverters.epochMillisToInstant(1234L))
    }

    // Null handling for instants

    @Test
    fun `a null Instant encodes to null`() {
        assertNull(TaskConverters.instantToEpochMillis(null))
    }

    @Test
    fun `a null epoch millis decodes to null`() {
        assertNull(TaskConverters.epochMillisToInstant(null))
    }

    // Round trips

    @Test
    fun `a LocalDate round trips exactly`() {
        listOf(
            LocalDate.of(2026, 8, 31),
            LocalDate.of(1970, 1, 1),
            LocalDate.of(1969, 12, 31),
            LocalDate.of(2000, 2, 29),
            LocalDate.of(2100, 12, 31)
        ).forEach { date ->
            val encoded = TaskConverters.localDateToEpochDay(date)

            assertEquals(date, TaskConverters.epochDayToLocalDate(encoded))
        }
    }

    @Test
    fun `a millisecond-precision Instant round trips exactly`() {
        // SQLite stores epoch milliseconds, so only millisecond-aligned
        // instants survive a round trip unchanged.
        listOf(
            Instant.EPOCH,
            Instant.parse("2026-01-01T09:00:00Z"),
            Instant.parse("2026-08-31T17:30:45.123Z"),
            Instant.ofEpochMilli(-1L)
        ).forEach { instant ->
            val encoded = TaskConverters.instantToEpochMillis(instant)

            assertEquals(instant, TaskConverters.epochMillisToInstant(encoded))
        }
    }

    @Test
    fun `sub-millisecond precision is truncated by the round trip`() {
        // Not a bug: this is the documented cost of storing epoch millis.
        // Recorded here so the boundary is executable rather than a comment.
        val withNanos = Instant.parse("2026-08-31T17:30:45.123456789Z")

        val roundTripped = TaskConverters.epochMillisToInstant(
            TaskConverters.instantToEpochMillis(withNanos)
        )

        assertNotEquals(withNanos, roundTripped)
        assertEquals(Instant.parse("2026-08-31T17:30:45.123Z"), roundTripped)
    }

    // LocalDateTime <-> ISO-8601 text

    @Test
    fun `a reminder time round trips exactly`() {
        val reminder = LocalDateTime.of(2026, 8, 31, 9, 30)

        val encoded = TaskConverters.localDateTimeToText(reminder)

        assertEquals(reminder, TaskConverters.textToLocalDateTime(encoded))
    }

    @Test
    fun `a reminder time encodes without a timezone`() {
        val encoded = TaskConverters.localDateTimeToText(
            LocalDateTime.of(2026, 8, 31, 9, 30)
        )

        // No trailing Z and no offset. The absence is the point: this column
        // holds a wall-clock time, and any zone in the text would be a claim
        // the type cannot make.
        assertEquals("2026-08-31T09:30", encoded)
    }

    @Test
    fun `a reminder time keeps seconds when it has them`() {
        val encoded = TaskConverters.localDateTimeToText(
            LocalDateTime.of(2026, 8, 31, 9, 30, 15)
        )

        assertEquals("2026-08-31T09:30:15", encoded)
        assertEquals(
            LocalDateTime.of(2026, 8, 31, 9, 30, 15),
            TaskConverters.textToLocalDateTime(encoded)
        )
    }

    @Test
    fun `midnight round trips, and does not collapse to a date`() {
        val midnight = LocalDateTime.of(2026, 8, 31, 0, 0)

        val encoded = TaskConverters.localDateTimeToText(midnight)

        assertEquals("2026-08-31T00:00", encoded)
        assertEquals(midnight, TaskConverters.textToLocalDateTime(encoded))
    }

    @Test
    fun `no reminder encodes to null and decodes back to null`() {
        assertNull(TaskConverters.localDateTimeToText(null))
        assertNull(TaskConverters.textToLocalDateTime(null))
    }

    @Test
    fun `malformed reminder text reads as no reminder rather than throwing`() {
        // Found on a device: one empty string in this column crashed the app
        // on every read of the task list. Losing one reminder is bad; an app
        // that cannot open, with every other reminder unreachable, is worse.
        assertNull(TaskConverters.textToLocalDateTime(""))
        assertNull(TaskConverters.textToLocalDateTime("not a date"))
        assertNull(TaskConverters.textToLocalDateTime("2026-08-31"))
        assertNull(TaskConverters.textToLocalDateTime("2026-13-45T99:99"))
    }

    // Recurrence period -> name

    @Test
    fun `a period round trips by name`() {
        RecurrenceUnit.entries.forEach { unit ->
            val encoded = TaskConverters.recurrenceUnitToName(unit)

            assertEquals(unit.name, encoded)
            assertEquals(unit, TaskConverters.nameToRecurrenceUnit(encoded))
        }
    }

    /**
     * The four names version 3 wrote, spelled out.
     *
     * D-027 renamed the enum and kept these, so that no install has its rows
     * rewritten to mean what they already meant. A test that only round trips
     * would pass just as happily if all four were renamed together, which is
     * the change this is here to catch.
     */
    @Test
    fun `the stored names are the ones every version has written`() {
        assertEquals(RecurrenceUnit.DAILY, TaskConverters.nameToRecurrenceUnit("DAILY"))
        assertEquals(RecurrenceUnit.WEEKLY, TaskConverters.nameToRecurrenceUnit("WEEKLY"))
        assertEquals(RecurrenceUnit.MONTHLY, TaskConverters.nameToRecurrenceUnit("MONTHLY"))
        assertEquals(RecurrenceUnit.YEARLY, TaskConverters.nameToRecurrenceUnit("YEARLY"))
    }

    @Test
    fun `a period this version does not know reads as no rule`() {
        assertNull(TaskConverters.nameToRecurrenceUnit("FORTNIGHTLY"))
        assertNull(TaskConverters.nameToRecurrenceUnit(""))
        assertNull(TaskConverters.nameToRecurrenceUnit(null))
    }

    // Weekday set -> names

    @Test
    fun `a weekday set round trips, in week order whatever order it was built in`() {
        val days = linkedSetOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)

        val encoded = TaskConverters.weekdaysToNames(days)

        assertEquals("MONDAY,WEDNESDAY,FRIDAY", encoded)
        assertEquals(days, TaskConverters.namesToWeekdays(encoded))
    }

    /**
     * An empty set stores as null, not as an empty string.
     *
     * A weekly rule with no days named and a rule written before version 10
     * mean the same thing, so they should not be two different values in the
     * column.
     */
    @Test
    fun `no weekdays encodes to null`() {
        assertNull(TaskConverters.weekdaysToNames(emptySet()))
        assertNull(TaskConverters.weekdaysToNames(null))
        assertNull(TaskConverters.namesToWeekdays(null))
    }

    @Test
    fun `a weekday name this version does not know is dropped rather than thrown on`() {
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            TaskConverters.namesToWeekdays("MONDAY,MOONDAY,FRIDAY")
        )
        assertEquals(emptySet<DayOfWeek>(), TaskConverters.namesToWeekdays(""))
    }
}
