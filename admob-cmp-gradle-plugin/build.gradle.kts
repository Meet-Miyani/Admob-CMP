plugins {
    `kotlin-dsl`
    // Must match version in root gradle/libs.versions.toml
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

dependencies {
    // The plugin configures Kotlin Multiplatform test binaries, so it compiles against KGP.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("admobCmp") {
            id = "dev.avinya.ads.admob-cmp"
            implementationClass = "dev.avinya.ads.gradle.AdMobCmpPlugin"
            displayName = "AdMob CMP"
            description = "Links Google Mobile Ads into Kotlin/Native test executables for admob-cmp consumers."
        }
    }
}
