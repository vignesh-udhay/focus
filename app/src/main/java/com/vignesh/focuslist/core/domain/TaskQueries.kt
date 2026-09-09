package com.vignesh.focuslist.core.domain

import java.time.LocalDate

/**
 * The derived task views.
 *
 * Today, Inbox, and Upcoming are queries over [Task.scheduledDate], not states
 * stored on a task.
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
 * database result: what has slipped, then today's untimed work, then today's
 * timed work, then what is already done. That is a timeline, read the way a
 * timeline reads. Within each group the caller's order is preserved, because
 * nothing here knows better than the order it was given.
 */
fun todayTasks(tasks: List<Task>, today: LocalDate): List<Task> =
    tasks
        .filter { task ->
            val scheduled = task.scheduledDate
            !task.isDeleted && scheduled != null && !scheduled.isAfter(today)
        }
        // sortedBy is stable, which is what preserves input order within a group.
        .sortedBy { task -> todayGroup(task, today) }

/** Scheduled before today, still outstanding, and needing a decision. */
private const val OVERDUE = 0

/** Today, with no time on it. Do it whenever. */
private const val NO_TIME_SET = 1

/** Today, with a time. It will announce itself. */
private const val LATER_TODAY = 2

/** Done, whenever it was scheduled. */
private const val COMPLETED = 3

/**
 * Which band of the Today list a task belongs to.
 *
 * Four bands since `docs/decisions.md` D-012, and in this order: Overdue, No
 * time set, Later today, Completed.
 *
 * **Overdue is first because the bands are a timeline**, and past, present,
 * future is the order a timeline reads in. More than that: an overdue task is
 * the one thing on this screen that represents the product's defining failure, a
 * reminder that fired and was not acted on. Placing it below work scheduled for
 * later in the day says the opposite of what `PRODUCT.md` says about
 * reliability. The counter-argument, that opening on overdue work is a guilt
 * list, was weighed and is answered by the Focus now card sitting above all of
 * it, so the screen still opens on what to do rather than on what was missed.
 *
 * **Today's two bands are told apart by whether the task carries a reminder**,
 * which is what "it will announce itself" means. A task with a time does not
 * need to be remembered, because the app will say; a task without one is only
 * ever done because the user looked.
 *
 * Completion is checked first, so a completed task sinks to the bottom whether
 * it was scheduled for today or is overdue.
 */
private fun todayGroup(task: Task, today: LocalDate): Int = when {
    task.isCompleted -> COMPLETED
    task.scheduledDate != today -> OVERDUE
    task.reminderAt == null -> NO_TIME_SET
    else -> LATER_TODAY
}

/**
 * The four groups [todayTasks] already sorts into.
 *
 * The ordering has always existed; naming it lets Today label the groups
 * instead of presenting one run of rows whose order the user has to infer.
 * This adds no filtering and changes no order.
 *
 * There were three of these before D-012, and the first was unlabelled on the
 * argument that "at the top of the Today screen, today's work needs no
 * announcement". The band order changed, so the first band is no longer the one
 * that needs no announcement, and the argument retired with the position. Every
 * band carries a label now.
 */
enum class TodayBand {

    /** Scheduled before today, still outstanding. Needs a decision. */
    OVERDUE,

    /** Today, with no time on it. Do it whenever. */
    NO_TIME_SET,

    /** Today, with a time. It will announce itself. */
    LATER_TODAY,

    /** Done, whenever it was scheduled. */
    COMPLETED
}

/** One band and the tasks in it, in the order [todayTasks] produced them. */
data class TodaySection(val band: TodayBand, val tasks: List<Task>)

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
 *
 * **There was a `promotedTaskId`, and D-048 removed it.** D-012 took the Focus
 * now card's task out of its band so it was never on screen twice. That was right
 * for the card D-012 described, which was a task drawn large with a checkbox on
 * it. The paused session card is not one: it cannot complete a task and it
 * asserts nothing about priority, so it and the row are not the same task twice.
 * Every task Today holds is now in exactly one band, with no exceptions to carry.
 */
fun todaySections(
    tasks: List<Task>,
    today: LocalDate
): List<TodaySection> =
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
    OVERDUE -> TodayBand.OVERDUE
    NO_TIME_SET -> TodayBand.NO_TIME_SET
    LATER_TODAY -> TodayBand.LATER_TODAY
    else -> TodayBand.COMPLETED
}

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
 * List membership derives from [Task.scheduledDate]. Giving a task a day moves
 * it to Today or Upcoming; removing that day returns it here.
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

/**
 * Everything finished, and still recoverable.
 *
 * The counterpart to every other list: they show what is outstanding, this
 * shows what is done. Scheduling is ignored entirely, so a completed task is
 * reachable here whatever its other fields say, and completing something can
 * never put it beyond reach.
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
