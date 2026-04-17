#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../services/tutorputor-platform"
corepack pnpm exec vitest run \
  src/modules/content/asset/__tests__/routes.test.ts \
  src/modules/content/semantic/__tests__/routes.test.ts \
  src/modules/content/quality-ml/__tests__/routes.test.ts \
  src/modules/content/modality-conversion/__tests__/routes.test.ts \
  src/modules/content/generation/__tests__/routes.test.ts \
  src/modules/content/review/__tests__/routes.test.ts \
  src/modules/content/cms/__tests__/routes.test.ts \
  src/modules/content/experiments/ab-testing/__tests__/routes.test.ts
