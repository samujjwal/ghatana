# YAPPC V5 Corrected Implementation Plan

**Date:** 2026-04-06  
**Purpose:** Replace the stale greenfield-style V5 plan with a current, non-redundant hardening and alignment program  
**Planning Horizon:** 8 weeks  
**Delivery Mode:** Fix-forward, canonical-module-first, zero-warning mindset

---

## 1. Planning Premise

This is **not** a plan to build YAPPC from scratch.

The codebase already contains:

- real ActiveJ HTTP APIs
- dedicated YAPPC CI workflows
- Data Cloud repositories and Prisma migrations
- a real agent runtime with optional LLM integration
- canonical `yappc-*` backend modules
- broad unit, integration, contract, and E2E test surfaces

The remaining work is a **hardening and truth-alignment program** across five streams:

1. Authentication and security boundary enforcement
2. Build, CI, and topology truth alignment
3. Frontend namespace and compatibility cleanup
4. AI runtime production hardening
5. Test signal, observability, and release readiness

---

## 2. Exit Criteria

This plan is complete only when all of the following are true:

1. No protected production flow can run with mock auth or guest fallback.
2. Canonical module names are the default vocabulary in CI, docs, and ownership references.
3. Frontend imports and aliases converge on the intended canonical package surface.
4. Production AI startup fails closed when provider configuration is missing.
5. Test retries no longer hide core instability.
6. Docs, settings, and workflows describe the same product topology.

---

## 3. Workstream Overview

| Stream | Priority | Goal | Outcome |
| --- | --- | --- | --- |
| A | P0 | Secure auth and fail closed | No mock auth in production paths |
| B | P0 | Align build and CI with canonical topology | One truthful module map |
| C | P1 | Complete frontend consolidation cleanup | Canonical package surface only |
| D | P1 | Harden AI runtime for production | No silent stub fallback in prod |
| E | P1 | Make tests and release gates trustworthy | Low-noise CI and explicit release readiness |

---

## 4. Week-By-Week Plan

### Week 1: Baseline Freeze And Truth Alignment

#### A1. Lock the evidence baseline

**Goal:** Make the current architecture explicit before changing behavior.

**Tasks**

1. Record the canonical backend module list and the still-supported alias modules.
2. Identify all workflows that reference legacy YAPPC paths.
3. Identify all docs that still describe removed, renamed, or compatibility-only structures.

**Primary surfaces**

- `products/yappc/settings.gradle.kts`
- `.github/workflows/yappc-ci.yml`
- `.github/workflows/product-isolated-ci.yml`
- `.github/workflows/contract-tests.yml`
- `products/yappc/docs/**`

**Acceptance criteria**

- one approved module truth table exists
- each legacy path has either a sunset owner or an explicit compatibility reason
- active docs and archive docs are clearly separated

#### B1. Establish a release blocking checklist

**Goal:** Convert the updated audit into enforceable delivery gates.

Completed: release-readiness guidance now lives in `products/yappc/docs/RELEASE_READINESS_CHECKLIST.md` and references only active workflows, checks, and docs.

---

### Weeks 2-3: Auth Hardening And Security Boundaries

#### A4. Add auth regression coverage

**Goal:** Make auth hardening durable.

**Status**

- the web auth client, login route, and Playwright auth specs now use the real `/login` flow, canonical `auth-session` storage shape, and redirect preservation without demo shortcuts
- focused auth regression suites are green across the frontend unit layer, Fastify auth middleware, Java HTTP auth filter, and frontend auth-policy gate
- the YAPPC web runtime now boots cleanly enough for browser auth execution, and the refreshed Playwright auth suite passes against the real `/login` and `/workspaces` flow without demo shortcuts

**Acceptance criteria**

- auth regression suite remains green across frontend, BFF, and Java HTTP surfaces
- browser auth journey executes in Playwright without relying on dev-only auth shortcuts
- no test depends on `VITE_MOCK_AUTH` outside explicitly marked dev-only suites

---

### Weeks 3-4: Build, CI, And Topology Truth Alignment

Completed: YAPPC CI now targets canonical service/domain modules, and active contributor docs now route through `products/yappc/docs/START_HERE_ARCHITECTURE.md` plus `MODULE_CATALOG.md` instead of retired module names.

---

### Weeks 4-5: Frontend Consolidation Completion

Completed: frontend config, tests, and active docs now default to canonical `@yappc/*` imports, legacy `reactflow` shims were removed, and namespace enforcement now blocks deprecated `@ghatana/yappc-*` and `reactflow` imports in active code.

---

### Weeks 5-8: Remaining Work Only

Completed since this plan was corrected:

- agent catalog ownership is now validated with actionable runtime-vs-catalog reporting and strict CI failure on catalog-only drift
- AI observability release gating now checks current metrics, tracing, and startup diagnostics surfaces
- default Vitest and Playwright runs no longer rely on blanket retries, and quarantine retries are explicit opt-in
- critical journeys now have a dedicated test ownership map and CI publishes a single release evidence bundle
- release diagnostics now verify liveness, readiness, authenticated metrics, and rollback/runbook guidance
- compatibility-only frontend alias tests have been removed from the default gate and replaced with active-code boundary enforcement
- focused retry-sensitive frontend suites are green without retries, including the CrossTabSync and LifecycleWebSocketService regressions exercised in the current cleanup batch
- broader frontend verification now passes namespace, auth-policy, duplication-boundary, manifest, workspace-dependency, and typecheck stages using ESM-compatible scripts and a stable lint heap budget

#### Remaining 1. Restore broad frontend verify to green

**Goal:** Finish the broad frontend gate so the canonical `verify` path is trustworthy beyond the focused touched-file batch.

**Tasks**

1. Establish package-accurate type-aware lint coverage for the legacy `libs/yappc-ui` surface instead of relying on a root-only project shape that does not model that package cleanly.
2. Resolve the pre-existing `libs/yappc-ui` lint debt now surfaced by the broader gate, especially unresolved-type and unsafe-access findings in utility and accessibility helper modules.
3. Return `pnpm -C products/yappc/frontend verify` to green with the no-retry default preserved.

**Acceptance criteria**

- `pnpm -C products/yappc/frontend verify` passes end-to-end from custom checks through lint and test
- no verify-stage script depends on CommonJS inside the ESM frontend scripts package
- the broad lint gate runs without heap exhaustion and without unresolved-type failures caused by missing package ownership or project wiring

Completed: compatibility-only frontend alias assertions were removed from the default Vitest gate, CI now enforces duplication-boundary checks directly, the critical-journey map remains the release-gate source of truth, and the active `verify` path has already been cleared through all custom checks plus TypeScript typecheck.

## 5. Remaining Backlog

### P2

1. Simplify persistence ownership documentation.
2. Continue tightening release runbooks and observability dashboards where they still reference stale metrics.
3. Remove historical migration material from the active contributor path.

- broad frontend lint/project wiring for legacy `libs/yappc-ui`
- remaining `libs/yappc-ui` type-aware lint debt surfaced by the canonical verify path

### Architecture/Documentation

- module truth table
- active vs archive doc cleanup
- final readiness checklist

---

## 7. Risks And Mitigations

### Risk: alias cleanup breaks compatibility consumers

**Mitigation:** publish owner list and usage inventory before removal; remove aliases in small batches.

### Risk: auth hardening breaks local development

**Mitigation:** keep dev-only auth paths, but isolate them behind non-production build and runtime guards.

### Risk: AI hardening blocks developer workflows when providers are unavailable

**Mitigation:** keep explicit dev/test stub mode, but forbid silent production fallback.

### Risk: retry removal exposes too many failing tests at once

**Mitigation:** stage retry removal by suite, tag flake owners, and quarantine only with explicit deadlines.

---

## 8. Success Metrics

### Security

- zero production code paths can enable mock auth
- all protected endpoints fail closed without valid auth context

### Architecture Truth

- active docs, settings, and CI all use the same module map
- legacy names are compatibility-only, documented, and sunset-owned

### Frontend Consolidation

- deprecated package imports trend to zero in active app code
- React Flow shims are removed or isolated behind one compatibility boundary

### AI Runtime

- production boot fails when provider config is absent
- AI metrics and tracing exist for release environments

### Quality Gates

- no blanket unit-test retries
- critical journeys have explicit release evidence

---

## 9. Deliverables

By the end of this plan, YAPPC should have:

1. a corrected and trustworthy architecture narrative
2. hardened auth without production bypasses
3. CI workflows aligned to the current module topology
4. a cleaner frontend namespace and compatibility surface
5. production-safe AI runtime behavior
6. lower-noise, higher-signal release verification

---

## 10. Final Planning Note

The previous V5 plan assumed YAPPC still needed fundamental construction work across CI, controllers, persistence, and agent runtime. That assumption is no longer true.

The right plan now is shorter and stricter: **finish the hardening, remove migration residue, and make the documented architecture tell the truth.**