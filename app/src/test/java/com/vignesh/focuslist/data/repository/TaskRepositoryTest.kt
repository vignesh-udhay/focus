package com.vignesh.focuslist.data.repository

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.Recurrence
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
    val remindersDelivered = mutableListOf<Pair<String, Long>>()
    val remindersRescheduled = mutableListOf<Pair<String, String?>>()
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

    override suspend fun markReminderDelivered(id: String, deliveredAt: Long) {
        remindersDelivered += id to deliveredAt
    }

    override suspend fun rescheduleReminder(id: String, reminderAt: String?) {
        remindersRescheduled += id to reminderAt
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
        scheduledDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        reminderAt: LocalDateTime? = null,
        reminderDeliveredAt: Instant? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: Recurrence? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = TaskEntity(
        id = id,
        title = title,
        notes = notes,
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt,
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
        scheduledDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        reminderAt: LocalDateTime? = null,
        reminderDeliveredAt: Instant? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: Recurrence? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null
    ) = Task(
        id = id,
        title = title,
        createdAt = createdAt,
        notes = notes,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt,
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

    @Test
    fun `markReminderDelivered passes the exact instant as epoch millis`() = runBlocking {
        val precise = Instant.ofEpochMilli(1_767_255_300_987L)

        repository.markReminderDelivered(id = "a", deliveredAt = precise)

        assertEquals(listOf("a" to 1_767_255_300_987L), dao.remindersDelivered)
    }

    @Test
    fun `rescheduleReminder encodes the new time the way the column stores it`() = runBlocking {
        repository.rescheduleReminder("a", LocalDateTime.of(2026, 9, 5, 9, 30))

        assertEquals(listOf("a" to "2026-09-05T09:30"), dao.remindersRescheduled)
    }

    @Test
    fun `rescheduleReminder passes null through to clear a reminder`() = runBlocking {
        repository.rescheduleReminder("a", null)

        assertEquals(listOf("a" to null), dao.remindersRescheduled)
    }

    @Test
    fun `rescheduling is one statement, so a new time cannot stay marked delivered`() =
        runBlocking {
            repository.rescheduleReminder("a", LocalDateTime.of(2026, 9, 5, 9, 30))

            // The clearing is in the SQL, not here, which is what makes it
            // impossible to move a reminder and forget. A second call to
            // markReminderDelivered would be a second statement and could be
            // skipped; this asserts nobody added one.
            assertEquals(emptyList<Pair<String, Long>>(), dao.remindersDelivered)
        }

    @Test
    fun `markReminderDelivered touches only the task it names`() = runBlocking {
        repository.markReminderDelivered(id = "a", deliveredAt = deletedAt)

        // A targeted write, not a whole-row update. It runs in a broadcast
        // receiver moments before the process is likely to be frozen, and it
        // must not overwrite an edit the user is making on screen.
        assertEquals(1, dao.remindersDelivered.size)
        assertEquals(emptyList<TaskEntity>(), dao.updated)
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
        // Completed, deleted, unscheduled and future-dated. Deciding which of
        // these belong in a view is TaskQueries' job.
        val everything = listOf(
            entity(id = "completed", completedAt = completedAt),
            entity(id = "deleted", deletedAt = deletedAt),
            entity(id = "today", scheduledDate = scheduled),
            entity(id = "future", scheduledDate = scheduled.plusDays(30)),
            entity(id = "unscheduled")
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
