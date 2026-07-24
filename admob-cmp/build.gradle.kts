import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin/Compose/AGP plugin versions come from the version catalog via the root
    // settings.gradle.kts pluginManagement block, so they are applied here by alias.
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

// NOTE on group/version/coordinates: do NOT set `group =` / `version =` or call
// `mavenPublishing { coordinates(...) }` here. `admob-cmp/gradle.properties` already
// declares GROUP=tech.avinya.ads, VERSION_NAME=0.1.0, POM_ARTIFACT_ID=admob-cmp plus the
// full POM (name/description/url/license/developer/scm), and the vanniktech plugin reads
// all of that automatically via `pomFromGradleProperties()` (called from its own
// `apply()`) — verified by inspecting the generated
// build/publications/kotlinMultiplatform/pom-default.xml, which already carries the
// correct coordinates. `pomFromGradleProperties()` runs at plugin-apply time (inside the
// `plugins {}` block above) and calls the extension's internal groupId()/version()
// setters, which finalize those properties; a second explicit `coordinates(...)` call
// from script body — even much later — collides with that finalization and throws
// "property 'groupId$plugin' is final and cannot be changed any further". This same
// gradle.properties-driven setup also already configures Maven Central publishing
// (SONATYPE_HOST=CENTRAL_PORTAL), so that repository was already live before this change;
// only the GitHub Packages repository below is new.
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Meet-Miyani/ViewTube")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// GMA Next-Gen on Android pulls UMP transitively; gmaUmp is pinned to the resolved
// version in the catalog so it can't drift. SDK levels mirror the catalog.
val admobCompileSdk = libs.versions.android.compileSdk.get().toInt()
val admobMinSdk = libs.versions.android.minSdk.get().toInt()

kotlin {
    explicitApi()

    // After any public API change run :admob-cmp:updateKotlinAbi and commit
    // api/admob-cmp.klib.api, or the build fails.
    //
    // `enabled` must be set explicitly: declaring an empty abiValidation {} block
    // does NOT switch validation on (it defaults to false), which left both
    // updateKotlinAbi and checkKotlinAbi silently SKIPPED — the dump went stale
    // and no API break was ever caught.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "avinya.tech.yt.ads"
        compileSdk = admobCompileSdk
        minSdk = admobMinSdk
        withHostTest {
            // Android's stub android.jar throws RuntimeException on any unmocked call by
            // default (e.g. android.util.Log.d) — this JVM-only host-test compilation has
            // no Robolectric to back the framework classes. This is the newer
            // androidLibrary-style KMP DSL's equivalent of classic AGP's
            // `testOptions.unitTests.isReturnDefaultValues = true`: it swaps in a mockable
            // android.jar that returns defaults (false/0/null/no-op) instead of throwing,
            // so plain framework calls like Log.d() no-op rather than crash. It does not
            // change how Mockito-mocked calls behave, so `mockito-core`'s
            // `mockStatic(Log::class.java)` usages in androidHostTest keep working as-is.
            isReturnDefaultValues = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":admob-cmp-core"))
            // The `compose.runtime/foundation/ui` accessors were deprecated; depend
            // on the Compose Multiplatform artifacts directly via the catalog.
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
            implementation(libs.ui.tooling.preview)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.ui.tooling)
            implementation(libs.androidx.uiTooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.google.ads.mobile.sdk)
            implementation(libs.google.user.messaging.platform)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // Platform-layer tests. The shared state machine is covered by commonTest, but
        // the Android mappers/pool/controllers had no coverage at all, which is where
        // the GMA-facing contracts (e.g. LoadAdError.code being an enum, not an int)
        // are easiest to break silently on an SDK bump.
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.google.ads.mobile.sdk)
            implementation("org.mockito:mockito-core:5.15.2")
        }
    }
}
