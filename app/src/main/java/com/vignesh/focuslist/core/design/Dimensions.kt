package com.vignesh.focuslist.core.design

import androidx.compose.ui.unit.dp

/**
 * Sizes that are not spacing.
 *
 * [FocuslistSpacing] covers the gaps between things. This covers the few
 * measurements that describe a thing itself and are needed in more than one
 * place, so that no screen has to write a number down.
 */
object FocuslistDimensions {

    /** The smallest an interactive target may be, in either direction. */
    val TouchTargetMin = 48.dp

    /** The shortest a task row may be, before its content makes it taller. */
    val TaskRowMinHeight = 56.dp

    /**
     * The widest a column of task content should ever be.
     *
     * A task list eight hundred pixels wide is a worse task list: the eye has
     * to travel from a checkbox on one side to a title that begins a long way
     * from it, and nothing is gained for the distance. Wide windows therefore
     * constrain and centre rather than stretch, which is what `PRODUCT.md`
     * means by not spreading the phone layout across a tablet.
     *
     * Spent through `focuslistContentGutter`, which turns it into the padding
     * each screen adds at its sides.
     */
    val ContentMaxWidth = 640.dp

    /**
     * The window width at which navigation moves from the bottom bar to a rail.
     *
     * Material's own navigation-rail guidance gives the component but no
     * breakpoint; this is the medium-width boundary the Focuslist adaptive
     * table names, and it decides presentation only. The same four destinations
     * are reachable on either side of it.
     */
    val NavigationRailBreakpoint = 600.dp

    /**
     * Vertical space a list reserves below its last row when the screen has a
     * floating action button.
     *
     * The Scaffold's content padding accounts for window insets and a bottom
     * bar but not for the button, so the list has to clear the button itself.
     * The regular button is 56dp tall and the Scaffold floats it 16dp above the
     * bottom of the content area; the last term is a comfortable gap between
     * the final task and the button.
     *
     * Composed from the spacing scale rather than written as a total, so the
     * three quantities it is made of stay visible: button, float, gap.
     *
     * Shared, because Today and Inbox both need it and previously computed it
     * separately, which is two chances to get it wrong.
     */
    val FabClearance =
        FocuslistSpacing.xxl + FocuslistSpacing.xs +
            FocuslistSpacing.md +
            FocuslistSpacing.xs
}
