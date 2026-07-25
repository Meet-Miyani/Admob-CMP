import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    android {
        namespace = "dev.avinya.ads.bundle"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":admob-cmp-core"))
            api(project(":admob-cmp-compose"))
        }
    }
}

publishing {
    publications.named<MavenPublication>("kotlinMultiplatform") {
        pom.withXml(PromotePomDependenciesToCompileScope("dev.avinya.ads", setOf("admob-cmp-core", "admob-cmp-compose")))
    }
}

val verifyKotlinMultiplatformPomDependencyScopes = tasks.register<VerifyPomDependencyScopes>("verifyKotlinMultiplatformPomDependencyScopes") {
    group = "verification"
    description = "Verifies API dependencies retain compile scope in the root multiplatform POM."
    dependsOn("generatePomFileForKotlinMultiplatformPublication")

    pomFile.set(layout.buildDirectory.file("publications/kotlinMultiplatform/pom-default.xml"))
    groupId.set("dev.avinya.ads")
    expectedArtifactIds.set(setOf("admob-cmp-core", "admob-cmp-compose"))
}

tasks.named("check") {
    dependsOn(verifyKotlinMultiplatformPomDependencyScopes)
}
