# Yappc Production-Grade Audit & Solution Report

## 1. Executive Summary
- Scope reviewed: `products/yappc` product modules, frontend surfaces, backend services, deployment assets, CI workflows, Data Cloud adapters, shared platform dependencies, observability assets, and related audit/test documentation as they exist on 2026-04-13.
- Overall maturity summary: Yappc has substantial implementation depth across lifecycle orchestration, agents, frontend UX, Data Cloud integration, and observability foundations, but it is not production ready in its current state because the repo currently exposes multiple conflicting runtime models, duplicated ownership, inconsistent auth behavior, and release/deployment drift.
- Major risks:
  - Runtime contract drift between actual lifecycle service endpoints, OpenAPI, Helm probes, and CI checks.
  - Split auth ownership between Java and Node surfaces, with insecure dev defaults such as `dev-key`, `default-tenant`, and `change-me-in-production` still reachable in code paths under `products/yappc/core/services-lifecycle` and `products/yappc/core/yappc-services`.
  - Parallel API/server/module trees: `YappcLifecycleService`, `YappcHttpServer`, `core/yappc-api`, `core/yappc-domain-impl`, `core/agents`, and `core/yappc-agents`.
  - Data Cloud query correctness issues in `products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java` and `.../repository/ProjectRepository.java`.
  - Audio/video capability is mostly aspirational from the Yappc product perspective; frontend code references speech endpoints that do not exist in the inspected Yappc runtime surfaces.
  - Test and release evidence overstates readiness because several suites are mock-backed, skipped, stale, or aimed at APIs that do not match the current runtime.
- Major opportunities:
  - Collapse onto one authoritative lifecycle backend and one clearly-owned web/BFF topology.
  - Reuse the stronger shared platform capabilities already present for metrics, tracing, workflow, policy, and tenant context instead of duplicating product-local wrappers.
  - Turn the existing governed memory, redaction, and observability work into a consistent production control plane.
  - Apply AI/ML where it is already naturally aligned to Yappc: lifecycle recommendations, semantic retrieval over project artifacts, anomaly detection, summarization, and operator guidance.
- Highest-priority actions:
  - Canonicalize the runtime contract: port, health probes, readiness checks, metrics exposure, and API ownership.
  - Unify auth, session, and tenant enforcement and remove insecure fallbacks from all production paths.
  - Consolidate duplicate Java HTTP surfaces and duplicate agent/module trees.
  - Repair Data Cloud query semantics and enforce the adapter seam so core modules stop depending directly on Data Cloud internals.
  - Decide whether audio/video is a real supported capability now; if yes, integrate it properly with `products/audio-video`, and if no, remove misleading product claims and endpoint assumptions.

## 2. Yappc Product Understanding
- Purpose:
  - `products/yappc/OWNER.md` describes Yappc as an AI-powered software composition platform within the developer tooling domain.
  - The inspected code and docs show Yappc centered on SDLC lifecycle management: intent capture, shaping, generation, validation, execution, observation, and improvement.
- Users/personas:
  - Product owner and product manager for intent capture and prioritization.
  - Architect and tech lead for shape/design and system decisions.
  - Developer and agent operator for generation, execution, and remediation.
  - QA/test lead for validation and release confidence.
  - DevOps/SRE for run/observe/operate workflows.
  - Admin/security/operator personas for auth, audit, and governance flows.
- Workflows:
  - Lifecycle workflow: `INTENT -> SHAPE -> VALIDATE -> GENERATE -> RUN -> OBSERVE -> IMPROVE`, visible in both Java lifecycle services and Node lifecycle route implementations.
  - Project/workspace management via frontend and Node API routes.
  - Agent orchestration and generation flows across `core/agents`, `core/yappc-agents`, `core/yappc-services`, and `core/ai`.
  - Canvas/realtime collaboration through frontend web code plus Fastify WebSocket services.
  - Auth/session flows through both Node and Java layers.
- Feature areas:
  - Lifecycle orchestration and approvals.
  - Agent execution and project scaffolding.
  - Project/workspace management and collaboration.
  - AI-assisted generation, validation, and suggestions.
  - Data persistence, memory, and artifact storage through Data Cloud adapters.
  - Frontend web UX plus a secondary lightweight web shell.
- Data Cloud role:
  - Persistence and retrieval for project entities and artifacts.
  - Repository adapters in `products/yappc/infrastructure/datacloud`.
  - Memory, tenant-scoped state, and AI-ready data are partly represented, but the boundary is not yet cleanly enforced.
  - Data Cloud is also implicated in readiness checks and product-wide storage strategy.
- Audio/Video role:
  - Intended voice/speech support exists in frontend code through `products/yappc/frontend/libs/yappc-ui/src/components/voice/useVoiceCommands.ts`.
  - Actual backend ownership for STT/TTS endpoints is not present in the inspected Yappc services.
  - Realtime collaboration exists, but it is not a production-grade media or presence architecture.
- Security/Auth role:
  - API key and JWT-based auth exist.
  - Tenant context and RBAC are present in multiple places.
  - Security ownership is fragmented across Java lifecycle, legacy Java API, and Node BFF/API routes.
  - Audit and governance capabilities exist, but production enforcement is undermined by duplicate logic and insecure defaults.
- O11y role:
  - Structured metrics, tracing, dashboards, and alerts exist in parts of the stack.
  - Critical-path visibility is present but fragmented.
  - Observability is not yet consistently aligned with actual runtime ownership and security policy.
- AI/ML-native opportunities:
  - Lifecycle next-step recommendations using current artifacts and workflow state.
  - Project/activity summarization for operators and stakeholders.
  - Semantic retrieval across project artifacts, prompts, and prior runs.
  - Risk/anomaly detection for lifecycle blockers, auth abuse, and failing media/realtime sessions.
  - Human-in-the-loop approval for high-impact generation or deployment actions.
  - Evaluation telemetry for model confidence, fallbacks, and acceptance rates.

## 3. Shared Library & Repo Reuse Investigation
- Relevant shared libraries found:
  - `platform/java` provides observability, governance/security, workflow, and launcher primitives reused by Yappc.
  - `platform/typescript` provides frontend/platform packages used alongside Yappc frontend workspaces.
  - `products/data-cloud` provides the underlying storage/runtime/client capabilities that Yappc currently consumes directly in several core modules.
  - `products/audio-video` exists at repo level and should be the first reuse target for real speech/media features.
  - `shared-services` and Yappc-internal shared modules such as `products/yappc/core/yappc-shared` and `products/yappc/platform` already provide reusable foundations.
- Relevant existing implementations found:
  - Shared metrics registry and collectors in `products/yappc/platform/src/main/java/com/ghatana/yappc/platform/observability` and the shared platform metrics provider used by `YappcLifecycleService`.
  - Shared tenant context and governance hooks referenced through `com.ghatana.platform.governance.security.TenantContext`.
  - Reusable Data Cloud client/query primitives in `products/data-cloud`, but currently bypassed by direct dependency patterns in several Yappc core modules.
  - Frontend shared UI packages under `products/yappc/frontend/libs`, including voice UI and collaboration-related utilities.
  - Durable workflow engine integration in lifecycle service code.
- Reuse/consolidation candidates:
  - Use `products/yappc/core/services-lifecycle` as the canonical backend entrypoint and collapse legacy/parallel HTTP surfaces into it.
  - Reuse shared platform observability primitives rather than keeping separate Node-vs-Java telemetry conventions.
  - Reuse the existing Data Cloud adapter layer in `products/yappc/infrastructure/datacloud` and remove direct `products:data-cloud:*` dependencies from Yappc core modules.
  - Reuse the stronger of `core/agents` and `core/yappc-agents` rather than maintaining both trees.
  - Reuse `products/audio-video` for real speech/media services instead of frontend-local endpoint assumptions.
  - Reuse the existing governed memory and redaction infrastructure as the base for broader privacy/retention governance.
- Duplication risks identified:
  - Auth and session logic duplicated across Java lifecycle, legacy Java API, and Node API.
  - HTTP controllers duplicated across `core/yappc-api` and `core/yappc-domain-impl`.
  - Agent/runtime modules duplicated across `core/agents` and `core/yappc-agents`.
  - Proxy logic duplicated between `frontend/apps/api/src/index.ts` and `frontend/apps/api/src/middleware/BackendGateway.ts`.
  - Voice handling duplicated between UI hook code and browser-only service code.
  - Frontend app ownership duplicated between `frontend/web` and `frontend/apps/web`.

## 4. Current State Assessment
- What exists:
  - A substantial Java lifecycle service in `products/yappc/core/services-lifecycle`.
  - Additional Java service/API layers in `products/yappc/core/yappc-services`, `products/yappc/core/yappc-api`, and `products/yappc/core/yappc-domain-impl`.
  - A Node Fastify/GraphQL/API service in `products/yappc/frontend/apps/api`.
  - A large, mature-looking frontend app in `products/yappc/frontend/web` and a small separate app in `products/yappc/frontend/apps/web`.
  - Data Cloud repositories/adapters and a governed memory plane.
  - Observability assets: tracing, metrics, dashboards, and alert rules.
  - CI workflows, contract checks, release-readiness documentation, and performance/testing scaffolding.
- What is missing:
  - A single authoritative runtime topology.
  - A single authoritative auth/session authority.
  - A single authoritative API contract and route vocabulary.
  - Production-grade audio/video backend integration.
  - End-to-end tenant-safe retention/deletion controls across all stored data classes.
  - Release gates that validate the real runtime instead of stale or mock-heavy surrogates.
- What is duplicated:
  - Java HTTP surfaces and controllers.
  - Agent/runtime trees.
  - Frontend web app surfaces.
  - Auth filters/session endpoints.
  - Gateway/proxy logic.
  - Voice input flows.
- What is deprecated:
  - Documentation still references `core:spi`, `core:lifecycle`, and `frontend/compat/*` packages that do not line up with the current tree.
  - `products/yappc/docs/CORE_ARCHITECTURE.md` and `.../MODULE_CATALOG.md` describe migration states and packages that are not reflected in the runtime.
  - `frontend/apps/api/package.json` explicitly marks the backend service location as historical and due for relocation.
- What should be deleted:
  - Stale health probe definitions and CI checks using nonexistent endpoints.
  - Dead/legacy API surfaces once the canonical backend is chosen, especially `YappcHttpServer` if it is not the production path.
  - Duplicate controllers/routes in `core/yappc-api` or `core/yappc-domain-impl` once a single owner is selected.
  - Stale load tests targeting non-canonical endpoints such as `products/yappc/k6-tests/load-test-generation-api.js`.
  - Documentation references to empty `frontend/compat` packages.
- What should be consolidated:
  - Runtime/server ownership.
  - Auth/session/tenant logic.
  - Data Cloud access behind a product-owned port.
  - Realtime/collaboration ownership.
  - Frontend app topology and CI scripts.
  - Docs, OpenAPI, Helm, and CI health contract definitions.

## 5. Detailed Findings and Solutions

### Finding 1: Runtime, deployment, and contract drift
- Issue:
  - `products/yappc/core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java` exposes `/health`, `/ready`, and secured `/metrics` on default port `8082`.
  - `products/yappc/docs/api/openapi.yaml` documents `/health` and `/ready` but still lists local server `http://localhost:8080`.
  - `.github/workflows/yappc-ci.yml` and the broader release/readiness flow still curl `/health/readiness` and `/health/liveness` on `localhost:8080`.
  - `products/yappc/deployment/helm/values.yaml` configures `/health/live`, `/health/ready`, and `/health/startup`, which do not match the inspected lifecycle service.
- Why it matters:
  - A deployment can fail readiness even when the service is healthy, or appear validated in CI while checking the wrong surface.
  - Release evidence becomes unreliable, and operators cannot trust probes, dashboards, or rollout safety.
- Impacted files/modules:
  - `products/yappc/core/services-lifecycle/.../YappcLifecycleService.java`
  - `products/yappc/docs/api/openapi.yaml`
  - `.github/workflows/yappc-ci.yml`
  - `.github/workflows/yappc-contract-tests.yml`
  - `products/yappc/deployment/helm/values.yaml`
  - `products/yappc/deployment/kubernetes/base/deployment.yaml`
- What needs to be done:
  - Define one canonical service port and one canonical health/readiness/metrics contract.
  - Align Helm, raw Kubernetes manifests, OpenAPI docs, release checklists, and CI smoke tests to that contract.
- Recommended solution:
  - Treat lifecycle service runtime behavior as the source of truth unless a different backend is explicitly selected.
  - Move health endpoint definitions into a shared config/test fixture that CI and deployment templates consume.
  - Add a startup smoke test that verifies the exact deployed port and paths from the packaged artifact.
- Reuse/consolidation approach:
  - Reuse the already-correcter `products/yappc/deployment/kubernetes/base/deployment.yaml` paths as the baseline over the stale Helm defaults.
  - Reuse the existing OpenAPI health paths, but correct the server base URL and port.
- Cleanup/deletion required:
  - Delete stale `/health/liveness`, `/health/readiness`, `/health/live`, `/health/ready`, and `/health/startup` references unless they are reintroduced intentionally.
- Tests required:
  - Lifecycle packaged-artifact smoke test in CI.
  - Helm template/render validation asserting correct probe paths.
  - Contract test for `/health`, `/ready`, and `/metrics`.
- Security/privacy implications:
  - Metrics exposure must be consistently public or consistently protected; mixed policy is itself a security defect.
- Observability requirements:
  - Emit startup, readiness, and dependency check metrics and correlate them with rollout events.
- Rollout/runtime considerations:
  - This must land before the next production deployment because it directly affects health checks and rollout safety.
- Priority:
  - `P0`

### Finding 2: Split authentication, unsafe defaults, and weak tenant isolation
- Issue:
  - Auth/session ownership is split between Java lifecycle auth endpoints and Node auth endpoints under the same general `/api/auth/*` namespace.
  - `products/yappc/core/services-lifecycle/.../YappcApiSecurity.java` defaults API keys to `dev-key`.
  - `products/yappc/core/services-lifecycle/.../LifecycleLoginController.java` bootstraps `dev@yappc.io / change-me-in-production` when auth users are not supplied.
  - `default-tenant` fallbacks appear in Java backend, artifact storage, AI repositories, and frontend session code.
  - `YappcEnvironmentConfig.validate()` appears only in tests, not in the inspected runtime startup path.
- Why it matters:
  - Production auth cannot be trusted when multiple authorities exist and dev defaults remain reachable.
  - Tenant leakage risk remains elevated when missing context silently falls back to `default-tenant`.
  - Session behavior becomes unpredictable because login, refresh, `me`, logout, and validation do not share one source of truth.
- Impacted files/modules:
  - `products/yappc/core/services-lifecycle/src/main/java/com/ghatana/yappc/services/security/YappcApiSecurity.java`
  - `.../LifecycleLoginController.java`
  - `.../JwtAuthController.java`
  - `.../YappcEnvironmentConfig.java`
  - `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcApiAuthFilter.java`
  - `products/yappc/frontend/apps/api/src/routes/auth.ts`
  - `products/yappc/frontend/web/src/providers/auth-session.ts`
- What needs to be done:
  - Choose one auth authority.
  - Remove insecure defaults from all production paths.
  - Enforce tenant context as mandatory for protected operations.
  - Invoke environment validation during runtime startup.
- Recommended solution:
  - Make the canonical backend the sole owner of login, token issuance, refresh, logout, `me`, and tenant claims.
  - Reduce the Node layer to either a thin authenticated BFF or a pure proxy, but not an independent auth service.
  - Fail fast on startup when dev keys, bootstrap users, or missing tenant mappings are present in production mode.
  - Replace default-tenant fallbacks with explicit rejection outside local/dev mode.
- Reuse/consolidation approach:
  - Reuse `YappcEnvironmentConfig` and shared `TenantContext`, but wire them into startup and request processing consistently.
  - Reuse one JWT implementation only.
- Cleanup/deletion required:
  - Delete duplicate auth filters/routes after the canonical auth owner is selected.
  - Remove `dev-key`, `change-me-in-production`, and silent `default-tenant` fallback logic from production code paths.
- Tests required:
  - End-to-end auth flow tests for login, refresh, logout, and `me`.
  - Negative tests for missing tenant, invalid token, wrong role, expired token, and production startup with insecure config.
  - Cross-layer tenant isolation tests for Data Cloud and AI repositories.
- Security/privacy implications:
  - This is the highest-severity security finding in the repo.
  - Fixing it reduces tenant bleed, session confusion, and operator misconfiguration risk.
- Observability requirements:
  - Emit and audit login failures, token refresh, logout, tenant mismatch, and role-denied events without leaking secrets.
- Rollout/runtime considerations:
  - Requires a deliberate cutover plan for any clients currently depending on Node-local auth endpoints.
- Priority:
  - `P0`

### Finding 3: Multiple overlapping backend HTTP surfaces and duplicated controllers
- Issue:
  - The repo contains at least three authoritative-looking API/server layers:
    - `products/yappc/core/services-lifecycle/.../YappcLifecycleService.java`
    - `products/yappc/core/yappc-services/.../YappcHttpServer.java`
    - `products/yappc/frontend/apps/api/src/index.ts`
  - `products/yappc/core/yappc-api` duplicates HTTP controllers/routes that also exist under `products/yappc/core/yappc-domain-impl`.
- Why it matters:
  - It is unclear which server owns which business contract.
  - Any change to routes, auth, metrics, or error handling risks diverging behavior and duplicated maintenance.
- Impacted files/modules:
  - `products/yappc/core/services-lifecycle`
  - `products/yappc/core/yappc-services`
  - `products/yappc/core/yappc-api`
  - `products/yappc/core/yappc-domain-impl`
  - `products/yappc/frontend/apps/api`
- What needs to be done:
  - Select one canonical backend HTTP surface.
  - Collapse duplicate controller code into the runtime-owning module.
  - Regenerate or hand-maintain one authoritative contract from that surface only.
- Recommended solution:
  - Keep `core/services-lifecycle` as the product runtime if that is the intended direction.
  - Move route/controller ownership to the actual runtime-serving module and retire `YappcHttpServer`.
  - Merge `core/yappc-api` and `core/yappc-domain-impl` HTTP classes into one owned package tree.
- Reuse/consolidation approach:
  - Reuse the lifecycle module’s existing service wiring, audit hooks, metrics registry, and workflow engine integrations.
  - Reuse the stronger of the duplicated controller implementations rather than rewriting both.
- Cleanup/deletion required:
  - Delete the non-canonical server entrypoint and its dead tests once equivalence is proven.
  - Delete duplicate `AgentController`, `AgentRoutes`, `VectorController`, `VectorRoutes`, `WorkflowController`, and `WorkflowRoutes` copies.
- Tests required:
  - Contract tests and HTTP integration tests only on the selected runtime surface.
  - Architecture tests preventing multiple HTTP entrypoint modules from owning the same route families.
- Security/privacy implications:
  - One backend surface makes auth, rate limiting, redaction, and audit enforcement coherent.
- Observability requirements:
  - One metrics/tracing policy per endpoint family, not three.
- Rollout/runtime considerations:
  - Do not preserve compatibility layers by default; only keep a transitional alias if a confirmed external dependency exists.
- Priority:
  - `P0`

### Finding 4: Data Cloud query/filter semantics are currently misleading and partly broken
- Issue:
  - `YappcDataCloudRepository.findByFilter(...)` turns every filter entry into equality matching and ignores `sort`.
  - `ProjectRepository.findRecentlyActive(...)` passes `Map.of("lastActivityAt", Map.of("$gte", since.toString()))`, which cannot behave as intended under the current adapter.
- Why it matters:
  - Product logic that appears to support recency queries, sorting, or richer filters may silently return incorrect results.
  - This undermines dashboards, lifecycle suggestions, and any future AI/retrieval features that depend on temporal data.
- Impacted files/modules:
  - `products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java`
  - `.../repository/ProjectRepository.java`
- What needs to be done:
  - Either implement supported operators and sorting correctly or simplify the repository API to exact-match-only methods.
- Recommended solution:
  - Replace ad hoc `Map<String, Object>` query encoding with a typed query abstraction backed directly by `DataCloudClient.Query`.
  - Implement explicit support for comparison operators, sorting, and pagination only where Data Cloud actually supports them.
  - Remove or rename methods whose current behavior is misleading.
- Reuse/consolidation approach:
  - Reuse `DataCloudClient.Query` and `Filter` primitives directly instead of wrapping them in under-specified maps.
- Cleanup/deletion required:
  - Delete the unused `sort` parameter if sorting is not implemented.
  - Delete pseudo-rich query methods until they are correct.
- Tests required:
  - Repository integration tests against a real or faithful Data Cloud test harness.
  - Cases for range filters, descending sort, pagination, tenant scoping, and empty-result correctness.
- Security/privacy implications:
  - Tenant isolation must remain mandatory while query semantics expand.
- Observability requirements:
  - Emit query latency, collection name, filter type, and result-count metrics.
- Rollout/runtime considerations:
  - If new query shapes require indexes, add them before enabling recency or search-heavy features at scale.
- Priority:
  - `P0`

### Finding 5: Data Cloud adapter seam is not actually enforced
- Issue:
  - Several Yappc core modules still depend directly on `products:data-cloud:*`, including TODO markers acknowledging the seam violation.
- Why it matters:
  - Product logic is coupled to storage/runtime details that should stay behind Yappc-owned ports and adapters.
  - This makes testing, replacement, and ownership boundaries weaker than they appear in docs.
- Impacted files/modules:
  - `products/yappc/core/agents/runtime/build.gradle.kts`
  - `products/yappc/core/agents/workflow/build.gradle.kts`
  - `products/yappc/core/knowledge-graph/build.gradle.kts`
  - `products/yappc/core/yappc-services/build.gradle.kts`
  - `products/yappc/infrastructure/datacloud`
- What needs to be done:
  - Move all direct Data Cloud dependencies to infrastructure adapters.
  - Make core modules depend on Yappc-owned ports only.
- Recommended solution:
  - Formalize `DataStorePort` or equivalent port ownership and route all persistence/vector/query access through it.
  - Keep `products/yappc/infrastructure/datacloud` as the only Data Cloud implementation detail boundary.
- Reuse/consolidation approach:
  - Reuse the existing adapter package and mappers instead of introducing another abstraction layer.
- Cleanup/deletion required:
  - Remove direct `products:data-cloud:spi`, `platform-launcher`, and `platform-plugins` dependencies from product-core modules.
- Tests required:
  - Architecture/dependency tests that fail builds when core modules import Data Cloud internals.
  - Adapter contract tests for the port implementation.
- Security/privacy implications:
  - Clearer ownership improves tenant isolation and reduces accidental cross-layer data access.
- Observability requirements:
  - Port-level metrics should distinguish product intent from adapter/storage failures.
- Rollout/runtime considerations:
  - This can be done incrementally after the P0 correctness fixes, but should happen before major new features land.
- Priority:
  - `P1`

### Finding 6: Node API/BFF ownership is unclear and gateway behavior is duplicated
- Issue:
  - `products/yappc/frontend/apps/api/package.json` explicitly says the backend is in the frontend workspace for historical reasons.
  - `frontend/apps/api/src/index.ts` both registers local routes and contains its own catch-all proxy logic.
  - `frontend/apps/api/src/middleware/BackendGateway.ts` implements overlapping proxy/gateway behavior.
- Why it matters:
  - The repo currently cannot clearly answer whether the Node service is a true product backend, a BFF, or a temporary compatibility proxy.
  - Duplicate proxy logic creates routing drift and hard-to-debug failures.
- Impacted files/modules:
  - `products/yappc/frontend/apps/api/package.json`
  - `products/yappc/frontend/apps/api/src/index.ts`
  - `products/yappc/frontend/apps/api/src/middleware/BackendGateway.ts`
- What needs to be done:
  - Decide whether the Node service remains as a BFF.
  - Centralize route ownership and proxy behavior in one place.
  - Move the service out of the frontend workspace if it remains part of production backend topology.
- Recommended solution:
  - Keep Node only if it provides real BFF value such as SSR-safe composition, UI-specific aggregation, or websocket termination.
  - If kept, make it a thin BFF in a clearly-owned backend directory and centralize all Java proxy decisions in one gateway implementation.
  - If not kept, remove local API responsibilities and let the frontend talk to the canonical backend through ingress/API gateway.
- Reuse/consolidation approach:
  - Reuse the existing Fastify telemetry and route plugins if the BFF remains.
- Cleanup/deletion required:
  - Delete duplicate catch-all proxy code from either `index.ts` or `BackendGateway.ts`.
  - Remove “historical reasons” placement by relocating the package or deleting it.
- Tests required:
  - Route ownership tests showing which paths are local versus proxied.
  - Proxy integration tests with auth headers, errors, and timeouts.
- Security/privacy implications:
  - Proxy layers must not weaken auth boundaries, expose public metrics accidentally, or forward untrusted tenant/user identity.
- Observability requirements:
  - Every proxied request should preserve correlation IDs and mark upstream/downstream latency separately.
- Rollout/runtime considerations:
  - Route ownership must be documented before moving this service or changing ingress rules.
- Priority:
  - `P1`

### Finding 7: Two frontend web surfaces and misaligned frontend CI
- Issue:
  - `products/yappc/frontend/web` is the substantial product UI.
  - `products/yappc/frontend/apps/web` is a second lightweight app with only a handful of source files.
  - `.github/workflows/yappc-fe-ci.yml` runs scripts and working directories that do not match `products/yappc/frontend/package.json` and the actual tree.
- Why it matters:
  - Frontend ownership is ambiguous.
  - CI can report green while failing to validate the real shipped UI, or fail for the wrong reasons.
- Impacted files/modules:
  - `products/yappc/frontend/web`
  - `products/yappc/frontend/apps/web`
  - `products/yappc/frontend/package.json`
  - `.github/workflows/yappc-fe-ci.yml`
- What needs to be done:
  - Select one canonical web app.
  - Align scripts, working directories, and release checks to that app.
- Recommended solution:
  - Treat `products/yappc/frontend/web` as the canonical product web client.
  - Retire or explicitly repurpose `frontend/apps/web` as a demo/shell if it has a real purpose.
  - Update CI to call only scripts that exist and point at actual workspace locations.
- Reuse/consolidation approach:
  - Reuse shared UI libraries from `frontend/libs`, but avoid keeping a second app shell unless it serves a distinct deployment.
- Cleanup/deletion required:
  - Delete stale `build:web` and `test:typecheck` assumptions from CI if they are not real.
  - Delete or archive `frontend/apps/web` if it is not a supported production artifact.
- Tests required:
  - Canonical frontend build smoke test.
  - Critical user-journey browser integration tests and Playwright smoke tests against the selected app.
- Security/privacy implications:
  - CI must validate the actual app that handles auth/session state and websocket connections.
- Observability requirements:
  - Frontend error reporting and performance budget checks should target the canonical app only.
- Rollout/runtime considerations:
  - If `frontend/apps/web` is currently deployed anywhere, inventory and retire that path explicitly.
- Priority:
  - `P1`

### Finding 8: Audio/Video capability is incomplete, split, and not production safe
- Issue:
  - `products/yappc/frontend/libs/yappc-ui/src/components/voice/useVoiceCommands.ts` assumes `/api/v1/speech/stt` and `/api/v1/speech/tts`.
  - The inspected Yappc runtime surfaces do not implement those endpoints.
  - `products/yappc/frontend/web/src/services/VoiceInputService.ts` is a browser-only fallback with `// @ts-nocheck`.
- Why it matters:
  - The product signals audio/video readiness without a trustworthy backend ownership model.
  - Browser-only voice behavior and nonexistent backend contracts are not acceptable as a production-grade media strategy.
- Impacted files/modules:
  - `products/yappc/frontend/libs/yappc-ui/src/components/voice/useVoiceCommands.ts`
  - `products/yappc/frontend/web/src/services/VoiceInputService.ts`
  - `products/audio-video` as the likely reuse target
- What needs to be done:
  - Decide whether speech/media is currently in scope for Yappc production.
  - If yes, integrate with a real audio/video service boundary and secure it.
  - If not, remove or clearly feature-flag the unfinished capability.
- Recommended solution:
  - Route speech features through `products/audio-video` or another clearly-owned media service.
  - Define signed/authenticated upload and transcription flows, result schemas, retries, fallback UX, and quotas.
  - Remove `@ts-nocheck` and browser-only singleton assumptions from the production path.
- Reuse/consolidation approach:
  - Reuse repo-level audio/video capabilities rather than inventing Yappc-local speech endpoints.
- Cleanup/deletion required:
  - Delete fake/stale endpoint defaults if the feature is not enabled.
  - Delete one of the duplicate voice implementations after the canonical path is chosen.
- Tests required:
  - Contract tests for STT/TTS endpoints.
  - Browser permission-denial tests.
  - Degraded network and retry/resume tests.
  - Security tests for media access/auth scopes.
- Security/privacy implications:
  - Voice and audio content are privacy-sensitive and require explicit retention, redaction, and access controls.
- Observability requirements:
  - Track transcription latency, failure rate, quality/error categories, microphone permission failures, and fallback usage.
- Rollout/runtime considerations:
  - Release only behind a feature flag until backend ownership, quotas, and privacy controls are complete.
- Priority:
  - `P1`

### Finding 9: Realtime collaboration is in-memory and not horizontally scalable
- Issue:
  - `products/yappc/frontend/apps/api/src/services/RealTimeService.ts` stores rooms and notification clients in memory and allows a `join` path that trusts client-supplied identity before strong server-side authorization is established.
- Why it matters:
  - Collaboration state disappears on restart, does not scale across instances, and risks cross-project/session confusion.
  - This is not production-safe for multi-user collaboration or future low-latency media/realtime features.
- Impacted files/modules:
  - `products/yappc/frontend/apps/api/src/services/RealTimeService.ts`
  - `products/yappc/frontend/web/src/contexts/WebSocketContext.tsx`
  - Yjs/WebSocket-related frontend packages
- What needs to be done:
  - Replace in-memory state and placeholder auth with a shared presence/pub-sub layer and verified room authorization.
- Recommended solution:
  - Move presence, fanout, and CRDT synchronization onto Redis, a dedicated realtime service, or another shared scalable backing layer.
  - Require validated JWT/tenant/project authorization before joining a room.
  - Make websocket instances stateless across pods where possible.
- Reuse/consolidation approach:
  - Reuse `yjs`, `y-websocket`, and existing frontend collaboration primitives, but move state ownership out of process memory.
- Cleanup/deletion required:
  - Delete the unauthenticated or weakly-authenticated `join` path that trusts client-provided user identifiers.
- Tests required:
  - Multi-instance websocket tests.
  - Authz tests for room join and cross-project isolation.
  - Reconnect, heartbeat, backpressure, and stale-room cleanup tests.
- Security/privacy implications:
  - Presence and collaborative content must respect role and project boundaries.
- Observability requirements:
  - Track active connections, join failures, auth failures, fanout latency, reconnect rates, and room cardinality.
- Rollout/runtime considerations:
  - This should be fixed before promoting collaborative workflows as production-grade.
- Priority:
  - `P1`

### Finding 10: Observability exists but is fragmented and inconsistently governed
- Issue:
  - Java services, Node API, and frontend telemetry all emit observability signals, but ownership, redaction, and auth policy are inconsistent.
  - Node exposes `/metrics` publicly in one surface while Java treats metrics as a secured endpoint.
  - `frontend/apps/api/src/routes/telemetry.ts` is light-weight and does not establish a durable operational pipeline.
- Why it matters:
  - Operators do not get one coherent view of request, job, data, and collaboration health.
  - Mixed auth policy around observability endpoints creates both blind spots and exposure risk.
- Impacted files/modules:
  - `products/yappc/platform/src/main/java/com/ghatana/yappc/platform/observability/*`
  - `products/yappc/frontend/apps/api/src/utils/tracing.ts`
  - `products/yappc/frontend/apps/api/src/utils/metrics.ts`
  - `products/yappc/frontend/apps/api/src/routes/telemetry.ts`
  - `products/yappc/deployment/monitoring/alerts/yappc.yml`
  - `products/yappc/deployment/monitoring/grafana/yappc-dashboard.json`
- What needs to be done:
  - Standardize log schema, trace propagation, metric names, and observability endpoint policy across the selected runtime topology.
- Recommended solution:
  - Define one correlation ID and tenant-safe logging schema across Node, Java, background jobs, and websocket flows.
  - Choose whether metrics are cluster-internal/public-to-scrape or auth-protected, then apply that policy consistently.
  - Route frontend error telemetry into a durable backend or third-party sink with sampling and redaction.
- Reuse/consolidation approach:
  - Reuse the shared platform metrics registry and lifecycle tracing setup rather than building separate per-service naming conventions.
- Cleanup/deletion required:
  - Remove duplicate or conflicting metric exposure policy.
  - Delete ad hoc telemetry routes once durable ingestion is in place.
- Tests required:
  - Trace propagation tests across Node proxy -> Java backend.
  - Metrics scrape smoke tests.
  - Log redaction tests for PII and secrets.
- Security/privacy implications:
  - Telemetry must not leak tokens, prompts, credentials, or raw sensitive payloads.
- Observability requirements:
  - Define business KPIs for lifecycle advancement, generation success, auth failures, collaboration health, and future media quality.
- Rollout/runtime considerations:
  - Observability hardening should follow runtime/auth consolidation so the final signal set reflects the real topology.
- Priority:
  - `P2`

### Finding 11: Testing and release evidence do not yet prove production readiness
- Issue:
  - Some tests labeled as E2E are mock-backed.
  - Several suites are skipped or disabled.
  - `products/yappc/k6-tests/load-test-generation-api.js` targets route families that do not match the inspected canonical lifecycle contract.
  - `docs/YAPPC_TYPESCRIPT_TEST_COVERAGE_AUDIT_2026-04-13.md` already concludes overall meaningful coverage is well below a production-grade target.
- Why it matters:
  - Green pipelines do not guarantee that the real runtime, auth, tenant, or data flows work in production conditions.
  - Release confidence remains low even though many tests exist.
- Impacted files/modules:
  - `products/yappc/e2e-tests`
  - `products/yappc/tests`
  - `products/yappc/k6-tests/load-test-generation-api.js`
  - `.github/workflows/yappc-ci.yml`
  - `.github/workflows/yappc-fe-ci.yml`
  - `docs/YAPPC_TYPESCRIPT_TEST_COVERAGE_AUDIT_2026-04-13.md`
- What needs to be done:
  - Reclassify tests by real tier.
  - Delete or rewrite stale suites.
  - Build release gates around the actual canonical runtime and critical user journeys.
- Recommended solution:
  - Require real integration/E2E coverage for auth, lifecycle transitions, project persistence, collaboration auth, and Data Cloud-backed retrieval.
  - Replace stale k6 targets with load tests that hit the actual supported API vocabulary.
  - Treat mock-backed browser flows as browser integration, not E2E.
- Reuse/consolidation approach:
  - Reuse existing CI artifact upload and release-evidence plumbing once the tests point to the real runtime.
- Cleanup/deletion required:
  - Delete or rename fake E2E suites and stale load-test endpoints.
  - Remove skipped tests or track them explicitly as blockers instead of silent debt.
- Tests required:
  - Real backend smoke tests.
  - Contract tests.
  - Auth/tenant integration tests.
  - Performance and chaos/degradation tests for the selected topology.
- Security/privacy implications:
  - Security-critical flows must have real regression coverage, not mocked stand-ins.
- Observability requirements:
  - CI should collect startup, readiness, auth, contract, and performance evidence for the canonical runtime only.
- Rollout/runtime considerations:
  - Do not claim production-grade readiness until release gates match the actual deployable system.
- Priority:
  - `P2`

### Finding 12: Documentation and module inventories are materially stale
- Issue:
  - `products/yappc/docs/CORE_ARCHITECTURE.md`, `.../MODULE_CATALOG.md`, `README.md`, and related release docs describe modules, migrations, compat packages, and ownership states that do not match the current repo layout.
  - `frontend/compat` is documented but empty.
- Why it matters:
  - Engineers cannot reliably determine current ownership from the docs.
  - Cleanup becomes harder because stale docs keep obsolete modules looking legitimate.
- Impacted files/modules:
  - `products/yappc/README.md`
  - `products/yappc/docs/CORE_ARCHITECTURE.md`
  - `products/yappc/docs/MODULE_CATALOG.md`
  - `products/yappc/docs/RELEASE_READINESS_CHECKLIST.md`
  - `products/yappc/docs/api/API_OWNERSHIP_MATRIX.md`
- What needs to be done:
  - Rewrite the docs based on the current codebase and the chosen canonical runtime topology.
  - Stop documenting empty or removed packages as if they are still active.
- Recommended solution:
  - Generate parts of the module catalog from Gradle/Pnpm workspace metadata.
  - Keep architecture docs short and opinionated around actual ownership, not migration aspirations.
- Reuse/consolidation approach:
  - Reuse `settings.gradle.kts`, workspace manifests, and build metadata as the source for module inventory.
- Cleanup/deletion required:
  - Delete stale compat/module entries and migration notes that are no longer actionable.
- Tests required:
  - A docs validation task that fails if documented modules or package directories do not exist.
- Security/privacy implications:
  - Stale docs can lead to misconfigured release and auth assumptions.
- Observability requirements:
  - Architecture docs should include canonical observability surfaces and endpoint policy.
- Rollout/runtime considerations:
  - Update docs alongside P0/P1 consolidation, not after, so new architecture is documented immediately.
- Priority:
  - `P2`

### Finding 13: Privacy, retention, and deletion governance are only partially implemented
- Issue:
  - `products/yappc/core/services-lifecycle/.../YappcRetentionService.java` appears focused on artifact TTL cleanup rather than a full product-wide data lifecycle.
  - Governed memory and redaction exist, but there is no evidence of a complete retention/deletion matrix across conversations, prompts, telemetry, artifacts, and future media.
- Why it matters:
  - Production systems need explicit lifecycle ownership for privacy-sensitive and tenant-scoped data.
  - AI/ML features increase the need for retention clarity, auditability, and deletion workflows.
- Impacted files/modules:
  - `products/yappc/core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcRetentionService.java`
  - `.../memory/GovernedMemoryPlane.java`
  - `products/yappc/config/memory/redaction-rules.yaml`
  - Data Cloud repositories and AI persistence modules
- What needs to be done:
  - Define product-wide data classes, retention windows, deletion workflows, export/delete rights, and audit evidence.
- Recommended solution:
  - Create a data lifecycle matrix covering artifacts, conversations, prompts, agent memory, telemetry, audit events, and any audio/video payloads.
  - Implement scheduled purge and delete-by-tenant/user/project workflows with audit trails and operator dashboards.
- Reuse/consolidation approach:
  - Reuse the governed memory plane and redaction rules as the enforcement foundation instead of creating another privacy subsystem.
- Cleanup/deletion required:
  - Remove ad hoc TTL-only assumptions as the sole “retention strategy.”
- Tests required:
  - Retention purge tests.
  - Tenant delete/export tests.
  - Redaction regression tests.
- Security/privacy implications:
  - This is a direct privacy and compliance readiness gap.
- Observability requirements:
  - Track purge counts, failed deletions, redaction hits, and pending deletion backlog.
- Rollout/runtime considerations:
  - Must be defined before launching AI memory or voice/media features broadly.
- Priority:
  - `P1`

## 6. Deep Gap Analysis

### 6.1 Features
- Core lifecycle capability exists, but the product surface is not complete in a production sense because major workflow ownership is split across Java lifecycle, legacy Java APIs, and Node APIs.
- Edge cases and failure paths are only partially covered; several tests are mock-backed or skipped.
- Permission-aware behavior exists in pieces through RBAC and tenant context, but the split auth design means role/persona enforcement is not uniformly trustworthy.
- Admin and operational flows are present in fragments, especially around auth, diagnostics, and observability, but they are not consolidated into one runtime model.
- Collaboration workflows exist, but realtime state handling is not production-grade.

### 6.2 Data Cloud
- CRUD and tenant-scoped repository patterns exist, which is a solid foundation.
- Query richness is overstated by the current adapter API and therefore correctness is suspect for anything beyond equality filters.
- Direct Data Cloud dependency leakage from core modules violates clean architecture and weakens product/platform boundaries.
- Retention, deletion, and privacy controls are partial, not product-wide.
- AI-readiness is plausible because data, artifacts, and memory are present, but retrieval/query quality and lifecycle governance need work first.

### 6.3 Audio/Video
- There is no inspected production-grade Yappc media backend.
- Frontend voice support is split between assumed backend speech endpoints and browser-only speech recognition.
- No evidence was found of resumable uploads, media access tokens, transcoding/processing ownership, or media pipeline telemetry in Yappc itself.
- Network variability, retry, and failure handling for speech/media are not currently production ready.
- Media feature scope should be treated as incomplete until a real backend contract exists.

### 6.4 Security / Auth
- AuthN/AuthZ logic exists, but in multiple stacks.
- Insecure defaults and tenant fallbacks materially weaken the posture.
- Service-to-service trust boundaries are not clean because BFF/proxy/backend roles are not clean.
- Secret/token/session handling is not safely centralized.
- Auditability exists in concept, but only partial enforcement can be trusted until auth ownership is consolidated.

### 6.5 Observability / O11y
- Metrics, tracing, dashboards, and alerts are real strengths in the repo.
- End-to-end visibility is incomplete because request flow traverses multiple overlapping surfaces with inconsistent route/auth policy.
- Business KPI ownership is not clearly defined across lifecycle, collaboration, auth, and AI quality.
- Background job and Data Cloud visibility need better standardization.
- Media telemetry is effectively absent from the Yappc product surface.

### 6.6 Performance
- Some performance intent exists through bundle checks, k6, and metrics classes.
- Query inefficiencies are likely where filter/sort semantics are not truly implemented.
- The in-memory realtime layer will degrade badly under multi-instance load.
- Multiple proxy/hop layers increase latency and operational complexity.
- AI inference cost/latency constraints are not yet systematically surfaced in production readiness assets.

### 6.7 Scalability
- Stateless scaling is blocked by in-memory websocket room state.
- Horizontal scaling is further complicated by overlapping Node and Java surfaces.
- DB/Data Cloud growth strategy and query/index plans are not yet explicit for advanced retrieval patterns.
- Retry/idempotency behavior is not clearly standardized across lifecycle transitions, auth, and proxy flows.
- Event-driven and background processing opportunities exist, but the ownership boundary between Yappc and platform services is not yet settled.

### 6.8 API / Contracts
- Current API vocabulary is fragmented:
  - Java lifecycle docs use `/api/v1/...`.
  - Node lifecycle routes expose their own `/api` and `/v1` aliases.
  - k6 load tests target `/api/v1/designs/...`.
- Validation and error handling differ by runtime surface.
- Contract reuse is weak because multiple controller trees define overlapping route families.
- Generated type and schema reuse appears partial rather than canonical.
- The current API surface is not yet suitable for reliable long-term UI and integration ownership.

### 6.9 Data / Persistence
- Data models and repositories exist, but several quality dimensions remain partial:
  - richer query semantics,
  - retention/deletion,
  - full audit/history strategy,
  - privacy-sensitive payload lifecycle,
  - explicit analytical/derived data ownership.
- Encryption support exists for some fields, which is a good sign, but product-wide minimization and deletion governance is not complete.
- Tenant isolation is a design goal, but fallback logic elsewhere weakens the guarantee.

### 6.10 Deployment / Runtime
- Build and deployment assets exist, including Helm, Kubernetes, blue/green YAML, monitoring configs, and CI workflows.
- Those assets are not trustworthy as a unified deployment model because probes, ports, and route assumptions drift from actual runtime code.
- Environment/config validation is not fail-fast in the inspected runtime path.
- Rollout/rollback strategy exists on paper, but operational safety is reduced by the drift.
- Runtime assumptions are not consistently documented because the docs themselves are stale.

### 6.11 UI / UX
- The main web app is broad and likely contains meaningful UX investment.
- Product surface consistency is weakened by the presence of two web apps and multiple route/backing-service assumptions.
- Error/loading/empty states are present in some areas, but release confidence is lowered by skipped tests and inconsistent backend ownership.
- Accessibility and discoverability likely have mixed quality because the frontend is large and only partially verified by trustworthy release gates.
- Voice and collaboration UX are ahead of the backend capability needed to support them safely.

### 6.12 Testing
- Yappc has more tests than a typical immature product, which is a positive baseline.
- The current test portfolio is not production-grade because too much of the critical path is mock-backed, skipped, stale, or routed to non-canonical APIs.
- Integration/E2E ownership for auth, Data Cloud, collaboration, and deployment smoke checks needs rebuilding around the final runtime topology.
- Performance and resilience testing exists in intent, but not yet against the real supported contracts.
- Security/privacy test coverage is present in places but not yet sufficient to offset the auth/tenant risks.

### 6.13 AI/ML-Native Readiness
- Yappc is naturally suited to AI/ML-native behavior, and some AI-centric modules already exist.
- Current AI enablement is stronger in generation/orchestration than in evaluation, retrieval, operational telemetry, and confidence handling.
- Recommended AI/ML opportunities:
  - lifecycle stage recommendations based on recent activity and blockers,
  - semantic search over artifacts and prompts,
  - project/run summaries,
  - anomaly detection on failed transitions, auth abuse, and collaboration instability,
  - human approval for risky generation/deployment changes.
- Required control additions:
  - confidence scores and fallback behavior,
  - privacy-aware prompt/context logging,
  - evaluation datasets and quality telemetry,
  - latency and cost budgets,
  - abuse/misuse monitoring for agent-assisted flows.

## 7. Duplicate / Deprecated / Dead Code Findings
| Exact issue | Impacted files/modules | Recommended action |
| --- | --- | --- |
| Parallel agent trees with same-named files diverging | `products/yappc/core/agents`, `products/yappc/core/yappc-agents` | Choose one module tree as canonical, migrate the stronger implementation, and delete the duplicate tree. |
| Duplicate HTTP controllers/routes | `products/yappc/core/yappc-api/src/main/java/com/ghatana/yappc/api/http/*`, `products/yappc/core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/*/http/*` | Consolidate controller ownership into the module that actually serves runtime traffic; delete the other copy. |
| Parallel backend entrypoints | `products/yappc/core/services-lifecycle/.../YappcLifecycleService.java`, `products/yappc/core/yappc-services/.../YappcHttpServer.java`, `products/yappc/frontend/apps/api/src/index.ts` | Pick one canonical backend plus optional thin BFF; retire legacy or duplicate entrypoints. |
| Duplicate auth stacks | Java lifecycle auth, legacy Java API auth filter, Node auth routes | Centralize auth/session/tenant logic into one authority and remove duplicate route families. |
| Duplicate gateway logic | `products/yappc/frontend/apps/api/src/index.ts`, `products/yappc/frontend/apps/api/src/middleware/BackendGateway.ts` | Keep one gateway implementation only. |
| Duplicate voice flows | `frontend/libs/yappc-ui/.../useVoiceCommands.ts`, `frontend/web/src/services/VoiceInputService.ts` | Keep one supported voice path and feature-flag or remove the other. |
| Two web app surfaces | `products/yappc/frontend/web`, `products/yappc/frontend/apps/web` | Keep one production web app and archive/delete the other unless it has a distinct deployment role. |
| Documented compatibility packages do not exist | `products/yappc/docs/MODULE_CATALOG.md`, empty `products/yappc/frontend/compat` | Remove stale compat references from docs or restore the packages intentionally; default action should be doc cleanup. |
| Stale load/perf contract | `products/yappc/k6-tests/load-test-generation-api.js` | Rewrite to target the canonical API or delete until a valid performance contract exists. |
| Stale probe and release evidence definitions | `.github/workflows/yappc-ci.yml`, Helm values, release docs | Replace with canonical probe/port definitions and delete obsolete paths. |

## 8. Boundary & Ownership Findings
- Yappc vs shared library boundaries:
  - Shared platform reuse exists, but Yappc core still depends directly on Data Cloud internals in places where it should depend on Yappc-owned ports.
  - The Node backend living inside the frontend workspace is a strong ownership smell even though it is not technically a shared library.
  - Some modules that look “shared” are actually Yappc-specific and should remain product-owned until another real consumer exists.
- Data Cloud / Audio-Video / Auth / O11y ownership issues:
  - Data Cloud:
    - `products/yappc/infrastructure/datacloud` is the right direction, but core modules still bypass it.
  - Audio/Video:
    - Yappc UI assumes speech services, but Yappc runtime does not own those contracts today.
    - Audio/video ownership should move to or remain with `products/audio-video`.
  - Auth:
    - Node and Java both behave like auth owners today.
    - This must become one authoritative service boundary.
  - O11y:
    - Shared metrics/tracing foundations exist, but endpoint exposure, correlation, and business telemetry ownership are not standardized.
- Yappc vs frontend/web boundary issues:
  - `frontend/web` appears to be the actual product.
  - `frontend/apps/web` currently behaves like an unowned or transitional shell.
  - `frontend/apps/api` behaves like a partially product-specific backend misplaced inside the frontend workspace.
- Refactor/consolidation guidance:
  - Yappc product should own:
    - lifecycle/business APIs,
    - product workflows,
    - product-specific persistence ports,
    - product UI.
  - Shared platform should own:
    - tracing/logging/metrics primitives,
    - tenant context primitives,
    - generic workflow/runtime helpers,
    - cross-product auth/security primitives, not Yappc-specific sessions/routes.
  - Data Cloud should own:
    - storage/runtime/client internals only, not Yappc business query semantics.
  - Audio/Video should own:
    - speech/media contracts, processing, access control, and telemetry if those features stay in scope for Yappc.

## 9. Detailed Action Plan

### P0

#### Action P0.1: Canonicalize the runtime contract
- Title:
  - Canonicalize lifecycle runtime, probes, and contract sources
- Problem:
  - Ports and health endpoints differ across runtime, Helm, CI, and docs.
- Solution:
  - Declare one canonical runtime port and probe set, then align OpenAPI, Helm, Kubernetes, CI, and release evidence to it.
- Impacted modules:
  - `core/services-lifecycle`
  - `docs/api/openapi.yaml`
  - `.github/workflows/yappc-ci.yml`
  - `.github/workflows/yappc-contract-tests.yml`
  - `deployment/helm`
  - `deployment/kubernetes/base`
- Dependencies:
  - Decision on canonical backend surface.
- Implementation steps:
  - Inventory all health/readiness/metrics references.
  - Update lifecycle service config docs with actual defaults.
  - Correct Helm and CI paths/ports.
  - Add one packaged-artifact startup smoke test as the release truth source.
- Cleanup steps:
  - Remove old probe paths and stale docs.
- Tests:
  - Smoke test for `/health`, `/ready`, `/metrics`.
  - Helm render assertions.
- O11y/security requirements:
  - Standardize metrics endpoint exposure policy.
- Deployment/runtime tasks:
  - Validate blue/green and ingress readiness behavior against the corrected probes.
- Acceptance criteria:
  - Every deployment artifact, CI job, and contract doc points at the same runtime port and probe paths.

#### Action P0.2: Unify auth, session, and tenant enforcement
- Title:
  - Collapse to one auth authority and remove insecure fallbacks
- Problem:
  - Java and Node both own auth flows and production code still contains dev defaults.
- Solution:
  - Choose one auth owner, make tenant context mandatory, and fail startup on insecure production config.
- Impacted modules:
  - `core/services-lifecycle`
  - `core/yappc-services`
  - `frontend/apps/api`
  - `frontend/web`
- Dependencies:
  - Canonical backend decision.
- Implementation steps:
  - Select the canonical auth/session endpoint family.
  - Wire `YappcEnvironmentConfig.validate()` into startup.
  - Remove `dev-key`, bootstrap dev user, and silent default-tenant behavior from production paths.
  - Update frontend session provider to use the canonical auth contract.
- Cleanup steps:
  - Delete duplicate auth routes/filters after cutover.
- Tests:
  - Real auth E2E.
  - Tenant isolation regression suite.
  - Production startup rejection suite.
- O11y/security requirements:
  - Audit auth events and deny paths; redact secrets.
- Deployment/runtime tasks:
  - Rotate credentials and stage cutover in a lower environment first.
- Acceptance criteria:
  - One auth surface exists, tenantless protected requests are rejected, and production cannot boot with dev defaults.

#### Action P0.3: Consolidate backend HTTP surfaces and route ownership
- Title:
  - Remove duplicate servers and duplicate controllers
- Problem:
  - Multiple backend surfaces and controller trees own overlapping APIs.
- Solution:
  - Keep one canonical backend surface and one controller tree.
- Impacted modules:
  - `core/services-lifecycle`
  - `core/yappc-services`
  - `core/yappc-api`
  - `core/yappc-domain-impl`
  - `frontend/apps/api`
- Dependencies:
  - Auth cutover plan.
- Implementation steps:
  - Map all route families to owning runtime.
  - Migrate needed controllers into the canonical module.
  - Replace duplicated route registration with one owned path per capability.
- Cleanup steps:
  - Delete `YappcHttpServer` or other non-canonical entrypoints.
  - Delete duplicate controller/route classes.
- Tests:
  - HTTP contract suite on the chosen runtime.
  - Route ownership architecture tests.
- O11y/security requirements:
  - Ensure all surviving endpoints share one auth, tracing, and error model.
- Deployment/runtime tasks:
  - Update ingress/API gateway rules to point only at the surviving backend.
- Acceptance criteria:
  - Only one backend surface serves each API family and all docs/tests reflect it.

#### Action P0.4: Fix Data Cloud query correctness
- Title:
  - Make Data Cloud filtering, sorting, and pagination truthful
- Problem:
  - Repository APIs currently imply richer semantics than the adapter actually supports.
- Solution:
  - Implement supported query operators explicitly or simplify the repository surface.
- Impacted modules:
  - `infrastructure/datacloud/adapter`
  - `infrastructure/datacloud/repository`
- Dependencies:
  - None beyond current adapter ownership.
- Implementation steps:
  - Define supported query operators and sort semantics.
  - Replace ad hoc map filters with typed query objects.
  - Update repository methods to match actual behavior.
- Cleanup steps:
  - Remove misleading methods and unused parameters.
- Tests:
  - Real Data Cloud-backed integration tests for range, sort, and pagination.
- O11y/security requirements:
  - Query metrics by collection and outcome; preserve tenant scoping.
- Deployment/runtime tasks:
  - Add indexes or query optimizations if required by new semantics.
- Acceptance criteria:
  - Repository behavior matches method names and documented query capabilities.

### P1

#### Action P1.1: Enforce the Data Cloud adapter seam
- Title:
  - Remove direct Data Cloud internals from Yappc core
- Problem:
  - Core modules depend directly on `products:data-cloud:*`.
- Solution:
  - Route all persistence and retrieval through Yappc-owned ports/adapters.
- Impacted modules:
  - `core/agents/runtime`
  - `core/agents/workflow`
  - `core/knowledge-graph`
  - `core/yappc-services`
  - `infrastructure/datacloud`
- Dependencies:
  - P0.4 strongly recommended first.
- Implementation steps:
  - Define the product-owned storage/retrieval port set.
  - Move implementation bindings into infrastructure.
  - Add dependency rules to prevent regressions.
- Cleanup steps:
  - Remove direct Gradle dependencies on Data Cloud implementation/runtime modules.
- Tests:
  - Dependency architecture checks and adapter contract tests.
- O11y/security requirements:
  - Port-level telemetry and tenant-safe adapter logging.
- Deployment/runtime tasks:
  - Validate runtime wiring after dependency changes.
- Acceptance criteria:
  - No product-core module imports Data Cloud implementation details directly.

#### Action P1.2: Rationalize frontend topology
- Title:
  - Keep one web app and one clearly-owned BFF strategy
- Problem:
  - `frontend/web`, `frontend/apps/web`, and `frontend/apps/api` create ambiguous product topology.
- Solution:
  - Choose the canonical web app and either promote the Node service to a real BFF or retire it.
- Impacted modules:
  - `frontend/web`
  - `frontend/apps/web`
  - `frontend/apps/api`
  - `frontend/package.json`
  - `.github/workflows/yappc-fe-ci.yml`
- Dependencies:
  - P0 runtime/auth decisions.
- Implementation steps:
  - Select production web artifact.
  - Align scripts and CI with the selected artifact.
  - Relocate or retire the Node API package.
- Cleanup steps:
  - Archive/delete the unused web app and duplicate proxy code.
- Tests:
  - Frontend smoke build and browser journey tests.
- O11y/security requirements:
  - Preserve session/correlation integrity through the selected web topology.
- Deployment/runtime tasks:
  - Update build pipelines and deployment manifests for the chosen app only.
- Acceptance criteria:
  - Repo has one production web app, one documented backend access path, and matching CI.

#### Action P1.3: Productionize collaboration and media scope
- Title:
  - Make realtime and audio/video either real or explicitly out of scope
- Problem:
  - Collaboration is in-memory and voice/media support is split and incomplete.
- Solution:
  - Back collaboration with scalable shared state and integrate speech/media with `products/audio-video` if retained.
- Impacted modules:
  - `frontend/apps/api/src/services/RealTimeService.ts`
  - `frontend/web`
  - `frontend/libs/yappc-ui`
  - `products/audio-video`
- Dependencies:
  - Auth consolidation.
- Implementation steps:
  - Enforce authenticated websocket joins.
  - Move room/presence state off-process.
  - Define speech/media service contracts or remove endpoint assumptions.
- Cleanup steps:
  - Delete placeholder auth and stale voice endpoint assumptions.
- Tests:
  - Multi-instance collaboration, reconnect, authz, and speech contract tests.
- O11y/security requirements:
  - Track websocket/session/media failures and protect tenant/project boundaries.
- Deployment/runtime tasks:
  - Provision shared backing services for collaboration if this remains in scope.
- Acceptance criteria:
  - Collaboration survives scale-out and restart, and any media feature has a real backend contract.

#### Action P1.4: Finish privacy and retention governance
- Title:
  - Define and implement product-wide data lifecycle controls
- Problem:
  - Retention/deletion is partial and centered on artifacts rather than all data classes.
- Solution:
  - Create a data lifecycle matrix and implement purge/delete/export workflows.
- Impacted modules:
  - `core/services-lifecycle`
  - `core/ai`
  - `infrastructure/datacloud`
  - configs under `products/yappc/config`
- Dependencies:
  - Auth/tenant consolidation.
- Implementation steps:
  - Inventory all stored data classes.
  - Define retention/deletion policies.
  - Implement purge jobs and audit trails.
- Cleanup steps:
  - Remove ad hoc lifecycle assumptions.
- Tests:
  - Retention and deletion integration tests.
- O11y/security requirements:
  - Purge metrics, deletion audit logs, redaction coverage.
- Deployment/runtime tasks:
  - Add scheduled jobs and operator dashboards.
- Acceptance criteria:
  - Every stored data class has an owner, retention rule, and deletion path.

### P2

#### Action P2.1: Unify observability and incident readiness
- Title:
  - Standardize logs, traces, metrics, and endpoint policy
- Problem:
  - Signal quality exists but is fragmented and inconsistently exposed.
- Solution:
  - Create one end-to-end observability model across the selected runtime topology.
- Impacted modules:
  - `products/yappc/platform`
  - `frontend/apps/api`
  - monitoring assets under `products/yappc/deployment/monitoring`
- Dependencies:
  - P0 runtime consolidation.
- Implementation steps:
  - Standardize correlation IDs and metric namespaces.
  - Define business KPIs and SLO indicators.
  - Route frontend telemetry to durable ingestion.
- Cleanup steps:
  - Remove conflicting metrics exposure and ad hoc telemetry routes.
- Tests:
  - Trace propagation and scrape tests.
- O11y/security requirements:
  - Redaction and auth consistency across telemetry paths.
- Deployment/runtime tasks:
  - Update Prometheus/Grafana/alert rules for the final topology.
- Acceptance criteria:
  - Operators can follow one request across frontend, BFF, backend, and storage paths.

#### Action P2.2: Rebuild release gates around real flows
- Title:
  - Make CI prove the actual production path
- Problem:
  - Several current tests and workflows do not validate the real supported runtime.
- Solution:
  - Reclassify tests by tier and gate releases on real smoke/integration/contract coverage.
- Impacted modules:
  - `.github/workflows/*`
  - `products/yappc/e2e-tests`
  - `products/yappc/tests`
  - `products/yappc/k6-tests`
- Dependencies:
  - P0 runtime/auth/API decisions.
- Implementation steps:
  - Delete or relabel fake E2Es.
  - Add real auth, lifecycle, persistence, and collaboration smoke flows.
  - Rewrite load tests to the canonical API.
- Cleanup steps:
  - Remove skipped tests from release-critical suites or fail the pipeline on them.
- Tests:
  - This action is test-focused by design.
- O11y/security requirements:
  - Release evidence must include auth and readiness diagnostics.
- Deployment/runtime tasks:
  - Gate production promotion on the corrected evidence bundle.
- Acceptance criteria:
  - A green pipeline means the real deployable system passed real critical-path checks.

#### Action P2.3: Refresh architecture, ownership, and module docs
- Title:
  - Make docs an accurate operational asset
- Problem:
  - Current docs preserve stale module and runtime stories.
- Solution:
  - Rewrite docs from current architecture and automate inventory validation.
- Impacted modules:
  - `products/yappc/README.md`
  - `products/yappc/docs/*`
- Dependencies:
  - P0/P1 topology decisions.
- Implementation steps:
  - Update README and architecture docs.
  - Regenerate module catalog from build metadata where possible.
  - Add docs validation for missing packages and stale paths.
- Cleanup steps:
  - Delete references to empty compat packages and removed modules.
- Tests:
  - Docs validation task in CI.
- O11y/security requirements:
  - Include runtime ports, auth ownership, and observability policy in ops docs.
- Deployment/runtime tasks:
  - Ensure operator runbooks reflect the chosen topology.
- Acceptance criteria:
  - Engineers can determine current ownership from docs without reading source first.

### P3

#### Action P3.1: Add AI/ML-native operator value safely
- Title:
  - Productize recommendations, summarization, and semantic retrieval
- Problem:
  - AI modules exist, but operator-facing value and evaluation are not systematically packaged.
- Solution:
  - Add AI features where they reduce friction and are observable, reviewable, and privacy-safe.
- Impacted modules:
  - `core/ai`
  - `core/agents`
  - `core/yappc-agents`
  - lifecycle services and frontend surfaces
- Dependencies:
  - P0/P1 correctness and privacy controls.
- Implementation steps:
  - Add lifecycle recommendation service.
  - Add artifact/project summarization.
  - Add semantic retrieval over approved data classes.
  - Capture feedback and confidence signals.
- Cleanup steps:
  - Remove unsupported “AI magic” UX claims that lack backend support.
- Tests:
  - Offline evals, acceptance tests, and confidence/fallback tests.
- O11y/security requirements:
  - Quality telemetry, prompt redaction, abuse monitoring, cost/latency budgets.
- Deployment/runtime tasks:
  - Feature flags and staged rollout.
- Acceptance criteria:
  - AI features improve operator workflows measurably and fail safely.

#### Action P3.2: Reduce UI and component duplication
- Title:
  - Simplify frontend package structure after topology decisions
- Problem:
  - Shared vs product-specific UI/component boundaries appear noisy and partially duplicated.
- Solution:
  - Consolidate components/hooks/utilities into the clearest owning packages after app topology is simplified.
- Impacted modules:
  - `products/yappc/frontend/web`
  - `products/yappc/frontend/libs`
- Dependencies:
  - P1.2 frontend topology selection.
- Implementation steps:
  - Inventory repeated components/hooks.
  - Move only truly shared pieces into shared libs.
  - Keep one-off Yappc UI in the product app.
- Cleanup steps:
  - Delete stale wrappers and duplicate variants.
- Tests:
  - Component behavior tests and accessibility checks.
- O11y/security requirements:
  - Preserve frontend error reporting and state-safety during refactors.
- Deployment/runtime tasks:
  - None beyond normal frontend release validation.
- Acceptance criteria:
  - Shared UI packages represent real reuse, not speculative or historical splits.

## 10. Production Checklist Status

### Product & Feature
- `Fail` - Feature scope is complete
- `Partial` - All major workflows are implemented
- `Partial` - Edge cases are handled
- `Partial` - Multi-state behavior is supported
- `Partial` - User roles/personas are respected
- `Partial` - AI/ML opportunities were evaluated and applied where appropriate

### Architecture & Reuse
- `Partial` - Existing shared libraries were reviewed first
- `Partial` - Reuse decisions were documented
- `Fail` - No unjustified new abstractions were introduced
- `Fail` - No duplicate logic/components/contracts remain
- `Fail` - Module and library boundaries are clear
- `Partial` - Product-specific code is not misplaced in shared libraries

### Data Cloud
- `Partial` - Data ingestion/storage/retrieval paths are correct
- `Partial` - Schema/index/constraints are appropriate
- `Partial` - Retention/deletion/privacy rules are handled
- `Partial` - Data isolation boundaries are correct
- `Partial` - Data contracts are clean and validated

### Audio/Video
- `Fail` - Core media workflows are complete
- `Fail` - Media errors and degraded-network cases are handled
- `Fail` - Media access is properly secured
- `Fail` - Media performance/latency risks were reviewed
- `Fail` - Media pipeline telemetry exists where needed

### Security & Auth
- `Fail` - Authentication is correct
- `Partial` - Authorization is correctly enforced
- `Partial` - Sensitive data handling is minimized and protected
- `Fail` - Secret/token/session handling is safe
- `Partial` - Security risks were reviewed
- `Partial` - Tenant/workspace isolation is handled where relevant
- `Partial` - Auditability exists where needed

### Monitoring / O11y / Operations
- `Partial` - Structured logging exists
- `Partial` - Metrics exist for key flows
- `Partial` - Tracing exists for critical paths
- `Partial` - Correlation IDs or equivalent trace linkage exist
- `Partial` - Alerts/SLO indicators are identifiable
- `Partial` - Operational debugging is possible
- `Partial` - Business and AI quality telemetry exist where needed

### Performance & Scalability
- `Partial` - Critical performance paths were reviewed
- `Partial` - Query/data/render/media inefficiencies were addressed
- `Partial` - Caching/background processing was considered
- `Partial` - Scalability bottlenecks were identified and addressed
- `Partial` - Rate limiting/idempotency/retry behavior is handled where needed

### UI / UX
- `Partial` - UI is consistent and accessible where applicable
- `Partial` - UX is simple and low cognitive load where applicable
- `Partial` - Empty/loading/error/success states are handled
- `Partial` - Actions are discoverable and coherent
- `Partial` - Navigation and workflows are complete where applicable

### Deployment & Delivery
- `Fail` - Build and release flow is production ready
- `Fail` - Environment/config/secrets handling is safe
- `Partial` - Health/readiness checks exist
- `Partial` - Rollout/rollback path exists
- `Partial` - CI/CD supports validation and release
- `Fail` - Runtime assumptions are documented

### Testing
- `Partial` - Unit tests were added/updated
- `Partial` - Integration tests were added/updated
- `Partial` - E2E tests were added/updated for critical flows where applicable
- `Partial` - Security/privacy relevant tests were included
- `Partial` - Performance tests were added where necessary
- `Partial` - AI/ML evaluation tests were included where necessary

## 11. Final Recommendation
- Go/no-go readiness:
  - `No-go` for production-grade readiness on the current repo state.
- Blockers:
  - Runtime/probe/CI drift.
  - Split auth ownership and insecure defaults.
  - Duplicate backend/controller/module trees.
  - Broken or misleading Data Cloud query semantics.
  - Incomplete audio/video and non-scalable realtime architecture.
  - Release gates that do not yet prove the real deployed system.
- Next actions:
  - Execute the `P0` workstream first and do not layer new features on top of the current runtime/auth ambiguity.
  - Move immediately into `P1` boundary cleanup so Data Cloud, frontend topology, privacy, collaboration, and media scope are explicit.
  - Use `P2` to rebuild release confidence and docs after the architecture is simplified.
  - Reserve `P3` for AI/ML-native enhancement and UI cleanup only after the foundation is trustworthy.
