package app.tellcat.core.domain

import app.tellcat.data.repository.TaskRepository
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Editing a task, with the next occurrence following while it is untouched.
 *
 * `docs/decisions.md` D-098. Since a reminder ringing makes the next
 * occurrence, a repeating task can have its successor while it is still open,
 * and an edit made after the ring stopped reaching the series. Setting Repeat
 * to "Doesn't repeat" on today's occurrence left tomorrow's to ring anyway; a
 * new time or a new title reached the series a day late. Before D-098 the
 * successor was made from the edited row at completion, so every one of these
 * worked. This keeps them working.
 *
 * A use case beside [TaskCompletion] because every edit path needs the same
 * rule, and the rule is not storage's to hold.
 */
class OccurrenceEdits(
    private val repository: TaskRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {

    /**
     * Applies [change] to [id] and lets its untouched successor follow, in one
     * transaction. Returns the task as it was before the edit, or null when
     * nothing was written: no live row, or [change] returned null.
     */
    suspend fun edit(id: String, change: (Task) -> Task?): Task? =
        repository.editWithSuccessor(id, change) { before, after, successor ->
            successor.followingEdit(before = before, after = after, zone = zone())
        }
}

/**
 * What this occurrence becomes when the one that made it is edited from
 * [before] to [after]: itself when it is not following, the occurrence
 * [after] would make in its place, or null when [after] makes none.
 *
 * **It follows only while untouched.** Not completed, not deleted, its
 * reminder not yet announced, and nothing about it changed since it was made.
 * The last is checked by making it again from [before] at the moment it was
 * made, which is [Task.createdAt], and comparing: an occurrence the user has
 * edited, ticked, snoozed or moved no longer matches, and is theirs. Edits to
 * an occurrence the user has touched stay where they were made.
 *
 * Only while the edited one is open. A successor made by completing is taken
 * back on reopening, so an open occurrence's successor is always one its
 * reminder made, and a completed occurrence's edits are a record, not a plan.
 *
 * Made again with the same id and creation moment, so it keeps its place and
 * its alarm, and the rule that reopening keeps what the reminder made still
 * holds.
 */
fun Task.followingEdit(before: Task, after: Task, zone: ZoneId): Task? {
    if (before.isCompleted || after.isCompleted) return this
    if (isCompleted || isDeleted || reminderDeliveredAt != null) return this

    val made = LocalDateTime.ofInstant(createdAt, zone)
    fun madeFrom(occurrence: Task) = occurrence.nextRecurringInstance(
        today = made.toLocalDate(),
        id = id,
        createdAt = createdAt,
        now = made
    )

    if (madeFrom(before) != this) return this
    return madeFrom(after)
}
