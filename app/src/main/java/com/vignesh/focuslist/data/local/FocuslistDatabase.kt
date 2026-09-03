package com.vignesh.focuslist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The local Focuslist database.
 *
 * Converters are registered on [TaskEntity], which covers every field Room
 * needs to read or write here.
 *
 * Version 2 adds `notes`, version 3 adds `recurrence`, and version 4 adds
 * `spawnedFromId`. Schemas are
 * exported to `app/schemas` and the
 * migrations live in `Migrations.kt`; both are what let an existing install
 * carry its tasks across a version bump. See `docs/design/storage.md`.
 */
@Database(
    entities = [TaskEntity::class],
    version = 4,
    exportSchema = true
)
abstract class FocuslistDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
}
