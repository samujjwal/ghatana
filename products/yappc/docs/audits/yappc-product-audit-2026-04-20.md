# Yappc End-to-End Product Audit

Date: 2026-04-20
Audited by: Codex
Scope: Product vision, feature completeness, UI/UX, frontend behavior, APIs and contracts, backend/domain logic, database operations, automation/orchestration, AI/ML-assisted behavior, governance and approvals, observability and visibility, tests, configs, and production readiness.

## A. Executive Assessment

Yappc is an alpha-stage product with meaningful architecture and product intent, but it is not yet production-ready. The repo is candid about that in `products/yappc/README.md`, and the codebase confirms it.

- Overall product maturity: Alpha
- Overall feature completeness: 4/10
- Overall end-to-end correctness: 3/10
- UI/UX simplicity score: 3/10
- Cognitive load: High
- Visibility/transparency: Operator-facing 6/10, end-user-facing 2/10
- Manual-effort burden: High
- Implicit AI/ML effectiveness: 3/10
- Release readiness: 2/10

### Top 10 blockers

1. Conflicting lifecycle models across docs, config, API, and DB.
2. New projects default to `SHAPE` instead of `INTENT`.
3. Project creation can return `500` because `aiHealthScore` is read from an undefined variable.
4. Mounted settings and profile flows are contract-mismatched or unbacked.
5. Onboarding collects intent data it does not durably persist, then still marks onboarding complete on many failures.
6. Deploy and preview are presented as real product flows but are not backed by real deployment or preview execution.
7. Frontend fallback layers hide remote-write failure and reduce truthfulness of persisted state.
8. Apparent page and component surface is much larger than the actually mounted, reachable product surface.
9. Tests are too mock-heavy to certify product completeness, and one integration suite is currently non-runnable.
10. Documentation and setup instructions have drifted from the actual structure and execution paths.

## B. Reconstructed Product Model

### Audit assumptions

- `products/yappc/frontend/web/src/routes.ts` is treated as the canonical mounted web route tree.
- `products/yappc/frontend/apps/api` is treated as the primary backend for mounted web flows.
- Java services and YAML pipelines are treated as intended architecture, not proof of current end-to-end behavior.
- A feature is treated as complete only when it is discoverable, usable, reachable, contract-aligned, persisted correctly, and supported by credible tests.
- This audit is code-trace and test-evidence based; it is not a manual browser QA run.

### Reconstructed personas

- Solo founder or builder turning intent into a working app quickly.
- Product manager shaping project intent, readiness, and lifecycle progression.
- Engineering or platform lead managing deploy confidence and operational visibility.
- Operator or reviewer needing policy, auditability, and health status.

### Reconstructed jobs to be done

- Create a workspace and project from intent with very low setup friction.
- Use guided lifecycle phases to move an idea toward a working app.
- See what the system did automatically, what is blocked, and what requires review.
- Preview and deploy safely with enough visibility and governance.
- Keep enough traceability to trust automation and troubleshoot failures.

### Reconstructed intended workflows

- Authenticate or bootstrap into the product.
- Complete onboarding and create a starter workspace and project.
- Author within canvas or lifecycle surfaces.
- Progress phases with readiness scoring and setup suggestions.
- Preview and deploy.
- Use settings, audit, and operational visibility surfaces as needed.

### Reconstructed intended feature set

- Workspaces
- Projects
- Onboarding
- Canvas authoring
- Lifecycle management and phase transitions
- Preview
- Deploy
- AI-assisted suggestions and readiness scoring
- Audit trail
- Settings and profile
- Health, metrics, and observability

### Expected automation points

- Name suggestions
- Persona inference or recommendation
- Readiness scoring
- Setup suggestions
- Next-best action recommendation
- Default project scaffolding
- Deploy planning suggestions

### Expected visibility points

- Current phase
- Blocked reasons
- Pending approvals or reviews
- Save and sync state
- Preview and deploy status
- Automation outputs
- Audit trail and notable state changes

### Justified review and governance points

- Phase transitions with policy gates
- Release and deploy approval
- Privileged settings or access changes
- Low-confidence or risk-bearing automation

## C. Complete Feature Inventory and Completeness Matrix

Legend:

- Claim: Docs, UI, Code
- Status values: Good, Partial, Broken, Absent, Hidden

### 1. Authentication and session

- Intended outcome: User can enter and use the product securely with a durable session.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Partial
- End-to-end implementation: Partial
- Visibility/status feedback: Partial
- Manual-effort level: Low
- AI/ML assistance: None
- UI state: Partial
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence: Limited
- Gaps:
  - Dev-auth bypass can conceal realistic auth behavior during development.
  - There is limited full-stack proof for the real auth path.
- Severity: P1
- Exact files:
  - `products/yappc/frontend/web/src/providers/AuthProvider.tsx`
  - `products/yappc/frontend/web/src/providers/auth-session.ts`
  - `products/yappc/frontend/apps/api/src/routes/auth.ts`
  - `products/yappc/frontend/apps/api/src/middleware/dev-auth-config.ts`
  - `products/yappc/frontend/apps/api/src/middleware/devAuth.ts`

### 2. Onboarding and bootstrap

- Intended outcome: User expresses intent once and receives a ready workspace and project with meaningful defaults.
- Claimed in: Docs, UI, Code
- Discoverability: Good
- Usability: Broken
- End-to-end implementation: Broken
- Visibility/status feedback: Broken
- Manual-effort level: High
- AI/ML assistance: Heuristic and shallow
- UI state: Partial
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence: No trustworthy end-to-end proof
- Gaps:
  - `OnboardingFlow` collects workspace name, project type, project name, and personas but only persists a workspace and a generic default project.
  - Persona selections are stored in localStorage rather than a durable system-of-record path.
  - On non-duplicate failures, onboarding can still be marked complete.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/components/workspace/OnboardingFlow.tsx`
  - `products/yappc/frontend/web/src/routes/onboarding.tsx`
  - `products/yappc/frontend/apps/api/src/routes/workspaces.ts`

### 3. Workspace list and workspace home

- Intended outcome: User can browse and manage workspaces and understand their current state.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Partial
- End-to-end implementation: Partial
- Visibility/status feedback: Broken
- Manual-effort level: Medium
- AI/ML assistance: Minimal
- UI state: Partial
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence: Route test exists, but does not prove full-stack behavior
- Gaps:
  - UI displays `workspace.memberCount || 1`, which fabricates visibility.
  - Workspace settings navigation points to a route that does not exist.
  - Workspace stats are incomplete relative to what the UI implies.
- Severity: P1
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/workspaces.tsx`
  - `products/yappc/frontend/apps/api/src/routes/workspaces.ts`
  - `products/yappc/frontend/web/src/routes/__tests__/workspaces-route.test.tsx`

### 4. Workspace settings

- Intended outcome: User can update meaningful workspace settings through a truthful, backed UI.
- Claimed in: UI, Code
- Discoverability: Partial
- Usability: Broken
- End-to-end implementation: Broken
- Visibility/status feedback: Broken
- Manual-effort level: High
- AI/ML assistance: None
- UI state: Partial
- API state: Broken
- Backend/domain state: Broken
- DB state: Partial
- Test evidence: No credible end-to-end proof
- Gaps:
  - Frontend expects a raw object while API returns `{ workspace }`.
  - Frontend exposes fields such as slug and notification toggles that the API does not support.
  - PATCH semantics only support `name` and `description`.
  - File docstring already admits the billing area is placeholder-level.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/settings.tsx`
  - `products/yappc/frontend/apps/api/src/routes/workspaces.ts`

### 5. Project list and project creation

- Intended outcome: User can create and browse projects reliably.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Partial
- End-to-end implementation: Broken
- Visibility/status feedback: Partial
- Manual-effort level: Medium
- AI/ML assistance: Heuristic
- UI state: Partial
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence:
  - `projects.setup-suggestion.test.ts` passed.
  - `projects.audit.test.ts` failed because create returned `500` instead of `201`.
- Gaps:
  - Project creation response references undefined `aiHealthScore`.
  - New projects default into the wrong lifecycle phase.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/projects.tsx`
  - `products/yappc/frontend/apps/api/src/routes/projects.ts`
  - `products/yappc/frontend/apps/api/src/routes/__tests__/projects.audit.test.ts`
  - `products/yappc/frontend/apps/api/src/routes/__tests__/projects.setup-suggestion.test.ts`

### 6. Project shell

- Intended outcome: User lands in a canonical project home with consistent, truthful project state.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Broken
- End-to-end implementation: Broken
- Visibility/status feedback: Partial
- Manual-effort level: Medium
- AI/ML assistance: None
- UI state: Partial
- API state: Broken
- Backend/domain state: Broken
- DB state: Partial
- Test evidence: Route test passed, but it is heavily mocked
- Gaps:
  - Frontend parses `/api/projects/:projectId` as if it returns a raw project object.
  - API returns `{ project: ... }`.
  - This mismatch undermines the canonical project state load path.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/project/_shell.tsx`
  - `products/yappc/frontend/apps/api/src/routes/projects.ts`
  - `products/yappc/frontend/web/src/routes/app/project/__tests__/shell.test.tsx`

### 7. Canvas and authoring surfaces

- Intended outcome: User can author project artifacts with low friction and reliable persistence.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Partial
- End-to-end implementation: Partial
- Visibility/status feedback: Partial
- Manual-effort level: Medium
- AI/ML assistance: Intended, but not deeply realized
- UI state: Partial
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence: Limited
- Gaps:
  - Large surface area exists, but there is not enough evidence that the full authoring loop is cohesive and trustworthy.
  - Save semantics are weakened by local fallback behavior.
- Severity: P1
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/project/canvas.tsx`
  - `products/yappc/frontend/web/src/routes/app/project/canvas-workspace.tsx`
  - `products/yappc/frontend/web/src/services/canvas/lifecycle/LifecycleArtifactService.ts`

### 8. Lifecycle and phase gates

- Intended outcome: User progresses through phases with readiness guidance, policy, and visibility.
- Claimed in: Docs, UI, Code
- Discoverability: Good
- Usability: Partial
- End-to-end implementation: Partial
- Visibility/status feedback: Partial
- Manual-effort level: Medium
- AI/ML assistance: Heuristic and modest
- UI state: Partial
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence:
  - `lifecycle-phase-preview-routes.test.ts` passed.
  - Web lifecycle route test passed but is mock-heavy.
- Gaps:
  - This is one of the stronger feature areas, but the repo has multiple incompatible lifecycle taxonomies.
  - Client fallback behavior can hide remote-write problems.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/project/lifecycle.tsx`
  - `products/yappc/frontend/apps/api/src/routes/lifecycle.ts`
  - `products/yappc/config/pipelines/lifecycle-management-v1.yaml`
  - `products/yappc/config/agents/phase-transition-events.yaml`
  - `products/yappc/frontend/web/src/routes/app/project/__tests__/lifecycle.test.tsx`
  - `products/yappc/frontend/apps/api/src/__tests__/lifecycle-phase-preview-routes.test.ts`

### 9. Deploy

- Intended outcome: User can release or deploy through a real, trustworthy workflow.
- Claimed in: Docs, UI, Code
- Discoverability: Good
- Usability: Broken
- End-to-end implementation: Broken
- Visibility/status feedback: Broken
- Manual-effort level: High
- AI/ML assistance: Minimal
- UI state: Partial
- API state: Partial
- Backend/domain state: Broken
- DB state: Absent for a real deploy loop
- Test evidence: UI-level route test only
- Gaps:
  - Route uses hardcoded cost and capacity values.
  - Presented as deployment management even though the backing CI/CD adapter is a no-op.
  - This is deployment theater rather than a credible production workflow.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/project/deploy.tsx`
  - `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/run/NoOpCiCdAdapter.java`
  - `products/yappc/frontend/web/src/routes/app/project/__tests__/deploy.test.tsx`

### 10. Preview

- Intended outcome: User can see a working preview of the current project state.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Broken
- End-to-end implementation: Absent
- Visibility/status feedback: Broken
- Manual-effort level: Medium
- AI/ML assistance: None
- UI state: Partial
- API state: Absent
- Backend/domain state: Absent
- DB state: Absent
- Test evidence: No credible proof
- Gaps:
  - Frontend constructs an iframe URL for a preview host path, but no backing implementation was found in Yappc for that route.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/project/preview.tsx`

### 11. Project settings and project admin

- Intended outcome: User can manage project settings, access, tokens, and audit through a backed UI.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Broken
- End-to-end implementation: Broken
- Visibility/status feedback: Broken
- Manual-effort level: High
- AI/ML assistance: None
- UI state: Partial
- API state: Absent or mismatched
- Backend/domain state: Absent or mismatched
- DB state: Partial
- Test evidence: No credible proof
- Gaps:
  - Frontend expects unimplemented `/members`, `/tokens`, and `/audit` endpoints.
  - PATCH omits required `workspaceId`.
  - File contains explicit stub indicators for E2E.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/app/project/settings.tsx`
  - `products/yappc/frontend/apps/api/src/routes/projects.ts`

### 12. Profile

- Intended outcome: User can view and update their own profile.
- Claimed in: UI, Code
- Discoverability: Good
- Usability: Broken
- End-to-end implementation: Absent
- Visibility/status feedback: Broken
- Manual-effort level: Medium
- AI/ML assistance: None
- UI state: Partial
- API state: Absent
- Backend/domain state: Absent
- DB state: Absent in the mounted web/API path
- Test evidence: None
- Gaps:
  - Frontend uses `/api/users/me`, but no matching backend route was found.
- Severity: P0
- Exact files:
  - `products/yappc/frontend/web/src/routes/profile.tsx`

### 13. AI suggestions and setup assistance

- Intended outcome: Reduce setup effort and help users make better choices implicitly.
- Claimed in: Docs, UI, Code
- Discoverability: Partial
- Usability: Partial
- End-to-end implementation: Partial
- Visibility/status feedback: Partial
- Manual-effort level: Medium
- AI/ML assistance: Mostly deterministic or rule-based
- UI state: Partial
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence: Setup suggestion route test passed
- Gaps:
  - Current implementation is mostly heuristic and not yet reducing enough real workflow effort.
  - Product language can imply more AI depth than the current operational reality.
- Severity: P1
- Exact files:
  - `products/yappc/frontend/apps/api/src/routes/ai.ts`
  - `products/yappc/frontend/web/src/hooks/useWorkspaceData.ts`
  - `products/yappc/frontend/apps/api/src/routes/__tests__/projects.setup-suggestion.test.ts`

### 14. Audit, metrics, and operational visibility

- Intended outcome: Operators can understand product health, actions, and traceability.
- Claimed in: Docs, Code
- Discoverability: Hidden to standard users
- Usability: Partial
- End-to-end implementation: Partial
- Visibility/status feedback: Partial
- Manual-effort level: Low
- AI/ML assistance: None
- UI state: Absent or minimal
- API state: Partial
- Backend/domain state: Partial
- DB state: Partial
- Test evidence: Some infrastructure exists, but no strong product-level proof
- Gaps:
  - Metrics, health, and audit plumbing are stronger than user-facing status truthfulness.
  - Product UI does not expose the right operator or reviewer visibility in a calm way.
- Severity: P1
- Exact files:
  - `products/yappc/frontend/apps/api/src/index.ts`
  - `products/yappc/frontend/apps/api/src/middleware/audit.middleware.ts`
  - `products/yappc/frontend/apps/api/src/services/audit/audit.service.ts`
  - `products/yappc/monitoring/prometheus/rules/yappc-*`
  - `products/yappc/monitoring/grafana/dashboards/yappc/*`

### 15. Latent or unreachable feature surface

- Intended outcome: Claimed features are actually reachable and usable.
- Claimed in: Code
- Discoverability: Hidden
- Usability: Hidden or absent
- End-to-end implementation: Hidden or absent
- Visibility/status feedback: N/A
- Manual-effort level: N/A
- AI/ML assistance: N/A
- UI state: Hidden
- API state: Hidden
- Backend/domain state: Hidden
- DB state: Hidden
- Test evidence: None
- Gaps:
  - The app has a small mounted route tree but a much larger page and component surface, which creates a false sense of completeness if judged superficially.
- Severity: P1
- Exact files:
  - `products/yappc/frontend/web/src/routes.ts`
  - `products/yappc/frontend/web/src/pages/**`

## D. UI/UX Review

### Navigation and information architecture

- The mounted IA is small and understandable, but it is undermined by a larger latent page surface.
- Workspace settings navigation is broken from the list screen.
- Lifecycle, preview, and deploy are surfaced as first-class concepts before they are trustworthy as end-to-end flows.

### Workflow simplicity

- Onboarding asks for more than it can actually save or apply.
- The product asks the user to think in platform and lifecycle abstractions too early.
- Several flows branch into settings or admin-style concepts before the core happy path is durable.

### Cognitive load

- Yappc currently exposes too many mental models at once: workspaces, projects, lifecycle phases, SDLC phases, preview, deploy, gates, audit.
- The user has to think about system internals that should be hidden behind defaults, summaries, and guided actions.

### Discoverability

- Primary routes are discoverable.
- Discoverability is currently misleading because some entry points lead to partial, mismatched, or unsupported flows.

### Form design

- Workspace and project settings expose unsupported fields.
- Some onboarding inputs are not durably saved.
- Defaults and inference are underused where they would materially reduce effort.

### Review and approval UX

- Governance exists more in config and intent than in the actual user-facing workflow.
- The product does not yet provide a crisp explanation for why user approval is required at specific points.

### Visibility, progress, and status UX

- Some values are fabricated or optimistic, such as workspace member count.
- Save and sync truth is weak because failures can be hidden behind local fallback behavior.
- Preview and deploy do not clearly communicate whether they are real, simulated, blocked, or not yet supported.

### AI/ML-assisted UX

- AI is too explicit in language relative to the current working depth.
- The better pattern would be more silent defaults, summaries, autofill, and next-best actions.

### Consistency and clarity

- API response envelopes are inconsistent with frontend expectations on key flows.
- Terminology is inconsistent between docs, config, schema, and UI-adjacent services.

## E. API Review

### Completeness issues

- Missing or unsupported routes for mounted UI flows:
  - `/api/users/me`
  - project members
  - project tokens
  - project audit support as consumed by the project settings UI
  - durable preview and deploy support

### Contract issues

- `GET /workspaces/:workspaceId` returns `{ workspace }`, while workspace settings expects a raw object.
- `GET /projects/:projectId` returns `{ project }`, while project shell and project settings expect a raw object.
- `PATCH /projects/:projectId` requires `workspaceId`, but current project settings UI does not send it.

### Validation and error semantics gaps

- Workspace and project settings expose fields not meaningfully supported by the API.
- Project creation can fail with a server error because of an internal response-shaping bug.

### Manual-interaction-causing API flaws

- Settings is fragmented into multiple calls, several of which are unsupported.
- The API does not provide enough derived-state or batch-oriented responses to keep the UI calm and simple.

### Visibility and status support gaps

- The API does not clearly expose states such as remote saved, local-only, pending background work, or approval required.
- There is no clean, user-facing status model for preview or deploy work.

### Frontend/backend mismatches

- Response envelope mismatches on project and workspace reads.
- Missing required query params from the frontend.
- Mounted UI implies richer contracts than the API actually supports.

## F. Backend and Domain Review

### Logic issues

- Fresh projects start in `SHAPE`, skipping `INTENT`.
- Project creation can 500 because of undefined response shaping.
- Onboarding does not carry captured intent through to durable project creation.

### Completeness issues

- CI/CD integration is explicitly no-op.
- Preview is assumed by the frontend but not backed by a found implementation in Yappc.
- Profile and settings flows are not fully backed.

### Orchestration issues

- There is no single authoritative lifecycle model.
- Pipeline and transition config do not align with Prisma and mounted application behavior.

### Automation gaps

- AI assistance is mostly heuristic and local-fallback driven.
- The product does not yet automate enough setup, inference, summarization, or progression work to justify the intended promise.

### Visibility and status propagation gaps

- Client services prioritize not blocking the user, but do so partly by hiding failed remote writes.
- That creates divergence risk between user-perceived state and persisted server truth.

### Failure handling issues

- Errors are sometimes suppressed instead of surfaced with actionable context.
- Continuity is favored over trust in a few high-risk save paths.

### Duplication and reuse concerns

- Lifecycle semantics are duplicated across docs, YAML, schema, and services.
- Permission enforcement is partly coarse-grained and partly ad hoc.

## G. Database Review

### Schema issues

- `LifecyclePhase` in Prisma does not align with the SDLC lifecycle defined elsewhere.
- `Project.lifecyclePhase @default(SHAPE)` is a domain flaw.

### Feature-support gaps

- Onboarding persona and intention capture are not durably represented in the primary web/API path.
- I did not find a durable state model for preview or deploy that matches the UI ambition.

### Query and transaction issues

- Workspace bootstrap creates a default project without carrying through project metadata captured from the user.
- Workspace list UI displays counts that are not actually provided by the backend.

### Integrity risks

- Local fallback repositories can diverge from server truth.
- Save semantics are not explicit enough to make data truth easy to trust.

### Audit, history, and traceability

- Audit infrastructure exists and is a positive foundation.
- User-facing traceability of automation decisions, approval reasons, and sync outcomes is still weak.

### Lifecycle and modeling issues

- The product cannot be both simple and correct until lifecycle state is modeled once, persisted once, and surfaced consistently everywhere.

## H. Visibility and Transparency Review

### What is visible enough

- Health and metrics endpoints exist.
- Audit middleware and service exist.
- Some readiness and lifecycle preview machinery exists.

### What is hidden but should be visible

- Whether a change is saved remotely or only locally.
- Whether preview or deploy is real, simulated, blocked, or unavailable.
- Why a transition is blocked and the exact next action to unblock it.
- What automation did automatically and with what confidence.

### What is too visible or noisy

- Advanced lifecycle and deployment abstractions are too prominent for the current maturity level.
- Platform-style controls and settings appear before the core flow is trustworthy.

### Missing progress and status communication

- No explicit sync state.
- No clear pending-review queue.
- No consistent long-running operation state.
- No clear ownership of the next action.

### Missing auditability

- Persona and onboarding selections can remain local-only.
- Heuristic and fallback paths are not exposed clearly enough where trust matters.

### Missing operational traceability

- Operator-facing observability is stronger than user-facing workflow truthfulness.
- The product does not yet translate backend visibility into calm, helpful product-level status.

## I. AI/ML Implicit Automation Review

### Where AI/ML is used correctly

- Setup suggestions and readiness scoring are directionally correct.
- The explicit `rule_based_heuristic` labeling is more honest than pretending these flows are model-driven.

### Where AI/ML should replace manual work

- Onboarding should infer and persist project shape from the first intent capture.
- Naming, persona grouping, readiness summarization, duplicate detection, and next-best actions should be mostly automatic.
- Deploy planning should auto-suggest safe defaults instead of making users reason like operators.

### Where AI/ML should remain hidden

- Naming assistance
- Autofill
- Summarization
- Prioritization
- Low-risk default selection

### Where AI/ML should expose rationale and review

- Phase transitions with governance impact
- Release and deploy recommendations
- Any automation with policy or external-risk implications

### Where approvals are rightly required

- Release and deploy
- Policy exceptions
- Access-control changes
- Low-confidence automation with material consequences

## J. Testing and Evidence Gaps

### Tests executed during this audit

- Web route tests passed:
  - `src/__tests__/routes.spec.ts`
  - `src/routes/app/project/__tests__/shell.test.tsx`
  - `src/routes/app/project/__tests__/deploy.test.tsx`
  - `src/routes/app/project/__tests__/lifecycle.test.tsx`
- API tests had mixed results:
  - `src/routes/__tests__/projects.setup-suggestion.test.ts` passed
  - `src/__tests__/lifecycle-phase-preview-routes.test.ts` passed
  - `src/routes/__tests__/projects.audit.test.ts` failed because create returned `500` instead of `201`
  - `src/__tests__/lifecycle-gates.integration.test.ts` did not start because the generated Prisma client module was missing

### Missing tests by level

- Full-stack onboarding
- Workspace settings
- Project settings and admin
- Profile
- Preview
- Deploy end-to-end

### Weak or incomplete tests

- Web route tests are heavily mocked and do not prove API-contract alignment.
- Integration coverage is not strong enough to certify feature completeness.

### Unverified workflows

- Any route with known contract mismatch or missing backend support
- Save and sync failure behavior when remote calls fail
- Governance and approval workflows as experienced by real users

### Highest-risk uncovered paths

- Project creation
- Settings
- Preview
- Deploy
- Profile

## K. Prioritized Remediation Plan

### P0: must fix immediately

#### 1. Unify the lifecycle model

- Problem: Core product story and implementation disagree on lifecycle.
- Why it matters: Prevents feature completeness, trust, and correct state modeling.
- Root cause: Parallel definitions in docs, YAML, schema, and code.
- Exact fix:
  - Choose one canonical lifecycle model.
  - Refactor README, config, Prisma enum, defaults, API types, and UI copy to match.
  - Add a shared lifecycle constant or domain package used everywhere.
- Affected layers: Docs, config, DB, API, web
- Suggested validations/tests:
  - Schema-level lifecycle tests
  - Route contract tests
  - Copy and config consistency checks

#### 2. Fix project creation correctness

- Problem: Project creation can return `500`.
- Why it matters: Breaks a core product action.
- Root cause: Undefined `aiHealthScore` response field.
- Exact fix:
  - Return `healthResult.score` or remove the field until correct.
  - Add regression test coverage.
- Affected layers: API, web, tests
- Suggested validations/tests:
  - Create-project route test expecting `201`
  - Contract assertion for response shape

#### 3. Repair or remove unsupported mounted routes

- Problem: Broken routes are currently discoverable.
- Why it matters: Product appears more complete than it is and wastes user effort.
- Root cause: UI shipped ahead of backend contracts.
- Exact fix:
  - Complete or hide `profile`, workspace settings, project settings, preview, and deploy.
  - Replace broken screens with explicit unavailable states if completion is deferred.
- Affected layers: Web, API
- Suggested validations/tests:
  - Mounted route inventory test
  - Smoke tests for every visible navigation entry

#### 4. Stop hiding failed remote writes

- Problem: User can believe data was saved when only local fallback succeeded.
- Why it matters: Direct trust and correctness risk.
- Root cause: Silent or weakly surfaced fallback behavior.
- Exact fix:
  - Introduce explicit `local-only`, `syncing`, `remote-failed`, and `remote-saved` statuses.
  - Surface them in the UI wherever save or transition occurs.
- Affected layers: Web services, API, UI
- Suggested validations/tests:
  - Failure injection tests
  - Save-state UX tests

### P1: required for product confidence

#### 5. Make onboarding honest and durable

- Problem: User-entered onboarding information is not carried through.
- Why it matters: First-run experience breaks trust and increases manual rework.
- Root cause: Frontend collects richer intent than backend creation path uses.
- Exact fix:
  - Either shrink onboarding to only what is saved, or persist project name, type, and personas into created records.
- Affected layers: Web, API, DB
- Suggested validations/tests:
  - Onboarding integration test
  - Persistence verification test

#### 6. Align API envelopes and query contracts with the frontend

- Problem: Core routes are structurally mismatched.
- Why it matters: Causes broken state loads and settings saves.
- Root cause: Unshared DTO and response-shape assumptions.
- Exact fix:
  - Standardize read and write contracts.
  - Generate shared types if feasible.
- Affected layers: API, web
- Suggested validations/tests:
  - Contract tests
  - Typed client compile check

#### 7. Replace deploy theater with real status or honest planning mode

- Problem: Deploy screen implies real operations it does not support.
- Why it matters: Erodes product trust.
- Root cause: UI maturity exceeded backend execution maturity.
- Exact fix:
  - Either wire real deploy state, or relabel and simplify it as release planning until infrastructure exists.
- Affected layers: Web, backend services
- Suggested validations/tests:
  - Smoke tests
  - Copy and behavior acceptance tests

#### 8. Restore runnable integration testing

- Problem: Important suites do not run reliably.
- Why it matters: Blocks confidence and regression protection.
- Root cause: Missing generated Prisma client and test-environment drift.
- Exact fix:
  - Ensure Prisma generation is part of test bootstrap and CI.
- Affected layers: Tooling, API, CI
- Suggested validations/tests:
  - Full integration suite run in CI

### P2: quality, UX, and visibility hardening

#### 9. Collapse project surfaces into one calmer project cockpit

- Problem: Too many concept-heavy screens.
- Why it matters: Raises cognitive load.
- Root cause: Architecture-first rather than workflow-first product design.
- Exact fix:
  - Merge shell, lifecycle, preview, and deploy into one progressive project home.
- Affected layers: Web, UX
- Suggested validations/tests:
  - Navigation smoke tests
  - UX acceptance criteria

#### 10. Remove unsupported fields and fabricated values

- Problem: UI exposes settings and stats that are not real.
- Why it matters: Misleading product behavior.
- Root cause: Placeholder UI shipped with incomplete contracts.
- Exact fix:
  - Remove unsupported fields or back them properly.
  - Remove fabricated counts unless derived from real data.
- Affected layers: Web, API
- Suggested validations/tests:
  - Route render assertions
  - Data-contract tests

#### 11. Surface audit timeline, sync state, and next action

- Problem: Users lack a truthful narrative of system state.
- Why it matters: Reduces trust and increases confusion.
- Root cause: Good backend visibility is not translated into product-level UX.
- Exact fix:
  - Add a compact project status and history panel with progressive disclosure.
- Affected layers: Web, API, audit services
- Suggested validations/tests:
  - UI behavior tests
  - Audit event rendering tests

### P3: strategic improvements

#### 12. Centralize lifecycle, policy, and readiness logic

- Problem: Domain logic is split across many places.
- Why it matters: Hard to keep simple and correct over time.
- Root cause: Incomplete centralization.
- Exact fix:
  - Build a shared domain layer for lifecycle, policy, and readiness semantics.
- Affected layers: Config, API, web, services
- Suggested validations/tests:
  - Shared unit tests
  - Contract consistency tests

#### 13. Expand implicit AI assistance only after core flow truthfulness is fixed

- Problem: AI promise is ahead of real workflow simplification.
- Why it matters: Risks visible novelty without practical value.
- Root cause: Product language outpacing operational depth.
- Exact fix:
  - Focus AI on autofill, summarization, next-best action, anomaly hints, and deduplication after the core flow is trustworthy.
- Affected layers: Product, web, API
- Suggested validations/tests:
  - Outcome-based UX tests
  - Human override and confidence tests

## L. Simplification and Automation Blueprint

### Screens and routes to merge or remove

- Merge `canvas`, `lifecycle`, `preview`, and `deploy` into one project cockpit with progressive disclosure.
- Hide or remove `profile` until `/api/users/me` exists.
- Remove unsupported settings tabs and fields until backed.

### Fields to remove now

- Unsupported workspace notification toggles and other unsaved fields.
- Unsupported project settings sections for members, tokens, and audit where the API does not back them.
- Hardcoded or unreal deploy capacity and cost controls.

### Fields to infer or prefill

- Workspace name from user intent when reasonable.
- Starter project name and type from onboarding input.
- Persona recommendations.
- Default release mode and environment where low-risk.

### Steps to automate

- Create workspace and correctly named starter project in one action.
- Persist onboarding intent into the created project automatically.
- Auto-refresh project shell after transitions.
- Auto-generate readiness summary and next recommended action after important saves.

### Manual tasks to batch or eliminate

- Combine workspace creation and project creation.
- Reduce repeated settings calls and save flows.
- Eliminate repeated transfer of onboarding choices into later configuration steps.

### Decisions to collapse into defaults

- Default starter template
- Default lifecycle entry point
- Default low-risk AI suggestions
- Default primary project status summary

### Review points to retain

- Release and deploy
- Policy exceptions
- Access-control changes

### Review points to remove

- Low-risk naming, categorization, and persona suggestions
- Routine readiness summarization
- Non-critical configuration nudges

### Visibility points to improve

- One project timeline with `Saved`, `Syncing`, `Blocked`, `Ready for review`, and `Deployed`
- One clear owner for the next action
- One audit and status drawer for automation and approvals

### Advanced options to hide behind progressive disclosure

- Tokens
- Infrastructure tuning
- Notification controls
- Audit internals
- Policy details

### AI/ML interventions to keep implicit

- Autofill
- Summarization
- Prioritization
- Duplicate detection
- Anomaly hints
- Next-best actions

## M. Final Verdict

At the product workflow level, Yappc is not yet feature-complete, not yet fully end-to-end, and not yet simple enough to count as production-grade.

### What is most complete today

- Basic auth and session scaffolding
- Health, metrics, and audit plumbing
- Lifecycle preview and readiness APIs
- Setup suggestion heuristics

### What is partially complete

- Workspaces and projects as entities
- Canvas and lifecycle authoring surfaces
- Observability foundations
- AI-assisted suggestion direction

### What is not visible enough

- Save and sync truth
- Preview and deploy reality
- Approval reasons and blocked-state rationale
- Ownership of the next action

### What is too manual

- Onboarding
- Settings and admin flows
- Lifecycle navigation
- Any flow that asks the user to reason about internal platform state without doing obvious automation first

### What is misleading or incomplete

- Profile
- Workspace settings
- Project settings
- Preview
- Deploy
- The apparent surface area of the product relative to the actually mounted and working product

### What prevents Yappc from being truly simple

- Conflicting lifecycle vocabulary
- Too many concept-heavy screens too early
- Unsupported controls and fragmented flows

### What prevents it from being fully complete and end-to-end

- Contract mismatches
- Missing endpoints
- Wrong lifecycle defaults
- A real project-create correctness bug
- No-op deployment backend
- Local fallback masking remote failure

### What must change

- Choose one lifecycle model and enforce it everywhere.
- Trim the UI to the working core.
- Make onboarding truthful and durable.
- Make save, sync, deploy, and approval state explicit.
- Replace visible AI framing with implicit automation that actually removes user work.
- Back every mounted flow with real contracts, real persistence, and real full-stack tests.

## Evidence Reference

Primary evidence reviewed included:

- `products/yappc/README.md`
- `products/yappc/frontend/web/src/routes.ts`
- `products/yappc/frontend/web/src/routes/onboarding.tsx`
- `products/yappc/frontend/web/src/routes/profile.tsx`
- `products/yappc/frontend/web/src/routes/settings.tsx`
- `products/yappc/frontend/web/src/routes/app/workspaces.tsx`
- `products/yappc/frontend/web/src/routes/app/projects.tsx`
- `products/yappc/frontend/web/src/routes/app/project/_shell.tsx`
- `products/yappc/frontend/web/src/routes/app/project/lifecycle.tsx`
- `products/yappc/frontend/web/src/routes/app/project/deploy.tsx`
- `products/yappc/frontend/web/src/routes/app/project/preview.tsx`
- `products/yappc/frontend/web/src/routes/app/project/settings.tsx`
- `products/yappc/frontend/web/src/components/workspace/OnboardingFlow.tsx`
- `products/yappc/frontend/web/src/hooks/useWorkspaceData.ts`
- `products/yappc/frontend/web/src/services/canvas/lifecycle/LifecycleArtifactService.ts`
- `products/yappc/frontend/web/src/services/canvas/lifecycle/PhaseGateService.ts`
- `products/yappc/frontend/apps/api/src/routes/workspaces.ts`
- `products/yappc/frontend/apps/api/src/routes/projects.ts`
- `products/yappc/frontend/apps/api/src/routes/lifecycle.ts`
- `products/yappc/frontend/apps/api/src/routes/ai.ts`
- `products/yappc/frontend/apps/api/prisma/schema.prisma`
- `products/yappc/config/pipelines/lifecycle-management-v1.yaml`
- `products/yappc/config/agents/phase-transition-events.yaml`
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/run/NoOpCiCdAdapter.java`

Test evidence reviewed included:

- `products/yappc/frontend/web/src/__tests__/routes.spec.ts`
- `products/yappc/frontend/web/src/routes/app/project/__tests__/shell.test.tsx`
- `products/yappc/frontend/web/src/routes/app/project/__tests__/deploy.test.tsx`
- `products/yappc/frontend/web/src/routes/app/project/__tests__/lifecycle.test.tsx`
- `products/yappc/frontend/apps/api/src/routes/__tests__/projects.audit.test.ts`
- `products/yappc/frontend/apps/api/src/routes/__tests__/projects.setup-suggestion.test.ts`
- `products/yappc/frontend/apps/api/src/__tests__/lifecycle-phase-preview-routes.test.ts`
- `products/yappc/frontend/apps/api/src/__tests__/lifecycle-gates.integration.test.ts`
