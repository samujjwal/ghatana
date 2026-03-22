# Developer Onboarding Guide

Welcome to the **ghatana** monorepo. This guide will get you from zero to a running local development environment.

---

## 1. Prerequisites

Install the following before cloning:

| Tool | Version | Install |
|------|---------|---------|
| **Java** | 21+ | [sdkman.io](https://sdkman.io) · `sdk install java 21-tem` |
| **Gradle** | 9.2.1 (wrapper bundled) | Included via `./gradlew` |
| **Node.js** | 18+ | [fnm](https://github.com/Schniz/fnm) or [nvm](https://nvm.sh) |
| **pnpm** | 10+ | `npm install -g pnpm` |
| **Docker** | 24+ | [docker.com/get-docker](https://www.docker.com/get-docker/) |
| **Docker Compose** | 2.20+ | Bundled with Docker Desktop |

Verify your setup:

```bash
java --version        # openjdk 21+
./gradlew --version   # Gradle 9.2.1
node --version        # v18+
pnpm --version        # 10+
docker --version      # 24+
```

---

## 2. First Build

```bash
# Clone
git clone git@github.com:ghatana/ghatana.git
cd ghatana

# Install Node dependencies
pnpm install

# Build all Java modules (first run takes ~5 min — downloads deps)
./gradlew clean build

# Run all tests
./gradlew test

# TypeScript tests
pnpm test
```

---

## 3. Running a Product Locally

### Example A — AEP (Agentic Event Processor)

```bash
# Start backing services
docker-compose -f products/aep/docker-compose.yml up -d

# Build
./gradlew :products:aep:build

# Run
./gradlew :products:aep:run
```

### Example B — YAPPC (Frontend + API)

```bash
# Start backing services
docker-compose up -d

# Java backend
./gradlew :products:yappc:platform:java:run

# Frontend (in a separate terminal)
pnpm --filter @ghatana/yappc-web dev
```

---

## 4. Key Concepts

### Agents and the GAA Lifecycle

The **Generic Adaptive Agent (GAA)** framework defines the standard agent execution pipeline:

```
PERCEIVE → REASON → ACT → CAPTURE → REFLECT
```

- **PERCEIVE** — collect input (events, messages, sensor data)
- **REASON** — select a plan/policy (pattern engine → LLM fallback)
- **ACT** — execute an `Operator` (side effect, API call, compute)
- **CAPTURE** — record the episode to `EventLog` (via `EventLogStore`)
- **REFLECT** — async, fire-and-forget; extracts patterns, updates policies

All agents extend `BaseAgent` from `libs:agent-framework`.

### Operators and Pipelines

**Operators** are the unit of work in the pipeline system. They follow the `UnifiedOperator` contract:
- Registered in the `OperatorCatalog` (single platform registry)
- Composed via `PipelineBuilder` (fluent API or YAML)
- Run via `ActiveJ Promise` — never blocking

### Events (Data Cloud)

The **Data Cloud** product (`products/data-cloud/`) acts as the event backbone:
- All side effects are expressed as events appended to an **EventLog**
- Subscriptions are push-based via `data-cloud/event` (real-time tailing)
- Package: `com.ghatana.datacloud.event`

### Tenancy

Multi-tenancy is enforced at all layers — HTTP, database queries, event routing, and storage. Always pass `TenantContext` through all service calls.

---

## 5. Code Standards — Quick Reference

### Java

| Rule | Detail |
|------|--------|
| **Async** | Use `ActiveJ Promise` only — never `CompletableFuture` or Reactor |
| **Blocking I/O** | `Promise.ofBlocking(executor, () -> ...)` |
| **Event Loop** | Never block the eventloop thread |
| **Base class** | Extend `EventloopTestBase` for ALL async tests |
| **Test runner** | `runPromise(() -> service.call())` — never `.getResult()` |
| **@doc tags** | Every public class needs all 4 tags (see below) |

**Required @doc tags:**

```java
/**
 * @doc.type    class
 * @doc.purpose One-line description of this class
 * @doc.layer   core | product | platform
 * @doc.pattern Service | Repository | ValueObject | EventSourced | etc.
 */
```

**Correct async test example:**

```java
class MyServiceTest extends EventloopTestBase {
    @Test
    void shouldProcessAsync() {
        String result = runPromise(() -> service.processAsync("input"));
        assertThat(result).isEqualTo("expected");
    }
}
```

### TypeScript / Frontend

| Rule | Detail |
|------|--------|
| **App state** | `Jotai` only (no zustand, no Redux) |
| **Server state** | `TanStack Query` |
| **Styling** | Tailwind CSS + `@ghatana/design-system` components |
| **Type safety** | No `any`. Strict null checks must pass. |
| **Linting** | Zero ESLint warnings allowed |

### Running Checks

```bash
# Java formatting
./gradlew spotlessApply

# Java static analysis
./gradlew checkstyleMain pmdMain

# Java doc-tag validation
./gradlew checkDocTags

# TypeScript lint
pnpm lint

# TypeScript type check
pnpm typecheck
```

---

## 6. How to Add a New Operator

1. Create a class implementing `UnifiedOperator<TIn, TOut>` in the appropriate product module.
2. Add `@doc.*` tags (all 4 required — `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`).
3. Register it in the product's `OperatorCatalog` configuration.
4. Write tests extending `EventloopTestBase`.
5. Run `./gradlew checkDocTags` — must produce zero warnings.

---

## 7. How to Add a New Agent

1. Create a class in `modules/agent/` (within your product) extending `BaseAgent` from `libs:agent-framework`.
2. Implement the GAA lifecycle methods: `perceive()`, `reason()`, `act()`, `capture()`, `reflect()`.
3. Register the agent in your product's `AgentRegistry` (product-scoped — do NOT use the platform SPI from other products).
4. All memory writes MUST append events to the `EventLog` — never direct mutation.
5. `reflect()` MUST be fire-and-forget (`Promise.whenComplete()` style) — never block the response.
6. Tag the class with `@doc.gaa.lifecycle` and `@doc.gaa.memory` as applicable.

---

## 8. Module Hierarchy

```
contracts/      ← Protobuf + OpenAPI schemas (no dependencies)
    ↑
platform/java/  ← Core platform libs (HTTP, DB, Auth, Observability, AI)
    ↑
products/       ← Business products (aep, data-cloud, dcmaar, virtual-org, ...)
```

**Dependency rule:** products depend on libs and contracts. Libs depend on contracts only. Cross-product dependencies are **forbidden**.

---

## 8a. Standard Product Layout Template

Every product in `products/` should follow this standard directory structure.  
Reference implementations: `products/finance/` and `products/audio-video/` (both scored 8/10 in boundary audit).

```
products/<name>/
├── OWNER.md                  ← Team ownership, Slack channel, on-call rotation (REQUIRED)
├── README.md                 ← Product overview and quickstart
├── build.gradle.kts          ← Root build file (or settings for multi-module)
├── docs/                     ← Product-local architecture documentation
│   └── CORE_ARCHITECTURE.md  ← Module map and dependency rules (recommended)
├── core/                     ← Product-local domain logic
│   ├── domain/               ← Value objects, domain events, aggregates
│   ├── spi/                  ← Public plugin/extension API (if product is extensible)
│   └── framework/            ← Framework integrations, lifecycle
├── libs/                     ← Product-owned shared libraries (usable by other products)
│   └── <lib-name>/           ← Each lib has its own build.gradle.kts
├── services/                 ← Runnable microservices / application modules
│   └── <service-name>/
├── ui/                       ← Frontend application(s) (TypeScript/React)
│   └── <app-name>/
└── infrastructure/           ← Docker, K8s manifests, Terraform (if product-specific)
```

### Placement Decision Tree

```
Is this code used by exactly 1 product?
    YES → products/<name>/core/ or products/<name>/services/
    NO (2+ products):
        Does one product own it?
            YES → products/<owner>/libs/<lib-name>/ (product-owned shared)
            NO  → platform/java/ or platform/typescript/ (truly global shared)
                  Requires Architecture Board approval via MODULE_ADMISSION_CHECKLIST.md
```

### OWNER.md Template

Every product MUST have an `OWNER.md` at its root:

```markdown
# Owner: <Product Name>

**Team:** <Team Name>
**Slack:** #<slack-channel>
**On-call:** <rotation-name>
**Architecture lead:** <name or role>

## Responsibility

<One-paragraph description of what this product does and its domain boundaries.>

## Consumers

<List of other products or external systems that consume this product's APIs.>
```

---

## 9. Getting Help

- **Architecture decisions:** `docs/adr/`
- **Platform library docs:** `docs/platform-libraries/`
- **Product-specific docs:** `products/<name>/docs/` or `products/<name>/README.md`
- **Migration guide (deprecated modules):** `migration/MODULE_MIGRATION_MAPPING.md`
- **Known issues:** `products/<name>/COMPILATION_ISSUES.md` (where present)
- **Build guide:** `gradle/PRODUCT_BUILD_GUIDE.md`
