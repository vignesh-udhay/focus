package app.tellcat.ui.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tellcat.core.design.TellcatMotion

/**
 * The completion check on a task row. `docs/decisions.md` D-105, D-117.
 *
 * Material's checkbox as a rounded square, 20dp across with 6dp corners, where
 * Material's is 18dp with 2dp: square again, as the owner asked after D-105's
 * circle, and rounder than Material's so it is still the task's own check and
 * not a checklist item's. The 2dp outline, colour roles
 * from [CheckboxDefaults.colors], 40dp state layer and 48dp target, and the
 * same semantics, `Role.Checkbox` with a toggleable state, so TalkBack reads it
 * exactly as it read the square one. Only the shape and the tick differ. The
 * tick is drawn smaller than Material's, in proportion to the circle, with
 * round ends.
 *
 * Tasks only. A checklist item is a line inside a task and keeps Material's
 * square `Checkbox`, so the two never look like the same kind of thing.
 */
@Composable
internal fun RoundCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors()
) {
    val boxColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> colors.disabledCheckedBoxColor
            !enabled -> colors.disabledUncheckedBoxColor
            checked -> colors.checkedBoxColor
            else -> colors.uncheckedBoxColor
        },
        animationSpec = TellcatMotion.stateColor(),
        label = "RoundCheckBox"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled && checked -> colors.disabledBorderColor
            !enabled -> colors.disabledUncheckedBorderColor
            checked -> colors.checkedBorderColor
            else -> colors.uncheckedBorderColor
        },
        animationSpec = TellcatMotion.stateColor(),
        label = "RoundCheckBorder"
    )
    val checkColor = if (enabled) colors.checkedCheckmarkColor else colors.disabledCheckmarkColor
    // How much of the tick is drawn, 0 to 1. Effects, like Material's own:
    // the tick changes how the control looks, not its bounds.
    val drawn by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = TellcatMotion.stateColor(),
        label = "RoundCheckMark"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = CheckboxStateLayerSize / 2),
                onValueChange = onCheckedChange
            )
    ) {
        Canvas(Modifier.size(BoxSize)) {
            val stroke = StrokeWidth.toPx()
            val corner = Corner.toPx()

            drawRoundRect(color = boxColor, cornerRadius = CornerRadius(corner))
            // Inset by half the stroke so the outline sits inside the box, as
            // Material's does, with the corner reduced to match.
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(corner - stroke / 2),
                style = Stroke(stroke)
            )

            if (drawn > 0f) {
                val path = Path().apply {
                    moveTo(size.width * TickLeft.first, size.height * TickLeft.second)
                    lineTo(size.width * TickVertex.first, size.height * TickVertex.second)
                    lineTo(size.width * TickRight.first, size.height * TickRight.second)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * drawn, partial, true)
                drawPath(
                    path = partial,
                    color = checkColor,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

/**
 * The tick's three points, as fractions of the box: Material's check mark
 * scaled to 72% about the centre, so it sits inside the circle with room
 * around it rather than reaching for the corners a square has and a circle
 * does not.
 */
private val TickLeft = 0.284f to 0.5f
private val TickVertex = 0.428f to 0.644f
private val TickRight = 0.716f to 0.356f

/**
 * Across the box. Material's is 18dp; D-105's circle was 22dp because a circle
 * that size read small. A square has more area than a circle, so it comes back
 * to 20dp: a little larger than Material's, which keeps it ahead of a checklist
 * item's 16dp box, without the circle's correction. D-117.
 */
private val BoxSize = 20.dp

/**
 * The corner radius. Material's checkbox is 2dp; 6dp is rounder at a glance
 * without reading as a circle. D-117.
 */
private val Corner = 6.dp

/** Material's checkbox outline and check mark width. */
private val StrokeWidth = 2.dp

/** Material's checkbox state layer, `CheckboxTokens.StateLayerSize`. */
internal val CheckboxStateLayerSize = 40.dp
