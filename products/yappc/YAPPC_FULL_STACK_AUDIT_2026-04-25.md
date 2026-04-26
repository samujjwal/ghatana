# YAPPC Full-Stack Product Audit and Remediation Blueprint

Date: 2026-04-25

Scope: YAPPC product under `products/yappc`, including product docs, active React Router web app, Fastify/GraphQL/Prisma API, Prisma schema, WebSocket collaboration, Java/domain modules, lifecycle automation, auth/session, observability, and current verification/test surfaces.

Primary source intent:

- `products/yappc/README.md`
- `products/yappc/docs/ARCHITECTURE.md`
- `products/yappc/docs/CRITICAL_JOURNEYS.md`
- `products/yappc/DOMAIN_MODEL_REGISTRY.md`
- `products/yappc/YAPPC_UI_UX_AUDIT_2026-04-24.md`
- `products/yappc/YAPPC_UI_UX_TRACKER_2026-04-24.md`

Primary implementation evidence:

- Mounted web routes: `products/yappc/frontend/web/src/routes.ts`
- Web app package: `products/yappc/frontend/web/package.json`
- Node API package: `products/yappc/frontend/apps/api/package.json`
- Fastify API bootstrap: `products/yappc/frontend/apps/api/src/index.ts`
- Auth middleware/routes: `products/yappc/frontend/apps/api/src/middleware/auth.middleware.ts`, `products/yappc/frontend/apps/api/src/routes/auth.ts`, `products/yappc/frontend/apps/api/src/middleware/public-paths.ts`
- Workspace/project/canvas/lifecycle routes: `products/yappc/frontend/apps/api/src/routes/workspaces.ts`, `projects.ts`, `canvas.ts`, `lifecycle.ts`, `lifecycle-execution.ts`
- Realtime service: `products/yappc/frontend/apps/api/src/services/RealTimeService.ts`
- Prisma schema: `products/yappc/frontend/apps/api/prisma/schema.prisma`
- Frontend auth/session/canvas/onboarding: `products/yappc/frontend/web/src/providers/auth-session.ts`, `services/session/SessionManager.ts`, `services/onboarding/OnboardingStatusService.ts`, `services/canvasBackend.ts`, `services/canvas/api/CanvasAPIClient.ts`

Verification performed after writing this audit:

- `pnpm --filter @yappc/web-app run typecheck` failed. The first failures include missing `INSTITUTIONALIZE` handling in lifecycle maps, MUI/design-system prop mismatches, missing exports/modules, route/component typing drift, and multiple stale type contracts.
- `pnpm --filter @yappc/web-app run test:smoke` passed: 1 test file, 1 test.
- Markdown artifact check: created `products/yappc/YAPPC_FULL_STACK_AUDIT_2026-04-25.md` with 1223 lines.

## 1. Executive Summary

YAPPC has a strong strategic concept but is not yet an end-to-end reliable product system. The docs define an ambitious AI-native product lifecycle platform: intent, shape, validate, generate, run, observe, learn, evolve. The implementation contains real building blocks for that future: a mounted React Router app, workspace/project management, canvas persistence, lifecycle phases and artifacts, GraphQL, Fastify REST routes, Prisma persistence, WebSocket collaboration, OpenTelemetry and Prometheus, Java/domain services, Data Cloud integration direction, and AI services.

The product is currently split across several partially aligned systems. The most important product risk is not visual polish. It is that user-visible workflows appear operational while backend authorization, resource ownership, state truth, validation truth, lifecycle semantics, and audit/observability are incomplete or inconsistent.

Overall health:

- Completeness: Medium-to-low. The main user surfaces exist, but end-to-end closure is incomplete for auth, onboarding, workspace/project authorization, canvas validation, lifecycle execution, deployment readiness, AI evidence, and governance.
- Simplicity: Medium. The active route map is cleaner than the older UI audit, but internal architectural complexity leaks into UX through duplicated route prefixes, local fallbacks, mixed lifecycle terminology, multiple persistence paths, and many AI/canvas/service abstractions.
- Correctness: High risk. Several critical paths allow role-based access without resource membership checks. Cookie-based auth and global auth middleware disagree. Canvas validation is mocked. WebSocket collaboration accepts user identity from the client. Lifecycle "AI approval" can mutate phase state based on deterministic artifact counts rather than governed review.
- Consistency: High risk. The docs, tracker, web app, API, lifecycle model, data model, Java domain packages, and generated contracts do not describe one product truth.
- Production readiness: Low. The README itself reports AI-native maturity 3/10, feature completeness 4/10, and production readiness 2/10. That is directionally consistent with the code evidence.

Biggest systemic weaknesses:

- Authorization is global-role based, not resource-membership based, across workspace/project/canvas/lifecycle flows.
- Auth uses three conflicting models: Java canonical auth, Node JWT middleware, and frontend local/cookie session handling.
- Lifecycle taxonomy has drifted from the strategic 8-phase model into a parallel 8-phase implementation model.
- Realtime collaboration is not secure enough for multi-tenant product use.
- Canvas and lifecycle validation are not yet truthful enough to support user trust.
- API/backend/data architecture is fragmented between `frontend/apps/api`, Java lifecycle services, core modules, generated clients, and Data Cloud direction.
- The UI tracker claims many items are done, but verification checkboxes and current code evidence still show unclosed product risks.

Biggest automation opportunities:

- Server-side lifecycle readiness synthesis from artifacts, test evidence, security scans, deployment state, and observability signals.
- AI-assisted but auditable project setup, canvas completion, traceability, test-gap detection, release-risk assessment, and exception triage.
- Automatic evidence linking across canvas nodes, lifecycle artifacts, generated code, deployments, incidents, and learned policies.

## 2. Deep Audit Scorecard

Ratings use Critical, High, Medium, Low as risk levels. High means significant remediation is required before product trust or production use.

| Area | Risk | Assessment |
|---|---:|---|
| Completeness | High | Major surfaces exist, but auth/session, resource authorization, canvas truth, lifecycle closure, deployment closure, notification closure, and admin/governance closure are incomplete. |
| Simplicity | Medium | Active mounted routes are clearer, but duplicated services, local fallback behavior, route-prefix sprawl, lifecycle taxonomy drift, and visible AI/canvas modes increase cognitive load. |
| Correctness | Critical | Resource access and realtime identity correctness are not dependable. Mock validation and heuristic AI approval create misleading product outcomes. |
| Consistency | High | Intent docs, README, tracker, route implementation, lifecycle enum names, API prefixes, and domain packages conflict. |
| UI/UX quality | Medium | Current mounted shell is more disciplined than older sprawl, but trust states, save truth, auth truth, and lifecycle wording need hardening. |
| Frontend quality | Medium | React Query, Jotai, route tests, typed storage registry, and generated contracts are present, but localStorage remains central and many client services bypass generated contracts. |
| API/contract quality | High | Three prefixes register the same routes; GraphQL is public; auth/cookie contracts drift; error/authorization semantics are inconsistent. |
| Backend/workflow quality | High | Business workflows exist but often lack resource authorization, transactional guarantees, producer authentication, and lifecycle truth. |
| Data/persistence quality | High | Core models exist, but tenant/resource access is not enforced by schema or repository pattern; canvas/lifecycle JSON blobs need stronger invariants and provenance. |
| Async/event quality | High | WebSocket, Redis room state, lifecycle execution result persistence, and Java event direction exist, but idempotency/auth/audit/progress are incomplete. |
| Observability/operability | High | OTel, metrics, health, and audit exist, but readiness is incomplete, audit can be fire-and-forget or semantically wrong, and degraded Java health returns HTTP 200. |
| Privacy/security/trust | Critical | Resource auth, public GraphQL, public metrics, default service token, local token storage, and unauthenticated WebSockets are not acceptable for production. |
| AI/ML embedding | High | Many AI concepts exist, but mock/deterministic fallback and confidence UI are ahead of governed evidence, provenance, and review controls. |
| Accessibility | Medium | Tests and components exist, but current evidence is insufficient for full route-level keyboard/screen-reader validation after large route/test changes. |
| Responsiveness | Medium | Mounted routes target responsive shells, but deleted/current e2e coverage makes confidence low. |
| Perceived performance | Medium | React Query and route simplification help; canvas/local fallback/retry behavior can hide failure and produce stale state. |
| Cognitive load | Medium | Too many lifecycle names, AI panels, modes, status badges, and implementation concepts leak into product language. |
| End-to-end product quality | High | Strategic shape is promising, but workflows are not yet complete, correct, consistent, and secure end to end. |

## 3. Surface-by-Surface and Layer-by-Layer Audit

### 3.1 Product Vision and Documentation

Purpose: Define YAPPC as an AI-native product lifecycle orchestration platform.

Completeness:

- The docs provide an unusually explicit target model and honest maturity snapshot.
- The README says the target phases are Intent, Shape, Validate, Generate, Run, Observe, Learn, Evolve.
- The implementation lifecycle route uses Intent, Context, Plan, Execute, Verify, Observe, Learn, Institutionalize, with legacy mapping from Shape/Validate/Generate/Run/Improve.
- README quick start references frontend 7001, API 7002, Java backend 7003, standalone OpenAPI 8082, while Node API CORS defaults to `http://localhost:5173`.
- README architecture text still mentions stale app patterns while active web app is React Router/Vite under `frontend/web`.

Simplicity:

- Product intent is clear at the top level, but users and developers must translate between multiple phase taxonomies and runtime components.

Correctness:

- Docs are directionally honest about maturity, but parts of architecture and tracker status overstate actual end-to-end readiness.

Consistency:

- The docs, route code, lifecycle enum, and data model do not share one canonical language.

Remediation:

- Establish one YAPPC product contract document for mounted routes, supported APIs, lifecycle states, owner/member semantics, and AI/governance claims.
- Mark stale docs as archived or update them to current React Router/Fastify/Java/Data Cloud reality.
- Convert the tracker into an evidence-backed gate: done means verified by test or manual trace, not just code touched.

### 3.2 Navigation, Shell, and Mounted Routes

Purpose: Provide entry points to dashboards, workspaces, projects, profile/settings, and project tabs.

Evidence:

- `routes.ts` mounts `/`, `/login`, `/onboarding`, `/workspaces`, `/projects`, `/profile`, `/settings`, `/p/:projectId`, `/p/:projectId/canvas`, `/preview`, `/deploy`, `/settings`, `/lifecycle`, and `*`.
- Archived and latent route files still exist under `_archived` and `routes/app/*`.

Completeness:

- Mounted product IA is reasonably compact.
- Project lifecycle, preview, deploy, canvas, and settings surfaces are present.
- Missing: role-specific empty states, permission-denied states tied to resource membership, background operation state, and end-to-end status history.

Simplicity:

- Route count is now manageable.
- The user still sees multiple operational domains: canvas, preview, deploy, lifecycle, settings, AI, collaboration, workspace/project context.

Correctness:

- Route-level gating depends on `useCurrentUser` and stored auth, but backend global auth does not accept httpOnly cookie auth for most routes.
- Settings route correctly reads query workspace id and warns about context mismatch, but backend does not verify the user may edit the workspace.

Consistency:

- Route names are clean, but lifecycle terminology and project/workspace ownership semantics drift from API behavior.

Remediation:

- Add a route access contract: unauthenticated, authenticated, workspace-member, workspace-owner, project-owner, project-included-readonly.
- Implement server-driven nav capabilities per current user/resource.
- Remove or quarantine latent routes from normal compilation and docs.

### 3.3 Authentication and Session

Purpose: Let users authenticate through canonical Java lifecycle auth and use the Node API/web app securely.

Evidence:

- `authRoutes` proxies auth to Java service.
- Login sets httpOnly `accessToken` and `refreshToken` cookies when `@fastify/cookie` is available and returns `{ user }`.
- General `authMiddleware` only reads `Authorization: Bearer ...`; it does not read cookies.
- `/auth/me` uses its own `authenticateToken`, which reads cookies and Authorization.
- Frontend `fetchAuthSession` reads `localStorage` `auth-session` and calls `/api/auth/me` with Bearer.
- `SessionManager` claims "httpOnly cookie -> localStorage -> legacy" but attempts to read `access_token` from `document.cookie`, while backend sets `accessToken`; httpOnly cookies are not readable by JS anyway.
- Cookie secret defaults to `change-me-in-production`.

Completeness:

- Login/refresh/logout/me routes exist.
- Missing: one canonical session model, CSRF strategy, cookie-vs-bearer contract, token rotation guarantees, refresh secret startup enforcement, and cross-service auth propagation tests.

Simplicity:

- Users should not need to understand cookies vs localStorage vs Bearer; the system currently does.

Correctness:

- Cookie-based login can succeed but leave most API routes unauthenticated because global auth middleware ignores cookies.
- Frontend may store no access token when login returned only user because cookie mode is active.
- SessionManager cannot read httpOnly cookies and uses a different cookie name from backend.

Consistency:

- Java auth, Node auth middleware, route-specific auth, frontend SessionManager, and generated auth contracts disagree.

Remediation:

- Pick one production auth path: preferably httpOnly cookies plus CSRF protection for browser routes, and service tokens for server-to-server only.
- Update global `authMiddleware` to validate accessToken cookies or require all frontend clients to use Bearer returned from login, not both.
- Fail startup without `COOKIE_SECRET`, `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, and `JAVA_BACKEND_API_KEY` in non-dev.
- Delete legacy `auth_token` fallback in production builds.

### 3.4 Workspace and Project Management

Purpose: Let users create, view, update, delete, and include projects in workspaces.

Evidence:

- Prisma models contain `Workspace`, `WorkspaceMember`, `Project`, and `WorkspaceProject`.
- `WorkspaceProject` comments say included projects are read-only.
- API routes use `requirePermission(...)` global RBAC but route handlers often do not verify workspace membership or ownership.

Completeness:

- CRUD surfaces exist.
- Missing: enforced owner/member checks, included-project read-only behavior, invitation/member management UX, transfer ownership, project archival, default workspace safeguards, deletion dependency preview, and audit history visible to admins/users.

Simplicity:

- Ownership/inclusion is conceptually useful but currently invisible and under-enforced.

Correctness:

- Any authenticated user with a global role permission can fetch, update, or delete arbitrary workspace/project ids in several routes if they know ids.
- Project create checks workspace existence but not membership.
- Project include can include arbitrary projects into arbitrary workspaces without proving access.

Consistency:

- Schema says included projects are read-only; route code does not consistently enforce included vs owned access.

Remediation:

- Add centralized resource authorization helpers: `requireWorkspaceMember`, `requireWorkspaceOwner`, `requireProjectReadable`, `requireProjectWritable`, `requireProjectOwnerWorkspace`.
- Make all workspace/project/canvas/lifecycle queries include the authorized workspace scope.
- Add negative authorization tests for every route.
- Surface ownership/read-only status in the UI.

### 3.5 Canvas and Collaboration

Purpose: Let users model products/systems visually, persist canvas data, collaborate, version, restore, validate, and use AI support.

Evidence:

- `CanvasDocument.content` schema comment says `{ nodes: CanvasNode[], edges: CanvasEdge[], viewport: CanvasViewport }`.
- REST canvas endpoints load/save a unified canvas, create `CanvasVersion`, and expose restore/version list.
- `POST /canvas/validate` always returns `valid: true`, `score: 100`.
- `RealTimeService` registers WebSocket routes before auth middleware.
- WebSocket `join` accepts `userId`, `userName`, `userEmail` from the client and does not require prior authenticated state.
- Realtime `persistNodeUpdate` treats `content.nodes` as a record keyed by node id, not an array.
- Frontend `canvasBackend` falls back to localStorage on backend failure and writes local storage on failed save.

Completeness:

- UI and API have substantial canvas machinery.
- Missing: real structural validation, membership checks, conflict detection, optimistic version checks, CRDT persistence, collaboration auth, offline reconciliation, durable save failure user recovery, and audit history for realtime changes.

Simplicity:

- Canvas product surface should feel like "saved, synced, validated, shared". Current implementation exposes hidden complexity through local fallbacks and multiple persistence paths.

Correctness:

- Validation is false confidence.
- Realtime identity can be spoofed.
- Node updates can corrupt the documented canvas shape.
- Realtime writes bypass version history and conflict semantics.
- Local fallback can show stale or unsynced data as if it were legitimate product state.

Consistency:

- REST canvas uses arrays and version records; realtime uses a record map and fire-and-forget writes.
- Save status UI is more mature than persistence guarantees.

Remediation:

- Require authenticated, project-readable WebSocket connection before join.
- Bind `userId` from verified token only.
- Verify project membership before canvas GET/PUT/version/restore.
- Use one canonical canvas JSON schema and validate on both client and server.
- Add `baseVersion` or CRDT document version to REST and realtime mutations.
- Replace mock validation with structural, lifecycle, security, dependency, and traceability checks.

### 3.6 Lifecycle Hub

Purpose: Guide projects through the YAPPC lifecycle with phase readiness, artifacts, gates, AI recommendations, approvals, evidence, audit, and execution results.

Evidence:

- README target phases: Intent, Shape, Validate, Generate, Run, Observe, Learn, Evolve.
- API lifecycle phases: Intent, Context, Plan, Execute, Verify, Observe, Learn, Institutionalize.
- Legacy mapping exists for Shape/Validate/Generate/Run/Improve.
- `automation/plan` can `oneClickApprove` and mutate `project.lifecyclePhase` if artifact count/readiness passes.
- `calculateTaskRisk` has TODO: replace with real security scan results.
- Lifecycle artifact/item/AI insight/activity log models are project-scoped but not tenant-scoped.
- Lifecycle execution result ingestion is exposed via API and uses only global workflow permission.

Completeness:

- Phase definitions, artifacts, gates, evidence, audit, next tasks, AI recommendations, and execution result persistence exist.
- Missing: canonical phase taxonomy, human approval model, evidence provenance, tenant/project access enforcement, deployment/test/security/observability integration truth, rollback/compensation, and user-visible execution status closure.

Simplicity:

- The UI should guide users with one next action and explain blockers. The backend currently exposes many concepts but lacks a unified source of lifecycle truth.

Correctness:

- "AI one-click approval" can transition state based on deterministic artifact counts and missing real scan/deploy/evidence signals.
- Gate readiness can divide by zero for gates with `minCount` 0, yielding unsafe readiness semantics.
- Lifecycle routes check project existence, not user access.
- Execution result ingestion lacks producer authentication and project membership validation.

Consistency:

- Phase naming drift is systemic.
- Lifecycle data model still stores old phase names (`shapeResult`, `validationResult`, `generationResult`, `runResult`, `evolutionResult`) while active route phases use Context/Plan/Execute/Verify/Institutionalize.

Remediation:

- Freeze one canonical phase enum and provide explicit migration from old names.
- Introduce a lifecycle state machine service with allowed transitions, approval requirements, evidence requirements, and audit emission.
- Require project access for all lifecycle reads/writes.
- Treat AI as decision support by default; require human/governance approval for high-risk transitions.
- Ingest lifecycle execution from Java/AEP via authenticated service identity and idempotency keys.

### 3.7 Preview, Deploy, Run, Observe

Purpose: Let users preview generated product output, deploy/release safely, observe operational health, and learn/evolve.

Evidence:

- Mounted project tabs include preview and deploy.
- API has DevSecOps routes and lifecycle devsecops endpoints, but lifecycle comments still say TODO for real security scan results.
- Health endpoint checks database and Java backend but returns HTTP 200 when Java backend is down.
- Observability utilities include tracing and Prometheus metrics.

Completeness:

- Preview/deploy entry points exist.
- Missing: real release candidate object, deployment pipeline contract, environment inventory, canary status, rollback action, incident closure, SLO evidence, promotion policy, notifications, and user-visible background job progress.

Simplicity:

- Deploy should answer "is it safe to ship?" with one recommended action and exact blockers. Current pieces are spread across lifecycle, devsecops, deploy UI, metrics, and AI services.

Correctness:

- Health/degraded semantics can mislead deploy readiness.
- Security/run/observe data is not yet wired as hard transition evidence.

Consistency:

- DevSecOps and lifecycle gates overlap without a shared policy/evidence model.

Remediation:

- Create `ReleaseCandidate`, `DeploymentRun`, `Environment`, `CheckRun`, and `Incident` product contracts.
- Gate Deploy/Run transitions on actual check and deployment state.
- Show one operations timeline per project: generated, built, tested, released, deployed, observed, incident, learned.

### 3.8 GraphQL and API Contracts

Purpose: Support API access for web app, admin, AI, workflow, rate limit, and other product surfaces.

Evidence:

- Fastify registers every REST route under `/api`, `/v1`, and `/api/v1`.
- Public paths include `/graphql` and `/graphiql`.
- GraphQL Yoga is configured with `graphiql: true`.
- GraphQL context uses optional `req.user`, but auth middleware skips `/graphql`.
- Catch-all Java proxy overwrites Authorization with `JAVA_BACKEND_API_KEY || 'service-to-service-token'`.
- AI rate limiter only matches `/api/v1/ai` and `/api/v1/copilot`, while routes also exist under `/api` and `/v1`.

Completeness:

- REST, GraphQL, generated OpenAPI/client direction exist.
- Missing: authenticated GraphQL policy, one prefix strategy, contract-level auth annotations, idempotency semantics, standard pagination/errors, and generated-client adoption by frontend.

Simplicity:

- Three API prefixes and a Java proxy make client behavior harder to reason about.

Correctness:

- Public GraphQL can expose data or mutations depending on resolver behavior.
- Default service token is unsafe.
- Rate limiting differs by prefix.

Consistency:

- REST route contracts, GraphQL schemas, generated clients, and hand-written frontend services are inconsistent.

Remediation:

- Pick canonical `/api/v1`; keep compatibility only at gateway with explicit deprecation headers.
- Require auth on GraphQL by default and enable GraphiQL only in local dev.
- Remove default service-to-service token fallback.
- Enforce standard error envelope and pagination.
- Make frontend use generated clients for all supported contracts.

### 3.9 Data and Domain Model

Purpose: Persist users, sessions, workspaces, projects, canvas docs, pages, lifecycle artifacts/items/insights/logs, audit logs, and execution results.

Evidence:

- Prisma schema contains rich product models but many are project-scoped, not tenant-scoped.
- `DOMAIN_MODEL_REGISTRY.md` says Java `yappc-domain` is canonical and API duplicates are deprecated.
- Product tree contains multiple Java module families and a Node API under frontend workspace.

Completeness:

- Data models exist for major entities.
- Missing: tenant id on several project-scoped lifecycle rows, row-level access invariant, explicit retention/deletion semantics, immutable audit guarantee, and model ownership clarity.

Simplicity:

- Developers must navigate Prisma models, Java canonical models, generated OpenAPI, GraphQL schemas, and Data Cloud adapter direction.

Correctness:

- Authorization relies on route code rather than schema/repository constraints.
- Lifecycle execution result unique key is global `executionId`; that may be acceptable but producer identity and project binding must be verified.

Consistency:

- Lifecycle phase/result names differ across schema, routes, README, and Java/domain comments.
- Domain model duplication is known but not fully retired.

Remediation:

- Add tenant/workspace scoping to all user/project/lifecycle/audit data where product semantics require isolation.
- Centralize access through repository/service methods that require actor context.
- Align Prisma enum and Java enum with product lifecycle contract.
- Complete domain duplication cleanup and add CI guard against duplicate model reintroduction.

### 3.10 Observability, Audit, and Operability

Purpose: Make product behavior diagnosable for users, admins, and operators.

Evidence:

- OTel tracing and Prometheus metrics are registered.
- `/health` includes database and Java backend checks.
- `/metrics` is public.
- Fastify audit middleware exists separately from an Express-style `AuditLoggingMiddleware`.
- Audit middleware is fire-and-forget and route/resource inference can be prefix-sensitive.
- Health returns 200 for Java backend down.
- OpenAPI test expects `/ready`, but active index evidence did not show `/ready` route in the Fastify bootstrap.

Completeness:

- Baseline telemetry exists.
- Missing: readiness/liveness split, authenticated/scrubbed metrics policy, audit consistency, job execution dashboard, dead letter/retry visibility, support/admin diagnostics, and user-facing operation timelines.

Simplicity:

- Users need contextual status, not raw infrastructure details. Operators need accurate readiness, not a single health endpoint.

Correctness:

- Java backend down should affect readiness and deployability.
- Audit entries can be incomplete or semantically misleading.

Consistency:

- Multiple audit implementations and excluded routes create drift.

Remediation:

- Add `/live`, `/ready`, `/health` with strict semantics.
- Return non-200 readiness when required dependencies are down.
- Centralize audit event creation behind one service.
- Add product events for lifecycle transitions, canvas saves, approvals, deploys, AI actions, and permission denials.

### 3.11 AI/ML and Automation

Purpose: Reduce manual product lifecycle work through recommendations, generation, validation, prioritization, and learning.

Evidence:

- README says AI-native maturity 3/10.
- Frontend and backend contain many AI services, panels, confidence indicators, mock/fallback implementations, prompt registry direction, agent services, and lifecycle AI endpoints.
- Lifecycle automation has deterministic readiness and timing heuristics.
- Search/cost/agent services include mock data or mock implementations in frontend services.

Completeness:

- AI is conceptually pervasive but not yet governed end to end.
- Missing: provenance, evidence, confidence calibration, human review triggers, override model, prompt/model audit, privacy policy, evaluation gates, and feedback loops.

Simplicity:

- AI should quietly fill gaps, prioritize work, and explain blockers. It currently appears in many panels/actions and can feel like a feature layer rather than product intelligence.

Correctness:

- Mock/deterministic outputs can look like AI truth.
- One-click lifecycle approval is too powerful without evidence-backed confidence and governance.

Consistency:

- Confidence/evidence UI exists in some places but not as a cross-product pattern.

Remediation:

- Define an `AIAction` contract: input, model/prompt version, output, confidence, evidence ids, user decision, override, audit id.
- Keep AI suggestions assistive unless confidence/evidence thresholds and governance policy allow automation.
- Add evaluation tests for lifecycle planning, artifact summarization, validation, risk scoring, and next-best-action.

### 3.12 Security, Privacy, and Trust

Purpose: Protect tenant data, sensitive operations, secrets, AI context, and user trust.

Critical trust gaps:

- Resource authorization missing across many route families.
- GraphQL and GraphiQL are public.
- Metrics are public.
- WebSocket identity is client-supplied.
- Service-to-service proxy has default token.
- Auth cookies/localStorage/Bearer handling conflicts.
- AI/canvas data can be stored in localStorage fallbacks.
- Cookie secret has an insecure default.

Remediation:

- Treat resource authorization as a P0.
- Add threat model for browser/API/Java/Data Cloud/AI provider boundaries.
- Encrypt or remove sensitive local persistence.
- Add data retention/deletion semantics for AI inputs, generated artifacts, canvas state, and audit logs.
- Add user/admin-visible access logs for sensitive actions.

## 4. Complete End-to-End Flow Review

### Flow 1: Login and Authenticated App Access

User goal: Log in and access workspaces/projects.

Entry point: `/login`.

Screen flow: login route -> auth service -> shell/dashboard/workspaces.

Frontend state: `AuthProvider` calls `fetchAuthSession`; `fetchAuthSession` reads local `auth-session` and calls `/api/auth/me` with Bearer.

API contracts: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/me`.

Backend orchestration: Node auth routes proxy to Java auth. General auth middleware validates Bearer only.

Persistence: Java auth owns canonical user/token validation; Node stores none directly for auth except cookies in response.

Async/integration: Java auth dependency.

Audit/history: not consistently proven for login/session events.

Failure/recovery: login can set cookies but frontend/global API path expects Bearer; refresh returns token in some cases.

Privacy/security: localStorage token fallback remains; httpOnly cookie model is not actually used by general API.

Completeness gaps: canonical session contract, CSRF, refresh secret enforcement, auth e2e across UI -> Node -> Java.

Ideal future journey: Login sets secure httpOnly cookies; global middleware validates cookies; frontend uses `credentials: include`; `/me` and all APIs work without local tokens; session state is consistent and auditable.

### Flow 2: Onboarding and Persona Setup

User goal: Complete first-time setup, personas, workspace/project defaults.

Entry point: `/onboarding`.

Frontend state: `OnboardingStatusService` tries server first and falls back to localStorage on 404/501/failure.

API contracts: `/api/onboarding/status` expected by frontend but not verified in inspected Fastify route registrations.

Backend/data: workspace/project create routes exist, but onboarding status persistence contract is not clearly canonical.

Failure/recovery: localStorage fallback makes UX resilient but can mark onboarding complete without server truth.

Completeness gaps: server-backed onboarding contract, cross-device state, audit trail, admin visibility, persona-to-workflow effect.

Ideal future journey: Server owns onboarding state; local cache is clearly "offline pending"; setup creates workspace/project/persona with one transaction and visible follow-up.

### Flow 3: Workspace Settings Update/Delete

User goal: Edit or delete workspace identity.

Entry point: `/settings?workspaceId=...`.

Frontend state: Settings route fetches `/api/workspaces/:workspaceId`, PATCHes and DELETEs the same id, shows save sync badge and context mismatch alert.

API/backend: workspace routes use global `requirePermission` for update/delete but need resource membership/ownership checks.

Persistence: Prisma workspace updated/deleted.

Audit/history: audit middleware should log, but user-visible audit history is not part of the flow.

Failure/recovery: error messages exist; deletion redirects to `/`.

Correctness gap: user can target a different workspace id if global role allows it.

Ideal future journey: UI receives capabilities from server, warns for destructive impact, requires workspace-owner, shows affected projects, logs immutable audit event, and redirects to safe workspace.

### Flow 4: Project CRUD and Inclusion

User goal: Create/manage projects and include read-only projects in other workspaces.

Entry point: `/projects`, `/workspaces`, project shell.

Frontend state: project/workspace routes call REST APIs.

API/backend: project routes check workspace/project existence but often not actor membership.

Persistence: `Project.ownerWorkspaceId`, `WorkspaceProject`.

Async/integration: AI suggestions for names/setup exist.

Correctness gap: included read-only semantics are defined in schema but not consistently enforced in route mutations.

Ideal future journey: Server returns project capabilities (`read`, `comment`, `edit`, `delete`, `include`); included projects are visibly read-only and impossible to mutate through API.

### Flow 5: Canvas Load/Edit/Save/Restore

User goal: Build a project model and trust it is saved.

Entry point: `/p/:projectId/canvas`.

Frontend state: canvas services attempt backend, fall back to localStorage, debounce saves, show status.

API/backend: GET/PUT `/api/projects/:projectId/canvas`, version list, restore, validate.

Persistence: `CanvasDocument`, `CanvasVersion`.

Async/realtime: WebSocket collaboration and Redis room state.

Correctness gaps: no membership checks, mock validation, local fallback can hide save failure, realtime shape drift, no conflict/version guard.

Ideal future journey: Canvas opens only for project members; save returns authoritative version; offline edits are marked pending; validation is real; restore is audited; collaboration joins through authenticated membership.

### Flow 6: Realtime Collaboration

User goal: Collaborate on canvas with accurate user presence and shared changes.

Entry point: WebSocket `/ws/canvas/:projectId` or `/canvas/:projectId`.

Frontend state: collaboration client sends auth/join/update events.

Backend: route registered before auth middleware; `join` trusts client-supplied user id/email/name.

Persistence: node updates are persisted asynchronously by patching latest canvas document.

Failure/recovery: fire-and-forget errors log to console; user does not get save failure.

Security gap: user impersonation and cross-project join are possible.

Ideal future journey: WebSocket handshake authenticates token, server derives actor, verifies project access, applies CRDT/versioned changes, emits save ack/failure, and records audit/version entries.

### Flow 7: Lifecycle Readiness and Transition

User goal: Understand phase readiness and safely advance lifecycle.

Entry point: `/p/:projectId/lifecycle`.

API: `/lifecycle/projects/:id/current`, `/phases/:phase/next`, `/projects/:projectId/automation/plan`, `/stages/transition`.

Backend: deterministic artifact counting, readiness heuristics, activity log entries.

Persistence: `Project.lifecyclePhase`, `LifecycleArtifact`, `LifecycleActivityLog`.

Correctness gaps: phase taxonomy drift, no resource auth, one-click AI approval can mutate state without real evidence policy, risk scoring TODO.

Ideal future journey: Lifecycle service computes readiness from artifacts, tests, security scans, deploy state, incidents, and approvals; AI explains recommendations; high-risk changes require human approval; every transition has evidence and audit.

### Flow 8: Lifecycle Execution Result Ingestion

User goal: See execution results from Java/AEP lifecycle automation.

Entry point: Lifecycle UI and execution history.

API: `/lifecycle-execution/results`, `/projects/:projectId/results`, `/results/:executionId`.

Backend: upsert by `executionId`.

Persistence: `LifecycleExecutionResult`.

Security/correctness gap: ingestion route uses normal workflow permission, not service identity; retrieval lacks project access check; phase result names use old taxonomy.

Ideal future journey: Java/AEP posts signed service events with idempotency keys; Node verifies project binding and records status; UI shows live progress, evidence, failures, retries, and fallbacks.

### Flow 9: Preview/Deploy/Observe

User goal: Preview, release, deploy, and monitor product output.

Entry point: `/p/:projectId/preview`, `/deploy`, lifecycle Run/Observe.

API/backend: DevSecOps/lifecycle endpoints exist but real security/deploy evidence is incomplete.

Correctness gaps: no single release candidate state, no end-to-end deployment transaction, health readiness weak, deployment rollback/incident closure not clearly complete.

Ideal future journey: Generated artifact -> preview -> release candidate -> checks -> approval -> deploy -> observe -> learn loop is one timeline with automated evidence and clear blockers.

### Flow 10: AI Assistance and Learning

User goal: Let YAPPC reduce manual work without hiding risk.

Entry point: AI panels, command actions, lifecycle recommendations, setup suggestions.

Backend/frontend: many AI services exist; mock/fallback paths remain.

Correctness gaps: confidence/evidence/governance are not consistently enforced; AI can be visible without reliable provenance.

Ideal future journey: AI silently pre-fills, ranks, summarizes, validates, and detects gaps; every material suggestion has evidence, confidence, source, and override/audit behavior.

## 5. Comprehensive Findings Catalog

| ID | Title | Severity | Category | Layers | Evidence | Impact | Recommended fix |
|---|---|---:|---|---|---|---|---|
| YAPPC-001 | Resource authorization is missing behind global RBAC | Critical | Security/correctness | API, backend, data | Workspace/project/canvas/lifecycle routes use role permissions but often do not verify membership/ownership | Cross-tenant data exposure and mutation | Add centralized resource auth guards and negative tests for every route |
| YAPPC-002 | Cookie auth and Bearer auth contracts conflict | Critical | Auth/correctness | Frontend, API, Java auth | Login returns cookies/user; global middleware reads Bearer only; frontend reads localStorage | Users can log in but fail authenticated API calls; insecure token fallback persists | Pick one browser auth model and update middleware/clients |
| YAPPC-003 | Public GraphQL and GraphiQL | Critical | Security/API | API, GraphQL | `/graphql` is public path; Yoga `graphiql: true` | Data/mutation exposure risk | Require auth by default; enable GraphiQL only in dev/admin |
| YAPPC-004 | Realtime collaboration trusts client-supplied identity | Critical | Security/realtime | WebSocket, canvas | `join` message contains user id/name/email and no required auth | User impersonation and unauthorized room joins | Authenticate handshake and derive identity server-side |
| YAPPC-005 | Canvas validation always returns valid score 100 | High | Correctness/trust | API, UX | `/canvas/validate` mock response | False safety signal | Implement real validation and show "not validated" until true |
| YAPPC-006 | Realtime canvas writes can corrupt canonical canvas shape | High | Data/correctness | WebSocket, DB | Prisma says nodes array; realtime persists nodes as record map | Canvas state drift/corruption | Canonical schema validator and one mutation path |
| YAPPC-007 | Realtime changes bypass version history | High | Completeness/audit | WebSocket, DB, UX | REST creates `CanvasVersion`; realtime only updates `CanvasDocument` | Restore/audit misses collaborative changes | Persist realtime ops into CRDT/version/event log |
| YAPPC-008 | Lifecycle phase taxonomy drift | High | Consistency/product | Docs, API, DB | README target vs route enum vs execution result fields | User/developer confusion, migration risk | Freeze canonical lifecycle enum and migrate labels/data |
| YAPPC-009 | AI one-click approval mutates lifecycle without governed evidence | High | AI/governance | API, DB, UX | `automation/plan` updates `Project.lifecyclePhase` on artifact count readiness | Unsafe automation and trust loss | Make AI assistive; require evidence thresholds and approvals |
| YAPPC-010 | Lifecycle execution ingestion lacks service producer authentication | High | Security/integration | API, Java/AEP, DB | `/lifecycle-execution/results` uses user workflow permission | Forged or cross-project execution results | Use signed service tokens, idempotency, project binding |
| YAPPC-011 | Java backend down still returns API health HTTP 200 | High | Operability | API, deployment | `/health` returns 200 unless DB down | Orchestrators/deploys can treat degraded product as ready | Add `/ready`; return non-200 for required dependency failure |
| YAPPC-012 | Default service-to-service token fallback | Critical | Security/integration | API proxy | Proxy uses `JAVA_BACKEND_API_KEY || 'service-to-service-token'` | Accidental shared credential and bypass risk | Require configured secret outside local dev |
| YAPPC-013 | API prefix sprawl changes behavior | Medium | API consistency | REST, rate limit, audit | Routes registered at `/api`, `/v1`, `/api/v1`; AI limiter only matches `/api/v1` | Inconsistent rate/auth/audit/client semantics | Canonicalize `/api/v1`; deprecate aliases |
| YAPPC-014 | Metrics endpoint is public | Medium | Security/o11y | API, ops | `/metrics` is public path | Possible topology/cardinality leakage | Restrict by network/auth or scrub carefully |
| YAPPC-015 | Audit is incomplete and semantically fragile | High | Trust/o11y | API, DB | fire-and-forget audit, prefix-sensitive resource inference, multiple audit implementations | Support/compliance cannot reconstruct truth | Central audit service with typed events |
| YAPPC-016 | Tracker says done while verification remains unchecked | Medium | Quality/process | Docs/tests | Tracker done list conflicts with unchecked verification and current code | False confidence | Require test/manual evidence before done |
| YAPPC-017 | Current worktree shows broad e2e spec deletions | High | Quality | Tests | `git status` shows many `frontend/e2e/*.spec.ts` deleted | Regression net weakened unless replaced | Restore or replace with current-release coverage |
| YAPPC-018 | Node API lives under frontend workspace | Medium | Architecture | Repo/package | `@yappc/api-app` description says historical location | Ownership/deploy confusion | Move API to backend/service package or document boundary |
| YAPPC-019 | Data Cloud canonical direction is not consistently active | Medium | Architecture/data | Docs, Java, Node API | Docs say Data Cloud canonical; active Node API uses direct Prisma | Split persistence truth | Define which data belongs to Prisma vs Data Cloud |
| YAPPC-020 | Domain model duplication remains | Medium | Consistency/data | `DOMAIN_MODEL_REGISTRY.md`, Java packages | Canonical Java model and deprecated API duplicate overlap | Drift and inconsistent business rules | Finish deprecation and add guardrails |
| YAPPC-021 | LocalStorage remains for high-sensitivity auth/session | High | Privacy/security | Frontend | `auth-session` target backend remains localStorage; legacy token fallback | XSS/token theft risk | Remove local token persistence for production |
| YAPPC-022 | Onboarding can complete locally without server truth | Medium | Correctness/completeness | Frontend, API | localStorage fallback for onboarding status | Cross-device inconsistency and silent setup failure | Server-owned onboarding with offline pending status |
| YAPPC-023 | Canvas local fallback hides failed saves | High | Correctness/UX | Frontend, API | `canvasBackend` writes localStorage on failed backend save | User believes work is safe remotely | Explicit pending/offline state and reconciliation |
| YAPPC-024 | Canvas API client is `@ts-nocheck` and hand-coded | Medium | Frontend/API | Frontend | `CanvasAPIClient.ts` uses `@ts-nocheck` and manual paths | Contract drift and runtime errors | Generate typed client from OpenAPI |
| YAPPC-025 | API key can be read from localStorage | High | Security | Frontend | `CanvasAPIClient.getApiKey` reads `localStorage.api_key` | Credential exposure | Remove browser-stored API keys |
| YAPPC-026 | Workspace/project delete lacks operational closure | Medium | UX/backend | UI, API, DB | Settings delete redirects without dependency/audit/status flow | Accidental data loss/support gaps | Add deletion preview, confirm, audit, undo/archive where possible |
| YAPPC-027 | Included project read-only semantics not enforced | High | Correctness/security | API, DB, UI | Schema comment says read-only; routes allow updates by workspace id | Unauthorized mutation | Capability-based access per project/workspace relation |
| YAPPC-028 | Lifecycle gate readiness uses heuristic counts | Medium | Correctness | API, lifecycle | artifact count readiness and TODO risk scan | Misleading readiness | Evidence-backed gate evaluators |
| YAPPC-029 | AI confidence/evidence pattern is not universal | Medium | AI/trust | Frontend, API | confidence badges exist in some UI; mock/fallback elsewhere | Inconsistent trust | Shared AI evidence component and backend contract |
| YAPPC-030 | Mock AI/services remain reachable in product code paths | Medium | Correctness/AI | Frontend/backend | mock implementations in search, agents, cost, collaboration | Users may see synthetic truth | Environment gate mocks and label demo data |
| YAPPC-031 | CORS default conflicts with docs | Low | DevEx/operability | API/docs | CORS default 5173; docs mention 7001 | Local setup failures | Align defaults and docs |
| YAPPC-032 | Refresh secret may fail at runtime instead of startup | Medium | Auth/operability | API | startup checks access secret only; refresh config is separate | Runtime auth failure | Validate all required auth secrets at startup |
| YAPPC-033 | Dev auth bypass registration order is questionable | Medium | Auth/dev | API | auth middleware registered before dev bypass | Dev bypass may not populate before auth | Put dev bypass before auth or integrate deliberately |
| YAPPC-034 | Notification WebSocket lacks auth/subscription model | High | Security/realtime | WebSocket | `/ws/notifications` registers before auth and accepts ping only | Unauthorized event stream risk | Authenticated subscriptions with scoped channels |
| YAPPC-035 | Lifecycle artifacts can be created with empty project id | High | Data/correctness | API, DB | artifact create defaults `projectId` to empty string | Bad records/500s/data integrity | Validate body and project access |
| YAPPC-036 | Project health score is labeled AI but computed rule-based | Medium | Trust/AI | API, UX | `aiHealthScore` exists; project route computes rule-based score | Misleading AI promise | Label as health score or add AI provenance |
| YAPPC-037 | Readiness and OpenAPI expectations drift | Medium | API/quality | API/tests | OpenAPI test expects `/ready`; bootstrap evidence shows `/health` only | Contract mismatch | Implement/readiness route or update contract |
| YAPPC-038 | Lifecycle result phase fields use old taxonomy | Medium | Data/consistency | Prisma/API | shape/validation/generation/run/evolution result fields | Migration/reporting drift | Version result schema by canonical phases |
| YAPPC-039 | Frontend no-op lifecycle saves can look successful | High | UX/correctness | Frontend | `CanvasRightPanelHost` passes no-op saves when callbacks absent | Data loss and misleading save state | Disable panels/actions unless persistence handler exists |
| YAPPC-040 | Native alert still appears in canvas governance component | Low | UX/accessibility | Frontend | `GovernancePanel.tsx` uses `alert(...)` per search evidence | Inconsistent/poor accessible feedback | Replace with toast/dialog/status pattern |

## 6. Completeness Gap Inventory

Missing screens/surfaces:

- Resource-specific permission denied pages.
- Workspace member/invite/role management.
- Project ownership/inclusion/read-only management.
- Release candidate and deployment run detail.
- Background job/execution progress detail.
- Admin support console for user/workspace/project incidents.
- AI decision audit/history screen.
- Data retention/privacy settings.

Missing states:

- Authenticated by cookie but API unauthorized.
- Offline pending save vs remote saved.
- Canvas validation unavailable/incomplete.
- Lifecycle transition awaiting approval.
- Java backend degraded/unavailable as readiness failure.
- AI fallback/mock output.
- Included project read-only state.
- Service execution retry/dead-letter state.

Missing validations:

- Workspace membership before workspace/project/canvas/lifecycle reads and writes.
- Canvas schema validation.
- Lifecycle artifact body validation.
- Lifecycle transition evidence validation.
- Service-to-service execution result signature validation.
- CSRF validation for cookie mode.
- API key/secrets presence in production.

Missing backend/API support:

- Canonical resource auth middleware.
- `/ready` readiness contract.
- Authenticated GraphQL policy.
- Canonical onboarding status endpoint.
- Release/deploy state machine.
- AI action/evidence contract.
- Idempotency keys for mutations and async ingestion.

Missing persistence logic:

- Tenant/workspace scoping on lifecycle/audit rows where required.
- Versioned lifecycle transitions.
- Realtime canvas operation log.
- Offline save reconciliation.
- Immutable audit event store semantics.

Missing audit/history/notification behavior:

- Login/logout/session events.
- Permission denial events.
- Canvas validation/save/restore/realtime edits.
- Lifecycle AI suggestion/approval/override.
- Deployment promotion/rollback.
- Background execution failure/retry.

Missing admin/governance flows:

- AI model/prompt/version governance.
- Workspace/project access review.
- Service token rotation.
- Audit export/search.
- Incident/support diagnostics.

Missing accessibility/responsive proof:

- Current mounted route keyboard/screen-reader e2e after broad e2e churn.
- Canvas keyboard alternatives for core operations.
- Modal/dialog/toast consistency replacing native alerts.

Missing end-to-end closure:

- Intent -> canvas -> validation -> lifecycle artifacts -> generation -> deploy -> observe -> learn is not one coherent verified workflow yet.

## 7. Simplification Plan

Remove:

- Public GraphiQL in non-dev.
- Production localStorage token/API key fallback.
- Mock validation responses from product endpoints.
- Default service-to-service token.
- Duplicate lifecycle phase labels once migration is complete.
- Latent/deprecated routes from product-facing inventories.

Merge:

- Three REST prefixes into one canonical API path.
- Fastify/Express-style audit patterns into one audit service.
- Canvas REST/realtime persistence into one operation/version model.
- Lifecycle phase/readiness/gate logic into one state machine.
- AI confidence/evidence display into one shared pattern.

Automate:

- Workspace/project setup defaults.
- Lifecycle artifact extraction from canvas/docs/code/tests.
- Readiness evidence linking.
- Release risk scoring from actual scans, test results, deploy history, and observability.
- Permission capability payload generation for UI.
- Audit event creation for sensitive actions.

Infer:

- User identity from verified token, never from WebSocket messages.
- Project access from workspace membership/project inclusion.
- Lifecycle current phase from authoritative project state only.
- Save/sync state from server acknowledgements.

Hide by default:

- Internal ids, owner ids, proxy/service topology, raw metrics, and advanced lifecycle artifacts unless needed.
- AI model/provider details from ordinary users while preserving admin audit.

Prefetch/prefill:

- Current workspace/project capabilities.
- Next lifecycle blockers and recommended action.
- Setup workspace/project names and personas.
- Canvas validation summary after save.

Move to admin/advanced:

- API prefix/version controls.
- Raw execution result JSON.
- Prompt/model governance.
- Service health and dependency checks.

Contain technical complexity:

- Browser should not know API keys.
- Users should not choose between API prefixes.
- Users should not see mock/demo data as product output.
- Users should not need to resolve local-vs-server save truth.

## 8. Correctness Review Register

- Misleading UI state: login can appear successful while API calls fail because cookie and Bearer contracts conflict.
- Misleading UI state: canvas can show local fallback data after backend save failure.
- Misleading UI state: canvas validation returns perfect score regardless of content.
- Incorrect workflow logic: global RBAC is used where resource membership is required.
- Incorrect workflow logic: included read-only projects can be mutated through APIs.
- Incorrect workflow logic: lifecycle one-click approval can mutate phase state without governed evidence.
- Incorrect validation: lifecycle artifact create defaults missing fields instead of rejecting.
- Incorrect API semantics: public GraphQL gets optional user context rather than required auth.
- Incorrect API semantics: same route under three prefixes can receive different rate-limit treatment.
- Incorrect backend logic: health returns 200 with required Java backend down.
- Incorrect data semantics: realtime node updates use object map for `nodes` while schema documents array.
- Incorrect async/retry behavior: realtime persistence errors are not surfaced to users.
- Incorrect automation behavior: AI/deterministic readiness is presented as approval intelligence.
- Incorrect permission behavior: workspace/project/canvas/lifecycle routes can leak data by id.
- Incorrect security behavior: WebSocket joins trust user identity from client payload.

## 9. Consistency Review Register

- Terminology drift: Shape/Validate/Generate/Run/Evolve vs Context/Plan/Execute/Verify/Institutionalize.
- State drift: lifecycle execution result fields preserve old phase names.
- Component drift: MUI still exists in dependencies and some component paths while product theme/design system is separate.
- Workflow drift: REST canvas writes create versions; realtime writes do not.
- API pattern drift: `/api`, `/v1`, `/api/v1`.
- Auth drift: Java auth proxy, Node JWT middleware, route-local auth, frontend local session.
- Validation drift: canvas validation mock vs lifecycle artifact gates vs DevSecOps TODOs.
- Permission drift: global role checks vs schema comments about workspace/project ownership.
- Messaging drift: AI confidence in some panels but not all AI outputs.
- Audit drift: Fastify audit middleware, Express audit middleware, lifecycle activity logs.
- Data semantic drift: Prisma model comments vs runtime JSON patching.
- Doc drift: README architecture and ports vs active app/API defaults.
- Tracker drift: completed labels vs unchecked verification and current evidence.

## 10. API / Backend / Data Review

Contract quality:

- Broad coverage but inconsistent auth, prefixing, errors, and generated-client usage.
- GraphQL needs auth and resolver-level permission contracts.
- REST endpoints need standard envelopes, pagination, filtering, idempotency, and capabilities.

Workflow support quality:

- Workspace/project/canvas/lifecycle workflows are represented but not safe end to end.
- Deploy/run/observe are still incomplete product workflows.

Business logic soundness:

- Resource ownership is the most important missing invariant.
- Lifecycle readiness uses deterministic placeholders where real evidence is required.

Data model alignment:

- Core entities exist.
- Tenant/resource scoping and canonical lifecycle naming need correction.
- Domain duplication with Java packages must be closed.

State machine quality:

- Lifecycle has stage ordering and gates but not a single authoritative state machine with transition policies.
- Deploy and execution states need first-class state machines.

Async/event handling:

- Realtime and lifecycle execution exist but need authentication, idempotency, user-visible progress, retry/dead-letter handling, and audit.

Integration reliability:

- Java backend dependency is checked but degraded health semantics are weak.
- Java proxy credential fallback is unsafe.
- Data Cloud relationship is not fully consistent in active Node paths.

## 11. Comprehensive AI/ML Embedding Plan

| Opportunity | Function | Mode | Confidence/governance | Priority |
|---|---|---|---|---:|
| Onboarding setup assistant | Prefill workspace, project, personas, first lifecycle plan | Assist | User confirms; audit setup | High |
| Canvas gap detector | Find missing actors, data flows, dependencies, risks | Assist | Evidence from canvas schema; no auto-mutation without confirm | High |
| Lifecycle readiness synthesizer | Combine artifacts, tests, scans, deploy, observability into next action | Assist/limited automation | Auto only when policy allows and evidence threshold met | Critical |
| Test-gap analyzer | Map requirements/canvas/code to missing tests | Assist | Link evidence and generated test proposals | High |
| Security/risk detector | Threat model, secret exposure, dependency risk, deployment risk | Assist/blocking for high severity | Human review for high/critical | Critical |
| Release risk assessor | Predict deploy risk from checks, incidents, diff size, coverage | Assist | Explain top drivers | High |
| Evidence linker | Auto-link artifacts to lifecycle gates | Automation with review | Show provenance | High |
| Incident summarizer | Summarize deploy incidents and suggested learnings | Assist | Human confirms learning item | Medium |
| Duplicate artifact detector | Merge duplicate requirements/tasks/artifacts | Assist | User approves merge | Medium |
| Next-best-action | Rank the one next task per role/project | Assist | Confidence and rationale visible | High |
| Prompt/model governance | Track prompt version, model, eval score, outcome | Governance | Admin visible | High |
| Privacy redaction | Remove secrets/PII before AI provider calls | Automation | Always-on with audit | Critical |

AI contract requirements:

- Every AI output must include action id, model/provider, prompt version, input artifact ids, output, confidence, evidence ids, fallback status, user decision, and audit id.
- Material state changes require policy: confidence threshold, evidence threshold, role threshold, and override trail.
- Mock/demo AI must be impossible to confuse with production AI.

## 12. Trust / Privacy / Security / Observability Review

User-facing visibility:

- Show current auth/session state only as needed.
- Show project access/read-only status.
- Show save truth: local pending, syncing, remote saved, failed, conflict.
- Show lifecycle readiness with evidence and blockers.
- Show AI suggestions with why/evidence/confidence.

Operational transparency:

- Project timeline should combine canvas saves, lifecycle artifacts, AI actions, transitions, deploys, incidents, and learnings.
- Operators need dependency health, queue/job status, failed webhooks, Java/AEP call status, and retry state.

Auditability:

- Immutable audit for auth, access denial, workspace/project mutation, canvas validation/save/restore, lifecycle approval, AI action, deploy/rollback.
- Audit entries need tenant, workspace, project, actor, actor role, source, correlation id, before/after where appropriate.

Permission clarity:

- Server returns capability object per resource.
- UI hides unavailable actions and explains forbidden actions.

Sensitive action handling:

- Delete, force transition, one-click approval, deploy, rollback, service token changes require confirmation and audit.

Privacy controls:

- Retention/deletion for canvas, generated artifacts, AI prompts, logs, and audit.
- Redaction before AI calls.
- No browser localStorage for credentials or secrets.

Diagnosability:

- `/live`, `/ready`, `/health`, `/metrics` with clear policies.
- Correlation id propagates Node -> Java -> AEP/Data Cloud.
- User-visible error ids map to logs/traces.

## 13. Design System / Reuse / Abstraction Review

Frontend reuse opportunities:

- One route guard/capability provider.
- One save/sync status pattern.
- One error/empty/loading/skeleton pattern per resource type.
- One AI evidence/confidence card.
- One destructive action confirmation component.
- One permission denied component.
- One generated API client layer.

API standardization opportunities:

- Canonical `/api/v1`.
- Standard auth/capability middleware.
- Standard error envelope: code, message, correlationId, details, fieldErrors.
- Standard pagination and filtering.
- Idempotency headers for mutations.
- Standard audit emission API.

Backend abstraction opportunities:

- Resource authorization service.
- Lifecycle state machine service.
- Canvas document operation service.
- AI action/evidence service.
- Deployment/release state service.
- Service-to-service authentication module.

State/status standardization:

- Lifecycle phase enum.
- Canvas sync status.
- Execution status.
- Deployment status.
- AI action status.
- Approval status.

## 14. Prioritized Remediation Roadmap

### Immediate

| Item | Priority | Effort | Impact | Owner | Dependencies |
|---|---:|---:|---:|---|---|
| Add resource authorization guards across workspace/project/canvas/lifecycle | P0 | L | Critical | Backend/security | Membership model |
| Require auth for GraphQL/GraphiQL and WebSockets | P0 | M | Critical | Backend/security | Auth contract |
| Fix browser auth contract: cookie or Bearer, not both | P0 | M | Critical | Frontend/backend/auth | Java auth proxy |
| Remove default service token and insecure cookie default in production | P0 | S | Critical | Backend/platform | Env config |
| Replace mock canvas validation with "validation unavailable" until real validator ships | P0 | S | High | Backend/product | UI messaging |
| Add `/ready` with strict dependency semantics | P0 | S | High | Platform | Health policy |

### Short Term

| Item | Priority | Effort | Impact | Owner | Dependencies |
|---|---:|---:|---:|---|---|
| Canonicalize lifecycle phase taxonomy | P1 | M | High | Product/backend/data | Migration plan |
| Implement project capability payloads for UI | P1 | M | High | Backend/frontend | Resource guards |
| Unify canvas schema and add server validation | P1 | M | High | Frontend/backend | Canvas contract |
| Add lifecycle state machine and transition policy | P1 | L | High | Backend/product | Phase taxonomy |
| Restore/replace current e2e coverage | P1 | M | High | QE/frontend | Auth/dev server |
| Standardize API error/pagination/idempotency | P1 | M | Medium | Backend/platform | API contract |

### Medium Term

| Item | Priority | Effort | Impact | Owner | Dependencies |
|---|---:|---:|---:|---|---|
| Build AI action/evidence/provenance model | P2 | L | High | ML/backend/frontend | Audit service |
| Move or clearly own Node API service outside frontend workspace | P2 | M | Medium | Platform | Deployment plan |
| Reconcile Prisma/Data Cloud/Java domain ownership | P2 | L | High | Data/platform | Domain registry |
| Implement release candidate/deploy/observe state models | P2 | L | High | Backend/platform/product | Lifecycle state machine |
| Build admin/support diagnostics | P2 | M | Medium | Platform/support | Audit/o11y |

### Long Term

| Item | Priority | Effort | Impact | Owner | Dependencies |
|---|---:|---:|---:|---|---|
| Full Intent -> Evolve closed loop with evidence and learning | P3 | XL | Strategic | Product/all | Prior phases |
| AI-driven lifecycle optimization with governance | P3 | XL | Strategic | ML/product/security | AI evidence/evals |
| Data Cloud canonical persistence migration where appropriate | P3 | XL | Strategic | Data/platform | Ownership decision |
| Plugin/agent ecosystem hardening | P3 | L | Strategic | Platform/security | Sandbox/audit |

## 15. Final Ideal Product Experience Vision

After remediation, YAPPC should feel like a calm operating system for product creation. A user signs in once, sees the exact workspaces and projects they are allowed to use, opens a project, and immediately understands the next best action.

The canvas becomes the shared product truth. It saves remotely with clear status, validates against real structural and lifecycle rules, supports authenticated collaboration, and turns product intent into traceable artifacts. Users never wonder whether work is local, remote, stale, or lost.

The lifecycle hub becomes the product's spine. It uses one canonical phase language, one state machine, and one evidence model. AI summarizes, pre-fills, detects gaps, links evidence, and recommends next actions. It stays mostly invisible until it has something useful to say. When it recommends or performs a material action, the product shows confidence, evidence, and audit context.

Deploy and observe become natural continuations of the lifecycle, not separate dashboards. Release readiness is grounded in actual tests, scans, deploy state, incidents, and SLOs. Failures are understandable, recoverable, and visible to the right role.

Privacy, security, and observability are built in. Access is enforced by resource membership, sensitive data does not sit in browser storage, AI inputs are governed, audit trails are immutable, and support teams can diagnose issues through correlation ids and product timelines.

The full stack coheres around a few shared contracts: authenticated actor, resource capability, lifecycle phase, canvas document version, AI action evidence, deployment state, and audit event.

## 16. Executive Summary Lists

### Top 10 Critical Issues

1. Missing resource authorization across workspace/project/canvas/lifecycle routes.
2. Cookie/Bearer auth contract mismatch.
3. Public GraphQL/GraphiQL.
4. Unauthenticated/spoofable WebSocket collaboration.
5. Default service-to-service token fallback.
6. Canvas validation returns fake success.
7. Lifecycle AI approval can mutate state without governed evidence.
8. Lifecycle execution ingestion lacks service authentication.
9. Local credential/API key storage remains.
10. Included-project read-only semantics are not enforced.

### Top 10 Completeness Gaps

1. Resource capability model.
2. Canonical onboarding status backend.
3. Real canvas validation and conflict handling.
4. Lifecycle state machine and approval workflow.
5. Release candidate/deploy/observe workflow.
6. User-visible background job progress.
7. AI action evidence/provenance.
8. Admin/support diagnostics.
9. Privacy retention/deletion controls.
10. Current e2e regression coverage.

### Top 10 Simplification Opportunities

1. One auth model.
2. One API prefix.
3. One lifecycle taxonomy.
4. One canvas persistence schema.
5. One audit event service.
6. One AI confidence/evidence pattern.
7. One save/sync status contract.
8. One resource capability object.
9. One release/deploy timeline.
10. One current product documentation source.

### Top 10 Correctness Issues

1. Workspace/project id access can bypass membership.
2. Canvas save/restore lacks project access checks.
3. WebSocket join trusts client identity.
4. Canvas validator always passes.
5. Realtime canvas node shape conflicts with schema.
6. Realtime changes bypass versions.
7. Lifecycle transition readiness is heuristic.
8. Health/readiness semantics mislead.
9. GraphQL auth context is optional.
10. Local fallback can mask server failure.

### Top 10 Consistency Issues

1. Lifecycle phase names drift.
2. Auth contracts drift.
3. API prefixes drift.
4. Canvas REST vs realtime persistence drifts.
5. AI confidence/evidence drifts.
6. Audit implementations drift.
7. Docs and implementation drift.
8. Tracker and verification drift.
9. Prisma and Java domain models drift.
10. Read-only project inclusion intent and route behavior drift.

### Top 10 API/Backend/Data Issues

1. Missing resource auth helpers.
2. Public GraphQL.
3. Unsafe Java proxy token fallback.
4. No strict readiness route.
5. Lifecycle execution ingestion not service-scoped.
6. Canvas validation mock.
7. Lifecycle artifact validation weak.
8. Tenant scoping incomplete.
9. Generated clients not universally used.
10. Domain ownership split across Node/Java/Data Cloud.

### Top 10 AI/ML Opportunities

1. Evidence-backed lifecycle readiness.
2. Canvas gap/risk detection.
3. Test-gap generation.
4. Release risk scoring.
5. Security/privacy redaction before AI calls.
6. Artifact auto-linking.
7. Project next-best-action.
8. Incident learning summarization.
9. Duplicate artifact/task detection.
10. Prompt/model governance and evals.

### Top 10 Trust/Privacy/Security/O11y Improvements

1. Resource-level authorization everywhere.
2. Secure, canonical browser session model.
3. Authenticated WebSockets.
4. Authenticated/scrubbed metrics.
5. Strict readiness/liveness/health split.
6. Immutable typed audit events.
7. Remove browser secrets and API keys.
8. AI input redaction and retention policy.
9. Correlation id across Node/Java/AEP/Data Cloud.
10. User/admin-visible product timeline.
