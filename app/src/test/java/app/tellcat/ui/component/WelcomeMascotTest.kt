package app.tellcat.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The welcome head and the launcher icon stay one drawing.
 *
 * `WelcomeMascot.kt` rebuilds the launcher's artwork in Kotlin so it can bind
 * to the colour roles, which a vector drawable cannot read. Only the colours
 * differ: the geometry and the group that frames it are copied. A copy can
 * drift, and the way it would drift is someone re-exporting the mark from the
 * board and updating the file they were looking at. This fails the build at
 * that moment rather than at the next look at a welcome screen.
 *
 * The same argument and the same shape as `NotificationIconTest`, which guards
 * the other copy of this mark under D-072.
 */
class WelcomeMascotTest {

    private val launcher = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()
    private val mascot = File("src/main/java/app/tellcat/ui/component/WelcomeMascot.kt").readText()

    @Test
    fun `every path the launcher draws is the path the welcome head draws`() {
        val drawn = launcher.pathData()

        assertEquals(
            "The launcher icon should still be the face and its three features.",
            4,
            drawn.size
        )
        assertEquals(
            "The mark was re-drawn in one file and not the other.",
            drawn,
            mascot.kotlinPaths()
        )
    }

    /**
     * The frame is the other half of the copy. The head sits where it sits
     * because of this group, so a re-export that moves or rescales the artwork
     * has to move it here too.
     */
    @Test
    fun `the welcome head carries the launcher's own frame`() {
        assertEquals(
            "The group that centres the artwork no longer matches the launcher.",
            listOf(
                launcher.attribute("scaleX"),
                launcher.attribute("translateX"),
                launcher.attribute("translateY")
            ),
            listOf(
                mascot.constant("ArtworkScale"),
                mascot.constant("ArtworkTranslateX"),
                mascot.constant("ArtworkTranslateY")
            )
        )
    }

    /**
     * The point of the rebuild. Fixed hex here would be the defect the dark
     * theme audit found: the one cat in the app that ignores the palette.
     */
    @Test
    fun `the welcome head takes colour roles and not the launcher's hex`() {
        assertEquals(
            "A hard-coded colour is back in the welcome mascot.",
            emptyList<String>(),
            Regex("0x[0-9A-Fa-f]{8}").findAll(mascot).map { it.value }.toList()
        )
    }
}

/** The four paths the launcher draws, in the order it draws them. */
private fun String.pathData(): List<String> =
    Regex("""android:pathData="([^"]+)"""")
        .findAll(this)
        .map { it.groupValues[1] }
        .toList()
        .also { require(it.isNotEmpty()) { "No pathData found." } }

/** The same four, as the Kotlin source spells them. */
private fun String.kotlinPaths(): List<String> =
    Regex("""private const val (?:Head|EyeLeft|EyeRight|Nose) =\s*\n\s*"([^"]+)"""")
        .findAll(this)
        .map { it.groupValues[1] }
        .toList()
        .also { require(it.isNotEmpty()) { "No path constants found." } }

/** A group attribute of the launcher drawable, as written. */
private fun String.attribute(name: String): String =
    Regex("""android:$name="([^"]+)"""").find(this)!!.groupValues[1].trimEnd('f')

/** A float constant of the Kotlin source, spelled the same way. */
private fun String.constant(name: String): String =
    Regex("""private const val $name = ([0-9.]+)f""").find(this)!!.groupValues[1]
