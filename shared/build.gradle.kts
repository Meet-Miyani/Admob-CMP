import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("dev.avinya.ads.admob-cmp")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val consumePublishedAdmobCmp =
    providers.gradleProperty("admobCmpConsumePublished")
        .map(String::toBoolean)
        .getOrElse(false)

kotlin {
    applyDefaultHierarchyTemplate()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    android {
       namespace = "dev.avinya.admob.cmp.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
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
                implementation(project(":showcase"))
                if (consumePublishedAdmobCmp) {
                    implementation("dev.avinya.ads:admob-cmp:${providers.gradleProperty("VERSION_NAME").get()}")
                } else {
                    implementation(project(":admob-cmp-compose"))
                }
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
        val androidHostTest by getting
        val iosMain by getting {
            dependsOn(adCapableMain)
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val iosTest by getting
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
