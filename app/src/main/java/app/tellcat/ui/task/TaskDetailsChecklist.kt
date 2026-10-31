package app.tellcat.ui.task

import androidx.compose.ui.unit.dp
import app.tellcat.ui.component.CheckBoxDrawing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.tellcat.R
import app.tellcat.core.design.TellcatMotion
import app.tellcat.core.design.TellcatHaptics
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.Task
import app.tellcat.ui.component.CheckboxStateLayerSize
import app.tellcat.ui.component.SectionLabel
import app.tellcat.ui.component.planRowColors

/**
 * The checklist under a task's title and notes: one connected group of items,
 * ending in the field that adds the next one.
 *
 * Between the identity and the plan because on a task like "Groceries" the
 * items are what the user opened the screen for.
 *
 * **A group, on the same container as the Plan rows.** `expressive-design-system.md`
 * puts a collection on a container, and a checklist is a collection. Drawn as
 * loose lines it read as stray text between the notes and the Plan group; as
 * segmented rows it is one unit the eye can find and scan, built from the same
 * `SegmentedListItem` and colour as the group below it, so the screen has two
 * groups of one kind rather than two kinds of list.
 *
 * **On a task without one, it is the add row and nothing else.** Most tasks
 * never have a checklist, and a heading over nothing on every one of them is a
 * section the screen asks the user to read past. The row says what it starts
 * ("Add checklist item"); the heading arrives with the first item.
 *
 * Each control writes as it is used, like everything on Task Details, and each
 * write is one change applied to the stored list rather than the list as drawn.
 * See `TaskDao.changeChecklist` for why.
 *
 * Ticked items stay where they are, crossed out and dimmed the way a completed
 * task row is: on a shopping list the order is usually the order of the shop,
 * and moving things would lose the user's place.
 */
@Composable
internal fun ChecklistRegion(
    taskId: String,
    checklist: List<ChecklistItem>,
    onAdd: (text: String) -> Unit,
    onSetDone: (index: Int, item: ChecklistItem, done: Boolean) -> Unit,
    onEdit: (index: Int, item: ChecklistItem, newText: String) -> Unit,
    onRemove: (index: Int, item: ChecklistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // The add row is the group's last segment, so it takes a shape too.
    val segments = checklist.size + 1

    Column(modifier = modifier) {
        if (checklist.isNotEmpty()) {
            SectionLabel(text = stringResource(R.string.task_checklist))
        }

        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            checklist.forEachIndexed { index, item ->
                // Keyed on what the row shows, so removing one item does not
                // hand its row's state, a half-typed edit included, to the item
                // that moves up into its place.
                key(index, item.text) {
                    ChecklistRow(
                        item = item,
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = segments),
                        onSetDone = { done -> onSetDone(index, item, done) },
                        onEdit = { newText -> onEdit(index, item, newText) },
                        onRemove = { onRemove(index, item) }
                    )
                }
            }

            AddChecklistItemRow(
                taskId = taskId,
                itemCount = checklist.size,
                shapes = ListItemDefaults.segmentedShapes(
                    index = checklist.size,
                    count = segments
                ),
                onAdd = onAdd
            )
        }
    }
}

/**
 * One item: a checkbox that ticks it, and its text, which is editable in place.
 *
 * **Edited where it is drawn, the way Google Keep's lists work.** Tapping the
 * text puts the cursor in it; leaving the field, or Done, writes the change.
 * There is no separate edit mode and no sheet: a typo in a shopping list is
 * fixed on the line it is on.
 *
 * **Removal is visible only on the line being edited.** An X on every row
 * stacked a column of removal controls as heavy as the checkboxes beside them;
 * a swipe hid removal entirely and fought Android's back gesture at the screen
 * edge. The X appears in the trailing slot while this line has the cursor,
 * which is the moment someone is working on an item, and clearing the text and
 * leaving it removes the item too. Either raises the usual undo. The same
 * removal is published to accessibility services as "Remove <item>", so it is
 * reachable without opening the line.
 *
 * **The checkbox is the tick, not the whole row**, because the row's text is a
 * field. The box keeps Material's 48dp target and is named by the item, so a
 * screen reader hears "Milk, checkbox, not checked".
 */
@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    shapes: ListItemShapes,
    onSetDone: (Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: () -> Unit
) {
    var draft by rememberSaveable(item.text) { mutableStateOf(item.text) }
    var editing by remember { mutableStateOf(false) }
    // Set before a removal, so the blur that follows it does not write the
    // draft back over whatever item has moved into this position.
    var removed by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val removeLabel = stringResource(R.string.task_checklist_remove, item.text)

    // The X is exactly as tall as the line it removes, so appearing never
    // makes the row taller. At its own size it outgrew the 56dp row by about
    // 4dp, and the line being edited pushed everything below it down. Its tap
    // area still reaches the 48dp floor: the list item leaves the slot room
    // and Compose extends a small target into it.
    val removeSize = with(LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.lineHeight.toDp()
    }

    fun remove() {
        removed = true
        onRemove()
    }

    // The task title's treatment, `expressive-motion.md`: colour moves on the
    // effects spec, the strikethrough simply appears. And not the completion
    // spring, which the motion rules keep for completing a task alone.
    val textColor by animateColorAsState(
        targetValue = if (item.done) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = TellcatMotion.stateColor(),
        label = "ChecklistItemColor"
    )

    SegmentedListItem(
        shapes = shapes,
        colors = planRowColors(),
        leadingContent = {
            // **The box takes the tap; the check inside only draws.** D-118:
            // the task's own rounded square at 16dp with 5dp corners, where the
            // task's is 20dp with 6dp, so an item reads as a smaller one of the
            // same kind. D-105 drew it at a 16dp box for the same lightness.
            // Scaling the checkbox itself scaled its touch target too, to
            // 42.7dp, which the semantics test caught on 2026-10-27. Here the
            // box keeps Material's target, its ripple, `Role.Checkbox` and the
            // item's name, and only the drawing is scaled.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .toggleable(
                        value = item.done,
                        role = Role.Checkbox,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = CheckboxStateLayerSize / 2),
                        // A light toggle tick, which the system plays only if
                        // touch feedback is on. `expressive-motion.md` says why
                        // this one and not the completion spring.
                        onValueChange = { done ->
                            TellcatHaptics.checklistTick(haptics, done)
                            onSetDone(done)
                        }
                    )
                    .semantics { contentDescription = item.text }
            ) {
                CheckBoxDrawing(
                    checked = item.done,
                    boxSize = ChecklistCheckSize,
                    corner = ChecklistCheckCorner
                )
            }
        },
        trailingContent = if (editing) {
            {
                IconButton(onClick = ::remove, modifier = Modifier.size(removeSize)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = removeLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicTextField(
            value = draft,
            // An item is one line, but a long one wraps rather than scrolling
            // sideways out of sight, so the field is multi-line for display and
            // a line break typed or pasted into it ends the edit instead.
            onValueChange = { value ->
                if ('\n' in value) {
                    draft = value.replace('\n', ' ')
                    focusManager.clearFocus()
                } else {
                    draft = value
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                // Strikethrough as well as colour, as on a completed task row,
                // so a ticked item survives a greyscale screen and colour
                // blindness.
                textDecoration = if (item.done) TextDecoration.LineThrough else null
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    editing = state.isFocused
                    if (!state.isFocused && !removed && draft != item.text) onEdit(draft)
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(removeLabel) {
                            remove()
                            true
                        }
                    )
                }
        )
    }
}

/**
 * The group's last segment, where items are typed.
 *
 * A `BasicTextField` rather than a `TextField`, because the segment already is
 * the container: a Material field inside it would draw its own inset, and the
 * typed text would start one inset to the right of every item above it.
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
private fun AddChecklistItemRow(
    taskId: String,
    itemCount: Int,
    shapes: ListItemShapes,
    onAdd: (String) -> Unit
) {
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

    SegmentedListItem(
        shapes = shapes,
        colors = planRowColors(),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                // The placeholder already says what this row is for.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.bringIntoViewRequester(bringIntoView)
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (draft.isBlank()) focusManager.clearFocus() else commit() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (!state.isFocused) commit()
                },
            decorationBox = { field ->
                Box {
                    if (draft.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (itemCount == 0) R.string.task_checklist_start
                                else R.string.task_checklist_add
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // One line at any font scale, so the empty row is
                            // never taller than a filled one.
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    field()
                }
            }
        )
    }
}

/**
 * A checklist item's check: 16dp, as D-105 drew it, with the corner scaled from
 * the task's 6dp on 20dp. D-118. The drawing only; the box around it keeps the
 * touch target.
 */
private val ChecklistCheckSize = 16.dp
private val ChecklistCheckCorner = 5.dp
