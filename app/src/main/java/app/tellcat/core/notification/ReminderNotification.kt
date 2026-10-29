package app.tellcat.core.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.tellcat.MainActivity
import app.tellcat.R
import app.tellcat.core.domain.MorningHour
import app.tellcat.core.text.RecurrenceStyle
import app.tellcat.core.text.recurrenceSummary
import app.tellcat.core.domain.SnoozeOption
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.availableSnoozeOptions
import app.tellcat.core.domain.hasLiveReminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/**
 * What a reminder looks like when it arrives.
 *
 * Two states, both drawn on the Clean Slate board. The reminder itself, with
 * Done and Snooze, and the snooze choice that replaces it in place when the
 * user taps Snooze. Replacing rather than stacking, because they are one
 * conversation about one task and two notifications would be the app talking
 * over itself.
 *
 * One notification per task, keyed on the task id, so several reminders at
 * once read as several things to do rather than the last one overwriting the
 * rest.
 *
 * Those several sit in one group under a summary, per `docs/decisions.md`
 * D-039. The summary discloses how many are stacked behind it and says nothing
 * else, which is the same exception D-031 makes for the widget's `+N more` and
 * the reason it does not contradict D-016's refusal of counts. The test
 * reminder is deliberately outside the group.
 *
 * The summary is posted whenever any reminder is on screen, including one. That
 * is not how it was first written, and [ReminderGroupSummaryId] carries the
 * reason it had to change.
 */

/** The reminder, as `notify/Collapsed` and `notify/Expanded` draw it. */
internal fun Context.postReminder(
    task: Task,
    // Before the first unlock after a restart, Done and Snooze cannot reach the
    // database, so Android asks for the unlock before running them. D-079.
    requireUnlockForActions: Boolean = false,
    // Posted without sound or a heads-up: what an alarm leaves behind after
    // Stop, or after ringing unanswered for `AlarmRingLimit`. The alarm already
    // made the noise; this is the record with Done and Snooze on it. D-102.
    silent: Boolean = false
): Boolean {
    val summary = task.reminderSummary(this)

    fun action(label: Int, action: String) = NotificationCompat.Action.Builder(
        0,
        getString(label),
        actionIntent(task.id, action, postedFor = task.ringsAt)
    ).setAuthenticationRequired(requireUnlockForActions).build()

    val builder = reminderBuilder(task, summary)
        .addAction(action(R.string.reminder_action_done, ReminderActionReceiver.ACTION_DONE))
        .addAction(action(R.string.reminder_action_snooze, ReminderActionReceiver.ACTION_SNOOZE))
        .setSilent(silent)

    // The expanded form is where the notes live. Collapsed shows the title and
    // the summary line, which is what the frame draws.
    task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        builder.setStyle(
            NotificationCompat.BigTextStyle().bigText("$summary\n$notes")
        )
    }

    val posted = notifyIfAllowed(task.notificationId, builder.build())
    updateReminderGroupSummary(justPosted = task.id)
    return posted
}

/**
 * The one follow-up, posted ten minutes after nobody answered. D-108.
 *
 * The reminder again, under its own title as `reminders.md` requires, with the
 * line saying what it is and the time it was set for. Done and Snooze carry the
 * moment the reminder was for, as the reminder's own did, so an answer to either
 * notification is current and an answer after a snooze or a new time is stale.
 * The same notification id, so it takes the reminder's place in the shade rather
 * than stacking beside it. On its own channel, so Android's settings can turn
 * follow-ups off without touching reminders.
 */
internal fun Context.postFollowUp(task: Task, requireUnlockForActions: Boolean = false): Boolean {
    val set = task.reminderAt?.let { at -> formatTime(at.toLocalTime()) } ?: return false
    val summary = getString(R.string.reminder_follow_up_summary, set)

    fun action(label: Int, action: String) = NotificationCompat.Action.Builder(
        0,
        getString(label),
        actionIntent(task.id, action, postedFor = task.ringsAt)
    ).setAuthenticationRequired(requireUnlockForActions).build()

    val builder = reminderBuilder(task, summary, channelId = FollowUpChannelId)
        .addAction(action(R.string.reminder_action_done, ReminderActionReceiver.ACTION_DONE))
        .addAction(action(R.string.reminder_action_snooze, ReminderActionReceiver.ACTION_SNOOZE))

    task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        builder.setStyle(NotificationCompat.BigTextStyle().bigText("$summary\n$notes"))
    }

    val posted = notifyIfAllowed(task.notificationId, builder.build())
    updateReminderGroupSummary(justPosted = task.id)
    return posted
}

/**
 * The snooze choice, as `notify/Snooze options` draws it.
 *
 * Three actions rather than the frame's four chips, because a notification
 * shows at most three. `availableSnoozeOptions` decides which three, and it is
 * asked here rather than assumed so the labels and the arithmetic cannot
 * disagree about what "this evening" means.
 */
internal fun Context.postSnoozeOptions(task: Task, now: LocalDateTime): Boolean {
    val builder = reminderBuilder(task, task.reminderSummary(this))
        .setSubText(getString(R.string.reminder_snooze_until))

    availableSnoozeOptions(now).forEach { option ->
        builder.addAction(
            0,
            snoozeLabel(option),
            actionIntent(task.id, ReminderActionReceiver.ACTION_SNOOZE_UNTIL, option, postedFor = task.ringsAt)
        )
    }

    val posted = notifyIfAllowed(task.notificationId, builder.build())
    updateReminderGroupSummary(justPosted = task.id)
    return posted
}

/**
 * The test reminder, arriving.
 *
 * Deliberately plain: no Done, no Snooze, nothing to act on. There is no task
 * behind it, and offering to complete one would be the app inventing work. It
 * says what it is and how late it was, which is the only thing the person who
 * asked for it wants to know.
 *
 * The lateness is in the text rather than left for the health screen, because
 * the point of the test is that the user is looking at the phone when it
 * arrives, or at least at the notification.
 */
internal fun Context.postTestReminder(arrivedAt: Instant): Boolean {
    val builder = NotificationCompat.Builder(this, ReminderChannelId)
        .setSmallIcon(R.drawable.ic_notification_cat)
        .setContentTitle(getString(R.string.reminder_test_title))
        .setContentText(
            getString(
                R.string.reminder_test_body,
                formatTime(arrivedAt.atZone(ZoneId.systemDefault()).toLocalTime())
            )
        )
        .setContentIntent(openIntent(TestReminder.TaskId))
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)

    return notifyIfAllowed(TestReminder.TaskId.notificationId, builder.build())
}

/**
 * Posts only while notification permission is available.
 *
 * The permission can be revoked after the check and before `notify`, so the
 * platform call still has to handle [SecurityException]. Callers use the
 * result when delivery history must distinguish announced from suppressed.
 */
internal fun Context.notifyIfAllowed(notificationId: Int, notification: Notification): Boolean {
    if (!canPostNotifications()) return false

    return try {
        NotificationManagerCompat.from(this).notify(notificationId, notification)
        true
    } catch (_: SecurityException) {
        false
    }
}

/** Takes the reminder off screen once it has been dealt with. */
internal fun Context.cancelReminder(taskId: String) {
    NotificationManagerCompat.from(this).cancel(taskId.notificationId)
    updateReminderGroupSummary(justCancelled = setOf(taskId.notificationId))
}

/**
 * Takes off the shade every reminder whose task is no longer outstanding, and
 * returns how many it took.
 *
 * `docs/decisions.md` D-099. [tasks] is every live task, as the reconciliation
 * reads them. A reminder stays while its task is among them and not
 * completed; one deleted, completed or gone has nothing left to act on, and
 * leaving it meant Done and Snooze working on a task the user had already
 * deleted. Only Done and Snooze on the notification itself used to take it
 * away, so completing in the app or the widget left it too.
 *
 * Read from the shade rather than from a record of what was posted, on the
 * same terms as the summary count: whatever was swiped away or never shown is
 * already gone. The test reminder is outside the group and is never touched.
 */
internal fun Context.withdrawSettledReminders(
    tasks: List<Task>,
    now: LocalDateTime = LocalDateTime.now()
): Int {
    val manager = getSystemService(NotificationManager::class.java) ?: return 0
    // **A reminder moved ahead is settled too.** D-109. Snoozed from inside the
    // app, or given a new time, the reminder in the shade describes a moment
    // the task has left behind, and its actions are already stale.
    val outstanding = tasks
        .filterNot { task -> task.isCompleted }
        .filterNot { task -> task.hasLiveReminder() && task.ringsAt?.isAfter(now) == true }
        .mapTo(HashSet()) { task -> task.notificationId }

    val settled = manager.activeNotifications
        .filter { it.notification.group == ReminderGroup && it.id != ReminderGroupSummaryId }
        .map { it.id }
        .filterNot { id -> id in outstanding }
        .toSet()
    if (settled.isEmpty()) return 0

    settled.forEach { id -> NotificationManagerCompat.from(this).cancel(id) }
    updateReminderGroupSummary(justCancelled = settled)
    return settled.size
}

// --- the group ----------------------------------------------------------------

/**
 * Posts, updates or removes the summary so it always agrees with the shade.
 *
 * Called after every post and every cancel rather than only on the way up,
 * because a summary left behind by the last reminder being dealt with is a
 * notification about nothing.
 *
 * The count starts from the shade rather than from state of our own, so it
 * cannot drift from what is really on screen: a reminder dismissed by swipe, by
 * another process, or by the system is already gone from [NotificationManager]
 * and was never going to tell us.
 *
 * **[justPosted] and [justCancelled] are not optimisations, they are the
 * correctness of the whole function.** Both `notify` and `cancel` hand the work
 * to the notification service, which processes it on its own handler, so the
 * shade read a microsecond later may still be missing the reminder just posted
 * or still holding the one just cancelled. Reading it raw makes the summary
 * count whatever the race happened to produce: it was observed announcing "1
 * reminder" over two. The caller knows which one it just moved, so the answer
 * is taken from the shade and then corrected by that one id.
 */
private fun Context.updateReminderGroupSummary(
    justPosted: String? = null,
    justCancelled: Set<Int> = emptySet()
) {
    val count = postedReminderCount(including = justPosted, excluding = justCancelled)

    // Only ever cancelled once the group is empty. See [ReminderGroupSummaryId]
    // for why cancelling it while a reminder survives is destructive.
    if (count == 0) {
        NotificationManagerCompat.from(this).cancel(ReminderGroupSummaryId)
        return
    }

    val summary = NotificationCompat.Builder(this, ReminderChannelId)
        .setSmallIcon(R.drawable.ic_notification_cat)
        // The count alone. Android draws the app name in the header already, so
        // the board's "Tellcat · 3 reminders" would say Tellcat twice.
        .setContentTitle(resources.getQuantityString(R.plurals.reminder_group_summary, count, count))
        .setContentIntent(openAppIntent())
        .setGroup(ReminderGroup)
        .setGroupSummary(true)
        // The children alert; the summary does not. Without this the default is
        // GROUP_ALERT_ALL and the summary would sound on top of the reminder
        // that just sounded. It is the single most consequential line here: get
        // the behaviour backwards and grouping silences the reminders
        // themselves, which D-005 ranks above a crash.
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .build()

    notifyIfAllowed(ReminderGroupSummaryId, summary)
}

/**
 * How many reminders are on screen, not counting the summary over them.
 *
 * Only this app's own notifications are visible here, and the group tag is what
 * keeps the focus-session estimate and the test reminder out of the total.
 *
 * Counted as a set of ids rather than as a running total, because [including]
 * has to be idempotent: the reminder just posted may or may not have reached
 * the shade already, and adding one to a count that already contains it is how
 * a summary ends up claiming a reminder that is not there.
 */
private fun Context.postedReminderCount(including: String?, excluding: Set<Int>): Int {
    val manager = getSystemService(NotificationManager::class.java) ?: return 0

    val ids = manager.activeNotifications
        .filter { it.notification.group == ReminderGroup && it.id != ReminderGroupSummaryId }
        .mapTo(mutableSetOf()) { it.id }

    including?.let { ids += it.notificationId }
    ids -= excluding

    return ids.size
}

/**
 * Where the summary goes when tapped.
 *
 * Modern Android expands the stack instead of launching, so this is the
 * fallback rather than the usual path. It carries no task id because a summary
 * over several reminders cannot pick one of them without guessing.
 */
private fun Context.openAppIntent(): PendingIntent = PendingIntent.getActivity(
    this,
    0,
    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

/** The tag that binds the reminders together. D-039. */
private const val ReminderGroup = "tellcat.reminders"

/**
 * The summary's own id.
 *
 * **Cancelling this while reminders remain destroys them.** The notification
 * service cancels a group's children along with its summary, so the obvious
 * implementation, drop the summary once fewer than two reminders are left,
 * takes the surviving reminder off the screen with it. The user is then not
 * told about work they never dealt with, which `CLAUDE.md` ranks above a crash.
 * It cost nothing to find and would have cost a great deal to ship: it is
 * invisible until a second reminder exists and the first is handled.
 *
 * So the summary is posted whenever the group is not empty and cancelled only
 * when it is, at which point there are no children left to take down. The cost
 * is that a single reminder carries a summary counting one. SystemUI flattens a
 * group holding one child and does not draw the summary over it, so this is not
 * expected to be visible, but that is a rendering behaviour rather than a
 * guarantee: `activeNotifications` still reports the summary, and
 * `ReminderGroupTest` asserts it there rather than pretending to know what was
 * drawn. A redundant header on some skin is the price; the alternative deletes
 * a reminder nobody dealt with.
 *
 * Task notifications are keyed on `String.hashCode`, so this is a value one
 * could in principle collide with. Left undefended on the same terms as the
 * focus estimate's fixed id: the odds are one in 2^32, and the cost is a
 * reminder sharing a slot rather than a reminder failing to arrive.
 */
private const val ReminderGroupSummaryId = Int.MIN_VALUE

// --- internals ----------------------------------------------------------------

private fun Context.reminderBuilder(task: Task, summary: String, channelId: String = ReminderChannelId) =
    NotificationCompat.Builder(this, channelId)
        // The mark rather than a stock bell, D-072. Android masks this drawable
        // down to its alpha and tints it, which is why the launcher icon cannot
        // be passed here and why [R.drawable.ic_notification_cat] is a
        // silhouette. The same icon on all three reminder notifications, so a
        // reminder, its group summary and the test all arrive as one app.
        .setSmallIcon(R.drawable.ic_notification_cat)
        .setContentTitle(task.title)
        .setContentText(summary)
        .setContentIntent(openIntent(task.id))
        .setGroup(ReminderGroup)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)

/**
 * The line under the title: the time it was set for, and how often it repeats.
 *
 * The time is formatted with the device's own 12 or 24 hour preference rather
 * than a pattern of ours, because a reminder that says 3:30 PM to someone
 * whose phone says 15:30 everywhere else reads as a different app's
 * notification.
 */
private fun Task.reminderSummary(context: Context): String {
    // **A snooze says so, and names the time the reminder was set for.** D-109.
    // The line used to give the snooze's own time, which is the moment it is
    // ringing, so it read as the reminder's time and hid that it was late.
    val set = reminderAt
    val time = if (snoozedUntil != null && set != null) {
        context.getString(R.string.reminder_snoozed_summary, context.formatTime(set.toLocalTime()))
    } else {
        // When it rang. D-078.
        val at = ringsAt ?: return ""
        context.formatTime(at.toLocalTime())
    }
    // Sentence form: "Every week" rather than "Weekly", which is how a line
    // read at a glance mid-task should phrase itself. `strings.xml` used to
    // carry a second set of four names for this; the plurals behind
    // `recurrenceSummary` say the same thing and also know what to do with an
    // interval and a weekday set, which four fixed names never could.
    val repeat = recurrence?.let {
        // A boundary, so reading the clock here is right: the formatter
        // itself stays pure and is handed the day, like every query in
        // `TaskQueries.kt`.
        recurrenceSummary(context, it, LocalDate.now(), RecurrenceStyle.Sentence)
    }

    return if (repeat == null) {
        time
    } else {
        context.getString(R.string.reminder_summary_separator, time, repeat)
    }
}

internal fun Context.formatTime(time: LocalTime): String {
    val moment = LocalDateTime.now().with(time).atZone(ZoneId.systemDefault()).toInstant()
    return android.text.format.DateFormat.getTimeFormat(this).format(Date.from(moment))
}

private fun Context.snoozeLabel(option: SnoozeOption): String = when (option) {
    SnoozeOption.TenMinutes -> getString(R.string.reminder_snooze_ten_minutes)
    SnoozeOption.OneHour -> getString(R.string.reminder_snooze_one_hour)
    SnoozeOption.ThisEvening -> getString(R.string.reminder_snooze_this_evening)
    // The hour is in the label, so it is formatted rather than written into
    // the string, and it stays true if MorningHour ever moves.
    SnoozeOption.TomorrowMorning ->
        getString(R.string.reminder_snooze_tomorrow, formatTime(MorningHour))
}

/**
 * A distinct [PendingIntent] per task and action.
 *
 * Matching ignores extras, so the action and the option have to be in the
 * data URI. Without that, Done and Snooze on the same task would be the same
 * pending intent and the second would silently replace the first.
 *
 * [postedFor] is the moment the notification was for, carried so an action
 * from a notification that has since gone stale is ignored. R-002, D-102. An
 * extra rather than part of the URI: a newer post for the same task replaces
 * this one, and `FLAG_UPDATE_CURRENT` rewrites the extra with it.
 */
private fun Context.actionIntent(
    taskId: String,
    action: String,
    option: SnoozeOption? = null,
    postedFor: LocalDateTime? = null
): PendingIntent {
    val intent = Intent(this, ReminderActionReceiver::class.java).apply {
        this.action = action
        data = Uri.parse("tellcat://reminder/$taskId/$action/${option?.name.orEmpty()}")
        putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
        option?.let { putExtra(ReminderActionReceiver.EXTRA_SNOOZE_OPTION, it.name) }
        postedFor?.let { putExtra(ReminderActionReceiver.EXTRA_POSTED_FOR, it.toString()) }
    }

    return PendingIntent.getBroadcast(
        this,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun Context.openIntent(taskId: String): PendingIntent = PendingIntent.getActivity(
    this,
    0,
    Intent(this, MainActivity::class.java)
        .setData(Uri.parse("tellcat://task/$taskId"))
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

/** One notification per task, and the same one every time it is touched. */
internal val Task.notificationId: Int get() = id.notificationId

internal val String.notificationId: Int get() = hashCode()
