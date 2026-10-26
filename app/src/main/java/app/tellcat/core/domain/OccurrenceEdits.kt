package app.tellcat.core.domain

import app.tellcat.data.repository.TaskRepository
import java.time.Instant
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

    /**
     * Deletes [id] at [deletedAt], and its untouched successor with it, in one
     * transaction. Returns the successors deleted, for an undo to bring back,
     * or null when there was no live row to delete.
     *
     * D-098 rule 6 applied to deleting. Before the ring, deleting an open
     * occurrence ended the series, because nothing had made the next one yet.
     * After it, the next one already existed and kept ringing. The same action
     * did two things depending on a moment the user cannot see, so an
     * untouched successor goes with it. One the user has touched stays.
     */
    suspend fun delete(id: String, deletedAt: Instant): List<String>? =
        repository.softDeleteWithSuccessors(id, deletedAt) { deleted, successor ->
            successor.isUntouchedSuccessorOf(deleted, zone())
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
    if (after.isCompleted || !isUntouchedSuccessorOf(before, zone)) return this
    return remadeFrom(after, zone)
}

/**
 * Whether this occurrence is still exactly what [previous] made, so it is
 * the app's to change or remove rather than the user's. D-098.
 *
 * Not completed, not deleted, its reminder not yet announced, and equal to
 * the occurrence [previous] makes at the moment this one was made. Only while
 * [previous] is open: a completed occurrence's successor is taken back on
 * reopening, not followed.
 */
fun Task.isUntouchedSuccessorOf(previous: Task, zone: ZoneId): Boolean {
    if (previous.isCompleted || spawnedFromId != previous.id) return false
    if (isCompleted || isDeleted || reminderDeliveredAt != null) return false
    return remadeFrom(previous, zone) == this
}

/** The occurrence [previous] makes in this one's place, at the moment this one was made. */
private fun Task.remadeFrom(previous: Task, zone: ZoneId): Task? {
    val made = LocalDateTime.ofInstant(createdAt, zone)
    return previous.nextRecurringInstance(
        today = made.toLocalDate(),
        id = id,
        createdAt = createdAt,
        now = made
    )
}
