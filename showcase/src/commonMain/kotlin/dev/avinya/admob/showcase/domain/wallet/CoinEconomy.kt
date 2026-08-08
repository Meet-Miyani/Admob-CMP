package dev.avinya.admob.showcase.domain.wallet

/**
 * The coin economy's rules, as pure functions over values.
 *
 * These are deliberately free of Room, coroutines and any platform API. The
 * rule this file exists to pin — **a rewarded ad credits exactly once, no
 * matter how many times its callback fires** — is the single most important
 * correctness property in the showcase, so it is expressed as something that
 * can be tested by comparing two values rather than by standing up a
 * database.
 *
 * [WalletRepository][dev.avinya.admob.showcase.data.repo.WalletRepository] is
 * the thin persistence adapter around these functions: load state, apply the
 * rule, write the outcome back.
 */

/** Everything the coin rules need to know, with no storage attached. */
data class WalletState(
    val balance: Int,
    val grantedKeys: Set<String> = emptySet(),
)

sealed interface CreditResult {
    data class Credited(val newBalance: Int) : CreditResult

    /**
     * The idempotency key was already recorded, so nothing changed.
     *
     * This is a **success**, not an error: a replayed reward callback means
     * the user already got paid, not that something went wrong.
     */
    data object AlreadyGranted : CreditResult
}

sealed interface DebitResult {
    data class Debited(val newBalance: Int) : DebitResult
    data class InsufficientFunds(val balance: Int, val required: Int) : DebitResult
}

/**
 * Credits [amount] coins unless [idempotencyKey] has already been granted.
 *
 * The key exists because a rewarded ad's `onReward` callback is not
 * guaranteed to fire exactly once. Crediting on every callback — or, worse,
 * crediting when `show()` returns rather than when the reward callback fires —
 * pays a user who dismissed the ad early. Both are the classic rewarded-ads
 * bugs, and this function is where the showcase refuses to commit them.
 */
fun WalletState.credit(amount: Int, idempotencyKey: String): CreditResult =
    if (idempotencyKey in grantedKeys) {
        CreditResult.AlreadyGranted
    } else {
        CreditResult.Credited(newBalance = balance + amount)
    }

/** Spends [amount] coins, refusing rather than going negative. */
fun WalletState.debit(amount: Int): DebitResult =
    if (balance < amount) {
        DebitResult.InsufficientFunds(balance = balance, required = amount)
    } else {
        DebitResult.Debited(newBalance = balance - amount)
    }
