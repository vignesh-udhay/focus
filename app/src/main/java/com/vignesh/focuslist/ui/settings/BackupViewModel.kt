package com.vignesh.focuslist.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vignesh.focuslist.data.repository.BackupRepository
import com.vignesh.focuslist.data.repository.FocuslistBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val isWorking: Boolean = false,
    val error: BackupOperation? = null,
    val done: BackupDone? = null,

    /**
     * A parsed backup waiting for the user to say yes, or null when none is.
     *
     * `docs/decisions.md` D-056. Choosing a file no longer restores it: the
     * file is read, this is set, and nothing is written until
     * [BackupViewModel.confirmRestore].
     */
    val pendingRestore: PendingRestore? = null
)

/**
 * What the restore confirmation says, in numbers the user can check.
 *
 * Two counts rather than a warning, because "this will replace your data" is a
 * claim the user cannot weigh and these can be weighed against each other. A
 * stale file shows a smaller [incomingTaskCount] than the
 * [currentTaskCount] on the phone, which is exactly the mistake D-056's dialog
 * exists to catch.
 *
 * Both exclude soft-deleted rows, matching the success snackbar, so the number
 * in the question and the number in the answer are measured the same way.
 */
data class PendingRestore(
    val incomingTaskCount: Int,
    val currentTaskCount: Int
)

/**
 * Which half of the page ran.
 *
 * One enum for both outcomes. It used to be `BackupError`, naming only the way
 * an operation can fail, and adding a success path would have meant a second
 * enum with the same two values and a different name.
 */
enum class BackupOperation { Export, Restore }

/**
 * An operation that finished, waiting to be announced.
 *
 * [id] distinguishes two identical results. Restoring the same file twice
 * produces the same operation and the same count, and without an id the state
 * would be `equals` to the one before it, so the screen would not notice the
 * second and would say nothing. That is the bug this whole path exists to fix,
 * and it would have come straight back in the quietest possible form.
 */
data class BackupDone(
    val operation: BackupOperation,
    val taskCount: Int,
    val id: Long
)

/** Owns file IO and keeps it off the composition and main thread. */
class BackupViewModel(
    private val repository: BackupRepository,
    private val contentResolver: ContentResolver
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = mutableState.asStateFlow()

    private var completions = 0L

    /**
     * The parsed file behind [BackupUiState.pendingRestore].
     *
     * Held here rather than in the state because the screen has no use for it:
     * the dialog draws two counts, and the tasks themselves are only wanted if
     * the user says yes. Dropped on cancel, on error, and once applied, so a
     * declined file is not left waiting for a later confirmation to pick up.
     */
    private var pendingBackup: FocuslistBackup? = null

    fun exportTo(uri: Uri) = run(BackupOperation.Export) {
        val written = contentResolver.openOutputStream(uri, "wt")
            ?.use { output -> repository.exportTo(output) }
            ?: error("Could not open backup destination")

        finished(BackupOperation.Export, written)
    }

    /**
     * Reads [uri] and asks, per `docs/decisions.md` D-056.
     *
     * Nothing is written. A file that cannot be parsed raises the ordinary
     * restore error from here, before the dialog, so the user is never asked to
     * confirm a restore that could not have worked.
     */
    fun restoreFrom(uri: Uri) = run(BackupOperation.Restore) {
        val backup = contentResolver.openInputStream(uri)
            ?.use { input -> repository.readBackup(input) }
            ?: error("Could not open backup file")

        pendingBackup = backup
        BackupUiState(
            pendingRestore = PendingRestore(
                // Counted the way the repository counts and the snackbar
                // reports: a backup carries soft-deleted rows so the trash can
                // be put back, and no list in the app shows them.
                incomingTaskCount = backup.tasks.count { row -> row.deletedAt == null },
                currentTaskCount = repository.currentTaskCount()
            )
        )
    }

    /**
     * Replaces the database with the file the user has now agreed to.
     *
     * The parsed file is dropped before the write rather than after it, so a
     * write that fails cannot leave a declined backup sitting behind the error
     * dialog waiting for something to apply it.
     */
    fun confirmRestore() {
        if (mutableState.value.isWorking) return

        val backup = pendingBackup ?: return
        pendingBackup = null

        run(BackupOperation.Restore) {
            finished(BackupOperation.Restore, repository.applyRestore(backup))
        }
    }

    /** Leaves the database exactly as it was, and drops the parsed file. */
    fun cancelRestore() {
        pendingBackup = null
        mutableState.update { it.copy(pendingRestore = null) }
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    /** Called once the screen has announced a result, so it is not announced twice. */
    fun consumeDone() {
        mutableState.update { it.copy(done = null) }
    }

    /**
     * Runs one piece of file work off the main thread, and lands on exactly one
     * outcome.
     *
     * Success used to land on `BackupUiState()`, which is the state the screen
     * starts in: the picker closed and nothing else happened, on a page that
     * shows no tasks and so offered no other evidence. Both outcomes are carried
     * now.
     *
     * [work] returns the state it succeeded onto rather than a task count, and
     * D-056 is why: restore has two steps now, and the first one succeeds onto a
     * question rather than onto a result. Failure is still shared, because every
     * one of them fails the same way, and it clears any parsed file with it.
     */
    private fun run(operation: BackupOperation, work: suspend () -> BackupUiState) {
        if (mutableState.value.isWorking) return

        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = BackupUiState(isWorking = true)
            mutableState.value = try {
                work()
            } catch (_: Exception) {
                pendingBackup = null
                BackupUiState(error = operation)
            }
        }
    }

    /** The state an operation that wrote something lands on. */
    private fun finished(operation: BackupOperation, taskCount: Int) = BackupUiState(
        done = BackupDone(operation = operation, taskCount = taskCount, id = ++completions)
    )

    class Factory(
        private val repository: BackupRepository,
        private val contentResolver: ContentResolver
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BackupViewModel(repository, contentResolver) as T
    }
}

