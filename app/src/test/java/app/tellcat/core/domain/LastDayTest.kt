package app.tellcat.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A month's last day, as a rule of its own. `docs/decisions.md` D-123. */
class LastDayTest {

    private val lastDay = Recurrence(RecurrenceUnit.MONTHLY, monthlyLastDay = true)
    private val ten: LocalTime = LocalTime.of(10, 0)

    private fun series(first: LocalDate, rule: Recurrence = lastDay, anchor: MonthDay? = null) = Task(
        id = "0",
        title = "Month end",
        createdAt = Instant.EPOCH,
        scheduledDate = first,
        reminderAt = first.atTime(ten),
        recurrence = rule,
        recurrenceAnchor = anchor
    )

    private fun walk(task: Task, count: Int): List<LocalDate> {
        val days = mutableListOf(task.scheduledDate!!)
        var current = task
        repeat(count - 1) { n ->
            current = current.nextRecurringInstance(today = current.scheduledDate!!, id = "${n + 1}", createdAt = Instant.EPOCH)
                ?: return days
            days += current.scheduledDate!!
        }
        return days
    }

    private fun dates(vararg iso: String) = iso.map(LocalDate::parse)

    /** 30, 31, 31, 28, 31, 30: every length of month, each on its last day. */
    @Test
    fun everyMonthIsOnItsLastDay() {
        assertEquals(
            dates("2026-11-30", "2026-12-31", "2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30"),
            walk(series(LocalDate.of(2026, 11, 30)), 6)
        )
    }

    @Test
    fun aLeapFebruaryIsThe29th() {
        assertEquals(dates("2028-01-31", "2028-02-29", "2028-03-31"), walk(series(LocalDate.of(2028, 1, 31)), 3))
    }

    @Test
    fun theReminderKeepsItsTime() {
        val next = series(LocalDate.of(2026, 11, 30)).nextRecurringInstance(
            today = LocalDate.of(2026, 11, 30), id = "n", createdAt = Instant.EPOCH
        )!!
        assertEquals(LocalDate.of(2026, 12, 31).atTime(ten), next.reminderAt)
    }

    @Test
    fun everyTwoAndThreeMonths() {
        assertEquals(
            dates("2027-01-31", "2027-03-31", "2027-05-31", "2027-07-31"),
            walk(series(LocalDate.of(2027, 1, 31), lastDay.copy(interval = 2)), 4)
        )
        assertEquals(
            dates("2026-11-30", "2027-02-28", "2027-05-31", "2027-08-31"),
            walk(series(LocalDate.of(2026, 11, 30), lastDay.copy(interval = 3)), 4)
        )
    }

    @Test
    fun endsByCountAndByDate() {
        assertEquals(
            dates("2026-11-30", "2026-12-31", "2027-01-31"),
            walk(series(LocalDate.of(2026, 11, 30), lastDay.copy(end = RecurrenceEnd.AfterOccurrences(3))), 10)
        )
        assertEquals(
            dates("2026-11-30", "2026-12-31", "2027-01-31", "2027-02-28"),
            walk(series(LocalDate.of(2026, 11, 30), lastDay.copy(end = RecurrenceEnd.OnDate(LocalDate.of(2027, 3, 30)))), 10)
        )
    }

    /** D-122's day is for rules on a date: this one keeps none, and a stray one changes nothing. */
    @Test
    fun itKeepsNoSeriesDayAndIgnoresOne() {
        assertNull(lastDay.anchorFor(LocalDate.of(2026, 11, 30)))
        assertFalse(lastDay.fallsOnADate)

        val withStray = series(LocalDate.of(2026, 11, 30), anchor = MonthDay.of(12, 15))
        assertEquals(dates("2026-11-30", "2026-12-31", "2027-01-31"), walk(withStray, 3))
        assertNull(series(LocalDate.of(2026, 11, 30)).nextRecurringInstance(LocalDate.of(2026, 11, 30), "n", Instant.EPOCH)!!.recurrenceAnchor)
    }

    /** Distinct from the day-of-the-month rule, which it must not be stored or compared as. */
    @Test
    fun itIsNotTheDayOfTheMonth() {
        assertTrue(lastDay.hasMonthlyLastDay)
        assertFalse(Recurrence(RecurrenceUnit.MONTHLY).hasMonthlyLastDay)
        assertTrue(lastDay != Recurrence(RecurrenceUnit.MONTHLY))
        // A flag on another unit means nothing.
        assertFalse(Recurrence(RecurrenceUnit.WEEKLY, monthlyLastDay = true).hasMonthlyLastDay)
    }

    // --- The first date, and the one-tap list ----------------------------------------------

    @Test
    fun itsFirstDateIsThisMonthsLastDay() {
        assertEquals(LocalDate.of(2026, 11, 30), lastDay.firstOccurrenceFrom(LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2026, 11, 30), lastDay.firstOccurrenceFrom(LocalDate.of(2026, 11, 30)))
        assertEquals(LocalDate.of(2028, 2, 29), lastDay.firstOccurrenceFrom(LocalDate.of(2028, 2, 3)))
    }

    /** Offered in one tap only on a last day that is not a 31st. */
    @Test
    fun theListOffersItOnlyWhereItDiffersFromTheDaysNumber() {
        assertTrue(lastDay in commonRepeats(LocalDate.of(2026, 11, 30)))
        assertTrue(lastDay in commonRepeats(LocalDate.of(2027, 2, 28)))
        assertTrue(lastDay in commonRepeats(LocalDate.of(2028, 2, 29)))
        assertFalse(lastDay in commonRepeats(LocalDate.of(2026, 12, 31)))
        assertFalse(lastDay in commonRepeats(LocalDate.of(2028, 2, 28)))
        assertFalse(lastDay in commonRepeats(LocalDate.of(2026, 11, 2)))
    }

    // --- The task moves onto its rule ---------------------------------------------------------

    @Test
    fun choosingItOnThe2ndMovesTheTaskToThe30th() {
        val task = Task(
            id = "t", title = "Pay rent", createdAt = Instant.EPOCH,
            scheduledDate = LocalDate.of(2026, 11, 2),
            dueDate = LocalDate.of(2026, 11, 4),
            reminderAt = LocalDate.of(2026, 11, 2).atTime(ten),
            recurrence = lastDay
        )

        val moved = task.onItsRule(today = LocalDate.of(2026, 11, 2))

        assertEquals(LocalDate.of(2026, 11, 30), moved.scheduledDate)
        assertEquals(LocalDate.of(2026, 11, 30).atTime(ten), moved.reminderAt)
        assertEquals(LocalDate.of(2026, 12, 2), moved.dueDate)
    }

    @Test
    fun aRuleThatFallsOnTheTasksDayLeavesItWhereItIs() {
        val task = series(LocalDate.of(2026, 11, 30))
        assertSame(task, task.onItsRule(today = LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun anUndatedTaskGetsItsFirstDay() {
        val task = Task(id = "t", title = "Pay rent", createdAt = Instant.EPOCH, recurrence = lastDay)
        assertEquals(LocalDate.of(2026, 11, 30), task.onItsRule(today = LocalDate.of(2026, 11, 2)).scheduledDate)
    }

    @Test
    fun aRuleChosenWithNoneLeavesTheTaskAlone() {
        val task = Task(id = "t", title = "Pay rent", createdAt = Instant.EPOCH, scheduledDate = LocalDate.of(2026, 11, 2))
        assertSame(task, task.onItsRule(today = LocalDate.of(2026, 11, 2)))
    }
}
