# AI Agent Instructions for the Ghatana Codebase

> **CRITICAL**: These instructions are the SINGLE SOURCE OF TRUTH. Strict adherence is mandatory.
> **Last Updated:** 2026-01-19 (v2.4.0 - GAA Framework Integration)

## 1. 🚨 THE GOLDEN RULES (NON-NEGOTIABLE)

1.  **Reuse First**: ALWAYS check `libs/java/*` (Core) and `libs/*` before writing new code. Duplicate implementations are forbidden.
2.  **Type Safety**: No `any`. Strict null checks. 100% type coverage.
3.  **Linting**: Zero warnings allowed.
4.  **Documentation**: Every public class/method MUST have JavaDoc and `@doc.*` tags.
5.  **Testing**: All async Java tests MUST use `EventloopTestBase`.
6.  **Architecture**: Follow the Hybrid Backend model (Java/ActiveJ for Domain, Node/Fastify for User API).

## 2. 🏗️ ARCHITECTURE & MODULES

### Module Structure

- **`libs/java/*`** (Core Platform): **Platform Standards**. (HTTP, DB, Auth, Observability). _Depend on these, do not modify._
- **`libs/*`**: **Shared Utilities**. (AI, UI Components, TypeScript libs).
- **`products/*`**: **Business Logic**. (e.g., `data-cloud`, `agentic-event-processor`, `guardian`).
- **`contracts/*`**: **API Schemas**. (Protobuf, OpenAPI).

### Dependency Flow

`products` -> `libs` -> `contracts` (Strict downward flow).

### Hybrid Backend Strategy

| Feature Type    | Technology  | Framework             | Use Case                                   |
| :-------------- | :---------- | :-------------------- | :----------------------------------------- |
| **Core Domain** | **Java 21** | ActiveJ / Spring Boot | High-perf, Event Processing, Complex Logic |
| **User API**    | **Node.js** | Fastify + Prisma      | UI State, Preferences, CRUD, Real-time     |

## 3. ☕ JAVA STANDARDS (ActiveJ & Core)

### ⚡ Concurrency (ActiveJ)

- **MUST** use `ActiveJ Promise` for all async work.
- **MUST** use `ActiveJ Eventloop`.
- **NEVER** block the event loop. Use `Promise.ofBlocking` for IO.
- **NEVER** use `CompletableFuture` or `Reactor` mixed with ActiveJ.

### 🧪 Testing (MANDATORY)

- **Base Class**: ALL async tests MUST extend `EventloopTestBase` (from `libs:activej-test-utils`).
- **Execution**: Use `runPromise(() -> service.call())`.
- **Forbidden**: NEVER call `.getResult()` on a Promise (throws NPE).
- **Data**: Use `TestDataBuilders`.

**✅ CORRECT Test Pattern:**

```java
@DisplayName("My Service Tests")
class MyServiceTest extends EventloopTestBase {
    @Test
    void shouldProcessAsync() {
        // GIVEN
        MyService service = new MyService();
        // WHEN
        String result = runPromise(() -> service.processAsync("input"));
        // THEN
        assertThat(result).isEqualTo("expected");
    }
}
```

### 🧱 Core Abstractions (Do Not Bypass)

- **HTTP**: Use `com.ghatana.core.http.*` (from `libs:http-server`).
- **DB**: Use `com.ghatana.core.database.*` (from `libs:database`).
- **Metrics**: Use `com.ghatana.observability.*` (from `libs:observability`).
- **AI**: Use `com.ghatana.ai.*` (from `libs:ai-integration`).

### 📚 Documentation (Required Tags)

Every public class must have these 4 tags:

```java
/**
 * @doc.type [class|interface|record|enum]
 * @doc.purpose [One-line description]
 * @doc.layer [core|product|platform]
 * @doc.pattern [Service|Repository|ValueObject|etc]
 */
```

## 4. ⚛️ FRONTEND STANDARDS

### Tech Stack

- **State**: `Jotai` (App State), `TanStack Query` (Server State).
- **Styling**: `Tailwind CSS` only.
- **Testing**: `React Testing Library`, `Jest`.

### React Native E2E Testing

- **DO**: Mock individual screens, use `require()` for Navigator imports.
- **DON'T**: Mock the Navigator itself.
- **Reference**: `products/dcmaar/apps/guardian/apps/parent-mobile/JEST_TESTING_GUIDE.md`.

## 5. 🛠️ WORKFLOWS & COMMANDS

### Development

- **Build**: `./gradlew clean build`
- **Test**: `./gradlew test`
- **Format**: `./gradlew spotlessApply`
- **Check**: `./gradlew checkstyleMain pmdMain`

### Migration (Shared -> Core/Libs)

**CRITICAL**: `multi-agent-system` is now `agentic-event-processor`.
**CRITICAL**: `collection-entity-system` is now `data-cloud`.
**CRITICAL**: `eventcloud` is now `data-cloud/event` (package: `com.ghatana.datacloud.event`).
**CRITICAL**: `shared:*` modules are DEPRECATED.

- `shared:metrics` -> `libs:observability`
- `shared:exception` -> `libs:common-utils`
- `shared:test-utils` -> `libs:activej-test-utils`

## 6. 🎯 CRITICAL ARCHITECTURAL DECISIONS (Binding)

1.  **Unified Operator Model**: All operators MUST extend `UnifiedOperator`.
2.  **Operator Catalog**: Single registry for ALL operators.
3.  **Pipeline Builder**: Fluent API / YAML for pipelines.
4.  **Event Tailing**: Real-time push-based subscription via `data-cloud/event`.
5.  **Hybrid State**: Local (RocksDB) + Central (Redis/Dragonfly/Kvrocks).
6.  **ActiveJ Only**: No Spring Reactor/WebFlux.
7.  **Observability**: Micrometer + OpenTelemetry via `libs:observability`.
8.  **Multi-Tenancy**: Isolation at ALL layers.

## 7. 🤖 GAA FRAMEWORK STANDARDS (Generic Adaptive Agent)

### Memory System
- **Event Sourcing**: ALL memory operations MUST append events to EventLog
- **Async Operations**: Wrap EventLogStore with `Promise.ofBlocking(executor, ...)`
- **Memory Types**: Episodic, Semantic, Procedural, Preference
- **Governance**: Apply redaction/retention BEFORE persisting

### Agent Lifecycle
- **Pipeline**: PERCEIVE → REASON → ACT → CAPTURE → REFLECT
- **Base Class**: Extend `BaseAgent` from `libs:agent-framework`
- **Turn Context**: Use `AgentTurnPipeline` for all agent executions
- **Reflection**: MUST be async, fire-and-forget, never block user response

### Configuration
- **AgentDefinition**: Stable, versioned blueprint (YAML/JSON)
- **AgentInstance**: Tenant-scoped runtime config with overrides
- **Validation**: Schema + semantic + security + cost validation
- **Hot Reload**: Support config changes without downtime

### Learning System
- **Policies**: Versioned, queryable, confidence-scored procedures
- **Pattern Engine**: Use for fast policy matching (reflex layer)
- **LLM Reflection**: Batch episodes, extract patterns, synthesize policies
- **Human Review**: Flag low-confidence (<0.7) policies for approval

### Documentation Tags (GAA-specific)
```java
/**
 * @doc.type [class|interface|record|enum]
 * @doc.purpose [One-line description]
 * @doc.layer [core|product|platform]
 * @doc.pattern [Service|Repository|ValueObject|EventSourced|etc]
 * @doc.gaa.memory [episodic|semantic|procedural|preference] (if memory-related)
 * @doc.gaa.lifecycle [perceive|reason|act|capture|reflect] (if lifecycle-related)
 */
```

## 8. ✅ DEFINITION OF DONE

**Code is NOT complete until:**

1.  [ ] Compiles & Passes Tests.
2.  [ ] JavaDoc + `@doc` tags present on ALL public classes.
3.  [ ] No duplicate code found (checked `libs/java/*`).
4.  [ ] `EventloopTestBase` used for async tests.
5.  [ ] Code formatted (`spotlessApply`).
6.  [ ] GAA memory operations use event sourcing (if applicable).
7.  [ ] Async operations properly wrapped with `Promise.ofBlocking` (if applicable).
