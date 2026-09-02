package com.vignesh.focuslist.core.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.util.Locale

class DateParserTest {

    /** A Monday, so the weekday cases can be read without a calendar. */
    private val today: LocalDate = LocalDate.of(2026, 8, 31)

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun parse(text: String, on: LocalDate = today) = parseDate(text, on)

    private fun date(text: String, on: LocalDate = today): LocalDate {
        val parsed = parse(text, on)
        assertTrue("$text was $parsed", parsed is ParsedDate.Recognized)
        return (parsed as ParsedDate.Recognized).date
    }

    private fun assertUnrecognized(text: String, on: LocalDate = today) {
        assertEquals("for input \"$text\"", ParsedDate.Unrecognized, parse(text, on))
    }

    // Vocabulary

    @Test
    fun `today is today`() {
        assertEquals(today, date("today"))
    }

    @Test
    fun `tomorrow is the day after today`() {
        assertEquals(LocalDate.of(2026, 9, 1), date("tomorrow"))
    }

    @Test
    fun `every weekday name is understood`() {
        DayOfWeek.entries.forEach { day ->
            val parsed = date(day.name.lowercase(Locale.ROOT))

            assertEquals(day, parsed.dayOfWeek)
            assertTrue("$day resolved to $parsed", parsed.isAfter(today))
        }
    }

    @Test
    fun `next weekday means the same as the bare weekday`() {
        assertEquals(date("friday"), date("next friday"))
    }

    @Test
    fun `this weekday means the same as the bare weekday`() {
        assertEquals(date("friday"), date("this friday"))
    }

    @Test
    fun `a month and day is understood month first`() {
        assertEquals(LocalDate.of(2026, 9, 3), date("september 3"))
    }

    @Test
    fun `a month and day is understood day first`() {
        assertEquals(LocalDate.of(2026, 9, 3), date("3 september"))
    }

    @Test
    fun `an abbreviated month is understood`() {
        assertEquals(LocalDate.of(2026, 9, 3), date("sep 3"))
        assertEquals(LocalDate.of(2026, 9, 3), date("3 sep"))
    }

    @Test
    fun `a month day and year is taken literally`() {
        assertEquals(LocalDate.of(2030, 9, 3), date("3 september 2030"))
        assertEquals(LocalDate.of(2030, 9, 3), date("september 3 2030"))
    }

    @Test
    fun `in n days counts forward from today`() {
        assertEquals(LocalDate.of(2026, 9, 2), date("in 2 days"))
        assertEquals(LocalDate.of(2026, 9, 1), date("in 1 day"))
    }

    @Test
    fun `in n weeks counts forward from today`() {
        assertEquals(LocalDate.of(2026, 9, 21), date("in 3 weeks"))
        assertEquals(LocalDate.of(2026, 9, 7), date("in 1 week"))
    }

    @Test
    fun `surrounding and repeated whitespace is ignored`() {
        assertEquals(today, date("   today  "))
        assertEquals(LocalDate.of(2026, 9, 2), date("in   2   days"))
    }

    @Test
    fun `a comma in a written date is ignored`() {
        assertEquals(LocalDate.of(2030, 9, 3), date("september 3, 2030"))
    }

    // Weekday boundaries

    @Test
    fun `a weekday that is today is a week away`() {
        // The 31st is a Monday.
        assertEquals(DayOfWeek.MONDAY, today.dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 7), date("monday"))
    }

    @Test
    fun `the weekday after today is tomorrow`() {
        assertEquals(LocalDate.of(2026, 9, 1), date("tuesday"))
    }

    @Test
    fun `the weekday before today is six days away`() {
        assertEquals(LocalDate.of(2026, 9, 6), date("sunday"))
    }

    // Month and year boundaries

    @Test
    fun `tomorrow crosses into the next year`() {
        val newYearsEve = LocalDate.of(2026, 12, 31)

        assertEquals(LocalDate.of(2027, 1, 1), date("tomorrow", on = newYearsEve))
    }

    @Test
    fun `a week crosses a month end`() {
        assertEquals(LocalDate.of(2026, 9, 7), date("in 1 week"))
    }

    @Test
    fun `a month and day that is today resolves to today`() {
        assertEquals(today, date("31 august"))
    }

    @Test
    fun `a month and day that has just passed rolls to next year`() {
        assertEquals(LocalDate.of(2027, 8, 30), date("30 august"))
    }

    @Test
    fun `a month and day early in the year rolls forward when typed in december`() {
        val december = LocalDate.of(2026, 12, 15)

        assertEquals(LocalDate.of(2027, 1, 2), date("2 january", on = december))
    }

    // Leap years

    @Test
    fun `tomorrow can be a leap day`() {
        val eve = LocalDate.of(2028, 2, 28)

        assertEquals(LocalDate.of(2028, 2, 29), date("tomorrow", on = eve))
    }

    @Test
    fun `a day counted forward skips a leap day that does not exist`() {
        val eve = LocalDate.of(2027, 2, 28)

        assertEquals(LocalDate.of(2027, 3, 1), date("in 1 day", on = eve))
    }

    @Test
    fun `the 29th of february finds the next leap year rather than the 28th`() {
        val parsed = date("29 february")

        assertEquals(LocalDate.of(2028, 2, 29), parsed)
    }

    @Test
    fun `the 29th of february with a non-leap year is not a date`() {
        assertUnrecognized("29 february 2027")
    }

    // Case and locale

    @Test
    fun `parsing ignores case`() {
        assertEquals(LocalDate.of(2026, 9, 1), date("TOMORROW"))
        assertEquals(LocalDate.of(2026, 9, 1), date("Tomorrow"))
        assertEquals(LocalDate.of(2026, 9, 3), date("SEPTEMBER 3"))
    }

    @Test
    fun `parsing survives a turkish default locale`() {
        // Turkish lower cases I to a dotless i, which would break "in n days"
        // for anyone whose device is set to it.
        Locale.setDefault(Locale.forLanguageTag("tr"))

        assertEquals(LocalDate.of(2026, 9, 2), date("In 2 days"))
    }

    @Test
    fun `the device locale does not change the result`() {
        val english = date("3 september")
        Locale.setDefault(Locale.FRANCE)

        assertEquals(english, date("3 september"))
    }

    // Rejection

    @Test
    fun `empty text is not a date and not an error`() {
        assertEquals(ParsedDate.Empty, parse(""))
    }

    @Test
    fun `whitespace only is not a date and not an error`() {
        assertEquals(ParsedDate.Empty, parse("   "))
    }

    @Test
    fun `next week names a range rather than a day`() {
        assertUnrecognized("next week")
    }

    @Test
    fun `a time of day is rejected rather than dropped`() {
        // A task carries a day and no time. Accepting the day here would tell
        // the user the hour was understood when nothing about it was stored.
        assertUnrecognized("tomorrow at 3pm")
        assertUnrecognized("friday at 09:00")
    }

    @Test
    fun `a numeric date is too ambiguous to accept`() {
        assertUnrecognized("03/09")
        assertUnrecognized("9-3-2026")
    }

    @Test
    fun `yesterday is not supported`() {
        assertUnrecognized("yesterday")
    }

    @Test
    fun `arbitrary text is not a date`() {
        assertUnrecognized("call the plumber")
        assertUnrecognized("soon")
        assertUnrecognized("septembre 3")
        assertUnrecognized("31 smarch")
    }

    @Test
    fun `a day that no month has is not a date`() {
        assertUnrecognized("30 february")
        assertUnrecognized("31 september")
    }

    @Test
    fun `a count beyond the end of the calendar is rejected rather than overflowing`() {
        // No arbitrary ceiling is imposed: the limit is the last date that can
        // be represented at all. A silly but representable count is still a
        // date, and only counts past the end of the calendar are refused.
        assertTrue(parse("in 99999999999 days") is ParsedDate.Recognized)

        assertUnrecognized("in 999999999999 days")
        assertUnrecognized("in 999999999999 weeks")
    }

    @Test
    fun `a count too long to be a number is rejected`() {
        assertUnrecognized("in 99999999999999999999999 days")
    }

    // The property that holds across the whole vocabulary

    @Test
    fun `no supported input ever resolves to a day before today`() {
        val relative = listOf("today", "tomorrow", "in 0 days", "in 1 day", "in 30 days", "in 52 weeks")
        val weekdays = DayOfWeek.entries.flatMap { day ->
            val name = day.name.lowercase(Locale.ROOT)
            listOf(name, "next $name", "this $name")
        }
        val monthDays = Month.entries.flatMap { month ->
            listOf(1, 15, 28).map { day -> "$day ${month.name.lowercase(Locale.ROOT)}" }
        }
        val inputs = relative + weekdays + monthDays

        // Walked across a leap year and into the next, so the month-day rolling
        // rule is exercised from every starting point rather than from one
        // convenient Monday.
        var day = LocalDate.of(2028, 1, 1)
        repeat(400) {
            inputs.forEach { input ->
                val parsed = parse(input, on = day)

                assertTrue("\"$input\" on $day was $parsed", parsed is ParsedDate.Recognized)
                assertFalse(
                    "\"$input\" on $day resolved to the past",
                    (parsed as ParsedDate.Recognized).date.isBefore(day)
                )
            }
            day = day.plusDays(1)
        }
    }
}
