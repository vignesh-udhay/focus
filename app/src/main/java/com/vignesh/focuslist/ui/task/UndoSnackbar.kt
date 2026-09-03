package com.vignesh.focuslist.ui.task

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R

/**
 * Puts the standing undo offer on [snackbarHostState].
 *
 * Completing, deleting and rescheduling all offer a way back, and all say so
 * the same way, so the offer belongs to the app rather than to any one list. Every task list
 * hosts this effect over the same view model and the same single offer: an
 * action taken on one screen is still undoable after moving to another, and
 * switching screens takes the snackbar with it and puts it back.
 *
 * One offer at a time. Keying the effect on the offer means a newer action
 * cancels the standing snackbar and replaces it rather than queueing behind
 * it, so the snackbar never outlives the state it describes.
 *
 * The screen keeps its own [SnackbarHostState] and passes it to its own
 * `Scaffold`, which is what leaves the previews stateless.
 */
@Composable
fun UndoSnackbarEffect(
    viewModel: TaskListViewModel,
    snackbarHostState: SnackbarHostState
) {
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()

    val completedMessage = stringResource(R.string.task_completed)
    val deletedMessage = stringResource(R.string.task_deleted)
    val rescheduledMessage = stringResource(R.string.task_rescheduled)
    // A separate key from the reschedule above even though the two read the
    // same today. They are different sentences about different moves, and a
    // language that distinguishes moving a task to a day from moving it to a
    // list should not be forced to pick one wording for both.
    val triagedMessage = stringResource(R.string.task_triaged)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(pendingUndo) {
        val offer = pendingUndo ?: return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = when (offer) {
                is PendingUndo.Completion -> completedMessage
                is PendingUndo.Deletion -> deletedMessage
                is PendingUndo.Reschedule -> rescheduledMessage
                is PendingUndo.Move -> triagedMessage
            },
            actionLabel = undoLabel,
            // Short, which is four seconds against the long form's ten.
            //
            // The long form was chosen because undo is the only way back, and
            // that reasoning ignored how often this appears. Completing a task
            // is the most frequent thing anyone does here, and ten seconds of
            // a bar across the bottom of the list after every tick is the same
            // tax `expressive-motion.md` refuses to put on the interaction
            // itself. A user who ticks the wrong task knows at once.
            //
            // It costs nothing in reach: Material passes either value through
            // `calculateRecommendedTimeoutMillis` with `containsControls` set,
            // so anyone who has asked the system for more time to act is given
            // it, whichever of the two is named here.
            duration = SnackbarDuration.Short
        )

        when (result) {
            SnackbarResult.ActionPerformed -> when (offer) {
                is PendingUndo.Completion -> viewModel.undoComplete(offer.taskId)
                is PendingUndo.Deletion -> viewModel.undoDelete(offer.taskId)
                is PendingUndo.Reschedule -> viewModel.undoReschedule(offer.taskId)
                is PendingUndo.Move -> viewModel.undoMove(offer.taskId)
            }
            // Let it pass and the action simply stands.
            SnackbarResult.Dismissed -> viewModel.dismissUndo(offer.taskId)
        }
    }
}
