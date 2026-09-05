package com.vignesh.focuslist.data.repository

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.data.local.TaskDao
import com.vignesh.focuslist.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A hand-written stand-in for the Room-generated DAO.
 *
 * [TaskDao] is an interface, so it can be implemented directly with no mocking
 * library. It records what it was asked to do, which is exactly what these
 * tests need to assert about delegation.
 */
private class FakeTaskDao : TaskDao {

    val emissions = MutableStateFlow<List<TaskEntity>>(emptyList())

    val inserted = mutableListOf<TaskEntity>()
    val updated = mutableListOf<TaskEntity>()
    val softDeleted = mutableListOf<Pair<String, Long>>()
    val restored = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override fun observeTasks(): Flow<List<TaskEntity>> = emissions

    override suspend fun insert(task: TaskEntity) {
        inserted += task
    }

    override suspend fun update(task: TaskEntity) {
        updated += task
    }

    override suspend fun softDelete(id: String, deletedAt: Long) {
        softDeleted += id to deletedAt
    }

    override suspend fun restore(id: String) {
        restored += id
    }

    override suspend fun deleteById(id: String) {
        deleted += id
    }
}

class TaskRepositoryTest {

    private val dao = FakeTaskDao()
    private val repository = TaskRepository(dao)

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")
    private val completedAt: Instant = Instant.parse("2026-01-02T17:30:00Z")
    private val deletedAt: Instant = Instant.parse("2026-01-03T08:15:00.123Z")
    private val scheduled: LocalDate = LocalDate.of(2026, 8, 31)
    private val due: LocalDate = LocalDate.of(2026, 9, 4)

    private fun entity(
        id: String,
        title: String = "Task $id",
        notes: String? = null,
        placement: TaskPlacement = TaskPlacement.ANYTIME,
        scheduledDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        reminderAt: LocalDateTime? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: Recurrence? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = TaskEntity(
        id = id,
        title = title,
        notes = notes,
        placement = placement,
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        reminderAt = reminderAt,
        estimatedDurationMinutes = estimatedDurationMinutes,
        recurrence = recurrence,
        spawnedFromId = null,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    private fun task(
        id: String,
        title: String = "Task $id",
        notes: String? = null,
        placement: TaskPlacement = TaskPlacement.ANYTIME,
        scheduledDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        reminderAt: LocalDateTime? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: Recurrence? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = Task(
        id = id,
        title = title,
        createdAt = createdAt,
        notes = notes,
        placement = placement,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        reminderAt = reminderAt,
        estimatedDurationMinutes = estimatedDurationMinutes,
        recurrence = recurrence,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    // 1

    @Test
    fun `observeTasks maps entities to domain tasks`() = runBlocking {
        dao.emissions.value = listOf(
            entity(
                id = "a",
                title = "Finish the landing page",
                scheduledDate = scheduled,
                dueDate = due,
                estimatedDurationMinutes = 45
            )
        )

        val tasks = repository.observeTasks().first()

        assertEquals(
            listOf(
                task(
                    id = "a",
                    title = "Finish the landing page",
                    scheduledDate = scheduled,
                    dueDate = due,
                    estimatedDurationMinutes = 45
                )
            ),
            tasks
        )
    }

    @Test
    fun `observeTasks re-emits when the dao emits again`() = runBlocking {
        dao.emissions.value = listOf(entity(id = "a"))
        assertEquals(listOf("a"), repository.observeTasks().first().map { it.id })

        dao.emissions.value = listOf(entity(id = "a"), entity(id = "b"))
        assertEquals(listOf("a", "b"), repository.observeTasks().first().map { it.id })
    }

    // 2

    @Test
    fun `insert maps the task to an entity and delegates`() = runBlocking {
        val subject = task(id = "a", title = "Book the dentist", scheduledDate = scheduled)

        repository.insert(subject)

        assertEquals(1, dao.inserted.size)
        assertEquals(
            entity(id = "a", title = "Book the dentist", scheduledDate = scheduled),
            dao.inserted.single()
        )
        assertTrue(dao.updated.isEmpty())
    }

    // 3

    @Test
    fun `update maps the task to an entity and delegates`() = runBlocking {
        val subject = task(id = "a", title = "Renamed", completedAt = completedAt)

        repository.update(subject)

        assertEquals(1, dao.updated.size)
        assertEquals(
            entity(id = "a", title = "Renamed", completedAt = completedAt),
            dao.updated.single()
        )
        assertTrue(dao.inserted.isEmpty())
    }

    // 4

    @Test
    fun `softDelete passes the exact instant as epoch millis`() = runBlocking {
        repository.softDelete(id = "a", deletedAt = deletedAt)

        assertEquals(listOf("a" to deletedAt.toEpochMilli()), dao.softDeleted)
    }

    @Test
    fun `softDelete does not round or re-read the clock`() = runBlocking {
        val precise = Instant.ofEpochMilli(1_767_255_300_987L)

        repository.softDelete(id = "a", deletedAt = precise)

        assertEquals(1_767_255_300_987L, dao.softDeleted.single().second)
    }

    // 5

    @Test
    fun `restore delegates with the given id`() = runBlocking {
        repository.restore("a")

        assertEquals(listOf("a"), dao.restored)
    }

    // 6

    @Test
    fun `null fields survive the mapping unchanged`() = runBlocking {
        dao.emissions.value = listOf(entity(id = "a"))

        val mapped = repository.observeTasks().first().single()

        assertNull(mapped.scheduledDate)
        assertNull(mapped.dueDate)
        assertNull(mapped.estimatedDurationMinutes)
        assertNull(mapped.completedAt)
        assertNull(mapped.deletedAt)
    }

    @Test
    fun `populated nullable fields survive the mapping unchanged`() = runBlocking {
        dao.emissions.value = listOf(
            entity(
                id = "a",
                scheduledDate = scheduled,
                dueDate = due,
                estimatedDurationMinutes = 45,
                completedAt = completedAt,
                deletedAt = deletedAt
            )
        )

        val mapped = repository.observeTasks().first().single()

        assertEquals(scheduled, mapped.scheduledDate)
        assertEquals(due, mapped.dueDate)
        assertEquals(45, mapped.estimatedDurationMinutes)
        assertEquals(completedAt, mapped.completedAt)
        assertEquals(deletedAt, mapped.deletedAt)
    }

    // 7

    @Test
    fun `createdAt survives the mapping unchanged`() = runBlocking {
        dao.emissions.value = listOf(entity(id = "a"))

        assertEquals(createdAt, repository.observeTasks().first().single().createdAt)
    }

    @Test
    fun `createdAt survives a write unchanged`() = runBlocking {
        repository.insert(task(id = "a"))

        assertEquals(createdAt, dao.inserted.single().createdAt)
    }

    // 8

    @Test
    fun `every placement survives the mapping unchanged`() = runBlocking {
        dao.emissions.value = TaskPlacement.entries.mapIndexed { index, placement ->
            entity(id = "task-$index", placement = placement)
        }

        val mapped = repository.observeTasks().first().map { it.placement }

        assertEquals(TaskPlacement.entries.toList(), mapped)
    }

    @Test
    fun `every placement survives a write unchanged`() = runBlocking {
        TaskPlacement.entries.forEach { placement ->
            repository.insert(task(id = placement.name, placement = placement))
        }

        assertEquals(TaskPlacement.entries.toList(), dao.inserted.map { it.placement })
    }

    // 9

    @Test
    fun `observeTasks preserves the dao's order`() = runBlocking {
        dao.emissions.value = listOf(
            entity(id = "c"),
            entity(id = "a"),
            entity(id = "b")
        )

        assertEquals(listOf("c", "a", "b"), repository.observeTasks().first().map { it.id })
    }

    // 10

    @Test
    fun `the repository filters nothing it is given`() = runBlocking {
        // Completed, deleted, unscheduled, future-dated and every placement.
        // Deciding which of these belong in a view is TaskQueries' job.
        val everything = listOf(
            entity(id = "completed", completedAt = completedAt),
            entity(id = "deleted", deletedAt = deletedAt),
            entity(id = "today", scheduledDate = scheduled),
            entity(id = "future", scheduledDate = scheduled.plusDays(30)),
            entity(id = "unscheduled"),
            entity(id = "inbox", placement = TaskPlacement.INBOX),
            entity(id = "someday", placement = TaskPlacement.SOMEDAY)
        )
        dao.emissions.value = everything

        val mapped = repository.observeTasks().first()

        assertEquals(everything.size, mapped.size)
        assertEquals(everything.map { it.id }, mapped.map { it.id })
    }

    @Test
    fun `an empty emission maps to an empty list`() = runBlocking {
        dao.emissions.value = emptyList()

        assertEquals(emptyList<Task>(), repository.observeTasks().first())
    }
}
