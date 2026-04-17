# YAPPC Implementation Plan

**Date:** 2026-04-17  
**Status:** Post-Audit Action Plan  
**Goal:** Production-Ready Product by Phased Hardening

---

## 📋 TASKS TRACKER

### Phase 0: Correctness Blockers (CRITICAL - Must Complete First)
| # | Task | Status | Owner | Evidence Required |
|---|------|--------|-------|-------------------|
| 0.1 | Fix route taxonomy - remove `/app/*` legacy | ⬜ TODO | TBD | Route registry shows only `/p/:projectId` |
| 0.2 | Fix onboarding redirect target | ⬜ TODO | TBD | Onboarding redirects to registered route |
| 0.3 | Remove demo data fallbacks from production | ⬜ TODO | TBD | `DEMO_FIXTURES_ENABLED` only in dev |
| 0.4 | Implement missing auth endpoints OR remove UI | ⬜ TODO | TBD | Register/forgot-password work or gone |
| 0.5 | Hide dead actions (export/assist/preview) | ⬜ TODO | TBD | Buttons hidden or wired to real APIs |
| 0.6 | Fix route smoke test | ⬜ TODO | TBD | CI passes route smoke test |
| 0.7 | Verify project creation uses real API | ⬜ TODO | TBD | Network tab shows POST `/api/projects` |
| 0.8 | Remove duplicate agent trees | ⬜ TODO | TBD | Only one agents directory exists |
| 0.9 | Delete deprecated web app | ⬜ TODO | TBD | `frontend/apps/web` removed |
| 0.10 | Fix `@ts-nocheck` suppressions | ⬜ TODO | TBD | Zero @ts-nocheck in production paths |

**Phase 0 Exit Criteria:** All routes work, no silent fallbacks, no fake UI, tests pass

### Phase 1: Proof & Workflow Completion
| # | Task | Status | Owner | Evidence Required |
|---|------|--------|-------|-------------------|
| 1.1 | Add E2E test: project CRUD | ⬜ TODO | TBD | Playwright test passes |
| 1.2 | Enable auth E2E tests | ⬜ TODO | TBD | Login/logout E2E passes |
| 1.3 | Persist lifecycle state to Data-Cloud | ⬜ TODO | TBD | Phase gates survive reload |
| 1.4 | Declare OpenAPI canonical | ⬜ TODO | TBD | One OpenAPI spec, generated clients |
| 1.5 | Fix remaining @ts-nocheck | ⬜ TODO | TBD | All production files typed |
| 1.6 | Add canvas save/load E2E | ⬜ TODO | TBD | Save, reload, verify state |
| 1.7 | Add workspace API failure tests | ⬜ TODO | TBD | 503/non-JSON handling tested |

**Phase 1 Exit Criteria:** E2E coverage for critical paths, lifecycle persisted, contracts aligned

### Phase 2: UX Simplification & Operations
| # | Task | Status | Owner | Evidence Required |
|---|------|--------|-------|-------------------|
| 2.1 | Simplify dashboard | ⬜ TODO | TBD | Only resume/create/review visible |
| 2.2 | Move AI inference server-side | ⬜ TODO | TBD | Suggestions come from API |
| 2.3 | Consolidate UI layers | ⬜ TODO | TBD | Only @ghatana/design-system used |
| 2.4 | Add observability (tracing) | ⬜ TODO | TBD | OTel + correlation IDs working |
| 2.5 | Add bundle budget enforcement | ⬜ TODO | TBD | CI fails on bundle size |
| 2.6 | Add skeleton loading states | ⬜ TODO | TBD | No jarring loading jumps |

**Phase 2 Exit Criteria:** Low cognitive load, implicit AI, fast perceived performance

### Phase 3: Differentiation & Moat
| # | Task | Status | Owner | Evidence Required |
|---|------|--------|-------|-------------------|
| 3.1 | Knowledge graph accuracy benchmarks | ⬜ TODO | TBD | Accuracy test suite passes |
| 3.2 | Verify collaboration real-time | ⬜ TODO | TBD | Two-browser E2E passes |
| 3.3 | Golden journey E2E per template | ⬜ TODO | TBD | Full lifecycle tested |
| 3.4 | Multi-agent orchestration proof | ⬜ TODO | TBD | Agent coordination tested |
| 3.5 | Security audit (penetration test) | ⬜ TODO | TBD | No critical vulnerabilities |

**Phase 3 Exit Criteria:** Defensible moat, proven differentiation, security hardened

---

## 🔴 PHASE 0: CORRECTNESS BLOCKERS (Weeks 1-3)

### Task 0.1: Fix Route Taxonomy

**Problem:** Route graph is split between `/p/:projectId` (active) and `/app/*` (legacy). UI navigates to wrong routes.

**Files to Modify:**
- `@/routes.ts` - Verify active registry
- `@/routes/onboarding.tsx` - Fix redirect target
- `@/routes/dashboard.tsx` - Remove dead links
- `@/routes/app/projects.tsx:250` - Fix project navigation

**Implementation Steps:**
1. Audit all navigation calls in the codebase
2. Replace `/app/p/${id}` with `/p/${id}/canvas`
3. Replace `/app` with `/` or `/dashboard`
4. Remove any remaining `/app/*` route definitions
5. Update Playwright tests to use canonical routes

**Verification:**
```bash
# Grep for legacy routes
grep -r "\/app\/" src/routes/ src/components/ --include="*.tsx" | grep -v "\.test\."
# Should return zero results after fix
```

**Acceptance Criteria:**
- [ ] No references to `/app/*` in active navigation code
- [ ] Route smoke test passes
- [ ] Manual click-through of all primary flows works

---

### Task 0.2: Fix Onboarding Redirect

**Problem:** Onboarding redirects to `/app` which is not a registered route.

**Current State:**
```tsx
// routes/onboarding.tsx (line ~45)
navigate('/app'); // Non-existent route
```

**Fix:**
```tsx
navigate('/dashboard'); // Or appropriate registered route
```

**Acceptance Criteria:**
- [ ] New user completes onboarding and lands on working page
- [ ] No 404 after onboarding

---

### Task 0.3: Remove Demo Data Fallbacks from Production

**Problem:** `useWorkspaceData.ts` silently falls back to fake data on API failure, masking outages.

**Location:** `frontend/web/src/hooks/useWorkspaceData.ts:52-55, 108-126, 162-180`

**Current Code:**
```typescript
const DEMO_FIXTURES_ENABLED =
  import.meta.env.DEV &&
  import.meta.env.VITE_ENABLE_DEMO_WORKSPACE_FIXTURES === 'true';

// In catch blocks:
if (DEMO_FIXTURES_ENABLED) {
  return [ /* demo workspace */ ];
}
```

**Issues:**
1. `DEMO_FIXTURES_ENABLED` can be accidentally true in production-like environments
2. Silent fallback prevents users from knowing backend is down
3. Fake data makes testing/debugging confusing

**Fix:**
```typescript
// Option 1: Remove entirely
} catch (error) {
  throw error; // Always propagate real errors
}

// Option 2: Explicit dev-only with loud logging
if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_DEMO_FIXTURES === 'true') {
  console.warn('⚠️ DEMO MODE: Using fake data due to API failure:', error);
  return demoData;
}
throw error;
```

**Acceptance Criteria:**
- [ ] API failures surface explicit error UI
- [ ] Demo data only available with explicit env flag in dev mode
- [ ] No silent fallbacks in production builds

---

### Task 0.4: Implement Missing Auth Endpoints OR Remove UI

**Problem:** UI offers register/forgot-password but API only has login/refresh/logout/me.

**Files:**
- UI: `frontend/web/src/routes/register.tsx`, `forgot-password.tsx`
- API: `frontend/apps/api/src/routes/auth.ts`

**Decision Required:** Do we need self-service registration?

**Option A - Implement:**
1. Add `POST /api/auth/register` to `auth.ts`
2. Implement email verification flow
3. Add `POST /api/auth/forgot-password` with email sending
4. Integrate with email service (SendGrid/AWS SES)

**Option B - Remove (Recommended for B2B):**
1. Remove register/forgot-password routes from router
2. Remove links from login page
3. Add "Contact admin for access" message
4. Implement admin-only user creation

**Acceptance Criteria:**
- [ ] No UI elements for non-functional endpoints
- [ ] If implemented, E2E tests prove full flow
- [ ] If removed, clear path for user acquisition

---

### Task 0.5: Hide Dead Actions

**Problem:** Export, AI assist, preview buttons visible but no verified backend.

**Files to Audit:**
- `routes/app/project/_shell.tsx` - Toolbar actions
- `routes/app/project/preview.tsx` - Preview functionality
- `routes/app/project/deploy.tsx` - Deploy/export actions
- `components/canvas/UnifiedToolbar.tsx` - Canvas toolbar

**Fix Pattern:**
```tsx
// Before
<button onClick={handleExport}>Export</button>

// After (feature flag or remove)
{features.exportEnabled && (
  <button onClick={handleExport}>Export</button>
)}
// OR just remove entirely
```

**Actions to Hide Until Real:**
- [ ] Export project
- [ ] AI assist (unless calling real endpoint)
- [ ] Preview (unless wired to real preview service)
- [ ] Nested lifecycle drilldowns
- [ ] Workflow/task links

**Acceptance Criteria:**
- [ ] No buttons that don't do anything
- [ ] No links to unimplemented routes
- [ ] Feature flags for work-in-progress features

---

### Task 0.6: Fix Route Smoke Test

**Problem:** Route smoke test currently fails.

**Location:** `frontend/web/src/routes/__tests__/smoke.test.tsx` (or similar)

**Fix Steps:**
1. Run test to identify failure
2. Fix broken imports (e.g., nonexistent `createApp`)
3. Update for current route taxonomy
4. Add coverage for all active routes

**Acceptance Criteria:**
- [ ] `pnpm test:routes` passes
- [ ] CI pipeline includes route smoke test
- [ ] All active routes tested

---

### Task 0.7: Verify Project Creation Uses Real API

**Problem (Historical):** CreateProjectDialog fabricated local objects without API call.

**Files:**
- `frontend/web/src/components/workspace/CreateProjectDialog.tsx:161`
- `frontend/web/src/hooks/useWorkspaceData.ts:574-629`

**Verification Steps:**
1. Open browser DevTools Network tab
2. Click "Create Project"
3. Submit form
4. Verify `POST /api/projects` request fired
5. Verify response contains real project ID (not `temp-`)
6. Verify redirect to `/p/${realId}/canvas`
7. Refresh page - project should still exist

**Acceptance Criteria:**
- [ ] Network shows real API call
- [ ] Project persists after reload
- [ ] Audit event emitted (if implemented)

---

### Task 0.8: Remove Duplicate Agent Trees

**Problem:** Both `core/agents/` and `core/yappc-agents/` exist - confusing, maintenance burden.

**Directories:**
- `/products/yappc/core/agents/` (602 items) - Original
- `/products/yappc/core/yappc-agents/` - Duplicate

**Analysis Required:**
1. Compare file counts and recent activity
2. Identify which has more complete implementations
3. Check which is imported by active code

**Fix:**
1. Migrate any unique code from duplicate to canonical
2. Update all imports to point to canonical location
3. Delete duplicate directory
4. Update build configuration

**Acceptance Criteria:**
- [ ] Only one agents directory exists
- [ ] Build passes
- [ ] All tests pass

---

### Task 0.9: Delete Deprecated Web App

**Problem:** `frontend/apps/web` has only DEPRECATED.md but still in repo.

**Location:** `/products/yappc/frontend/apps/web/`

**Fix:**
```bash
cd /products/yappc/frontend/apps/
rm -rf web/
# Update any references in docs/scripts
```

**Acceptance Criteria:**
- [ ] Directory removed
- [ ] No broken references
- [ ] No impact on active web app

---

### Task 0.10: Fix @ts-nocheck Suppressions

**Problem:** 30+ files have `@ts-nocheck` - violates strict TypeScript mandate.

**Files to Fix (High Priority):**
- Active route files
- Canvas components in production path
- Auth/session management

**Fix Pattern:**
```typescript
// Remove this:
// @ts-nocheck

// Fix the actual errors
// Add specific suppressions only if absolutely necessary:
// @ts-expect-error - reason for suppression
```

**Acceptance Criteria:**
- [ ] Zero `@ts-nocheck` in `src/routes/`, `src/components/canvas/`
- [ ] All production code has strict typing
- [ ] `tsc --noEmit` passes with strict mode

---

## 🟡 PHASE 1: PROOF & WORKFLOW COMPLETION (Weeks 3-6)

### Task 1.1: Add E2E Test - Project CRUD

**Test Flow:**
1. Login
2. Create project (verify API call)
3. Verify project appears in list
4. Open project (verify canvas loads)
5. Update project (if implemented)
6. Delete project (if implemented)
7. Verify project removed

**File:** `frontend/e2e/project-crud.spec.ts`

**Acceptance Criteria:**
- [ ] Test runs against real backend
- [ ] No mocks
- [ ] Passes in CI

---

### Task 1.2: Enable Auth E2E Tests

**Current State:** Auth E2E tests are skipped (`.skip`).

**Location:** `frontend/e2e/auth-flow.spec.ts` (or similar)

**Fix:**
1. Remove `.skip` from auth tests
2. Ensure test database seeded with test user
3. Verify login flow works end-to-end
4. Add logout flow test
5. Add token refresh test

**Acceptance Criteria:**
- [ ] Login E2E passes
- [ ] Logout E2E passes
- [ ] Token refresh tested

---

### Task 1.3: Persist Lifecycle State to Data-Cloud

**Problem:** Phase gates and artifacts are in-memory only.

**Files:**
- `frontend/web/src/services/canvas/lifecycle/PhaseGateService.ts`
- `frontend/web/src/services/canvas/lifecycle/LifecycleArtifactService.ts`

**Implementation:**
1. Define lifecycle state schema
2. Create Data-Cloud repository
3. Implement save on phase transition
4. Implement load on project open
5. Add audit trail

**Acceptance Criteria:**
- [ ] Phase state survives reload
- [ ] Audit log shows transitions
- [ ] Concurrent modifications handled safely

---

### Task 1.4: Declare OpenAPI Canonical

**Problem:** React, Node BFF, Java services, OpenAPI, GraphQL are out of sync.

**Solution:**
1. Declare `products/yappc/docs/api/openapi.yaml` as canonical
2. Remove GraphQL schema (or declare secondary)
3. Generate TypeScript types from OpenAPI
4. Generate API client from OpenAPI
5. Implement contract tests

**Files:**
- `products/yappc/docs/api/openapi.yaml`
- `frontend/web/src/clients/generated/` (generated)

**Acceptance Criteria:**
- [ ] Single source of truth for contracts
- [ ] Frontend uses generated types
- [ ] Contract tests verify backend compliance

---

### Task 1.5: Fix Remaining @ts-nocheck

**Scope:** Remaining 20+ files outside critical paths.

**Priority Order:**
1. Shared libraries (`libs/`)
2. Test utilities
3. Legacy components not in active path

**Acceptance Criteria:**
- [ ] Zero `@ts-nocheck` in entire codebase
- [ ] Strict TypeScript enforced in CI

---

### Task 1.6: Add Canvas Save/Load E2E

**Test Flow:**
1. Open project canvas
2. Add nodes/elements
3. Save (Ctrl+S or auto-save)
4. Reload page
5. Verify canvas state restored

**Acceptance Criteria:**
- [ ] Canvas state persists across reloads
- [ ] No data loss
- [ ] Concurrent edit handling (if applicable)

---

### Task 1.7: Add Workspace API Failure Tests

**Test Scenarios:**
1. Backend returns 503
2. Backend returns non-JSON
3. Network timeout
4. CORS failure

**Acceptance Criteria:**
- [ ] Explicit error messages shown
- [ ] No silent fallbacks
- [ ] Retry logic works

---

## 🟢 PHASE 2: UX SIMPLIFICATION & OPERATIONS (Weeks 6-9)

### Task 2.1: Simplify Dashboard

**Current:** Too many concepts (projects, workflows, tasks, workspaces, lifecycle states)

**Target:** Three actions only
1. Resume Project (continue recent)
2. Create Project (new)
3. Review Blockers (issues)

**Files:**
- `frontend/web/src/routes/dashboard.tsx`

**Acceptance Criteria:**
- [ ] Dashboard shows only 3 primary actions
- [ ] Recent projects visible
- [ ] No dead links

---

### Task 2.2: Move AI Inference Server-Side

**Current:** Heuristic client-side suggestions (banners)

**Target:** Server-side inference with editable defaults

**Files:**
- `frontend/web/src/components/workspace/CreateProjectDialog.tsx` (AI suggestion section)
- `frontend/apps/api/src/routes/projects.ts` (add suggestion endpoint)

**Acceptance Criteria:**
- [ ] Suggestions come from API
- [ ] User can edit before accepting
- [ ] Fallback if AI service unavailable

---

### Task 2.3: Consolidate UI Layers

**Problem:** Both `@ghatana/design-system` and `@yappc/ui` active.

**Decision:** Consolidate to `@ghatana/design-system`

**Migration Steps:**
1. Audit all `@yappc/ui` imports
2. Map to `@ghatana/design-system` equivalents
3. Create migration guide
4. Update all imports
5. Deprecate `@yappc/ui`
6. Remove after migration complete

**Acceptance Criteria:**
- [ ] Zero imports of `@yappc/ui` in active code
- [ ] Consistent component usage
- [ ] No visual regression

---

### Task 2.4: Add Observability (OTel Tracing)

**Current:** Basic metrics only

**Target:** Distributed tracing with correlation IDs

**Implementation:**
1. Add OpenTelemetry to Java backend
2. Add OTel to Node BFF
3. Propagate correlation IDs
4. Create Grafana dashboards
5. Add alerting rules

**Acceptance Criteria:**
- [ ] End-to-end traces visible
- [ ] Correlation IDs in all logs
- [ ] Alerts for error rates, latency

---

### Task 2.5: Add Bundle Budget Enforcement

**Implementation:**
1. Set bundle size budget (e.g., 500KB initial)
2. Add CI check
3. Fail build on budget exceeded
4. Report bundle analysis

**File:** `.github/workflows/ci.yml` or `frontend/web/.size-limit.json`

**Acceptance Criteria:**
- [ ] CI fails if bundle exceeds budget
- [ ] Monthly budget review

---

### Task 2.6: Add Skeleton Loading States

**Current:** Flash of loading, then content

**Target:** Skeleton placeholders while loading

**Components:**
- Dashboard skeleton (already exists: `DashboardSkeleton.tsx`)
- Project list skeleton
- Canvas skeleton
- Settings skeleton

**Acceptance Criteria:**
- [ ] No jarring loading flashes
- [ ] Skeleton matches final layout
- [ ] Reduced perceived load time

---

## 🔵 PHASE 3: DIFFERENTIATION & MOAT (Weeks 9-12+)

### Task 3.1: Knowledge Graph Accuracy Benchmarks

**Problem:** No proof knowledge graph is accurate.

**Implementation:**
1. Create benchmark dataset
2. Implement accuracy metrics
3. Run regular benchmarks
4. Track accuracy over time

**Acceptance Criteria:**
- [ ] Accuracy > 80% on benchmark
- [ ] Metrics dashboard
- [ ] Regression alerts

---

### Task 3.2: Verify Collaboration Real-Time

**Claim:** "Redis-backed real-time collaboration"

**Verification:**
1. Two-browser test
2. User A adds node
3. User B sees node within 1 second
4. Concurrent edit conflict resolution

**Acceptance Criteria:**
- [ ] Two-user E2E passes
- [ ] Latency < 1s
- [ ] No data loss on conflict

---

### Task 3.3: Golden Journey E2E Per Template

**Test:** Full lifecycle for each supported template

**Templates:**
- React web app
- Node.js API
- Java service
- Python microservice
- Mobile app

**Flow:**
1. Capture intent
2. Derive shape
3. Generate scaffold
4. Verify build succeeds
5. Verify tests pass

**Acceptance Criteria:**
- [ ] One golden journey per major template
- [ ] Generated code builds and tests pass

---

### Task 3.4: Multi-Agent Orchestration Proof

**Problem:** 7 agent types exist but coordination not proven.

**Test:**
1. Trigger multi-agent workflow
2. Verify agents coordinate
3. Verify retries on failure
4. Verify HITL integration

**Acceptance Criteria:**
- [ ] Multi-agent E2E passes
- [ ] Failure recovery tested
- [ ] Human-in-the-loop works

---

### Task 3.5: Security Audit (Penetration Test)

**Scope:**
- Authentication bypass
- Authorization checks
- SQL injection
- XSS
- CSRF
- Tenant isolation

**Acceptance Criteria:**
- [ ] No critical vulnerabilities
- [ ] Security report published internally
- [ ] Remediation plan for any issues

---

## 📊 SUCCESS METRICS

### Technical
- [ ] 99.9% uptime (measured)
- [ ] < 500ms p95 API response
- [ ] 80% test coverage
- [ ] Zero P0 bugs open
- [ ] < 100ms time to interactive

### UX
- [ ] 80% onboarding completion
- [ ] < 3 minutes to first project
- [ ] 4.5/5 user satisfaction
- [ ] 40% 7-day retention

### Business
- [ ] First paying customer
- [ ] < 5% churn (monthly)
- [ ] 5% trial-to-paid conversion

---

## 🔄 REVIEW CADENCE

| Review | Frequency | Attendees | Output |
|--------|-----------|-----------|--------|
| Daily standup | Daily | Team | Blocker identification |
| Phase review | End of each phase | Team + stakeholders | Go/no-go decision |
| Demo | Weekly | Team + stakeholders | Working software |
| Retrospective | End of each phase | Team | Process improvements |

---

## 📝 NOTES

### Definition of Done (per task)
1. Code implemented and reviewed
2. Tests pass (unit + integration + E2E where applicable)
3. Documentation updated
4. Demo recorded (for features)
5. Acceptance criteria verified
6. Merged to main

### Risk Mitigation
- **Scope creep:** Strict adherence to phases; new requests go to backlog
- **Technical debt:** 20% of each sprint dedicated to cleanup
- **Resource constraints:** Priority is Phase 0 (correctness)
- **Integration failures:** Daily CI checks; immediate rollback on failure

### Communication Plan
- **Slack:** Daily updates in #yappc-dev
- **Email:** Weekly summary to stakeholders
- **Docs:** This plan lives in repo; updates via PR

---

**Last Updated:** 2026-04-17  
**Next Review:** End of Phase 0
