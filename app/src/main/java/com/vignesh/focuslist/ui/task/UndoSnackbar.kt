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
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(pendingUndo) {
        val offer = pendingUndo ?: return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = when (offer) {
                is PendingUndo.Completion -> completedMessage
                is PendingUndo.Deletion -> deletedMessage
                is PendingUndo.Reschedule -> rescheduledMessage
            },
            actionLabel = undoLabel,
            // Undo is the only way back, so give it the longer reading time.
            duration = SnackbarDuration.Long
        )

        when (result) {
            SnackbarResult.ActionPerformed -> when (offer) {
                is PendingUndo.Completion -> viewModel.undoComplete(offer.taskId)
                is PendingUndo.Deletion -> viewModel.undoDelete(offer.taskId)
                is PendingUndo.Reschedule -> viewModel.undoReschedule(offer.taskId)
            }
            // Let it pass and the action simply stands.
            SnackbarResult.Dismissed -> viewModel.dismissUndo(offer.taskId)
        }
    }
}
