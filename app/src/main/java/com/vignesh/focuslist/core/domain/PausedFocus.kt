package com.vignesh.focuslist.core.domain

/**
 * The task a paused focus session is on, from `docs/decisions.md` D-012 as
 * amended by D-035 and narrowed to this by D-048.
 *
 * **This used to be a rule with reasons, and now it is a lookup.** D-012 gave the
 * Focus now card three reasons to appear, D-035 cut one, and D-048 cut the second.
 * What is left cannot be phrased as a choice: there is one thing the card speaks
 * for, so there is no reason to state and nothing to rank. `FocusNowReason` and
 * `FocusNow` are gone with the ranking, because a type that can only express one
 * value is a type pretending to be a decision.
 *
 * **Why this one survived.** A paused session is the single fact about Today that
 * no arrangement of rows can produce. No row can say that work was started on
 * this task and has fifteen minutes left in it. The reason D-048 removed,
 * `ReminderPassed`, named a task that sits in the Overdue band directly below the
 * card, under a label that already says the work is late; the card was restating
 * its neighbour. This one has no neighbour to restate.
 *
 * **It is also what makes leaving a paused session safe.** D-015 pauses the
 * session when the sheet closes rather than stopping it, and the session is
 * recoverable precisely because this card points at it afterwards.
 *
 * **No clock, and no date.** D-035 removed the `today` parameter when it removed
 * the only reason that compared a task's date against the current day. D-048
 * removes `now` for the same reason one entry on: `ReminderPassed` was the only
 * reason that read the time, and a paused session is paused whatever the time. A
 * query that reads no clock cannot be stale, which retires the caveat
 * `TaskListViewModel` carried about the card appearing late.
 *
 * @param tasks every task, unfiltered. The paused session's task need not be
 * scheduled for today: a task focused from any list and paused is still the thing
 * the user was doing.
 * @param pausedTaskId the task a paused session is on, or null when no session is
 * paused. Passed in rather than read, because a session lives in the view model
 * and this stays a function of data.
 */
fun pausedFocusTask(tasks: List<Task>, pausedTaskId: String?): Task? {
    if (pausedTaskId == null) return null

    return tasks.firstOrNull { task -> task.id == pausedTaskId }
        // Deleted and completed tasks are not work to resume, whatever else is
        // true of them. A session paused on a task that was then completed from
        // somewhere else stops being asserted, rather than pointing at finished
        // work until the session is cleared.
        ?.takeUnless { task -> task.isDeleted || task.isCompleted }
}
