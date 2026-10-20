package app.tellcat.ui.task

import app.tellcat.core.domain.Task

/**
 * [task] as text someone else can read, for sending through Android's share
 * sheet.
 *
 * A copy, not a link. Tellcat has no account and no server, `docs/decisions.md`
 * D-006, so what is sent is what the list said at that moment and nothing
 * keeps the two in step afterwards. Plain text because the person receiving it
 * may not have Tellcat: it has to read properly in a chat app.
 *
 * Here rather than in `core/domain` because it is presentation: only the share
 * action reads it, the way only a screen reads `DateLabel`.
 *
 * Done items are sent as done rather than left out, so the copy says what the
 * list says. The marks are characters any phone can show.
 */
fun checklistShareText(task: Task): String = buildString {
    append(task.title)
    task.checklist.forEach { item ->
        append('\n')
        append(if (item.done) DoneMark else OpenMark)
        append(' ')
        append(item.text)
    }
}

private const val OpenMark = '☐'
private const val DoneMark = '☑'
