package app.tellcat.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTaskTest {

    @Test
    fun `nothing shared is no task`() {
        assertNull(sharedTask(null, null))
        assertNull(sharedTask("  \n \n", "   "))
    }

    @Test
    fun `one line is a title`() {
        assertEquals(SharedTask("Call the plumber", null, emptyList()), sharedTask("Call the plumber", null))
    }

    @Test
    fun `the first line is the title and the rest are notes`() {
        assertEquals(
            SharedTask("Renew the car insurance", "Policy number is on the fridge.", emptyList()),
            sharedTask("\nRenew the car insurance\nPolicy number is on the fridge.\n", null)
        )
    }

    @Test
    fun `marked lines are the checklist, done or not`() {
        assertEquals(
            SharedTask(
                "Groceries",
                null,
                listOf(ChecklistItem("Milk"), ChecklistItem("Eggs", done = true), ChecklistItem("Bread", done = true))
            ),
            sharedTask("Groceries\n☐ Milk\n☑︎ Eggs\n☑️ Bread", null)
        )
    }

    @Test
    fun `notes apps' brackets are list lines too`() {
        assertEquals(
            listOf(ChecklistItem("Milk"), ChecklistItem("Eggs", done = true), ChecklistItem("Tea")),
            sharedTask("Groceries\n- [ ] Milk\n- [x] Eggs\n[ ] Tea", null)!!.checklist
        )
    }

    @Test
    fun `notes keep their lines and lose the blank one before the list`() {
        assertEquals(
            SharedTask("Groceries", "From the shop\non Elm Street.", listOf(ChecklistItem("Milk"))),
            sharedTask("Groceries\nFrom the shop\non Elm Street.\n\n☐ Milk", null)
        )
    }

    @Test
    fun `a browser's page title is the title and its address the notes`() {
        assertEquals(
            SharedTask("Tellcat", "https://tellcat.app", emptyList()),
            sharedTask("https://tellcat.app", "Tellcat")
        )
    }

    @Test
    fun `a subject repeated as the first line is said once`() {
        assertEquals(
            SharedTask("Groceries", null, listOf(ChecklistItem("Milk"))),
            sharedTask("Groceries\n☐ Milk", "Groceries")
        )
    }

    @Test
    fun `an empty item is dropped`() {
        assertEquals(emptyList<ChecklistItem>(), sharedTask("Groceries\n☐   ", null)!!.checklist)
    }
}
