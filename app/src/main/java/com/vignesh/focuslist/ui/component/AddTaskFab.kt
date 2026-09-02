package com.vignesh.focuslist.ui.component

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.vignesh.focuslist.R

/**
 * The capture button, on Today and Inbox.
 *
 * The only floating action button in the app. It names no colour: the Material
 * default is `primaryContainer` with `onPrimaryContainer` content, and that is
 * the documented role.
 *
 * It was briefly overridden to `primary`, to make it strong in light and pale
 * in dark the way a prominent action inverts. That was reverted. The Material
 * Components documentation gives the button four styles, Primary, Secondary,
 * Tertiary and Surface, and every one of them is a *container* role; there is
 * no base-role floating action button in the specification.
 *
 * Regular rather than extended. The extended button carried the words "Add
 * task" across 80dp of the content column and repeated, in text, what a plus on
 * a task screen already says. At 56dp it clears more of the list, and the
 * label survives as the content description, so nothing is lost to a screen
 * reader.
 *
 * The glyph is a bare plus. The design's own is `add_circle`, which draws a
 * circle inside the button's own circle; Material pairs this container with a
 * plain glyph, because the container is already the circle.
 *
 * Shared rather than repeated because Today and Inbox draw the identical
 * button, and one of them would eventually drift.
 */
@Composable
internal fun AddTaskFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.task_add)
        )
    }
}
