#!/usr/bin/env bash
# Verify the klibs.io listing for admob-cmp.
#
# klibs.io is a Next.js app that returns HTTP 200 for every path, including
# unknown ones, so status codes prove nothing. A missing project renders a
# not-found component carrying data-testid="not-found-page-message"; that
# marker is the only reliable signal.
#
# Exit codes:
#   0  listing exists
#   1  listing missing (not-found marker present)
#   2  network or tooling failure — result is unknown, not negative
set -uo pipefail

REPO_OWNER="${REPO_OWNER:-Meet-Miyani}"
REPO_SLUG="${REPO_SLUG:-admob-compose-multiplatform}"
URL="https://klibs.io/project/${REPO_OWNER}/${REPO_SLUG}"

echo "klibs.io listing check"
echo "  url: ${URL}"

BODY="$(curl -sL --max-time 30 "${URL}")" || {
  echo "  RESULT: UNKNOWN (curl failed)"
  exit 2
}

if [ -z "${BODY}" ]; then
  echo "  RESULT: UNKNOWN (empty response)"
  exit 2
fi

if printf '%s' "${BODY}" | grep -q 'not-found-page-message'; then
  echo "  RESULT: NOT INDEXED"
  echo "  klibs.io has no project page for ${REPO_OWNER}/${REPO_SLUG}."
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "  RESULT: UNKNOWN (python3 not found in PATH)"
  exit 2
fi

echo "  RESULT: INDEXED"
echo
echo "  Metadata rendered on the page (verify each against docs/distribution/klibs-io.md):"
PY_OUT="$(printf '%s' "${BODY}" | python3 -c '
import sys, re, html
try:
    s = sys.stdin.read()
    s = re.sub(r"<script.*?</script>", " ", s, flags=re.S)
    s = re.sub(r"<style.*?</style>", " ", s, flags=re.S)
    t = html.unescape(re.sub(r"<[^>]+>", " ", s))
    print("   ", re.sub(r"\s+", " ", t).strip()[:900])
except Exception:
    sys.exit(1)
' 2>/dev/null)" || {
  echo "  RESULT: UNKNOWN (metadata extraction failed)"
  exit 2
}

echo "${PY_OUT}"
exit 0
