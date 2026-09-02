package com.vignesh.focuslist.ui.component

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * Where the undo offer appears.
 *
 * A Material [SnackbarHost] with the appearance and motion Material gives it.
 * The one addition is a live region, so the offer is announced when it arrives
 * rather than waiting for the user to happen upon it. Undo is time limited, and
 * an offer nobody hears is not an offer.
 *
 * Polite rather than assertive: completing a task is the user's own doing, and
 * cutting off whatever a screen reader is saying to report it would be rude
 * about something that is not urgent.
 *
 * Shared so that every screen's host is the same host. Six copies of one line
 * is six chances for one of them to be missing this.
 */
@Composable
internal fun UndoSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
