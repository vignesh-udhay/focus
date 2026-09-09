package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one missed delivery the user has acknowledged on Today.
 *
 * This is deliberately an incident ID rather than a general dismissed flag.
 * It hides only the matching Today banner, survives process death, and becomes
 * irrelevant as soon as Reminder Health reports a newer missed delivery. The
 * delivery itself remains in Room and continues to appear in Reminder Health.
 */
class ReminderHealthAcknowledgements(context: Context) {
    private val storage = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)

    private val mutableDismissedDeliveryId = MutableStateFlow(
        storage.getString(DismissedDeliveryIdKey, null)
    )
    val dismissedDeliveryId: StateFlow<String?> = mutableDismissedDeliveryId.asStateFlow()

    fun dismiss(deliveryId: String) {
        storage.edit { putString(DismissedDeliveryIdKey, deliveryId) }
        mutableDismissedDeliveryId.value = deliveryId
    }

    private companion object {
        const val FileName = "reminder_health_acknowledgements"
        const val DismissedDeliveryIdKey = "dismissed_delivery_id"
    }
}
