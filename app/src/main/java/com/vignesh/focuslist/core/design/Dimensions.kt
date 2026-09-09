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
     * The column Focus lays its four elements out in.
     *
     * `expressive-components.md` draws Focus as one 364dp column, centred in the
     * content area rather than pinned under the app bar: it is a single-purpose
     * mode screen with one column on it, and hanging that column from the top
     * left the lower half of the screen empty for no reason.
     *
     * It is also the width the action row's arithmetic is done against. Two
     * worded buttons come to roughly 223dp and 198dp at 200% font scale and
     * overflow it, which is why the clock control is an icon.
     */
    val FocusColumnWidth = 364.dp

    /**
     * The gap between everything on Focus.
     *
     * One value for all three gaps, because the screen is four elements in a
     * column and giving them different spacings would imply a grouping that is
     * not there. 20dp rather than a `FocuslistSpacing` step, because it sits
     * between `md` and `lg` and the design names it: at 16dp the shape crowds
     * the title, at 24dp the column overflows a short screen at 200%.
     */
    val FocusColumnGap = 20.dp

    /**
     * The Focus shape, at a fixed size in every window.
     *
     * It holds a fixed-size readout rather than content, so scaling it with the
     * window would only make the digits look lost. An earlier design capped it
     * at 320dp "so a wide window gets a shape, not a wall", which was solving a
     * problem this size does not have.
     */

    /**
     * The height of a worded action button, anywhere in the app.
     *
     * A floor rather than a fixed height: pinned exactly, a label at 200% font
     * scale is cut through the middle of its letters, so the button is allowed
     * to grow to hold its own text.
     *
     * 56dp is Material's Medium button, and the expressive scale runs 32, 40,
     * 56, 96, 136 with nothing between 40 and 56. Three sheet buttons used to
     * read [TouchTargetMin] for their height, which put them at 48dp: not a
     * size in this system at all, but an accessibility floor standing in for
     * one. Start focus reached instead for the Focus screen's own control size
     * and got the right number through the wrong token. This is the token they
     * were both looking for.
     */
    val ActionHeight = 56.dp

    /**
     * The side of Focus's square clock control.
     *
     * Fixed rather than a floor, because roundness is what keeps it from
     * growing with the text at all. The worded button beside it is
     * [ActionHeight], which is the same 56dp described as what it is.
     */
    val FocusControlSize = 56.dp

    /**
     * The side of one weekday chip in the Repeat editor.
     *
     * Named rather than left to the component, because seven chips at their
     * natural width do not fit one line. Measured on a 1080px emulator they came
     * to 1002px inside a 992px content column, so the seventh day wrapped to a
     * row of its own at the default font scale.
     *
     * 48dp is the board's own number: its node is called "Weekday selector / 7
     * equal 48dp targets", and seven of them come to 336dp inside the sheet's
     * 364dp with the remainder spent as the gaps between.
     *
     * **Not [TouchTargetMin], which is the same number and a different thing.**
     * That is a floor every interactive element has to clear, and three sheet
     * buttons once read it for their height and landed on a measurement nothing
     * had chosen. This is a size the design names, which happens to agree with
     * the floor.
     *
     * Applied as a fixed width and a minimum height, and the asymmetry is the
     * point. Fixing both would make a true circle and clip the letter at large
     * font scales, which `expressive-components.md` forbids outright; a floor
     * lets the chip grow downward to hold its own text while the row keeps its
     * seven columns.
     */
    val WeekdayChipSize = 48.dp

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
