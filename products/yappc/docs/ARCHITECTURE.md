# YAPPC Architecture

**Status:** Active  
**Last Updated:** 2026-01-27  
**Owner:** Architecture Team

---

## Overview

YAPPC is a **microservices-based AI platform** built on the Ghatana Platform Standards with:
- **Domain-Driven Design** for clear bounded contexts
- **ActiveJ async runtime** for high-performance I/O
- **Data-Cloud** as canonical persistence layer
- **Multi-tenancy** with strict isolation
- **Event-driven** for cross-module communication

---

## System Architecture

### High-Level Layers

```
┌─────────────────────────────────────────────────────────────┐
│                      API Layer (api/)                        │
│              HTTP Endpoints & gRPC Services                  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (domain/)                    │
│         Business Logic & Domain Services                     │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│Infrastructure│ │   AI Layer   │ │  Core Layer  │
│  (datacloud) │ │    (ai/)     │ │   (core/)    │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       └────────────────┼────────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │   Data-Cloud Storage Layer    │
        │  (Canonical Data Layer)       │
        └───────────────────────────────┘
```

### Module Structure

```
yappc/
├── api/                          # API Layer
│   ├── http/                     # REST endpoints (ActiveJ HTTP)
│   └── grpc/                     # gRPC services
│
├── domain/                       # Domain Layer
│   ├── model/                    # Domain entities
│   ├── service/                  # Business logic
│   ├── repository/               # Repository interfaces
│   └── event/                    # Domain events
│
├── infrastructure/               # Infrastructure Layer
│   ├── datacloud/                # Data-Cloud integration
│   │   ├── mappers/              # Domain ↔ Entity mappings
│   │   ├── adapters/             # Repository implementations
│   │   └── config/               # Data-Cloud configuration
│   ├── security/                 # Authentication/Authorization
│   ├── observability/            # Metrics, logging, tracing
│   └── messaging/                # Event bus
│
├── ai/                           # AI Layer
│   ├── agents/                   # AI agent implementations
│   ├── workflows/                # Multi-agent workflows
│   ├── vector/                   # Vector search
│   └── llm/                      # LLM integrations
│
├── core/                         # Core Platform Layer
│   ├── kg/                       # Knowledge Graph
│   │   ├── ingestion/            # KG data ingestion
│   │   ├── query/                # KG querying
│   │   └── core/                 # KG engine
│   ├── scaffold/                 # Project scaffolding
│   │   ├── templates/            # Project templates
│   │   ├── generator/            # Code generation
│   │   └── api/                  # Scaffold API
│   └── refactorer/               # Code refactoring
│       ├── analyzer/             # Code analysis
│       ├── transformer/          # Code transformation
│       └── validator/            # Refactoring validation
│
├── app-creator/                  # Frontend Layer
│   ├── apps/                     # Next.js applications
│   │   └── web/                  # Main YAPPC UI
│   └── libs/                     # Shared React components
│
├── backend/                      # Node.js Backend (User Services)
│   ├── api/                      # Express/Fastify routes
│   └── services/                 # User preferences, UI state
│
├── libs/                         # Shared Libraries
│   └── java/yappc-domain/        # Shared domain models
│       ├── model/                # Common DTOs
│       └── enums/                # Shared enums
│
└── config/                       # Configuration
    ├── application.yml           # Application config
    └── datacloud.yml             # Data-Cloud config
```

---

## Technology Stack

### Backend

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Runtime** | Java 21 | Modern Java features, virtual threads |
| **Async I/O** | ActiveJ 6.0 | High-performance non-blocking I/O |
| **Persistence** | Data-Cloud | Canonical data layer, multi-model storage |
| **API** | ActiveJ HTTP, gRPC | REST and RPC endpoints |
| **Observability** | Micrometer, OpenTelemetry | Metrics, traces, logs |
| **AI/ML** | OpenAI, Anthropic, Ollama | LLM integrations |
| **Vector Store** | Pinecone/Qdrant | Semantic search |
| **Build** | Gradle 9.2.1 | Multi-module builds |

### Frontend

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Framework** | React 18, Next.js 14 | Modern React with SSR |
| **Language** | TypeScript 5 | Type-safe development |
| **Styling** | Tailwind CSS | Utility-first CSS |
| **Canvas** | ReactFlow | Visual graph editor |
| **State** | Jotai, TanStack Query | Client state, server state |
| **Build** | Turbo, Vite | Fast builds |

### Infrastructure

| Service | Technology | Purpose |
|---------|------------|---------|
| **Cache** | Redis/Dragonfly | Session cache, rate limiting |
| **Database** | PostgreSQL | Relational data |
| **Analytics** | ClickHouse | Time-series data |
| **Search** | Elasticsearch | Full-text search |
| **Message Queue** | RabbitMQ/Kafka | Async messaging |
| **Container** | Docker | Containerization |
| **Orchestration** | Kubernetes | Container orchestration |

---

## Design Principles

### 1. Domain-Driven Design (DDD)

**Bounded Contexts:**
- Each module represents a bounded context
- Clear ubiquitous language within each context
- Anti-corruption layers at boundaries

**Entities & Value Objects:**
- Domain entities in `domain/model/`
- Value objects are immutable
- Repository pattern for persistence

### 2. Async-First Architecture

**ActiveJ Promise:**
```java
public Promise<Project> createProject(CreateProjectRequest request) {
    return validateRequest(request)
        .then(validated -> repositor y.save(validated))
        .then(saved -> publishEvent(saved))
        .then(project -> Promise.of(project));
}
```

**No Blocking:**
- All I/O operations return `Promise<T>`
- Use `Promise.ofBlocking()` for unavoidable blocking
- EventloopTestBase for all async tests

### 3. Data-Cloud as Single Source of Truth

**Repository Pattern:**
```java
public interface ProjectRepository {
    Promise<Project> findById(String id);
    Promise<List<Project>> findByTenant(String tenantId);
    Promise<Project> save(Project project);
}
```

**Implementation:**
```java
public class DataCloudProjectRepository implements ProjectRepository {
    private final EntityStore entityStore;
    private final ProjectMapper mapper;
    
    @Override
    public Promise<Project> findById(String id) {
        return entityStore.get("Project", id)
            .map(entity -> mapper.toDomain(entity));
    }
}
```

### 4. Multi-Tenancy

**Tenant Isolation:**
- Every request carries tenant context
- `TenantContext.getCurrentTenantId()` for implicit tenant
- Row-level security in Data-Cloud
- Separate vector collections per tenant

**Security:**
```java
@TenantIsolated
public class ProjectService {
    public Promise<Project> getProject(String projectId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return repository.findByIdAndTenant(projectId, tenantId);
    }
}
```

### 5. Event-Driven Communication

**Domain Events:**
```java
@DomainEvent
public record ProjectCreated(
    String projectId,
    String tenantId,
    String name,
    Instant createdAt
) {}
```

**Event Publishing:**
```java
public Promise<Void> publishEvent(DomainEvent event) {
    return eventBus.publish(event.topic(), event);
}
```

---

## Data Flow Patterns

### 1. Query Flow (Read)

```
User Request
    ↓
API Layer (HTTP/gRPC)
    ↓
Domain Service
    ↓
Repository Interface
    ↓
Data-Cloud Adapter
    ↓
Data-Cloud Entity Store
    ↓
Underlying Storage (Postgres/Redis)
```

### 2. Command Flow (Write)

```
User Command
    ↓
API Layer (validation)
    ↓
Domain Service (business logic)
    ↓
Domain Entity (state change)
    ↓
Repository (persistence)
    ↓
Data-Cloud Entity Store
    ↓
Underlying Storage
    ↓
Domain Event Published
    ↓
Event Handlers (async)
```

### 3. AI Workflow Flow

```
User Prompt
    ↓
AI Agent (Copilot)
    ↓
LLM API (OpenAI/Anthropic/Ollama)
    ↓
Response Parsing
    ↓
Code Generation
    ↓
Validation & Testing
    ↓
Storage in Data-Cloud
    ↓
Event: CodeGenerated
```

---

## Security Architecture

### Authentication

- **JWT-based** authentication
- **OAuth 2.0** for third-party integrations
- **API Keys** for programmatic access

### Authorization

- **Role-Based Access Control (RBAC)** - User, Admin, Owner roles
- **Resource-Based** - Permission checks at resource level
- **Tenant-Scoped** - All resources scoped to tenant

### Tenant Isolation

```java
// Implicit tenant context
public Promise<List<Project>> listProjects() {
    String tenantId = TenantContext.getCurrentTenantId();
    return repository.findByTenant(tenantId);
}

// Explicit tenant validation
public Promise<Project> getProject(String projectId) {
    return repository.findById(projectId)
        .then(project -> {
            enforcer.validateTenantAccess(project.getTenantId());
            return Promise.of(project);
        });
}
```

---

## Observability

### Metrics

- **Micrometer** for application metrics
- **JVM metrics** - Memory, GC, threads
- **Business metrics** - Projects created, AI calls, etc.
- **Custom metrics** via `@Timed`, `@Counted`

### Distributed Tracing

- **OpenTelemetry** for trace collection
- **Trace propagation** across services
- **Span attributes** for context

### Logging

- **Structured logging** with SLF4J + Logback
- **Correlation IDs** for request tracking
- **Tenant ID** in all log statements
- **Log levels** configurable per module

---

## Performance Targets

| Metric | Target | Current |
|--------|--------|---------|
| API Response Time (p95) | <200ms | ~150ms |
| API Response Time (p99) | <500ms | ~380ms |
| Throughput | >1000 req/s | ~850 req/s |
| Database Query (p95) | <50ms | ~35ms |
| AI Generation (p95) | <3s | ~2.5s |
| Memory Usage | <2GB | ~1.5GB |
| CPU Usage (avg) | <50% | ~35% |

---

## Deployment Architecture

### Development

```
Developer Laptop
├── Java Backend (localhost:8080)
├── React Frontend (localhost:3000)
├── Redis (localhost:6379)
├── PostgreSQL (localhost:5432)
└── Ollama (localhost:11434)
```

### Production (Kubernetes)

```
Load Balancer (Ingress)
    ↓
┌──────────────────────────────────┐
│  YAPPC Backend (3 replicas)      │
│  - Java + ActiveJ                │
│  - Resource: 2 CPU, 4GB RAM      │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│  Data-Cloud Service              │
│  - Shared platform service       │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│  Backing Services                │
│  - PostgreSQL (RDS)              │
│  - Redis (ElastiCache)           │
│  - ClickHouse                    │
│  - Vector Store (Pinecone)       │
└──────────────────────────────────┘
```

---

## API Design

### REST API

**Base URL:** `https://api.yappc.ghatana.com/v1`

**Endpoints:**
```
GET    /projects                 # List projects
POST   /projects                 # Create project
GET    /projects/{id}            # Get project
PUT    /projects/{id}            # Update project
DELETE /projects/{id}            # Delete project

POST   /projects/{id}/generate   # Generate code
POST   /projects/{id}/validate   # Validate project
POST   /projects/{id}/deploy     # Deploy project
```

**Authentication:**
```
Authorization: Bearer <jwt_token>
X-Tenant-ID: <tenant_id>
```

### gRPC API

**Service Definition:**
```protobuf
service ProjectService {
  rpc ListProjects(ListProjectsRequest) returns (ListProjectsResponse);
  rpc GetProject(GetProjectRequest) returns (Project);
  rpc CreateProject(CreateProjectRequest) returns (Project);
  rpc UpdateProject(UpdateProjectRequest) returns (Project);
  rpc DeleteProject(DeleteProjectRequest) returns (Empty);
}
```

---

## Testing Strategy

### Unit Tests

- **Coverage target:** 80%+
- **Async tests:** Use `EventloopTestBase`
- **Mocking:** Mockito for dependencies
- **Example:**

```java
class ProjectServiceTest extends EventloopTestBase {
    @Test
    void shouldCreateProject() {
        // Given
        ProjectService service = new ProjectService(repository);
        CreateProjectRequest request = new CreateProjectRequest("My Project");
        
        // When
        Project result = runPromise(() -> service.createProject(request));
        
        // Then
        assertThat(result.getName()).isEqualTo("My Project");
    }
}
```

### Integration Tests

- Test full stack (API → Domain → Data-Cloud)
- Use `@IntegrationTest` annotation
- Test with real Data-Cloud instance

### E2E Tests

- Playwright for UI testing
- Test complete user workflows
- Run against staging environment

---

## Migration Patterns

### ActiveJ Migration

**Before (blocking):**
```java
public Project getProject(String id) {
    return repository.findById(id);
}
```

**After (non-blocking):**
```java
public Promise<Project> getProject(String id) {
    return repository.findById(id);
}
```

### Data-Cloud Migration

**Before (JDBC):**
```java
@Entity
public class Project {
    @Id private String id;
    @Column private String name;
}
```

**After (Data-Cloud):**
```java
// Domain model (unchanged)
public class Project {
    private String id;
    private String name;
}

// Mapper
public class ProjectMapper {
    public Entity toEntity(Project project) {
        return Entity.builder()
            .type("Project")
            .id(project.getId())
            .attribute("name", project.getName())
            .build();
    }
}
```

---

## References

### Architecture Decision Records (ADRs)

- [ADR-001: ActiveJ Adoption](docs/architecture/ADR-001-activej-adoption.md)
- [ADR-002: Data-Cloud Integration](docs/architecture/ADR-002-datacloud-integration.md)
- [ADR-003: Multi-Tenancy Strategy](docs/architecture/ADR-003-multi-tenancy.md)

### Related Documentation

- [Developer Guide](DEVELOPER_GUIDE.md)
- [Deployment Guide](guides/DEPLOYMENT_GUIDE.md)
- [Testing Guide](guides/ACTIVEJ_TEST_MIGRATION_GUIDE.md)

---

**Status:** Living Document  
**Owner:** Architecture Team  
**Review Cycle:** Quarterly  
**Next Review:** 2026-04-27
