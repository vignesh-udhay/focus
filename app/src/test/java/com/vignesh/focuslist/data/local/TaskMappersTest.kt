package com.vignesh.focuslist.data.local

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.TaskPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TaskMappersTest {

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")
    private val completedAt: Instant = Instant.parse("2026-01-02T17:30:00Z")
    private val deletedAt: Instant = Instant.parse("2026-01-03T08:15:00Z")
    private val scheduledDate: LocalDate = LocalDate.of(2026, 8, 31)
    private val dueDate: LocalDate = LocalDate.of(2026, 9, 4)

    private fun fullTask() = Task(
        id = "task-1",
        title = "Finish the landing page",
        createdAt = createdAt,
        notes = "Ask Priya which logo to use",
        placement = TaskPlacement.ANYTIME,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        estimatedDurationMinutes = 45,
        recurrence = Recurrence.WEEKLY,
        spawnedFromId = "task-0",
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    private fun fullEntity() = TaskEntity(
        id = "task-1",
        title = "Finish the landing page",
        notes = "Ask Priya which logo to use",
        placement = TaskPlacement.ANYTIME,
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        estimatedDurationMinutes = 45,
        recurrence = Recurrence.WEEKLY,
        spawnedFromId = "task-0",
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    // 1. Task -> Entity

    @Test
    fun `task maps to entity field for field`() {
        val entity = fullTask().toEntity()

        assertEquals("task-1", entity.id)
        assertEquals("Finish the landing page", entity.title)
        assertEquals("Ask Priya which logo to use", entity.notes)
        assertEquals(TaskPlacement.ANYTIME, entity.placement)
        assertEquals(createdAt, entity.createdAt)
        assertEquals(scheduledDate, entity.scheduledDate)
        assertEquals(dueDate, entity.dueDate)
        assertEquals(45, entity.estimatedDurationMinutes)
        // Neither of these was checked here before, which is how a column can
        // be added to one side of the mapping and quietly dropped on the way
        // across. "Field for field" now means it.
        assertEquals(Recurrence.WEEKLY, entity.recurrence)
        assertEquals("task-0", entity.spawnedFromId)
        assertEquals(completedAt, entity.completedAt)
        assertEquals(deletedAt, entity.deletedAt)
    }

    @Test
    fun `task to entity carries createdAt unchanged`() {
        assertEquals(createdAt, fullTask().toEntity().createdAt)
    }

    // 2. Entity -> Task

    @Test
    fun `entity maps to task field for field`() {
        val task = fullEntity().toDomain()

        assertEquals("task-1", task.id)
        assertEquals("Finish the landing page", task.title)
        assertEquals(TaskPlacement.ANYTIME, task.placement)
        assertEquals(createdAt, task.createdAt)
        assertEquals(scheduledDate, task.scheduledDate)
        assertEquals(dueDate, task.dueDate)
        assertEquals(45, task.estimatedDurationMinutes)
        assertEquals(Recurrence.WEEKLY, task.recurrence)
        assertEquals("task-0", task.spawnedFromId)
        assertEquals(completedAt, task.completedAt)
        assertEquals(deletedAt, task.deletedAt)
    }

    @Test
    fun `entity to task carries createdAt unchanged`() {
        assertEquals(createdAt, fullEntity().toDomain().createdAt)
    }

    // 3-6. Nullable fields

    @Test
    fun `null scheduledDate survives both directions`() {
        val task = fullTask().copy(scheduledDate = null)

        assertNull(task.toEntity().scheduledDate)
        assertNull(fullEntity().copy(scheduledDate = null).toDomain().scheduledDate)
    }

    @Test
    fun `null dueDate survives both directions`() {
        val task = fullTask().copy(dueDate = null)

        assertNull(task.toEntity().dueDate)
        assertNull(fullEntity().copy(dueDate = null).toDomain().dueDate)
    }

    @Test
    fun `null completedAt survives both directions`() {
        val task = fullTask().copy(completedAt = null)

        assertNull(task.toEntity().completedAt)
        assertNull(fullEntity().copy(completedAt = null).toDomain().completedAt)
    }

    @Test
    fun `null deletedAt survives both directions`() {
        val task = fullTask().copy(deletedAt = null)

        assertNull(task.toEntity().deletedAt)
        assertNull(fullEntity().copy(deletedAt = null).toDomain().deletedAt)
    }

    @Test
    fun `null estimatedDurationMinutes survives both directions`() {
        val task = fullTask().copy(estimatedDurationMinutes = null)

        assertNull(task.toEntity().estimatedDurationMinutes)
        assertNull(fullEntity().copy(estimatedDurationMinutes = null).toDomain().estimatedDurationMinutes)
    }

    @Test
    fun `a task with every optional field null round trips`() {
        val minimal = Task(
            id = "task-2",
            title = "Call the bank",
            createdAt = createdAt
        )

        assertEquals(minimal, minimal.toEntity().toDomain())
    }

    // 7. Every placement value

    @Test
    fun `every placement value survives both directions`() {
        TaskPlacement.entries.forEach { placement ->
            val task = fullTask().copy(placement = placement)

            assertEquals(placement, task.toEntity().placement)
            assertEquals(placement, fullEntity().copy(placement = placement).toDomain().placement)
        }
    }

    @Test
    fun `every placement value round trips`() {
        TaskPlacement.entries.forEach { placement ->
            val task = fullTask().copy(placement = placement)

            assertEquals(task, task.toEntity().toDomain())
        }
    }

    // 8. Round trips

    @Test
    fun `task round trips through entity preserving equality`() {
        val task = fullTask()

        assertEquals(task, task.toEntity().toDomain())
    }

    @Test
    fun `entity round trips through task preserving equality`() {
        val entity = fullEntity()

        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `round trip preserves createdAt`() {
        val task = fullTask()

        assertEquals(createdAt, task.toEntity().toDomain().createdAt)
    }

    @Test
    fun `round trip preserves derived completion and deletion state`() {
        val outstanding = fullTask().copy(completedAt = null, deletedAt = null)
        val finished = fullTask()

        assertEquals(false, outstanding.toEntity().toDomain().isCompleted)
        assertEquals(false, outstanding.toEntity().toDomain().isDeleted)
        assertEquals(true, finished.toEntity().toDomain().isCompleted)
        assertEquals(true, finished.toEntity().toDomain().isDeleted)
    }

    // 5. Notes

    @Test
    fun `notes round-trip through both mappings`() {
        val task = fullTask()

        assertEquals(task.notes, task.toEntity().toDomain().notes)
        assertEquals("Ask Priya which logo to use", task.toEntity().toDomain().notes)
    }

    @Test
    fun `null notes round-trip through both mappings`() {
        val withoutNotes = fullTask().copy(notes = null)

        assertNull(withoutNotes.toEntity().notes)
        assertNull(withoutNotes.toEntity().toDomain().notes)
    }

    @Test
    fun `entity maps notes to task`() {
        assertEquals("Ask Priya which logo to use", fullEntity().toDomain().notes)
    }

    @Test
    fun `the mappings do not invent a note`() {
        // Blank is not null. The mappers copy; turning one into the other is
        // the view model's job, and doing it here too would hide a mistake.
        val blank = fullTask().copy(notes = "")

        assertEquals("", blank.toEntity().notes)
        assertEquals("", blank.toEntity().toDomain().notes)
    }
}
