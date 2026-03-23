# Ghatana Monorepo Boundary Cleanup Tracker

**Based on:** `docs/audits/ghatana-monorepo-simplification-boundary-audit-2026-03-22.md`  
**Started:** 2026-03-22  
**Goal:** Implement all audit-recommended structural fixes. No backward-compatibility constraints (active dev, no prior release).

---

## Legend
- ✅ Done
- 🔄 In Progress
- ⬜ Not Started
- ❌ Blocked

---

## Phase 0 — Immediate Structural Fixes (1–3 days)
_Goal: Make repo truth consistent. No build impairment._

| # | Task | Status | Notes |
|---|------|--------|-------|
| P0-1 | Delete 8 AEP legacy `platform-*` shell dirs | ✅ | `platform-{agent,analytics,api,connectors,core,engine,registry,scaling}/` — only contained `build.gradle.kts` shells; `aep-*` counterparts have real content |
| P0-2 | Fix AEP UI stale path aliases | ✅ | `vite.config.ts` + `vitest.config.ts` pointed to `capabilities/design-system` + `capabilities/canvas-core` (no longer exist) → updated to `design-system/` + `canvas/` |
| P0-3 | Delete empty `platform/typescript/capabilities/` folder | ✅ | Contents were migrated to `design-system/`, `realtime/`, `canvas/` via rsync |
| P0-4 | Remove deprecated YAPPC modules from root `settings.gradle.kts` | ✅ | Removed: `services:ai`, `services:scaffold`, `backend:websocket`, `core:scaffold:packs`, `infrastructure:security`, `launcher` |
| P0-5 | Migrate missing `scaffold:packs` resources to `scaffold:core` | ✅ | Moved 5 missing resource files before deleting packs module |
| P0-6 | Delete YAPPC deprecated module directories | ✅ | Deleted: `services/ai`, `services/scaffold`, `backend/websocket`, `infrastructure/security`, `launcher`, `core/scaffold/packs` |

---

## Phase 1 — YAPPC Graph Cleanup (1–2 weeks)
_Goal: YAPPC build graph shrinks; no duplicate module pairs._

| # | Task | Status | Notes |
|---|------|--------|-------|
| P1-1 | Verify `services:lifecycle` build passes after absorbing `services:ai` + `services:scaffold` | ✅ | lifecycle was already the canonical home |
| P1-2 | Verify `backend:api` build passes after absorbing `backend:websocket` | ✅ | api already had all websocket files |
| P1-3 | Verify `backend:auth` build passes after absorbing `infrastructure:security` | ✅ | auth already had security files |
| P1-4 | Verify `core:scaffold:core` passes after absorbing `core:scaffold:packs` | ✅ | all Java + resources migrated |
| P1-5 | Fix `services:scaffold/build.gradle.kts` dep on `core:scaffold:packs` in any surviving ref | ✅ | module deleted |
| P1-6 | Ensure root `settings.gradle.kts` YAPPC section matches local `products/yappc/settings.gradle.kts` | ✅ | reconciled in P0-4 |

---

## Phase 2 — Cross-Product Dependency Cleanup (2–4 weeks)
_Goal: AEP and YAPPC depend only on product-owned contracts, not implementations._

| # | Task | Status | Notes |
|---|------|--------|-------|
| P2-1 | AEP `server/build.gradle.kts`: replace `data-cloud:platform` → `data-cloud:spi` | ✅ | Changed `api(data-cloud:platform)` → `implementation(data-cloud:spi)` in `products/aep/server/build.gradle.kts` |
| P2-2 | Identify YAPPC modules depending on `data-cloud:platform` | ✅ | Found: `infrastructure/datacloud`, `core/lifecycle`, `services`, `services/platform`, `backend/api`. `backend/api` stays on platform for versioning APIs. |
| P2-3 | Migrate YAPPC DC consumers to `data-cloud:spi` | ✅ | Migrated: `infrastructure/datacloud` (all adapters, repositories, mapper, tests), `core/lifecycle` (DataCloudArtifactStore), `services/lifecycle` (LifecycleServiceModule). Added spi binding in `DataCloudModule`. `services/platform` removed unused dep. |
| P2-4 | Identify YAPPC modules depending on AEP implementation (not contracts) | ✅ | `backend/api`→`aep-engine` (AepLibraryClient, YappcBackpressureConfig); `core/agents/runtime`→`aep-central-runtime` (YappcAepIntegration) |
| P2-5 | Introduce adapter layer or use `aep-operator-contracts` for YAPPC → AEP coupling | ✅ | Moved `AepEngine`+`BackpressureStrategy` to `aep-operator-contracts`; added `AgentRegistryContracts` interface; `AepLibraryClient` now takes injected `AepEngine`; `YappcAepIntegration` uses `AgentRegistryContracts`; `core/agents/runtime` dep changed to `aep-operator-contracts` |

---

## Phase 3 — Module Consolidation (revised: packages over splits)
_Goal: Remove redundant/empty modules. Use package-level separation within existing modules instead of new Gradle module splits._

**Decision rationale:** Module splits add build-file boilerplate (new `build.gradle.kts`, `settings.gradle.kts` entries, dep wiring) without meaningful enforcement benefit in a monorepo. Package-level organization achieves the same spatial separation. Only truly empty/duplicate modules are removed.

| # | Task | Status | Notes |
|---|------|--------|-------|
| P3-1 | ~~Split `data-cloud/platform`~~ → CANCELLED | ✅ | 628 files, but package structure already provides `analytics`, `plugins`, `api`, `client` separation. No consumers need a subset. Module split adds 6 build files + complex dep wiring for zero benefit. |
| P3-2 | ~~Split `yappc/backend/api`~~ → CANCELLED | ✅ | Leaf module — nothing depends on it externally. Packages (`aep`, `websocket`, `config/modules`) already separate concerns. Split would only add boilerplate. |
| P3-3 | Delete `platform/java/ai-api` (100% duplicate of `ai-integration`) | ✅ | Every single one of `ai-api`'s 27 classes existed in `ai-integration`. Removed `api(ai-api)` dep from `ai-integration/build.gradle.kts`, removed entry from `settings.gradle.kts`, deleted directory. |
| P3-4 | Dissolve `aep-runtime-core` empty shell → `aep-engine` | ✅ | `aep-runtime-core` had 0 source files — pure facade re-exporting `aep-engine`. Updated 7 dependents (`aep-api`, `aep-registry`, `aep-connectors`, `aep-agent`, `server`, `aep-scaling`, `aep-analytics`) to use `aep-engine` directly. Removed from `settings.gradle.kts`, deleted directory. |
| P3-5 | ~~Split `services/lifecycle`~~ → CANCELLED | ✅ | Packages `lifecycle`, `lifecycle/config`, `ai`, `scaffold` already organize concerns. No external split needed. |

---

## Phase 4 — Frontend Splits and Alias Cleanup (8–12 weeks)
_Goal: YAPPC frontend becomes maintainable; no dead import paths._

| # | Task | Status | Notes |
|---|------|--------|-------|
| P4-1 | Split `products/yappc/frontend/libs/canvas` → `canvas-kernel`, `canvas-editor`, `canvas-domain-overlays` | ⬜ | 583 files / 289k lines |
| P4-2 | Split `products/yappc/frontend/libs/ui` → `ui-primitives`, `ui-domain`, `ui-workflow`, `ui-legacy` | ⬜ | 839 files / 189k lines |
| P4-3 | Remove `@yappc/core` (compat facade) — replace with explicit imports or rename to `@yappc/compat-core` | ⬜ | Re-exports deprecated packages |
| P4-4 | Remove `@ghatana/utils` wrapper → fold consumers into `@ghatana/platform-utils` | ⬜ | Pure re-export compatibility package |
| P4-5 | Clean legacy `@ghatana/yappc-*` alias family from `tsconfig.base.json` | ⬜ | Old naming still active alongside new `@yappc/*` names |
| P4-6 | Fix `products/yappc/frontend/libs/ui` self-import cycle (`@yappc/ui` inside `@yappc/ui`) | ⬜ | After split, each sub-package imports explicitly |

---

## Guardrails to Implement (ongoing)
| # | Task | Status | Notes |
|---|------|--------|-------|
| G-1 | CI check: root vs product settings parity for YAPPC modules | ⬜ | Add gradle task to compare |
| G-2 | CI check: no product→product dependency except allowlisted contracts | ⬜ | ArchUnit or Gradle config |
| G-3 | CI check: wrapper-only packages detector | ⬜ | Detect packages with only re-exports |
| G-4 | CI check: empty directory/package detector | ⬜ | Find and fail on empty module dirs |

---

## Key Decisions Made
1. **No backward compatibility**: Direct deletion of duplicate/deprecated modules without migration bridges.
2. **Canonical absorption**: lifecycle > services:ai + services:scaffold; backend:api > backend:websocket; backend:auth > infrastructure:security; scaffold:core > scaffold:packs.
3. **AEP platform-* directories**: Were shell-only (build.gradle.kts only); deleted after confirming aep-* counterparts have all real content.
4. **scaffold:packs resources**: 5 resource files not yet in scaffold:core were migrated before deleting packs module.

## Reference
- Audit: `docs/audits/ghatana-monorepo-simplification-boundary-audit-2026-03-22.md`
- Target layout: Audit §49
- Execution roadmap: Audit Table 20
