package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.TaskPlacement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Exercises [TaskDao] against a real in-memory Room database, so the entity,
 * the converters, and the generated SQL are all verified together.
 *
 * This is an instrumented test because Room needs an Android context and a
 * real SQLite implementation. It cannot run as a plain JVM unit test without
 * adding Robolectric or the bundled SQLite driver.
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var database: FocuslistDatabase
    private lateinit var dao: TaskDao

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")
    private val completedAt: Instant = Instant.parse("2026-01-02T17:30:00Z")
    private val deletedAt: Instant = Instant.parse("2026-01-03T08:15:00Z")
    private val scheduled: LocalDate = LocalDate.of(2026, 8, 31)
    private val due: LocalDate = LocalDate.of(2026, 9, 4)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FocuslistDatabase::class.java
        ).build()
        dao = database.taskDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        id: String,
        title: String = "Task $id",
        notes: String? = null,
        placement: TaskPlacement = TaskPlacement.ANYTIME,
        scheduledDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        reminderAt: LocalDateTime? = null,
        reminderDeliveredAt: Instant? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: Recurrence? = null,
        spawnedFromId: String? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null,
        createdAt: Instant = this@TaskDaoTest.createdAt
    ) = TaskEntity(
        id = id,
        title = title,
        notes = notes,
        placement = placement,
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt,
        estimatedDurationMinutes = estimatedDurationMinutes,
        recurrence = recurrence,
        spawnedFromId = spawnedFromId,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    private suspend fun observed(): List<TaskEntity> =
        dao.observeTasks().first().sortedBy { it.id }

    // 1

    @Test
    fun insertThenObserveReturnsTheTask() = runBlocking {
        val task = entity(id = "a", title = "Finish the landing page")

        dao.insert(task)

        assertEquals(listOf(task), observed())
    }

    // 2

    @Test
    fun insertingMultipleTasksPreservesEveryRow() = runBlocking {
        val a = entity(id = "a")
        val b = entity(id = "b")
        val c = entity(id = "c")

        dao.insert(a)
        dao.insert(b)
        dao.insert(c)

        assertEquals(listOf(a, b, c), observed())
    }

    // 3

    @Test
    fun updateChangesTheStoredTask() = runBlocking {
        val original = entity(id = "a", title = "Draft the brief")
        dao.insert(original)

        val edited = original.copy(
            title = "Draft the brief and send it",
            estimatedDurationMinutes = 30
        )
        dao.update(edited)

        assertEquals(listOf(edited), observed())
    }

    // 4

    @Test
    fun insertingAnExistingIdReplacesTheRow() = runBlocking {
        dao.insert(entity(id = "a", title = "First version"))
        val replacement = entity(id = "a", title = "Second version")

        dao.insert(replacement)

        assertEquals(listOf(replacement), observed())
    }

    // 5

    @Test
    fun softDeleteSetsDeletedAt() = runBlocking {
        dao.insert(entity(id = "a"))
        dao.insert(entity(id = "b"))

        dao.softDelete(id = "a", deletedAt = deletedAt.toEpochMilli())

        // The row is gone from the live stream, which is only possible if
        // deletedAt became non-null.
        assertEquals(listOf("b"), observed().map { it.id })
        assertEquals(deletedAt.toEpochMilli(), storedDeletedAt("a"))
    }

    /** Reads the raw column, since the DAO deliberately exposes no deleted rows. */
    private fun storedDeletedAt(id: String): Long? {
        database.query("SELECT deletedAt FROM tasks WHERE id = ?", arrayOf<Any?>(id)).use { cursor ->
            cursor.moveToFirst()
            return if (cursor.isNull(0)) null else cursor.getLong(0)
        }
    }

    // 6

    @Test
    fun observeTasksExcludesSoftDeletedTasks() = runBlocking {
        dao.insert(entity(id = "a"))
        dao.insert(entity(id = "b", deletedAt = deletedAt))

        assertEquals(listOf("a"), observed().map { it.id })
    }

    @Test
    fun aSoftDeletedTaskCanBeRestored() = runBlocking {
        val task = entity(id = "a", title = "Chase the missing invoice")
        dao.insert(task)

        dao.softDelete(id = "a", deletedAt = deletedAt.toEpochMilli())
        assertEquals(emptyList<String>(), observed().map { it.id })
        assertEquals(deletedAt.toEpochMilli(), storedDeletedAt("a"))

        dao.restore(id = "a")

        assertEquals(listOf(task), observed())
        assertNull(storedDeletedAt("a"))
    }

    // 7

    @Test
    fun aCompletedTaskRemainsObservable() = runBlocking {
        val done = entity(id = "a", completedAt = completedAt)

        dao.insert(done)

        assertEquals(listOf(done), observed())
        assertEquals(completedAt, observed().single().completedAt)
    }

    // 8

    @Test
    fun scheduledDatePersistsThroughTheDao() = runBlocking {
        dao.insert(entity(id = "a", scheduledDate = scheduled))

        assertEquals(scheduled, observed().single().scheduledDate)
    }

    @Test
    fun datesBeforeTheEpochPersistThroughTheDao() = runBlocking {
        val early = LocalDate.of(1969, 12, 31)
        dao.insert(entity(id = "a", scheduledDate = early, dueDate = early))

        val stored = observed().single()
        assertEquals(early, stored.scheduledDate)
        assertEquals(early, stored.dueDate)
    }

    // 9

    @Test
    fun nullableFieldsSurviveTheDatabaseRoundTrip() = runBlocking {
        val sparse = entity(id = "a")

        dao.insert(sparse)

        val stored = observed().single()
        assertNull(stored.scheduledDate)
        assertNull(stored.dueDate)
        assertNull(stored.estimatedDurationMinutes)
        assertNull(stored.completedAt)
        assertNull(stored.deletedAt)
        assertEquals(sparse, stored)
    }

    @Test
    fun populatedNullableFieldsSurviveTheDatabaseRoundTrip() = runBlocking {
        val full = entity(
            id = "a",
            scheduledDate = scheduled,
            dueDate = due,
            estimatedDurationMinutes = 45,
            completedAt = completedAt
        )

        dao.insert(full)

        assertEquals(full, observed().single())
    }

    // 10

    @Test
    fun everyPlacementSurvivesTheDatabaseRoundTrip() = runBlocking {
        TaskPlacement.entries.forEachIndexed { index, placement ->
            dao.insert(entity(id = "task-$index", placement = placement))
        }

        val stored = observed().map { it.placement }
        assertEquals(TaskPlacement.entries.toList(), stored)
    }

    @Test
    fun createdAtSurvivesTheDatabaseRoundTrip() = runBlocking {
        dao.insert(entity(id = "a"))

        assertEquals(createdAt, observed().single().createdAt)
    }

    @Test
    fun notesSurviveTheDatabaseRoundTrip() = runBlocking {
        dao.insert(entity(id = "a", notes = "Ask Priya which logo to use"))

        assertEquals("Ask Priya which logo to use", observed().single().notes)
    }

    @Test
    fun anAbsentNoteStaysNull() = runBlocking {
        dao.insert(entity(id = "a"))

        assertNull(observed().single().notes)
    }

    // The ordering contract

    /**
     * The stream is ordered oldest first, whatever order the rows went in.
     *
     * `TaskQueries` sorts each view for itself, but its sorts are stable, so
     * the order they are handed decides how ties come out. Focus reads the
     * head of its queue, which makes that order user-visible.
     */
    @Test
    fun observeTasksReturnsOldestFirst() = runBlocking {
        dao.insert(entity(id = "third", createdAt = createdAt.plusSeconds(120)))
        dao.insert(entity(id = "first", createdAt = createdAt))
        dao.insert(entity(id = "second", createdAt = createdAt.plusSeconds(60)))

        val ids = dao.observeTasks().first().map { it.id }

        assertEquals(listOf("first", "second", "third"), ids)
    }

    // The link between a recurring task and its successor

    /**
     * The column that lets undo find the occurrence a completion spawned.
     *
     * Every other test here leaves it null, so without this one the table
     * could be storing the id and handing back nothing and the suite would
     * still be green. That is how it was added in the first place: the column
     * reached the entity and the mappers, and this file was never taught the
     * field exists.
     */
    @Test
    fun theSpawningTaskSurvivesTheDatabaseRoundTrip() = runBlocking {
        dao.insert(entity(id = "next", spawnedFromId = "original"))

        assertEquals("original", observed().single().spawnedFromId)
    }

    /** A task somebody typed was spawned by nothing, and says so. */
    @Test
    fun aTaskWithNoOriginStaysNull() = runBlocking {
        dao.insert(entity(id = "a"))

        assertNull(observed().single().spawnedFromId)
    }

    // The ordering contract, continued

    /** Two tasks captured in the same millisecond still have one settled order. */
    @Test
    fun observeTasksBreaksEqualCreationTimesById() = runBlocking {
        dao.insert(entity(id = "c", createdAt = createdAt))
        dao.insert(entity(id = "a", createdAt = createdAt))
        dao.insert(entity(id = "b", createdAt = createdAt))

        val ids = dao.observeTasks().first().map { it.id }

        assertEquals(listOf("a", "b", "c"), ids)
    }
}
