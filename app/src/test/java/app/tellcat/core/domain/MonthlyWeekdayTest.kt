package app.tellcat.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.TUESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly repeats on the nth or last weekday, `docs/decisions.md` D-116.
 *
 * Every date below is checked against the 2026 to 2028 calendars: January 2026
 * starts on a Thursday, so its Tuesdays are the 6th, 13th, 20th and 27th and
 * its Fridays the 2nd to the 30th, five of them.
 */
class MonthlyWeekdayTest {

    private val thirdTuesday = MonthlyWeekday(3, TUESDAY)
    private val lastFriday = MonthlyWeekday(MonthlyWeekday.Last, FRIDAY)

    private fun monthly(rule: MonthlyWeekday?, interval: Int = 1, end: RecurrenceEnd = RecurrenceEnd.Never) =
        Recurrence(RecurrenceUnit.MONTHLY, interval = interval, end = end, monthlyWeekday = rule)

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    // --- the weekday in a month ------------------------------------------------

    @Test
    fun `the nth weekday is counted from the first of the month`() {
        assertEquals(date(2026, 1, 20), thirdTuesday.inMonth(YearMonth.of(2026, 1)))
        assertEquals(date(2026, 2, 17), thirdTuesday.inMonth(YearMonth.of(2026, 2)))
    }

    /** May 2026 has five Fridays: the fourth is the 22nd, the last the 29th. */
    @Test
    fun `fourth and last differ in a month with five of the weekday`() {
        assertEquals(date(2026, 5, 22), MonthlyWeekday(4, FRIDAY).inMonth(YearMonth.of(2026, 5)))
        assertEquals(date(2026, 5, 29), lastFriday.inMonth(YearMonth.of(2026, 5)))
    }

    /** February 2026 has four Fridays, so the fourth is the last. */
    @Test
    fun `fourth and last agree in a month with four`() {
        assertEquals(date(2026, 2, 27), MonthlyWeekday(4, FRIDAY).inMonth(YearMonth.of(2026, 2)))
        assertEquals(date(2026, 2, 27), lastFriday.inMonth(YearMonth.of(2026, 2)))
    }

    @Test
    fun `the last Tuesday of February in a leap year and in another`() {
        val lastTuesday = MonthlyWeekday(MonthlyWeekday.Last, TUESDAY)
        assertEquals(date(2028, 2, 29), lastTuesday.inMonth(YearMonth.of(2028, 2)))
        assertEquals(date(2027, 2, 23), lastTuesday.inMonth(YearMonth.of(2027, 2)))
    }

    /** Storage can hold anything; outside one to four reads as last. */
    @Test
    fun `an ordinal outside one to four reads as last`() {
        assertEquals(date(2026, 5, 29), MonthlyWeekday(5, FRIDAY).inMonth(YearMonth.of(2026, 5)))
        assertEquals(date(2026, 5, 29), MonthlyWeekday(0, FRIDAY).inMonth(YearMonth.of(2026, 5)))
    }

    // --- the next occurrence ---------------------------------------------------

    @Test
    fun `the next third Tuesday is next month's`() {
        assertEquals(date(2026, 2, 17), monthly(thirdTuesday).nextOccurrence(date(2026, 1, 20), date(2026, 1, 20)))
    }

    @Test
    fun `the next last Friday is the true last of each month`() {
        val rule = monthly(lastFriday)
        assertEquals(date(2026, 2, 27), rule.nextOccurrence(date(2026, 1, 30), date(2026, 1, 30)))
        assertEquals(date(2026, 5, 29), rule.nextOccurrence(date(2026, 4, 24), date(2026, 4, 24)))
    }

    @Test
    fun `the year rolls over`() {
        // December 2026 starts on a Tuesday: the 15th is the third.
        assertEquals(date(2027, 1, 19), monthly(thirdTuesday).nextOccurrence(date(2026, 12, 15), date(2026, 12, 15)))
    }

    @Test
    fun `every two and every three months`() {
        assertEquals(date(2026, 3, 17), monthly(thirdTuesday, interval = 2).nextOccurrence(date(2026, 1, 20), date(2026, 1, 20)))
        assertEquals(date(2026, 4, 21), monthly(thirdTuesday, interval = 3).nextOccurrence(date(2026, 1, 20), date(2026, 1, 20)))
    }

    /** Finished late: the next one still to come, never one already gone, never a bank of them. */
    @Test
    fun `a late finish gives the next one still to come`() {
        assertEquals(date(2026, 7, 21), monthly(thirdTuesday).nextOccurrence(date(2026, 1, 20), date(2026, 6, 30)))
    }

    /** Finished early: the next one after the anchor, never one before it. D-098. */
    @Test
    fun `an early finish gives the one after the anchor`() {
        assertEquals(date(2026, 2, 17), monthly(thirdTuesday).nextOccurrence(date(2026, 1, 20), date(2026, 1, 10)))
    }

    /** Every two months counts on-months from the anchor's, not from the finish. */
    @Test
    fun `an interval keeps its on-months whenever the task is finished`() {
        // Anchored in January, every two months: March, May, July. Finished in April.
        assertEquals(date(2026, 5, 19), monthly(thirdTuesday, interval = 2).nextOccurrence(date(2026, 1, 20), date(2026, 4, 10)))
    }

    /** Every rule written before D-116 has none, and keeps the day of the month. */
    @Test
    fun `a monthly rule without a weekday keeps its day number`() {
        assertEquals(date(2026, 2, 20), monthly(null).nextOccurrence(date(2026, 1, 20), date(2026, 1, 20)))
    }

    // --- the choices the sheet offers -----------------------------------------

    @Test
    fun `the third Tuesday is offered as the third, and not as last`() {
        assertEquals(listOf(thirdTuesday), MonthlyWeekday.choicesFor(date(2026, 1, 20)))
    }

    /** February 27th 2026 is both the fourth Friday and the last. */
    @Test
    fun `a fourth that is also the last offers both`() {
        assertEquals(listOf(MonthlyWeekday(4, FRIDAY), lastFriday), MonthlyWeekday.choicesFor(date(2026, 2, 27)))
    }

    /** January 30th 2026 is the fifth Friday: only last, since most months have no fifth. */
    @Test
    fun `a fifth occurrence offers only last`() {
        assertEquals(listOf(lastFriday), MonthlyWeekday.choicesFor(date(2026, 1, 30)))
    }

    // --- the series ------------------------------------------------------------

    private val rent = Task(
        id = "rent",
        title = "Pay the rent",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        scheduledDate = date(2026, 1, 30),
        reminderAt = date(2026, 1, 30).atTime(9, 0),
        earlyReminder = EarlyReminder.OneDay,
        recurrence = monthly(lastFriday)
    )

    /** The reminder keeps its time of day, and the early reminder carries. D-115. */
    @Test
    fun `a successor keeps the reminder's time and the early reminder`() {
        val next = rent.nextRecurringInstance(today = date(2026, 1, 30), id = "next", createdAt = Instant.EPOCH)!!

        assertEquals(date(2026, 2, 27), next.scheduledDate)
        assertEquals(date(2026, 2, 27).atTime(9, 0), next.reminderAt)
        assertEquals(EarlyReminder.OneDay, next.earlyReminder)
        assertEquals(lastFriday, next.recurrence?.monthlyWeekday)
    }

    // --- an occurrence moved by hand -------------------------------------------

    /**
     * January's last-Friday occurrence dragged to Saturday 28 February, past
     * February's last Friday, the 27th. The successor is the first rule date
     * after the moved day, 27 March: no second February, and March not
     * skipped. The rule is kept, D-116, and "always after the anchor" is what
     * places it. Coordinator's edge, 2026-10-31.
     */
    @Test
    fun `an occurrence moved past the next rule date is followed by the first rule date after it`() {
        val moved = rent.copy(scheduledDate = date(2026, 2, 28), reminderAt = date(2026, 2, 28).atTime(9, 0))

        val next = moved.nextRecurringInstance(today = date(2026, 2, 28), id = "next", createdAt = Instant.EPOCH)!!

        assertEquals(date(2026, 3, 27), next.scheduledDate)
        assertEquals(lastFriday, next.recurrence?.monthlyWeekday)
    }

    /** The same moved occurrence, finished a month late: the next still to come, once. */
    @Test
    fun `a moved occurrence finished late gives the next still to come`() {
        val moved = rent.copy(scheduledDate = date(2026, 2, 28), reminderAt = date(2026, 2, 28).atTime(9, 0))

        val next = moved.nextRecurringInstance(today = date(2026, 3, 30), id = "next", createdAt = Instant.EPOCH)!!

        // April 2026's Fridays are the 3rd to the 24th.
        assertEquals(date(2026, 4, 24), next.scheduledDate)
    }

    /** Moved earlier, before its own rule date: the successor is that month's, after it. */
    @Test
    fun `an occurrence moved earlier is followed by the rule date after it`() {
        val moved = rent.copy(scheduledDate = date(2026, 2, 20), reminderAt = date(2026, 2, 20).atTime(9, 0))

        val next = moved.nextRecurringInstance(today = date(2026, 2, 20), id = "next", createdAt = Instant.EPOCH)!!

        assertEquals(date(2026, 2, 27), next.scheduledDate)
    }

    @Test
    fun `a series that ends after a count stops there`() {
        val sixth = rent.copy(occurrenceNumber = 6, recurrence = monthly(lastFriday, end = RecurrenceEnd.AfterOccurrences(6)))
        assertNull(sixth.nextRecurringInstance(today = date(2026, 1, 30), id = "next", createdAt = Instant.EPOCH))
    }

    @Test
    fun `a series that ends on a date stops before passing it`() {
        val ending = rent.copy(recurrence = monthly(lastFriday, end = RecurrenceEnd.OnDate(date(2026, 2, 20))))
        assertNull(ending.nextRecurringInstance(today = date(2026, 1, 30), id = "next", createdAt = Instant.EPOCH))
    }
}
