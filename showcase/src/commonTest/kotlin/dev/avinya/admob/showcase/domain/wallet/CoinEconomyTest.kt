package dev.avinya.admob.showcase.domain.wallet

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The coin economy's rules. No database, no coroutines, no platform runtime —
 * these are the properties a consumer integrating rewarded ads has to get
 * right, so they are tested as values.
 */
class CoinEconomyTest {

    @Test
    fun startsAtZero() {
        assertEquals(0, WalletState(balance = 0).balance)
    }

    @Test
    fun creditsIncreaseTheBalance() {
        assertEquals(
            CreditResult.Credited(newBalance = 50),
            WalletState(balance = 0).credit(50, "grant-1"),
        )
        assertEquals(
            CreditResult.Credited(newBalance = 100),
            WalletState(balance = 50).credit(50, "grant-2"),
        )
    }

    @Test
    fun aReplayedIdempotencyKeyDoesNotDoubleCredit() {
        val state = WalletState(balance = 50, grantedKeys = setOf("grant-1"))

        assertEquals(CreditResult.AlreadyGranted, state.credit(50, "grant-1"))
    }

    @Test
    fun aDifferentKeyStillCreditsWhenOthersAreAlreadyGranted() {
        val state = WalletState(balance = 50, grantedKeys = setOf("grant-1"))

        assertEquals(CreditResult.Credited(newBalance = 100), state.credit(50, "grant-2"))
    }

    @Test
    fun debitsReduceTheBalance() {
        assertEquals(
            DebitResult.Debited(newBalance = 40),
            WalletState(balance = 100).debit(60),
        )
    }

    @Test
    fun debitingMoreThanTheBalanceFailsAndReportsBothNumbers() {
        assertEquals(
            DebitResult.InsufficientFunds(balance = 30, required = 50),
            WalletState(balance = 30).debit(50),
        )
    }

    @Test
    fun spendingExactlyTheBalanceIsAllowed() {
        assertEquals(
            DebitResult.Debited(newBalance = 0),
            WalletState(balance = 50).debit(50),
        )
    }
}
