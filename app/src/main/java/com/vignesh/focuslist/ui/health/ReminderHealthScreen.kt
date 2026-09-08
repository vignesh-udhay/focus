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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vignesh.focuslist.core.notification.displayManufacturer
import com.vignesh.focuslist.core.notification.openAppSettings
import com.vignesh.focuslist.core.notification.openBackgroundWorkSettings
import com.vignesh.focuslist.core.notification.resolvableScreens
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
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
            FocuslistTopAppBar(
                title = stringResource(R.string.reminder_health_title),
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

            // One connected collection, like every other list in the app, with
            // Material's own segmented shapes and gap rather than three loose
            // cards. They were `FocuslistSpacing.sm` apart and separately
            // rounded, which read as three unrelated things rather than one
            // report. Do not hand-roll the radii; `PlanRow` carried its own copy
            // of them briefly and that was the same mistake.
            val checks = health?.checks.orEmpty()

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                // The headline grows with its copy and the rows do not, so the
                // gap between them is stated here rather than left to the
                // column's own spacing. A longer body has already eaten it once
                // and overlapped the first row by 8dp.
                modifier = Modifier.padding(top = HeadlineToChecksGap)
            ) {
                checks.forEachIndexed { index, (check, checkState) ->
                    CheckRow(
                        check = check,
                        state = checkState,
                        restriction = health?.restriction,
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = checks.size
                        )
                    )
                }
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
    // **The app colours what it knows**, which is D-021 in one line. Error is
    // for what happened and for what the app was told; the inferred restriction
    // gets the ordinary surface, because a guess gets words and a mark, not a
    // tint.
    val (container, content) = when (state) {
        is ReminderHealthState.Missed, is ReminderHealthState.ActionNeeded ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        // Neutral, and deliberately not a third colour. Tertiary was tried and
        // rejected on the render: this palette puts `tertiaryContainer` at
        // #FFD7E3 and `errorContainer` at #FFD8D6, one step apart in green, so
        // the caution and the error were indistinguishable. `reminder-health.md`
        // records that, and that the palette has three usable container
        // families rather than five.
        is ReminderHealthState.WorthChecking ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface

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

/**
 * One of the three things that has to be true, and whether it is.
 *
 * **The whole row carries the state, not a badge inside it.** `Blocked` takes
 * the error container, because the app was refused and can say so. `Warning` and
 * `Ok` take the ordinary row surface, because in both cases there is nothing the
 * app has measured going wrong.
 *
 * The row is not interactive and carries no pressed or focused state: it
 * reports, it does not navigate. `SegmentedListItem` is used for its shape and
 * colour rather than for a click it never takes.
 */
@Composable
private fun CheckRow(
    check: HealthCheck,
    state: CheckState,
    restriction: DeviceRestriction?,
    shapes: ListItemShapes
) {
    val container = when (state) {
        CheckState.Blocked -> MaterialTheme.colorScheme.errorContainer
        CheckState.Warning, CheckState.Ok -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val content = when (state) {
        CheckState.Blocked -> MaterialTheme.colorScheme.onErrorContainer
        CheckState.Warning, CheckState.Ok -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = container,
        contentColor = content,
        shape = shapes.shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm),
            modifier = Modifier.padding(FocuslistSpacing.sm)
        ) {
            StatusGlyph(state = state, onContainer = content)

            Column {
                Text(
                    text = stringResource(check.label(restriction)),
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = stringResource(check.detail(state, restriction)),
                    style = MaterialTheme.typography.bodySmall,
                    // Follows the row rather than naming `onSurfaceVariant`,
                    // which on an error container would be the one piece of
                    // text on the row not reading against its own background.
                    color = content.copy(alpha = SupportingTextAlpha)
                )
            }
        }
    }
}

/**
 * A tick, a question mark or an exclamation, in a 40dp slot and nothing else.
 *
 * **There is no container behind it**, and removing one is the point. Each row
 * used to carry a 40dp badge, and it was never doing consistent work: on a
 * `Blocked` row the badge took the error container and so did the row, so the
 * circle was invisible in the one state that matters most. Three stacked circles
 * also compete with the text they annotate, and a container inside a container
 * is the thing this design system keeps removing.
 *
 * The glyph carries the colour instead, and the exclamation reads more strongly
 * for it than it did behind a badge of its own row's colour.
 *
 * The mark is the second channel on top of the words. "May block background
 * alarms" and "Not allowed" already differ in text; this is what a person
 * scanning sees first.
 */
@Composable
private fun StatusGlyph(state: CheckState, onContainer: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(GlyphSlotSize)
    ) {
        when (state) {
            CheckState.Ok -> Icon(
                painter = painterResource(R.drawable.ic_check),
                // The row's own text says what passed.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(GlyphIconSize)
            )

            // A question mark, because the app is asking rather than telling.
            CheckState.Warning -> Text(
                text = WarningMark,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // An exclamation, on the error container the row already carries.
            CheckState.Blocked -> Text(
                text = BlockedMark,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer
            )
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
            stringResource(R.string.reminder_health_open_notifications) to
                { context.openNotificationSettings() }

        HealthCheck.ExactAlarms ->
            stringResource(R.string.reminder_health_open_alarms) to
                { context.openExactAlarmSettings() }

        // The one button whose destination depends on the phone, so it is
        // named after where it will actually arrive. On a skin that both ships
        // a sleep feature and lets an app open it, that is the feature's own
        // screen, under the name the user will look for. Everywhere else it is
        // the app's Android settings page, under Android's name for it. The
        // button asks first rather than promising Autostart and opening App
        // info, which is what OxygenOS would have it do.
        HealthCheck.BackgroundWork ->
            checks.restriction.openLabel(context.hasVendorScreen(checks.restriction)) to {
                context.openBackgroundWorkSettings(checks.restriction)
            }

        null -> stringResource(R.string.reminder_health_test) to onTest
    }

    Button(
        onClick = action,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FocuslistSpacing.sm)
    ) {
        Text(label)
    }
}

/** "Open Autostart settings" on a Xiaomi, "Open battery settings" elsewhere. */
@Composable
private fun DeviceRestriction?.openLabel(hasVendorScreen: Boolean): String =
    if (this == null || !hasVendorScreen) {
        stringResource(R.string.reminder_health_open_battery)
    } else {
        stringResource(R.string.reminder_health_open_restriction, stringResource(label))
    }

/**
 * Whether this phone has a manufacturer screen the app is allowed to open.
 *
 * Remembered because it asks the package manager, and the answer cannot change
 * while the screen is open: it would take installing or removing a system app.
 */
@Composable
private fun Context.hasVendorScreen(restriction: DeviceRestriction?): Boolean =
    remember(restriction) { resolvableScreens(restriction).isNotEmpty() }

// --- what each state says --------------------------------------------------

private val ReminderHealthState.label: Int
    get() = when (this) {
        ReminderHealthState.Checking -> R.string.reminder_health_checking_label
        ReminderHealthState.Ready -> R.string.reminder_health_ready_label
        is ReminderHealthState.ActionNeeded -> R.string.reminder_health_action_label
        is ReminderHealthState.WorthChecking -> R.string.reminder_health_caution_label
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
            stringResource(R.string.reminder_restriction_action_title, displayManufacturer())
    }

    // The same sentence `ActionNeeded` says for the same cause. What differs
    // between the two states is the certainty, and the certainty is carried by
    // the body and the colour rather than by a second title.
    is ReminderHealthState.WorthChecking -> when (state.cause) {
        HealthCheck.Notifications ->
            stringResource(R.string.reminder_health_no_notifications_title)

        HealthCheck.ExactAlarms ->
            stringResource(R.string.reminder_health_no_exact_title)

        HealthCheck.BackgroundWork ->
            stringResource(R.string.reminder_restriction_action_title, displayManufacturer())
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

        // The honest sentence: the feature can delay reminders, and the app
        // cannot tell whether it is on. `reminder_restriction_action_body`
        // already says exactly that.
        is ReminderHealthState.WorthChecking -> when (state.cause) {
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // Below 12 there is no such screen, because there was no such
        // permission. The app's own page is the nearest honest destination.
        openAppSettings()
        return
    }

    startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun Context.formatTime(at: Instant): String =
    DateFormat.getTimeFormat(this).format(Date.from(at))

/**
 * The leading slot the status glyph sits in.
 *
 * It is a slot, not a badge. `reminder-health.md`: the glyph sits directly in a
 * 40dp leading slot, which keeps the tick, the question mark and the exclamation
 * on one axis without drawing a circle behind them.
 */
private val GlyphSlotSize = 40.dp

private val GlyphIconSize = 24.dp

private val IndicatorSize = 24.dp

/** Drawn rather than iconised, so the row needs no second asset. */
/** A question mark, because the app is asking rather than telling. */
private const val WarningMark = "?"

/** An exclamation, for the state the app was actually told about. */
private const val BlockedMark = "!"

/**
 * The gap between the headline card and the check group.
 *
 * Stated rather than left to the column's spacing, because the headline grows
 * with its copy and the rows do not: a longer body has already eaten this gap
 * once and overlapped the first row by 8dp.
 */
private val HeadlineToChecksGap = 12.dp

/**
 * How much the supporting line steps back from its row's own content colour.
 *
 * An alpha rather than `onSurfaceVariant`, because the row's colour changes with
 * its state: on an error container `onSurfaceVariant` would be the one piece of
 * text on the row not reading against its own background.
 */
private const val SupportingTextAlpha = 0.75f
