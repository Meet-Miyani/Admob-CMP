import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

private fun Element.directChild(tagName: String): Element? =
    (0 until childNodes.length)
        .asSequence()
        .map(childNodes::item)
        .filterIsInstance<Element>()
        .firstOrNull { it.tagName == tagName }

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
        pom.withXml {
            val dependencies = asElement().getElementsByTagName("dependency")
            for (index in 0 until dependencies.length) {
                val dependency = dependencies.item(index) as? Element ?: continue
                val groupId = dependency.directChild("groupId")?.textContent
                val artifactId = dependency.directChild("artifactId")?.textContent
                val scope = dependency.directChild("scope")

                // Gradle metadata already carries API variants; preserve compile scope for POM-only consumers.
                if (
                    groupId == "dev.avinya.ads" &&
                    artifactId == "admob-cmp-core" &&
                    scope?.textContent == "runtime"
                ) {
                    scope.textContent = "compile"
                }
            }
        }
    }
}

val verifyKotlinMultiplatformPomDependencyScopes = tasks.register("verifyKotlinMultiplatformPomDependencyScopes") {
    group = "verification"
    description = "Verifies API dependencies retain compile scope in the root multiplatform POM."
    dependsOn("generatePomFileForKotlinMultiplatformPublication")

    val pomFile = layout.buildDirectory.file("publications/kotlinMultiplatform/pom-default.xml")
    inputs.file(pomFile)

    doLast {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pomFile.get().asFile)
        val dependencies = document.getElementsByTagName("dependency")
        val coreDependency = (0 until dependencies.length)
            .asSequence()
            .mapNotNull { dependencies.item(it) as? Element }
            .firstOrNull {
                it.directChild("groupId")?.textContent == "dev.avinya.ads" &&
                    it.directChild("artifactId")?.textContent == "admob-cmp-core"
            }

        checkNotNull(coreDependency) {
            "The root admob-cmp-compose POM must declare dev.avinya.ads:admob-cmp-core."
        }
        check(coreDependency.directChild("scope")?.textContent == "compile") {
            "The root admob-cmp-compose POM must publish dev.avinya.ads:admob-cmp-core with compile scope."
        }
    }
}

tasks.named("check") {
    dependsOn(verifyKotlinMultiplatformPomDependencyScopes)
}
