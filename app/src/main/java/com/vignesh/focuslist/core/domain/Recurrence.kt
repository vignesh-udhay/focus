package com.vignesh.focuslist.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How often a task comes back.
 *
 * `PRODUCT.md` names recurrence as a property a task may have and recurring
 * tasks as a V1 feature, but does not say what a rule may express. This is the
 * smallest reading of it: four fixed periods, and no interval, no weekday set,
 * no end date, and no occurrence count. They cover the work that actually
 * repeats on a calendar, and each of the others is a column and a control that
 * can be added later without changing what is here.
 *
 * A task with no rule is one that happens once, which is most of them, so the
 * absence of recurrence is null rather than a fifth constant. Nothing has to
 * read `NONE` and mean it.
 *
 * Recurrence is not habits, which `PRODUCT.md` puts out of scope. A recurring
 * task is a task that comes back; a habit is a streak, a chart, and a surface
 * of its own. None of those follow from this.
 */
enum class Recurrence {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

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
 * they did not do.
 *
 * The step count is calculated rather than searched, so a rule anchored years
 * ago costs the same as one anchored yesterday. The loop that follows exists
 * for the month-end case alone, where clamping can leave the estimate one
 * short: from the 31st of January, one month is the 28th of February, which is
 * not after the 28th of February. It runs at most a step or two.
 */
fun Recurrence.nextOccurrence(anchor: LocalDate, after: LocalDate): LocalDate {
    var steps = estimatedSteps(anchor, after).coerceAtLeast(1)
    var next = advance(anchor, steps)

    while (!next.isAfter(after)) {
        steps++
        next = advance(anchor, steps)
    }

    return next
}

/**
 * How many whole periods fit between [anchor] and [after], plus one.
 *
 * An estimate, not an answer: it is exact for days, weeks and years, and can
 * be one short for months when the anchor is a day the target month does not
 * have. [nextOccurrence] corrects for that.
 */
private fun Recurrence.estimatedSteps(anchor: LocalDate, after: LocalDate): Long {
    val unit = when (this) {
        Recurrence.DAILY -> ChronoUnit.DAYS
        Recurrence.WEEKLY -> ChronoUnit.WEEKS
        Recurrence.MONTHLY -> ChronoUnit.MONTHS
        Recurrence.YEARLY -> ChronoUnit.YEARS
    }

    return unit.between(anchor, after) + 1
}

/** [anchor] moved on by [steps] whole periods of this rule. */
private fun Recurrence.advance(anchor: LocalDate, steps: Long): LocalDate = when (this) {
    Recurrence.DAILY -> anchor.plusDays(steps)
    Recurrence.WEEKLY -> anchor.plusWeeks(steps)
    Recurrence.MONTHLY -> anchor.plusMonths(steps)
    Recurrence.YEARLY -> anchor.plusYears(steps)
}

/**
 * The instance that follows this one, or null when the task does not recur.
 *
 * Completing a recurring task finishes that occurrence and starts the next.
 * The finished one keeps its `completedAt` and its place in the Logbook, so
 * the record of having done the work on Monday survives the work coming back
 * on Thursday. This builds the copy that comes back.
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
 * be started stays that way next time round.
 */
fun Task.nextRecurringInstance(today: LocalDate, id: String, createdAt: Instant): Task? {
    val rule = recurrence ?: return null

    val anchor = scheduledDate ?: today
    val nextScheduled = rule.nextOccurrence(anchor = anchor, after = today)
    val shift = ChronoUnit.DAYS.between(anchor, nextScheduled)

    return copy(
        id = id,
        createdAt = createdAt,
        scheduledDate = nextScheduled,
        dueDate = dueDate?.plusDays(shift),
        // The next occurrence is outstanding, whatever happened to this one.
        completedAt = null,
        deletedAt = null
    )
}
