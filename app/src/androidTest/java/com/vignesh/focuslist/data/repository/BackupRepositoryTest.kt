package com.vignesh.focuslist.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.data.local.FocuslistDatabase
import com.vignesh.focuslist.data.local.FocuslistPreferences
import com.vignesh.focuslist.data.local.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * What a finished backup reports to the screen.
 *
 * The Backup page used to say nothing at all when an operation succeeded, so
 * these counts are the whole of the evidence a user gets that a restore did
 * anything. A count that does not match what the lists then show would be worse
 * than the silence it replaced.
 *
 * Instrumented rather than JVM because the repository's two halves are Room and
 * `SharedPreferences`, and both are the real ones here. The round trip also
 * means restore is fed a file this app actually wrote.
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var database: FocuslistDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(context, FocuslistDatabase::class.java).build()
        repository = BackupRepository(database.backupDao(), FocuslistPreferences(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * A backup carries soft-deleted rows, per `BackupDaoTest`, so that a restore
     * puts the trash back as it was. Every list filters them out with
     * `WHERE deletedAt IS NULL`, so counting them would report a number the user
     * cannot find anywhere.
     */
    @Test
    fun exportCountsOnlyTheTasksTheListsWillShow() = runBlocking {
        database.taskDao().insert(task("live"))
        database.taskDao().insert(task("binned", deletedAt = DeletedAt))

        assertEquals(1, repository.exportTo(ByteArrayOutputStream()))
    }

    @Test
    fun restoreReportsTheSameCountTheBackupReported() = runBlocking {
        database.taskDao().insert(task("live"))
        database.taskDao().insert(task("binned", deletedAt = DeletedAt))
        val file = ByteArrayOutputStream()
        val exported = repository.exportTo(file)

        val restored = repository.restore(file.toByteArray())

        assertEquals(exported, restored)
        assertEquals(1, restored)
    }

    /** The count is what was restored, not what happened to be there before. */
    @Test
    fun restoreCountsTheFileRatherThanTheDeviceItLandsOn() = runBlocking {
        database.taskDao().insert(task("a"))
        database.taskDao().insert(task("b"))
        val file = ByteArrayOutputStream()
        repository.exportTo(file)

        database.backupDao().replaceTasks(emptyList())

        assertEquals(2, repository.restore(file.toByteArray()))
        assertEquals(2, database.backupDao().snapshotTasks().size)
    }

    @Test
    fun anEmptyBackupRestoresAndSaysSo() = runBlocking {
        val file = ByteArrayOutputStream()
        repository.exportTo(file)

        assertEquals(0, repository.restore(file.toByteArray()))
    }

    /**
     * `docs/decisions.md` D-056 split restore into a parse and a write so the
     * confirmation dialog can stand between them. Nothing in the app calls both
     * halves back to back any more, and the round trips above are about the
     * round trip rather than about the split, so they go through this.
     */
    private suspend fun BackupRepository.restore(file: ByteArray): Int =
        applyRestore(readBackup(file.inputStream()))

    /** The number the confirmation weighs the file against, per D-056. */
    @Test
    fun theCurrentCountExcludesTheTrash() = runBlocking {
        database.taskDao().insert(task("live"))
        database.taskDao().insert(task("binned", deletedAt = DeletedAt))

        assertEquals(1, repository.currentTaskCount())
    }

    /**
     * The half the dialog sits behind. Reading a file must leave the device
     * exactly as it was, or a user who cancels has already lost.
     */
    @Test
    fun readingABackupWritesNothing() = runBlocking {
        database.taskDao().insert(task("a"))
        database.taskDao().insert(task("b"))
        val file = ByteArrayOutputStream()
        repository.exportTo(file)

        database.backupDao().replaceTasks(emptyList())
        val backup = repository.readBackup(file.toByteArray().inputStream())

        assertEquals(2, backup.tasks.size)
        assertEquals(0, database.backupDao().snapshotTasks().size)
    }

    private fun task(id: String, deletedAt: Instant? = null) = TaskEntity(
        id = id,
        title = "Task $id",
        notes = null,
        createdAt = Instant.parse("2026-09-08T08:00:00Z"),
        scheduledDate = null,
        dueDate = null,
        reminderAt = null,
        reminderDeliveredAt = null,
        estimatedDurationMinutes = null,
        recurrence = null,
        recurrenceInterval = null,
        recurrenceWeekdays = null,
        recurrenceEndDate = null,
        recurrenceEndCount = null,
        occurrenceNumber = 1,
        spawnedFromId = null,
        completedAt = null,
        deletedAt = deletedAt
    )

    private companion object {
        val DeletedAt: Instant = Instant.parse("2026-09-08T10:00:00Z")
    }
}
