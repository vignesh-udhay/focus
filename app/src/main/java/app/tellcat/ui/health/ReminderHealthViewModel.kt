package app.tellcat.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.tellcat.core.domain.ReminderHealth
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.hasAlarmStyleReminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import app.tellcat.core.domain.ReminderHealthState
import app.tellcat.core.domain.reminderHealth
import app.tellcat.core.notification.ReminderHealthChecks
import app.tellcat.data.local.ReminderHealthAcknowledgements
import app.tellcat.data.repository.ReminderDeliveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/**
 * State for the reminder health screen.
 *
 * Its own view model rather than a corner of `TaskListViewModel`, because it
 * reads a different table and asks a different question. Nothing here is about
 * the user's work; it is all about whether the phone can be trusted with it.
 *
 * The permission checks are read on demand rather than observed, because
 * Android offers nothing to observe: a permission changes while the user is in
 * Settings, and the app finds out by looking again when it comes back.
 * [refresh] is what looking again means, and the screen calls it on resume.
 */
class ReminderHealthViewModel(
    private val deliveries: ReminderDeliveryRepository,
    private val checks: ReminderHealthChecks,
    private val acknowledgements: ReminderHealthAcknowledgements,
    private val now: () -> Instant = Instant::now,
    // Whether any outstanding reminder rings like an alarm, which is when the
    // alarm row is listed. D-102, R-014. None by default, so a screen built
    // without tasks lists the three rows it always did.
    private val alarmStyleReminders: Flow<Boolean> = flowOf(false)
) : ViewModel() {

    private val checkedAt = MutableStateFlow<Instant?>(null)

    /**
     * What the screen draws.
     *
     * [ReminderHealthState.Checking] until the first read completes, which is
     * a real state rather than an empty one: the app has not yet asked, and
     * saying "healthy" before asking would be a guess presented as an answer.
     */
    val state: StateFlow<ReminderHealthState> =
        combine(deliveries.observeDeliveries(), checkedAt, alarmStyleReminders) { recorded, at, alarms ->
            if (at == null) {
                ReminderHealthState.Checking
            } else {
                health(recorded, at, alarms).state
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReminderHealthState.Checking
        )

    /** The three rows, which are drawn under every state. */
    val health: StateFlow<ReminderHealth?> =
        combine(deliveries.observeDeliveries(), checkedAt, alarmStyleReminders) { recorded, at, alarms ->
            at?.let { health(recorded, it, alarms) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    /**
     * What Today may interrupt the task list with.
     *
     * The health screen continues to read [state], so acknowledging an
     * incident changes only its presentation on Today. Removing the matching
     * incident from that presentation recalculates the remaining state rather
     * than returning nothing: an active permission failure underneath a past
     * miss must become `ActionNeeded`, not disappear with it. A newer miss
     * carries a different delivery ID and appears without any explicit reset.
     */
    val todayState: StateFlow<ReminderHealthState?> =
        combine(health, acknowledgements.dismissedDeliveryId) { current, dismissedId ->
            current?.visibleOnToday(dismissedId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    /** Re-reads the permissions. Called when the screen appears or returns. */
    fun refresh() {
        checkedAt.value = now()
    }

    /** Acknowledges only the missed incident currently being shown on Today. */
    fun dismissTodayBanner() {
        val missed = health.value?.state as? ReminderHealthState.Missed ?: return
        acknowledgements.dismiss(missed.delivery.id)
    }

    private fun health(
        recorded: List<app.tellcat.core.domain.ReminderDelivery>,
        at: Instant,
        hasAlarms: Boolean
    ) =
        reminderHealth(
            notifications = checks.notifications(),
            exactAlarms = checks.exactAlarms(),
            restriction = checks.restriction(),
            deliveries = recorded,
            now = at,
            alarmStyle = if (hasAlarms) checks.alarmStyle() else null
        )

    class Factory(
        private val deliveries: ReminderDeliveryRepository,
        private val checks: ReminderHealthChecks,
        private val acknowledgements: ReminderHealthAcknowledgements,
        private val tasks: Flow<List<Task>>
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ReminderHealthViewModel(
                deliveries = deliveries,
                checks = checks,
                acknowledgements = acknowledgements,
                alarmStyleReminders = tasks.map(::hasAlarmStyleReminders).distinctUntilChanged()
            ) as T
    }

    private companion object {

        /** The same grace the task lists use, for the same reason. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The incident-specific rule D-044 applies to Today and nowhere else. */
internal fun ReminderHealth.visibleOnToday(dismissedDeliveryId: String?): ReminderHealthState =
    if (latestConcern?.id == dismissedDeliveryId) {
        copy(latestConcern = null).state
    } else {
        state
    }
