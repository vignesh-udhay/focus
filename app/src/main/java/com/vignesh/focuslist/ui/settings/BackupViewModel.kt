package com.vignesh.focuslist.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vignesh.focuslist.data.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val isWorking: Boolean = false,
    val error: BackupOperation? = null,
    val done: BackupDone? = null
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

    fun exportTo(uri: Uri) = run(BackupOperation.Export) {
        contentResolver.openOutputStream(uri, "wt")?.use { repository.exportTo(it) }
            ?: error("Could not open backup destination")
    }

    fun restoreFrom(uri: Uri) = run(BackupOperation.Restore) {
        contentResolver.openInputStream(uri)?.use { repository.restoreFrom(it) }
            ?: error("Could not open backup file")
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    /** Called once the screen has announced a result, so it is not announced twice. */
    fun consumeDone() {
        mutableState.update { it.copy(done = null) }
    }

    /**
     * Runs one operation and lands on exactly one outcome.
     *
     * Success used to land on `BackupUiState()`, which is the state the screen
     * starts in: the picker closed and nothing else happened, on a page that
     * shows no tasks and so offered no other evidence. Both outcomes are carried
     * now, and [work] returns the count the success message reports.
     */
    private fun run(operation: BackupOperation, work: suspend () -> Int) {
        if (mutableState.value.isWorking) return

        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = BackupUiState(isWorking = true)
            mutableState.value = try {
                val taskCount = work()
                BackupUiState(
                    done = BackupDone(
                        operation = operation,
                        taskCount = taskCount,
                        id = ++completions
                    )
                )
            } catch (_: Exception) {
                BackupUiState(error = operation)
            }
        }
    }

    class Factory(
        private val repository: BackupRepository,
        private val contentResolver: ContentResolver
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BackupViewModel(repository, contentResolver) as T
    }
}

