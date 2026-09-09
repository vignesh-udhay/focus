package com.vignesh.focuslist.data.repository

import com.vignesh.focuslist.data.local.AppearancePreferences
import com.vignesh.focuslist.data.local.BackupDao
import com.vignesh.focuslist.data.local.FocuslistPreferences
import com.vignesh.focuslist.data.local.TaskEntity
import java.io.InputStream
import java.io.OutputStream

/** Coordinates the database and preference halves of one local backup. */
class BackupRepository(
    private val dao: BackupDao,
    private val preferences: FocuslistPreferences
) {
    /** @return how many tasks the file now holds, for the screen to report. */
    suspend fun exportTo(output: OutputStream): Int {
        val tasks = dao.snapshotTasks()

        FocuslistBackupCodec.write(
            output = output,
            backup = FocuslistBackup(
                preferences = preferences.state.value,
                tasks = tasks
            )
        )

        return tasks.liveCount()
    }

    /** @return how many tasks were restored, for the screen to report. */
    suspend fun restoreFrom(input: InputStream): Int {
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

        return backup.tasks.liveCount()
    }
}

/**
 * The tasks a count should mention.
 *
 * A backup carries soft-deleted rows so that a restore can put the trash back
 * exactly as it was, but every list in the app filters them out with
 * `WHERE deletedAt IS NULL`. Counting them would have the screen report a
 * number the user cannot find anywhere after they navigate away.
 */
private fun List<TaskEntity>.liveCount(): Int = count { row -> row.deletedAt == null }
