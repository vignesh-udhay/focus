package com.vignesh.focuslist.core.domain

import java.time.LocalDateTime

/**
 * Which reminders are still owed to the user.
 *
 * The rule this file exists to enforce is that storage decides what is
 * scheduled and `AlarmManager` is only a cache of that decision. A reminder
 * that cannot be found by reading tasks does not exist, whatever the system
 * was told, because everything the system was told is gone after a restart.
 * So the boot receiver, the timezone-change receiver, and the scheduler all
 * ask these functions rather than remembering anything themselves.
 *
 * Pure, and taking the current time as a parameter rather than reading a
 * clock, on the same terms as `TaskQueries.kt`. That is what lets the awkward
 * cases be tested without a device: a reminder that came due while the phone
 * was off, a reminder on a task completed from the notification, a reminder
 * dragged into the past by a timezone change.
 *
 * A reminder is live only while its task is outstanding. Completing or
 * deleting a task silently retires its reminder, which is the behaviour the
 * user expects and, more to the point, the one that stops a deleted task
 * ringing at 6am.
 */

/**
 * Whether this task is still owed a reminder at all.
 *
 * Says nothing about when. [pendingReminders] and [missedReminders] split the
 * tasks this accepts between them, and every task it rejects is in neither.
 */
fun Task.hasLiveReminder(): Boolean =
    reminderAt != null && !isCompleted && !isDeleted

/**
 * Reminders still ahead of [now]: exactly the set that should be sitting in
 * `AlarmManager`.
 *
 * This is the list a boot receiver rebuilds from, and the list a scheduler
 * reconciles against. Anything held by the system and absent from here is
 * stale and should be cancelled.
 *
 * Soonest first, because a caller that has to work through them in order
 * should not have to know to sort. Tasks sharing a time keep the order they
 * were given.
 */
fun pendingReminders(tasks: List<Task>, now: LocalDateTime): List<Task> =
    tasks
        .filter { task -> task.hasLiveReminder() && task.reminderAt!!.isAfter(now) }
        // Every task here has a reminder; the filter saw to that.
        .sortedBy { task -> task.reminderAt }

/**
 * Reminders whose moment has already passed and which the user was never
 * told about.
 *
 * These are the ones that matter. A reminder falls in here when the phone was
 * off at the time, when the alarm was dropped by a battery optimiser, or when
 * a clock or timezone change moved its moment into the past. `PRODUCT.md`
 * says a reminder that does not fire is the most severe class of bug in this
 * product, so the app has to be able to see them rather than discover them
 * through a user's review.
 *
 * Deciding what to do about one is not this function's business, and the
 * answer differs by how late it is. Phase 1 needs to know they exist; the
 * health screen in Phase 2 is what reports on them.
 *
 * A reminder due at exactly [now] counts as missed rather than pending. It was
 * owed, and treating the boundary the other way would let a reminder fall
 * between the two lists and be scheduled by nobody.
 *
 * Oldest first, so the longest-overdue reads first.
 */
fun missedReminders(tasks: List<Task>, now: LocalDateTime): List<Task> =
    tasks
        .filter { task -> task.hasLiveReminder() && !task.reminderAt!!.isAfter(now) }
        // Every task here has a reminder; the filter saw to that.
        .sortedBy { task -> task.reminderAt }
