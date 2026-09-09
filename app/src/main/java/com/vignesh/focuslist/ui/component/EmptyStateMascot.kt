package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The part every mascot shares: its colours, and how it takes space.
 *
 * One mascot per screen, and the same cat in all three. What changes is its
 * posture, and the posture is what says why the screen is empty: curled asleep
 * for a day with nothing on it, sitting upright for an inbox waiting to be
 * filled, lying down but awake for days that are still clear. Nothing else is
 * in the drawing. An earlier set gave the cat a prop on each screen, a bowl, a
 * calendar, a checked card, and the object did the explaining; these say the
 * same three things with the animal alone.
 *
 * Purely decorative to a screen reader. The empty state's headline is the line
 * that says what the screen holds, and it carries the heading semantics, so
 * announcing the drawing as well would only put a second description of the
 * same fact in TalkBack's path.
 *
 * ## Why an ImageVector and not a drawable
 *
 * The artwork is three flat fills, and all three are colour roles rather than
 * ink: `primaryFixed`, `primaryFixedDim` and `onPrimaryFixedVariant`, which
 * `Color.kt` carries for this. A vector drawable cannot read the Compose colour
 * scheme, and a single `colorFilter` tint cannot supply three colours, so the
 * paths are built by [build] and handed the resolved roles instead.
 *
 * Fixed roles hold one value in light and dark by definition, so a mascot does
 * not restate itself per theme, and a device palette replaces all three
 * together when dynamic colour is on. That is deliberate: in the app the
 * illustration should belong to the user's phone. The launcher icon is the
 * opposite case and takes fixed hex, because a launcher draws outside the
 * app's theme and an icon has to be recognisable among strangers.
 *
 * The three tones are named for their order, not their subject, because what
 * each one draws changes from pose to pose. [light] is the coat and the soft
 * contact shade beneath it, which the board draws at almost the coat's own
 * value; [mid] is the shading that gives the coat its folds and its tail;
 * [dark] is the eyes and the nose.
 *
 * They bind by the part's name rather than by its colour, because the greys
 * drift between poses. One difference is deliberate and lives in the poses
 * rather than here: the sitting cat's nose is drawn lighter than its eyes and
 * takes [mid], where the other two draw it dark.
 *
 * ## Sizing
 *
 * [width] and [height] are the frame the board draws the pose in, scaled by one
 * factor shared across all three. Neither dimension is normalised: a cat
 * sitting is genuinely taller than the same cat lying down, so matching heights
 * would shrink the sitting one and matching widths would swell it. The board
 * already drew the three at a consistent scale, within about 9% by area, so
 * carrying that scale through is what keeps them one animal.
 *
 * A mascot takes that size until the window is narrower than it, then gives way
 * rather than clipping. It is the part of an empty state that can afford to.
 */
@Composable
internal fun MascotImage(
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    build: (light: Color, mid: Color, dark: Color) -> ImageVector
) {
    val light = MaterialTheme.colorScheme.primaryFixed
    val mid = MaterialTheme.colorScheme.primaryFixedDim
    val dark = MaterialTheme.colorScheme.onPrimaryFixedVariant

    Image(
        // Keyed on the colours alone. The builder is a reference to a top-level
        // function and the two sizes are constants, so nothing else here can
        // change without the call site itself changing.
        imageVector = remember(light, mid, dark) { build(light, mid, dark) },
        contentDescription = null,
        modifier = modifier
            .widthIn(max = width.dp)
            .fillMaxWidth()
            .aspectRatio(width / height)
    )
}
