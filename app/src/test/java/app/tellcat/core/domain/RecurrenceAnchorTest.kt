package app.tellcat.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A series on a date keeps its day. `docs/decisions.md` D-122.
 *
 * Each next occurrence used to count from the one just finished, so a monthly
 * series on the 31st, clamped to the 28th in February, stayed on the 28th from
 * March, its reminder ringing on the wrong day with nothing said. These walk a
 * series through its completions the way the app does: each occurrence is made
 * from the last, on the day it was due.
 */
class RecurrenceAnchorTest {

    private val ten: LocalTime = LocalTime.of(10, 0)

    private fun series(
        first: LocalDate,
        rule: Recurrence,
        anchor: MonthDay? = rule.anchorFor(first)
    ) = Task(
        id = "0",
        title = "Month end",
        createdAt = Instant.EPOCH,
        scheduledDate = first,
        reminderAt = first.atTime(ten),
        recurrence = rule,
        recurrenceAnchor = anchor
    )

    /** The scheduled days of the first [count] occurrences, each completed on its day. */
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

    // --- The walk that found it --------------------------------------------------------

    @Test
    fun aMonthlySeriesOnThe31stIsOnTheMonthsLastDayEveryTime() {
        assertEquals(
            dates("2026-12-31", "2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31", "2027-06-30"),
            walk(series(LocalDate.of(2026, 12, 31), Recurrence(RecurrenceUnit.MONTHLY)), 7)
        )
    }

    /** The reminder moves with the day and keeps its time. */
    @Test
    fun theReminderFollowsTheDayAtTheSameTime() {
        var task = series(LocalDate.of(2027, 1, 31), Recurrence(RecurrenceUnit.MONTHLY))
        repeat(2) { task = task.nextRecurringInstance(today = task.scheduledDate!!, id = "n", createdAt = Instant.EPOCH)!! }

        assertEquals(LocalDate.of(2027, 3, 31).atTime(ten), task.reminderAt)
    }

    /** A row from before the fix, with no stored day, takes its own and carries it on. */
    @Test
    fun aSeriesWithNoStoredDayTakesItsOwnAndKeepsIt() {
        val legacy = series(LocalDate.of(2027, 1, 31), Recurrence(RecurrenceUnit.MONTHLY), anchor = null)

        assertEquals(dates("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30"), walk(legacy, 4))
    }

    // --- The 29th, the 30th, and the 29th of February -------------------------------------

    @Test
    fun aSeriesOnThe29thIsOnThe29thAgainAfterFebruary() {
        assertEquals(
            dates("2027-01-29", "2027-02-28", "2027-03-29", "2027-04-29"),
            walk(series(LocalDate.of(2027, 1, 29), Recurrence(RecurrenceUnit.MONTHLY)), 4)
        )
        // In a leap year February has its 29th.
        assertEquals(
            dates("2028-01-29", "2028-02-29", "2028-03-29"),
            walk(series(LocalDate.of(2028, 1, 29), Recurrence(RecurrenceUnit.MONTHLY)), 3)
        )
    }

    @Test
    fun aSeriesOnThe30thIsOnThe30thAgainAfterFebruary() {
        assertEquals(
            dates("2027-01-30", "2027-02-28", "2027-03-30", "2027-04-30", "2027-05-30"),
            walk(series(LocalDate.of(2027, 1, 30), Recurrence(RecurrenceUnit.MONTHLY)), 5)
        )
    }

    /** It drifted too: Feb 29 to Feb 28, then the 28th for good. Leap years get the 29th back. */
    @Test
    fun aYearlySeriesOnThe29thOfFebruaryHasItBackInLeapYears() {
        assertEquals(
            dates("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29", "2033-02-28"),
            walk(series(LocalDate.of(2028, 2, 29), Recurrence(RecurrenceUnit.YEARLY)), 6)
        )
    }

    // --- Intervals and ends ---------------------------------------------------------------------

    @Test
    fun everyTwoMonthsFromThe31st() {
        assertEquals(
            dates("2026-12-31", "2027-02-28", "2027-04-30", "2027-06-30", "2027-08-31"),
            walk(series(LocalDate.of(2026, 12, 31), Recurrence(RecurrenceUnit.MONTHLY, interval = 2)), 5)
        )
    }

    @Test
    fun everyThreeMonthsFromThe30thOfNovember() {
        assertEquals(
            dates("2026-11-30", "2027-02-28", "2027-05-30", "2027-08-30", "2027-11-30"),
            walk(series(LocalDate.of(2026, 11, 30), Recurrence(RecurrenceUnit.MONTHLY, interval = 3)), 5)
        )
    }

    @Test
    fun anEndByCountStopsOnTime() {
        val rule = Recurrence(RecurrenceUnit.MONTHLY, end = RecurrenceEnd.AfterOccurrences(4))
        assertEquals(
            dates("2026-12-31", "2027-01-31", "2027-02-28", "2027-03-31"),
            walk(series(LocalDate.of(2026, 12, 31), rule), 10)
        )
    }

    @Test
    fun anEndByDateStopsOnTime() {
        val rule = Recurrence(RecurrenceUnit.MONTHLY, end = RecurrenceEnd.OnDate(LocalDate.of(2027, 3, 31)))
        assertEquals(
            dates("2026-12-31", "2027-01-31", "2027-02-28", "2027-03-31"),
            walk(series(LocalDate.of(2026, 12, 31), rule), 10)
        )
    }

    // --- Moved by hand, finished late or early ------------------------------------------------

    /** Moving one occurrence keeps the series' day, as D-116 keeps a weekday rule. */
    @Test
    fun anOccurrenceMovedByHandKeepsTheSeriesDay() {
        val moved = series(LocalDate.of(2027, 3, 31), Recurrence(RecurrenceUnit.MONTHLY), anchor = MonthDay.of(12, 31))
            .copy(scheduledDate = LocalDate.of(2027, 4, 2))

        val next = moved.nextRecurringInstance(today = LocalDate.of(2027, 4, 2), id = "n", createdAt = Instant.EPOCH)!!
        assertEquals(LocalDate.of(2027, 4, 30), next.scheduledDate)
    }

    @Test
    fun finishedLateItIsTheNextStillToCome() {
        val task = series(LocalDate.of(2027, 1, 31), Recurrence(RecurrenceUnit.MONTHLY))
        val next = task.nextRecurringInstance(today = LocalDate.of(2027, 3, 5), id = "n", createdAt = Instant.EPOCH)!!
        assertEquals(LocalDate.of(2027, 3, 31), next.scheduledDate)
    }

    @Test
    fun finishedEarlyItIsTheOneAfter() {
        val task = series(LocalDate.of(2027, 1, 31), Recurrence(RecurrenceUnit.MONTHLY))
        val next = task.nextRecurringInstance(today = LocalDate.of(2027, 1, 20), id = "n", createdAt = Instant.EPOCH)!!
        assertEquals(LocalDate.of(2027, 2, 28), next.scheduledDate)
    }

    // --- Other rules are untouched -------------------------------------------------------------------

    /** Exactly the occurrence counted from this one, as before the fix, and no day kept. */
    private fun assertUnchanged(rule: Recurrence, first: LocalDate, today: LocalDate) {
        val task = series(first, rule)
        val next = task.nextRecurringInstance(today = today, id = "n", createdAt = Instant.EPOCH)!!

        assertNull(task.recurrenceAnchor)
        assertNull(next.recurrenceAnchor)
        assertEquals(rule.nextOccurrence(anchor = first, after = today), next.scheduledDate)
    }

    @Test
    fun dailyIsUnchanged() = assertUnchanged(Recurrence(RecurrenceUnit.DAILY), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 1, 31))

    @Test
    fun weeklyIsUnchanged() {
        assertUnchanged(Recurrence(RecurrenceUnit.WEEKLY), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 1, 31))
        assertUnchanged(
            Recurrence(RecurrenceUnit.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            LocalDate.of(2027, 1, 28),
            LocalDate.of(2027, 1, 28)
        )
    }

    @Test
    fun aWeekdayOfTheMonthIsUnchanged() {
        assertUnchanged(
            Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(MonthlyWeekday.Last, DayOfWeek.FRIDAY)),
            LocalDate.of(2027, 1, 29),
            LocalDate.of(2027, 1, 29)
        )
    }

    @Test
    fun onlyARuleOnADateKeepsADay() {
        val day = LocalDate.of(2027, 1, 31)
        assertEquals(MonthDay.of(1, 31), Recurrence(RecurrenceUnit.MONTHLY).anchorFor(day))
        assertEquals(MonthDay.of(1, 31), Recurrence(RecurrenceUnit.YEARLY).anchorFor(day))
        assertNull(Recurrence(RecurrenceUnit.DAILY).anchorFor(day))
        assertNull(Recurrence(RecurrenceUnit.WEEKLY).anchorFor(day))
        assertNull(Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(1, DayOfWeek.MONDAY)).anchorFor(day))
    }

    /**
     * When the series' day is the occurrence's own, nothing changes: every start
     * in two years, finished on its day and a few days late, every interval.
     */
    @Test
    fun onItsOwnDayItIsExactlyTheOldCount() {
        for (unit in listOf(RecurrenceUnit.MONTHLY, RecurrenceUnit.YEARLY)) {
            for (interval in 1..3) {
                val rule = Recurrence(unit, interval = interval)
                var start = LocalDate.of(2027, 1, 1)
                while (start.year < 2029) {
                    for (late in listOf(0L, 3L, 40L)) {
                        val after = start.plusDays(late)
                        assertEquals(
                            "$unit $interval from $start after $after",
                            rule.nextOccurrence(anchor = start, after = after),
                            rule.nextOccurrenceOn(MonthDay.from(start), from = start, after = after)
                        )
                    }
                    start = start.plusDays(1)
                }
            }
        }
    }
}
