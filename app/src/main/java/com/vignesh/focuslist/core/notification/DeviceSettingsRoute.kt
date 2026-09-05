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
 */
internal fun candidatesFor(restriction: DeviceRestriction?): List<SettingsScreen> =
    when (restriction) {
        // MIUI and HyperOS. Autostart is the one that matters: without it the
        // app is not merely delayed, it is not started.
        DeviceRestriction.Autostart -> listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings"
        )

        // ColorOS and OxygenOS, in the order the packages appeared.
        DeviceRestriction.SleepStandby -> listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.coloros.oppoguardelf" to "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
        )

        // One UI. Sleeping apps live inside Device care's battery page.
        DeviceRestriction.SleepingApps -> listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity"
        )

        // EMUI and MagicOS.
        DeviceRestriction.ProtectedApps -> listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
        )

        // A device with no known restriction has no screen worth guessing at.
        null -> emptyList()
    }.map { (packageName, activity) -> SettingsScreen(packageName, activity) }

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
 * Opens the manufacturer's own screen if one is there, and the app's Android
 * settings page if not.
 *
 * The fallback is not a failure case, it is the common case: most phones ship
 * no such screen, and on those the generic page is the right answer anyway,
 * since that is where Android keeps its own battery controls.
 *
 * [SecurityException] is caught alongside the expected miss because a settings
 * activity can be visible to the package manager and still not exported to
 * this app. That combination is only reachable on hardware nobody has to hand,
 * so it is caught rather than reasoned about.
 */
fun Context.openBackgroundWorkSettings(restriction: DeviceRestriction?) {
    for (component in resolvableScreens(restriction)) {
        try {
            startActivity(intentFor(component))
            Log.d(LogTag, "Background work settings: ${component.flattenToShortString()}")
            return
        } catch (e: ActivityNotFoundException) {
            Log.d(LogTag, "Resolved but would not start: ${component.flattenToShortString()}", e)
        } catch (e: SecurityException) {
            Log.d(LogTag, "Resolved but not exported: ${component.flattenToShortString()}", e)
        }
    }

    startActivity(appSettingsIntent())
}

/**
 * The candidates this device actually has, in the table's order.
 *
 * Split out from the launch so a test on real hardware can ask what the app
 * would do without opening anything. On a device with none of these screens
 * the answer is an empty list, which is the whole point: nothing is launched
 * on a guess.
 */
internal fun Context.resolvableScreens(restriction: DeviceRestriction?): List<ComponentName> =
    candidatesFor(restriction)
        .map { ComponentName(it.packageName, it.activity) }
        .filter { packageManager.resolveActivity(intentFor(it), 0) != null }

private fun intentFor(component: ComponentName): Intent =
    Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The app's own page in Android settings. Present on every device. */
fun Context.openAppSettings() {
    startActivity(appSettingsIntent())
}

internal fun Context.appSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
