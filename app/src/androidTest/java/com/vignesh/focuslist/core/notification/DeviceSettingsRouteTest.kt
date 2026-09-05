package com.vignesh.focuslist.core.notification

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vignesh.focuslist.core.domain.DeviceRestriction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the routing table does against a real package manager.
 *
 * The unit test can check the table. It cannot check the thing that decides
 * whether any of it works, which is package visibility: from Android 11 an
 * undeclared package is invisible and resolves to nothing, and the app would
 * quietly behave as though the feature had never been written. That question
 * only has an answer on a device.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSettingsRouteTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theFallbackResolvesOnThisDevice() {
        // The one destination the app promises. If this fails, the button does
        // nothing at all here.
        assertNotNull(
            "The app's own settings page did not resolve",
            context.packageManager.resolveActivity(context.appSettingsIntent(), 0)
        )
    }

    /**
     * On a phone the app warns about, the deep link has to land somewhere.
     *
     * The only assertion in the suite that needs particular hardware, and it
     * passes vacuously on everything else. Run it on a OnePlus, a Xiaomi or a
     * Samsung and it answers the question the table cannot: whether the guessed
     * activity names are still the names that vendor ships. When one is
     * renamed, this is what says so.
     */
    @Test
    fun aRestrictedDeviceHasAtLeastOneScreenToOffer() {
        val restriction = restrictionFor(Build.MANUFACTURER) ?: return

        assertTrue(
            "${Build.MANUFACTURER} maps to $restriction, but none of its screens resolved. " +
                "The candidate list in DeviceSettingsRoute.kt is out of date, or a package " +
                "is missing from <queries> in the manifest.",
            context.resolvableScreens(restriction).isNotEmpty()
        )
    }

    @Test
    fun aDeviceWithNoRestrictionIsOfferedNothing() {
        assertTrue(context.resolvableScreens(null).isEmpty())
    }

    /**
     * Every restriction is asked about, on whatever device this runs on.
     *
     * Nothing is asserted about the answers. It is here because the resolution
     * path touches package visibility and cross-user component lookups, and a
     * mistake there throws rather than returning null.
     */
    @Test
    fun askingAboutEveryRestrictionIsSafe() {
        DeviceRestriction.entries.forEach { context.resolvableScreens(it) }
    }
}
