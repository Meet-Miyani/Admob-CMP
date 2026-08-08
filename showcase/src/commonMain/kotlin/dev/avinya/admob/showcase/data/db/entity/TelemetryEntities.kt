package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per SDK `AdEvent`. Feeds the Inspector's Events tab. Capped at 500 rows. */
@Entity(tableName = "ad_events", indices = [Index("at")])
data class AdEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val placementId: String,
    val format: String,
    val type: String,
    val detail: String?,
)

/**
 * One row per `AdPolicy` decision, including suppressions.
 *
 * Recording *why* no ad appeared is the single most useful thing the
 * Inspector shows, so suppressions are first-class rows, not log noise.
 * Capped at 500 rows.
 */
@Entity(tableName = "policy_decisions", indices = [Index("at")])
data class PolicyDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val placementId: String,
    val decision: String,
    val reason: String?,
)

/** One row per `AdEvent.Paid`. Feeds the Inspector's Revenue tab. Capped at 500 rows. */
@Entity(tableName = "paid_events", indices = [Index("at")])
data class PaidEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val placementId: String,
    val valueMicros: Long,
    val currency: String,
    val precision: String,
)
