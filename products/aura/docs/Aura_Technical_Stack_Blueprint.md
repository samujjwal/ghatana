# Aura Technical Stack Blueprint

## Architecture Pattern: Hybrid Backend

Aura uses a **hybrid backend** strategy with a deliberate seam between user-facing CRUD/real-time concerns and high-performance core domain logic:

| Feature Type     | Technology                 | Use Case                                                                                     |
| ---------------- | -------------------------- | -------------------------------------------------------------------------------------------- |
| **User API**     | Node.js + Fastify + Prisma | Profile management, preferences, feed delivery, real-time, CRUD                              |
| **Core Domain**  | Java 21 + ActiveJ          | Recommendation engine, ingestion workers, ranking pipeline, high-throughput event processing |
| **ML Inference** | Python + FastAPI           | Model serving, feature computation, batch pipelines                                          |

This separation allows the user-facing API to iterate rapidly (with JavaScript's ecosystem advantages) while the computationally intensive recommendation and ingestion systems leverage Java / ActiveJ's performance and async event-loop model.

### Modularity Guardrails

- Start with three deployable boundaries: `apps/api`, `apps/core-worker`, and `apps/ml-inference`.
- Keep product domains explicit inside those deployables: profile, catalog, recommendation,
  explainability, community, and governance.
- Extract a domain into its own service only when runtime isolation, scaling behavior, compliance
  requirements, release cadence, or team ownership make a separate deployable materially better.
- Avoid both extremes: no service-per-concept by default, and no monolithic blob of shared utilities
  that erases domain boundaries.

---

## Frontend

| Technology       | Purpose                                                      | License |
| ---------------- | ------------------------------------------------------------ | ------- |
| React Router v7  | Modern routing with framework mode for SSR and data fetching | MIT     |
| React 19+        | UI component library and runtime                             | MIT     |
| Tailwind CSS     | Utility-first styling — no custom CSS frameworks             | MIT     |
| React Native     | Mobile applications (iOS and Android)                        | MIT     |
| Jotai            | Lightweight application state management                     | MIT     |
| TanStack Query   | Server state, caching, and background refresh                | MIT     |
| GraphQL (Apollo) | Flexible GraphQL client for complex nested queries           | MIT     |

**Note**: All frontend libraries are open-source with permissive licenses (MIT). React Router v7 framework mode replaces the need for Next.js by providing native SSR, data fetching, error boundaries, and streaming. Route definitions and loaders are colocated with components, enabling simpler and more maintainable code.

---

## Backend — User API Layer

| Technology               | Purpose                                                     |
| ------------------------ | ----------------------------------------------------------- |
| Node.js 22               | Runtime                                                     |
| Fastify                  | HTTP framework (performance-optimized, plugin architecture) |
| Prisma                   | ORM for PostgreSQL — schema, migrations, type-safe queries  |
| GraphQL (Fastify plugin) | Public-facing GraphQL API endpoint                          |
| JWT                      | Authentication tokens — verified on every request           |

---

## Backend — Core Domain Layer

| Technology       | Purpose                                                                    |
| ---------------- | -------------------------------------------------------------------------- |
| Java 21          | Core domain runtime                                                        |
| ActiveJ          | High-performance async event loop for recommendation and ingestion workers |
| ActiveJ Promises | All async operations (no CompletableFuture, no Reactor)                    |
| ActiveJ HTTP     | Internal HTTP services where needed                                        |

---

## ML / AI

| Technology            | Purpose                                                 |
| --------------------- | ------------------------------------------------------- |
| Python 3.11           | ML model development and inference                      |
| FastAPI               | Inference endpoints for separate-runtime model serving  |
| PyTorch               | Model training (shade matching, ranking)                |
| scikit-learn          | Gradient boosted ranking, feature preprocessing         |
| pgvector              | Semantic similarity via vector embeddings in PostgreSQL |
| Sentence Transformers | Ingredient and product embedding generation             |

---

## Databases

| Technology                     | Role                                                                 |
| ------------------------------ | -------------------------------------------------------------------- |
| PostgreSQL 16                  | Primary relational store: products, users, recommendations, consents |
| pgvector                       | Vector embeddings for semantic search (PostgreSQL extension)         |
| Redis                          | Cache, session store, rate limiting, hot recommendation paths        |
| Object Storage (S3-compatible) | Raw ingestion payloads, ML training snapshots, audit archives        |

---

## Messaging & Events

| Technology                 | Role                                              |
| -------------------------- | ------------------------------------------------- |
| Apache Kafka (or Redpanda) | Durable event bus when cross-process decoupling is warranted |
| Dead-letter topics         | Failed event retry and investigation              |

All event consumers must be idempotent. All events are immutable and versioned. Start with in-process
domain events and durable job execution where possible; introduce Kafka or Redpanda once fan-out,
independent retries, or throughput make a dedicated bus worthwhile.

---

## Observability

| Technology    | Role                                                                     |
| ------------- | ------------------------------------------------------------------------ |
| Micrometer    | JVM metrics instrumentation (Java deployables)                           |
| OpenTelemetry | Distributed tracing and structured logging across deployable boundaries  |
| Prometheus    | Metrics collection and alerting rules                                    |
| Grafana       | Dashboards for service health, recommendation quality, model performance |

---

## Infrastructure & Deployment

| Technology     | Role                                       |
| -------------- | ------------------------------------------ |
| Docker         | Containerization of all deployables        |
| Kubernetes     | Orchestration, scaling, health checks when scale requires it |
| Helm           | Kubernetes release management              |
| Gitea Actions  | CI/CD pipelines: test, lint, build, deploy |
| Terraform      | Infrastructure-as-code for cloud resources |

---

## Development Tools

| Tool                         | Role                                         |
| ---------------------------- | -------------------------------------------- |
| Gradle                       | Java/Kotlin build tool                       |
| pnpm                         | Node.js package manager (workspace-aware)    |
| Spotless                     | Java code formatting                         |
| Checkstyle + PMD             | Java static analysis                         |
| ESLint + Prettier            | TypeScript/JavaScript linting and formatting |
| Jest + React Testing Library | Frontend unit and integration tests          |

---

## Technology Decision Rationale

### Why Fastify over NestJS?

Fastify has significantly lower overhead and better raw throughput for the user-facing API tier. Prisma's type-safe ORM provides the structured data access layer that NestJS's DI system would have provided. The combination is simpler and faster.

### Why Java/ActiveJ for Core Domain?

The recommendation engine, ingestion workers, and ranking pipeline are high-throughput, latency-sensitive components. ActiveJ's event loop model (similar to Vert.x and Netty) handles concurrency without OS thread overhead. Java 21's virtual threads provide an additional concurrency option for blocking I/O paths.

### Why PostgreSQL + pgvector over a separate vector database?

Consolidating relational and vector storage reduces operational complexity significantly during early stages. pgvector is production-ready and avoids synchronization issues between separate stores. A dedicated vector database (e.g., Weaviate, Pinecone) can be added if retrieval scale warrants it later.

### Why Python for ML inference?

The ML ecosystem (PyTorch, scikit-learn, HuggingFace) is Python-native. Keep ML inference behind a
small FastAPI runtime boundary because the Python toolchain is distinct, but avoid fragmenting models
into many deployables until traffic or ownership clearly requires it.

### Why React Router v7 eliminates Next.js?

React Router v7 in framework mode provides all core capabilities that Next.js offers: SSR, data fetching, streaming, error boundaries, and automatic code-splitting. By using React Router directly with a Fastify backend for API routes, we eliminate the need for a meta-framework and reduce dependency complexity. Route handlers and components are colocated, improving code maintainability. This approach is simpler to reason about and aligns with Aura's preference for explicit, composable tooling over heavy frameworks.

---

## Open-Source & Licensing Principles

Aura's technology stack prioritizes **open-source tools with permissive licenses** to ensure vendor neutrality, auditability, and long-term sustainability:

| Category                     | Licensing Approach                                                      |
| ---------------------------- | ----------------------------------------------------------------------- |
| **Core Application Stack**   | MIT and Apache 2.0 (React, React Router v7, Fastify, ActiveJ, FastAPI)  |
| **Databases & Caching**      | PostgreSQL-licensed and Apache 2.0 (PostgreSQL, pgvector, Redis, Kafka) |
| **Frontend Routing & State** | MIT (React Router v7, Jotai, TanStack Query)                            |
| **Observability**            | Apache 2.0 (Micrometer, OpenTelemetry, Prometheus)                      |
| **Infrastructure**           | Apache 2.0 and permissive (Docker, Kubernetes, Helm)                    |
| **Build & Deployment**       | Permissive open-source (Gitea, Gradle, pnpm, Terraform)                 |

**Rationale**: Permissive licenses (MIT, Apache 2.0, BSD) allow commercial use, modification, and redistribution with minimal restrictions. This ensures Aura can evolve without licensing concerns and allows the community to contribute back improvements freely.
