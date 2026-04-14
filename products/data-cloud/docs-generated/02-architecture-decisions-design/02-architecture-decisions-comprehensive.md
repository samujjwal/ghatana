# Data Cloud Comprehensive Architecture Decisions

**Document ID:** DC-ARCH-DEC-001-ENHANCED  
**Version:** 2.0  
**Date:** 2026-04-12  
**Evidence Base:** 12 ADRs + Architecture Documentation Suite

---

## Executive Summary

Data Cloud's architecture is governed by **12 Architecture Decision Records (ADRs)** that define its runtime foundation, storage model, security posture, plugin extensibility, and module boundaries. This document consolidates all architectural decisions with visual diagrams, dependency graphs, and implementation guidance.

### Decision Overview

```mermaid
flowchart TB
    subgraph Foundation["Foundation Decisions"]
        F1["ADR-004: ActiveJ<br/>Async Framework"]
        F2["ADR-003: Four-Tier<br/>Storage"]
        F3["ADR-008: SPI with<br/>ServiceLoader"]
    end

    subgraph Security["Security Decisions"]
        S1["ADR-005: Multi-Tenant<br/>Isolation"]
        S2["ADR-019: Auth Gateway<br/>Boundary"]
    end

    subgraph Boundaries["Boundary Decisions"]
        B1["ADR-DC-001: Module<br/>Ownership"]
        B2["ADR-013: Shared<br/>Services"]
        B3["ADR-012: AEP<br/>Gateway"]
    end

    subgraph Data["Data Decisions"]
        D1["ADR-010: Flyway<br/>Migrations"]
        D2["ADR-006: Checkpoint<br/>Recovery"]
    end

    subgraph Architecture["Architecture Decisions"]
        A1["ADR-015: Domain<br/>Interface Extraction"]
        A2["ADR-014: Consumer<br/>Audit"]
    end

    F1 --> F3
    F2 --> F3
    F1 --> A1
    F3 --> A1
    S1 --> B1
    B2 --> B1
    B3 --> B1
```

---

## 1. Runtime Foundation (ADR-004)

### Decision: ActiveJ as Core Async and DI Framework

**Status**: Accepted  
**Date**: 2026-01-10  
**Scope**: All Data Cloud async operations

### Architecture

```mermaid
flowchart TB
    subgraph ActiveJ["ActiveJ 6.0 Stack"]
        Promise["Promise<T><br/>Universal async"]
        DI["DI Module<br/>@Provides wiring"]
        EventLoop["EventLoop<br/>Non-blocking I/O"]
        HTTP["AsyncServlet<br/>HTTP endpoints"]
    end

    subgraph DataCloud["Data Cloud Runtime"]
        Client["DataCloudClient<br/>Promise-based"]
        Handlers["HTTP Handlers<br/>Async handlers"]
        Stores["Entity/Event Stores<br/>Async storage"]
    end

    ActiveJ --> DataCloud
```

### Key Patterns

```java
// Promise composition pattern
public Promise<Entity> saveEntity(Entity entity) {
    return validate(entity)
        .map(this::enrichWithMetadata)
        .then(e -> store.save(e))
        .whenComplete((result, error) -> {
            if (error != null) {
                auditLog.recordFailure(error);
            }
        });
}

// Testing pattern
@Test
void shouldSaveEntity() {
    Promise<Entity> promise = client.saveEntity(testEntity);
    Entity result = promise.getResult(); // Blocks for test
    assertThat(result).isNotNull();
}
```

### Consequences

| Aspect       | Impact                                                                            |
| ------------ | --------------------------------------------------------------------------------- |
| **Positive** | Single async primitive, lightweight DI, high performance                          |
| **Negative** | Team must learn Promise patterns, thread-local context requires explicit transfer |
| **Risk**     | `Promise.ofException(e).getResult()` returns null (testing pitfall)               |

---

## 2. Storage Architecture (ADR-003)

### Decision: Four-Tier Event-Cloud Storage with Automatic Lifecycle

**Status**: Accepted  
**Date**: 2026-01-18  
**Scope**: All Data Cloud persistence

### Storage Tier Model

```mermaid
flowchart LR
    subgraph Tiers["Storage Tiers"]
        Hot["🔥 HOT<br/>Redis/Dragonfly<br/><1ms<br/>Minutes-Hours"]
        Warm["🌡️ WARM<br/>PostgreSQL<br/><10ms<br/>Days-Weeks"]
        Cool["❄️ COOL<br/>Apache Iceberg<br/><100ms<br/>Months"]
        Cold["🧊 COLD<br/>S3/Parquet<br/>Seconds<br/>Years"]
    end

    subgraph Flow["Data Flow"]
        Ingest["Data Ingest"]
        Query["Query Request"]
    end

    Ingest --> Hot
    Hot --> Warm
    Warm --> Cool
    Cool --> Cold
    Query --> Tiers
```

### Tier Configuration

| Tier     | Backend         | Latency | Retention     | Use Case                           |
| -------- | --------------- | ------- | ------------- | ---------------------------------- |
| **HOT**  | Redis/Dragonfly | <1ms    | Minutes-Hours | Real-time queries, active sessions |
| **WARM** | PostgreSQL      | <10ms   | Days-Weeks    | Entity CRUD, recent events         |
| **COOL** | Apache Iceberg  | <100ms  | Months        | Analytics, historical analysis     |
| **COLD** | S3/Parquet      | Seconds | Years         | Archive, compliance                |

### Implementation

```java
// Storage tier enum (enforced by Flyway)
public enum StorageTier {
    HOT, WARM, COOL, COLD
}

// Unified client API
public interface DataCloudClient {
    Promise<Entity> getEntity(String tenantId, String collection, String id);
    // Backend transparently queries appropriate tier
}
```

---

## 3. Plugin Extensibility (ADR-008)

### Decision: Data-Cloud SPI with ServiceLoader Discovery

**Status**: Accepted  
**Date**: 2026-01-18  
**Scope**: Storage backends, plugins

### SPI Architecture

```mermaid
flowchart TB
    subgraph SPI["SPI Layer (13 interfaces)"]
        EntityStore["EntityStore<br/>CRUD operations"]
        EventLogStore["EventLogStore<br/>Append-only log"]
        StorageConnector["StorageConnector<br/>Backend bridge"]
        StoragePlugin["StoragePlugin<br/>Lifecycle"]
        EncryptionService["EncryptionService<br/>At-rest encryption"]
        AuditLogger["AuditLogger<br/>Audit trail"]
        TenantContext["TenantContext<br/>Isolation context"]
    end

    subgraph Capabilities["Capability Interfaces"]
        Aggregation["AggregationCapability"]
        Similarity["SimilaritySearchCapability"]
        Streaming["StreamingCapability"]
        Transaction["TransactionCapability"]
    end

    subgraph Implementations["Built-in Implementations"]
        InMemory["InMemory*Store<br/>Fallback"]
        PostgreSQL["PostgreSQL*Store<br/>Production"]
        Kafka["Kafka*Store<br/>Event streaming"]
    end

    SPI --> Capabilities
    SPI --> Implementations
```

### Discovery Pattern

```java
// ServiceLoader discovery
public static DataCloudClient create() {
    ServiceLoader<EntityStore> loader = ServiceLoader.load(EntityStore.class);
    EntityStore store = loader.findFirst()
        .orElse(new InMemoryEntityStore()); // Always available fallback

    return new DataCloudClient(store);
}

// Testing entry point
DataCloudClient client = DataCloud.forTesting(); // In-memory, no external deps
```

### Capability Detection

```java
// Feature detection via capability interfaces
if (store instanceof TransactionCapability tx) {
    tx.beginTransaction();
    try {
        tx.save(entity);
        tx.commit();
    } catch (Exception e) {
        tx.rollback();
        throw e;
    }
}
```

---

## 4. Multi-Tenant Isolation (ADR-005)

### Decision: Thread-Local TenantContext with Principal Value Object

**Status**: Accepted  
**Date**: 2026-01-25  
**Scope**: All multi-tenant operations

### Isolation Architecture

```mermaid
flowchart TB
    subgraph Entry["Request Entry"]
        Filter["Security Filter<br/>X-Tenant-ID header"]
        Principal["Principal<br/>name + roles + tenantId"]
    end

    subgraph Context["Context Propagation"]
        ThreadLocal["ThreadLocal<Principal><br/>ThreadLocal<String>"]
        Scope["AutoCloseable Scope<br/>try-with-resources"]
    end

    subgraph Enforcement["Data Layer Enforcement"]
        DB["Database<br/>tenant-aware filtering / enforcement path"]
        Audit["Audit Log<br/>tenant-scoped"]
        Events["Event Store<br/>tenant-scoped topics"]
    end

    Entry --> Context
    Context --> Enforcement
```

### Implementation Pattern

```java
// Request entry point
public Promise<Response> handle(HttpRequest request) {
    Principal principal = extractPrincipal(request);

    try (AutoCloseable scope = TenantContext.scope(principal)) {
        // All downstream code implicitly knows the tenant
        String tenantId = TenantContext.getCurrentTenantId();
        return processRequest(request);
    }
    // Context automatically restored on scope exit
}

// Data access layer
public class EntityRepository {
    public List<Entity> findByCollection(String collection) {
        String tenantId = TenantContext.getCurrentTenantId();
        return jdbc.query(
            "SELECT * FROM entities WHERE tenant_id = ? AND collection = ?",
            tenantId, collection
        );
    }
}
```

### Constraints

| Constraint               | Handling                                                    |
| ------------------------ | ----------------------------------------------------------- |
| Thread pool context loss | Explicit capture and re-scope required                      |
| Default tenant           | `"default-tenant"` for development/single-tenant            |
| Cross-thread transfer    | `TenantContext.capture()` → `TenantContext.scope(captured)` |

---

## 5. Module Ownership (ADR-DC-001)

### Decision: Clear Ownership Matrix with Strict Downward Dependencies

**Status**: Accepted  
**Date**: 2026-01-19  
**Scope**: All Data Cloud modules

### Module Dependency Graph

```mermaid
flowchart TB
    subgraph Frontend["Frontend"]
        UI["ui<br/>React 19"]
    end

    subgraph API["API Layer"]
        SDK["sdk<br/>OpenAPI generated"]
    end

    subgraph Runtime["Runtime"]
        Launcher["launcher<br/>HTTP/gRPC bootstrap"]
    end

    subgraph Core["Core Modules"]
        PL["platform-launcher<br/>237 files"]
        SPI["spi<br/>Public contracts"]
    end

    subgraph Workers["Background Workers"]
        FS["feature-store-ingest<br/>Event tailing"]
        AR["agent-registry<br/>Metadata persistence"]
    end

    UI --> SDK
    SDK --> Launcher
    Launcher --> PL
    Launcher --> SPI
    PL --> SPI
    FS --> PL
    AR --> PL
```

### Ownership Matrix

| Module                 | Domain                      | Owner               | Public Contract                            |
| ---------------------- | --------------------------- | ------------------- | ------------------------------------------ |
| `platform-launcher`    | Runtime services, DI wiring | Data Cloud Platform | `EmbeddedDataCloudClient`, `EventLogStore` |
| `spi`                  | Storage provider SPI        | Data Cloud Platform | `StorageProvider`, `IndexProvider`         |
| `launcher`             | Deployable bootstrap        | Data Cloud Runtime  | `DataCloudLauncher`                        |
| `agent-registry`       | Agent metadata persistence  | Data Cloud AI       | `AgentRegistry`, `AgentDefinition`         |
| `feature-store-ingest` | ML feature ingestion        | Data Cloud AI       | `FeatureIngestService`                     |
| `sdk`                  | External client SDK         | Data Cloud SDK      | `DataCloudClient`                          |
| `ui`                   | React UI components         | Data Cloud Frontend | npm package                                |

### Forbidden Dependencies

```mermaid
flowchart LR
    subgraph Forbidden["❌ Forbidden"]
        F1["platform-launcher → launcher.*"]
        F2["sdk → launcher.*"]
        F3["data-cloud → aep.*"]
        F4["product → virtual-org"]
    end

    subgraph Allowed["✅ Allowed"]
        A1["launcher → platform-launcher"]
        A2["platform-launcher → spi"]
        A3["aep → data-cloud:spi"]
    end
```

---

## 6. AEP Boundary (ADR-012)

### Decision: Preserve AEP Gateway for Agentic Processing

**Status**: Accepted  
**Date**: 2026-02-15  
**Scope**: Data Cloud ↔ AEP integration

### Integration Pattern

```mermaid
sequenceDiagram
    participant DC as Data Cloud
    participant Events as Event Cloud
    participant AEP as AEP Gateway

    DC->>Events: Emit agentic intents<br/>domain events
    AEP->>Events: Subscribe to requests
    AEP->>AEP: Planning, tool use<br/>multi-step orchestration
    AEP->>DC: Write results, checkpoints<br/>telemetry, memory
```

### Responsibility Split

| Concern                | Data Cloud                   | AEP                              |
| ---------------------- | ---------------------------- | -------------------------------- |
| **Persistence**        | ✅ Entity/event storage      | ✅ Checkpoint, memory, telemetry |
| **Event streaming**    | ✅ Event cloud               | ✅ Consumes events               |
| **Agentic processing** | ❌ Not in scope              | ✅ Planning, orchestration       |
| **AI/ML features**     | ✅ Embedded ranking, anomaly | ✅ Agent reasoning               |

---

## 7. Schema Management (ADR-010)

### Decision: Flyway for Database Migrations

**Status**: Accepted  
**Date**: 2026-01-20  
**Scope**: All database schema changes

### Migration Architecture

```mermaid
flowchart LR
    subgraph Flyway["Flyway Migration"]
        V001["V001__events_table.sql"]
        V004["V004__collections_metadata.sql"]
        V010["V010__entity_relations.sql"]
        V011["V011__tenant_isolation.sql"]
    end

    subgraph Schema["Database Schema"]
        Events["events table"]
        Meta["meta_collections"]
        Relations["entity_relations"]
        Tenant["tenant_id columns"]
    end

    Flyway --> Schema
```

### Key Migrations

| Version | Description          | Impact                   |
| ------- | -------------------- | ------------------------ |
| V001    | Events table         | Core event storage       |
| V004    | Collections metadata | Schema management        |
| V010    | Entity relations     | Graph relationships      |
| V011    | Tenant isolation     | Multi-tenant enforcement |

---

## 8. Domain Interface Extraction (ADR-015)

### Decision: Extract Domain Interfaces to Break Circular Dependencies

**Status**: Accepted  
**Date**: 2026-03-25  
**Scope**: platform/java/core ↔ platform/java/domain

### Clean Architecture Flow

```mermaid
flowchart TB
    subgraph Layer1["contracts"]
        C["No dependencies"]
    end

    subgraph Layer2["domain"]
        D["Domain interfaces<br/>DomainEvent<br/>OperatorContract"]
    end

    subgraph Layer3["core"]
        Core["Implements domain interfaces<br/>AbstractOperator implements OperatorContract"]
    end

    subgraph Layer4["higher"]
        H["http, security<br/>observability"]
    end

    Layer1 --> Layer2
    Layer2 --> Layer3
    Layer3 --> Layer4
```

### Migration Phases

| Phase | Action                       | Files                |
| ----- | ---------------------------- | -------------------- |
| 1     | Introduce interfaces         | 3 new interfaces     |
| 2     | Migrate callsites            | 463+ files           |
| 3     | Remove bidirectional imports | Enforce via ArchUnit |
| 4     | Flip build dependency        | Breaking change      |

---

## 9. Decision Cross-Reference Matrix

| Decision                   | Affects               | Related Decisions                                    |
| -------------------------- | --------------------- | ---------------------------------------------------- |
| ADR-004 (ActiveJ)          | All async code        | ADR-008 (SPI uses Promise), ADR-015 (async patterns) |
| ADR-003 (Four-Tier)        | Storage backends      | ADR-008 (SPI implementations)                        |
| ADR-008 (SPI)              | Plugin architecture   | ADR-003 (tier implementations), ADR-004 (Promise)    |
| ADR-005 (Multi-Tenant)     | Security, data access | ADR-DC-001 (handler allocation), ADR-019 (auth)      |
| ADR-DC-001 (Ownership)     | Module structure      | ADR-013 (shared services), ADR-012 (AEP boundary)    |
| ADR-013 (Shared Services)  | feature-store-ingest  | ADR-DC-001 (module placement)                        |
| ADR-012 (AEP Gateway)      | Integration pattern   | ADR-DC-001 (agent-registry)                          |
| ADR-010 (Flyway)           | Schema evolution      | All data storage                                     |
| ADR-015 (Domain Interface) | Clean architecture    | ADR-004 (async patterns)                             |
| ADR-019 (Auth Gateway)     | Security boundary     | ADR-005 (tenant isolation)                           |

---

## 10. Implementation Checklist

### For New Features

- [ ] Does it respect the module ownership matrix?
- [ ] Does it use Promise for async operations?
- [ ] Does it properly handle TenantContext?
- [ ] Does it use the SPI for extensibility?
- [ ] Does it include Flyway migrations if needed?
- [ ] Does it update the OpenAPI spec?

### For Code Review

- [ ] No forbidden dependencies introduced
- [ ] No circular dependencies created
- [ ] ADR patterns followed consistently
- [ ] Tests use `DataCloud.forTesting()` where appropriate

---

## 11. References

### ADR Documents

- [ADR-DC-001: Module Ownership](./adr-dc-001-module-ownership.md)
- [ADR-Index: All ADRs](./00-adr-index.md)

### Related Documentation

- [System Architecture](./01-system-architecture.md)
- [Technical Overview](../04-technical-docs-stack-caveats-guidance/01-technical-overview.md)
- [Engineering Caveats](../04-technical-docs-stack-caveats-guidance/03-engineering-caveats.md)

### Source ADRs (Platform Level)

- `/docs/adr/ADR-003-four-tier-event-cloud.md`
- `/docs/adr/ADR-004-activej-framework.md`
- `/docs/adr/ADR-005-multi-tenant-isolation.md`
- `/docs/adr/ADR-008-datacloud-spi.md`
- `/docs/adr/ADR-012-keep-aep-gateway.md`
- `/docs/adr/ADR-013-shared-services-ownership.md`
- `/docs/adr/ADR-015-domain-interface-extraction.md`
- `/docs/adr/ADR-019-auth-gateway-security-gateway-boundary.md`

---

_This comprehensive architecture decisions document consolidates all ADRs with visual diagrams and implementation guidance. Last updated: April 12, 2026._
