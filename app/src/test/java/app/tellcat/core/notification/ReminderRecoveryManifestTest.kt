package app.tellcat.core.notification

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * That the app is still told when its alarms have been thrown away.
 *
 * `ReminderSchedulerTest` proves `reconcile` does the right thing. Nothing
 * proved it is ever called. The scheduler has thirteen tests and four
 * production call sites, and every one of those tests passes on a build where
 * the wiring has been removed and no reminder fires again.
 *
 * Three of the four call sites are Kotlin and would fail to compile if they
 * went. The fourth is this manifest entry, which is not code, is not
 * referenced by name from anywhere the compiler reads, and is the one that
 * matters most: `AlarmManager` keeps nothing across a restart, so without the
 * broadcast the app never learns it has promises to rebuild. Every reminder
 * set before a reboot is simply gone, and the app is not told, and neither is
 * the user. `docs/decisions.md` D-005 calls that the most severe failure this
 * product has.
 *
 * So it is asserted here, cheaply, in a plain JVM test. The same shape as
 * `DeviceSettingsRouteTest.every declared package is queried in the manifest`,
 * and for the same reason: a manifest entry that silently stops existing looks
 * exactly like a feature that was never built.
 */
class ReminderRecoveryManifestTest {

    private val manifest: String = File("src/main/AndroidManifest.xml").readText()

    private val declaration: String =
        manifest.substringAfter(RECEIVER, "").substringBefore("</receiver>", "")

    @Test
    fun `the recovery receiver is declared`() {
        assertTrue(
            "$RECEIVER is missing from the manifest. Nothing will rebuild alarms " +
                "after a restart, and no other test will notice.",
            RECEIVER in manifest
        )
    }

    @Test
    fun `it listens for every event that discards an alarm`() {
        RECOVERY_ACTIONS.forEach { action ->
            assertTrue(
                "$action is missing from the recovery receiver. ${WHY[action]}",
                """<action android:name="$action" />""" in declaration
            )
        }
    }

    @Test
    fun `it is exported, because the sender is the system`() {
        // A receiver for system broadcasts that is not exported is never
        // called, and nothing at build time or run time says so.
        assertTrue(
            "The recovery receiver is not exported, so the system cannot deliver to it.",
            """android:exported="true"""" in declaration
        )
    }

    @Test
    fun `the boot permission is held`() {
        // BOOT_COMPLETED is not delivered without it, however the receiver is
        // declared.
        assertTrue(
            "RECEIVE_BOOT_COMPLETED is not declared, so the boot broadcast never arrives.",
            "android.permission.RECEIVE_BOOT_COMPLETED" in manifest
        )
    }

    private companion object {

        const val RECEIVER = ".core.notification.ReminderRecoveryReceiver"

        /**
         * Why each one is here, so a future reader deleting one has to argue
         * with a sentence rather than with a list.
         */
        val WHY = mapOf(
            "android.intent.action.LOCKED_BOOT_COMPLETED" to
                "A reminder due before the first unlock after a restart has to be " +
                "set from the copy kept for the locked phone. D-079.",
            "android.intent.action.BOOT_COMPLETED" to
                "A restart discards every alarm the system was holding.",
            "android.intent.action.MY_PACKAGE_REPLACED" to
                "Updating the app cancels its alarms. This is the one that shows " +
                "up in development, and it is why a reminder set before a build " +
                "install never arrives.",
            "android.intent.action.TIME_SET" to
                "The wall clock moving changes what an alarm's time means. D-009 " +
                "measured this phone taking time from carrier NITZ several times " +
                "a day.",
            "android.intent.action.TIMEZONE_CHANGED" to
                "A reminder is a local time, so crossing a zone moves the instant " +
                "it resolves to.",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" to
                "Alarms set while the exact-alarm grant was missing are inexact. " +
                "A grant from system settings has to turn them exact. D-078."
        )

        val RECOVERY_ACTIONS = WHY.keys
    }

    /**
     * D-078. Play allows USE_EXACT_ALARM only for alarm, timer and calendar
     * apps. Declaring it on a task app is a rejection waiting to happen, and
     * SCHEDULE_EXACT_ALARM has to cover every API level in its place.
     */
    @Test
    fun `exact alarms are asked for, not claimed`() {
        assertTrue(
            "USE_EXACT_ALARM is declared. Play reserves it for alarm and calendar apps.",
            "android.permission.USE_EXACT_ALARM" !in manifest
        )
        assertTrue(
            "SCHEDULE_EXACT_ALARM is missing, so no reminder can be exact.",
            """<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />""" in manifest
        )
    }

    /**
     * D-079. Without these two, nothing of this app runs before the first unlock
     * after a restart, and a reminder due then arrives late.
     */
    @Test
    fun `the recovery and alarm receivers run before the first unlock`() {
        assertTrue(
            "The recovery receiver is not direct-boot aware.",
            """android:directBootAware="true"""" in declaration
        )

        val alarmReceiver = manifest.substringAfter(
            """android:name=".core.notification.ReminderReceiver""""
        ).substringBefore("/>")
        assertTrue(
            "The alarm receiver is not direct-boot aware, so a locked phone says nothing.",
            """android:directBootAware="true"""" in alarmReceiver
        )
    }
}
