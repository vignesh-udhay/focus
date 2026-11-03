package app.tellcat.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.tellcat.core.design.TellcatSpacing

/**
 * Where the undo offer appears, on a screen with no Add button.
 *
 * A Material [SnackbarHost] drawn as Tellcat's pill, [TellcatSnackbar], inset
 * from the edges by the screen margin. The one behavioural addition is a live
 * region, so the offer is announced when it arrives rather than waiting for the
 * user to happen upon it. Undo is time limited, and an offer nobody hears is
 * not an offer.
 *
 * Polite rather than assertive: completing a task is the user's own doing, and
 * cutting off whatever a screen reader is saying to report it would be rude
 * about something that is not urgent.
 *
 * Shared so that every screen's host is the same host. Six copies of one line
 * is six chances for one of them to be missing this. Today and Inbox, which
 * have the Add button, use [AddTaskFabWithSnackbar] instead, which is this same
 * host placed beside the button.
 */
@Composable
internal fun UndoSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) { data ->
        TellcatSnackbar(data, Modifier.padding(TellcatSpacing.md))
    }
}

/**
 * The Add button, with the snackbar in the same row to its left.
 *
 * `docs/decisions.md` D-124. Scaffold puts its snackbar above the floating
 * action button, which stacked a bar on the button on the navigation bar: three
 * layers at the bottom of the list after nearly every tick. Here the two share
 * one row. Nothing stacks, Undo and View sit where the thumb already is, and the
 * pill covers no more of the list than the button's own clearance already
 * leaves free.
 *
 * It takes Scaffold's floating-action-button slot at `FabPosition.Center`,
 * filling the width. Scaffold measures that slot against the whole width less
 * any side insets and centres it, so the row lands exactly on the content
 * column the caller's [modifier] describes, with no copy of Scaffold's own
 * spacing here. The button ends where it always did: the screen margin, plus
 * the content gutter on a wide window. Scaffold lifts the slot above the
 * navigation bar by the same distance at either position.
 *
 * **The button never moves.** The row is as tall as its taller child, and the
 * button sits at the bottom of it. A one-line pill is shorter than the button
 * and is centred on it; one that wraps, at 200% or with a long message, grows
 * upwards rather than lifting the button out from under a thumb.
 *
 * The host is the same live region [UndoSnackbarHost] declares, for the same
 * reasons.
 */
@Composable
internal fun AddTaskFabWithSnackbar(
    hostState: SnackbarHostState,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = TellcatSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.md),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            SnackbarHost(
                hostState = hostState,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) { data ->
                TellcatSnackbar(data)
            }
        }

        AddTaskFab(onClick = onAddTask)
    }
}

/**
 * Material's [Snackbar], in Tellcat's colours and shape.
 *
 * D-124. Material's default is `inverseSurface`, a dark bar on a light screen
 * with square corners, and on Tellcat's calm surfaces it read as something
 * dropped on the app from outside. This keeps the component, its layout, its
 * motion and its timing, and changes three values:
 *
 * - The container is `surfaceContainerHighest`, the top of the surface ladder.
 *   It sits over the task collection, `surfaceContainer`, and next to the
 *   navigation bar, `surfaceContainerHigh`, and is the one tone that clears
 *   both. Its content is `onSurface`.
 * - The action is `primary`, the base role, so Undo and View are the strongest
 *   thing in the pill in either theme, as Material's `inversePrimary` was on
 *   the dark bar.
 * - The shape is `extraLarge`. At one line that is a full pill; when the text
 *   wraps, the corners stay at 28dp rather than rounding off the words.
 *
 * The action is built here rather than by Material's [SnackbarData] overload,
 * because that overload pads the snackbar by a fixed 12dp all round, which
 * would hold the pill off the button it sits beside. It is the same
 * [TextButton] that overload draws.
 */
@Composable
private fun TellcatSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier
) {
    val actionLabel = data.visuals.actionLabel

    Snackbar(
        modifier = modifier,
        action = actionLabel?.let { label ->
            {
                TextButton(
                    onClick = data::performAction,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(label)
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Text(data.visuals.message)
    }
}
