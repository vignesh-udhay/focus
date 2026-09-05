package com.vignesh.focuslist.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
 * A destination behind More.
 *
 * `PRODUCT.md` also places Areas, Projects, and Settings here. They join this
 * list when they exist; nothing else about More has to change.
 */
private data class SecondaryDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int
)

private val SecondaryDestinations = listOf(
    SecondaryDestination(
        FocuslistRoutes.UPCOMING,
        R.string.upcoming_title,
        R.drawable.ic_upcoming
    ),
    SecondaryDestination(
        FocuslistRoutes.LOGBOOK,
        R.string.logbook_title,
        R.drawable.ic_logbook
    ),
    // Behind More for now. The Clean Slate board puts it in an app-bar
    // overflow beside Logbook and Settings, which arrives with Phase 3's
    // navigation change rather than here.
    SecondaryDestination(
        FocuslistRoutes.REMINDER_HEALTH,
        R.string.reminder_health_title,
        R.drawable.ic_notifications
    )
)

/** The routes More can reach, for deciding whether More is the current place. */
private val SecondaryRoutes = SecondaryDestinations.map { it.route }.toSet()

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
    @param:DrawableRes val selectedIconRes: Int
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
    )
)

/**
 * The bottom navigation bar, on every screen.
 *
 * Today, Inbox, and More.
 *
 * `PRODUCT.md` describes compact navigation as Today, Inbox, Focus, and More,
 * and Focus is deliberately no longer among them. It became a sheet opened from
 * the task it is for, which cannot be a destination and does not want to be: a
 * bar entry landed the user on whichever task happened to head the queue, with
 * nothing to say why that one. `focus.md` records the reversal in full.
 *
 * More is not a destination of its own. `PRODUCT.md` names it in the bar but
 * does not define a screen for it, so it opens the secondary destinations as a
 * menu and is shown as current while the user is on one of them.
 */
@Composable
fun FocuslistNavigationBar(
    currentRoute: String?,
    onOpenTopLevel: (String) -> Unit,
    onOpenSecondary: (String) -> Unit,
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

        MoreItem(currentRoute = currentRoute, onOpenSecondary = onOpenSecondary)
    }
}

/**
 * The navigation rail, on every screen once the window is wide enough.
 *
 * The same four destinations in the same order as the bar, because this is one
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
    onOpenSecondary: (String) -> Unit,
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
                            if (selected) destination.selectedIconRes else destination.iconRes
                        ),
                        // The label names it; the icon would only repeat that.
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.labelRes)) }
            )
        }

        MoreRailItem(currentRoute = currentRoute, onOpenSecondary = onOpenSecondary)
    }
}

/** More, in the rail. The same menu, opened from a rail item instead. */
@Composable
private fun MoreRailItem(
    currentRoute: String?,
    onOpenSecondary: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    NavigationRailItem(
        selected = currentRoute in SecondaryRoutes,
        onClick = { expanded = true },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = null
            )

            MoreMenu(
                expanded = expanded,
                onDismiss = { expanded = false },
                onOpenSecondary = onOpenSecondary
            )
        },
        label = { Text(stringResource(R.string.nav_more)) }
    )
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
                painter = painterResource(
                    if (selected) destination.selectedIconRes else destination.iconRes
                ),
                // The label names it; the icon would only repeat that.
                contentDescription = null
            )
        },
        label = { Text(stringResource(destination.labelRes)) }
    )
}

/**
 * More: a menu rather than a screen.
 *
 * The menu is anchored inside the item so it opens over the bar without
 * disturbing how the bar lays its items out.
 *
 * Alone among the four it keeps one icon in both states. Three dots have no
 * filled counterpart to switch to, and drawing one would be inventing a symbol
 * rather than using a pair Material already defines.
 */
@Composable
private fun RowScope.MoreItem(
    currentRoute: String?,
    onOpenSecondary: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    NavigationBarItem(
        selected = currentRoute in SecondaryRoutes,
        onClick = { expanded = true },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = null
            )

            MoreMenu(
                expanded = expanded,
                onDismiss = { expanded = false },
                onOpenSecondary = onOpenSecondary
            )
        },
        label = { Text(stringResource(R.string.nav_more)) }
    )
}

/**
 * What More opens, wherever it is opened from.
 *
 * Shared so the bar and the rail cannot drift into offering different places.
 */
@Composable
private fun MoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenSecondary: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SecondaryDestinations.forEach { destination ->
            DropdownMenuItem(
                text = { Text(stringResource(destination.labelRes)) },
                onClick = {
                    onDismiss()
                    onOpenSecondary(destination.route)
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
