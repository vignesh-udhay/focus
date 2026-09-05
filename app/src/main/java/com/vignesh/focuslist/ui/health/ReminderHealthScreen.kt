package com.vignesh.focuslist.ui.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.CheckState
import com.vignesh.focuslist.core.domain.DeviceRestriction
import com.vignesh.focuslist.core.domain.HealthCheck
import com.vignesh.focuslist.core.domain.ReminderDelivery
import com.vignesh.focuslist.core.domain.ReminderHealth
import com.vignesh.focuslist.core.domain.ReminderHealthState
import com.vignesh.focuslist.core.notification.TestReminder
import com.vignesh.focuslist.ui.component.durationLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * Whether the app can be relied on, said plainly.
 *
 * Four states over the same three rows, from the `reminder/Health *` frames.
 * The headline changes; the rows do not, because a user who came here because
 * a reminder was late still needs to see what the app can and cannot do.
 *
 * The state is decided in `ReminderHealth`, not here. This screen renders an
 * answer it does not compute, which is what keeps the rule that a recorded
 * failure outranks a granted permission in one testable place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderHealthScreen(
    viewModel: ReminderHealthViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()

    var isTestScheduled by rememberSaveable { mutableStateOf(false) }

    // Permissions change while the user is in Settings and there is nothing to
    // observe, so the screen asks again every time it comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminder_health_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.reminder_health_back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
        ) {
            Headline(state, health?.restriction)

            health?.checks?.forEach { (check, checkState) ->
                CheckRow(check = check, state = checkState, restriction = health?.restriction)
            }

            // Absent while checking rather than disabled. A button that cannot
            // be pressed yet invites pressing it.
            if (state != ReminderHealthState.Checking) {
                PrimaryAction(
                    health = health,
                    onTest = {
                        TestReminder.schedule(context)
                        isTestScheduled = true
                    }
                )

                Text(
                    text = stringResource(R.string.reminder_health_last_checked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = FocuslistSpacing.xs)
                )
            }
        }
    }

    if (isTestScheduled) {
        AlertDialog(
            onDismissRequest = { isTestScheduled = false },
            title = { Text(stringResource(R.string.reminder_test_scheduled_title)) },
            text = { Text(stringResource(R.string.reminder_test_scheduled_body)) },
            confirmButton = {
                TextButton(onClick = { isTestScheduled = false }) {
                    Text(stringResource(R.string.reminder_test_scheduled_done))
                }
            }
        )
    }
}

/**
 * The band at the top, which is the whole message.
 *
 * Coloured by severity rather than decorated with it. A user glancing at this
 * screen should know the answer before reading a word, and the words are there
 * for the ones who need to act.
 */
@Composable
private fun Headline(state: ReminderHealthState, restriction: DeviceRestriction?) {
    val (container, content) = when (state) {
        is ReminderHealthState.Missed, is ReminderHealthState.ActionNeeded ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        else ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FocuslistSpacing.xs)
    ) {
        Column(
            modifier = Modifier.padding(FocuslistSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)
        ) {
            if (state == ReminderHealthState.Checking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(IndicatorSize))

                    Text(
                        text = stringResource(R.string.reminder_health_checking_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Text(
                    text = stringResource(R.string.reminder_health_checking_body),
                    style = MaterialTheme.typography.bodyMedium
                )

                return@Column
            }

            Text(
                text = stringResource(state.label),
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = stateTitle(state),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.semantics { heading() }
            )

            stateBody(state, restriction)?.let { body ->
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** One of the three things that has to be true, and whether it is. */
@Composable
private fun CheckRow(
    check: HealthCheck,
    state: CheckState,
    restriction: DeviceRestriction?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm),
            modifier = Modifier.padding(FocuslistSpacing.sm)
        ) {
            Badge(state)

            Column {
                Text(
                    text = stringResource(check.label(restriction)),
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = stringResource(check.detail(state, restriction)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A tick or a warning, in a circle.
 *
 * The badge is the only thing on the row a person scanning will read, so it
 * carries the state on its own rather than relying on the text beside it.
 */
@Composable
private fun Badge(state: CheckState) {
    val container = when (state) {
        CheckState.Ok -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        color = container,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.size(BadgeSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (state == CheckState.Ok) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    // The row's own text says what passed.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(BadgeIconSize)
                )
            } else {
                Text(
                    text = WarningMark,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * The one thing to do next.
 *
 * A healthy app offers the test. An unhealthy one offers the settings screen
 * for whatever is wrong, most severe first, because a screen with three
 * buttons is a screen that has not decided what the user should do.
 */
@Composable
private fun PrimaryAction(health: ReminderHealth?, onTest: () -> Unit) {
    val context = LocalContext.current
    val checks = health ?: return

    // The same ordering the headline uses, taken from the same place, so the
    // sentence and the button can never point at different problems.
    val (label, action) = when (checks.firstFailing) {
        HealthCheck.Notifications ->
            R.string.reminder_health_open_notifications to { context.openNotificationSettings() }

        HealthCheck.ExactAlarms ->
            R.string.reminder_health_open_alarms to { context.openExactAlarmSettings() }

        HealthCheck.BackgroundWork ->
            R.string.reminder_health_open_battery to { context.openBatterySettings() }

        null -> R.string.reminder_health_test to onTest
    }

    Button(
        onClick = action,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FocuslistSpacing.sm)
    ) {
        Text(stringResource(label))
    }
}

// --- what each state says --------------------------------------------------

private val ReminderHealthState.label: Int
    get() = when (this) {
        ReminderHealthState.Checking -> R.string.reminder_health_checking_label
        ReminderHealthState.Ready -> R.string.reminder_health_ready_label
        is ReminderHealthState.ActionNeeded -> R.string.reminder_health_action_label
        is ReminderHealthState.Missed -> R.string.reminder_health_missed_label
    }

/**
 * The headline.
 *
 * A missed reminder says how late it was, because that is the fact the user
 * came to check. Action needed names the manufacturer, so the sentence matches
 * the phone the user is holding.
 *
 * Composable so the lateness is worded by the same `durationLabel` the rest of
 * the app uses. Two places phrasing a duration differently is how "45 min" and
 * "45 minutes" end up on the same screen.
 */
@Composable
private fun stateTitle(state: ReminderHealthState): String = when (state) {
    ReminderHealthState.Checking -> stringResource(R.string.reminder_health_checking_label)
    ReminderHealthState.Ready -> stringResource(R.string.reminder_health_ready_title)

    is ReminderHealthState.ActionNeeded -> when (state.cause) {
        HealthCheck.Notifications ->
            stringResource(R.string.reminder_health_no_notifications_title)

        HealthCheck.ExactAlarms ->
            stringResource(R.string.reminder_health_no_exact_title)

        // The frame's own sentence, now said only when it is the true cause.
        HealthCheck.BackgroundWork ->
            stringResource(R.string.reminder_restriction_action_title, Build.MANUFACTURER)
    }

    is ReminderHealthState.Missed ->
        if (state.delivery.lateness >= Duration.ofMinutes(1)) {
            stringResource(
                R.string.reminder_health_missed_title,
                durationLabel(state.delivery.lateness.toMinutes().toInt()).spoken
            )
        } else {
            // On time, and nobody was told. A different failure needing a
            // different sentence: nothing was late, something was lost.
            stringResource(R.string.reminder_health_missed_silent_title)
        }
}

@Composable
private fun stateBody(state: ReminderHealthState, restriction: DeviceRestriction?): String? =
    when (state) {
        ReminderHealthState.Checking -> stringResource(R.string.reminder_health_checking_body)
        ReminderHealthState.Ready -> stringResource(R.string.reminder_health_ready_body)

        is ReminderHealthState.ActionNeeded -> when (state.cause) {
            HealthCheck.Notifications ->
                stringResource(R.string.reminder_health_no_notifications_body)

            HealthCheck.ExactAlarms ->
                stringResource(R.string.reminder_health_no_exact_body)

            HealthCheck.BackgroundWork -> stringResource(
                R.string.reminder_restriction_action_body,
                stringResource(restriction?.label ?: R.string.reminder_health_check_background)
            )
        }

        is ReminderHealthState.Missed -> describe(state.delivery)
    }

/** "Take medication, due 3:30 PM. Arrived 4:11 PM." */
@Composable
private fun describe(delivery: ReminderDelivery): String {
    val context = LocalContext.current

    return if (delivery.lateness >= Duration.ofMinutes(1)) {
        stringResource(
            R.string.reminder_health_missed_body,
            delivery.taskTitle,
            context.formatTime(delivery.scheduledWallAt),
            context.formatTime(delivery.arrivedWallAt)
        )
    } else {
        stringResource(
            R.string.reminder_health_missed_silent_body,
            delivery.taskTitle,
            context.formatTime(delivery.scheduledWallAt)
        )
    }
}

private fun HealthCheck.label(restriction: DeviceRestriction?): Int = when (this) {
    HealthCheck.Notifications -> R.string.reminder_health_check_notifications
    HealthCheck.ExactAlarms -> R.string.reminder_health_check_exact
    // The manufacturer's own word where there is one, because that is the word
    // the user is looking for in their settings app.
    HealthCheck.BackgroundWork -> restriction?.label ?: R.string.reminder_health_check_background
}

private fun HealthCheck.detail(state: CheckState, restriction: DeviceRestriction?): Int = when {
    this == HealthCheck.BackgroundWork && state != CheckState.Ok ->
        R.string.reminder_restriction_may_block

    this == HealthCheck.BackgroundWork -> R.string.reminder_health_no_restrictions
    state == CheckState.Ok -> R.string.reminder_health_allowed
    else -> R.string.reminder_health_not_allowed
}

private val DeviceRestriction.label: Int
    get() = when (this) {
        DeviceRestriction.SleepStandby -> R.string.reminder_restriction_sleep_standby
        DeviceRestriction.Autostart -> R.string.reminder_restriction_autostart
        DeviceRestriction.SleepingApps -> R.string.reminder_restriction_sleeping_apps
        DeviceRestriction.ProtectedApps -> R.string.reminder_restriction_protected_apps
    }

// --- where each button goes ------------------------------------------------

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun Context.openExactAlarmSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }

    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * The app's own battery screen.
 *
 * Not the manufacturer's, because every manufacturer buries theirs somewhere
 * different and an intent that resolves on one skin crashes on another. This
 * one exists everywhere, and it is where the OEM controls usually sit. Sending
 * a user to a screen that exists beats guessing at one that might not.
 */
private fun Context.openBatterySettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun Context.formatTime(at: Instant): String =
    DateFormat.getTimeFormat(this).format(Date.from(at))

private val BadgeSize = 40.dp

private val BadgeIconSize = 24.dp

private val IndicatorSize = 24.dp

/** Drawn rather than iconised, so the row needs no second asset. */
private const val WarningMark = "!"
