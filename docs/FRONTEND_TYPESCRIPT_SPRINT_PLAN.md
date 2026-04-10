# Frontend + TypeScript Library Consolidation — Sprint Plan

> **Date**: 2026-04-08  
> **Based on**: `frontend-typescript-audit-report.md` + `comprehensive-frontend-typescript-audit-report.md`  
> **Guidelines**: `.github/copilot-instructions.md` (sections 5, 6, 15, 17, 25–28)  
> **Sprint cadence**: 2-week sprints  
> **Approach**: Forward-fix (no backward compat shims, replace in-place per §25)

---

## Principles Applied

| Guideline | Application in This Plan |
|-----------|--------------------------|
| §1 Reuse before creating | Products adopt platform libs before writing duplicates |
| §3 Keep boundaries explicit | Platform layer stays product-agnostic; product libs own domain logic |
| §5 Type safety at impl-time | Every migration includes strict typing, no `any`, Zod at boundaries |
| §6 React/Frontend standards | State management via TanStack Query + Jotai; typed hooks and props |
| §17 Package naming | `@ghatana/platform-*` for infra; `@ghatana/<name>` for UI; `@<product>/*` for product |
| §25 Forward-fix migration | Replace legacy imports immediately; delete deprecated code; no aliases |
| §26 tsconfig strictness | All packages: `strict: true`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess` |
| §27 Runtime validation | Zod schemas at every API boundary |
| §28 Bundle optimization | Bundle analysis in CI; size limits enforced per library category |

---

## Current State Summary (Confirmed by Inspection)

### Platform Libraries — 22 packages in `platform/typescript/`

| Package | State | Action |
|---------|-------|--------|
| `@ghatana/tokens` | ✅ Production-ready | Keep |
| `@ghatana/theme` | ✅ Production-ready, MUI coupling | Keep, extract adapter |
| `@ghatana/design-system` | ✅ 131 exports, well-structured | Keep, add export maps |
| `@ghatana/platform-utils` | ✅ Production-ready | Keep, expand coverage |
| `@ghatana/api` | ✅ Real implementation (was incorrectly listed as stub) | Keep, drive adoption |
| `@ghatana/realtime` | ✅ Real WebSocket/SSE client implementation | Keep, drive adoption |
| `@ghatana/charts` | ✅ Production-ready | Keep |
| `@ghatana/canvas` | ✅ Production-ready | Keep |
| `@ghatana/i18n` | ✅ Production-ready, underutilized | Keep, drive adoption |
| `@ghatana/sso-client` | ✅ Production-ready | Keep |
| `@ghatana/accessibility-audit` | ✅ Production-ready | Keep |
| `@ghatana/platform-shell` | ✅ Production-ready | Keep |
| `@ghatana/ui-integration` | ✅ Production-ready | Keep |
| `@ghatana/code-editor` | ✅ Production-ready | Keep |
| `@ghatana/audit-ui` | ⚠️ 3 files, zero product consumers | **Merge into design-system** |
| `@ghatana/privacy-ui` | ⚠️ 3 files, zero product consumers | **Merge into design-system** |
| `@ghatana/security-ui` | ⚠️ 3 files, zero product consumers | **Merge into design-system** |
| `@ghatana/voice-ui` | ⚠️ 3 files, zero product consumers | **Merge into design-system** |
| `@ghatana/nlp-ui` | ⚠️ 3 files, depends on voice-ui + privacy-ui | **Merge into design-system** |
| `@ghatana/selection-ui` | ⚠️ 3 files, zero product consumers | **Merge into design-system** |

### Product Libraries — High-Priority Overlap

| Package | Problem | Action |
|---------|---------|--------|
| `@yappc/api` | Re-implements HTTP client instead of using `@ghatana/api` as transport | Refactor: delegate to `@ghatana/api` |
| `@yappc/core` | Generic utils/types/constants overlap `@ghatana/platform-utils` | Audit + extract generics upward |
| `@yappc/canvas` | Pure wrapper around `@ghatana/canvas` with no additions | Remove; use `@ghatana/canvas` directly |
| `@dcmaar/agent-types` | Duplicates `@dcmaar/types` for agent shapes | Consolidate into `@dcmaar/types` |
| `@dcmaar/agent-core-types` | Third agent type definition (overlaps both above) | Merge into `@dcmaar/types` |
| `@dcmaar/browser-extension-core` | 748-line event system reinvents platform event patterns | Refactor event layer |
| `@dcmaar/browser-extension-ui` | Overlaps `@dcmaar/ui` | Merge into `@dcmaar/ui` |
| `@dcmaar/agent-ui` | Overlaps `@dcmaar/ui` | Merge into `@dcmaar/ui` |
| `@yappc/testing` | Product-level testing utils duplicating platform patterns | Merge generic parts into `platform/typescript/testing` |

---

## Sprint Plan

### Sprint 1 (Weeks 1–2): Remove Unused Platform Stubs
**Priority**: P0 — Dead code removal, zero risk  
**Theme**: _Clean the house before rebuilding it_

#### Goals
- Remove 6 orphan specialized platform UI libs (zero product consumers, 3 files each)
- Preserve any real logic by moving it into `@ghatana/design-system` as domain-scoped exports

#### Tasks

**S1-1** Audit each specialized lib for reusable logic  
- `@ghatana/audit-ui`: `AuditLogService`, `useAuditLog`, `AuditEvent` — move to `@ghatana/design-system/audit`  
- `@ghatana/privacy-ui`: move components to `@ghatana/design-system/privacy`  
- `@ghatana/security-ui`: move to `@ghatana/design-system/security`  
- `@ghatana/voice-ui`: move to `@ghatana/design-system/voice`  
- `@ghatana/nlp-ui`: move to `@ghatana/design-system/nlp`  
- `@ghatana/selection-ui`: move to `@ghatana/design-system/selection`  

**S1-2** Move code into `@ghatana/design-system` under named sub-paths  
```
platform/typescript/design-system/src/
  audit/       ← from @ghatana/audit-ui
  privacy/     ← from @ghatana/privacy-ui
  security/    ← from @ghatana/security-ui
  voice/       ← from @ghatana/voice-ui
  nlp/         ← from @ghatana/nlp-ui
  selection/   ← from @ghatana/selection-ui
```
Export via package.json `exports` paths: `@ghatana/design-system/audit`, etc.  

**S1-3** Add export entries to `@ghatana/design-system/package.json`  

**S1-4** Delete 6 library directories and their `pnpm-workspace.yaml` entries  

**S1-5** Update `platform/typescript/package.json` workspace glob  

**S1-6** Add/update tests: each moved module must have unit tests in `__tests__/`  

#### Definition of Done
- [ ] 6 platform dirs removed
- [ ] All logic preserved under `@ghatana/design-system/*` sub-paths
- [ ] `pnpm build` passes with zero warnings
- [ ] `tsc --noEmit` passes
- [ ] Tests added for moved modules (min 80% line coverage)
- [ ] No broken imports in platform workspace

---

### Sprint 2 (Weeks 3–4): DCMAAR Type Consolidation
**Priority**: P0 — Active duplication causing inconsistent types  
**Theme**: _One source of truth for DCMAAR types_

#### Context
Three overlapping type libraries exist in DCMAAR:
- `@dcmaar/types` — plugin shapes (`IPlugin`, `ExtensionPluginManifest`, …) — **most widely consumed**
- `@dcmaar/agent-types` — agent application types (uses `type-fest`)
- `@dcmaar/agent-core` — agent runtime types (separate package)

#### Goals
- Merge `@dcmaar/agent-types` into `@dcmaar/types` as a sub-module
- Audit `@dcmaar/agent-core` for type exports; move type-only exports into `@dcmaar/types`
- Keep runtime/implementation code in `@dcmaar/agent-core`

#### Tasks

**S2-1** Map all exports of `@dcmaar/agent-types` vs `@dcmaar/types` — identify exact overlaps  

**S2-2** Add `./agent` export path to `@dcmaar/types`  
```json
"exports": {
  ".": "./dist/index.js",
  "./agent": "./dist/agent/index.js",
  "./plugin": "./dist/plugin/index.js"
}
```

**S2-3** Move `@dcmaar/agent-types` source into `@dcmaar/types/src/agent/`  

**S2-4** Update all imports in DCMAAR workspace:
```ts
// Before
import { AgentConfig } from '@dcmaar/agent-types';
// After
import type { AgentConfig } from '@dcmaar/types/agent';
```

**S2-5** Delete `@dcmaar/agent-types` package directory  

**S2-6** Extract type-only exports from `@dcmaar/agent-core` into `@dcmaar/types/agent`  
(Keep `@dcmaar/agent-core` for runtime: connectors, execution, lifecycle)  

**S2-7** Merge `@dcmaar/agent-ui` into `@dcmaar/ui`  
- Move components into `@dcmaar/ui/src/agent/`  
- Add `./agent` export path to `@dcmaar/ui`  
- Delete `@dcmaar/agent-ui`  

**S2-8** Merge `@dcmaar/browser-extension-ui` into `@dcmaar/ui`  
- Add `./extension` export path  
- Delete `@dcmaar/browser-extension-ui`  

**S2-9** Tests: all merged modules must keep/add unit tests  

#### Definition of Done
- [ ] `@dcmaar/agent-types` and `@dcmaar/browser-extension-ui` and `@dcmaar/agent-ui` deleted
- [ ] `@dcmaar/types` exports `./agent` and `./plugin` sub-paths
- [ ] `@dcmaar/ui` exports `./agent` and `./extension` sub-paths
- [ ] All DCMAAR workspace imports updated
- [ ] `tsc --noEmit` passes across all DCMAAR packages
- [ ] Zero duplicate type definitions
- [ ] Tests: 80%+ coverage for merged modules

---

### Sprint 3 (Weeks 5–6): DCMAAR Browser Extension Event Abstraction
**Priority**: P1 — 748 lines of reinvented event infrastructure  
**Theme**: _Extract the pattern, keep the domain_

#### Context
`@dcmaar/browser-extension-core` implements its own `UnifiedBrowserEventCapture` (748 lines) defining `BrowserEvent = TabEvent | NavigationEvent | NetworkEvent | WebRequestEvent | HistoryEvent | FlowEvent`. This reinvents what belongs in a platform event abstraction.

#### Goals
- Extract the generic event infrastructure into `@ghatana/realtime` (base event types) or a new focused `platform/typescript/events/` lib
- Keep DCMAAR-domain browser event types in `@dcmaar/browser-extension-core`
- `@dcmaar/browser-extension-core` extends platform event base types, not reinvents them

#### Tasks

**S3-1** Audit the 748-line `UnifiedBrowserEventCapture` — separate:
- Generic: `PlatformEvent`, `EventSource`, `EventHandler`, subscription patterns
- DCMAAR-domain: `TabEvent`, `NavigationEvent`, browser API detection logic

**S3-2** Add base event types to `@ghatana/realtime`  
```ts
// platform/typescript/realtime/src/events/
export interface PlatformEvent<T = unknown> {
  readonly id: string;
  readonly type: string;
  readonly timestamp: number;
  readonly source: EventSource;
  readonly data: T;
}
export interface EventSource {
  readonly type: 'browser' | 'server' | 'client' | 'extension';
  readonly id: string;
}
export interface EventSubscription {
  unsubscribe(): void;
}
export interface EventEmitter<T extends PlatformEvent> {
  on(type: string, handler: (event: T) => void): EventSubscription;
  emit(event: T): void;
}
```
Export via `@ghatana/realtime/events`  

**S3-3** Refactor `@dcmaar/browser-extension-core` to extend platform types  
```ts
import type { PlatformEvent, EventSource } from '@ghatana/realtime/events';
export interface BrowserTabEvent extends PlatformEvent<TabEventData> {
  source: EventSource & { type: 'browser' };
}
```

**S3-4** Add `@ghatana/realtime` as dependency in `@dcmaar/browser-extension-core`  

**S3-5** Add Zod validation for event schemas at extension-backend boundary  

**S3-6** Tests: unit tests for `PlatformEvent` base types; integration tests for DCMAAR event capture  

#### Definition of Done
- [ ] Platform event base types in `@ghatana/realtime/events`
- [ ] `@dcmaar/browser-extension-core` uses platform types as base
- [ ] No standalone event type reinvention in DCMAAR
- [ ] Zod schemas validate event payloads at serialization boundary
- [ ] All existing DCMAAR browser extension tests pass
- [ ] New platform event tests added

---

### Sprint 4 (Weeks 7–8): @yappc/canvas Removal + Platform Canvas Adoption
**Priority**: P1 — Pure wrapper with zero additions  
**Theme**: _Remove wrappers that add no value_

#### Context
`@yappc/canvas` is a wrapper around `@ghatana/canvas` with no material additions. Per §25 (Forward-Fix), wrappers without value are deleted, not maintained.

#### Goals
- Remove `@yappc/canvas` entirely
- All YAPPC consumers import from `@ghatana/canvas` directly
- If YAPPC canvas has any YAPPC-specific extensions, they stay in the app (not a lib)

#### Tasks

**S4-1** Audit `@yappc/canvas` source — catalog every export that adds behavior vs re-exports  

**S4-2** For genuine YAPPC-only extensions: move inline to `products/yappc/frontend/web/src/canvas/`  

**S4-3** Update all YAPPC imports:
```ts
// Before
import { CanvasRenderer } from '@yappc/canvas';
// After
import { CanvasRenderer } from '@ghatana/canvas';
```

**S4-4** Remove `@yappc/canvas` from `products/yappc/frontend/libs/yappc-canvas/`  

**S4-5** Update YAPPC workspace `pnpm-workspace.yaml`  

**S4-6** Update `@yappc/ide` and any other consumers that depended on `@yappc/canvas`  

#### Definition of Done
- [ ] `@yappc/canvas` directory deleted
- [ ] All YAPPC canvas imports point to `@ghatana/canvas`
- [ ] YAPPC web app builds cleanly
- [ ] No regression in canvas-related tests

---

### Sprint 5 (Weeks 9–10): @yappc/core Generic Utility Extraction
**Priority**: P1 — YAPPC utils/constants overlap @ghatana/platform-utils  
**Theme**: _Push generics up, keep domain local_

#### Context
`@yappc/core` is described as "Consolidated core, types, and utilities" and exports `./utils`, `./types`, `./constants`. `@ghatana/platform-utils` exists for exactly this purpose. YAPPC web already imports `@ghatana/platform-utils` for `cn`.

#### Goals
- Identify which `@yappc/core` exports are truly YAPPC-domain vs generic utilities
- Generic utilities (string helpers, type utilities, common constants) → `@ghatana/platform-utils`
- YAPPC domain types/constants stay in `@yappc/core` (renamed to `@yappc/domain` if warranted)

#### Tasks

**S5-1** Catalog all exports from `@yappc/core/utils`, `@yappc/core/types`, `@yappc/core/constants`  

**S5-2** Classify each export:
- Generic (e.g., `formatDate`, `truncate`, `cn`) → move to `@ghatana/platform-utils`
- YAPPC-domain (e.g., `CodeProject`, `IDESession`, `CollabUser`) → keep in `@yappc/core`

**S5-3** Add generic utilities to `platform/typescript/foundation/platform-utils/src/`  
Each utility must:
- Have explicit TypeScript types (no `any`)
- Have unit tests in `__tests__/`
- Have JSDoc

**S5-4** Update all `@yappc/core/utils` imports to `@ghatana/platform-utils` where applicable  

**S5-5** Remove deleted utilities from `@yappc/core`; update exports map  

**S5-6** Add `@ghatana/platform-utils` to `@yappc/core` peer dependencies so both can coexist during transition  

#### Definition of Done
- [ ] All generic utilities moved to `@ghatana/platform-utils`
- [ ] `@yappc/core` contains only YAPPC-domain exports
- [ ] All consumers updated
- [ ] `tsc --noEmit` clean across YAPPC workspace
- [ ] `@ghatana/platform-utils` tests cover new additions at 80%+

---

### Sprint 6 (Weeks 11–12): @yappc/api HTTP Layer Adoption
**Priority**: P1 — Duplicate HTTP client infrastructure  
**Theme**: _@ghatana/api is the HTTP substrate; product libs own domain calls_

#### Context
`@yappc/api` implements auth, AI, GraphQL, devsecops — all legitimate product-specific concerns. The problem is it rolls its own HTTP transport instead of using `@ghatana/api` (which has retry logic, middleware, error classification). YAPPC effectively has two HTTP client implementations.

#### Goals
- `@yappc/api` delegates all HTTP to `@ghatana/api` (`ApiClient`)
- `@yappc/api` graph/auth/devsecops remain as product-specific features on top of the shared transport
- No reimplementation of retry, error parsing, header management

#### Tasks

**S6-1** Audit `@yappc/api` HTTP call sites — catalog everywhere it manually manages `fetch`, headers, retries  

**S6-2** Add `@ghatana/api` as a dependency of `@yappc/api`  

**S6-3** Replace manual HTTP calls with `ApiClient`:
```ts
// Before
const response = await fetch('/api/ai', { headers: buildHeaders() });
// After
const client = new ApiClient({ baseUrl: config.apiBase });
const result = await client.get<AiResponse>('/ai', { headers: { ... } });
```

**S6-4** Validate all GraphQL responses with Zod schemas at the boundary  

**S6-5** Validate auth token responses with Zod schemas  

**S6-6** Update `@yappc/api` to export typed service classes that use `ApiClient` internally  

**S6-7** Update YAPPC web app to use the updated `@yappc/api`  

#### Definition of Done
- [ ] `@yappc/api` has `@ghatana/api` as its HTTP substrate
- [ ] No manual `fetch` calls without `ApiClient`
- [ ] All API response types validated with Zod
- [ ] No auth tokens logged (security check)
- [ ] All existing `@yappc/api` tests pass

---

### Sprint 7 (Weeks 13–14): Testing Library Consolidation
**Priority**: P2 — Multiple testing utility libs creating inconsistency  
**Theme**: _One testing contract for each runtime_

#### Context
- `platform/typescript/testing/` exists as `@ghatana/platform-testing` (or similar)
- `@yappc/testing` has product-specific test utilities
- `@flashit/shared` includes testing utilities mixed with shared types

#### Goals
- Generic test helpers (Vitest setup, mock factories, custom matchers) → `platform/typescript/testing`
- Product-specific test helpers (YAPPC context providers, mock IDE sessions) → remain in product
- `@flashit/shared` split: types → `@flashit/types`, API client → uses `@ghatana/api`

#### Tasks

**S7-1** Audit `@yappc/testing` — separate generic vs YAPPC-specific  

**S7-2** Move generic test utilities to `platform/typescript/testing/src/`:
- Custom Vitest matchers
- React Testing Library setup helpers
- Mock factory patterns
- MSW (Mock Service Worker) base config  
Export as `@ghatana/platform-utils/testing` or new `@ghatana/testing` (if scope justifies it per §12)

**S7-3** Keep YAPPC-specific test utilities in `@yappc/testing` but slimmed down  

**S7-4** Split `@flashit/shared`:
```
@flashit/shared → @flashit/types    (types, schemas)
              → uses @ghatana/api   (HTTP calls refactored)
```

**S7-5** Update all YAPPC test file imports  

**S7-6** Verify all YAPPC tests pass after migration  

#### Definition of Done
- [ ] Generic test utilities in platform
- [ ] `@yappc/testing` is YAPPC-domain only
- [ ] `@flashit/shared` types separated from API concerns
- [ ] Test suites green across YAPPC and Flashit

---

### Sprint 8 (Weeks 15–16): TypeScript Strictness Audit and Enforcement
**Priority**: P2 — Type safety baseline per §26  
**Theme**: _No `any`, no implicit types, no unchecked access_

#### Context
Per §26 and §7 of guidelines, all TypeScript must have `strict: true`, `noImplicitAny`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`. The audit found inconsistent tsconfig across products.

#### Goals
- All platform packages: strict tsconfig enforced
- All product UI libraries: strict tsconfig enforced
- Zero `any` in public APIs (boundary adapters may use `unknown`)
- ESLint `@typescript-eslint/no-explicit-any: error` across all packages

#### Tasks

**S8-1** Audit all `tsconfig.json` files in `platform/typescript/*` — add missing strict flags  

**S8-2** Audit all product library tsconfigs:
- `@yappc/*` frontend libs
- `@dcmaar/*` TypeScript libs
- `@tutorputor/*` TypeScript libs
- `@flashit/*` TypeScript libs

**S8-3** Add strict flags incrementally, fix type errors file by file:
```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "noImplicitReturns": true,
    "noFallthroughCasesInSwitch": true
  }
}
```

**S8-4** Replace all `any` in public APIs with `unknown` + type guards  

**S8-5** Add global ESLint rule `@typescript-eslint/no-explicit-any: error` to root `eslint.config.js`  

**S8-6** Enable `@typescript-eslint/recommended-requiring-type-checking` ruleset  

**S8-7** Add `tsc --noEmit` check to Turborepo pipeline for all packages  

#### Definition of Done
- [ ] All platform packages pass strict tsc
- [ ] All product UI libs pass strict tsc
- [ ] ESLint reports zero `any` violations
- [ ] `turbo run type-check` is green
- [ ] CI fails on any new `any` introduction

---

### Sprint 9 (Weeks 17–18): Zod Boundary Validation
**Priority**: P2 — Runtime safety at all API boundaries per §27  
**Theme**: _Trust nothing from outside; validate at the door_

#### Goals
- Every API response parsed through Zod in `@ghatana/api` consumers
- Every environment config validated through Zod
- Every `@yappc/api` response type has a Zod schema
- `@dcmaar/browser-extension-core` event payloads validated at serialization boundary

#### Tasks

**S9-1** Add response schema validation to `@ghatana/api` — optional schema parameter on `get/post/put/delete`:
```ts
const data = await client.get('/users', { schema: UserListSchema });
// throws ValidationError if response doesn't match schema
```

**S9-2** Add Zod schemas for all `@yappc/api` service responses  

**S9-3** Add environment variable validation in all app entry points:
```ts
const envSchema = z.object({
  API_BASE_URL: z.string().url(),
  NODE_ENV: z.enum(['development', 'production', 'test']),
}).strict();
export const env = envSchema.parse(process.env);
```

**S9-4** Validate DCMAAR browser extension message payloads with Zod  

**S9-5** Validate AEP/Data-Cloud API responses in `@data-cloud/ui`  

**S9-6** Replace all `JSON.parse(x) as T` patterns with `Schema.parse(JSON.parse(x))`  

#### Definition of Done
- [ ] No raw `JSON.parse(...) as T` in any platform or product lib
- [ ] All env vars validated at startup
- [ ] All cross-service API responses Zod-validated
- [ ] `@ghatana/api` supports optional schema parameter
- [ ] Tests verify `ValidationError` is thrown on bad input

---

### Sprint 10 (Weeks 19–20): Bundle Optimization + Export Maps
**Priority**: P2 — Per §28 bundle size requirements  
**Theme**: _Ship less, load faster_

#### Goals
- `@ghatana/design-system`: add granular export maps for tree-shaking (131 exports → sub-paths)
- `@ghatana/tokens`: simplify to ≤6 export paths (from 13)
- All platform libs: gzipped core < 100KB, features < 50KB
- CI bundle analysis enabled for `@data-cloud/ui` and YAPPC web

#### Tasks

**S10-1** Add export maps to `@ghatana/design-system/package.json`:
```json
"exports": {
  ".": "./dist/index.js",
  "./atoms": "./dist/atoms/index.js",
  "./molecules": "./dist/molecules/index.js",
  "./organisms": "./dist/organisms/index.js",
  "./audit": "./dist/audit/index.js",
  "./privacy": "./dist/privacy/index.js"
}
```

**S10-2** Update all consumers: import from sub-paths instead of barrel:
```ts
// Before
import { Button, Alert, DataTable } from '@ghatana/design-system';
// After
import { Button } from '@ghatana/design-system/atoms';
import { Alert } from '@ghatana/design-system/molecules';
```

**S10-3** Simplify `@ghatana/tokens` exports from 13 to ≤6 intentional paths  

**S10-4** Enable `@next/bundle-analyzer` in YAPPC web and Data-Cloud UI  

**S10-5** Add `turbo run bundle:analyze` pipeline  

**S10-6** Fail CI if gzipped bundle exceeds tier limits:
- Core libs: 100KB
- Feature modules: 50KB
- Shared components: 25KB
- Utility libs: 10KB

**S10-7** Enable Rollup tree-shaking (`sideEffects: false`) in all platform package.json  

#### Definition of Done
- [ ] `@ghatana/design-system` exports via sub-paths
- [ ] Bundle analysis in CI for web apps
- [ ] All platform lib gzip sizes within limits
- [ ] `sideEffects: false` in all platform packages
- [ ] No circular imports (verified by `madge` or eslint import rules)

---

### Sprint 11 (Weeks 21–22): Documentation + Naming Audit
**Priority**: P3 — Developer experience and §24 compliance  
**Theme**: _Every library has a README; every export has a type; every package has the right name_

#### Goals
- Every platform package has a README with: name, purpose, quick start, API surface
- `@ghatana/dcmaar` and `@ghatana/dcmaar-backend-shim` are audited (they appear misplaced in products but named as platform)
- Canonical names enforced per §17

#### Tasks

**S11-1** Audit `@ghatana/dcmaar` and `@ghatana/dcmaar-backend-shim`:
- Why are product packages using the `@ghatana/` scope?
- If product-specific: rename to `@dcmaar/*`
- If genuinely shared platform: move to `platform/`

**S11-2** Write or update README for each platform library missing one  

**S11-3** Verify all platform packages follow §17 naming rules:
- `@ghatana/platform-*`: cross-cutting infra (platform-utils, platform-shell)
- `@ghatana/<name>`: UI or domain-specific (design-system, tokens, theme, etc.)

**S11-4** Add ESLint import rule for forbidden cross-product dependencies:
```json
{ "no-restricted-imports": ["error", { "paths": [
  { "name": "@yappc/*", "message": "YAPPC libs cannot be imported from DCMAAR" }
]}}
```

**S11-5** Update `docs/NAMING_CONVENTIONS.md` with canonical package table  

#### Definition of Done
- [ ] All platform packages have README
- [ ] `@ghatana/dcmaar` scope decision resolved
- [ ] Cross-product import rule in ESLint
- [ ] Naming conventions doc updated
- [ ] pnpm audit runs clean (no naming violations)

---

### Sprint 12 (Weeks 23–24): Governance + CI Enforcement
**Priority**: P3 — Long-term guardrails  
**Theme**: _Automate what the architecture requires_

#### Goals
- Turborepo pipeline enforces build → type-check → lint → test → bundle-check in order
- ESLint boundary rules catch new violations before merge
- No product lib can `import` a sibling product lib (cross-product guard)
- Bundle size CI step fails fast

#### Tasks

**S12-1** Add Turborepo task to `turbo.json`:
```json
{
  "boundary-check": { "dependsOn": ["build"], "outputs": [] },
  "type-check":     { "dependsOn": ["build"], "outputs": [] },
  "bundle:analyze": { "dependsOn": ["build"], "outputs": ["dist/**"] }
}
```

**S12-2** Add `eslint-plugin-boundaries` rules to root `eslint.config.js`:
```js
boundaries: {
  elements: [
    { type: 'platform', pattern: 'platform/typescript/*' },
    { type: 'product-yappc', pattern: 'products/yappc/*' },
    { type: 'product-dcmaar', pattern: 'products/dcmaar/*' }
  ],
  rules: [
    { from: 'platform', disallow: [['product-*']] },
    { from: 'product-yappc', disallow: [['product-dcmaar']] },
    { from: 'product-dcmaar', disallow: [['product-yappc']] }
  ]
}
```

**S12-3** Add import-order enforcement with `eslint-plugin-import`:
```js
'import/order': ['error', { groups: ['builtin','external','internal','parent','sibling'] }]
```

**S12-4** Enable `package.json` `exports` validation in CI  

**S12-5** Create `scripts/check-library-boundaries.ts` — validates no product lib imports another product's lib  

**S12-6** Add CODEOWNERS entries for `platform/typescript/*`  

#### Definition of Done
- [ ] Turborepo pipeline green end-to-end
- [ ] Boundary ESLint rules active, zero violations
- [ ] Import order enforced
- [ ] Cross-product import check in CI
- [ ] CODEOWNERS updated

---

## Summary Table

| Sprint | Theme | Libraries Changed | Priority | Risk |
|--------|-------|-------------------|----------|------|
| S1 (W1-2) | Remove unused platform stubs | 6 specialized *-ui libs → design-system | P0 | Low |
| S2 (W3-4) | DCMAAR type consolidation | @dcmaar/agent-types, agent-ui, browser-ext-ui | P0 | Low |
| S3 (W5-6) | Browser extension event abstraction | @ghatana/realtime, @dcmaar/browser-extension-core | P1 | Medium |
| S4 (W7-8) | @yappc/canvas removal | @yappc/canvas → @ghatana/canvas | P1 | Low |
| S5 (W9-10) | @yappc/core generic extraction | @yappc/core → @ghatana/platform-utils | P1 | Medium |
| S6 (W11-12) | @yappc/api HTTP adoption | @yappc/api uses @ghatana/api transport | P1 | Medium |
| S7 (W13-14) | Testing lib consolidation | @yappc/testing, @flashit/shared | P2 | Low |
| S8 (W15-16) | TypeScript strictness audit | All platform + product UI libs | P2 | Medium |
| S9 (W17-18) | Zod boundary validation | All API consumers | P2 | Low |
| S10 (W19-20) | Bundle optimization + export maps | @ghatana/design-system, @ghatana/tokens | P2 | Low |
| S11 (W21-22) | Documentation + naming audit | All platform libs, @ghatana/dcmaar scope | P3 | Low |
| S12 (W23-24) | Governance + CI enforcement | eslint-plugin-boundaries, turbo pipeline | P3 | Low |

---

## Metrics and Success Criteria

| Metric | Baseline | Target |
|--------|----------|--------|
| Platform TypeScript packages | 22 | 16 (-6 specialized stubs) |
| DCMAAR TypeScript packages | 26 | 22 (-4 consolidated) |
| Libraries with zero consumers | 6 | 0 |
| Packages lacking strict tsconfig | TBD (audit S8) | 0 |
| `any` in public APIs | Unknown | 0 |
| API responses without Zod schema | Unknown | 0 |
| Platform libs with README | Unknown | 16/16 |
| Circular imports | Unknown | 0 |
| Bundle limit violations | Unknown | 0 |
| Cross-product boundary violations | Unknown | 0 |

---

## Architecture Invariants to Preserve

These do NOT change across any sprint:

1. **Platform cannot depend on products** — `@ghatana/*` must never import `@yappc/*`, `@dcmaar/*`, etc.
2. **Product domain logic does not move to platform** — only generic, domain-free utilities go up
3. **@ghatana/api is the HTTP substrate** — products extend it; they do not replace it
4. **Event base types live in `@ghatana/realtime`** — products extend `PlatformEvent`; they do not reinvent it
5. **`@ghatana/design-system` is the single UI component source** — specialized domain UI is a sub-path of it, not a sibling package
6. **Types stay close to their domain** — DCMAAR types in `@dcmaar/types`, YAPPC types in `@yappc/core`; nothing leaks into platform unless it is truly generic

---

## Guidelines Update Notes

The following areas of `.github/copilot-instructions.md` are confirmed accurate and require **no changes**:

- §5 TypeScript Standards — implementation-time typing requirements are correct
- §17 Package Naming — two-tier model (`@ghatana/platform-*` vs `@ghatana/<name>`) is correct and consistent with ADR-016
- §25 Forward-Fix Migration — no backward compat; replace in-place; correct approach
- §27 Runtime Type Validation — Zod at boundaries, correct
- §28 Bundle Optimization — size limits and CI gates are correct

The following areas reflect findings from the audit that should be **noted as known gaps** (no guideline change needed, just implementation required per this plan):

- §5: "No `any`" — violated in several product libs; Sprint 8 closes this
- §26: Strict tsconfig — not universally applied; Sprint 8 closes this
- §6: State management patterns — YAPPC uses Jotai (correct per guidelines), but `@yappc/state` creates a product wrapper that should use Jotai atoms typed as `atom<T>` without re-exporting Jotai itself
- §12 Library governance — `@ghatana/dcmaar` in products/ using platform scope violates naming; Sprint 11 resolves
