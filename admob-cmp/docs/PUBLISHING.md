# Publishing (maintainers)

## Coordinates & policy

- `dev.avinya.ads:admob-cmp` — Maven publication coordinates under group
  `dev.avinya.ads` and package `dev.avinya.ads`.
- Version `1.0.0` is the first stable release.
- Plugin: `com.vanniktech.maven.publish` 0.37.0 using the Central Portal.
  Shared POM metadata lives in the root `gradle.properties`; artifact-specific
  names live in each published module's `gradle.properties`.

## Local publish

```bash
./scripts/publish-local.sh
# = ./gradlew publishToMavenLocal -PsignAllPublications=false
```

Works without signing keys. Publishes `admob-cmp-core`, `admob-cmp-compose`, and
`admob-cmp`, including Android, iosArm64, iosSimulatorArm64, and KMP-root
publications under `~/.m2/repository/dev/avinya/ads/`.

## Maven Central

**Publishing the GitHub release is what triggers CI to publish.** `.github/workflows/publish.yml`
runs on `release: [published]`; the `workflow_dispatch` trigger exists only to retry a run that
failed, so you do not have to delete and recreate a release. Do **not** dispatch manually after
publishing a release — each run creates its own pair of staging deployments, and you would then
have four to reconcile instead of two. A `concurrency` group serialises runs so two uploads can
never overlap, but it cannot merge the duplicates; drop the extra pair in Central Portal.

To publish from a local machine instead:

```bash
export ORG_GRADLE_PROJECT_mavenCentralUsername=...   # Central Portal token user
export ORG_GRADLE_PROJECT_mavenCentralPassword=...   # token password
export ORG_GRADLE_PROJECT_signingInMemoryKey=...     # ASCII-armored GPG key
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=...
./scripts/publish-maven-central.sh   # library + Gradle plugin
```

The script asserts that the library and plugin `VERSION_NAME`s match, then runs both
publications. Neither auto-releases. Inspect the deployments and signatures in Central
Portal, then publish them manually.

### Two deployments, both required

The Gradle plugin is a **separate included build** (`admob-cmp-gradle-plugin`), so it is
*not* covered by the root `publishToMavenCentral`. The script runs both:

```bash
./gradlew publishToMavenCentral
./gradlew -p admob-cmp-gradle-plugin publishToMavenCentral
```

Because these are two separate Gradle builds, they produce **two staging deployments** in
Central Portal. Release both. Publishing the library alone ships a release whose README,
SETUP.md, and cinterop `userSetupHint` all instruct consumers to apply a plugin that does
not exist on Maven Central.

The plugin's `VERSION_NAME` in `admob-cmp-gradle-plugin/gradle.properties` must be bumped in
lockstep with the root `gradle.properties`; the script refuses to publish a mismatched pair.

## API stability workflow

`explicitApi()` + KGP ABI validation guard the public surface:

```bash
./gradlew :admob-cmp:checkKotlinAbi    # CI gate — fails on undumped API changes
./gradlew :admob-cmp:updateKotlinAbi   # regenerate api/admob-cmp.klib.api; commit it
```

Any public API change must ship with the regenerated dump in the same commit.

## Published-artifact verification

The iOS klib carries cinterop bindings against GMA/UMP. Consumers' Kotlin
compiles resolve those cinterop klibs from inside the published artifact (they
are packaged per-target); the consumer's *app link* then needs the real
GMA/UMP binaries, which they have via SPM (see SETUP.md).

**Verified 2026-07-25:** the iOS publications contain the `cinterop-gma` /
`cinterop-ump` klibs, and a scratch KMP project with only
`implementation("dev.avinya.ads:admob-cmp:1.0.0")` from `mavenLocal()`
compiled both Android and `iosSimulatorArm64` demo sources successfully.
Repeat before each release:

```bash
./gradlew publishToMavenLocal -PsignAllPublications=false
./gradlew :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64 \
  -PadmobCmpConsumePublished=true --refresh-dependencies
```

## Release checklist

1. Core and Compose Android-host and iOS-simulator tests are green.
2. Core and Compose `checkKotlinAbi` are green and dumps are committed.
3. Both facade POM dependency-scope checks are green.
4. The Android app and iOS Xcode consumer build successfully.
5. Local publication plus `-PadmobCmpConsumePublished=true` compiles Android and iOS.
6. `./gradlew publishToMavenCentral --dry-run` and `./gradlew -p admob-cmp-gradle-plugin publishToMavenCentral --dry-run` schedule all modules.
7. Confirm the `dev.avinya` namespace is verified in Central Portal.
8. Run `./scripts/publish-maven-central.sh`, inspect **both** staging deployments (library and plugin), then release each manually.
