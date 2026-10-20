package app.tellcat.core.domain

/**
 * One line of a task's checklist: a thing to get or do on the way to finishing
 * the task, like "Milk" on a task called "Groceries".
 *
 * Deliberately smaller than a task. It has no date, no reminder and no
 * checklist of its own: the task it belongs to is what speaks up, and the
 * items are what the user reads once it has. A checklist is how a shopping
 * list lives in Tellcat without becoming a second kind of list beside the task
 * list.
 *
 * Identified by position rather than by an id. A checklist is short, it is
 * only ever edited inside its own task, and every edit replaces the whole list.
 */
data class ChecklistItem(
    val text: String,
    val done: Boolean = false
)

/**
 * The item [text] describes, or null when it describes nothing.
 *
 * One line, trimmed. A line break inside an item would read as two items
 * everywhere the checklist is written out as lines: storage, and the text a
 * shared list becomes.
 */
fun checklistItem(text: String): ChecklistItem? =
    text.lines()
        .joinToString(" ") { it.trim() }
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let(::ChecklistItem)

/**
 * The checklist a new occurrence of a recurring task starts with: the same
 * items, none of them done.
 *
 * Saturday's groceries are bought on Saturday. The list itself is what the
 * series repeats, so next week's copy keeps every item and owes all of them.
 */
fun List<ChecklistItem>.reset(): List<ChecklistItem> = map { it.copy(done = false) }
