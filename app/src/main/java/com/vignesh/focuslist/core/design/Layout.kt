package com.vignesh.focuslist.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much empty space a screen leaves at each side to keep its content from
 * stretching across a wide window.
 *
 * Zero on a phone, where the window is narrower than
 * [FocuslistDimensions.ContentMaxWidth] and the content already fills it. On
 * anything wider it is whatever it takes to centre a column of that width,
 * which is what `PRODUCT.md` asks for when it says not to stretch the phone
 * layout across a tablet.
 *
 * Expressed as a gutter rather than as a width so that one number serves every
 * caller. A list adds it to its content padding, an empty state to its own
 * padding, and the floating action button to its end padding, and all three
 * line up on the same column without any of them having to measure the others.
 * A list keeps taking touches across the full window; only what it draws is
 * constrained.
 *
 * Measured against the area the content actually occupies, which is not always
 * the window: once a navigation rail takes the leading edge, the rail's width
 * is no longer content space, and centring against the window would push the
 * column off centre by half the rail. See [LocalContentWidth].
 */
@Composable
@ReadOnlyComposable
fun focuslistContentGutter(): Dp {
    val overflow = focuslistContentWidth() - FocuslistDimensions.ContentMaxWidth

    return if (overflow > 0.dp) overflow / 2 else 0.dp
}

/**
 * How wide the area holding the content is.
 *
 * [LocalContentWidth] when something upstream has measured it, and the window
 * otherwise. Reading the window rather than the display means a freeform or
 * split-screen window is measured as the app actually sees it.
 */
@Composable
@ReadOnlyComposable
fun focuslistContentWidth(): Dp {
    LocalContentWidth.current?.let { return it }

    val widthPx = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { widthPx.toDp() }
}

/**
 * The width of the area the content has been given, when a caller knows it.
 *
 * Null means nobody has narrowed it and the window is the content area, which
 * is the case on every compact layout. The navigation host provides it once a
 * rail is beside the content, so that everything measuring against the content
 * column agrees on where that column is.
 */
val LocalContentWidth = compositionLocalOf<Dp?> { null }

/**
 * Whether this window is wide enough to navigate from a rail rather than a bar.
 *
 * A presentation decision and nothing more: the destinations, the graph and the
 * back stack are identical on both sides of the boundary.
 *
 * Deliberately measured from the window rather than [focuslistContentWidth],
 * because the rail is what makes the content area narrower. Asking the content
 * area whether to show a rail would be circular.
 */
@Composable
@ReadOnlyComposable
fun focuslistUsesNavigationRail(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val width = with(LocalDensity.current) { widthPx.toDp() }

    return width >= FocuslistDimensions.NavigationRailBreakpoint
}
