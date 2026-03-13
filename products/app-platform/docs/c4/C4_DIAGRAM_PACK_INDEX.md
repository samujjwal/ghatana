# C4 DIAGRAM PACK - MASTER INDEX

## AppPlatform Multi-Domain Operating System — C4 Diagram Pack

**Version:** 2.0  
**Date:** March 3, 2026  
**Status:** Architecture Baseline (Post-ARB Remediation)  
**Project**: AppPlatform Multi-Domain Operating System | Capital Markets (Siddhanta) is the reference domain pack

> **📦 NOTE:** C4 diagrams depicting Siddhanta-specific components (C1–C4 SIDDHANTA files) describe the Capital Markets domain pack instantiation, not the generic AppPlatform Kernel.

> **Stack authority**: [ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md) defines the canonical Siddhanta stack. The C4 pack remains valid for boundaries and interactions, but legacy technology labels must be interpreted through ADR-011.

---

## 📋 DOCUMENT OVERVIEW

This master index provides navigation to the complete C4 diagram pack for Project Siddhanta. The C4 model provides a hierarchical set of architecture diagrams at different levels of abstraction, from system context down to code-level details.

**C4 Model Hierarchy**:

1. **C1 - Context**: System in its environment (users, external systems)
2. **C2 - Container**: High-level technology choices (applications, databases, services)
3. **C3 - Component**: Internal structure of containers (modules, components)
4. **C4 - Code**: Class-level design (entities, services, repositories)

**Cross-Cutting Architecture Primitives** (enforced at all C4 levels):

- **Domain Pack Architecture**: All domain-specific functionality is externalized into pluggable domain packs (Capital Markets, Banking, Healthcare, Insurance)
- **Content Pack Taxonomy (T1/T2/T3)**: All jurisdiction-specific behavior is externalized — T1 (Config/data-only), T2 (Rules/OPA-Rego), T3 (Executable/signed plugins)
- **Generic Kernel**: Kernel modules (K-01 through K-19) remain domain-agnostic and reusable across all domains
- **Domain Isolation**: Domain packs are isolated from kernel and from each other
- **Multi-Calendar (K-15)**: Canonical timestamps are ISO 8601 UTC. The K-15 Multi-Calendar Service generates a `CalendarDate` record (with optional additional calendars via T1 pack) on demand. The K-05 Event Bus standard envelope carries an optional `calendar_date` field (type: `CalendarDate`) alongside the required `timestamp` (UTC).
- **Hardened NFR Targets**: Order placement ≤2ms internal / ≤12ms e2e P99, 50K sustained / 100K burst TPS, 99.999% availability, 10-year data retention (configurable per jurisdiction).

---

## 🗂️ DIAGRAM PACK CONTENTS

### 📄 C1 - System Context Diagram

**File**: [`C4_C1_CONTEXT_SIDDHANTA.md`](C4_C1_CONTEXT_SIDDHANTA.md)

**Purpose**: Shows the Siddhanta platform in the context of its users and external systems across multiple domains

**Key Contents**:

- System boundary and scope for multi-domain platform
- Primary actors (retail investors, brokers, regulators, patients, doctors, customers, etc.)
- External systems (exchanges, depositories, banks, KYC providers, healthcare systems, insurance networks)
- Key interactions and data flows across domains
- Trust boundaries and integration points
- Domain pack boundaries and isolation

**Sections**:

1. Diagram Legend
2. C1 Context Diagram (Mermaid)
3. Textual Context Description
4. Assumptions (deployment, integration, regulatory, user, operational, security)
5. Invariants (data integrity, compliance, security, operational, business, performance)
6. What Breaks This (external failures, regulatory changes, technical failures, operational errors, business continuity, market structure changes)
7. Context Boundaries & Scope
8. Deployment Context
9. Technology Context
10. Stakeholder Perspectives
11. Quality Attributes
12. Evolution & Future Context

**Use This When**:

- Explaining the system to non-technical stakeholders
- Understanding external dependencies
- Planning integrations with third-party systems
- Assessing regulatory compliance scope
- Defining system boundaries

---

### 📄 C2 - Container Diagram

**File**: [`C4_C2_CONTAINER_SIDDHANTA.md`](C4_C2_CONTAINER_SIDDHANTA.md)

**Purpose**: Shows the high-level technology architecture and container-level components for multi-domain platform

**Key Contents**:

- Client-facing layer (web portal, mobile app, operator portal)
- API & integration layer (API gateway, FIX gateway, integration hub)
- Domain pack layer (Capital Markets, Banking, Healthcare, Insurance)
- Kernel services layer (K-01 through K-19 generic services)
- Data layer (PostgreSQL, TimescaleDB/pgvector, Redis, OpenSearch, S3/Ceph)
- Event & messaging layer (Kafka, notification service)
- Observability layer (Prometheus, OpenSearch, Jaeger)

**Sections**:

1. Diagram Legend
2. C2 Container Diagram (Mermaid)
3. Container Descriptions (detailed for each container)
4. Assumptions (technology, data, integration, deployment, security)
5. Invariants (data consistency, service communication, security, operational, performance)
6. What Breaks This (container failures, service failures, integration failures, data corruption, performance degradation, security breaches, operational errors)
7. Technology Stack Summary
8. Deployment Architecture

**Use This When**:

- Designing the technology stack
- Planning infrastructure and deployment
- Understanding service boundaries
- Designing inter-service communication
- Capacity planning and scaling strategy

---

### 📄 C3 - Component Diagram

**File**: [`C4_C3_COMPONENT_SIDDHANTA.md`](C4_C3_COMPONENT_SIDDHANTA.md)

**Purpose**: Shows the internal component structure of key services (OMS, PMS, CMS)

**Key Contents**:

- **Order Management Service (OMS)**:
  - API layer (OrderController)
  - Command processing (OrderCommandHandler, OrderValidator)
  - Domain services (OrderService, RoutingService, ExecutionService)
  - Data access (OrderRepository, OrderEventStore)
  - Event handling (OrderEventPublisher, ExecutionEventHandler)
  - Adapters (FIXAdapter, ExchangeAdapter)

- **Position Management Service (PMS)**:
  - Position calculation (PositionCalculator)
  - Corporate action processing (CorporateActionProcessor)
  - Reconciliation (ReconciliationEngine)
  - Event handlers (TradeEventHandler, SettlementEventHandler, CAEventHandler)

- **Cash Management Service (CMS)**:
  - Cash operations (DepositService, WithdrawalService, MarginFundingService, FeeService)
  - Segregation validation (SegregationValidator)
  - Banking integration (BankingAdapter, PaymentGatewayAdapter)

**Sections**:

1. Diagram Legend
2. C3 Component Diagrams (Mermaid - one per service)
3. Component Descriptions (detailed for each component)
4. Assumptions (component design, communication, data, technology)
5. Invariants (component, data, business logic)
6. What Breaks This (component failures, integration failures, data consistency issues, business logic errors)
7. Component Interaction Patterns
8. Component Testing Strategy

**Use This When**:

- Designing internal service architecture
- Understanding component responsibilities
- Planning CQRS and event sourcing implementation
- Designing validation and business rule enforcement
- Understanding data flow within services

---

### 📄 C4 - Code Diagram

**File**: [`C4_C4_CODE_SIDDHANTA.md`](C4_C4_CODE_SIDDHANTA.md)

**Purpose**: Shows class-level design for key domain models (Order, Position)

**Key Contents**:

- **Order Domain**:
  - Controllers (OrderController)
  - DTOs (PlaceOrderRequest, OrderResponse)
  - Commands (PlaceOrderCommand, ModifyOrderCommand, CancelOrderCommand)
  - Validators (OrderValidator, ValidationResult)
  - Services (OrderService, RoutingService, ExecutionService)
  - Entities (Order, OrderStatus, OrderType, Side)
  - Repositories (OrderRepository)
  - Events (OrderEvent, OrderPlacedEvent, OrderExecutedEvent)
  - Event infrastructure (OrderEventStore, OrderEventPublisher)

- **Position Domain**:
  - Entities (Position, CorporateAction)
  - Services (PositionCalculator, CorporateActionProcessor)
  - Repositories (PositionRepository, CorporateActionRepository)
  - Event handlers (TradeEventHandler, SettlementEventHandler)

**Sections**:

1. Diagram Legend
2. C4 Code Diagrams (Mermaid class diagrams)
3. Class Descriptions (detailed with code examples)
4. Assumptions (code design, data precision, concurrency, event sourcing)
5. Invariants (entity, business logic, data integrity)
6. What Breaks This (concurrency issues, data integrity issues, transaction issues, event handling issues)
7. Code Quality Practices
8. Testing Strategy

**Use This When**:

- Implementing domain models
- Understanding class relationships and dependencies
- Designing JPA entities and repositories
- Implementing event sourcing and CQRS
- Code reviews and refactoring
- Onboarding new developers

---

## 🎯 NAVIGATION GUIDE

### By Audience

#### **Executive / Business Stakeholders**

Start with: **C1 Context Diagram**

- Understand what the system does
- See external dependencies
- Understand regulatory compliance scope

#### **Solution Architects**

Start with: **C1 Context**, then **C2 Container**

- Understand system boundaries
- Design technology stack
- Plan integrations and deployments

#### **Software Architects**

Start with: **C2 Container**, then **C3 Component**

- Design service architecture
- Plan CQRS and event sourcing
- Design inter-service communication

#### **Developers**

Start with: **C3 Component**, then **C4 Code**

- Understand component responsibilities
- Implement domain models
- Write unit and integration tests

#### **DevOps / SRE**

Focus on: **C2 Container**

- Understand deployment architecture
- Plan infrastructure and scaling
- Design observability and monitoring

#### **QA / Testers**

Focus on: **C3 Component**, **C4 Code**

- Understand component interactions
- Design integration tests
- Understand failure scenarios ("What Breaks This")

#### **Compliance / Auditors**

Focus on: **C1 Context**

- Understand regulatory reporting flows
- Verify client money segregation
- Review audit trail requirements

---

### By Task

#### **Planning a New Feature**

1. **C1**: Identify external systems involved
2. **C2**: Determine which containers need changes
3. **C3**: Design new components or modify existing
4. **C4**: Implement classes and methods

#### **Troubleshooting a Production Issue**

1. **C2**: Identify affected containers (services, databases)
2. **C3**: Narrow down to specific components
3. **C4**: Review code-level logic
4. **"What Breaks This"**: Check known failure scenarios

#### **Onboarding New Team Members**

1. **C1**: Understand the big picture
2. **C2**: Learn the technology stack
3. **C3**: Understand service internals
4. **C4**: Study code patterns and practices

#### **Security Review**

1. **C1**: Review trust boundaries and external integrations
2. **C2**: Review authentication, authorization, encryption
3. **C3**: Review validation and business rule enforcement
4. **C4**: Review code-level security practices

#### **Performance Optimization**

1. **C2**: Identify bottleneck containers (databases, services)
2. **C3**: Identify slow components (queries, calculations)
3. **C4**: Optimize algorithms, database queries
4. **Invariants**: Review performance invariants (latency, throughput)

---

## 📊 DIAGRAM STATISTICS

| Diagram Level | File Size   | Sections | Diagrams  | Code Examples |
| ------------- | ----------- | -------- | --------- | ------------- |
| C1 Context    | ~50 KB      | 15       | 1 Mermaid | 0             |
| C2 Container  | ~70 KB      | 10       | 1 Mermaid | 0             |
| C3 Component  | ~60 KB      | 13       | 3 Mermaid | 0             |
| C4 Code       | ~55 KB      | 11       | 2 Mermaid | 20+           |
| **Total**     | **~235 KB** | **49**   | **7**     | **20+**       |

---

## 🔑 KEY ARCHITECTURAL DECISIONS

### 1. Event-Driven Architecture (CQRS + Event Sourcing)

**Decision**: Use CQRS with event sourcing for core services (OMS, Ledger)  
**Rationale**:

- Audit trail requirement (regulatory)
- Scalability (separate read/write models)
- Eventual consistency acceptable for most use cases

**Documented In**: C2, C3, C4

### 2. Microservices Architecture

**Decision**: Decompose into domain-driven microservices  
**Rationale**:

- Independent scaling
- Technology diversity
- Team autonomy
- Fault isolation

**Documented In**: C2, C3

### 3. Polyglot Persistence

**Decision**: Use different databases for different use cases  
**Rationale**:

- PostgreSQL for transactional data
- Redis for caching
- ClickHouse for analytics
- Elasticsearch for search

**Documented In**: C2

### 4. API-First Design

**Decision**: All services expose REST APIs (OpenAPI 3.0)  
**Rationale**:

- Contract-first development
- Client code generation
- API documentation
- Versioning support

**Documented In**: C2, C3

### 5. Hexagonal Architecture (Ports & Adapters)

**Decision**: Use hexagonal architecture for services  
**Rationale**:

- Testability (mock external dependencies)
- Flexibility (swap adapters)
- Clean separation of concerns

**Documented In**: C3, C4

---

## 🛡️ CROSS-CUTTING CONCERNS

### Security

- **Authentication**: OAuth 2.0, OTP, biometric (C1, C2)
- **Authorization**: RBAC/ABAC (C2, C3)
- **Encryption**: TLS 1.3 in transit, AES-256 at rest (C1, C2)
- **Secrets Management**: Vault or cloud KMS (C2)

### Observability

- **Monitoring**: Prometheus, Grafana (C2)
- **Logging**: ELK Stack (C2)
- **Tracing**: Jaeger, OpenTelemetry (C2)
- **Alerting**: PagerDuty, Slack (C2)

### Resilience

- **Circuit Breakers**: On all external integrations, managed by K-18 Resilience Patterns Library (C2, C3)
- **Retries**: Exponential backoff with jitter via K-18 (C2, C3)
- **Timeouts**: Cascading timeout budgets via K-18 (C2, C3)
- **Bulkheads**: Thread pool and semaphore isolation via K-18 (C2)
- **Distributed Transactions**: Outbox pattern and saga compensation via K-17 (C2, C3)
- **Dead-Letter Queues**: Monitored with RCA-before-replay via K-19 (C2, C3)
- **Chaos Engineering**: Fault injection and DR drills via T-02 (C2)

### Data Integrity

- **Immutability**: Ledger and event store append-only (C2, C3, C4)
- **Idempotency**: All operations idempotent (C2, C3, C4)
- **Optimistic Locking**: Version field for concurrency (C4)
- **Referential Integrity**: Foreign key constraints (C4)

---

## 📚 RELATED DOCUMENTATION

### Platform Specification

- **File**: `docs/All_In_One_Capital_Markets_Platform_Specification.md`
- **Purpose**: Detailed platform requirements and operator packs

### Epic Set

- **Directory**: `epics/`
- **Epic Count**: 42 (19 Kernel + 1 PU + 14 Domain + 2 Workflow + 1 Pack + 2 Testing + 1 Operations + 2 Regulatory)
- **Key Epics**:
  - `EPIC-D-01-OMS.md` - Order Management System
  - `EPIC-D-02-EMS.md` - Execution Management System
  - `EPIC-D-03-PMS.md` - Position Management System
  - `EPIC-D-04-Market-Data.md` - Market Data Service
  - `EPIC-K-17-Distributed-Transaction-Coordinator.md` - Outbox pattern, saga compensation [ARB P0-01]
  - `EPIC-K-18-Resilience-Patterns.md` - Circuit breakers, bulkheads, retry [ARB P0-02]
  - `EPIC-K-19-DLQ-Management.md` - Dead-letter queue management [ARB P0-04]
  - `EPIC-D-13-Client-Money-Reconciliation.md` - Client fund segregation [ARB P1-11]
  - `EPIC-D-14-Sanctions-Screening.md` - Real-time sanctions screening [ARB P1-13]
  - `EPIC-R-02-Incident-Notification.md` - Regulator incident notification [ARB P1-15]
  - `EPIC-T-02-Chaos-Engineering.md` - Chaos engineering & resilience testing [ARB P2-19]
  - (and 31 more epics)

### ARB Stress Test Review

- **File**: `archive/reviews/2026-03/ARB_STRESS_TEST_REVIEW.md`
- **Purpose**: Architecture Review Board findings and remediation tracking

### Regulatory Architecture Document

- **File**: `REGULATORY_ARCHITECTURE_DOCUMENT.md`
- **Purpose**: Regulatory compliance architecture and gap analysis

### Low-Level Design Documents

- **Index**: `LLD_INDEX.md`
- **Purpose**: Implementation-ready design specifications for kernel and domain modules

### Authoritative Source Register

- **File**: `docs/Authoritative_Source_Register.md`
- **Purpose**: External data sources and references

### Glossary

- **File**: `docs/Documentation_Glossary_and_Policy_Appendix.md`
- **Purpose**: Shared terminology and policy baseline

---

## 🔄 DIAGRAM MAINTENANCE

### Update Frequency

- **C1 Context**: Update when external integrations change (quarterly)
- **C2 Container**: Update when technology stack changes (monthly)
- **C3 Component**: Update when service architecture changes (sprint)
- **C4 Code**: Update when domain models change (as needed)

### Version Control

- All diagrams versioned in Git
- Diagram changes reviewed in pull requests
- Breaking changes require architecture review

### Approval Process

| Diagram Level | Approvers                                |
| ------------- | ---------------------------------------- |
| C1 Context    | Chief Architect, CTO, Head of Compliance |
| C2 Container  | Chief Architect, Lead Architects         |
| C3 Component  | Lead Architects, Service Owners          |
| C4 Code       | Service Owners, Tech Leads               |

---

## 🎓 LEARNING PATH

### Week 1: Context & Containers

- [ ] Read C1 Context Diagram
- [ ] Understand external systems and integrations
- [ ] Read C2 Container Diagram
- [ ] Understand technology stack and deployment

### Week 2: Components

- [ ] Read C3 Component Diagram (OMS)
- [ ] Understand CQRS and event sourcing patterns
- [ ] Read C3 Component Diagram (PMS, CMS)
- [ ] Understand component interactions

### Week 3: Code

- [ ] Read C4 Code Diagram (Order domain)
- [ ] Study code examples and patterns
- [ ] Read C4 Code Diagram (Position domain)
- [ ] Review "What Breaks This" sections

### Week 4: Deep Dive

- [ ] Review all "Assumptions" sections
- [ ] Review all "Invariants" sections
- [ ] Review all "What Breaks This" sections
- [ ] Hands-on: Implement a simple feature end-to-end

---

## ⚠️ IMPORTANT NOTES

### Assumptions

Each diagram level has an "Assumptions" section documenting key assumptions. **Always review assumptions before making architectural decisions.**

### Invariants

Each diagram level has an "Invariants" section documenting constraints that must always hold. **Violating invariants will cause system failures or regulatory violations.**

### What Breaks This

Each diagram level has a "What Breaks This" section documenting known failure scenarios and mitigations. **Review this section when troubleshooting production issues.**

### Mermaid Diagrams

All diagrams use Mermaid syntax for version control and easy updates. To render:

- GitHub/GitLab: Automatic rendering
- VS Code: Mermaid Preview extension
- CLI: `mmdc -i diagram.md -o diagram.png`

---

## 📞 CONTACTS

| Role                           | Responsibility                       | Contact |
| ------------------------------ | ------------------------------------ | ------- |
| Chief Architect                | Overall architecture, C1/C2 approval | [TBD]   |
| Lead Architect (Core Services) | OMS, PMS, CMS architecture           | [TBD]   |
| Lead Architect (Data)          | Data layer, analytics                | [TBD]   |
| Lead Architect (Integration)   | External integrations, APIs          | [TBD]   |
| Head of Compliance             | Regulatory requirements              | [TBD]   |
| CISO                           | Security architecture                | [TBD]   |

---

## 📝 REVISION HISTORY

| Version | Date       | Author            | Changes                                                                                                                                                                                               |
| ------- | ---------- | ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0     | 2025-03-02 | Architecture Team | Initial C4 diagram pack (C1, C2, C3, C4)                                                                                                                                                              |
| 2.0     | 2026-03-03 | Architecture Team | Post-ARB remediation: updated epic references (42 epics), added resilience patterns (K-17/K-18/K-19), added cross-references to ARB review, regulatory architecture, and LLD documents                |
| 2.1     | 2026-03-09 | Architecture Team | v2.1 hardening pass: all C1-C4 diagrams bumped to v2.1, T1/T2/T3 Content Pack taxonomy added, dual-calendar (Bikram Sambat) enforced, NFR alignment (≤2ms/≤12ms, 50K TPS, 99.999%, 10-year retention) |

---

## 🚀 QUICK START

**New to the project?**

1. Start with **C1 Context Diagram** to understand the big picture
2. Read the **Platform Specification** for business context
3. Review **C2 Container Diagram** for technology overview
4. Pick a service (e.g., OMS) and read **C3 Component Diagram**
5. Study **C4 Code Diagram** for implementation patterns

**Need to implement a feature?**

1. Identify affected services in **C2 Container Diagram**
2. Review component structure in **C3 Component Diagram**
3. Study class design in **C4 Code Diagram**
4. Check **Invariants** and **What Breaks This** sections
5. Implement following the established patterns

**Troubleshooting an issue?**

1. Identify symptom (e.g., order stuck, position mismatch)
2. Find affected container in **C2**
3. Find affected component in **C3**
4. Review **What Breaks This** for known failure scenarios
5. Check logs, metrics, traces (observability layer in **C2**)

---

**END OF C4 DIAGRAM PACK INDEX**
