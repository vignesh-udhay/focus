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
}
