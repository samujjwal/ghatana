# V2 Product Deep Audit: `data-cloud`, `aep`, `yappc`

Audit date: 2026-03-20  
Repo: `/Users/samujjwal/Development/ghatana`

Method: static monorepo inspection of product roots, build/package manifests, selected hotspot files, test/config surfaces, and lightweight validations.

Completed validations used as evidence:

- `pnpm --filter @data-cloud/ui type-check` failed in `products/data-cloud/ui/src/lib/mock-data.ts:923`
- `pnpm --filter @ghatana/aep-gateway test` passed: 1 file, 10 tests
- `pnpm -C products/yappc/frontend run verify:workspace` failed because `products/yappc/frontend/scripts/verify-workspace-deps.js:20` requires a non-existent local `../pnpm-workspace.yaml`

Codebase scale snapshot:

| Product | Gradle modules | JS/TS packages | Java files | TS/TSX files | Test files |
|---|---:|---:|---:|---:|---:|
| `data-cloud` | 9 | 1 | 666 | 265 | 92 |
| `aep` | 12 | 2 | 559 | 71 | 96 |
| `yappc` | 46 | 28 | 2,377 | 2,055 | 784 |

## Part 1 - Executive Assessment

## 1. Executive Verdict

Combined V2 readiness is **No-Go**.

- `aep` is the closest to deployable shape.
- `data-cloud` has real platform depth, but its UI and launcher-quality surfaces are not release-clean.
- `yappc` has the most capability breadth, but also the highest architectural drift, tooling breakage, and ownership ambiguity.

## 2. Executive Risk Summary

| Risk | Severity | Why it matters |
|---|---|---|
| Broken product validation surfaces | Critical | `@data-cloud/ui` fails type-check; `yappc/frontend` workspace verification is broken |
| Naming and package-governance drift | High | Shared TS package expected as `@ghatana/platform-utils` does not exist; consumers point to it anyway |
| Runtime boundary confusion | High | `yappc/frontend` contains a backend Node API app and scripts targeting a missing `apps/web` package |
| Oversized orchestrator files | High | `DataCloudHttpServer.java`, `ProductionModule.java`, and `libs/canvas/src/index.ts` are too large for safe change velocity |
| Tenant isolation defaults | High | YAPPC still has `"default"` fallbacks and a global subscription path in runtime code |
| Observability underwired | Medium-High | AEP/Data Cloud still instantiate `NoopMetricsCollector` in product runtime paths |
| Generated and duplicated bulk masking health | Medium | YAPPC hotspots are inflated by generated Prisma artifacts and exact duplicate command classes |

## 3. Audit Scope and Boundaries

In scope:

- `products/data-cloud`
- `products/aep`
- `products/yappc`
- shared libraries they directly consume from `platform/typescript`, `platform/java`, and `products/yappc/frontend/libs`

Boundary notes:

- This audit is evidence-based but not an exhaustive runtime certification.
- Java integration suites, container orchestration, and full end-to-end product flows were not fully executed in this turn.
- File-level scoring focuses on critical/hotspot files rather than every file in the repo.

## 4. Product Mission and Responsibilities

| Product | Mission reconstructed from repo | Primary responsibility |
|---|---|---|
| `data-cloud` | Event store, streaming substrate, agent registry, storage/query/governance platform | Durable event/data platform and product-facing persistence APIs |
| `aep` | Agentic event processor and operator pipeline runtime | Event routing, operator execution, analytics/pattern processing |
| `yappc` | AI-native product development platform spanning planning, generation, lifecycle, and canvas UX | Product orchestration layer consuming AEP and Data Cloud |

## 5. In-Scope Modules / Packages / Files

Primary modules reviewed:

- `data-cloud`: `platform`, `launcher`, `agent-registry`, `ui`
- `aep`: `platform-core`, `platform-analytics`, `orchestrator`, `server`, `gateway`, `ui`
- `yappc`: `backend/api`, `services/lifecycle`, `core/scaffold`, `core/domain`, `frontend`, `frontend/apps/api`, `frontend/libs/canvas`, `frontend/libs/ui`
- shared TS libs: `@ghatana/design-system`, `@ghatana/theme`, `platform-utils` directory, `@ghatana/ui-integration`, `@ghatana/canvas`, `@ghatana/flow-canvas`, `@yappc/code-editor`

Critical files inspected:

- `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`
- `products/data-cloud/ui/src/lib/mock-data.ts`
- `products/data-cloud/ui/src/lib/theme.ts`
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
- `products/aep/platform-core/src/main/java/com/ghatana/core/pipeline/PipelineExecutionEngine.java`
- `products/aep/gateway/src/index.ts`
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java`
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/aep/AepServiceClient.java`
- `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/TriggerListenerBootstrap.java`
- `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java`
- `products/yappc/core/domain/src/main/java/com/ghatana/products/yappc/domain/agent/http/AgentController.java`
- `products/yappc/frontend/package.json`
- `products/yappc/frontend/scripts/verify-workspace-deps.js`
- `products/yappc/frontend/libs/canvas/src/index.ts`
- `platform/typescript/foundation/platform-utils/package.json`
- `platform/typescript/capabilities/design-system/package.json`
- `platform/typescript/ui-integration/package.json`

## 6. High-Level Readiness Assessment

| Product | Readiness | Summary |
|---|---|---|
| `data-cloud` | Amber-Red | Strong backend platform ambition, but UI correctness and launcher maintainability are below release standard |
| `aep` | Amber-Green | Modularized better than peers and has a healthy gateway test surface, but still too much server orchestration and weak production observability defaults |
| `yappc` | Red | Broad capability set, but delivery surface is internally inconsistent and frontend/workspace topology is materially drifted from docs/scripts |

## Part 2 - Product & Dependency Topology

## 7. Product Topology Reconstruction

| Product | Reconstructed topology |
|---|---|
| `data-cloud` | Java platform core plus launcher and registry; React UI depends on shared TS design/canvas/theme libraries |
| `aep` | Java modular core plus server and orchestrator; small Fastify gateway and separate React UI |
| `yappc` | Large Java product with backend services, scaffold/core engines, lifecycle services, plus a separate frontend workspace and an embedded Node API app under `frontend/apps/api` |

## 8. Internal Dependency Map

| From | To | Evidence | Assessment |
|---|---|---|---|
| `data-cloud/platform` | `platform-entity`, `platform-event`, `platform-config`, `platform-analytics`, `spi`, multiple `platform:java:*` libs | `products/data-cloud/platform/build.gradle.kts` | Deep but understandable; module count is good, file sizes are not |
| `data-cloud/ui` | `@ghatana/design-system`, `@ghatana/theme`, `@ghatana/canvas`, `@ghatana/realtime`, `@ghatana/flow-canvas`, `@yappc/code-editor` | `products/data-cloud/ui/package.json` | Good reuse intent, but dependency naming drift undermines it |
| `aep/server` | AEP platform modules + `products:data-cloud:platform` | `products/aep/server/build.gradle.kts` | AEP is tightly coupled to Data Cloud, which is expected but should be contract-first |
| `aep/gateway` | Fastify-only BFF | `products/aep/gateway/package.json` | Small and bounded; best-maintained TS surface in scope |
| `yappc/backend/api` | YAPPC backend modules + `services:lifecycle` + `core:ai` + `core:framework` + `products:aep:platform-bundle` + `products:data-cloud:platform` | `products/yappc/backend/api/build.gradle.kts` | Very high fan-in; central module is overloaded |
| `yappc/frontend` | libs-based frontend workspace plus `apps/api` Node backend | `products/yappc/frontend/package.json`, `products/yappc/frontend/apps/api/package.json` | Boundary violation; frontend root is also backend runtime host |

## 9. Platform Integration Map

| Product | Platform integrations observed |
|---|---|
| `data-cloud` | Kafka, PostgreSQL, ClickHouse, OpenSearch, Redis, S3/Glacier, gRPC, Helm/K8s, feature store/model registry |
| `aep` | Data Cloud event stream, gRPC, HTTP, pipeline registry, deployment orchestration, JWT-gated gateway |
| `yappc` | AEP platform bundle, Data Cloud platform, OpenAI/Anthropic wiring, Prisma/Postgres, Prometheus/Micrometer, GraphQL Yoga/Fastify in frontend API app |

## 10. Third-Party Dependency Map

| Area | Key dependencies | Comments |
|---|---|---|
| Java async/runtime | ActiveJ | Consistent across products |
| TS frontend | React 19, Vite 7, Vitest 4, Playwright | Modern baseline |
| Product UIs | Jotai, React Query, React Flow | Common UX stack |
| Data systems | PostgreSQL, Kafka, ClickHouse, OpenSearch, Redis | Data Cloud carries most platform ops weight |
| AI | OpenAI/Anthropic integration, feature-store/model-registry wiring | Mostly strongest in YAPPC |
| Observability | Micrometer, Prometheus, OpenTelemetry deps | Present in manifests, inconsistently active in runtime paths |

## 11. Ownership Model

| Layer | Likely owner | Audit assessment |
|---|---|---|
| Product runtime logic | product teams | Reasonable |
| Shared Java platform | platform team | Reuse is strong, but coupling is heavy |
| Shared TS platform libs | platform/frontend platform team | Governance is inconsistent; names and contracts drift |
| Docs and migration plans | individual product teams | Documentation volume is high but often stale |

## 12. Product vs Shared Responsibility Matrix

| Capability | Product-owned | Shared-owned |
|---|---|---|
| Event storage and streaming | `data-cloud` | `platform:java:*` primitives |
| Operator execution | `aep` | Data Cloud contracts/platform types |
| Product lifecycle orchestration | `yappc` | AEP bundle + Data Cloud platform |
| Design system/theme/canvas | product UIs consume | `platform/typescript/*`, `yappc/frontend/libs/*` |
| Authz, audit, observability | mixed | Shared Java platform is heavily reused |

## Part 3 - Deep Quality Audit

## 13. Product Architecture Audit

`data-cloud` and `aep` show a recognizable product-core plus launcher/server split. `yappc` does not have the same clarity: it is simultaneously a Java product platform, a TS workspace, a frontend library farm, and a Node API runtime inside `frontend/apps/api`. The biggest architecture problem is not lack of modules; it is **too many modules with weakly enforced boundaries and stale migration residue**.

## 14. Frontend Audit

Findings:

- `data-cloud/ui` has good shared-library reuse intent, but correctness is not release-clean because type-check fails in `mock-data.ts`.
- `aep/ui` is smaller and likely easier to stabilize, but its backing gateway and Java API contract coverage appear thin outside unit tests.
- `yappc/frontend` is the weakest frontend topology in scope: root scripts target `apps/web`, yet the repo currently has no `apps/web/package.json` and no source tree there, while docs still reference `apps/web/src/**` extensively.

## 15. Backend Audit

Findings:

- `data-cloud` backend is functionally rich but too concentrated in mega-classes and mega-plugins.
- `aep` backend is better decomposed, but `AepHttpServer.java` is still a large façade with route wiring plus product logic.
- `yappc/backend/api` is a central dependency magnet. `ProductionModule.java` alone has more than 100 provider bindings and imports across product, platform, AEP, Data Cloud, and AI services.

## 16. Data / Contract Audit

Strengths:

- AEP has explicit OpenAPI sync enforcement in `products/aep/server/build.gradle.kts`.
- Data Cloud and YAPPC both show contract/data surface awareness in OpenAPI, DB migrations, and schema folders.

Weaknesses:

- YAPPC splits runtime contract truth across Java APIs, a Node/Prisma API app, generated Prisma types, and frontend libs.
- Generated artifacts dominate hotspot counts, which makes contract changes hard to reason about in reviews.

## 17. Event / Workflow Audit

Strengths:

- Data Cloud and AEP both encode event-centric product identities clearly.
- YAPPC appropriately depends on AEP rather than reinventing pipeline execution.

Weaknesses:

- YAPPC lifecycle runtime still contains permissive tenant defaults and global subscription behavior, which weakens event isolation guarantees.

## 18. Shared Library Usage Audit

Good:

- `data-cloud/ui` consumes shared theme/design/canvas packages rather than cloning them.
- YAPPC frontend is intentionally library-first.

Bad:

- shared TS package naming is inconsistent: consumers require `@ghatana/platform-utils`, but the actual package is named `@ghatana/utils`
- the same bad peer dependency appears in multiple shared libs, making the issue systemic rather than local

## 19. Reuse vs Duplication Audit

| Pattern | Evidence | Assessment |
|---|---|---|
| Good reuse | `data-cloud/ui` depends on shared design/theme/canvas libs | Positive architectural direction |
| Exact duplication | `products/yappc/core/scaffold/cli/.../ADRCommand.java` and `products/yappc/core/scaffold/api/.../ADRCommand.java` are byte-identical | Clear delete/merge candidate |
| Documentation/runtime drift | `products/yappc/frontend/docs/**` repeatedly references `apps/web/src/**`, but the current tree has no live `apps/web` source package | Stale migration residue |
| Generated bulk | `products/yappc/frontend/apps/api/src/generated/prisma/index.d.ts` is 89k+ LOC | Review noise and maintenance drag |

## 20. Naming Audit

| Issue | Evidence | Impact |
|---|---|---|
| Package name mismatch | `platform/typescript/foundation/platform-utils/package.json` declares `@ghatana/utils`, while consumers ask for `@ghatana/platform-utils` | Broken mental model and likely broken installs outside happy paths |
| Namespace inconsistency | `@data-cloud/ui` and `@aep/ui` bypass `@ghatana/*` naming used elsewhere | Weak repo-wide package governance |
| Misleading workspace placement | `products/yappc/frontend/apps/api` is a backend Fastify/GraphQL/Prisma service | Confuses ownership, CI, and package boundaries |
| Missing-but-scripted app | `products/yappc/frontend/package.json` scripts target `web`, but there is no package manifest there | Delivery confusion |

## 21. Module-Level Audit

| Module | Product | Audit summary | Score /10 |
|---|---|---|---:|
| `platform` | data-cloud | Rich but overgrown platform core with too many large plugin/service classes | 5.5 |
| `launcher` | data-cloud | Operationally important but oversized HTTP façade and noop metrics usage | 5.0 |
| `ui` | data-cloud | Good reuse direction, blocked by compile correctness issues | 4.5 |
| `platform-core` | aep | Solid engine core with strong test presence; still broad | 6.8 |
| `server` | aep | Viable canonical entrypoint, but too much route and runtime responsibility in one class | 6.3 |
| `orchestrator` | aep | Useful separation, still tightly coupled to product runtime | 6.5 |
| `gateway` | aep | Small, focused, tested | 8.0 |
| `ui` | aep | Smaller and more stabilizable than peers, but needs stronger integration proof | 6.5 |
| `backend/api` | yappc | Central module is too large and dependency-dense | 4.5 |
| `services/lifecycle` | yappc | Important orchestration layer with tenant/default risks | 5.0 |
| `core/scaffold` | yappc | Valuable capability, but duplication and sprawl remain | 5.5 |
| `frontend/libs/canvas` | yappc | Powerful but extremely large export surface and operational sprawl | 4.5 |

## 22. Package-Level Audit

| Package | Assessment | Score /10 |
|---|---|---:|
| `@data-cloud/ui` | Reuse intent good, compile health bad | 4.5 |
| `@aep/ui` | Reasonable package scope | 6.5 |
| `@ghatana/aep-gateway` | Best-scoped TS package inspected | 8.0 |
| `@ghatana/design-system` | Useful shared foundation, but peer dep naming drift | 6.0 |
| `@ghatana/ui-integration` | Same peer dep drift; unclear adoption boundary | 5.5 |
| `@ghatana/canvas` | Broad capability, large maintenance surface | 5.5 |
| `@ghatana/flow-canvas` | Small and focused | 7.2 |
| `@yappc/code-editor` | Sensible bounded package | 7.0 |
| `@ghatana/yappc-frontend` | Root package scripts drift from actual workspace | 4.0 |
| `@yappc/api-app` | Misplaced backend app inside frontend workspace | 4.8 |

## 23. File-Level Audit

Scoring rule: 0 is poor/high risk, 10 is strong/low risk.

| File | Responsibility | Naming | Complexity | Cohesion | Testability | Maintainability | Side effects | Security | Notes |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java` | 4 | 7 | 2 | 3 | 4 | 3 | 3 | 5 | 1,993 LOC HTTP façade with many route families |
| `products/data-cloud/ui/src/lib/mock-data.ts` | 3 | 6 | 3 | 4 | 5 | 4 | 5 | 7 | 970 LOC mock file; current type-check failure |
| `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java` | 5 | 7 | 4 | 5 | 6 | 5 | 4 | 6 | Better than Data Cloud equivalent, still too large |
| `products/aep/platform-core/src/main/java/com/ghatana/core/pipeline/PipelineExecutionEngine.java` | 7 | 8 | 6 | 7 | 7 | 6 | 7 | 7 | Core logic is dense but purposefully cohesive |
| `products/aep/gateway/src/index.ts` | 8 | 8 | 7 | 8 | 7 | 8 | 7 | 8 | Cleanest runtime TS file reviewed |
| `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java` | 2 | 6 | 1 | 2 | 3 | 2 | 4 | 5 | 1,620 LOC DI god-module |
| `products/yappc/frontend/libs/canvas/src/index.ts` | 3 | 5 | 2 | 2 | 5 | 2 | 6 | 7 | 1,625 LOC export barrel; weak package encapsulation |
| `products/yappc/frontend/scripts/verify-workspace-deps.js` | 4 | 7 | 6 | 5 | 6 | 4 | 7 | 8 | Broken by incorrect workspace path assumption |
| `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/aep/AepServiceClient.java` | 6 | 7 | 5 | 6 | 6 | 5 | 5 | 4 | Tenant fallback still returns default tenant |
| `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/TriggerListenerBootstrap.java` | 6 | 7 | 5 | 6 | 6 | 5 | 3 | 3 | Global subscription path weakens tenant isolation |

## 24. Test Audit

Observations:

- `data-cloud` and `aep` have meaningful Java and UI test presence.
- `aep/gateway` tests are healthy but narrow: only JWT helper coverage was observed.
- `yappc` has the most tests, but volume is not the same as confidence because tooling and workspace drift are still present.

Test gaps table:

| Gap | Evidence | Risk |
|---|---|---|
| Data Cloud UI compile failure despite tests | `@data-cloud/ui` type-check failed | UI test suite cannot be fully trusted as release gate |
| AEP gateway lacks proxy/WebSocket integration tests | Only `src/__tests__/jwt.test.ts` was validated | Security passes, proxy behavior unproven |
| YAPPC frontend workspace verification is broken | `verify-workspace-deps.js` cannot locate workspace file | CI/lint pipeline is not trustworthy |
| YAPPC missing current `apps/web` package but docs/scripts depend on it | `frontend/package.json` plus docs references | Web delivery path is unclear and likely untested |
| Tenant default/global paths need regression tests | `AepServiceClient`, `AgentController`, `YappcLifecycleService`, `TriggerListenerBootstrap` | Multi-tenant safety risk |

## 25. Security Audit

Main concerns:

- YAPPC still falls back to `"default"` tenant values in live code:
  - `products/yappc/core/domain/src/main/java/com/ghatana/products/yappc/domain/agent/http/AgentController.java:685`
  - `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java:394`
  - `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/aep/AepServiceClient.java:279`
- `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/TriggerListenerBootstrap.java:114` subscribes globally across tenants.
- AEP gateway does enforce JWT presence and signature checks and forwards tenant headers intentionally; that part is comparatively solid.

## 26. Observability Audit

Strengths:

- manifests and dependencies show serious observability intent across all three products
- YAPPC has Micrometer collector implementation and Prometheus endpoint wiring

Weaknesses:

- AEP still wires `new NoopMetricsCollector()` in server construction at `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java:154`
- Data Cloud launcher uses `NoopMetricsCollector` for AI metrics at `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudLauncher.java:210`
- observability readiness is therefore inconsistent: good platform capability, incomplete production activation

## 27. Build & Delivery Audit

Good:

- AEP server enforces OpenAPI sync.
- YAPPC root Gradle applies dependency governance constraints banning direct LangChain4J/Reactor/RxJava use.

Bad:

- Data Cloud UI does not type-check.
- YAPPC frontend verification script is broken.
- YAPPC frontend scripts target a missing `web` package.
- YAPPC delivery topology is hard to reason about because runtime surfaces are split between Java services and a Node API under `frontend/apps/api`.

## 28. DevEx Audit

The monorepo has strong ambition but weak current ergonomics.

- good: many scripts, docs, validations, and package segmentation
- bad: several of those scripts and docs are stale, which creates false confidence
- worst offender: YAPPC frontend, where docs, scripts, and filesystem topology are visibly out of sync

## 29. Performance Audit

Potential concerns:

- huge export surfaces and giant source files increase bundle and type-check cost
- generated Prisma code in YAPPC creates significant review and build overhead
- product cores are async/event-driven, which is good for scaling, but observability under-activation will make performance diagnosis harder

## 30. UX Flow Audit

`data-cloud/ui` and `aep/ui` look like product UIs built around known route/page surfaces. `yappc/frontend` is weaker because the intended `apps/web` delivery surface is not materially present in the current tree, while the docs still describe it as the central UX.

## Part 4 - Scoring

## 31. Product Scorecard

| Dimension | Data Cloud | AEP | YAPPC |
|---|---:|---:|---:|
| Architecture Quality | 6 | 7 | 5 |
| Code Quality | 5 | 6 | 5 |
| Dependency Hygiene | 5 | 6 | 4 |
| Naming Quality | 6 | 7 | 4 |
| Test Coverage | 6 | 7 | 7 |
| Security | 6 | 7 | 5 |
| Observability | 5 | 5 | 6 |
| Delivery Readiness | 4 | 7 | 4 |
| Maintainability | 4 | 6 | 3 |
| Scalability | 7 | 7 | 6 |
| UX Completeness | 5 | 6 | 4 |
| **Overall** | **5.4** | **6.5** | **4.8** |

## 32. Module Scores

See Section 21. Highest-scoring module in scope: `@ghatana/aep-gateway`. Lowest-scoring reviewed module: `yappc/backend/api`.

## 33. Package Scores

See Section 22.

## 34. File Hotspots

| File | LOC | Type | Why it is a hotspot |
|---|---:|---|---|
| `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java` | 1,993 | handwritten | Route concentration and side effects |
| `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java` | 1,620 | handwritten | DI god-module |
| `products/yappc/frontend/libs/canvas/src/index.ts` | 1,625 | handwritten | Barrel/export sprawl |
| `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java` | 917 | handwritten | Server façade with mixed concerns |
| `products/data-cloud/ui/src/lib/mock-data.ts` | 970 | handwritten | Huge mock surface; compile error source |
| `products/yappc/frontend/apps/api/src/generated/prisma/index.d.ts` | 89,273 | generated | Generated bulk dominating repo surface |
| `products/yappc/frontend/libs/canvas/src/features/stories/canvas-feature-stories.generated.ts` | 9,924 | generated | Huge generated TS artifact inside active library |

## 35. Delivery Readiness Score

| Product | Delivery readiness /10 | Why |
|---|---:|---|
| `data-cloud` | 4.5 | UI compile break and oversized operational surfaces |
| `aep` | 7.0 | Closest to shippable if observability and integration coverage improve |
| `yappc` | 4.0 | Broken verification, topology drift, missing scripted app surface |

## 36. Risk Hotspots

| Hotspot | Severity | Evidence |
|---|---|---|
| Shared TS package naming drift | Critical | `@ghatana/platform-utils` is referenced, but package is named `@ghatana/utils` |
| YAPPC frontend workspace drift | Critical | `frontend/package.json` targets `web`; repo lacks current `apps/web` package manifest/source |
| Data Cloud UI compile break | Critical | `mock-data.ts` type-check failure |
| YAPPC tenant defaults/global subscription | High | `AgentController`, `YappcLifecycleService`, `TriggerListenerBootstrap`, `AepServiceClient` |
| Noop metrics in product runtime | High | AEP/Data Cloud runtime code paths |
| Exact duplicate YAPPC command implementations | Medium | duplicated `ADRCommand.java` |

## 37. Critical Defects

| ID | Defect | Evidence |
|---|---|---|
| CD-1 | `@data-cloud/ui` does not type-check | `products/data-cloud/ui/src/lib/mock-data.ts:923` |
| CD-2 | Shared package contract is broken by name drift | `products/data-cloud/ui/package.json:26`, `platform/typescript/foundation/platform-utils/package.json:2`, `platform/typescript/capabilities/design-system/package.json:67`, `platform/typescript/ui-integration/package.json:44` |
| CD-3 | YAPPC frontend verification script is broken | `products/yappc/frontend/scripts/verify-workspace-deps.js:20` |
| CD-4 | YAPPC frontend scripts reference a missing app package | `products/yappc/frontend/package.json:12-18`, `:80-81` |
| CD-5 | YAPPC core contains exact duplicate command class | duplicate `ADRCommand.java` in scaffold `cli` and `api` modules |
| CD-6 | Tenant isolation still relies on `"default"` fallback behavior | `AgentController.java:685`, `YappcLifecycleService.java:394`, `AepServiceClient.java:279` |
| CD-7 | YAPPC lifecycle globally subscribes across tenants | `TriggerListenerBootstrap.java:114` |
| CD-8 | AEP/Data Cloud runtime observability is underwired | `AepHttpServer.java:154`, `DataCloudLauncher.java:210` |

## Part 5 - Target State

## 38. Target Architecture

Target shape:

- `data-cloud`: small launcher, narrow handlers, platform core split by capability
- `aep`: maintain modular core, move more route logic out of server façade
- `yappc`: strict separation between Java product platform, frontend workspace, and Node API services; no mixed ownership roots

## 39. Dependency Model

Target dependency rules:

- products depend on shared platform contracts, not each other’s oversized implementation modules where avoidable
- YAPPC should consume AEP/Data Cloud through thinner product-facing clients/contracts
- TS shared package names must be canonical and enforced in CI

## 40. Library Usage Model

- keep shared design/theme/canvas reuse
- reduce giant barrel exports
- move generated code behind dedicated generated packages and exclude from manual hotspot ownership

## 41. Platform Integration Model

- make AEP and Data Cloud integrations explicit adapter layers in YAPPC
- centralize tenant context enforcement before downstream calls
- standardize metrics/tracing bootstrap for all product entrypoints

## 42. Naming Model

- pick one canonical package name for platform utilities and enforce it
- rename or relocate `yappc/frontend/apps/api` if it remains a backend runtime
- remove dead `apps/web` references or restore the package with real ownership

## 43. Test & Delivery Model

- type-check, lint, and workspace verification must pass before tests are treated as signal
- gateway/BFFs need route, proxy, and websocket integration tests
- multi-tenant default/fallback paths require explicit security regression tests

## Part 6 - Execution Plan

## 44. Immediate Fixes

1. Fix `products/data-cloud/ui/src/lib/mock-data.ts` import/type error and re-run `type-check`.
2. Resolve `@ghatana/platform-utils` naming drift by renaming the shared package or updating all consumers.
3. Fix `products/yappc/frontend/scripts/verify-workspace-deps.js` to read the actual workspace definition.
4. Decide whether `apps/web` is missing or intentionally removed; then align scripts and docs in `products/yappc/frontend/package.json`.
5. Remove `"default"` tenant fallbacks from YAPPC runtime request paths.

## 45. Short-Term Plan

| Timeframe | Actions |
|---|---|
| 1-2 weeks | Split Data Cloud/AEP HTTP façade responsibilities into handler/service slices |
| 1-2 weeks | Add AEP gateway proxy/WebSocket tests |
| 1-2 weeks | Deduplicate YAPPC scaffold ADR command implementation |
| 1-2 weeks | Enable real metrics collector in AEP/Data Cloud runtime entrypoints |

## 46. Medium-Term Plan

| Timeframe | Actions |
|---|---|
| 2-6 weeks | Carve thinner YAPPC adapter modules for AEP and Data Cloud |
| 2-6 weeks | Move YAPPC Node API out of `frontend/` or formalize it as a separate backend workspace |
| 2-6 weeks | Reduce YAPPC canvas package export surface and split domain-focused entrypoints |
| 2-6 weeks | Separate generated Prisma/codegen output from handwritten source ownership |

## 47. Long-Term Plan

| Timeframe | Actions |
|---|---|
| 6-12 weeks | Introduce enforceable architecture rules for cross-product dependencies |
| 6-12 weeks | Standardize product launcher/server skeletons for auth, metrics, tracing, and tenant context |
| 6-12 weeks | Establish product ownership scorecards that reject stale docs/scripts against filesystem reality |

## 48. Rename / Move / Delete Plan

| Action | Type | Rationale |
|---|---|---|
| Rename `@ghatana/utils` to `@ghatana/platform-utils` or update consumers to `@ghatana/utils` | Rename | End package-name drift |
| Remove one duplicate `ADRCommand.java` implementation | Delete | Exact duplication |
| Move `products/yappc/frontend/apps/api` out of `frontend/` if it remains a backend service | Move | Clarify runtime boundaries |
| Delete stale `apps/web` references from scripts/docs if app no longer exists | Delete | Reduce false build instructions |
| Split `ProductionModule.java` provider sets into submodules | Move | Recover maintainability |

## 49. Test Improvement Plan

| Area | Improvement |
|---|---|
| Data Cloud UI | Block merge on `type-check` and contract tests |
| AEP gateway | Add Fastify proxy/WebSocket/CORS tests |
| YAPPC security | Add tenant-context regression suite for request, event, and AEP-client paths |
| YAPPC frontend | Restore or remove `apps/web` and then re-baseline e2e and smoke tests |
| Shared TS libs | Add package-resolution checks for workspace peer dependency names |

## 50. CI / Lint Enforcement Plan

| Enforcement | Purpose |
|---|---|
| Workspace package-name validation | Catch `@ghatana/platform-utils` style drift |
| Dead-path/script validator | Fail if scripts reference missing packages |
| Duplicate-file guard on critical source trees | Catch exact copies like duplicated ADR command |
| Generated-code boundaries | Exclude generated files from hotspot scoring but ensure regeneration reproducibility |
| Tenant-safety rule checks | Ban `"default"` tenant fallback in runtime request paths |

## Part 7 - Final

## 51. Go / No-Go Recommendation

**No-Go** for combined V2 product release.

Conditional product view:

- `aep`: near-term conditional go after observability activation and a small round of integration hardening
- `data-cloud`: no-go until UI build health and launcher maintainability improve
- `yappc`: no-go until workspace topology, tenant handling, and delivery scripts are aligned

## 52. Top 10 Fixes

1. Fix `@data-cloud/ui` type-check failure.
2. Resolve `@ghatana/platform-utils` naming drift repo-wide.
3. Repair YAPPC frontend workspace verification script.
4. Restore or remove the missing `apps/web` delivery path and align scripts/docs.
5. Remove YAPPC runtime `"default"` tenant fallbacks.
6. Replace AEP/Data Cloud runtime `NoopMetricsCollector` usage with real metrics wiring.
7. Split `ProductionModule.java` into feature-specific DI modules.
8. Split `DataCloudHttpServer.java` and `AepHttpServer.java` into smaller façade plus handler/service seams.
9. Delete the duplicate YAPPC `ADRCommand.java`.
10. Separate generated code from handwritten ownership and review hotpaths.

## 53. Final Conclusion

The monorepo does not suffer from lack of investment; it suffers from **investment without enough closure**. All three products show strong platform ambition and meaningful shared-library reuse, but only AEP is currently close to a disciplined release surface. Data Cloud needs correctness cleanup and server-size reduction. YAPPC needs a sharper reset: its current value proposition is broad, but its filesystem, scripts, docs, and runtime boundaries no longer agree with each other. Until that alignment work is done, any release confidence will be overstated.
