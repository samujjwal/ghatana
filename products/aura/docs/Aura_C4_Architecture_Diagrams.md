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
    API[API Gateway / BFF]
    Profile[Profile Service]
    Catalog[Catalog Service]
    Reco[Recommendation Service]
    Explain[Explainability Service]
    Ingest[Ingestion Pipeline]
    Community[Community Intelligence Service]
    Consent[Consent & Governance Service]
    Analytics[Analytics Service]
    Assistant[Assistant Orchestrator]
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
  Ingest --> PG
  Ingest --> Blob
  Ingest --> MQ
  Community --> PG
  Reco --> PG
  Reco --> VDB
  Reco --> Cache
  Explain --> PG
  Profile --> PG
  Assistant --> Reco
  Assistant --> Explain
  Analytics --> PG
```

## 3. Component Diagram — Recommendation Service

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
  API1 --> SVC[Core Services]
  API2 --> SVC
  SVC --> DB[(Managed PostgreSQL)]
  SVC --> REDIS[(Managed Redis)]
  SVC --> VEC[(Vector DB)]
  SVC --> OBJ[(Object Storage)]
  SVC --> OBS[Observability Stack]
  WORK[Background Workers] --> DB
  WORK --> OBJ
  WORK --> VEC
```

## Architectural Notes

- **Starting approach:** Begin with a modular monolith if team size is small. The modular monolith
  maps cleanly to the hybrid backend pattern: Node.js + Fastify (User API / BFF) and Java 21 +
  ActiveJ (Core Domain — ingestion, ranking, recommendation workers).
- **Layer alignment:** The services shown in the Container Diagram map onto the canonical 7-layer
  platform model defined in `Aura_System_Architecture.md`:
  1. Source & Ingestion Layer → Ingestion Pipeline
  2. Canonical Knowledge Layer → Catalog Service
  3. Personal Intelligence Layer → Profile Service
  4. Decision & Recommendation Layer → Recommendation Service
  5. Agent Orchestration Layer → Assistant Orchestrator
  6. Experience Delivery Layer → API Gateway, Web App, Mobile App
  7. Observability, Governance & Learning Layer → Analytics Service, Consent Service
- **Scaling path:** Move ingestion, recommendation, and community analysis into separate services
  when throughput warrants. Keep explainability and consent as first-class services from the start.
- **Vector retrieval:** pgvector (embedded in PostgreSQL) serves as the initial Vector Store. Migrate
  to a dedicated vector database (Pinecone, Weaviate) when embedding volume exceeds ~10 M records.
