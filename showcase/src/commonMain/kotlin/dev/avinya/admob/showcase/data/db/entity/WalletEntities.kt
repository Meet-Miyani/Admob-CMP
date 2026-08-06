package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row wallet. [id] is always 0. */
@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 0,
    val coinBalance: Int,
    val updatedAt: Long,
)

/**
 * One row per granted reward, keyed by an idempotency key.
 *
 * Guards against a replayed reward callback double-crediting the wallet.
 * `INSERT OR IGNORE` on this table is what makes crediting idempotent.
 */
@Entity(tableName = "reward_grants")
data class RewardGrantEntity(
    @PrimaryKey val idempotencyKey: String,
    val amount: Int,
    val grantedAt: Long,
)
