package com.vignesh.focuslist.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.focuslistMotionEnabled

/**
 * The Focus mascot: the same cat, lying awake when the clock is stopped and
 * curled asleep while it runs.
 *
 * D-046 put it where the shape used to be. What it says is exactly what the
 * shape said under D-014, whether the clock is running, and it says it in the
 * app's own animal rather than in a polygon. Two poses cover all five states:
 * Paused and Open-ended paused lie awake; Running, Estimate reached and
 * Open-ended sleep.
 *
 * **The cat does not wake up when the estimate runs out.** Estimate reached
 * sleeps like any other running state, and only the status line changes. D-046
 * has the argument: `focus.md` already says overrunning is not failure, and an
 * animal that sat up at the buzzer would say it was.
 *
 * Drawn from `cat-hd-paused-focus` and `cat-hd-asleep-idle`, which replaced
 * D-046's sit and nap under D-074. Both take the same scale factor as the other
 * five poses. [MascotImage] explains why the paths are built in Kotlin and which
 * colour role each tone takes.
 *
 * **Both poses are lying down, so the eyes carry the state.** The old pair
 * differed in height by a third and these differ by under 3dp. That is what
 * makes the crossfade calm, and it is also why [FocusPose] is careful about the
 * floor: with so little else moving, a ground line that shifted a dp on every
 * pause would be the most visible thing in the transition.
 *
 * Decorative to a screen reader, like every other pose. The task title is the
 * heading, the button reads Pause or Resume, and the status line names the
 * state, so nothing here is the only channel for anything.
 */
@Composable
internal fun FocusMascot(running: Boolean, modifier: Modifier = Modifier) {
    val awake = rememberMascotImage(::catLyingAwake)
    val asleep = rememberMascotImage(::catCurledAsleep)
    val settle = rememberSettle(running)

    // Bottom-aligned, then each pose is moved by [FocusPose.offsetX] and
    // [FocusPose.offsetY] so the centre of its ground shadow lands on one point
    // of the box. [FocusPose] says why that point and not the frame's edge.
    Box(
        modifier = modifier.size(width = MascotBoxWidth.dp, height = MascotBoxHeight.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        MascotPose(
            image = awake,
            pose = Awake,
            alpha = 1f - settle.value,
            scale = 1f - Settle * settle.value
        )
        MascotPose(
            image = asleep,
            pose = Asleep,
            alpha = settle.value,
            scale = 1f + Settle * (1f - settle.value)
        )
    }
}

/**
 * One pose in the crossfade.
 *
 * Drawn at its own size rather than filling the box. The two poses are 198.38
 * and 209.56 wide, and letting each fill the same width would shrink the wider
 * one by about five percent, which is the one thing that stops them reading as
 * the same animal.
 *
 * **Scaled about the ground under it.** [FocusPose.ground] is the centre of the
 * pose's shadow, which is the same point of the box for both poses, so the
 * floor does not move while the body compresses and the settle reads as the cat
 * lowering itself rather than as the drawing sliding.
 *
 * The offsets are static and per pose. Nothing here animates sideways or
 * vertically.
 */
@Composable
private fun MascotPose(
    image: ImageVector,
    pose: FocusPose,
    alpha: Float,
    scale: Float
) {
    Image(
        imageVector = image,
        contentDescription = null,
        modifier = Modifier
            .size(width = pose.width.dp, height = pose.height.dp)
            .offset(x = pose.offsetX.dp, y = pose.offsetY.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = pose.ground
            }
    )
}

/**
 * How far the cat has settled. Zero is awake, one is asleep.
 *
 * **One value drives both halves, and that is deliberate.** The alpha is an
 * effect and the scale is spatial, and `Motion.kt` is right that the two are
 * different kinds of animation. They are not two animations here. They are one
 * gesture, and putting them on separate specs is exactly what `focus.md`
 * records about the container transform it deleted: "two things animating on
 * separate specs arrive at separate times, and the fix is to make one a
 * function of the other rather than to tune both until they agree."
 *
 * On `mascotSettle`, the default spatial spec. Not `focusSession`, which is the
 * slow spec and belongs to the sheet arriving and leaving; this is a state
 * change inside a session that is already open.
 *
 * Under reduced motion it snaps, and nothing is withheld by that. The pose is
 * information and it still changes; the travel and the scale are decoration and
 * they do not happen.
 */
@Composable
private fun rememberSettle(running: Boolean): Animatable<Float, AnimationVector1D> {
    val animate = focuslistMotionEnabled()
    val target = if (running) 1f else 0f
    val settle = remember { Animatable(target) }
    val spec = FocuslistMotion.mascotSettle<Float>()

    LaunchedEffect(target, animate) {
        if (animate) settle.animateTo(target, spec) else settle.snapTo(target)
    }

    return settle
}

/**
 * How far either pose departs from its resting size while it crosses.
 *
 * Two percent. The leaving pose compresses to 0.98 and the arriving one starts
 * at 1.02 and settles, which is enough for the eye to read a transformation
 * rather than a dissolve and small enough that it stays calm. Larger values
 * start to look like a UI transition rather than an animal lying down.
 */
private const val Settle = 0.02f

/**
 * The scale factor all seven poses share, in dp per viewport unit. D-074.
 *
 * The five list poses carry its products as literals. It is a number here
 * because this file has to do arithmetic with it.
 */
private const val MascotScale = 0.26f

/**
 * A Focus pose's frame and the ground shadow inside it, which is everything the
 * crossfade needs to know about where the pose stands.
 *
 * **The anchor is the centre of the shadow, in both axes.** The shadow is the
 * ground, it is the widest part of either pose, and it is the one element both
 * poses draw the same way. The frame is none of those things. A drawing centred
 * by its bounding box is not centred to the eye, and a drawing sat on its
 * frame's bottom edge is not sat on the floor: these two shadows lie 49.29 and
 * 53.86 units above their frames' edges, so bottom alignment alone would lift
 * the floor 1.2dp on every pause and drop it on every resume, under a crossfade
 * written so the floor does not move.
 *
 * **The shadow is drawn from these numbers too.** [shadow] builds the ellipse
 * from the same four values the offsets are derived from, so the anchor cannot
 * drift away from the drawing. The old poses carried hand-measured offsets
 * beside a shadow path exported separately, and a redraw moved one without the
 * other.
 *
 * Viewport units throughout, as the artwork gives them. Everything in dp is a
 * product with [MascotScale].
 */
private class FocusPose(
    val viewportWidth: Float,
    val viewportHeight: Float,
    private val shadowCx: Float,
    private val shadowCy: Float,
    private val shadowRx: Float,
    private val shadowRy: Float
) {
    val width = viewportWidth * MascotScale
    val height = viewportHeight * MascotScale

    /** How far the ground lies above the frame's bottom edge. */
    val groundInset = (viewportHeight - shadowCy) * MascotScale

    /** How far the drawing rises above the ground. */
    val rise = shadowCy * MascotScale

    /** How far the frame reaches sideways from the ground's centre, on its longer side. */
    val reach = maxOf(shadowCx, viewportWidth - shadowCx) * MascotScale

    /** Moves the shadow's centre onto the box's centre line. */
    val offsetX = (viewportWidth / 2f - shadowCx) * MascotScale

    /** Lifts the pose until its ground meets [GroundInset]. Zero or negative, since the box aligns bottoms. */
    val offsetY get() = groundInset - GroundInset

    val ground = TransformOrigin(
        pivotFractionX = shadowCx / viewportWidth,
        pivotFractionY = shadowCy / viewportHeight
    )

    val shadow: List<PathNode>
        get() = PathBuilder().apply {
            moveTo(shadowCx - shadowRx, shadowCy)
            arcToRelative(shadowRx, shadowRy, 0f, true, false, 2f * shadowRx, 0f)
            arcToRelative(shadowRx, shadowRy, 0f, true, false, -2f * shadowRx, 0f)
            close()
        }.nodes
}

private val Awake = FocusPose(
    viewportWidth = 763f,
    viewportHeight = 549f,
    shadowCx = 377.15f,
    shadowCy = 499.71f,
    shadowRx = 370.3f,
    shadowRy = 34.36f
)

private val Asleep = FocusPose(
    viewportWidth = 806f,
    viewportHeight = 539f,
    shadowCx = 403.54f,
    shadowCy = 485.14f,
    shadowRx = 387f,
    shadowRy = 39.01f
)

/**
 * Where the shared ground sits above the box's bottom edge: the deeper of the
 * two insets, so neither pose has to hang below the box to reach it.
 */
private val GroundInset = maxOf(Awake.groundInset, Asleep.groundInset)

/**
 * Wide enough to hold both poses once each stands on the shared ground, and as
 * tall as the taller one above it.
 *
 * Not the union of the two frames. Each is measured out from the ground's
 * centre, because that is where they are placed from, and a box measured on the
 * frames would let whichever pose was moved furthest hang outside it.
 */
private val MascotBoxWidth = 2f * maxOf(Awake.reach, Asleep.reach)
private val MascotBoxHeight = maxOf(Awake.rise, Asleep.rise) + GroundInset

private fun catLyingAwake(light: Color, mid: Color, dark: Color): ImageVector =
    ImageVector.Builder(
        name = "FocusCatLyingAwake",
        defaultWidth = Awake.width.dp,
        defaultHeight = Awake.height.dp,
        viewportWidth = Awake.viewportWidth,
        viewportHeight = Awake.viewportHeight
    )
        .addPath(
            pathData = Awake.shadow,
            fill = SolidColor(light)
        )
        .addPath(
            pathData = addPathNodes(AwakeBody),
            fill = SolidColor(light)
        )
        .addPath(
            pathData = addPathNodes(AwakeTail),
            fill = SolidColor(mid)
        )
        .addPath(
            pathData = addPathNodes(AwakeNose),
            fill = SolidColor(dark)
        )
        .addPath(
            pathData = addPathNodes(AwakeEyeLeft),
            fill = SolidColor(dark)
        )
        .addPath(
            pathData = addPathNodes(AwakeEyeRight),
            fill = SolidColor(dark)
        )
        .build()

private fun catCurledAsleep(light: Color, mid: Color, dark: Color): ImageVector =
    ImageVector.Builder(
        name = "FocusCatCurledAsleep",
        defaultWidth = Asleep.width.dp,
        defaultHeight = Asleep.height.dp,
        viewportWidth = Asleep.viewportWidth,
        viewportHeight = Asleep.viewportHeight
    )
        .addPath(
            pathData = Asleep.shadow,
            fill = SolidColor(light)
        )
        .addPath(
            pathData = addPathNodes(AsleepBody),
            fill = SolidColor(light)
        )
        .addPath(
            pathData = addPathNodes(AsleepTail),
            fill = SolidColor(mid)
        )
        .addPath(
            pathData = addPathNodes(AsleepNose),
            fill = SolidColor(dark)
        )
        .addPath(
            pathData = addPathNodes(AsleepEyeLeft),
            fill = SolidColor(dark)
        )
        .addPath(
            pathData = addPathNodes(AsleepEyeRight),
            fill = SolidColor(dark)
        )
        .build()

private const val AwakeBody = "M 277,16.55 C 268.15,18.62 263.95,20.77 255.12,27.62 C 245.6,35.05 236.68,43.98 224.88,57.85 C 222.1,61.12 220.55,63.07 217.4,67.2 C 215.6,69.57 213.58,72.2 212.93,73.07 C 210.33,76.43 202.43,87.95 198.73,93.8 C 193.95,101.32 194.48,101.07 189.9,98.05 C 188.53,97.12 186.2,95.52 184.75,94.5 C 183.3,93.48 180.5,91.62 178.5,90.42 C 176.5,89.2 173.78,87.52 172.45,86.67 C 166.95,83.17 165,82 162.25,80.5 C 160.68,79.62 157.57,77.85 155.38,76.55 C 144.05,69.8 125.75,61.55 114.5,58.15 C 111.03,57.1 106.53,56 105.73,56 C 105.4,56 104.33,55.82 103.38,55.62 C 101.03,55.12 95.65,55.12 93.38,55.62 C 84.05,57.68 77.12,63.15 67.85,75.75 C 61.48,84.43 52.9,100.7 47.5,114.38 C 46.8,116.15 45.95,118.25 45.62,119 C 42.35,126.6 35.55,147.05 32.25,159.25 C 32,160.15 31.57,161.77 31.28,162.88 C 30.98,163.98 30.58,165.6 30.38,166.5 C 30.2,167.4 29.65,169.75 29.15,171.75 C 27.23,179.52 25.85,186.12 24.9,192.25 C 24.68,193.55 24.23,196.38 23.9,198.5 C 21.2,215.27 19.5,235.88 19.5,251.85 C 19.5,263.82 20.43,279.95 21.62,288.5 C 21.82,289.95 22.1,291.9 22.23,292.88 C 23.2,300.12 24,304.45 25.38,310.25 C 25.6,311.15 25.98,312.82 26.23,314 C 26.8,316.62 27.78,320.23 28.9,323.88 C 29.35,325.38 29.88,327.07 30.03,327.62 C 31.95,334.2 36.03,345.95 38.05,350.75 C 43.1,362.65 50.48,376 56.15,383.55 C 59.4,387.85 60,388.77 60,389.62 C 60,390.07 59.35,392.18 58.55,394.27 C 55.35,402.65 53.1,410.7 51.9,418.12 C 51.1,423 50.88,426.05 50.88,433.25 C 50.88,440.7 51.1,443.48 52.15,449.88 C 53.55,458.18 56.95,466.18 62.62,474.5 C 65.53,478.73 65.78,479.2 65.67,480.05 C 65.52,481.12 65.83,481.5 66.85,481.5 C 67.45,481.5 68.15,482.05 70.85,484.7 C 72.65,486.48 75.08,488.68 76.25,489.6 C 77.42,490.52 78.8,491.62 79.33,492.05 C 82.17,494.32 94.35,500.82 99.62,502.9 C 104.53,504.8 115.83,507.82 120.5,508.48 C 121.53,508.62 123.78,508.98 125.5,509.25 C 127.22,509.55 130.38,509.93 132.5,510.12 C 134.62,510.32 137.22,510.62 138.25,510.75 C 142.78,511.38 167.93,512.88 180.25,513.25 C 184.18,513.38 188.95,513.6 190.88,513.75 C 192.8,513.9 199.72,514.15 206.25,514.27 C 212.78,514.4 219.2,514.55 220.5,514.58 C 238.32,515.17 290.8,515.42 306.88,515 C 342.7,514.08 347.25,513.9 377.25,512.25 C 379.8,512.1 383.57,511.82 385.62,511.62 C 389.93,511.2 397.9,511.12 399.38,511.52 C 399.93,511.68 402.23,511.9 404.5,512.02 C 406.77,512.15 409.75,512.35 411.12,512.48 C 412.5,512.6 416.55,512.83 420.12,512.98 C 423.7,513.12 427.57,513.38 428.75,513.5 C 433.77,514.05 455.23,514.33 479.62,514.12 C 493.1,514 506.05,513.9 508.38,513.88 C 510.73,513.88 512.77,513.77 512.93,513.67 C 513.1,513.55 518.68,513.35 525.3,513.23 C 531.95,513.08 538.68,512.88 540.25,512.77 C 541.83,512.65 546.05,512.45 549.62,512.27 C 553.2,512.12 557.2,511.9 558.5,511.75 C 559.8,511.62 564.6,511.35 569.12,511.12 C 573.67,510.9 578.4,510.62 579.62,510.5 C 580.88,510.35 583.3,510.12 585.05,510 C 586.8,509.88 588.93,509.65 589.8,509.52 C 590.68,509.38 592.9,509.15 594.75,509 C 597.73,508.77 601,508.38 607.12,507.5 C 608.1,507.35 609.67,507.18 610.62,507.1 C 612.27,507 615.73,506.43 619.62,505.62 C 620.6,505.43 622.1,505.2 623,505.12 C 625.02,504.95 627.45,504.35 635.75,501.98 C 643.75,499.73 649.45,497.68 653.25,495.73 C 654.27,495.2 656.83,494 658.88,493.05 C 665.95,489.77 670.25,487.12 670.98,485.57 C 671.4,484.7 671.68,484.48 672.8,484.1 C 673.53,483.88 674.85,483.23 675.75,482.68 C 679.25,480.52 679.83,480.25 681.15,480.25 C 682.65,480.25 683,479.95 682.83,478.88 C 682.68,477.9 683.25,477.2 686.85,474 C 690.7,470.62 700,460.02 704.03,454.48 C 707.38,449.85 712.68,441.65 714.68,438 C 715.1,437.25 716.18,435.27 717.12,433.62 C 727.7,414.75 736.83,388.18 741.12,363.62 C 741.48,361.55 741.95,358.98 742.15,357.88 C 742.78,354.3 743.35,349.55 744.43,339 C 745.13,331.8 745.08,311.15 744.3,303 C 743.65,296.05 742.68,287.95 742.1,284.62 C 740.65,276.2 740.25,274.3 738.95,269.25 C 738.53,267.68 738.05,265.75 737.88,265 C 735.2,253.6 728.4,235.48 721.88,222.5 C 717.9,214.57 709.02,200.27 703.88,193.5 C 698.75,186.75 698.08,185.88 692.25,178.88 C 691.33,177.77 688.23,174.48 685.38,171.55 C 677.02,163 670.38,157.22 658,147.77 C 647.88,140.05 627.65,128.4 614.88,122.95 C 603.5,118.1 588.02,112.55 581.62,111.05 C 581.08,110.9 577.73,110.05 574.18,109.15 C 568.08,107.6 563.85,106.6 560.75,106 C 548.65,103.68 534.33,101.77 523.7,101.15 C 520.38,100.92 515.7,100.62 513.38,100.47 C 508.38,100.12 484.45,100.32 478.12,100.75 C 475.93,100.9 472.73,101.12 471,101.25 C 463.43,101.75 448.12,103.6 443,104.62 C 441.98,104.82 439.55,105.27 437.62,105.65 C 427.75,107.47 415.68,110.22 409.12,112.12 C 407.48,112.6 404.73,113.4 403,113.9 C 401.27,114.4 399.25,115 398.5,115.25 C 391.85,117.48 382.18,120.5 381.75,120.5 C 380.88,120.5 380.07,119.4 377.33,114.38 C 370.43,101.85 362.3,88.82 354.58,77.85 C 342.8,61.15 333.83,50.3 322.45,39.12 C 309.73,26.58 298.6,18.9 290.57,17.1 C 289.65,16.9 288.3,16.55 287.6,16.35 C 285.65,15.8 279.7,15.9 277,16.55 Z"
private const val AwakeTail = "M 521.62,308.4 C 514.23,308.92 499.45,310.95 494.62,312.1 C 493.8,312.3 492.18,312.67 491,312.95 C 487.8,313.7 483.48,314.9 480.12,315.98 C 478.48,316.52 476.12,317.25 474.88,317.62 C 473.65,318 471.18,318.88 469.38,319.57 C 467.6,320.25 464.57,321.42 462.68,322.15 C 457.5,324.17 446.73,329.8 438.75,334.62 C 432.27,338.57 421.75,346.27 416.43,350.95 C 405.95,360.17 394.23,373.23 388.12,382.5 C 378.4,397.27 372.8,410.05 369.03,426 C 367.8,431.18 367.33,434.3 366.88,440.62 C 366.32,448.27 366.48,456.38 367.28,461.12 C 367.4,461.95 367.68,463.62 367.85,464.88 C 368.05,466.1 368.3,467.57 368.45,468.12 C 368.6,468.68 368.93,470.02 369.15,471.12 C 369.38,472.23 369.7,473.8 369.85,474.62 C 370.9,479.57 375.4,490.27 378.68,495.55 C 381.4,499.98 387.1,506.23 390.58,508.68 C 391.7,509.45 392.95,510.35 393.33,510.68 C 393.95,511.18 394.33,511.25 396.2,511.25 C 397.4,511.25 398.83,511.38 399.38,511.52 C 399.93,511.68 402.23,511.9 404.5,512.02 C 406.77,512.15 409.75,512.35 411.12,512.48 C 412.5,512.6 416.55,512.83 420.12,512.98 C 423.7,513.12 427.57,513.38 428.75,513.5 C 433.77,514.05 455.23,514.33 479.62,514.12 C 493.1,514 506.05,513.9 508.38,513.88 C 510.73,513.88 512.77,513.77 512.93,513.67 C 513.1,513.55 518.68,513.35 525.3,513.23 C 531.95,513.08 538.68,512.88 540.25,512.77 C 541.83,512.65 546.05,512.45 549.62,512.27 C 553.2,512.12 557.2,511.9 558.5,511.75 C 559.8,511.62 564.6,511.35 569.12,511.12 C 573.67,510.9 578.4,510.62 579.62,510.5 C 580.88,510.35 583.27,510.12 585,510 C 586.73,509.88 589.38,509.6 590.88,509.4 C 592.4,509.2 594.75,508.98 596.12,508.88 C 597.5,508.8 599.58,508.55 600.75,508.35 C 601.92,508.15 603.83,507.88 605,507.75 C 606.17,507.62 608.25,507.4 609.62,507.25 C 612.25,506.98 617.08,506.18 619.62,505.62 C 620.45,505.45 621.92,505.23 622.88,505.12 C 625.02,504.95 627.33,504.38 635.75,501.98 C 643.75,499.7 649.45,497.68 653.25,495.73 C 654.27,495.2 656.75,494.02 658.73,493.12 C 662.95,491.18 665.35,489.9 668.18,488.07 C 670.43,486.6 670.7,486.35 670.3,485.95 C 670.13,485.77 669,486.02 666.65,486.85 C 664.8,487.48 663.08,488 662.85,488 C 662.6,488 660.73,488.55 658.65,489.23 C 654.33,490.65 652.13,491.25 645.12,492.85 C 636.45,494.88 631.62,495.85 629.12,496.12 C 628.3,496.23 626.95,496.45 626.12,496.62 C 625.3,496.8 623.9,497.02 623,497.12 C 622.1,497.23 620.9,497.4 620.33,497.52 C 618.2,497.98 606.95,498.9 598.75,499.27 C 575.25,500.35 558.6,498.88 544.25,494.43 C 538.95,492.8 531.15,488.88 526.83,485.68 C 522.65,482.57 517.7,477.4 514.83,473.12 C 512.58,469.77 510.93,465.5 509.68,459.75 C 507.12,448.15 508.88,436.68 515.05,424.55 C 517,420.73 517.95,419.25 520.93,415.55 C 527.65,407.15 534.4,402 546.5,396.07 C 554.05,392.38 554.83,391.95 559.75,388.67 C 570.88,381.27 577.8,373.32 581.83,363.38 C 584.43,356.95 586.03,347.6 585.52,341.85 C 584.73,332.75 580.45,324.17 574.3,319.3 C 568.85,315 563.18,312.32 556.8,311.1 C 555.78,310.9 554.88,310.7 554.77,310.65 C 554.4,310.4 548.17,309.45 544,308.98 Z"
private const val AwakeNose = "M 192.05,267.52 C 188.85,269.12 188,272.82 190.15,275.93 C 190.68,276.73 192.43,279 194,281 C 195.57,283 197.65,285.68 198.6,286.93 C 201.85,291.35 203.93,292.57 206.78,291.72 C 208.18,291.3 209.22,290.3 211.58,287.18 C 212.75,285.65 215.28,282.4 217.23,280 C 219.15,277.6 220.98,275.15 221.28,274.55 C 222.38,272.32 221.55,269.53 219.35,267.82 C 218.43,267.12 218.43,267.12 205.78,267.05 C 193.3,267 193.1,267 192.05,267.53 Z"
private const val AwakeEyeLeft = "M 99.95,252 C 96.73,252.55 92.3,255.85 90.68,258.93 C 89.9,260.38 89.25,263.32 89.25,265.35 C 89.25,267.07 90.05,270.2 90.93,271.82 C 92.45,274.68 96.4,277.52 99.8,278.22 C 100.73,278.42 107.2,278.5 120.88,278.45 C 140.62,278.38 140.62,278.38 142.12,277.8 C 149.25,275.12 152.82,267.78 150.47,260.57 C 149.1,256.32 145.12,252.82 140.75,252 C 139.18,251.7 101.72,251.7 99.95,252 Z"
private const val AwakeEyeRight = "M 267.38,248.82 C 261.52,251.2 258.75,255.4 258.75,261.9 C 258.75,268.2 261.93,272.47 268.03,274.38 C 270.15,275.02 270.15,275.02 289.9,274.95 C 309.63,274.85 309.63,274.85 311.5,274.18 C 316,272.53 319.02,269.23 320.23,264.65 C 320.88,262.17 320.88,260.85 320.25,258.4 C 319.02,253.57 316.1,250.32 311.5,248.68 C 309.62,248 309.62,248 289.5,248 Z"

private const val AsleepBody = "M 480.38,15.27 C 472.38,15.48 464.32,15.98 456.62,16.73 C 448.5,17.52 437.27,19.15 430.38,20.5 C 427.5,21.08 425.93,21.38 423.12,21.85 C 422.02,22.05 420.4,22.38 419.5,22.6 C 418.6,22.83 416.52,23.27 414.88,23.62 C 411.85,24.3 406.73,25.58 403.25,26.52 C 402.23,26.82 400.6,27.27 399.62,27.52 C 391.07,29.85 377.52,34.25 370.88,36.88 C 356.12,42.65 349.8,45.45 337.75,51.47 C 330.3,55.2 327.7,56.57 324.62,58.38 C 323.4,59.1 320.57,60.73 318.38,62 C 314.05,64.52 306.75,69.07 303.75,71.12 C 287.68,82.12 276.82,90.52 264.88,101.22 C 256.3,108.9 240.82,124.52 236.45,129.9 C 236,130.47 234.45,132.32 233,134 C 229.85,137.7 225.1,143.52 222.75,146.62 C 221.8,147.85 220.07,150.1 218.93,151.62 C 215.78,155.75 214.4,157.75 204.75,172.25 C 203.7,173.82 201.5,177.38 199.88,180.12 C 195.65,187.23 195.65,187.23 192.5,185.4 C 191.47,184.8 189.22,183.5 187.5,182.5 C 178.93,177.52 164.25,169.85 159.35,167.77 C 158.4,167.38 155.43,166.12 152.75,165 C 130.03,155.45 111.25,151.25 97.88,152.75 C 90.5,153.57 83.3,157.6 77.7,163.97 C 70.53,172.15 63.93,183.15 55.5,200.88 C 54.77,202.4 51.38,210.27 50.38,212.75 C 50.05,213.5 49.42,215.07 48.93,216.25 C 47.78,219.07 43.75,230.18 42.5,234.05 C 38.65,245.78 33.67,264.8 32.15,273.68 C 31.95,274.75 31.55,276.98 31.25,278.62 C 28.27,295.12 26.75,310.07 26.35,326.5 C 26.15,334.5 26.9,351.12 27.73,357.67 C 27.9,358.88 28.2,361.23 28.4,362.88 C 28.6,364.52 28.93,367.1 29.12,368.62 C 29.48,371.32 31.15,380.2 31.88,383.12 C 32.08,383.95 32.52,385.85 32.88,387.38 C 33.23,388.88 33.73,390.9 34,391.88 C 34.25,392.82 34.67,394.5 34.98,395.55 C 35.65,398.17 37.15,402.82 38.85,407.62 C 43.35,420.3 47.85,429.25 55.58,440.88 C 59.85,447.32 62.05,449.93 70.48,458.57 C 74.68,462.88 74.8,463.05 74.67,463.88 C 74.5,464.95 74.9,465.23 76.35,465.07 C 77.28,464.95 77.53,465.05 78.88,466.1 C 87.03,472.45 92.08,475.57 101.03,479.8 C 111.3,484.65 115.3,486.23 124,488.73 C 125.03,489 127.28,489.65 129,490.15 C 132.03,491.02 133.7,491.45 138.25,492.48 C 139.43,492.75 141.1,493.12 142,493.35 C 142.9,493.55 144.7,493.85 146,494 C 147.3,494.15 149.28,494.45 150.4,494.65 C 151.5,494.85 153.18,495.12 154.15,495.23 C 165.53,496.62 168.78,496.98 172.98,497.27 C 175.1,497.4 176.93,497.57 177.05,497.65 C 177.25,497.77 187.73,498.38 196.62,498.75 C 199.38,498.88 249,498.98 306.88,498.98 C 561.58,499 551.62,499 559.75,498.62 C 563.95,498.43 569.98,498.12 573.12,498 C 576.3,497.85 580.05,497.62 581.5,497.48 C 582.95,497.35 585.58,497.12 587.38,496.98 C 589.15,496.85 591.3,496.62 592.12,496.5 C 592.95,496.38 594.25,496.25 595,496.25 C 595.75,496.25 597.1,496.07 597.98,495.88 C 598.85,495.65 601.25,495.38 603.35,495.25 C 605.43,495.1 607.98,494.88 609,494.73 C 610.02,494.57 611.77,494.38 612.88,494.27 C 613.98,494.18 615.12,493.95 615.43,493.8 C 615.73,493.62 616.48,493.5 617.1,493.5 C 618.5,493.5 621.55,493.1 624,492.57 C 625.02,492.38 626.83,492 627.98,491.75 C 630.1,491.32 630.75,490.9 630.75,489.98 C 630.75,489.23 631.33,489.05 634.88,488.77 C 636.8,488.62 639.27,488.4 640.38,488.27 C 641.48,488.12 643.62,487.9 645.12,487.75 C 651.83,487.1 663.58,484.95 670.38,483.12 C 671.75,482.75 673.92,482.18 675.2,481.85 C 677.95,481.18 686.68,478.32 689.88,477.07 C 696.48,474.5 698.27,473.75 702.5,471.62 C 709.35,468.25 713.38,466.1 715.75,464.57 C 717.88,463.2 717.88,463.2 719.62,463.35 C 721.6,463.5 722.3,463.27 721.78,462.65 C 720.98,461.68 721.9,460.73 727.25,457 C 728.17,456.38 730.48,454.57 732.38,453 C 745.02,442.57 756.17,428.2 764.45,411.62 C 766.75,406.98 770.6,398.18 771.38,395.75 C 771.62,395 772.08,393.75 772.38,393 C 773.48,390.35 777.17,378.32 779.1,371.25 C 779.77,368.7 780.98,363.55 781.73,359.88 C 782,358.62 782.45,356.5 782.75,355.12 C 784.23,348.2 786.12,335.12 787,326 C 787.12,324.43 787.38,322.23 787.5,321.12 C 787.62,320.02 787.85,316.82 788,314 C 788.15,311.18 788.38,307.98 788.53,306.88 C 789.2,301.12 788.4,270.85 787.4,263.75 C 787.33,263.27 787.15,261.75 787,260.38 C 786.15,252.48 785.17,245.48 784.23,240.5 C 783.98,239.05 783.45,236.3 783.12,234.38 C 782.5,230.9 782.15,229.2 781.23,225.38 C 780.98,224.27 780.35,221.68 779.88,219.62 C 778.45,213.7 776.92,208.5 773.95,199.62 C 771.43,192.12 770.5,189.65 767.25,181.88 C 766.67,180.5 765.83,178.48 765.38,177.38 C 756.38,155.55 739.9,130.32 721.75,110.5 C 711.62,99.48 701.48,90.15 689.85,81.22 C 677.83,72.02 671.85,67.97 659.12,60.45 C 652,56.22 649.5,54.88 638.38,49.3 C 631.42,45.8 620.67,41.15 611.62,37.7 C 606.2,35.65 588.17,29.72 583.62,28.5 C 582.52,28.2 580.85,27.75 579.88,27.5 C 572.9,25.6 568.02,24.35 564.25,23.55 C 558.15,22.22 554.95,21.6 552.5,21.2 C 551.6,21.07 549.88,20.75 548.62,20.5 C 547.4,20.23 545.75,19.95 545,19.88 C 544.25,19.8 542.77,19.58 541.75,19.38 C 540.73,19.17 538.12,18.8 536,18.5 C 533.88,18.23 530.95,17.83 529.5,17.62 C 528.05,17.42 525.35,17.15 523.5,17 C 521.65,16.85 519.27,16.62 518.25,16.5 C 515.98,16.2 505.4,15.58 499.83,15.38 C 491.13,15.1 487.6,15.08 480.38,15.27 Z"
private const val AsleepTail = "M 562.75,267.75 C 556.15,268.2 552.75,268.5 549.25,268.97 C 547.25,269.25 544.33,269.65 542.75,269.85 C 541.17,270.07 539.33,270.37 538.62,270.52 C 537.95,270.65 536.08,271 534.5,271.27 C 529.8,272.07 522.73,273.82 515.5,276 C 513.77,276.52 511.52,277.2 510.5,277.5 C 502.2,279.93 485.07,286.98 476,291.67 C 469.88,294.85 465.62,297.15 463.25,298.62 C 456.02,303 453,304.93 451.18,306.17 C 445.57,310.07 442.23,312.48 440.65,313.73 C 439.7,314.5 438.58,315.4 438.15,315.75 C 428.15,323.73 420.4,331 413,339.35 C 399.62,354.42 389.8,370.43 383.33,387.62 C 381,393.8 378.45,403.12 377.75,407.98 C 377.6,409 377.43,409.92 377.35,410.02 C 377.28,410.15 377.12,411.45 376.98,412.93 C 376.85,414.43 376.62,416.57 376.5,417.75 C 375.98,422.18 375.93,426.75 376.33,430 C 376.55,431.73 376.8,434.07 376.9,435.25 C 377.4,442.02 379.45,450.75 382.3,458.25 C 385.25,466 391.23,476.23 396.5,482.48 C 401.5,488.43 407.32,493.73 412.2,496.75 C 413.6,497.62 414.88,498.48 415.03,498.68 C 415.48,499.18 548.9,499.15 559.75,498.62 C 563.95,498.43 569.98,498.12 573.12,498 C 576.3,497.85 580.05,497.62 581.5,497.48 C 582.95,497.35 585.58,497.12 587.38,496.98 C 589.15,496.85 591.3,496.62 592.12,496.5 C 592.95,496.38 594.25,496.25 595,496.25 C 595.75,496.25 597.1,496.07 597.98,495.88 C 598.85,495.65 601.25,495.38 603.35,495.25 C 605.43,495.1 607.98,494.88 609,494.73 C 610.02,494.57 611.77,494.38 612.88,494.27 C 613.98,494.18 615.12,493.95 615.43,493.8 C 615.73,493.62 616.5,493.5 617.18,493.5 C 618.4,493.5 622.55,492.93 624.38,492.52 C 624.92,492.4 626.27,492.12 627.38,491.9 C 630,491.35 630.17,491.3 630.35,490.82 C 630.7,489.9 630.25,489.88 617.18,489.95 C 610.13,489.98 602.98,489.9 601.25,489.77 C 595.67,489.35 592.17,489 589.75,488.62 C 588.45,488.43 586.65,488.2 585.75,488.12 C 584.85,488.05 583.12,487.8 581.88,487.62 C 580.65,487.43 578.85,487.15 577.88,487.02 C 574.45,486.55 567.23,484.77 561.75,483.07 C 539.9,476.27 523.98,464.52 515.93,449.25 C 513.38,444.43 510.88,436.62 510.18,431.38 C 509.7,427.73 509.82,420.93 510.48,417 C 511.58,410.12 511.78,409.25 512.85,406.1 C 516.38,395.7 520.43,388.1 527.73,378.25 C 532.88,371.27 544.9,359.32 552.88,353.23 C 565.12,343.9 572.92,338.65 582.12,333.62 C 590.02,329.27 591.73,328.4 598.08,325.23 C 609.55,319.48 616.68,314.85 620.93,310.45 C 622.4,308.9 625.15,304.92 626.12,302.88 C 626.67,301.73 627.15,300.12 627.53,298.15 C 628.08,295.25 628.08,295.12 627.68,292.6 C 627.13,289.12 626.5,287.52 624.85,285.07 C 621.38,279.95 614.15,275.07 605.88,272.3 C 599.88,270.28 590.88,268.55 583.25,267.97 C 578.55,267.62 566.75,267.5 562.75,267.75 Z"
private const val AsleepNose = "M 250.25,392.9 C 244.28,394.75 238.88,396.52 238.28,396.82 C 235.9,398.02 234.93,400.38 235.98,402.42 C 236.38,403.2 237.35,404.07 240.15,406.17 C 242.18,407.65 245.62,410.32 247.85,412.07 C 254.68,417.48 255.4,417.82 257.88,416.9 C 259.35,416.32 259.8,415.62 260.6,412.62 C 260.98,411.25 261.73,408.73 262.25,407 C 264.9,398.5 266.05,394.12 265.93,392.98 C 265.75,390.9 264.18,389.48 262.07,389.5 Z"
private const val AsleepEyeLeft = "M 187.6,393 C 185.68,393.77 184.3,395.12 182.9,397.65 C 180.8,401.45 178.85,404.08 176.18,406.67 C 169.38,413.3 162.78,415.97 154,415.68 C 148,415.48 144.3,414.45 137.88,411.15 C 134.88,409.62 134.88,409.62 132.62,409.62 C 129.97,409.62 128.55,410.18 126.88,411.88 C 125.2,413.55 124.62,414.98 124.62,417.62 C 124.62,421.43 126.17,423.7 130.18,425.7 C 140.55,430.9 148.73,432.75 158.43,432.05 C 167.28,431.4 175.08,428.48 182.5,423.02 C 188.62,418.55 196.6,408.45 198.1,403.27 C 199.28,399.25 197.73,395.3 194.23,393.38 Z"
private const val AsleepEyeRight = "M 352.6,339.27 C 350.05,340.25 348.65,341.95 347.28,345.67 C 343.4,356.2 333.93,364.12 323.1,365.88 C 317.5,366.77 310.18,365.8 303.88,363.3 C 302.07,362.6 301.27,362.42 299.95,362.42 C 294.82,362.42 290.93,367.67 292.38,372.65 C 293.43,376.32 295.32,377.88 300.88,379.62 C 309.4,382.32 312.82,382.88 319.88,382.67 C 327.4,382.48 331.6,381.42 338.62,378 C 346.8,374.02 352.2,369.23 357.65,361.07 C 361.18,355.8 363.35,350.5 363.35,347.12 Z"
