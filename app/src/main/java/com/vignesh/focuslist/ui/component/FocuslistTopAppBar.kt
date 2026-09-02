package com.vignesh.focuslist.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * The app bar every Focuslist screen wears.
 *
 * A title and nothing else. There is no navigation icon and no action, because
 * every destination is reachable from the navigation bar, so an app bar action
 * would only duplicate it.
 *
 * Three decisions live here rather than in six screens: the emphasized title
 * role, the heading semantics that let a screen reader say where the user is,
 * and the fact that the two are always applied together. Screens that wrote
 * their own bar drifted apart on all three.
 *
 * @param scrollBehavior how the bar reacts to the content scrolling under it.
 * Null for a screen with nothing to scroll, or one whose bar must stay put
 * because something beneath it has to remain reachable.
 */
@Composable
internal fun FocuslistTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        // The bar is the page, not a layer above it. Material's default is
        // `surface`, which assumes the page is `surface` too; once the page
        // moved to `surfaceContainer` the default left the bar sitting two
        // tones from the collection and reading as part of it. Sharing the
        // page's role puts the bar back on the ground and lets the collection
        // be the only thing floating.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            // One step off the page when content passes underneath, so the
            // lift is still visible now that the resting colour has moved.
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { heading() }
            )
        },
        modifier = modifier,
        scrollBehavior = scrollBehavior
    )
}
