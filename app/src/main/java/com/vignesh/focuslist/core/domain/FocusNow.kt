package com.vignesh.focuslist.core.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The rule behind the Focus now card, from `docs/decisions.md` D-012.
 *
 * `PRODUCT.md` opens Today with "What should I do now?" and asks that Today make
 * the answer obvious within seconds. An unlabelled run of rows does not answer
 * it; it leaves the user to work it out. This is the rule that answers it, and
 * it is a pure function so that the answer is testable without a device.
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
 * asserted a task without saying why would be the queue again.
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
    ReminderPassed,

    /**
     * Scheduled for today, carrying no time.
     *
     * The weakest of the three and still a real answer: the user put this on
     * today and said nothing about when, so any moment is as good as another and
     * now is a moment.
     */
    NoTimeToday
}

/** One task, and the reason it is the one to do now. */
data class FocusNow(val task: Task, val reason: FocusNowReason)

/**
 * The task to do now, or null when nothing qualifies.
 *
 * Only the three reasons above qualify. When nothing does the card is absent and
 * the list begins at its first band; there is no fallback to "the first task",
 * because a card that always found something to say would be asserting without
 * grounds, which is the failure D-012 was written to avoid.
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
 * @param now the current moment, passed in like every other query takes [today],
 * so the result is deterministic. A date is not enough here: two of the three
 * reasons turn on the time of day.
 */
fun focusNow(
    tasks: List<Task>,
    today: LocalDate,
    now: LocalDateTime,
    pausedTaskId: String? = null
): FocusNow? {
    val candidates = tasks.mapNotNull { task ->
        focusNowReasonOf(task, today, now, pausedTaskId)?.let { reason -> FocusNow(task, reason) }
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
    today: LocalDate,
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

    // Scheduled for today and saying nothing about when. Overdue work is
    // deliberately not here: it belongs in the Overdue band, where it needs a
    // decision, and promoting it would answer "what should I do now" with
    // something the user already chose not to do.
    if (task.scheduledDate == today && task.reminderAt == null) return FocusNowReason.NoTimeToday

    return null
}
