package com.vignesh.focuslist.data.repository

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.data.local.TaskDao
import com.vignesh.focuslist.data.local.toDomain
import com.vignesh.focuslist.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

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
