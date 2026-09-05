package com.vignesh.focuslist.data.repository

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.data.local.TaskConverters
import com.vignesh.focuslist.data.local.TaskDao
import com.vignesh.focuslist.data.local.toDomain
import com.vignesh.focuslist.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime

/**
 * Storage of tasks, in domain terms.
 *
 * The repository translates and delegates, nothing more. It applies no
 * filtering, holds no rules, and never reads the clock: Today, Upcoming,
 * Inbox, Anytime, and Someday are derived by `TaskQueries`, and timestamps are
 * supplied by the caller.
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

    suspend fun insert(task: Task) {
        dao.insert(task.toEntity())
    }

    suspend fun update(task: Task) {
        dao.update(task.toEntity())
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
     * Moves [id]'s reminder to [reminderAt], or clears it when null.
     *
     * Encoded the same way `TaskConverters` stores the column, and it clears
     * the delivery record in the same statement so the new time is owed rather
     * than born already announced.
     */
    suspend fun rescheduleReminder(id: String, reminderAt: LocalDateTime?) {
        dao.rescheduleReminder(
            id = id,
            reminderAt = TaskConverters.localDateTimeToText(reminderAt)
        )
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
