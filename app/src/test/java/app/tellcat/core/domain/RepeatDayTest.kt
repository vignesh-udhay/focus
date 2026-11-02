package app.tellcat.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A repeating task always has a day, and the Repeat sheet's common rules.
 * `docs/decisions.md` D-121.
 */
class RepeatDayTest {

    // A Monday. November 2026: Fridays fall on the 6th, 13th, 20th and 27th.
    private val monday: LocalDate = LocalDate.of(2026, 11, 2)

    private fun task(
        recurrence: Recurrence? = Recurrence(RecurrenceUnit.DAILY),
        scheduledDate: LocalDate? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = Task(
        id = "t",
        title = "Water the plants",
        createdAt = Instant.EPOCH,
        recurrence = recurrence,
        scheduledDate = scheduledDate,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    // --- The first day a rule falls on --------------------------------------------

    @Test
    fun aPeriodRuleStartsOnItsAnchor() {
        assertEquals(monday, Recurrence(RecurrenceUnit.DAILY).firstOccurrenceFrom(monday))
        assertEquals(monday, Recurrence(RecurrenceUnit.MONTHLY).firstOccurrenceFrom(monday))
        assertEquals(monday, Recurrence(RecurrenceUnit.YEARLY).firstOccurrenceFrom(monday))
    }

    /** Not strictly after, as the next occurrence is: a Monday rule set on a Monday starts that day. */
    @Test
    fun aWeekdayRuleStartsTodayWhenTodayIsOneOfItsDays() {
        val rule = Recurrence(RecurrenceUnit.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(monday, rule.firstOccurrenceFrom(monday))
    }

    @Test
    fun aWeekdayRuleOtherwiseStartsOnItsNextDay() {
        val rule = Recurrence(RecurrenceUnit.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(monday.plusDays(3), rule.firstOccurrenceFrom(monday.plusDays(1)))
    }

    @Test
    fun theLastFridayStartsOnThisMonthsLastFriday() {
        val rule = Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(MonthlyWeekday.Last, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 11, 27), rule.firstOccurrenceFrom(monday))
        assertEquals(LocalDate.of(2026, 11, 27), rule.firstOccurrenceFrom(LocalDate.of(2026, 11, 27)))
    }

    @Test
    fun theLastFridayAlreadyGoneStartsNextMonth() {
        val rule = Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(MonthlyWeekday.Last, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 12, 25), rule.firstOccurrenceFrom(LocalDate.of(2026, 11, 28)))
    }

    // --- A repeating task always has a day ------------------------------------------

    @Test
    fun anUndatedRepeatGetsItsFirstDay() {
        assertEquals(monday, task().withRepeatDay(monday).scheduledDate)
    }

    @Test
    fun anUndatedRepeatOnThursdaysGetsThursday() {
        val rule = Recurrence(RecurrenceUnit.WEEKLY, weekdays = setOf(DayOfWeek.THURSDAY))
        assertEquals(monday.plusDays(3), task(recurrence = rule).withRepeatDay(monday).scheduledDate)
    }

    @Test
    fun aDatedRepeatKeepsItsDay() {
        val dated = task(scheduledDate = monday.plusDays(10))
        assertSame(dated, dated.withRepeatDay(monday))
    }

    @Test
    fun aTaskThatDoesNotRepeatStaysUndated() {
        assertNull(task(recurrence = null).withRepeatDay(monday).scheduledDate)
    }

    @Test
    fun aReminderOnAnUndatedRepeatGivesItItsDay() {
        val withReminder = task().copy(reminderAt = monday.plusDays(4).atTime(9, 0))
        assertEquals(monday.plusDays(4), withReminder.withRepeatDay(monday).scheduledDate)
    }

    // --- The repair at start ----------------------------------------------------------

    @Test
    fun theRepairGivesOnlyOpenUndatedRepeatsADay() {
        val tasks = listOf(
            task().copy(id = "undated"),
            task(scheduledDate = monday.plusDays(1)).copy(id = "dated"),
            task(recurrence = null).copy(id = "plain"),
            task(completedAt = Instant.EPOCH).copy(id = "done"),
            task(deletedAt = Instant.EPOCH).copy(id = "deleted")
        )

        val repaired = giveUndatedRepeatsADay(tasks, monday)

        assertEquals(listOf("undated"), repaired.map(Task::id))
        assertEquals(monday, repaired.single().scheduledDate)
    }

    @Test
    fun theRepairHasNothingToDoTheSecondTime() {
        val once = giveUndatedRepeatsADay(listOf(task()), monday)
        assertTrue(giveUndatedRepeatsADay(once, monday).isEmpty())
    }

    // --- The common rules ----------------------------------------------------------------

    @Test
    fun theCommonRulesAreReadFromTheDay() {
        val rules = commonRepeats(monday)

        assertEquals(Recurrence(RecurrenceUnit.DAILY), rules.first())
        assertTrue(Recurrence(RecurrenceUnit.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY)) in rules)
        assertTrue(Recurrence(RecurrenceUnit.MONTHLY) in rules)
        assertTrue(Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(1, DayOfWeek.MONDAY)) in rules)
        assertTrue(Recurrence(RecurrenceUnit.YEARLY) in rules)
        assertEquals(Recurrence(RecurrenceUnit.WEEKLY, weekdays = MondayToFriday), rules.last())
    }

    /** The 27th is both the fourth Friday and the last, D-116, so both are offered. */
    @Test
    fun aLastFridayOffersTheLastFriday() {
        val rules = commonRepeats(LocalDate.of(2026, 11, 27))
        assertTrue(Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(4, DayOfWeek.FRIDAY)) in rules)
        assertTrue(Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = MonthlyWeekday(MonthlyWeekday.Last, DayOfWeek.FRIDAY)) in rules)
    }

    @Test
    fun theFirstMondayIsNotTheLast() {
        val rules = commonRepeats(monday)
        assertFalse(rules.any { it.monthlyWeekday?.isLast == true })
    }

    @Test
    fun everyWeekdayIsMondayToFriday() {
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            MondayToFriday
        )
    }
}
