package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDaoTest {
    private lateinit var database: FocuslistDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FocuslistDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun snapshotIncludesSoftDeletedTasks() = runBlocking {
        val deleted = task("deleted", deletedAt = Instant.parse("2026-09-08T10:00:00Z"))
        database.taskDao().insert(deleted)

        assertEquals(listOf(deleted), database.backupDao().snapshotTasks())
    }

    @Test
    fun restoreReplacesTasksAndClearsDeviceSpecificDeliveryHistory() = runBlocking {
        database.taskDao().insert(task("old"))
        database.reminderDeliveryDao().insert(
            ReminderDeliveryEntity(
                id = "delivery",
                taskId = "old",
                taskTitle = "Old task",
                dueAt = "2026-09-08T09:30:00",
                scheduledWallAt = 1,
                scheduledElapsedAt = 2,
                arrivedWallAt = 3,
                arrivedElapsedAt = 4,
                scheduledAheadMs = 5,
                outcome = "Announced"
            )
        )
        val replacement = task("new")

        database.backupDao().replaceTasks(listOf(replacement))

        assertEquals(listOf(replacement), database.backupDao().snapshotTasks())
        assertEquals(emptyList<ReminderDeliveryEntity>(), database.reminderDeliveryDao().observeDeliveries().first())
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
}

