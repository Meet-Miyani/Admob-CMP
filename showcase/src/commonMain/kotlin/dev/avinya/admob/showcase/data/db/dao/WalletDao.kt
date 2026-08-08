package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    @Upsert
    suspend fun upsert(wallet: WalletEntity)

    @Query("SELECT * FROM wallet WHERE id = 0")
    suspend fun current(): WalletEntity?

    @Query("SELECT coinBalance FROM wallet WHERE id = 0")
    fun balance(): Flow<Int?>

    /**
     * IGNORE, not REPLACE: a replayed reward callback must be a no-op.
     * The return value is the inserted row id, or -1 when the key already existed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordGrant(grant: RewardGrantEntity): Long
}
