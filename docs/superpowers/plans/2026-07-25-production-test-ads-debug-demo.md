# Production-Pattern Test Ads Debug Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android and iOS demo apps launch directly into the existing AdMob debug console using official Google test inventory and a production-pattern consent, tracking, initialization, readiness, lifecycle, and failure flow.

**Architecture:** Keep `shared/commonMain` free of Android/iOS-only AdMob dependencies. Add an `adCapableMain` intermediate source set shared by Android and iOS; it owns one `PlatformAdDemo` actual, the test-only configuration, and the startup state UI. JVM, JS, and Wasm provide no-op UI actuals. The iOS application links the exact SPM versions used by the Kotlin/Native bindings and declares the required host metadata.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1, Android GMA Next-Gen 1.2.1, iOS Google Mobile Ads 13.5.0, iOS UMP 3.1.0, Kotlin coroutines 1.11.0, Xcode Swift Package Manager.

## Global Constraints

- Use only Google's sample app IDs and `AdDebugCatalog.Test`; never add a production ID or arbitrary-ID override.
- Android sample app ID: `ca-app-pub-3940256099942544~3347511713`.
- iOS sample app ID: `ca-app-pub-3940256099942544~1458002511`.
- Every format placement remains protected by `AdPlacement.strictTestMode = true`.
- UMP consent runs before GMA initialization; iOS tracking authorization runs from the `BeforeMobileAdsInitialize` hook, after consent and before the first request.
- Use the process-wide manager returned by `rememberAdManager()`; never construct or retain a second manager.
- Do not compose `AdDebugScreen` until `AdManagerStatus.Ready`.
- Cancellation propagates; initialization errors are visible and retryable.
- `shared/commonMain`, JVM, JS, and Wasm must not depend on `:admob-cmp-core` or `:admob-cmp-compose`.
- Pin the iOS host SPM packages exactly to GMA `13.5.0` and UMP `3.1.0`, matching the bound XCFramework headers.
- Preserve committed release signing and the existing library ABI files.
- Use `==` for Kotlin/Native `data object` status checks; do not use `is AdManagerStatus.Ready`-style checks.
- Do not add automatic app-open presentation. The debug screen keeps manual load/show controls.

---

## Execution preflight

- [ ] Confirm execution is isolated with `superpowers:using-git-worktrees`.

- [ ] Confirm the branch starts from the approved design commit:

```bash
git log -1 --oneline
```

Expected: `46b9d50 docs: design production-pattern test ads demo`.

- [ ] Confirm the only pre-existing untracked file in the main checkout is not carried into the feature:

```bash
git status --short
```

Expected in the main checkout: `?? .codegraph/daemon.pid`. Leave it untouched.

- [ ] Run the current shared target baseline:

```bash
./gradlew \
  :shared:testAndroidHostTest \
  :shared:iosSimulatorArm64Test \
  :shared:compileKotlinJvm \
  :shared:compileKotlinJs \
  :shared:compileKotlinWasmJs \
  :androidApp:compileDebugKotlin \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 1: Add the Android/iOS demo source-set boundary and tested startup model

**Files:**
- Modify: `shared/build.gradle.kts`
- Create: `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartup.kt`
- Create: `shared/src/adCapableTest/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartupTest.kt`

**Interfaces:**
- Consumes: `AdConfig`, `AdInitializationHook`, `AdInitializationPhase`, `AdManagerStatus`, and `AdError` from `:admob-cmp-core`.
- Produces: `demoTestAdConfig(trackingHook: AdInitializationHook): AdConfig`, `TrackingAuthorizationHook`, `DemoAdStartupUiState`, and `AdManagerStatus.toDemoAdStartupUiState()`.

- [ ] **Step 1: Introduce `adCapableMain` and `adCapableTest`**

Replace the existing `sourceSets` block in `shared/build.gradle.kts` with:

```kotlin
sourceSets {
    val commonMain by getting {
        dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
    }
    val commonTest by getting {
        dependencies {
            implementation(libs.kotlin.test)
        }
    }

    val adCapableMain by creating {
        dependsOn(commonMain)
        dependencies {
            implementation(project(":admob-cmp-compose"))
        }
    }
    val adCapableTest by creating {
        dependsOn(commonTest)
        dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    val androidMain by getting {
        dependsOn(adCapableMain)
        dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
    val androidHostTest by getting {
        dependsOn(adCapableTest)
    }
    val iosMain by getting {
        dependsOn(adCapableMain)
        dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
    val iosTest by getting {
        dependsOn(adCapableTest)
    }
}
```

This moves the `:admob-cmp-compose` dependency out of duplicate leaf declarations and makes the same source-compatible host compile for Android and iOS only.

- [ ] **Step 2: Write the failing startup-model tests**

Create `shared/src/adCapableTest/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartupTest.kt`:

```kotlin
package dev.avinya.admob.cmp.demo

import avinya.tech.yt.ads.AdError
import avinya.tech.yt.ads.AdInitializationPhase
import avinya.tech.yt.ads.AdManagerStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoAdStartupTest {
    @Test
    fun `demo config uses only official sample app ids`() {
        val config = demoTestAdConfig(TrackingAuthorizationHook {})

        assertEquals("ca-app-pub-3940256099942544~3347511713", config.androidAppId)
        assertEquals("ca-app-pub-3940256099942544~1458002511", config.iosAppId)
        assertTrue(config.testMode)
        assertTrue(config.testDeviceIds.isEmpty())
        assertEquals(1, config.initializationHooks.size)
    }

    @Test
    fun `tracking hook runs only immediately before mobile ads initialization`() = runTest {
        var requestCount = 0
        val hook = TrackingAuthorizationHook { requestCount += 1 }
        val config = demoTestAdConfig(hook)

        hook.onPhase(AdInitializationPhase.BeforeConsentRequest, config)
        assertEquals(0, requestCount)

        hook.onPhase(AdInitializationPhase.BeforeMobileAdsInitialize, config)
        assertEquals(1, requestCount)

        hook.onPhase(AdInitializationPhase.AfterMobileAdsInitialize, config)
        assertEquals(1, requestCount)
    }

    @Test
    fun `manager status maps to deterministic startup ui`() {
        assertEquals(
            DemoAdStartupUiState.Starting,
            AdManagerStatus.Idle.toDemoAdStartupUiState(),
        )
        assertEquals(
            DemoAdStartupUiState.Starting,
            AdManagerStatus.Initializing.toDemoAdStartupUiState(),
        )
        assertEquals(
            DemoAdStartupUiState.Ready,
            AdManagerStatus.Ready.toDemoAdStartupUiState(),
        )
        assertEquals(
            DemoAdStartupUiState.ConsentRequired,
            AdManagerStatus.ConsentRequired.toDemoAdStartupUiState(),
        )

        val retryableFailure = AdManagerStatus.Failed(
            error = AdError.message("network unavailable"),
            retryable = true,
        ).toDemoAdStartupUiState()
        assertEquals(
            DemoAdStartupUiState.Failed("network unavailable", retryable = true),
            retryableFailure,
        )
        assertTrue((retryableFailure as DemoAdStartupUiState.Failed).retryable)

        val disabled = AdManagerStatus.Disabled("disabled by host").toDemoAdStartupUiState()
        assertEquals(
            DemoAdStartupUiState.Failed("disabled by host", retryable = false),
            disabled,
        )
        assertFalse((disabled as DemoAdStartupUiState.Failed).retryable)
    }
}
```

- [ ] **Step 3: Run the focused test and verify RED**

```bash
./gradlew \
  :shared:testAndroidHostTest \
  --tests 'dev.avinya.admob.cmp.demo.DemoAdStartupTest' \
  --rerun-tasks \
  --console=plain
```

Expected: compilation fails because `demoTestAdConfig`, `TrackingAuthorizationHook`, `DemoAdStartupUiState`, and `toDemoAdStartupUiState` do not exist.

- [ ] **Step 4: Implement the minimal startup model**

Create `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartup.kt`:

```kotlin
package dev.avinya.admob.cmp.demo

import avinya.tech.yt.ads.AdConfig
import avinya.tech.yt.ads.AdInitializationHook
import avinya.tech.yt.ads.AdInitializationPhase
import avinya.tech.yt.ads.AdManagerStatus

internal const val DEMO_ANDROID_APP_ID: String =
    "ca-app-pub-3940256099942544~3347511713"
internal const val DEMO_IOS_APP_ID: String =
    "ca-app-pub-3940256099942544~1458002511"

internal fun demoTestAdConfig(
    trackingHook: AdInitializationHook,
): AdConfig = AdConfig(
    androidAppId = DEMO_ANDROID_APP_ID,
    iosAppId = DEMO_IOS_APP_ID,
    testMode = true,
    initializationHooks = listOf(trackingHook),
)

internal class TrackingAuthorizationHook(
    private val requestAuthorization: suspend () -> Unit,
) : AdInitializationHook {
    override suspend fun onPhase(
        phase: AdInitializationPhase,
        config: AdConfig,
    ) {
        if (phase == AdInitializationPhase.BeforeMobileAdsInitialize) {
            requestAuthorization()
        }
    }
}

internal sealed interface DemoAdStartupUiState {
    data object Starting : DemoAdStartupUiState
    data object Ready : DemoAdStartupUiState
    data object ConsentRequired : DemoAdStartupUiState
    data class Failed(
        val message: String,
        val retryable: Boolean,
    ) : DemoAdStartupUiState
}

internal fun AdManagerStatus.toDemoAdStartupUiState(): DemoAdStartupUiState = when {
    this == AdManagerStatus.Idle || this == AdManagerStatus.Initializing ->
        DemoAdStartupUiState.Starting
    this == AdManagerStatus.Ready ->
        DemoAdStartupUiState.Ready
    this == AdManagerStatus.ConsentRequired ->
        DemoAdStartupUiState.ConsentRequired
    this is AdManagerStatus.Failed ->
        DemoAdStartupUiState.Failed(error.message, retryable)
    this is AdManagerStatus.Disabled ->
        DemoAdStartupUiState.Failed(reason, retryable = false)
    else ->
        DemoAdStartupUiState.Failed("Unknown SDK state.", retryable = true)
}
```

- [ ] **Step 5: Run the startup tests on both ad-capable targets**

```bash
./gradlew \
  :shared:testAndroidHostTest \
  :shared:iosSimulatorArm64Test \
  --rerun-tasks \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`; `DemoAdStartupTest` passes on Android host and iOS simulator.

- [ ] **Step 6: Confirm unsupported targets still resolve without AdMob**

```bash
./gradlew \
  :shared:compileKotlinJvm \
  :shared:compileKotlinJs \
  :shared:compileKotlinWasmJs \
  --rerun-tasks \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Task 1**

```bash
git add \
  shared/build.gradle.kts \
  shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartup.kt \
  shared/src/adCapableTest/kotlin/dev/avinya/admob/cmp/demo/DemoAdStartupTest.kt
git commit -m "feat: add production-pattern demo startup model"
```

---

### Task 2: Launch the existing debug console directly and remove the obsolete demo seam

**Files:**
- Modify: `shared/src/commonMain/kotlin/dev/avinya/admob/cmp/App.kt`
- Create: `shared/src/commonMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.kt`
- Create: `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt`
- Create: `shared/src/jvmMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.jvm.kt`
- Create: `shared/src/jsMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.js.kt`
- Create: `shared/src/wasmJsMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.wasmJs.kt`
- Modify: `androidApp/src/main/kotlin/dev/avinya/admob/cmp/MainActivity.kt`
- Delete: `shared/src/commonMain/kotlin/dev/avinya/admob/cmp/ads/AdController.kt`
- Delete: `shared/src/androidMain/kotlin/dev/avinya/admob/cmp/ads/AdController.android.kt`
- Delete: `shared/src/iosMain/kotlin/dev/avinya/admob/cmp/ads/AdController.ios.kt`
- Delete: `shared/src/jvmMain/kotlin/dev/avinya/admob/cmp/ads/AdController.jvm.kt`
- Delete: `shared/src/jsMain/kotlin/dev/avinya/admob/cmp/ads/AdController.js.kt`
- Delete: `shared/src/wasmJsMain/kotlin/dev/avinya/admob/cmp/ads/AdController.wasmJs.kt`
- Delete: `shared/src/commonTest/kotlin/dev/avinya/admob/cmp/ads/DemoAdPlacementIdsTest.kt`

**Interfaces:**
- Consumes: Task 1's `demoTestAdConfig`, `TrackingAuthorizationHook`, and `toDemoAdStartupUiState`.
- Produces: `@Composable internal expect fun PlatformAdDemo()` with one shared Android/iOS actual and unsupported-target actuals.

- [ ] **Step 1: Add the common platform entry contract**

Create `shared/src/commonMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.kt`:

```kotlin
package dev.avinya.admob.cmp.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal expect fun PlatformAdDemo()

@Composable
internal fun UnsupportedAdPlatform() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "The AdMob demo is available on Android and iOS.",
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
```

- [ ] **Step 2: Implement the shared Android/iOS production-pattern host**

Create `shared/src/adCapableMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.adCapable.kt`:

```kotlin
package dev.avinya.admob.cmp.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import avinya.tech.yt.ads.ConsentMode
import avinya.tech.yt.ads.LocalAdManager
import avinya.tech.yt.ads.debug.AdDebugCatalog
import avinya.tech.yt.ads.debug.AdDebugScreen
import avinya.tech.yt.ads.rememberAdManager

@Composable
internal actual fun PlatformAdDemo() {
    val manager = rememberAdManager()
    val status by manager.status.collectAsState()
    var retryGeneration by remember { mutableIntStateOf(0) }
    val config = remember(manager) {
        demoTestAdConfig(
            trackingHook = TrackingAuthorizationHook {
                manager.tracking.requestAuthorization()
            },
        )
    }

    LaunchedEffect(manager, config, retryGeneration) {
        manager.initialize(
            config = config,
            consentMode = ConsentMode.GatherBeforeInitialize,
        )
    }

    when (val uiState = status.toDemoAdStartupUiState()) {
        DemoAdStartupUiState.Starting ->
            DemoStartupMessage(
                message = "Preparing consent and Google Mobile Ads…",
                showProgress = true,
            )
        DemoAdStartupUiState.Ready ->
            CompositionLocalProvider(LocalAdManager provides manager) {
                AdDebugScreen(
                    catalog = AdDebugCatalog.Test,
                    manager = manager,
                    onBack = {},
                )
            }
        DemoAdStartupUiState.ConsentRequired ->
            DemoStartupMessage(
                message = "Consent is still required before ads can be requested.",
                actionLabel = "Retry consent",
                onAction = { retryGeneration += 1 },
            )
        is DemoAdStartupUiState.Failed ->
            DemoStartupMessage(
                message = uiState.message,
                actionLabel = if (uiState.retryable) "Retry initialization" else "Try again",
                onAction = { retryGeneration += 1 },
            )
    }
}

@Composable
private fun DemoStartupMessage(
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showProgress) {
                CircularProgressIndicator()
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
```

The initialization hook is dispatched by the library after UMP consent succeeds and immediately before native GMA initialization. Android's tracking controller is a no-op; iOS requests ATT at the correct point.

- [ ] **Step 3: Add unsupported-target actuals**

Create each file with the exact matching actual:

`shared/src/jvmMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.jvm.kt`

```kotlin
package dev.avinya.admob.cmp.demo

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformAdDemo() {
    UnsupportedAdPlatform()
}
```

`shared/src/jsMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.js.kt`

```kotlin
package dev.avinya.admob.cmp.demo

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformAdDemo() {
    UnsupportedAdPlatform()
}
```

`shared/src/wasmJsMain/kotlin/dev/avinya/admob/cmp/demo/PlatformAdDemo.wasmJs.kt`

```kotlin
package dev.avinya.admob.cmp.demo

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformAdDemo() {
    UnsupportedAdPlatform()
}
```

- [ ] **Step 4: Replace the generated sample root**

Replace `shared/src/commonMain/kotlin/dev/avinya/admob/cmp/App.kt` with:

```kotlin
package dev.avinya.admob.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.avinya.admob.cmp.demo.PlatformAdDemo

@Composable
fun App() {
    MaterialTheme {
        PlatformAdDemo()
    }
}
```

- [ ] **Step 5: Simplify the Android entry point**

Replace `androidApp/src/main/kotlin/dev/avinya/admob/cmp/MainActivity.kt` with:

```kotlin
package dev.avinya.admob.cmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}
```

Do not initialize a second app-level controller provider. `rememberAdManager()` owns the process singleton.

- [ ] **Step 6: Remove the superseded interstitial-only demo seam**

Delete the seven `AdController` source files and `DemoAdPlacementIdsTest.kt` listed in this task's **Files** block.

Confirm no obsolete references remain:

```bash
rg -n \
  "getAdController|DemoAdPlacementIds|initializeAndroidAdController|AdmobCMPDemo" \
  androidApp shared
```

Expected: no matches.

- [ ] **Step 7: Compile every platform**

```bash
./gradlew \
  :shared:compileAndroidMain \
  :shared:compileKotlinIosSimulatorArm64 \
  :shared:compileKotlinJvm \
  :shared:compileKotlinJs \
  :shared:compileKotlinWasmJs \
  :androidApp:compileDebugKotlin \
  --rerun-tasks \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Re-run startup and library tests**

```bash
./gradlew \
  :shared:testAndroidHostTest \
  :shared:iosSimulatorArm64Test \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:iosSimulatorArm64Test \
  --rerun-tasks \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit Task 2**

```bash
git add -A
git commit -m "feat: launch the test ads debug console from the demo app"
```

---

### Task 3: Make the iOS host linkable and production-complete

**Files:**
- Modify: `iosApp/iosApp/Info.plist`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`
- Create: `iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` via `xcodebuild`
- Modify: `README.md`

**Interfaces:**
- Consumes: the static `Shared` Kotlin framework's bindings-only references to `GoogleMobileAds` and `UserMessagingPlatform`.
- Produces: an iOS app target that links `GoogleMobileAds` 13.5.0 and `GoogleUserMessagingPlatform` 3.1.0, declares test app metadata, and passes `:admob-cmp-core:doctorIos`.

- [ ] **Step 1: Capture the current iOS integration failure**

```bash
./gradlew \
  :admob-cmp-core:downloadGmaIos \
  :admob-cmp-core:downloadUmpIos \
  :admob-cmp-core:doctorIos \
  --console=plain
```

Expected RED diagnostic:

```text
❌ SPM product 'GoogleMobileAds' not referenced
❌ SPM product 'GoogleUserMessagingPlatform' not referenced
❌ Info.plist is missing GADApplicationIdentifier
```

- [ ] **Step 2: Add the exact Swift package products to the Xcode project**

Edit `iosApp/iosApp.xcodeproj/project.pbxproj` with these fixed object IDs.

Add to `PBXBuildFile`:

```text
A11D0B000000000000000001 /* GoogleMobileAds in Frameworks */ = {isa = PBXBuildFile; productRef = A11D0B000000000000000005 /* GoogleMobileAds */; };
A11D0B000000000000000002 /* GoogleUserMessagingPlatform in Frameworks */ = {isa = PBXBuildFile; productRef = A11D0B000000000000000006 /* GoogleUserMessagingPlatform */; };
```

Add both build files to the `128058CE1A2E0AB6866179F8 /* Frameworks */` phase:

```text
files = (
    A11D0B000000000000000001 /* GoogleMobileAds in Frameworks */,
    A11D0B000000000000000002 /* GoogleUserMessagingPlatform in Frameworks */,
);
```

Add both products to `59459CE0A8B27F5E01A85858 /* iosApp */`:

```text
packageProductDependencies = (
    A11D0B000000000000000005 /* GoogleMobileAds */,
    A11D0B000000000000000006 /* GoogleUserMessagingPlatform */,
);
```

Add both package references to `9F90FED230CA915CF89E7CF2 /* Project object */`:

```text
packageReferences = (
    A11D0B000000000000000003 /* XCRemoteSwiftPackageReference "swift-package-manager-google-mobile-ads" */,
    A11D0B000000000000000004 /* XCRemoteSwiftPackageReference "swift-package-manager-google-user-messaging-platform" */,
);
```

Add these sections before `XCBuildConfiguration`:

```text
/* Begin XCRemoteSwiftPackageReference section */
    A11D0B000000000000000003 /* XCRemoteSwiftPackageReference "swift-package-manager-google-mobile-ads" */ = {
        isa = XCRemoteSwiftPackageReference;
        repositoryURL = "https://github.com/googleads/swift-package-manager-google-mobile-ads.git";
        requirement = {
            kind = exactVersion;
            version = 13.5.0;
        };
    };
    A11D0B000000000000000004 /* XCRemoteSwiftPackageReference "swift-package-manager-google-user-messaging-platform" */ = {
        isa = XCRemoteSwiftPackageReference;
        repositoryURL = "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git";
        requirement = {
            kind = exactVersion;
            version = 3.1.0;
        };
    };
/* End XCRemoteSwiftPackageReference section */

/* Begin XCSwiftPackageProductDependency section */
    A11D0B000000000000000005 /* GoogleMobileAds */ = {
        isa = XCSwiftPackageProductDependency;
        package = A11D0B000000000000000003 /* XCRemoteSwiftPackageReference "swift-package-manager-google-mobile-ads" */;
        productName = GoogleMobileAds;
    };
    A11D0B000000000000000006 /* GoogleUserMessagingPlatform */ = {
        isa = XCSwiftPackageProductDependency;
        package = A11D0B000000000000000004 /* XCRemoteSwiftPackageReference "swift-package-manager-google-user-messaging-platform" */;
        productName = GoogleUserMessagingPlatform;
    };
/* End XCSwiftPackageProductDependency section */
```

In both target-level `Debug` and `Release` build settings (`14F2BB4C96F66098DBB72C94` and `ECF1D959C0BC27542452ACAB`), add:

```text
OTHER_LDFLAGS = (
    "$(inherited)",
    "-ObjC",
    "-framework",
    JavaScriptCore,
);
```

- [ ] **Step 3: Add the sample app ID, ATT text, and current SKAdNetwork list**

Replace `iosApp/iosApp/Info.plist` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CADisableMinimumFrameDurationOnPhone</key>
    <true/>
    <key>GADApplicationIdentifier</key>
    <string>ca-app-pub-3940256099942544~1458002511</string>
    <key>NSUserTrackingUsageDescription</key>
    <string>This identifier is used to provide more relevant advertising and measure ad performance.</string>
    <key>SKAdNetworkItems</key>
    <array>
        <dict><key>SKAdNetworkIdentifier</key><string>cstr6suwn9.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>4fzdc2evr5.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>2fnua5tdw4.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>ydx93a7ass.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>p78axxw29g.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>v72qych5uu.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>ludvb6z3bs.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>cp8zw746q7.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>3sh42y64q3.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>c6k4g5qg8m.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>s39g8k73mm.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>wg4vff78zm.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>3qy4746246.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>f38h382jlk.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>hs6bdukanm.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>mlmmfzh3r3.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>v4nxqhlyqp.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>wzmmz9fp6w.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>su67r6k2v3.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>yclnxrl5pm.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>t38b2kh725.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>7ug5zh24hu.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>gta9lk7p23.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>vutu7akeur.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>y5ghdn5j9k.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>v9wttpbfk9.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>n38lu8286q.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>47vhws6wlr.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>kbd757ywx3.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>9t245vhmpl.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>a2p9lx4jpn.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>22mmun2rn5.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>44jx6755aq.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>k674qkevps.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>4468km3ulz.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>2u9pt9hc89.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>8s468mfl3y.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>klf5c3l5u5.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>ppxm28t8ap.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>kbmxgpxpgc.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>uw77j35x4d.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>578prtvx9j.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>4dzt52r2t5.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>tl55sbb4fm.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>c3frkrj4fj.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>e5fvkxwrpn.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>8c4e2ghe7u.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>3rd42ekr43.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>97r2b46745.skadnetwork</string></dict>
        <dict><key>SKAdNetworkIdentifier</key><string>3qcr597p9d.skadnetwork</string></dict>
    </array>
</dict>
</plist>
```

- [ ] **Step 4: Validate the plist and resolve packages**

```bash
plutil -lint iosApp/iosApp/Info.plist
xcodebuild \
  -resolvePackageDependencies \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp
```

Expected:

```text
iosApp/iosApp/Info.plist: OK
resolved source packages
```

Commit the generated shared package lock at:

```text
iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
```

Verify it contains versions `13.5.0` and `3.1.0`:

```bash
rg -n '"version" : "(13\.5\.0|3\.1\.0)"' \
  iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
```

Expected: two matches.

- [ ] **Step 5: Verify `doctorIos` turns green**

```bash
./gradlew :admob-cmp-core:doctorIos --console=plain
```

Expected:

```text
✅ Xcode project links SPM product 'GoogleMobileAds'
✅ Xcode project links SPM product 'GoogleUserMessagingPlatform'
✅ Info.plist declares GADApplicationIdentifier
✅ Info.plist declares SKAdNetworkItems
```

The sample-ID warning is expected for this test-only app.

- [ ] **Step 6: Build the actual iOS app target**

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Expected: `** BUILD SUCCEEDED **` with no undefined `GADMobileAds`, `UserMessagingPlatform`, or `JavaScriptCore` symbols.

- [ ] **Step 7: Document the runnable test-ad demo**

In `README.md`, add this paragraph immediately below `### Running the apps`:

```markdown
Android and iOS open directly into the AdMob debug console. The app follows the
production consent → iOS tracking → one-time SDK initialization sequence, but all
application and ad-unit IDs are Google's official samples and every placement has
strict test-mode validation. Before replacing the sample IDs for a release, follow
[`admob-cmp/docs/SETUP.md`](./admob-cmp/docs/SETUP.md) and run
`./gradlew :admob-cmp-core:doctorIos`.
```

- [ ] **Step 8: Commit Task 3**

```bash
git add \
  iosApp/iosApp/Info.plist \
  iosApp/iosApp.xcodeproj/project.pbxproj \
  iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved \
  README.md
git commit -m "build: link the iOS test ads demo host"
```

---

### Task 4: Run the final cross-platform release gate

**Files:**
- No production file changes expected.

**Interfaces:**
- Consumes: all Task 1–3 deliverables.
- Produces: verification evidence that the direct debug demo is safe, compilable, linkable, and does not regress the split library.

- [ ] **Step 1: Run the complete Gradle gate from a clean task graph**

```bash
./gradlew \
  :admob-cmp-core:compileCommonMainKotlinMetadata \
  :admob-cmp-core:compileAndroidMain \
  :admob-cmp-core:compileKotlinIosSimulatorArm64 \
  :admob-cmp-compose:compileCommonMainKotlinMetadata \
  :admob-cmp-compose:compileAndroidMain \
  :admob-cmp-compose:compileKotlinIosSimulatorArm64 \
  :admob-cmp:compileAndroidMain \
  :admob-cmp:compileKotlinIosSimulatorArm64 \
  :admob-cmp-core:testAndroidHostTest \
  :admob-cmp-core:iosSimulatorArm64Test \
  :admob-cmp-compose:testAndroidHostTest \
  :admob-cmp-compose:iosSimulatorArm64Test \
  :admob-cmp-core:checkKotlinAbi \
  :admob-cmp-compose:checkKotlinAbi \
  :admob-cmp-compose:verifyKotlinMultiplatformPomDependencyScopes \
  :shared:testAndroidHostTest \
  :shared:iosSimulatorArm64Test \
  :shared:compileKotlinJvm \
  :shared:compileKotlinJs \
  :shared:compileKotlinWasmJs \
  :androidApp:assembleDebug \
  -PVERSION_NAME=0.2.0 \
  -PRELEASE_SIGNING_ENABLED=false \
  --no-configuration-cache \
  --rerun-tasks \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Re-run the iOS consumer build**

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Verify test-only safety and dependency boundaries**

```bash
rg -n \
  "ca-app-pub-" \
  shared/src/adCapableMain \
  iosApp/iosApp/Info.plist

rg -n \
  "admob-cmp-(core|compose)" \
  shared/build.gradle.kts

if rg -n \
  "avinya\.tech\.yt\.ads|admob-cmp-(core|compose)" \
  shared/src/commonMain \
  shared/src/jvmMain \
  shared/src/jsMain \
  shared/src/wasmJsMain
then
  exit 1
fi
```

Expected:

- the only app IDs are the two Google sample IDs;
- the AdMob dependency exists only in `adCapableMain`;
- the boundary scan exits successfully with no forbidden matches.

- [ ] **Step 4: Check repository integrity**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and a clean feature worktree.

- [ ] **Step 5: Record completion without an empty commit**

Do not create a Task 4 commit when verification produced no file changes. Record the exact Gradle and Xcode results in the execution ledger and proceed to `superpowers:requesting-code-review`.

---

## Plan coverage

- Direct Android/iOS launch into `AdDebugScreen`: Task 2.
- Process-wide manager, consent-first initialization, ATT ordering, readiness gate, retry UI: Tasks 1–2.
- Official test app IDs, `AdDebugCatalog.Test`, strict test units: Tasks 1–2.
- Unsupported JVM/JS/Wasm targets remain dependency-free: Tasks 1–2 and Task 4 boundary scan.
- iOS SPM products, app ID, ATT description, SKAdNetwork attribution, JavaScriptCore linkage: Task 3.
- Android/iOS library and demo regression checks: Task 4.
