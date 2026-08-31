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

if grep -q "BUILD SUCCESSFUL" "$LOG"; then
  rm -f "$LOG"
  exit 0
fi

# Failure (or the build died without printing a result): extract the most
# useful lines as annotations.
grep -E "^e: |error: |Caused by: |What went wrong|Unresolved reference|Compilation error|\.kt:[0-9]+:[0-9]+|FAILED" "$LOG" \
  | grep -v "Deprecated Gradle features" \
  | head -40 \
  | while IFS= read -r line; do
      line="$(printf '%s' "$line" | cut -c1-900)"
      printf '::error ::%s\n' "$line"
    done

# Always include the raw tail so killed-daemon / OOM cases stay visible.
TAIL="$(tail -45 "$LOG" | tr '\n' '|' | tr -s ' ' | cut -c1-2600)"
printf '::error ::LOG TAIL: %s\n' "$TAIL"

rm -f "$LOG"
exit 1
