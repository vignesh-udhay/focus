package app.tellcat.core.notification

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The manifest declarations "Ring like an alarm" cannot work without.
 * `docs/decisions.md` D-102.
 *
 * Every one of these fails silently on a device: a foreground service of an
 * undeclared type throws only when it starts, an activity without
 * `showWhenLocked` opens behind the lock screen, and a missing permission makes
 * the full screen a heads-up for ever. None of it is visible until an alarm is
 * due, which is the worst moment to find out.
 */
class AlarmManifestTest {

    private val manifest: String = File("src/main/AndroidManifest.xml").readText()

    private fun declaration(name: String, closing: String = "/>"): String =
        manifest.substringAfter(name, "").substringBefore(closing, "")

    @Test
    fun `the permissions are declared`() {
        listOf(
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK"
        ).forEach { permission ->
            assertTrue("$permission is missing.", """<uses-permission android:name="$permission" />""" in manifest)
        }
    }

    /**
     * systemExempted, the type Android allows an app holding the exact-alarm
     * grant, which an alarm-style reminder only rings with. Not mediaPlayback:
     * Play asks that playback be started by the user, and an alarm starts itself.
     */
    @Test
    fun `the service is a system-exempted foreground service, private, and runs before the first unlock`() {
        val service = declaration(".core.notification.AlarmRingService")

        assertTrue("AlarmRingService is not declared.", service.isNotEmpty())
        assertTrue("""android:foregroundServiceType="systemExempted"""" in service)
        assertTrue("""android:exported="false"""" in service)
        assertTrue("""android:directBootAware="true"""" in service)
    }

    @Test
    fun `no media-playback declaration is left for Play to ask about`() {
        assertTrue("FOREGROUND_SERVICE_MEDIA_PLAYBACK" !in manifest)
        assertTrue("mediaPlayback" !in manifest)
    }

    @Test
    fun `the alarm screen shows over the lock screen and turns the screen on`() {
        val activity = declaration("\".AlarmActivity\"")

        assertTrue("AlarmActivity is not declared.", activity.isNotEmpty())
        assertTrue("""android:showWhenLocked="true"""" in activity)
        assertTrue("""android:turnScreenOn="true"""" in activity)
        assertTrue("""android:exported="false"""" in activity)
        assertTrue("""android:directBootAware="true"""" in activity)
        assertTrue("""android:excludeFromRecents="true"""" in activity)
    }
}
