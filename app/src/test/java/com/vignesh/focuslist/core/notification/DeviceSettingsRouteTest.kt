package com.vignesh.focuslist.core.notification

import com.vignesh.focuslist.core.domain.DeviceRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The routing table, and the manifest entry that decides whether it works.
 *
 * The failure this guards against is silent by construction. A candidate whose
 * package is not declared in `<queries>` is invisible from Android 11 onward,
 * so it never resolves, so the app falls through to the generic settings page
 * and nothing anywhere reports a problem. It would look exactly like the
 * feature not being built, on the only phones that need it.
 */
class DeviceSettingsRouteTest {

    @Test
    fun `every restriction has somewhere to send the user`() {
        DeviceRestriction.entries.forEach { restriction ->
            assertTrue(
                "$restriction has no candidate screen",
                candidatesFor(restriction).isNotEmpty()
            )
        }
    }

    @Test
    fun `a device with no restriction is sent nowhere in particular`() {
        // Not an oversight. There is no vendor screen to guess at, and the
        // caller's fallback is the right destination.
        assertEquals(emptyList<SettingsScreen>(), candidatesFor(null))
    }

    @Test
    fun `every candidate belongs to a package the app declares`() {
        DeviceRestriction.entries.flatMap { candidatesFor(it) }.forEach { screen ->
            assertTrue(
                "${screen.packageName} is not in QueriedSettingsPackages",
                screen.packageName in QueriedSettingsPackages
            )
        }
    }

    @Test
    fun `every declared package is queried in the manifest`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        QueriedSettingsPackages.forEach { packageName ->
            assertTrue(
                "$packageName is missing from <queries>, so it will never resolve",
                """<package android:name="$packageName" />""" in manifest
            )
        }
    }

    @Test
    fun `the same screen is not tried twice`() {
        // A duplicate is harmless at runtime and a sign the table was edited
        // by copying, which is how the wrong activity name gets in.
        val all = DeviceRestriction.entries.flatMap { candidatesFor(it) }

        assertEquals(all.size, all.distinct().size)
    }

    @Test
    fun `activity names are fully qualified`() {
        // ComponentName does not expand a leading dot against the target
        // package, only against the caller's. A ".Foo" here would resolve
        // against Focuslist and find nothing.
        DeviceRestriction.entries.flatMap { candidatesFor(it) }.forEach { screen ->
            assertTrue(
                "${screen.activity} is not a fully qualified class name",
                !screen.activity.startsWith('.') && screen.activity.contains('.')
            )
        }
    }
}
