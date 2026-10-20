package app.tellcat.data.repository

import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.Task
import app.tellcat.data.local.TaskConverters
import app.tellcat.data.local.TaskDao
import app.tellcat.data.local.toDomain
import app.tellcat.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime

/**
 * Storage of tasks, in domain terms.
 *
 * The repository translates and delegates, nothing more. It applies no
 * filtering, holds no rules, and never reads the clock: Today, Upcoming and
 * Inbox are derived by `TaskQueries`, and timestamps are supplied by the
 * caller.
 *
 * `TaskEntity` never escapes this class.
 */
class TaskRepository(private val dao: TaskDao) {

    /**
     * Every live task, re-emitting whenever the stored data changes.
     *
     * The DAO's order is preserved: oldest first, and total, so the stable
     * sorts in `TaskQueries` break their ties the same way on every emission.
     */
    fun observeTasks(): Flow<List<Task>> =
        dao.observeTasks().map { entities -> entities.map { entity -> entity.toDomain() } }

    /**
     * One task by id, or null when there is no live row for it.
     *
     * What every write path reads before it decides. D-063 replaced
     * `observeTasks().first()` here: that took one emission off a stream of
     * every task and had no error path, so a failed read crashed the caller.
     */
    suspend fun findTask(id: String): Task? = dao.findTask(id)?.toDomain()

    /** The live occurrences a completion created from [parentId]. */
    suspend fun findSpawnsOf(parentId: String): List<Task> =
        dao.findSpawnsOf(parentId).map { entity -> entity.toDomain() }

    suspend fun insert(task: Task) {
        dao.insert(task.toEntity())
    }

    suspend fun update(task: Task) {
        dao.update(task.toEntity())
    }

    /**
     * Completes [completed] and starts [next] in one transaction, or neither.
     *
     * `docs/decisions.md` D-063. Two calls to [update] and [insert] could half
     * happen, and a recurring task completed without its successor is a
     * reminder silently lost, which `PRODUCT.md` ranks above a crash.
     */
    suspend fun completeWithNext(completed: Task, next: Task?) {
        dao.completeWithNext(completed = completed.toEntity(), next = next?.toEntity())
    }

    /** Reopens [reopened] and removes [spawnId] together, or neither. */
    suspend fun reopenWithoutSpawn(reopened: Task, spawnId: String?) {
        dao.reopenWithoutSpawn(reopened = reopened.toEntity(), spawnId = spawnId)
    }

    /**
     * Marks a task deleted at exactly [deletedAt].
     *
     * The instant is encoded as epoch milliseconds, matching how
     * `TaskConverters` stores every other timestamp.
     */
    suspend fun softDelete(id: String, deletedAt: Instant) {
        dao.softDelete(id = id, deletedAt = deletedAt.toEpochMilli())
    }

    /**
     * Records that [id]'s reminder was announced at [deliveredAt].
     *
     * What retires a reminder. Until this is written the task is still owed
     * one, and the scheduler will announce it again on its next pass.
     */
    suspend fun markReminderDelivered(id: String, deliveredAt: Instant) {
        dao.markReminderDelivered(id = id, deliveredAt = deliveredAt.toEpochMilli())
    }

    /**
     * Snoozes [id]'s reminder to [until], leaving the reminder itself alone.
     *
     * Encoded the same way `TaskConverters` stores the column, and it clears
     * the delivery record in the same statement so the new time is owed rather
     * than born already announced. D-078 says why the reminder is not moved.
     */
    suspend fun snoozeReminder(id: String, until: LocalDateTime) {
        dao.snoozeReminder(
            id = id,
            snoozedUntil = requireNotNull(TaskConverters.localDateTimeToText(until))
        )
    }

    /**
     * Applies [change] to [id]'s checklist as one transaction. See
     * `TaskDao.changeChecklist` for why this is not a read here and an
     * [update] after it.
     */
    suspend fun changeChecklist(id: String, change: (List<ChecklistItem>) -> List<ChecklistItem>) {
        dao.changeChecklist(id, change)
    }

    suspend fun restore(id: String) {
        dao.restore(id)
    }

    /**
     * Removes a task's row for good.
     *
     * Not the counterpart of [softDelete], which is what deleting a task does
     * and stays undoable. This erases, and is only for rows the app inserted
     * itself and is now withdrawing.
     */
    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
