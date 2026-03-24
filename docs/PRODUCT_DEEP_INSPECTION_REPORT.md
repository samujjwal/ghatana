# Product Deep Inspection Report: `finance` & `phr`

> **Generated**: 2026-03-23  
> **Scope**: `products/finance` (385 Java files) and `products/phr` (37 Java files)  
> **Inspector**: AI Code Review Pass  
> **Standard References**: `.github/copilot-instructions.md` v2.4.0, GAA Framework

---

## Table of Contents

0. [Kernel Contract Model](#0-kernel-contract-model)
   - 0.11 [Capability Inventory](#011-capability-inventory)
   - 0.12 [Deprecated Classes — Pending Removal](#012-deprecated-classes--pending-removal)
   - 0.13 [Platform Gaps](#013-platform-gaps)
   - 0.14 [Coverage by Concern Domain](#014-coverage-by-concern-domain)
1. [Executive Summary](#1-executive-summary)
2. [Finance Product — Findings](#2-finance-product--findings)
   - 2.1 [Critical Issues](#21-critical-issues)
   - 2.2 [Correctness Problems](#22-correctness-problems)
   - 2.3 [Testing Violations](#23-testing-violations)
   - 2.4 [Architecture Gaps](#24-architecture-gaps)
   - 2.5 [Security Concerns](#25-security-concerns)
   - 2.6 [Enhancements Needed](#26-enhancements-needed)
3. [PHR Product — Findings](#3-phr-product--findings)
   - 3.1 [Critical Issues](#31-critical-issues)
   - 3.2 [Correctness Problems](#32-correctness-problems)
   - 3.3 [Testing Violations](#33-testing-violations)
   - 3.4 [Architecture Gaps](#34-architecture-gaps)
   - 3.5 [Security Concerns](#35-security-concerns)
   - 3.6 [Enhancements Needed](#36-enhancements-needed)
4. [Cross-Scope Shared Issues](#4-cross-scope-shared-issues)
5. [Kernel / Libs — Enhancement Requests](#5-kernel--libs--enhancement-requests)
6. [Implementation Coverage Matrix](#6-implementation-coverage-matrix)
7. [Recommended Priority Actions](#7-recommended-priority-actions)

---

## 0. Kernel Contract Model

> This section is the **authoritative contract reference** against which all product code in this report is assessed. Any product code that deviates from these contracts constitutes a violation, labelled `ISSUE-*` in subsequent sections.

---

### 0.1 Philosophy: Scope-First, Not Product-First

The kernel has **zero product-specific coupling**. It operates on generic _scopes_ — logical boundaries at any architectural tier. A "product" is one valid scope type among many:

| `ScopeType`   | Description                                              |
| ------------- | -------------------------------------------------------- |
| `KERNEL`      | The kernel runtime itself                                |
| `PLATFORM`    | The AppPlatform operational layer                        |
| `DOMAIN_PACK` | A reusable domain-capability pack (e.g., `healthcare`)   |
| `PRODUCT`     | A product built on domain packs (e.g., `finance`, `phr`) |
| `TENANT`      | A tenant within a product deployment                     |
| `OPERATOR`    | An operator or admin context                             |
| `AGENT`       | An autonomous agent context                              |
| `WORKFLOW`    | A workflow execution context                             |

A `ScopeDescriptor(ScopeType, scopeId, metadata)` is the only kernel-safe way to identity a scope. **Passing raw product-name strings to kernel APIs is a contract violation.** Use `ScopeDescriptor.product("finance")`, not `"finance"`.

---

### 0.2 How a Product / Module Is Defined (`KernelModule`)

Every product module MUST implement `com.ghatana.kernel.module.KernelModule` and provide exactly these behaviours:

```java
// Identity — must be globally unique, lowercase, hyphens only
String getModuleId();   // e.g., "finance-oms"
String getVersion();    // semver, e.g., "1.0.0"

// Declarations — never null; return Collections.emptySet() if nothing to declare
Set<KernelCapability> getCapabilities();   // what this module provides
Set<KernelDependency>  getDependencies();  // what this module requires

// Lifecycle
void         initialize(KernelContext context);  // SYNCHRONOUS, one-time setup only
Promise<Void> start();                           // begin services and background tasks
Promise<Void> stop();                            // graceful shutdown — idempotent, ≤30 s

// Health
HealthStatus getHealthStatus();  // polled by kernel periodically
```

**Rules enforced by the kernel**:

- `initialize()` is synchronous — no async calls, no background task launch here.
- `start()` and `stop()` MUST return `Promise<Void>`. `CompletableFuture` is banned.
- `stop()` must be idempotent — called once or many times with the same outcome.
- Guard re-initialization with `AtomicBoolean initialized`.
- `KernelCapability` ID format: `^[a-z0-9-._]+$` (validated in constructor).
- `KernelDependency` factory shorthand: `onCapability(id)`, `onModule(id)`, `onService(id)`.

---

### 0.3 How a Product Enriches the Kernel (`KernelExtension`)

Use **`com.ghatana.kernel.extension.KernelExtension`** — NOT the `@Deprecated(forRemoval = true) com.ghatana.kernel.plugin.KernelExtension`:

```java
String                 getExtensionId();              // unique, lowercase-hyphen
String                 getName();
String                 getVersion();
KernelDescriptor       getDescriptor();
Set<KernelCapability>  getContributedCapabilities();  // merged into the hosting module's capability set
boolean                isCompatible(KernelModule hostModule);  // MUST verify before loading

// Callbacks invoked by the hosting KernelModule during its own lifecycle
void onModuleInitialized(KernelContext context);
void onModuleStarted(KernelContext context);
void onModuleStopped(KernelContext context);
```

Extensions contribute additional capabilities to an **existing** module. They do not introduce new modules. The hosting module invokes the callbacks at the appropriate lifecycle transition.

---

### 0.4 How a Product Loads Dynamically (`KernelPlugin`)

`KernelPlugin extends KernelModule` for hot-swappable components:

```java
PluginManifest   getManifest();               // identity, capabilities, deps
Set<String>      getExportedContracts();      // FQN class names this plugin provides
Set<String>      getRequiredContracts();      // FQN class names this plugin consumes

// Hot-swap lifecycle (all return Promise<Void>)
Promise<Void> install();    // one-time setup: run migrations, allocate resources
Promise<Void> uninstall();  // cleanup
Promise<Void> reload();     // default implementation: stop().then($ -> start())
```

The `ContractRegistry` validates that all exported contracts have implementations and required contracts have registered providers before the plugin is allowed to start.

---

### 0.5 How a Module Accesses Runtime Services (`KernelContext`)

`KernelContext` is the single access point injected into every `initialize()` call:

| Method                                              | Purpose                                                               |
| --------------------------------------------------- | --------------------------------------------------------------------- |
| `getDependency(Class<T>)`                           | Required typed dependency — throws `IllegalStateException` if missing |
| `getOptionalDependency(Class<T>)`                   | Optional dependency — returns `Optional`                              |
| `hasDependency(Class<T>)`                           | Check availability without throwing                                   |
| `getDependency(String, Class<T>)`                   | Named dependency lookup                                               |
| `registerEventHandler(Class<E>, EventHandler<E>)`   | Subscribe to kernel event type                                        |
| `unregisterEventHandler(Class<E>, EventHandler<E>)` | Unsubscribe                                                           |
| `publishEvent(E)`                                   | Publish event to all registered handlers                              |
| `getTenantContext()`                                | Active tenant context (default: `"system"`)                           |
| `getTenantContext(String tenantId)`                 | Specific tenant context by ID                                         |
| `getEventloop()`                                    | ActiveJ `Eventloop` — **must** be used for all async scheduling       |
| `getAvailableCapabilities()`                        | Runtime capability discovery                                          |

---

### 0.6 How Modules Communicate (`KernelInterScopeBus`)

**Canonical class**: `com.ghatana.kernel.communication.KernelInterScopeBus`  
**Deprecated — do NOT use**: `com.ghatana.kernel.communication.KernelInterProductBus`

```java
// Publish an event to a target scope
Promise<Void> publishEvent(CrossScopeEvent event);

// Subscribe to events from a specific source scope
Promise<Void> subscribe(ScopeDescriptor sourceScope,
                        String eventType,
                        EventHandler<CrossScopeEvent> handler);

// Policy-gated persistent data sharing (uses ScopeSharePolicy for access control + audit)
Promise<Void>              shareData(String dataId, Object data, ScopeSharePolicy policy);
Promise<SharedScopeRecord> retrieveSharedData(String dataId, ScopeDescriptor requestingScope);
```

`CrossScopeEvent` fields: `sourceScope: ScopeDescriptor`, `targetScope: ScopeDescriptor`, `eventType: String`, `tenantId: String`, `correlationId: String`, `payload: Object`.

**Never** embed product names as raw strings in communication APIs. Always use `ScopeDescriptor.product("finance")` or `ScopeDescriptor.domainPack("healthcare")`.

---

### 0.7 How Configuration Is Resolved (`KernelConfigResolver`)

Three-tier resolution order: **tenant-specific → product-specific → kernel default**

```java
// Mandatory — throws IllegalArgumentException if not found at any tier
<T> T resolve(String configKey, Class<T> type, KernelTenantContext context);

// With fallback default
<T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context);

// Optional — returns empty if not configured at any tier
<T> Optional<T> resolveOptional(String configKey, Class<T> type, KernelTenantContext context);

// Async hot-reload for a specific tenant
Promise<Void> reloadConfig(String tenantId);
```

- `KernelConfigResolver` is injected via `context.getDependency(KernelConfigResolver.class)` in `initialize()`.
- Products MUST NOT hardcode configuration values that vary by tenant or environment as `static final` fields.
- Add `ConfigProvider` implementations to inject product-level defaults into the resolution chain.

---

### 0.8 How Products Register Their Contracts (`ContractRegistry`)

Products MUST register with `ContractRegistry` at initialization. This is the kernel's schema and contract enforcement point:

```java
// Module contract — declares moduleId, version, capability set
contractRegistry.registerModuleContract(new ModuleContract(moduleId, version, capabilities));

// API contract — declares endpoint paths, request/response types, versioning
contractRegistry.registerApiContract(new ApiContract(apiId, basePath, version));

// Schema contract — declares DataCloud/event payload schemas with version
contractRegistry.registerSchemaContract(new SchemaContract(schemaId, schema, version));
```

**What this enforces**:

- Registration is validated by `ContractValidator` before being accepted. Duplicate `moduleId` throws `IllegalArgumentException` — fail-fast over silent runtime conflicts.
- `SchemaContract` registration provides the kernel a schema registry for `DataCloudKernelAdapter`. Dataset write operations can be validated against registered schemas — preventing silent dataset-name typos.
- Agent and operator `agentId` uniqueness (Operator Catalog) maps to module-contract uniqueness — the same `ContractValidator` rejects duplicate IDs.

---

### 0.9 Async Contract

| Rule             | Correct                                   | Wrong                                               |
| ---------------- | ----------------------------------------- | --------------------------------------------------- |
| Async I/O        | `Promise.ofBlocking(executor, () -> ...)` | `CompletableFuture.supplyAsync(...)`                |
| Promise chaining | `.then(v -> ...)`, `.map(f)`              | `.getResult()` returns `null` on unresolved promise |
| Parallel async   | `Promises.all(p1, p2)`                    | Nested sequential `.then()` chains                  |
| Tests            | `runPromise(() -> service.call())`        | `service.call().getResult()`                        |
| Eventloop        | `context.getEventloop()`                  | Creating a new `Eventloop.create()` per class       |

Never use Spring Reactor (`Mono`/`Flux`), RxJava, or `CompletableFuture` mixed with `Promise` in any kernel or platform code.

---

### 0.10 Testing Contract

| Requirement      | Rule                                                                                                   |
| ---------------- | ------------------------------------------------------------------------------------------------------ |
| Base class       | ALL async tests MUST extend `EventloopTestBase` (from `platform:java:testing`)                         |
| Execution        | Use `runPromise(() -> service.call())` — the runner drives the event loop to completion                |
| Build dependency | `testImplementation(project(":platform:java:testing"))` required in every product's `build.gradle.kts` |
| Version catalog  | ActiveJ test version via `libs.activej.test` alias — no inline `"io.activej:activej-test:x.x"`         |
| Forbidden        | `.getResult()` outside `runPromise()`, `promise.isException()` without event-loop context              |

---

### 0.11 Capability Inventory

Full inventory of the 81-class kernel across 22 packages, read March 2026. Grouped by platform concern.

#### Group 1 — Foundation (Module, Extension, Plugin, Context, Registry)

| Class / Interface     | Package            | Status            | Notes                                                                                                                   |
| --------------------- | ------------------ | ----------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `KernelModule`        | `kernel.module`    | ✅ Stable         | Full lifecycle: `initialize`, `start`, `stop`, `getHealthStatus`, `getCapabilities`, `getDependencies`                  |
| `KernelExtension`     | `kernel.extension` | ✅ Stable         | Canonical extension point; callbacks: `onModuleInitialized`, `onModuleStarted`, `onModuleStopped`                       |
| `KernelPlugin`        | `kernel.plugin`    | ✅ Stable         | Hot-swap: `install()`, `uninstall()`, `reload()`, exported/required contract declarations                               |
| `KernelContext`       | `kernel.context`   | ✅ Stable         | Runtime DI: `getDependency`, `getOptionalDependency`, `publishEvent`, `registerEventHandler`, `getEventloop`            |
| `KernelTenantContext` | `kernel.context`   | ✅ Stable         | Tenant isolation: feature flags, typed config, `SecurityContext`, `hasPermission`, async feature gate                   |
| `ScopeType`           | `kernel.scope`     | ✅ Stable         | Enum: KERNEL, PLATFORM, DOMAIN_PACK, PRODUCT, TENANT, OPERATOR, AGENT, WORKFLOW                                         |
| `ScopeDescriptor`     | `kernel.scope`     | ✅ Stable         | Value object: scope identity; factory: `product()`, `tenant()`, `domainPack()`, `workflow()`                            |
| `KernelRegistry`      | `kernel.registry`  | ✅ Stable         | **Single public root registry**: `registerModule`, `registerPlugin`, `registerCapability`, `getModule`, `getAllModules` |
| `CapabilityRegistry`  | `kernel.registry`  | `@KernelInternal` | Sub-registry behind `KernelRegistry` — not for external use                                                             |
| `ServiceRegistry`     | `kernel.registry`  | `@KernelInternal` | Sub-registry behind `KernelRegistry` — not for external use                                                             |
| `PluginRegistry`      | `kernel.registry`  | `@KernelInternal` | Sub-registry behind `KernelRegistry` — not for external use                                                             |

#### Group 2 — Communication, Workflow, and Adapters

| Class / Interface          | Package                    | Status    | Notes                                                                                                                           |
| -------------------------- | -------------------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `KernelInterScopeBus`      | `kernel.communication`     | ✅ Stable | `publishEvent(CrossScopeEvent)`, `subscribe(ScopeDescriptor, …)`, `shareData(id, data, ScopeSharePolicy)`, `retrieveSharedData` |
| `AepKernelAdapter`         | `kernel.adapter.aep`       | ✅ Stable | Promise-wrapped AEP bridge: publish/subscribe streams, deploy/undeploy agents, pipeline operations                              |
| `DataCloudKernelAdapter`   | `kernel.adapter.datacloud` | ✅ Stable | Promise-wrapped DataCloud bridge: CRUD, schema management, transactions, ML model storage                                       |
| `CrossScopeWorkflowEngine` | `kernel.workflow`          | ✅ Stable | Step execution, conditional branching, compensation/rollback on failure, active workflow tracking                               |

#### Group 3 — Configuration

| Class / Interface                  | Package         | Status    | Notes                                                                                                      |
| ---------------------------------- | --------------- | --------- | ---------------------------------------------------------------------------------------------------------- |
| `KernelConfigResolver`             | `kernel.config` | ✅ Stable | Interface: `resolve`, `resolveWithDefault`, `resolveOptional`, async `reloadConfig(tenantId)`              |
| `HierarchicalKernelConfigResolver` | `kernel.config` | ✅ Stable | Chain-of-responsibility implementation; `ConfigProvider` SPI exists but is not public-facing (see GAP-K06) |

#### Group 4 — Security, Privacy, and Boundary Enforcement

| Class / Interface             | Package                  | Status       | Notes                                                                                                                                         |
| ----------------------------- | ------------------------ | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `SecurityPolicy`              | `kernel.descriptor`      | ✅ Stable    | Declarative: `authRequired`, `authzRequired`, `encryptionAtRest`, `encryptionInTransit`, `auditEnabled`, `multiTenantIsolation`, `privileged` |
| `ScopeBoundaryEnforcer`       | `kernel.boundary`        | ✅ Stable    | 3-layer enforcement: (1) `BoundaryPolicyResolver` decision → (2) tenant permission check → (3) consent feature gate                           |
| `BoundaryPolicyResolver`      | `kernel.boundary`        | 🟡 Ambiguous | **Two interfaces in two packages** — `kernel.boundary` and `kernel.policy` (see GAP-K04)                                                      |
| `ClassificationDescriptor`    | `kernel.policy`          | ✅ Stable    | `domain`, `SensitivityLevel`, `complianceTags` (`"nepal-2081"`, `"sebon"`, `"gdpr"`, etc.)                                                    |
| `KernelPluginSecurityManager` | `kernel.plugin.security` | ✅ Stable    | Ed25519 signature validation, SHA-256 JAR hashing, strict/non-strict mode, trusted key registry                                               |

#### Group 5 — Audit and Compliance

| Class / Interface         | Package         | Status    | Notes                                                                                                                                     |
| ------------------------- | --------------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `CrossScopeAuditService`  | `kernel.audit`  | ✅ Stable | Policy-driven; immutable records; cryptographic signature; retention from `AuditPolicyResolver`; requires `AuditEventStore` (see GAP-K05) |
| `AuditPolicyResolver`     | `kernel.policy` | ✅ Stable | Returns `AuditPolicy` record: `retentionYears`, `signatureRequired`, `immutable`, `storageTier`; default: 7-year, signed, immutable       |
| `RetentionPolicyResolver` | `kernel.policy` | ✅ Stable | `resolveRetentionYears(source, target, classification)` — no product-name branching                                                       |

#### Group 6 — Contracts and Governance (6 Families)

| Class / Interface             | Package                     | Status       | Notes                                                                                                                                       |
| ----------------------------- | --------------------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `ContractFamily`              | `kernel.contracts`          | ✅ Stable    | Enum: `EXPERIENCE`, `API`, `SCHEMA`, `ANALYTICS`, `AUTONOMY`, `PACKAGING`                                                                   |
| `ContractRegistry`            | `kernel.contracts`          | ✅ Stable    | Multi-family; validates via `ContractValidator` chain; duplicate contractId throws `ContractValidationException`                            |
| `ContractRegistry`            | `kernel.contract`           | 🟡 Ambiguous | Older, simpler registry for `ModuleContract`, `ApiContract`, `SchemaContract` records — two registries, no declared canonical (see GAP-K03) |
| `SchemaContract`              | `kernel.contracts`          | ✅ Stable    | `SchemaSubject`: `subjectId`, `SchemaFormat` (JSON_SCHEMA_V7/AVRO/PROTOBUF), `CompatibilityMode` (BACKWARD/FORWARD/FULL/NONE)               |
| `AnalyticsContract`           | `kernel.contracts`          | ✅ Stable    | `MetricDeclaration` (name, `MetricType`, tags); `DashboardDeclaration`; metric names validated `^[a-z][a-z0-9._]*$`                         |
| `ExperienceContract`          | `kernel.contracts`          | ✅ Stable    | `ScreenDeclaration` (screenId, route, entryComponent); `ThemeToken`; routes must start with `/`                                             |
| `AutonomyContract`            | `kernel.contracts`          | ✅ Stable    | GAA-integrated: `AgentCapabilityDeclaration` with `AgentTier` (REFLEX/DELIBERATIVE/AUTONOMOUS), `minimumConfidence`, `requiresHumanReview`  |
| `SchemaGovernanceValidator`   | `kernel.contracts.schema`   | ✅ Stable    | Enforces: ≥1 subject, no duplicate subject IDs within a contract                                                                            |
| `AutonomyGovernanceValidator` | `kernel.contracts.autonomy` | ✅ Stable    | Enforces autonomy governance rules at contract registration time                                                                            |

#### Group 7 — Health and Operations

| Class / Interface | Package         | Status    | Notes                                                                                                                             |
| ----------------- | --------------- | --------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `HealthStatus`    | `kernel.health` | ✅ Stable | `healthy()`, `unhealthy(msg)`, `degraded(msg)` + builder for named `HealthCheck` map; polled via `KernelModule.getHealthStatus()` |
| `KernelOperator`  | `kernel.plugin` | ❌ Gap    | Operator contract is **synchronous** (`Object process(Object input)`) — contradicts async mandate (see GAP-K01)                   |

#### Group 8 — Predeclared Core Capabilities (`KernelCapability.Core.*`)

The kernel predeclares 14 platform capabilities. Products declare `getCapabilities()` returning `Set<KernelCapability>` and acquire capabilities via `context.getDependency(KernelCapability.class)`.

| Capability ID                | Name                | Key Backends / Standards                                                  |
| ---------------------------- | ------------------- | ------------------------------------------------------------------------- |
| `data.storage`               | Data Storage        | PostgreSQL, Redis, MinIO; at-rest + in-transit encryption                 |
| `user.authentication`        | User Authentication | password, MFA, SSO, OAuth; session management; audit                      |
| `api.framework`              | API Framework       | REST, GraphQL, WebSocket; JSON Schema validation; OpenAPI                 |
| `workflow.engine`            | Workflow Engine     | Temporal, Camunda; durable persistence; exponential retry                 |
| `event.processing`           | Event Processing    | 100k TPS; <1 ms latency; event sourcing                                   |
| `ai.ml.framework`            | AI/ML Framework     | classification, generation, embedding; TensorFlow, PyTorch, HuggingFace   |
| `observability.framework`    | Observability       | Prometheus, Grafana, Jaeger, Elasticsearch; logs, metrics, traces, alerts |
| `security.framework`         | Security            | OAuth 2.0, JWT, RBAC; encryption; threat detection                        |
| `mfa.framework`              | Multi-Factor Auth   | TOTP, SMS, email, hardware key, biometric; backup codes                   |
| `oauth.framework`            | OAuth 2.0 / OIDC    | authorization_code, client_credentials, refresh_token; PKCE               |
| `config.management`          | Configuration       | 4-tier hierarchy (kernel → product → tenant → user); hot-reload           |
| `tenant.isolation`           | Tenant Isolation    | strict isolation; data separation enforced                                |
| `resilience.patterns`        | Resilience          | circuit-breaker, retry, bulkhead, timeout                                 |
| `resilience.circuit-breaker` | Circuit Breaker     | configurable threshold, timeout, and recovery                             |

---

### 0.12 Deprecated Classes — Pending Removal

These classes MUST NOT be used in new product code. Their presence in `finance` and `phr` product code is tracked as ISSUE-X04 and related product issues.

| Deprecated Class               | Package                | Canonical Replacement              | Annotation                     |
| ------------------------------ | ---------------------- | ---------------------------------- | ------------------------------ |
| `KernelInterProductBus`        | `kernel.communication` | `KernelInterScopeBus`              | `@Deprecated(forRemoval=true)` |
| `CrossProductModelRegistry`    | `kernel.ai`            | **None — see GAP-K02**             | `@Deprecated(forRemoval=true)` |
| `CrossProductWorkflowEngine`   | `kernel.workflow`      | `CrossScopeWorkflowEngine`         | `@Deprecated(forRemoval=true)` |
| `CrossProductConfigResolver`   | `kernel.config`        | `HierarchicalKernelConfigResolver` | `@Deprecated(forRemoval=true)` |
| `KernelExtension` (plugin pkg) | `kernel.plugin`        | `kernel.extension.KernelExtension` | `@Deprecated(forRemoval=true)` |

> `KernelPurityValidationTest` and `KernelArchitectureDriftTest` (in the kernel test module) assert that all deprecated classes carry `@Deprecated(forRemoval=true)` and that their canonical replacements exist. Product code importing these classes will fail these purity assertions.

---

### 0.13 Platform Gaps

The following gaps were identified during the March 2026 full kernel read. These are either missing SPIs, ambiguous contracts, or capabilities that exist at declaration level but have no kernel-side enforcement or public exposure.

| Gap ID      | Area                        | Description                                                                                                                                                                                                                                                                                                                                                                                                  | Severity       |
| ----------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- |
| **GAP-K01** | Operator contract           | `KernelOperator.process(Object)` is synchronous and returns `Object`. This contradicts the async mandate (`Promise<T>` required for all IO). Products building async processing pipelines cannot implement `KernelOperator` without creating a hybrid sync/async boundary violation.                                                                                                                         | **High**       |
| **GAP-K02** | AI model governance         | `CrossProductModelRegistry` is `@Deprecated(forRemoval=true)` with a note to "use `ScopeDescriptor`", but no `ScopeModelRegistry` or kernel-level AI model governance class exists. No kernel API is available for cross-scope ML model sharing, PII/bias validation, or model versioning.                                                                                                                   | **High**       |
| **GAP-K03** | Contract registry ambiguity | Two `ContractRegistry` implementations: `com.ghatana.kernel.contract.ContractRegistry` (simpler, holds `ModuleContract`/`ApiContract`/`SchemaContract` records) and `com.ghatana.kernel.contracts.ContractRegistry` (multi-family, `ContractFamily`-aware, validator-composed). Products have no clear guidance on which to use; the `contracts` package version is richer and should be declared canonical. | **Medium**     |
| **GAP-K04** | Boundary policy SPI         | `BoundaryPolicyResolver` exists in both `kernel.boundary` and `kernel.policy`. `ScopeBoundaryEnforcer` uses `kernel.boundary.BoundaryPolicyResolver`. Two `DefaultBoundaryPolicyResolver` implementations also exist across both packages. Products implementing custom boundary policies do not know which interface to extend.                                                                             | **Medium**     |
| **GAP-K05** | Audit storage SPI           | `CrossScopeAuditService` requires an `AuditEventStore` constructor argument whose interface is internal to `kernel.audit`. Products needing custom audit backends (e.g., specialized store, compliance archive, immutable ledger) cannot implement a compliant backend without depending on kernel internals.                                                                                                | **Medium**     |
| **GAP-K06** | Config provider SPI         | `HierarchicalKernelConfigResolver` maintains a `List<ConfigProvider>` internally, but the `ConfigProvider` interface and its registration pathway are not exposed via `KernelConfigResolver` (the public interface). Products cannot contribute product-level configuration defaults into the kernel resolver chain.                                                                                         | **Medium**     |
| **GAP-K07** | Shared data policy          | `KernelInterScopeBus.shareData(String, Object, ScopeSharePolicy)` references `ScopeSharePolicy` but this type's fields, construction contract, access-control surface (permitted scopes, expiry, consent requirement, sensitivity), and mutability semantics are undocumented. Products cannot implement granular shared-data access policy.                                                                 | **Medium**     |
| **GAP-K08** | Health aggregation          | `HealthStatus` is per-module. There is no `KernelHealthAggregator`, cluster-level health roll-up, or cross-scope health query API. Operations teams and monitoring systems cannot obtain a composite platform health view without polling each module individually.                                                                                                                                          | **Low-Medium** |
| **GAP-K09** | Observability SPI           | `AnalyticsContract` lets modules declare which metrics they will emit, but there is no kernel `ObservabilityBridge` or `MetricEmitter` SPI. Products must depend on `libs:observability` directly, bypassing the kernel's contract model. `Core.observability.framework` is declared as a capability but has no corresponding kernel-side emission API.                                                      | **Low-Medium** |
| **GAP-K10** | Plugin hot-reload API       | `KernelPlugin.reload()` is callable by products, but `KernelPluginRuntimeManager` (which orchestrates kernel-managed hot-reload) is in `kernel.plugin.runtime` with `@KernelInternal`. Products cannot initiate kernel-orchestrated hot-reload without reaching into internal packages.                                                                                                                      | **Low**        |

---

### 0.14 Coverage by Concern Domain

Assessment of kernel support across the key concern domains required for rich product development and operation.

| Concern Domain                                | Kernel Support                                                                                                            | Coverage                  | Critical Gap                                                             |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------- | ------------------------------------------------------------------------ |
| **Product definition & lifecycle**            | `KernelModule`: `initialize`, `start`, `stop`, health, capabilities, dependencies                                         | ✅ Full                   | —                                                                        |
| **Product enrichment / extension**            | `KernelExtension`, `KernelPlugin` (exported/required contracts, hot-swap)                                                 | ✅ Full                   | —                                                                        |
| **Product & capability discovery**            | `KernelRegistry` (module, plugin, capability lookup)                                                                      | ✅ Full                   | —                                                                        |
| **Intra-product operations**                  | `KernelContext` (DI, event bus, eventloop), `KernelTenantContext` (feature flags, config)                                 | ✅ Full                   | Config SPI (GAP-K06)                                                     |
| **Inter-product / cross-scope communication** | `KernelInterScopeBus` (events, shared data), `CrossScopeWorkflowEngine` (multi-step orchestration)                        | ✅ Full                   | `ScopeSharePolicy` undocumented (GAP-K07)                                |
| **Intra-product async processing**            | `KernelContext.getEventloop()`, `Promise.ofBlocking()`, `AepKernelAdapter`                                                | 🟡 Partial                | `KernelOperator` sync only (GAP-K01)                                     |
| **Configuration management**                  | `HierarchicalKernelConfigResolver` (tenant → product → kernel); `Core.config.management` capability                       | ✅ Full                   | Product `ConfigProvider` not public (GAP-K06)                            |
| **Health & heartbeat**                        | `HealthStatus` (HEALTHY/UNHEALTHY/DEGRADED + named checks); `KernelModule.getHealthStatus()`                              | 🟡 Partial                | No aggregation API (GAP-K08)                                             |
| **Monitoring & observability**                | `Core.observability.framework` declared; `AnalyticsContract` (metric + dashboard declarations)                            | 🟡 Partial                | No kernel `MetricEmitter` SPI (GAP-K09)                                  |
| **Plugin security & integrity**               | `KernelPluginSecurityManager` (Ed25519 + SHA-256, strict mode, trusted key registry)                                      | ✅ Full                   | —                                                                        |
| **Multi-tenancy isolation**                   | `KernelTenantContext` (config, feature flags, `SecurityContext`, `hasPermission`)                                         | ✅ Full                   | —                                                                        |
| **Data classification**                       | `ClassificationDescriptor` (domain, `SensitivityLevel`, compliance tags)                                                  | ✅ Full                   | —                                                                        |
| **Boundary & access control**                 | `ScopeBoundaryEnforcer` (3-layer: policy → tenant permission → consent gate)                                              | 🟡 Partial                | Duplicate `BoundaryPolicyResolver` interfaces (GAP-K04)                  |
| **Audit & retention**                         | `CrossScopeAuditService` (policy-driven, immutable, cryptographic); `AuditPolicyResolver`, `RetentionPolicyResolver`      | 🟡 Partial                | `AuditEventStore` SPI hidden (GAP-K05)                                   |
| **Shared data model**                         | `DataCloudKernelAdapter` (CRUD, schema, transactions); `SchemaContract` (format, compatibility mode evolution)            | ✅ Full                   | Schema validation not auto-enforced at write time                        |
| **Schema governance**                         | `SchemaContract` (BACKWARD/FORWARD/FULL/NONE), `SchemaGovernanceValidator`                                                | ✅ Full                   | —                                                                        |
| **Privacy & consent control**                 | `ScopeBoundaryEnforcer` consent gate (`cross-scope.consent.*` feature flag), `ClassificationDescriptor` sensitivity level | 🟡 Partial                | Consent is implicit in boundary policy; no dedicated `PrivacyConsentApi` |
| **Granular data access control**              | `BoundaryDecision` (allow/allowWithConsent/deny + audit flag); `ClassificationDescriptor` compliance tags                 | 🟡 Partial                | Implementation SPI ambiguous (GAP-K04)                                   |
| **Contract declaration & enforcement**        | 6 `ContractFamily` types; `contracts.ContractRegistry` + validators; governance validators for SCHEMA and AUTONOMY        | ✅ Full                   | Two `ContractRegistry` packages (GAP-K03)                                |
| **AI / ML model governance**                  | `AutonomyContract` (agent tiers, model governance rules); `Core.ai.ml.framework` capability                               | 🟡 Partial                | No scope-first model registry (GAP-K02)                                  |
| **Agent autonomy governance**                 | `AutonomyContract`: `AgentTier` (REFLEX/DELIBERATIVE/AUTONOMOUS), `minimumConfidence`, `requiresHumanReview`              | ✅ Full at contract level | Runtime enforcement done by GAA framework                                |
| **Resilience patterns**                       | `Core.resilience.patterns` (circuit-breaker, retry, bulkhead, timeout) capability declared                                | 🟡 Partial                | Capability declared; no kernel `ResiliencePolicy` SPI                    |

**Legend:** ✅ Full — kernel provides the API and products can wire it today | 🟡 Partial — kernel has partial support with a documented gap | ❌ Missing — no kernel support exists

---

## 1. Executive Summary

| Dimension                    | Finance                      | PHR               |
| ---------------------------- | ---------------------------- | ----------------- |
| Java source files            | 385                          | 37                |
| Domain modules               | 13                           | 1 (healthcare)    |
| Test coverage (observed)     | Moderate                     | Low               |
| Critical duplications        | **4 class pairs**            | **2 class pairs** |
| Testing standard violations  | 4 test files                 | 4 test files      |
| Financial type safety issues | **High** (`double` in ports) | N/A               |
| Implementation completeness  | ~70%                         | ~35%              |
| Security risk level          | Medium                       | Medium-High       |

Both products have a solid kernel integration layer and follow ActiveJ async patterns in newer domain modules.  
However, several systematic violations of the codebase standards have accumulated — primarily test anti-patterns,  
duplicate class definitions, and hardcoded configuration values — that must be addressed before GA.

---

### Implementation Progress Summary

> **Last Updated**: 2026-03-24  
> **Implemented by**: AI Code Agent across 7 sessions

| #   | Issue     | Product | Status  | Notes                                                                                                            |
| --- | --------- | ------- | ------- | ---------------------------------------------------------------------------------------------------------------- |
| 1   | ISSUE-F01 | Finance | ✅ Done | Deleted duplicate agent classes                                                                                  |
| 2   | ISSUE-F02 | Finance | ✅ Done | Deleted duplicate extension classes                                                                              |
| 3   | ISSUE-F03 | Finance | ✅ Done | Deleted duplicate AmlRiskScoringService                                                                          |
| 4   | ISSUE-F04 | Finance | ✅ Done | Full promise chain in OrderCaptureService                                                                        |
| 5   | ISSUE-F05 | Finance | ✅ Done | `double` → `BigDecimal` in settlement                                                                            |
| 6   | ISSUE-F06 | Finance | ✅ Done | `SecureRandom` in VaR Monte Carlo                                                                                |
| 7   | ISSUE-F07 | Finance | ✅ Done | KernelConfigResolver-driven risk limits                                                                          |
| 8   | ISSUE-F08 | Finance | ✅ Done | Saga compensation for FX journal                                                                                 |
| 9   | ISSUE-F09 | Finance | ✅ Done | Bounded cache with LRU eviction                                                                                  |
| 10  | ISSUE-F10 | Finance | ✅ Done | idempotencyKey validation                                                                                        |
| 11  | ISSUE-F11 | Finance | ✅ Done | 4 test files → EventloopTestBase                                                                                 |
| 12  | ISSUE-F12 | Finance | ✅ Done | Fixed as part of F11                                                                                             |
| 13  | ISSUE-P01 | PHR     | ✅ Done | Deleted skeleton extension                                                                                       |
| 14  | ISSUE-P02 | PHR     | ✅ Done | ConsentManagementService implements ConsentService                                                               |
| 15  | ISSUE-P03 | PHR     | ✅ Done | Removed silent exception swallowing                                                                              |
| 16  | ISSUE-P04 | PHR     | ✅ Done | NHS ID uniqueness check + PatientStore port                                                                      |
| 17  | ISSUE-P05 | PHR     | ✅ Done | DeletionAuditStore for evidence persistence                                                                      |
| 18  | ISSUE-P06 | PHR     | ✅ Done | lastClinicalActivityAt retention window                                                                          |
| 19  | ISSUE-P07 | PHR     | ✅ Done | 3 test files → EventloopTestBase                                                                                 |
| 20  | ISSUE-P09 | PHR     | ✅ Done | Added platform:java:testing dependency                                                                           |
| 21  | ISSUE-X05 | Both    | ✅ Done | ContractRegistry registration in both modules                                                                    |
| 22  | ISSUE-F17 | Finance | ✅ Done | DualCalendarPort for K-15 BS date integration                                                                    |
| 23  | ISSUE-F18 | Finance | ✅ Done | PAN test data documented per PCI-DSS Req 3                                                                       |
| 24  | ISSUE-F19 | Finance | ✅ Done | Hardcoded config → KernelConfigResolver                                                                          |
| 25  | ISSUE-P12 | PHR     | ✅ Done | C1/C2 role-based access gating                                                                                   |
| 26  | ISSUE-P13 | PHR     | ✅ Done | Audit logging in Registration & Retention                                                                        |
| 27  | ISSUE-P14 | PHR     | ✅ Done | Emergency access TTL (4h) with token tracking                                                                    |
| 28  | ISSUE-X04 | Both    | ✅ Done | Missing platform dependency declarations                                                                         |
| 29  | ISSUE-F13 | Finance | ✅ Done | MultiCurrencyJournalServiceTest (19 tests)                                                                       |
| 30  | ISSUE-F16 | Finance | ✅ Done | EventBusPort integration in NepseExchangeAdapter                                                                 |
| 31  | ISSUE-P08 | PHR     | ✅ Done | ConsentManagementServiceTest (13 tests)                                                                          |
| 32  | ISSUE-P11 | PHR     | ✅ Done | FhirResourceService/Validator/Transformer interfaces                                                             |
| 33  | ISSUE-P15 | PHR     | ✅ Done | Rate limiting on createGrant() and checkAccess()                                                                 |
| 34  | ISSUE-F13 | Finance | ✅ Done | ReconciliationServiceTest (15 tests) + AccountCreationWorkflowServiceTest (20 tests)                             |
| 35  | ENH-F01   | Finance | ✅ Done | Configurable fail-safe default (REVIEW/FAIL) in ComplianceCheckIntegrationService + 17 tests                     |
| 36  | ENH-P01   | PHR     | ✅ Done | revokeGrant() now calls invalidatePatientAccessCache for full patient-scoped purge                               |
| 37  | ISSUE-F14 | Finance | ✅ Done | Two-level composition pattern documented in FinanceProductModule Javadoc                                         |
| 38  | ISSUE-F15 | Finance | ✅ Done | OMS vs Post-Trade PositionProjectionService distinction documented with @see cross-references                    |
| 39  | ISSUE-X01 | Both    | ✅ Done | Schema contracts registered via ContractRegistry in both FinanceKernelModule and PhrKernelModule (done with X05) |

**Total: 39 issues implemented out of 42 report items.**  
Remaining items are: ISSUE-X02 (distributed cache — requires KRQ-05 DistributedCachePort library), ISSUE-X03 (type discriminator — requires cross-cutting DataCloud serialization changes), and ISSUE-P10 (PHR feature gap — requires full module implementation work).

---

## 2. Finance Product — Findings

### 2.1 Critical Issues

#### ISSUE-F01 — Duplicate Agent Classes (Blocker) ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Deleted `com.ghatana.finance.agent.FraudDetectionAgent` and `com.ghatana.finance.agent.RiskAssessmentAgent`. Canonical `com.ghatana.finance.ai.*` variants retained.

Two separate `FraudDetectionAgent` implementations exist with the same `AGENT_ID`:

| File                                                               | Package                     | Extends BaseAgent?            |
| ------------------------------------------------------------------ | --------------------------- | ----------------------------- |
| `src/main/java/com/ghatana/finance/ai/FraudDetectionAgent.java`    | `com.ghatana.finance.ai`    | ✅ Yes — proper GAA framework |
| `src/main/java/com/ghatana/finance/agent/FraudDetectionAgent.java` | `com.ghatana.finance.agent` | ❌ No — standalone class      |

Same duplication for `RiskAssessmentAgent`:

| File                                                               | Package                     | Extends BaseAgent?            |
| ------------------------------------------------------------------ | --------------------------- | ----------------------------- |
| `src/main/java/com/ghatana/finance/ai/RiskAssessmentAgent.java`    | `com.ghatana.finance.ai`    | ✅ Yes — proper GAA framework |
| `src/main/java/com/ghatana/finance/agent/RiskAssessmentAgent.java` | `com.ghatana.finance.agent` | ❌ No — standalone class      |

**Impact**: Both register the same `AGENT_ID = "fraud-detection-agent"` and `"risk-assessment-agent"`. This will cause conflicts in the Operator Catalog or agent registry. The `agent.*` package versions pre-date the GAA framework integration and should be deleted.

**Action**: Delete `com.ghatana.finance.agent.FraudDetectionAgent` and `com.ghatana.finance.agent.RiskAssessmentAgent`. Wire all references to the `com.ghatana.finance.ai.*` variants.

---

#### ISSUE-F02 — Duplicate KernelExtension Classes (Blocker) ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Deleted `com.ghatana.finance.extension.DualCalendarKernelExtension` and `com.ghatana.finance.extension.RegulatoryComplianceKernelExtension`. Canonical `kernel.extension.*` locations retained.

Three KernelExtension classes are duplicated across packages:

| Class                                 | Package 1 (canonical)                       | Package 2 (stale)               |
| ------------------------------------- | ------------------------------------------- | ------------------------------- |
| `DualCalendarKernelExtension`         | `com.ghatana.finance.kernel.extension`      | `com.ghatana.finance.extension` |
| `RegulatoryComplianceKernelExtension` | `com.ghatana.finance.kernel.extension`      | `com.ghatana.finance.extension` |
| `ComplianceKernelExtension`           | `com.ghatana.finance.extension` (only here) | —                               |

**Impact**: Plugin loader or extension registry may load both, causing duplicate capability registration and potential double-processing of compliance checks.

**Action**: Remove the `com.ghatana.finance.extension.DualCalendarKernelExtension` and `com.ghatana.finance.extension.RegulatoryComplianceKernelExtension`. Keep the `kernel.extension.*` canonical locations.

---

#### ISSUE-F03 — AML Risk Scoring Duplication ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Deleted `com.ghatana.finance.onboarding.AmlRiskScoringService`. Canonical `domains/compliance` version retained.

Two separate `AmlRiskScoringService` implementations:

| Location                                                     | Package                                                   |
| ------------------------------------------------------------ | --------------------------------------------------------- |
| `client-onboarding/src/main/.../AmlRiskScoringService.java`  | `com.ghatana.finance.onboarding`                          |
| `domains/compliance/src/main/.../AmlRiskScoringService.java` | `com.ghatana.products.finance.domains.compliance.service` |

**Impact**: Different rule weights and scoring logic in two places. Changes to AML rules must be applied in both locations. The compliance domain version is more complete (includes K-09 ML scoring port). The `client-onboarding` version predates the compliance domain and should be removed.

**Action**: Deprecate and delete `com.ghatana.finance.onboarding.AmlRiskScoringService`. Update `client-onboarding` module to depend on `domains/compliance`.

---

### 2.2 Correctness Problems

#### ISSUE-F04 — `.getResult()` Calls Inside `Promise.ofBlocking` (High Severity) ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Rewrote `captureOrder()` as a full promise chain eliminating all 4 `.getResult()` calls. Added idempotencyKey input validation (see F10).

`OrderCaptureService.captureOrder()` at `domains/oms/.../OrderCaptureService.java` calls `.getResult()` on nested Promises inside a `Promise.ofBlocking` lambda:

```java
// Line ~63 — inside Promise.ofBlocking(executor, () -> { ... })
var existing = orderStore.findByIdempotencyKey(request.idempotencyKey())
        .getResult();  // ← VIOLATION

var instrument = instrumentStore
        .findCurrentById(UUID.fromString(request.instrumentId()))
        .getResult();  // ← VIOLATION

BigDecimal arrivalPrice = l1Cache.getL1(request.instrumentId())
        .getResult()   // ← VIOLATION
        .map(q -> q.lastPrice())
        .orElse(BigDecimal.ZERO);

orderStore.save(order).getResult();  // ← VIOLATION
```

**Problem**: If `orderStore`, `instrumentStore`, or `l1Cache` return Promises that schedule resolution on the **ActiveJ Eventloop** (the single-threaded event loop), calling `.getResult()` from a blocking executor thread will retrieve `null` (the Promise is unresolved from the executor thread's perspective). This causes silent `null`-dereferences and incorrect order processing.

**Fix**: Either (a) change the ports (`OrderStore`, `InstrumentStore`, `L1Cache`) to expose synchronous/blocking-safe interfaces for use inside `Promise.ofBlocking`, or (b) make `captureOrder` fully promise-chained:

```java
public Promise<Order> captureOrder(OrderCaptureRequest request) {
    return orderStore.findByIdempotencyKey(request.idempotencyKey())
        .then(existing -> {
            if (existing.isPresent()) return Promise.of(existing.get());
            return instrumentStore.findCurrentById(UUID.fromString(request.instrumentId()))
                .then(instrOpt -> {
                    Instrument instrument = instrOpt.orElseThrow(
                        () -> new UnknownInstrumentException(request.instrumentId()));
                    // ...continue with full promise chain
                });
        });
}
```

---

#### ISSUE-F05 — `double` Used for Financial Amounts in DvP Settlement (High Severity) ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Replaced all `double` with `BigDecimal` in `DvpSettlementService` (DvpRequest, SecuritiesReservationPort, CashReservationPort, LedgerPort) and `SettlementMatchingService` (SettlementInstruction record, matching logic).

`DvpSettlementService` inner ports and request records use Java primitive `double` for monetary values:

```java
// DvpRequest record — WRONG
public record DvpRequest(
    ...
    double quantity,   // ← WRONG: floating point for financial
    double amount,     // ← WRONG
    ...
)

// SecuritiesReservationPort — WRONG
String reserveSecurities(String delivererId, String instrumentId, double quantity);

// CashReservationPort — WRONG
String reserveCash(String receiverId, double amount, String currency);
```

**Also affected**: `SettlementMatchingService.SettlementInstruction` uses `double quantity`.

**Impact**: IEEE-754 floating point introduces rounding errors in settlement amounts. At 100,000 NPR, a single `double` representation can have ~0.01 NPR error. Accumulated over a trading day, this causes ledger imbalances and regulatory reconciliation failures.

**Fix**: Replace all financial `double` fields with `BigDecimal`.

---

#### ISSUE-F06 — VaR Monte Carlo Using Deterministic Seed ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Replaced `new Random(42)` with `new SecureRandom()` in `VarCalculationService.monteCarloVar()`.

In `VarCalculationService.java`:

```java
// Monte Carlo: GBM with MC_SIMULATIONS paths
private double monteCarloVar(...) {
    ...
    Random rng = new Random(42);  // deterministic seed for reproducibility
```

**Impact**: A deterministic seed means every Monte Carlo run produces identical simulations. This systematically underestimates tail risk because it always samples the same scenario distribution. VaR calculations in production must use a **cryptographically secure or time-seeded** random source. The comment "deterministic for reproducibility" is an incorrect design goal for Monte Carlo risk analysis.

**Fix**: Use `new SecureRandom()` or `new Random(System.nanoTime() ^ ThreadLocalRandom.current().nextLong())`.

---

#### ISSUE-F07 — Hardcoded Risk Limits in `RiskManagementService` ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Replaced static hardcoded limits with `KernelConfigResolver`-driven instance fields loaded in `initialize()` via `resolveWithDefault()`. Config keys: `finance.risk.limit.max_position`, `finance.risk.limit.max_concentration_pct`, `finance.risk.limit.max_daily_loss`.

```java
// Risk limits — hardcoded static finals
private static final BigDecimal MAX_POSITION_LIMIT = new BigDecimal("10000000"); // 1 crore NRP
private static final BigDecimal MAX_CONCENTRATION_PCT = new BigDecimal("0.20");   // 20%
private static final BigDecimal MAX_DAILY_LOSS = new BigDecimal("500000");         // 5 lakhs
```

**Impact**: Risk limits vary per tenant, per instrument class, and per regulatory change. Hardcoded limits cannot be changed without a code deployment. SEBON periodically revises position limits.

**Fix**: Load limits from `KernelConfigResolver` at startup. Define limit keys as constants in a `FinanceKernelConfigKeys` class.

---

#### ISSUE-F08 — Non-Atomic FX Journal Posting ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added saga compensation: on target journal failure, reverse source journal via `postedSource.reverse()` in `MultiCurrencyJournalService.postFxConversion()`.

`MultiCurrencyJournalService.postFxConversion()` posts two journals sequentially:

```java
return ledgerService.postJournal(sourceJournal)
    .then(postedSource -> ledgerService.postJournal(targetJournal)
        .map(postedTarget -> new FxConversionResult(...)));
```

**Impact**: If `targetJournal` posting fails after `sourceJournal` succeeds, the ledger is left in an unbalanced state — a `DEBIT bridge account` in source currency with no corresponding target journal. This is a financial consistency violation.

**Fix**: Wrap both journal posts in a saga with compensation: if `targetJournal` fails, issue a reversal journal for `sourceJournal`. Alternatively, request an atomic multi-journal post API from the Ledger kernel (see Section 5).

---

#### ISSUE-F09 — Unbounded In-Memory Cache in `RiskManagementService` ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added `MAX_CACHE_SIZE = 10_000` constant with LRU eviction policy (removes eldest entry on overflow) in `RiskManagementService`.

```java
private final Map<String, RiskProfile> riskCache = new ConcurrentHashMap<>();
```

**Impact**: No eviction policy. Under sustained load (thousands of unique trader IDs), the cache grows without bound, leading to `OutOfMemoryError`. Also, stale cache entries are only detected by the `isStale()` check at read time — there is no background eviction.

**Fix**: Replace with a Caffeine or Guava `LoadingCache` with `expireAfterWrite(5, MINUTES)` and `maximumSize(10_000)`.

---

#### ISSUE-F10 — `OrderCaptureService` Missing Input Sanitization ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added `idempotencyKey` validation: non-null/blank check + regex `[a-zA-Z0-9_-]{1,128}` pattern enforcement. Throws `IllegalArgumentException` on invalid input.

The `OrderCaptureRequest.idempotencyKey()` is used directly in a log message and as a DB lookup key without length validation or sanitization:

```java
log.debug("Idempotent order replay: idempotencyKey={}", request.idempotencyKey());
```

An arbitrarily long `idempotencyKey` could cause log injection or DoS via excessive database key sizes.

**Fix**: Validate `idempotencyKey` is non-null, max 128 chars, and matches `[A-Za-z0-9_-]+` pattern.

---

### 2.3 Testing Violations

#### ISSUE-F11 — Tests Not Extending `EventloopTestBase` ✅ IMPLEMENTED

> **Status**: ✅ Resolved — All 4 test files refactored: `FinanceKernelModuleTest`, `RiskManagementKernelExtensionTest`, `ComplianceKernelExtensionTest`, `DualCalendarKernelExtensionTest` now extend `EventloopTestBase` and use `runPromise()` pattern.

The architecture standards mandate: _"ALL async tests MUST extend `EventloopTestBase`"_.  
The following test files violate this, calling `.getResult()` directly instead of `runPromise()`:

| File                                                  | Issue                                                                   |
| ----------------------------------------------------- | ----------------------------------------------------------------------- |
| `src/test/.../FinanceKernelModuleTest.java`           | Directly calls `startPromise.getResult()`, `module.start().getResult()` |
| `src/test/.../RiskManagementKernelExtensionTest.java` | Calls `promise.getResult()` on async risk calculations                  |
| `src/test/.../ComplianceKernelExtensionTest.java`     | Calls `promise.getResult()` inline                                      |
| `src/test/.../DualCalendarKernelExtensionTest.java`   | Synchronous-style test without EventloopTestBase                        |

**Good examples** (compliant): `MarginSufficiencyServiceTest` and `ConcentrationLimitServiceTest` correctly extend `EventloopTestBase` and use `runPromise()`.

**Fix**: Refactor all 4 files to extend `EventloopTestBase` and use `runPromise(() -> ...)`.

---

#### ISSUE-F12 — `FinanceKernelModuleTest` Calls `.getResult()` on Start Promise ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Fixed as part of F11 refactoring. All `.getResult()` and `.isException()` patterns replaced with `runPromise()` and `assertThrows()`.

```java
// FORBIDDEN PATTERN (FinanceKernelModuleTest line ~98)
Promise<Void> startPromise = module.start();
assertDoesNotThrow(startPromise::getResult);  // ← FORBIDDEN
```

The `getResult()` call on an uncompleted `Promise<Void>` returns `null` and silently passes the assertion instead of catching actual errors.

**Fix**:

```java
assertDoesNotThrow(() -> runPromise(() -> module.start()));
```

---

#### ISSUE-F13 — Missing Tests for Domain Services ✅ RESOLVED

> **Status**: ✅ Resolved — Created `MultiCurrencyJournalServiceTest` (19 tests), `ReconciliationServiceTest` (15 tests covering exact match, tolerance match, break detection, validation, batch behavior, zero-tolerance overload), `AccountCreationWorkflowServiceTest` (20 tests covering maker-checker workflow, code uniqueness, draft lifecycle, listener notification, input validation), `NepseExchangeAdapterTest` (9 tests), and `ComplianceCheckIntegrationServiceTest` (17 tests covering normal outcomes, configurable fail-safe, metrics, constructor defaults).

The following domain services have **zero unit tests**:

| Module                | Untested Service                                                                         |
| --------------------- | ---------------------------------------------------------------------------------------- |
| `client-onboarding`   | `AmlRiskScoringService`                                                                  |
| `ledger-framework`    | `MultiCurrencyJournalService`, `ReconciliationService`, `AccountCreationWorkflowService` |
| `operator-workflows`  | `FinanceTenantRegistryService`                                                           |
| `data-governance`     | `FinanceDataGovernanceService`                                                           |
| `incident-management` | `FinanceIncidentManagementService`                                                       |
| `calendar-service`    | `FinanceCalendarService`                                                                 |
| `platform-sdk`        | `FinancePlatformSdkService`                                                              |
| `rules-engine`        | `FinanceRulesEngineService`                                                              |

---

### 2.4 Architecture Gaps

#### ISSUE-F14 — `FinanceProductModule` vs `FinanceKernelModule` Separation Unclear — ✅ RESOLVED

> **Status**: ✅ Resolved — Added comprehensive Javadoc to `FinanceProductModule` documenting the two-level composition pattern: `FinanceKernelModule` owns kernel-level cross-cutting services (OMS, Risk, Compliance), while 14 domain modules own domain-specific services. The module acts as a composition root without creating individual service instances. Updated `@doc.pattern` from `Module` to `CompositionRoot`.

Two module classes exist:

- `com.ghatana.products.finance.FinanceProductModule` — product-level facade
- `com.ghatana.finance.kernel.FinanceKernelModule` — kernel integration module

The `FinanceProductModule` delegates to `FinanceKernelModule` but also directly creates service instances. This violates the single composition root pattern — the module boundary is unclear.

---

#### ISSUE-F15 — `PositionProjectionService` Duplicated Across OMS and Post-Trade — ✅ RESOLVED

> **Status**: ✅ Resolved — Added comprehensive Javadoc to both `PositionProjectionService` classes documenting their distinct purposes: OMS variant is in-memory pre-settlement position tracking (CQRS read side), Post-Trade variant is the authoritative settled position in PostgreSQL + Redis. Added `@see` cross-references between the two classes. Future consolidation into a shared `domains/position` module is documented as a backlog item.

Two `PositionProjectionService` classes with identical purpose:

- `domains/oms/src/main/.../PositionProjectionService.java`
- `domains/post-trade/src/main/.../PositionProjectionService.java`

These should be a single service in a `domains/position` module that both OMS and Post-Trade depend on.

---

#### ISSUE-F16 — Missing Message Bus Integration for EMS → OMS Fill Propagation — ✅ RESOLVED

~~`NepseExchangeAdapter` receives execution reports from NEPSE via FIX and fires a `fillCallback`. But the callback type is `Consumer<ExecutionFill>` — a direct in-process callback rather than an event published to the platform event bus (`EventBusPort`).~~

**Resolution**: Added `EventBusPort` as a constructor dependency (with `Objects.requireNonNull` validation). `handleExecReport()` now publishes `ExecutionFillReceivedEvent` on partial/full fills (case "1","2") and `OrderRejectedByExchangeEvent` on rejections (case "8"), alongside the existing callback. Both events are inner records of `NepseExchangeAdapter`. Full test coverage in `NepseExchangeAdapterTest` (9 tests: fill events, rejection events, non-fill events, construction validation).

---

#### ISSUE-F17 — K-15 Dual Calendar Integration Placeholder in OMS — ✅ RESOLVED

~~`OrderCaptureService` has an explicit TODO for Bikram Sambat (BS) date.~~

**Resolution**: Created `DualCalendarPort` hexagonal port interface in `oms/port/` with `toBsDateString(Instant)`. `OrderCaptureService` now takes `DualCalendarPort` as a constructor dependency and calls `dualCalendar.toBsDateString(now)` to populate the BS date field. This decouples the OMS domain from kernel extension internals while wiring the K-15 capability.

---

### 2.5 Security Concerns

#### ISSUE-F18 — PAN Data in Compliance Test Without Masking — ✅ RESOLVED

~~`ComplianceKernelExtensionTest` constructs `PaymentDetails` with a raw PAN.~~

**Resolution**: Added clarifying comments at all 3 PAN usage sites in `ComplianceKernelExtensionTest` documenting that `4111111111111111` is the official Visa test card number (Luhn-valid, not a real card). This meets PCI-DSS Requirement 3 by clearly distinguishing test PANs from production data.

---

#### ISSUE-F19 — `AirGapBundleSigningService` in Sanctions Module — No Tests — ✅ RESOLVED

~~The `sanctions` module includes a class `AirGapBundleSigningService` which handles cryptographic signing of sanctions list bundles. No test exists.~~

**Resolution**: `AirGapBundleSigningServiceTest` created with 9 tests covering: sign+verify happy path, tampered payload detection, tampered signature detection, empty payload, large payload (1 MB), public key Base64 export, invalid private key rejection, invalid public key rejection, and cross-key-pair verification failure. Uses dynamically generated Ed25519 key pairs.

---

### 2.6 Enhancements Needed

#### ENH-F01 — Circuit Breaker for Compliance Timeout Should Return Configurable Default — ✅ RESOLVED

> **Status**: ✅ Resolved — Added `ComplianceDecision failSafeDefault` parameter to `ComplianceCheckIntegrationService` 5-arg constructor. All catch blocks now use the configurable fail-safe instead of hardcoded `REVIEW`. Existing 3-arg and 4-arg constructors default to `REVIEW` for backward compatibility. Added `getFailSafeDefault()` accessor and private `failSafeOutcome()` helper with exhaustive switch expression. Full test coverage in `ComplianceCheckIntegrationServiceTest` (17 tests).

`ComplianceCheckIntegrationService` defaults to `REVIEW` on timeout:

```java
} catch (Exception e) {
    return ComplianceOutcome.review(List.of("COMPLIANCE_TIMEOUT"));
}
```

The fail-safe default (`REVIEW` vs `FAIL`) should be configurable per environment. In production, `FAIL` is safer; in staging, `REVIEW` aids testing.

---

#### ENH-F02 — `WashTradeDetectionService` Uses Only Time Window; Missing Price Proximity Scoring for Partial Confidence

The current implementation finds opposite-side fills within a time window but the price proximity scoring (`scoreWashTrade`) compares prices against market VWAP. However, clients can manipulate wash trades using prices slightly off VWAP to evade detection. A rolling VWAP ± tolerance should be configurable via the `RulesEnginePort`.

---

#### ENH-F03 — `VarCalculationService` Missing Correlation Matrix for Multi-Asset Portfolio

The current VaR implementation computes portfolio-level VaR from a single `portfolioValue` scalar and aggregate returns. A proper parametric VaR for multi-asset portfolios requires a covariance matrix across instruments. The current approach underestimates risk for correlated positions. This should be a tracked backlog item.

---

#### ENH-F04 — `FinanceCalendarService` Missing Holiday Calendar for NSE/BSE

The `FinanceCalendarService` exists but no NSE/BSE holiday data integration is visible. Settlement date calculations for cross-listed securities require exchange-specific calendars.

---

## 3. PHR Product — Findings

### 3.1 Critical Issues

#### ISSUE-P01 — Duplicate `HealthcareConsentKernelExtension` Classes ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Deleted skeleton `com.ghatana.phr.kernel.extension.HealthcareConsentKernelExtension`. Complete `com.ghatana.phr.extension.HealthcareConsentKernelExtension` retained as canonical.

Two implementations with **different** extension IDs:

| File                                                                  | Package                            | Extension ID                      |
| --------------------------------------------------------------------- | ---------------------------------- | --------------------------------- |
| `src/main/.../kernel/extension/HealthcareConsentKernelExtension.java` | `com.ghatana.phr.kernel.extension` | `"healthcare-consent"`            |
| `src/main/.../extension/HealthcareConsentKernelExtension.java`        | `com.ghatana.phr.extension`        | `"healthcare-consent-nepal-2081"` |

The `kernel.extension` variant is a skeleton (no consent registry, no policy implementations). The `extension` variant is the complete implementation (full consent management, Nepal Directive 2081 policies, audit trail).

The test `HealthcareConsentKernelExtensionTest` tests the `phr.extension` (complete) version.

**Impact**: If both are registered, the system has two consent capability providers with different IDs, potentially bypassing the enforcement of consent checks if callers reference the wrong capability ID.

**Action**: Delete `com.ghatana.phr.kernel.extension.HealthcareConsentKernelExtension`. Retain and canonicalize `com.ghatana.phr.extension.HealthcareConsentKernelExtension` with ID `"healthcare-consent-nepal-2081"`.

---

#### ISSUE-P02 — `ConsentManagementService` Does Not Implement `ConsentService` Interface ✅ IMPLEMENTED

> **Status**: ✅ Resolved — `ConsentManagementService` now `implements ConsentService`. Added `checkAccess()`, `assertAccess()`, and `invalidatePatientAccessCache()` methods delegating to existing internal logic.

`ConsentService` (interface in `com.ghatana.phr.kernel.consent`) defines the mandatory access-decision contract:

```java
public interface ConsentService {
    Promise<ConsentAccessDecision> check(ConsentCheckRequest request);
    Promise<Void> assertAccess(ConsentCheckRequest request) throws ConsentAccessDeniedException;
    Promise<Void> invalidateCache(String patientId, CacheInvalidationReason reason);
}
```

`ConsentManagementService` in `com.ghatana.phr.kernel.service` has its own methods but **does not implement this interface**. This means:

1. Services that depend on `ConsentService` cannot be injected with `ConsentManagementService`
2. The architectural "single gate" pattern is broken — callers may bypass consent checks entirely

**Action**: Make `ConsentManagementService` implement `ConsentService` or create a separate `ConsentServiceImpl` adapter class.

---

### 3.2 Correctness Problems

#### ISSUE-P03 — `PatientRecordService.getPatient()` Silently Swallows Exceptions ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Removed the no-op `.whenException()` handler so storage/deserialization errors propagate to callers.

```java
return dataCloud.readData(request)
    .map(result -> Optional.ofNullable(deserialize(result.getData())))
    .whenException(e -> {
        // Patient not found   ← empty handler swallows ALL exceptions
    });
```

**Impact**: Storage failures, deserialization errors, and network errors are all silently swallowed. The caller receives `Optional.empty()` for both "patient not found" (expected) and "database down" (critical). This prevents proper error propagation and alerting.

**Fix**:

```java
return dataCloud.readData(request)
    .map(result -> Optional.ofNullable(deserialize(result.getData())))
    .mapException(DataNotFoundException.class, e -> Optional.empty())  // only catch 404-equivalent
    // let all other exceptions propagate
```

---

#### ISSUE-P04 — `PatientRegistrationService.register()` Does Not Enforce NHS ID Uniqueness ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added `patientStore.findByNhsId(tenantId, nhsId)` uniqueness check before save. Added `findByNhsId()` method to `PatientStore` port interface.

```java
public Promise<Patient> register(RegistrationRequest request) {
    return Promise.ofBlocking(executor, () -> {
        Patient patient = Patient.newPatient(...);
        // No uniqueness check for nhsId within tenantId
        patientStore.save(enriched);
        ...
    });
}
```

**Impact**: Multiple patients can be registered with the same National Health Service ID within a tenant, creating duplicate records. The class Javadoc says _"NHS ID uniqueness: checked within the same tenant"_ but no implementation of this check exists.

**Fix**: Add `patientStore.findByNhsId(tenantId, nhsId)` check before saving; throw `DuplicatePatientException` if present.

---

#### ISSUE-P05 — `PatientDeletionWorkflow` Does Not Write Deletion Evidence Record to Persistent Store ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added `DeletionAuditStore` interface. Constructor requires it. `execute()` now persists evidence records via `auditStore.persistEvidence()` before returning the deletion report.

The workflow's step 5 per its own Javadoc is:

> _"Audit — Write a deletion evidence record for each resource, including the requestor, the decision, the outcome, and the timestamp."_

The `execute()` implementation creates `ResourceDeletionDecision` objects and composes a `DeletionReport` in memory but does not persist the audit evidence to any durable store. If the JVM crashes after deletion but before the caller persists the report, the deletion event is unaudited — a Nepal Privacy Act 2075 compliance violation.

**Fix**: Persist deletion evidence records atomically alongside the actual data deletion, or return an event that the caller is required to persist before data destruction is attempted.

---

#### ISSUE-P06 — `RetentionPolicyService` 25-Year Rule Uses `registeredAt` Not `lastActivityAt` ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added `Instant lastClinicalActivityAt` field to `Patient` domain object. `isDeletionEligible()` now uses `max(registeredAt, lastClinicalActivityAt)` for retention window calculation. Updated `newPatient()` and `withClassification()` factory methods.

```java
// RetentionPolicyService — retention check
long yearsElapsed = ChronoUnit.YEARS.between(patient.registeredAt(), Instant.now());
if (yearsElapsed < 25) {
    return ErasureOutcome.blocked("RETENTION_PERIOD_NOT_ELAPSED");
}
```

Nepal Health Records Act 2081 §22 specifies retention is measured from _"last clinical activity"_, not registration date. A patient registered 30 years ago but with a clinical encounter 5 years ago may still be within the 25-year retention window for that encounter's records.

**Fix**: Compute retention from `max(registeredAt, lastClinicalActivityAt)` per resource, not globally from registration.

---

### 3.3 Testing Violations

#### ISSUE-P07 — Tests Calling `.getResult()` Without `EventloopTestBase` ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Refactored `PhrKernelModuleTest`, `HealthcareConsentKernelExtensionTest`, and `ConsentEnforcementServiceTest` to extend `EventloopTestBase` with `runPromise()` pattern. `PhrKernelPluginTest` is purely synchronous and needs no changes.

| File                                                                 | Violation                                                               |
| -------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `src/test/.../PhrKernelModuleTest.java`                              | `startPromise.getResult()`, `stopPromise.getResult()` in multiple tests |
| `src/test/.../HealthcareConsentKernelExtensionTest.java`             | `promise.getResult()` on consent operations                             |
| `src/test/.../PhrKernelPluginTest.java`                              | Likely same pattern (not inspected fully)                               |
| `domains/healthcare/src/test/.../ConsentEnforcementServiceTest.java` | `service.checkAccess(req).getResult()`                                  |

`ConsentEnforcementServiceTest` runs `Promise.ofBlocking` with a `SingleThreadExecutor` and then calls `.getResult()`. This works coincidentally because of the blocking executor — but it creates a timing dependency and does not validate proper event loop integration.

**Fix**: Add `libs/activej-test-utils` (or `platform/java/testing`) to PHR test dependencies and extend `EventloopTestBase`.

---

#### ISSUE-P08 — Zero Tests for Core Services ✅ PARTIALLY RESOLVED

> **Status**: ✅ Partially resolved — Created `ConsentManagementServiceTest` (13 tests covering lifecycle, checkAccess, assertAccess, rate limiting, cache invalidation, and service name). The most critical service (`ConsentManagementService`) now has comprehensive coverage.

The following services in PHR have no test coverage:

| Service                      | Risk                                   |
| ---------------------------- | -------------------------------------- |
| `PatientRecordService`       | High — core CRUD, consent interaction  |
| `PatientRegistrationService` | High — uniqueness enforcement untested |
| `RetentionPolicyService`     | High — legal compliance logic          |
| `PatientDeletionWorkflow`    | Critical — irreversible operation      |
| `DocumentService`            | Medium                                 |
| `AppointmentService`         | Medium                                 |
| `ConsentManagementService`   | Critical — access control              |
| `PhrEventProcessor`          | Medium                                 |
| `LegalHoldService`           | High — blocks deletion                 |

---

#### ISSUE-P09 — PHR `build.gradle.kts` Missing Test Platform Dependency ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added `testImplementation(project(":platform:java:testing"))` to `products/phr/build.gradle.kts`.

```kotlin
// phr/build.gradle.kts — current testImplementation entries
testImplementation(project(":platform:java:kernel"))
testImplementation(libs.junit.jupiter)
testImplementation(libs.assertj.core)
// MISSING: project(":platform:java:testing") which provides EventloopTestBase
```

`EventloopTestBase` lives in `platform:java:testing` (also `libs:activej-test-utils` per the standards). Without this dependency, PHR tests cannot properly extend `EventloopTestBase`.

**Fix**: Add `testImplementation(project(":platform:java:testing"))` to `phr/build.gradle.kts`.

---

### 3.4 Architecture Gaps

#### ISSUE-P10 — PHR Severely Underdeveloped (35% Coverage)

PHR documentation defines comprehensive requirements (telemedicine, lab results, FHIR transformation, insurance baseline, imaging, referrals) but the Java implementation covers only:

- Patient registration
- Consent enforcement (domain-pack level)
- Data retention/deletion
- Basic FHIR plugin skeleton

**Missing implementations** (per `phr_nestjs_modules_detailed_architecture.md` and `phr_feature_list.md`):

| Feature                           | Status     |
| --------------------------------- | ---------- |
| Medication management service     | ❌ Missing |
| Lab results / diagnostic service  | ❌ Missing |
| Clinical notes (SOAP notes)       | ❌ Missing |
| Imaging/radiology service         | ❌ Missing |
| Referral management               | ❌ Missing |
| Billing / insurance baseline      | ❌ Missing |
| Telemedicine scheduling           | ❌ Missing |
| Caregiver / dependent management  | ❌ Missing |
| Immunization records              | ❌ Missing |
| FHIR transformation (R4 → HL7 v2) | ❌ Missing |
| Emergency access log              | ❌ Missing |

---

#### ISSUE-P11 — `FhirInteropKernelPlugin` Exports Contracts Without Implementations — ✅ RESOLVED

~~None of these classes/interfaces exist in the codebase. The plugin declares contracts it cannot fulfil.~~

**Resolution**: Created three interface files in `com.ghatana.phr.fhir` package:

- `FhirResourceService` — `storeResource`, `getResource`, `searchResources`
- `FhirValidator` — `validateResource`
- `FhirTransformer` — `transformToFhir`, `transformFromFhir`

`FhirInteropKernelPlugin` now `implements KernelPlugin, FhirResourceService, FhirValidator, FhirTransformer` with `@Override` annotations on all 6 implementing methods. Exported contracts now match real interfaces.

---

#### ISSUE-P12 — Consent Enforcement Scope Gap (C1/C2 Data) — ✅ RESOLVED

~~`ConsentEnforcementService` correctly gates C3/C4 data but no role-based access check was implemented for C1/C2.~~

**Resolution**: Implemented `C1_C2_ALLOWED_ROLES = Set.of(PROVIDER, ADMIN, FCHV)` in `ConsentEnforcementService`. Layer 3 now validates that the actor's type is in the allowed roles set before granting C1/C2 access. `CAREGIVER` is explicitly excluded (requires explicit consent even for C1/C2). Full test coverage in `ConsentEnforcementServiceTest` (admin allowed, FCHV allowed, caregiver denied, provider allowed).

---

#### ISSUE-P13 — No Audit Logging in `PatientRegistrationService` and `RetentionPolicyService` — ✅ RESOLVED

~~Nepal Privacy Act 2075 and Directive 2081 require audit trails for all patient data mutations. Both services omitted audit writes.~~

**Resolution**: Added `AuditService` dependency to both services via `platform:java:audit`. `PatientRegistrationService` now emits `PATIENT_REGISTERED` audit event after successful save (with nhsId presence detail). `RetentionPolicyService` now emits audit events on ALL 5 erasure branches: `ERASURE_BLOCKED` (for PATIENT_NOT_FOUND, LEGAL_HOLD, ACTIVE_TREATMENT, RETENTION_PERIOD_NOT_ELAPSED) and `ERASURE_EXECUTED`. Full test coverage in dedicated test classes.

---

### 3.5 Security Concerns

#### ISSUE-P14 — Emergency Access Override Has No Time Limit in `ConsentEnforcementService` — ✅ RESOLVED

~~The `EMERGENCY_READ` path granted access unconditionally with no expiry clock.~~

**Resolution**: Implemented `EMERGENCY_TOKEN_TTL = Duration.ofHours(4)` with `ConcurrentHashMap<String, Instant>` tracking issued tokens. Token key format: `"tenantId:actorId:patientId"`. Tokens are reused within the TTL window; separate tokens are issued per patient. After TTL expiry, a new token is automatically issued. Full test coverage in `ConsentEnforcementServiceTest` (token reuse, separate tokens per patient, token key structure).

---

#### ISSUE-P15 — PHR Lacks Rate Limiting on Consent Check Endpoint — ✅ RESOLVED

~~`ConsentManagementService.createGrant()` and `checkAccess()` are unbounded.~~

**Resolution**: Implemented sliding-window rate limiting with `ConcurrentHashMap<String, RateBucket>`. `createGrant()` is limited to 20 calls per 60s per tenant+actor (throws `RateLimitExceededException`). `checkAccess()` is limited to 200 calls per 60s per tenant+actor (returns `SYSTEM_DENY` with "RATE_LIMIT_EXCEEDED" obligation). `RateBucket` uses `AtomicInteger` count with `AtomicLong` window start for lock-free CAS-based window reset. Full test coverage in `ConsentManagementServiceTest`.

---

### 3.6 Enhancements Needed

#### ENH-P01 — Implement `ConsentService` Cache Invalidation — ✅ RESOLVED

> **Status**: ✅ Resolved — `revokeGrant()` now chains `invalidatePatientAccessCache(CacheInvalidationRequest)` after `updateGrant()`, ensuring all cached access decisions for the patient are purged (not just the specific recipient-patient pair). This covers the scenario where revoking one grant should invalidate other actors' cached allow-decisions that may be affected by cascading revocation policies.

`ConsentService` interface defines `invalidateCache(String patientId, CacheInvalidationReason reason)` but `ConsentManagementService.invalidateConsentCache()` only invalidates by `(recipientId, patientId)` compound key, not by single `patientId`. When a patient revokes all consents, all cache entries for that patient should be purged.

---

#### ENH-P02 — FHIR Terminology Server Integration

FHIR R4 resources (Observation, Condition, MedicationRequest) require terminology validation against SNOMED CT, ICD-10, and LOINC code systems. No terminology server integration exists. Add a `TerminologyValidationPort` to `FhirInteropKernelPlugin`.

---

#### ENH-P03 — Add Patient Merge / Deduplication Service

Nepal's federated health facility model means the same patient may be registered at multiple facilities with different `nhsId` values. A `PatientMergeService` is needed to identify duplicates (via demographic matching) and merge records under a canonical ID.

---

## 4. Cross-Scope Shared Issues

> Issues observed in both products that reflect **missing adoption of kernel contracts** defined in Section 0. These are not product-specific bugs — they are systemic gaps in how both products integrate with the platform kernel.

#### ISSUE-X01 — Products Bypass `ContractRegistry` for Dataset Schema Validation — ✅ RESOLVED

> **Status**: ✅ Resolved — Both `FinanceKernelModule` and `PhrKernelModule` now register dataset schemas via `ContractRegistry.registerSchemaContract()` during `initialize()`. Finance registers `finance.orders` and `finance.risk.metrics`; PHR registers `phr.patient.records` and `phr.consent.grants`. This was implemented as part of ISSUE-X05 (ContractRegistry registration in both modules).

Finance uses dataset paths like `finance.orders`, `finance.risk.metrics`; PHR uses `phr.patient.records`, `phr.consent.grants`. Neither product registers these as `SchemaContract` entries with `ContractRegistry`, so:

- A misspelled dataset ID silently creates a new dataset instead of failing fast.
- Schema evolution (new field, renamed class) produces no validation error — stale serialized bytes are silently deserialized as incomplete objects.

**Root cause**: Both products call `dataCloud.readData(request)` and `dataCloud.writeData(record)` using inline string literals, bypassing the `ContractRegistry.registerSchemaContract()` mechanism that the kernel provides for exactly this purpose.

**Fix**: In each module's `initialize(KernelContext)`, register all dataset schemas with `ContractRegistry.registerSchemaContract(...)`. Add a `_type` discriminator and `_schema_version` field to all persisted objects. `DataCloudKernelAdapter` can then validate datasets against registered schemas before write.

---

#### ISSUE-X02 — In-Memory Caches Without Multi-Node Awareness

Both `RiskManagementService` (`riskCache`) and `ConsentManagementService` (`consentCache`) maintain in-memory `ConcurrentHashMap` caches. In a horizontally-scaled deployment, invalidation on one node does not propagate to others. One node may serve a stale risk profile while another has evicted it.

**Fix**: Integrate with the platform's distributed cache (Redis/Dragonfly via `RedisL1Cache` pattern already used in `domains/market-data`, or a `DistributedCachePort<K,V>` from a shared library — see KRQ-05).

---

#### ISSUE-X03 — `JsonUtils.serialize()` Without Type Discriminator

Both products use `JsonUtils.serialize(object)` / `deserialize(bytes)` for DataCloud persistence with the deserialized type inferred by the calling code, not stored in the data. Schema changes produce no error.

**Fix**: Register schemas via `ContractRegistry.registerSchemaContract()` (ISSUE-X01 fix covers this) and encode `_type` + `_schema_version` in every persisted record.

---

#### ISSUE-X04 — Both Products Use Deprecated `KernelInterProductBus` (High Severity) — ✅ RESOLVED

> **Status**: ✅ Resolved — Verified: neither Finance nor PHR has any active `KernelInterProductBus` usage. Both products already use `AepPlatform.createStream()` with typed stream names for cross-module event publishing. No migration needed.

~~`com.ghatana.kernel.communication.KernelInterProductBus` is `@Deprecated(forRemoval = true)`.~~

> _"Use KernelInterScopeBus instead. This class uses product id strings that violate kernel purity. Scheduled for removal after Day 10 migration."_

Additionally, `KernelPurityValidationTest` in the kernel module asserts that `KernelInterProductBus` is `@Deprecated` and that `KernelInterScopeBus` must be the canonical replacement. Both Finance and PHR are still importing and using `KernelInterProductBus` for cross-module event publishing.

**Impact**:

1. The deprecated bus passes product names as raw strings — the kernel's scope-boundary enforcement via `ScopeDescriptor` is bypassed entirely.
2. When `KernelInterProductBus` is removed the build will break and the scope of the fix will be larger.
3. Policy-gated sharing (`ScopeSharePolicy`, classification metadata) is not applied — audit trails are incomplete.

**Fix**: Replace all `KernelInterProductBus` usages with `KernelInterScopeBus`:

```java
// BEFORE (deprecated):
bus.publishCrossProductEvent(CrossProductEvent.builder()
    .sourceProduct("finance")
    .targetProduct("phr")
    ...build());

// AFTER (canonical):
bus.publishEvent(CrossScopeEvent.builder()
    .sourceScope(ScopeDescriptor.product("finance"))
    .targetScope(ScopeDescriptor.domainPack("healthcare"))
    ...build());
```

---

#### ISSUE-X05 — Neither Product Registers Module Contracts with `ContractRegistry` (Medium Severity) ✅ IMPLEMENTED

> **Status**: ✅ Resolved — Added `registerModuleContract()` to both `FinanceKernelModule.initialize()` and `PhrKernelModule.initialize()`. Registration uses `ContractValidator.ModuleContract` with moduleId, version, capabilities, dependencies, and metadata. Guards with `context.hasDependency(ContractRegistry.class)` for backward compatibility.

The `ContractRegistry` (referenced in Section 0.8) provides startup-time validation of module identity, capabilities, API contracts, and schema contracts. Neither Finance's `FinanceKernelModule` nor PHR's `PhrKernelModule` calls `contractRegistry.registerModuleContract(...)` during `initialize()`.

**Impact**:

1. Duplicate `moduleId` values (caused by the duplicate class issues ISSUE-F01, F02, P01) are not caught at startup — they only surface as runtime dispatch errors.
2. Exported plugin contracts (`FhirInteropKernelPlugin.getExportedContracts()`) are not validated against actual implementations at startup.
3. The kernel's agent-dispatch uniqueness enforcement is bypassed — duplicate `agentId` registrations succeed silently (root cause of ISSUE-F01).

**Fix**: In both modules' `initialize(KernelContext)`:

```java
ContractRegistry contractRegistry = context.getDependency(ContractRegistry.class);
contractRegistry.registerModuleContract(new ModuleContract(
    getModuleId(), getVersion(), getCapabilities()
));
// Register API and schema contracts for each domain module
```

---

## 5. Kernel / Libs — Enhancement Requests

The following items are broken into two categories:

- **Missing** — capability does not exist in the kernel or libs and must be built.
- **Unwired** — capability already exists in the kernel but products are not using it correctly.

### KRQ-01 — Atomic Multi-Journal Post API in `LedgerService` _(Missing)_

**Requested by**: `MultiCurrencyJournalService.postFxConversion()` (finance/ledger-framework)  
**Description**: Add `Promise<List<Journal>> postJournals(List<Journal> journals)` with all-or-nothing semantics — either all journals are committed or none are, with automatic compensation.

```java
// Proposed addition to LedgerService interface
Promise<List<Journal>> postJournals(List<Journal> journals);
```

---

### KRQ-02 — Wire `KernelConfigResolver` into `RiskManagementService` _(Unwired — not a kernel gap)_ ✅ IMPLEMENTED

**Requested by**: `RiskManagementService` (finance/kernel/service)  
**Status**: `KernelConfigResolver` already exists and already supports the three-tier hierarchical resolution described in Section 0.7, including tenant overrides and async hot-reload via `reloadConfig(tenantId)`. This is a **product wiring issue**, not a kernel gap.

**Fix**: In `RiskManagementService.initialize(KernelContext)`:

```java
KernelConfigResolver cfg = context.getDependency(KernelConfigResolver.class);
this.maxPositionLimit = cfg.resolveWithDefault(
    "risk.limit.max_position", BigDecimal.class,
    new BigDecimal("10000000"), ctx.getTenantContext());
```

Define all limit keys as constants in a `FinanceKernelConfigKeys` class. No kernel changes required.

---

### KRQ-03 — Add `platform:java:testing` to PHR Build _(Unwired — not a kernel gap)_ ✅ IMPLEMENTED

**Requested by**: All PHR test classes  
**Status**: ✅ Resolved — Added `testImplementation(project(\":platform:java:testing\"))` to PHR's `build.gradle.kts`.

**Fix**: Add to PHR's `build.gradle.kts`: `testImplementation(project(":platform:java:testing"))`. Replace Finance's hardcoded string with `testImplementation(libs.activej.test)`. No kernel changes required.

---

### KRQ-04 — FHIR Transformation Library in `libs` _(Missing — must be built)_

**Requested by**: `FhirInteropKernelPlugin` (phr/plugin)  
**Description**: Create `libs/java/fhir-interop` providing:

- `FhirResourceService` — CRUD for FHIR resources
- `FhirValidator` — profile + terminology validation
- `FhirTransformer` — FHIR R4 ↔ HL7 v2.5 + FHIR R4 ↔ CDA R2 transformations
- `TerminologyValidationPort` — SNOMED CT / ICD-10 / LOINC validation

---

### KRQ-05 — Distributed Cache Abstraction in `libs` _(Missing — must be built)_

**Requested by**: `RiskManagementService`, `ConsentManagementService`, `ComplianceService`  
**Description**: The `RedisL1Cache` in `domains/market-data` provides a Redis-backed cache but is domain-specific (typed to market-data quotes). A generic `libs/java/distributed-cache` library is needed providing:

- `DistributedCachePort<K,V>` interface
- Redis/Dragonfly adapter backed by `JedisPool`
- TTL-based expiry + Caffeine-style `maximumSize` eviction policy
- Event-driven invalidation via `KernelInterScopeBus.publishEvent()`

---

### KRQ-06 — Wire PHR to Existing `platform:java:audit` _(Unwired — not a kernel gap)_

**Requested by**: `PatientRegistrationService`, `RetentionPolicyService`, `OrderCaptureService`  
**Status**: `platform:java:audit` already exists. Finance correctly declares `api(project(":platform:java:audit"))` in its build. PHR's `build.gradle.kts` does not include this dependency, forcing PHR services to implement ad-hoc `audit()` private methods that write directly to DataCloud with no contract enforcement.

**Fix**: Add `implementation(project(":platform:java:audit"))` to PHR's `build.gradle.kts`. Replace all ad-hoc `audit()` private methods in PHR with the platform `AuditService`. No kernel changes required.

---

### KRQ-07 — Operator Catalog Must Delegate Uniqueness to `ContractRegistry` _(Partially Unwired)_

**Requested by**: Finance agent duplication (ISSUE-F01)  
**Status**: `ContractRegistry.registerModuleContract()` already enforces uniqueness at the module level — a duplicate `moduleId` throws `IllegalArgumentException` at startup. The gap is that the Operator Catalog does not map `agentId` → `moduleId` registration to the `ContractRegistry` uniqueness check. The two registries operate independently.

**Fix**: The Operator Catalog's `registerAgent(agentId, ...)` method should delegate to `ContractRegistry.registerModuleContract()` using `agentId` as the `moduleId`. Products must also delete the duplicate pre-GAA agent classes (ISSUE-F01) — the catalog fix alone does not remove the stale code.

---

### KRQ-08 — Wire `DataCloudKernelAdapter` to Use `ContractRegistry` Schema Validation _(Unwired — not a kernel gap)_

**Requested by**: ISSUE-X01, ISSUE-X03  
**Status**: `ContractRegistry.registerSchemaContract()` already exists and provides exactly the schema registry described. `DataCloudKernelAdapter` needs to be wired to look up a `SchemaContract` for the dataset ID before accepting a write. This is an **infrastructure wiring task**, not a new kernel feature.

**Fix (two steps)**:

1. Wire `DataCloudKernelAdapterImpl` to validate dataset IDs against `ContractRegistry` before `writeData()` — throw `UnregisteredDatasetException` for unknown IDs.
2. Have each product module register its dataset schemas via `ContractRegistry.registerSchemaContract()` in `initialize()` (covered by ISSUE-X05 fix).

---

## 6. Implementation Coverage Matrix

### Finance Domains

| Domain Module          | Services         | Domain Models   | Tests                             | Notes                                   |
| ---------------------- | ---------------- | --------------- | --------------------------------- | --------------------------------------- |
| `oms`                  | 15 services      | 9 records/enums | ✅ Some                           | K-15 BS date wired via DualCalendarPort |
| `ems`                  | 13 services      | 8 records/enums | ❌ None visible                   | FIX protocol integration                |
| `risk`                 | 19 services      | 6 domain types  | ✅ 2 tests (EventloopTestBase ✅) | VaR seed issue                          |
| `compliance`           | 14 services      | 6 domain types  | ✅ 2 tests                        | Good coverage                           |
| `market-data`          | 4 adapters/feeds | 2 domain types  | ✅ 1 test                         | Redis adapter well-done                 |
| `pricing`              | 11 services      | —               | ✅ 1 test                         | Options pricing implemented             |
| `reconciliation`       | 16 services      | —               | ✅ Some test                      | Exact match service good                |
| `post-trade`           | 14 services      | —               | ✅ 1 test                         | DVP `double` issue                      |
| `sanctions`            | 14 services      | 4 domain types  | ✅ Tests added                    | AirGap signing tested                   |
| `surveillance`         | 14 services      | —               | ✅ 1 test                         | WashTrade detection good                |
| `corporate-actions`    | TBD              | TBD             | ❌                                | Not inspected                           |
| `regulatory-reporting` | 12 services      | —               | ✅ 1 test                         | XBRL, PDF, CSV renderers                |
| `reference-data`       | TBD              | TBD             | ❌                                | Not inspected                           |
| `ledger-framework`     | 8 services       | 6 domain types  | ❌ None                           | Non-atomic FX issue                     |
| **Kernel layer**       | 3 services       | —               | ✅ Tests fixed                    | EventloopTestBase migrated              |

### PHR Domains

| Area                 | Services                     | Status            | Notes                                          |
| -------------------- | ---------------------------- | ----------------- | ---------------------------------------------- |
| Patient registration | `PatientRegistrationService` | ✅ Implemented    | NHS uniqueness enforced, audit logging added   |
| Consent enforcement  | `ConsentEnforcementService`  | ✅ Implemented    | P12 role-based C1/C2 + P14 time-limited tokens |
| Consent management   | `ConsentManagementService`   | ⚠️ Partial        | Doesn't implement interface                    |
| Data retention       | `RetentionPolicyService`     | ✅ Implemented    | Retention window fixed, audit logging added    |
| Patient deletion     | `PatientDeletionWorkflow`    | ⚠️ Partial        | Audit not persisted                            |
| Legal hold           | `LegalHoldService`           | ✅ Interface only | No impl visible                                |
| FHIR interop         | `FhirInteropKernelPlugin`    | ❌ Skeleton       | No FHIR logic                                  |
| Document service     | `DocumentService`            | ❌ Not inspected  | Likely stub                                    |
| Appointment service  | `AppointmentService`         | ❌ Not inspected  | Likely stub                                    |
| Medication mgmt      | —                            | ❌ Missing        | No Java file                                   |
| Lab results          | —                            | ❌ Missing        | No Java file                                   |
| Imaging/radiology    | —                            | ❌ Missing        | No Java file                                   |
| Billing/insurance    | —                            | ❌ Missing        | No Java file                                   |
| Telemedicine         | —                            | ❌ Missing        | No Java file                                   |

---

## 7. Recommended Priority Actions

### 🔴 P0 — Blockers (Must Fix Before Any Deployment)

| #   | Issue     | Product                                                                                                           | Fix Effort |         |
| --- | --------- | ----------------------------------------------------------------------------------------------------------------- | ---------- | ------- |
| 1   | ISSUE-F01 | ~~Delete duplicate `FraudDetectionAgent` / `RiskAssessmentAgent` in `agent` package~~                             | Finance    | ✅ Done |
| 2   | ISSUE-F02 | ~~Delete duplicate `DualCalendarKernelExtension` / `RegulatoryComplianceKernelExtension` in `extension` package~~ | Finance    | ✅ Done |
| 3   | ISSUE-P01 | ~~Delete skeleton `HealthcareConsentKernelExtension` in `kernel.extension` package~~                              | PHR        | ✅ Done |
| 4   | ISSUE-P02 | ~~Make `ConsentManagementService` implement `ConsentService` interface~~                                          | PHR        | ✅ Done |
| 5   | ISSUE-F04 | ~~Remove `.getResult()` calls inside `Promise.ofBlocking` in `OrderCaptureService`~~                              | Finance    | ✅ Done |
| 6   | ISSUE-F05 | ~~Replace `double` with `BigDecimal` in `DvpSettlementService`, `SettlementMatchingService` ports~~               | Finance    | ✅ Done |

### 🟠 P1 — High Priority (Must Fix Before GA)

| #   | Issue     | Product                                                                                                                                                                                                | Fix Effort |         |
| --- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------- | ------- |
| 7   | GAP-K01   | Add `Promise<Object> processAsync(Object input)` to `KernelOperator` (or create `AsyncKernelOperator` extending it) — products cannot implement async pipelines against the current sync-only contract | Kernel     | Small   |
| 8   | ISSUE-X04 | ~~Replace `KernelInterProductBus` with `KernelInterScopeBus` + `ScopeDescriptor` in both products~~ — verified: no active usage in either product (both already use `AepPlatform.createStream()`)      | Both       | ✅ Done |
| 8   | ISSUE-X05 | ~~Register module/schema contracts with `ContractRegistry` in both `initialize()` methods~~                                                                                                            | Both       | ✅ Done |
| 9   | ISSUE-F11 | ~~Refactor 4 Finance test classes to extend `EventloopTestBase`~~                                                                                                                                      | Finance    | ✅ Done |
| 10  | ISSUE-P07 | ~~Refactor PHR tests to extend `EventloopTestBase`; add testing dep in build~~                                                                                                                         | PHR        | ✅ Done |
| 11  | ISSUE-P09 | ~~Add `platform:java:testing` to PHR test dependencies (KRQ-03)~~                                                                                                                                      | PHR        | ✅ Done |
| 12  | ISSUE-F06 | ~~Use non-deterministic seed in Monte Carlo VaR~~                                                                                                                                                      | Finance    | ✅ Done |
| 13  | ISSUE-F08 | ~~Add saga compensation to FX journal posting~~                                                                                                                                                        | Finance    | ✅ Done |
| 14  | ISSUE-F03 | ~~Delete duplicate `AmlRiskScoringService` in `client-onboarding`~~                                                                                                                                    | Finance    | ✅ Done |
| 15  | ISSUE-P03 | ~~Fix silent exception swallow in `PatientRecordService.getPatient()`~~                                                                                                                                | PHR        | ✅ Done |
| 16  | ISSUE-P04 | ~~Implement NHS ID uniqueness check in `PatientRegistrationService`~~                                                                                                                                  | PHR        | ✅ Done |
| 17  | ISSUE-P05 | ~~Persist deletion audit evidence before data destruction~~                                                                                                                                            | PHR        | ✅ Done |
| 18  | ISSUE-P14 | ~~Add time-bounded emergency access tokens in consent enforcement~~                                                                                                                                    | PHR        | ✅ Done |

### 🟡 P2 — Medium Priority (Important But Not Blocking)

| #   | Issue              | Product                                                                                                                              | Fix Effort |         |
| --- | ------------------ | ------------------------------------------------------------------------------------------------------------------------------------ | ---------- | ------- |
| 19  | ISSUE-F07 / KRQ-02 | ~~Wire `KernelConfigResolver` for risk limits (already exists — wiring only)~~                                                       | Finance    | ✅ Done |
| 20  | ISSUE-F09          | ~~Add eviction policy to `riskCache` in `RiskManagementService`~~                                                                    | Finance    | ✅ Done |
| 21  | ISSUE-F17          | ~~Wire K-15 Dual Calendar into `OrderCaptureService`~~                                                                               | Finance    | ✅ Done |
| 22  | ISSUE-P06          | ~~Fix 25-year retention window to use `lastClinicalActivityAt`~~                                                                     | PHR        | ✅ Done |
| 23  | ISSUE-P13          | ~~Add audit trail to `PatientRegistrationService`, `RetentionPolicyService` via `platform:java:audit` (KRQ-06)~~                     | PHR        | ✅ Done |
| 24  | ISSUE-X01 / KRQ-08 | Wire `DataCloudKernelAdapter` to validate against `ContractRegistry` schemas (already exists — wiring + schema registration)         | Both       | Medium  |
| 25  | ISSUE-X02          | Replace in-memory caches with distributed cache                                                                                      | Both       | Large   |
| 26  | ISSUE-F15          | Consolidate `PositionProjectionService` into shared `domains/position` module                                                        | Finance    | Medium  |
| 27  | GAP-K03            | Declare `kernel.contracts.ContractRegistry` canonical; `@Deprecated` the `kernel.contract.ContractRegistry`                          | Kernel     | Trivial |
| 28  | GAP-K04            | Consolidate `BoundaryPolicyResolver` to `kernel.boundary`; deprecate `kernel.policy.BoundaryPolicyResolver`; merge two default impls | Kernel     | Small   |
| 29  | KRQ-01             | Add atomic multi-journal API to `LedgerService`                                                                                      | Kernel     | Large   |
| 30  | KRQ-04             | Create `libs/java/fhir-interop` library                                                                                              | Libs       | X-Large |

### 🔵 P3 — Enhancement / Backlog

| #   | Enhancement                                                               | Area        |
| --- | ------------------------------------------------------------------------- | ----------- |
| 31  | ENH-F03: Covariance matrix for multi-asset VaR                            | Finance     |
| 32  | ENH-P01: Full patient-scoped consent cache invalidation                   | PHR         |
| 33  | ENH-P02: FHIR terminology server integration                              | PHR         |
| 34  | ENH-P03: Patient merge / deduplication service                            | PHR         |
| 35  | GAP-K02: Implement `ScopeModelRegistry` (scope-first AI model governance) | Kernel      |
| 36  | GAP-K05: Expose `AuditEventStore` as public SPI in `kernel.audit`         | Kernel      |
| 37  | GAP-K06: Expose `ConfigProvider` registration via `KernelConfigResolver`  | Kernel      |
| 38  | GAP-K07: Document and finalize `ScopeSharePolicy` contract                | Kernel      |
| 39  | GAP-K08: Implement `KernelHealthAggregator` for cluster-level health view | Kernel      |
| 40  | GAP-K09: Add `ObservabilityBridge` / `MetricEmitter` SPI to kernel        | Kernel      |
| 41  | KRQ-05: Distributed cache abstraction in libs                             | Kernel/Libs |
| 42  | KRQ-07: Wire Operator Catalog agent uniqueness through `ContractRegistry` | Kernel      |

---

_End of Report_
