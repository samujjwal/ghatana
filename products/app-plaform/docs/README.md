# Siddhanta — Capital Markets Operating System

> A **jurisdiction-neutral, multi-domain operating system** designed as an extensible platform. Capital markets (Siddhanta) is the first instantiation — not the architectural boundary.

## Architecture at a Glance

| Dimension               | Choice                                                                                                                                                                                                                     |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Architecture Style**  | 7-layer microservices, Event Sourcing, CQRS, Domain Pack Architecture                                                                                                                                                                                |
| **Extensibility Model** | Domain Packs + T1 Config / T2 Rules (OPA-Rego) / T3 Executable Content Packs                                                                                                                                                              |
| **Runtime Metadata**    | Versioned process templates, task schemas, routing policies, and value catalogs resolved through K-02 and consumed by W-01 / K-13                                                                                          |
| **Calendar**            | Dual-Calendar Native (Bikram Sambat + Gregorian)                                                                                                                                                                           |
| **Tech Stack**          | Java 21 + ActiveJ, Node.js LTS + TypeScript + Fastify + Prisma, Python 3.11 + FastAPI, React 18 + Tailwind CSS + Jotai + TanStack Query, Kafka 3+, PostgreSQL 15+, TimescaleDB, Redis 7+, OpenSearch, Kubernetes, Istio |
| **Security**            | Zero-Trust, OPA policy-as-code, mTLS, HashiCorp Vault                                                                                                                                                                      |
| **Availability Target** | 99.999% (5.26 min downtime/year)                                                                                                                                                                                           |
| **Data Retention**      | 10 years regulatory minimum                                                                                                                                                                                                |

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
finance/
├── README.md                                  # Top-level orientation and authority map
├── adr/                                       # Architecture Decision Records (ADR-001 through ADR-011)
├── architecture/                              # Master architecture suite and sectioned specifications
├── c4/                                        # C4 context, container, component, and code views
├── plans/                                     # Current execution plan and delivery program plan
├── epics/                                     # 42 implementation epics plus dependency matrix and glossary
├── stories/                                   # Story index, milestone story packs, and backlog
├── lld/                                       # Low-level designs for kernel and domain modules
├── docs/                                      # Strategy, specifications, TDD expansions, and claim packs
├── regulatory/                                # Regulatory-specific supporting documents
└── archive/                                   # Historical reviews, prompts, and snapshots
```

## Document Authority Order

1. `UNIFIED_IMPLEMENTATION_PLAN.md` for consolidated implementation strategy and timeline
2. `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` for canonical stack and implementation baseline
3. `finance-ghatana-integration-plan.md` for detailed Ghatana platform component reuse strategy
4. `plans/CURRENT_EXECUTION_PLAN.md` for detailed milestone execution
5. `epics/` and `lld/` for implementation scope, contracts, and design detail
6. `stories/STORY_INDEX.md` and milestone story files for backlog execution
7. `docs/`, `c4/`, and historical archive content for supporting context unless they explicitly override nothing above

Detailed classification of normative versus supporting material: `docs/DOCUMENT_AUTHORITY_MAP.md`

## Key Architectural Decisions

1. **T1/T2/T3 Content Pack Taxonomy** — All jurisdiction-specific logic lives in packs, not platform code
2. **Dual-Calendar Native** — Bikram Sambat alongside Gregorian at the data layer
3. **K-05 Standard Event Envelope** — Every event carries `event_type`, `aggregate_id`, `causality_id`, `trace_id`, `timestamp_bs`, `timestamp_gregorian`, and a `data` payload wrapper
4. **10-Year Regulatory Retention** — All audit/trade/event data retained for 10 years minimum
5. **99.999% Availability** — Active-active multi-AZ with automatic failover
6. **ADR-011 Stack Standardization** — One canonical stack baseline aligned to finance ADRs and reusable Ghatana platform products
7. **Metadata-Driven Process Model** — Step order, human-task forms, routing policies, and operator choice lists are governed as versioned metadata, not embedded in service or portal code

## Epic Layers

| Layer               | Count | Scope                                                                                                                                                                           |
| ------------------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kernel (K)          | 19    | IAM, Config, Rules, Plugin, Event Bus, Observability, Audit, Data Governance, AI Governance, Deployment, API Gateway, Platform SDK, Dual-Calendar, Ledger, DTC, Resilience, DLQ |
| Domain (D)          | 14    | OMS, EMS, PMS, Market Data, Pricing, Risk, Compliance, Surveillance, Post-Trade, Regulatory Reporting, Reference Data, Corporate Actions, Client Money, Sanctions               |
| Workflow (W)        | 2     | Orchestration, Client Onboarding                                                                                                                                                |
| Packs (P)           | 1     | Content Pack Certification Pipeline                                                                                                                                             |
| Testing (T)         | 2     | Integration Testing, Chaos Engineering                                                                                                                                          |
| Operations (O)      | 1     | Operator Console                                                                                                                                                                |
| Regulatory (R)      | 2     | Regulator Portal, Incident Response & Escalation                                                                                                                                |
| Platform Unity (PU) | 1     | Platform Manifest                                                                                                                                                               |

## Version

**v2.2** — March 10, 2026 | Post-ARB Review | Hardened for consistency, extensibility, metadata-driven runtime configuration, and future-proofing.
