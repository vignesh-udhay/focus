package app.tellcat.ui.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.notification.canPostNotifications

/**
 * What has to be true for a reminder to arrive, asked at the moment one is set.
 *
 * Two permissions, and they fail differently. Without `POST_NOTIFICATIONS` the
 * alarm fires and the notification is dropped, so nothing appears. Without
 * permission to schedule an exact alarm the notification appears, late. Both
 * are the failure `PRODUCT.md` calls the most severe in this product, so both
 * are asked about here rather than left to be discovered.
 *
 * Asked at the first reminder, not at launch. `POST_NOTIFICATIONS` is a
 * question only answerable in context, and the context is a promise the user
 * has just made to themselves. The app already asks this way for focus
 * estimates; a user who never opens Focus was never asked at all, and every
 * reminder they set would have been silent.
 *
 * [isRequested] is the whole of what puts this on screen, the way a running
 * session is the whole of what shows Focus.
 *
 * The two are handled in order and only one has a screen. Notifications get
 * Android's own dialog, which explains itself and needs nothing from us. Exact
 * alarms get the sheet, because Android shows no dialog for that one at all:
 * it is a settings page the user has to be walked to.
 *
 * @param canScheduleExact read as a function rather than a value, because the
 * answer changes while this is on screen: the user leaves for settings, grants
 * it, and comes back.
 * @param onPermissionGranted run when either permission is newly given, so
 * anything already owed is re-reconciled against what the app can now do.
 */
@Composable
fun ReminderPermissionGate(
    isRequested: Boolean,
    canScheduleExact: () -> Boolean,
    onPermissionGranted: () -> Unit,
    onDone: () -> Unit
) {
    if (!isRequested) return

    val context = LocalContext.current

    // Answered on the way in, so the screen below is never shown to argue for
    // something that has already been granted.
    var isNotificationAsked by rememberSaveable { mutableStateOf(context.canPostNotifications()) }
    var isExactGranted by rememberSaveable { mutableStateOf(canScheduleExact()) }

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Refusing is not argued with. A screen that appeared to make the case
        // again would be the app not listening, and what a refusal costs
        // belongs on the Phase 2 health screen, said once rather than at every
        // reminder.
        //
        // Being granted does need acting on. An alarm that fired while the app
        // could not post left its reminder owed and unannounced, and nothing
        // writes to storage here, so nothing would otherwise notice until the
        // next process start.
        if (granted) onPermissionGranted()
        isNotificationAsked = true
    }

    val settings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Settings reports nothing about what was changed there, so the grant
        // itself is what gets read back.
        isExactGranted = canScheduleExact()

        // Same reason as above: what is owed has not changed, but what the app
        // can promise about it has, so the alarms are rebuilt on the new
        // answer rather than left as the degraded ones already set.
        if (isExactGranted) onPermissionGranted()
    }

    // **Asked again every time the app comes back**, not only when Settings
    // returns through the launcher above. The grant is often made somewhere
    // else: App info, or Android's own Alarms & reminders list. In either, this
    // screen stayed up asking for a permission that was already granted. It is
    // also the safe answer if a settings page reports back before the user has
    // touched the switch, which OxygenOS appears to do: back from that page
    // there went to the home screen rather than to the app. Found on a OnePlus
    // 8T on 2026-10-10.
    LifecycleResumeEffect(Unit) {
        if (!isNotificationAsked && context.canPostNotifications()) {
            isNotificationAsked = true
        }
        if (!isExactGranted && canScheduleExact()) {
            isExactGranted = true
            onPermissionGranted()
        }
        onPauseOrDispose {}
    }

    LaunchedEffect(isNotificationAsked, isExactGranted) {
        when {
            !isNotificationAsked ->
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)

            isExactGranted -> onDone()
        }
    }

    if (!isNotificationAsked || isExactGranted) return

    // Back, the scrim and a swipe all answer Not now, through the sheet.
    ReminderPermissionSheet(
        onOpenSettings = { settings.launch(context.exactAlarmSettingsIntent()) },
        onNotNow = onDone
    )
}

/**
 * The ask, as a bottom sheet over the reminder just saved. D-107.
 *
 * Laid out like the full-screen alarm's ask, `AlarmPrimingSheet`: a title, one
 * line, and two full-width actions. The two are the same kind of moment, a
 * permission asked for in context that the user can decline, and they used to
 * look like two different apps: this one took over the whole screen with an
 * icon tile, an oversized title and three bullet points.
 *
 * One line, not a paragraph or a list: what the user gets, short enough to
 * read at a glance.
 *
 * The note under the actions says what the button will do, and it says it
 * because the honest answer is unusual. Every other permission in Android
 * arrives as a popup; this one does not, and a user who taps expecting a
 * yes/no dialog and lands in Settings has been surprised by their own app.
 *
 * Swiping it away, the scrim and Back all answer Not now. It scrolls, so at the
 * largest system font scale the buttons can still be reached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPermissionSheet(onOpenSettings: () -> Unit, onNotNow: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onNotNow,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.reminder_permission_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )

            Text(
                text = stringResource(R.string.reminder_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TellcatSpacing.xs)
                    .heightIn(min = TellcatDimensions.ActionHeight)
            ) {
                Text(stringResource(R.string.reminder_permission_open_settings))
            }

            // A text button, as in the full-screen ask and every other sheet.
            TextButton(
                onClick = onNotNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TellcatDimensions.ActionHeight)
            ) {
                Text(stringResource(R.string.reminder_permission_not_now))
            }

            Text(
                text = stringResource(R.string.reminder_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Alarms and reminders, in Android's own settings.
 *
 * The screen exists from API 31, which is the first version where an app can
 * be refused an exact alarm. Below that the intent has nowhere to go, and
 * `canScheduleExact` already answers true, so nothing reaches here.
 *
 * Not the manufacturer's battery screens. Those are a different problem with a
 * different route per manufacturer, and they belong to Phase 2. See
 * `docs/decisions.md`, D-009, for why they will be needed anyway.
 */
private fun Context.exactAlarmSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }
