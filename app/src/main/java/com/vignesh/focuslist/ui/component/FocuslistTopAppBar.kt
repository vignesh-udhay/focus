package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * The app bar every Focuslist screen wears.
 *
 * A title, an optional subtitle, and optional chrome on either side.
 *
 * A screen wears exactly one of the two side slots. A destination in the
 * navigation bar takes [actions] and no back arrow, because the bar is how it
 * is left. A screen reached from the overflow takes [navigationIcon] and no
 * bar, because it is a room rather than a place and a bar with nothing
 * selected in it reads as broken.
 *
 * Three decisions live here rather than in six screens: the two-row flexible
 * bar, the heading semantics that let a screen reader say where the user is,
 * and the fact that the two are always applied together. Screens that wrote
 * their own bar drifted apart on all three.
 *
 * The bar names no colour. Material's default is `surface` at rest, lifting to
 * `surfaceContainer` as content passes underneath, and the page is `surface`
 * too: the bar and the page are one ground, and the task collection is the only
 * thing sitting on it. The previous override existed only because the page had
 * been moved onto a container role, and it went when the page came back.
 *
 * The title style is the component's own, not ours. `LargeFlexibleTopAppBar`
 * draws it at `displaySmall` expanded and shrinks it as the bar collapses;
 * naming a style here would fight that and freeze the collapsed state at the
 * expanded size.
 *
 * @param navigationIcon a back arrow, on a screen the overflow opened. Empty
 * on the three primary destinations, which are left through the bar.
 * @param actions the app-bar overflow, on the three primary screens. Empty by
 * default, because most screens have nothing to put there and an empty action
 * row still reserves its width. This is the only reason the bar has the slot:
 * Logbook, Reminder health and Settings are not places among the lists, so
 * they cannot live in the navigation bar, and the board puts them here.
 * @param subtitle an optional second line under the title. A slot rather than
 * a string because Today spends it on the date *and* a pill carrying the total
 * time planned, which no single string can express. A screen with nothing to
 * say there passes nothing, and the bar is shorter for it.
 * @param scrollBehavior how the bar reacts to the content scrolling under it.
 * Null for a screen with nothing to scroll, or one whose bar must stay put
 * because something beneath it has to remain reachable. Pass
 * `exitUntilCollapsedScrollBehavior` to let the large title collapse; a pinned
 * behaviour holds the bar at full height, which is rarely what a two-row bar
 * wants.
 */
@Composable
internal fun FocuslistTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    LargeFlexibleTopAppBar(
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() }
            )
        },
        modifier = modifier,
        subtitle = subtitle,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior
    )
}
