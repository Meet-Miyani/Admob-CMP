package dev.avinya.admob.showcase.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves Room's KMP codegen produces a database that actually opens.
 *
 * Deliberately the *only* Room test in the module, and deliberately iOS-only.
 *
 * Room validates entities and `@Query` SQL at compile time, so a bad column
 * name or a malformed query is already a build failure — re-asserting that at
 * runtime buys nothing. The one thing compilation cannot prove is that the
 * `@ConstructedBy` / `RoomDatabaseConstructor` wiring yields a working
 * instance, which is what this canary covers.
 *
 * It lives in `iosTest` because iOS's in-memory builder needs no `Context`.
 * The Android equivalent would drag Robolectric and `androidx.test` into the
 * build for the same single assertion — an unreasonable cost for a showcase
 * whose job is to demonstrate the ad SDK, not to re-test Room.
 */
class RoomCodegenCanaryTest {

    @Test
    fun theGeneratedDatabaseOpensAndRoundTripsARow() = runTest {
        val db = Room.inMemoryDatabaseBuilder<ShowcaseDatabase>(
            factory = { ShowcaseDatabaseConstructor.initialize() },
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

        try {
            db.walletDao().upsert(WalletEntity(id = 0, coinBalance = 120, updatedAt = 1L))

            assertEquals(120, db.walletDao().current()?.coinBalance)
        } finally {
            db.close()
        }
    }
}
