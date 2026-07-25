#!/usr/bin/env sh
set -eu
GRADLE_CMD="./gradlew"
if [ ! -x "$GRADLE_CMD" ]; then
  GRADLE_CMD="gradle"
fi
$GRADLE_CMD publishToMavenLocal -PsignAllPublications=false
printf '%s\n' "Published admob-cmp-core, admob-cmp-compose, and admob-cmp to ~/.m2/repository"
