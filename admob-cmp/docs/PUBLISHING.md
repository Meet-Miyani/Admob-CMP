# Publishing (maintainers)

## Coordinates & policy

- `dev.avinya.ads:admob-cmp` — Maven publication coordinates under group
  `dev.avinya.ads` and package `dev.avinya.ads`.
- Version `0.x.y` while the Android GMA Next-Gen SDK is beta; move to `1.0`
  when it GAs (~July 2026) after a deprecation pass.
- Plugin: `com.vanniktech.maven.publish` with `SONATYPE_HOST=CENTRAL_PORTAL`
  (OSSRH was decommissioned in June 2025). POM metadata lives in
  `admob-cmp/gradle.properties`.

## Local publish

```bash
./scripts/publish-local.sh          # = ./gradlew :admob-cmp:publishToMavenLocal
```

Works without signing keys. Produces android, iosArm64, iosSimulatorArm64, and
KMP-root publications under `~/.m2/repository/dev/avinya/ads/`.

## Maven Central

```bash
export ORG_GRADLE_PROJECT_mavenCentralUsername=...   # Central Portal token user
export ORG_GRADLE_PROJECT_mavenCentralPassword=...   # token password
export ORG_GRADLE_PROJECT_signingInMemoryKey=...     # ASCII-armored GPG key
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=...
./scripts/publish-maven-central.sh   # = publishAndReleaseToMavenCentral
```

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

**Verified 2026-06-12:** the iOS publications contain the `cinterop-gma` /
`cinterop-ump` klibs, and a scratch KMP project with only
`implementation("dev.avinya.ads:admob-cmp:0.1.0")` from `mavenLocal()`
compiled an `iosSimulatorArm64` source against `AdManager`/`AdPlacement`
successfully. Repeat before each release:

```bash
./gradlew :admob-cmp:publishToMavenLocal
# then in a scratch KMP project with mavenLocal():
#   implementation("dev.avinya.ads:admob-cmp:0.1.0")
#   compile a source referencing dev.avinya.ads.AdManager for iosSimulatorArm64
```

## Release checklist

1. `iosSimulatorArm64Test` + `testAndroidHostTest` green.
2. `checkKotlinAbi` green (dump committed).
3. `:composeApp:assemble` + iosApp xcodebuild green (sample app consumes source).
4. Bump `VERSION_NAME` in `admob-cmp/gradle.properties`.
5. `publish-maven-central.sh`; verify on central.sonatype.com.
