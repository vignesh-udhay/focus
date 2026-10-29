package app.tellcat.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.tellcat.core.notification.FollowUpChannelId
import app.tellcat.core.notification.ensureFollowUpChannel
import app.tellcat.core.notification.followUpChannelOn

/**
 * Whether follow-ups are on, D-108, read again each time Settings returns to
 * the front. Android offers nothing to observe, so without the re-read the row
 * would still say On after the user turned the channel off.
 */
@Composable
internal fun rememberFollowUpsOn(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(context.followUpChannelOn()) }

    LifecycleResumeEffect(Unit) {
        on = context.followUpChannelOn()
        onPauseOrDispose {}
    }

    return on
}

/**
 * Android's page for the "Reminders again" channel, which holds the follow-up's
 * switch. D-110. The channel is made first, so the page is the channel's and not
 * the app's.
 */
internal fun Context.openFollowUpChannelSettings() {
    ensureFollowUpChannel()
    startActivity(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, FollowUpChannelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
