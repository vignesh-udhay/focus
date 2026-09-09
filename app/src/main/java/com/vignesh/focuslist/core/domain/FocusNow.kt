package com.vignesh.focuslist.core.domain

import java.time.LocalDateTime

/**
 * The rule behind the Focus now card, from `docs/decisions.md` D-012 as amended
 * by D-035.
 *
 * `PRODUCT.md` opens Today with "What should I do now?" and asks that Today make
 * the answer obvious within seconds. An unlabelled run of rows does not answer
 * it; it leaves the user to work it out. This is the rule that answers it, and
 * it is a pure function so that the answer is testable without a device.
 *
 * **The card speaks for an event, not for a position in the list.** D-035 cut a
 * third reason, `NoTimeToday`, which matched any task scheduled for today
 * carrying no time. Under it the card was the first row of the band immediately
 * below it, lifted out and drawn larger, explained by that band's own label, on
 * grounds equally true of every other task in the band. What is left are two
 * things that happened: work was started and paused, or a reminder fired and was
 * not acted on. Neither can be read off a list.
 *
 * **This is not the Focus queue D-004 removed.** The queue was a ranking with
 * nothing to say why its head was its head, and completing the head advanced to
 * the next one. This states its reason on screen, and completing its task
 * re-runs the rule rather than advancing: usually the card then disappears. It
 * cannot chain, because there is nothing to chain through.
 */

/**
 * Why a task is the one to do now.
 *
 * The card shows exactly one of these, in words, on the card. A card that
 * asserted a task without saying why would be the queue again, and per D-035 a
 * reason that could not tell its task apart from the four below it did not clear
 * that bar either.
 *
 * Declared in priority order, and [Comparable] by it, so the rule below sorts on
 * the enum rather than on a second table that could disagree with it.
 */
enum class FocusNowReason {

    /**
     * A focus session was paused and is waiting to be resumed.
     *
     * The strongest reason, and the one D-013 exists to make possible. It wins
     * because the user has already decided this is the task and started work on
     * it; nothing else on the screen has that behind it.
     *
     * It is also what makes leaving a paused session safe. The session survives
     * the sheet closing precisely because this card is pointing at it.
     */
    ResumePaused,

    /**
     * A reminder time has arrived and passed, and the task is still outstanding.
     *
     * The app said it would interrupt at a moment, that moment has been, and the
     * work is not done. `PRODUCT.md` calls a missed reminder the highest
     * severity bug in the product, so a reminder that fired and was not acted on
     * is the next most urgent thing after work already begun.
     */
    ReminderPassed
}

/** One task, and the reason it is the one to do now. */
data class FocusNow(val task: Task, val reason: FocusNowReason)

/**
 * The task to do now, or null when nothing qualifies.
 *
 * Only the two reasons above qualify. When neither does the card is absent and
 * the list begins at its first band; there is no fallback to "the first task",
 * because a card that always found something to say would be asserting without
 * grounds, which is the failure D-012 was written to avoid and the one D-035
 * found it had not fully avoided.
 *
 * Returning null is now the common case, on any day without a passed reminder or
 * a paused session. That is the intent, not a gap: D-035 weighed a card seen
 * rarely against one seen daily on grounds it could not defend, and took the
 * first.
 *
 * **Where several qualify, the reason decides first, then the earliest time,
 * then the order Today already shows.** That is D-012's tie-break verbatim, and
 * the order matters: sorting by time first would let a task with a weak reason
 * outrank one with a strong one.
 *
 * @param tasks every task, unfiltered. The function does its own filtering
 * rather than taking Today's list, because the paused session's task need not be
 * scheduled for today: a task focused from any list and paused is still the
 * thing the user was doing.
 * @param pausedTaskId the task a paused session is on, or null when no session
 * is paused. Passed in rather than read, because a session lives in the view
 * model and this stays a function of data.
 * @param now the current moment, passed in like every other query takes a date,
 * so the result is deterministic. A date would not be enough: the reminder
 * reason turns on the time of day.
 *
 * **There is no `today` parameter, and there used to be.** `NoTimeToday` was the
 * only reason that compared a task's date against the current day, and D-035
 * removed it. Neither surviving reason asks what day it is: a paused session is
 * paused whatever the date, and a reminder that has passed has passed. Keeping
 * the parameter would have claimed a dependency the rule no longer has.
 */
fun focusNow(
    tasks: List<Task>,
    now: LocalDateTime,
    pausedTaskId: String? = null
): FocusNow? {
    val candidates = tasks.mapNotNull { task ->
        focusNowReasonOf(task, now, pausedTaskId)?.let { reason -> FocusNow(task, reason) }
    }

    return candidates.minWithOrNull(
        // The reason first. Then the earliest time, which for a reminder is the
        // reminder and for everything else is nothing, so the untimed sort
        // equal and fall through to the third key. Then the order Today shows,
        // which is the order `tasks` arrived in: `minWith` keeps the first of
        // equals, so nothing more is needed to express it.
        compareBy<FocusNow> { it.reason }
            .thenBy(nullsLast()) { it.task.reminderAt }
    )
}

/**
 * Why [task] qualifies, or null when it does not.
 *
 * The order of the checks is the priority order, and each returns rather than
 * falling through, so one task cannot be counted under two reasons.
 */
private fun focusNowReasonOf(
    task: Task,
    now: LocalDateTime,
    pausedTaskId: String?
): FocusNowReason? {
    // Deleted and completed tasks are not work to do now, whatever else is true
    // of them. Checked first so a paused session on a task that was completed
    // from somewhere else does not keep asserting it.
    if (task.isDeleted || task.isCompleted) return null

    // Whatever list it came from. A task focused from Inbox and paused is still
    // the thing the user was doing, and sending them back to find it would be
    // the app losing their place.
    if (task.id == pausedTaskId) return FocusNowReason.ResumePaused

    val reminderAt = task.reminderAt
    if (reminderAt != null && !reminderAt.isAfter(now)) return FocusNowReason.ReminderPassed

    // A task scheduled for today carrying no time used to match here, and D-035
    // removed it. It stays in the "No time set" band, which is the only place
    // that fact was ever worth stating.
    return null
}
