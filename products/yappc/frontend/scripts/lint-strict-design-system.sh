#!/usr/bin/env bash
set -euo pipefail

BASE_BRANCH="${GITHUB_BASE_REF:-main}"
if git rev-parse --verify --quiet "origin/${BASE_BRANCH}" >/dev/null; then
  DIFF_BASE="$(git merge-base "origin/${BASE_BRANCH}" HEAD)"
else
  DIFF_BASE="HEAD~1"
fi

changed_files=()
while IFS= read -r line; do
  changed_files+=("$line")
done < <(
  git diff --name-only "${DIFF_BASE}"...HEAD -- \
    "web/src/components/dashboard" \
    "web/src/components/command" \
    "web/src/components/compiler" \
    "web/src/components/phase" \
    "web/src/routes/app/project" \
    | grep -E '\\.(ts|tsx)$' || true
)

if [[ ${#changed_files[@]} -eq 0 ]]; then
  echo "No changed design-system-governed files detected; strict lint gate skipped."
  exit 0
fi

echo "Running strict design-system lint gate on changed files:"
printf ' - %s\n' "${changed_files[@]}"

NODE_OPTIONS=--max-old-space-size=8192 pnpm exec eslint \
  --max-warnings=0 \
  --no-error-on-unmatched-pattern \
  --ignore-pattern '**/*.d.ts' \
  --ignore-pattern '**/generated/**' \
  "${changed_files[@]}"
