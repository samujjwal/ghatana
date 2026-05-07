#!/usr/bin/env bash
set -euo pipefail

pnpm --filter @tutorputor/platform exec vitest run src/modules/content/telemetry/__tests__/routes.test.ts
pnpm --filter @tutorputor/platform exec vitest run src/modules/content/recommendation/__tests__/routes.test.ts
pnpm --filter @tutorputor/platform exec vitest run src/modules/content/publish/__tests__/routes.test.ts
pnpm --filter @tutorputor/platform exec vitest run src/modules/content/evaluation/__tests__/routes.test.ts
pnpm --filter @tutorputor/platform exec vitest run src/modules/content/candidates/__tests__/routes.test.ts
