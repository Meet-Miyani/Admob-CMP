package dev.avinya.admob.showcase.data.repo

import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.BaseRoomTest
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import dev.avinya.admob.showcase.data.db.getInMemoryDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class WalletTestClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

class WalletRepositoryTest : BaseRoomTest() {

    private fun database(): ShowcaseDatabase =
        getInMemoryDatabaseBuilder()
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun startsAtZero() = runTest {
        val db = database()
        try {
            assertEquals(0, WalletRepository(db.walletDao(), WalletTestClock()).currentBalance())
        } finally {
            db.close()
        }
    }

    @Test
    fun creditsIncreaseTheBalance() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), WalletTestClock())

            assertEquals(CreditResult.Credited(newBalance = 50), repo.credit(50, "grant-1"))
            assertEquals(CreditResult.Credited(newBalance = 100), repo.credit(50, "grant-2"))
        } finally {
            db.close()
        }
    }

    @Test
    fun aReplayedIdempotencyKeyDoesNotDoubleCredit() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), WalletTestClock())
            repo.credit(50, "grant-1")

            assertEquals(CreditResult.AlreadyGranted, repo.credit(50, "grant-1"))
            assertEquals(50, repo.currentBalance())
        } finally {
            db.close()
        }
    }

    @Test
    fun debitsReduceTheBalance() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), WalletTestClock())
            repo.credit(100, "grant-1")

            assertEquals(DebitResult.Debited(newBalance = 40), repo.debit(60))
        } finally {
            db.close()
        }
    }

    @Test
    fun debitingMoreThanTheBalanceFailsAndChangesNothing() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), WalletTestClock())
            repo.credit(30, "grant-1")

            assertEquals(
                DebitResult.InsufficientFunds(balance = 30, required = 50),
                repo.debit(50),
            )
            assertEquals(30, repo.currentBalance())
        } finally {
            db.close()
        }
    }
}
