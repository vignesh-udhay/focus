package com.vignesh.focuslist.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.ui.theme.FocuslistTheme

/**
 * A single task, rendered as one segment of a segmented task list.
 *
 * The row is presentational: it renders the state it is given and reports
 * interaction through callbacks.
 *
 * It does not know how many tasks exist or where it sits among them. The caller
 * resolves [shapes] with [ListItemDefaults.segmentedShapes] and passes the
 * result in, so the segment rounds its corners according to its position.
 *
 * @param title the task title, the dominant element of the row.
 * @param isCompleted whether the task is complete.
 * @param shapes the segment shapes for this row's position in the collection.
 * @param onToggleComplete invoked when the completion control is tapped.
 * @param onClick invoked when the row is tapped, to open task details.
 * @param onLongClick invoked when the row is long pressed. Null, the default,
 * leaves long press doing nothing.
 * @param onClickLabel describes what [onClick] does. `SegmentedListItem` takes
 * a label for the long press but not for the tap, so this is applied as a
 * semantics property: it names the existing action rather than replacing it.
 * @param onLongClickLabel describes what [onLongClick] does, so accessibility
 * services can announce and offer it. Supply it whenever [onLongClick] is set.
 * @param colors the segment colors. The default is the Material segmented
 * treatment; a collection may override the container color to sit against its
 * own background.
 * @param metadata optional supporting details, rendered below the title and
 * separated by a middot, for example "Today" and "Every week". When empty, no
 * supporting content is emitted and no vertical space is reserved for it.
 * @param trailingContent optional content at the end of the row. `TaskListRow`
 * puts the duration estimate here, where it reads as a column rather than as
 * one more clause in a sentence; a task with no estimate passes nothing and the
 * title takes the width. It carried the actions menu's button until D-023
 * removed it, and nothing here is tappable now.
 * @param isOverdue whether the task's day has already passed. Colours the
 * first metadata segment with the tertiary role as a second cue. The date is
 * always that first segment when a task has one, so this needs no more than a
 * flag; the state is still readable without colour, because an overdue task
 * shows a date where a current one reads "Today".
 */
@Composable
fun TaskRow(
    title: String,
    isCompleted: Boolean,
    shapes: ListItemShapes,
    onToggleComplete: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    metadata: List<String> = emptyList(),
    trailingContent: (@Composable () -> Unit)? = null,
    isOverdue: Boolean = false
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        // Colour changes nothing about the row's bounds, so this is an effects
        // spec. Reaching for the expressive scheme here would change nothing:
        // its effects specs are identical to the standard scheme's.
        animationSpec = FocuslistMotion.stateColor(),
        label = "TaskRowTitleColor"
    )

    // The one expressive moment in the app. Completing a task presses the
    // control in and lets it spring back; the fast spatial spec overshoots,
    // and that overshoot is the whole gesture. Nothing else here moves.
    val completionScale = remember { Animatable(1f) }
    val completionSpec = FocuslistMotion.completion<Float>()
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(isCompleted) {
        if (isFirstComposition) {
            // A row scrolling into view, or a task that was already done, has
            // nothing to celebrate.
            isFirstComposition = false
            return@LaunchedEffect
        }
        if (isCompleted) {
            completionScale.snapTo(CompletionPressScale)
            completionScale.animateTo(1f, completionSpec)
        }
    }

    val metadataText = metadataAnnotatedString(
        metadata = metadata,
        isOverdue = isOverdue,
        // `tertiary`, not `error`. Material scopes error to error states, and
        // makes it a static red that never follows the wallpaper; an overdue
        // task is neither. See `expressive-design-system.md`.
        overdueColor = MaterialTheme.colorScheme.tertiary
    )

    val toggleDescription = stringResource(
        if (isCompleted) R.string.task_row_mark_incomplete else R.string.task_row_mark_complete,
        title
    )

    SegmentedListItem(
        // Tapping the row opens the task. Completion is the checkbox's own
        // interaction, so the toggleable overload is deliberately not used.
        onClick = onClick,
        shapes = shapes,
        // A floor, not a height: a row with a metadata line or a wrapped
        // title is already taller than this and grows past it. It exists so a
        // bare one-line row cannot come out shorter than the rest of the
        // collection and make the list look ragged.
        modifier = modifier
            .heightIn(min = FocuslistDimensions.TaskRowMinHeight)
            .then(
                if (onClickLabel == null) {
                    Modifier
                } else {
                    Modifier.semantics { onClick(label = onClickLabel, action = null) }
                }
            ),
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        colors = colors,
        leadingContent = {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = onToggleComplete,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = completionScale.value
                        scaleY = completionScale.value
                    }
                    .sizeIn(
                        minWidth = FocuslistDimensions.TouchTargetMin,
                        minHeight = FocuslistDimensions.TouchTargetMin
                    )
                    .semantics { contentDescription = toggleDescription }
            )
        },
        supportingContent = if (metadataText == null) {
            null
        } else {
            {
                // Wraps rather than truncating. It is short, and a wrapped date
                // beats a clipped one.
                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    ) {
        // **The trailing content shares the title's line rather than the row's
        // middle.** `ListItem` centres its trailing slot, which lines a duration
        // up with nothing: on a two-line row the title's centre is 27dp and the
        // row's is 36dp, so the estimate floated between the two lines and the
        // column wobbled against the titles beside it.
        //
        // `alignByBaseline` on both puts them on one baseline, which is the
        // table idiom and the strongest way to say the number belongs to that
        // title. It reads from the type rather than from a constant, so it
        // holds at every font scale, and it uses the *first* baseline, so a
        // title that wraps to two lines does not drag the estimate down with
        // it.
        //
        // Material's own `ListItem` switches its trailing slot to the top for
        // three-line items, so "centre when short, anchor when tall" is the
        // platform's rule already; this generalises it rather than departing
        // from it.
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                // Strikethrough as well as colour, so completion survives a
                // greyscale screen and colour blindness alike.
                textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                // Two lines, then stop. A long title must not be able to push
                // the rest of the list around.
                maxLines = TitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
            )

            trailingContent?.let { trailing ->
                Row(
                    modifier = Modifier
                        .padding(start = FocuslistSpacing.xs)
                        .alignByBaseline()
                ) {
                    trailing()
                }
            }
        }
    }
}

/** Two lines of title, then an ellipsis. */
private const val TitleMaxLines = 2

/** How far the completion control presses in before springing back. */
private const val CompletionPressScale = 0.8f

/**
 * The metadata line, with the date coloured when the task is overdue.
 *
 * Returns null when there is nothing to say, so the row can omit its
 * supporting slot entirely rather than reserving empty space for it.
 *
 * Only the first segment takes the overdue colour, and the date is always that
 * segment on an overdue row: `taskMetadata` leads with the reminder time, and
 * suppresses it precisely when the task is overdue, because a reminder that has
 * already fired describes nothing still to come. So the two rules hold each
 * other up rather than needing an index passed between them.
 */
@Composable
private fun metadataAnnotatedString(
    metadata: List<String>,
    isOverdue: Boolean,
    overdueColor: Color
): AnnotatedString? {
    if (metadata.isEmpty()) return null

    return buildAnnotatedString {
        metadata.forEachIndexed { index, segment ->
            if (index > 0) append(MetadataSeparator)

            if (index == 0 && isOverdue) {
                withStyle(SpanStyle(color = overdueColor)) { append(segment) }
            } else {
                append(segment)
            }
        }
    }
}

private const val MetadataSeparator = " · "

private data class TaskRowSample(
    val title: String,
    val isCompleted: Boolean,
    val metadata: List<String>
)

private class TaskRowSampleProvider : PreviewParameterProvider<TaskRowSample> {
    override val values = sequenceOf(
        TaskRowSample(
            title = "Finish landing page",
            isCompleted = false,
            metadata = emptyList()
        ),
        TaskRowSample(
            title = "Review the onboarding copy",
            isCompleted = false,
            metadata = listOf("Today", "45 min")
        ),
        TaskRowSample(
            title = "Send the sprint summary",
            isCompleted = true,
            metadata = listOf("Today", "Work")
        )
    )
}

@PreviewLightDark
@Composable
private fun TaskRowPreview(
    @PreviewParameter(TaskRowSampleProvider::class) sample: TaskRowSample
) {
    FocuslistTheme(dynamicColor = false) {
        // A lone segment rounds all four corners. The surrounding colour is a
        // container role so the segment reads against it.
        Surface(color = MaterialTheme.colorScheme.surface) {
            TaskRow(
                title = sample.title,
                isCompleted = sample.isCompleted,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                onToggleComplete = {},
                onClick = {},
                modifier = Modifier.padding(FocuslistSpacing.md),
                metadata = sample.metadata
            )
        }
    }
}
