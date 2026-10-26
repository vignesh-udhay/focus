package app.tellcat.core.domain

import app.tellcat.core.time.CurrentDay
import app.tellcat.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {

    /**
     * Completes [id], and starts the next occurrence if it recurs.
     *
     * Returns the id of the occurrence that was created, or null when the task
     * does not recur, is missing, or was already complete. Callers use it to
     * offer an undo that can take the new occurrence back with it.
     *
     * Null too when the next occurrence already exists, because the reminder
     * made it when it was announced. `docs/decisions.md` D-098: an occurrence
     * makes its successor once, and finishing Monday's late leaves Tuesday's
     * as it is. An undo then has only the completion to take back.
     *
     * The completed task is left exactly as any other completed task, so it
     * reaches the Logbook on its own terms, and the next occurrence is a new
     * row rather than the same one moved: the record of having done it today
     * should survive it coming back on Thursday.
     */
    suspend fun complete(id: String): String? {
        val task = repository.findTask(id) ?: return null
        if (task.isCompleted) return null

        val completedAt = clock()
        val next = task.nextRecurringInstance(
            today = currentDay.today.first(),
            id = newId(),
            createdAt = completedAt,
            now = LocalDateTime.ofInstant(completedAt, zone())
        )

        // **One transaction, and D-063 is why.** This was an `update` followed
        // by an `insert`, so a failure between them left a recurring task
        // complete with no successor: its reminder gone, and nothing saying so.
        // `PRODUCT.md` puts that above a crash, and requires that completing an
        // occurrence produces the next one.
        val started = repository.completeWithNext(
            completed = task.copy(completedAt = completedAt),
            next = next
        )

        return if (started) next?.id else null
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
     *
     * And only one this completion made. D-098: an occurrence whose reminder
     * was announced made its successor then, before it was completed, and
     * reopening it does not take back what the completion never created.
     */
    suspend fun reopen(id: String) {
        val task = repository.findTask(id) ?: return
        val completedAt = task.completedAt ?: return

        // Asked for by parent rather than filtered out of every task. The rule
        // for which occurrence counts stays here, because it is a domain rule:
        // one that has itself been completed has its own record and its own
        // successor, and removing it would be destroying work.
        //
        // A completion stamps its successor with its own moment, `complete`
        // above, so a successor created before that moment came from the
        // reminder.
        val spawn = repository.findSpawnsOf(task.id).firstOrNull { candidate ->
            !candidate.isCompleted && !candidate.createdAt.isBefore(completedAt)
        }

        // Atomic for the mirror of D-063's reason: a failure between the two
        // writes destroyed an untouched next occurrence and left the task
        // completed, losing the reminder in the other direction.
        repository.reopenWithoutSpawn(
            reopened = task.copy(completedAt = null),
            spawnId = spawn?.id
        )
    }
}
