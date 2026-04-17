# YAPPC Product Audit - Detailed Implementation Plan

**Date:** 2026-04-17  
**Product:** YAPPC (Yet Another Platform Product Creator)  
**Status:** Late Prototype / Early Alpha - NOT Production Ready  
**Audit Confidence:** High (based on evidence from code, tests, docs, prior audits)

---

## Executive Verdict

| Dimension | Assessment |
|-----------|------------|
| **Production Readiness** | ❌ **Critically Not Ready** |
| **Feature Completeness** | ⚠️ **Misleadingly Complete** |
| **Correctness Confidence** | ❌ **Low** |
| **Hardening** | ❌ **Weak** |
| **UI/UX Quality** | ❌ **Weak** (4/10) |
| **Problem-Solution Fit** | ✅ **Strong** (vision is clear) |
| **Competitive Position** | ⚠️ **Moderate** (strong thesis, weak execution) |
| **Innovation/Differentiation Potential** | ✅ **Strong** (8-phase lifecycle + multi-agent) |

### Top 15 Critical Findings

1. **Broken Route Taxonomy**: `/app/*` routes referenced but not registered; active router uses `/p/:projectId` but UI navigates to `/app/p/:id`
2. **Onboarding Redirects to Non-Existent Route**: `/app` is not in the route registry
3. **Client-Only Project Creation**: `CreateProjectDialog` fabricates local objects, never calls API
4. **Demo Data Fallbacks Mask Failures**: `useWorkspaceData` silently falls back to fake data on API failure
5. **In-Memory Lifecycle State**: Phase gates and artifacts are not persisted or collaborative
6. **Missing Auth Endpoints**: UI offers register/forgot-password but API only exposes login/refresh/logout/me
7. **Dead Actions Visible**: Export, AI assist, preview, nested lifecycle drilldowns have no verified backend
8. **Contract Authority Unclear**: React, Node BFF, Java services, OpenAPI, and GraphQL are out of sync
9. **Tests Are Stale**: Route smoke test fails, auth E2E is skipped, integration test imports nonexistent `createApp`
10. **TODO/FIXME in Critical Paths**: 279 TODO/FIXME markers across 168 files
11. **High Cognitive Load**: Dashboard exposes too many concepts before establishing primary job
12. **AI is Visible Not Implicit**: Heuristic suggestion banners instead of quiet automation
13. **Placeholder AI/ML**: Most "AI" is decorative rather than reducing user work
14. **Duplicate UI Layers**: Both `@ghatana/design-system` and `@yappc/ui` active with overlapping responsibilities
15. **Configuration Cache Issues**: Custom validation tasks incompatible with Gradle configuration cache

## Implementation Progress Update

**Last Updated:** 2026-04-17

### Completed In Workspace

- [x] **P0.1** Onboarding redirect now targets a registered route.
- [x] **P0.2** Project creation dialog now uses the real project API mutation.
- [x] **P0.3** Silent demo fallbacks are removed from the main workspace/project fetch path unless an explicit dev fixture flag is enabled.
- [x] **P0.4** Project navigation is aligned to `/p/:projectId` and the canvas route.
- [x] **P0.5** Lifecycle artifacts and phase state now persist across reloads in the current web client instead of being purely in-memory.
- [x] **P0.6** Register and forgot-password are removed from the active route table, and unsupported auth actions fail explicitly.
- [x] **P0.7** Dashboard dead links are removed in favor of truthful actions.
- [x] **P0.8** Route smoke coverage is updated for the active route taxonomy.
- [x] **P0.9** API entrypoint now exports `createApp` and supports `/api/v1` compatibility registration.
- [x] **P0.10** Project creation on the active API surface now emits an explicit audit event with workspace and project metadata, and the app factory registers the existing HTTP audit middleware.
- [x] **P2.1** Dashboard is simplified around resume, create, and blocker review actions.
- [x] **P2.2** The active workspaces route now auto-creates a starter workspace with a default project on first empty-state entry, and its create-workspace action uses the live dialog instead of a dead route.
- [x] **P2.3** Project creation now uses existing server-backed name suggestion hooks instead of client-only fabrication.
- [x] **P1.4** YAPPC now declares `products/yappc/docs/api/openapi.yaml` as the frontend codegen source, adds a real `codegen:openapi` entrypoint plus a tracked generated output location, expands that schema to cover the live auth, workspace, and project CRUD/suggestion surface, and the active auth session/bootstrap, auth service, and `useWorkspaceData` clients now adopt generated OpenAPI contracts for their live payloads.
- [x] **P2.4** The YAPPC frontend verify path now builds the active web app and runs the enforced bundle-budget check script as part of the shared verification flow.
- [x] **P3.1** Lifecycle route now surfaces a visible Learn/Evolve insight area using existing recommendation and evidence hooks.
- [x] **P3.2** Project setup suggestions now infer the initial project type on the server from the description and workspace context instead of relying on client-only heuristics.
- [x] **P3.3** Create project flow warns on duplicate names within the active workspace.
- [x] **P3.6** Create project suggestions now surface related projects from other workspaces when the server can find relevant matches for the inferred project type.
- [x] **P1.8** Added explicit workspace API failure handling tests for 503 and non-JSON responses in the active web data hook.
- [x] **P1.1** Real-backend release-gate coverage now proves project creation survives reload plus a full logout/login roundtrip on the active `/projects` flow.
- [x] **P1.2** Real-backend release-gate coverage now exercises login and logout from the active shell instead of relying on mock-backed auth-only browser tests.
- [x] **P1.3** Legacy compatibility saves in `frontend/web/src/services/persistence.ts` now delegate to the active `services/canvas/CanvasPersistence` implementation, and the remaining mode/level helper in `frontend/web/src/utils/canvasPersistence.ts` now stores through that same authority so the mounted canvas scene, code-editor canvas, and older hooks no longer persist through conflicting save/load paths.
- [x] **P1.5** The mounted web root no longer layers the stale `@yappc/ui` shortcut provider or toast provider onto the active product shell, reducing the live overlap between `@ghatana/design-system` and `@yappc/ui` on the current route spine.
- [x] **P1.6** The top-level YAPPC deployment guidance now declares the active Prisma workflow in `frontend/apps/api` as the migration authority instead of stale Flyway commands.
- [x] **P1.7** Real-backend release-gate coverage now verifies the active auth bootstrap refreshes an expired access token through `/api/auth/refresh` and keeps the user on the protected surface.
- [~] **P2.6** Removed unnecessary `@ts-nocheck` suppressions from the active root entry surface, the active `routes/app/**` tree, the active `ProjectLayout`, the remaining active code-editor canvas persistence helper, the active canvas persistence hook and route-local test under `routes/app/project/canvas`, a validated mounted canvas route batch (`CanvasRoute`, `CanvasScene`, `CanvasManager`, `CanvasPanels`, `CanvasToolbar`, `CanvasDialogs`, `CanvasEmptyState`, `ImprovedEmptyState`, `CanvasLoadingState`, `CanvasAccessibilityPanel`, `CanvasPerformancePanel`, `CanvasOutlinePanel`, `CanvasStatusBar`, `CanvasNodeContextMenu`, `DraggableBox`), the adjacent canvas hooks/utilities (`useCanvasLayout`, `useCanvasExport`, `useCanvasAccessibility`, `useCanvasPerformance`, `useCanvasTemplates`, `useCanvasKeyboardShortcuts`, `useCanvasScene/utils`), direct live-path hooks/services (`useUnifiedCanvas`, `useCollaboration`, `CanvasLifecycle`, `CanvasPerformanceMonitor`, `CanvasPersistence`, `CanvasExporter`, `HierarchyManager`, `ZoomManager`), and a first directly imported unified canvas component/panel layer (`CanvasErrorBoundary`, `UnifiedLeftRail`, `UnifiedRightPanel`, `UnifiedToolbar`, `ShapeStylePicker`, `AssetsPanel`, `ComponentsPanel`, `LayersPanel`, `AIPanel`, `DataPanel`, `HistoryPanel`, `FavoritesPanel`); broader canvas/components/services and legacy/test suppressions still remain outside the cleaned live path.

### Remaining Partial Items

- [~] **P2.6** Continue removing unnecessary `@ts-nocheck` suppressions from active-spine files after each slice is typed and validated.

---

## 2. Vision and Scope Assessment

### Inferred Vision

YAPPC aims to be an **AI-native operating system for software product creation**, implementing an 8-phase lifecycle:

```
Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve
```

The **Run** phase drives an underlying SDLC lifecycle (Discover → Define → Design → Plan → Build → Test → Release → Deploy) managed by the AEP pipeline.

### Target Users/Customers

- **Founder/Product Lead**: Captures intent, validates concepts
- **Staff Engineer/Designer**: Shapes architecture, reviews code
- **Workspace Admin/Reviewer**: Manages teams, approves deployments
- **Governance Approver**: Ensures compliance, security

### Key Product Claims

1. End-to-end orchestration from idea to delivery to operation to continuous improvement
2. Multi-agent AI workflows (7 specialized agent types)
3. Visual canvas for product ideation and architecture
4. Intelligent scaffolding for 15+ technology stacks
5. Semantic knowledge graph for context-aware assistance
6. Lifecycle-native governance and approval flows

### Scope Clarity vs Ambiguity

| Clear Areas | Ambiguous Areas |
|-------------|-----------------|
| 8-phase lifecycle model | Where shared platform ends and YAPPC begins |
| Multi-agent specialization | Whether canvas is generic or YAPPC-specific |
| AI integration ambition | How "Learn/Evolve" manifests in UX |
| Scaffolding capabilities | Production deployment readiness |

### Alignment/Misalignment Between Docs and Implementation

| Claim | Implementation Evidence | Alignment |
|-------|--------------------------|-----------|
| "Production Ready" (DEPLOYMENT_GUIDE.md) | Broken routes, client-only project creation, in-memory state | ❌ **Misaligned** |
| "AI-native platform" | Heuristic UI shortcuts, visible suggestion banners, minimal server-side inference | ⚠️ **Partially Aligned** |
| "Complete 8-phase lifecycle" | Phase preview API exists but lifecycle artifacts are in-memory | ⚠️ **Partially Aligned** |
| "Real-time collaboration" | CRDT infrastructure present but core workflows are single-user | ⚠️ **Partially Aligned** |
| "Comprehensive testing" | Route smoke test fails, auth E2E skipped, stale mocks | ❌ **Misaligned** |

---

## 3. Problem and Pain-Point Analysis

### Primary Customer Problems

#### P1: Development Lifecycle Fragmentation
- **Who experiences it**: Product teams, engineering managers
- **Why it matters**: Teams lose context switching between Jira, Figma, GitHub, Confluence, Slack
- **Severity**: High - daily friction
- **Current solutions**: Point tools stitched together with manual processes
- **YAPPC addresses**: Attempts unified workspace but currently fragments across route generations
- **Verdict**: ⚠️ **Relevant but weakly addressed** - vision is right but execution fragments

#### P2: Manual Code Generation and Scaffolding
- **Who experiences it**: Developers, tech leads
- **Why it matters**: 20-40% of development time on boilerplate, setup, repetitive patterns
- **Severity**: High - persistent drag on velocity
- **Current solutions**: Yeoman, cookiecutter, internal templates (all require manual curation)
- **YAPPC addresses**: Real scaffolding engine with AI enhancement exists in `core/scaffold/*`
- **Verdict**: ✅ **Strongly relevant and well-targeted** - backend capability is real

#### P3: Requirements-to-Code Gap
- **Who experiences it**: Product managers, developers
- **Why it matters**: Requirements documents don't translate cleanly to technical specs
- **Severity**: Medium-High - causes rework and misalignment
- **Current solutions**: Manual translation, additional documentation layers
- **YAPPC addresses**: AI requirements service with semantic search and classification in `core/ai`
- **Verdict**: ⚠️ **Relevant but not fully integrated** - capability exists but not wired to primary UX

#### P4: Knowledge Loss During Team Transitions
- **Who experiences it**: Engineering managers, new team members
- **Why it matters**: Context walks out the door when people leave
- **Severity**: Medium - chronic but not acute
- **Current solutions**: Documentation (always stale), tribal knowledge transfer
- **YAPPC addresses**: Knowledge graph engine in `core/knowledge-graph`
- **Verdict**: ⚠️ **Future-relevant but currently unsupported** - infrastructure present but not central to UX

### Problem Classification Summary

| Pain Point | Classification | Root Pain or Symptom? |
|------------|----------------|----------------------|
| Tool fragmentation | Strongly relevant, well-targeted | Root pain |
| Manual scaffolding | Strongly relevant, well-targeted | Root pain |
| Requirements gap | Relevant, weakly addressed | Root pain |
| Knowledge loss | Future-relevant, unsupported | Symptom of fragmentation |
| Governance overhead | Vaguely targeted | Root pain |
| Code quality drift | Relevant, not implemented well | Symptom |

---

## 4. Market and Competitor Analysis

### Major Competitors/Substitutes

| Competitor | Strengths | Weaknesses |
|------------|-----------|------------|
| **GitHub Copilot** | Deep IDE integration, massive training data | No lifecycle view, no collaboration, no governance |
| **Vercel v0** | Fast UI generation, visual iteration | Frontend-only, no backend generation, no lifecycle |
| **Tempo** | Product-to-code generation, visual canvas | Limited scaffolding, no multi-agent, weak governance |
| **Replit** | Complete environment, easy sharing | Limited AI assistance, no enterprise governance |
| **Cursor** | AI-native editor, codebase understanding | IDE-only, no product lifecycle, no collaboration |
| **Linear + Figma + GitHub** | Best-in-class individual tools | Fragmented handoffs, no unified lifecycle |
| **Traditional Consulting/Agencies** | End-to-end delivery, accountability | Expensive, slow, not repeatable |

### Unresolved Market Pain

1. **Integration tax**: Even AI tools require manual stitching
2. **Governance gap**: AI-generated code lacks audit trails and approval flows
3. **Context fragmentation**: Design, code, and deployment exist in separate mental models
4. **Learning curve**: Each tool requires separate mastery

### Market Whitespace Opportunities

1. **AI-native lifecycle orchestration**: No competitor owns the full 8-phase flow
2. **Governance-integrated generation**: Most AI tools ignore enterprise compliance needs
3. **Visual-to-executable continuity**: Gap between design tools and working code

### Where YAPPC is Behind

| Area | Gap | Evidence |
|------|-----|----------|
| End-to-end correctness | Primary flows broken | Route mismatches, client-only creation |
| UX simplicity | Too many concepts exposed | Dashboard complexity, broken navigation |
| Production hardening | Not production-grade | In-memory state, demo fallbacks, stale tests |
| AI effectiveness | Visible not implicit | Heuristic banners vs. server-side inference |

### Where YAPPC Can Credibly Lead

| Area | Differentiator | Moat Potential |
|------|----------------|----------------|
| 8-phase lifecycle | No competitor has equivalent depth | Strong if execution catches up to vision |
| Multi-agent specialization | Code/architecture/testing/delivery agents | Moderate - can be replicated |
| Scaffolding depth | 15+ framework support, real implementation | Strong - significant backend investment |
| Enterprise governance | Built-in approval flows and audit | Strong - compliance is sticky |

---

## 5. Solution Assessment

### Current Solution Strengths

1. **Backend Architecture**: 18 well-organized core modules with clear dependency layers
2. **Scaffolding Engine**: Real implementation with template system, pack management, multi-language generators
3. **Agent Framework**: Multi-agent orchestration with specialization (code, architecture, testing, delivery)
4. **Knowledge Graph**: Semantic understanding infrastructure present
5. **Lifecycle Model**: 8-phase framework is genuinely differentiated

### Weak Solution Choices

1. **Frontend/UI Layering**: Overlapping design systems (`@ghatana/design-system` + `@yappc/ui`) create confusion
2. **Route Architecture**: Legacy `/app/*` assumptions mixed with current `/p/:projectId` router
3. **State Management**: Client-side fabrication instead of server truth for project creation
4. **AI Visibility**: Heuristic UI suggestions rather than implicit automation
5. **Contract Surface**: Multiple competing API surfaces (REST, GraphQL, OpenAPI, gRPC) not aligned

### Missing Solution Layers

| Layer | Why Missing | Impact |
|-------|-------------|--------|
| Canonical contract authority | Parallel evolution without consolidation | API drift, frontend/backend misalignment |
| Server-side AI inference | Frontend bias, development convenience | AI is decorative not transformative |
| Durable lifecycle persistence | Placeholder repositories remained | Governance UI is not trustworthy |
| Progressive disclosure | Feature sprawl without prioritization | High cognitive load, user confusion |
| Honest error states | Demo fallbacks for developer convenience | Users can't trust system health |

### Alternative/Better Solution Directions

1. **Contract-First Development**: Generate frontend types from canonical OpenAPI, not parallel hand-written surfaces
2. **Server-Side AI Orchestration**: Move inference from frontend heuristics to backend services with caching
3. **Truthful UI**: Remove all client-side fabrication; every action hits real backend or shows disabled/unavailable state
4. **Simplified Navigation**: Single primary path (auth → workspace → project → canvas → lifecycle → deploy)
5. **Implicit AI**: Server-provided defaults instead of suggestion banners

### Root Problems vs Symptoms

| YAPPC Currently Solves... | Actually a Symptom Of... | Root Problem YAPPC Should Solve |
|---------------------------|--------------------------|--------------------------------|
| Visual canvas for ideation | Requirements scatter across tools | Unified intent capture and validation |
| Code generation | Manual boilerplate creation | Semantic understanding of project needs |
| Phase gates | Unclear readiness for release | Automated readiness detection with AI |
| AI suggestions | Developer uncertainty | Server-inferred defaults with approval |

---

## 6. Product Claim vs Reality Matrix

| Capability | Claimed In | Implementation Evidence | Missing Pieces | Correctness | Hardening | Test Evidence | Verdict |
|------------|------------|--------------------------|----------------|-------------|-----------|---------------|---------|
| **User Registration** | UI screens (`register.tsx`) | UI exists, service code exists | Route wiring in active API surface | UI: ✅ Backend: ⚠️ API: ❌ | ❌ No validation, no rate limiting | Mock tests only, real E2E skipped | ⚠️ **Misleadingly complete** |
| **Project Creation** | Dashboard, CreateProjectDialog | Polished modal UI | API call from UI, persistence | UI: ✅ API: ❌ Backend: ✅ | ❌ Bypasses auth/permissions/audit | No browser-to-DB proof | ❌ **Not end-to-end** |
| **Canvas Editing** | canvas.tsx (833 lines) | Real canvas persistence endpoints | Multiple overlapping persistence systems | Partial | ⚠️ | Insufficient full-path coverage | ⚠️ **Partial** |
| **Lifecycle Governance** | lifecycle.tsx, PhaseGateService | Phase preview API tested | Artifacts and gates in-memory only | Partial | ❌ | Preview tests pass, persistence unproven | ⚠️ **Partial-to-misleading** |
| **AI Assistance** | AIAssistantModal (314 lines) | Heuristic UI suggestions | Server-side inference for setup | UI: ⚠️ Backend: ❌ | ❌ | No proof | ❌ **Thin wrapper** |
| **Export/Preview/Deploy** | _shell.tsx controls | Some readiness preview verified | Export/preview/assist endpoints unverified | Partial | ❌ | Insufficient | ⚠️ **Partial/broken** |
| **Workflows/Tasks** | dashboard.tsx links | No verified endpoints | /api/tasks and workflow routes unproven | ❌ | ❌ | None | ❌ **Not implemented** |
| **Real-time Collaboration** | WebSocket infrastructure | CRDT framework present | Not central to primary UX | ⚠️ | ⚠️ | Weak | ⚠️ **Infrastructure without UX** |
| **Multi-tenant Isolation** | Critical journeys documented | AuthFilterChainE2ETest exists | Tenant enforcement in all paths | ✅ | ⚠️ | Contract tests exist | ✅ **Credible** |
| **Observability** | Metrics, health endpoints | YappcMetricsRegistryTest exists | Full integration in active paths | ✅ | ⚠️ | CI diagnostics step | ✅ **Credible** |

---

## 7. Gap Analysis

### Vision/Product Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| "Production Ready" claim vs broken reality | Erodes trust, risks deployment disasters | High | P0 |
| Learn/Evolve not visible in UX | Core differentiator is hidden | High | P1 |
| Too many concepts in primary UX | Cognitive overload, user abandonment | High | P1 |

### Frontend Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| Route taxonomy broken | Primary navigation fails | Critical | P0 |
| Demo fallbacks in production | Users can't trust system health | High | P0 |
| Client-only project creation | Core action doesn't persist | Critical | P0 |
| `/app` route not registered | Onboarding is broken | Critical | P0 |
| Overlapping UI libraries (`@ghatana/ds` + `@yappc/ui`) | Confusion, maintenance burden | Medium | P1 |

### Backend Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| In-memory lifecycle repositories | Governance is not trustworthy | High | P0 |
| Missing register/forgot-password routes | Auth flow incomplete | High | P0 |
| Direct AEP/DataCloud imports in capability modules | Violates adapter boundary | Medium | P2 |
| 279 TODO/FIXME in production paths | Technical debt accumulation | Medium | P1 |

### Data/Persistence Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| Migration authority unclear (Prisma vs Flyway) | Release safety risk | High | P1 |
| Lifecycle state not durable | Governance audit trail missing | Critical | P0 |

### Testing/Proof Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| Route smoke test fails | No automated navigation proof | High | P0 |
| Auth E2E skipped | No real auth proof | High | P0 |
| Integration test imports nonexistent `createApp` | Tests don't match code | High | P0 |
| No browser-to-DB creation test | Core flow unverified | Critical | P0 |

### Hardening/Security Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| Project creation bypasses auth/permissions/audit | Security vulnerability | Critical | P0 |
| Demo data fallbacks mask outages | Operational blindness | High | P0 |
| No rate limiting on auth endpoints | Attack vector | Medium | P1 |

### Observability/Operations Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| Operations documentation generic | Hard to operate in production | Medium | P2 |
| Missing explicit operator control plane | Production incident response | Medium | P2 |

### UX Gaps

| Gap | Why It Matters | Impact | Urgency |
|-----|---------------|--------|---------|
| AI is visible not implicit | Doesn't reduce user effort | High | P1 |
| Dashboard tries to do too much | High cognitive load | Medium | P1 |
| Broken navigation continuity | User confusion, abandonment | Critical | P0 |

---

## 8. Hardening Findings

| Location | Issue | Failure/Risk Mode | Severity | Required Fix |
|----------|-------|-------------------|----------|--------------|
| `useWorkspaceData.ts` | Demo data fallback on API failure | Users believe system works when backend is down | **Critical** | Replace with explicit error states; dev-only fixtures behind env guard |
| `CreateProjectDialog.tsx` | Client-only project creation | Data loss on refresh, bypasses governance | **Critical** | Wire to POST `/api/projects`, refresh query state |
| `PhaseGateService.ts` | In-memory lifecycle state | Governance decisions lost on refresh | **Critical** | Persist via API/DB or remove from UX |
| `onboarding.tsx:34` | Redirect to `/app` (non-existent route) | Broken first-run experience | **Critical** | Update redirect to registered route |
| `routes.ts` vs `projects.tsx` | Route mismatch `/p/:id` vs `/app/p/:id` | Primary navigation broken | **Critical** | Align all navigation to active router |
| `auth.ts` | Missing register/forgot-password routes | Auth flow incomplete | **High** | Add routes or remove UI options |
| `lifecycle.tsx` | Nested routes not registered | Lifecycle drilldowns broken | **High** | Register routes or remove links |
| `_shell.tsx` | Deploy/preview/assist without verified backend | False completeness | **High** | Hide until backed by verified routes |
| `dashboard.tsx` | Links to `/projects/new`, `/workflows/*` without verified routes | Dead navigation | **High** | Remove links or implement routes |
| `build.gradle.kts` | Custom validation tasks incompatible with config cache | Build reliability issues | **Medium** | Applied fix with `notCompatibleWithConfigurationCache()` |
| Various (279 locations) | TODO/FIXME in production paths | Technical debt, unknown bugs | **Medium** | Triage and resolve or create tracking issues |

---

## 9. Fake Completeness Findings

| Location | Evidence | Why Unacceptable | Risk | Required Replacement |
|----------|----------|-----------------|------|---------------------|
| `CreateProjectDialog.tsx` | Creates local-only object, never calls API | Users believe project is saved | Data loss, governance bypass | Real API call with loading/error states |
| `useWorkspaceData.ts` | `const DEMO_WORKSPACE = {...}` fallback | Production failure masked | Operational blindness, false confidence | Explicit error UI with retry; dev fixtures behind `NODE_ENV` guard |
| `PhaseGateService.ts` | Uses `new Map()` for artifacts | Governance state ephemeral | No audit trail, compliance failure | Persistent repository implementation |
| `LifecycleArtifactService.ts` | In-memory repository pattern | Decisions don't survive reload | Trust erosion | Database-backed artifact store |
| `AIAssistantModal.tsx` | Heuristic suggestions (not server-inferred) | "AI" is just local logic | User disappointment, false advertising | Server-side inference with confidence scores |
| `dashboard.tsx:workflows` | Links to `/workflows/*` | Navigation to unimplemented feature | Broken UX | Remove links or implement workflows |
| `canvas-demo-helpers.ts` | Generated demo stories with fake data | Demo content in production | Confusion between real and fake | Remove from production builds |
| `onboarding.tsx` | `localStorage.getItem('onboarding_complete')` | Onboarding state per-browser | Inconsistent first-run experience | Server-persisted user preference |

---

## 10. End-to-End Correctness Findings

| Workflow | Expected Behavior | Actual Behavior | Affected Layers/Files | Severity | Required Correction |
|----------|-------------------|-----------------|----------------------|----------|---------------------|
| **First-run onboarding** | New user → onboarding → create workspace → enter product | Redirects to `/app` (404) | `onboarding.tsx`, `routes.ts` | **Critical** | Fix redirect target to `/workspaces` or `/projects` |
| **User registration** | Enter credentials → account created → logged in | UI shows form but API rejects (no route) | `register.tsx`, `auth.ts` | **Critical** | Add register route to API or remove UI |
| **Project creation** | Click "New Project" → fill form → persisted project appears in list | Local-only object created, disappears on refresh | `CreateProjectDialog.tsx`, `projects.ts` | **Critical** | Wire to real API, implement optimistic UI with rollback |
| **Project navigation** | Click project in list → open canvas | Navigation to `/app/p/:id` (wrong route) | `projects.tsx`, `routes.ts` | **Critical** | Update navigation to `/p/:projectId` |
| **Canvas persistence** | Edit canvas → auto-save → survive reload | Multiple competing persistence paths, unclear authority | `CanvasPersistence.ts`, `canvas.ts` | **High** | Single authoritative persistence service |
| **Lifecycle phase transition** | Request phase change → gate validation → approval → transition | In-memory only, no multi-user visibility | `PhaseGateService.ts`, `lifecycle.ts` | **High** | Implement persistent phase store with approval workflow |
| **Deploy readiness** | View deploy page → see readiness check → approve → deploy | Mix of real preview and in-memory state | `deploy.tsx`, `_shell.tsx` | **High** | Remove in-memory governance from deploy path |
| **Auth session refresh** | Token expires → silent refresh → continue | Unknown if implemented end-to-end | `AuthService.ts`, `auth.ts` | **Medium** | Verify and test token refresh flow |
| **Workspace switching** | Select different workspace → projects update | May fall back to demo data silently | `useWorkspaceData.ts`, `workspaces.ts` | **High** | Remove fallback, implement proper error states |

---

## 11. Testing and Proof Gaps

| Capability/Use Case | Expected Proof | Current Proof | Confidence Level | Recommended Tests |
|---------------------|---------------|---------------|------------------|-------------------|
| **User registration** | Browser → API → DB → email → login | Mocked unit tests only | **None** | Playwright: register → verify email → login |
| **Project creation (full)** | Browser → API → DB → list refresh | No end-to-end proof | **None** | Playwright: create project → verify in list → reload → verify persisted |
| **Project navigation** | Click project → correct route → canvas loads | Stale Playwright spec uses old routes | **None** | Update golden-path.spec.ts for current routes |
| **Canvas save/load** | Edit → save → reload → state restored | Partial, competing paths | **Low** | Single authoritative test with cleanup |
| **Lifecycle phase transition** | Request → gate check → approval → transition | Preview tests pass, persistence unproven | **Medium** | Multi-session approval audit test |
| **Auth session management** | Login → wait → refresh → still authenticated | Unknown | **Low** | Token refresh + expiry E2E test |
| **Workspace API failure** | API 503 → error state shown | Demo data fallback | **None** | Forced failure test with mock server |
| **Route smoke test** | All registered routes load without error | Currently failing | **None** | Fix and require in CI |
| **Auth E2E** | Real login with seeded DB | Skipped by default | **None** | Enable in CI with test fixtures |
| **Integration test** | API routes with test DB | Imports nonexistent `createApp` | **None** | Fix imports or regenerate test harness |

---

## 12. UI/UX Findings

### Broken Journeys

| Journey | Problem | Severity |
|---------|---------|----------|
| First-time user | Redirects to non-existent `/app` | **Critical** |
| Create project | Appears to work but doesn't persist | **Critical** |
| Open project | Navigation to wrong route | **Critical** |
| Workspace load | Silent fallback to fake data | **High** |
| Lifecycle drilldown | Nested routes not registered | **High** |

### Missing States

| State | Where Missing | Impact |
|-------|---------------|--------|
| API failure (workspace) | `useWorkspaceData.ts` | Users can't distinguish real from fake |
| Network error (project creation) | `CreateProjectDialog.tsx` | Silent failure |
| Loading (lifecycle gates) | `lifecycle.tsx` | Unclear if phase check is running |
| Empty (no projects) | `projects.tsx` | May be inconsistent |

### Confusing Patterns

| Pattern | Location | Problem |
|---------|----------|---------|
| AI suggestion banners | `CreateProjectDialog.tsx` | Heuristic, not server-inferred |
| Dashboard density | `dashboard.tsx` | Too many concepts (projects, workflows, tasks, lifecycle) |
| Route naming | `routes.ts` vs `projects.tsx` | `/p/:id` vs `/app/p/:id` confusion |
| Dual design systems | `frontend/libs/*` | Unclear when to use `@ghatana/design-system` vs `@yappc/ui` |

### Hidden Complexity

| Complexity | Location | Better Approach |
|------------|----------|-----------------|
| 833-line canvas route | `canvas.tsx` | Decompose into orchestration, workflow, canvas host, AI sidecar |
| `@ts-nocheck` on theme bridge | `app-theme.tsx` | Add proper typing or simplify bridge |
| 279 TODO/FIXME markers | Various | Triage and create proper tracking issues |

### Recommended Simplifications

1. **Single primary path**: auth → auto-create workspace → auto-create starter project → open canvas
2. **Remove until real**: workflows, tasks, nested lifecycle drilldowns
3. **Progressive disclosure**: Advanced options (templates, policy tuning) behind "more" buttons
4. **Implicit AI**: Server-provided defaults instead of suggestion banners

---

## 13. Strategic Recommendations

### What to Remove

| Item | Why | Impact |
|------|-----|--------|
| Legacy `/app/*` route assumptions | Conflict with active router | Fixes navigation |
| Demo data fallbacks in production paths | Erodes trust | Makes failures visible |
| Register/forgot-password UI (until backend ready) | False completeness | Removes dead actions |
| Workflow/task dashboard sections (until implemented) | Cognitive load | Simplifies UX |
| 12 deprecated frontend packages in `compat/` | Maintenance burden | Reduces complexity |
| Historical backend modules (`backend:api`, etc.) | Already consolidated | Docs cleanup |

### What to Simplify

| Item | Current | Simplified |
|------|---------|------------|
| Route taxonomy | Mixed `/app/*` and `/p/:id` | Single `/p/:projectId` |
| UI libraries | `@ghatana/ds` + `@yappc/ui` overlap | `@ghatana/ds` canonical, `@yappc/ui` thin composition |
| Dashboard | Projects + workflows + tasks + lifecycle | Projects + quick actions |
| Project creation | Multi-field form | Name + description, AI suggests type |
| AI visibility | Suggestion banners everywhere | Server defaults, editable results |

### What to Complete

| Item | Current State | Completion Criteria |
|------|---------------|---------------------|
| Project creation end-to-end | Client-only | API call, persistence, optimistic UI |
| Onboarding redirect | Broken | Fix to real route or merge into auth flow |
| Project navigation | Wrong route | Align all navigation to `/p/:projectId` |
| Lifecycle persistence | In-memory | Database-backed with approval workflow |
| Auth endpoints | Missing routes | Add register/forgot-password or remove UI |
| Route smoke test | Failing | Fix and require in CI |
| Tests | Stale mocks | Browser-to-DB E2E on seeded data |

### What to Harden

| Item | Current | Hardened |
|------|---------|----------|
| Workspace fetch | Demo fallback | Explicit error with retry |
| Project creation | No auth check | Full auth/permissions/audit |
| Lifecycle state | In-memory | Persistent with audit trail |
| Contract authority | 5 competing surfaces | Single canonical OpenAPI |
| Configuration cache | Custom tasks fail | Fix or mark incompatible |

### What to Validate with Customers/Market

| Hypothesis | Validation Method |
|------------|-------------------|
| 8-phase lifecycle resonates | User interviews with product leads |
| AI-generated project setup preferred | A/B test server defaults vs manual entry |
| Canvas-based ideation is primary | Analytics on canvas engagement |
| Governance integration is differentiator | Enterprise customer interviews |
| Workflow automation valued | Feature flag + usage analytics |

### What to Differentiate On

| Differentiator | Why It Matters | Investment |
|----------------|----------------|------------|
| 8-phase lifecycle completion | No competitor has equivalent depth | High |
| Server-side AI inference | Truly reduces user effort | High |
| Governance-integrated generation | Enterprise compliance moat | High |
| Scaffolding breadth | 15+ framework coverage | Maintain |
| Multi-agent specialization | Quality through specialization | Maintain |

### What to Deprioritize

| Item | Reason |
|------|--------|
| Workflow automation (full) | Core spine not yet trustworthy |
| Cross-workspace analytics | Advanced feature ahead of basics |
| Mobile app depth | Web product not yet production-ready |
| Advanced policy tuning | Progressive disclosure candidate |
| Real-time collaboration polish | Infrastructure present but core flow broken |

---

## 14. Prioritized Action Plan

### Phase 0: Correctness Blockers + Fake Completeness + Hardening Gaps (4-6 weeks)

**Goal**: Establish one truthful product spine: auth → workspace → create/open project → canvas

| Task | Problem | Fix Direction | Priority | Expected Impact |
|------|---------|---------------|----------|-----------------|
| **P0.1** Fix onboarding redirect | Redirects to `/app` (404) | Change to `/projects` or `/workspaces` | P0 | Unblocks first-run experience |
| **P0.2** Wire project creation to API | Client-only fake creation | Call POST `/api/projects`, optimistic UI | P0 | Core action becomes trustworthy |
| **P0.3** Remove demo data fallbacks | Silent failure masking | Explicit error states, dev fixtures behind env guard | P0 | Users can trust system health |
| **P0.4** Align project navigation | `/app/p/:id` vs `/p/:projectId` | Update all navigation to active router | P0 | Primary navigation works |
| **P0.5** Persist lifecycle state | In-memory governance | Implement persistent phase repository | P0 | Governance becomes trustworthy |
| **P0.6** Add missing auth routes or remove UI | Register/forgot-password unavailable | Add routes or hide UI options | P0 | No dead actions |
| **P0.7** Remove dead dashboard links | `/projects/new`, `/workflows/*` unimplemented | Hide until backed by routes | P0 | No broken navigation |
| **P0.8** Fix route smoke test | Currently failing | Update for current route taxonomy | P0 | CI gate functional |
| **P0.9** Fix integration test imports | Imports nonexistent `createApp` | Update to current app factory | P0 | Tests match code |
| **P0.10** Add project creation auth/audit | Bypasses permissions | Enforce auth, add audit logging | P0 | Security hardening |

### Phase 1: Proof/Tests + Workflow Completion + Architecture Fixes (4-6 weeks)

**Goal**: Trustworthy tests, canonical contract, durable workflows

| Task | Problem | Fix Direction | Priority | Expected Impact |
|------|---------|---------------|----------|-----------------|
| **P1.1** Browser-to-DB E2E tests | No real end-to-end proof | Playwright tests on seeded DB | P1 | Release confidence |
| **P1.2** Enable auth E2E | Currently skipped | Fix and enable in CI | P1 | Auth flow verified |
| **P1.3** Single canvas persistence authority | Multiple competing paths | One service, one cache strategy | P1 | State consistency |
| **P1.4** Canonical contract surface | REST/GraphQL/OpenAPI/Java out of sync | Declare canonical API, generate types | P1 | Contract drift eliminated |
| **P1.5** Simplify UI layer | `@ghatana/ds` + `@yappc/ui` overlap | Consolidate to canonical shared UI | P1 | Reduced complexity |
| **P1.6** Resolve migration authority | Prisma vs Flyway | Pick one per datastore, update docs | P1 | Release safety |
| **P1.7** Add token refresh E2E | Session management unverified | Test expiry + refresh flow | P1 | Session reliability |
| **P1.8** API failure handling tests | No forced failure coverage | Mock server outage tests | P1 | Resilience proof |

### Phase 2: UX Simplification + Operational Readiness + Performance (3-4 weeks)

**Goal**: Simple, fast, operable product

| Task | Problem | Fix Direction | Priority | Expected Impact |
|------|---------|---------------|----------|-----------------|
| **P2.1** Dashboard simplification | Too many concepts | Center on resume/create/review | P2 | Reduced cognitive load |
| **P2.2** Auto-create starter workspace | User must manually create | Auto-create after registration | P2 | Faster time-to-value |
| **P2.3** AI server-side inference | Heuristic UI suggestions | Server defaults, editable results | P2 | Reduced fields/clicks |
| **P2.4** Performance budget enforcement | No bundle size limits | Add CI bundle analysis | P2 | Fast load times |
| **P2.5** Operator control plane | Generic operations docs | YAPPC-specific operational views | P2 | Production operability |
| **P2.6** Remove unnecessary `@ts-nocheck` from the active product spine | Weak typing across active frontend flows | Replace suppressions with proper types incrementally | P2 | Type safety |
| **P2.7** Progressive disclosure implementation | All features visible | Hide advanced behind "more" | P2 | Simpler primary UX |

### Phase 3: Differentiation + Innovation + Frontrunner Positioning (6-8 weeks)

**Goal**: Meaningful moats, market leadership

| Task | Problem | Fix Direction | Priority | Expected Impact |
|------|---------|---------------|----------|-----------------|
| **P3.1** Learn/Evolve visible UX | Hidden differentiator | Surface telemetry → insights → actions | P3 | Competitive differentiation |
| **P3.2** Server-side project type inference | Manual project type selection | AI suggests based on description | P3 | Reduced effort |
| **P3.3** Duplicate detection | No awareness of existing | Detect similar projects/requirements | P3 | Quality improvement |
| **P3.4** Artifact summarization | Manual artifact review | AI-generated phase summaries | P3 | Faster governance |
| **P3.5** Anomaly detection | No proactive health alerts | Surface readiness anomalies | P3 | Prevent bad deployments |
| **P3.6** Cross-workspace recommendations | Siloed knowledge | Suggest patterns from other workspaces | P3 | Knowledge leverage |
| **P3.7** Deep workflow automation | Workflows not fully implemented | Complete workflow engine | P3 | Full lifecycle automation |
| **P3.8** Real-time collaboration polish | Infrastructure present, UX weak | Finish collaborative features | P3 | Team enablement |

---

## Appendix A: File References

### Critical Files to Modify

```
# Phase 0 Priority
frontend/web/src/routes/onboarding.tsx
frontend/web/src/components/workspace/CreateProjectDialog.tsx
frontend/web/src/hooks/useWorkspaceData.ts
frontend/web/src/routes/app/projects.tsx
frontend/web/src/routes.ts
frontend/web/src/services/canvas/lifecycle/PhaseGateService.ts
frontend/web/src/services/canvas/lifecycle/LifecycleArtifactService.ts
frontend/apps/api/src/routes/auth.ts
frontend/web/src/routes/dashboard.tsx

# Phase 1 Priority
frontend/web/src/services/canvas/CanvasPersistence.ts
frontend/web/src/routes/app/project/canvas.tsx
frontend/web/src/hooks/useCanvasPersistence.ts
frontend/apps/api/src/index.ts
frontend/apps/api/src/__tests__/routes.integration.test.ts
frontend/e2e/golden-path.spec.ts
api/yappc-api.openapi.yaml

# Phase 2 Priority
frontend/web/src/routes/dashboard.tsx
frontend/web/src/components/workspace/OnboardingFlow.tsx
frontend/web/src/app-theme.tsx
frontend/web/package.json (bundle analysis)

# Phase 3 Priority
frontend/web/src/routes/app/project/lifecycle.tsx
core/knowledge-graph/src/main/java/... (expose to UX)
core/ai/src/main/java/... (inference services)
```

### Documentation to Update

```
DEPLOYMENT_GUIDE.md (remove "Production Ready" claim until true)
README.md (update status, coverage claims)
docs/CRITICAL_JOURNEYS.md (add Phase 0 journeys)
```

---

## Appendix B: Success Metrics

### Phase 0 Completion Criteria

- [ ] Onboarding completes without 404
- [ ] Project created via UI survives page reload
- [ ] No demo data fallbacks in production builds
- [ ] All navigation uses correct route taxonomy
- [ ] No dead links in primary UX
- [ ] Route smoke test passes in CI
- [ ] Integration test imports valid
- [ ] All project creation calls authenticated

### Phase 1 Completion Criteria

- [ ] E2E test: register → create project → canvas → reload → project persists
- [ ] E2E test: login → create project → logout → login → project visible
- [ ] Contract tests: frontend types match OpenAPI
- [ ] Single canvas persistence path with cleanup
- [ ] `@yappc/ui` reduced to <50 components
- [ ] All migrations use single authority (Prisma or Flyway)

### Phase 2 Completion Criteria

- [ ] Dashboard shows only: resume project, create project, review blockers
- [ ] New users have auto-created workspace with starter project
- [ ] AI suggestions from server, not frontend heuristics
- [ ] Bundle size <200KB initial JS
- [ ] Operator dashboard with health, metrics, deployment status

### Phase 3 Completion Criteria

- [ ] Learn/Evolve visible as dedicated product area
- [ ] 80% of projects have server-suggested (not manual) type
- [ ] Duplicate detection warns before creation
- [ ] Phase summaries auto-generated
- [ ] Anomaly detection alerts on readiness issues

---

**Document Owner:** YAPPC Architecture Team  
**Review Schedule:** Weekly during Phase 0, bi-weekly thereafter  
**Next Review Date:** 2026-04-24
