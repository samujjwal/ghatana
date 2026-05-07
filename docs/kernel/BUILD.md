# Ghatana Build Guide

> **Owner:** Platform Team | **Status:** Active | **Last Updated:** 2026-04-14

---

## 1. Build System Overview

Ghatana uses:
- **Gradle** (wrapper via `./gradlew`, v9.2.1) for all Java/Kotlin modules
- **pnpm + Turborepo** for all TypeScript packages
- **Cargo** for Rust components (in selected product areas)

### Gradle Convention Plugins

All Java modules use `build-logic` convention plugins only. There is no `buildSrc`.

| Plugin ID | Purpose |
|-----------|---------|
| `id("java-module")` | Standard library/module |
| `id("java-application")` | Runnable application |
| `id("protobuf-module")` | Protobuf contract module |
| `id("finance-domain-module")` | Finance-domain-specific module |
| `id("integration-test-profile")` | Enables integration test source set |

**Never use legacy `com.ghatana.*` convention IDs** — all modules have been migrated.

### Version Catalog

All dependency versions are declared in `gradle/libs.versions.toml`. Never hardcode versions directly in `build.gradle.kts`.

```kotlin
// Correct
dependencies {
    implementation(libs.bundles.activej)
    implementation(libs.bundles.platform.security)
}

// Wrong – hardcoded version
dependencies {
    implementation("io.activej:activej-core:6.0.0")
}
```

---

## 2. Running the Build

```bash
# Full cold build
./gradlew clean build

# Run all Java tests
./gradlew test

# Run TypeScript tests
pnpm test

# Run a single module
./gradlew :platform:java:agent-core:build

# Run tiered tests (unit → integration → contract → e2e)
./scripts/test-tiered.sh

# Coverage report per module
./scripts/coverage-report.sh
```

---

## 2.1 Composite Build Execution

Ghatana uses composite builds for isolated Gradle workspaces. Each composite build includes its local settings and can be executed independently.

**Root-mode execution** (from repository root):

```bash
# Build all modules including both composites
./gradlew build

# Build only platform-kernel composite modules
./gradlew :platform-kernel:kernel-core:build
./gradlew :platform-kernel:kernel-plugin:build
./gradlew :platform-kernel:kernel-persistence:build

# Build only platform-plugins composite modules
./gradlew :platform-plugins:plugin-compliance:build
./gradlew :platform-plugins:plugin-risk-management:build

# Run purity and verification gates (root)
./gradlew :platform-kernel:kernel-core:check
./gradlew :platform-plugins:plugin-compliance:check
```

**Included-build-mode execution** (from within a composite):

```bash
# From platform-kernel/ folder
cd platform-kernel
./gradlew kernel-core:build
./gradlew kernel-plugin:build
./gradlew kernel-persistence:build
./gradlew kernel-core:check  # Runs purity gates for kernel-core

# From platform-plugins/ folder
cd platform-plugins
./gradlew plugin-compliance:build
./gradlew plugin-risk-management:build
./gradlew plugin-compliance:check
```

**Key differences:**

| Aspect | Root-mode | Included-build-mode |
|--------|-----------|-------------------|
| Working directory | `d:\samuj\Developments\ghatana` | `d:\samuj\Developments\ghatana\platform-kernel` or `platform-plugins` |
| Task paths | `:platform-kernel:kernel-core:build` | `kernel-core:build` |
| Project references | `:platform-kernel:kernel-core` | `:kernel-core` |
| Use case | CI/CD, full builds, cross-composite deps | Local dev, isolated workspace builds |
| Gradle settings | root `settings.gradle.kts` | local `platform-kernel/settings.gradle.kts` or `platform-plugins/settings.gradle.kts` |

---

## 3. New Module Scaffolding

Use the module creation script — do not create modules manually:

```bash
./scripts/create-module.sh
```

The script prompts for module type (platform/product/shared/integration), creates the directory structure from templates, and sets up the `build.gradle.kts` with the correct convention plugin.

Before a new `platform/java/*` module is accepted, it must pass the admission gate:

```bash
./scripts/check-new-platform-module.sh platform/java/<module-name>
```

The gate checks for: required directory structure, `build.gradle.kts`, source sets, JavaDoc tags, and `README.md`.

---

## 4. AEP Engine Conventions

Conventions established in the 2026-03 AEP audit. Apply these in `products/data-cloud/planes/action/` and any module extending AEP.

### Naming

- Use `Aep` as the product prefix in Java types: `AepTraceContext`, `AepConsentCache`, `AepRateLimiter`
- Reserve all-caps `AEP` for prose, headings, and metric namespaces only — not Java type names
- Avoid generic names like `Manager` or `Helper`

### Package Structure

Keep engine runtime behavior in `com.ghatana.aep` and subpackages:
`config`, `cache`, `consent`, `delivery`, `error`, `health`, `lifecycle`, `metrics`, `ratelimit`, `tracing`, `version`

Keep product-specific adapter code in `aep-event-cloud` and `platform:java:messaging`. Do not push transport logic into `aep-engine`.

### Builder APIs

- Use direct, fluent method names that match the runtime concern they configure
- Accept `Duration` for duration-based settings; normalize internally
- Validate required state at `build()` time; reject invalid numeric values early

### Logging Levels

| Level | When to use |
|-------|-------------|
| `debug` | Per-event flow: pattern matching, idempotency suppression, sequence-order diagnostics |
| `info` | Lifecycle transitions: startup, shutdown, pipeline submission, hot reload complete |
| `warn` | Handled issues: subscriber failures, missing optional config, partial delivery failures |
| `error` | Operations that fail with caller-visible behavior impact |

### Supported Runtime Config Keys

Configure via `Aep.AepConfig.customConfig`:

| Key | Purpose |
|-----|---------|
| `idempotencyTtlSeconds` / `idempotencyMaxKeysPerTenant` | Idempotency cache |
| `asyncTimeoutMs` / `shutdownDrainTimeoutMs` | Timeouts |
| `rateLimitEnabled` / `rateLimitMaxRequestsPerMinute` / `rateLimitBurstSize` / `rateLimitWindowSeconds` | Rate limiting |
| `consentProvider` / `consentCacheTtlSeconds` / `consentCacheMaxEntries` | Consent |
| `patternCacheTtlSeconds` | Pattern cache |
| `hotReloadConfigPath` / `hotReloadCheckIntervalMs` | Hot reload |
| `currentEventVersion` / `minSupportedEventVersion` | Event versioning |

**Hot-reload safe settings only:** anomaly threshold, tracing enablement, async timeout, consent-cache TTL, rate-limit enablement and capacity. Structural settings (worker thread count, transport selection, pipeline capacity) are startup-only.

---

## 5. Platform API Conventions

All async operations use `io.activej.promise.Promise`. All APIs require tenant context.

### Validation

```java
ValidationService validation = ValidationService.builder()
    .addValidator(new EmailValidator())
    .addValidator(new NotNullValidator())
    .build();
Promise<ValidationResult> result = validation.validateEvent(event, context);
```

### Auth

```java
AuthService auth = new DefaultAuthService();
Promise<UserPrincipal> user = auth.authenticate(token);
boolean canAccess = auth.checkPermission(user, "collection:read");
```

### AEP Agent Execution

```java
Agent agent = new CodeGenerationAgent(llmGateway);
Promise<GeneratedCode> result = agent.execute(task, context);

// Pipeline of agents
Agent pipeline = AgentPipeline.builder()
    .add(new AnalysisAgent())
    .add(new CodeGenAgent())
    .add(new ValidationAgent())
    .build();
```

### Data-Cloud Collections

```java
CollectionService collections = new DefaultCollectionService();
Promise<Collection> created = collections.create(tenantId, collection);
Promise<List<Collection>> list = collections.list(tenantId);
```

---

## 6. Java Documentation Tags

Every public Java class must include all four required Javadoc tags:

```java
/**
 * @doc.type class
 * @doc.purpose Processes incoming events for tenant-scoped workflows.
 * @doc.layer product
 * @doc.pattern Service
 */
```

Validate compliance: `./scripts/validate-doc-tags.sh`

---

## 7. Gradle Profiler Scenarios

`build.scenarios` at the repo root defines profiler scenarios for measuring build performance. Run with:

```bash
gradle-profiler --benchmark --project-dir . --scenario-file build.scenarios
```

Key scenarios: `cold_build` (worst-case CI), `incremental_platform_core` (developer edit cycle).

---

## Related Documents

- [GOVERNANCE.md](./GOVERNANCE.md) — build CI gates and governance
- [ONBOARDING.md](./ONBOARDING.md) — first-build and prerequisites
- [docs/adr/](./adr/) — Architecture Decision Records