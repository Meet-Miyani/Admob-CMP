#!/usr/bin/env sh
set -eu
GRADLE_CMD="./gradlew"
if [ ! -x "$GRADLE_CMD" ]; then
  GRADLE_CMD="gradle"
fi
$GRADLE_CMD :admob-cmp:publishToMavenLocal
printf '%s\n' "Published to ~/.m2/repository"
