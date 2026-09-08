package com.vignesh.focuslist.data.repository

import com.vignesh.focuslist.data.local.AppearancePreferences
import com.vignesh.focuslist.data.local.BackupDao
import com.vignesh.focuslist.data.local.FocuslistPreferences
import java.io.InputStream
import java.io.OutputStream

/** Coordinates the database and preference halves of one local backup. */
class BackupRepository(
    private val dao: BackupDao,
    private val preferences: FocuslistPreferences
) {
    suspend fun exportTo(output: OutputStream) {
        FocuslistBackupCodec.write(
            output = output,
            backup = FocuslistBackup(
                preferences = preferences.state.value,
                tasks = dao.snapshotTasks()
            )
        )
    }

    suspend fun restoreFrom(input: InputStream) {
        // Parse and validate before the first local write. A foreign or damaged
        // file therefore leaves the current task list exactly as it was.
        val backup = FocuslistBackupCodec.read(input)
        val previousPreferences: AppearancePreferences = preferences.state.value

        preferences.replace(backup.preferences)
        try {
            dao.replaceTasks(backup.tasks)
        } catch (failure: Throwable) {
            // SharedPreferences and Room cannot share one transaction. Restore
            // the small half if the database half fails, then surface the
            // original failure to the screen.
            runCatching { preferences.replace(previousPreferences) }
                .onFailure(failure::addSuppressed)
            throw failure
        }
    }
}
