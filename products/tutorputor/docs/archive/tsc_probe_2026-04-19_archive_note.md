# tsc_probe.txt — Archived 2026-04-27

The file `tsc_probe.txt` originally recorded TypeScript compiler errors captured on or around
2026-04-19 when the `@tutorputor/contracts` package had not been compiled (no `dist/` folder)
and several `exactOptionalPropertyTypes` violations existed in platform source files.

## Root causes resolved

1. **`@tutorputor/contracts` dist/ missing** — The contracts package must be compiled
   (`pnpm exec tsc -p tsconfig.json` inside `products/tutorputor/contracts/`) before any
   dependent package runs `tsc`. Added to CI build order.

2. **`exactOptionalPropertyTypes` violations** — Fixed in:
   - `services/tutorputor-platform/src/modules/content/studio/service.ts` (lines 1649, 1658)
     — `reviewQueueId` and `suggestion` now use conditional spread instead of assigning
     `T | undefined` to a required optional field.

## Verification

Commit: `4a5e8c6dc468b518e83448bce6c64f45b462e0e4`

```
cd products/tutorputor/contracts && pnpm exec tsc -p tsconfig.json  # exit 0
cd products/tutorputor/services/tutorputor-platform && pnpm exec tsc --noEmit  # exit 0
```

Both commands exit with code 0, producing zero diagnostics.

See `products/tutorputor/docs/architecture/CURRENT_VERIFICATION_STATUS.md` for the
generated typecheck status artifact.
