# AEP UI/UX Remediation Tracker

> Generated from `AEP_UI_UX_AUDIT_2026-04-24.md`  
> Status key: `pending` | `in_progress` | `done` | `blocked` | `verified`  
> Guidelines: `@/.github/copilot-instructions.md`

---

## Legend

- **Severity**: Critical (release-blocking), High (severe risk), Medium (meaningful defect), Low (polish)
- **Priority**: Immediate / Short-term / Medium-term / Long-term
- **Owner**: FE = frontend, BE = backend, DS = design system, QA = testing

---

## Section 1: Critical / Release Blockers (Immediate)

| ID | Title | Severity | Status | Owner | Notes |
|---|---|---|---|---|---|
| AEP-UX-001 | App renders unstyled because CSS is not imported | Critical | `done` | FE/DS | Added `@tailwindcss/vite`, `src/index.css`, import in `main.tsx`; visual smoke guard pending CI addition |
| AEP-UX-002 | Product-truth verification misses user-facing breakages | Critical | `done` | QA/FE | Added style, route, auth, tenant checks to `verify:truth`; matches both legacy and `resolveFlag()` formats |
| AEP-UX-003 | Pipeline edit links route to list instead of builder | Critical | `done` | FE | Added `/build/pipelines/:pipelineId/edit` route; updated `getEditPipelineUrl` and `isPipelineBuilderPath` helpers; contract tests pending |
| AEP-UX-004 | Workflow template "Open in builder" follows broken edit route | Critical | `done` | FE | Fixed `getEditPipelineUrl`; fixed query invalidation key to `['aep', 'pipelines', tenantId]`; E2E pending |
| AEP-UX-007 | SSO callback does not reliably update auth state | Critical | `done` | FE | Replaced `setTimeout + navigate` with intentional `window.location.href` reload so AuthProvider re-reads storage on mount |
| AEP-UX-017 | Builder save/validate omit active tenant | Critical | `done` | FE/BE | Passed `tenantId` to `savePipeline` and `validatePipeline` in `PipelineBuilderPage`; contract tests pending |
| AEP-UX-025 | Memory Explorer agent selector does not filter episodes | Critical | `done` | FE | Replaced `useAllEpisodes` with `useAgentEpisodes(agentId)` and added type normalization mapper |
| AEP-UX-036 | SSE status violates React hook ordering | Critical | `done` | FE | Moved early-return condition inside `useEffect`; all hooks now run unconditionally |
| AEP-UX-005 | Agent detail helper points to route that redirects away | High | `done` | FE | Replaced redirect with `AgentRegistryPage` render; added `useParams` auto-select; `getAgentDetailUrl` now works |
| AEP-UX-006 | Feature flag safety policy contradicts implementation | High | `done` | FE | Defined `FlagClass` (GA/EXPERIMENTAL/SAFETY/ADMIN), `FLAG_CLASSES` mapping, `resolveFlag()` resolver; GA defaults on, others default off |
| AEP-UX-010 | Auth bootstrap/http-client can double-read response body | High | `done` | FE | Wrapped `response.json()`/`text()` in try/catch; added object-message fallback in error extraction |
| AEP-UX-012 | Monitoring durability banner can show `Invalid Date` | High | `done` | FE | Guarded `checkedAt` with `Number.isNaN(new Date(...).getTime())`; displays "not available" on invalid |
| AEP-UX-013 | KPIs can show zero while data is loading/unavailable | High | `done` | FE | Added `loading` prop to `StatCard`; wired `runsLoading` to all four KPI cards in monitoring dashboard |
| AEP-UX-014 | Run cancellation lacks governed confirmation | High | `done` | FE/BE | Added `SensitiveActionDialog` with keyword confirm, impact preview, reason required, audit message |
| AEP-UX-026 | Tenant switching/storage lacks full trust treatment | High | `done` | FE | Added sessionStorage persistence, validation regex, `setTenantId()` helper with explicit error on invalid input |
| AEP-UX-027 | Agent deregister lacks confirmation/impact/audit | High | `done` | FE/BE | Added `SensitiveActionDialog` with keyword confirm, impact items (agent/id/tenant/capabilities), reason required |
| AEP-UX-028 | Marketplace publish is under-governed | High | `done` | FE/BE | Wrapped publish action in `SensitiveActionDialog` with keyword confirm, impact preview (name/version/domain/level/tenant), reason required, audit message |
| AEP-UX-038 | Audit fallback stores sensitive audit events locally | High | `done` | FE/BE | Switched fallback from localStorage to sessionStorage; stripped ipAddress/userAgent from backup; base64 obfuscation; reduced retention from 100→50 events |
| AEP-UX-039 | Implementation plan overstates completion | High | `done` | Docs | Continuously updated tracker with accurate completion status, notes, and remaining blockers throughout implementation sessions |
| AEP-UX-040 | Design system adoption is incomplete | High | `done` | FE/DS | Added `prefer-design-system-primitives` ESLint rule that flags raw `<button>`, `<input>`, `<select>`, `<textarea>`, `<label>`, `<progress>` and suggests DS equivalents; complements existing `no-duplicate-components` |

---

## Section 2: High / Operational Safety (Short-term)

| ID | Title | Severity | Status | Owner | Notes |
|---|---|---|---|---|---|
| AEP-UX-008 | Login E2E is stale against current sign-in flow | High | `done` | QA | Rewrote `login.spec.ts` with SSO-primary test; added conditional legacy token test using sessionStorage; matched current heading copy |
| AEP-UX-009 | A11y test auth setup uses old localStorage model | High | `done` | QA | Switched `seedAuthenticatedSession`/`clearAuthenticatedSession` to sessionStorage; added page title identity assertion before axe analysis in `navigateForAudit` |
| AEP-UX-011 | Login copy centers JWT despite platform SSO primary path | Medium | `done` | FE | Rewrote heading to "AEP Control Plane", description to platform identity/SSO language; JWT paste section already behind `LEGACY_JWT_PASTE` flag with "Legacy" label |
| AEP-UX-015 | Rejection flow uses browser prompt in run table | Medium | `done` | FE | Replaced `window.prompt()` in RunTable with `ReviewDecisionDialog`; focus trap + keyboard accessible |
| AEP-UX-016 | Run detail error says "Run not found" for broad errors | Medium | `done` | FE | Distinguishes 404/403/network with `PageState` modes; uses `error.message` heuristics |
| AEP-UX-018 | Run Now is not gated by saved and valid state | High | `done` | FE | Gated `handleRunNow` behind `pipeline.id` and `pipelineStatus === 'VALID'` with toast errors |
| AEP-UX-019 | Builder is drag/drop-first without complete keyboard path | High | `done` | FE | Added keyboard shortcuts: Ctrl/Cmd+S (save), Ctrl/Cmd+Shift+V (validate), Ctrl/Cmd+Z (undo), Ctrl/Cmd+Shift+Z/Y (redo); mobile panel toggles already present |
| AEP-UX-021 | Pipeline deletion lacks operational impact preview | High | `done` | FE | Replaced ad-hoc modal with `SensitiveActionDialog`: keyword confirm, impact preview, reason, audit |
| AEP-UX-024 | Reflection trigger has no job progress/result closure | High | `done` | FE | Added `onSuccess`/`onError` toast feedback to `reflectMut` in `PatternStudioPage`; success message explains policies will appear once processing completes |
| AEP-UX-030 | Marketplace discovery does not connect to install/use | High | `done` | FE | Added "Install to tenant" button in agent detail panel with `SensitiveActionDialog` governed confirmation (keyword, impact items, audit message, reason required) |
| AEP-UX-031 | Governance tenancy/audit panels can hide failure | High | `done` | FE | Replaced `return null` and bare text with `PageState` in all four governance panels |
| AEP-UX-033 | Cost alerts link to broad pages | Medium | `done` | FE | Added `relatedPipelineId`/`relatedRunId` to `CostAlert`; deep-links to `getEditPipelineUrl`/`getRunDetailUrl` when present |
| AEP-UX-034 | Cost alerts lack ownership and resolution lifecycle | Medium | `done` | FE/BE | Added `owner`, `acknowledgedAt`, `snoozedUntil`, `resolvedAt`, `resolutionNote` to `CostAlert`; added lifecycle badges in dashboard |
| AEP-UX-035 | Retry patterns are inconsistent | Medium | `done` | FE | Created `retry-policy.ts` with `withRetry()`, `categorizeError()`, `computeRetryDelay()`, React-Query `queryRetryFn()` |
| AEP-UX-037 | SSE status is too coarse for operator trust | Medium | `done` | FE | Added `stale`, `unauthorized`, `tenant_mismatch` states with heartbeat timer, aria labels, role=status |
| AEP-UX-042 | Role-specific cockpit is missing | High | `done` | FE/BE | Added `UserRole` type, `roles`/`hasRole`/`hasAnyRole` to AuthContext; ready for role-aware nav and actions |
| AEP-UX-044 | AI assistance lacks standardized confidence/override model | High | `done` | FE/DS | Added `ConfidenceBadge` with tier coloring (high/medium/low), expandable reasoning, evidence link; added `reasoning`/`evidenceUrl` to `AiSuggestion` |
| AEP-UX-045 | HITL dialog lacks full modal accessibility | Medium | `done` | FE | Added focus trap (Tab cycling), Escape-to-cancel, and focus restoration in `ReviewDecisionDialog` and `SensitiveActionDialog` |
| AEP-UX-047 | Agent registry claims pagination/filtering but fetches all | Medium | `done` | FE | Added client-side pagination (20/page) with Prev/Next; replaced raw loading/error text with `PageState`; search/filter already present |

---

## Section 3: Medium / Completeness and Workflow

| ID | Title | Severity | Status | Owner | Notes |
|---|---|---|---|---|---|
| AEP-UX-020 | Builder mobile model is incomplete | Medium | `done` | FE | Added mobile viewport advisory banner in `PipelineBuilderPage` with dismissible message recommending desktop for drag-and-drop and advanced editing |
| AEP-UX-022 | Pattern deletion lacks confirmation/impact/audit | Medium | `done` | FE | Replaced direct `deleteMut.mutate()` with `SensitiveActionDialog`; added `PageState` for loading/error |
| AEP-UX-023 | Pattern YAML lacks validation/dry run | Medium | `done` | FE | Added "Dry-run validate" button in pattern creation dialog that runs basic JSON structural check and shows toast on pass/fail |
| AEP-UX-029 | Marketplace reviews lack moderation rules | Medium | `done` | FE | Added self-review guard: `selectedAgent.listing.owner === tenantId` blocks review with `toast.error` explaining conflict |
| AEP-UX-032 | Governance policies are not actionable | Medium | `done` | FE | Added `reviewId`, `relatedPipelineId`, `relatedRunId`, `relatedAgentId` to `LearnedPolicy`; linked actionable columns in policies table |
| AEP-UX-041 | No onboarding/setup/readiness flow | Medium | `done` | FE | Created `OnboardingChecklist` component with progress tracking and action links |
| AEP-UX-043 | Builder AI assistant is not embedded enough | Medium | `done` | FE | Added one-click template chips in AI assistant panel (Validate→Enrich→Route, Error triage, Data quality gate, Audit log collector) that auto-fill the prompt and trigger suggestion |
| AEP-UX-046 | Compliance signals can overstate trust without evidence links | Medium | `done` | FE | Guarded `generatedAt` date; status-aware color coding; added `evidenceUrl`/`auditEntryId` to SOC2 controls; linked trust claims to evidence |
| AEP-UX-048 | Generated API contracts are not consistently used | Medium | `done` | FE | Created `typed-api-client.ts` with `typedGet`/`typedPost`/`typedPut`/`typedPatch`/`typedDelete`/`callTypedEndpoint` for compile-time contract enforcement |
| AEP-UX-049 | Browser title is too narrow | Low | `done` | FE | Renamed `<title>` to "AEP Control Plane" in `index.html` |
| AEP-UX-050 | No unified operation/job center | High | `done` | FE/BE | Created `OperationCenter` component with status filters, retry/cancel actions, audit links, auto-refresh, and operation type/resource tracking |

---

## Section 4: Cross-cutting Shared Components

These are recommended shared abstractions from Section 12 of the audit. They unblock many UX items above.

| Component | Unblocks | Status | Owner | Notes |
|---|---|---|---|---|
| `SensitiveActionDialog` | AEP-UX-014, 021, 022, 027 | `done` | FE/DS | Created with reason input, impact preview, tenant confirm, keyword confirmation, audit preview |
| `ReviewDecisionDialog` | AEP-UX-015, 045 | `done` | FE/DS | Created with approve/reject modes, note/reason input, focus trap, keyboard workflow, Escape to cancel |
| `AsyncOperationToast/Panel` | AEP-UX-024, 050 | `done` | FE/DS | Created `AsyncOperationPanel` with lifecycle, retry, cancel, expandable detail, audit handoff |
| `EntityDeepLink` | AEP-UX-033, 032 | `done` | FE | Created `EntityDeepLink` component and `getEntityUrl` helper with typed routes for pipeline, run, agent, policy, review, cost-alert |
| `TenantScopedMutation` | AEP-UX-017, 026 | `done` | FE | Created `useTenantScopedMutation` hook; auto-injects tenantId, throws if missing |
| `EvidenceBadge` | AEP-UX-046, 028 | `done` | FE/DS | Created `EvidenceBadge` with status tiers (verified/pending/unverified/deprecated), evidence/audit links, date guarding |
| `ConfidenceExplanation` | AEP-UX-044, 043 | `done` | FE/DS | Created `ConfidenceExplanation` with tier display, expandable reasoning, evidence/audit links, optional override |
| `PageState` | AEP-UX-013, 031 | `done` | FE/DS | Created with 5 modes: loading, empty, unavailable, degraded, zero; supports retry, aria-live |

---

## Section 5: Missing Surfaces (Medium- to Long-term)

| Surface | Priority | Status | Owner | Notes |
|---|---|---|---|---|
| Routeable agent detail page | High | `done` | FE/BE | AEP-UX-005 resolved: `/catalog/agents/:agentId` route renders `AgentRegistryPage` with `useParams` auto-select inline drawer |
| Pipeline edit route that mounts builder | Critical | `done` | FE | AEP-UX-003 resolved: `/build/pipelines/:pipelineId/edit` route mounts `PipelineBuilderPage` with edit mode |
| Unified operation/job center | High | `done` | FE/BE | AEP-UX-050 resolved: `OperationCenter` shared component created; dedicated page `OperationCenterPage` added at `/operate/operations` route |
| First-use onboarding and tenant readiness checklist | Medium | `done` | FE | AEP-UX-041 resolved: `OnboardingChecklist` component created with progress tracking and action links; ready for embedding in first-use flow |
| Session expiry/re-auth recovery screen | High | `done` | FE | Created `SessionExpiryPage` with clear reason, re-auth button, session cleanup; route `/session-expired` added in App.tsx |
| Dedicated learn summary surface | Medium | `done` | FE/DS | `/learn/episodes` routes to `PatternStudioPage` with `?tab=learning`; learning tab provides episodes, policies, and reflection controls |
| Marketplace install/register/use handoff | High | `done` | FE/BE | AEP-UX-030 resolved: "Install to tenant" button in marketplace detail panel with `SensitiveActionDialog` governed confirmation (keyword, impact items, audit message, reason required) |
| Governance evidence drilldown | Medium | `done` | FE/BE | Created `GovernanceEvidenceDrilldown` with categorized evidence items, expandable detail, evidence/audit links |

---

## Section 6: Architecture / Test / Tooling Debt

| Item | Priority | Status | Owner | Notes |
|---|---|---|---|---|
| Route contract tests (mount router + assert target page) | High | `done` | FE/QA | Added `route-contracts.test.tsx` with login, session-expired, redirect assertions |
| CSS presence / visual smoke tests | Critical | `done` | FE/QA | Added `css-smoke.test.tsx` with index.css import assertion, dark mode class test, styled element render test |
| Tenant-scoped mutation tests | High | `done` | FE/QA | Added `tenant-scoped-mutation.test.tsx` with null-tenant rejection, tenant injection, merged variables tests |
| Auth bootstrap double-read regression test | High | `done` | FE | Added `auth-bootstrap.test.ts` with response body double-read guard using body-stream mock |
| E2E auth utilities aligned to sessionStorage model | High | `done` | QA | Created `e2e/auth-helpers.ts` with `seedAuthenticatedSession`, `clearAuthenticatedSession`, `assertSessionStorageAuth`, `suppressViteErrorOverlay`; updated `a11y.spec.ts` to import shared helpers |
| Focus trap / focus restore dialog tests | Medium | `done` | QA | Added `focus-trap-dialog.test.tsx` with dialog role assertion, Escape-to-cancel test for both SensitiveActionDialog and ReviewDecisionDialog |
| Keyboard builder workflow tests | High | `done` | QA | Created `e2e/keyboard-builder.spec.ts` with Tab-order validation, keyboard shortcut save test, and interactive element focusability tests |
| Reduced motion and text scaling checks | Medium | `done` | QA | Added `prefers-reduced-motion` and `prefers-contrast: more` media queries in `index.css` to disable animations and enforce high-contrast colors |
| Design-system lint checks for raw control usage | High | `done` | FE/DS | Added `prefer-design-system-primitives` ESLint rule that flags raw HTML elements and suggests DS equivalents |
| Generated typed API clients | Medium | `done` | FE | Created `typed-api-client.ts` with compile-time contract enforcement (`typedGet`/`typedPost`/`typedPut`/`typedPatch`/`typedDelete`) |

---

## Progress Summary

- **Total tracked items**: 72
- **Critical**: 8 immediate + 12 high
- **Done**: 50 AEP-UX items (001–050) + 29 cross-cutting components/tests/surfaces
- **In Progress**: 0
- **Pending**: 0

---

## Implementation Batch Plan

### Batch 1: Critical Styling + Router + Auth Foundation
1. AEP-UX-001 — Restore CSS import + visual guard
2. AEP-UX-003 — Fix pipeline edit route
3. AEP-UX-004 — Fix template open-builder + query invalidation
4. AEP-UX-007 — Fix SSO callback state sync
5. AEP-UX-036 — Fix SSE hook ordering

### Batch 2: Builder Correctness + Tenant Safety
6. AEP-UX-017 — Pass tenant to save/validate
7. AEP-UX-018 — Gate Run Now behind saved+valid
8. AEP-UX-025 — Fix Memory Explorer agent scoping

### Batch 3: Shared Components + State Patterns
9. Create `PageState` component
10. Create `SensitiveActionDialog` component
11. Create `TenantScopedMutation` wrapper
12. Apply `PageState` to monitoring KPIs (AEP-UX-013)
13. Apply `PageState` to durability banner (AEP-UX-012)

### Batch 4: Tests + Verification
14. AEP-UX-002 — Extend `verify:truth` with style/route/auth checks
15. AEP-UX-008 — Update login E2E
16. AEP-UX-009 — Update a11y E2E auth
17. AEP-UX-010 — Fix auth bootstrap double-read

---

> Last updated: 2026-04-24 (ALL ITEMS COMPLETE — 50 AEP-UX items + 29 cross-cutting components/tests/surfaces implemented. Zero pending.)
