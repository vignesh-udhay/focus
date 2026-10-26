package app.tellcat.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import app.tellcat.core.design.TellcatHaptics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatMotion
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.ui.theme.TellcatTheme

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
    // How TalkBack should read the metadata line, when that differs from what
    // is drawn. Null reads the line as it is.
    metadataSpoken: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    isOverdue: Boolean = false,
    // The metadata segment an alarm glyph leads: the reminder time, on a
    // reminder that rings like an alarm. D-102. Drawn only; [metadataSpoken]
    // says it in words.
    alarmSegment: Int? = null
) {
    val titleTextSize = MaterialTheme.typography.bodyLarge.fontSize
    val titleColor by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        // Colour changes nothing about the row's bounds, so this is an effects
        // spec. Reaching for the expressive scheme here would change nothing:
        // its effects specs are identical to the standard scheme's.
        animationSpec = TellcatMotion.stateColor(),
        label = "TaskRowTitleColor"
    )

    // The one expressive moment in the app. Completing a task presses the
    // control in and lets it spring back, and the strikethrough draws across the
    // title as it does. D-086. Nothing else here moves.
    //
    // The two start together on the same `completion` spring, so they land as
    // one quick gesture. D-104 moved the line off `reveal`, whose slower spring,
    // with the hold after it, made completing feel sluggish on the owner's phone.
    val haptics = LocalHapticFeedback.current
    val completionScale = remember { Animatable(1f) }
    // How much of the title the strikethrough has crossed, 0 to 1. Drawn rather
    // than set as a text decoration, so it can travel.
    val strike = remember { Animatable(if (isCompleted) 1f else 0f) }
    val completionSpec = TellcatMotion.completion<Float>()
    var isFirstComposition by remember { mutableStateOf(true) }
    var titleLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(isCompleted) {
        if (isFirstComposition) {
            // A row scrolling into view, or a task that was already done, has
            // nothing to celebrate: its line is simply there.
            isFirstComposition = false
            return@LaunchedEffect
        }
        if (isCompleted) {
            completionScale.snapTo(CompletionPressScale)
            launch { completionScale.animateTo(1f, completionSpec) }
            strike.snapTo(0f)
            strike.animateTo(1f, completionSpec)
        } else {
            // Reopening is a reversal, not a moment.
            strike.snapTo(0f)
        }
    }

    val metadataText = metadataAnnotatedString(
        metadata = metadata,
        alarmSegment = alarmSegment,
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
            .heightIn(min = TellcatDimensions.TaskRowMinHeight)
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
            // Round, not Material's square box. D-105. Same size, colours,
            // target and semantics; checklist items keep the square one.
            RoundCheck(
                checked = isCompleted,
                onCheckedChange = { completed ->
                    TellcatHaptics.taskCompletion(haptics, completed)
                    onToggleComplete(completed)
                },
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = completionScale.value
                        scaleY = completionScale.value
                    }
                    .sizeIn(
                        minWidth = TellcatDimensions.TouchTargetMin,
                        minHeight = TellcatDimensions.TouchTargetMin
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
                    style = MaterialTheme.typography.bodySmall,
                    inlineContent = AlarmGlyphContent,
                    modifier = if (metadataSpoken.isNullOrEmpty()) {
                        Modifier
                    } else {
                        Modifier.semantics { contentDescription = metadataSpoken }
                    }
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
                // Two lines, then stop. A long title must not be able to push
                // the rest of the list around.
                maxLines = TitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> titleLayout = layout },
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
                    // Strikethrough as well as colour, so completion survives a
                    // greyscale screen and colour blindness alike. Drawn over
                    // the text in the title's own colour, crossing it from where
                    // the line starts reading.
                    .drawWithContent {
                        drawContent()
                        titleLayout?.let { layout ->
                            drawStrikethrough(
                                layout = layout,
                                progress = strike.value.coerceIn(0f, 1f),
                                color = titleColor,
                                textSize = titleTextSize.toPx()
                            )
                        }
                    }
            )

            trailingContent?.let { trailing ->
                Row(
                    modifier = Modifier
                        .padding(start = TellcatSpacing.xs)
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
    alarmSegment: Int?,
    isOverdue: Boolean,
    overdueColor: Color
): AnnotatedString? {
    if (metadata.isEmpty()) return null

    return buildAnnotatedString {
        metadata.forEachIndexed { index, segment ->
            if (index > 0) append(MetadataSeparator)

            // In front of the time, as a label on it: "alarm at 6:26 PM". After
            // it, the glyph sat against the separator and could belong to
            // either side. D-102.
            if (index == alarmSegment) {
                appendInlineContent(AlarmGlyphId, AlarmGlyphAlternate)
                append(" ")
            }

            if (index == 0 && isOverdue) {
                withStyle(SpanStyle(color = overdueColor)) { append(segment) }
            } else {
                append(segment)
            }
        }
    }
}

private const val AlarmGlyphId = "alarm"

/** What the glyph stands for in plain text, which nothing reads aloud: the row's spoken line does. */
private const val AlarmGlyphAlternate = "[alarm]"

/**
 * The alarm glyph, the size of the text it sits in and in its colour, so it
 * reads as part of the line rather than as a control. D-102.
 */
private val AlarmGlyphContent = mapOf(
    AlarmGlyphId to InlineTextContent(
        Placeholder(width = 1.em, height = 1.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alarm),
            contentDescription = null,
            tint = LocalContentColor.current
        )
    }
)

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
    TellcatTheme(dynamicColor = false) {
        // A lone segment rounds all four corners. The surrounding colour is a
        // container role so the segment reads against it.
        Surface(color = MaterialTheme.colorScheme.surface) {
            TaskRow(
                title = sample.title,
                isCompleted = sample.isCompleted,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                onToggleComplete = {},
                onClick = {},
                modifier = Modifier.padding(TellcatSpacing.md),
                metadata = sample.metadata
            )
        }
    }
}

/**
 * A strikethrough across [layout], the first [progress] of its length, line by
 * line from where each line starts reading.
 *
 * Placed and weighted as Skia places a strikethrough for a font that specifies
 * none, which is what `TextDecoration.LineThrough` drew here before: its centre
 * [StrikeOffset] of the text size above the baseline, its thickness
 * [StrikeThickness] of it. A drawn line that looked different from the decoration
 * it replaces would be a second style of completed task.
 */
private fun DrawScope.drawStrikethrough(
    layout: TextLayoutResult,
    progress: Float,
    color: Color,
    textSize: Float
) {
    if (progress <= 0f) return
    val lines = 0 until layout.lineCount
    var remaining = lines.sumOf { line ->
        (layout.getLineRight(line) - layout.getLineLeft(line)).toDouble()
    }.toFloat() * progress

    for (line in lines) {
        if (remaining <= 0f) return
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        val length = minOf(remaining, right - left)
        val y = layout.getLineBaseline(line) - textSize * StrikeOffset
        val rightToLeft =
            layout.getParagraphDirection(layout.getLineStart(line)) == ResolvedTextDirection.Rtl
        val start = if (rightToLeft) right else left
        val end = if (rightToLeft) right - length else left + length
        drawLine(color, Offset(start, y), Offset(end, y), strokeWidth = textSize * StrikeThickness)
        remaining -= length
    }
}

/** Skia's default strikethrough position, as a fraction of the text size above the baseline. */
private const val StrikeOffset = 6f / 21f

/** Skia's default strikethrough thickness, as a fraction of the text size. */
private const val StrikeThickness = 1f / 18f
