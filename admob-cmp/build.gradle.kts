import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element

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
        pom.withXml {
            fun Element.directChild(tagName: String): Element? =
                (0 until childNodes.length)
                    .asSequence()
                    .map(childNodes::item)
                    .filterIsInstance<Element>()
                    .firstOrNull { it.tagName == tagName }

            val reexportArtifactIds = setOf("admob-cmp-core", "admob-cmp-compose")
            val dependencies = asElement().getElementsByTagName("dependency")
            for (index in 0 until dependencies.length) {
                val dependency = dependencies.item(index) as? Element ?: continue
                val groupId = dependency.directChild("groupId")?.textContent
                val artifactId = dependency.directChild("artifactId")?.textContent
                val scope = dependency.directChild("scope")

                // Gradle metadata already carries API variants; preserve compile scope for POM-only consumers.
                if (groupId == "dev.avinya.ads" && artifactId in reexportArtifactIds && scope?.textContent == "runtime") {
                    scope.textContent = "compile"
                }
            }
        }
    }
}
