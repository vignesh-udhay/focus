package com.vignesh.focuslist.core.notification

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.missedReminders
import com.vignesh.focuslist.core.domain.pendingReminders
import com.vignesh.focuslist.core.domain.reminderTrigger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * How a task reminder reaches the user.
 *
 * The counterpart to [FocusAlarms], and deliberately not the same thing.
 * `AndroidFocusAlarms` schedules inexactly, and its KDoc argues well for why:
 * an estimate is a guess, and announcing it to the second claims a precision
 * the number never had. **None of that reasoning carries here.** A reminder is
 * a time the user chose and expects to be kept, so this schedules exactly. See
 * `docs/decisions.md`, D-005.
 *
 * Keyed by task id rather than holding one outstanding alarm, because unlike a
 * focus session there can be any number of reminders owed at once.
 *
 * No title is passed. The receiver reads the task back out of storage when the
 * alarm fires, so a task renamed after its reminder was set announces its new
 * title, and a task completed or deleted in the meantime announces nothing.
 * That is the same rule the scheduler runs on: storage decides, the alarm is a
 * cache of the decision.
 */
interface ReminderAlarms {

    /**
     * Fire for [taskId] at [at], replacing anything already scheduled for it.
     *
     * Replacing rather than adding, so re-running the whole reconciliation is
     * safe and cannot produce two alarms for one task.
     */
    fun schedule(taskId: String, at: Instant)

    /** Drop anything outstanding for [taskId]. A no-op if there is nothing. */
    fun cancel(taskId: String)

    /**
     * Whether the system will honour an exact alarm right now.
     *
     * Reported rather than assumed, because `AGENTS.md` forbids swallowing a
     * scheduling failure: if the app cannot promise a reminder it has to say
     * so. Nothing in this slice surfaces it yet; the Phase 2 health screen is
     * where it becomes visible.
     */
    fun canScheduleExact(): Boolean
}

/**
 * Makes the system's alarms agree with what storage says is owed.
 *
 * The whole reminder subsystem is this one function run again whenever
 * anything might have changed: a task edited, the device restarted, the clock
 * or timezone moved. One code path, several triggers, and no memory of what
 * was scheduled last time. That is what makes it recoverable after a restart,
 * when there is no memory to have.
 *
 * It walks every task rather than only the ones with reminders, so a task that
 * has just lost its reminder, been completed, or been ticked off from the
 * notification gets its alarm cancelled by the same pass that schedules the
 * others. Cancelling something that was never scheduled costs nothing.
 *
 * The clock and zone are injected so the awkward cases are JVM tests. They are
 * read once per pass rather than per task, so a reconciliation cannot straddle
 * midnight and treat two tasks as being on different days.
 */
class ReminderScheduler(
    private val alarms: ReminderAlarms,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Instant = Instant::now
) {

    fun reconcile(tasks: List<Task>) {
        val now = clock()
        val here = zone()
        val localNow = LocalDateTime.ofInstant(now, here)

        // Pending and missed together: both are owed. The trigger time is what
        // separates them, and `reminderTrigger` decides that.
        val owed = (pendingReminders(tasks, localNow) + missedReminders(tasks, localNow))
            .associateBy { task -> task.id }

        tasks.forEach { task ->
            val reminderAt = owed[task.id]?.reminderAt

            if (reminderAt == null) {
                alarms.cancel(task.id)
            } else {
                alarms.schedule(task.id, reminderTrigger(reminderAt, here, now))
            }
        }
    }
}
