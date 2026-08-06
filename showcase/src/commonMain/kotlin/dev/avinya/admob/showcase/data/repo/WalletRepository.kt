package dev.avinya.admob.showcase.data.repo

import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.dao.WalletDao
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import dev.avinya.admob.showcase.domain.wallet.CreditResult
import dev.avinya.admob.showcase.domain.wallet.DebitResult
import dev.avinya.admob.showcase.domain.wallet.WalletState
import dev.avinya.admob.showcase.domain.wallet.credit
import dev.avinya.admob.showcase.domain.wallet.debit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistence adapter for the coin wallet.
 *
 * Holds no rules of its own. Every decision lives in
 * [dev.avinya.admob.showcase.domain.wallet] as a pure function; this class
 * only loads state, applies the rule, and writes the outcome back. That split
 * is what lets the economy be tested by comparing values rather than by
 * standing up a database.
 *
 * Idempotency is enforced at both levels on purpose: `recordGrant` inserts
 * with `IGNORE` so a concurrent replay loses the race atomically at the
 * database, and the domain rule then turns that into the right result value.
 */
class WalletRepository(
    private val walletDao: WalletDao,
    private val clock: Clock,
) {

    fun balance(): Flow<Int> = walletDao.balance().map { it ?: 0 }

    suspend fun currentBalance(): Int = walletDao.current()?.coinBalance ?: 0

    suspend fun credit(amount: Int, idempotencyKey: String): CreditResult {
        val now = clock.nowMillis()

        // INSERT OR IGNORE returns -1 when the key already existed. Deciding
        // from the write result rather than from a prior read keeps the guard
        // atomic against a concurrent replay of the same reward callback.
        val alreadyGranted = walletDao.recordGrant(
            RewardGrantEntity(idempotencyKey = idempotencyKey, amount = amount, grantedAt = now),
        ) == -1L

        val state = WalletState(
            balance = currentBalance(),
            grantedKeys = if (alreadyGranted) setOf(idempotencyKey) else emptySet(),
        )

        return when (val result = state.credit(amount, idempotencyKey)) {
            is CreditResult.AlreadyGranted -> result
            is CreditResult.Credited -> {
                walletDao.upsert(
                    WalletEntity(id = 0, coinBalance = result.newBalance, updatedAt = now),
                )
                result
            }
        }
    }

    suspend fun debit(amount: Int): DebitResult {
        val state = WalletState(balance = currentBalance())

        return when (val result = state.debit(amount)) {
            is DebitResult.InsufficientFunds -> result
            is DebitResult.Debited -> {
                walletDao.upsert(
                    WalletEntity(id = 0, coinBalance = result.newBalance, updatedAt = clock.nowMillis()),
                )
                result
            }
        }
    }
}
