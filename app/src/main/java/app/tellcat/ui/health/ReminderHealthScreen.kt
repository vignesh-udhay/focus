package app.tellcat.ui.health

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tellcat.R
import app.tellcat.ui.task.openAlarmChannelSettings
import app.tellcat.ui.task.openFullScreenSettings
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.domain.CheckState
import app.tellcat.core.domain.ConcernWindow
import app.tellcat.core.domain.DeviceRestriction
import app.tellcat.core.domain.HealthCheck
import app.tellcat.core.domain.ReminderDelivery
import app.tellcat.core.domain.ReminderHealth
import app.tellcat.core.domain.ReminderHealthState
import app.tellcat.core.domain.AlarmMode
import app.tellcat.core.notification.TestReminder
import app.tellcat.core.notification.currentAlarmMode
import app.tellcat.core.notification.displayManufacturer
import app.tellcat.core.notification.openAppSettings
import app.tellcat.core.notification.openBackgroundWorkSettings
import app.tellcat.core.notification.resolvableScreens
import app.tellcat.ui.component.TellcatTopAppBar
import app.tellcat.ui.component.durationLabel
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
            TellcatTopAppBar(
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
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.sm)
        ) {
            Headline(state, health?.restriction)

            // One connected collection, like every other list in the app, with
            // Material's own segmented shapes and gap rather than three loose
            // cards. They were `TellcatSpacing.sm` apart and separately
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
                    val (label, open) = checkAction(check, health)
                    CheckRow(
                        check = check,
                        state = checkState,
                        restriction = health?.restriction,
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = checks.size
                        ),
                        onClick = open,
                        onClickLabel = label
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
                    modifier = Modifier.padding(horizontal = TellcatSpacing.xs)
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
            .padding(top = TellcatSpacing.xs)
    ) {
        Column(
            modifier = Modifier.padding(TellcatSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.xs)
        ) {
            if (state == ReminderHealthState.Checking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.sm)
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
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onClickLabel: String
) {
    val container = when (state) {
        CheckState.Blocked -> MaterialTheme.colorScheme.errorContainer
        CheckState.Warning, CheckState.Ok -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val content = when (state) {
        CheckState.Blocked -> MaterialTheme.colorScheme.onErrorContainer
        CheckState.Warning, CheckState.Ok -> MaterialTheme.colorScheme.onSurface
    }

    // **Each row opens its own settings.** D-106. The rows used to report
    // only, and one button below opened the first failing one, so the user
    // fixed things top to bottom through a button that changed its name each
    // time. On the owner's phone they tapped the rows instead, and nothing
    // happened. The label names where the tap goes, for TalkBack.
    Surface(
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = shapes.shape,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { onClick(label = onClickLabel, action = null) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.sm),
            modifier = Modifier.padding(TellcatSpacing.sm)
        ) {
            StatusGlyph(state = state, onContainer = content)

            Column(modifier = Modifier.weight(1f)) {
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

            // Says the row goes somewhere, as the Plan rows' chevron does. Not
            // described: the row's click label already says where.
            Icon(
                painter = painterResource(R.drawable.ic_chevron_forward),
                contentDescription = null,
                tint = content.copy(alpha = SupportingTextAlpha),
                modifier = Modifier.size(CheckRowChevronSize)
            )
        }
    }
}

/** Material's dense icon size, the Plan rows' chevron. */
private val CheckRowChevronSize = 20.dp

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
 *
 * **Which is why none of it is announced.** A second channel for the eye is
 * noise for a screen reader: the row says "Notifications, Not allowed" already,
 * and TalkBack reading a bare "!" after it adds a character, not a fact. The Ok
 * branch has always passed `contentDescription = null` for that reason; the two
 * text marks needed `clearAndSetSemantics` to say the same thing, and until the
 * Phase 5 pass they were being read out.
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {}
            )

            // An exclamation, on the error container the row already carries.
            CheckState.Blocked -> Text(
                text = BlockedMark,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

/**
 * The test, once nothing is failing. D-106.
 *
 * It used to open the settings for whatever was wrong, most severe first, and
 * become the test once all was well. Each row opens its own settings now, so
 * the button only tests: a test while something is refused would only show
 * the refusal again.
 */
@Composable
private fun PrimaryAction(health: ReminderHealth?, onTest: () -> Unit) {
    val checks = health ?: return
    if (checks.firstFailing != null) return

    Button(
        onClick = onTest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TellcatSpacing.sm)
    ) {
        Text(stringResource(R.string.reminder_health_test))
    }
}

/**
 * Where tapping a check row goes, and its name for TalkBack. D-106.
 */
@Composable
private fun checkAction(check: HealthCheck, checks: ReminderHealth?): Pair<String, () -> Unit> {
    val context = LocalContext.current
    val restriction = checks?.restriction

    return when (check) {
        HealthCheck.Notifications ->
            stringResource(R.string.reminder_health_open_notifications) to
                { context.openNotificationSettings() }

        HealthCheck.ExactAlarms ->
            stringResource(R.string.reminder_health_open_alarms) to
                { context.openExactAlarmSettings() }

        // The one row whose destination depends on the phone, so its label is
        // named after where it will actually arrive. On a skin that both ships
        // a sleep feature and lets an app open it, that is the feature's own
        // screen, under the name the user will look for. Everywhere else it is
        // the app's Android settings page, under Android's name for it, rather
        // than promising Autostart and opening App info, which is what
        // OxygenOS would have it do.
        HealthCheck.BackgroundWork ->
            restriction.openLabel(context.hasVendorScreen(restriction)) to {
                context.openBackgroundWorkSettings(restriction)
            }

        // D-102. To whatever the mode says is missing: the exact grant, the
        // Alarms channel, or the full-screen toggle. With nothing missing, the
        // Alarms channel, where its sound and lock-screen settings are.
        HealthCheck.AlarmStyle -> when (context.currentAlarmMode()) {
            AlarmMode.NoExactAlarms ->
                stringResource(R.string.task_reminder_alarm_exact_allow) to { context.openExactAlarmSettings() }

            AlarmMode.Banner ->
                stringResource(R.string.task_reminder_alarm_allow) to { context.openFullScreenSettings() }

            AlarmMode.PlainReminder, AlarmMode.FullScreen ->
                stringResource(R.string.task_reminder_alarm_channel_open) to { context.openAlarmChannelSettings() }
        }
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
        is ReminderHealthState.Ready ->
            if (this.verified) R.string.reminder_health_ready_label
            else R.string.reminder_health_unverified_label
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
    is ReminderHealthState.Ready -> stringResource(
        if (state.verified) R.string.reminder_health_ready_title
        else R.string.reminder_health_unverified_title
    )

    is ReminderHealthState.ActionNeeded -> when (state.cause) {
        HealthCheck.Notifications ->
            stringResource(R.string.reminder_health_no_notifications_title)

        HealthCheck.ExactAlarms ->
            stringResource(R.string.reminder_health_no_exact_title)

        // The frame's own sentence, now said only when it is the true cause.
        HealthCheck.BackgroundWork ->
            stringResource(R.string.reminder_restriction_action_title, displayManufacturer())

        HealthCheck.AlarmStyle -> stringResource(R.string.reminder_health_alarm_off_title)
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

        HealthCheck.AlarmStyle -> stringResource(R.string.reminder_health_alarm_banner_title)
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
        is ReminderHealthState.Ready -> stringResource(
            if (state.verified) R.string.reminder_health_ready_body
            else R.string.reminder_health_unverified_body
        )

        is ReminderHealthState.ActionNeeded -> when (state.cause) {
            HealthCheck.Notifications ->
                stringResource(R.string.reminder_health_no_notifications_body)

            HealthCheck.ExactAlarms ->
                stringResource(R.string.reminder_health_no_exact_body)

            HealthCheck.BackgroundWork -> stringResource(
                R.string.reminder_restriction_action_body,
                stringResource(restriction?.label ?: R.string.reminder_health_check_background)
            )

            HealthCheck.AlarmStyle -> stringResource(R.string.reminder_health_alarm_off_body)
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

            HealthCheck.AlarmStyle -> stringResource(R.string.reminder_health_alarm_banner_body)
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
            context.formatTime(delivery.arrivedWallAt),
            ConcernWindow.toDays()
        )
    } else {
        stringResource(
            R.string.reminder_health_missed_silent_body,
            delivery.taskTitle,
            context.formatTime(delivery.scheduledWallAt),
            ConcernWindow.toDays()
        )
    }
}

private fun HealthCheck.label(restriction: DeviceRestriction?): Int = when (this) {
    HealthCheck.Notifications -> R.string.reminder_health_check_notifications
    HealthCheck.ExactAlarms -> R.string.reminder_health_check_exact
    // The manufacturer's own word where there is one, because that is the word
    // the user is looking for in their settings app.
    HealthCheck.BackgroundWork -> restriction?.label ?: R.string.reminder_health_check_background
    HealthCheck.AlarmStyle -> R.string.reminder_health_check_alarm
}

private fun HealthCheck.detail(state: CheckState, restriction: DeviceRestriction?): Int = when {
    // How they will ring, in the editor's words. D-102.
    this == HealthCheck.AlarmStyle -> when (state) {
        CheckState.Ok -> R.string.reminder_health_alarm_full_screen
        CheckState.Warning -> R.string.reminder_health_alarm_banner
        CheckState.Blocked -> R.string.reminder_health_alarm_plain
    }

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
