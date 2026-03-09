# ADR-001: Service Architecture and Responsibility Split

**Status:** Accepted  
**Date:** 2026-01-27  
**Deciders:** Engineering Team, Architecture Board  
**Related:** [PRINCIPAL_ENGINEER_ANALYSIS_2026-01-27.md](../../PRINCIPAL_ENGINEER_ANALYSIS_2026-01-27.md)

---

## Context

YAPPC has evolved into a multi-service platform with both Java and Node.js components. The codebase shows evidence of multiple service definitions with unclear boundaries and responsibilities. This has led to:

- Developer confusion during onboarding
- Unclear deployment strategies
- Difficulty in understanding which service owns which functionality
- Ambiguity in port allocation and service discovery

Current service structure:
- `domain/` - Java service (ActiveJ)
- `ai-requirements-api` - Java service on port 8081
- `lifecycle-api` - Java service on port 8082
- `backend-api` - Node.js service
- `canvas-ai-service` - Java service
- Frontend - React app on port 3000

## Decision

We adopt a **Hybrid Backend Architecture** that leverages the strengths of both Java/ActiveJ and Node.js, following the pattern documented in `.github/copilot-instructions.md`.

### Architecture Model: Hybrid Backend

```
┌─────────────────────────────────────────────────────────────────┐
│                    Frontend (React/Next.js)                      │
│                          Port 3000                               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                   ┌────────┴────────┐
                   │                 │
┌──────────────────▼──────┐  ┌──────▼───────────────────────────┐
│   Java Services         │  │   Node.js Services               │
│   (ActiveJ/Spring Boot) │  │   (Fastify)                      │
├─────────────────────────┤  ├──────────────────────────────────┤
│ • domain                │  │ • backend-api                    │
│   - Core domain logic   │  │   - User preferences             │
│   - Main API gateway    │  │   - UI state management          │
│   - Port 8080           │  │   - CRUD operations              │
│                         │  │   - Real-time subscriptions      │
│ • ai-requirements-api   │  │   - Port 8000                    │
│   - AI/LLM operations   │  │                                  │
│   - RAG workflows       │  │                                  │
│   - Port 8081           │  │                                  │
│                         │  │                                  │
│ • lifecycle-api         │  │                                  │
│   - Project lifecycle   │  │                                  │
│   - Build orchestration │  │                                  │
│   - Port 8082           │  │                                  │
│                         │  │                                  │
│ • canvas-ai-service     │  │                                  │
│   - Canvas AI features  │  │                                  │
│   - Port 8083           │  │                                  │
└─────────────────────────┘  └──────────────────────────────────┘
                   │                 │
                   └────────┬────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                  Infrastructure Layer                         │
├───────────────────────────────────────────────────────────────┤
│  PostgreSQL (5432)  │  Redis (6379)  │  Data-Cloud           │
└───────────────────────────────────────────────────────────────┘
```

### Service Responsibility Matrix

| Service | Technology | Port | Responsibilities | When to Use |
|---------|-----------|------|------------------|-------------|
| **domain** | Java/ActiveJ | 8080 | • Core business logic<br>• Complex domain operations<br>• API gateway/routing<br>• Authentication/Authorization | High-performance operations<br>Complex business rules<br>Event processing |
| **ai-requirements-api** | Java/ActiveJ | 8081 | • AI/LLM integration<br>• Requirements analysis<br>• Code generation<br>• RAG workflows | AI-powered features<br>Heavy computation<br>LLM operations |
| **lifecycle-api** | Java/ActiveJ | 8082 | • Project lifecycle<br>• Build orchestration<br>• Deployment pipelines<br>• Task execution | Long-running processes<br>Build/deploy operations<br>Workflow orchestration |
| **canvas-ai-service** | Java/ActiveJ | 8083 | • Canvas AI features<br>• Visual element generation<br>• Layout optimization | Canvas-specific AI<br>Visual processing |
| **backend-api** | Node.js/Fastify | 8000 | • User preferences<br>• UI state management<br>• Simple CRUD<br>• Real-time updates<br>• Frontend API | Simple data operations<br>UI state sync<br>Real-time features<br>Rapid iterations |
| **Frontend** | React/Next.js | 3000 | • User interface<br>• Client-side routing<br>• State management (Jotai)<br>• GraphQL client | User interaction<br>Visual presentation |

### Port Allocation Strategy

**Development Environment:**
- 3000: Frontend (React)
- 8000: Backend API (Node.js)
- 8080: Domain service (Java)
- 8081: AI Requirements API (Java)
- 8082: Lifecycle API (Java)
- 8083: Canvas AI Service (Java)
- 5432: PostgreSQL
- 6379: Redis

**Production Environment:**
- Use service discovery (Kubernetes/Consul)
- Internal services use cluster IPs
- External access via ingress controller

### Cross-Service Communication

**Synchronous (HTTP/REST):**
```java
// Java → Java
Promise<Response> response = httpClient.get("http://ai-requirements-api:8081/analyze");

// Node.js → Java
const response = await fetch('http://domain:8080/api/projects');
```

**Asynchronous (Events via Redis/Data-Cloud):**
```java
// Publish event
eventPublisher.publish("project.created", event);

// Subscribe to event
eventSubscriber.subscribe("project.created", this::handleProjectCreated);
```

### Data Ownership

| Data Type | Owner Service | Access Pattern |
|-----------|--------------|----------------|
| Domain Entities (Projects, Workspaces) | `domain` | Direct access via API |
| User Preferences | `backend-api` | Direct access via API |
| AI Analysis Results | `ai-requirements-api` | Request/Response |
| Build/Deploy Status | `lifecycle-api` | Polling + Events |
| Canvas Data | `domain` + `canvas-ai-service` | Hybrid (CQRS pattern) |

## Consequences

### Positive

1. **Clear Separation of Concerns**
   - Java services handle complex domain logic and high-performance needs
   - Node.js handles UI state and simple CRUD (faster iteration)

2. **Technology Strengths**
   - ActiveJ for event processing and high throughput
   - Fastify for rapid API development and WebSocket

3. **Scalability**
   - Services can scale independently
   - Different resource allocation per service type

4. **Developer Experience**
   - Clear ownership and responsibility
   - Easier onboarding with documented boundaries
   - Reduced cognitive load

5. **Deployment Flexibility**
   - Can deploy services independently
   - Enables progressive rollouts
   - Service-specific SLAs

### Negative

1. **Operational Complexity**
   - More services to monitor and maintain
   - Requires service mesh or API gateway
   - Distributed debugging challenges

2. **Network Overhead**
   - Cross-service calls add latency
   - Requires resilience patterns (circuit breaker, retry)

3. **Data Consistency**
   - Eventual consistency across services
   - Requires careful transaction management
   - Saga pattern for distributed transactions

4. **Deployment Coordination**
   - Schema changes require coordination
   - API versioning becomes critical
   - Backward compatibility required

### Mitigations

1. **Service Mesh** (Istio/Linkerd)
   - Automatic retry, timeout, circuit breaking
   - Distributed tracing
   - Traffic management

2. **API Gateway** (Kong/Spring Cloud Gateway)
   - Centralized routing
   - Rate limiting
   - Authentication

3. **Event Sourcing** (Data-Cloud)
   - Reliable event delivery
   - Event replay for debugging
   - Audit trail

4. **Contract Testing** (Pact)
   - Ensure API compatibility
   - Early detection of breaking changes
   - Consumer-driven contracts

## Implementation Guidelines

### 1. Service Creation Checklist

When creating a new service:
- [ ] Define clear responsibility boundaries
- [ ] Allocate port number
- [ ] Add to docker-compose.yml with appropriate profile
- [ ] Create health check endpoint (`/health`)
- [ ] Add readiness probe (`/ready`)
- [ ] Configure metrics endpoint (`/metrics`)
- [ ] Add to service discovery
- [ ] Document in this ADR

### 2. When to Create a New Service

Create a new service when:
- Functionality has distinct scaling requirements
- Team ownership is separate
- Technology stack differs significantly
- Deployment cadence is independent

Do NOT create a new service for:
- Simple feature additions
- Code organization (use modules instead)
- Temporary experiments

### 3. Service Communication Patterns

**Use Synchronous HTTP when:**
- Request/response pattern
- Immediate response required
- Low latency requirements

**Use Asynchronous Events when:**
- Fire-and-forget operations
- Multiple consumers
- Eventual consistency acceptable
- Decoupling preferred

### 4. Service Testing Strategy

```
Unit Tests (70%)
  ↓
Integration Tests (20%)
  ↓
Contract Tests (5%)
  ↓
E2E Tests (5%)
```

## Alternatives Considered

### Alternative 1: Pure Java Monolith

**Pros:**
- Simpler deployment
- No cross-language complexity
- Better IDE support

**Cons:**
- Slower iteration for UI-centric features
- All-or-nothing scaling
- Spring Boot overhead for simple CRUD

**Decision:** Rejected due to UI iteration speed requirements

### Alternative 2: Pure Microservices

**Pros:**
- Maximum independence
- Fine-grained scaling

**Cons:**
- Operational complexity
- Network chattiness
- Distributed data challenges

**Decision:** Rejected as over-engineered for current scale

### Alternative 3: Node.js Monolith + Java Services

**Pros:**
- Node.js for entire API layer
- Java only for compute-intensive tasks

**Cons:**
- Loses ActiveJ benefits for domain logic
- Duplicate business logic risk
- Type safety issues

**Decision:** Rejected due to domain logic complexity

## References

- [Hybrid Backend Strategy - Copilot Instructions](../../.github/copilot-instructions.md)
- [YAPPC Architecture](../ARCHITECTURE.md)
- [Principal Engineer Analysis](../../PRINCIPAL_ENGINEER_ANALYSIS_2026-01-27.md)
- [Martin Fowler - Microservices](https://martinfowler.com/articles/microservices.html)
- [Sam Newman - Building Microservices](https://samnewman.io/books/building_microservices/)

## Future Considerations

1. **Service Mesh Adoption** (Q2 2026)
   - Evaluate Istio vs Linkerd
   - Pilot with non-critical services

2. **API Gateway** (Q2 2026)
   - Centralized routing and security
   - Rate limiting and throttling

3. **gRPC for Internal Communication** (Q3 2026)
   - Faster than HTTP/JSON
   - Type-safe contracts
   - Bi-directional streaming

4. **Serverless Functions** (Q4 2026)
   - For sporadic, event-driven tasks
   - Cost optimization

## Approval

| Role | Name | Approval | Date |
|------|------|----------|------|
| Principal Engineer | - | ✅ | 2026-01-27 |
| Engineering Lead | - | Pending | - |
| Product Lead | - | Pending | - |
| CTO | - | Pending | - |

---

**Next Review:** 2026-03-27 (60 days)  
**Version:** 1.0  
**Last Updated:** 2026-01-27
