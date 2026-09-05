package com.vignesh.focuslist.core.domain

import com.vignesh.focuslist.core.time.CurrentDay
import com.vignesh.focuslist.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

/**
 * Finishing a task, and taking it back.
 *
 * Extracted from `TaskListViewModel`, where it lived until a notification
 * needed it. A `BroadcastReceiver` has no view model, and the alternative was
 * a second implementation of recurrence completion sitting in the receiver.
 * Two of these would drift, and the rule they encode was not cheap to get
 * right: see the reopen behaviour below, which exists because unticking a
 * daily task after the undo snackbar had gone left the user holding two.
 *
 * The first thing in `core/domain` that touches storage. Everything else here
 * is a pure function over a list that the caller supplies. This is a use case
 * rather than a query, which `AGENTS.md` puts in the domain layer when it
 * earns the separation, and one operation needed identically by a view model,
 * a notification action and later the widget is the clearest case there is.
 *
 * The clock and id generator are injected so completion is testable without
 * asserting on wall-clock time or a random UUID.
 */
class TaskCompletion(
    private val repository: TaskRepository,
    private val currentDay: CurrentDay,
    private val clock: () -> Instant = Instant::now,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {

    /**
     * Completes [id], and starts the next occurrence if it recurs.
     *
     * Returns the id of the occurrence that was created, or null when the task
     * does not recur, is missing, or was already complete. Callers use it to
     * offer an undo that can take the new occurrence back with it.
     *
     * The completed task is left exactly as any other completed task, so it
     * reaches the Logbook on its own terms, and the next occurrence is a new
     * row rather than the same one moved: the record of having done it today
     * should survive it coming back on Thursday.
     */
    suspend fun complete(id: String): String? {
        val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return null
        if (task.isCompleted) return null

        repository.update(task.copy(completedAt = clock()))

        val next = task.nextRecurringInstance(
            today = currentDay.today.first(),
            id = newId(),
            createdAt = clock()
        )
        next?.let { repository.insert(it) }

        return next?.id
    }

    /**
     * Reopens [id], taking back the copy its completion produced.
     *
     * The same thing the undo offer does, because the offer only stands for
     * four seconds and the checkbox is permanent. Without this, unticking a
     * daily task after the snackbar had gone left the user holding two: this
     * one, and tomorrow's.
     *
     * Only while that copy is untouched. A spawn that has itself been
     * completed has its own record and its own successor, and a deleted one
     * the user has already dealt with. Either way it is no longer a row nobody
     * asked for, and removing it would be destroying work rather than tidying
     * up.
     */
    suspend fun reopen(id: String) {
        val tasks = repository.observeTasks().first()
        val task = tasks.firstOrNull { it.id == id } ?: return
        if (!task.isCompleted) return

        tasks.firstOrNull { spawn ->
            spawn.spawnedFromId == task.id && !spawn.isCompleted && !spawn.isDeleted
        }?.let { spawn -> repository.delete(spawn.id) }

        repository.update(task.copy(completedAt = null))
    }
}
