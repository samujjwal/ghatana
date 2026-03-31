# YAPPC Onboarding — Developer

This guide walks you through everything needed to build, test, and ship changes to the YAPPC Lifecycle Service.

---

## Prerequisites

- JDK 21 (`sdk use java 21` via SDKMAN or equivalent)
- Node.js 20+ and `pnpm` (for TypeScript tooling)
- Docker + Docker Compose (for local dependencies)
- IntelliJ IDEA or VS Code with the Java extension pack

Verify your JDK:

```bash
java -version   # should print openjdk 21.*
./gradlew --version
```

---

## Project Layout

```
products/yappc/
├── core/
│   ├── domain/             # Lifecycle domain: phases, gates, transition events
│   ├── spi/                # Extension interfaces (implement these to plug in)
│   ├── yappc-shared/       # Cross-cutting value objects, error types
│   ├── framework/          # DI helpers, shared utilities
│   ├── ai/                 # LLM integration, safety filter, circuit breaker
│   ├── knowledge-graph/    # Work-item relationship graph
│   ├── agents/             # Agent execution (runtime, workflow, specialists)
│   ├── scaffold/           # Scaffold templates + API
│   ├── refactorer/         # Refactoring suggestion pipeline
│   └── services-lifecycle/ # HTTP server entry point (start here for new endpoints)
├── build.gradle.kts        # Shared subproject config: JaCoCo, Checkstyle, PMD
└── docs/                   # Architecture docs, ADRs, onboarding guides (here)
```

---

## Running Locally

### 1. Start supporting services

```bash
cd /path/to/ghatana
docker compose -f monitoring/docker-compose.yml up -d   # Prometheus + Grafana + Jaeger
docker compose -f products/yappc/docker-compose.dev.yml up -d   # Postgres
```

### 2. Build and run the lifecycle service

```bash
# Full build (tests + static analysis)
./gradlew :products:yappc:core:services-lifecycle:build

# Run the service
./gradlew :products:yappc:core:services-lifecycle:run
```

The service starts on port **8080** by default.

### 3. Health check

```bash
curl http://localhost:8080/health/readiness
# → {"status":"UP"}
```

---

## The Event Loop — What This Means For You

YAPPC uses **ActiveJ**, not Spring. This changes how you write code:

### DO

```java
// Return a Promise from async operations
public Promise<PhaseTransitionResult> advance(String tenantId, PhaseTransition transition) {
    return repository.findById(tenantId, transition.workItemId())
        .then(workItem -> gate.validate(workItem, transition))
        .then(result -> repository.save(result.nextState()))
        .map(saved -> new PhaseTransitionResult(saved));
}
```

### DO NOT

```java
// NEVER block the event loop
public PhaseTransitionResult advance(...) {
    WorkItem item = repository.findById(...).getResult();  // ← FORBIDDEN - blocks event loop
    ...
}
```

If you must call blocking I/O (rare), wrap with the approved bridge:

```java
return Promise.ofBlocking(blockingExecutor, () -> someBlockingOperation());
```

---

## Writing Tests

All async tests extend `EventloopTestBase` and use `runPromise()`:

```java
@DisplayName("PhaseGate Tests")
class PhaseGateTest extends EventloopTestBase {

    @Test
    void shouldAdvanceFromDraftToReview() {
        var gate = new DraftToReviewGate();
        var context = PhaseTransitionContext.of("tenant-1", workItem());

        PhaseGateResult result = runPromise(() -> gate.validate(context));

        assertThat(result.passed()).isTrue();
    }
}
```

Key rules:
- Extend `EventloopTestBase` (from `libs:activej-test-utils`)
- Use `runPromise(() -> yourPromise)` — never `.getResult()`
- Place tests in `src/test/java/com/ghatana/yappc/...` mirroring the source package

### Running Tests

```bash
# All tests in the module
./gradlew :products:yappc:core:services-lifecycle:test

# Single test class
./gradlew :products:yappc:core:services-lifecycle:test \
  --tests "com.ghatana.yappc.services.lifecycle.gdpr.GdprControllerTest"

# With coverage report
./gradlew :products:yappc:core:services-lifecycle:jacocoTestReport
# → open build/reports/jacoco/test/html/index.html
```

Coverage is enforced at ≥ 80% branch and ≥ 80% line. The `check` task will fail if coverage drops below this threshold.

---

## Adding a New HTTP Endpoint

1. Create a controller class in the appropriate package (e.g., `services-lifecycle/.../myfeature/MyController.java`).
2. Add `@Provides` bindings in `LifecycleServiceModule.java`.
3. Register the route in `YappcLifecycleService.java` inside `createHttpServer()`.
4. All routes pass through `TenantContextFilter` → `JwtAuthFilter` → your controller.

Template for a controller:

```java
/**
 * @doc.type class
 * @doc.purpose Handles HTTP requests for [feature].
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class MyController {

    private final MyDomainService service;
    private final ObjectMapper objectMapper;

    public MyController(MyDomainService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    public Promise<HttpResponse> handleGet(HttpRequest request) {
        String tenantId = TenantContext.requireCurrentTenantId();
        return service.doSomething(tenantId)
            .map(result -> HttpResponse.ok200()
                .withBody(objectMapper.writeValueAsBytes(result))
                .withHeader(CONTENT_TYPE, "application/json"));
    }
}
```

---

## JavaDoc Requirements

Every public class you create **must** include all four `@doc.*` tags or the build will fail:

```java
/**
 * Brief description of the class.
 *
 * @doc.type class
 * @doc.purpose One sentence explaining what this class does.
 * @doc.layer product
 * @doc.pattern Service|Controller|Repository|Agent|Filter|...
 */
```

---

## AI Module Notes

If you are working in `core/ai/`:

- `AIFallbackService` / `DefaultAIFallbackService` — the circuit breaker + retry + fallback chain. Providers are registered at startup.
- `AISafetyFilter` — always call this before returning AI output to users. Use `AISafetyFilter.Config.defaults()` for full protection.
- Prompt templates live in `src/main/resources/prompts/`. Name them `{capability}-v{N}.txt`.

---

## Static Analysis

The build enforces Checkstyle, PMD, and SpotBugs. Run checks before opening a PR:

```bash
./gradlew :products:yappc:core:services-lifecycle:check
```

Reports land in `build/reports/`. SpotBugs HTML report is the most useful for understanding security/concurrency findings.

---

## Dependency Management

All dependency versions are managed via the version catalog at `gradle/libs.versions.toml`. Do **not** hardcode versions in `build.gradle.kts` files. Reference them as `libs.something`:

```kotlin
dependencies {
    implementation(libs.bundles.activej)
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
}
```

To add a new dependency, first check if it already exists in the catalog:

```bash
grep -i "your-library" gradle/libs.versions.toml
```

If not, add it to the catalog, not directly to the build file.

---

## Key Commands Reference

| Task | Command |
|------|---------|
| Compile only | `./gradlew :products:yappc:core:services-lifecycle:compileJava` |
| Run all tests | `./gradlew :products:yappc:core:services-lifecycle:test` |
| Full check (lint + tests + coverage) | `./gradlew :products:yappc:core:services-lifecycle:check` |
| Run the service | `./gradlew :products:yappc:core:services-lifecycle:run` |
| Build all YAPPC modules | `./gradlew :products:yappc:build` |
| Coverage report | `./gradlew :products:yappc:core:services-lifecycle:jacocoTestReport` |

---

## Getting Help

- **Architecture questions**: read `docs/adr/` and the [architect onboarding guide](architect.md)
- **Build failures**: check `docs/TROUBLESHOOTING.md` (or run `./gradlew build --info 2>&1 | grep "error:"`)
- **ActiveJ usage patterns**: see `platform/java/core/src/test/` for canonical examples
- **Stuck?**: ping the YAPPC engineering channel or the service owner
