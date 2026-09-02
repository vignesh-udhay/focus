package com.vignesh.focuslist.core.domain

import java.time.LocalDate

/**
 * The derived task views.
 *
 * Today and Upcoming are queries over [Task.scheduledDate], not states stored
 * on a task. Inbox, Anytime, and Someday read the [TaskPlacement] axis.
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

/**
 * Captured but not yet triaged: the queue to empty.
 *
 * A task counts as untriaged only while it is both in [TaskPlacement.INBOX]
 * and unscheduled. Giving a task a day is a decision, so a scheduled task has
 * been triaged even if its placement never changed, and it belongs to Today or
 * Upcoming rather than here.
 *
 * Completed tasks are excluded, as in Upcoming. Inbox is a queue, and finishing
 * something is one of the ways it leaves.
 *
 * That makes this narrower than [anytimeTasks] and [somedayTasks], which read
 * placement alone. Those are buckets a task sits in; this is a queue it passes
 * through.
 *
 * Newest first, because capture comes in bursts and what was just written down
 * is what the user is still thinking about.
 */
fun inboxTasks(tasks: List<Task>): List<Task> =
    tasks
        .filter { task ->
            !task.isDeleted &&
                !task.isCompleted &&
                task.placement == TaskPlacement.INBOX &&
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
 * Everything sitting in [placement] and still outstanding.
 *
 * Reads placement alone, never scheduling. A task can be both Anytime and
 * scheduled for today, and it belongs in both lists: the two axes are
 * independent, so neither one removes a task from the other's view.
 *
 * Completed tasks are excluded, as in Inbox and Upcoming. These are lists of
 * what could be picked up, and something finished cannot be.
 *
 * Ordered in two groups. Tasks with a day come first, nearest first, because a
 * date is the stronger commitment. Everything else follows, newest first, on
 * the same reasoning as Inbox. Within each group the order the query was given
 * is preserved, so nothing shuffles for equal dates or equal capture times.
 */
private fun placementTasks(tasks: List<Task>, placement: TaskPlacement): List<Task> {
    val (scheduled, unscheduled) = tasks
        .filter { task ->
            !task.isDeleted && !task.isCompleted && task.placement == placement
        }
        .partition { task -> task.scheduledDate != null }

    return scheduled.sortedBy { task -> task.scheduledDate } +
        unscheduled.sortedByDescending { task -> task.createdAt }
}
