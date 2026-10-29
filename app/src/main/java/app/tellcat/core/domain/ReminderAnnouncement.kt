package app.tellcat.core.domain

import app.tellcat.data.repository.TaskRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * What announcing a reminder does to its task, beyond saying it.
 *
 * `docs/decisions.md` D-098. For a task that happens once, only that the
 * reminder was told, which stops the next pass announcing it again. For a
 * repeating task, also the next occurrence, made now rather than waiting for
 * this one to be completed: until D-098 only [TaskCompletion] made it, so a
 * daily reminder left unanswered once was silent every day after.
 *
 * The occurrence just announced stays open. If nobody answers it, it reaches
 * Today's Overdue band once its day is over, which is the miss being seen
 * rather than rolled away. The open occurrence before it, if there is one, is
 * retired: a series keeps one missed occurrence open, the latest, on the
 * rule `Recurrence.kt` states that missing an occurrence does not bank it.
 *
 * A use case beside [TaskCompletion] for the reason that one gives: two
 * callers need it identically, the reminder receiver and the settling of
 * reminders announced while the phone was locked, and neither has a view
 * model.
 *
 * The id generator and zone are injected so the series arithmetic is testable
 * without a random UUID or the device's timezone. There is no clock: the
 * moment of announcing is the caller's, because a reminder told while the
 * phone was locked is written down later than it was said.
 */
class ReminderAnnouncement(
    private val repository: TaskRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {

    /**
     * Records that [taskId]'s reminder was announced at [announcedAt].
     *
     * The next occurrence counts from the day it was announced, as completing
     * counts from the day it was done, and its reminder from that moment, so
     * it is never one already gone. Writing it is skipped inside the
     * transaction when this occurrence already has one: a snooze rings the
     * same occurrence again, and it makes its successor once.
     *
     * Does nothing for a task with no live row.
     */
    suspend fun record(
        taskId: String,
        announcedAt: Instant,
        // Whether to post it once more if nobody answers: true when it was
        // announced as a notification and follow-ups are allowed. D-108.
        remindsAgain: Boolean = false
    ) {
        val task = repository.findTask(taskId) ?: return
        val announcedHere = LocalDateTime.ofInstant(announcedAt, zone())

        val next = task.nextRecurringInstance(
            today = announcedHere.toLocalDate(),
            id = newId(),
            createdAt = announcedAt,
            now = announcedHere
        )

        // Only inside a series that still repeats. The occurrence before this
        // one is still open only if nobody answered it.
        val missed = task.spawnedFromId
            ?.takeIf { task.recurrence != null }
            ?.let { previousId -> repository.findTask(previousId) }
            ?.takeUnless { previous -> previous.isCompleted }

        repository.recordAnnounced(
            id = taskId,
            deliveredAt = announcedAt,
            next = next,
            retireId = missed?.id,
            followUpAt = if (remindsAgain) followUpTime(announcedHere) else null
        )
    }
}
