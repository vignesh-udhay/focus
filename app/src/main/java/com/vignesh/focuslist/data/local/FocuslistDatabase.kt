package com.vignesh.focuslist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The local Focuslist database.
 *
 * Converters are registered on [TaskEntity], which covers every field Room
 * needs to read or write here.
 *
 * Version 2 adds `notes`, version 3 adds `recurrence`, version 4 adds
 * `spawnedFromId`, version 5 adds `reminderAt`, version 6 adds
 * `reminderDeliveredAt`, version 7 adds the `reminder_deliveries` table, and
 * version 8 adds how far ahead each alarm was set, and version 9 removes the
 * obsolete task placement column. Schemas are exported to `app/schemas` and
 * the migrations live in `Migrations.kt`; both are what let an existing
 * install carry its tasks across a version bump. See `docs/design/storage.md`.
 */
@Database(
    entities = [TaskEntity::class, ReminderDeliveryEntity::class],
    version = 9,
    exportSchema = true
)
abstract class FocuslistDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun reminderDeliveryDao(): ReminderDeliveryDao
}
