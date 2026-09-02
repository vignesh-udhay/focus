package com.vignesh.focuslist.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The Focuslist corner scale.
 *
 * Softer than Material's at the small end, and the same at the top. The
 * language is restrained on purpose: rounded rectangles and nothing else. No
 * `MaterialShapes` polygons, and no shape morphing as an interaction.
 *
 * Shape does not carry hierarchy here. It says what a thing is, not how
 * important it is, so two components at different levels of prominence do not
 * get different radii to make the point.
 *
 * All eight slots are declared, including the three Material 3 Expressive
 * added. Leaving any of them out means it silently keeps the Material default,
 * which is how the scale previously ended up non-monotonic: `large` was
 * overridden to 24dp while `largeIncreased` stayed at Material's 20dp, so the
 * larger token produced the smaller corner.
 */
val FocuslistShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp)
)
