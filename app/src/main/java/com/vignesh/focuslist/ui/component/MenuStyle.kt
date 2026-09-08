package com.vignesh.focuslist.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * The corner both of the app's menus take.
 *
 * **Material's default is not this.** `MenuDefaults.shape` resolves
 * `CornerExtraSmall`, 4dp, which is the corner a menu had before the rest of
 * this design system moved. Everything the app draws on a surface is on the
 * large corner: task rows, Plan rows, the health check rows, the Focus now
 * card, the text fields. A 4dp menu was the last thing still square by
 * comparison, and it read as a component from a different app.
 *
 * Read from the theme rather than written as a number, for the reason
 * `FocuslistFieldShape` gives: `ListItemDefaults.segmentedShapes` resolves
 * `CornerLarge`, this is the same value, and D-008 leaves the corner scale to
 * `MaterialExpressiveTheme` rather than restating it per component.
 *
 * Shared because there are two menus and they are the same thing seen on
 * different screens. `expressive-components.md` keeps the rest of their
 * anatomy: one container colour, labels with no icons, and colour only where a
 * label is not enough.
 */
internal val FocuslistMenuShape: Shape
    @Composable get() = MaterialTheme.shapes.large
