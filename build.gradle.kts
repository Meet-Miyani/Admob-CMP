plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.mavenPublish) apply false
    // Applied (not `apply false`) so the root project owns the aggregating
    // Dokka publication. Subprojects then apply `id("org.jetbrains.dokka")`
    // without a version, resolving it from this build's script classpath.
    alias(libs.plugins.dokka)
}

// Dokka Gradle plugin v2 aggregates through the `dokka` configuration, not
// through task wiring. These three are the documented public modules.
dependencies {
    dokka(project(":admob-cmp"))
    dokka(project(":admob-cmp-core"))
    dokka(project(":admob-cmp-compose"))
}

dokka {
    moduleName.set("AdMob CMP")
    moduleVersion.set(providers.gradleProperty("VERSION_NAME"))
}

// Astro copies `docs-site/public/` to the site root verbatim, so the aggregated
// Dokka HTML lands at https://ads.avinya.dev/api/. `Sync` (not `Copy`) so a
// removed declaration cannot leave a stale page behind.
tasks.register<Sync>("syncApiDocsToDocsSite") {
    group = "documentation"
    description = "Generates the aggregated Dokka HTML and copies it into docs-site/public/api."
    from(tasks.named("dokkaGeneratePublicationHtml"))
    into(layout.projectDirectory.dir("docs-site/public/api"))
}
