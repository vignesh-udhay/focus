package com.vignesh.focuslist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** The one atomic database boundary used by a user-controlled backup. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt, id")
    suspend fun snapshotTasks(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteTasks()

    @Query("DELETE FROM reminder_deliveries")
    suspend fun deleteReminderDeliveries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    /**
     * Restore replaces rather than merges.
     *
     * Delivery history is deliberately cleared: it measures the phone that
     * fired an alarm and would become a false report when moved to another one.
     */
    @Transaction
    suspend fun replaceTasks(tasks: List<TaskEntity>) {
        deleteReminderDeliveries()
        deleteTasks()
        insertTasks(tasks)
    }
}

