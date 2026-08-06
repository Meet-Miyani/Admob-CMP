package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.avinya.admob.showcase.data.db.entity.AdEventEntity
import dev.avinya.admob.showcase.data.db.entity.PaidEventEntity
import dev.avinya.admob.showcase.data.db.entity.PolicyDecisionEntity
import kotlinx.coroutines.flow.Flow

/** Row cap for every log table. A demo left running must not grow unbounded. */
internal const val TELEMETRY_ROW_CAP = 500

@Dao
interface TelemetryDao {

    @Insert
    suspend fun insertAdEvent(event: AdEventEntity)

    @Insert
    suspend fun insertPolicyDecision(decision: PolicyDecisionEntity)

    @Insert
    suspend fun insertPaidEvent(event: PaidEventEntity)

    @Query("SELECT COUNT(*) FROM ad_events")
    suspend fun adEventCount(): Int

    @Query("SELECT * FROM ad_events ORDER BY at DESC LIMIT :limit")
    fun recentAdEvents(limit: Int = TELEMETRY_ROW_CAP): Flow<List<AdEventEntity>>

    @Query("SELECT * FROM policy_decisions ORDER BY at DESC LIMIT :limit")
    fun recentPolicyDecisions(limit: Int = TELEMETRY_ROW_CAP): Flow<List<PolicyDecisionEntity>>

    @Query("SELECT * FROM paid_events ORDER BY at DESC LIMIT :limit")
    fun recentPaidEvents(limit: Int = TELEMETRY_ROW_CAP): Flow<List<PaidEventEntity>>

    @Query("DELETE FROM ad_events WHERE id NOT IN (SELECT id FROM ad_events ORDER BY id DESC LIMIT :cap)")
    suspend fun trimAdEvents(cap: Int = TELEMETRY_ROW_CAP)

    @Query("DELETE FROM policy_decisions WHERE id NOT IN (SELECT id FROM policy_decisions ORDER BY id DESC LIMIT :cap)")
    suspend fun trimPolicyDecisions(cap: Int = TELEMETRY_ROW_CAP)

    @Query("DELETE FROM paid_events WHERE id NOT IN (SELECT id FROM paid_events ORDER BY id DESC LIMIT :cap)")
    suspend fun trimPaidEvents(cap: Int = TELEMETRY_ROW_CAP)

    /** Insert and trim in one transaction, so the cap can never be exceeded between calls. */
    @Transaction
    suspend fun recordAdEvent(event: AdEventEntity) {
        insertAdEvent(event)
        trimAdEvents()
    }

    @Transaction
    suspend fun recordPolicyDecision(decision: PolicyDecisionEntity) {
        insertPolicyDecision(decision)
        trimPolicyDecisions()
    }

    @Transaction
    suspend fun recordPaidEvent(event: PaidEventEntity) {
        insertPaidEvent(event)
        trimPaidEvents()
    }
}
