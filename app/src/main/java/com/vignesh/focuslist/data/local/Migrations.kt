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

/** Every migration, in order, for the builder and the migration test. */
val FocuslistMigrations = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
