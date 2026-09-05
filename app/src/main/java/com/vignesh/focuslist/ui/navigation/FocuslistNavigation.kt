package com.vignesh.focuslist.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.vignesh.focuslist.R

/**
 * The routes the navigation graph knows.
 *
 * Names, not positions: a route survives screens being added, reordered, or
 * reached from somewhere new, which the switch this replaced did not.
 */
object FocuslistRoutes {

    const val TODAY = "today"
    const val INBOX = "inbox"
    const val UPCOMING = "upcoming"
    const val LOGBOOK = "logbook"
    const val REMINDER_HEALTH = "reminder-health"
}

/**
 * A destination behind the app-bar overflow.
 *
 * These are not places among the lists. They are rooms you go into and come
 * back from, which is why each draws a back arrow rather than the navigation
 * bar. Settings joins this list when it exists; nothing else has to change.
 */
private data class OverflowDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int
)

private val OverflowDestinations = listOf(
    OverflowDestination(
        FocuslistRoutes.LOGBOOK,
        R.string.logbook_title,
        R.drawable.ic_logbook
    ),
    OverflowDestination(
        FocuslistRoutes.REMINDER_HEALTH,
        R.string.reminder_health_title,
        R.drawable.ic_notifications
    )
)

/**
 * One of the destinations navigation switches between directly.
 *
 * Held as data rather than written out twice, because the bar and the rail are
 * two presentations of one navigation model and must offer exactly the same
 * places. `NavigationBarItem` is a `RowScope` extension and `NavigationRailItem`
 * is not, so the two cannot share a single item composable; they can and do
 * share this.
 */
private data class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    /**
     * The filled counterpart, or null where the design system has no pair yet.
     *
     * Null is not a gap to route around. The selection indicator behind the
     * icon already says which item is current, and the filled glyph is a second
     * signal rather than the only one. Drawing a filled variant to fill the
     * hole would be inventing a symbol instead of using a pair Material
     * defines, which is what the three-dot More item used to justify.
     */
    @param:DrawableRes val selectedIconRes: Int? = null
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(
        FocuslistRoutes.TODAY,
        R.string.today_title,
        R.drawable.ic_today,
        R.drawable.ic_today_filled
    ),
    TopLevelDestination(
        FocuslistRoutes.INBOX,
        R.string.inbox_title,
        R.drawable.ic_inbox,
        R.drawable.ic_inbox_filled
    ),
    // The board names this icon `schedule`, which is Material's clock. The
    // clock is already the reminder health icon, and two of them in one piece
    // of chrome would say less than one of each, so the calendar this app
    // already had keeps the slot. Same destination, same position, different
    // glyph, and worth a look during the design pass.
    TopLevelDestination(
        FocuslistRoutes.UPCOMING,
        R.string.upcoming_title,
        R.drawable.ic_upcoming
    )
)

/**
 * The bottom navigation bar, on the three primary destinations.
 *
 * Today, Inbox, and Upcoming, which is what `PRODUCT.md` names and what every
 * screen on the Clean Slate board shows.
 *
 * `PRODUCT.md` describes compact navigation as Today, Inbox, Focus, and More,
 * and Focus is deliberately no longer among them. It became a sheet opened from
 * the task it is for, which cannot be a destination and does not want to be: a
 * bar entry landed the user on whichever task happened to head the queue, with
 * nothing to say why that one. `focus.md` records the reversal in full.
 *
 * More is gone from the bar. It was a fourth item standing in for a screen
 * that does not exist, and the places behind it are not places among the
 * lists: Logbook and Reminder health are rooms you go into and come back from.
 * They moved to the app-bar overflow, where the board puts them.
 */
@Composable
fun FocuslistNavigationBar(
    currentRoute: String?,
    onOpenTopLevel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // One step above the task collection, which is what this role is for.
    //
    // Material's default here is `surfaceContainer`, and that is also where the
    // collection sits, so the two came out identical: a row scrolled to the
    // bottom edge met the bar at the same tone with nothing between them.
    // Measured 94.0 against 94.0 in light and 8.8 against 8.8 in dark.
    //
    // The bar moves rather than the collection, because moving the collection
    // would eat into a page-to-collection separation that is already only 3.9.
    //
    // The cost is the active indicator. It is `secondaryContainer` at tone 90,
    // and every step the bar takes toward it is separation lost: this is the
    // pairing that once came out 0.2 apart under `surfaceContainerHighest` and
    // read as a bar with no pill on it at all. Measured after this change and
    // recorded in `expressive-design-system.md`; if it is ever moved again,
    // measure that pair first.
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        TopLevelDestinations.forEach { destination ->
            TopLevelItem(
                destination = destination,
                currentRoute = currentRoute,
                onOpen = onOpenTopLevel
            )
        }
    }
}

/**
 * The navigation rail, on every screen once the window is wide enough.
 *
 * The same three destinations in the same order as the bar, because this is one
 * navigation model with two presentations. Nothing becomes reachable or
 * unreachable by resizing the window; only where the control sits changes.
 *
 * The container is `surfaceContainerHigh`, matching the bar rather than the
 * rail's Material default of `surface`. The page is `surface` now, and at the
 * breakpoint the content column leaves no gutter, so a `surface` rail would
 * meet the task collection at almost the same lightness. Matching the bar is
 * the whole rule here: one navigation model with two presentations, so a change
 * to one is a change to both.
 *
 * No header. Material offers the slot for a floating action button, but this
 * one belongs to Today and Inbox rather than to the chrome. It stays on the
 * content column, where it lines up with the list it adds to.
 */
@Composable
fun FocuslistNavigationRail(
    currentRoute: String?,
    onOpenTopLevel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        TopLevelDestinations.forEach { destination ->
            val selected = currentRoute == destination.route

            NavigationRailItem(
                selected = selected,
                onClick = { onOpenTopLevel(destination.route) },
                icon = {
                    Icon(
                        painter = painterResource(
                            destination.iconFor(selected)
                        ),
                        // The label names it; the icon would only repeat that.
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.labelRes)) }
            )
        }
    }
}

/**
 * One of the destinations the bar switches between directly.
 *
 * The icon is filled while the destination is current and outlined otherwise,
 * so which item is selected is legible from the icon's own weight and not only
 * from the colour of the indicator behind it.
 */
@Composable
private fun RowScope.TopLevelItem(
    destination: TopLevelDestination,
    currentRoute: String?,
    onOpen: (String) -> Unit
) {
    val selected = currentRoute == destination.route

    NavigationBarItem(
        selected = selected,
        onClick = { onOpen(destination.route) },
        icon = {
            Icon(
                painter = painterResource(destination.iconFor(selected)),
                // The label names it; the icon would only repeat that.
                contentDescription = null
            )
        },
        label = { Text(stringResource(destination.labelRes)) }
    )
}

/** Which glyph the item wears, given whether it is the current place. */
@DrawableRes
private fun TopLevelDestination.iconFor(selected: Boolean): Int =
    if (selected) selectedIconRes ?: iconRes else iconRes

/**
 * The app-bar overflow: three dots, top right, on the three primary screens.
 *
 * Everything the navigation bar does not hold. The board draws it at the end
 * of the header row on Today, Inbox and Upcoming, and nowhere else, because
 * the screens behind it already have a back arrow and offering a way in from
 * inside would be a loop.
 *
 * One icon in both states, as the More item was. Three dots have no filled
 * counterpart, and this one is never a destination anyway.
 */
@Composable
fun FocuslistOverflowMenu(
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.nav_more)
            )
        }

        OverflowItems(
            expanded = expanded,
            onDismiss = { expanded = false },
            onOpen = onOpen
        )
    }
}

/**
 * What the overflow opens.
 *
 * Separate from the button so the list has one definition, whatever ends up
 * anchoring it.
 */
@Composable
private fun OverflowItems(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        OverflowDestinations.forEach { destination ->
            DropdownMenuItem(
                text = { Text(stringResource(destination.labelRes)) },
                onClick = {
                    onDismiss()
                    onOpen(destination.route)
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        // Beside its own label, as in the bar.
                        contentDescription = null
                    )
                }
            )
        }
    }
}
