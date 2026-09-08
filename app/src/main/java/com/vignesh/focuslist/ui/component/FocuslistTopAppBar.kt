package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * The app bar every Focuslist screen wears.
 *
 * A compact 64dp `TopAppBar` carrying a title and chrome on one side. It is
 * pinned: it takes no scroll behaviour and holds its height in every scroll
 * position.
 *
 * A screen wears exactly one of the two side slots. A destination in the
 * navigation bar takes [actions] and no back arrow, because the bar is how it
 * is left. A screen reached from the overflow takes [navigationIcon] and no
 * bar, because it is a room rather than a place and a bar with nothing
 * selected in it reads as broken.
 *
 * Three decisions live here rather than in six screens: the compact pinned bar,
 * the heading semantics that let a screen reader say where the user is, and the
 * fact that the two are always applied together. Screens that wrote their own
 * bar drifted apart on all three.
 *
 * **This was `LargeFlexibleTopAppBar` at 152dp with a subtitle slot, and
 * `docs/decisions.md` D-020 took both away.** The tall bar was never justified
 * by the title; it was justified by a payload the header carried, and the
 * payload went in two steps. D-017 removed Today's planned-minutes pill, and
 * D-020 removed the date and Inbox's count. What was left was 152dp holding a
 * single word the user had just tapped to get there, which is precisely the
 * situation the compact bar's original rule described and refused.
 *
 * `today-screen.md` carries the reasoning and the rule that governs any third
 * change: **a subtitle and the height are one decision.** They come back
 * together or not at all, and anyone proposing a tall bar again has to put
 * something in it first.
 *
 * The bar names no colour. Material's default is `surface`, and the page is
 * `surface` too: the bar and the page are one ground, and the task collection
 * is the only thing sitting on it. The previous override existed only because
 * the page had been moved onto a container role, and it went when the page came
 * back.
 *
 * The title style is the component's own, not ours. `TopAppBar` draws it at
 * `titleLarge`. Naming a style here would be a screen deciding its own type,
 * which is how a design system stops being one.
 *
 * @param title the screen's name, or null on a screen whose own content is the
 * heading. Task Details passes null: the task's title sits directly beneath the
 * bar, and a generic name above it would be two stacked headings with the upper
 * one naming the app. Those screens publish the name as `paneTitle` instead, so
 * a screen reader still says where the user is. Null rather than a screen
 * building its own bar, because the alternative is what this component exists to
 * prevent.
 * @param navigationIcon a back arrow, on a screen the overflow opened. Empty
 * on the three primary destinations, which are left through the bar.
 * @param actions the app-bar overflow, on the three primary screens. Empty by
 * default, because most screens have nothing to put there and an empty action
 * row still reserves its width. This is the only reason the bar has the slot:
 * Logbook, Reminder health and Settings are not places among the lists, so
 * they cannot live in the navigation bar, and the board puts them here.
 */
@Composable
internal fun FocuslistTopAppBar(
    title: String?,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() }
                )
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions
    )
}
