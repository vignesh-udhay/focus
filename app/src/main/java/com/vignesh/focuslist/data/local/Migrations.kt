package com.vignesh.focuslist.data.local

import androidx.room.migration.Migration

/**
 * Schema history.
 *
 * Every version bump needs a migration here and a line in the list below.
 * `FocuslistApplication` registers them all, and the builder deliberately does
 * not fall back to a destructive migration: losing a user's tasks is not an
 * acceptable outcome for a schema change, so a missing migration should fail
 * loudly in development rather than quietly empty the database in the field.
 *
 * Version 1: the original nine columns.
 * Version 2: adds `notes`.
 * Version 3: adds `recurrence`.
 * Version 4: adds `spawnedFromId`.
 * Version 5: adds `reminderAt`.
 * Version 6: adds `reminderDeliveredAt`.
 */

/**
 * Adds the nullable `notes` column.
 *
 * `ALTER TABLE ... ADD COLUMN` with no `NOT NULL` and no default is the whole
 * migration: SQLite appends the column and every existing row reads it as
 * null, which is exactly what a task written before notes existed should say
 * about its notes. Nothing else about a row is read, rewritten, or moved.
 */
val MIGRATION_1_2 = Migration(1, 2) { database ->
    database.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT")
}

/**
 * Adds the nullable `recurrence` column.
 *
 * The same shape as the migration above, and for the same reason: a task
 * written before recurrence existed does not repeat, and a null column says
 * so without a single row being read or rewritten.
 *
 * `TEXT` because `TaskConverters` stores the rule by name rather than by
 * ordinal, so reordering the enum cannot silently change what a stored task
 * repeats on.
 */
val MIGRATION_2_3 = Migration(2, 3) { database ->
    database.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT")
}

/**
 * Version 4 records which occurrence a recurring copy came from.
 *
 * Nullable and undefaulted, like the two before it. Every task already in the
 * database was made by a person rather than spawned by a completion, and null
 * is exactly what that means, so there is nothing to backfill.
 */
val MIGRATION_3_4 = Migration(3, 4) { database ->
    database.execSQL("ALTER TABLE tasks ADD COLUMN spawnedFromId TEXT")
}

/**
 * Version 5 gives a task a time to speak up.
 *
 * The same append-a-nullable-column shape as every migration before it. No
 * existing task has a reminder, and null says so without a row being touched,
 * so nothing an install already holds can be lost or changed by this.
 *
 * `TEXT` because `TaskConverters` stores a reminder as ISO-8601 rather than as
 * an epoch number. A `LocalDateTime` has no timezone and so has no honest
 * numeric form, and a column of numbers here would be indistinguishable from
 * the epoch-millis columns beside it.
 */
val MIGRATION_4_5 = Migration(4, 5) { database ->
    database.execSQL("ALTER TABLE tasks ADD COLUMN reminderAt TEXT")
}

/**
 * Version 6 records when a reminder was actually announced.
 *
 * Nullable and undefaulted like the rest. Null reads as "not yet delivered",
 * which is the right answer for every row already in the database: none of
 * them can have been announced, because the column did not exist.
 *
 * `INTEGER` rather than the `TEXT` of `reminderAt` beside it, and the
 * difference is the point. A reminder time is a wall-clock time the user
 * chose. A delivery time is a moment that happened, so it is an `Instant` and
 * persists as epoch milliseconds like `completedAt` and `deletedAt`.
 */
val MIGRATION_5_6 = Migration(5, 6) { database ->
    database.execSQL("ALTER TABLE tasks ADD COLUMN reminderDeliveredAt INTEGER")
}

/** Every migration, in order, for the builder and the migration test. */
val FocuslistMigrations =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
