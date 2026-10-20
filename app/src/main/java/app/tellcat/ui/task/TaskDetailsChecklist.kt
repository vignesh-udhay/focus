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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import app.tellcat.core.design.TellcatSpacing
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
 * items are what the user opened the screen for.
 *
 * **On a task without one, it is the add field and nothing else.** Most tasks
 * never have a checklist, and a heading over nothing on every one of them is a
 * section the screen asks the user to read past. The field says what it starts
 * ("Add checklist item"); the heading arrives with the first item.
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
        if (checklist.isNotEmpty()) {
            SectionLabel(text = stringResource(R.string.task_checklist))
        }

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

        AddChecklistItemField(
            taskId = taskId,
            itemCount = checklist.size,
            onAdd = onAdd
        )
    }
}

/**
 * One item. The whole row ticks it, so the target is the line and not only the
 * box; the remove button is its own target at the trailing end.
 *
 * **The checkbox and the remove button mark the item's first line**, on the
 * title's rule in `task-details.md`: centred on a three-line item they pointed
 * at the middle line, and at large font scales at nothing in particular. The
 * offsets are measured from the line height for the reason the title's are,
 * because which of the line and the 48dp target is taller changes with the
 * font scale.
 */
@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    onSetDone: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val lineCentre = with(LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.lineHeight.toDp() / 2
    }
    val targetCentre = TellcatDimensions.TouchTargetMin / 2
    val controlTop = (lineCentre - targetCentre).coerceAtLeast(0.dp)
    val textTop = (targetCentre - lineCentre).coerceAtLeast(0.dp)
    // A gap between rows only once a line is taller than the 48dp target, at
    // large font scales, where rows would otherwise touch. At ordinary sizes
    // the target already spaces them, and a list is easier to scan dense.
    val rowGap = if (lineCentre > targetCentre) TellcatSpacing.xxs else 0.dp

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = item.done, role = Role.Checkbox, onValueChange = onSetDone)
            .padding(vertical = rowGap)
    ) {
        // Drawn only: the row above is the control, and a second one inside it
        // would be announced twice.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = controlTop)
                .size(TellcatDimensions.TouchTargetMin)
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
                .padding(start = TextFieldHorizontalInset, top = textTop, bottom = textTop)
        )

        IconButton(onClick = onRemove, modifier = Modifier.padding(top = controlTop)) {
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
 * Where items are typed.
 *
 * - **Done adds the item and keeps the keyboard up**, so a shopping list is
 *   typed one item after another without reaching for the field again.
 * - **Done on an empty field closes the keyboard.** It is how entry ends; with
 *   nothing typed there is nothing else Done could mean.
 * - **The field follows the list down.** Each item added pushes it lower, and
 *   it used to slide under the keyboard after a few, leaving the user typing
 *   into a field they could not see. After each addition it is brought back
 *   into view.
 * - **Leaving the field adds whatever is in it**, the way the title and notes
 *   commit on blur, so nothing typed is lost to a tap elsewhere.
 */
@Composable
private fun AddChecklistItemField(taskId: String, itemCount: Int, onAdd: (String) -> Unit) {
    var draft by rememberSaveable(taskId) { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val bringIntoView = remember { BringIntoViewRequester() }

    fun commit() {
        if (draft.isNotBlank()) onAdd(draft)
        draft = ""
    }

    // After the new row has been laid out, which is what moved the field.
    LaunchedEffect(itemCount) {
        if (!focused) return@LaunchedEffect
        withFrameNanos { }
        bringIntoView.bringIntoView()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.bringIntoViewRequester(bringIntoView)
    ) {
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
                    text = stringResource(
                        if (itemCount == 0) R.string.task_checklist_start
                        else R.string.task_checklist_add
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (draft.isBlank()) focusManager.clearFocus() else commit() }
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (!state.isFocused) commit()
                }
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
