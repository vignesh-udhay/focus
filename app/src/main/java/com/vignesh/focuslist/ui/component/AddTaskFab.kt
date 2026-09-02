package com.vignesh.focuslist.ui.component

import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * no base-role floating action button in the specification. Reaching for one
 * meant leaving the spec for the app's most prominent control, which is the
 * worst place to do it.
 *
 * Shared rather than repeated because Today and Inbox draw the identical
 * button, and one of them would eventually drift.
 */
@Composable
internal fun AddTaskFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediumExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(stringResource(R.string.task_add))
    }
}
