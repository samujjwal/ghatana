#!/bin/bash
#
# Enforce anti-test-theatre and disabled-test ticketing rules.
#
# Fails on:
# - expect(true).toBe(true) or expect(1).toBe(1) in test files
# - it.skip / describe.skip / xit without a GH-<id> reference in the same line
# - @Disabled in Java test files without a GH-<id> reference on the line or immediate next line

set -euo pipefail

ROOTS=()
for candidate in \
  platform \
  platform-kernel \
  platform-plugins \
  shared-services \
  products/audio-video \
  products/data-cloud \
  products/aep \
  products/yappc; do
  if [ -d "$candidate" ]; then
    ROOTS+=("$candidate")
  fi
done

if [ "${#ROOTS[@]}" -eq 0 ]; then
  echo "No scan roots found; skipping test-authenticity check."
  exit 0
fi

EXIT_CODE=0

print_section() {
  echo ""
  echo "=== $1 ==="
}

check_placeholder_assertions() {
  print_section "Checking placeholder assertions"

  if rg -n "expect\(true\)\.toBe\(true\)|expect\(1\)\.toBe\(1\)" \
    "${ROOTS[@]}" \
    -g "**/*.test.ts" -g "**/*.test.tsx" -g "**/*.spec.ts" -g "**/*.spec.tsx" -g "**/*Test.java" -g "**/*IT.java" \
    -g "!**/node_modules/**" -g "!**/build/**" -g "!**/dist/**"; then
    echo ""
    echo "ERROR: Placeholder assertions detected."
    EXIT_CODE=1
  else
    echo "OK: No placeholder assertions detected."
  fi
}

check_skipped_ts_tests() {
  print_section "Checking skipped TS/JS tests"

  local matches
  matches=$(rg -n "\b(it|describe)\.skip\(|\bxit\(" \
    "${ROOTS[@]}" \
    -g "**/*.test.ts" -g "**/*.test.tsx" -g "**/*.spec.ts" -g "**/*.spec.tsx" \
    -g "!**/node_modules/**" -g "!**/build/**" -g "!**/dist/**" || true)

  if [ -z "$matches" ]; then
    echo "OK: No skipped TS/JS tests detected."
    return
  fi

  local violations=0
  while IFS= read -r entry; do
    [ -z "$entry" ] && continue

    local file
    local line_no
    file="${entry%%:*}"
    local rest="${entry#*:}"
    line_no="${rest%%:*}"

    local current
    local next
    current=$(sed -n "${line_no}p" "$file" || true)
    next=$(sed -n "$((line_no + 1))p" "$file" || true)

    if [[ "$current" =~ GH-[0-9]+ ]] || [[ "$next" =~ GH-[0-9]+ ]]; then
      continue
    fi

    echo "$entry"
    violations=1
  done <<< "$matches"

  if [ "$violations" -eq 1 ]; then
    echo ""
    echo "ERROR: Skipped TS/JS tests without GH ticket reference."
    EXIT_CODE=1
  else
    echo "OK: Skipped TS/JS tests have GH ticket references."
  fi
}

check_disabled_java_tests() {
  print_section "Checking @Disabled Java tests"

  local disabled_lines
  disabled_lines=$(rg -n "@Disabled(\(|$)" \
    "${ROOTS[@]}" \
    -g "**/src/test/**/*.java" \
    -g "!**/build/**" -g "!**/bin/**" || true)

  if [ -z "$disabled_lines" ]; then
    echo "OK: No @Disabled Java tests detected."
    return
  fi

  local violations=0

  while IFS= read -r entry; do
    [ -z "$entry" ] && continue

    local file
    local line_no
    file="${entry%%:*}"
    local rest="${entry#*:}"
    line_no="${rest%%:*}"

    local current
    local next
    current=$(sed -n "${line_no}p" "$file" || true)
    next=$(sed -n "$((line_no + 1))p" "$file" || true)

    if [[ "$current" =~ GH-[0-9]+ ]] || [[ "$next" =~ GH-[0-9]+ ]]; then
      continue
    fi

    echo "$entry"
    violations=1
  done <<< "$disabled_lines"

  if [ "$violations" -eq 1 ]; then
    echo ""
    echo "ERROR: @Disabled Java tests without GH ticket reference."
    EXIT_CODE=1
  else
    echo "OK: @Disabled Java tests have GH ticket references."
  fi
}

check_placeholder_assertions
check_skipped_ts_tests
check_disabled_java_tests

if [ "$EXIT_CODE" -eq 0 ]; then
  echo ""
  echo "All test-authenticity checks passed."
else
  echo ""
  echo "Test-authenticity checks failed."
fi

exit "$EXIT_CODE"

