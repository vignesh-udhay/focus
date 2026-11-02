package app.tellcat.core.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * A repeating task always has a day. `docs/decisions.md` D-121.
 *
 * Tellcat makes the next occurrence of a series when the last is completed, so a
 * repeating task with no scheduled date sits in Inbox, never comes to Today and
 * never reminds: the series does not run, and nothing says so. Quick Add writes
 * the first day into the title when Repeat is set without one, Task Details
 * gives the task that day when a repeat is set on it, and [giveUndatedRepeatsADay]
 * repairs tasks saved before either did.
 */

/**
 * The first day on or after [from] that this rule falls on, counting the rule
 * from [from] as its anchor.
 *
 * Not [nextOccurrence], which is strictly after its floor: a daily rule set on
 * Monday starts on Monday, and a weekly rule on Monday and Thursday set on a
 * Monday starts that Monday, not on Thursday.
 */
fun Recurrence.firstOccurrenceFrom(from: LocalDate): LocalDate = when {
    hasWeekdays -> if (from.dayOfWeek in weekdays) from else nextOccurrence(anchor = from, after = from)
    hasMonthlyWeekday -> {
        val thisMonth = monthlyWeekday!!.inMonth(YearMonth.from(from))
        if (thisMonth.isBefore(from)) monthlyWeekday.inMonth(YearMonth.from(from).plusMonths(1)) else thisMonth
    }
    // A rule that steps whole periods falls on its anchor.
    else -> from
}

/**
 * This task with a day, if it repeats and has none: the rule's first day, counted
 * from the reminder's day when there is one, otherwise from [today]. Unchanged
 * otherwise, so it is safe on every write.
 */
fun Task.withRepeatDay(today: LocalDate): Task {
    val rule = recurrence ?: return this
    if (scheduledDate != null) return this
    val from = reminderAt?.toLocalDate()?.let { maxOf(it, today) } ?: today
    val first = rule.firstOccurrenceFrom(from)
    // And the day the series keeps, D-122, from the day it starts.
    return copy(scheduledDate = first, recurrenceAnchor = recurrenceAnchor ?: rule.anchorFor(first))
}

/**
 * The rules the Repeat sheet offers in one tap, read from [anchor], the task's
 * day. D-121, after TickTick's list. In order: daily; weekly on the anchor's
 * weekday (D-114's preselection, so the rule the sheet would have built); monthly
 * on its day of the month; monthly on its nth or last weekday, where it is one
 * (D-116); yearly; every weekday. Everything else is Custom.
 */
fun commonRepeats(anchor: LocalDate): List<Recurrence> = buildList {
    add(Recurrence(RecurrenceUnit.DAILY))
    add(Recurrence(RecurrenceUnit.WEEKLY, weekdays = setOf(anchor.dayOfWeek)))
    add(Recurrence(RecurrenceUnit.MONTHLY))
    MonthlyWeekday.choicesFor(anchor).forEach { add(Recurrence(RecurrenceUnit.MONTHLY, monthlyWeekday = it)) }
    add(Recurrence(RecurrenceUnit.YEARLY))
    add(Recurrence(RecurrenceUnit.WEEKLY, weekdays = MondayToFriday))
}

/** Monday to Friday, "Every weekday". */
val MondayToFriday: Set<java.time.DayOfWeek> = java.time.DayOfWeek.entries.take(5).toSet()

/**
 * The open tasks that repeat with no day, each given the day [withRepeatDay]
 * gives it. Empty when there is nothing to repair, which is every start after
 * the first.
 *
 * A step at start rather than a database migration: the first day depends on the
 * rule, and the rule is evaluated here in Kotlin, nowhere in SQL. Running at every
 * start also catches a task restored from an older backup, which a migration that
 * ran once would not.
 */
fun giveUndatedRepeatsADay(tasks: List<Task>, today: LocalDate): List<Task> =
    tasks
        .filter { it.recurrence != null && it.scheduledDate == null && !it.isCompleted && !it.isDeleted }
        .map { it.withRepeatDay(today) }
