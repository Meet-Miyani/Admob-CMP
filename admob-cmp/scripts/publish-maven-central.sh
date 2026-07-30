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
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE_CMD="$REPO_ROOT/gradlew"

if [ ! -x "$GRADLE_CMD" ]; then
  GRADLE_CMD="gradle"
fi

cd "$REPO_ROOT"

# The plugin is an included build with its own VERSION_NAME. Consumers are told to
# pair the two by the same version number, so refuse to publish a mismatched pair
# rather than discover it after the coordinates are immutable.
LIB_VERSION="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)"
PLUGIN_VERSION="$(sed -n 's/^VERSION_NAME=//p' admob-cmp-gradle-plugin/gradle.properties)"
if [ "$LIB_VERSION" != "$PLUGIN_VERSION" ]; then
  echo "Version mismatch: library is $LIB_VERSION but plugin is $PLUGIN_VERSION." >&2
  echo "Bump both gradle.properties files in lockstep before publishing." >&2
  exit 1
fi
echo "Publishing admob-cmp $LIB_VERSION (library + Gradle plugin)"

# Deliberately do not auto-release: inspect each deployment in Central Portal, then
# publish it manually once all artifacts and signatures are confirmed.
#
# These are two separate Gradle builds, so they produce TWO staging deployments in
# Central Portal. Both must be released — a library without its plugin ships docs
# and a userSetupHint pointing at an artifact that does not exist.
$GRADLE_CMD publishToMavenCentral
$GRADLE_CMD -p admob-cmp-gradle-plugin publishToMavenCentral
