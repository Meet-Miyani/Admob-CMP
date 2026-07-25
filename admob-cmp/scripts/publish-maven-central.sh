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
# Upload every module into one Central Portal staging deployment. Deliberately
# do not auto-release: inspect the deployment in Central Portal, then publish it
# manually once all artifacts and signatures are confirmed.
$GRADLE_CMD publishToMavenCentral
