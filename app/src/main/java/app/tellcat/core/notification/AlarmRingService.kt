package app.tellcat.core.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.os.UserManagerCompat
import app.tellcat.TellcatApplication
import app.tellcat.core.domain.AlarmRingLimit
import app.tellcat.core.domain.AlarmSnooze
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.snoozedUntil
import app.tellcat.core.domain.stillRingsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime

/**
 * Rings an alarm-style reminder until it is answered. `docs/decisions.md` D-102.
 *
 * A foreground service, because every way the competitor study saw an alarm
 * fail was something that should not have stopped it: Home, Back, a tap
 * outside, the power button, the shade opening. None of those reaches a
 * service. Its notification carries Stop and Snooze and, where Android allows,
 * the full screen; [AlarmActivity][app.tellcat.AlarmActivity] is the
 * same two answers drawn large.
 *
 * **One alarm at a time.** A second one arriving while the first rings leaves
 * the first as a normal reminder, quietly, and rings the second. Nothing else
 * in the shade is touched, ever: the service removes only its own notification.
 *
 * **Storage is the state**, as everywhere in the reminder code. The service
 * holds which alarm it is ringing and nothing else; every reconciliation asks
 * [silenceIfSettled] whether that task still wants ringing, which is how Done
 * from the notification, a snooze from either path, an edit to the time, and
 * deleting or completing the task stop it (R-002, D-099).
 */
class AlarmRingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: AlarmPlayer? = null
    private var current: RingingAlarm? = null

    /** Stops an alarm left unanswered for [AlarmRingLimit], as Stop does. */
    private val ringLimit = Runnable { finish(leaveReminder = true) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarm = intent?.ringingAlarm()

        when {
            intent?.action == ActionRing && alarm != null -> ring(alarm)
            intent?.action == ActionStop && alarm != null -> answer(alarm, snooze = false)
            intent?.action == ActionSnooze && alarm != null -> answer(alarm, snooze = true)
            // Nothing to do: an answer to an alarm already gone, or a restart.
            current == null -> stopSelf()
        }

        // Not restarted by the system. An alarm the process lost is announced
        // by the reconciliation as a normal reminder rather than rung again
        // hours later.
        return START_NOT_STICKY
    }

    private fun ring(alarm: RingingAlarm) {
        // A second alarm while one rings: the first stays as a normal reminder.
        current?.takeIf { it.taskId != alarm.taskId }?.let { replaced -> leaveReminder(replaced) }

        current = alarm
        ringing.value = alarm

        val started = try {
            // `systemExempted` from Android 14, the type Android allows an app
            // holding the exact-alarm grant, which an alarm-style reminder only
            // rings with. D-102. Below 14 there are no types to declare.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    AlarmNotificationId,
                    alarmNotification(alarm),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(AlarmNotificationId, alarmNotification(alarm))
            }
            true
        } catch (error: Exception) {
            // Refused by the system. The reminder must still arrive, so it is
            // posted as a normal one, with its sound.
            Log.w(LogTag, "Could not ring ${alarm.taskId} as an alarm.", error)
            false
        }

        if (!started) {
            current = null
            ringing.value = null
            leaveReminder(alarm, silent = false)
            stopSelf()
            return
        }

        if (player == null) player = AlarmPlayer(this)
        player?.start()

        handler.removeCallbacks(ringLimit)
        handler.postDelayed(ringLimit, AlarmRingLimit.toMillis())
    }

    /**
     * Stop or Snooze. Ignored unless it is an answer to the alarm ringing now:
     * a tap on a notification for an alarm already replaced does nothing.
     */
    private fun answer(alarm: RingingAlarm, snooze: Boolean) {
        if (current != alarm) {
            if (current == null) stopSelf()
            return
        }

        if (!snooze) {
            finish(leaveReminder = true)
            return
        }

        // Silence first: the person asked for quiet, and the write can wait a
        // moment. Before the first unlock the database cannot be opened, so the
        // snooze becomes a Stop; the notification asked for the unlock already.
        finish(leaveReminder = !UserManagerCompat.isUserUnlocked(this))
        if (!UserManagerCompat.isUserUnlocked(this)) return

        val application = applicationContext as? TellcatApplication ?: return
        scope.launch {
            val until = snoozedUntil(AlarmSnooze, LocalDateTime.now()) ?: return@launch
            application.taskRepository.snoozeReminder(alarm.taskId, until)
            // Set before this returns, as the notification's own Snooze does.
            application.reconcileReminders()
        }
    }

    /** Stops ringing, and leaves the normal reminder behind when asked. */
    private fun finish(leaveReminder: Boolean) {
        handler.removeCallbacks(ringLimit)
        player?.stop()

        val finished = current
        current = null
        ringing.value = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (leaveReminder && finished != null) leaveReminder(finished)
    }

    /** The reconciliation's question, answered on the main thread. */
    private fun silenceIfSettledNow(tasks: List<Task>) {
        val alarm = current ?: return
        val task = tasks.firstOrNull { it.id == alarm.taskId }
        if (!task.stillRingsFor(alarm.ringsAt)) finish(leaveReminder = false)
    }

    /**
     * Posts [alarm]'s task as a normal reminder, with Done and Snooze: what
     * remains after Stop, after ringing unanswered, or after being replaced
     * (R-020). Quiet unless the alarm never got to ring.
     *
     * Read from storage where it can be, so the notes and the repeat are
     * current. Before the first unlock it cannot be, and the alarm's own copy
     * stands in, with actions that ask for the unlock.
     */
    private fun leaveReminder(alarm: RingingAlarm, silent: Boolean = true) {
        val context = applicationContext
        scope.launch {
            ensureReminderChannel()
            val unlocked = UserManagerCompat.isUserUnlocked(context)
            val stored = if (unlocked) {
                (context as? TellcatApplication)?.taskRepository?.findTask(alarm.taskId)
            } else {
                null
            }

            when {
                stored != null -> if (!stored.isCompleted) context.postReminder(stored, silent = silent)
                !unlocked -> context.postReminder(
                    Task(
                        id = alarm.taskId,
                        title = alarm.title,
                        createdAt = Instant.EPOCH,
                        notes = alarm.notes,
                        reminderAt = alarm.ringsAt
                    ),
                    requireUnlockForActions = true,
                    silent = silent
                )
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ringLimit)
        player?.stop()
        player = null
        if (instance === this) instance = null
        ringing.value = null
        super.onDestroy()
    }

    companion object {
        const val ActionRing = "app.tellcat.action.ALARM_RING"
        const val ActionStop = "app.tellcat.action.ALARM_STOP"
        const val ActionSnooze = "app.tellcat.action.ALARM_SNOOZE"

        private const val ExtraTaskId = "tellcat.extra.alarmTaskId"
        private const val ExtraTitle = "tellcat.extra.alarmTitle"
        private const val ExtraNotes = "tellcat.extra.alarmNotes"
        private const val ExtraRingsAt = "tellcat.extra.alarmRingsAt"

        @Volatile
        private var instance: AlarmRingService? = null

        private val ringing = MutableStateFlow<RingingAlarm?>(null)

        /** The alarm ringing now, or null. What the full screen draws. */
        val nowRinging: StateFlow<RingingAlarm?> = ringing.asStateFlow()

        /**
         * Starts ringing [alarm], and says whether Android let it. A refusal,
         * which an inexact alarm can cause, leaves the caller to post the
         * normal reminder.
         */
        fun start(context: Context, alarm: RingingAlarm): Boolean = try {
            ContextCompat.startForegroundService(context, intent(context, alarm, ActionRing))
            true
        } catch (error: Exception) {
            Log.w(LogTag, "Could not start the alarm for ${alarm.taskId}.", error)
            false
        }

        /** Stop or Snooze for [alarm], as the notification and the full screen send it. */
        fun answerIntent(context: Context, alarm: RingingAlarm, action: String): Intent =
            intent(context, alarm, action)

        /**
         * Stops the alarm if its task no longer wants ringing: completed,
         * deleted, no longer an alarm, or due at another moment. Called by
         * every reconciliation, with every live task. R-002, D-099.
         */
        fun silenceIfSettled(tasks: List<Task>) {
            val service = instance ?: return
            service.handler.post { service.silenceIfSettledNow(tasks) }
        }

        private fun intent(context: Context, alarm: RingingAlarm, action: String): Intent =
            Intent(context, AlarmRingService::class.java)
                .setAction(action)
                .putExtra(ExtraTaskId, alarm.taskId)
                .putExtra(ExtraTitle, alarm.title)
                .putExtra(ExtraNotes, alarm.notes)
                .putExtra(ExtraRingsAt, alarm.ringsAt.toString())

        private fun Intent.ringingAlarm(): RingingAlarm? {
            val taskId = getStringExtra(ExtraTaskId) ?: return null
            val ringsAt = getStringExtra(ExtraRingsAt)?.let(LocalDateTime::parse) ?: return null
            return RingingAlarm(
                taskId = taskId,
                title = getStringExtra(ExtraTitle).orEmpty(),
                notes = getStringExtra(ExtraNotes),
                ringsAt = ringsAt
            )
        }
    }
}

/**
 * The sound and the vibration.
 *
 * The alarm stream (`USAGE_ALARM`), so it sounds on Silent and at the alarm
 * volume, which is To Do Reminder's failure answered. It always vibrates, with
 * alarm attributes, so a phone on vibrate-only or face down still buzzes. The
 * user's own alarm sound, falling back to the default ringtone, so it is a
 * sound they already know means "wake up".
 */
private class AlarmPlayer(private val context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    fun start() {
        if (player != null) return
        requestFocus()
        player = soundUris().firstNotNullOfOrNull { uri -> playing(uri) }
        if (player == null) Log.w(LogTag, "No alarm sound could be played; vibrating only.")
        vibrate()
    }

    fun stop() {
        player?.let { playing ->
            try {
                playing.stop()
            } catch (_: IllegalStateException) {
                // Already stopped; releasing is what matters.
            }
            playing.release()
        }
        player = null
        vibrator()?.cancel()
        focus?.let { request -> context.getSystemService<AudioManager>()?.abandonAudioFocusRequest(request) }
        focus = null
    }

    private fun playing(uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(context, uri)
            isLooping = true
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
            start()
        }
    } catch (error: Exception) {
        Log.w(LogTag, "Could not play $uri.", error)
        null
    }

    private fun soundUris(): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        Settings.System.DEFAULT_ALARM_ALERT_URI,
        Settings.System.DEFAULT_RINGTONE_URI,
        Settings.System.DEFAULT_NOTIFICATION_URI
    )

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        context.getSystemService<AudioManager>()?.requestAudioFocus(request)
        focus = request
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = vibrator() ?: return
        val pattern = VibrationEffect.createWaveform(VibrationPattern, VibrationRepeatFrom)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator.vibrate(pattern, attributes)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            context.getSystemService<Vibrator>()
        }

    private companion object {
        /** Off, then 800ms on and 600ms off, repeated from the first buzz. */
        val VibrationPattern = longArrayOf(0, 800, 600)
        const val VibrationRepeatFrom = 1
    }
}
