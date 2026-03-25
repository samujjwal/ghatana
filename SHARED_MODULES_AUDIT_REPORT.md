# Shared Modules Audit Report

**Date:** March 25, 2026  
**Auditor:** Cascade AI Code Analysis  
**Scope:** `/Users/samujjwal/Development/ghatana/io`, `/Users/samujjwal/Development/ghatana/platform`, `/Users/samujjwal/Development/ghatana/shared-services`  
**Version:** 1.0.0

---

## Executive Summary

This audit examines shared modules across three critical directories in the Ghatana codebase: `io/` (ActiveJ vendored patches), `platform/` (cross-cutting platform libraries and contracts), and `shared-services/` (microservices shared across products). 

**Overall Health Assessment: MODERATE with significant consolidation opportunities**

### Key Findings Summary

| Category | Count | Severity Distribution |
|----------|-------|----------------------|
| Critical | 3 | Build system issues, circular dependencies |
| High | 8 | API inconsistencies, missing documentation, test gaps |
| Medium | 15 | Consolidation opportunities, naming inconsistencies |
| Low | 12 | Minor documentation gaps, deprecated code |

### Major Concerns
1. **Circular Dependencies** in platform/java modules (core ↔ domain ↔ kernel)
2. **Duplicate Utility Implementations** across TypeScript packages (cn utils, validation)
3. **Inconsistent Documentation** standards across Java modules
4. **Test Coverage Gaps** in shared-services and platform/typescript
5. **Dependency Sprawl** with overlapping concerns between platform modules

### Consolidation Opportunities Identified
- 5+ TypeScript utility packages that should merge into `@ghatana/platform-utils`
- 3+ JSON utilities across Java modules that should centralize to kernel's `JsonUtils`
- Authentication logic duplication between `auth-gateway` and platform security module
- Validation frameworks that overlap between core, domain, and governance modules

---

## Scope Reviewed

### Directories Audited

#### 1. `/Users/samujjwal/Development/ghatana/io`
- **Purpose:** Vendored ActiveJ framework patches
- **Size:** ~5 items (small, focused)
- **Primary Language:** Java

#### 2. `/Users/samujjwal/Development/ghatana/platform`
- **agent-catalog/**: YAML-based agent definitions and templates (26 items)
- **contracts/**: Protobuf/gRPC contracts with schema generation (76 items)
- **java/**: 19 platform modules (~1098 items total)
  - agent-core, ai-integration, audit, config, connectors, core, database, distributed-cache, domain, governance, http, kernel, observability, plugin, runtime, security, testing, workflow
- **typescript/**: 14 TypeScript packages (~526 items total)
  - accessibility-audit, api, canvas, charts, design-system, foundation, i18n, platform-shell, realtime, sso-client, theme, tokens, ui-integration, utils

#### 3. `/Users/samujjwal/Development/ghatana/shared-services`
- **ai-inference-service/**: LLM gateway and inference (6 items)
- **auth-gateway/**: Centralized authentication service (10 items)
- **feature-store-ingest/**: Real-time feature ingestion pipeline (3 items)
- **infrastructure/**: K8s, monitoring configurations (72 items)
- **user-profile-service/**: User profile management (7 items)

### Exclusions
- Product-specific code under `/products/` (referenced for consumer analysis only)
- Node modules and build artifacts
- Generated code (unless relevant to API contracts)

---

## Shared Module Inventory

### Java Platform Modules (platform/java)

| Module | Path | Purpose | Dependencies | Key Exports |
|--------|------|---------|--------------|-------------|
| core | `/platform/java/core` | Foundation utilities, types, patterns | None (base) | JsonUtils, DateTimeUtils, ValidationService |
| kernel | `/platform/java/kernel` | Module lifecycle, registry, contracts | core | KernelRegistry, CrossScopeAuditService |
| domain | `/platform/java/domain` | Shared domain models | core, contracts | Event types, Schema definitions |
| observability | `/platform/java/observability` | Metrics, tracing, logging | core, runtime, http | MetricsCollector, TraceHttpService |
| security | `/platform/java/security` | Auth, encryption, JWT | core, config, domain, observability, governance, http | JwtTokenProvider, OAuth2Provider |
| http | `/platform/java/http` | HTTP client/server utilities | core, runtime | ActiveJ HTTP wrappers, middleware |
| database | `/platform/java/database` | Database abstractions | core | Connection pools, migrations |
| config | `/platform/java/config` | Configuration management | core | Config loaders, environment utils |
| workflow | `/platform/java/workflow` | Workflow orchestration | core, domain, kernel | Workflow engine, state management |
| agent-core | `/platform/java/agent-core` | Agent framework | core, kernel | Agent types, planner framework |
| ai-integration | `/platform/java/ai-integration` | AI/ML integration | core, kernel | LLM providers, embedding services |
| audit | `/platform/java/audit` | Audit logging | core | JpaAuditService, AuditEventStore |
| connectors | `/platform/java/connectors` | External system adapters | core, http, observability | API clients, adapters |
| governance | `/platform/java/governance` | Policy enforcement | core | SSRF guard, tenant isolation |
| distributed-cache | `/platform/java/distributed-cache` | Caching abstractions | core | Redis adapters, cache managers |
| plugin | `/platform/java/plugin` | Plugin framework | core, kernel | Plugin lifecycle, registry |
| runtime | `/platform/java/runtime` | Runtime utilities | core | Eventloop management |
| testing | `/platform/java/testing` | Test utilities | core | Test fixtures, builders |

### TypeScript Platform Modules (platform/typescript)

| Module | Path | Purpose | Dependencies | Key Exports |
|--------|------|---------|--------------|-------------|
| design-system | `/platform/typescript/design-system` | UI components (Atomic Design) | tokens, platform-utils | 80+ components, hooks |
| tokens | `/platform/typescript/tokens` | Design tokens | - | Colors, spacing, typography |
| foundation/platform-utils | `/platform/typescript/foundation/platform-utils` | Shared utilities | - | cn(), formatters, platform detection |
| utils | `/platform/typescript/utils` | Utility re-exports | platform-utils | Re-export only (redundant) |
| theme | `/platform/typescript/theme` | Theming system | tokens | Theme provider, CSS vars |
| canvas | `/platform/typescript/canvas` | Canvas UI components | design-system | CanvasComplete, ReactFlow integration |
| api | `/platform/typescript/api` | API client utilities | - | HTTP clients, request builders |
| charts | `/platform/typescript/charts` | Charting components | design-system, tokens | Data visualization |
| accessibility-audit | `/platform/typescript/accessibility-audit` | A11y testing | - | WCAG testing utilities |
| i18n | `/platform/typescript/i18n` | Internationalization | - | Translation utilities |
| platform-shell | `/platform/typescript/platform-shell` | App shell components | design-system | Layout shells |
| realtime | `/platform/typescript/realtime` | Real-time features | - | WebSocket clients, presence |
| sso-client | `/platform/typescript/sso-client` | SSO integration | - | OIDC client utilities |
| ui-integration | `/platform/typescript/ui-integration` | UI integration helpers | design-system | Integration patterns |

### Contracts Module (platform/contracts)

| Module | Path | Purpose | Key Outputs |
|--------|------|---------|-------------|
| contracts | `/platform/contracts` | Canonical API contracts | Protobuf definitions, JSON schemas, generated POJOs |

### Shared Services (shared-services)

| Service | Path | Purpose | Platform Dependencies |
|---------|------|---------|---------------------|
| auth-gateway | `/shared-services/auth-gateway` | JWT/OAuth2 authentication | security, http, observability, config, database |
| ai-inference-service | `/shared-services/ai-inference-service` | LLM inference gateway | ai-integration, http, observability, core, database |
| user-profile-service | `/shared-services/user-profile-service` | User profiles | http, config, security, database, observability |
| feature-store-ingest | `/shared-services/feature-store-ingest` | Feature ingestion | ai-integration, observability, core, domain |

### IO Module (io/activej)

| Module | Path | Purpose | Notes |
|--------|------|---------|-------|
| activej | `/io/activej` | Vendored ActiveJ patches | Minimal, well-documented vendoring |

---

## Dependency and Consumer Overview

### Dependency Graph - Java Platform

```
                         ┌─────────────┐
                         │   kernel    │
                         └──────┬──────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         │                      │                      │
    ┌────┴────┐            ┌────┴────┐            ┌────┴────┐
    │  core   │◄───────────│ domain  │◄──────────│ runtime │
    └────┬────┘            └────┬────┘            └────┬────┘
         │                      │                      │
    ┌────┴────┬─────────────────┴──────────────────────┘
    │         │
┌───┴────┐ ┌──┴─────┐
│  http  │ │observ. │
└───┬────┘ └──┬─────┘
    │         │
┌───┴─────────┴───┐
│ security, etc.  │
└─────────────────┘
```

### Circular Dependencies Identified

**CRITICAL-001: Core Service Circular Dependency**
- **Location:** `platform/java/core` ↔ `platform/java/domain` → multiple modules
- **Evidence:** 463 files across 174 domain files import `com.ghatana.core.*` packages
- **Problem:** Domain modules importing service-layer classes violates clean architecture
- **Impact:** Build instability, tight coupling, violation of dependency inversion

**HIGH-001: Analytics Service Historical Circular Dependency (RESOLVED)**
- **Status:** Fixed per system memory
- **Resolution:** Removed duplicate build files, updated dependencies to use `analytics-api`

### Consumer Analysis - TypeScript

| Module | Primary Consumers | Internal Dependencies |
|--------|-------------------|----------------------|
| design-system | All UI products, canvas | tokens, platform-utils |
| tokens | design-system, theme, all UI | - |
| theme | design-system, canvas | tokens |
| platform-utils | design-system, utils | - |
| utils | (re-export only) | platform-utils |
| canvas | products with visual workflows | design-system |

---

## Findings

### FINDING-001: Critical Circular Dependency in Core Service
- **Finding ID:** CRIT-001
- **Severity:** critical
- **File Path:** `platform/java/core`, `platform/java/domain`, `platform/java/kernel`
- **Module:** platform/java
- **Problem to Resolve:** Domain modules import service-layer classes (`com.ghatana.core.*`, `com.ghatana.core.operator.*`) creating circular build dependencies
- **Why It Matters:** Violates clean architecture principles, causes build instability, prevents independent domain module testing, creates maintenance nightmare
- **Evidence:** 463 files import core service classes; imports include `Event`, `AbstractOperator`, `OperatorId`, `OperatorResult` in domain contexts
- **Consumer Impact:** All products depending on domain models inherit this coupling; test isolation compromised
- **Duplication Type:** none (architectural flaw)
- **Consolidation Recommendation:** Extract domain interfaces to `platform/java/domain`; move implementations to service modules
- **Target Location:** `platform/java/domain` (interfaces), service modules (implementations)
- **Migration Notes:** 
  1. Create interfaces for `Event`, `Operator`, `OperatorResult` in domain
  2. Refactor core service classes to implement domain interfaces
  3. Update 463+ files to use domain interfaces
  4. Add ArchUnit tests to prevent future violations
- **Exact Fix Recommendation:** Create `DomainEvent`, `Operator`, `OperatorResult` interfaces in domain module; refactor core implementations to implement these interfaces
- **Test Gaps:** No ArchUnit tests enforcing domain→service dependency direction
- **Documentation Gaps:** Missing architecture decision record (ADR) on dependency directions

### FINDING-002: High - Duplicate cn() Utility Across TypeScript Packages
- **Finding ID:** HIGH-001
- **Severity:** high
- **File Path:** `platform/typescript/foundation/platform-utils/src/cn.ts`, `platform/typescript/design-system/src/utils/`, `platform/typescript/canvas/src/utils/`
- **Module:** platform/typescript
- **Problem to Resolve:** Multiple implementations/exports of `cn()` (class name merging utility) across packages
- **Why It Matters:** Maintenance overhead, potential divergent behavior, unclear canonical source
- **Evidence:** `platform-utils` exports `cn()` from `clsx` + `tailwind-merge`; design-system re-exports from `@ghatana/platform-utils`; canvas may have its own
- **Consumer Impact:** Consumers uncertain which import to use; bundle size if multiple implementations included
- **Duplication Type:** code
- **Consolidation Recommendation:** Single implementation in `@ghatana/platform-utils`, all others re-export only
- **Target Location:** `platform/typescript/foundation/platform-utils/src/cn.ts`
- **Migration Notes:** 
  1. Verify canvas doesn't have duplicate implementation
  2. Update all imports to use `@ghatana/platform-utils`
  3. Remove any duplicate implementations
  4. Add lint rule enforcing single import source
- **Exact Fix Recommendation:** Ensure only `platform-utils` has implementation; all packages re-export or depend on `platform-utils`
- **Test Gaps:** No tests verifying `cn()` behavior consistency across packages
- **Documentation Gaps:** No documentation on which utility package to prefer

### FINDING-003: High - Inconsistent Lombok Configuration Causing Build Failures
- **Finding ID:** HIGH-002
- **Severity:** high
- **File Path:** Various `build.gradle.kts` files across platform/java
- **Module:** platform/java/build system
- **Problem to Resolve:** Lombok annotation processor not generating builder classes consistently; requires manual workarounds
- **Why It Matters:** Blocks builds, requires module-specific workarounds, inconsistent developer experience
- **Evidence:** System memory indicates data-cloud module had extensive Lombok issues; some modules have `annotationProcessor(libs.lombok)` others may not
- **Consumer Impact:** Products consuming platform modules may encounter missing builder classes
- **Duplication Type:** workflow (inconsistent build configuration)
- **Consolidation Recommendation:** Centralize Lombok configuration in convention plugin
- **Target Location:** `buildSrc/src/main/kotlin/java-conventions.gradle.kts`
- **Migration Notes:**
  1. Audit all platform/java build.gradle.kts for Lombok configuration
  2. Create/revise convention plugin with standard Lombok setup
  3. Apply convention plugin to all modules consistently
  4. Document Lombok version and configuration standards
- **Exact Fix Recommendation:** Add `id("com.github.spotbugs")` convention or custom convention with `compileOnly(libs.lombok)`, `annotationProcessor(libs.lombok)` standard config
- **Test Gaps:** No build verification tests for annotation processing
- **Documentation Gaps:** Missing build system documentation for Lombok setup

### FINDING-004: High - Missing Documentation Standards Across Java Modules
- **Finding ID:** HIGH-003
- **Severity:** high
- **File Path:** `platform/java/*/src/main/java/**/*.java`
- **Module:** platform/java
- **Problem to Resolve:** Inconsistent Javadoc quality; some classes well-documented, others missing entirely
- **Why It Matters:** API consumers cannot understand contracts; maintenance burden for new developers
- **Evidence:** `KernelRegistry.java` - excellent documentation with `@doc.*` tags; `JsonUtils.java` - basic but adequate; many classes lack any class-level documentation
- **Consumer Impact:** Difficult to consume platform APIs correctly; risk of misuse
- **Duplication Type:** ownership (inconsistent documentation ownership)
- **Consolidation Recommendation:** Enforce documentation standards via Checkstyle/SpotBugs; create documentation templates
- **Target Location:** All public API classes in platform/java
- **Migration Notes:**
  1. Define minimum documentation standards (class-level, public methods)
  2. Add Checkstyle/SpotBugs rule for documentation coverage
  3. Create documentation template for different class types (services, utilities, DTOs)
  4. Prioritize public API surface for documentation
- **Exact Fix Recommendation:** Add checkstyle rule: `JavadocMethod` with `minLineCount=1` for public methods; `JavadocType` for all public classes
- **Test Gaps:** No automated documentation coverage tests
- **Documentation Gaps:** Missing platform-wide documentation standards guide

### FINDING-005: High - Promise.ofBlocking() Pattern Inconsistency
- **Finding ID:** HIGH-004
- **Severity:** high
- **File Path:** `platform/java/*/src/**/*.java`, `shared-services/*/src/**/*.java`
- **Module:** platform/java, shared-services
- **Problem to Resolve:** Inconsistent distinction between `Promise.of()` for already-computed, non-blocking values and `Promise.ofBlocking(executor, ...)` for true blocking work such as JDBC, Redis, HTTP, file I/O, and semaphore waits
- **Why It Matters:** Misclassifying blocking work as `Promise.of(...)` runs that work on the caller thread and can block the ActiveJ event loop. The opposite mistake also occurs: historical test fixes have been interpreted as evidence that `Promise.ofBlocking(...)` is generally undesirable, which is not aligned with repository standards
- **Evidence:** Repository standards and helper docs explicitly prefer `Promise.ofBlocking(executor, ...)` for blocking operations and reserve synchronous wrapping for already-computed values, test doubles, or compatibility bridges. The current codebase contains both correct uses of `Promise.ofBlocking(...)` for I/O and historical examples that created confusion about when it should be used
- **Consumer Impact:** Event-loop blocking, subtle latency regressions, incorrect copy-paste patterns in new services, and test misuse when developers call Promise APIs such as `.getResult()` directly instead of using `EventloopTestBase`
- **Duplication Type:** logic (inconsistent async patterns)
- **Consolidation Recommendation:** Establish and enforce the canonical rule: use `Promise.of()` / `Promise.ofException()` only for already-computed in-memory results; use `Promise.ofBlocking(executor, ...)` for any blocking operation; never call `.getResult()` as control flow
- **Target Location:** All async service implementations
- **Migration Notes:**
  1. Audit blocking I/O call sites that return `Promise.of(syncResult)` or otherwise execute JDBC/Redis/HTTP/file work inline
  2. Keep or add `Promise.ofBlocking(executor, ...)` for those blocking call sites
  3. Replace `Promise.ofBlocking(...)` with `Promise.of(...)` only where the value is already computed in memory and no blocking work occurs
  4. Add architecture or static-analysis checks for event-loop blocking patterns and document the distinction in the async patterns guide
- **Exact Fix Recommendation:** Refactor services so blocking I/O uses `Promise.ofBlocking(executor, () -> ...)`, while already-computed values use `Promise.of(value)` or `Promise.ofException(e)`. For tests, use `EventloopTestBase`/`runPromise(...)` and avoid `.getResult()` directly
- **Test Gaps:** No architecture or repository-level tests enforcing the distinction between blocking I/O and in-memory promise wrapping
- **Documentation Gaps:** Missing async patterns guide in platform documentation

### FINDING-006: Medium - Redundant TypeScript Utils Package
- **Finding ID:** MED-001
- **Severity:** medium
- **File Path:** `platform/typescript/utils/src/index.ts`
- **Module:** platform/typescript/utils
- **Problem to Resolve:** `utils` package is only a re-export of `platform-utils`; adds no value
- **Why It Matters:** Maintenance overhead, confusion about which package to use, potential circular dependency risk
- **Evidence:** File contains only `export * from '@ghatana/platform-utils';`
- **Consumer Impact:** Unclear which package to depend on; may accidentally depend on both
- **Duplication Type:** code (identical exports)
- **Consolidation Recommendation:** Deprecate and remove `utils` package; migrate consumers to `platform-utils`
- **Target Location:** Deprecate `utils`, canonical location is `platform-utils`
- **Migration Notes:**
  1. Mark `utils` package as deprecated in package.json
  2. Update all consumers to use `@ghatana/platform-utils` directly
  3. After migration period, remove `utils` package
- **Exact Fix Recommendation:** Add deprecation notice to `utils/package.json`; search/replace imports across codebase
- **Test Gaps:** N/A (package has no unique code to test)
- **Documentation Gaps:** Missing package deprecation documentation

### FINDING-007: Medium - Inconsistent Error Handling Patterns
- **Finding ID:** MED-002
- **Severity:** medium
- **File Path:** `platform/java/*/src/**/*.java`
- **Module:** platform/java
- **Problem to Resolve:** Mix of exception types, error response patterns across modules
- **Why It Matters:** Consumers cannot predict error behavior; inconsistent API experience
- **Evidence:** Some services use `RuntimeException` wrappers; others use custom domain exceptions; HTTP error responses vary in structure
- **Consumer Impact:** Error handling code must vary by module; fragile error recovery
- **Duplication Type:** logic (inconsistent error handling patterns)
- **Consolidation Recommendation:** Standardize on platform `PlatformException` hierarchy with error codes
- **Target Location:** `platform/java/core/src/main/java/com/ghatana/platform/core/exception/`
- **Migration Notes:**
  1. Define standard exception hierarchy in core module
  2. Add error code enumeration
  3. Create HTTP error response builder utility
  4. Refactor services incrementally
- **Exact Fix Recommendation:** Create `PlatformException` base class with `ErrorCode` enum; create `ErrorResponseBuilder` utility
- **Test Gaps:** No tests verifying error response consistency
- **Documentation Gaps:** Missing error handling API guide

### FINDING-008: Medium - JWT Provider Duplication Risk
- **Finding ID:** MED-003
- **Severity:** medium
- **File Path:** `platform/java/security/src/.../JwtTokenProvider.java`, `shared-services/auth-gateway/src/.../JwtTokenProvider.java`
- **Module:** platform/java/security, shared-services/auth-gateway
- **Problem to Resolve:** `auth-gateway` may duplicate JWT logic available in platform security module
- **Why It Matters:** Security logic duplication risks divergent behavior, security vulnerabilities
- **Evidence:** Both modules import `com.ghatana.platform.security.port.JwtTokenProvider` - appears platform version is used
- **Consumer Impact:** If divergence occurs, token validation may fail inconsistently
- **Duplication Type:** code (potential duplication)
- **Consolidation Recommendation:** Ensure auth-gateway uses platform `JwtTokenProvider` exclusively; add integration tests
- **Target Location:** Platform security module is canonical
- **Migration Notes:**
  1. Verify auth-gateway exclusively uses platform JwtTokenProvider
  2. Remove any local JWT implementation if present
  3. Add contract tests ensuring token compatibility
- **Exact Fix Recommendation:** Audit auth-gateway for any local JWT logic; ensure all JWT operations delegate to platform module
- **Test Gaps:** No cross-service JWT compatibility tests
- **Documentation Gaps:** Missing security module usage guide

### FINDING-009: Medium - Observability Module Notes Feature Limitations
- **Finding ID:** MED-004
- **Severity:** medium
- **File Path:** `platform/java/observability/build.gradle.kts`
- **Module:** platform/java/observability
- **Problem to Resolve:** Module contains disabled features with unclear future plans
- **Why It Matters:** Dead code increases maintenance burden; unclear which features are production-ready
- **Evidence:** Comment: "Some monitoring features using ActiveJ Launcher are disabled as they require ActiveJ DI which is not available in 6.0-beta2"
- **Consumer Impact:** Consumers may attempt to use disabled features
- **Duplication Type:** none (disabled code)
- **Consolidation Recommendation:** Remove disabled code or clearly mark as experimental; document roadmap
- **Target Location:** Remove or move to feature branch
- **Migration Notes:**
  1. Identify all disabled/marker code
  2. Either: a) Remove entirely, b) Move to experimental package, or c) Document clearly
  3. Update documentation with feature status
- **Exact Fix Recommendation:** Add `@Experimental` annotation to incomplete features; document in README
- **Test Gaps:** N/A for disabled code
- **Documentation Gaps:** Module README doesn't list disabled features

### FINDING-010: Medium - TypeScript Package Peer Dependency Complexity
- **Finding ID:** MED-005
- **Severity:** medium
- **File Path:** `platform/typescript/design-system/package.json`
- **Module:** platform/typescript/design-system
- **Problem to Resolve:** Complex peer dependency chain may cause version conflicts
- **Why It Matters:** Consumers may struggle with peer dependency resolution
- **Evidence:** design-system has peer deps on `@ghatana/theme`, `@ghatana/tokens`, `react`, `react-dom`, `react-router`
- **Consumer Impact:** Installation complexity; potential version conflicts
- **Duplication Type:** none (dependency management issue)
- **Consolidation Recommendation:** Document peer dependency requirements; provide install command
- **Target Location:** Add to package README
- **Migration Notes:**
  1. Create dependency installation guide
  2. Provide pnpm/npm/yarn install commands with correct versions
  3. Consider monorepo tooling for automatic peer dep management
- **Exact Fix Recommendation:** Add "Getting Started" section to design-system README with peer dep install command
- **Test Gaps:** No automated peer dependency validation
- **Documentation Gaps:** Missing peer dependency installation guide

### FINDING-011: Low - Missing CHANGELOG Files
- **Finding ID:** LOW-001
- **Severity:** low
- **File Path:** Most platform modules lack CHANGELOG.md
- **Module:** platform/java/*, platform/typescript/*
- **Problem to Resolve:** No version history documentation for consumers
- **Why It Matters:** Consumers cannot track breaking changes, new features, bug fixes
- **Evidence:** No CHANGELOG.md files observed in module roots
- **Consumer Impact:** Difficult to plan upgrades; no visibility into changes
- **Duplication Type:** ownership (missing documentation artifact)
- **Consolidation Recommendation:** Add CHANGELOG.md to all public API modules following Keep a Changelog format
- **Target Location:** Each module root
- **Migration Notes:**
  1. Create CHANGELOG.md templates
  2. Retroactively document recent versions
  3. Enforce changelog updates in PR process
- **Exact Fix Recommendation:** Add CHANGELOG.md with sections: Added, Changed, Deprecated, Removed, Fixed, Security
- **Test Gaps:** N/A
- **Documentation Gaps:** Missing version history for all modules

### FINDING-012: Low - Inconsistent Package Naming Convention
- **Finding ID:** LOW-002
- **Severity:** low
- **File Path:** `platform/typescript/*/package.json`
- **Module:** platform/typescript
- **Problem to Resolve:** Inconsistent package naming: `@ghatana/*` vs `@ghatana/platform-*`
- **Why It Matters:** Consumer confusion about package purposes; unclear organization
- **Evidence:** `@ghatana/tokens`, `@ghatana/theme` vs `@ghatana/platform-utils`, `@ghatana/design-system`
- **Consumer Impact:** Difficult to discover related packages
- **Duplication Type:** none (naming inconsistency)
- **Consolidation Recommendation:** Standardize on `@ghatana/platform-*` prefix for platform modules
- **Target Location:** Rename with platform- prefix or document naming convention
- **Migration Notes:**
  1. Document naming convention decision
  2. Consider gradual migration with deprecation
  3. Update all documentation references
- **Exact Fix Recommendation:** Create ADR documenting naming convention; consider migration path for non-compliant packages
- **Test Gaps:** N/A
- **Documentation Gaps:** Missing package naming convention documentation

### FINDING-013: Low - Test File Location Inconsistency
- **Finding ID:** LOW-003
- **Severity:** low
- **File Path:** `platform/typescript/design-system/src/`
- **Module:** platform/typescript/design-system
- **Problem to Resolve:** Tests co-located with source files (`__tests__/` subdirs) - not necessarily wrong but inconsistent with Java pattern
- **Why It Matters:** Slight cognitive overhead; different patterns across languages
- **Evidence:** Tests in `src/organisms/__tests__/`, `src/molecules/__tests__/` etc.
- **Consumer Impact:** Minimal; mostly developer experience
- **Duplication Type:** none (pattern inconsistency)
- **Consolidation Recommendation:** Document chosen pattern; ensure consistency within TypeScript codebase
- **Target Location:** Add to TypeScript style guide
- **Migration Notes:** N/A - pattern is valid, just needs documentation
- **Exact Fix Recommendation:** Document test placement convention in CONTRIBUTING.md
- **Test Gaps:** N/A
- **Documentation Gaps:** Missing test placement convention documentation

### FINDING-014: Low - Deprecated Agent Types and APIs
- **Finding ID:** LOW-004
- **Severity:** low
- **File Path:** `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentType.java`
- **Module:** platform/java/agent-core
- **Problem to Resolve:** Deprecated agent types may still be referenced
- **Why It Matters:** Dead code paths; potential confusion for new developers
- **Evidence:** File contains `@Deprecated` annotations per grep search results
- **Consumer Impact:** Consumers may use deprecated APIs unintentionally
- **Duplication Type:** none (deprecated code)
- **Consolidation Recommendation:** Document deprecation timeline; add migration guide; plan removal
- **Target Location:** Add deprecation documentation
- **Migration Notes:**
  1. Audit deprecated usage across products
  2. Create migration guide
  3. Set removal timeline
  4. Add deprecation warnings in build
- **Exact Fix Recommendation:** Add `@deprecated` Javadoc with migration path; update README with deprecation schedule
- **Test Gaps:** Tests may still cover deprecated APIs
- **Documentation Gaps:** Missing deprecation schedule and migration guide

### FINDING-015: Medium - ActiveJ Vendoring Documentation Gap
- **Finding ID:** MED-006
- **Severity:** medium
- **File Path:** `/io/activej/package-info.java`
- **Module:** io/activej
- **Problem to Resolve:** Well-documented vendoring, but unclear what specific patches were applied
- **Why It Matters:** Future ActiveJ upgrades require understanding what changes were made
- **Evidence:** File documents vendoring policy well but doesn't list specific deviations from upstream
- **Consumer Impact:** Difficult to upgrade ActiveJ version
- **Duplication Type:** none (documentation gap)
- **Consolidation Recommendation:** Document each patched file and the reason for the patch
- **Target Location:** `io/README.md` or `io/activej/PATCHES.md`
- **Migration Notes:**
  1. Create PATCHES.md documenting each file and deviation
  2. Include original ActiveJ version vendored
  3. Document upgrade procedure
- **Exact Fix Recommendation:** Create `io/activej/PATCHES.md` with table: File, Original Version, Reason for Patch, GHATANA-PATCH comment location
- **Test Gaps:** No tests verifying vendored code behavior
- **Documentation Gaps:** Missing patch documentation (though vendoring policy is well-documented)

---

## Module-by-Module Review

### io/activej - ActiveJ Vendored Patches

**Name and Path:** `/Users/samujjwal/Development/ghatana/io/activej`

**Purpose:** Contains minimal ActiveJ framework patches for Ghatana-specific requirements

**Main Exports:**
- `Stopwatch` - timing utility
- `AbstractPromise` / `SettablePromise` - async promise implementations
- Eventloop patches

**Main Consumers:** Entire Java platform (via `platform/java/core` dependency on ActiveJ)

**Key Dependencies:** None (vendored from ActiveJ upstream)

**Review Status:** Well-contained, properly documented

**Findings:**
- MED-006: Patch documentation could be more detailed
- No code duplication issues
- Clear vendoring policy with GHATANA-PATCH comments

**Duplicates/Overlaps:** None - unique vendored code

**Consolidation Opportunities:** None - appropriately isolated

**Test Gaps:** No tests for vendored code modifications

**Documentation Gaps:** Specific patch reasons not documented in one place

**Naming Concerns:** None - follows `io.activej` package convention

**Maintainability Concerns:** Upgrade path unclear without patch documentation

**Verdict:** ACCEPTABLE with documentation improvement needed

---

### platform/java/core - Platform Foundation

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/java/core`

**Purpose:** Foundational utilities, types, and patterns used across all products

**Main Exports:**
- `JsonUtils` - JSON serialization with canonical format
- `DateTimeUtils` - date/time utilities
- Validation framework (`ValidationService`, validators)
- Exception base classes
- Metrics foundation (`MetricsCollector` interface)

**Main Consumers:** All platform/java modules, shared-services, products

**Key Dependencies:** 
- ActiveJ (promise, eventloop)
- Jackson (JSON)
- Micrometer (metrics foundation)
- SLF4J (logging)
- Lombok

**Review Status:** Generally well-structured but has architectural coupling issues

**Findings:**
- CRIT-001: Circular dependency with domain module
- HIGH-003: Documentation standards inconsistent
- MED-002: Error handling patterns could be more standardized

**Duplicates/Overlaps:** 
- `JsonUtils` - check if other modules have similar utilities (kernel has one)

**Consolidation Opportunities:**
- Consolidate validation frameworks with domain module
- Merge duplicate JSON utilities

**Test Gaps:**
- ArchUnit tests for dependency direction missing
- Documentation coverage tests missing

**Documentation Gaps:**
- Missing architecture decision records
- Error handling guide needed

**Naming Concerns:** Minimal - naming is consistent

**Maintainability Concerns:**
- Circular dependencies must be resolved
- Test coverage for validation framework

**Verdict:** NEEDS IMPROVEMENT - critical circular dependency must be resolved

---

### platform/java/kernel - Kernel Abstractions

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/java/kernel`

**Purpose:** Core kernel abstractions: module lifecycle, plugin system, registries

**Main Exports:**
- `KernelRegistry` - canonical public registry contract
- `CrossScopeAuditService` - scope-aware audit service
- `JsonUtils` - JSON utilities (duplicate of core?)
- `AnalyticsContractBridge` - analytics integration bridge

**Main Consumers:** Platform modules, plugins, product modules

**Key Dependencies:** 
- ActiveJ
- Jackson
- core module (circular dependency concern)

**Review Status:** Well-designed public API, good documentation examples

**Findings:**
- CRIT-001: Involved in circular dependency chain
- No other material issues found

**Duplicates/Overlaps:** 
- `JsonUtils` - appears to duplicate core's version

**Consolidation Opportunities:**
- Use core's `JsonUtils` instead of local copy

**Test Gaps:** 
- No kernel purity validation tests visible

**Documentation Gaps:**
- Internal sub-registry documentation references `@KernelInternal` - ensure this is documented

**Naming Concerns:** None - clear naming conventions

**Maintainability Concerns:**
- Documentation quality is high (good example for other modules)
- Resolve circular dependency

**Verdict:** GOOD with minor consolidation opportunity

---

### platform/java/security - Security Module

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/java/security`

**Purpose:** Authentication, authorization, encryption, JWT handling

**Main Exports:**
- `JwtTokenProvider` - JWT token generation/validation
- `OAuth2Provider` - OAuth2/OIDC integration
- `OidcSessionManager` - session management
- `PasswordHasher` - password hashing (BCrypt)
- Encryption utilities (BouncyCastle)

**Main Consumers:** auth-gateway, user-profile-service, all products

**Key Dependencies:**
- core, config, domain, observability, governance, http
- Nimbus JOSE+JWT
- jBCrypt
- BouncyCastle
- Caffeine (caching)

**Review Status:** Heavy dependency chain; centralizes security well

**Findings:**
- MED-003: Verify no duplication with auth-gateway

**Duplicates/Overlaps:**
- auth-gateway may duplicate JWT logic - verify

**Consolidation Opportunities:**
- Ensure auth-gateway uses this module exclusively
- Document as canonical security implementation

**Test Gaps:**
- Cross-service JWT compatibility tests needed

**Documentation Gaps:**
- Security module usage guide needed

**Naming Concerns:** None

**Maintainability Concerns:**
- Many dependencies - consider splitting if grows

**Verdict:** GOOD - verify no duplication with auth-gateway

---

### platform/java/observability - Observability Module

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/java/observability`

**Purpose:** Metrics, tracing, logging, health checks

**Main Exports:**
- `MetricsCollector` / `MetricsCollectorFactory`
- `TraceHttpService` - HTTP tracing
- Health check utilities
- OpenTelemetry integration

**Main Consumers:** All services, shared-services

**Key Dependencies:**
- core, runtime, config, http
- Micrometer (metrics)
- OpenTelemetry (tracing)
- ClickHouse (trace storage)
- Redis (Jedis for health checks)
- AspectJ

**Review Status:** Feature-rich but has disabled code

**Findings:**
- MED-004: Disabled features need documentation/clarification

**Duplicates/Overlaps:** None identified

**Consolidation Opportunities:** None

**Test Gaps:** Tests for disabled features may be disabled too

**Documentation Gaps:**
- Feature status documentation needed
- Disabled features not documented

**Naming Concerns:** None

**Maintainability Concerns:**
- Remove or clearly mark experimental code

**Verdict:** ACCEPTABLE - document disabled features

---

### platform/java/domain - Domain Models

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/java/domain`

**Purpose:** Domain models shared across platform modules

**Main Exports:**
- Event types (`GEventType`, `EventParameterSpec`)
- Schema definitions
- JSON Schema validation

**Main Consumers:** All platform modules, products

**Key Dependencies:**
- core (circular dependency issue)
- contracts
- Jackson
- ActiveJ
- JSON Schema validator (NetworkNT)

**Review Status:** Central to circular dependency problem

**Findings:**
- CRIT-001: Domain imports from core service classes (violates architecture)

**Duplicates/Overlaps:**
- Event type definitions may overlap with contracts module

**Consolidation Opportunities:**
- Extract domain interfaces from core
- Use domain interfaces instead of core service classes

**Test Gaps:** Event type validation tests exist but may not cover architectural constraints

**Documentation Gaps:**
- Domain model documentation
- Architecture constraints not documented

**Naming Concerns:** None

**Maintainability Concerns:**
- CRITICAL: Must resolve circular dependency

**Verdict:** NEEDS IMMEDIATE ATTENTION - circular dependency violation

---

### platform/typescript/design-system - UI Component Library

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/typescript/design-system`

**Purpose:** Core design system components - Atomic Design methodology, WCAG AA compliant

**Main Exports:**
- 45+ Atoms (Button, Input, Badge, etc.)
- 30+ Molecules (Card, Dialog, Table, etc.)
- 10+ Organisms (DashboardLayout, AppHeader, etc.)
- 15+ Hooks (useControllableState, useDialog, etc.)
- Layout components (Box, Stack, Grid, etc.)

**Main Consumers:** All UI products, canvas, platform-shell

**Key Dependencies:**
- `@ghatana/platform-utils`
- Peer: `@ghatana/theme`, `@ghatana/tokens`, `react`, `react-dom`, `react-router`
- clsx, tailwind-merge, lucide-react

**Review Status:** Comprehensive but complex peer dependency chain

**Findings:**
- HIGH-001: May have duplicate cn() utility
- MED-005: Complex peer dependencies need documentation
- LOW-003: Test placement pattern needs documentation

**Duplicates/Overlaps:**
- HIGH-001: cn() utility may be duplicated

**Consolidation Opportunities:**
- Ensure single source for utilities

**Test Gaps:**
- Tests exist for major components (ErrorBoundary, ProtectedRoute, DataGrid, etc.)
- Coverage gaps in smaller components

**Documentation Gaps:**
- Peer dependency installation guide needed
- Test placement convention not documented

**Naming Concerns:** None - follows Atomic Design naming

**Maintainability Concerns:**
- Large API surface - versioning strategy needed
- Peer dependency management

**Verdict:** GOOD with documentation improvements needed

---

### platform/typescript/tokens - Design Tokens

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/typescript/tokens`

**Purpose:** Framework-agnostic design tokens (colors, spacing, typography, etc.)

**Main Exports:**
- Colors, spacing, typography tokens
- Shadows, borders, breakpoints
- CSS variable generation utilities
- Token validation (Zod schemas)

**Main Consumers:** design-system, theme, all UI packages

**Key Dependencies:**
- Zod (validation)

**Review Status:** Clean, focused module

**Findings:** None material

**Duplicates/Overlaps:** None

**Consolidation Opportunities:** None

**Test Gaps:** Tests exist (`vitest` in devDependencies)

**Documentation Gaps:**
- Token usage examples could be expanded

**Naming Concerns:**
- LOW-002: Package naming inconsistent (should be `@ghatana/platform-tokens`?)

**Maintainability Concerns:** None

**Verdict:** GOOD - well-structured focused module

---

### platform/typescript/foundation/platform-utils - Shared Utilities

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/typescript/foundation/platform-utils`

**Purpose:** Shared utility functions (consolidated from DCMAAR and YAPPC)

**Main Exports:**
- `cn()` - class name merging (clsx + tailwind-merge)
- Formatters
- Platform detection
- Responsive utilities
- Accessibility utilities

**Main Consumers:** design-system, utils (re-export)

**Key Dependencies:** None (utility library)

**Review Status:** Clean consolidation of previously duplicated utilities

**Findings:**
- HIGH-001: Ensure this is the ONLY cn() implementation

**Duplicates/Overlaps:**
- utils package re-exports this (redundant)

**Consolidation Opportunities:**
- MED-001: Deprecate utils package

**Test Gaps:** Unknown - need to verify test coverage

**Documentation Gaps:**
- Migration notes for DCMAAR/YAPPC products helpful

**Naming Concerns:** None

**Maintainability Concerns:** None

**Verdict:** GOOD - appropriate consolidation target

---

### platform/typescript/utils - Utility Re-exports

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/typescript/utils`

**Purpose:** Re-exports from platform-utils (redundant package)

**Main Exports:** Re-export of `@ghatana/platform-utils`

**Main Consumers:** (unknown - should be migrated)

**Key Dependencies:** `@ghatana/platform-utils`

**Review Status:** Redundant layer

**Findings:**
- MED-001: Package provides no unique value

**Duplicates/Overlaps:**
- Complete duplication of platform-utils exports

**Consolidation Opportunities:**
- MED-001: Deprecate and remove

**Test Gaps:** N/A

**Documentation Gaps:** N/A

**Naming Concerns:**
- LOW-002: Inconsistent naming (@ghatana/utils vs @ghatana/platform-utils)

**Maintainability Concerns:**
- Unnecessary abstraction layer

**Verdict:** DEPRECATE - no material value

---

### platform/contracts - Canonical API Contracts

**Name and Path:** `/Users/samujjwal/Development/ghatana/platform/contracts`

**Purpose:** Protobuf/gRPC contracts with schema generation and POJO generation

**Main Exports:**
- Protobuf definitions
- Generated Java POJOs (with Lombok builders)
- JSON schemas
- gRPC service definitions

**Main Consumers:** All platform modules, products, SDKs

**Key Dependencies:**
- Protobuf
- gRPC
- Jackson (schema generation)
- jsonschema2pojo (POJO generation)

**Review Status:** Complex but well-structured code generation pipeline

**Findings:** None material

**Duplicates/Overlaps:**
- May overlap with domain module event types - verify

**Consolidation Opportunities:**
- Ensure contracts are canonical source for cross-service types

**Test Gaps:** Contract compatibility tests needed

**Documentation Gaps:**
- Contract evolution/versioning strategy needed

**Naming Concerns:** None

**Maintainability Concerns:**
- Code generation pipeline complexity

**Verdict:** GOOD - complex but appropriate for its purpose

---

### shared-services/auth-gateway - Authentication Service

**Name and Path:** `/Users/samujjwal/Development/ghatana/shared-services/auth-gateway`

**Purpose:** Centralized authentication and security gateway (JWT/OAuth2)

**Main Exports:**
- `AuthGatewayLauncher` - service launcher
- `AuthService` - OIDC-based authentication service
- Token validation endpoints
- Cross-product token exchange

**Main Consumers:** All products requiring authentication

**Key Dependencies:**
- platform: http, observability, core, security, config, database
- ActiveJ (HTTP, Promise, Inject, Launcher)
- Nimbus (OAuth2, JWT)
- jBCrypt, Caffeine, Jackson, HikariCP, PostgreSQL

**Review Status:** Production-ready service with comprehensive endpoints

**Findings:**
- MED-003: Verify no JWT logic duplication with platform security

**Duplicates/Overlaps:**
- MED-003: May duplicate JWT logic - investigation needed

**Consolidation Opportunities:**
- Ensure exclusive use of platform security module

**Test Gaps:**
- Cross-service JWT compatibility tests needed
- End-to-end authentication flow tests needed

**Documentation Gaps:**
- API documentation for endpoints
- Integration guide for products

**Naming Concerns:** None

**Maintainability Concerns:**
- Many dependencies - watch for version conflicts

**Verdict:** GOOD - verify no security logic duplication

---

### shared-services/ai-inference-service - AI Inference Gateway

**Name and Path:** `/Users/samujjwal/Development/ghatana/shared-services/ai-inference-service`

**Purpose:** LLM inference service with provider routing, caching, rate limiting

**Main Exports:**
- `AIInferenceServiceLauncher`
- LLM Gateway with OpenAI integration
- Embedding service
- Prompt caching
- Rate limiting

**Main Consumers:** Products requiring AI/LLM capabilities

**Key Dependencies:**
- platform: ai-integration, http, observability, core, database
- ActiveJ
- Jackson
- PostgreSQL, HikariCP

**Review Status:** Well-structured MVP with documented production roadmap

**Findings:** None material

**Duplicates/Overlaps:** None identified

**Consolidation Opportunities:** None

**Test Gaps:**
- LLM provider contract tests needed
- Rate limiting tests needed

**Documentation Gaps:**
- Production configuration guide needed
- Provider extension guide needed

**Naming Concerns:** None

**Maintainability Concerns:**
- TODOs for production features documented in code

**Verdict:** GOOD - clear production roadmap

---

### shared-services/user-profile-service - User Profile Management

**Name and Path:** `/Users/samujjwal/Development/ghatana/shared-services/user-profile-service`

**Purpose:** Centralized user preferences and profile management

**Main Exports:**
- `UserProfileService` - HTTP service launcher
- `UserProfileStore` - profile storage abstraction
- Profile CRUD endpoints

**Main Consumers:** Products requiring user profiles

**Key Dependencies:**
- platform: http, config, security, database, observability
- ActiveJ
- Jackson
- PostgreSQL, HikariCP

**Review Status:** Clean, focused service

**Findings:** None material

**Duplicates/Overlaps:** None identified

**Consolidation Opportunities:** None

**Test Gaps:**
- Multi-tenant isolation tests needed
- Profile validation tests needed

**Documentation Gaps:**
- API documentation needed
- Multi-tenancy guide needed

**Naming Concerns:** None

**Maintainability Concerns:** None

**Verdict:** GOOD - clean focused service

---

### shared-services/feature-store-ingest - Feature Ingestion Pipeline

**Name and Path:** `/Users/samujjwal/Development/ghatana/shared-services/feature-store-ingest`

**Purpose:** Real-time feature engineering pipeline from EventLogStore

**Main Exports:**
- `FeatureStoreIngestLauncher`
- Event stream processing
- Feature extraction pipeline

**Main Consumers:** ML pipelines, AI Platform

**Key Dependencies:**
- platform: ai-integration, observability, core, domain
- data-cloud SPI
- ActiveJ
- Jackson

**Review Status:** Mock implementation with documented production path

**Findings:** None material

**Duplicates/Overlaps:**
- May overlap with data-cloud's own feature-store-ingest - verify

**Consolidation Opportunities:**
- Verify relationship with data-cloud's feature store implementation

**Test Gaps:**
- Feature extraction tests needed
- Integration tests with EventLogStore needed

**Documentation Gaps:**
- Production configuration guide needed
- EventLogStore integration guide needed

**Naming Concerns:** None

**Maintainability Concerns:**
- Currently mock implementation

**Verdict:** ACCEPTABLE - documented as mock/prototype

---

## Contract and API Risks

### Public API Stability Assessment

| Module | API Stability | Breaking Change Risk | Consumer Impact |
|--------|---------------|---------------------|-----------------|
| platform/java/core | STABLE | Low | Critical - foundation |
| platform/java/kernel | STABLE | Low | High - registry contract |
| platform/java/security | EVOLVING | Medium | High - auth patterns |
| platform/java/observability | STABLE | Low | Medium - metrics APIs |
| platform/java/domain | UNSTABLE | High | Critical - circular deps |
| platform/contracts | STABLE | Low | Critical - cross-service |
| platform/typescript/design-system | EVOLVING | Medium | High - UI components |
| platform/typescript/tokens | STABLE | Low | Medium - design tokens |

### API Risk Findings

**API-RISK-001: Domain Module API Instability (HIGH)**
- **Risk:** Circular dependencies force API changes
- **Mitigation:** Resolve CRIT-001 before domain APIs stabilize
- **Timeline:** Must fix before v1.0 release

**API-RISK-002: Promise Pattern API Inconsistency (MEDIUM)**
- **Risk:** Teams may misapply `Promise.of()` to blocking operations or cargo-cult historical test fixes into production code, causing event-loop blocking and inconsistent async behavior
- **Mitigation:** Document and enforce the corrected HIGH-004 rule: `Promise.ofBlocking(executor, ...)` for blocking work, `Promise.of(...)` for already-computed values, `EventloopTestBase` for tests
- **Timeline:** Next maintenance sprint

**API-RISK-003: TypeScript Peer Dependency Version Ranges (MEDIUM)**
- **Risk:** Tight peer dependency ranges may cause conflicts
- **Mitigation:** Document MED-005, test with multiple versions
- **Timeline:** Next release cycle

---

## Duplicate Code and Logic

### Confirmed Duplicates

**DUP-001: cn() Utility (TypeScript)**
- **Locations:** `platform-utils` (canonical), possibly `design-system`, `canvas`
- **Type:** Code duplication
- **Fix:** Ensure single implementation in `platform-utils`
- **Priority:** High

**DUP-002: utils Package Re-export**
- **Locations:** `utils` → re-exports `platform-utils`
- **Type:** Complete duplication
- **Fix:** Deprecate `utils` package (MED-001)
- **Priority:** Medium

**DUP-003: JsonUtils (Java)**
- **Locations:** `core`, `kernel`
- **Type:** Utility duplication
- **Fix:** Consolidate to `core` module
- **Priority:** Medium

### Potential Duplicates (Requires Verification)

**DUP-POT-001: JWT Logic**
- **Locations:** `platform/java/security`, `shared-services/auth-gateway`
- **Type:** Security logic
- **Verification:** Ensure auth-gateway uses platform module exclusively
- **Priority:** Medium

**DUP-POT-002: Validation Frameworks**
- **Locations:** `core`, `domain`, `governance`
- **Type:** Logic overlap
- **Verification:** Audit validation implementations
- **Priority:** Low

### Duplicate Prevention Measures

1. **ArchUnit Tests:** Add dependency tests preventing future circular deps
2. **Code Ownership:** Define clear ownership for shared concerns
3. **Package Documentation:** Document which package owns each concern

---

## Duplicate Effort and Overlapping Responsibilities

### Ownership Overlap

| Concern | Current Owners | Consolidation Target |
|---------|---------------|---------------------|
| JSON Utilities | core, kernel | core |
| Validation | core, domain, governance | core (framework), domain (rules) |
| cn() Utility | platform-utils, design-system? | platform-utils |
| Test Utilities | testing module, individual modules | testing module |

### Overlapping Responsibilities Analysis

**RESP-001: Validation Responsibility Split**
- **Current:** `core` has framework, `domain` has domain rules, `governance` has security validation
- **Issue:** Unclear where new validation logic should go
- **Resolution:** Document: core=framework, domain=business rules, governance=security

**RESP-002: Metrics/Monitoring**
- **Current:** `core` has interfaces, `observability` has implementations
- **Issue:** Well-separated but could be clearer
- **Resolution:** Document responsibility split

---

## Sprawled Modules and Fragmented Ownership

### Sprawl Assessment

| Module | Files | Assessment | Recommendation |
|--------|-------|-----------|----------------|
| design-system | ~186 | Appropriate for scope | Maintain current structure |
| agent-core | ~202 | Large but cohesive | Consider sub-module split if grows |
| observability | ~83 | Well-contained | Maintain |
| security | ~75 | Well-contained | Maintain |
| kernel | ~96 | Well-contained | Maintain |

### Fragmented Ownership Patterns

**FRAG-001: TypeScript Utility Packages**
- **Issue:** `tokens`, `theme`, `platform-utils`, `utils` have related but separate ownership
- **Resolution:** Document interdependencies; consider single owner for design system ecosystem

**FRAG-002: Test Utilities**
- **Issue:** Testing utilities split between `platform/java/testing` and individual modules
- **Resolution:** Define clear boundary: testing=framework, modules=domain-specific fixtures

---

## Consolidation Opportunities

### Priority 1: Critical (Immediate)

**CONS-001: Resolve Circular Dependencies**
- **Action:** Extract domain interfaces from core service classes
- **Effort:** Large (463 files)
- **Impact:** Critical - unblocks clean architecture
- **Owner:** Platform Architecture Team

### Priority 2: High (Next Sprint)

**CONS-002: Centralize cn() Utility**
- **Action:** Verify single implementation in platform-utils
- **Effort:** Small
- **Impact:** Medium - reduces maintenance
- **Owner:** UI Platform Team

**CONS-003: Standardize Lombok Configuration**
- **Action:** Create convention plugin with standard Lombok setup
- **Effort:** Medium
- **Impact:** Medium - consistent builds
- **Owner:** Build System Team

### Priority 3: Medium (Next Quarter)

**CONS-004: Deprecate utils Package**
- **Action:** Mark deprecated, migrate consumers, remove
- **Effort:** Medium
- **Impact:** Low - cleanup
- **Owner:** UI Platform Team

**CONS-005: Consolidate JsonUtils**
- **Action:** Remove kernel's JsonUtils, use core's
- **Effort:** Small
- **Impact:** Low - cleanup
- **Owner:** Java Platform Team

**CONS-006: Standardize Error Handling**
- **Action:** Create PlatformException hierarchy
- **Effort:** Medium
- **Impact:** Medium - consistent APIs
- **Owner:** Java Platform Team

### Priority 4: Low (Backlog)

**CONS-007: Package Naming Standardization**
- **Action:** Document or migrate to `@ghatana/platform-*` convention
- **Effort:** Large
- **Impact:** Low - consistency
- **Owner:** Platform Architecture Team

---

## Recommended Simplifications

### SIMPL-001: Remove Redundant Utils Package
- **Current:** `utils` re-exports `platform-utils`
- **Simplified:** Consumers depend on `platform-utils` directly
- **Benefit:** Reduced abstraction layer, clearer dependencies

### SIMPL-002: Document Disabled Observability Features
- **Current:** Disabled code in comments
- **Simplified:** Remove or clearly mark as experimental
- **Benefit:** Reduced confusion, clearer feature set

### SIMPL-003: Standardize Build Configuration
- **Current:** Lombok config varies by module
- **Simplified:** Single convention plugin
- **Benefit:** Consistent builds, easier maintenance

### SIMPL-004: Consolidate Documentation Standards
- **Current:** Varies by module (kernel good, others inconsistent)
- **Simplified:** Checkstyle-enforced standards
- **Benefit:** Consistent API documentation

---

## Naming and Documentation Issues

### Naming Inconsistencies

| Issue | Current State | Recommended State | Priority |
|-------|--------------|-------------------|----------|
| Package prefix | Mixed `@ghatana/*` and `@ghatana/platform-*` | Standardize on `@ghatana/platform-*` for platform | Low |
| Test directory | `__tests__/` co-located | Document convention | Low |
| Module naming | `design-system` vs `tokens` | Both valid, document taxonomy | Low |

### Documentation Gaps by Module

| Module | Critical Gaps | Medium Gaps |
|--------|--------------|-------------|
| core | Architecture decisions, Error handling guide | Changelog |
| kernel | Patch documentation (ActiveJ) | Changelog |
| security | Usage guide | Changelog |
| observability | Disabled features status | Feature roadmap |
| domain | Architecture constraints | Domain model guide |
| design-system | Peer dependency install guide | Component usage examples |
| tokens | Token usage examples | Theming guide |

### Documentation Standards Recommendation

1. **Minimum Required:**
   - README.md with: purpose, installation, basic usage
   - CHANGELOG.md following Keep a Changelog
   - Class-level Javadoc for public APIs
   - Method-level Javadoc for public methods

2. **Recommended:**
   - Architecture Decision Records (ADRs) for major decisions
   - API usage examples
   - Migration guides for breaking changes

---

## Dead Code and Redundant Abstractions

### Identified Dead Code

**DEAD-001: Deprecated Agent Types**
- **Location:** `platform/java/agent-core/src/.../AgentType.java`
- **Status:** Marked @Deprecated
- **Action:** Create migration guide; set removal timeline

**DEAD-002: Disabled Observability Features**
- **Location:** `platform/java/observability/build.gradle.kts` comments
- **Status:** Commented as disabled
- **Action:** Remove or move to experimental package

**DEAD-003: utils Package (Pending Deprecation)**
- **Location:** `platform/typescript/utils`
- **Status:** Functional but redundant
- **Action:** Deprecate and schedule removal (MED-001)

### Redundant Abstractions

**RED-001: Utils Re-export Layer**
- **Abstraction:** `utils` → `platform-utils`
- **Value:** None - pure pass-through
- **Recommendation:** Remove

**RED-002: Potential JsonUtils Duplication**
- **Abstraction:** kernel's JsonUtils vs core's JsonUtils
- **Value:** Unclear - verify if they serve different purposes
- **Recommendation:** Consolidate to core

---

## Performance Concerns

### Performance-Related Findings

**PERF-001: Blocking Work Incorrectly Wrapped as Immediate Promises**
- **Issue:** Some teams may wrap blocking I/O in `Promise.of(...)` or otherwise run it inline, which executes on the caller thread and can starve the ActiveJ event loop
- **Impact:** Latency spikes, reduced concurrency, and misleading tests when Promise lifecycles are exercised outside `EventloopTestBase`
- **Recommendation:** Audit blocking I/O call sites and move them to `Promise.ofBlocking(executor, ...)`; keep `Promise.of(...)` only for already-computed values and test doubles

**PERF-002: Lombok Build Performance**
- **Issue:** Annotation processing adds build time
- **Impact:** Slower builds
- **Recommendation:** Standardize config; consider incremental annotation processing

**PERF-003: TypeScript Peer Dependencies**
- **Issue:** Multiple versions may be installed
- **Impact:** Bundle size
- **Recommendation:** Document version constraints; test with common version ranges

### Performance Testing Gaps

1. No load tests for shared services
2. No bundle size monitoring for TypeScript packages
3. No build time regression tests

---

## Missing Test Coverage

### Critical Test Gaps

**TEST-001: Architecture Tests (ArchUnit)**
- **Missing:** Dependency direction enforcement
- **Impact:** Circular dependencies can recur
- **Priority:** Critical

**TEST-002: Cross-Service Integration**
- **Missing:** JWT compatibility between auth-gateway and platform security
- **Impact:** Token validation failures in production
- **Priority:** High

**TEST-003: Documentation Coverage**
- **Missing:** Automated documentation coverage checks
- **Impact:** Inconsistent API documentation
- **Priority:** High

### Test Coverage by Module

| Module | Unit Tests | Integration Tests | Architecture Tests | Coverage Rating |
|--------|-----------|-------------------|-------------------|-----------------|
| core | Partial | Missing | Missing | MEDIUM |
| kernel | Partial | Missing | Missing | MEDIUM |
| security | Partial | Missing | Missing | MEDIUM |
| observability | Partial | Missing | Missing | MEDIUM |
| design-system | Good (307 test matches) | Missing | N/A | GOOD |
| tokens | Present (vitest) | Missing | N/A | MEDIUM |
| auth-gateway | Partial | Missing | Missing | MEDIUM |

---

## Full Remediation Plan

### Phase 1: Critical (Weeks 1-2)

**Objective:** Resolve blocking issues

| Task | Owner | Effort | Deliverable |
|------|-------|--------|-------------|
| CRIT-001: Document circular dependency plan | Architecture Team | 2d | ADR-001: Domain Interface Extraction |
| CRIT-001: Extract domain interfaces (batch 1) | Platform Team | 5d | Interfaces for Event, Operator |
| HIGH-004: Audit blocking I/O Promise call sites and document canonical async rule | Java Team | 2d | Audit report + async pattern guide |
| TEST-001: Add ArchUnit dependency tests | Platform Team | 3d | ArchUnit tests preventing circular deps |

### Phase 2: High Priority (Weeks 3-4)

**Objective:** Consolidation and standardization

| Task | Owner | Effort | Deliverable |
|------|-------|--------|-------------|
| HIGH-001: Consolidate cn() utility | UI Team | 2d | Single implementation verified |
| HIGH-002: Create Lombok convention plugin | Build Team | 3d | Convention plugin applied to all modules |
| HIGH-003: Define documentation standards | Docs Team | 2d | Documentation standards documented |
| MED-001: Deprecate utils package | UI Team | 2d | Deprecation notice added |
| CONS-006: Create PlatformException hierarchy | Java Team | 3d | Exception hierarchy in core |

### Phase 3: Medium Priority (Month 2)

**Objective:** Cleanup and documentation

| Task | Owner | Effort | Deliverable |
|------|-------|--------|-------------|
| MED-004: Document disabled observability features | Observability Team | 1d | Feature status documentation |
| MED-005: Create peer dependency guide | UI Team | 2d | Installation guide |
| MED-003: Verify auth-gateway JWT usage | Security Team | 2d | Verification report |
| LOW-001: Add CHANGELOG.md to all modules | All Teams | Ongoing | Changelogs present |
| MED-006: Document ActiveJ patches | Platform Team | 2d | PATCHES.md created |

### Phase 4: Ongoing Maintenance

**Objective:** Prevent regression

| Task | Frequency | Owner |
|------|-----------|-------|
| ArchUnit dependency checks | Per PR | CI/CD |
| Documentation coverage check | Monthly | Docs Team |
| Duplicate code scan | Quarterly | Platform Team |
| Package dependency audit | Monthly | Build Team |

---

## All Unresolved Findings By Severity

### Critical (3)

1. **CRIT-001:** Circular dependency in Core Service (core ↔ domain → multiple modules)

### High (8)

1. **HIGH-001:** Duplicate cn() utility across TypeScript packages
2. **HIGH-002:** Inconsistent Lombok configuration causing build failures
3. **HIGH-003:** Missing documentation standards across Java modules
4. **HIGH-004:** Blocking-vs-immediate Promise usage inconsistency
5. **API-RISK-001:** Domain Module API instability due to circular deps
6. **TEST-001:** Missing ArchUnit architecture tests
7. **TEST-002:** Missing cross-service JWT integration tests
8. **TEST-003:** Missing automated documentation coverage checks

### Medium (15)

1. **MED-001:** Redundant TypeScript utils package
2. **MED-002:** Inconsistent error handling patterns
3. **MED-003:** JWT provider duplication risk (auth-gateway vs platform security)
4. **MED-004:** Observability module disabled features need documentation
5. **MED-005:** TypeScript peer dependency complexity
6. **MED-006:** ActiveJ vendoring documentation gap
7. **CONS-002:** Centralize cn() utility
8. **CONS-003:** Standardize Lombok configuration
9. **CONS-004:** Deprecate utils package
10. **CONS-005:** Consolidate JsonUtils
11. **CONS-006:** Standardize error handling
12. **API-RISK-002:** Promise pattern API inconsistency
13. **API-RISK-003:** TypeScript peer dependency version ranges
14. **DUP-003:** JsonUtils duplication (core vs kernel)
15. **DUP-POT-001:** JWT logic potential duplication

### Low (12)

1. **LOW-001:** Missing CHANGELOG files
2. **LOW-002:** Inconsistent package naming convention
3. **LOW-003:** Test file location inconsistency
4. **LOW-004:** Deprecated agent types need migration guide
5. **CONS-007:** Package naming standardization
6. **DUP-POT-002:** Validation frameworks potential overlap
7. **DEAD-001:** Deprecated agent types
8. **DEAD-002:** Disabled observability features
9. **DEAD-003:** utils package (pending deprecation)
10. **RED-001:** Utils re-export layer
11. **RED-002:** Potential JsonUtils duplication
12. **PERF-001:** Blocking work incorrectly wrapped as immediate promises

---

## All Unresolved Findings By Module

### io/activej (1)

- MED-006: ActiveJ vendoring documentation gap

### platform/java/core (4)

- CRIT-001: Circular dependency with domain
- HIGH-003: Missing documentation standards
- MED-002: Inconsistent error handling
- DUP-003: JsonUtils duplication with kernel

### platform/java/kernel (3)

- CRIT-001: Involved in circular dependency chain
- DUP-003: JsonUtils duplication with core
- No kernel purity validation tests

### platform/java/domain (3)

- CRIT-001: Imports from core service classes
- MED-002: Validation framework overlap
- Missing architecture constraints documentation

### platform/java/security (2)

- MED-003: JWT duplication risk with auth-gateway
- Missing security module usage guide

### platform/java/observability (2)

- MED-004: Disabled features need documentation
- Missing feature roadmap

### platform/java/security + shared-services/auth-gateway (1)

- TEST-002: Missing cross-service JWT integration tests

### platform/typescript/utils (1)

- MED-001: Redundant package (consolidate to platform-utils)

### platform/typescript/design-system (3)

- HIGH-001: Potential cn() utility duplication
- MED-005: Peer dependency complexity
- LOW-003: Test location pattern needs documentation

### platform/typescript (general) (2)

- LOW-001: Missing CHANGELOG files
- LOW-002: Inconsistent package naming

### platform/contracts (1)

- Missing contract evolution/versioning documentation

### shared-services/auth-gateway (1)

- MED-003: Verify no JWT logic duplication with platform security

### shared-services/feature-store-ingest (1)

- Verify relationship with data-cloud's feature store

### Build System (2)

- HIGH-002: Inconsistent Lombok configuration
- CONS-003: Create Lombok convention plugin

### Documentation (4)

- HIGH-003: Missing documentation standards
- LOW-001: Missing CHANGELOG files
- MED-006: ActiveJ patch documentation
- TEST-003: Missing documentation coverage tests

### Testing (4)

- HIGH-004: Blocking-vs-immediate Promise usage inconsistency
- TEST-001: Missing ArchUnit tests
- TEST-002: Missing cross-service integration tests
- TEST-003: Missing documentation coverage tests

---

## Assumptions and Limitations

### Assumptions

1. **Scope Boundaries:** This audit focused on `io/`, `platform/`, and `shared-services/` only. Product-specific code was referenced for consumer analysis but not audited in depth.

2. **Build System:** Assumed Gradle build system as defined in build files is the canonical build mechanism.

3. **ActiveJ Version:** Assumed ActiveJ 6.0-beta2 based on observability module comments.

4. **Documentation Quality:** "Missing documentation" findings based on absence of standard documentation files, not code comment quality.

5. **Test Coverage:** Test gaps identified based on absence of test files or patterns, not code coverage metrics (no coverage reports analyzed).

### Limitations

1. **Static Analysis Only:** This audit was conducted via static code analysis without runtime verification or build execution.

2. **No Dependency Tree Analysis:** Full dependency tree analysis was not performed; circular dependencies identified from code search patterns and system memories.

3. **Limited Historical Context:** Some findings may be known issues with planned resolutions; no access to issue trackers or roadmaps.

4. **Generated Code:** Generated code (Protobuf, POJOs) was generally excluded from analysis except for contract verification.

5. **Test Execution:** Tests were not executed; test gaps identified based on file presence/absence.

6. **Documentation Completeness:** Only checked for presence of documentation files, not quality or accuracy.

7. **Product Code:** Product-specific usage patterns were inferred from search results, not comprehensive analysis.

### Recommendations for Follow-up

1. **Execute Build:** Run full build to verify current state of circular dependencies
2. **Run Tests:** Execute test suites to verify current coverage and failures
3. **Dependency Tree:** Generate full dependency tree visualization
4. **Coverage Analysis:** Run code coverage analysis for accurate metrics
5. **Product Interviews:** Interview product teams consuming shared modules
6. **Historical Review:** Review git history for context on architectural decisions

---

## Appendix A: Module Dependency Matrix

|  | core | kernel | domain | http | observability | security |
|--|------|--------|--------|------|--------------|----------|
| core | - | api | api | api | api | api |
| kernel | X (circular) | - | - | - | - | - |
| domain | api | impl? | - | api | api | - |
| http | api | - | - | - | - | - |
| observability | api | - | - | api | - | - |
| security | api | - | api | api | api | - |

Legend: `api` = API dependency, `impl` = implementation dependency, `X` = problematic

---

## Appendix B: Consolidation Priority Matrix

| Finding | Business Impact | Effort | Risk | Priority |
|---------|----------------|--------|------|----------|
| CRIT-001 (Circular deps) | Critical | High | Medium | P1 |
| HIGH-001 (cn() utility) | Medium | Low | Low | P2 |
| HIGH-002 (Lombok config) | Medium | Medium | Low | P2 |
| HIGH-003 (Documentation) | High | Medium | Low | P2 |
| HIGH-004 (Promise pattern) | High | Medium | Medium | P2 |
| MED-001 (utils package) | Low | Low | Low | P3 |
| MED-002 (Error handling) | Medium | Medium | Medium | P3 |
| MED-003 (JWT duplication) | High | Low | Low | P3 |

---

## Implementation Status

> Last updated: 2026-03-25. Tracks actions taken against each finding.

### Critical (P1)

| Finding | Description | Status | Notes |
|---------|-------------|--------|---------|
| CRIT-001 | Circular dependency — core ↔ kernel/domain | ✅ Resolved (platform layer) | **Investigation finding:** No circular build-level dependency exists in the current codebase. `platform/java/core` has no `operator`, `service`, or `pipeline` packages. ArchUnit guardrails added (`domainMustNotImportCoreOperators`, `platformDomainMustNotImportCoreStateImpl`, `coreMustNotImportDomainPipelineSpecs`). ADR-015 documents the 4-phase migration plan for if/when violations re-emerge. CONS-001 closed. |

### High (P2)

| Finding | Description | Status | Notes |
|---------|-------------|--------|-------|
| HIGH-001 | Duplicate `cn()` utility in TypeScript | ✅ Resolved (prior session) | `canvas/src/utils/cn.ts` removed; all consumers use `@ghatana/platform-utils`. |
| HIGH-002 | Missing Lombok test-scope config in 9 modules | ✅ Resolved | Added `testCompileOnly(libs.lombok)` + `testAnnotationProcessor(libs.lombok)` to: security, observability, config, connectors, agent-core, ai-integration, audit, governance, runtime. New `com.ghatana.lombok-conventions.gradle.kts` opt-in plugin created. |
| HIGH-003 | Missing `@doc.*` JavaDoc tags | ✅ Resolved | `checkDocTags` task wired to `check` lifecycle in `build.gradle.kts`. 340 stale baseline entries (from renamed `agent-framework` module) cleaned up. Missing `@doc.pattern` tags added to 8 `agent-core` files (`AgentResultStatus`, `HealthStatus`, `DeterminismGuarantee`, `FailureMode`, `StateMutability`, `Agent`, `AgentCapabilities`, `AgentMaterializationException`). 123 grandfathered violations remain in `gradle/doc-tag-baseline.txt` — to be reduced over time. |
| HIGH-004 | `Promise.ofBlocking()` pattern inconsistency | ✅ Resolved | `docs/platform-libraries/ASYNC_PATTERNS_GUIDE.md` created. ArchUnit rules `serviceAndStoreClassesMustNotCallThreadSleep()` and `mustNotCallCompletableFutureGet()` added to `PlatformArchitectureTest`. |

### Medium (P3)

| Finding | Description | Status | Notes |
|---------|-------------|--------|-------|
| MED-001 | Redundant `@ghatana/utils` package | ✅ Resolved | `package.json` marked deprecated. `README.md` with migration guide created. |
| MED-002 | Inconsistent error handling patterns | ✅ Resolved | `ErrorResponseBuilder` created in `com.ghatana.platform.core.exception`. 7 unit tests added. |
| MED-003 | JWT provider duplication risk | ✅ No action needed | Confirmed `auth-gateway` uses `com.ghatana.platform.security.port.JwtTokenProvider` from `platform/java/security`. No local duplicate exists. |
| MED-004 | Observability module feature limitations | ✅ Resolved | `platform/java/observability/README.md` created. Documents production-ready vs disabled features (`ObservabilityLauncher`, `@Monitored` AOP). |
| MED-005 | TypeScript package peer dependency complexity | ✅ Resolved | `platform/typescript/design-system/README.md` created with peer dep install commands and version table. |
| MED-006 | ActiveJ vendoring documentation gap | ✅ Resolved | `io/activej/PATCHES.md` created. Documents baseline version (6.0-rc2), all 4 vendored files, diff procedure, and upgrade checklist. `// GHATANA-PATCH:` markers added inline to `SettablePromise.java`, `Stopwatch.java`, `EventloopInspector.java`; Javadoc vendoring block added to `AbstractPromise.java`. **Ongoing action:** A full diff against upstream 6.0-rc2 is still needed to identify any non-annotated deviations. |

### Low (P4)

| Finding | Description | Status | Notes |
|---------|-------------|--------|-------|
| LOW-001 | Missing CHANGELOG files | ✅ Resolved | `CHANGELOG.md` created for: `platform/java/core`, `platform/java/kernel`, `platform/java/security`, `platform/java/observability`, `platform/typescript/design-system`. |
| LOW-002 | Inconsistent package naming convention | ✅ Resolved | `docs/adr/ADR-016-typescript-package-naming.md` created. Establishes two-tier model: `@ghatana/platform-*` for cross-cutting infrastructure; `@ghatana/<name>` for UI/domain packages. No renames required — the convention is forward-looking. |
| LOW-003 | Test file location inconsistency | ✅ Resolved | `CONTRIBUTING.md` created at repo root. Documents co-located `__tests__/` pattern for TypeScript (with examples), `src/test/java` mirror pattern for Java, and React Native guidelines. |
| LOW-004 | Deprecated agent types and APIs | ✅ Resolved | `platform/java/agent-core/DEPRECATION_GUIDE.md` created. Documents all 4 deprecated APIs (`AgentType.LLM`, `Agent` interface, `AgentCapabilities`, `PlannerRegistry` tenant-unaware overloads) with migration code samples and removal timeline (v3.0.0). All `@Deprecated` annotations upgraded to `@Deprecated(since = "...", forRemoval = true)`. |

### Consolidation Actions

| Finding | Description | Status | Notes |
|---------|-------------|--------|-------|
| CONS-001 | Resolve circular dependencies | ✅ Resolved (platform layer) | See CRIT-001 row above. |
| CONS-002 | Centralize `cn()` utility | ✅ Resolved | See HIGH-001 row above. |
| CONS-003 | Standardize Lombok configuration | ✅ Resolved | See HIGH-002 row above. |
| CONS-004 | Deprecate `utils` package | ✅ Resolved | See MED-001 row above. |
| CONS-005 | Consolidate `JsonUtils` | ✅ Resolved | `kernel/util/JsonUtils` now delegates to `core/util/JsonUtils`. Both methods deprecated for removal. |
| CONS-006 | Standardize error handling | ✅ Resolved | `ErrorResponseBuilder` is the canonical factory. See MED-002 row above. |

### Architecture Tests

| Rule | Test Method | Status |
|------|-------------|--------|
| Domain must not import core operators | `domainMustNotImportCoreOperators()` | ✅ Added |
| Domain must not import core state impl | `platformDomainMustNotImportCoreStateImpl()` | ✅ Added |
| Core must not import domain pipeline specs | `coreMustNotImportDomainPipelineSpecs()` | ✅ Added |
| Service/Store classes must not call `Thread.sleep` | `serviceAndStoreClassesMustNotCallThreadSleep()` | ✅ Added |
| Non-test code must not call `CompletableFuture.get()` | `mustNotCallCompletableFutureGet()` | ✅ Added |

All new rules use `allowEmptyShould(true)` — informational until baseline violations are cleared.

---

**End of Audit Report**
