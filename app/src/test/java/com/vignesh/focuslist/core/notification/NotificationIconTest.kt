package com.vignesh.focuslist.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two drawings of the Catimo mark stay one drawing. D-072.
 *
 * The notification icon copies its path from the themed launcher layer, because
 * a vector drawable cannot reference another one and only the frame around the
 * artwork differs. A copy can drift, and the way it would drift is someone
 * re-exporting the mark and updating the file they were looking at. This fails
 * the build at that moment rather than at the next look at a status bar.
 */
class NotificationIconTest {

    private val themed = File("src/main/res/drawable/ic_launcher_monochrome.xml").readText()
    private val notification = File("src/main/res/drawable/ic_notification_cat.xml").readText()

    @Test
    fun `the notification mark is the silhouette the launcher already uses`() {
        assertEquals(
            "The mark was re-drawn in one file and not the other.",
            themed.pathData(),
            notification.pathData()
        )
    }

    /**
     * 24dp, which is what Material draws a system icon at and what the status
     * bar expects. The frame is the only thing that differs between the two
     * files, so it is the thing worth asserting.
     */
    @Test
    fun `the notification mark is framed as an icon rather than a launcher tile`() {
        assertTrue(
            "A notification icon is 24dp; this one is not.",
            """android:width="24dp"""" in notification &&
                """android:height="24dp"""" in notification
        )
        assertTrue(
            "The themed layer should still be a 108dp adaptive frame.",
            """android:width="108dp"""" in themed
        )
    }
}

/** The one path both files draw, as written. */
private fun String.pathData(): String =
    substringAfter("android:pathData=\"", missingDelimiterValue = "")
        .substringBefore("\"", missingDelimiterValue = "")
        .also { require(it.isNotEmpty()) { "No pathData found." } }
