package app.tellcat.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistTest {

    @Test
    fun `an item is trimmed`() {
        assertEquals(ChecklistItem("Milk"), checklistItem("  Milk "))
    }

    @Test
    fun `a blank item is no item`() {
        assertNull(checklistItem(""))
        assertNull(checklistItem("   "))
        assertNull(checklistItem("\n"))
    }

    @Test
    fun `an item pasted across lines becomes one line`() {
        assertEquals(ChecklistItem("Milk 2 litres"), checklistItem("Milk\n 2 litres"))
    }

    @Test
    fun `reset keeps every item and ticks none`() {
        val list = listOf(ChecklistItem("Milk", done = true), ChecklistItem("Eggs"))

        assertEquals(listOf(ChecklistItem("Milk"), ChecklistItem("Eggs")), list.reset())
    }

    private val groceries = listOf(ChecklistItem("Milk"), ChecklistItem("Eggs"), ChecklistItem("Bread"))

    @Test
    fun `ticking the item the user saw ticks it`() {
        assertEquals(
            listOf(ChecklistItem("Milk"), ChecklistItem("Eggs", done = true), ChecklistItem("Bread")),
            groceries.withDone(1, "Eggs", done = true)
        )
    }

    @Test
    fun `a tick on an index that now holds another item changes nothing`() {
        // Milk was removed before the tap on Eggs, at index 1, was written.
        val moved = groceries.drop(1)

        assertEquals(moved, moved.withDone(1, "Eggs", done = true))
    }

    @Test
    fun `a tick past the end changes nothing`() {
        assertEquals(groceries, groceries.withDone(3, "Eggs", done = true))
    }

    @Test
    fun `removing the item the user saw removes only it`() {
        assertEquals(
            listOf(ChecklistItem("Milk"), ChecklistItem("Bread")),
            groceries.withoutItem(1, "Eggs")
        )
    }

    @Test
    fun `a removal on an index that now holds another item changes nothing`() {
        assertEquals(groceries, groceries.withoutItem(0, "Eggs"))
    }

    @Test
    fun `an item put back goes where it was`() {
        assertEquals(groceries, groceries.withoutItem(1, "Eggs").withItemAt(1, ChecklistItem("Eggs")))
    }

    @Test
    fun `an item put back past the end goes last`() {
        assertEquals(
            listOf(ChecklistItem("Milk"), ChecklistItem("Bread")),
            listOf(ChecklistItem("Milk")).withItemAt(5, ChecklistItem("Bread"))
        )
    }

    @Test
    fun `editing an item changes its text and keeps its tick`() {
        val ticked = listOf(ChecklistItem("Mlik", done = true), ChecklistItem("Eggs"))

        assertEquals(
            listOf(ChecklistItem("Milk", done = true), ChecklistItem("Eggs")),
            ticked.withText(0, "Mlik", "Milk")
        )
    }

    @Test
    fun `an edit on an index that now holds another item changes nothing`() {
        assertEquals(groceries, groceries.withText(0, "Eggs", "Oat milk"))
    }
}
