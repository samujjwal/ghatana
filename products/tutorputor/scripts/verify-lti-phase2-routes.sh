#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../services/tutorputor-platform"
corepack pnpm exec vitest run src/modules/integration/lti/routes.test.ts
