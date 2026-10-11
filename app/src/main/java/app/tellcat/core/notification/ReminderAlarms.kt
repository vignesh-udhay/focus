package app.tellcat.core.notification

import app.tellcat.core.domain.Task
import app.tellcat.core.domain.missedReminders
import app.tellcat.core.domain.pendingReminders
import app.tellcat.core.domain.reminderTrigger
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
    private val clock: () -> Instant = Instant::now,
    // Told about every alarm the system refused, so it is written down rather
    // than swallowed. A parameter because this file stays free of Android.
    private val onFailure: (taskId: String, error: Exception) -> Unit = { _, _ -> }
) {

    /**
     * One pass, and the number of alarms the system refused during it.
     *
     * **A refusal on one alarm no longer stops the rest.** D-078. Android caps an
     * app at 500 alarms and throws past it, and on API 31 and 32 a revoked
     * exact-alarm grant throws too. The pass used to stop at the first throw,
     * so every reminder after it went unscheduled, and the exception escaped
     * into the caller, which crashed the process on every start.
     *
     * **Cancels first, then schedules soonest first, at most
     * [MaxScheduledReminders].** Cancelling first frees the system's slots
     * before anything asks for one. The soonest are the ones that cannot wait,
     * and anything past the cap is scheduled by a later pass, which every
     * delivery triggers by writing down that it was announced.
     */
    fun reconcile(tasks: List<Task>): ReconcileResult {
        val now = clock()
        val here = zone()
        val localNow = LocalDateTime.ofInstant(now, here)

        // Missed first, because they are already late, then pending, which is
        // sorted soonest first. The trigger time is what separates them, and
        // `reminderTrigger` decides that.
        val owed = missedReminders(tasks, localNow) + pendingReminders(tasks, localNow)
        val owedIds = owed.mapTo(HashSet()) { task -> task.id }

        var failures = 0
        fun attempt(taskId: String, action: () -> Unit): Boolean =
            try {
                action()
                true
            } catch (error: Exception) {
                failures++
                onFailure(taskId, error)
                false
            }

        tasks
            .filter { task -> task.id !in owedIds }
            .forEach { task -> attempt(task.id) { alarms.cancel(task.id) } }

        val scheduled = owed.take(MaxScheduledReminders).mapNotNull { task ->
            // Every owed task rings at some moment; the queries saw to that.
            val ringsAt = task.ringsAt ?: return@mapNotNull null
            val set = attempt(task.id) {
                alarms.schedule(task.id, reminderTrigger(ringsAt, here, now))
            }
            if (set) ScheduledReminder(taskId = task.id, title = task.title, ringsAt = ringsAt) else null
        }

        return ReconcileResult(failures = failures, scheduled = scheduled)
    }
}

/**
 * What one pass did: how many alarms the system refused, and which reminders now
 * have one.
 *
 * The second half is what D-079 mirrors into device-protected storage, so the
 * reminders can be set again before the first unlock after a restart.
 */
data class ReconcileResult(val failures: Int, val scheduled: List<ScheduledReminder>)

/** A reminder that has an alarm, as much of it as ringing it needs. */
data class ScheduledReminder(val taskId: String, val title: String, val ringsAt: LocalDateTime)

/**
 * The most reminder alarms one pass asks the system to hold.
 *
 * Below Android's cap of 500 per app, with room for the focus estimate, the test
 * reminder and alarms of deleted tasks that have not fired yet.
 */
internal const val MaxScheduledReminders = 400
