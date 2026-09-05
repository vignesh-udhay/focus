package com.vignesh.focuslist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the record of what the app actually delivered.
 *
 * Append and read. Nothing updates a row: a delivery is a thing that happened,
 * and a table you can only add to cannot be quietly corrected into agreeing
 * with what the app wishes had happened.
 */
@Dao
interface ReminderDeliveryDao {

    /**
     * Newest first, capped.
     *
     * The cap is here rather than in the domain because it is the same number
     * that bounds the table, and two places deciding how much history exists
     * would eventually disagree.
     */
    @Query("SELECT * FROM reminder_deliveries ORDER BY arrivedWallAt DESC LIMIT :limit")
    fun observeDeliveries(limit: Int = DeliveryHistoryLimit): Flow<List<ReminderDeliveryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(delivery: ReminderDeliveryEntity)

    /**
     * Drops everything past the newest [keep] rows.
     *
     * Run after each insert rather than on a schedule, so the table is bounded
     * at every moment rather than between cleanups. A reminder app that grew a
     * row per firing for a year would carry tens of thousands of rows nobody
     * will ever read.
     *
     * Ordered by arrival for the same reason the read is: it is when the user
     * experienced the thing, and it is the column an index would go on if this
     * ever grew enough to need one.
     */
    @Query(
        """
        DELETE FROM reminder_deliveries
        WHERE id NOT IN (
            SELECT id FROM reminder_deliveries ORDER BY arrivedWallAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trimTo(keep: Int = DeliveryHistoryLimit)
}

/**
 * How many firings the app remembers.
 *
 * Enough to see a pattern rather than an incident, and small enough that the
 * table never becomes a thing anyone has to think about. A device that
 * misdelivers will show it inside fifty reminders; one that needs a thousand
 * to prove itself is a device that is working.
 */
const val DeliveryHistoryLimit = 50
