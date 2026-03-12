# ADR-011: Stack Standardization and Ghatana Platform Alignment

**Status**: Accepted  
**Date**: 2026-03-09  
**Decision Makers**: Platform Architecture Team

---

## Context

Project Siddhanta currently contains multiple stack descriptions written at different times. The current finance ADR set narrowed several platform choices, but older architecture and C4 documents still describe superseded options such as Kong, GraphQL Federation, MongoDB, RabbitMQ, GitLab CI, Redux, Camunda, and Temporal.

The program also needs a single implementation baseline that is consistent with the Ghatana engineering standard and with the reusable Ghatana platform products already available for AI/ML, event processing, workflow, and data management.

Primary alignment sources:

- [CURRENT_EXECUTION_PLAN.md](CURRENT_EXECUTION_PLAN.md)
- [ADR-005_AI_AGENT_ARCHITECTURE.md](ADR-005_AI_AGENT_ARCHITECTURE.md)
- [ADR-007_DATABASE_TECHNOLOGY.md](ADR-007_DATABASE_TECHNOLOGY.md)
- [ADR-008_API_GATEWAY_TECHNOLOGY.md](ADR-008_API_GATEWAY_TECHNOLOGY.md)
- [ADR-009_EVENT_BUS_TECHNOLOGY.md](ADR-009_EVENT_BUS_TECHNOLOGY.md)
- [ADR-010_CONTAINER_ORCHESTRATION.md](ADR-010_CONTAINER_ORCHESTRATION.md)
- [../ghatana-archive-20260308-114326/.github/copilot-instructions.md](../ghatana-archive-20260308-114326/.github/copilot-instructions.md)
- [../ghatana/products/aep/platform/build.gradle.kts](../ghatana/products/aep/platform/build.gradle.kts)
- [../ghatana/platform/java/event-cloud/build.gradle.kts](../ghatana/platform/java/event-cloud/build.gradle.kts)
- [../ghatana/platform/java/workflow/build.gradle.kts](../ghatana/platform/java/workflow/build.gradle.kts)
- [../ghatana/products/data-cloud/platform/build.gradle.kts](../ghatana/products/data-cloud/platform/build.gradle.kts)
- [../ghatana/products/data-cloud/spi/build.gradle.kts](../ghatana/products/data-cloud/spi/build.gradle.kts)
- [../ghatana/platform/java/ai-integration/README.md](../ghatana/platform/java/ai-integration/README.md)
- [../ghatana/shared-services/ai-registry/README.md](../ghatana/shared-services/ai-registry/README.md)
- [../ghatana/shared-services/ai-inference-service/README.md](../ghatana/shared-services/ai-inference-service/README.md)
- [../ghatana/shared-services/feature-store-ingest/README.md](../ghatana/shared-services/feature-store-ingest/README.md)

---

## Decision

### 1. Authority Order

For stack and implementation-baseline questions, documents must be interpreted in this order:

1. This ADR
2. Current accepted finance ADRs and [CURRENT_EXECUTION_PLAN.md](CURRENT_EXECUTION_PLAN.md)
3. Ghatana engineering standard in [../ghatana-archive-20260308-114326/.github/copilot-instructions.md](../ghatana-archive-20260308-114326/.github/copilot-instructions.md)
4. Older C4, architecture, and research documents as historical context only

If an older document conflicts with this ADR, this ADR wins.

### 2. Canonical Minimal-But-Complete Stack

| Scope | Standard |
|---|---|
| Architecture | 7-layer microservices, DDD boundaries, event-driven, CQRS, event sourcing, dual-calendar native, multi-tenant, T1/T2/T3 pack model |
| Kernel, domain, event, workflow, and data platform services | Java 21 + ActiveJ + Gradle |
| User-facing API and control-plane CRUD services | Node.js LTS + TypeScript + Fastify + Prisma |
| AI/ML execution only where needed | Python 3.11 + FastAPI |
| External API contract | REST + OpenAPI |
| Internal service contract | gRPC + protobuf |
| Realtime transport | WebSocket where required |
| Frontend | React 18 + TypeScript + Tailwind CSS + Jotai + TanStack Query + React Router |
| Primary database | PostgreSQL 15+ |
| Time-series | TimescaleDB |
| Cache and ephemeral state | Redis 7+ |
| Search and log indexing | OpenSearch |
| Object storage | Ceph / S3-compatible storage |
| Vector search | pgvector on PostgreSQL |
| Event backbone | Kafka 3+ |
| Ingress and gateway | Envoy/Istio ingress with K-11 gateway control plane |
| Platform runtime | Docker + Kubernetes + Istio |
| Delivery | Terraform + Helm + ArgoCD + Gitea |
| Observability | Micrometer + OpenTelemetry + Prometheus + Grafana + Jaeger + OpenSearch |
| Monorepo tooling | Gradle + pnpm |
| Testing | JUnit 5 + Testcontainers for Java, Jest + React Testing Library + Playwright for web |

### 3. Backend Partitioning Rule

- Java 21 + ActiveJ is the default for kernel services, domain services, event processing, workflow execution, audit, governance, and latency-sensitive financial paths.
- Node.js + TypeScript + Fastify + Prisma is limited to user API surfaces, admin/control-plane CRUD services, and lightweight portal-facing orchestration.
- Python is not a general backend stack. It exists only for model training and inference services that genuinely require Python ML libraries.
- Go is deferred. It is not part of the initial canonical stack.

### 4. Ghatana Product Alignment

#### AI/ML

Siddhanta will reuse Ghatana AI platform products instead of inventing separate infrastructure:

- Model registry: [../ghatana/shared-services/ai-registry/README.md](../ghatana/shared-services/ai-registry/README.md)
- Inference gateway and serving baseline: [../ghatana/shared-services/ai-inference-service/README.md](../ghatana/shared-services/ai-inference-service/README.md)
- Feature ingestion: [../ghatana/shared-services/feature-store-ingest/README.md](../ghatana/shared-services/feature-store-ingest/README.md)
- Shared AI integration library: [../ghatana/platform/java/ai-integration/README.md](../ghatana/platform/java/ai-integration/README.md)

These products must be used under Siddhanta's production policy constraints from [ADR-005_AI_AGENT_ARCHITECTURE.md](ADR-005_AI_AGENT_ARCHITECTURE.md), especially local-only production LLM inference and auditable AI decisions.

#### Event Processing

Siddhanta will standardize event processing on the Ghatana event stack:

- Event orchestration platform: [../ghatana/products/aep/platform/build.gradle.kts](../ghatana/products/aep/platform/build.gradle.kts)
- Event runtime/client abstraction: [../ghatana/platform/java/event-cloud/build.gradle.kts](../ghatana/platform/java/event-cloud/build.gradle.kts)

Kafka remains the canonical event backbone. Siddhanta should integrate through Ghatana event abstractions rather than building a second custom event runtime beside them.

#### Workflow

Siddhanta will use Ghatana workflow components:

- Workflow runtime: [../ghatana/platform/java/workflow/build.gradle.kts](../ghatana/platform/java/workflow/build.gradle.kts)
- Cross-service orchestration support: [../ghatana/products/aep/platform/build.gradle.kts](../ghatana/products/aep/platform/build.gradle.kts)

Camunda and Temporal are not part of the baseline stack. Introducing either requires a new ADR.

#### Data Management

Siddhanta will use Ghatana Data Cloud abstractions for shared data-governance and storage integration concerns:

- Data Cloud platform: [../ghatana/products/data-cloud/platform/build.gradle.kts](../ghatana/products/data-cloud/platform/build.gradle.kts)
- Data Cloud SPI: [../ghatana/products/data-cloud/spi/build.gradle.kts](../ghatana/products/data-cloud/spi/build.gradle.kts)

This does not change the primary persistence baseline: PostgreSQL, TimescaleDB, Redis, Elasticsearch, MinIO/S3, and pgvector remain the standard stores. Data Cloud is the shared abstraction and governance layer above them.

### 5. Explicit Baseline Exclusions

The following are not part of the standard Siddhanta baseline and must not be reintroduced in current documents as defaults:

- Kong as the primary gateway
- GraphQL Federation as the primary external API model
- MongoDB as a required document store
- RabbitMQ as the primary messaging backbone
- Neo4j, Qdrant, or Weaviate as baseline data stores
- Camunda or Temporal as baseline workflow engines
- GitLab CI as the delivery baseline
- Redux Toolkit / RTK Query as the default frontend state stack
- Vitest as the default frontend test runner
- Elasticsearch as the search and logging platform
- MinIO as the default object storage
- GitHub Actions as the delivery baseline

Any use of the technologies above requires a separate ADR or module-specific justification.

## 1. Purpose

This ADR defines the canonical technology stack for the **Siddhanta Multi-Domain Operating System**. It establishes standardization, versioning, and compatibility rules for all platform components while ensuring the kernel remains domain-agnostic.

## 2. Scope

- **Kernel Layer**: 19 generic modules (K-01 through K-19) that are domain-agnostic
- **Domain Pack Layer**: Pluggable domain packs (Capital Markets, Banking, Healthcare, Insurance)
- **Content Pack Layer**: T1/T2/T3 packs for jurisdiction and tenant customization
- **Platform Unity**: Cross-cutting concerns (manifest, certification, marketplace)

This ADR is the **single source of truth** for all technology decisions. Any deviation requires a separate ADR.

---

## Consequences

- All current baseline documents must reference this ADR when describing the stack.
- Older documents may preserve historical diagrams or illustrative examples, but they must not present superseded technologies as current defaults.
- New implementation work must align to this ADR unless a newer accepted ADR explicitly changes the baseline.
- See [Finance-Ghatana Integration Plan](../../finance-ghatana-integration-plan.md) for detailed implementation guidance on reusing Ghatana platform components.
