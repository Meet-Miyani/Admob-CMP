#!/usr/bin/env bash
# Verify the POM metadata that ships in every published artifact.
#
# Published POMs are immutable: 1.0.0-1.1.0 carry the pre-rename repo URL
# permanently, and GitHub's 301 covers those readers. This check exists so that
# every FUTURE release carries the new URL, the trademark line, and the search
# keywords from the public-visibility spec.
#
# Two files hold the full URL/SCM property set, because the Gradle plugin is a
# separate included build with its own publishing config. Both must agree.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NEW_REPO="https://github.com/Meet-Miyani/admob-compose-multiplatform"
OLD_REPO="https://github.com/Meet-Miyani/Admob-CMP"
TRADEMARK="Not affiliated with or endorsed by Google."
FAIL=0

fail() { echo "  FAIL: $*"; FAIL=1; }
ok()   { echo "  ok:   $*"; }

prop() { # prop <file> <key> -> value, or empty
  sed -n "s/^$2=//p" "$1" | head -1
}

check_eq() { # check_eq <file> <key> <expected>
  local got; got="$(prop "$1" "$2")"
  if [ "${got}" = "$3" ]; then ok "$1 :: $2"; else
    fail "$1 :: $2"; echo "        expected: $3"; echo "        actual:   ${got:-<missing>}"
  fi
}

check_contains() { # check_contains <file> <key> <substring>
  local got; got="$(prop "$1" "$2")"
  case "${got}" in
    *"$3"*) ok "$1 :: $2 contains '$3'" ;;
    *) fail "$1 :: $2 must contain '$3'"; echo "        actual: ${got:-<missing>}" ;;
  esac
}

echo "== URL and SCM properties (both files that define them) =="
for f in "${ROOT}/gradle.properties" "${ROOT}/admob-cmp-gradle-plugin/gradle.properties"; do
  check_eq "$f" POM_URL                 "${NEW_REPO}"
  check_eq "$f" POM_SCM_URL             "${NEW_REPO}"
  check_eq "$f" POM_SCM_CONNECTION      "scm:git:${NEW_REPO}.git"
  check_eq "$f" POM_SCM_DEV_CONNECTION  "scm:git:ssh://git@github.com/Meet-Miyani/admob-compose-multiplatform.git"
done

echo "== No file anywhere still references the old repo slug =="
if grep -rn "${OLD_REPO}" "${ROOT}"/gradle.properties "${ROOT}"/*/gradle.properties 2>/dev/null; then
  fail "the pre-rename repo URL is still present in a gradle.properties file"
else
  ok "no gradle.properties references ${OLD_REPO}"
fi

echo "== Descriptions carry the trademark line (spec §3) =="
for f in "${ROOT}/admob-cmp/gradle.properties" \
         "${ROOT}/admob-cmp-core/gradle.properties" \
         "${ROOT}/admob-cmp-compose/gradle.properties" \
         "${ROOT}/admob-cmp-gradle-plugin/gradle.properties"; do
  check_contains "$f" POM_DESCRIPTION "${TRADEMARK}"
done

echo "== Names and descriptions carry the search keywords (spec §7) =="
check_contains "${ROOT}/admob-cmp/gradle.properties"                POM_NAME        "Compose Multiplatform"
check_contains "${ROOT}/admob-cmp-core/gradle.properties"           POM_DESCRIPTION "Kotlin Multiplatform"
check_contains "${ROOT}/admob-cmp-compose/gradle.properties"        POM_DESCRIPTION "Compose Multiplatform"
check_contains "${ROOT}/admob-cmp-gradle-plugin/gradle.properties"  POM_DESCRIPTION "Kotlin Multiplatform"
check_contains "${ROOT}/admob-cmp/gradle.properties"                POM_DESCRIPTION "AdMob"

echo
if [ "${FAIL}" -ne 0 ]; then
  echo "POM metadata verification FAILED"
  exit 1
fi
echo "POM metadata verification passed"
exit 0
