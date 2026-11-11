package app.tellcat.core.domain

import java.time.Instant
import java.time.LocalDateTime

/**
 * What a restart takes off the shade, and what is put back. `docs/decisions.md`
 * D-125.
 *
 * Android clears every notification when the phone restarts. A reminder that
 * was announced and still not answered was gone after it, though its task was
 * still open. The recovery receiver asks this, after the reconcile on boot, and
 * posts each one again quietly.
 */

/**
 * One notification to put back: [task]'s reminder, or its follow-up when
 * [followedUp].
 */
data class RestoredReminder(val task: Task, val followedUp: Boolean)

/**
 * The reminders to post again after a restart at [bootedAt], in the order they
 * were first posted.
 *
 * Only what was in the shade before the restart: open, announced, not answered
 * and not swiped away. One posted since [bootedAt] is still on screen, told
 * while the phone was locked or by a follow-up that came due, so posting it
 * again would bring back one the user may have swiped away before the unlock.
 * A follow-up owed by [now] is left to D-108, which posts it once with sound;
 * putting the reminder back first would be a second post of the same moment.
 *
 * The follow-up is what was there when [Task.inShadeSince] is later than the
 * announcement, since posting it is the only thing that moves it on.
 */
fun remindersToRestore(tasks: List<Task>, bootedAt: Instant, now: LocalDateTime): List<RestoredReminder> =
    tasks
        .sortedBy { task -> task.inShadeSince }
        .mapNotNull { task ->
            val since = task.inShadeSince ?: return@mapNotNull null
            val announced = task.reminderDeliveredAt ?: return@mapNotNull null
            val restored = since.isBefore(bootedAt) &&
                !task.isCompleted && !task.isDeleted && task.reminderAt != null &&
                task.pendingFollowUp?.isAfter(now) != false
            if (restored) RestoredReminder(task, followedUp = since.isAfter(announced)) else null
        }
