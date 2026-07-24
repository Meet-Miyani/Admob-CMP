#!/usr/bin/env sh
set -eu
# vanniktech maven-publish reads credentials/signing from Gradle properties.
# Provide them as ORG_GRADLE_PROJECT_* environment variables:
#   mavenCentralUsername / mavenCentralPassword  - Central Portal user token
#   signingInMemoryKey                            - ASCII-armored GPG private key
#   signingInMemoryKeyPassword                    - key passphrase
: "${ORG_GRADLE_PROJECT_mavenCentralUsername:?Missing ORG_GRADLE_PROJECT_mavenCentralUsername}"
: "${ORG_GRADLE_PROJECT_mavenCentralPassword:?Missing ORG_GRADLE_PROJECT_mavenCentralPassword}"
: "${ORG_GRADLE_PROJECT_signingInMemoryKey:?Missing ORG_GRADLE_PROJECT_signingInMemoryKey}"
: "${ORG_GRADLE_PROJECT_signingInMemoryKeyPassword:?Missing ORG_GRADLE_PROJECT_signingInMemoryKeyPassword}"
cd "$(dirname "$0")/../.."
./gradlew :admob-cmp:publishAndReleaseToMavenCentral --no-configuration-cache
