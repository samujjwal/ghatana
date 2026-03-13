# AppPlatform — Multi-Domain Operating System

> A **jurisdiction-neutral, multi-domain operating system** designed as an extensible platform kernel. **Capital Markets (Siddhanta)** is the first domain pack instantiation — not the architectural boundary.

## Architecture at a Glance

| Dimension               | Choice                                                                                                                                                                                                                             |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Architecture Style**  | 7-layer microservices, Event Sourcing, CQRS, Domain Pack Architecture                                                                                                                                                              |
| **Extensibility Model** | Domain Packs + T1 Config / T2 Rules (OPA-Rego) / T3 Executable Content Packs                                                                                                                                                       |
| **AI/ML Substrate**     | K-09 AI Governance as mandatory inference router; Embedding Service (pgvector); HITL fine-tuning pipeline; Model A/B testing; LLM prompts versioned as config; every module uses AI implicitly via K-09                            |
| **Runtime Metadata**    | Versioned process templates, task schemas, routing policies, and value catalogs resolved through K-02 and consumed by W-01 / K-13                                                                                                  |
| **Calendar**            | Multi-Calendar via K-15 (pluggable via T1 calendar packs; Capital Markets pack includes Bikram Sambat + Gregorian)                                                                                                                 |
| **Tech Stack**          | Java 21 + ActiveJ, Node.js LTS + TypeScript + Fastify + Prisma, Python 3.11 + FastAPI, React 18 + Tailwind CSS + Jotai + TanStack Query, Kafka 3+, PostgreSQL 15+ + pgvector, TimescaleDB, Redis 7+, OpenSearch, Kubernetes, Istio |
| **AI/ML Libraries**     | PyTorch / ONNX Runtime (model serving), LangChain / LlamaIndex (RAG + agent chains), MLflow (experiment tracking), Apache Feast (feature store), Triton Inference Server (GPU serving), OpenAI / Anthropic APIs (LLM)              |
| **Security**            | Zero-Trust, OPA policy-as-code, mTLS, HashiCorp Vault                                                                                                                                                                              |
| **Availability Target** | 99.999% (5.26 min downtime/year)                                                                                                                                                                                                   |
| **Data Retention**      | 10 years regulatory minimum                                                                                                                                                                                                        |

> **Stack authority**: [adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md) is the canonical technology baseline. Older architecture and C4 docs may contain historical labels that do not override ADR-011.

## Domain Architecture

The platform supports multiple domains through a **Domain Pack Architecture**:

### Current Domain Packs

- **Capital Markets (Siddhanta)**: Trading, settlement, risk, compliance for securities markets
- **Banking**: Retail banking, corporate banking, payments, treasury (template)
- **Healthcare**: Patient management, clinical workflows, billing, research (template)
- **Insurance**: Policy management, claims processing, underwriting (template)

### Domain Pack Structure

Each domain pack includes:

- Domain-specific data models and schemas
- Business rules and workflows
- External integrations and adapters
- User interface components
- Configuration templates

> **Learn more**: See [GENERIC_PLATFORM_EXPANSION_ANALYSIS.md](GENERIC_PLATFORM_EXPANSION_ANALYSIS.md) for detailed multi-domain strategy.

## Repository Structure

```
products/app-platform/
├── finance-ghatana-integration-plan.md        # Capital Markets-specific Ghatana reuse plan
└── docs/
    ├── README.md                              # Top-level orientation and authority map
    ├── adr/                                   # Architecture Decision Records (ADR-001 through ADR-011)
    ├── architecture/                          # Master architecture suite and sectioned specifications
    ├── c4/                                    # C4 context, container, component, and code views
    ├── epics/                                 # 42 implementation epics plus dependency matrix and glossary
    ├── stories/                               # Story index, milestone story packs, and backlog
    ├── lld/                                   # Low-level designs for kernel and domain modules
    ├── regulatory/                            # Regulatory-specific supporting documents
    ├── archive/                               # Historical reviews, superseded plans, prompts, and snapshots
    └── *.md                                   # Active strategy, specification, TDD, and traceability docs
```

## Document Authority Order

1. `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` for canonical stack and implementation baseline
2. `UNIFIED_IMPLEMENTATION_PLAN.md` for canonical milestone sequencing and implementation strategy
3. `WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md` for the operational sprint and week cadence
4. `../finance-ghatana-integration-plan.md` for Capital Markets-specific Ghatana component reuse guidance
5. `epics/` and `lld/` for implementation scope, contracts, and design detail
6. `stories/STORY_INDEX.md` and milestone story files for backlog execution and sprint-level story allocation
7. `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, and `Claim_Traceability_Matrix.md` for source-backed factual and legal claims
8. `architecture/`, `c4/`, and `archive/` for supporting context only; archived plans do not override the active baseline

Detailed classification of normative versus supporting material: `docs/DOCUMENT_AUTHORITY_MAP.md`

## Key Architectural Decisions

1. **T1/T2/T3 Content Pack Taxonomy** — All jurisdiction-specific logic lives in packs, not platform code
2. **AI as Substrate (Principle 17)** — K-09 AI Governance is a mandatory intermediary for every AI/ML operation on the platform. No module calls an AI endpoint directly. Every model, prompt, and embedding is registered in the Model Registry, governed via HITL, audited via K-07, and subject to drift monitoring. AI enrichment is implicit and transparent — it runs as background infrastructure, enhancing every operation without requiring user configuration.
3. **Multi-Calendar Native** — K-15 provides pluggable `CalendarDate` support; active calendar systems (e.g., Bikram Sambat for Nepal, Hijri for Gulf markets) are declared by domain packs via T1 config — never hardcoded in the kernel
4. **K-05 Standard Event Envelope** — Every event carries `event_type`, `aggregate_id`, `causation_id`, `trace_id`, `timestamp` (ISO 8601 UTC), a `data` payload wrapper, an optional `calendar_date` field (type `CalendarDate`), and an optional `ai_annotation` field (type `AiAnnotation`) populated by K-09 when a registered model enriches the event
5. **10-Year Regulatory Retention** — Platform default; configurable per jurisdiction via T1 Config Packs; all audit/trade/event data retained for at minimum the jurisdictional requirement
6. **99.999% Availability** — Active-active multi-AZ with automatic failover
7. **ADR-011 Stack Standardization** — One canonical stack baseline aligned to finance ADRs and reusable Ghatana platform products
8. **Metadata-Driven Process Model** — Step order, human-task forms, routing policies, and operator choice lists are governed as versioned metadata, not embedded in service or portal code

## Epic Layers

| Layer               | Count | Scope                                                                                                                                                                            |
| ------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kernel (K)          | 19    | IAM, Config, Rules, Plugin, Event Bus, Observability, Audit, Data Governance, AI Governance, Deployment, API Gateway, Platform SDK, Multi-Calendar, Ledger, DTC, Resilience, DLQ |
| Domain (D)          | 14    | OMS, EMS, PMS, Market Data, Pricing, Risk, Compliance, Surveillance, Post-Trade, Regulatory Reporting, Reference Data, Corporate Actions, Client Money, Sanctions                |
| Workflow (W)        | 2     | Orchestration, Client Onboarding                                                                                                                                                 |
| Packs (P)           | 1     | Content Pack Certification Pipeline                                                                                                                                              |
| Testing (T)         | 2     | Integration Testing, Chaos Engineering                                                                                                                                           |
| Operations (O)      | 1     | Operator Console                                                                                                                                                                 |
| Regulatory (R)      | 2     | Regulator Portal, Incident Response & Escalation                                                                                                                                 |
| Platform Unity (PU) | 1     | Platform Manifest                                                                                                                                                                |

## Version

**v2.2** — March 10, 2026 | Post-ARB Review | Hardened for consistency, extensibility, metadata-driven runtime configuration, and future-proofing.
