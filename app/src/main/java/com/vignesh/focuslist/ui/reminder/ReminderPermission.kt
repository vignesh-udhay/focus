package com.vignesh.focuslist.ui.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.notification.canPostNotifications

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
 * alarms get the screen, because Android shows no dialog for that one at all:
 * it is a settings page the user has to be walked to, which is exactly what
 * `reminder/Precise Permission — Clean Slate` draws.
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

    LaunchedEffect(isNotificationAsked, isExactGranted) {
        when {
            !isNotificationAsked ->
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)

            isExactGranted -> onDone()
        }
    }

    if (!isNotificationAsked || isExactGranted) return

    // The frame draws Not now, and back is the same answer by another route.
    BackHandler { onDone() }

    ReminderPermissionScreen(
        onOpenSettings = { settings.launch(context.exactAlarmSettingsIntent()) },
        onNotNow = onDone
    )
}

/**
 * The screen, as `reminder/Precise Permission — Clean Slate` lays it out.
 *
 * An icon, the ask, the reasons, and two actions: the settings page, and a way
 * to decline. The note under them says what the button will do, and it says it
 * because the honest answer is unusual. Every other permission in Android
 * arrives as a popup; this one does not, and a user who taps expecting a
 * yes/no dialog and lands in Settings has been surprised by their own app.
 *
 * The reasons are a card rather than prose because they are the part anyone
 * actually reads, and three short lines get read where a paragraph does not.
 *
 * The content scrolls and the actions do not. At the largest system font scale
 * the reasons alone are taller than the screen, and a rationale whose button
 * has fallen off the bottom cannot be agreed to.
 */
@Composable
private fun ReminderPermissionScreen(onOpenSettings: () -> Unit, onNotNow: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = FocuslistSpacing.lg)
                .padding(bottom = FocuslistSpacing.lg)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = FocuslistSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.md)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(IconWrapSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            // A clock, as the frame draws it. The screen is
                            // about when a reminder arrives, not whether it is
                            // allowed to make a sound, and a bell here would
                            // point at the other permission.
                            painter = painterResource(R.drawable.ic_schedule),
                            // The heading below says what this is about.
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(IconSize)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.reminder_permission_title),
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = stringResource(R.string.reminder_permission_body),
                    style = MaterialTheme.typography.bodyLarge
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(FocuslistSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.reminder_permission_why),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )

                        listOf(
                            R.string.reminder_permission_reason_closed,
                            R.string.reminder_permission_reason_idle,
                            R.string.reminder_permission_reason_forget
                        ).forEach { reason ->
                            Text(
                                text = stringResource(
                                    R.string.reminder_permission_bullet,
                                    stringResource(reason)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FocuslistSpacing.md)
            ) {
                Text(stringResource(R.string.reminder_permission_open_settings))
            }

            TextButton(
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reminder_permission_not_now))
            }

            Text(
                text = stringResource(R.string.reminder_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = FocuslistSpacing.xs)
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

private val IconWrapSize = 88.dp

private val IconSize = 36.dp
