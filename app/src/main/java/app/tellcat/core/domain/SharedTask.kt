package app.tellcat.core.domain

/**
 * A task as another app handed it over through the share sheet, before the user
 * has confirmed it. D-097.
 *
 * No date and no reminder. Shared text was written for a person, not as a
 * capture, so a "friday" at the end of a forwarded message is not taken as an
 * instruction; the task lands in Inbox and is planned from there.
 */
data class SharedTask(
    val title: String,
    val notes: String?,
    val checklist: List<ChecklistItem>
)

/**
 * What [text] and [subject] describe, or null when they describe nothing.
 *
 * The subject is the title when there is one: browsers send a page's title
 * there and its address as the text, and Tellcat's own share puts the task's
 * title in both. Otherwise the first line is the title. Lines marked as list
 * items become the checklist, Tellcat's own ☐ and ☑ and the `[ ]` and `[x]` most
 * notes apps write; every other line is notes.
 */
fun sharedTask(text: String?, subject: String?): SharedTask? {
    val lines = text.orEmpty().lines().map(String::trimEnd)
    val heading = subject?.lines()?.joinToString(" ") { it.trim() }?.trim()?.takeIf { it.isNotEmpty() }

    val body = if (heading != null) {
        // Tellcat sends its title as the subject and as the first line too.
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first >= 0 && lines[first].trim() == heading) lines.drop(first + 1) else lines
    } else {
        lines.dropWhile { it.isBlank() }.drop(1)
    }
    val title = heading ?: lines.firstOrNull { it.isNotBlank() }?.trim() ?: return null

    val checklist = mutableListOf<ChecklistItem>()
    val notes = mutableListOf<String>()
    body.forEach { line ->
        val item = listItem(line)
        if (item != null) checklist += item else notes += line
    }

    return SharedTask(
        title = title,
        notes = notes.joinToString("\n").trim().takeIf { it.isNotEmpty() },
        checklist = checklist
    )
}

/** The checklist item [line] marks, or null when it is not a list line. */
private fun listItem(line: String): ChecklistItem? {
    val trimmed = line.trimStart().removePrefix("- ").removePrefix("* ").trimStart()
    val (done, rest) = when {
        trimmed.startsWith('☐') -> false to trimmed.drop(1)
        trimmed.startsWith('☑') || trimmed.startsWith('☒') -> true to trimmed.drop(1)
        trimmed.startsWith("[ ]") -> false to trimmed.drop(3)
        trimmed.startsWith("[x]", ignoreCase = true) -> true to trimmed.drop(3)
        else -> return null
    }
    // The selectors that ask for ☑'s text or emoji style.
    val text = rest.trimStart('︎', '️')
    return checklistItem(text)?.copy(done = done)
}
