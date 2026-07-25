import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { enabled.set(true) }

    android {
        namespace = "dev.avinya.ads.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isReturnDefaultValues = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":admob-cmp-core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.google.ads.mobile.sdk)
            implementation(libs.google.user.messaging.platform)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

publishing {
    publications.named<MavenPublication>("kotlinMultiplatform") {
        pom.withXml(PromotePomDependenciesToCompileScope("dev.avinya.ads", setOf("admob-cmp-core")))
    }
}

val verifyKotlinMultiplatformPomDependencyScopes = tasks.register<VerifyPomDependencyScopes>("verifyKotlinMultiplatformPomDependencyScopes") {
    group = "verification"
    description = "Verifies API dependencies retain compile scope in the root multiplatform POM."
    dependsOn("generatePomFileForKotlinMultiplatformPublication")

    pomFile.set(layout.buildDirectory.file("publications/kotlinMultiplatform/pom-default.xml"))
    groupId.set("dev.avinya.ads")
    expectedArtifactIds.set(setOf("admob-cmp-core"))
}

tasks.named("check") {
    dependsOn(verifyKotlinMultiplatformPomDependencyScopes)
}
