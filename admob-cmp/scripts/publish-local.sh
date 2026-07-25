#!/usr/bin/env sh
set -eu

# Resolve repository root by walking up from this script's location
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE_CMD="$REPO_ROOT/gradlew"

if [ ! -x "$GRADLE_CMD" ]; then
  GRADLE_CMD="gradle"
fi

cd "$REPO_ROOT"
$GRADLE_CMD publishToMavenLocal -PsignAllPublications=false
printf '%s\n' "Published admob-cmp-core, admob-cmp-compose, and admob-cmp to ~/.m2/repository"
