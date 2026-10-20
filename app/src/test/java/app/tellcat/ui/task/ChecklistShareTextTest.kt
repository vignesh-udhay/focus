package app.tellcat.ui.task

import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ChecklistShareTextTest {

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")

    @Test
    fun `a shared checklist reads as the title and one line per item`() {
        val groceries = Task(
            id = "a",
            title = "Groceries",
            createdAt = createdAt,
            checklist = listOf(ChecklistItem("Milk"), ChecklistItem("Eggs", done = true))
        )

        assertEquals("Groceries\n☐ Milk\n☑ Eggs", checklistShareText(groceries))
    }

    @Test
    fun `a task with no checklist shares as its title`() {
        assertEquals("Call the plumber", checklistShareText(Task("a", "Call the plumber", createdAt)))
    }
}
