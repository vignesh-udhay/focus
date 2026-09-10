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

    /**
     * How many tasks the device holds right now, for the screen to compare
     * against a backup before replacing them.
     *
     * Counted the same way [liveCount] counts, so the two numbers in the
     * confirmation are measured by one rule.
     */
    suspend fun currentTaskCount(): Int = dao.countLiveTasks()

    /**
     * Parses [input] without writing anything.
     *
     * Split out from [applyRestore] for `docs/decisions.md` D-056: the
     * confirmation dialog has to name how many tasks the file holds, which it
     * cannot do before the file is read, and the user has to be able to say no
     * afterwards. A damaged or foreign file therefore fails here, before anyone
     * is asked to confirm a restore that was never going to work.
     *
     * The ordering itself is not new. This half already ran before the first
     * local write so that a bad file left the task list alone; what changed is
     * that the gap between the two halves is now where the user stands.
     */
    suspend fun readBackup(input: InputStream): FocuslistBackup =
        FocuslistBackupCodec.read(input)

    /** @return how many tasks were restored, for the screen to report. */
    suspend fun applyRestore(backup: FocuslistBackup): Int {
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
