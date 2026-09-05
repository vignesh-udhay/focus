package com.vignesh.focuslist.core.domain

import java.time.LocalDate

/**
 * The derived task views.
 *
 * Today, Inbox, and Upcoming are queries over [Task.scheduledDate], not states
 * stored on a task. The legacy Anytime and Someday query helpers remain until
 * [TaskPlacement] is removed from persistence in a separately reviewed step.
 *
 * Every query takes the current date explicitly rather than reading the clock,
 * so results are deterministic and testable.
 */

/**
 * Tasks to work on now: everything scheduled for today or earlier, so nothing
 * overdue can quietly disappear.
 *
 * Completed tasks stay in the result. Today shows what was finished alongside
 * what is left.
 *
 * The result is ordered so the list reads as a deliberate plan rather than a
 * database result: today's work first, then what has slipped, then what is
 * already done. Within each of those groups the caller's order is preserved,
 * because nothing here knows better than the order it was given.
 */
fun todayTasks(tasks: List<Task>, today: LocalDate): List<Task> =
    tasks
        .filter { task ->
            val scheduled = task.scheduledDate
            !task.isDeleted && scheduled != null && !scheduled.isAfter(today)
        }
        // sortedBy is stable, which is what preserves input order within a group.
        .sortedBy { task -> todayGroup(task, today) }

/** Scheduled for today, still outstanding. */
private const val SCHEDULED_TODAY = 0

/** Scheduled before today, still outstanding. */
private const val OVERDUE = 1

/** Done, whenever it was scheduled. */
private const val COMPLETED = 2

/**
 * Which band of the Today list a task belongs to.
 *
 * Completion is checked first, so a completed task sinks to the bottom whether
 * it was scheduled for today or is overdue.
 */
private fun todayGroup(task: Task, today: LocalDate): Int = when {
    task.isCompleted -> COMPLETED
    task.scheduledDate == today -> SCHEDULED_TODAY
    else -> OVERDUE
}

/**
 * The three groups [todayTasks] already sorts into.
 *
 * The ordering has always existed; naming it lets Today label the groups
 * instead of presenting one run of rows whose order the user has to infer.
 * This adds no filtering and changes no order.
 */
enum class TodayBand {

    /** Scheduled for today, still outstanding. */
    SCHEDULED,

    /** Scheduled before today, still outstanding. */
    OVERDUE,

    /** Done, whenever it was scheduled. */
    COMPLETED
}

/** One band and the tasks in it, in the order [todayTasks] produced them. */
data class TodaySection(val band: TodayBand, val tasks: List<Task>)

/**
 * Total estimated minutes still to do today, or null when nothing says.
 *
 * Counts every outstanding task the Today view shows, which means overdue work
 * as well as work scheduled for today: both are on the plate, and a total that
 * quietly omitted the overdue half would understate the day. Completed tasks
 * are excluded because the number answers "how much is left", not "how much was
 * there".
 *
 * Null rather than zero when no outstanding task carries an estimate. Zero and
 * "unknown" are different facts, and the header shows nothing for the second
 * rather than claiming a day with no work in it.
 */
fun todayPlannedMinutes(tasks: List<Task>, today: LocalDate): Int? {
    val estimates = todayTasks(tasks, today)
        .filterNot(Task::isCompleted)
        .mapNotNull(Task::estimatedDurationMinutes)

    return if (estimates.isEmpty()) null else estimates.sum()
}

/**
 * [todayTasks], split at the points where its band changes.
 *
 * A reading of the existing order rather than a second sort: the tasks arrive
 * grouped because [todayTasks] sorted them that way, so this walks them once
 * and cuts where the band changes. Concatenating the sections returns exactly
 * what [todayTasks] returned.
 *
 * Empty bands produce no section, so a day with nothing overdue has no empty
 * heading to explain.
 */
fun todaySections(tasks: List<Task>, today: LocalDate): List<TodaySection> =
    todayTasks(tasks, today)
        .fold(mutableListOf<Pair<TodayBand, MutableList<Task>>>()) { sections, task ->
            val band = todayBandOf(task, today)
            val current = sections.lastOrNull()

            if (current != null && current.first == band) {
                current.second += task
            } else {
                sections += band to mutableListOf(task)
            }

            sections
        }
        .map { (band, tasks) -> TodaySection(band, tasks) }

/** Which band [task] falls into, by the same rule [todayTasks] sorts on. */
fun todayBandOf(task: Task, today: LocalDate): TodayBand = when (todayGroup(task, today)) {
    SCHEDULED_TODAY -> TodayBand.SCHEDULED
    OVERDUE -> TodayBand.OVERDUE
    else -> TodayBand.COMPLETED
}

/**
 * Today's work, still outstanding: what Focus can be pointed at.
 *
 * Defined over [todayTasks] rather than beside it, so Focus follows Today's
 * plan by construction rather than through a second filter that agrees with it
 * until one of the two is edited. The ordering is Today's, unchanged.
 *
 * Completion is the only thing this drops. Today keeps a finished task in its
 * bottom band as a record of the session, but Focus is for working on one task
 * and a finished task cannot be worked on.
 *
 * This is a strict subset of [todayTasks], which is what keeps it safe: Focus
 * is never the only place a task can be found, so nothing is reachable from
 * here and nowhere else.
 */
fun focusQueue(tasks: List<Task>, today: LocalDate): List<Task> =
    todayTasks(tasks, today).filter { task -> !task.isCompleted }

/**
 * Tasks scheduled beyond today, and still outstanding.
 *
 * Unlike Today, this excludes completed tasks. A finished task is not something
 * that is coming up.
 *
 * Ordered nearest first, because the only question this view answers is what is
 * coming next. Tasks sharing a date keep the order they were given.
 */
fun upcomingTasks(tasks: List<Task>, today: LocalDate): List<Task> =
    tasks
        .filter { task ->
            val scheduled = task.scheduledDate
            !task.isDeleted && !task.isCompleted && scheduled != null && scheduled.isAfter(today)
        }
        // Every task here has a scheduled date; the filter saw to that.
        .sortedBy { task -> task.scheduledDate }

/** One scheduled day and the tasks on it, in the order [upcomingTasks] gave them. */
data class UpcomingSection(val date: LocalDate, val tasks: List<Task>)

/**
 * [upcomingTasks], split where the scheduled day changes.
 *
 * A reading of the existing order rather than a second sort. [upcomingTasks]
 * already sorts by date, so this walks the result once and cuts where the day
 * changes; concatenating the sections returns exactly what it returned.
 *
 * The same shape as [todaySections], and for the same reason: the ordering is
 * owned here, and a screen that re-derived it would be free to disagree.
 */
fun upcomingSections(tasks: List<Task>, today: LocalDate): List<UpcomingSection> =
    upcomingTasks(tasks, today)
        .fold(mutableListOf<Pair<LocalDate, MutableList<Task>>>()) { sections, task ->
            // Every task here has a scheduled date; upcomingTasks saw to that.
            val date = task.scheduledDate!!
            val current = sections.lastOrNull()

            if (current != null && current.first == date) {
                current.second += task
            } else {
                sections += date to mutableListOf(task)
            }

            sections
        }
        .map { (date, tasks) -> UpcomingSection(date, tasks) }

/**
 * Everything outstanding without a scheduled day.
 *
 * List membership derives from [Task.scheduledDate], not [Task.placement].
 * Giving a task a day moves it to Today or Upcoming; removing that day returns
 * it here. Ignoring placement also keeps legacy Anytime and Someday tasks
 * reachable after those destinations are removed.
 *
 * Completed tasks are excluded, as in Upcoming. Inbox is a queue, and finishing
 * something is one of the ways it leaves.
 *
 * Newest first, because capture comes in bursts and what was just written down
 * is what the user is still thinking about.
 */
fun inboxTasks(tasks: List<Task>): List<Task> =
    tasks
        .filter { task ->
            !task.isDeleted &&
                !task.isCompleted &&
                task.scheduledDate == null
        }
        .sortedByDescending { task -> task.createdAt }

/** Triaged and actionable. */
fun anytimeTasks(tasks: List<Task>): List<Task> =
    placementTasks(tasks, TaskPlacement.ANYTIME)

/** Triaged and deliberately deferred. */
fun somedayTasks(tasks: List<Task>): List<Task> =
    placementTasks(tasks, TaskPlacement.SOMEDAY)

/**
 * Everything finished, and still recoverable.
 *
 * The counterpart to every other list: they show what is outstanding, this
 * shows what is done. Placement and scheduling are ignored entirely, so a
 * completed task is reachable here whatever its other fields say, and
 * completing something can never put it beyond reach.
 *
 * Deleted tasks stay out, as everywhere else. Deletion has its own undo, and a
 * task that was completed and then deleted is deleted.
 *
 * Newest first, by when the task was completed rather than when it was made,
 * because this list is read as a record of what just happened.
 */
fun completedTasks(tasks: List<Task>): List<Task> =
    tasks
        .filter { task -> !task.isDeleted && task.isCompleted }
        // Every task here has a completion time; the filter saw to that.
        .sortedByDescending { task -> task.completedAt }

/**
 * Everything sitting in [placement], still outstanding, and without a day.
 *
 * The date rule is the same one Inbox applies, and applying it here is a
 * reversal. These lists used to read placement alone, on the reasoning that
 * placement and scheduling are independent axes and neither should remove a
 * task from the other's view. What that produced was a task appearing in Today
 * and in Anytime at once, and a Someday task scheduled for this afternoon,
 * which is the list calling something deliberately deferred while the calendar
 * calls it due.
 *
 * Giving a task a day is the decision these queries are waiting for. Once it
 * has one it belongs to Today or Upcoming. Their undated tasks also appear in
 * Inbox so legacy placement values cannot make work unreachable.
 *
 * Completed tasks are excluded, as in Inbox and Upcoming. These are lists of
 * what could be picked up, and something finished cannot be.
 *
 * Newest first, on the same reasoning as Inbox. The two-group ordering that
 * used to put dated tasks ahead of the rest went with the dated tasks.
 */
private fun placementTasks(tasks: List<Task>, placement: TaskPlacement): List<Task> =
    tasks
        .filter { task ->
            !task.isDeleted &&
                !task.isCompleted &&
                task.placement == placement &&
                task.scheduledDate == null
        }
        .sortedByDescending { task -> task.createdAt }
