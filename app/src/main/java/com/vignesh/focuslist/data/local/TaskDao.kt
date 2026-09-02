package com.vignesh.focuslist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Storage access for tasks.
 *
 * The DAO reads and writes rows. It holds no view logic: Today, Upcoming,
 * Inbox, Anytime, and Someday are derived by `TaskQueries` over the domain
 * model, not by SQL here.
 */
@Dao
interface TaskDao {

    /**
     * Every live task, oldest first, re-emitting whenever the table changes.
     *
     * The view ordering is still the caller's: `TaskQueries` sorts each list
     * for itself. What this guarantees is a *deterministic* starting order, so
     * that the stable sorts downstream break their ties the same way every
     * time. Without an `ORDER BY`, SQLite is free to return rows in whatever
     * order it finds them, and Focus resolves to the head of its queue, so an
     * unspecified row order would decide which single task the user is shown.
     *
     * `createdAt` alone is not a total order, since two tasks can be captured
     * in the same millisecond. `id` is the primary key, so adding it makes the
     * order total.
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY createdAt, id")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    /**
     * Marks a task deleted without removing the row, so the deletion can be
     * undone.
     *
     * @param deletedAt epoch milliseconds, matching the column's encoding.
     */
    @Query("UPDATE tasks SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    /**
     * Undoes a soft delete, returning the task to [observeTasks].
     *
     * Clearing the timestamp is the whole operation: nothing else about the
     * task changed when it was deleted.
     */
    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    /**
     * Removes a row outright.
     *
     * Deliberately not what deleting a task does: a user's deletion is soft so
     * that it can be undone. This is for a row the app created on the user's
     * behalf and is now taking back, where a soft delete would leave a task
     * they never asked for sitting in the table forever.
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
