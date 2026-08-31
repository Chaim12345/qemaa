#!/bin/sh
# TEMPORARY diagnostic wrapper (replace with the standard Gradle wrapper once green).
#
# CI installs Gradle 9.3.1 on PATH (gradle/actions/setup-gradle) and only
# regenerates ./gradlew when it is missing. This shim delegates to the system
# Gradle and mirrors compiler/test errors into GitHub annotations (::error::)
# so failures are diagnosable from the checks API without raw log access.

set -u

LOG="$(mktemp)"

gradle "$@" 2>&1 | tee "$LOG"

if grep -q "BUILD FAILED" "$LOG"; then
  grep -E "^e: |error: |Caused by: |What went wrong|Unresolved reference|Compilation error|\.kt:[0-9]+:[0-9]+" "$LOG" \
    | grep -v "Deprecated Gradle features" \
    | head -50 \
    | while IFS= read -r line; do
        line="$(printf '%s' "$line" | cut -c1-900)"
        printf '::error ::%s\n' "$line"
      done
  rm -f "$LOG"
  exit 1
fi

rm -f "$LOG"
grep -q "BUILD SUCCESSFUL" "$LOG" || exit 1
exit 0
