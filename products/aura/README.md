# Aura — Personal AI Intelligence Platform

**Product Owner:** @ghatana/aura-team  
**Status:** Pre-production (Design & Architecture Phase)  
**Stack:** TypeScript / React 19 / Java 21 / ActiveJ / Prisma

## Purpose

**Aura** is a consumer AI product that acts as a personal intelligence engine. It provides personalised recommendations, style intelligence, knowledge graph management, and long-horizon task execution — combined into a single, adaptive consumer experience.

> **Current status:** The architecture and product specifications are complete. Engineering implementation begins per [`Aura_Engineering_Sprint_Plan_6_Months.md`](docs/Aura_Engineering_Sprint_Plan_6_Months.md).

## Key Product Concepts

| Concept | Description |
|---------|-------------|
| **Personal Intelligence Engine** | Per-user model that learns preferences and context over time |
| **Knowledge Graph** | User-owned graph linking people, products, events, and styles |
| **Shade/Color Ontology** | Deep taxonomy for style and aesthetic classification |
| **Ingredient Knowledge Graph** | Structured ingredient data for product intelligence |
| **Recommendation Engine** | Hybrid collaborative + content-based + LLM recommendations |
| **Long-Horizon Task Execution** | Multi-step agent tasks spanning days/weeks |

## Architecture

See [`docs/Aura_System_Architecture.md`](docs/Aura_System_Architecture.md) and [`docs/Aura_C4_Architecture_Diagrams.md`](docs/Aura_C4_Architecture_Diagrams.md) for the full architecture.

```
Consumer App (React / RN)
        │
API Layer (Java ActiveJ — high-perf domain logic)
        │
Personal Intelligence Engine
        ├── Knowledge Graph (Neo4j / custom)
        ├── Recommendation Service
        ├── Task Execution Agent (GAA framework)
        └── LLM Integration (libs:ai-integration)
        │
Data Layer (PostgreSQL + Prisma, Redis cache)
```

## Key Documentation

| Document | Purpose |
|----------|---------|
| [`Aura_PRD_v1.md`](docs/Aura_PRD_v1.md) | Product Requirements |
| [`Aura_Canonical_Platform_Specification.md`](docs/Aura_Canonical_Platform_Specification.md) | Canonical platform spec |
| [`Aura_Master_Platform_Specification.md`](docs/Aura_Master_Platform_Specification.md) | Master technical spec |
| [`Aura_System_Architecture.md`](docs/Aura_System_Architecture.md) | System architecture overview |
| [`Aura_Technical_Stack_Blueprint.md`](docs/Aura_Technical_Stack_Blueprint.md) | Technology choices & rationale |
| [`Aura_API_Contracts.md`](docs/Aura_API_Contracts.md) | API contracts |
| [`Aura_Database_Schema_Prisma.md`](docs/Aura_Database_Schema_Prisma.md) | Database schema |
| [`Aura_Product_Roadmap_Epics.md`](docs/Aura_Product_Roadmap_Epics.md) | Roadmap |
| [`Aura_Engineering_Sprint_Plan_6_Months.md`](docs/Aura_Engineering_Sprint_Plan_6_Months.md) | 6-month sprint plan |
| [`Aura_24_Month_Strategy.md`](docs/Aura_24_Month_Strategy.md) | Long-term strategy |

## Prerequisites

- Node.js 18+ / pnpm 10+
- Java 21
- PostgreSQL
- Redis
- Docker + Docker Compose

## Conventions

- Backend: Java 21 + ActiveJ Promise (no CompletableFuture, no Reactor)
- State management: Jotai + TanStack Query (no zustand)
- Agent tasks: GAA framework from `libs:agent-framework`; extend `BaseAgent`
- Tests: `EventloopTestBase` for all async Java tests
- Styling: Tailwind CSS + `@ghatana/design-system`
