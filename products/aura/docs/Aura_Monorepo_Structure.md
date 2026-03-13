# Aura Monorepo Structure

## Recommended Layout

```text
aura/
  .gitea/
    workflows/                  # Gitea Actions CI/CD pipelines
  apps/
    web/                        # React Router v7 web application (SSR, data fetching, streaming)
    mobile/                     # React Native mobile application (iOS + Android)
    api/                        # Node.js Fastify BFF / GraphQL gateway and user-facing modular monolith
    core-worker/                # Java 21 + ActiveJ host for ingestion, enrichment, ranking, and async jobs
    ml-inference/               # Python FastAPI inference service for separate-runtime model serving
  domains/
    profile/                    # You Index — declared, inferred, imported, and overridden user attributes
    catalog/                    # Canonical product, ingredient, shade, and source intelligence rules
    recommendation/             # Candidate generation, ranking, confidence, and outcome logic
    explainability/             # Reason codes, evidence assembly, transparency output contracts
    community/                  # Review analysis, sentiment, social signals
    governance/                 # Consent management, data-use enforcement, policy engine
  packages/
    ui/                         # Shared React component library (design system primitives)
    design-tokens/              # Tailwind and NativeWind design token definitions
    graphql-schema/             # Shared GraphQL schema definitions and generated types
    api-client/                 # Generated or hand-crafted API client SDK
    domain-types/               # Shared TypeScript/Java domain entity types and enums
    event-contracts/            # Canonical event schemas and Protobuf/Avro contracts
    shared-utils/               # Common utilities (validation, parsing, date helpers)
  data/
    prisma/                     # Prisma schema, migrations, and seed data
    seed/                       # Test and development seed scripts
    migrations/                 # SQL migration history
  ml/
    notebooks/                  # Exploration and analysis notebooks
    training/                   # Model training pipelines and scripts
    evaluation/                 # Offline evaluation harnesses and metric collectors
    feature-pipelines/          # Feature engineering and transformation code
  docs/
    prd/                        # Product requirement documents
    architecture/               # Architecture decision records and system diagrams
    api/                        # OpenAPI and GraphQL API documentation
    runbooks/                   # Operational runbooks for production incidents
  infra/
    docker/                     # Dockerfiles for all deployables
    kubernetes/                 # Kubernetes manifests and Helm charts
    terraform/                  # Infrastructure-as-code (cloud resources)
```

## Guidance

- **Shared packages are the source of truth** for domain types and event contracts. Apps and domain
  modules must import from packages, not duplicate definitions.
- **Design tokens and UI primitives** are reusable platform assets. Apply them consistently across
  web and mobile to maintain design system coherence.
- **Frontend routing:** React Router v7 framework mode provides first-class support for data fetching,
  streaming responses, and error boundaries. Route definitions and loaders are colocated with components
  for maintainability. This replaces the need for separate data-loading orchestration libraries.
- **ML code is monorepo colocated** but kept in its own top-level directory to allow separate
  Python virtual environments and toolchains.
- **Deployables stay intentionally small in the early stage**: `apps/api`, `apps/core-worker`, and
  `apps/ml-inference` are the only default runtime boundaries. `domains/` represent module boundaries,
  not an instruction to create a separate service per concept.
- **The core worker** runs Java 21 + ActiveJ modules for ingestion, enrichment, and ranking. Split it
  into dedicated workers only after measured queue depth, latency isolation, or release cadence
  differences make that operationally useful.
- **Modules follow extraction guardrails**: promote a domain to its own service only when at least one
  of these is true: it needs a different runtime, materially different scaling, stronger compliance or
  security isolation, or a dedicated ownership team.
- **Avoid both failure modes**: do not create standalone services just to mirror the domain model, and
  do not collapse domain rules into generic utility layers that make future extraction impossible.
- **The Hybrid Backend pattern remains intact**: Node.js + Fastify handles User API (CRUD, preferences,
  real-time UI state); Java 21 + ActiveJ handles Core Domain (high-throughput event processing, heavy
  ranking, ingestion pipeline); Python handles ML inference where a separate runtime is genuinely needed.
