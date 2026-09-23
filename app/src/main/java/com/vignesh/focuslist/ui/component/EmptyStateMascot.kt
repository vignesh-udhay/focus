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
 * One mascot per screen, and the same cat in all of them. What changes is its
 * posture, and the posture is what says why the screen is empty: sitting with
 * its eyes half shut for a day that is clear and still open, curled asleep for
 * an inbox with nothing left to decide, lying down but awake for days that are
 * still clear. D-074 drew this set, and it is where Today and Inbox traded
 * postures. Nothing else is in the drawing. An earlier set gave the cat a prop on each screen, a bowl, a
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
 * each one draws changes from pose to pose. [light] is the coat and the ground
 * shadow beneath it; [mid] is the tail and the shading between the paws;
 * [dark] is the eyes, the nose and the error pose's question mark.
 *
 * They bind by the part's name rather than by its colour, because the greys
 * drift between poses. The source files draw the shadow about halfway between
 * the coat and the tail, and it takes [light] anyway: the mascot is three
 * roles, and D-074 says what to do if the shadow ever reads as missing.
 *
 * ## Sizing
 *
 * [width] and [height] are the frame the pose was drawn in, scaled by one
 * factor shared across all seven poses, 0.26dp per viewport unit. Neither
 * dimension is normalised: a cat sitting is genuinely taller than the same cat
 * lying down, so matching heights would shrink the sitting one and matching
 * widths would swell it. The set was drawn at a consistent scale, so carrying
 * that scale through is what keeps them one animal.
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
    Image(
        imageVector = rememberMascotImage(build),
        contentDescription = null,
        modifier = modifier
            .widthIn(max = width.dp)
            .fillMaxWidth()
            .aspectRatio(width / height)
    )
}

/**
 * A pose, resolved against the three colour roles every mascot shares.
 *
 * Lifted out of [MascotImage] for [FocusMascot], which lays its two poses out
 * itself and so cannot use the sizing that composable applies. Which three
 * roles a mascot takes is the policy this file exists to hold, and a second
 * copy of it elsewhere would be a second place for it to drift.
 *
 * Keyed on the colours alone. The builder is a reference to a top-level
 * function and the sizes around it are constants, so nothing else here can
 * change without the call site itself changing.
 */
@Composable
internal fun rememberMascotImage(
    build: (light: Color, mid: Color, dark: Color) -> ImageVector
): ImageVector {
    val light = MaterialTheme.colorScheme.primaryFixed
    val mid = MaterialTheme.colorScheme.primaryFixedDim
    val dark = MaterialTheme.colorScheme.onPrimaryFixedVariant

    return remember(light, mid, dark) { build(light, mid, dark) }
}
