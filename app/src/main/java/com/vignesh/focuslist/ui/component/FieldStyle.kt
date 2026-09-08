package com.vignesh.focuslist.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * The look every text field that draws a container shares.
 *
 * `docs/decisions.md` D-025 settled that such a field is the filled one, and
 * that argument holds: every other surface in this app is a tinted container on
 * a plain page, and an outlined field was the one component asking to be read by
 * its border instead.
 *
 * **What D-025 did not argue for came along with the component.** Material's
 * filled field ships a one-pixel rule beneath it and a container rounded on its
 * top two corners only. Nothing else in Focuslist has a rule line, and nothing
 * else is rounded on two corners, so the field was the one piece still speaking
 * Material 2. Both are removed here rather than at each call site, so the four
 * fields that draw a container cannot drift apart.
 *
 * Task Details' title and notes do not use this. They paint the container out
 * entirely, which D-018 asks for: the title is the screen's heading and its
 * primary input at once, and a container round it makes the top of the screen
 * read as a summary card.
 */
@Composable
internal fun focuslistFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
    // The error indicator is deliberately left alone. The rule is removed as
    // decoration, and in the error state it stops being decoration: it is the
    // cue Material pairs with the error colour and the supporting text, and
    // `expressive-components.md` keeps the Material error treatment. A line
    // that appears only when something is wrong is worth more than one that is
    // always there.
)

/**
 * The corner every field that draws a container takes.
 *
 * Read from the theme rather than written as 16dp, so the field cannot drift
 * from the rows. `ListItemDefaults.segmentedShapes` resolves `CornerLarge`,
 * which is this same value, and D-008 leaves the corner scale to
 * `MaterialExpressiveTheme` rather than restating it per component.
 *
 * Large rather than full, and D-025 names the reason as its own reversal
 * condition: a filled container reading as a chip or a button where it sits
 * beside real ones, "the place to watch is Quick Add, where the field sits above
 * a filled Add task button". A pill-shaped field walks into exactly that.
 */
internal val FocuslistFieldShape: Shape
    @Composable get() = MaterialTheme.shapes.large
