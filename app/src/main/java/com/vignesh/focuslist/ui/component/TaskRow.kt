package com.vignesh.focuslist.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.heightIn
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
 * separated by a middot, for example "Today" and "45 min". When empty, no
 * supporting content is emitted and no vertical space is reserved for it.
 * @param isOverdue whether the task's day has already passed. Colours the
 * first metadata segment with the error role as a second cue. The date is
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
        overdueColor = MaterialTheme.colorScheme.error
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            // Strikethrough as well as colour, so completion survives a
            // greyscale screen and colour blindness alike.
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            // Two lines, then stop. A long title must not be able to push the
            // rest of the list around.
            maxLines = TitleMaxLines,
            overflow = TextOverflow.Ellipsis
        )
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
 * Only the first segment takes the error colour. `taskMetadata` puts the date
 * first whenever a task has one, and the duration, which is never overdue,
 * after it.
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
