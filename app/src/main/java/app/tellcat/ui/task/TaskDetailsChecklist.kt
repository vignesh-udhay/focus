package app.tellcat.ui.task

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.Task
import app.tellcat.ui.component.SectionLabel

/**
 * The checklist under a task's title and notes: the items, then a field to add
 * one.
 *
 * Between the identity and the plan because on a task like "Groceries" the
 * items are what the user opened the screen for. Always drawn, even empty,
 * because the field is the only way to start a checklist; an empty one is that
 * field and its label and nothing else.
 *
 * Each control writes as it is used, like everything on Task Details, and each
 * write is one change applied to the stored list rather than the list as drawn.
 * See `TaskDao.changeChecklist` for why.
 *
 * The checkboxes sit in the column the title's checkbox sits in, so the items
 * read as belonging to the task. Ticked items stay where they are, crossed out
 * and dimmed the way a completed task row is: on a shopping list the order is
 * usually the order of the shop, and moving things would lose the user's place.
 */
@Composable
internal fun ChecklistRegion(
    taskId: String,
    checklist: List<ChecklistItem>,
    onAdd: (text: String) -> Unit,
    onSetDone: (index: Int, item: ChecklistItem, done: Boolean) -> Unit,
    onRemove: (index: Int, item: ChecklistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionLabel(text = stringResource(R.string.task_checklist))

        checklist.forEachIndexed { index, item ->
            // Keyed on what the row shows, so removing one item does not hand
            // its row's state to the item that moves up into its place.
            key(index, item.text) {
                ChecklistRow(
                    item = item,
                    onSetDone = { done -> onSetDone(index, item, done) },
                    onRemove = { onRemove(index, item) }
                )
            }
        }

        AddChecklistItemField(taskId = taskId, onAdd = onAdd)
    }
}

/**
 * One item. The whole row ticks it, so the target is the line and not only the
 * box; the remove button is its own target at the trailing end.
 */
@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    onSetDone: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TellcatDimensions.TouchTargetMin)
            .toggleable(value = item.done, role = Role.Checkbox, onValueChange = onSetDone)
    ) {
        // Drawn only: the row above is the control, and a second one inside it
        // would be announced twice.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(TellcatDimensions.TouchTargetMin)
        ) {
            Checkbox(checked = item.done, onCheckedChange = null)
        }

        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            // Strikethrough as well as colour, as on a completed task row, so
            // a ticked item survives a greyscale screen and colour blindness.
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            // Starts where the add field's text starts, under the title and
            // notes, rather than at the field's edge, which sits one inset to
            // the left of the text inside it.
            modifier = Modifier
                .weight(1f)
                .padding(start = TextFieldHorizontalInset)
        )

        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.task_checklist_remove, item.text),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Material's own horizontal inset on a `TextField`, which the item text matches. */
private val TextFieldHorizontalInset = 16.dp

/**
 * Where items are typed. Done adds the item and keeps the keyboard up, so a
 * shopping list is typed one item after another without reaching for the
 * field again. Leaving the field adds whatever is in it, the way the title and
 * notes commit on blur, so nothing typed is lost to a tap elsewhere.
 */
@Composable
private fun AddChecklistItemField(taskId: String, onAdd: (String) -> Unit) {
    var draft by rememberSaveable(taskId) { mutableStateOf("") }

    fun commit() {
        if (draft.isNotBlank()) onAdd(draft)
        draft = ""
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(TellcatDimensions.TouchTargetMin)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                // The field's placeholder already says what this is for.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = borderlessFieldColors(),
            placeholder = {
                Text(
                    text = stringResource(R.string.task_checklist_add),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { state -> if (!state.isFocused) commit() }
        )
    }
}

/**
 * Opens Android's share sheet with [task] as text: Quick Share to a phone
 * nearby, a chat app, email, whatever the user has.
 *
 * The share sheet rather than a picker of Tellcat's own, because Android
 * already knows every way this phone can send text, and Quick Share is the
 * phone-to-phone path people mean when they think of AirDrop.
 */
internal fun shareChecklist(context: Context, task: Task) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        // No EXTRA_TITLE: the share sheet draws it as a heading above the text,
        // and the text already opens with the title, so the preview said it
        // twice.
        .putExtra(Intent.EXTRA_TEXT, checklistShareText(task))

    context.startActivity(Intent.createChooser(send, null))
}
