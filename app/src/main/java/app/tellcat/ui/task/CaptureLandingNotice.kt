package app.tellcat.ui.task

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import app.tellcat.R
import app.tellcat.core.domain.PrimaryList
import kotlinx.coroutines.launch

/**
 * Says where a capture landed, when that is not the list on screen.
 *
 * `docs/decisions.md` D-075. A capture that lands elsewhere vanishes from the
 * screen the user is looking at: the sheet named the day before the save, and
 * closed on it. This puts "Saved to Upcoming" on the screen's own snackbar host,
 * with View to go there.
 *
 * **Nothing when it landed here.** The new row is the confirmation, and a
 * snackbar on every capture is the noise `PRODUCT.md` principle 7 refuses.
 *
 * Through the screen's existing host, so it is announced by the same polite
 * live region as the undo offer, and queues behind a standing offer rather than
 * dismissing it, which would throw the undo away.
 *
 * Returns the function the screen calls with the list [TaskListViewModel.capture]
 * reported.
 */
@Composable
fun rememberCaptureLandingNotice(
    snackbarHostState: SnackbarHostState,
    currentList: PrimaryList,
    onOpenList: (PrimaryList) -> Unit
): (PrimaryList) -> Unit {
    val scope = rememberCoroutineScope()
    val openList by rememberUpdatedState(onOpenList)

    val savedToToday = stringResource(R.string.quick_add_saved_to_today)
    val savedToInbox = stringResource(R.string.quick_add_saved_to_inbox)
    val savedToUpcoming = stringResource(R.string.quick_add_saved_to_upcoming)
    val viewLabel = stringResource(R.string.quick_add_view)

    return remember(snackbarHostState, currentList, savedToToday, savedToInbox, savedToUpcoming, viewLabel) {
        { landed ->
            if (landed != currentList) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = when (landed) {
                            PrimaryList.TODAY -> savedToToday
                            PrimaryList.INBOX -> savedToInbox
                            PrimaryList.UPCOMING -> savedToUpcoming
                        },
                        actionLabel = viewLabel,
                        // Short, as the undo offer is. Material lengthens it for
                        // anyone who has asked the system for more time to act,
                        // because the snackbar carries an action.
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) openList(landed)
                }
            }
        }
    }
}
