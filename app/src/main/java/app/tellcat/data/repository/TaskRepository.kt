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
     *
     * Returns whether [next] was written. It is not when [completed] already
     * has a successor, made when its reminder was announced. D-098.
     */
    suspend fun completeWithNext(completed: Task, next: Task?): Boolean =
        dao.completeWithNext(completed = completed.toEntity(), next = next?.toEntity())

    /**
     * Applies [edit] to [id] and lets the occurrences it made follow, in one
     * transaction. Returns the task as it was before, or null when nothing was
     * written. `docs/decisions.md` D-098; see `TaskDao.editWithSuccessor`.
     */
    suspend fun editWithSuccessor(
        id: String,
        edit: (Task) -> Task?,
        follow: (before: Task, after: Task, successor: Task) -> Task?
    ): Task? = dao.editWithSuccessor(
        id = id,
        edit = { entity -> edit(entity.toDomain())?.toEntity() },
        follow = { before, after, successor ->
            val stored = successor.toDomain()
            when (val followed = follow(before.toDomain(), after.toDomain(), stored)) {
                null -> null
                // Handed back as stored, so an occurrence left alone is not
                // rewritten.
                stored -> successor
                else -> followed.toEntity()
            }
        }
    )?.toDomain()

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
     * Records [id]'s reminder announced at [deliveredAt], starts [next] and
     * retires [retireId], in one transaction. `docs/decisions.md` D-098; see
     * `TaskDao.recordAnnounced`.
     */
    suspend fun recordAnnounced(
        id: String,
        deliveredAt: Instant,
        next: Task?,
        retireId: String?,
        followUpAt: LocalDateTime? = null
    ) {
        dao.recordAnnounced(
            id = id,
            deliveredAt = deliveredAt.toEpochMilli(),
            next = next?.toEntity(),
            retireId = retireId,
            followUpAt = TaskConverters.localDateTimeToText(followUpAt)
        )
    }

    /**
     * Forgets [id]'s pending follow-up: it was posted, or the user answered the
     * reminder some other way. D-108.
     */
    suspend fun clearFollowUp(id: String) {
        dao.setFollowUp(id = id, followUpAt = null)
    }

    /** The early reminder for [id] was told at [at]. D-115. */
    suspend fun recordEarlyAnnounced(id: String, at: java.time.Instant) {
        dao.markEarlyDelivered(id = id, deliveredAt = at.toString())
    }

    /** Moves [id]'s early reminder to [until], leaving the reminder alone. D-115. */
    suspend fun snoozeEarlyReminder(id: String, until: java.time.LocalDateTime) {
        dao.snoozeEarlyReminder(id = id, until = until.toString())
    }

    /** Deleted tasks that had a reminder, for the alarms they may hold. D-099. */
    suspend fun deletedTasksWithReminders(): List<Task> =
        dao.findDeletedWithReminders().map { entity -> entity.toDomain() }

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
     *
     * Not what the screens use since D-098: they go through
     * `OccurrenceEdits`, which lets a repeating task's untouched next
     * occurrence follow the change. This leaves it behind.
     */
    suspend fun changeChecklist(id: String, change: (List<ChecklistItem>) -> List<ChecklistItem>) {
        dao.changeChecklist(id, change)
    }

    suspend fun restore(id: String) {
        dao.restore(id)
    }

    /**
     * Deletes [id] at [deletedAt] with each occurrence it made that [follows]
     * accepts, in one transaction. Returns their ids, or null when there was
     * no live row. `docs/decisions.md` D-098; see
     * `TaskDao.softDeleteWithSuccessors`.
     */
    suspend fun softDeleteWithSuccessors(
        id: String,
        deletedAt: Instant,
        follows: (deleted: Task, successor: Task) -> Boolean
    ): List<String>? = dao.softDeleteWithSuccessors(id, deletedAt.toEpochMilli()) { deleted, successor ->
        follows(deleted.toDomain(), successor.toDomain())
    }

    /** Restores every one of [ids] together, or none. */
    suspend fun restoreAll(ids: List<String>) {
        dao.restoreAll(ids)
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
