package com.vignesh.focuslist.core.notification

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.vignesh.focuslist.core.domain.DeviceRestriction

/**
 * Where to send a user whose phone may be eating reminders.
 *
 * None of these screens is part of Android. They belong to software each
 * manufacturer wrote for itself, they are not documented, and they are renamed
 * and moved between releases of the same skin. So this file is a list of
 * guesses, ordered best first, and everything around it is built on the
 * assumption that a guess will be wrong.
 *
 * Two things make a wrong guess harmless. Every candidate is resolved against
 * the package manager before it is launched, and the launch itself is guarded,
 * because an activity can resolve and still refuse to start. Whatever happens,
 * the user lands on the app's own settings page, which exists on every device.
 * Crashing a person's phone while telling them their reminders are at risk
 * would be a poor joke.
 *
 * That guard is not theoretical. It is the whole story on modern OxygenOS, and
 * `docs/decisions.md` D-010 records what was measured there.
 */

/**
 * The manufacturer screens the app is allowed to see.
 *
 * From Android 11 a package the app has not declared is invisible to it, and
 * `resolveActivity` returns null for an activity that is plainly installed. So
 * these names appear twice: here, and in `<queries>` in the manifest. A
 * candidate whose package is missing from the manifest fails silently, always
 * falling through to the generic screen, which is why a test checks the two
 * lists agree.
 */
internal val QueriedSettingsPackages = listOf(
    "com.miui.securitycenter",
    "com.coloros.safecenter",
    "com.coloros.oppoguardelf",
    "com.oppo.safe",
    "com.oneplus.security",
    "com.samsung.android.lool",
    "com.samsung.android.sm",
    "com.huawei.systemmanager"
)

/**
 * Candidate screens for a restriction, best first.
 *
 * Several per vendor because one vendor is several vendors over time. OPPO's
 * safe centre was renamed when ColorOS absorbed it, OnePlus had its own
 * security app before the merge, and Samsung moved its battery screen one
 * package deeper. A phone matches at most one of them, and old handsets are
 * the ones most likely to need this screen at all.
 *
 * **There is no entry for ColorOS or OxygenOS 12 and later.** Those skins
 * renamed everything to `com.oplus`, and every replacement screen is guarded
 * by `oplus.permission.OPLUS_COMPONENT_SAFE`, which is `signature` level. The
 * screens resolve, and starting one throws. No third-party app can open them,
 * so there is nothing here to try. D-010 has the measurement.
 */
internal fun candidatesFor(restriction: DeviceRestriction?): List<SettingsScreen> =
    when (restriction) {
        // MIUI and HyperOS. Autostart is the one that matters: without it the
        // app is not merely delayed, it is not started.
        DeviceRestriction.Autostart -> listOf(
            SettingsScreen(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            SettingsScreen("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
        )

        // ColorOS and OxygenOS before the merge, for handsets that never took
        // the update. They are also the handsets most likely to need it.
        DeviceRestriction.SleepStandby -> listOf(
            SettingsScreen(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            SettingsScreen(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            SettingsScreen(
                "com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
            ),
            SettingsScreen(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            SettingsScreen(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
        )

        // One UI. Sleeping apps live inside Device care's battery page.
        DeviceRestriction.SleepingApps -> listOf(
            SettingsScreen(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            ),
            SettingsScreen(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
            SettingsScreen(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        )

        // EMUI and MagicOS.
        DeviceRestriction.ProtectedApps -> listOf(
            SettingsScreen(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            SettingsScreen(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            ),
            SettingsScreen(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        )

        // A device with no known restriction has no screen worth guessing at.
        null -> emptyList()
    }

/**
 * One manufacturer settings screen, as two strings.
 *
 * Deliberately not a [ComponentName]. The routing table is the part of this
 * file worth testing and the part most likely to be edited wrongly, and every
 * Android type in a plain unit test is a stub that throws. Keeping the table
 * free of them means the test can be an ordinary one.
 */
internal data class SettingsScreen(val packageName: String, val activity: String)

/**
 * Opens the manufacturer's own screen if the phone will allow it, and the
 * app's Android settings page if not.
 *
 * The fallback is not a failure case, it is the common case. Most phones ship
 * no such screen; the newest OPPO and OnePlus ship one and refuse to open it;
 * and on all of them the generic page is a fair destination, because it is
 * where Android keeps its own battery controls and it carries a Battery usage
 * entry one tap away.
 */
fun Context.openBackgroundWorkSettings(restriction: DeviceRestriction?) {
    for (screen in resolvableScreens(restriction)) {
        try {
            startActivity(screen.intent())
            Log.d(LogTag, "Background work settings: $screen")
            return
        } catch (e: ActivityNotFoundException) {
            Log.d(LogTag, "Resolved but would not start: $screen", e)
        } catch (e: SecurityException) {
            // The OxygenOS case, and probably not only that one. Logged rather
            // than counted, because the user is about to land somewhere useful
            // either way.
            Log.d(LogTag, "Resolved but this app may not start it: $screen", e)
        }
    }

    startActivity(appSettingsIntent())
}

/**
 * The candidates this device actually has, in the table's order.
 *
 * Split out from the launch for two callers. A test on real hardware can ask
 * what the app would do without opening anything, and the health screen asks
 * whether there is a manufacturer screen to name before it promises one on a
 * button.
 */
internal fun Context.resolvableScreens(restriction: DeviceRestriction?): List<SettingsScreen> =
    candidatesFor(restriction).filter { packageManager.resolveActivity(it.intent(), 0) != null }

private fun SettingsScreen.intent(): Intent =
    Intent()
        .setComponent(ComponentName(packageName, activity))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The app's own page in Android settings. Present on every device. */
fun Context.openAppSettings() {
    startActivity(appSettingsIntent())
}

internal fun Context.appSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
