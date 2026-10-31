package app.tellcat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The local Tellcat database.
 *
 * Converters are registered on [TaskEntity], which covers every field Room
 * needs to read or write here.
 *
 * Version 2 adds `notes`, version 3 adds `recurrence`, version 4 adds
 * `spawnedFromId`, version 5 adds `reminderAt`, version 6 adds
 * `reminderDeliveredAt`, version 7 adds the `reminder_deliveries` table, and
 * version 8 adds how far ahead each alarm was set, version 9 removes the
 * obsolete task placement column, and version 10 gives a recurrence rule an
 * interval, a weekday set and an end condition, version 11 stores a snooze
 * apart from its reminder, and version 12 gives a task a checklist.
 * Version 13 lets a reminder ring like an alarm, and version 14 remembers
 * when an unanswered reminder is posted once more.
 * Schemas are exported to `app/schemas` and the migrations live in
 * `Migrations.kt`; both are what let an existing install carry its tasks
 * across a version bump. See
 * `docs/design/storage.md`.
 */
@Database(
    entities = [TaskEntity::class, ReminderDeliveryEntity::class],
    version = 16,
    exportSchema = true
)
abstract class TellcatDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun reminderDeliveryDao(): ReminderDeliveryDao

    abstract fun backupDao(): BackupDao
}
