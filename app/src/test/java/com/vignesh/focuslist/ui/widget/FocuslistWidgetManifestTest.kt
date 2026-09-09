package com.vignesh.focuslist.ui.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Wiring that the compiler cannot prove for a launcher-hosted surface. */
class FocuslistWidgetManifestTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val providerInfo = File("src/main/res/xml/focuslist_widget_info.xml").readText()
    private val provider = manifest.receiverDeclaration(".ui.widget.FocuslistWidgetReceiver")
    private val refresh = manifest.receiverDeclaration(".ui.widget.FocuslistWidgetRefreshReceiver")

    @Test
    fun `the glance receiver is registered as an app widget provider`() {
        assertTrue("The widget receiver is missing.", provider.isNotEmpty())
        assertTrue("android.appwidget.action.APPWIDGET_UPDATE" in provider)
        assertTrue("@xml/focuslist_widget_info" in provider)
    }

    @Test
    fun `clock changes refresh the launcher even when the app is closed`() {
        assertTrue("The clock receiver is missing.", refresh.isNotEmpty())
        listOf(
            "android.intent.action.DATE_CHANGED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED"
        ).forEach { action -> assertTrue("$action is missing.", action in refresh) }
        assertTrue("The system cannot reach a non-exported clock receiver.",
            """android:exported="true""" in refresh)
    }

    @Test
    fun `provider is responsive and event driven`() {
        assertTrue("""android:resizeMode="horizontal|vertical""" in providerInfo)
        assertTrue("""android:updatePeriodMillis="0""" in providerInfo)
        assertTrue("@drawable/focuslist_widget_preview" in providerInfo)
    }
}

private fun String.receiverDeclaration(name: String): String =
    substringAfter("""android:name="$name""", missingDelimiterValue = "")
        .substringBefore("</receiver>", missingDelimiterValue = "")
