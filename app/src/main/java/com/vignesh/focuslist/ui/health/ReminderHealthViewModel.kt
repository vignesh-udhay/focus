package com.vignesh.focuslist.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vignesh.focuslist.core.domain.ReminderHealth
import com.vignesh.focuslist.core.domain.ReminderHealthState
import com.vignesh.focuslist.core.domain.reminderHealth
import com.vignesh.focuslist.core.notification.ReminderHealthChecks
import com.vignesh.focuslist.data.repository.ReminderDeliveryRepository
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
    private val now: () -> Instant = Instant::now
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
        combine(deliveries.observeDeliveries(), checkedAt) { recorded, at ->
            if (at == null) {
                ReminderHealthState.Checking
            } else {
                health(recorded, at).state
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReminderHealthState.Checking
        )

    /** The three rows, which are drawn under every state. */
    val health: StateFlow<ReminderHealth?> =
        combine(deliveries.observeDeliveries(), checkedAt) { recorded, at ->
            at?.let { health(recorded, it) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    /** Re-reads the permissions. Called when the screen appears or returns. */
    fun refresh() {
        checkedAt.value = now()
    }

    private fun health(recorded: List<com.vignesh.focuslist.core.domain.ReminderDelivery>, at: Instant) =
        reminderHealth(
            notifications = checks.notifications(),
            exactAlarms = checks.exactAlarms(),
            restriction = checks.restriction(),
            deliveries = recorded,
            now = at
        )

    class Factory(
        private val deliveries: ReminderDeliveryRepository,
        private val checks: ReminderHealthChecks
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ReminderHealthViewModel(deliveries, checks) as T
    }

    private companion object {

        /** The same grace the task lists use, for the same reason. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
