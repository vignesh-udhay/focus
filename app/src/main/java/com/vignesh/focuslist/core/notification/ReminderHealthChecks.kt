package com.vignesh.focuslist.core.notification

import android.content.Context
import android.os.Build
import com.vignesh.focuslist.core.domain.CheckState
import com.vignesh.focuslist.core.domain.DeviceRestriction

/**
 * What the platform will admit about whether a reminder can arrive.
 *
 * Facts only. Whether a fact is worth telling the user is decided in
 * `ReminderHealth`, because that answer depends on the delivery record as well
 * as on the platform, and `docs/decisions.md` D-009 is the case where the two
 * disagree.
 *
 * An interface so the health screen can be driven from a test without an
 * Android device, and so a fake can produce the states this one cannot be made
 * to produce on demand.
 */
interface ReminderHealthChecks {

    /** Whether the app may post at all. */
    fun notifications(): CheckState

    /** Whether the system will accept an exact alarm. */
    fun exactAlarms(): CheckState

    /**
     * The manufacturer power feature this device ships, or null.
     *
     * What the device is capable of doing, not what it is doing. Nothing here
     * can read the setting.
     */
    fun restriction(): DeviceRestriction?
}

/**
 * The real checks.
 *
 * Two of the three are genuine questions to the system. The third is a guess
 * from the manufacturer name, and it is a guess because there is nothing else
 * to go on: none of these features is exposed by any API, and the one thing
 * that is exposed, `isIgnoringBatteryOptimizations()`, was measured in D-009
 * to change an alarm's flags and leave its delivery window untouched. An
 * allowlist the app can join and still be throttled is not evidence of health,
 * so it is not consulted here.
 */
class AndroidReminderHealthChecks(
    private val context: Context,
    private val alarms: ReminderAlarms,
    private val manufacturer: String = Build.MANUFACTURER
) : ReminderHealthChecks {

    override fun notifications(): CheckState =
        if (context.canPostNotifications()) CheckState.Ok else CheckState.Blocked

    override fun exactAlarms(): CheckState =
        if (alarms.canScheduleExact()) CheckState.Ok else CheckState.Blocked

    override fun restriction(): DeviceRestriction? = restrictionFor(manufacturer)
}

/**
 * Which feature a manufacturer ships, if any.
 *
 * A lookup rather than a rule, because there is no rule: these are product
 * decisions by unrelated companies. The list holds the vendors whose features
 * are known to delay alarms, and every other device is treated as fine, which
 * is the honest default. A false warning on a Pixel would teach its owner that
 * this screen is noise.
 *
 * Matched case-insensitively on a prefix, because `Build.MANUFACTURER` is not
 * a controlled vocabulary: it reads "Xiaomi" on some builds and "xiaomi" on
 * others, and sub-brands ship their parent's software under their own name.
 */
internal fun restrictionFor(manufacturer: String): DeviceRestriction? {
    val name = manufacturer.trim().lowercase()

    return when {
        // ColorOS and OxygenOS. The family D-009 was measured on, where
        // OplusHansManager was observed freezing the process around every
        // broadcast.
        name.startsWith("oneplus") ||
            name.startsWith("oppo") ||
            name.startsWith("realme") -> DeviceRestriction.SleepStandby

        // MIUI and HyperOS. Autostart is off by default, which is the whole
        // problem: a reminder app that was never granted it cannot wake.
        name.startsWith("xiaomi") ||
            name.startsWith("redmi") ||
            name.startsWith("poco") -> DeviceRestriction.Autostart

        name.startsWith("samsung") -> DeviceRestriction.SleepingApps

        name.startsWith("huawei") || name.startsWith("honor") ->
            DeviceRestriction.ProtectedApps

        else -> null
    }
}
