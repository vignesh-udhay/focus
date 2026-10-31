package app.tellcat.core.notification

import app.tellcat.core.domain.Task
import app.tellcat.core.domain.FollowUpDelay
import app.tellcat.core.domain.pendingFollowUp
import app.tellcat.core.domain.followUpsOwed
import app.tellcat.core.domain.earlyRemindersOwed
import app.tellcat.core.domain.pendingEarlyReminder
import app.tellcat.core.domain.missedReminders
import app.tellcat.core.domain.pendingReminders
import app.tellcat.core.domain.reminderTrigger
import java.time.Duration
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
     * Keep one inexact alarm at [at] that reconciles when it fires, replacing
     * the one before, or none when [at] is null. D-103.
     *
     * Inexact on purpose. Revoking "Alarms & reminders" cancels the app's exact
     * alarms and kills it, and Android says nothing about it, so no receiver can
     * rebuild them. Android leaves inexact alarms standing, so this one is still
     * there to start the rebuild.
     */
    fun scheduleBackstop(at: Instant?)

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
     *
     * **Then the backstop, [BackstopDelay] after the soonest future reminder,
     * while the alarms are exact.** D-103. If the exact alarms are taken away,
     * it fires, and the pass it starts sets them again, inexactly, and tells
     * the reminder that was missed. Normally it never fires: the reminder's
     * own alarm comes first, and the pass after it moves the backstop on.
     * Future reminders only, never one already missed, so a reminder that
     * cannot be shown does not wake the phone every minute.
     *
     * **[deleted] is cancelled too, where an alarm could still be waiting.**
     * D-099. A deleted task is not in [tasks], so until this nothing reached
     * its alarm: it stayed armed, fired, and was only kept quiet by the
     * receiver reading the task first. One whose moment has gone has fired
     * already and holds nothing to cancel.
     */
    fun reconcile(tasks: List<Task>, deleted: List<Task> = emptyList()): ReconcileResult {
        val now = clock()
        val here = zone()
        val localNow = LocalDateTime.ofInstant(now, here)

        // Missed first, because they are already late, then pending, which is
        // sorted soonest first. The trigger time is what separates them, and
        // `reminderTrigger` decides that.
        //
        // **Follow-ups take their place in the same order**, on the task's own
        // alarm, D-108. A task waiting on one has no reminder owed (it stands
        // announced), so the slot is free, and the receiver tells the two apart
        // by reading the task. Sorted by trigger, so a follow-up due in four
        // minutes is scheduled before a reminder due tomorrow and a missed one
        // still leads.
        //
        // **Early reminders too**, D-115, on an alarm of their own beside the
        // reminder's, since the two are owed at once. In the same order and
        // under the same cap, and so inside the backstop.
        val owed = (
            (missedReminders(tasks, localNow) + pendingReminders(tasks, localNow))
                .mapNotNull { task -> task.ringsAt?.let { at -> Owed(task, at, followUpOf = null) } } +
                followUpsOwed(tasks).mapNotNull { task ->
                    task.pendingFollowUp?.let { at -> Owed(task, at, followUpOf = task.ringsAt) }
                } +
                earlyRemindersOwed(tasks, localNow).mapNotNull { task ->
                    task.pendingEarlyReminder?.let { at ->
                        Owed(task, at, followUpOf = null, earlyFor = task.reminderAt)
                    }
                }
            ).sortedBy { due -> reminderTrigger(due.at, here, now) }
        val owedIds = owed.filter { due -> due.earlyFor == null }.mapTo(HashSet()) { due -> due.task.id }
        val owedEarlyIds = owed.filter { due -> due.earlyFor != null }.mapTo(HashSet()) { due -> due.task.id }

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

        // An early alarm with nothing owed is cancelled the same way, but only
        // where one was ever set, so a pass is not two cancels for every task.
        tasks
            .filter { task -> task.earlyReminder != null || task.earlySnoozedUntil != null }
            .filter { task -> task.id !in owedEarlyIds }
            .forEach { task -> attempt(task.id) { alarms.cancel(earlyAlarmId(task.id)) } }

        // And one announced within the follow-up window, whose follow-up alarm
        // may still be armed. D-108.
        val followUpWindow = now.minus(FollowUpDelay).minus(BackstopDelay)
        deleted
            .filter { task ->
                task.ringsAt?.isAfter(localNow) == true ||
                    task.reminderDeliveredAt?.isAfter(followUpWindow) == true
            }
            .forEach { task -> attempt(task.id) { alarms.cancel(task.id) } }

        // And a deleted task's early alarm, while its reminder is ahead. D-115.
        deleted
            .filter { task -> task.earlyReminder != null && task.reminderAt?.isAfter(localNow) == true }
            .forEach { task -> attempt(task.id) { alarms.cancel(earlyAlarmId(task.id)) } }

        val triggers = mutableListOf<Instant>()
        val scheduled = owed.take(MaxScheduledReminders).mapNotNull { due ->
            val task = due.task
            val ringsAt = due.at
            val trigger = reminderTrigger(ringsAt, here, now)
            val alarmId = if (due.earlyFor != null) earlyAlarmId(task.id) else task.id
            val set = attempt(task.id) { alarms.schedule(alarmId, trigger) }
            if (set) {
                triggers += trigger
                ScheduledReminder(
                    taskId = task.id,
                    title = task.title,
                    ringsAt = ringsAt,
                    // An early reminder is a plain one, D-115: only the
                    // reminder's own moment rings like an alarm.
                    ringsLikeAlarm = task.ringsLikeAlarm && due.followUpOf == null && due.earlyFor == null,
                    followUpOf = due.followUpOf,
                    earlyFor = due.earlyFor
                )
            } else {
                null
            }
        }

        val backstop = triggers
            .filter { trigger -> trigger.isAfter(now) }
            .minOrNull()
            ?.takeIf { alarms.canScheduleExact() }
            ?.plus(BackstopDelay)
        attempt(BackstopId) { alarms.scheduleBackstop(backstop) }

        return ReconcileResult(failures = failures, scheduled = scheduled)
    }
}

/**
 * How long after a reminder's moment the backstop waits. D-103.
 *
 * Long enough that the reminder's own alarm, and the pass that follows it,
 * normally come first and move the backstop on before it fires. Short next
 * to the hour by which Android may defer an inexact alarm.
 */
internal val BackstopDelay: Duration = Duration.ofMinutes(1)

/** What a refused backstop is logged against, in place of a task id. */
internal const val BackstopId = "backstop"

/**
 * What one pass did: how many alarms the system refused, and which reminders now
 * have one.
 *
 * The second half is what D-079 mirrors into device-protected storage, so the
 * reminders can be set again before the first unlock after a restart.
 */
data class ReconcileResult(val failures: Int, val scheduled: List<ScheduledReminder>)

/**
 * A reminder that has an alarm, as much of it as ringing it needs: before the
 * first unlock that includes whether it rings like one, D-102.
 */
data class ScheduledReminder(
    val taskId: String,
    val title: String,
    val ringsAt: LocalDateTime,
    val ringsLikeAlarm: Boolean = false,
    /**
     * For a follow-up, the moment of the reminder it posts once more, and
     * [ringsAt] is when. Null for a reminder. D-108.
     */
    val followUpOf: LocalDateTime? = null,
    /**
     * For an early reminder, the moment of the reminder it warns about, and
     * [ringsAt] is when it rings. Null otherwise. D-115.
     */
    val earlyFor: LocalDateTime? = null
)

/** One alarm a pass owes: whose, when, and whether it is a follow-up or an early one. */
private data class Owed(
    val task: Task,
    val at: LocalDateTime,
    val followUpOf: LocalDateTime?,
    val earlyFor: LocalDateTime? = null
)

/**
 * The id a task's early alarm is held under, beside the reminder's own, which
 * is the task id. D-115. The receiver reads it back with [parseAlarmId].
 */
fun earlyAlarmId(taskId: String): String = taskId + EarlyAlarmSuffix

/** The task an alarm id is for, and whether it is the early one. */
fun parseAlarmId(alarmId: String): Pair<String, Boolean> =
    if (alarmId.endsWith(EarlyAlarmSuffix)) {
        alarmId.removeSuffix(EarlyAlarmSuffix) to true
    } else {
        alarmId to false
    }

/** Task ids are UUIDs, so no id ends in this. */
private const val EarlyAlarmSuffix = "/early"

/**
 * The most reminder alarms one pass asks the system to hold.
 *
 * Below Android's cap of 500 per app, with room for the focus estimate, the test
 * reminder and alarms of deleted tasks that have not fired yet.
 */
internal const val MaxScheduledReminders = 400
