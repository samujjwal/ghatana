# Architecture & Design Documentation Suite

# Project Siddhanta - Jurisdiction-Neutral Capital Markets Operating System

**Document Version:** 2.1  
**Date:** March 5, 2026  
**Status:** Implementation-Ready (Post-ARB Remediation)  
**Classification:** Internal - Confidential  
**First Jurisdiction:** Nepal (SEBON / NRB / NEPSE)

---

## Document Overview

> **Stack authority**: Effective 2026-03-09, [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md) is the canonical technology baseline for Siddhanta. Older stack labels in this suite are historical unless explicitly aligned to ADR-011.
>
> **Platform Reuse**: See [Finance-Ghatana Integration Plan](../../finance-ghatana-integration-plan.md) for detailed implementation guidance on reusing Ghatana platform components.

This is the **master index** for the comprehensive Architecture & Design Documentation Suite for Project Siddhanta. The complete documentation has been organized into multiple parts for manageability while maintaining comprehensive coverage of all architectural aspects.

### Purpose

This documentation suite provides:

- **Strategic Vision**: Executive-level overview of project objectives and capabilities
- **Technical Architecture**: Detailed system design and implementation specifications
- **Platform Extensibility Model**: Content/plugin taxonomy (T1/T2/T3) enabling jurisdiction-neutral operation
- **Metadata-Driven Control Plane**: Versioned process definitions, human-task schemas, and value catalogs resolved at runtime instead of hardcoded into services
- **Operational Guidance**: Deployment, security, and observability frameworks
- **Compliance Framework**: Multi-jurisdiction regulatory adherence (SEBON/NRB primary, SEBI/RBI, MiFID II)
- **Risk Management**: Comprehensive risk assessment and mitigation strategies
- **Future-Proofing**: Roadmap for digital assets, ESG, T+0 settlement, CBDC, and post-quantum cryptography

### Intended Audience

- **Executive Leadership**: Strategic vision and business value
- **Solution Architects**: System design and integration patterns
- **Development Teams**: Implementation specifications and code examples
- **Operations Teams**: Deployment and monitoring guidelines
- **Compliance Officers**: Regulatory requirements and audit trails
- **Security Teams**: Security architecture and controls

---

## Documentation Structure

The complete Architecture & Design Documentation Suite consists of **15 comprehensive sections** organized into **3 parts** plus **2 supplementary documents**:

### Part 1: Foundation & Core Architecture

**File:** `ARCHITECTURE_SPEC_PART_1_SECTIONS_1-3.md`

- **Section 1: Executive Summary**
  - Project vision and strategic objectives
  - Core capabilities and technology stack
  - Architectural principles and key SLAs
- **Section 2: Layered Architecture**
  - 7-layer architecture design
  - Component descriptions and interactions
  - Cross-cutting concerns

- **Section 3: Event-Driven & CQRS Architecture**
  - Event sourcing implementation
  - CQRS pattern with read/write models
  - Event processing patterns and schema management

**File:** `ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md`

- **Section 4: Configuration Resolution Architecture**
  - Hierarchical configuration management
  - Secrets management with HashiCorp Vault
  - Feature flags and dynamic configuration

- **Section 5: Plugin Runtime Architecture**
  - Extensible plugin system
  - Plugin lifecycle management
  - Sandboxed execution and inter-plugin communication

### Part 2: Advanced Systems & Operations

**File:** `ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`

- **Section 6: AI Governance & Integration Architecture**
  - Model registry and versioning
  - Training pipelines and feature store
  - Model serving and monitoring
  - Drift detection and governance framework

- **Section 7: Data Architecture**
  - Polyglot persistence strategy
  - Database schemas (PostgreSQL, TimescaleDB, Redis, Elasticsearch, object storage)
  - Caching with Redis
  - Data lake and ETL pipelines
  - Ghatana Data Cloud alignment for shared abstractions and governance

- **Section 8: Deployment Architecture**
  - Kubernetes cluster setup
  - Service deployment configurations
  - Istio service mesh
  - GitHub Actions + ArgoCD delivery pipelines

**File:** `ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md`

- **Section 9: Security Architecture**
  - Zero-trust security model
  - Authentication (OAuth 2.0, JWT, MFA)
  - Authorization (RBAC)
  - Encryption and API security
  - Security monitoring and audit logging

- **Section 10: Observability Architecture**
  - Metrics collection (Prometheus)
  - Distributed tracing (Jaeger, OpenTelemetry)
  - Centralized logging (ELK stack)
  - Grafana dashboards and alerting

### Part 3: Optimization & Governance

**File:** `ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md`

- **Section 11: Performance Optimization Architecture**
  - Multi-level caching strategies
  - Database query optimization
  - Asynchronous processing
  - Load balancing and auto-scaling

- **Section 12: Compliance & Regulatory Architecture**
  - Automated compliance monitoring
  - Regulatory reporting (SEBI, RBI, MiFID II)
  - GDPR compliance
  - Audit logging

- **Section 13: Future-Safe Validation Architecture**
  - API versioning and backward compatibility
  - Database schema evolution
  - Feature flag management
  - Technology stack upgradability

- **Section 14: Requirements Traceability Matrix**
  - Complete mapping of requirements to implementation
  - Epic-to-component traceability
  - Test coverage analysis

- **Section 15: Risks & Mitigation Strategies**
  - Technical, business, and operational risks
  - Mitigation strategies and ownership
  - Risk monitoring and incident response

---

## Quick Navigation Guide

### For Executive Leadership

Start with:

1. **Section 1: Executive Summary** - Strategic vision and business value
2. **Section 14: Requirements Traceability Matrix** - Coverage and completeness
3. **Section 15: Risks & Mitigation Strategies** - Risk landscape

### For Solution Architects

Focus on:

1. **Section 2: Layered Architecture** - System design
2. **Section 3: Event-Driven & CQRS Architecture** - Core patterns
3. **Section 7: Data Architecture** - Data management strategy
4. **Section 8: Deployment Architecture** - Infrastructure design

### For Development Teams

Review:

1. **Section 2-5: Core Architecture** - Implementation patterns
2. **Section 6: AI Governance** - ML/AI integration
3. **Section 9: Security Architecture** - Security implementation
4. **Section 11: Performance Optimization** - Performance best practices

### For Operations Teams

Study:

1. **Section 8: Deployment Architecture** - Deployment procedures
2. **Section 10: Observability Architecture** - Monitoring and alerting
3. **Section 11: Performance Optimization** - Scaling strategies
4. **Section 15: Risks & Mitigation** - Incident response

### For Compliance Officers

Examine:

1. **Section 12: Compliance & Regulatory Architecture** - Regulatory framework
2. **Section 9: Security Architecture** - Security controls
3. **Section 14: Requirements Traceability Matrix** - Compliance coverage
4. **Section 15: Risks & Mitigation** - Compliance risks

---

## Key Architectural Decisions

### 1. Jurisdiction-Neutral Core with Content Pack Extensibility

- **Decision**: Zero jurisdiction-specific logic in core; all country rules externalized to versioned, signed Content Packs
- **Rationale**: Nepal is first instantiation, not architectural boundary — enables multi-jurisdiction rollout
- **Content Pack Taxonomy**:
  - **T1 (Config Packs)**: Data-only — tax tables, calendars, thresholds, exchange parameters
  - **T2 (Rule Packs)**: Declarative logic — OPA/Rego compliance rules, validation schemas
  - **T3 (Executable Packs)**: Signed code — exchange adapters, pricing models, settlement processors
- **Lifecycle**: Pack Certification (EPIC-P-01) → Marketplace → Maker-Checker Deployment → Hot-Swap
- **Trade-offs**: Pack development complexity, certification overhead
- **Mitigation**: Platform SDK (K-12), comprehensive pack templates, automated certification pipeline

### 1A. Metadata-Driven Process and Value Model

- **Decision**: Treat workflow definitions, step catalogs, human-task forms, and operator-facing value sets as versioned metadata assets resolved through K-02 and executed or rendered by W-01, K-13, and domain services.
- **Rationale**: Many capital-market processes differ by jurisdiction, tenant, operator type, product, and regulatory notice cadence; hardcoding step order or dropdown values creates avoidable release churn.
- **Implementation**: Process templates, task schemas, and allowed value catalogs are schema-validated, auditable, hot-reloadable, and bound by operator-defined min/max and permission constraints.
- **Trade-offs**: Higher metadata-governance burden and stronger compatibility requirements.
- **Mitigation**: Definition versioning, approval workflows, catalog deprecation rules, and simulation/test mode before activation.

### 2. Microservices Architecture

- **Decision**: Adopt microservices with domain-driven design
- **Rationale**: Independent scaling, deployment, and team autonomy
- **Trade-offs**: Increased operational complexity, distributed system challenges
- **Mitigation**: Service mesh (Istio), comprehensive observability, resilience patterns library (K-18)

### 3. Event Sourcing & CQRS

- **Decision**: Implement event sourcing for all core domains
- **Rationale**: Complete audit trail, temporal queries, regulatory compliance, replay capability
- **Trade-offs**: Increased storage, eventual consistency complexity
- **Mitigation**: Snapshots for performance, clear consistency boundaries, DLQ management (K-19)

### 4. Dual-Calendar Native Architecture

- **Decision**: Bikram Sambat and Gregorian calendars at the data layer
- **Rationale**: Nepal regulatory/business requirement; many emerging markets have dual-calendar needs
- **Implementation**: K-15 Dual-Calendar Service provides `DualDate(gregorian, bs)` for every timestamp
- **Trade-offs**: Storage overhead, conversion complexity
- **Mitigation**: Dedicated calendar service, materialized views for report generation

### 5. Polyglot Persistence

- **Decision**: Use multiple database technologies
- **Rationale**: Optimize for specific use cases (transactional, time-series, documents)
- **Trade-offs**: Operational complexity, data consistency challenges
- **Mitigation**: Clear data ownership, automated backup/recovery, distributed transaction coordinator (K-17)

### 6. Kubernetes-Based Deployment with Abstraction

- **Decision**: Deploy on Kubernetes with deployment abstraction layer (K-10)
- **Rationale**: Container orchestration, auto-scaling, multi-cloud portability
- **Trade-offs**: Learning curve, operational overhead
- **Mitigation**: Managed Kubernetes services, Helm charts, GitOps (ArgoCD), air-gap support

### 7. Zero-Trust Security Model

- **Decision**: Implement zero-trust architecture — assume breach, verify everything
- **Rationale**: Financial system requires highest security posture, regulatory mandate
- **Trade-offs**: Increased authentication/authorization overhead
- **Mitigation**: Service mesh for mTLS, efficient token validation, secrets rotation (K-14)

### 8. Multi-Cloud Strategy with Air-Gap Support

- **Decision**: Support deployment across AWS, Azure, GCP, and air-gapped on-premise
- **Rationale**: Avoid vendor lock-in, regulatory requirements (data residency), disaster recovery
- **Trade-offs**: Increased complexity, testing overhead
- **Mitigation**: Deployment abstraction layer (K-10), infrastructure as code, cryptographically signed offline packs

### 9. AI as Integrated Substrate

- **Decision**: AI embedded across all workflows with governance framework
- **Rationale**: Risk assessment, anomaly detection, compliance monitoring, decision support
- **Governance**: Model registry, drift detection, explainability, human-in-the-loop override, bias auditing
- **Trade-offs**: Model maintenance, explainability requirements, compute cost
- **Mitigation**: AI Governance framework (K-09), MLOps pipeline, feature store

### 10. Distributed Transaction Coordination (Post-ARB)

- **Decision**: Saga pattern with distributed transaction coordinator (K-17)
- **Rationale**: Financial operations span multiple services — settlement, ledger, reconciliation
- **Trade-offs**: Saga complexity, compensating transaction design
- **Mitigation**: DTC framework with compensating actions, DLQ management (K-19), idempotency guarantees

### 11. Stack Standardization & Ghatana Platform Alignment

- **Decision**: Standardize Siddhanta on the canonical stack defined by [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)
- **Rationale**: Remove cross-document stack drift and reuse proven Ghatana platform products for AI/ML, event processing, workflow, and data management
- **Trade-offs**: Some older diagrams and examples become historical rather than current defaults
- **Mitigation**: Treat ADR-011 as the stack authority and update current baseline documents to reference it explicitly

---

## Pervasive AI Design Principles

Siddhanta is architected as an **AI-native capital markets platform**. AI is not a feature layer — it is an intrinsic substrate woven into every decision flow, data pipeline, and operational process. The following principles govern how AI is embedded throughout the system.

### Principle 1: Intelligence at Every Decision Boundary

Every consequential decision — order creation, settlement confirmation, compliance assessment, break classification, report generation — carries a machine-learning signal alongside the deterministic rule. No significant workflow is "AI-dark."

| Domain                      | AI Capability                                                              | Algorithm                                            |
| --------------------------- | -------------------------------------------------------------------------- | ---------------------------------------------------- |
| Order Management (D-01)     | Pre-trade impact prediction, execution strategy, cost estimation           | GBM, XGBoost, TCA model                              |
| Execution Management (D-02) | Venue toxicity scoring, RL execution optimization                          | LightGBM, PPO/DQN                                    |
| Risk Engine (D-06)          | Market regime detection, AI-generated stress scenarios                     | HMM (3-state), VAE                                   |
| Compliance Engine (D-07)    | Adverse media NLP, STR risk scoring, RAG EDD copilot                       | BERT, XGBoost, RAG-LLM                               |
| Sanctions Screening (D-14)  | Multilingual embedding name match, GNN PEP network scoring                 | sentence-transformer, GraphSAGE                      |
| Surveillance (D-08)         | Anomaly detection, GNN collusion detection, LLM SAR narrative              | Isolation Forest + Autoencoder, GraphSAGE, LLM       |
| Settlement (D-09)           | Failure prediction, proactive intervention                                 | XGBoost                                              |
| Corporate Actions (D-12)    | Break classification, LLM instruction narrative                            | Gradient-boosted, Mistral-7B                         |
| Reconciliation (D-13)       | Break pattern classification, RAG resolution recommender, LSTM forecasting | GBM, RAG-LLM, LSTM + Prophet                         |
| Reference Data (D-11)       | Entity resolution, instrument classification                               | BERT record linkage, NLP multi-label                 |
| Observability (K-06)        | Metric anomaly detection, log NLP search, alert correlation                | Isolation Forest + LSTM-AE, HDBSCAN, embedding graph |
| DLQ (K-19)                  | Failure pattern classification, root cause RAG, overflow prediction        | GBM + sentence-transformer, RAG-LLM, LSTM            |
| Operations (O-01)           | Natural language platform query, capacity planning                         | LLM NL→PromQL, Prophet                               |
| Regulator Portal (R-01)     | Regulatory query copilot                                                   | RAG-LLM (SEBON/NRB-scoped)                           |
| Incident Response (R-02)    | Incident clustering, LLM RCA assistant                                     | HDBSCAN + logistic regression, RAG-LLM               |

### Principle 2: Locally-Hosted LLMs — No External API Calls

All generative AI (RAG copilots, narrative generation, NLQ translation) uses **locally-hosted models** running in the K-04 Plugin Runtime T2 secure sandbox. Mistral-7B (or equivalent instruction-tuned model) is the default inference runtime. External LLM APIs (OpenAI, Anthropic, Google) are architecturally prohibited in production to ensure:

- **Data residency** compliance (SEBON, NRB, Nepal data protection)
- **Air-gap compatibility** (on-premise deployments)
- **Deterministic audit** (exact prompt ↔ response pairs stored in K-07)

### Principle 3: Vector-Native Data Layer

Every domain that requires semantic similarity — sanctions screening, log search, break resolution, alert deduplication, EDD copilot retrieval — exposes **pgvector** collections managed through K-08 Data Governance and Ghatana Data Cloud abstractions. Embedding models (sentence-transformers, multilingual-e5) are registered in K-09 and subject to drift monitoring.

### Principle 4: K-09 as the Universal AI Governance Backbone

All 33 registered models flow through the K-09 AI Governance service for:

- **Model Registry** — semantic versioning, metadata, dependency tracking
- **SHAP Explainability** — per-prediction feature attribution attached to every output event
- **Drift Detection** — PSI/KS tests on input distributions; automatic rollback on breach
- **HITL Controls** — tiered override gates before production promotion
- **Bias Monitoring** — fairness metrics tracked for demographic and market-condition segments
- **K-07 Audit** — every model invocation, prediction, and human override immutably recorded

### Principle 5: AI Risk Tiering — Not All Models Are Equal

Models are assigned a governance tier **at registration**:

| Tier         | HITL Gate                          | Before Production           | Examples                                                   |
| ------------ | ---------------------------------- | --------------------------- | ---------------------------------------------------------- |
| TIER_1       | Monitor-only                       | Standard peer review        | DLQ classifier, CA break routing, log anomaly              |
| TIER_2       | Recommendation, human decides      | Staged canary               | Settlement prediction, regime detection, recon forecasting |
| TIER_3       | Automated decision, shadow first   | ≥5 days shadow evaluation   | STR auto-case, RL execution, GNN collusion                 |
| ADVISORY_LLM | Human reviews output before action | Accuracy eval on golden set | All copilots, narrative generators, NLQ engines            |

### Principle 6: AI Feedback Loops Are First-Class Data

Human overrides, analyst edits, and HITL decisions are not discarded — they are:

- Stored in K-07 audit with full context
- Surfaced as labeled training signals to K-10 ML Feature Store
- Fed into nightly retraining pipelines registered with K-09
- Tracked as override-rate metrics that trigger escalation when drift exceeds thresholds

### Principle 7: Explainability Is Non-Negotiable

Every AI output that influences a workflow carries a **SHAP feature attribution vector** or equivalent explanation artifact. This is not a regulatory checkbox — it is an operational requirement that allows compliance officers, risk managers, and regulators to interrogate every machine-driven decision.

### Principle 8: AI and Deterministic Rules Coexist — Rules Win

AI signals are **advisory to deterministic rules**, not replacements. If a K-03 rule mandates a hard block (e.g., breaching a regulatory position limit), no ML model prediction can override it. AI augments human and rule-based judgment; it does not supplant the regulatory control plane.

---

### Frontend

- **Framework**: React 18 with TypeScript
- **Styling**: TailwindCSS
- **State Management**: Jotai + TanStack Query
- **Real-time**: WebSocket

### Backend Services

- **Kernel/Domain/Event/Workflow/Data**: Java 21 + ActiveJ
- **User API / Control Plane**: Node.js LTS + TypeScript + Fastify + Prisma
- **AI/ML**: Python 3.11 + FastAPI
- **API Gateway**: Envoy/Istio ingress with K-11 gateway control plane

### Data Layer

- **Transactional**: PostgreSQL 15+
- **Time-Series**: TimescaleDB
- **Cache**: Redis
- **Search**: Elasticsearch
- **Object Storage / Data Lake**: S3 / MinIO
- **Vector Search**: pgvector
- **Shared Abstractions**: Ghatana Data Cloud platform + SPI

### Messaging & Events

- **Event Streaming**: Apache Kafka via Ghatana AEP/Event Cloud alignment
- **Event Store**: PostgreSQL with custom implementation

### Infrastructure

- **Orchestration**: Kubernetes (EKS/AKS/GKE)
- **Service Mesh**: Istio
- **CI/CD**: GitHub Actions, ArgoCD
- **Container Registry**: Harbor
- **Secrets**: HashiCorp Vault

### Observability

- **Metrics**: Prometheus + Grafana
- **Tracing**: Jaeger + OpenTelemetry
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **Alerting**: Alertmanager, PagerDuty

### AI/ML

- **Training Frameworks**: TensorFlow, PyTorch, scikit-learn, LightGBM, XGBoost
- **Deep Learning / Sequence Models**: LSTM (LSTM-Autoencoder for anomaly detection, LSTM for forecasting)
- **Graph Neural Networks**: PyTorch Geometric (GraphSAGE for collusion detection, PEP network scoring)
- **Generative / VAE**: PyTorch VAE for stress scenario generation
- **Language Models**: HuggingFace Transformers — BERT fine-tuning (adverse media, entity resolution, instrument classification)
- **Sentence Embeddings**: sentence-transformers (all-MiniLM-L6-v2, multilingual-e5-large) for vector search
- **LLM Inference**: Mistral-7B (locally-hosted via vLLM/Ollama in K-04 T2 sandbox — no external API)
- **Platform Reuse**: Ghatana `ai-registry`, `ai-inference-service`, `feature-store-ingest`, and `platform/java/ai-integration`
- **RAG Framework**: Custom retrieval pipeline over pgvector and governed K-08/K-09 services
- **Time-Series Forecasting**: Prophet (capacity planning, recon failure forecasting)
- **Reinforcement Learning**: Stable-Baselines3 (PPO/DQN for RL execution optimizer)
- **Clustering**: HDBSCAN (log intelligence, incident clustering)
- **MLOps / Registry**: MLflow registered with K-09 AI Governance
- **Feature Store**: Feast (K-10 ML Feature Store)
- **Model Serving**: Python FastAPI wrappers; K-04 Plugin Runtime for LLM inference
- **Vector Stores**: pgvector (PostgreSQL extension)
- **Explainability**: SHAP (shap library), LIME (lime library)

---

## Implementation Roadmap

### Phase 1: Platform Kernel (Months 1-4)

- Core infrastructure setup (Kubernetes, databases, event bus)
- Authentication and authorization framework (K-01)
- Configuration engine (K-02), rules engine (K-03), plugin runtime (K-04)
- Event bus and event store (K-05)
- Observability stack (K-06), audit framework (K-07)
- Dual-calendar service (K-15), secrets management (K-14)
- Distributed transaction coordinator (K-17), resilience patterns (K-18), DLQ management (K-19)
- **Gate**: All kernel modules reach "Platform Stable" status

### Phase 2: Core Domain Subsystems (Months 5-8)

- Order management system (D-01), execution management (D-02)
- Portfolio management (D-03), market data (D-04)
- Risk engine (D-06), compliance & controls (D-07)
- Ledger framework (K-16), post-trade & settlement (D-09)
- NEPSE exchange adapter (T3 pack), Nepal regulatory rules (T2 pack)

### Phase 3: Advanced Domain & Workflows (Months 9-11)

- Pricing engine (D-05), trade surveillance (D-08)
- Corporate actions (D-12), regulatory reporting (D-10)
- Reference data (D-11), client money reconciliation (D-13)
- Sanctions screening (D-14)
- Workflow orchestration (W-01), client onboarding (W-02)

### Phase 4: Operations, Packs & Scale (Months 12-14)

- Regulator portal (R-01), incident notification (R-02)
- Pack certification & marketplace (P-01)
- Operator workflows & runbooks (O-01)
- Platform integration testing (T-01), chaos engineering (T-02)
- Performance optimization and multi-region deployment
- **Gate**: Production readiness certification

---

## Future-Proofing Roadmap

### Near-Term (12-18 months)

| Initiative                        | Description                                        | Architecture Impact                              |
| --------------------------------- | -------------------------------------------------- | ------------------------------------------------ |
| **T+1 / T+0 Settlement**          | Reduced settlement cycles per regulatory mandate   | Post-trade engine, DTC, real-time reconciliation |
| **Additional Jurisdiction Packs** | India (SEBI/NSE/BSE), regional markets             | T1/T2/T3 pack development, no core changes       |
| **Advanced AI Models**            | LLM-powered compliance analysis, anomaly detection | AI Governance (K-09), model registry expansion   |
| **Mobile Trading**                | Native iOS/Android trading apps                    | API Gateway, WebSocket auth, push notifications  |

### Medium-Term (18-36 months)

| Initiative                        | Description                                                | Architecture Impact                                              |
| --------------------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------- |
| **Digital Assets & Tokenization** | Support for tokenized securities, crypto-asset custody     | New asset class in D-11, custody T3 plugin, regulatory pack      |
| **ESG Integration**               | ESG scoring, sustainability reporting, green bond tracking | D-03 portfolio ESG overlay, D-10 ESG reporting pack              |
| **CBDC Integration**              | Central Bank Digital Currency settlement                   | K-16 ledger extension, new settlement T3 plugin                  |
| **Post-Quantum Cryptography**     | Quantum-safe key exchange and signatures                   | K-14 secrets rotation to PQC algorithms, Ed448 plugin signatures |

### Long-Term (36+ months)

| Initiative                        | Description                                        | Architecture Impact                            |
| --------------------------------- | -------------------------------------------------- | ---------------------------------------------- |
| **DeFi Bridge**                   | Bridges to decentralized finance protocols         | New T3 plugins, additional compliance packs    |
| **Cross-Border Settlement**       | Multi-CBDC and cross-border payment rails          | K-17 extension, multi-currency ledger          |
| **Autonomous Compliance**         | Fully AI-driven regulatory interpretation          | K-09 model autonomy levels, regulatory sandbox |
| **Quantum Computing Integration** | Quantum advantage for portfolio optimization, risk | Plugin-based quantum compute offload           |

### Architecture Guardrails for Future-Proofing

1. **No technology wedge**: All infrastructure choices wrapped in abstraction layers
2. **Event schema evolution**: Backward-compatible schema changes via schema registry
3. **API versioning**: Sunset policy with minimum 12-month deprecation window
4. **Database migrations**: Liquibase/Flyway with zero-downtime schema evolution
5. **Plugin hot-swap**: New capabilities deployable without platform restart
6. **Feature flags**: Progressive rollout for all new functionality

---

## Key Performance Indicators (KPIs)

### System Performance

- **Order Latency (p99)**: < 1ms
- **Market Data Latency**: < 100μs
- **API Response Time (p95)**: < 50ms
- **Throughput**: 100,000+ orders/sec
- **System Availability**: 99.99%

### Business Metrics

- **Client Onboarding Time**: < 24 hours
- **Trade Settlement Accuracy**: 100%
- **Regulatory Report Timeliness**: 100%
- **Client Satisfaction Score**: > 4.5/5

### Operational Metrics

- **Deployment Frequency**: Daily
- **Mean Time to Recovery (MTTR)**: < 15 minutes
- **Change Failure Rate**: < 5%
- **Security Incident Response**: < 1 hour

---

## Compliance & Regulatory Coverage

### Nepal Regulations (Primary Jurisdiction)

- **SEBON**: Securities Board of Nepal
  - Order audit trail and best execution
  - Broker licensing and reporting
  - Market surveillance and insider trading detection
- **NRB**: Nepal Rastra Bank
  - KYC/AML compliance (Nepal NRB Master Circular)
  - Foreign exchange regulations
  - Payment system oversight

- **NEPSE**: Nepal Stock Exchange
  - Exchange connectivity and order protocols
  - Circuit breaker and market halt handling
  - Settlement cycle compliance (T+2, future T+1/T+0)

### Indian Regulations (Second Jurisdiction via Packs)

- **SEBI**: Securities and Exchange Board of India
  - Order audit trail and best execution
  - Client fund segregation
  - Risk management framework
- **RBI**: Reserve Bank of India
  - KYC/AML compliance
  - Payment processing regulations

### International Standards

- **MiFID II**: Markets in Financial Instruments Directive
  - Transaction reporting
  - Best execution
- **Dodd-Frank**: US financial regulation
  - Swap data reporting
  - Risk management

- **FATF**: Financial Action Task Force
  - AML/CFT recommendations
  - Enhanced due diligence for high-risk jurisdictions

### Data Privacy & Compliance

- **Nepal Data Privacy**: Compliance with Nepal's data protection requirements
- **GDPR**: General Data Protection Regulation (for EU clients)
  - Right to access, rectification, and erasure
  - Data encryption and anonymization
- **ISO 27001**: Information security management
- **SOC 2**: Service organization controls
- **FATCA/CRS**: Tax information exchange (via T2 rule packs)

---

## Security Framework

### Defense-in-Depth Layers

1. **Perimeter**: WAF, DDoS protection, IP whitelisting
2. **Identity**: OAuth 2.0, JWT, MFA, SSO
3. **API**: Rate limiting, request validation, API keys
4. **Application**: Input validation, CSRF/XSS protection
5. **Network**: mTLS, service mesh, network policies
6. **Data**: Encryption at rest and in transit, masking
7. **Infrastructure**: Secrets management, container security

### Security Controls

- **Authentication**: Multi-factor authentication (TOTP)
- **Authorization**: Role-based access control (RBAC)
- **Encryption**: AES-256-GCM for data at rest, TLS 1.3 for transit
- **Audit**: Comprehensive audit logging with Elasticsearch
- **Monitoring**: Real-time security event monitoring
- **Incident Response**: 24/7 security operations center

---

## Disaster Recovery & Business Continuity

### Recovery Objectives

- **Recovery Time Objective (RTO)**: < 15 minutes
- **Recovery Point Objective (RPO)**: < 1 second
- **Data Retention**: 10 years (regulatory requirement — SEBON and SEBI mandate minimum 7 years; platform retains 10 years for cross-jurisdiction safety margin)

### Backup Strategy

- **Database**: Continuous replication + daily snapshots
- **Files**: S3 with versioning and cross-region replication
- **Configuration**: GitOps with version control
- **Secrets**: Vault with automated backup

### Multi-Region Deployment

- **Primary Region**: ap-south-1 (Mumbai)
- **Secondary Region**: ap-southeast-1 (Singapore)
- **Disaster Recovery**: us-east-1 (N. Virginia)
- **Failover**: Automated with health checks and DNS routing

---

## Testing Strategy

### Test Pyramid

1. **Unit Tests**: 80% coverage minimum
2. **Integration Tests**: API and service integration
3. **End-to-End Tests**: Critical user journeys
4. **Performance Tests**: Load and stress testing
5. **Security Tests**: Penetration testing, vulnerability scanning

### Test Environments

- **Development**: Feature development and unit testing
- **Staging**: Integration and E2E testing
- **Pre-Production**: Performance and security testing
- **Production**: Canary deployments and monitoring

### Continuous Testing

- Automated test execution on every commit
- Performance regression testing
- Security scanning in CI/CD pipeline
- Chaos engineering for resilience testing

---

## Monitoring & Alerting

### Metrics Collection

- **System Metrics**: CPU, memory, disk, network
- **Application Metrics**: Request rate, latency, errors
- **Business Metrics**: Orders, trades, positions
- **Custom Metrics**: Domain-specific KPIs

### Alert Severity Levels

- **P0 (Critical)**: System down, data loss - Page on-call immediately
- **P1 (High)**: Major functionality impaired - Alert within 15 minutes
- **P2 (Medium)**: Minor functionality impaired - Alert within 1 hour
- **P3 (Low)**: Informational - Email notification

### Dashboards

- **Executive Dashboard**: Business KPIs and system health
- **Operations Dashboard**: Infrastructure and service metrics
- **Trading Dashboard**: Order flow and market data
- **Security Dashboard**: Security events and threats

---

## Documentation Maintenance

### Version Control

- All documentation stored in Git repository
- Semantic versioning (MAJOR.MINOR.PATCH)
- Change log maintained for all updates
- Review and approval process for major changes

### Review Cycle

- **Quarterly**: Architecture review and updates
- **Monthly**: Technology stack evaluation
- **Weekly**: Risk assessment updates
- **Continuous**: Implementation documentation

### Stakeholder Communication

- Architecture Decision Records (ADRs) for major decisions
- Regular architecture review meetings
- Documentation portal with search capability
- Training sessions for new team members

---

## Glossary of Terms

| Term                   | Definition                                                                                            |
| ---------------------- | ----------------------------------------------------------------------------------------------------- |
| **Bikram Sambat (BS)** | Official Nepali calendar; dual-calendar architecture stores both BS and Gregorian for every timestamp |
| **CBDC**               | Central Bank Digital Currency — digital form of fiat currency issued by a central bank                |
| **Content Pack**       | Versioned, signed bundle of jurisdiction-specific configuration, rules, or code (T1/T2/T3)            |
| **CQRS**               | Command Query Responsibility Segregation — pattern separating read and write operations               |
| **DDD**                | Domain-Driven Design — software design approach focused on business domains                           |
| **DLQ**                | Dead Letter Queue — queue for events that fail processing, with replay capability                     |
| **DTC**                | Distributed Transaction Coordinator — orchestrates sagas across service boundaries                    |
| **Dual-Calendar**      | Architecture mandate: every timestamp stored as `DualDate(gregorian, bikram_sambat)`                  |
| **EMS**                | Execution Management System — system for managing trade execution                                     |
| **ESG**                | Environmental, Social, and Governance — sustainability metrics for investments                        |
| **Event Sourcing**     | Pattern storing all changes as sequence of immutable events                                           |
| **FATF**               | Financial Action Task Force — intergovernmental body setting AML/CFT standards                        |
| **FIX Protocol**       | Financial Information eXchange — industry protocol for trading                                        |
| **HPA**                | Horizontal Pod Autoscaler — Kubernetes auto-scaling mechanism                                         |
| **Maker-Checker**      | Approval workflow requiring one user to initiate and another to approve a critical action             |
| **mTLS**               | Mutual TLS — two-way authentication using certificates                                                |
| **NEPSE**              | Nepal Stock Exchange — primary securities exchange of Nepal                                           |
| **NRB**                | Nepal Rastra Bank — central bank and monetary authority of Nepal                                      |
| **OMS**                | Order Management System — system for managing trading orders                                          |
| **OPA**                | Open Policy Agent — policy engine for declarative rules evaluation                                    |
| **PMS**                | Portfolio Management System — system for managing investment portfolios                               |
| **RBAC**               | Role-Based Access Control — authorization based on user roles                                         |
| **Rego**               | Policy language used by OPA for writing compliance and business rules                                 |
| **SEBON**              | Securities Board of Nepal — securities regulator of Nepal                                             |
| **SLA**                | Service Level Agreement — commitment to service quality                                               |
| **T1 Pack**            | Config pack — data-only (tax tables, calendars, thresholds)                                           |
| **T2 Pack**            | Rule pack — declarative logic (OPA/Rego compliance rules, validation schemas)                         |
| **T3 Pack**            | Executable pack — signed code (exchange adapters, pricing models, settlement processors)              |
| **TOTP**               | Time-based One-Time Password — MFA authentication method                                              |

---

## References & Related Documents

### Internal Documents

- **Project Vision Document**: `/docs/All_In_One_Capital_Markets_Platform_Specification.md`
- **Implementation Baseline**: `UNIFIED_IMPLEMENTATION_PLAN.md` and `WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md`
- **Epic Specifications**: `/epics/EPIC-*.md` (42 epics across Kernel, Domain, Workflow, Operations, Packs, Regulatory, Testing, Platform Unity layers)
- **Regulatory Architecture Document**: `/REGULATORY_ARCHITECTURE_DOCUMENT.md`
- **ARB Stress Test Review (Archived)**: `/archive/reviews/2026-03/ARB_STRESS_TEST_REVIEW.md`
- **C4 Diagram Pack**: `/C4_DIAGRAM_PACK_INDEX.md` + `/C4_C1_*.md` through `/C4_C4_*.md`
- **LLD Documents**: `/LLD_INDEX.md` + `/LLD_*.md` (16 LLDs)
- **API Documentation**: Generated via Swagger/OpenAPI
- **Runbooks**: Operational procedures for common scenarios

### External Standards

- **SEBI Guidelines**: https://www.sebi.gov.in/
- **RBI Regulations**: https://www.rbi.org.in/
- **MiFID II**: https://www.esma.europa.eu/
- **GDPR**: https://gdpr.eu/
- **ISO 27001**: https://www.iso.org/isoiec-27001-information-security.html

### Technology Documentation

- **Kubernetes**: https://kubernetes.io/docs/
- **Istio**: https://istio.io/docs/
- **Kafka**: https://kafka.apache.org/documentation/
- **PostgreSQL**: https://www.postgresql.org/docs/
- **React**: https://react.dev/

---

## Contact & Support

### Architecture Team

- **Chief Architect**: [Contact Information]
- **Solution Architects**: [Contact Information]
- **Platform Team**: [Contact Information]

### Support Channels

- **Architecture Questions**: #architecture-discussion (Slack)
- **Technical Support**: #tech-support (Slack)
- **Security Issues**: security@siddhanta.io
- **On-Call**: PagerDuty rotation

### Contribution Guidelines

- Fork repository and create feature branch
- Follow coding standards and documentation templates
- Submit pull request with detailed description
- Obtain approval from architecture review board

---

## Document History

| Version | Date          | Author            | Changes                                                                                                                                                                                                                                                                                                                                              |
| ------- | ------------- | ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0     | January 2025  | Architecture Team | Initial comprehensive documentation suite                                                                                                                                                                                                                                                                                                            |
| 2.0     | March 3, 2026 | Architecture Team | Post-ARB remediation: updated epic inventory (42 epics), added new kernel modules (K-17/K-18/K-19), domain modules (D-13/D-14), regulatory modules (R-02), testing modules (T-02), updated references and coverage map                                                                                                                               |
| 2.1     | March 5, 2026 | Architecture Team | Hardening pass: established jurisdiction-neutral core principle, added T1/T2/T3 content pack taxonomy, Nepal regulatory context (SEBON/NRB/NEPSE), dual-calendar mandate, future-proofing roadmap (digital assets, ESG, CBDC, PQC), 10-year data retention, expanded glossary, cross-reference alignment between architecture specs, epics, and LLDs |

---

## Appendices

### Appendix A: Complete File Listing

1. `ARCHITECTURE_AND_DESIGN_SPECIFICATION.md` - This master index
2. `ARCHITECTURE_SPEC_PART_1_SECTIONS_1-3.md` - Foundation architecture
3. `ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md` - Configuration & plugins
4. `ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md` - AI, data, deployment
5. `ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md` - Security & observability
6. `ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md` - Performance, compliance, risks

### Appendix B: Epic Coverage Map

All 42 epics from `/epics/` directory are covered across the architecture:

**Kernel Layer (19 epics):**

- **K-01 to K-16**: Core kernel modules (IAM, Config Engine, Rules Engine, Plugin Runtime, Event Bus, Observability, Audit Framework, Data Governance, AI Governance, Deployment Abstraction, API Gateway, Platform SDK, Admin Portal, Secrets Management, Dual-Calendar Service, Ledger Framework)
- **K-17**: Distributed Transaction Coordinator [ARB P0-01]
- **K-18**: Resilience Patterns Library [ARB P0-02]
- **K-19**: DLQ Management & Event Replay [ARB P0-04]

**Domain Layer (14 epics):**

- **D-01 to D-12**: Core domain subsystems (OMS, EMS, PMS, Market Data, Pricing Engine, Risk Engine, Compliance, Surveillance, Post-Trade, Regulatory Reporting, Reference Data, Corporate Actions)
- **D-13**: Client Money Reconciliation [ARB P1-11]
- **D-14**: Sanctions Screening [ARB P1-13]

**Workflow Layer (2 epics):**

- **W-01**: Cross-Domain Workflow Orchestration
- **W-02**: Client Onboarding & KYC Workflow

**Other Layers (7 epics):**

- **O-01**: Operator Workflows (Operations)
- **P-01**: Pack Certification & Marketplace (Packs)
- **PU-004**: Platform Manifest (Platform Unity)
- **R-01**: Regulator Portal & Evidence Export (Regulatory)
- **R-02**: Incident Notification & Escalation (Regulatory) [ARB P1-15]
- **T-01**: Integration Testing & E2E Scenarios (Testing)
- **T-02**: Chaos Engineering & Resilience Testing (Testing) [ARB P2-19]

### Appendix C: Technology Evaluation Criteria

When evaluating new technologies or alternatives:

1. **Performance**: Meets latency and throughput requirements
2. **Scalability**: Supports growth projections
3. **Security**: Meets security standards
4. **Compliance**: Supports regulatory requirements
5. **Community**: Active community and long-term viability
6. **Cost**: Total cost of ownership
7. **Integration**: Compatibility with existing stack
8. **Team Expertise**: Learning curve and available skills

---

## Conclusion

This Architecture & Design Documentation Suite provides a comprehensive blueprint for implementing Project Siddhanta - an all-in-one capital markets platform. The documentation is designed to be:

- **Comprehensive**: Covering all aspects from vision to implementation
- **Practical**: Including code examples and configuration samples
- **Maintainable**: Structured for easy updates and evolution
- **Accessible**: Organized for different stakeholder needs

The architecture is designed with the following core principles:

- **Scalability**: Handle growth from small brokers to large institutions
- **Reliability**: 99.99% availability with robust disaster recovery
- **Security**: Zero-trust model with defense-in-depth
- **Compliance**: Built-in regulatory adherence
- **Performance**: Sub-millisecond latency for critical operations
- **Flexibility**: Extensible through plugins and feature flags
- **Observability**: Comprehensive monitoring and tracing

**Next Steps:**

1. Review this documentation with all stakeholders
2. Obtain sign-off from architecture review board
3. Begin Phase 1 implementation
4. Establish regular review and update cadence
5. Create detailed implementation guides for development teams

---

**Document Status**: ✅ Complete and Ready for Implementation

**Last Updated**: March 5, 2026  
**Next Review**: June 5, 2026

---

_This document is confidential and proprietary to Project Siddhanta. Unauthorized distribution is prohibited._
