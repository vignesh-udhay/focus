package com.vignesh.focuslist.core.notification

import com.vignesh.focuslist.core.domain.DeviceRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which manufacturers the app warns about.
 *
 * Worth a test of its own because both mistakes are expensive in opposite
 * ways. Missing a vendor means a user is not told their device eats reminders
 * until one is eaten. Naming a vendor that behaves means a warning on a phone
 * that works, which teaches its owner that this screen is noise, and then the
 * real warning is not read either.
 */
class RestrictionForTest {

    @Test
    fun `the OxygenOS family gets sleep standby`() {
        // The family in D-009, where OplusHansManager was observed freezing
        // the process around every broadcast.
        listOf("OnePlus", "OPPO", "realme").forEach { name ->
            assertEquals(name, DeviceRestriction.SleepStandby, restrictionFor(name))
        }
    }

    @Test
    fun `the MIUI family gets autostart`() {
        listOf("Xiaomi", "Redmi", "POCO").forEach { name ->
            assertEquals(name, DeviceRestriction.Autostart, restrictionFor(name))
        }
    }

    @Test
    fun `Samsung gets sleeping apps`() {
        assertEquals(DeviceRestriction.SleepingApps, restrictionFor("samsung"))
    }

    @Test
    fun `Huawei and Honor get protected apps`() {
        assertEquals(DeviceRestriction.ProtectedApps, restrictionFor("HUAWEI"))
        assertEquals(DeviceRestriction.ProtectedApps, restrictionFor("HONOR"))
    }

    @Test
    fun `a device with no known restriction is left alone`() {
        // The honest default. A false warning on a Pixel costs the app the
        // user's attention for the warning that matters.
        listOf("Google", "motorola", "Nothing", "Sony", "Fairphone").forEach { name ->
            assertNull(name, restrictionFor(name))
        }
    }

    @Test
    fun `the match survives the casing a build actually reports`() {
        // Build.MANUFACTURER is not a controlled vocabulary. The same vendor
        // ships it capitalised on one build and lowercase on another.
        assertEquals(DeviceRestriction.SleepStandby, restrictionFor("ONEPLUS"))
        assertEquals(DeviceRestriction.SleepStandby, restrictionFor("oneplus"))
        assertEquals(DeviceRestriction.Autostart, restrictionFor("XIAOMI"))
    }

    @Test
    fun `surrounding whitespace does not defeat the match`() {
        assertEquals(DeviceRestriction.SleepStandby, restrictionFor(" OnePlus "))
    }

    @Test
    fun `a sub-brand shipping the parent's software is matched by prefix`() {
        // "Redmi Note" and the like arrive as the whole marketing name on some
        // builds, and the software underneath is still MIUI.
        assertEquals(DeviceRestriction.Autostart, restrictionFor("Redmi Note 13"))
        assertEquals(DeviceRestriction.SleepStandby, restrictionFor("OnePlus 8T"))
    }

    @Test
    fun `an empty manufacturer is not a restriction`() {
        assertNull(restrictionFor(""))
        assertNull(restrictionFor("   "))
    }
}
