package app.tellcat.ui.task

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.domain.AlarmMode
import app.tellcat.core.notification.AlarmChannelId
import app.tellcat.core.notification.currentAlarmMode
import app.tellcat.ui.alarm.AlarmScreen
import java.time.LocalDateTime

/**
 * "Ring like an alarm", in the reminder editor. `docs/decisions.md` D-102.
 *
 * A switch, off by default, and under it one line saying how this reminder will
 * actually ring on this phone right now, before the user relies on it: full
 * screen, as a banner, or as a normal reminder when the Alarms channel is off.
 * The line is read off the device each time the editor comes back to the
 * front, so returning from settings with the grant updates it.
 *
 * Turning it on without the full-screen grant opens [AlarmPrimingSheet] (R-013).
 * The switch stays on whatever the answer: without the grant it still rings,
 * as a banner, until answered, and the line says so.
 */
@Composable
internal fun ReminderAlarmSwitch(
    ringsLikeAlarm: Boolean,
    onRingsLikeAlarmChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mode = rememberAlarmMode()
    var isPrimingOpen by remember { mutableStateOf(false) }

    // Back from settings with the grant: the sheet has done its work.
    LaunchedEffect(mode) {
        if (mode == AlarmMode.FullScreen) isPrimingOpen = false
    }

    val label = stringResource(R.string.task_reminder_alarm)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TellcatDimensions.TouchTargetMin)
                // The whole row is the switch, as Material's list-item switch
                // is, and TalkBack reads it as one.
                .toggleable(
                    value = ringsLikeAlarm,
                    role = Role.Switch,
                    onValueChange = { on ->
                        onRingsLikeAlarmChange(on)
                        if (on && mode == AlarmMode.Banner) isPrimingOpen = true
                    }
                )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            // Drawn only; the row carries the interaction and the semantics.
            Switch(checked = ringsLikeAlarm, onCheckedChange = null)
        }

        if (ringsLikeAlarm) {
            Text(
                text = stringResource(mode.promise),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (mode) {
                AlarmMode.Banner -> OutlinedButton(
                    onClick = { isPrimingOpen = true },
                    modifier = Modifier.padding(top = TellcatSpacing.xs)
                ) {
                    Text(stringResource(R.string.task_reminder_alarm_allow))
                }

                AlarmMode.NoExactAlarms -> OutlinedButton(
                    onClick = { context.openExactAlarmSettings() },
                    modifier = Modifier.padding(top = TellcatSpacing.xs)
                ) {
                    Text(stringResource(R.string.task_reminder_alarm_exact_allow))
                }

                AlarmMode.PlainReminder -> OutlinedButton(
                    onClick = { context.openAlarmChannelSettings() },
                    modifier = Modifier.padding(top = TellcatSpacing.xs)
                ) {
                    Text(stringResource(R.string.task_reminder_alarm_channel_open))
                }

                AlarmMode.FullScreen -> Unit
            }
        }
    }

    if (isPrimingOpen) {
        AlarmPrimingSheet(
            onAllow = { context.openFullScreenSettings() },
            onDismiss = { isPrimingOpen = false }
        )
    }
}

/** The editor's one line for each way an alarm can ring. */
private val AlarmMode.promise: Int
    get() = when (this) {
        AlarmMode.FullScreen -> R.string.task_reminder_alarm_full_screen
        AlarmMode.Banner -> R.string.task_reminder_alarm_banner
        AlarmMode.NoExactAlarms -> R.string.task_reminder_alarm_no_exact
        AlarmMode.PlainReminder -> R.string.task_reminder_alarm_off
    }

/**
 * Asking for full screen, with a picture of what it is for. R-013, D-102.
 *
 * The competitor study's best ask was Todoist's: show the alarm screen, open
 * the exact settings toggle, and come back to it on. The picture is the real
 * [AlarmScreen], laid out at phone size and scaled down, so it cannot drift
 * from what the user will see.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmPrimingSheet(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val pictureDescription = stringResource(R.string.alarm_priming_picture)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.alarm_priming_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(TellcatDimensions.AlarmPreviewWidth, TellcatDimensions.AlarmPreviewHeight)
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = TellcatDimensions.AlarmPreviewBorder,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.large
                    )
                    // A picture, not a second alarm: one description, no controls.
                    .clearAndSetSemantics { contentDescription = pictureDescription }
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(
                            TellcatDimensions.AlarmPreviewSourceWidth,
                            TellcatDimensions.AlarmPreviewSourceHeight
                        )
                        .graphicsLayer {
                            val scale = TellcatDimensions.AlarmPreviewWidth / TellcatDimensions.AlarmPreviewSourceWidth
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    AlarmScreen(
                        title = stringResource(R.string.alarm_priming_sample_title),
                        notes = null,
                        ringsAt = PreviewTime,
                        today = PreviewTime.toLocalDate(),
                        onSnooze = {},
                        onStop = {}
                    )
                }
            }

            Text(
                text = stringResource(R.string.alarm_priming_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onAllow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TellcatDimensions.ActionHeight)
            ) {
                Text(stringResource(R.string.alarm_priming_allow))
            }
            // A text button, as every lesser action beneath a filled one is in
            // the app's sheets: Clear, Doesn't repeat, End session.
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TellcatDimensions.ActionHeight)
            ) {
                Text(stringResource(R.string.alarm_priming_not_now))
            }
        }
    }
}

/** A morning alarm for the picture: the time people most often set one. */
private val PreviewTime: LocalDateTime = LocalDateTime.of(2026, 1, 1, 7, 0)

/**
 * How an alarm-style reminder rings on this phone, read again each time the
 * screen returns to the front, so a grant made in settings shows at once.
 */
@Composable
internal fun rememberAlarmMode(): AlarmMode {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(context.currentAlarmMode()) }

    LifecycleResumeEffect(Unit) {
        mode = context.currentAlarmMode()
        onPauseOrDispose {}
    }

    return mode
}

/**
 * The exact toggle for full-screen alerts, from Android 14. Below it the
 * permission is granted at install and there is nothing to open, so the app's
 * notification settings stand in.
 */
internal fun Context.openFullScreenSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * "Alarms & reminders" for Tellcat. Only Android 12 and later can refuse it,
 * so below that the mode that offers this button never comes up.
 */
private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** The Alarms channel's own settings page. */
internal fun Context.openAlarmChannelSettings() {
    startActivity(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmChannelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
