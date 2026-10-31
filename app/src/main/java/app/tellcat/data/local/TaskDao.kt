package app.tellcat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.tellcat.core.domain.ChecklistItem
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

    /**
     * How many occurrences [parentId] has made, deleted ones included.
     *
     * `docs/decisions.md` D-098: an occurrence makes its successor once, when
     * its reminder is announced or when it is completed, whichever comes
     * first. A successor the user deleted still counts, so the other path
     * cannot bring it back.
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE spawnedFromId = :parentId")
    suspend fun countSpawnsOf(parentId: String): Int

    /**
     * Deleted tasks that had a reminder.
     *
     * D-099. [observeTasks] leaves them out, so the reconciliation never saw
     * them to cancel the alarms they may still hold.
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL AND reminderAt IS NOT NULL")
    suspend fun findDeletedWithReminders(): List<TaskEntity>

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
     *
     * [next] is also left out when [completed] already has a successor, which
     * its reminder made when it was announced. D-098. Returns whether [next]
     * was written, so an undo only takes back what this completion made.
     */
    @Transaction
    suspend fun completeWithNext(completed: TaskEntity, next: TaskEntity?): Boolean {
        update(completed)
        if (next == null || countSpawnsOf(completed.id) > 0) return false
        insert(next)
        return true
    }

    /**
     * Records that [id]'s reminder was announced, starts its series' next
     * occurrence, and retires the missed one before it, or does none of it.
     *
     * `docs/decisions.md` D-098, atomic for D-063's reason: a series marked
     * announced without its successor has no next reminder and nothing saying
     * so. [next] is null for a task that does not recur, and is left out when
     * [id] already has a successor. [retireId] is the earlier occurrence still
     * open, deleted at [deliveredAt], or null.
     *
     * @param deliveredAt epoch milliseconds, matching the column's encoding.
     */
    @Transaction
    suspend fun recordAnnounced(
        id: String,
        deliveredAt: Long,
        next: TaskEntity?,
        retireId: String?,
        // When it is posted once more if nobody answers, or null. D-108.
        followUpAt: String? = null
    ) {
        markReminderDelivered(id, deliveredAt)
        setFollowUp(id, followUpAt)
        if (next != null && countSpawnsOf(id) == 0) insert(next)
        retireId?.let { retired -> softDelete(retired, deliveredAt) }
    }

    /**
     * Applies [edit] to [id] and lets the occurrences it made follow, in one
     * transaction, and returns the row as it was before.
     *
     * `docs/decisions.md` D-098. A repeating task's next occurrence can exist
     * while this one is still open, made when its reminder rang, and an edit
     * made after that has to reach it the way it would have reached one made
     * at completion. [follow] decides, for each live occurrence [id] made:
     * the row it becomes, the same row to leave it, or null to remove it.
     * Nothing is written when [id] has no live row or [edit] returns null.
     */
    @Transaction
    suspend fun editWithSuccessor(
        id: String,
        edit: (TaskEntity) -> TaskEntity?,
        follow: (before: TaskEntity, after: TaskEntity, successor: TaskEntity) -> TaskEntity?
    ): TaskEntity? {
        val before = findTask(id) ?: return null
        val after = edit(before) ?: return null
        update(after)
        findSpawnsOf(id).forEach { successor ->
            val followed = follow(before, after, successor)
            when {
                // Made by the app and never touched, so erased rather than
                // left deleted, as an undone completion's occurrence is.
                followed == null -> deleteById(successor.id)
                followed != successor -> update(followed)
            }
        }
        return before
    }

    /**
     * Deletes [id], and each live occurrence it made that [follows] says goes
     * with it, in one transaction. Returns the ids of those occurrences, or
     * null when [id] has no live row.
     *
     * `docs/decisions.md` D-098: an untouched next occurrence is deleted with
     * the one that made it, as it would never have existed had the reminder
     * not rung first. Soft, both of them, so one undo brings both back.
     *
     * @param deletedAt epoch milliseconds, matching the column's encoding.
     */
    @Transaction
    suspend fun softDeleteWithSuccessors(
        id: String,
        deletedAt: Long,
        follows: (deleted: TaskEntity, successor: TaskEntity) -> Boolean
    ): List<String>? {
        val deleted = findTask(id) ?: return null
        val successors = findSpawnsOf(id).filter { successor -> follows(deleted, successor) }.map { it.id }
        softDelete(id, deletedAt)
        successors.forEach { successor -> softDelete(successor, deletedAt) }
        return successors
    }

    /** Undoes the soft delete of every one of [ids], or of none. */
    @Transaction
    suspend fun restoreAll(ids: List<String>) {
        ids.forEach { id -> restore(id) }
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
    @Query("UPDATE tasks SET deletedAt = :deletedAt, followUpAt = NULL WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    /**
     * Sets or clears when [id]'s unanswered reminder is posted once more.
     * `docs/decisions.md` D-108.
     *
     * A targeted write for `markReminderDelivered`'s reasons: it happens in a
     * receiver about to be frozen, and when a task is opened, and neither may
     * race an edit the user is making.
     *
     * @param followUpAt ISO-8601 local date and time, or null to clear it.
     */
    @Query("UPDATE tasks SET followUpAt = :followUpAt WHERE id = :id")
    suspend fun setFollowUp(id: String, followUpAt: String?)

    /**
     * The early reminder was told. D-115. Only that column, so nothing else is written over.
     *
     * @param deliveredAt epoch milliseconds, matching the column's encoding.
     */
    @Query("UPDATE tasks SET earlyDeliveredAt = :deliveredAt WHERE id = :id")
    suspend fun markEarlyDelivered(id: String, deliveredAt: Long)

    /**
     * A snooze of the early reminder, D-115: owed again at [until]. The
     * reminder's own time and snooze are untouched.
     */
    @Query("UPDATE tasks SET earlySnoozedUntil = :until, earlyDeliveredAt = NULL WHERE id = :id")
    suspend fun snoozeEarlyReminder(id: String, until: String)

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
     * Snoozes a reminder to a new moment and forgets that the old one was
     * announced.
     *
     * The two halves are one statement because they are one fact. A reminder
     * given a new time has not been delivered yet, and a version of this that
     * set the time without clearing the record would produce a reminder born
     * already delivered, which never fires. That invariant is stated on
     * `Task.reminderDeliveredAt`; this is where it is kept.
     *
     * `reminderAt` is left alone. D-078: it is the time the user chose, and for
     * a recurring task the series' time, which a snooze must not move. A
     * pending follow-up goes with the old announcement, D-108.
     *
     * @param snoozedUntil ISO-8601 local date and time, matching the column's
     * encoding.
     */
    @Query(
        "UPDATE tasks SET snoozedUntil = :snoozedUntil, reminderDeliveredAt = NULL, " +
            "followUpAt = NULL WHERE id = :id"
    )
    suspend fun snoozeReminder(id: String, snoozedUntil: String)

    /**
     * Replaces [id]'s checklist and nothing else.
     *
     * @param checklist the column's encoding, from
     * `TaskConverters.checklistToLines`, or null for no checklist.
     */
    @Query("UPDATE tasks SET checklist = :checklist WHERE id = :id")
    suspend fun setChecklist(id: String, checklist: String?)

    /**
     * Applies [change] to [id]'s stored checklist, in one transaction.
     *
     * A tick is read, changed and written as a unit, and Room runs write
     * transactions one at a time. Two quick ticks on different items were two
     * separate reads of the same list otherwise, and the second write put back
     * the item the first had just ticked.
     *
     * Nothing is written for a task with no live row.
     */
    @Transaction
    suspend fun changeChecklist(id: String, change: (List<ChecklistItem>) -> List<ChecklistItem>) {
        val stored = findTask(id) ?: return
        setChecklist(id, TaskConverters.checklistToLines(change(stored.checklist.orEmpty())))
    }

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
