package dev.avinya.admob.showcase.data.repo

import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.dao.WalletDao
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface CreditResult {
    data class Credited(val newBalance: Int) : CreditResult

    /** The idempotency key was already recorded. Nothing changed — this is success, not an error. */
    data object AlreadyGranted : CreditResult
}

sealed interface DebitResult {
    data class Debited(val newBalance: Int) : DebitResult
    data class InsufficientFunds(val balance: Int, val required: Int) : DebitResult
}

/**
 * The coin wallet.
 *
 * [credit] is idempotent by design. A rewarded ad's reward callback can be
 * replayed, and crediting twice for one watched ad is the bug this guards.
 */
class WalletRepository(
    private val walletDao: WalletDao,
    private val clock: Clock,
) {

    fun balance(): Flow<Int> = walletDao.balance().map { it ?: 0 }

    suspend fun currentBalance(): Int = walletDao.current()?.coinBalance ?: 0

    suspend fun credit(amount: Int, idempotencyKey: String): CreditResult {
        val now = clock.nowMillis()
        val inserted = walletDao.recordGrant(
            RewardGrantEntity(idempotencyKey = idempotencyKey, amount = amount, grantedAt = now),
        )
        if (inserted == -1L) return CreditResult.AlreadyGranted

        val newBalance = currentBalance() + amount
        walletDao.upsert(WalletEntity(id = 0, coinBalance = newBalance, updatedAt = now))
        return CreditResult.Credited(newBalance)
    }

    suspend fun debit(amount: Int): DebitResult {
        val balance = currentBalance()
        if (balance < amount) return DebitResult.InsufficientFunds(balance = balance, required = amount)

        val newBalance = balance - amount
        walletDao.upsert(WalletEntity(id = 0, coinBalance = newBalance, updatedAt = clock.nowMillis()))
        return DebitResult.Debited(newBalance)
    }
}
