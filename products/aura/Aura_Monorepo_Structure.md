# Aura Monorepo Structure

## Recommended Layout

```text
aura/
  apps/
    web/                        # React Router v7 web application (SSR, data fetching, streaming)
    mobile/                     # React Native mobile application (iOS + Android)
    api-gateway/                # Node.js Fastify BFF / GraphQL gateway (User API)
    ingestion-worker/           # Java 21 + ActiveJ ingestion pipeline worker
    recommendation-worker/      # Java 21 + ActiveJ recommendation and ranking worker
  services/
    profile-service/            # You Index — declared, inferred, and imported user attributes
    catalog-service/            # Canonical product, ingredient, and shade knowledge store
    recommendation-service/     # Candidate generation, ranking, and scoring
    explainability-service/     # Reason codes, explanation generation, transparency audit
    community-intelligence-service/  # Review analysis, sentiment, social signals
    governance-service/         # Consent management, data-use enforcement, policy engine
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
    docker/                     # Dockerfiles for all services
    kubernetes/                 # Kubernetes manifests and Helm charts
    terraform/                  # Infrastructure-as-code (cloud resources)
    github-actions/             # CI/CD pipelines
```

## Guidance

- **Shared packages are the source of truth** for domain types and event contracts. Services must
  import from packages, not duplicate definitions.
- **Design tokens and UI primitives** are reusable platform assets. Apply them consistently across
  web and mobile to maintain design system coherence.
- **Frontend routing:** React Router v7 framework mode provides first-class support for data fetching,
  streaming responses, and error boundaries. Route definitions and loaders are colocated with components
  for maintainability. This replaces the need for separate data-loading orchestration libraries.
- **ML code is monorepo colocated** but kept in its own top-level directory to allow separate
  Python virtual environments and toolchains.
- **The ingestion worker and recommendation worker** run as Java 21 + ActiveJ services. They are
  kept separate from the Node.js services because they have different runtime requirements, scaling
  profiles, and deployment cadences.
- **Services follow the Hybrid Backend pattern**: Node.js + Fastify handles User API (CRUD, preferences,
  real-time UI state); Java 21 + ActiveJ handles Core Domain (high-throughput event processing,
  heavy ranking, ingestion pipeline).
