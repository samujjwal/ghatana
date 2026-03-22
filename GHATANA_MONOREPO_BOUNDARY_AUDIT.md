# Ghatana Monorepo — Boundary, Right-Sizing, and Code Placement Audit

**Audit Date:** March 21, 2026  
**Auditor:** Principal Engineering Team  
**Scope:** Entire Monorepo (`/Users/samujjwal/Development/ghatana`)  
**Focus:** Boundary correctness, over/under-engineering, code placement, simplification opportunities

---

## Part 1 — Executive Summary

### 1. Executive Verdict

**CONDITIONAL NO-GO** — The monorepo demonstrates significant architectural ambition but suffers from fundamental boundary violations, extensive over-engineering, and systematic under-engineering across critical paths. **Immediate intervention required** before production scale.

### 2. Main Boundary Problems

| Problem | Severity | Impact |
|---------|----------|--------|
| **YAPPC Excessive Granularity** | CRITICAL | 48+ Gradle modules for single product |
| **TutorPutor Service Sprawl** | CRITICAL | 9 services, 16/18 failing TypeScript builds |
| **Data-Cloud Lombok Builder Crisis** | HIGH | Disabled modules, broken builds |
| **Platform/Domain Confusion** | HIGH | `domain/yappc/` vs `products/yappc/core/domain` |
| **Cross-Product AI Integration Duplication** | MEDIUM | AI-integration scattered across products |
| **Shared-Services Ambiguity** | MEDIUM | Unclear ownership for auth, AI registry |

### 3. Main Over-Engineering Problems

| Problem | Severity | Example |
|---------|----------|---------|
| **Micro-Module Proliferation** | CRITICAL | YAPPC scaffold: `core/scaffold/api/http`, `core/scaffold/api/grpc`, `core/scaffold/cli`, `core/scaffold/core` |
| **Abstraction Layers Without Consumers** | HIGH | `platform:java:agent-*` modules with unclear product consumers |
| **Speculative Extensibility** | HIGH | `products:finance:extensions` — empty pack extension placeholder |
| **Wrapper-on-Wrapper Pattern** | MEDIUM | Multiple `platform:java:kernel:modules:*` wrappers around core concepts |
| **Interface Proliferation** | MEDIUM | `platform:contracts` proto + POJOs + mappers + JSON schemas — 4 representation layers |

### 4. Main Under-Engineering Problems

| Problem | Severity | Example |
|---------|----------|---------|
| **TypeScript Build Failures** | CRITICAL | 16/18 TutorPutor modules failing compilation |
| **Disabled Data-Cloud Modules** | HIGH | ML, AI, Attention, Memory modules moved to `disabled/` |
| **Missing Integration Boundaries** | HIGH | `products:aep:platform-core` excludes 20+ test files due to classpath issues |
| **God Module Remnants** | MEDIUM | `products:aep:platform-core` with 215 items, mixed concerns |
| **Weak Contract Boundaries** | MEDIUM | Cross-product gRPC contracts lack versioning strategy |

### 5. Top Structural Risks

1. **Build System Instability** — Product-level settings.gradle.kts files create standalone/monorepo mode complexity
2. **Dependency Convergence Failure** — Multiple LangChain4j versions, Jackson BOM mismatches
3. **Circular Dependency Cycles** — `services:core` → `domain` → `services:core` (463 files)
4. **Lombok Annotation Processing Breakdown** — Data-Cloud builders not generating
5. **TypeScript/Java Duality** — Platform/typescript vs platform/java split without clear ownership

### 6. High-Level Simplification Opportunities

| Opportunity | Current | Target | Effort |
|-------------|---------|--------|--------|
| Merge YAPPC scaffold modules | 7+ modules | 2 modules (api, core) | M |
| Consolidate TutorPutor services | 9 services | 3 services (platform, ai, content) | L |
| Flatten Data-Cloud bounded contexts | 4 platform-* modules | 1 core module + SPI | M |
| Unify AI integration layer | Scattered across products | Single `platform:java:ai` | L |
| Merge agent framework modules | agent-api, agent-spi, agent-framework | 1 agent-core | S |

---

## Part 2 — Boundary Model

### 7. Current Boundary Model

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         GHATANA MONOREPO                                │
├─────────────────────────────────────────────────────────────────────────┤
│  GLOBAL SHARED                                                          │
│  ├── platform/java/core, domain, database, http, observability         │
│  ├── platform/typescript/capabilities/canvas-core                      │
│  ├── platform/typescript/capabilities/design-system                    │
│  ├── platform/contracts (protobuf + OpenAPI)                            │
│  └── shared-services/ai-registry, auth-gateway, auth-service             │
├─────────────────────────────────────────────────────────────────────────┤
│  PRODUCTS (13 products, 17000+ items)                                   │
│  ├── aep/ (734 items) — Event processing platform                       │
│  ├── yappc/ (6637 items) — AI-native product platform                   │
│  ├── data-cloud/ (1084 items) — Multi-tenant metadata                   │
│  ├── tutorputor/ (1145 items) — AI tutoring platform                    │
│  ├── dcmaar/ (2961 items) — AI platform guardian                      │
│  ├── finance/ (420 items) — Financial operating system                  │
│  ├── flashit/ (541 items) — Context capture                           │
│  ├── audio-video/ (359 items) — Speech, vision, intelligence          │
│  ├── software-org/ (1651 items) — Software organization sim           │
│  ├── virtual-org/ (407 items) — Virtual organization                  │
│  ├── security-gateway/ (105 items) — OAuth/OIDC/RBAC                   │
│  ├── phr/ (107 items) — Personal health records                         │
│  └── app-platform/ (915 items) — Legacy app platform                    │
├─────────────────────────────────────────────────────────────────────────┤
│  DOMAIN (Unclear Purpose)                                               │
│  └── domain/yappc/ (22 items) — Duplicates products/yappc/core/domain  │
├─────────────────────────────────────────────────────────────────────────┤
│  APPS (Single Application)                                              │
│  └── apps/yappc-web/ (1114 items) — Only app in apps/ directory        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 8. Recommended Boundary Model

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    RECOMMENDED GHATANA BOUNDARIES                       │
├─────────────────────────────────────────────────────────────────────────┤
│  TRULY GLOBAL SHARED (Minimal, Stable, Domain-Neutral)                  │
│  ├── platform/core/ — Async primitives, logging, types                  │
│  ├── platform/database/ — Connection management, transactions            │
│  ├── platform/observability/ — Metrics, tracing, logging                 │
│  ├── platform/security/ — Auth primitives, JWT validation                │
│  └── platform/contracts/ — Cross-product protobuf contracts              │
├─────────────────────────────────────────────────────────────────────────┤
│  PRODUCT-OWNED SHARED (Owned by one product, reused by others)         │
│  ├── products/data-cloud/platform-sdk/ — Reused by YAPPC, AEP            │
│  ├── products/aep/agent-framework/ — Reused for event processing        │
│  └── products/yappc/scaffold-runtime/ — Reused for product generation   │
├─────────────────────────────────────────────────────────────────────────┤
│  PRODUCT-LOCAL (Each product self-contained)                            │
│  ├── aep/ — Event processing, pipelines, operators                      │
│  ├── yappc/ — Product generation, scaffold, agents                        │
│  ├── data-cloud/ — Metadata, storage tiers, plugins                     │
│  ├── tutorputor/ — Content, learning kernel, assessments                  │
│  ├── dcmaar/ — Threat detection, AI guardians                         │
│  ├── finance/ — Trading, risk, compliance domains                       │
│  ├── flashit/ — Context capture, multimodal                             │
│  ├── audio-video/ — STT, TTS, vision services                           │
│  ├── software-org/ — Simulation engine                                  │
│  ├── virtual-org/ — Virtual workspace                                   │
│  ├── security-gateway/ — Auth service                                   │
│  ├── phr/ — Health records                                              │
│  └── app-platform/ — LEGACY — migrate to YAPPC                          │
├─────────────────────────────────────────────────────────────────────────┤
│  CROSS-PRODUCT SERVICES (Clear ownership, shared tenancy)                │
│  ├── shared-services/auth-service/ — Own: Platform Team                  │
│  ├── shared-services/ai-registry/ — Own: AI Platform Team               │
│  └── shared-services/feature-store/ — Own: Data Team                    │
├─────────────────────────────────────────────────────────────────────────┤
│  DEPRECATED/TO BE REMOVED                                             │
│  ├── domain/ — Merge into products or delete                           │
│  ├── apps/ — Move into product directories                              │
│  └── platform/typescript/capabilities/ — Move to product-owned          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 9. Definition of Product-Local vs Product-Owned Shared vs Truly Global Shared

| Classification | Definition | Examples | Admission Criteria |
|----------------|------------|----------|-------------------|
| **Product-Local** | Code used exclusively by one product, evolves with that product's needs | `tutorputor:learning-kernel`, `finance:domains:risk` | No cross-product imports, product-specific naming |
| **Product-Owned Shared** | Code primarily owned by one product but legitimately reused by others | `data-cloud:platform-sdk` (consumed by YAPPC, AEP), `yappc:scaffold-runtime` | Clear owner, stable API, documented contracts |
| **Truly Global Shared** | Domain-neutral code reusable across all products without modification | `platform:core:Promise`, `platform:observability:Metrics`, `platform:security:JwtValidator` | Domain-neutral, stable API, no product leakage |

### 10. Ownership Principles

1. **Single Owner Principle**: Every module has exactly one owning product/team
2. **Change Cohesion**: Code that changes together lives together
3. **Consumer Visibility**: Owners must know all consumers of their shared code
4. **Explicit Contracts**: Shared boundaries must have explicit, versioned contracts
5. **Graduation Path**: Product-local → Product-owned shared → Global shared (never reverse)

### 11. Code Placement Principles

1. **Start Product-Local**: New code starts in the product that needs it
2. **Extract When Proven**: Extract to shared only after 2+ products need it
3. **Keep Near Owner**: Even shared code lives near its owning product
4. **Avoid Global Dumping**: `platform/` should be sparse and stable
5. **Delete When Unused**: Code with no consumers is deleted, not "kept for later"

### 12. Shared Library Admission Criteria

| Criterion | Global Shared | Product-Owned Shared | Product-Local |
|-----------|---------------|---------------------|---------------|
| Cross-product usage | 3+ products | 2+ products | 1 product |
| Domain neutrality | Required | Preferred | Not required |
| API stability | Required | Required | Flexible |
| Owner clarity | Platform Team | Product Team | Product Team |
| Documentation | Full API docs | Consumer contracts | Internal docs |
| Test coverage | >80% | >70% | >60% |
| Breaking changes | Semver major | Coordinated | Product-only |

---

## Part 3 — Monorepo Structure Audit

### 13. Product-Level Placement Audit

| Product | Items | Boundary Status | Key Issues | Recommendation |
|---------|-------|-----------------|------------|----------------|
| **aep** | 734 | ✅ Mostly Correct | Test exclusions in platform-core | Keep, reduce test exclusions |
| **yappc** | 6637 | ❌ Over-Engineered | 48+ Gradle modules, excessive granularity | Consolidate to ~15 modules |
| **data-cloud** | 1084 | ⚠️ Partially Broken | Lombok builders failing, modules disabled | Fix Lombok, re-enable modules |
| **tutorputor** | 1145 | ❌ Under-Engineered | 16/18 TypeScript modules failing | Fix TypeScript, consolidate services |
| **dcmaar** | 2961 | ⚠️ Unknown | Large but not analyzed | Needs audit |
| **finance** | 420 | ⚠️ Suspicious | 15+ domain modules, may be premature | Validate domain boundaries |
| **flashit** | 541 | ✅ Correct | Clean structure, focused scope | Keep |
| **audio-video** | 359 | ✅ Correct | Modular services (stt, tts, vision) | Keep |
| **software-org** | 1651 | ⚠️ Unknown | Not recently analyzed | Needs audit |
| **virtual-org** | 407 | ✅ Correct | Clean launcher + modules structure | Keep |
| **security-gateway** | 105 | ✅ Correct | Single focused module | Keep |
| **phr** | 107 | ✅ Correct | Minimal structure | Keep |
| **app-platform** | 915 | ❌ Legacy | Should migrate to YAPPC | Deprecate, migrate to YAPPC |

### 14. Shared Library Audit

| Library | Current Location | Classification | Consumers | Recommendation |
|---------|------------------|----------------|-----------|----------------|
| `platform:java:core` | Global | ✅ Truly Global | All products | Keep |
| `platform:java:domain` | Global | ⚠️ Misplaced | AEP, Data-Cloud, YAPPC | Move to product-owned |
| `platform:java:agent-framework` | Global | ⚠️ Over-Engineered | Unclear | Merge with agent-spi, agent-api |
| `platform:java:agent-spi` | Global | ❌ Misplaced | Only AEP | Move to `products:aep:agent-spi` |
| `platform:java:ai-integration` | Global | ✅ Product-Owned Shared | Data-Cloud, YAPPC | Keep near Data-Cloud owner |
| `platform:typescript:capabilities:canvas-core` | Global | ✅ Truly Global | YAPPC, Data-Cloud | Keep in platform (shared by 2+ products) |
| `platform:typescript:capabilities:design-system` | Global | ✅ Truly Global | All web apps | Keep, stabilize API |
| `platform:contracts` | Global | ✅ Truly Global | All products | Keep, add versioning |
| `shared-services:ai-registry` | Shared | ⚠️ Ambiguous | All AI-consuming products | Clarify ownership |
| `shared-services:auth-service` | Shared | ✅ Correct | All products | Keep, add to platform |

### 15. Package and Module Boundary Audit

| Module | Lines of Config | Classification | Issue | Action |
|--------|-----------------|----------------|-------|--------|
| `yappc:core:scaffold:api:http` | 17 | ❌ Micro-module | Single-file module | Merge into `core:scaffold:api` |
| `yappc:core:scaffold:api:grpc` | 17 | ❌ Micro-module | Single-file module | Merge into `core:scaffold:api` |
| `yappc:core:scaffold:cli` | 17 | ❌ Micro-module | Single-file module | Merge into `core:scaffold:core` |
| `yappc:core:domain:service` | 30 | ⚠️ Suspicious | Nested domain | Flatten to `core:domain` |
| `yappc:core:domain:task` | 30 | ⚠️ Suspicious | Nested domain | Flatten to `core:domain` |
| `yappc:services:domain` | 15 | ❌ Delegation Stub | Delegates to platform | Delete, use platform directly |
| `yappc:services:infrastructure` | 15 | ❌ Delegation Stub | Delegates to platform | Delete, use platform directly |
| `yappc:infrastructure:datacloud` | 45 | ⚠️ Over-Engineered | Thin wrapper around data-cloud | Merge with data-cloud integration |
| `data-cloud:platform-entity` | 1 | ❌ Empty Module | Only re-exports | Merge into platform |
| `data-cloud:platform-event` | 1 | ❌ Empty Module | Only re-exports | Merge into platform |
| `data-cloud:platform-config` | 1 | ❌ Empty Module | Only re-exports | Merge into platform |
| `data-cloud:platform-analytics` | 1 | ❌ Empty Module | Only re-exports | Merge into platform |

### 16. Folder Placement Audit

| Folder | Current Location | Proper Classification | Issue | Action |
|--------|------------------|---------------------|-------|--------|
| `domain/yappc/` | Root level | ❌ Misplaced | Duplicates products/yappc/core/domain | Delete or merge |
| `apps/yappc-web/` | Root level | ⚠️ Misplaced | Single app in apps/ | Move to `products/yappc/frontend/web` |
| `platform/typescript/canvas/` | Global | ⚠️ Misplaced | Product-specific capability | Move to `products/tutorputor/libs/canvas` |
| `platform/typescript/capabilities/realtime-engine/` | Global | ⚠️ Misplaced | Likely product-specific | Identify owner, move |
| `buildSrc/src/` | Root | ✅ Correct | Build conventions | Keep |
| `shared-services/*/ui/` | Shared | ⚠️ Suspicious | UI in shared services | Move to products or platform |

### 17. File Placement Audit

| File | Current Location | Proper Location | Issue |
|------|------------------|-----------------|-------|
| `settings.gradle.kts` (product) | `products/*/settings.gradle.kts` | Keep | ✅ Correct — standalone build support |
| `DATA_CLOUD_MODULE_SPLIT_PLAN.md` | `products/data-cloud/` | Keep | ✅ Correct — planning doc with product |
| `TUTORPUTOR_COMPREHENSIVE_AUDIT_V2.md` | `products/tutorputor/` | Keep | ✅ Correct — audit with product |
| `YAPPC_*.md` (multiple) | `products/yappc/` | Keep | ⚠️ Consolidate — too many planning docs |
| `dependency-alignment-report.json` | Root | Root | ✅ Correct — monorepo-level concern |
| `pnpm-workspace.yaml` | Root | Root | ✅ Correct — monorepo workspace definition |

### 18. Cross-Product Coupling Audit

| Consumer | Dependency | Type | Risk Level | Mitigation |
|----------|------------|------|------------|------------|
| `yappc:services` | `data-cloud:platform` | Direct | MEDIUM | Use SDK abstraction |
| `yappc:infrastructure:datacloud` | `data-cloud:platform` | Direct | LOW | Thin adapter acceptable |
| `aep:platform-core` | `data-cloud:spi` | Direct | MEDIUM | Should use platform abstraction |
| `aep:platform-core` | `platform:java:agent-*` | Multiple | HIGH | Agent framework needs consolidation |
| `tutorputor:services` | `platform:typescript/*` | Direct | MEDIUM | Canvas should be product-local |
| `all products` | `platform:contracts` | Proto | LOW | ✅ Correct — explicit contracts |
| `shared-services:ai-registry` | `platform:java:ai-integration` | Direct | LOW | ✅ Correct — appropriate coupling |

### 19. Shared vs Local Responsibility Matrix

| Responsibility | AEP | YAPPC | Data-Cloud | TutorPutor | Global |
|----------------|-----|-------|------------|------------|--------|
| Event Processing | ✅ Owner | Consumer | Consumer | — | — |
| Product Generation | — | ✅ Owner | — | — | — |
| Metadata Management | Consumer | ✅ Owner | ✅ Owner | Consumer | — |
| Content Generation | — | — | — | ✅ Owner | — |
| AI/ML Orchestration | Consumer | Consumer | ✅ Owner | Consumer | — |
| Auth/Security | — | — | — | — | ✅ Owner |
| Canvas/Visual | — | — | — | ✅ Owner | — |
| Agent Framework | ✅ Owner | — | — | — | — |
| Observability | — | — | — | — | ✅ Owner |

---

## Part 4 — Over-Engineering and Under-Engineering Audit

### 20. Over-Engineering Findings

| Finding | Location | Evidence | Impact | Solution |
|---------|----------|----------|--------|----------|
| **Scaffold Module Explosion** | `yappc/core/scaffold/*` | 7 nested modules (api, api/http, api/grpc, cli, core, adapters, packs) | Build complexity, cognitive load | Merge to `scaffold-api`, `scaffold-core` |
| **Agent Framework Fragmentation** | `platform/java/agent-*` | 8 separate modules (api, spi, framework, memory, learning, resilience, registry, dispatch) | Unclear boundaries, maintenance burden | Consolidate to 3 modules |
| **Kernel Module Indirection** | `platform/java/kernel/modules/*` | 7 wrapper modules (authentication, config, event-store, audit, resilience, observability, secrets) | Wrapper-on-wrapper pattern | Flatten to direct platform deps |
| **YAPPC Domain Over-Split** | `yappc/core/domain/*` | `domain`, `domain/service`, `domain/task` | 3 layers for simple domain | Flatten to single `domain` |
| **AI Integration Registry** | `platform/java/ai-integration/*` | `registry`, `observability`, `feature-store` submodules | Premature split | Keep as single module |
| **Contracts Multiplication** | `platform/contracts/*` | `proto`, `pojos`, `mappers`, `json-schemas` | 4 representation layers | Consolidate to proto + generated |
| **Service Delegation Stubs** | `yappc/services/domain`, `infrastructure` | Both delegate to `:services:platform` | False modularity | Delete, use platform directly |

### 21. Under-Engineering Findings

| Finding | Location | Evidence | Impact | Solution |
|---------|----------|----------|--------|----------|
| **TutorPutor TypeScript Crisis** | `products/tutorputor/*` | 16/18 modules failing build, 0% test coverage | Cannot deliver features | Fix TypeScript, add CI gates |
| **Data-Cloud Lombok Failure** | `products/data-cloud/platform` | Builders not generating, modules disabled | Cannot compile, tests fail | Fix annotation processing |
| **AEP Test Exclusions** | `aep/platform-core/build.gradle.kts` | 20+ test files excluded | Unknown test coverage | Fix classpath, re-enable tests |
| **Circular Dependency Core** | `services:core` | 463 files import `com.ghatana.core.*` | Violates clean architecture | Create domain interfaces |
| **LangChain4j Integration Failure** | `platform/java/ai-integration` | Disabled due to dependency resolution | No AI integration | Fix BOM resolution |
| **Finance Domain Premature Split** | `products/finance/domains/*` | 15 domain modules, many likely empty | Over-planning, maintenance burden | Validate domains, merge empty ones |

### 22. Unnecessary Abstractions

| Abstraction | Location | Why Unnecessary | Simplification |
|-------------|----------|-----------------|----------------|
| `WorkflowContext.addMetadata()` | `platform:java:workflow` | Single use, can be inline | Inline method |
| `Promise.ofBlocking()` wrapper | `agent-learning` | ActiveJ already provides | Use `Promise.of()` pattern |
| `TaskStatus.READY/RUNNING` enum | `analytics` | Over-specific states | Use `IN_PROGRESS` |
| `platform-entity`, `platform-event`, `platform-config`, `platform-analytics` | `data-cloud` | Empty re-export modules | Merge into `platform` |
| `services:domain`, `services:infrastructure` | `yappc` | Delegation stubs to platform | Delete, use platform directly |

### 23. Missing Boundaries

| Missing Boundary | Location | Current State | Needed Structure |
|------------------|----------|---------------|------------------|
| **YAPPC Frontend/Backend Contract** | `yappc/frontend` ↔ `yappc/backend` | Implicit REST calls | Explicit OpenAPI contracts |
| **TutorPutor API Versioning** | `tutorputor/services/*` | No versioning strategy | Versioned API contracts |
| **Data-Cloud Plugin Interface** | `data-cloud/plugins/*` | SPI exists but unclear | Well-defined plugin SDK |
| **Cross-Product Event Schema** | `platform/contracts` | Proto only | Event schema registry |
| **AI Provider Abstraction** | `platform/java:ai-integration` | LangChain4j disabled | Clean provider interface |

### 24. Over-Splitting / Micro-Packaging Findings

| Module | Size (Files) | Split Depth | Recommendation |
|--------|--------------|-------------|----------------|
| `yappc/core/scaffold/api/http` | ~3 | 4 levels (core → scaffold → api → http) | Merge to `scaffold-api` |
| `yappc/core/scaffold/api/grpc` | ~3 | 4 levels | Merge to `scaffold-api` |
| `yappc/core/scaffold/cli` | ~5 | 3 levels | Merge to `scaffold-core` |
| `yappc/core/domain/service` | ~15 | 3 levels | Flatten to `domain` |
| `yappc/core/domain/task` | ~12 | 3 levels | Flatten to `domain` |
| `platform/java/kernel/modules/*` | ~8 each | 3 levels | Delete, use platform directly |
| `platform/java/ai-integration/*` | ~10 each | 2 levels | Keep single module |

### 25. Under-Splitting / God-Module Findings

| Module | Size (Files/Items) | Concerns Mixed | Recommended Split |
|--------|-------------------|----------------|-------------------|
| `yappc/core/scaffold` | 1161 items | API, CLI, packs, adapters, core | Split to `scaffold-runtime`, `scaffold-cli` |
| `aep/platform-core` | 215 items | Engine, registry, security, connectors | Extract `registry`, `security`, `connectors` |
| `dcmaar/` | 2961 items | Unknown — needs analysis | Audit and split |
| `software-org/` | 1651 items | Unknown — needs analysis | Audit and split |

### 26. Premature Generalization Findings

| Generalization | Location | Evidence | Recommendation |
|----------------|----------|----------|----------------|
| `finance/extensions/` | `products/finance/extensions` | Empty placeholder for pack extensions | Delete until needed |
| `platform/java/agent-resilience/` | Global | No clear product consumers | Merge into agent-framework |
| `platform/java/agent-dispatch/` | Global | Unclear dispatch abstraction | Merge into agent-framework |
| `app-platform/` | Root | Legacy, should use YAPPC | Deprecate, migrate to YAPPC |

### 27. Hidden Coupling Findings

| Coupling | Source | Target | Discovery Method | Risk |
|----------|--------|--------|-------------------|------|
| Test classpath pollution | `aep:platform-core` | `aep:platform-scaling`, `data-cloud:spi` | Test exclusions in build.gradle | Tests don't reflect real usage |
| Direct data-cloud dependency | `yappc:services` | `data-cloud:platform` | build.gradle dependencies | Breaking changes in data-cloud break YAPPC |
| LangChain4j version conflict | `yappc` | `platform:java:ai-integration` | Dependency alignment report | Incompatible versions |
| TypeScript/Java duality | `tutorputor` | `platform:typescript/*` | pnpm-workspace.yaml | Canvas should be product-local |

---

## Part 5 — Merge / Split / Move Analysis

### 28. Merge Opportunities

| Merge Group | Current State | Target State | Effort | Priority |
|-------------|---------------|--------------|--------|----------|
| **YAPPC Scaffold API** | `core/scaffold/api`, `api/http`, `api/grpc` | Single `scaffold-api` module | S | P1 |
| **YAPPC Agent Framework** | `platform/java/agent-api`, `agent-spi`, `agent-framework` | Single `agent-core` module | M | P1 |
| **Data-Cloud Platform Submodules** | `platform-entity`, `platform-event`, `platform-config`, `platform-analytics` | Merge into `platform` | S | P0 |
| **YAPPC Service Stubs** | `services:domain`, `services:infrastructure` | Delete, use platform directly | XS | P1 |
| **Kernel Wrappers** | `platform/java/kernel/modules/*` | Delete, use platform directly | S | P2 |
| **Platform Domain** | `platform/java/domain`, `domain/yappc/` | Consolidate single domain module | M | P2 |

### 29. Combine/Consolidate Opportunities

| Combine Group | Current State | Target State | Effort | Priority |
|-------------|---------------|--------------|--------|----------|
| **TutorPutor Services** | 9 separate services | 3 consolidated services | L | P0 |
| **YAPPC Core Domain** | `core/domain`, `domain/service`, `domain/task` | Single `core/domain` | S | P1 |
| **AI Integration** | `ai-integration`, `registry`, `observability`, `feature-store` | Single `ai-integration` | S | P2 |
| **Platform Contracts** | `proto`, `pojos`, `mappers`, `json-schemas` | Proto + generated code | M | P2 |

### 30. Split Opportunities

| Module | Current State | Proposed Split | Effort | Priority |
|--------|---------------|----------------|--------|----------|
| `yappc/core/scaffold` | 1161 items | `scaffold-runtime`, `scaffold-cli`, `scaffold-packs` | M | P2 |
| `aep/platform-core` | 215 items | `platform-engine`, `platform-registry`, `platform-connectors` | M | P2 |
| `dcmaar/` | 2961 items | Audit needed, then split | L | P3 |
| `software-org/` | 1651 items | Audit needed, then split | L | P3 |

### 31. Move Closer to Product Opportunities

| Code | Current Location | Target Location | Reason |
|------|------------------|-----------------|--------|
| `platform:java:agent-spi` | Global shared | `products:aep:agent-spi` | Only AEP uses it |
| `platform:java:agent-framework` | Global shared | `products:aep:agent-framework` | AEP-specific abstraction |
| `platform:typescript:capabilities:canvas-core` | Global shared | Keep in platform | Shared by YAPPC (primary) + Data-Cloud (13+ components) — NOT TutorPutor |
| `platform:typescript:capabilities:realtime-engine` | Global shared | Identify owner, then move | Likely product-specific |
| `domain/yappc/` | Root level | `products:yappc/core/domain` | Duplicate of product code |

### 32. Move to Product-Owned Shared Opportunities

| Code | Current Location | Target Location | Owner | Consumers |
|------|------------------|-----------------|-------|-----------|
| `platform:java:ai-integration` | Global | Near `data-cloud` | Data-Cloud | YAPPC, AEP, TutorPutor |
| `platform:java:domain` | Global | Near `data-cloud` | Data-Cloud | AEP, YAPPC |
| `platform:java:database` | Global | Keep global | Platform Team | All products |
| `shared-services:ai-registry` | Shared | Near `data-cloud` | Data-Cloud AI Team | All AI consumers |

### 33. Move to Truly Global Shared Opportunities

| Code | Current Location | Why Global | Priority |
|------|------------------|------------|----------|
| `platform:java:core` | Already global | ✅ Correct — async primitives | Keep |
| `platform:java:observability` | Already global | ✅ Correct — metrics, tracing | Keep |
| `platform:java:security` | Already global | ✅ Correct — auth primitives | Keep |
| `platform:contracts` | Already global | ✅ Correct — cross-product contracts | Keep, add versioning |
| `shared-services:auth-service` | Shared | ✅ Correct — global auth | Move to `platform:security:service` |

### 34. Rename Opportunities

| Current Name | Better Name | Reason |
|--------------|-------------|----------|
| `yappc` | `product-creator` or `platform-builder` | YAPPC is acronym, unclear meaning |
| `dcmaar` | `ai-guardian` or `threat-detector` | DCMAAR is acronym, unclear meaning |
| `app-platform` | `legacy-app-platform` | Mark as deprecated |
| `platform-entity` | (delete) | Empty module |
| `platform-event` | (delete) | Empty module |
| `platform-config` | (delete) | Empty module |
| `platform-analytics` | (delete) | Empty module |

### 35. Delete / Deprecate Opportunities

| Item | Current State | Action | Timeline |
|------|---------------|--------|----------|
| `domain/yappc/` | Duplicate of product code | Delete | Immediate |
| `yappc/services:domain` | Delegation stub | Delete | Immediate |
| `yappc/services:infrastructure` | Delegation stub | Delete | Immediate |
| `data-cloud:platform-*` | Empty re-export modules | Merge into platform | Immediate |
| `app-platform/` | Legacy product | Deprecate, migrate to YAPPC | 6 months |
| `platform/java/kernel/modules/*` | Wrapper modules | Delete, use platform | 3 months |
| `platform/java/agent-resilience` | Unclear consumers | Merge or delete | 3 months |
| `platform/java/agent-dispatch` | Unclear consumers | Merge or delete | 3 months |

---

## Part 6 — Deep File and Folder Review

### 36. Folder-Level Review

| Folder | Responsibility | Owner | Boundary Fit | Score | Action |
|--------|---------------|-------|--------------|-------|--------|
| `products/aep/` | Event processing platform | AEP Team | ✅ Correct | 8/10 | Keep |
| `products/aep/platform-core/` | Core engine + registry + security + connectors | AEP Team | ❌ Overloaded | 5/10 | Split |
| `products/yappc/core/scaffold/` | Product generation | YAPPC Team | ❌ Too granular | 4/10 | Merge submodules |
| `products/data-cloud/platform/` | Metadata management | Data-Cloud Team | ⚠️ Broken | 4/10 | Fix Lombok |
| `products/tutorputor/services/` | Content, learning, assessment | TutorPutor Team | ❌ Failing | 3/10 | Fix TypeScript |
| `platform/java/core/` | Async primitives, types | Platform Team | ✅ Correct | 9/10 | Keep |
| `platform/java/agent-*/` | Agent abstractions | (Unclear) | ❌ Fragmented | 4/10 | Consolidate |
| `domain/yappc/` | YAPPC domain | (Duplicate) | ❌ Wrong place | 2/10 | Delete |
| `apps/yappc-web/` | Web frontend | YAPPC Team | ⚠️ Misplaced | 5/10 | Move to product |
| `shared-services/` | Cross-product services | Platform Team | ✅ Correct | 7/10 | Clarify ownership |

### 37. Module-Level Review

| Module | Responsibility | Cohesion | Coupling | Testability | Score | Action |
|--------|---------------|----------|----------|-------------|-------|--------|
| `platform:java:core` | Foundation types | High | Low | High | 9/10 | Keep |
| `platform:java:domain` | Shared domain | Medium | Medium | Medium | 6/10 | Move to product-owned |
| `products:aep:platform-core` | Engine + registry + security | Low | High | Low | 4/10 | Split |
| `products:yappc:core:scaffold` | Product generation | Low | Medium | Medium | 5/10 | Refactor |
| `products:data-cloud:platform` | Metadata + storage | High | Medium | Low | 5/10 | Fix build |
| `products:tutorputor:platform` | Content orchestration | Unknown | Unknown | None | 3/10 | Fix build |
| `shared-services:auth-service` | Global auth | High | Medium | Medium | 7/10 | Keep |
| `shared-services:ai-registry` | AI model registry | Medium | Medium | Medium | 6/10 | Clarify ownership |

### 38. Package-Level Review

| Package | Responsibility | Naming Quality | Placement | Score | Action |
|---------|---------------|----------------|-----------|-------|--------|
| `com.ghatana.platform.core` | Foundation | ✅ Clear | ✅ Correct | 9/10 | Keep |
| `com.ghatana.aep.engine` | Event processing | ✅ Clear | ✅ Correct | 8/10 | Keep |
| `com.ghatana.yappc.scaffold` | Product generation | ⚠️ Vague | ⚠️ Nested too deep | 5/10 | Flatten |
| `com.ghatana.datacloud.entity` | Metadata | ✅ Clear | ✅ Correct | 7/10 | Keep |
| `com.ghatana.tutorputor.content` | Content generation | ✅ Clear | ✅ Correct | 6/10 | Fix build |
| `com.ghatana.agent.framework` | Agent abstractions | ⚠️ Generic | ❌ Misplaced | 4/10 | Move to AEP |

### 39. File-Level Hotspot Review

| File | Responsibility | Complexity | Maintainability | Testability | Score | Action |
|------|---------------|------------|-----------------|-------------|-------|--------|
| `settings.gradle.kts` (root) | Module definition | High | Low | N/A | 5/10 | Simplify |
| `yappc/settings.gradle.kts` | Product modules | High | Low | N/A | 4/10 | Consolidate |
| `platform-core/build.gradle.kts` | Core dependencies | High | Low | N/A | 5/10 | Split tests |
| `data-cloud/platform/build.gradle.kts` | Platform deps | High | Medium | N/A | 5/10 | Fix Lombok |
| `tutorputor/*/package.json` | TypeScript deps | High | Low | None | 3/10 | Fix builds |

### 40. Naming Review

| Name | Clarity | Precision | Consistency | Score | Recommendation |
|------|---------|-----------|-------------|-------|----------------|
| `aep` | ⚠️ Acronym | Medium | ✅ Consistent | 6/10 | Expand to `agentic-event-processor` in docs |
| `yappc` | ❌ Acronym | Low | ✅ Consistent | 4/10 | Rename to `product-creator` or `platform-builder` |
| `dcmaar` | ❌ Acronym | Low | ✅ Consistent | 4/10 | Rename to `ai-guardian` |
| `platform-core` | ✅ Clear | High | ✅ Consistent | 8/10 | Keep |
| `scaffold` | ⚠️ Metaphor | Medium | ✅ Consistent | 6/10 | Consider `product-generator` |
| `kernel` | ⚠️ Metaphor | Medium | ⚠️ Inconsistent | 5/10 | Remove metaphor, use `core` |

### 41. Duplication Review

| Duplicated Logic | Locations | Risk Level | Solution |
|------------------|-----------|------------|----------|
| Domain models | `platform/java/domain` vs `domain/yappc/` vs `products/yappc/core/domain` | HIGH | Consolidate |
| AI integration | `platform/java/ai-integration` vs `products/yappc/core/ai` vs `products/tutorputor/libs/content-studio-agents` | MEDIUM | Extract shared library |
| Event handling | `platform/java/event-cloud` vs `products/aep/platform-core` | MEDIUM | Clarify ownership |
| Canvas implementation | `platform/typescript/capabilities/canvas-core` — shared by YAPPC + Data-Cloud | ✅ RESOLVED | Keep in platform (TutorPutor claim was incorrect) |
| Settings.gradle pattern | All products have standalone settings | LOW | ✅ Acceptable pattern |

### 42. Ownership Review

| Module | Current Owner | Should Be | Gap |
|--------|-------------|-----------|-----|
| `platform:java:agent-*` | Platform Team | AEP Team | Agents are AEP-specific |
| `platform:java:ai-integration` | Platform Team | Data-Cloud Team | Data-Cloud is primary AI consumer |
| `shared-services:ai-registry` | (Unclear) | Data-Cloud AI Team | Needs explicit ownership |
| `platform:typescript/capabilities/*` | Platform Team | Product Teams | Move to product-local |
| `products:app-platform/*` | (Legacy) | Deprecate | Migrate to YAPPC |

---

## Part 7 — Scoring

### 43. Product Boundary Scorecard

| Product | Boundary Clarity | Right-Sizing | Coupling Control | Overall |
|---------|------------------|--------------|------------------|---------|
| aep | 7/10 | 6/10 | 6/10 | **6.3/10** |
| yappc | 4/10 | 3/10 | 5/10 | **4.0/10** |
| data-cloud | 6/10 | 5/10 | 6/10 | **5.7/10** |
| tutorputor | 5/10 | 4/10 | 5/10 | **4.7/10** |
| dcmaar | 5/10 | 4/10 | 5/10 | **4.7/10** |
| finance | 5/10 | 4/10 | 6/10 | **5.0/10** |
| flashit | 8/10 | 8/10 | 8/10 | **8.0/10** |
| audio-video | 8/10 | 8/10 | 8/10 | **8.0/10** |
| software-org | 5/10 | 4/10 | 5/10 | **4.7/10** |
| virtual-org | 7/10 | 7/10 | 7/10 | **7.0/10** |
| security-gateway | 8/10 | 8/10 | 8/10 | **8.0/10** |
| phr | 8/10 | 8/10 | 8/10 | **8.0/10** |
| app-platform | 4/10 | 4/10 | 5/10 | **4.3/10** |

### 44. Package Boundary Scores

| Package | Clarity | Cohesion | Coupling | Overall |
|---------|---------|----------|----------|---------|
| platform:java:core | 9/10 | 9/10 | 8/10 | **8.7/10** |
| platform:java:domain | 6/10 | 6/10 | 6/10 | **6.0/10** |
| platform:java:agent-* | 4/10 | 4/10 | 7/10 | **5.0/10** |
| products:aep:platform-core | 5/10 | 4/10 | 5/10 | **4.7/10** |
| products:yappc:core:scaffold | 5/10 | 4/10 | 6/10 | **5.0/10** |
| products:data-cloud:platform | 6/10 | 6/10 | 6/10 | **6.0/10** |

### 45. Module Boundary Scores

| Module | Responsibility | Placement | Naming | Overall |
|--------|---------------|-----------|--------|---------|
| core | 9/10 | 9/10 | 9/10 | **9.0/10** |
| domain | 6/10 | 5/10 | 7/10 | **6.0/10** |
| agent-framework | 4/10 | 4/10 | 6/10 | **4.7/10** |
| agent-spi | 5/10 | 4/10 | 7/10 | **5.3/10** |
| agent-memory | 6/10 | 5/10 | 7/10 | **6.0/10** |
| ai-integration | 6/10 | 6/10 | 7/10 | **6.3/10** |
| scaffold:api:http | 4/10 | 4/10 | 5/10 | **4.3/10** |
| scaffold:api:grpc | 4/10 | 4/10 | 5/10 | **4.3/10** |
| platform-entity | 3/10 | 3/10 | 6/10 | **4.0/10** |

### 46. File Hotspot Scores

| File | Responsibility | Complexity | Maintainability | Overall |
|------|---------------|------------|-----------------|---------|
| settings.gradle.kts (root) | 6/10 | 4/10 | 4/10 | **4.7/10** |
| yappc/settings.gradle.kts | 5/10 | 3/10 | 4/10 | **4.0/10** |
| platform-core/build.gradle.kts | 5/10 | 4/10 | 5/10 | **4.7/10** |
| data-cloud/platform/build.gradle.kts | 6/10 | 5/10 | 5/10 | **5.3/10** |

### 47. Over-Engineering Score

| Dimension | Score |
|-----------|-------|
| Abstraction complexity | 4/10 |
| Module granularity | 3/10 |
| Speculative extensibility | 4/10 |
| Indirection layers | 4/10 |
| **Overall** | **3.8/10** |

### 48. Under-Engineering Score

| Dimension | Score |
|-----------|-------|
| Build stability | 3/10 |
| Test coverage | 4/10 |
| TypeScript health | 3/10 |
| Java health | 5/10 |
| **Overall** | **3.8/10** |

### 49. Maintainability Score

| Dimension | Score |
|-----------|-------|
| Code clarity | 5/10 |
| Testability | 4/10 |
| Documentation | 5/10 |
| Build stability | 4/10 |
| **Overall** | **4.5/10** |

### 50. Simplicity Score

| Dimension | Score |
|-----------|-------|
| Concept count | 4/10 |
| Dependency depth | 4/10 |
| Cognitive load | 4/10 |
| Build complexity | 3/10 |
| **Overall** | **3.8/10** |

### 51. Ownership Clarity Score

| Dimension | Score |
|-----------|-------|
| Module ownership | 5/10 |
| Team boundaries | 5/10 |
| Decision rights | 5/10 |
| Maintenance responsibility | 5/10 |
| **Overall** | **5.0/10** |

### 52. Reuse Quality Score

| Dimension | Score |
|-----------|-------|
| Shared code necessity | 5/10 |
| Shared code stability | 5/10 |
| Consumer clarity | 6/10 |
| Abstraction quality | 5/10 |
| **Overall** | **5.3/10** |

---

## Part 8 — Target State

### 53. Target Monorepo Layout

```
ghatana/
├── platform/                           # Truly global shared (minimal, stable)
│   ├── java/
│   │   ├── core/                       # Async primitives, types, logging
│   │   ├── database/                   # Connection management, transactions
│   │   ├── observability/              # Metrics, tracing, logging
│   │   ├── security/                   # Auth primitives, JWT
│   │   └── testing/                    # Test utilities, fixtures
│   ├── typescript/
│   │   ├── foundation/                 # Core TypeScript utilities
│   │   └── theme/                      # Design tokens, theme system
│   └── contracts/                      # Cross-product protobuf contracts
│
├── products/                           # Product-local code
│   ├── aep/                            # Event processing platform
│   │   ├── contracts/                  # AEP-specific contracts
│   │   ├── platform/
│   │   │   ├── engine/                 # Pipeline engine (split from core)
│   │   │   ├── registry/               # Agent registry (split from core)
│   │   │   ├── connectors/             # Connectors (split from core)
│   │   │   └── security/               # Security (split from core)
│   │   ├── agent-framework/            # Moved from platform
│   │   ├── server/
│   │   └── ui/
│   │
│   ├── yappc/                          # Product creator platform
│   │   ├── core/
│   │   │   ├── domain/                 # Flattened: no service/task submodules
│   │   │   ├── scaffold/
│   │   │   │   ├── api/                # Merged: http + grpc
│   │   │   │   └── core/               # Merged: core + cli + adapters
│   │   │   ├── ai/
│   │   │   ├── agents/
│   │   │   └── lifecycle/
│   │   ├── services/                   # Consolidated: no domain/infra stubs
│   │   ├── backend/
│   │   ├── frontend/
│   │   └── launcher/
│   │
│   ├── data-cloud/                     # Metadata platform
│   │   ├── spi/                        # Plugin SPI
│   │   ├── platform/                   # Core platform (merged submodules)
│   │   ├── ai-integration/             # AI platform (product-owned shared)
│   │   ├── launcher/
│   │   └── ui/
│   │
│   ├── tutorputor/                     # AI tutoring platform
│   │   ├── libs/
│   │   │   ├── canvas/                 # Moved from platform
│   │   │   ├── learning-kernel/
│   │   │   ├── simulation-engine/
│   │   │   └── content-generation/
│   │   ├── services/
│   │   │   ├── platform/               # Consolidated
│   │   │   ├── ai/                     # Consolidated
│   │   │   └── content/                # Consolidated
│   │   ├── apps/
│   │   └── contracts/
│   │
│   └── [other products...]             # Similar structure
│
├── shared-services/                    # Cross-product services (clear ownership)
│   ├── auth-service/                   # Own: Platform Security Team
│   ├── ai-registry/                    # Own: Data-Cloud AI Team
│   └── feature-store/                  # Own: Data-Cloud Platform Team
│
├── domain/                             # DELETE — merge into products
│
└── apps/                               # DELETE — move to products

```

### 54. Target Product Boundary Layout

Each product follows this structure:

```
products/{product}/
├── contracts/                          # Product-specific contracts (optional)
├── core/                               # Core business logic (optional)
├── libs/                               # Product-local libraries
├── services/                           # Backend services (consolidated)
├── apps/ or frontend/                  # Frontend applications
├── infrastructure/                     # Product-specific infra (optional)
└── launcher/                           # Entry point (optional)
```

### 55. Target Shared Library Layout

| Category | Location | Examples |
|----------|----------|----------|
| Truly Global | `platform/*` | core, database, observability, security |
| Product-Owned Shared | `products/{owner}/libs/shared-*` | data-cloud/ai-integration, yappc/scaffold-runtime |
| Cross-Product Services | `shared-services/*` | auth-service, ai-registry |

### 56. Target Package/Module Design Principles

1. **Single Responsibility**: Each module has one clear purpose
2. **Single Owner**: Each module has one owning team
3. **Explicit Contracts**: Public APIs are versioned and documented
4. **Minimal Dependencies**: Dependencies are explicit and justified
5. **Buildable in Isolation**: Each module builds without siblings
6. **Testable in Isolation**: Tests don't require other modules

### 57. Target Ownership Model

| Module Type | Owner | Examples |
|-------------|-------|----------|
| Platform modules | Platform Team | core, database, observability |
| Product modules | Product Team | aep, yappc, data-cloud |
| Product-owned shared | Product Team + Governance | ai-integration (Data-Cloud owns) |
| Cross-product services | Dedicated Team | auth-service (Platform Security) |

### 58. Target Naming Model

| Pattern | Example | Usage |
|---------|---------|-------|
| Descriptive | `event-processor` | Products, services |
| Functional | `ai-integration` | Libraries, capabilities |
| Domain-specific | `risk-engine` | Domain modules |
| No acronyms | `product-creator` (not `yappc`) | Product names |
| No metaphors | `core` (not `kernel`) | Infrastructure |

---

## Part 9 — Execution Plan

### 59. Immediate Moves (Week 1-2)

| Action | Target | Effort | Owner |
|--------|--------|--------|-------|
| Delete `domain/yappc/` | Root | XS | Platform Team |
| Delete `yappc/services:domain` | YAPPC | XS | YAPPC Team |
| Delete `yappc/services:infrastructure` | YAPPC | XS | YAPPC Team |
| Merge `data-cloud:platform-*` into platform | Data-Cloud | S | Data-Cloud Team |
| Delete `apps/yappc-web/` (move to product) | Root | S | YAPPC Team |

### 60. Short-Term Refactors (Month 1-2)

| Action | Target | Effort | Owner |
|--------|--------|--------|-------|
| Consolidate YAPPC scaffold modules | YAPPC | M | YAPPC Team |
| Merge platform agent modules | Platform | M | Platform + AEP Teams |
| Fix Data-Cloud Lombok builders | Data-Cloud | M | Data-Cloud Team |
| Fix TutorPutor TypeScript builds | TutorPutor | L | TutorPutor Team |
| Flatten YAPPC core/domain | YAPPC | S | YAPPC Team |

### 61. Medium-Term Structural Changes (Month 3-6)

| Action | Target | Effort | Owner |
|--------|--------|--------|-------|
| Split AEP platform-core | AEP | M | AEP Team |
| Consolidate TutorPutor services | TutorPutor | L | TutorPutor Team |
| Move agent-framework to AEP | Platform → AEP | M | Platform + AEP Teams |
| Move canvas to TutorPutor | Platform → TutorPutor | M | Platform + TutorPutor Teams |
| Deprecate app-platform | app-platform | L | App-Platform + YAPPC Teams |

### 62. Long-Term Architecture Cleanup (Month 6-12)

| Action | Target | Effort | Owner |
|--------|--------|--------|-------|
| Audit and split dcmaar | dcmaar | L | DCMAAR Team |
| Audit and split software-org | software-org | L | Software-Org Team |
| Consolidate finance domains | finance | M | Finance Team |
| Version all cross-product contracts | platform/contracts | M | Platform Team |
| Establish explicit ownership for all shared code | Monorepo | L | Platform + Product Teams |

### 63. Merge Plan

| Merge | Source Modules | Target Module | Timeline |
|-------|---------------|---------------|----------|
| 1 | `yappc:core:scaffold:api:http`, `api/grpc` | `scaffold-api` | Week 2 |
| 2 | `yappc:core:scaffold:cli`, `adapters`, `core` | `scaffold-core` | Week 3 |
| 3 | `platform:java:agent-api`, `agent-spi`, `agent-framework` | `agent-core` | Week 4 |
| 4 | `data-cloud:platform-entity`, `platform-event`, `platform-config`, `platform-analytics` | `platform` | Week 1 |
| 5 | `platform:java:agent-memory`, `agent-learning` | `agent-runtime` | Week 6 |
| 6 | `platform:java:ai-integration:registry`, `observability`, `feature-store` | `ai-integration` | Week 6 |

### 64. Split Plan

| Split | Source Module | Target Modules | Timeline |
|-------|---------------|---------------|----------|
| 1 | `aep:platform-core` | `engine`, `registry`, `connectors`, `security` | Month 2-3 |
| 2 | `yappc:core:scaffold` | `scaffold-runtime`, `scaffold-cli` | Month 2 |
| 3 | `tutorputor:services` | `platform`, `ai`, `content` | Month 3-4 |

### 65. Move Plan

| Move | Source | Target | Timeline |
|------|--------|--------|----------|
| 1 | `platform:java:agent-*` | `products:aep:agent-*` | Month 2-3 |
| 2 | ~~`platform:typescript/capabilities:canvas-core`~~ | ~~`products:tutorputor:libs:canvas`~~ | ❌ CANCELLED (shared by YAPPC + Data-Cloud) |
| 3 | `apps/yappc-web/` | `products:yappc/frontend/web` | Week 2 |
| 4 | `domain/yappc/` | Delete (merge with product) | Week 1 |
| 5 | `platform:java:ai-integration` | Near `data-cloud` (ownership) | Month 3 |

### 66. Rename Plan

| Rename | From | To | Timeline |
|--------|------|-----|----------|
| 1 | `yappc` (docs) | `yappc` → `product-creator` | Month 3 |
| 2 | `dcmaar` (docs) | `dcmaar` → `ai-guardian` | Month 3 |
| 3 | `kernel` | `kernel` → `core` | Month 4 |
| 4 | `app-platform` | `app-platform` → `legacy-app-platform` | Week 1 |

### 67. Delete/Deprecate Plan

| Action | Item | Timeline | Replacement |
|--------|------|----------|-------------|
| Delete | `domain/yappc/` | Week 1 | `products:yappc/core/domain` |
| Delete | `yappc/services:domain` | Week 1 | `platform:java:domain` |
| Delete | `yappc/services:infrastructure` | Week 1 | `platform:java:*` |
| Merge | `data-cloud:platform-*` | Week 1 | `data-cloud:platform` |
| Deprecate | `app-platform/` | 6 months | `yappc/` |
| Delete | `platform/java/kernel/modules/*` | Month 3 | `platform:java:*` |

### 68. Guardrails to Prevent Boundary Drift

1. **Module Admission Checklist**: All new modules require:
   - Clear ownership declaration
   - Consumer list (if shared)
   - Boundary classification (local/owned-shared/global)
   - Build and test verification

2. **Shared Library Governance**:
   - Product-owned shared requires product team approval
   - Global shared requires Platform Team + Architecture Review Board
   - Maximum 20 global shared libraries (currently: ~30)

3. **Cross-Product Dependency Review**:
   - All cross-product dependencies require explicit approval
   - Dependency graph published weekly
   - Circular dependencies blocked at build time

4. **Naming Conventions**:
   - No acronyms in product names (existing grandfathered)
   - No metaphors (kernel, scaffold) in new code
   - Explicit, descriptive naming enforced in code review

5. **Regular Boundary Audits**:
   - Quarterly boundary health checks
   - Annual comprehensive audit
   - Automated detection of boundary violations

---

## Part 10 — Final Decision

### 69. Go / No-Go on Current Structure

**VERDICT: NO-GO**

The current monorepo structure is **not ready for production scale**. Critical issues must be resolved:

**Blockers:**
1. TutorPutor: 16/18 TypeScript modules failing build
2. Data-Cloud: Lombok builders not generating, modules disabled
3. YAPPC: Excessive module granularity (48+ modules)
4. Circular dependencies in core service

**Conditions for GO:**
1. All TypeScript modules build successfully
2. Data-Cloud modules re-enabled with working builds
3. YAPPC consolidated to <20 modules
4. Circular dependencies resolved
5. Clear ownership established for all shared code

### 70. Top 10 Boundary Fixes

| Rank | Fix | Effort | Impact | Owner |
|------|-----|--------|--------|-------|
| 1 | Fix TutorPutor TypeScript builds | L | CRITICAL | TutorPutor Team |
| 2 | Fix Data-Cloud Lombok builders | M | HIGH | Data-Cloud Team |
| 3 | Consolidate YAPPC scaffold modules | M | HIGH | YAPPC Team |
| 4 | Delete/merge empty platform-* modules | XS | MEDIUM | Data-Cloud Team |
| 5 | Move agent-framework to AEP | M | MEDIUM | Platform + AEP Teams |
| 6 | Consolidate platform agent modules | M | MEDIUM | Platform Team |
| 7 | Flatten YAPPC core/domain | S | MEDIUM | YAPPC Team |
| 8 | Delete delegation stub services | XS | LOW | YAPPC Team |
| 9 | Move canvas to TutorPutor | M | MEDIUM | Platform + TutorPutor Teams |
| 10 | Delete domain/yappc/ duplicate | XS | LOW | Platform Team |

### 71. Top 10 Simplification Actions

| Rank | Action | Effort | Impact | Owner |
|------|--------|--------|--------|-------|
| 1 | Consolidate YAPPC to 15 modules | L | HIGH | YAPPC Team |
| 2 | Merge TutorPutor services to 3 | L | HIGH | TutorPutor Team |
| 3 | Flatten platform/java/agent-* to 3 modules | M | MEDIUM | Platform Team |
| 4 | Delete kernel module wrappers | S | LOW | Platform Team |
| 5 | Merge data-cloud platform submodules | S | MEDIUM | Data-Cloud Team |
| 6 | Delete app-platform (deprecate) | L | MEDIUM | App-Platform Team |
| 7 | Consolidate platform contracts | M | MEDIUM | Platform Team |
| 8 | Flatten YAPPC domain | S | MEDIUM | YAPPC Team |
| 9 | Merge AI integration submodules | S | LOW | Data-Cloud Team |
| 10 | Rename acronyms to descriptive names | S | LOW | All Teams |

### 72. Final Conclusion

The Ghatana monorepo demonstrates **sophisticated architectural ambition** but suffers from **fundamental boundary violations** and **systematic over/under-engineering**.

**Strengths:**
- Clear product separation at top level
- Good platform/shared infrastructure foundation
- Active boundary awareness (planning documents exist)
- Strong technology choices (ActiveJ, gRPC, protobuf)

**Critical Weaknesses:**
- YAPPC: Micro-module proliferation (48+ modules)
- TutorPutor: Build system breakdown (16/18 failing)
- Data-Cloud: Lombok annotation processing failure
- Platform: Unclear ownership for agent/AI modules
- Cross-product: Insufficient abstraction boundaries

**Required Actions:**
1. **Immediate (Weeks 1-2)**: Delete empty modules, fix Data-Cloud builders
2. **Short-term (Month 1-2)**: Fix TutorPutor builds, consolidate YAPPC
3. **Medium-term (Month 3-6)**: Move misplaced code, clarify ownership
4. **Long-term (Month 6-12)**: Architecture cleanup, establish guardrails

**Success Criteria:**
- All builds green (100% module success rate)
- <20 modules per product (currently YAPPC has 48+)
- Clear ownership for every shared module
- Zero circular dependencies
- All cross-product contracts versioned

The monorepo **can be corrected** with focused effort over 3-6 months. The foundational structure is sound; the issues are primarily **granularity, ownership clarity, and build stability** rather than fundamental architectural flaws.

---

**Audit Completed:** March 21, 2026  
**Next Review:** June 21, 2026 (3 months)  
**Audit Owner:** Principal Engineering Team  
**Status:** NO-GO — Requires remediation before production scale
