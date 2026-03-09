# YAPPC Codebase - Quick Reference & Action Items

> **Updated:** 2026-02-21 | **Version:** 2.0 (post-refactoring)

## 🏗️ Module Architecture (v2.0)

YAPPC follows a modular service architecture aligned with platform standards. All services use ActiveJ (not Spring Boot).

### Service Modules

| Module | Port | Purpose | Entry Point |
|:---|:---|:---|:---|
| `services/api` | 8080 | HTTP/gRPC endpoints, legacy bridge | `YappcApiService` |
| `services/ai` | 8081 | AI agents, model routing, vector search | `YappcAiService` |
| `services/lifecycle` | 8082 | SDLC phase management | `YappcLifecycleService` |
| `services/scaffold` | 8083 | Code generation, templates | `YappcScaffoldService` |
| `services/domain` | — | Business logic (library) | DI module |
| `services/infrastructure` | — | Data integration (library) | DI module |

### Core Libraries

| Module | Purpose |
|:---|:---|
| `core/ai` | AI capabilities (agents, router, canvas, vector, **resilience**) |
| `core/scaffold` | Scaffold schemas, adapters, packs |
| `core/lifecycle` | SDLC models and engines |
| `core/framework` | ⚠️ **DEPRECATED** — see [Migration Runbook](MIGRATION_RUNBOOK.md) |
| `libs/java/yappc-domain` | Domain models, DTOs, repositories |
| `libs/java/yappc-plugin-spi` | Plugin SPI for native plugin development |

### DI Modules (ActiveJ)

All service wiring uses ActiveJ `AbstractModule` / `ModuleBuilder`:
- `AiServiceModule` — AI router, vector service, canvas, agents
- `LifecycleServiceModule` — Phase engine, SDLC state machines
- `ScaffoldServiceModule` — Template engine, pack registry
- `InfrastructureServiceModule` — Data connectors, external integrations
- `DomainServiceModule` — Business services, repositories

### Build Commands

```bash
# Full YAPPC build (all 6 services + core)
./gradlew :products:yappc:services:api:build \
          :products:yappc:services:ai:build \
          :products:yappc:services:lifecycle:build \
          :products:yappc:services:scaffold:build --parallel

# Single service build
./gradlew :products:yappc:services:api:compileJava

# Run tests
./gradlew :products:yappc:services:api:test

# Standalone build (from products/yappc/)
cd products/yappc && make build-standalone
```

---

---

## ⚠️ FRAMEWORK DEPRECATION NOTICE

The entire `core/framework/framework-api` module (55+ classes) is **deprecated since v2.0.0**. All classes are annotated `@Deprecated(since = "2.0.0", forRemoval = true)`.

**Key migrations:**
| Old (Deprecated) | New Location |
|:---|:---|
| `framework.api.ActiveJPatterns` | `platform.core.async.ActiveJPatterns` |
| `framework.ai.AIFallbackService` | `yappc.ai.resilience.AIFallbackService` |
| `framework.audit.AuditReport/Statistics` | `core.audit.reporting.*` |
| `framework.encryption.KeyManagementService` | `platform.security.encryption.KeyManagementService` |
| `framework.security.Authentication*` | `platform.security.auth.*` |

See [Migration Runbook](MIGRATION_RUNBOOK.md) for full migration instructions.

---

## 🚨 THREE CRITICAL BLOCKERS

### 1. Refactorer Module - Not ActiveJ Compliant

**Location:** `products/yappc/core/refactorer/`  
**Issue:** Uses raw Java threads instead of ActiveJ Promises/Eventloop  
**Examples of problems:**

- `ClippyRunner.java` - should use ActiveJ async
- Tests don't extend `EventloopTestBase`
- No @doc tags on classes
- No observability integration

**What to do:**

1. Read: `libs/activej-test-utils` documentation
2. See example: `domain/service/src/test/java/DashboardServiceTest.java`
3. Convert async operations to return `Promise<T>`
4. Add tests extending `EventloopTestBase` using `runPromise()`

**Time estimate:** 40-50 hours

---

### 2. Duplicate DTO Classes - Remove Duplicates

**Location:** `libs/java/yappc-domain/src/main/java/`  
**Files with duplicates:**

```
com/ghatana/products/yappc/domain/dto/CreateFindingRequest.java      ← KEEP THIS
com/ghatana/products/yappc/domain/CreateFindingRequest.java          ← DELETE
com/ghatana/products/yappc/domain/service/impl/CreateFindingRequest.java ← DELETE

Same for CreateScanRequest.java (3 files total)
```

**Action:**

1. Keep only: `domain/dto/CreateFindingRequest.java`
2. Delete duplicates
3. Update all imports in other files
4. Add @doc tags to the kept version

**Time estimate:** 3-4 hours

---

### 3. Domain Models Are Incomplete Placeholders

**Location:** `libs/java/yappc-domain/src/main/java/com/ghatana/products/yappc/domain/model/`  
**Problem:** Models only have basic getters/setters, missing business logic

**Dashboard model is missing:**

```java
// Currently has: id, workspaceId, name
// Needs to add:
private String tenantId;
private boolean isDefault;
private String persona;
private String slug;
private List<Widget> widgets;
private Instant createdAt;
private Instant updatedAt;
// + validation
// + methods
// + @doc tags
```

**Same for:** ScanFinding, Incident, ComplianceAssessment, CloudResource, etc.

**Time estimate:** 20-25 hours

---

## ✅ Reference: How to Do Things RIGHT in YAPPC

### Writing a Service Class (Async)

**✅ CORRECT Pattern** (see `domain/service/src/test/java/DashboardServiceTest.java`):

```java
/**
 * Dashboard service for managing dashboard state and operations.
 *
 * @doc.type class
 * @doc.purpose Manage dashboard CRUD operations with multi-tenancy support
 * @doc.layer product
 * @doc.pattern Service
 */
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboardRepository;
    private final MetricsCollector metrics;

    public DashboardServiceImpl(
        DashboardRepository dashboardRepository,
        MetricsCollector metrics) {
        this.dashboardRepository = dashboardRepository;
        this.metrics = metrics;
    }

    /**
     * Create a new dashboard.
     *
     * @param tenantId tenant identifier
     * @param dashboard dashboard data
     * @return Promise<Dashboard> created dashboard
     */
    public Promise<Dashboard> createDashboard(String tenantId, Dashboard dashboard) {
        // Use ActiveJ Promise, not CompletableFuture!
        return dashboardRepository.save(dashboard)
            .then(saved -> {
                metrics.recordMetric("dashboard.created");
                return Promise.of(saved);
            });
    }
}
```

### Writing a Test Class (Async)

**✅ CORRECT Pattern** (see `domain/service/src/test/java/DashboardServiceTest.java`):

```java
/**
 * Unit tests for DashboardService.
 *
 * @doc.type class
 * @doc.purpose Test dashboard service business logic
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("Dashboard Service Tests")
class DashboardServiceTest extends EventloopTestBase {

    private DashboardRepository dashboardRepository;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardRepository = mock(DashboardRepository.class);
        dashboardService = new DashboardServiceImpl(
            dashboardRepository,
            NoopMetricsCollector.getInstance()
        );
    }

    @Test
    @DisplayName("Should create dashboard successfully")
    void shouldCreateDashboardSuccessfully() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        Dashboard dashboard = new Dashboard();
        dashboard.setTenantId("tenant-123");
        dashboard.setWorkspaceId(workspaceId);

        Dashboard savedDashboard = new Dashboard();
        savedDashboard.setId(UUID.randomUUID());
        when(dashboardRepository.save(any(Dashboard.class)))
            .thenReturn(Promise.of(savedDashboard));

        // WHEN
        Dashboard result = runPromise(() ->
            dashboardService.createDashboard("tenant-123", dashboard)
        );

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        verify(dashboardRepository).save(any(Dashboard.class));
    }
}
```

### Writing a Domain Model Class

**✅ CORRECT Pattern**:

```java
/**
 * Dashboard domain model.
 *
 * @doc.type class
 * @doc.purpose Represent a user dashboard with widgets and configuration
 * @doc.layer product
 * @doc.pattern Domain Model (Entity)
 */
public class Dashboard {

    private UUID id;
    private String tenantId;
    private UUID workspaceId;
    private String name;
    private String persona;
    private String slug;
    private boolean isDefault;
    private List<Widget> widgets;
    private Instant createdAt;
    private Instant updatedAt;

    // Constructor
    public Dashboard() {}

    // Getters/Setters (all required)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    // ... rest of getters/setters

    // Business logic methods
    /**
     * Validate dashboard before persistence.
     *
     * @throws IllegalArgumentException if invalid
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Dashboard name is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
    }
}
```

### Adding @doc Tags to All Public Classes

**Template to use:**

```java
/**
 * [Brief description of what this class does]
 *
 * [More detailed explanation if needed]
 *
 * @doc.type class          // or: interface, record, enum, exception
 * @doc.purpose [One-line business purpose]
 * @doc.layer core          // or: product, platform
 * @doc.pattern Service     // or: Repository, DTO, ValueObject, etc
 */
public class YourClass {
    // implementation
}
```

### Dependency Injection Pattern (ActiveJ)

**✅ Use constructor injection:**

```java
public class DashboardServiceImpl implements DashboardService {
    private final DashboardRepository repository;
    private final MetricsCollector metrics;

    public DashboardServiceImpl(
        DashboardRepository repository,
        MetricsCollector metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }
}
```

---

## 📋 Checklist for Code Reviews

Before merging any code to main:

- [ ] All public classes have @doc tags (type, purpose, layer, pattern)
- [ ] All async methods return `Promise<T>` (not `CompletableFuture`)
- [ ] All async tests extend `EventloopTestBase` and use `runPromise()`
- [ ] No `.getResult()` or `.get()` calls on Promises
- [ ] No direct imports from `io.activej.http.*` (use `libs:http-server`)
- [ ] No direct Micrometer imports (use `libs:observability`)
- [ ] No `any` types in TypeScript
- [ ] No duplicate classes (check against libs/java/\*)
- [ ] Domain models are complete (not placeholders)
- [ ] All services have MetricsCollector integration
- [ ] Unit tests exist with reasonable coverage
- [ ] DESIGN_ARCHITECTURE.md exists for the module

---

## 🔍 How to Find Examples

### Good Examples in YAPPC:

- **Service Entry Point:** `services/api/src/main/java/.../YappcApiService.java`
- **DI Module:** `services/ai/src/main/java/.../AiServiceModule.java`
- **AI Resilience:** `core/ai/src/main/java/.../resilience/AIFallbackService.java`
- **Testing:** `services/api/src/test/java/.../ServiceIntegrationTest.java`
- **@doc tags:** `core/ai/src/main/java/.../service/YAPPCAIService.java`
- **EventloopTestBase:** `services/api/src/test/java/` (integration tests)

### Good Examples in Other Products:

- Look at `libs/java/*` for abstraction patterns
- Look at `libs/activej-test-utils` for testing patterns
- Look at `libs/database` for persistence patterns
- Look at `libs/observability` for metrics patterns

---

## 🚀 Quick Start: Adding New Code to YAPPC

### 1. Create Domain Model

- [ ] Add class to `libs/java/yappc-domain/src/main/java/com/ghatana/products/yappc/domain/model/`
- [ ] Implement complete properties and validation
- [ ] Add @doc tags
- [ ] Add JavaDoc with proper documentation

### 2. Create Repository Interface

- [ ] Extend `TenantAwareRepository<T, ID>`
- [ ] Add custom query methods
- [ ] Add to `libs/java/yappc-domain/src/main/java/com/ghatana/products/yappc/domain/repository/`
- [ ] Add @doc tags and documentation

### 3. Create Service Implementation

- [ ] Implement business logic with async methods
- [ ] Return `Promise<T>` for all async operations
- [ ] Inject `MetricsCollector` and emit metrics
- [ ] Use `libs:http-server` and `libs:observability` abstractions
- [ ] Add to `domain/service/src/main/java/`

### 4. Create Tests

- [ ] Extend `EventloopTestBase`
- [ ] Use `runPromise()` for async operations
- [ ] Mock repositories with `.thenReturn(Promise.of(...))`
- [ ] Add to `domain/service/src/test/java/`

### 5. Add Documentation

- [ ] Add DESIGN_ARCHITECTURE.md if new module
- [ ] Add @doc tags to all public classes
- [ ] Document dependencies and design constraints

---

## 🐛 Common Mistakes to AVOID

### ❌ DON'T:

```java
// ❌ WRONG: Using CompletableFuture instead of Promise
public CompletableFuture<Dashboard> create(Dashboard d) { }

// ❌ WRONG: Calling .getResult() on Promise
String result = promise.getResult();

// ❌ WRONG: Direct Micrometer import
import io.micrometer.core.instrument.MeterRegistry;

// ❌ WRONG: Direct ActiveJ HTTP import
import io.activej.http.HttpServer;

// ❌ WRONG: Generic Object type
public Object getData() { }

// ❌ WRONG: Test not extending EventloopTestBase
@Test void myTest() { service.async().get(); }

// ❌ WRONG: No @doc tags
public class MyService { }

// ❌ WRONG: Duplicate class definition
// CreateFindingRequest in 3 locations
```

### ✅ DO:

```java
// ✅ CORRECT: Using Promise for async
public Promise<Dashboard> create(Dashboard d) { }

// ✅ CORRECT: Using runPromise() in tests
Dashboard result = runPromise(() -> service.create(d));

// ✅ CORRECT: Using observability abstraction
import com.ghatana.observability.MetricsCollector;

// ✅ CORRECT: Using HTTP abstraction
import com.ghatana.core.http.server.HttpServerBootstrap;

// ✅ CORRECT: Specific generic type
public <T> T getData(Class<T> type) { }

// ✅ CORRECT: Test extending EventloopTestBase
@Test void myTest() extends EventloopTestBase {
    String result = runPromise(() -> service.async());
}

// ✅ CORRECT: All classes have @doc tags
/**
 * @doc.type class
 * @doc.purpose ...
 * @doc.layer product
 * @doc.pattern Service
 */
public class MyService { }

// ✅ CORRECT: Single definition
// CreateFindingRequest only in domain/dto/
```

---

## 📞 Getting Help

- **Questions about ActiveJ:** See `libs/activej-test-utils` or `libs/activej-runtime`
- **Questions about testing:** See `domain/service/src/test/java/DashboardServiceTest.java`
- **Questions about @doc tags:** See `app-creator/backend/src/main/java/.../FigmaClient.java`
- **Questions about observability:** See `libs/observability`
- **Questions about HTTP:** See `libs/http-server`
- **Questions about architecture:** See `ANALYSIS_REPORT.md` and `CODEBASE_ANALYSIS.json`

---

**Last Updated:** February 21, 2026  
**Version:** 2.0 (post-refactoring)
