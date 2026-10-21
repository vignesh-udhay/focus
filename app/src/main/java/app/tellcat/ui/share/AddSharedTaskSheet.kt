package app.tellcat.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import app.tellcat.R
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.domain.SharedTask
import app.tellcat.ui.component.TellcatFieldShape
import app.tellcat.ui.component.tellcatFieldColors

/**
 * What another app shared, as a task waiting to be added. D-097.
 *
 * Quick Add's shape, so it reads as the same act: a heading, the title in a
 * filled field, the line that says where it lands, and Add task. The title can
 * be corrected here; the notes and the checklist come along as they are and are
 * edited in Task Details like any other.
 *
 * The keyboard stays down. Quick Add opens on an empty field, this one on a
 * title already written, so the likely next tap is Add task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSharedTaskSheet(
    shared: SharedTask,
    onDismiss: () -> Unit,
    onAdd: (SharedTask) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf(shared.title) }
    val add = { if (title.isNotBlank()) onAdd(shared.copy(title = title.trim())) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg)
        ) {
            Text(
                text = stringResource(R.string.share_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(bottom = TellcatSpacing.sm)
                    .semantics { heading() }
            )

            TextField(
                value = title,
                // A title is one line; a shared line break would split it.
                onValueChange = { typed -> title = typed.replace('\n', ' ') },
                colors = tellcatFieldColors(),
                shape = TellcatFieldShape,
                maxLines = 3,
                supportingText = { Text(stringResource(R.string.quick_add_saved_to_inbox)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth()
            )

            shared.notes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = TellcatSpacing.sm)
                )
            }

            // A count of what the sheet does not show, which D-084 allows.
            if (shared.checklist.isNotEmpty()) {
                val count = shared.checklist.size
                Text(
                    text = pluralStringResource(R.plurals.share_checklist_count, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = TellcatSpacing.xs)
                )
            }

            Button(
                onClick = add,
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = TellcatSpacing.md)
            ) {
                Text(stringResource(R.string.quick_add_save))
            }
        }
    }
}
