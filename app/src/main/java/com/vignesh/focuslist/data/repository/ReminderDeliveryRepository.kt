package com.vignesh.focuslist.data.repository

import com.vignesh.focuslist.core.domain.ReminderDelivery
import com.vignesh.focuslist.data.local.ReminderDeliveryDao
import com.vignesh.focuslist.data.local.toDomain
import com.vignesh.focuslist.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage of what the app actually delivered, in domain terms.
 *
 * Separate from `TaskRepository` because it is a separate thing. A delivery is
 * not a property of a task: it outlives the task, it is never edited, and the
 * questions asked of it are about the device rather than about the work.
 *
 * Translates and delegates, nothing more. What counts as late lives in
 * `ReminderDelivery`; this class has no opinion about it.
 */
class ReminderDeliveryRepository(private val dao: ReminderDeliveryDao) {

    /** The recent history, newest first. */
    fun observeDeliveries(): Flow<List<ReminderDelivery>> =
        dao.observeDeliveries().map { rows -> rows.map { it.toDomain() } }

    /**
     * Writes one firing down, and trims the history behind it.
     *
     * Both in one call, so no caller can add to an unbounded table by
     * forgetting the second step.
     */
    suspend fun record(delivery: ReminderDelivery) {
        dao.insert(delivery.toEntity())
        dao.trimTo()
    }
}
