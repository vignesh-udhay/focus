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
    val error: BackupError? = null
)

enum class BackupError { Export, Restore }

/** Owns file IO and keeps it off the composition and main thread. */
class BackupViewModel(
    private val repository: BackupRepository,
    private val contentResolver: ContentResolver
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = mutableState.asStateFlow()

    fun exportTo(uri: Uri) = run(BackupError.Export) {
        contentResolver.openOutputStream(uri, "wt")?.use { repository.exportTo(it) }
            ?: error("Could not open backup destination")
    }

    fun restoreFrom(uri: Uri) = run(BackupError.Restore) {
        contentResolver.openInputStream(uri)?.use { repository.restoreFrom(it) }
            ?: error("Could not open backup file")
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun run(error: BackupError, operation: suspend () -> Unit) {
        if (mutableState.value.isWorking) return

        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = BackupUiState(isWorking = true)
            mutableState.value = try {
                operation()
                BackupUiState()
            } catch (_: Exception) {
                BackupUiState(error = error)
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

