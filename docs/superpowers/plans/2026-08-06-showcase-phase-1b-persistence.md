# Showcase — Phase 1b: Persistence

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 0 spike schema with the real nine-table Room database, seed it with 126 deterministic articles, and expose it through repositories, DataStore settings and a manual `AppGraph`.

**Architecture:** Room holds structured data; DataStore holds preferences only. Repositories return `Result`-shaped outcomes rather than throwing. `AppGraph` is hand-rolled DI constructed once per process, reached through `LocalAppGraph`. The single platform seam is `rememberPlatformStorage()`, resolved composably so no `Context` has to be plumbed through `shared`.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, AGP 9.2.1, Room 2.8.4 + KSP, androidx.sqlite bundled 2.7.0, DataStore 1.2.1, Navigation3 (runtime 1.1.5 / CMP ui 1.1.1), Paging 3.5.0.

**Spec:** [2026-08-06-showcase-app-design.md](../specs/2026-08-06-showcase-app-design.md)

**Prerequisite:** [Phase 1a — App Shell](2026-08-06-showcase-phase-1a-app-shell.md) complete.

**No ads render in this plan.** The app still shows the themed placeholder; what changes is that a seeded database exists behind it.

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Invariant 0 — the SDK does not change.** No file under `admob-cmp/`, `admob-cmp-core/`, `admob-cmp-compose/` or `admob-cmp-gradle-plugin/` may be created, modified or deleted. If the showcase needs something the SDK does not expose: record it in `docs/showcase-sdk-gaps.md` using the format in the spec's Invariant 0, work around it inside `:showcase`, and escalate to the owner. **Stop and ask — do not patch the library.**
- **Kotlin stays at 2.3.20.** Do not bump `kotlin` in `gradle/libs.versions.toml`. The whole build applies one Kotlin plugin version and admob-cmp's frozen ABI plus its experimental `abiValidation` DSL require exactly this version.
- **No dependencies beyond the approved list** in the spec's "Approved dependencies" table. Specifically **not** approved: Koin, Hilt, Ktor, Coil, SQLDelight, kotlinx-datetime, kotlinx-serialization. Adding any requires the owner's consent first.
- **Do not modify** `gradle.properties` or `admob-cmp-gradle-plugin/gradle.properties` `VERSION_NAME`. This is not a release.
- **Do not modify** `.github/workflows/release.yml`. No SDK tests go into CI — standing owner decision.
- **Do not create files under `gradle/`** other than editing `gradle/libs.versions.toml`. No new secrets.
- **Do not commit** `api/*.klib.api` changes. `:showcase` is unpublished; the frozen ABI is unaffected.
- `minSdk` is 26, `compileSdk` 37, `targetSdk` 36, JVM target 11 — read from `libs.versions.toml`, never hardcoded.
- Package root for all new code: `dev.avinya.admob.showcase`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Branch is `feat/showcase-app`. Do not open a PR; the pre-PR protocol in AGENTS.md is a hard stop requiring the owner's explicit confirmation.

### Three corrections to the spec, applied by this plan

The spec was written before the build files were read in detail. These three points supersede it; fold them back into the spec if it is ever revised.

1. **Android test task is `testAndroidHostTest`, not `testDebugUnitTest`.** `:showcase` uses `com.android.kotlin.multiplatform.library` (same as `shared` and `admob-cmp-core`), whose host-test task is `testAndroidHostTest`. `testDebugUnitTest` does not exist for that plugin.
2. **No framework `export` is needed.** The spec says `shared` "exports" `:showcase`. It does not need to. `ContentView.swift` only calls `MainViewControllerKt.MainViewController()`; no Swift code references a `:showcase` type. `shared`'s framework is `isStatic = true`, so showcase code is linked in regardless — `export` only controls generated Obj-C headers. Use plain `implementation(project(":showcase"))`. Simpler, and it keeps the framework header surface unchanged.
3. **`:showcase` must apply the `dev.avinya.ads.admob-cmp` Gradle plugin.** The spec does not mention it. Without it, `:showcase:iosSimulatorArm64Test` fails at link with `Undefined symbols: _OBJC_CLASS_$_GADBannerView`. Supplying GMA/UMP frameworks to Kotlin/Native **test executables** is that plugin's entire purpose — an iOS *app* resolves them from Xcode's SPM packages, but a test executable has no Xcode.

### One open decision for the owner (do not resolve unilaterally)

Nav3's `rememberNavBackStack` requires `NavKey`s to be `@Serializable`, which needs the **kotlinx-serialization** plugin — not on the approved dependency list. The Phase 1c plan therefore uses a plain `mutableStateListOf` backstack, which works fully but **does not survive process death**. Raise this with the owner when Phase 1c completes; do not add kotlinx-serialization without consent.

---

---

## File Structure

**Created:** `data/db/entity/{Content,Wallet,Telemetry}Entities.kt`, `data/db/dao/{Article,Wallet,Telemetry}Dao.kt`, `data/seed/ArticleSeed.kt`, `data/prefs/SettingsRepository.kt`, `data/repo/{Article,Wallet}Repository.kt`, `di/PlatformStorage.kt` (+ android/ios actuals), `di/AppGraph.kt`, and their tests.

**Modified:** `data/db/ShowcaseDatabase.kt` (real entity list), `ShowcaseApp.kt` (provide the graph, seed on first launch), `showcase/build.gradle.kts` (DataStore dependency).

**Deleted:** the Phase 0 spike entity, DAO and test.

---

### Task 1: Real Room schema — entities

Replaces the Task 2 spike entity with the nine tables from the spec.

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/ContentEntities.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/WalletEntities.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/TelemetryEntities.kt`
- Delete: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/SpikeEntity.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/SchemaTest.kt`

**Interfaces:**
- Consumes: `ShowcaseDatabase` from the Phase 0 plan.
- Produces: `ArticleEntity`, `BookmarkEntity`, `ReadingProgressEntity`, `UnlockEntity`, `UnlockSource`, `WalletEntity`, `RewardGrantEntity`, `AdEventEntity`, `PolicyDecisionEntity`, `PaidEventEntity`. Task 2's DAOs and Task 3's repositories consume all of these.

- [ ] **Step 1: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/SchemaTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaTest {

    private fun database(): ShowcaseDatabase =
        Room.inMemoryDatabaseBuilder<ShowcaseDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun opensWithEveryTablePresent() = runTest {
        val db = database()
        try {
            // Opening lazily creates the schema; a trivial read per DAO proves
            // each table exists and matches its entity.
            assertEquals(emptyList(), db.articleDao().allIds())
            assertEquals(null, db.walletDao().current())
            assertEquals(0, db.telemetryDao().adEventCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun walletIsASingleRowKeyedAtZero() = runTest {
        val db = database()
        try {
            db.walletDao().upsert(WalletEntity(id = 0, coinBalance = 120, updatedAt = 1L))
            db.walletDao().upsert(WalletEntity(id = 0, coinBalance = 200, updatedAt = 2L))

            assertEquals(200, db.walletDao().current()?.coinBalance)
        } finally {
            db.close()
        }
    }

    @Test
    fun storesAndReadsBackAnArticle() = runTest {
        val db = database()
        try {
            db.articleDao().insertAll(
                listOf(
                    ArticleEntity(
                        id = "a1",
                        title = "Structured concurrency",
                        author = "R. Elder",
                        body = "para one\n\npara two",
                        section = "Kotlin",
                        publishedAt = 1_700_000_000_000L,
                        readTimeMin = 7,
                        isPremium = false,
                        unlockCostCoins = 0,
                    ),
                )
            )

            assertEquals(listOf("a1"), db.articleDao().allIds())
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ArticleEntity`.

- [ ] **Step 3: Write the content entities**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/ContentEntities.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An article. Body paragraphs are separated by a blank line. */
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val body: String,
    val section: String,
    val publishedAt: Long,
    val readTimeMin: Int,
    val isPremium: Boolean,
    val unlockCostCoins: Int,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookmarkEntity(
    @PrimaryKey val articleId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val articleId: String,
    val scrollFraction: Float,
    val updatedAt: Long,
)

/** How a premium article came to be unlocked. Surfaced in the Library. */
enum class UnlockSource { REWARDED, COINS }

@Entity(
    tableName = "unlocks",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("unlockedAt")],
)
data class UnlockEntity(
    @PrimaryKey val articleId: String,
    val unlockedAt: Long,
    val source: UnlockSource,
)
```

- [ ] **Step 4: Write the wallet entities**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/WalletEntities.kt`:

```kotlin
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
```

- [ ] **Step 5: Write the telemetry entities**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/TelemetryEntities.kt`:

```kotlin
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
```

- [ ] **Step 6: Delete the spike entity and update the database**

```bash
git rm showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/entity/SpikeEntity.kt
git rm showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/SpikeDao.kt
git rm showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/db/RoomSpikeTest.kt
```

Replace `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/ShowcaseDatabase.kt` with:

```kotlin
package dev.avinya.admob.showcase.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import dev.avinya.admob.showcase.data.db.dao.ArticleDao
import dev.avinya.admob.showcase.data.db.dao.TelemetryDao
import dev.avinya.admob.showcase.data.db.dao.WalletDao
import dev.avinya.admob.showcase.data.db.entity.AdEventEntity
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.BookmarkEntity
import dev.avinya.admob.showcase.data.db.entity.PaidEventEntity
import dev.avinya.admob.showcase.data.db.entity.PolicyDecisionEntity
import dev.avinya.admob.showcase.data.db.entity.ReadingProgressEntity
import dev.avinya.admob.showcase.data.db.entity.RewardGrantEntity
import dev.avinya.admob.showcase.data.db.entity.UnlockEntity
import dev.avinya.admob.showcase.data.db.entity.WalletEntity

@Database(
    entities = [
        ArticleEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class,
        UnlockEntity::class,
        WalletEntity::class,
        RewardGrantEntity::class,
        AdEventEntity::class,
        PolicyDecisionEntity::class,
        PaidEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ShowcaseDatabaseConstructor::class)
abstract class ShowcaseDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun walletDao(): WalletDao
    abstract fun telemetryDao(): TelemetryDao
}

/**
 * KMP databases cannot be instantiated reflectively, so Room's KSP processor
 * generates the `actual` per target. Do not hand-write one.
 */
@Suppress("KotlinNoActualForExpect")
expect object ShowcaseDatabaseConstructor : RoomDatabaseConstructor<ShowcaseDatabase> {
    override fun initialize(): ShowcaseDatabase
}
```

The DAOs referenced above are written in Task 2. This task therefore does not compile until Task 2 lands — **complete Tasks 1 and 2 back to back and commit once, at the end of Task 2.**

- [ ] **Step 7: Do not commit yet — continue to Task 2**

---

### Task 2: Room DAOs

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/ArticleDao.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/WalletDao.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/TelemetryDao.kt`

**Interfaces:**
- Consumes: every entity from Task 1.
- Produces: `ArticleDao`, `WalletDao`, `TelemetryDao`. Task 3's repositories consume all three. The Phase 3 plan adds `ArticleDao.pagingSource()`.

- [ ] **Step 1: Write the ArticleDao**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/ArticleDao.kt`:

```kotlin
package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.BookmarkEntity
import dev.avinya.admob.showcase.data.db.entity.ReadingProgressEntity
import dev.avinya.admob.showcase.data.db.entity.UnlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Query("SELECT id FROM articles ORDER BY publishedAt DESC")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun count(): Int

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun byId(id: String): ArticleEntity?

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<ArticleEntity>

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE articleId = :articleId")
    suspend fun progressFor(articleId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE articleId = :articleId")
    suspend fun removeBookmark(articleId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE articleId = :articleId)")
    fun isBookmarked(articleId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addUnlock(unlock: UnlockEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM unlocks WHERE articleId = :articleId)")
    fun isUnlocked(articleId: String): Flow<Boolean>
}
```

- [ ] **Step 2: Write the WalletDao**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/WalletDao.kt`:

```kotlin
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
```

- [ ] **Step 3: Write the TelemetryDao**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/db/dao/TelemetryDao.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the Task 1 schema test**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **PASS**, all three `SchemaTest` cases.

- [ ] **Step 5: Run the iOS tests**

```bash
./gradlew :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 6: Confirm the exported schema was generated**

```bash
ls showcase/schemas/dev.avinya.admob.showcase.data.db.ShowcaseDatabase/
```

Expected: `1.json`.

- [ ] **Step 7: Commit Tasks 1 and 2 together**

```bash
git add -A showcase
git commit -m "$(cat <<'EOF'
feat(showcase): add the real Room schema and DAOs

Nine tables: articles, bookmarks, reading_progress, unlocks, wallet,
reward_grants, ad_events, policy_decisions, paid_events. Replaces the
Phase 0 spike entity.

Log tables are capped at 500 rows and trimmed inside the same
transaction as the insert, so the cap can never be exceeded between
calls. Reward grants insert with IGNORE so a replayed callback is a
no-op rather than a double credit.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Seed content and repositories

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeed.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/ArticleRepository.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepository.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeedTest.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepositoryTest.kt`

**Interfaces:**
- Consumes: DAOs from Task 2; `Clock` from the Phase 1a plan.
- Produces:
  - `object ArticleSeed { fun articles(): List<ArticleEntity> }`
  - `class ArticleRepository(articleDao, clock)` with `suspend fun seedIfEmpty()`, `suspend fun article(id: String): ArticleEntity?`, `suspend fun page(limit: Int, offset: Int): List<ArticleEntity>`, `suspend fun setProgress(articleId: String, fraction: Float)`, `suspend fun progress(articleId: String): Float`, `fun isBookmarked(articleId: String): Flow<Boolean>`, `suspend fun setBookmarked(articleId: String, bookmarked: Boolean)`, `fun isUnlocked(articleId: String): Flow<Boolean>`
  - `class WalletRepository(walletDao, clock)` with `fun balance(): Flow<Int>`, `suspend fun currentBalance(): Int`, `suspend fun credit(amount: Int, idempotencyKey: String): CreditResult`, `suspend fun debit(amount: Int): DebitResult`
  - `sealed interface CreditResult { data class Credited(val newBalance: Int); data object AlreadyGranted }`
  - `sealed interface DebitResult { data class Debited(val newBalance: Int); data class InsufficientFunds(val balance: Int, val required: Int) }`
  - Phase 5's Store screen consumes `WalletRepository` and both result types.

- [ ] **Step 1: Write the failing seed test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeedTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleSeedTest {

    @Test
    fun producesEnoughArticlesForSixPagesOfTwenty() {
        assertTrue(
            ArticleSeed.articles().size >= 120,
            "need >= 120 articles so paging actually pages; got ${ArticleSeed.articles().size}",
        )
    }

    @Test
    fun isDeterministic() {
        assertEquals(ArticleSeed.articles(), ArticleSeed.articles())
    }

    @Test
    fun idsAreUnique() {
        val ids = ArticleSeed.articles().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate article ids in seed")
    }

    @Test
    fun everyArticleHasAtLeastFourParagraphsSoTheInlineNativeAdHasSomewhereToSit() {
        val tooShort = ArticleSeed.articles().filter { it.body.split("\n\n").size < 4 }
        assertEquals(emptyList(), tooShort.map { it.id })
    }

    @Test
    fun someArticlesArePremiumAndAllOfThemCostCoins() {
        val premium = ArticleSeed.articles().filter { it.isPremium }
        assertTrue(premium.isNotEmpty(), "expected some premium articles")
        assertTrue(premium.all { it.unlockCostCoins > 0 }, "premium articles must cost coins")
    }

    @Test
    fun freeArticlesCostNothing() {
        assertTrue(ArticleSeed.articles().filterNot { it.isPremium }.all { it.unlockCostCoins == 0 })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: ArticleSeed`.

- [ ] **Step 3: Write the seed**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/seed/ArticleSeed.kt`:

```kotlin
package dev.avinya.admob.showcase.data.seed

import dev.avinya.admob.showcase.data.db.entity.ArticleEntity

/**
 * Deterministic local content.
 *
 * The showcase has no network layer on purpose: ad behaviour is hard enough
 * to reason about without content loading also being a variable. Same input,
 * same 126 articles, every run, offline.
 */
object ArticleSeed {

    private val sections = listOf("Kotlin", "Compose", "Multiplatform", "Android", "iOS", "Tooling")

    private val topics = listOf(
        "Structured concurrency in practice",
        "What recomposition actually costs",
        "Reading a klib ABI dump",
        "Expect and actual, revisited",
        "Stable types and skippability",
        "Coroutine cancellation you can trust",
        "Paging without the pain",
        "A tour of the memory model",
        "Designing for two platforms at once",
        "When to reach for a state machine",
        "Build times are a feature",
        "Snapshot state, explained",
        "Interop that does not leak",
        "Testing without an emulator",
        "Dependency injection, by hand",
        "Immutability and its discontents",
        "Lifecycles across platforms",
        "Draw, layout, measure",
        "Persistence that survives a refactor",
        "Naming things, still hard",
        "The cost of an abstraction",
    )

    private val authors = listOf("R. Elder", "M. Okonkwo", "S. Lindqvist", "A. Bhatt", "J. Moreau", "T. Nakamura")

    private fun body(topic: String, section: String, index: Int): String = buildString {
        append("$topic is one of those areas where the obvious approach and the correct ")
        append("approach diverge quietly, and the divergence only shows up under load.\n\n")
        append("Most $section code starts simple. A single call site, a single owner, ")
        append("no contention. The trouble begins when the second caller arrives and ")
        append("nobody has decided who owns the state.\n\n")
        append("The rule that has held up best: make the boundary explicit before you ")
        append("make it fast. An explicit boundary can be optimised later. An implicit ")
        append("one has to be discovered first, usually during an incident.\n\n")
        append("There is a version of this argument that goes too far, and it ends in ")
        append("six layers of indirection for a two-line function. Judgement number ")
        append("$index: does the abstraction pay for the reading cost it imposes?\n\n")
        append("If it does not, delete it. That is the whole technique.")
    }

    /** 126 articles: 21 topics across 6 sections. Every 7th is premium. */
    fun articles(): List<ArticleEntity> = buildList {
        var index = 0
        sections.forEach { section ->
            topics.forEach { topic ->
                val premium = index % 7 == 6
                add(
                    ArticleEntity(
                        id = "article-${index.toString().padStart(3, '0')}",
                        title = topic,
                        author = authors[index % authors.size],
                        body = body(topic, section, index),
                        section = section,
                        // Fixed base epoch minus a per-index offset: deterministic and
                        // strictly descending, so feed order is stable across runs.
                        publishedAt = BASE_PUBLISHED_AT - index * ONE_HOUR_MILLIS,
                        readTimeMin = 4 + index % 9,
                        isPremium = premium,
                        unlockCostCoins = if (premium) 50 else 0,
                    )
                )
                index++
            }
        }
    }

    private const val BASE_PUBLISHED_AT = 1_767_225_600_000L // 2026-01-01T00:00:00Z
    private const val ONE_HOUR_MILLIS = 3_600_000L
}
```

- [ ] **Step 4: Run to verify the seed test passes**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*ArticleSeedTest*' --no-configuration-cache
```

Expected: **PASS**, all six.

- [ ] **Step 5: Write the failing wallet test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepositoryTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.repo

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FixedClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

class WalletRepositoryTest {

    private fun database(): ShowcaseDatabase =
        Room.inMemoryDatabaseBuilder<ShowcaseDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @Test
    fun startsAtZero() = runTest {
        val db = database()
        try {
            assertEquals(0, WalletRepository(db.walletDao(), FixedClock()).currentBalance())
        } finally {
            db.close()
        }
    }

    @Test
    fun creditsIncreaseTheBalance() = runTest {
        val db = database()
        try {
            val repo = WalletRepository(db.walletDao(), FixedClock())

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
            val repo = WalletRepository(db.walletDao(), FixedClock())
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
            val repo = WalletRepository(db.walletDao(), FixedClock())
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
            val repo = WalletRepository(db.walletDao(), FixedClock())
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
```

- [ ] **Step 6: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*WalletRepositoryTest*' --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: WalletRepository`.

- [ ] **Step 7: Write WalletRepository**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/WalletRepository.kt`:

```kotlin
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
```

- [ ] **Step 8: Write ArticleRepository**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/repo/ArticleRepository.kt`:

```kotlin
package dev.avinya.admob.showcase.data.repo

import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.data.db.dao.ArticleDao
import dev.avinya.admob.showcase.data.db.entity.ArticleEntity
import dev.avinya.admob.showcase.data.db.entity.BookmarkEntity
import dev.avinya.admob.showcase.data.db.entity.ReadingProgressEntity
import dev.avinya.admob.showcase.data.seed.ArticleSeed
import kotlinx.coroutines.flow.Flow

/** Reads and writes article content, bookmarks and reading progress. */
class ArticleRepository(
    private val articleDao: ArticleDao,
    private val clock: Clock,
) {

    /** Populates the database on first launch. A no-op afterwards. */
    suspend fun seedIfEmpty() {
        if (articleDao.count() == 0) {
            articleDao.insertAll(ArticleSeed.articles())
        }
    }

    suspend fun article(id: String): ArticleEntity? = articleDao.byId(id)

    suspend fun page(limit: Int, offset: Int): List<ArticleEntity> = articleDao.page(limit, offset)

    suspend fun setProgress(articleId: String, fraction: Float) {
        articleDao.upsertProgress(
            ReadingProgressEntity(
                articleId = articleId,
                scrollFraction = fraction.coerceIn(0f, 1f),
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    suspend fun progress(articleId: String): Float =
        articleDao.progressFor(articleId)?.scrollFraction ?: 0f

    fun isBookmarked(articleId: String): Flow<Boolean> = articleDao.isBookmarked(articleId)

    suspend fun setBookmarked(articleId: String, bookmarked: Boolean) {
        if (bookmarked) {
            articleDao.addBookmark(BookmarkEntity(articleId = articleId, createdAt = clock.nowMillis()))
        } else {
            articleDao.removeBookmark(articleId)
        }
    }

    fun isUnlocked(articleId: String): Flow<Boolean> = articleDao.isUnlocked(articleId)
}
```

- [ ] **Step 9: Run all tests on both platforms**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 10: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add deterministic seed content and repositories

126 offline articles so Paging has six real pages, generated
deterministically — no network layer anywhere in the showcase.

WalletRepository.credit is idempotent: the grant row inserts with
IGNORE, so a replayed rewarded-ad callback returns AlreadyGranted rather
than crediting twice.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: DataStore settings

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepository.kt`
- Test: `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `ThemeMode` from the Phase 1a plan.
- Produces: `class SettingsRepository(dataStore: DataStore<Preferences>)` exposing `themeMode: Flow<ThemeMode>`, `onboardingComplete: Flow<Boolean>`, `inspectorEnabled: Flow<Boolean>`, `adsMasterSwitch: Flow<Boolean>`, and setters for each. Task 5 constructs it; the Phase 2 and Phase 6 plans consume it.

- [ ] **Step 1: Add the dependency**

In `showcase/build.gradle.kts`, add to `commonMain` dependencies:

```kotlin
                implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 2: Write the failing test**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepositoryTest.kt`:

```kotlin
package dev.avinya.admob.showcase.data.prefs

import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    @Test
    fun defaultsBeforeAnythingIsWritten() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        assertEquals(ThemeMode.System, repo.themeMode.first())
        assertFalse(repo.onboardingComplete.first())
        assertTrue(repo.inspectorEnabled.first())
        assertTrue(repo.adsMasterSwitch.first())
    }

    @Test
    fun persistsThemeMode() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        repo.setThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, repo.themeMode.first())
    }

    @Test
    fun persistsOnboardingCompletion() = runTest {
        val repo = SettingsRepository(inMemoryPreferencesDataStore())

        repo.setOnboardingComplete(true)

        assertTrue(repo.onboardingComplete.first())
    }

    @Test
    fun anUnrecognisedStoredThemeFallsBackToTheDefault() = runTest {
        val store = inMemoryPreferencesDataStore()
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply { set(SettingsKeys.ThemeMode, "Sepia") }
        }

        assertEquals(ThemeMode.System, SettingsRepository(store).themeMode.first())
    }
}
```

The helper `inMemoryPreferencesDataStore()` is written in Step 4.

- [ ] **Step 3: Run to verify it fails**

```bash
./gradlew :showcase:testAndroidHostTest --tests '*SettingsRepositoryTest*' --no-configuration-cache
```

Expected: **FAIL** — `Unresolved reference: SettingsRepository`.

- [ ] **Step 4: Write the test helper**

Create `showcase/src/commonTest/kotlin/dev/avinya/admob/showcase/data/prefs/InMemoryPreferences.kt`:

```kotlin
package dev.avinya.admob.showcase.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A `DataStore<Preferences>` held entirely in memory.
 *
 * Avoids touching the filesystem from tests, which keeps the same test body
 * running unchanged on the Android host and on iOS.
 */
internal fun inMemoryPreferencesDataStore(): DataStore<Preferences> = InMemoryPreferencesDataStore()

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        val updated = transform(state.value)
        state.value = updated
        updated
    }
}
```

- [ ] **Step 5: Write SettingsRepository**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/data/prefs/SettingsRepository.kt`:

```kotlin
package dev.avinya.admob.showcase.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.avinya.admob.showcase.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object SettingsKeys {
    val ThemeMode = stringPreferencesKey("theme_mode")
    val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
    val ConsentDebugGeography = stringPreferencesKey("consent_debug_geography")
    val InspectorEnabled = booleanPreferencesKey("inspector_enabled")
    val AdsMasterSwitch = booleanPreferencesKey("ads_master_switch")
}

/**
 * User preferences. Structured data lives in Room; this holds only settings.
 *
 * [adsMasterSwitch] is a local kill switch. Turning it off suppresses every
 * placement in the app without touching any SDK or consent state — useful for
 * demoing the app itself, and for proving the app is fully usable ad-free.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        // An unrecognised stored value must not crash the app on launch.
        ThemeMode.entries.firstOrNull { it.name == prefs[SettingsKeys.ThemeMode] } ?: ThemeMode.Default
    }

    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[SettingsKeys.OnboardingComplete] ?: false }

    val consentDebugGeography: Flow<String?> =
        dataStore.data.map { it[SettingsKeys.ConsentDebugGeography] }

    val inspectorEnabled: Flow<Boolean> =
        dataStore.data.map { it[SettingsKeys.InspectorEnabled] ?: true }

    val adsMasterSwitch: Flow<Boolean> =
        dataStore.data.map { it[SettingsKeys.AdsMasterSwitch] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[SettingsKeys.ThemeMode] = mode.name }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[SettingsKeys.OnboardingComplete] = complete }
    }

    suspend fun setConsentDebugGeography(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(SettingsKeys.ConsentDebugGeography)
            else prefs[SettingsKeys.ConsentDebugGeography] = value
        }
    }

    suspend fun setInspectorEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.InspectorEnabled] = enabled }
    }

    suspend fun setAdsMasterSwitch(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.AdsMasterSwitch] = enabled }
    }
}
```

- [ ] **Step 6: Run tests on both platforms**

```bash
./gradlew :showcase:testAndroidHostTest :showcase:iosSimulatorArm64Test --no-configuration-cache
```

Expected: **PASS**.

- [ ] **Step 7: Commit**

```bash
git add showcase
git commit -m "$(cat <<'EOF'
feat(showcase): add DataStore-backed settings

Theme, onboarding, consent debug geography, inspector visibility and a
local ads kill switch. An unrecognised stored theme falls back to the
default rather than crashing on launch.

Tests use an in-memory DataStore so the same bodies run on the Android
host and iOS without touching the filesystem.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: AppGraph and the platform storage seam

**Files:**
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.kt`
- Create: `showcase/src/androidMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.android.kt`
- Create: `showcase/src/iosMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.ios.kt`
- Create: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/AppGraph.kt`
- Modify: `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt`

**Interfaces:**
- Consumes: the theme and `Clock` from the Phase 1a plan; everything from Tasks 1–4 of this plan.
- Produces:
  - `interface PlatformStorage { fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase>; fun dataStorePath(): String }`
  - `@Composable expect fun rememberPlatformStorage(): PlatformStorage`
  - `class AppGraph(storage: PlatformStorage)` exposing `database`, `settings`, `articles`, `wallet`, `clock`, `appScope`
  - `val LocalAppGraph: ProvidableCompositionLocal<AppGraph>`
  - Every feature ViewModel factory in the Phase 2–6 plans reads its dependencies from `LocalAppGraph.current`.

- [ ] **Step 1: Write the common seam**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase

/**
 * The one platform-specific thing the showcase needs: where files live.
 *
 * Android needs a `Context`; iOS needs the documents directory. Resolving
 * this composably rather than plumbing a `Context` through `shared` is what
 * keeps `androidApp` and the iOS framework free of structural changes.
 */
interface PlatformStorage {
    fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase>
    fun dataStorePath(): String
}

@Composable
expect fun rememberPlatformStorage(): PlatformStorage

internal const val SHOWCASE_DATABASE_FILE = "showcase.db"
internal const val SHOWCASE_PREFERENCES_FILE = "showcase.preferences_pb"
```

- [ ] **Step 2: Write the Android actual**

Create `showcase/src/androidMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.android.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase

private class AndroidPlatformStorage(private val context: Context) : PlatformStorage {

    override fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
        Room.databaseBuilder<ShowcaseDatabase>(
            context = context,
            name = context.getDatabasePath(SHOWCASE_DATABASE_FILE).absolutePath,
        )

    override fun dataStorePath(): String =
        context.filesDir.resolve(SHOWCASE_PREFERENCES_FILE).absolutePath
}

@Composable
actual fun rememberPlatformStorage(): PlatformStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidPlatformStorage(context) }
}
```

- [ ] **Step 3: Write the iOS actual**

Create `showcase/src/iosMain/kotlin/dev/avinya/admob/showcase/di/PlatformStorage.ios.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private class IosPlatformStorage : PlatformStorage {

    override fun databaseBuilder(): RoomDatabase.Builder<ShowcaseDatabase> =
        Room.databaseBuilder<ShowcaseDatabase>(name = documentsPath(SHOWCASE_DATABASE_FILE))

    override fun dataStorePath(): String = documentsPath(SHOWCASE_PREFERENCES_FILE)

    @OptIn(ExperimentalForeignApi::class)
    private fun documentsPath(fileName: String): String {
        val documents: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(documents?.path) { "iOS documents directory unavailable" } + "/" + fileName
    }
}

@Composable
actual fun rememberPlatformStorage(): PlatformStorage = remember { IosPlatformStorage() }
```

- [ ] **Step 4: Write the AppGraph**

Create `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/di/AppGraph.kt`:

```kotlin
package dev.avinya.admob.showcase.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.avinya.admob.showcase.core.time.Clock
import dev.avinya.admob.showcase.core.time.SystemClock
import dev.avinya.admob.showcase.data.db.ShowcaseDatabase
import dev.avinya.admob.showcase.data.prefs.SettingsRepository
import dev.avinya.admob.showcase.data.repo.ArticleRepository
import dev.avinya.admob.showcase.data.repo.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.files.Path

/**
 * Manual dependency graph, constructed once per process.
 *
 * Hand-rolled rather than Koin or Hilt: the graph is small, and a demo whose
 * point is to be read benefits from wiring you can follow by eye.
 */
class AppGraph(storage: PlatformStorage) {

    val clock: Clock = SystemClock

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ShowcaseDatabase = storage.databaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val preferences: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath { Path(storage.dataStorePath()) }

    val settings: SettingsRepository = SettingsRepository(preferences)

    val articles: ArticleRepository = ArticleRepository(database.articleDao(), clock)

    val wallet: WalletRepository = WalletRepository(database.walletDao(), clock)
}

/**
 * Set by [dev.avinya.admob.showcase.ShowcaseApp]. Reading it outside that
 * subtree is a programming error, so there is no default.
 */
val LocalAppGraph: ProvidableCompositionLocal<AppGraph> = compositionLocalOf {
    error("LocalAppGraph accessed outside ShowcaseApp")
}

@Composable
internal fun rememberAppGraph(): AppGraph {
    val storage = rememberPlatformStorage()
    return remember(storage) { AppGraph(storage) }
}
```

- [ ] **Step 5: Resolve DataStore's path type before compiling**

DataStore's `createWithPath` has taken an `okio.Path` in some releases and a `kotlinx.io.files.Path` in others. Determine which applies to 1.2.1 rather than guessing — a wrong import here produces a confusing "unresolved reference" that looks like a missing dependency.

```bash
./gradlew :showcase:dependencies --configuration androidMainCompileClasspath --no-configuration-cache | grep -iE "okio|kotlinx-io"
```

- If the output lists **`com.squareup.okio:okio`**, use `okio.Path.Companion.toPath()`:

```kotlin
import okio.Path.Companion.toPath

private val preferences: DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { storage.dataStorePath().toPath() }
```

- If it lists **`org.jetbrains.kotlinx:kotlinx-io-core`**, keep the `kotlinx.io.files.Path` form already written in Step 4.

Either way the transitive dependency is DataStore's own. **Do not add okio or kotlinx-io to the version catalog** — neither is separately approved, and needing an explicit declaration would mean something else is wrong.

- [ ] **Step 6: Wire the graph into ShowcaseApp**

Replace `showcase/src/commonMain/kotlin/dev/avinya/admob/showcase/ShowcaseApp.kt` with:

```kotlin
package dev.avinya.admob.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.avinya.admob.showcase.di.LocalAppGraph
import dev.avinya.admob.showcase.di.rememberAppGraph
import dev.avinya.admob.showcase.ui.theme.ShowcaseTheme
import dev.avinya.admob.showcase.ui.theme.ThemeMode

/**
 * Root of the showcase app and the only public composable `:showcase` exposes.
 *
 * `shared` calls this from its `PlatformAdDemo` actual on Android and iOS;
 * desktop and web keep rendering `UnsupportedAdPlatform()`.
 */
@Composable
fun ShowcaseApp() {
    val graph = rememberAppGraph()
    val themeMode by graph.settings.themeMode.collectAsState(initial = ThemeMode.Default)

    LaunchedEffect(graph) { graph.articles.seedIfEmpty() }

    CompositionLocalProvider(LocalAppGraph provides graph) {
        ShowcaseTheme(themeMode = themeMode) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Showcase app — foundation")
            }
        }
    }
}
```

- [ ] **Step 7: Verify both platforms compile**

```bash
./gradlew :showcase:compileKotlinIosArm64 :showcase:testAndroidHostTest --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run on an emulator and confirm the database is created**

```bash
./gradlew :androidApp:installDebug
adb shell run-as dev.avinya.admob.cmp ls databases/
```

Expected: `showcase.db` present after launching the app.

- [ ] **Step 9: Commit**

```bash
git add showcase/src
git commit -m "$(cat <<'EOF'
feat(showcase): add AppGraph and the platform storage seam

rememberPlatformStorage() is the only expect/actual in the module: Android
resolves paths from LocalContext, iOS from NSFileManager. Resolving it
composably rather than plumbing a Context through shared is what keeps
androidApp and the iOS framework structurally unchanged.

Manual DI, no Koin or Hilt — the graph is small and readable by eye,
which is the point of a showcase.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

---

## Exit criteria

- [ ] All nine tables exist and `showcase/schemas/.../1.json` reflects them
- [ ] `showcase.db` is created on first launch and seeded with 126 articles
- [ ] `SchemaTest`, `ArticleSeedTest`, `WalletRepositoryTest`, `SettingsRepositoryTest` pass on the Android host and iOS
- [ ] A replayed idempotency key returns `CreditResult.AlreadyGranted` and does not change the balance
- [ ] `git diff --stat master -- 'admob-cmp*'` is empty

---

## Next plan

**Phase 1c — Nav shell** (`2026-08-06-showcase-phase-1c-nav-shell.md`): the Nav3 shell with four tabs, and wiring `:showcase` tests into `scripts/release-readiness.sh`.
