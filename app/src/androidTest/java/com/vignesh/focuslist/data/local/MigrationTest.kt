package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Proves that a database written by version 1 survives the move to the current
 * schema.
 *
 * This is the test the migrations exist for. It builds a genuine version-1
 * database from the exported schema, writes rows into it with raw SQL as the
 * old app would have, runs the real migrations, and reads the result back
 * through the real [FocuslistDatabase] and its DAO.
 *
 * The whole chain rather than one hop, because that is what an install that
 * has been on the phone since version 1 actually runs. A separate test covers
 * the version-2 starting point, which is the other install that exists.
 *
 * `runMigrationsAndValidate` is what makes this more than an SQL spot-check:
 * it compares the migrated table against the exported schema for the target
 * version, so a migration that produced the right rows with the wrong column
 * type would still fail here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FocuslistDatabase::class.java
    )

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")
    private val completedAt: Instant = Instant.parse("2026-01-02T17:30:00Z")
    private val scheduled: LocalDate = LocalDate.of(2026, 8, 31)
    private val due: LocalDate = LocalDate.of(2026, 9, 4)

    /**
     * Writes a row the way version 1 would have, naming its nine columns
     * explicitly so this test keeps describing version 1 however the entity
     * changes later.
     */
    private fun SupportSQLiteDatabase.insertV1(
        id: String,
        title: String,
        placement: String,
        createdAtMillis: Long,
        scheduledEpochDay: Long?,
        dueEpochDay: Long?,
        durationMinutes: Int?,
        completedAtMillis: Long?,
        deletedAtMillis: Long?
    ) {
        execSQL(
            "INSERT INTO tasks (id, title, placement, createdAt, scheduledDate, dueDate, " +
                "estimatedDurationMinutes, completedAt, deletedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                id, title, placement, createdAtMillis, scheduledEpochDay, dueEpochDay,
                durationMinutes, completedAtMillis, deletedAtMillis
            )
        )
    }

    /** A version-1 database holding one row of every shape the app can store. */
    private fun seedVersion1() {
        helper.createDatabase(TEST_DB, 1).use { database ->
            database.insertV1(
                id = "scheduled",
                title = "Finish the landing page",
                placement = "ANYTIME",
                createdAtMillis = createdAt.toEpochMilli(),
                scheduledEpochDay = scheduled.toEpochDay(),
                dueEpochDay = due.toEpochDay(),
                durationMinutes = 45,
                completedAtMillis = null,
                deletedAtMillis = null
            )

            database.insertV1(
                id = "bare",
                title = "Reply to Priya",
                placement = "INBOX",
                createdAtMillis = createdAt.plusSeconds(60).toEpochMilli(),
                scheduledEpochDay = null,
                dueEpochDay = null,
                durationMinutes = null,
                completedAtMillis = null,
                deletedAtMillis = null
            )

            database.insertV1(
                id = "done",
                title = "Send the sprint summary",
                placement = "SOMEDAY",
                createdAtMillis = createdAt.plusSeconds(120).toEpochMilli(),
                scheduledEpochDay = scheduled.toEpochDay(),
                dueEpochDay = null,
                durationMinutes = 15,
                completedAtMillis = completedAt.toEpochMilli(),
                deletedAtMillis = null
            )
        }
    }

    private fun migrate(): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(TEST_DB, LatestVersion, true, *FocuslistMigrations)

    @Test
    fun migrationValidatesAgainstTheExportedSchema() {
        seedVersion1()

        // Throws if the migrated table does not match the exported schema for
        // the current version, column for column.
        migrate().close()
    }

    @Test
    fun migrationKeepsEveryRow() {
        seedVersion1()

        migrate().use { database ->
            database.query("SELECT id FROM tasks ORDER BY id").use { cursor ->
                val ids = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }

                assertEquals(listOf("bare", "done", "scheduled"), ids)
            }
        }
    }

    @Test
    fun migrationKeepsEveryColumnUnchanged() {
        seedVersion1()

        migrate().use { database ->
            database.query(
                "SELECT title, placement, createdAt, scheduledDate, dueDate, " +
                    "estimatedDurationMinutes, completedAt, deletedAt " +
                    "FROM tasks WHERE id = ?",
                arrayOf<Any?>("scheduled")
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals("Finish the landing page", cursor.getString(0))
                assertEquals("ANYTIME", cursor.getString(1))
                assertEquals(createdAt.toEpochMilli(), cursor.getLong(2))
                assertEquals(scheduled.toEpochDay(), cursor.getLong(3))
                assertEquals(due.toEpochDay(), cursor.getLong(4))
                assertEquals(45, cursor.getInt(5))
                assertTrue(cursor.isNull(6))
                assertTrue(cursor.isNull(7))
            }
        }
    }

    @Test
    fun migrationKeepsCompletionAndNullability() {
        seedVersion1()

        migrate().use { database ->
            database.query(
                "SELECT completedAt, scheduledDate FROM tasks WHERE id = ?",
                arrayOf<Any?>("done")
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(completedAt.toEpochMilli(), cursor.getLong(0))
                assertEquals(scheduled.toEpochDay(), cursor.getLong(1))
            }

            database.query(
                "SELECT scheduledDate, dueDate, estimatedDurationMinutes " +
                    "FROM tasks WHERE id = ?",
                arrayOf<Any?>("bare")
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }

    @Test
    fun migratedRowsDoNotRecur() {
        seedVersion1()

        migrate().use { database ->
            database.query("SELECT id, recurrence FROM tasks").use { cursor ->
                var rows = 0
                while (cursor.moveToNext()) {
                    rows++
                    // A task written before recurrence existed happens once,
                    // and a null column is how the schema says so.
                    assertTrue(cursor.getString(0) + " recurs", cursor.isNull(1))
                }

                assertEquals(3, rows)
            }
        }
    }

    /**
     * The other install that exists: one that arrived on version 2 and has
     * notes but not recurrence.
     *
     * Version 2 is where `notes` came in, so a row is written with one to
     * prove the second migration carries it rather than only proving that a
     * column of nulls survives.
     */
    @Test
    fun aVersionTwoDatabaseMigratesToTheCurrentSchema() {
        helper.createDatabase(TEST_DB, 2).use { database ->
            database.execSQL(
                "INSERT INTO tasks (id, title, notes, placement, createdAt, scheduledDate, " +
                    "dueDate, estimatedDurationMinutes, completedAt, deletedAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "noted", "Book the venue", "Ask about the Tuesday rate", "ANYTIME",
                    createdAt.toEpochMilli(), scheduled.toEpochDay(), null, 30, null, null
                )
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            LatestVersion,
            true,
            MIGRATION_2_3,
            MIGRATION_3_4
        ).use { database ->
            database.query(
                "SELECT notes, recurrence, spawnedFromId FROM tasks WHERE id = ?",
                arrayOf<Any?>("noted")
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ask about the Tuesday rate", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }

    /**
     * A row written before the column existed was made by a person, not
     * spawned by finishing something, and null is exactly what that says.
     */
    @Test
    fun migratedRowsHaveNoSpawnParent() {
        seedVersion1()

        migrate().use { database ->
            database.query("SELECT id, spawnedFromId FROM tasks").use { cursor ->
                var rows = 0
                while (cursor.moveToNext()) {
                    rows++
                    assertTrue(cursor.getString(0) + " has a spawn parent", cursor.isNull(1))
                }

                assertEquals(3, rows)
            }
        }
    }

    @Test
    fun migratedRowsHaveNoNotes() {
        seedVersion1()

        migrate().use { database ->
            database.query("SELECT id, notes FROM tasks").use { cursor ->
                var rows = 0
                while (cursor.moveToNext()) {
                    rows++
                    assertTrue(cursor.getString(0) + " has notes", cursor.isNull(1))
                }

                assertEquals(3, rows)
            }
        }
    }

    /**
     * The migrated file opens through the production database class and reads
     * back as domain tasks.
     *
     * Room validates the identity hash on open, so this fails if the migrated
     * schema and version 2 disagree at all.
     */
    @Test
    fun theMigratedDatabaseOpensThroughTheRealDatabaseClass() {
        seedVersion1()
        migrate().close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FocuslistDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*FocuslistMigrations)
            .build()

        helper.closeWhenFinished(database)

        val tasks: List<Task> = runBlocking { database.taskDao().observeTasks().first() }
            .map { entity -> entity.toDomain() }

        // Oldest first, which is the order the DAO now guarantees.
        assertEquals(listOf("scheduled", "bare", "done"), tasks.map { it.id })

        val scheduledTask = tasks.single { it.id == "scheduled" }
        assertEquals("Finish the landing page", scheduledTask.title)
        assertEquals(TaskPlacement.ANYTIME, scheduledTask.placement)
        assertEquals(scheduled, scheduledTask.scheduledDate)
        assertEquals(due, scheduledTask.dueDate)
        assertEquals(45, scheduledTask.estimatedDurationMinutes)
        assertNull(scheduledTask.notes)

        assertNull(scheduledTask.recurrence)

        assertTrue(tasks.single { it.id == "done" }.isCompleted)
        assertTrue(tasks.all { it.notes == null })
        assertTrue(tasks.all { it.recurrence == null })
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** The schema every migration in this test is aimed at. */
        const val LatestVersion = 4
    }
}
