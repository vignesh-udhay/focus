package com.vignesh.focuslist.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The period a rule steps by.
 *
 * These four constants keep the names the four-value `Recurrence` enum used,
 * even though the sheet now labels them Day, Week, Month and Year. The label is
 * a string resource; the constant is what is written in the `recurrence` column
 * of every install, and `docs/decisions.md` D-027 keeps schema version 10 from
 * rewriting a single existing row.
 */
enum class RecurrenceUnit {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

/**
 * When a repeating task stops repeating.
 *
 * Three cases and no fourth. [Never] carries nothing, which is why this is a
 * sealed interface with an object rather than a nullable date beside a nullable
 * count: "no end" is a state the type can hold rather than an absence two
 * columns have to agree about.
 */
sealed interface RecurrenceEnd {

    /** The series runs until the user stops it. The default. */
    data object Never : RecurrenceEnd

    /** The last occurrence is on or before [date]. */
    data class OnDate(val date: LocalDate) : RecurrenceEnd

    /**
     * The series holds [count] occurrences in total, the first one included.
     *
     * Counted from [Task.occurrenceNumber] rather than by walking the chain of
     * spawns. D-019 raised this as the thing that had to be answered before an
     * end condition could exist, and D-027 answers it: the chain is walkable
     * only until someone deletes a link, and a stored position is not.
     */
    data class AfterOccurrences(val count: Int) : RecurrenceEnd
}

/**
 * How often a task comes back.
 *
 * `PRODUCT.md` names recurrence as a property a task may have and recurring
 * tasks as a V1 feature, but does not say what a rule may express. This is the
 * board's answer, built under `docs/decisions.md` D-027: a period, how many of
 * them to step, which weekdays inside the week count, and when to stop.
 *
 * A task with no rule is one that happens once, which is most of them, so the
 * absence of recurrence is null rather than a fifth unit. Nothing has to read
 * `NONE` and mean it.
 *
 * Recurrence is not habits, which `PRODUCT.md` puts out of scope. A recurring
 * task is a task that comes back; a habit is a streak, a chart, and a surface
 * of its own. None of those follow from this.
 *
 * @param unit the period the rule steps by.
 * @param interval how many periods each step covers. One is every period, two
 * is every other. Values below one are meaningless and are read as one rather
 * than rejected, on the same terms `TaskConverters` states: a rule that reads
 * oddly is recoverable, and throwing while reading the database is not.
 * @param weekdays which days inside an on-week the task falls on, for
 * [RecurrenceUnit.WEEKLY] only. Empty means the day the task is anchored to,
 * so clearing every chip cannot leave a rule that matches no day at all.
 * Ignored by the other three units, which have no weekday axis.
 * @param end when the series stops.
 */
data class Recurrence(
    val unit: RecurrenceUnit,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val end: RecurrenceEnd = RecurrenceEnd.Never
) {

    /**
     * Whether this rule picks days inside the week rather than stepping whole
     * periods.
     *
     * The two cases evaluate differently, and both the evaluator and the sheet
     * ask the same question of the same property rather than each testing the
     * unit and the set for themselves.
     */
    val hasWeekdays: Boolean
        get() = unit == RecurrenceUnit.WEEKLY && weekdays.isNotEmpty()
}

/**
 * Weeks are counted from Monday.
 *
 * A fixed reference point, not a display convention. "Every two weeks on Monday
 * and Thursday" has to agree with itself about which weeks are the on-weeks, and
 * a locale-dependent week start would move the rule when the device's locale
 * changed. Which day the chips are drawn from is the UI's question and is
 * answered separately.
 */
private val WeekStart = DayOfWeek.MONDAY

/**
 * The first occurrence of this rule that falls strictly after [after],
 * counting from [anchor].
 *
 * Anchored on the original date rather than on the last completion, so a task
 * scheduled for the 1st stays on the 1st however late it is finished. Every
 * step is measured from [anchor], never from the previous result, which is
 * what keeps a monthly task on the 31st through February instead of walking it
 * back to the 28th and leaving it there.
 *
 * Strictly after [after], so finishing a week late produces the next date
 * still to come rather than one already gone. Missing an occurrence does not
 * bank it: nobody is served by opening the app to four copies of a daily task
 * they did not do. The interval does not change this.
 *
 * The step count is calculated rather than searched in both branches, so a rule
 * anchored years ago costs the same as one anchored yesterday.
 */
fun Recurrence.nextOccurrence(anchor: LocalDate, after: LocalDate): LocalDate =
    if (hasWeekdays) {
        nextWeekdayOccurrence(anchor = anchor, after = after)
    } else {
        nextPeriodOccurrence(anchor = anchor, after = after)
    }

/**
 * The next date for a rule that steps whole periods.
 *
 * Only multiples of [Recurrence.interval] are occurrences, so the estimate lands
 * on a block boundary rather than on the next period. The loop that follows
 * exists for the month-end case alone, where clamping can leave the estimate one
 * short: from the 31st of January, one month is the 28th of February, which is
 * not after the 28th of February. It runs at most a step or two.
 */
private fun Recurrence.nextPeriodOccurrence(anchor: LocalDate, after: LocalDate): LocalDate {
    val step = steps()
    // At least one block, so completing a task early produces the following
    // occurrence rather than the one just finished.
    var blocks = (unit.chronoUnit.between(anchor, after) / step + 1).coerceAtLeast(1)
    var next = unit.advance(anchor, blocks * step)

    while (!next.isAfter(after)) {
        blocks++
        next = unit.advance(anchor, blocks * step)
    }

    return next
}

/**
 * The next date for a weekly rule that names its days.
 *
 * On-weeks are whole multiples of [Recurrence.interval] from the Monday of the
 * anchor's week, and inside an on-week every selected day is an occurrence. So a
 * task anchored to a Wednesday with Monday, Wednesday and Friday selected comes
 * back on Friday of the same week, which is the behaviour the weekday set exists
 * for and the one thing it does that the plain period cannot.
 *
 * The floor is the later of [after] and [anchor]. A task completed before the
 * day it was scheduled for should produce the occurrence after that day, not one
 * earlier in the same week that the series has not reached yet.
 *
 * The block is calculated and the scan is local, so the cost does not depend on
 * how old the anchor is. The loop runs at most twice: if no selected day in the
 * first block clears the floor, the next block starts strictly after it.
 */
private fun Recurrence.nextWeekdayOccurrence(anchor: LocalDate, after: LocalDate): LocalDate {
    val step = steps()
    val floor = maxOf(after, anchor)

    val anchorWeek = anchor.with(TemporalAdjusters.previousOrSame(WeekStart))
    val floorWeek = floor.with(TemporalAdjusters.previousOrSame(WeekStart))

    val weeksOut = ChronoUnit.WEEKS.between(anchorWeek, floorWeek).coerceAtLeast(0)
    // Rounded up to a whole block, so the scan starts on an on-week.
    var blocks = (weeksOut + step - 1) / step
    val days = weekdays.sortedBy(DayOfWeek::getValue)

    while (true) {
        val weekStart = anchorWeek.plusWeeks(blocks * step)

        days.forEach { day ->
            // `weekStart` is a Monday and `value` is 1 through 7, so this is the
            // day inside that week rather than a search from it.
            val candidate = weekStart.plusDays((day.value - 1).toLong())
            if (candidate.isAfter(floor)) return candidate
        }

        blocks++
    }
}

/**
 * The interval as a step count, never below one.
 *
 * Storage can hold anything, and a zero here would step nowhere forever.
 */
private fun Recurrence.steps(): Long = interval.coerceAtLeast(1).toLong()

/** The unit this period is measured in. */
private val RecurrenceUnit.chronoUnit: ChronoUnit
    get() = when (this) {
        RecurrenceUnit.DAILY -> ChronoUnit.DAYS
        RecurrenceUnit.WEEKLY -> ChronoUnit.WEEKS
        RecurrenceUnit.MONTHLY -> ChronoUnit.MONTHS
        RecurrenceUnit.YEARLY -> ChronoUnit.YEARS
    }

/** [anchor] moved on by [steps] whole periods of this unit. */
private fun RecurrenceUnit.advance(anchor: LocalDate, steps: Long): LocalDate = when (this) {
    RecurrenceUnit.DAILY -> anchor.plusDays(steps)
    RecurrenceUnit.WEEKLY -> anchor.plusWeeks(steps)
    RecurrenceUnit.MONTHLY -> anchor.plusMonths(steps)
    RecurrenceUnit.YEARLY -> anchor.plusYears(steps)
}

/**
 * Whether a series holding [occurrence] occurrences so far may produce another.
 *
 * Checked before the date is calculated, because a count that has run out ends
 * the series whatever the calendar says.
 */
private fun Recurrence.hasRoomAfter(occurrence: Int): Boolean = when (val stop = end) {
    is RecurrenceEnd.AfterOccurrences -> occurrence < stop.count
    else -> true
}

/**
 * Whether [date] is still inside the series.
 *
 * Checked after the date is calculated, because an end date is a question about
 * the occurrence that would come next rather than about how many there have
 * been.
 */
private fun Recurrence.covers(date: LocalDate): Boolean = when (val stop = end) {
    is RecurrenceEnd.OnDate -> !date.isAfter(stop.date)
    else -> true
}

/**
 * The instance that follows this one, or null when the task does not recur.
 *
 * Completing a recurring task finishes that occurrence and starts the next.
 * The finished one keeps its `completedAt` and its place in the Logbook, so
 * the record of having done the work on Monday survives the work coming back
 * on Thursday. This builds the copy that comes back.
 *
 * Null also means the series has ended, which is new with `docs/decisions.md`
 * D-027 and is why the two end conditions are checked here rather than in the
 * evaluator: `nextOccurrence` answers when, and this answers whether.
 *
 * [id] and [createdAt] are supplied rather than generated, on the same terms
 * as the rest of the domain: nothing here reads a clock or a random source, so
 * the rule is deterministic and testable without either.
 *
 * The anchor is the task's own scheduled date, falling back to [today] for a
 * recurring task that never had one. Everything else about the task is carried
 * across untouched, including the rule itself, so the series continues.
 *
 * A due date moves by the same number of days as the scheduled date, which
 * keeps whatever gap the two had. A task due three days after it is meant to
 * be started stays that way next time round. A reminder moves the same way,
 * keeping its time of day, and arrives on the next occurrence not yet
 * announced.
 */
fun Task.nextRecurringInstance(today: LocalDate, id: String, createdAt: Instant): Task? {
    val rule = recurrence ?: return null
    if (!rule.hasRoomAfter(occurrenceNumber)) return null

    val anchor = scheduledDate ?: today
    val nextScheduled = rule.nextOccurrence(anchor = anchor, after = today)
    if (!rule.covers(nextScheduled)) return null

    val shift = ChronoUnit.DAYS.between(anchor, nextScheduled)

    return copy(
        id = id,
        createdAt = createdAt,
        scheduledDate = nextScheduled,
        dueDate = dueDate?.plusDays(shift),
        // The reminder moves with the task, by the same shift and at the same
        // time of day, on the same reasoning as the due date: whatever
        // relationship the two had is the one the series should keep.
        reminderAt = reminderAt?.plusDays(shift),
        // And it has not been announced. Carrying the old delivery record
        // across left every occurrence after the first already marked
        // delivered, so a daily reminder fired once and then went silent for
        // good, which is the most severe failure this product has.
        reminderDeliveredAt = null,
        // Where this one sits in the series, which is what an "after N" rule
        // counts. Taken from the occurrence that produced it rather than
        // recomputed, so deleting an earlier copy cannot move it.
        occurrenceNumber = occurrenceNumber + 1,
        // Which occurrence produced this one. Reopening that occurrence uses
        // it to find this copy again and take it back.
        spawnedFromId = this.id,
        // The next occurrence is outstanding, whatever happened to this one.
        completedAt = null,
        deletedAt = null
    )
}
