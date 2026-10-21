package app.tellcat.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The welcome mascot: the Tellcat head, facing front.
 *
 * The one screen shown before a fresh install enters the task lists, so the
 * mark the user is about to put on their home screen is the thing that greets
 * them. The five empty-state poses and the two Focus poses are whole cats;
 * this is the face alone, which is what an app icon is.
 *
 * ## Why the paths are copied rather than the drawable drawn
 *
 * The artwork is `ic_launcher_foreground.xml`, and that file is deliberately
 * fixed hex: a launcher renders outside the app's theme, so the colour roles
 * do not resolve there. Inside the app the opposite rule applies, and
 * [MascotImage] states it: an illustration binds to the fixed roles so it
 * belongs to the user's phone. Drawing the launcher asset here would have put
 * the one cat in the app that ignores the palette, which is what it did until
 * the dark theme audit found it.
 *
 * A vector drawable cannot read the Compose colour scheme and one `colorFilter`
 * cannot supply two colours, so the paths are rebuilt here against the roles.
 * That copy can drift, exactly as D-072's can, and `WelcomeMascotTest` fails
 * the build at the moment someone re-exports the mark and updates one file.
 *
 * [light] is the face, the launcher's P90. [dark] is the eyes and the nose, its
 * P30, which the launcher background also carries so the features read as holes
 * punched through the face rather than marks drawn on it. No tone takes [mid]:
 * the face is flat, with no fold to shade.
 *
 * The group that places the artwork is the launcher's own, so the face is the
 * icon's exactly. The frame around it is not: the launcher's 108 canvas is
 * mostly safe-zone padding, which drew the face 52dp wide and read as a gap on
 * the welcome screen, so the frame is cropped to the face and drawn at twice
 * the size.
 *
 * Purely decorative to a screen reader. The welcome headline beneath it carries
 * the heading semantics and says what the screen is.
 */
@Composable
internal fun WelcomeMascot(modifier: Modifier = Modifier) {
    MascotImage(
        width = FrameWidth * FrameScale,
        height = FrameHeight * FrameScale,
        modifier = modifier,
        build = ::catHead
    )
}

/** The face and a little air, in the launcher canvas's units. */
private const val FrameLeft = 26f
private const val FrameTop = 28f
private const val FrameWidth = 56f
private const val FrameHeight = 52f
private const val FrameScale = 2f

private fun catHead(light: Color, mid: Color, dark: Color): ImageVector =
    ImageVector.Builder(
        name = "WelcomeCatHead",
        defaultWidth = (FrameWidth * FrameScale).dp,
        defaultHeight = (FrameHeight * FrameScale).dp,
        viewportWidth = FrameWidth,
        viewportHeight = FrameHeight
    )
        .addGroup(name = "frame", translationX = -FrameLeft, translationY = -FrameTop)
        .addGroup(
            name = "artwork",
            scaleX = ArtworkScale,
            scaleY = ArtworkScale,
            translationX = ArtworkTranslateX,
            translationY = ArtworkTranslateY
        )
        .addPath(pathData = addPathNodes(Head), fill = SolidColor(light))
        .addPath(pathData = addPathNodes(EyeLeft), fill = SolidColor(dark))
        .addPath(pathData = addPathNodes(EyeRight), fill = SolidColor(dark))
        .addPath(pathData = addPathNodes(Nose), fill = SolidColor(dark))
        .clearGroup()
        .clearGroup()
        .build()

private const val ArtworkScale = 0.055424f
private const val ArtworkTranslateX = 28.0f
private const val ArtworkTranslateY = 29.713f

private const val Head =
    "M296.31 0C286.4 1.55 275.4 6 264.81 12.7C260.5 15.45 250.21 22.9 249.31 23.9C249.16 24.05 247.35 25.65 245.31 27.4C210.96 56.9 167.91 124.45 132.71 204.2C119.95 233.15 104.86 274.75 94.7 308.95C93.6 312.65 91.95 318.15 91.05 321.2C87.85 331.65 84.35 344.2 82.3 352.4C80.65 359.15 79.8 361.5 77.7 365.4C72.25 375.55 58.25 403.7 53.35 414.4C34.25 455.9 21.7 491 12.6 528.2C8.1 546.65 6.75 553.25 4.75 566.2C-1.3 605.65 -1.55 635.7 3.85 667.45C7.3 687.55 13.8 709.5 21.05 725.2C30.25 745.2 38.55 758.85 50.65 773.95C77.95 808 118.45 833.85 168.21 849C169.91 849.5 172.81 850.4 174.71 851C178.21 852.05 187.35 854.5 192.56 855.8C206.06 859.05 211.66 860.2 232.31 863.95C241.66 865.65 269.4 869.05 288.81 870.9C333.65 875.15 380.81 876.55 475.65 876.4C550.05 876.25 580.36 875.65 613.55 873.65C629.61 872.7 649.4 871.2 653.11 870.7C654.21 870.55 659.46 870 664.8 869.45C670.15 868.9 676 868.2 677.8 867.95C679.61 867.7 682.96 867.25 685.3 866.95C699.3 865.15 723.15 861.05 732.05 858.95C739.5 857.2 751.36 854.35 752.8 853.95C788 844.2 813 833.8 836.71 818.95C881.21 791.1 913.15 747.85 928.25 694.95C939.65 655.3 941.21 615.1 933.25 565.95C932 558.35 929.71 546.15 928.8 542.7C928.61 541.85 928.15 539.85 927.8 538.2C924.25 521.55 914.96 490.75 907.4 470.7C902.9 458.75 900.71 453.1 898.36 447.45C895.46 440.55 895.25 440.1 892.36 433.8C891.21 431.35 890.3 429.3 890.3 429.2C890.3 428.8 885.46 418.75 879 405.7C870.5 388.55 867.11 382.05 858 365.7C854.3 359 853.61 357.05 850.55 345.2C849.55 341.35 848.21 336.05 847.5 333.45C846.8 330.85 845.61 326.2 844.8 323.2C844 320.15 842.65 315.1 841.8 311.95C839.36 302.95 838.8 301.05 837.5 295.95C831.75 274.15 821.55 240.85 814.96 222.45C791.15 155.65 767.55 126.7 731.05 119.6C724 118.25 706.71 118.4 697.86 119.9C660.86 126.2 616.9 147.5 574.05 179.9C566.75 185.4 566 186 555.15 194.45C541.65 205 542.65 204.4 538.8 204.4C537 204.4 533.65 204.1 531.3 203.65C528.96 203.25 523.9 202.35 520.05 201.7C516.21 201.05 511.06 200.15 508.56 199.7C506.11 199.25 502.81 198.7 501.31 198.45C493.86 197.2 492.15 196.95 486.81 196C483.65 195.5 480.5 194.9 479.81 194.75C479.11 194.6 475.86 194 472.56 193.4C462.5 191.55 458.2 188.8 454.56 181.9C453.5 179.85 450 173.6 446.81 167.95C443.61 162.3 440.2 156.35 439.31 154.7C423.86 126.6 403.31 93 386.65 68.45C358.4 26.8 337.15 6.5 316.56 1.4L296.31 0Z"

private const val EyeLeft =
    "M240.35 539.75C228.31 543 221 555.35 224.06 567.2C226.25 575.65 233.96 582.55 242.56 583.8C244.21 584.05 264.36 584.2 287.31 584.15C335.11 584 332.75 584.25 338.9 579.55C344.75 575.1 347.81 569.1 347.81 561.9C347.81 552.3 342.15 544.1 332.95 540.35L330.06 539.2L286.56 539.1L240.35 539.75Z"

private const val EyeRight =
    "M608.11 539.5C602.75 540.75 596.65 545.4 593.65 550.45C590.9 555.2 590 563.05 591.61 568.45C594 576.55 602.15 583.2 610.21 583.7C616.5 584.1 667.55 584.3 683.05 584L698.55 583.7L702.46 581.75C718.05 573.95 719.61 552.6 705.25 543C698.9 538.75 701.36 538.95 652.86 539L608.11 539.5Z"

private const val Nose =
    "M457.81 571.95C444.4 572.4 440.4 573.4 438.9 576.75C437.45 579.9 438.5 583.05 443.06 589.2C450.36 599.1 459.5 608.55 464.31 611.15C467.95 613.15 471.65 612.9 475.4 610.35C481.86 606.05 495.15 591 498.9 583.8C501.11 579.65 500.65 576.65 497.65 574.6L457.81 571.95Z"
