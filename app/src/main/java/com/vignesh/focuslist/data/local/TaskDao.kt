package com.vignesh.focuslist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Storage access for tasks.
 *
 * The DAO reads and writes rows. It holds no view logic: Today, Upcoming and
 * Inbox are derived by `TaskQueries` over the domain model, not by SQL here.
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

    /**
     * One task by id, or null when there is no live row for it.
     *
     * `docs/decisions.md` D-063. Every write path used to reach its task with
     * `observeTasks().first()`: subscribe to a stream of every task, take one
     * emission, cancel it, then search the list in memory. That reads the whole
     * table to find one row, and worse, it has no error path — a `Flow` that
     * throws crashes the collector, and the caught stream would suspend for
     * ever instead.
     *
     * A suspending point query throws at one call site the caller can wrap, and
     * cannot hang. The `deletedAt IS NULL` filter matches [observeTasks], so
     * callers see exactly what they saw before.
     */
    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL")
    suspend fun findTask(id: String): TaskEntity?

    /**
     * The live occurrences a completion created from [parentId].
     *
     * Reopening needs to find them and the rule for which one counts is a
     * domain rule, so only the deleted filter this DAO already owns is applied
     * here. Whether an occurrence is still untouched is decided above.
     */
    @Query("SELECT * FROM tasks WHERE spawnedFromId = :parentId AND deletedAt IS NULL")
    suspend fun findSpawnsOf(parentId: String): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    /**
     * Completes a task and starts its next occurrence, or does neither.
     *
     * `docs/decisions.md` D-063. These were two separate writes with nothing
     * binding them, so a failure between them left a recurring task complete
     * with no successor: its reminder gone, and nothing anywhere saying so.
     * `PRODUCT.md` requires that "completing one occurrence must produce the
     * next one", and that promise cannot be kept by two writes that can half
     * happen.
     *
     * [next] is null for a task that does not recur, which is the ordinary
     * case and needs no second write.
     */
    @Transaction
    suspend fun completeWithNext(completed: TaskEntity, next: TaskEntity?) {
        update(completed)
        next?.let { occurrence -> insert(occurrence) }
    }

    /**
     * Reopens a task and removes the occurrence its completion created, or
     * does neither.
     *
     * The mirror of [completeWithNext] and atomic for the same reason: a
     * failure between the two writes destroyed the untouched next occurrence
     * and left the task completed, which loses the reminder in the other
     * direction.
     *
     * [spawnId] is null when completion produced nothing to take back.
     */
    @Transaction
    suspend fun reopenWithoutSpawn(reopened: TaskEntity, spawnId: String?) {
        spawnId?.let { id -> deleteById(id) }
        update(reopened)
    }

    /**
     * Marks a task deleted without removing the row, so the deletion can be
     * undone.
     *
     * @param deletedAt epoch milliseconds, matching the column's encoding.
     */
    @Query("UPDATE tasks SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    /**
     * Records that a task's reminder has been announced.
     *
     * A targeted write rather than a whole-row update, because it happens in a
     * broadcast receiver moments before the process is likely to be frozen,
     * and because it must not race with an edit the user is making on screen.
     *
     * @param deliveredAt epoch milliseconds, matching the column's encoding.
     */
    @Query("UPDATE tasks SET reminderDeliveredAt = :deliveredAt WHERE id = :id")
    suspend fun markReminderDelivered(id: String, deliveredAt: Long)

    /**
     * Moves a reminder to a new time and forgets that the old one was
     * announced.
     *
     * The two halves are one statement because they are one fact. A reminder
     * given a new time has not been delivered yet, and a version of this that
     * set the time without clearing the record would produce a reminder born
     * already delivered, which never fires. That invariant is stated on
     * `Task.reminderDeliveredAt`; this is where it is kept.
     *
     * @param reminderAt ISO-8601 local date and time, matching the column's
     * encoding, or null to clear the reminder entirely.
     */
    @Query(
        "UPDATE tasks SET reminderAt = :reminderAt, reminderDeliveredAt = NULL WHERE id = :id"
    )
    suspend fun rescheduleReminder(id: String, reminderAt: String?)

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
