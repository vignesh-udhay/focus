package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing

/**
 * One row of the Plan group on Task Details.
 *
 * A label at the start, its current value at the end, and a chevron saying the
 * row opens something. `docs/decisions.md` D-018 makes these the whole of how
 * the five schedulable fields are set: each row shows what it holds and opens a
 * sheet of presets.
 *
 * **The value states what is set rather than naming the sheet it opens.** A row
 * reading only "Scheduled" would hide its own contents, and the point of the row
 * over a text field is that "Due date  None" says what can be set where an empty
 * field does not.
 *
 * **An unset value is not styled differently.** `None` and `Doesn't repeat`
 * render exactly like `Today` and `45m`. `task-details.md` answers this in full
 * because the question comes up every time someone reads the screen: those are
 * values the user chose or accepted, not gaps, and dimming them would make the
 * screen read as a form with blanks to fill. There is also no token for it. The
 * next step down from `onSurfaceVariant` is `outline`, which on this row's
 * container is about 3.8:1 and fails AA at 14sp, so unset values would become
 * the hardest thing on the screen to read.
 *
 * **This wraps `SegmentedListItem`, like every other row family in the app.** It
 * briefly did not, and carried private `16.dp`, `4.dp` and `2.dp` constants of
 * its own for the corners and the gap. Those are the segmented treatment, and
 * Material already owns it through [ListItemDefaults.segmentedShapes] and
 * [ListItemDefaults.SegmentedGap], which `TaskRow`, Inbox, Today, Upcoming and
 * the Logbook all read from. A second copy of three numbers is how the same
 * group of rows ends up disagreeing about its own corners, which is exactly what
 * happened on the board before `expressive-components.md` pinned it down.
 */
@Composable
internal fun PlanRow(
    label: String,
    value: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = planRowColors()
) {
    // Announced as one action naming both halves, because a screen reader
    // reading "Scheduled" and "Today" as two nodes leaves the user to work out
    // that the second is the first's value and that either can be pressed.
    val description = stringResource(R.string.task_plan_row_action, label, value)

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = colors,
        // A floor, not a fixed height. 56dp is the row at 100%; a long value at
        // 200% wraps and the row grows to hold it rather than clipping, which is
        // the same rule every task row follows.
        modifier = modifier
            .heightIn(min = PlanRowMinHeight)
            .semantics { onClick(label = description, action = null) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )

                Icon(
                    painter = painterResource(R.drawable.ic_chevron_forward),
                    // The row already carries the action and its label.
                    // Describing the chevron too would announce it twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(PlanRowChevronSize)
                )
            }
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * The Plan group's container colour.
 *
 * `surfaceContainerLow` rather than the `segmentedColors` default, which
 * `expressive-components.md` fixes for this group: the Plan rows sit on a plain
 * `surface` page with no tinted card above them any more, so they are the only
 * thing lifting off the ground.
 */
@Composable
internal fun planRowColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
)

/** The one-line row height `expressive-components.md` gives the Plan row. */
private val PlanRowMinHeight = 56.dp

/** Material's own dense icon size, which is what the row draws the chevron at. */
private val PlanRowChevronSize = 20.dp

/**
 * Groups [content] as one connected collection.
 *
 * The gap is Material's [ListItemDefaults.SegmentedGap], the same value every
 * other collection in the app spaces its rows by.
 */
@Composable
internal fun PlanRowGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        content()
    }
}
