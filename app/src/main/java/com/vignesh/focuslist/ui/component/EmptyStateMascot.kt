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
 * One mascot per screen, and each says why its own screen is empty rather than
 * decorating the absence. Today's dachshund sleeps because nothing is
 * scheduled, Upcoming's watches a ball because something is coming, Inbox's
 * leans out from behind a blank card. It is the same dog in all three, which is
 * the point: a different animal per screen would read as three products.
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
 * together when dynamic colour is on.
 *
 * The three tones do the same job in every pose. `pale` is the ground shadow
 * and whatever the dog is with, the card and the ball; `body` is the animal
 * itself; `detail` is the ear, tail, paws, nose and eye. Bound this way the
 * three mascots recolour together and cannot drift apart.
 *
 * ## Sizing
 *
 * [width] and [height] are the frame the board draws the pose in, and they
 * differ per pose: the sleeping dog is wide and flat, the sitting one is tall.
 * A mascot takes that size until the window is narrower than it, then gives way
 * rather than clipping. It is the part of an empty state that can afford to.
 */
@Composable
internal fun MascotImage(
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    build: (pale: Color, body: Color, detail: Color) -> ImageVector
) {
    val pale = MaterialTheme.colorScheme.primaryFixed
    val body = MaterialTheme.colorScheme.primaryFixedDim
    val detail = MaterialTheme.colorScheme.onPrimaryFixedVariant

    Image(
        // Keyed on the colours alone. The builder is a reference to a top-level
        // function and the two sizes are constants, so nothing else here can
        // change without the call site itself changing.
        imageVector = remember(pale, body, detail) { build(pale, body, detail) },
        contentDescription = null,
        modifier = modifier
            .widthIn(max = width.dp)
            .fillMaxWidth()
            .aspectRatio(width / height)
    )
}
