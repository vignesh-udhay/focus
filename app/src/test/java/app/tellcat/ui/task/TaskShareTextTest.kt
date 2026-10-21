package app.tellcat.ui.task

import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.SharedTask
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.sharedTask
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskShareTextTest {

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")

    private val groceries = listOf(ChecklistItem("Milk"), ChecklistItem("Eggs", done = true))

    @Test
    fun `a shared checklist reads as the title and one line per item`() {
        val task = Task(id = "a", title = "Groceries", createdAt = createdAt, checklist = groceries)

        assertEquals("Groceries\n☐ Milk\n☑︎ Eggs", taskShareText(task))
    }

    @Test
    fun `a task with no checklist shares as its title`() {
        assertEquals("Call the plumber", taskShareText(Task("a", "Call the plumber", createdAt)))
    }

    @Test
    fun `notes travel under the title`() {
        val task = Task("a", "Renew the car insurance", createdAt, notes = "Policy number is on the fridge.")

        assertEquals("Renew the car insurance\nPolicy number is on the fridge.", taskShareText(task))
    }

    @Test
    fun `a blank line keeps the notes apart from the list`() {
        val task = Task(
            id = "a",
            title = "Groceries",
            createdAt = createdAt,
            notes = "  From the shop on Elm Street.  ",
            checklist = groceries
        )

        assertEquals(
            "Groceries\nFrom the shop on Elm Street.\n\n☐ Milk\n☑︎ Eggs",
            taskShareText(task)
        )
    }

    @Test
    fun `blank notes are not sent`() {
        val task = Task(id = "a", title = "Groceries", createdAt = createdAt, notes = "   ", checklist = groceries)

        assertEquals("Groceries\n☐ Milk\n☑︎ Eggs", taskShareText(task))
    }

    /** D-097: what one Tellcat sends, another reads back as the same task. */
    @Test
    fun `a shared task comes back whole`() {
        val task = Task(
            id = "a",
            title = "Groceries",
            createdAt = createdAt,
            notes = "From the shop\non Elm Street.",
            checklist = groceries
        )

        assertEquals(
            SharedTask(task.title, task.notes, task.checklist),
            sharedTask(taskShareText(task), subject = task.title)
        )
    }

    /** ☑ has a colour emoji form; the selector keeps it the same style as ☐. */
    @Test
    fun `the done mark asks for its text style`() {
        val task = Task(id = "a", title = "List", createdAt = createdAt, checklist = listOf(ChecklistItem("x", done = true)))

        assertEquals('︎', taskShareText(task).substringAfter('☑').first())
    }
}
