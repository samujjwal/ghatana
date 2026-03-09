# Ghatana Codebase Audit & Remediation Plan

**Version:** 1.0  
**Audit Date:** 2026-02-21  
**Scope:** Full monorepo — platform/java (22 modules), platform/typescript (12 modules), products (10), shared-services (5)  
**Total Files Audited:** ~2,800 Java source files, ~400 TypeScript source files, ~130 build configs  

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Audit Findings — Java Platform](#2-audit-findings--java-platform)
3. [Audit Findings — TypeScript Platform](#3-audit-findings--typescript-platform)
4. [Audit Findings — Products](#4-audit-findings--products)
5. [Audit Findings — Agentic System Architecture](#5-audit-findings--agentic-system-architecture)
6. [Remediation Plan](#6-remediation-plan)
7. [Future-Proofing Recommendations](#7-future-proofing-recommendations)

---

## 1. Executive Summary

### Health Scorecard

| Area | Score | Key Issue |
|------|-------|-----------|
| **Architecture** | 🟡 B- | Strong foundations, but cross-product coupling and 3 coexisting Agent interfaces fragment the system |
| **Code Duplication** | 🔴 D | 7 classpath-conflicting FQNs, 6 JwtTokenProvider copies, duplicate auth/security modules, product-level reimplementations |
| **Test Coverage** | 🔴 D | 9 Java platform modules with **zero** tests (234 untested source files); TypeScript at 4% test coverage |
| **Type Safety** | 🟡 C | 282 `any` uses in TypeScript; 20+ `@SuppressWarnings("rawtypes")` in Java |
| **Documentation** | 🟡 C+ | `auth` module has 0% `@doc` tags; `core` at 38%; TypeScript at 0-15% for foundational modules |
| **Build Hygiene** | 🟡 C | Compiled files in source dirs, dead Java modules, hardcoded versions, inconsistent group IDs |
| **ActiveJ Compliance** | 🟡 B- | `database` module uses `CompletableFuture` (21 occurrences); 76 CF-using files across products |
| **Agentic Extensibility** | 🟢 B+ | Strong SPI design, but MemoryPlane and ConsolidationPipeline have OCP violations; no DAG pipeline runtime |

### Critical Findings Summary

| # | Finding | Severity | Scope |
|---|---------|----------|-------|
| 1 | 7 classes with **identical FQN** between `core` and `governance` modules — classpath conflicts | **P0** | Platform |
| 2 | `auth` and `security` modules are **near-complete duplicates** (JWT x3, RBAC x4, AuthN x3) | **P0** | Platform |
| 3 | 9 platform modules with **zero tests** (234 source files: agent-memory, security, ai-integration, agent-learning, event-cloud, audit, connectors, governance, workflow) | **P0** | Platform |
| 4 | **3 coexisting Agent interfaces** — products use legacy/divergent agent contracts | **P0** | Cross-cutting |
| 5 | `observability` module is a **kitchen-sink** — 28 files (cache, connectors, session, DB routing, audit) should be in other modules | **P1** | Platform |
| 6 | Product code in platform: `flashit-shared` (TS), `yappc.ai.*` (Java), `ui/org/*` (TS) | **P1** | Cross-cutting |
| 7 | `CompletableFuture` violations: `database` module (21x), yappc (53 files), AEP (6), audio-video (6) | **P1** | Cross-cutting |
| 8 | Cross-product dependencies: software-org → virtual-org + AEP; virtual-org → AEP; yappc → data-cloud | **P1** | Products |
| 9 | TypeScript: 44+24 compiled `.js`/`.d.ts` files committed in `theme/src/` and `utils/src/` | **P1** | TypeScript |
| 10 | `design-system` deprecated but has 72 unique components not yet migrated to `ui` | **P1** | TypeScript |
| 11 | `context-policy` module is a **complete duplicate** of 8 classes already in `core` | **P2** | Platform |
| 12 | `config` module has internal duplication — partial migration between 2 package hierarchies | **P2** | Platform |
| 13 | No DAG pipeline runtime — `Workflow`/`WorkflowStep` are thin interfaces without DAG execution | **P2** | Platform |
| 14 | `MemoryPlane` interface violates OCP — tier-specific methods prevent adding new memory types | **P2** | Platform |
| 15 | Dead Java modules: `flashit/platform/java`, `flashit/backend/agent` — build config but zero source files | **P3** | Products |

---

## 2. Audit Findings — Java Platform

### 2.1 Classpath Conflicts (P0)

Seven classes exist with **identical fully-qualified names** in both `platform/java/core` and `platform/java/governance`:

| Class | FQN |
|-------|-----|
| `Governance` | `com.ghatana.platform.governance.Governance` |
| `CompatibilityPolicy` | `com.ghatana.platform.governance.CompatibilityPolicy` |
| `DataClassification` | `com.ghatana.platform.governance.DataClassification` |
| `StorageHints` | `com.ghatana.platform.governance.StorageHints` |
| `RbacPolicy` | `com.ghatana.platform.governance.rbac.RbacPolicy` |
| `Principal` | `com.ghatana.platform.governance.security.Principal` |
| `TenantContext` | `com.ghatana.platform.governance.security.TenantContext` |

**Root Cause:** `core` contains 8 files under `com.ghatana.platform.governance.*` and 4 files under `com.ghatana.platform.audit.*` that belong in their respective modules.  
**Fix:** Remove the copies from `core`; the `governance` module should be canonical.

### 2.2 Duplicate auth/security Modules (P0)

Both `platform/java/auth` (22 files) and `platform/java/security` (45 files) implement:

| Concept | `auth` | `security` | `domain` | `governance` |
|---------|--------|-----------|----------|-------------|
| JwtTokenProvider | 2 copies | 1 copy | — | — |
| Role | 1 | 1 | 1 | 1 |
| Permission | 1 | 1 | 1 | — |
| AuthenticationService | 1 | 3 (composite, JWT, provider) | — | — |
| AuthorizationService | 3 | 1 | — | — |
| RBAC filter | — | 1 | — | 1 |

**Fix:** Consolidate into a single `security` module. Move auth-specific concerns (password hashing, token store) into it.

### 2.3 Misplaced Code in Core (P1)

`platform/java/core` (115 files) contains packages that belong in other modules:

| Package in `core` | Belongs in | Files |
|-------------------|-----------|-------|
| `com.ghatana.platform.governance.*` | `governance` | 8 |
| `com.ghatana.platform.audit.*` | `audit` | 4 |
| `com.ghatana.platform.policy.*` | `context-policy` | 8 |
| `com.ghatana.platform.resilience.*` | Own module or `core` (assess) | 2 |
| `com.ghatana.core.state.*` | Own module or `runtime` | 1 |

### 2.4 Observability Kitchen-Sink (P1)

`platform/java/observability` (86 files) contains 28+ files unrelated to observability:

| Package | Files | Should Be In |
|---------|-------|-------------|
| `cache.*`, `cache.pubsub.*`, `cache.warming.*` | 12 | `database` |
| `connector.*`, `connector.impl.*` | 6 | `connectors` |
| `session.*` | 6 | `auth` or `security` |
| `db.*` (RoutingDataSource, ReplicaLagMonitor) | 4 | `database` |
| `com.ghatana.audit.AuditLogger` | 1 | `audit` |

### 2.5 Zero-Test Modules (P0)

| Module | Source Files | Risk Level |
|--------|-------------|------------|
| `agent-memory` | 72 | CRITICAL — JDBC persistence, retrieval pipelines, security filters |
| `security` | 45 | CRITICAL — JWT, RBAC, encryption, OAuth2 |
| `agent-learning` | 32 | HIGH — consolidation, evaluation, retention |
| `ai-integration` | 32+ | HIGH — LLM gateway, embedding, vector store |
| `governance` | 17 | HIGH — policy engine, tenant isolation |
| `workflow` | 12 | HIGH — `UnifiedOperator` is a core ADR commitment |
| `connectors` | 10 | MEDIUM — Kafka source/sink |
| `event-cloud` | 7 | MEDIUM — core event infrastructure |
| `audit` | 7 | LOW — but duplicated across 3 modules |

### 2.6 CompletableFuture Violations (P1)

**Platform:**
- `database/AsyncRedisCache.java` — 18 occurrences of `CompletableFuture`
- `database/DatabaseHealthCheck.java` — 3 occurrences
- `database/Cache.java` interface — defines CF-based async methods

**Products:**
- YAPPC: **53 files** (worst offender)
- AEP: 6 files
- audio-video: 6 files
- data-cloud: 4 files
- security-gateway: 3 files
- tutorputor: 3 files
- virtual-org: 1 file

### 2.7 Build System Issues (P2)

| Issue | Affected Modules |
|-------|-----------------|
| Hardcoded `activej:6.0-beta2` instead of version catalog | core, auth, observability, runtime, ai-integration |
| Inconsistent group IDs (`com.ghatana.core` vs `com.ghatana.platform` vs `com.ghatana.libs`) | connectors, ingestion, context-policy |
| Legacy `io.eventcloud` package names | testing (4 files) |
| `JUnit Jupiter API` as main dependency (not test-only) | runtime |
| Product code in platform library (`com.ghatana.yappc.ai.*`) | ai-integration (3 files) |

### 2.8 Additional Duplicates (P2)

| Class/Concept | Locations |
|---------------|-----------|
| `TenantId` | `core/types`, `core/core.types`, `domain/auth`, `domain/identity`, `domain/platform.domain.auth` |
| `UserId` | `domain/auth`, `domain/identity`, `domain/platform.domain.auth` |
| `Preconditions` | `core`, `database` |
| `ErrorResponse` | `http`, `observability` |
| `DatabaseHealthCheck` | `database`, `observability` |
| `HealthStatus` | `agent-framework`, `database`, `plugin` |
| `AsyncBridge` | `runtime` (2 copies within same module) |
| `ConfigManager` | `config` (2 copies — partial migration) |
| `Sphere` + related | `core` (8 files), `context-policy` (8 files — identical) |

### 2.9 Incomplete Implementations (TODOs/Stubs)

| Module | Critical TODOs |
|--------|----------------|
| `governance/GovernanceConfigManager` | **Completely empty** — `loadConfig()` and `watchConfig()` are stubs |
| `http/RequestSizeLimitFilter` | Security filter incomplete — no Content-Length validation |
| `workflow/WorkflowStepOperator` | "Integrate with OperatorCatalog to load and execute the actual operator" |
| `ai-integration/OpenAIEmbeddingService` | Stub — "Replace with actual OpenAI API calls when SDK 4.7.1 is ready" |
| `http/HttpServerBuilder` | Metrics endpoint returns "TODO: Integrate with observability" |
| `domain/GEventType` | `Event event = null; // TODO: new Event(...)` |
| `security/OidcSessionManager` | "Implement token revocation if needed" |

### 2.10 Documentation Gaps

| Module | `@doc` Tag Coverage |
|--------|-------------------|
| `auth` | **0%** (0/22) |
| `core` | 38% (44/115) |
| `config` | 72% (21/29) |
| `testing` | 80% (41/51) |
| All others | 81-100% |

---

## 3. Audit Findings — TypeScript Platform

### 3.1 Product Code in Platform (P0)

| Module | Violation | Fix |
|--------|-----------|-----|
| `flashit-shared` | **Entire module** is Flashit product code (emotions, tags, Flashit API client, moment types) | Move to `products/flashit/shared/` |
| `canvas` | Stale `"./plugins/yappc"` export in `package.json` pointing to nonexistent dir | Remove export |
| `ui` | `components/org/` has software-org-specific components (`DepartmentStatusBadge`, `AgentAvatar`, `KpiCard`, `OrgMap`) | Move to `products/software-org/frontend/` |

### 3.2 Build Output in Source Directories (P1)

| Module | Compiled Files in `src/` |
|--------|--------------------------|
| `theme` | **44 files** (`.js`, `.d.ts`, `.map`) |
| `utils` | **24 files** (`.js`, `.d.ts`, `.map`) |

**Fix:** Configure `tsconfig.json` `outDir` properly; add `src/**/*.js` to `.gitignore`; delete committed build artifacts.

### 3.3 Deprecated design-system (P1)

`@ghatana/design-system` is marked deprecated but contains **72 unique source files** with components not available in `@ghatana/ui`:

**Components needing migration:** `FormStepper`, `HoverCard`, `Tour`, `PullToRefresh`, `SwipeableCard`, `ResponsiveImage`, `ResponsiveTable`, `ContextIndicator`, `PageTransition`, `FocusTrap`, `EmptyState`, `ConfirmDialog`, `LiveRegion`

**Duplicate subsystems to delete (already in `@ghatana/tokens`):** `design-system/src/tokens/` (colors, spacing, typography, shadows, radii, animations)

### 3.4 Duplicate WebSocket Clients (P1)

`@ghatana/realtime` exports **two** WebSocket client implementations:
- `client.ts` (495 lines) — full-featured with heartbeat, middleware, reconnect
- `WebSocketClient.ts` (183 lines) — simpler variant

Additionally, `products/yappc/frontend/libs/realtime/` has its own `WebSocketClient.ts`.

**Fix:** Keep `client.ts` as canonical; deprecate/remove `WebSocketClient.ts`; migrate YAPPC to use `@ghatana/realtime`.

### 3.5 Cross-Product Frontend Duplication

| Utility | Platform Canonical Location | Duplicated In |
|---------|---------------------------|---------------|
| `cn()` | `@ghatana/utils` | yappc/web, yappc/libs/ui, dcmaar/agent-core |
| `ErrorBoundary` | `@ghatana/ui` | flashit/mobile, flashit/web, dcmaar/browser-ext, dcmaar/desktop, yappc/libs/ui |
| `ThemeProvider` | `@ghatana/theme` | dcmaar/agent-core/ui, yappc/libs/ui |
| Accessibility helpers | `@ghatana/utils/accessibility` | `@ghatana/ui/utils/accessibility`, `@ghatana/accessibility-audit` |

### 3.6 `any` Type Usage

| Module | Count | Priority |
|--------|-------|----------|
| canvas | 103 | HIGH |
| ui | 46 | HIGH |
| accessibility-audit | 42 | MEDIUM (mostly tests) |
| realtime | 30 | MEDIUM (mostly tests) |
| charts | 23 | MEDIUM |
| flashit-shared | 18 | MEDIUM |
| Total | **282** | |

### 3.7 Test Coverage (TypeScript)

| Module | Test Files | Source Files | Coverage |
|--------|-----------|--------------|----------|
| accessibility-audit | 5 | 14 | Good |
| ui | 8 | 137 | Low |
| realtime | 1 | 7 | Minimal |
| tokens | 1 | 13 | Minimal |
| canvas | 1 | 98 | Very poor |
| api, charts, design-system, diagram, flashit-shared, theme, utils | 0 | 130+ | **None** |
| **Total** | **16** | **399** | **4%** |

### 3.8 Documentation (TypeScript)

| Module | `@doc` Tag Coverage |
|--------|-------------------|
| tokens | 0% (0/13) |
| utils | 0% (0/6) |
| accessibility-audit | 0% (0/14) |
| theme | 8% (1/12) |
| ui | 15% (21/137) |
| charts | 33% (4/12) |
| design-system | 29% (21/72) |
| api | 57% (4/7) |
| flashit-shared | 63% (5/8) |
| realtime | 86% (6/7) |
| canvas | 70% (68/97) |
| diagram | 100% (13/13) |

---

## 4. Audit Findings — Products

### 4.1 Cross-Product Dependency Violations (P1)

| Source | Depends On | Type | Severity |
|--------|-----------|------|----------|
| software-org | virtual-org:modules:framework | Deep coupling — all 10 departments import `com.ghatana.virtualorg.framework.*` | **HIGH** |
| software-org | aep:platform | Agent contracts for DevSecOps | **HIGH** |
| virtual-org | aep:platform | 4 modules depend on AEP | **MEDIUM** |
| yappc | data-cloud:platform | 3 active dependencies | **MEDIUM** |
| aep | data-cloud:spi | SPI interfaces only | **LOW** |

**Fix:** Extract shared abstractions into platform modules:
1. `virtual-org:modules:framework` → Extract agent/workflow interfaces to `platform:java:agent-framework`
2. `aep:platform` agent contracts → Extract to `platform:java:agent-framework` or a new `platform:java:agent-contracts` SPI module
3. `yappc → data-cloud:platform` → Extract shared data access patterns to platform

### 4.2 EventloopTestBase Compliance

| Product | Tests Using EventloopTestBase | Total Tests | Compliance |
|---------|------------------------------|-------------|------------|
| security-gateway | 11 | 12 | **92%** |
| virtual-org | 25 | 40 | 63% |
| tutorputor | 9 | 18 | 50% |
| data-cloud | 8 | 23 | 35% |
| aep | 0 | 23 | **0%** |
| software-org | 0 | 4 | **0%** |

### 4.3 Product-Specific Issues

**AEP:**
- 20+ files still use "eventcloud" in class/package names (legacy naming)
- 269 potential blocking calls (`.get()`, `.join()`, `Thread.sleep`) in non-test code
- `com.ghatana.core.*` packages in product src (should be `com.ghatana.aep.*`)

**Audio-Video:**
- **Not integrated into root build** — has its own `settings.gradle.kts`
- Reimplements platform auth/observability (JwtTokenProvider, UserPrincipal, SecurityGateway, MetricsCollector, TracingProvider, TracingManager)
- Uses `com.ghatana.platform.*` package prefix for product code (split-package risk)

**Flashit:**
- Java modules (`platform/java`, `backend/agent`) declare dependencies but have **zero source files** — dead modules

**YAPPC:**
- 53 files using `CompletableFuture` — worst offender
- Has its own `platform/activej/activej-runtime` and `activej-websocket` — overlaps `platform:java:runtime`
- Product-local observability reimplementation in refactorer
- `platform:java:testing` used as **main** (not test-only) dependency

**Software-Org:**
- Deeply coupled to virtual-org — all 10 departments import `com.ghatana.virtualorg.framework.*`

### 4.4 Shared-Services Assessment

All 5 modules are thin launcher/adapter microservices (8 total Java source files). They are included in the build but **not referenced by any product**.

| Module | Status |
|--------|--------|
| ai-inference-service | 2 src, 1 test — functional |
| ai-registry | 1 src, 0 tests — launcher only |
| auth-gateway | 3 src, 0 tests — custom RateLimiter should use platform's |
| auth-service | 1 src, 0 tests — **missing `@doc` tags** |
| feature-store-ingest | 1 src, 0 tests — launcher only |

---

## 5. Audit Findings — Agentic System Architecture

### 5.1 Extensibility Assessment

| Area | Rating | Notes |
|------|--------|-------|
| Agent creation (new types) | **A** | ServiceLoader-based `AgentProvider` SPI; JAR-drop extensibility |
| Agent lifecycle (PERCEIVE→REFLECT) | **A** | Template Method in `BaseAgent`; each phase overridable |
| Agent composition | **B+** | `CompositeAgent`, `OrchestrationStrategy`, `DelegationManager`, `ConversationManager` |
| Agent registry | **A** | `UnifiedAgentRegistry` with hot-reload support |
| Memory retrieval | **A-** | Pluggable `RetrievalPipeline` + `HybridRetriever`; `ContextInjector` is concrete (not an interface) |
| Memory types (add new) | **C** | `MemoryPlane` has tier-specific methods — violates OCP |
| Memory governance | **C+** | `MemoryRedactionFilter` has hardcoded regex patterns |
| Learning evaluation | **A** | Composable `EvaluationGate` + `CompositeEvaluationGate` |
| Learning consolidation | **B-** | `ConsolidationPipeline` hardcodes 2 paths; no pluggable stages |
| Retention policies | **A** | Pluggable `DecayFunction` with 3 built-in implementations |
| Operator creation | **A** | `UnifiedOperator` with string-based capabilities; `AbstractOperator` plumbing |
| Pipeline/DAG execution | **D** | No DAG runtime — `Workflow`/`WorkflowStep` are thin stubs |
| Agent config inheritance | **D** | No template/override mechanism — each product builds config from scratch |

### 5.2 Three Generations of Agent Interfaces

| Generation | Interface | Used By | Status |
|------------|----------|---------|--------|
| Gen 1 (legacy) | `com.ghatana.agent.Agent` (untyped `process(T, AgentContext)`) | virtual-org | Should migrate to Gen 3 |
| Gen 2 (duplicate) | `com.ghatana.aep.domain.agent.Agent` (event-specific) | AEP (deprecated marker) | Should migrate to Gen 3 |
| Gen 2b (divergent) | `com.ghatana.ai.agent.Agent` (simple `process()+getName()`) | yappc/core/ai | Should migrate to Gen 3 |
| **Gen 3 (current)** | `com.ghatana.agent.framework.runtime.BaseAgent<I,O>` (GAA lifecycle) | tutorputor, yappc/sdlc-agents | **Canonical** |

### 5.3 Open-Closed Principle Violations

| Component | Violation | Fix |
|-----------|-----------|-----|
| `AgentType` enum | Adding types requires modifying platform | Add `CUSTOM(String)` variant |
| `MemoryPlane` interface | Tier-specific methods (`storeEpisode`, `storeFact`, etc.) | Add generic `store(MemoryItem)` + type-dispatched query |
| `MemoryItemType` enum | Adding types requires modifying platform | Use extensible registry pattern |
| `MemoryRedactionFilter` | Hardcoded regex patterns | Accept `List<Pattern>` via config |
| `ConsolidationPipeline` | Hardcoded 2-path consolidation | Use `List<ConsolidationStage>` |
| `ContextInjector` | Concrete class, not an interface | Extract `ContextInjector` interface |

### 5.4 Missing Infrastructure

1. **No DAG pipeline runtime** — `Pipeline` and `PipelineBuilder` are referenced in UnifiedOperator JavaDoc but don't exist
2. **No agent configuration inheritance** — no template/override mechanism for agent configs
3. **No framework-level agent-to-workflow bridge** — `WorkflowAgentService` exists in agent-framework but is tightly coupled
4. **`CompositeAgent` loses type safety** — forces `Map<String, Object>` for I/O

---

## 6. Remediation Plan

### Phase 1: Critical Fixes (Week 1-2)

> **Goal:** Eliminate classpath conflicts, consolidate duplicates, unblock builds

#### 1.1 Resolve Classpath Conflicts (P0)

**Task:** Remove 8 governance files and 4 audit files from `platform/java/core/src/main/java/com/ghatana/platform/governance/` and `.../audit/`  
**Owner:** Platform team  
**Risk:** LOW — governance module already has canonical copies  
**Steps:**
1. Verify governance module has identical (or superset) implementations
2. Delete duplicates from core
3. Update any core-internal references to import from governance module
4. Add governance module as dependency to core (if not already)
5. Run full build + tests

#### 1.2 Consolidate auth into security (P0)

**Task:** Merge `platform/java/auth` (22 files) into `platform/java/security` (45 files)  
**Owner:** Platform team  
**Risk:** MEDIUM — many downstream consumers  
**Steps:**
1. Audit all imports of `com.ghatana.auth.*` and `com.ghatana.platform.auth.*` across all products
2. Move non-duplicate auth files into `security` module under appropriate packages
3. For the 3 duplicate `JwtTokenProvider` copies, keep the most complete one
4. For the 4 duplicate `Role` copies, canonicalize in `security`
5. Create package-level `@Deprecated` forwarding classes for backward compatibility (1 release cycle)
6. Update all product `build.gradle.kts` to depend on `security` instead of `auth`
7. Remove `auth` module from `settings.gradle.kts` after migration

#### 1.3 Remove context-policy Duplication (P0)

**Task:** Delete `platform/java/context-policy` — all 8 classes are identical to `core/com.ghatana.platform.policy.*`  
**Owner:** Platform team  
**Risk:** LOW — verify no consumers reference `context-policy` directly  
**Steps:**
1. Search for `":platform:java:context-policy"` in all build files
2. Replace with `":platform:java:core"` (policy classes are already in core)
3. Remove module from `settings.gradle.kts`

#### 1.4 Add Tests to Critical Zero-Test Modules (P0)

**Task:** Add minimum viable test suites (at least integration smoke tests) for the 3 highest-risk zero-test modules  
**Priority order:** `security` → `agent-memory` → `workflow`  
**Target:** ≥ 30% coverage per module  
**Steps per module:**
1. Create test class extending `EventloopTestBase`
2. Test public API surface — constructors, key methods, error paths
3. For `security`: JWT generation/validation, RBAC evaluation, encryption round-trip
4. For `agent-memory`: Store/retrieve for each memory type, retrieval pipeline, redaction filter
5. For `workflow`: Operator lifecycle, operator registration, operator execution

---

### Phase 2: Architectural Cleanup (Week 3-4)

> **Goal:** Enforce module boundaries, remove cross-product coupling, fix ActiveJ compliance

#### 2.1 Decompose Observability Module (P1)

**Task:** Move 28 misplaced files from `observability` to appropriate modules  
**Steps:**
1. Move `cache.*`, `cache.pubsub.*`, `cache.warming.*` (12 files) → `database`
2. Move `connector.*`, `connector.impl.*` (6 files) → `connectors`
3. Move `session.*` (6 files) → `security`
4. Move `db.*` (RoutingDataSource, ReplicaLagMonitor) (4 files) → `database`
5. Move `AuditLogger` → `audit`
6. Delete duplicate `ErrorResponse` (keep in `http`)
7. Delete duplicate `DatabaseHealthCheck` (keep in `database`)
8. Update import paths in all consumers

#### 2.2 Move Product Code Out of Platform (P1)

**Java:**
1. Move `com.ghatana.yappc.ai.{abtesting, cache, cost}` (3 files) from `platform/java/ai-integration` → `products/yappc/core/ai`

**TypeScript:**
1. Move `platform/typescript/flashit-shared/` → `products/flashit/shared/`
2. Move `platform/typescript/ui/src/components/org/` → `products/software-org/frontend/libs/org-components/`
3. Remove stale `"./plugins/yappc"` export from `platform/typescript/canvas/package.json`
4. Update all import paths in consuming products

#### 2.3 Fix CompletableFuture Violations in Platform (P1)

**Task:** Convert `database` module's `AsyncRedisCache`, `Cache`, and `DatabaseHealthCheck` to use ActiveJ `Promise`  
**Steps:**
1. Replace `CompletableFuture<T>` return types with `Promise<T>`
2. Use `Promise.ofBlocking(executor, () -> ...)` for blocking Redis/DB calls
3. Update all consumers (especially in `observability`)
4. Run all dependent tests

#### 2.4 Break Cross-Product Dependencies (P1)

**Task:** Extract shared abstractions used across products into platform modules  
**Steps:**
1. **virtual-org framework extraction:**
   - Identify interfaces from `virtual-org:modules:framework` that `software-org` imports
   - Extract agent/workflow SPIs to `platform:java:agent-framework` or new `platform:java:org-framework`
   - Repoint both software-org and virtual-org to the platform module
2. **AEP agent contracts extraction:**
   - Extract `com.ghatana.aep.domain.agent.Agent` contract to `platform:java:agent-framework`
   - Create adapter to bridge to `BaseAgent<I,O>` (Gen 3)
   - Repoint virtual-org and software-org to platform
3. **YAPPC → data-cloud:**
   - Identify the specific data-cloud APIs that yappc uses
   - Extract to `data-cloud:spi` (already exists) or a new interface module

#### 2.5 Clean Build Artifacts from TypeScript Sources (P1)

**Steps:**
1. Delete 44 compiled files from `platform/typescript/theme/src/`
2. Delete 24 compiled files from `platform/typescript/utils/src/`
3. Fix `tsconfig.json` `outDir` in both modules to point to `dist/` (not `src/`)
4. Add `*.js`, `*.d.ts`, `*.js.map` patterns to `.gitignore` under `platform/typescript/*/src/`
5. Delete `theme/temp-package.json`
6. Delete `accessibility-audit/src/AccessibilityAuditor.old.ts` and `AccessibilityReportViewer.old.tsx`

---

### Phase 3: Consistency & Standards (Week 5-6)

> **Goal:** Follow naming conventions, add docs, standardize builds, expand test coverage

#### 3.1 Unify Agent Interfaces (P0 — Deferred to Phase 3 due to scope)

**Task:** Migrate all products to Gen 3 agent framework (`BaseAgent<I,O>`)  
**Priority order:** AEP (deprecated flag already present) → virtual-org → yappc/core/ai  
**Steps per product:**
1. Create product-specific `BaseAgent` subclass extending `com.ghatana.agent.framework.runtime.BaseAgent`
2. Implement GAA lifecycle methods (perceive, act, capture, reflect)
3. Wire existing logic into lifecycle phases
4. Update registrations, tests, and configs
5. Mark legacy agent classes `@Deprecated(forRemoval = true, since = "3.0")`
6. Remove legacy adapter classes (e.g., `LegacyAgentAdapter`, `VirtualOrgEventAdapter`) after migration

#### 3.2 Fix Build System Inconsistencies (P2)

**Steps:**
1. **Version catalog:** Replace all hardcoded `activej:6.0-beta2` with `libs.activej.*` references in: core, auth, observability, runtime, ai-integration
2. **Group IDs:** Standardize to `com.ghatana.platform` for: connectors (`com.ghatana.core`), ingestion (`com.ghatana.core`), context-policy (`com.ghatana.libs`)
3. **JUnit in main scope:** Move JUnit dependency to `testImplementation` in runtime module; move `EventloopTestExtension`/`EventloopTestRunner` to `testing` module
4. **Legacy packages:** Rename `io.eventcloud.testing.*` (4 files) → `com.ghatana.platform.testing.*` in testing module
5. **Dead modules:** Remove `products:flashit:platform:java` and `products:flashit:backend:agent` from `settings.gradle.kts`

#### 3.3 Add `@doc` Tags (P2)

**Priority order (by worst coverage):**

| Module | Current | Target | Files to Tag |
|--------|---------|--------|-------------|
| `auth` (before consolidation) or `security` (after) | 0% | 100% | 22-67 |
| `core` | 38% | 90%+ | ~71 |
| `config` | 72% | 90%+ | ~8 |
| `testing` | 80% | 90%+ | ~10 |
| TypeScript `tokens` | 0% | 80%+ | 13 |
| TypeScript `utils` | 0% | 80%+ | 6 |
| TypeScript `theme` | 8% | 80%+ | 11 |
| TypeScript `ui` | 15% | 60%+ | ~95 |

#### 3.4 Complete Internal Module Migrations (P2)

1. **`config` module:** Merge `com.ghatana.config.runtime.*` and `com.ghatana.platform.config.*` — keep one package hierarchy
2. **`domain` module:** Remove duplicate `UserId` (exists in 3 packages); canonicalize `TenantId` to one location
3. **`core` module:** Remove duplicate `TenantId` between `com.ghatana.platform.types` and `com.ghatana.platform.core.types`
4. **`runtime` module:** Remove duplicate `AsyncBridge` (2 copies); merge into one
5. **AEP legacy naming:** Rename `eventcloud` references in 20+ files to use `datacloud` or `aep` naming

#### 3.5 Expand Test Coverage (P2)

**Java Platform — add tests to remaining zero-test modules:**

| Module | Minimum Tests Needed | Key Areas |
|--------|---------------------|-----------|
| `ai-integration` | 8 | LLMGateway mock, embedding mock, prompt templates |
| `agent-learning` | 6 | ConsolidationPipeline, EvaluationGate composition, RetentionManager |
| `event-cloud` | 4 | EventCloud CRUD, EventStream, AppendResult |
| `connectors` | 4 | InMemory source/sink, event flow |
| `governance` | 4 | PolicyEngine evaluation, tenant isolation |
| `audit` | 3 | AuditService write/query, reporting |

**Products — improve EventloopTestBase compliance:**

| Product | Current | Target |
|---------|---------|--------|
| AEP | 0% | 50%+ |
| software-org | 0% | 50%+ |
| data-cloud | 35% | 70%+ |

**TypeScript — add tests to foundational modules:**

| Module | Tests Needed |
|--------|-------------|
| `api` | HTTP client, middleware pipeline, retry logic |
| `utils` | `cn()`, formatters, platform detection |
| `theme` | ThemeProvider rendering, dark mode toggle, brand presets |
| `tokens` | Token values, CSS variable generation |

#### 3.6 Migrate design-system to ui (P1)

**Steps:**
1. Identify 13+ unique components in `design-system` not in `ui`
2. Move each component to appropriate `ui` atom/molecule/organism location
3. Move unique hooks to `ui/hooks/`
4. Delete `design-system/src/tokens/` (duplicates `@ghatana/tokens`)
5. Update all product imports from `@ghatana/design-system` to `@ghatana/ui`
6. Delete `design-system` package after all imports are updated
7. Remove from `pnpm-workspace.yaml`

#### 3.7 Reduce TypeScript `any` Usage (P2)

**Priority targets (highest count first):**
1. `canvas` (103 instances) — replace `as any` with proper generics in test utils; type event handlers
2. `ui` (46 instances) — fix `ref as any` pattern in Typography components; type `Record<string, any>` accessibly helpers
3. `charts` (23 instances) — replace `data?: any[]` with `data?: ChartDataPoint[]` generics

---

### Phase 4: Future-Proofing (Week 7-8, Ongoing)

> **Goal:** Ensure the architecture scales without rewrites

#### 4.1 Genericize MemoryPlane (P2)

**Task:** Add generic `store(MemoryItem)` / `query(MemoryQuery)` methods alongside tier-specific ones  
**Steps:**
1. Add `Promise<Void> store(MemoryItem item)` to `MemoryPlane` interface
2. Add `Promise<List<MemoryItem>> query(MemoryQuery query)` to `MemoryPlane` interface
3. Implement type-dispatching in existing implementations
4. Retain tier-specific methods as convenience overloads (non-breaking)
5. Make `MemoryItemType` extensible (registry pattern or sealed interface with `Custom(String)`)

#### 4.2 Build DAG Pipeline Runtime (P2)

**Task:** Implement the `PipelineBuilder` and `DAGPipelineExecutor` referenced in `UnifiedOperator` JavaDoc  
**Steps:**
1. Create `Pipeline` interface with DAG structure (nodes = operators, edges = data flow)
2. Create `PipelineBuilder` fluent API supporting: sequential, parallel fork/join, conditional branch
3. Create `DAGPipelineExecutor` that executes using ActiveJ `Promise` combinators
4. Support checkpoint/recovery via `event-cloud` appendable log
5. Wire into `WorkflowStepOperator` (currently has TODO for this)

#### 4.3 Make ConsolidationPipeline Pluggable (P2)

**Steps:**
1. Extract `ConsolidationStage` interface from each hardcoded consolidation path
2. Refactor `ConsolidationPipeline` to accept `List<ConsolidationStage>`
3. Make `EpisodicToSemanticConsolidator` and `EpisodicToProceduralConsolidator` implement `ConsolidationStage`
4. Allow products to inject custom stages (e.g., `EpisodicToPreferenceConsolidator`)

#### 4.4 Add Agent Configuration Inheritance (P2)

**Steps:**
1. Create `AgentConfigTemplate` in `agent-framework` — a YAML-mergeable base config
2. Define platform-level defaults (retry policy, timeout, memory config, observation level)
3. Allow product-level configs to `extends: platform-defaults` and override specific fields
4. Implement `AgentConfigMaterializer.materialize(template, overrides)` merge logic

#### 4.5 Make AgentType Extensible (P3)

**Steps:**
1. Add `CUSTOM` variant to `AgentType` enum: `CUSTOM(String customType)`
2. OR migrate to string-based typing with a validation registry
3. Update `UnifiedAgentRegistry` to handle custom types

#### 4.6 Externalize MemoryRedactionFilter Patterns (P3)

**Steps:**
1. Extract PII/credential/PHI regex patterns to a YAML config file
2. Accept custom patterns via constructor injection
3. Add `RedactionPatternProvider` SPI for products to contribute patterns

---

## 7. Future-Proofing Recommendations

### 7.1 Architectural Guardrails

| Guardrail | Implementation |
|-----------|---------------|
| **No classpath conflicts** | Add ArchUnit test: `noClasses().that().resideInAPackage("com.ghatana.platform.governance..").should().resideInModule("core")` |
| **No cross-product deps** | Add Gradle constraint: products cannot depend on other products (only platform and own submodules) |
| **No CompletableFuture** | Add ArchUnit test: all `Promise`-returning methods must not reference `CompletableFuture` |
| **EventloopTestBase enforcement** | Add ArchUnit test: all test classes with `@Test` methods using `Promise` must extend `EventloopTestBase` |
| **`@doc` tag enforcement** | Add Checkstyle/PMD rule requiring `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` on all public classes |
| **No `any` in TypeScript** | Enable `strict: true` and `noImplicitAny: true` in all `tsconfig.json` files |

### 7.2 Module Boundary Enforcement

```
Allowed Dependency Flow:
  products/* → platform/java/*, platform/typescript/*
  platform/java/* → platform/contracts
  shared-services/* → platform/java/*
  
Forbidden:
  products/* → products/*  (extract to platform if needed)
  platform/* → products/*  (never)
  platform/* → shared-services/*  (never)
```

### 7.3 Testing Strategy

| Level | Coverage Target | Framework |
|-------|----------------|-----------|
| Unit (Java) | 60%+ per module | JUnit 5 + `EventloopTestBase` + AssertJ |
| Integration (Java) | 30%+ per module | TestContainers + platform `testing` module |
| Unit (TypeScript) | 50%+ per module | Vitest + React Testing Library |
| E2E (TypeScript) | Critical paths only | Playwright |
| Architecture | 100% rule coverage | ArchUnit |

### 7.4 Technical Debt Tracking

Introduce `@TechnicalDebt` annotation for Java / `// @tech-debt` comment for TypeScript:

```java
@TechnicalDebt(
    category = "DUPLICATION",
    description = "Uses legacy Agent interface instead of BaseAgent<I,O>",
    ticket = "GH-1234",
    targetRemovalVersion = "3.0"
)
```

### 7.5 Scaling Considerations

| Area | Current State | Recommendation |
|------|---------------|----------------|
| **Memory storage** | JDBC only | Add pluggable storage backends (RocksDB for local, Redis for distributed) via SPI |
| **Agent discovery** | In-memory registry | Add distributed registry (etcd/Consul) SPI for multi-node deployments |
| **Event processing** | Single-node | Ensure `event-cloud` supports partitioned streams for horizontal scaling |
| **Pipeline execution** | No DAG runtime | Build DAG executor with checkpoint/recovery for fault tolerance |
| **Configuration** | File-based | Add remote config source (etcd, Consul, ConfigMap) with watch for hot-reload |
| **Multi-tenancy** | Code-level isolation | Verify all platform modules respect `TenantContext` — add integration tests |

---

## Appendix A: Full Duplication Matrix

| Concept | Locations (Module:Package) | Canonical Location |
|---------|--------------------------|-------------------|
| `JwtTokenProvider` | auth (×2), security (×1), audio-video (×1), security-gateway (×2) | **security** |
| `Role` | auth, security, governance, domain | **security** |
| `Permission` | auth, security, domain | **security** |
| `TenantId` | core/types, core/core.types, domain/auth, domain/identity, domain/platform.domain.auth | **core/types** |
| `UserId` | domain/auth, domain/identity, domain/platform.domain.auth | **domain/identity** |
| `Governance` | core, governance | **governance** |
| `DataClassification` | core, governance | **governance** |
| `StorageHints` | core, governance | **governance** |
| `Principal` | core/governance, governance | **governance** |
| `TenantContext` | core/governance, governance | **governance** |
| `Sphere` (×8 classes) | core/policy, context-policy | **core/policy** |
| `ErrorResponse` | http, observability | **http** |
| `DatabaseHealthCheck` | database, observability | **database** |
| `HealthStatus` | agent-framework, database, plugin | **core** (extract) |
| `Preconditions` | core, database | **core** |
| `AsyncBridge` | runtime (×2) | **runtime** (merge) |
| `ConfigManager` | config (×2) | **config** (merge) |
| `AuditService` | core, audit | **audit** |
| `AuditEvent` | core, domain | **domain** |
| `Agent` interface | agent-framework, ai-integration, aep, domain | **agent-framework** |
| `MemoryStore` | agent-framework, agent-memory | **agent-memory** |
| `cn()` (TS) | utils, yappc (×2), dcmaar | **@ghatana/utils** |
| `ErrorBoundary` (TS) | ui, flashit (×2), dcmaar (×2), yappc | **@ghatana/ui** |
| `ThemeProvider` (TS) | theme, dcmaar, yappc | **@ghatana/theme** |
| `WebSocketClient` (TS) | realtime (×2), yappc | **@ghatana/realtime** |
| `Design tokens` (TS) | tokens, design-system | **@ghatana/tokens** |

---

## Appendix B: Module Dependency Graph (Simplified)

```
platform/contracts
    ↑
platform/java/core ← domain, event-cloud
    ↑
platform/java/{database, http, observability, config, runtime}
    ↑
platform/java/{auth, security, governance, audit, connectors, ingestion, context-policy, plugin, workflow}
    ↑
platform/java/{agent-framework, ai-integration}
    ↑
platform/java/{agent-memory, agent-learning}
    ↑
products/{aep, data-cloud, yappc, virtual-org, software-org, tutorputor, security-gateway, flashit, audio-video, dcmaar}
    ↑
shared-services/{ai-inference-service, ai-registry, auth-gateway, auth-service, feature-store-ingest}
```

---

## Appendix C: Remediation Effort Estimates

| Phase | Tasks | Estimated Effort | Risk |
|-------|-------|-----------------|------|
| **Phase 1** — Critical Fixes | 4 task groups | 2 weeks (1 dev) | LOW-MEDIUM |
| **Phase 2** — Architectural Cleanup | 5 task groups | 2 weeks (2 devs) | MEDIUM |
| **Phase 3** — Consistency & Standards | 7 task groups | 2 weeks (2 devs) | LOW |
| **Phase 4** — Future-Proofing | 6 task groups | 2+ weeks (ongoing) | MEDIUM-HIGH |
| **Total** | 22 task groups | **~8 weeks** | |

---

*Generated by comprehensive codebase audit — 2026-02-21*
