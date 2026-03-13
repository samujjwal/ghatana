# Aura Full C4 Architecture Diagrams

## 1. System Context Diagram

```mermaid
flowchart LR
  U[User] --> A[Aura Platform]
  A --> R1[Retailers / Affiliate Networks]
  A --> R2[Product Catalog Providers]
  A --> R3[Community Sources]
  A --> R4[Optional Wearables / Wellness Apps]
  A --> R5[Email Receipt Providers]
  B[Brand Analysts] --> A
```

### Actors

- User: consumes recommendations, manages profile, asks Aura assistant.
- Brand Analysts: access anonymized analytics in later phases.
- External Systems: catalogs, affiliate networks, community sources, optional wearable and receipt integrations.

## 2. Container Diagram

```mermaid
flowchart TB
  subgraph Client
    Web[Web App]
    Mobile[Mobile App]
  end

  subgraph Platform
    API[API / BFF (Node.js + Fastify)]
    Core[Core Worker Host (Java + ActiveJ)]
    ML[ML Inference (Python + FastAPI)]
    Assistant[Assistant Orchestrator]
  end

  subgraph Modules["Internal Domain Modules (inside API / Core Worker)"]
    Profile[Profile Module]
    Catalog[Catalog Module]
    Reco[Recommendation Module]
    Explain[Explainability Module]
    Community[Community Module]
    Consent[Governance Module]
    Analytics[Analytics & Learning Module]
  end

  subgraph Data
    PG[(PostgreSQL)]
    VDB[(Vector Store)]
    Cache[(Redis)]
    Blob[(Object Storage)]
    MQ[(Job Queue / Event Bus)]
  end

  Web --> API
  Mobile --> API
  API --> Profile
  API --> Catalog
  API --> Reco
  API --> Explain
  API --> Assistant
  API --> Consent
  API --> Analytics
  Core --> Catalog
  Core --> Reco
  Core --> Community
  Core --> Analytics
  Community --> PG
  Reco --> PG
  Reco --> VDB
  Reco --> Cache
  Explain --> PG
  Profile --> PG
  Assistant --> Reco
  Assistant --> Explain
  Analytics --> PG
  Consent --> PG
  Reco --> ML
  Core --> Blob
  Core --> MQ
```

## 3. Component Diagram — Recommendation Module

```mermaid
flowchart LR
  Request[Recommendation Request] --> Candidate[Candidate Generator]
  Candidate --> Rules[Rules Filter]
  Rules --> Feature[Feature Builder]
  Feature --> Ranker[Ranking Engine]
  Ranker --> Explainer[Explanation Generator]
  Explainer --> Output[Scored Recommendations]
  Feedback[Clicks / Saves / Purchases] --> Learner[Feedback Processor]
  Learner --> Ranker
```

### Components

- Candidate Generator: narrows product universe.
- Rules Filter: hard exclusions such as allergen conflicts, price bounds, ethical filters.
- Feature Builder: derives compatibility, sentiment, popularity, price-fit, similarity features.
- Ranking Engine: scores candidates.
- Explanation Generator: emits reason codes and user-facing explanations.
- Feedback Processor: learns from outcomes.

## 4. Deployment Diagram

```mermaid
flowchart TB
  CDN[CDN] --> FE[Frontend Hosting]
  FE --> LB[Load Balancer]
  LB --> API1[API Pod 1]
  LB --> API2[API Pod 2]
  API1 --> CORE[Core Worker Pods]
  API2 --> CORE
  API1 --> ML[ML Inference Pods]
  API2 --> ML
  API1 --> DB[(Managed PostgreSQL)]
  API2 --> DB
  API1 --> REDIS[(Managed Redis)]
  API2 --> REDIS
  CORE --> DB
  CORE --> REDIS
  CORE --> VEC[(Vector DB)]
  CORE --> OBJ[(Object Storage)]
  CORE --> OBS[Observability Stack]
  ML --> OBJ
  ML --> OBS
```

## Architectural Notes

- **Starting approach:** Begin with a modular monolith split across a Node.js API/BFF and a Java
  core worker host. Keep Python ML inference as its own runtime boundary because the toolchain is
  meaningfully different, not because every model deserves its own deployable.
- **Layer alignment:** The deployables and internal modules shown in the Container Diagram map onto the canonical 7-layer
  platform model defined in `Aura_System_Architecture.md`:
  1. Source & Ingestion Layer → Core Worker ingestion modules
  2. Canonical Knowledge Layer → Catalog Module
  3. Personal Intelligence Layer → Profile Module
  4. Decision & Recommendation Layer → Recommendation Module
  5. Agent Orchestration Layer → Assistant Orchestrator
  6. Experience Delivery Layer → API / BFF, Web App, Mobile App
  7. Observability, Governance & Learning Layer → Analytics & Learning Module, Governance Module
- **Scaling path:** Split ingestion, recommendation, or community analysis into separate services only
  when throughput, latency isolation, compliance boundaries, or team ownership warrant it. Keep
  explainability and consent as first-class modules from the start, but not mandatory deployables.
- **Vector retrieval:** pgvector (embedded in PostgreSQL) serves as the initial Vector Store. Migrate
  to a dedicated vector database (Pinecone, Weaviate) when embedding volume exceeds ~10 M records.
