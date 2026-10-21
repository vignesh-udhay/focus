package app.tellcat.ui.task

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.tellcat.core.domain.Task
import app.tellcat.ui.share.ShareActivity

/**
 * [task] as text someone else can read, for sending through Android's share
 * sheet: the title, the notes, then the checklist. D-096.
 *
 * A copy, not a link. Tellcat has no account and no server, D-006, so what is
 * sent is what the task said at that moment. Plain text because the person
 * receiving it may not have Tellcat: it has to read properly in a chat app.
 *
 * Done items are sent as done rather than left out, so the copy says what the
 * list says.
 */
fun taskShareText(task: Task): String = buildString {
    append(task.title)
    val notes = task.notes?.trim()?.takeIf { it.isNotEmpty() }
    notes?.let { append('\n').append(it) }
    // A blank line keeps the notes from reading as the first item.
    if (notes != null && task.checklist.isNotEmpty()) append('\n')
    task.checklist.forEach { item ->
        append('\n')
        append(if (item.done) DoneMark else OpenMark)
        append(' ')
        append(item.text)
    }
}

/**
 * Opens Android's share sheet with [task]: Quick Share to a phone nearby, a chat
 * app, email, whatever the user has. The title doubles as an email's subject;
 * chat apps ignore it.
 */
internal fun shareTask(context: Context, task: Task) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        // No EXTRA_TITLE: the share sheet draws it above the text, which already
        // opens with the title.
        .putExtra(Intent.EXTRA_SUBJECT, task.title)
        .putExtra(Intent.EXTRA_TEXT, taskShareText(task))

    // Tellcat's own "Add to Tellcat" left out: it would only copy the task.
    val chooser = Intent.createChooser(send, null).putExtra(
        Intent.EXTRA_EXCLUDE_COMPONENTS,
        arrayOf(ComponentName(context, ShareActivity::class.java))
    )
    context.startActivity(chooser)
}

private const val OpenMark = "☐"

// With the text-style selector, because ☑ has a colour emoji form and ☐ does
// not: an app that swaps in emoji would otherwise draw the two unlike each other.
private const val DoneMark = "☑︎"
