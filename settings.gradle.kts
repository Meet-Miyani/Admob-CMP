rootProject.name = "AdmobCMP"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                // GMA Next-Gen (ads-mobile-sdk) pulls cronet from org.chromium.net,
                // which is served by Google's Maven repo, not Maven Central.
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                // GMA Next-Gen (ads-mobile-sdk) pulls cronet from org.chromium.net,
                // which is served by Google's Maven repo, not Maven Central.
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
include(":webApp")
include(":admob-cmp")
include(":admob-cmp-core")
