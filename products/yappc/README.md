# YAPPC - Product Development Platform

**Status:** Evidence-backed implementation and hardening  
**Last Updated:** 2026-05-26  
**Version:** 2.0

---

## 🎯 What is YAPPC?

YAPPC (Yet Another Platform Product Creator) is an **AI-powered product/app creation, visibility, health, and evolution platform**.

YAPPC follows an **8-phase creator lifecycle model**: Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve.

**YAPPC Creator Lifecycle = Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve**
**Kernel Product Lifecycle = dev → validate → test → build → package → deploy → verify**

When YAPPC generates or manages a lifecycle-governed ProductUnit, delivery execution is delegated to Kernel through public contracts. YAPPC does not directly execute Kernel lifecycle phases or mutate Kernel registry files.

Current implementation status is tracked in [docs/YAPPC_BACKLOG_PROGRESS.md](docs/YAPPC_BACKLOG_PROGRESS.md). P0, P1, and P2 audit items are complete in that ledger; release approval still depends on current-head CI evidence and the generated release evidence bundle.

The **Run** phase target drives an underlying SDLC lifecycle (Discover → Define → Design → Plan → Build → Test → Release → Deploy) managed by the AEP pipeline at `config/pipelines/lifecycle-management-v1.yaml`. Phase transitions, gate agents, and state persistence are defined in `config/agents/phase-transition-events.yaml`.

## Implementation Reality (2026-05-26)

| Dimension | Current Score | Notes |
|----------|---------------|-------|
| AI-Native Maturity | 7/10 | Intent grounding, prompt lifecycle, learning evidence, A/B testing, approval loops, and agent state are implemented; deeper autonomous adaptation remains governed and evidence-gated |
| Feature Completeness | 8/10 | P0/P1 lifecycle, Kernel handoff, Data Cloud truth, product-family, admin, and phase UX work is complete in the backlog ledger |
| Production Readiness | 7/10 | Security, privacy, governance, observability, release evidence, and CI proof paths exist; release approval still requires current-head evidence and environment-specific signoff |

### Verified Current State

- Intent, Shape, Validate, Generate, Run, Observe, Learn, and Evolve have backend/frontend evidence in the backlog ledger.
- Kernel ProductUnitIntent export and API handoff use Kernel public contract values and typed validation.
- Data Cloud-backed lifecycle truth, platform run status, evidence, governance, and tenant enforcement fail closed when dependencies degrade.
- Prompt template registry supports active-version control, rollback, scoring, and weight rebalancing.
- Release evidence now includes CI execution proof, scorecard dimensions, route/OpenAPI/client parity, E2E matrix mapping, a11y, performance, security, privacy, and governance checks.

### Known Limitations by Lifecycle Phase

| Phase | Current Maturity | Known Limitations |
|------|------------------|-------------------|
| Intent | High | Ongoing improvement area is broader model/prompt evaluation coverage |
| Shape | High | Ongoing improvement area is richer canvas/performance coverage with larger real projects |
| Validate | High | Ongoing improvement area is expanding policy fixtures and release-gate combinations |
| Generate | High | Ongoing improvement area is broader generated-app round-trip coverage |
| Run | Medium-High | Ongoing improvement area is environment-specific retry/rollback/promote E2E depth |
| Observe | Medium-High | Ongoing improvement area is richer dependency-specific operational drill coverage |
| Learn | Medium-High | Ongoing improvement area is longer-running feedback-loop evaluation |
| Evolve | Medium-High | Ongoing improvement area is broader impact-analysis and diff-review E2E coverage |

### Key Capabilities (Implemented + In Progress)

- **AI-Powered Code Generation** - Generate code from natural-language intent with assurance checks and evidence
- **Intelligent Scaffolding** - Multi-framework project generation (React, Node.js, Java, Python, etc.)
- **Knowledge Graph** - Semantic understanding of your codebase and dependencies
- **Visual Canvas** - Miro-like interface for product ideation and architecture
- **Automated Refactoring** - AI-driven code improvements and migrations
- **Full-Stack Observability** - Built-in monitoring, logging, and analytics
- **Agentic Workflows** - Multi-agent collaboration for complex tasks

---

## 🚀 Quick Start

### Prerequisites

- Java 21+ (Temurin recommended)
- Node.js 20+ with pnpm
- Docker & Docker Compose
- 8GB+ RAM recommended

**Verify your environment:**
```bash
./tools/scripts/verify-dev-environment.sh
```

### Run YAPPC

#### Option 1: Using Make (Recommended)

```bash
# First-time setup
make quick-start

# Start backend services
make start-backend

# Start frontend (in a new terminal)
make start-frontend
```

#### Option 2: Manual Setup

```bash
# Clone repository
git clone https://github.com/ghatana/ghatana.git
cd ghatana

# Start infrastructure (Redis, Postgres, etc.)
make -C products/yappc start-infra

# Start all backend services
make -C products/yappc start-backend

# Start frontend
cd products/yappc/frontend
pnpm install
pnpm dev
```

#### Option 3: Docker Only

```bash
# Start everything with Docker Compose
cd products/yappc/deployment/docker
docker compose --profile full up -d
```

Access YAPPC in unified local development at **http://localhost:7001**

**Available Services:**
- Frontend: http://localhost:7001
- API Gateway: http://localhost:7002
- GraphQL API: http://localhost:7002/graphql
- Java Backend: http://localhost:7003
- Standalone service/OpenAPI flows: http://localhost:8082

For detailed setup, see [docs/guides/DEPLOYMENT_GUIDE.md](docs/guides/DEPLOYMENT_GUIDE.md)

---

## 📚 Documentation

### Getting Started

- **[Architecture Overview](docs/ARCHITECTURE.md)** - System design and module structure
- **[Kernel Visibility and Control Plane](docs/architecture/KERNEL_VISIBILITY_AND_CONTROL_PLANE.md)** - YAPPC as visibility layer over Kernel
- **[Lifecycle/Kernel/Data Cloud/AEP Diagrams](docs/architecture/YAPPC_LIFECYCLE_KERNEL_DATA_CLOUD_AEP.md)** - Current implementation boundaries and evidence loops
- **[Creator Lifecycle to Kernel Mapping](docs/architecture/CREATOR_LIFECYCLE_TO_KERNEL_MAPPING.md)** - Phase mapping between YAPPC and Kernel
- **[Service Architecture (ADR-001)](docs/architecture/ADR-001-service-architecture.md)** - Service boundaries and responsibilities
- **[Developer Guide](docs/DEVELOPER_GUIDE.md)** - Contributing, coding standards, testing
- **[Ownership Boundaries](docs/OWNERSHIP_BOUNDARIES.md)** - YAPPC vs Kernel vs Data Cloud vs AEP vs platform ownership
- **[Deployment Guide](docs/guides/DEPLOYMENT_GUIDE.md)** - Running YAPPC locally and in production
- **[API Reference](docs/API_REFERENCE.md)** - Generated route reference from OpenAPI and the route manifest
- **[OpenAPI Contract](docs/api/openapi.yaml)** - HTTP contract validated against the route manifest

### Guides

- [Testing Guide](docs/guides/ACTIVEJ_TEST_MIGRATION_GUIDE.md) - Writing and running tests
- [Ollama Integration](docs/guides/OLLAMA_MANUAL_TESTING_GUIDE.md) - Local LLM setup
- [Terminology Reference](docs/guides/terminology-glossary.md) - YAPPC concepts and glossary
- [Release Evidence Bundle](docs/RELEASE_EVIDENCE_BUNDLE.md) - CI release evidence and scorecard artifacts

### Engineering

- [Backlog Progress](docs/YAPPC_BACKLOG_PROGRESS.md) - Evidence-backed audit progress ledger
- [Library Structure](frontend/libs/README.md) - Frontend library organization

---

## 🏗️ Architecture

YAPPC is organized into clear layers:

```
yappc/
├── core/                   # Java capabilities and services
├── frontend/web/           # React web app
├── frontend/libs/          # Frontend libraries
├── infrastructure/         # Data Cloud integration
├── kernel-bridge/          # Kernel integration tests/adapters
├── lifecycle/              # Readiness evidence contracts
├── scripts/                # Product validation and evidence scripts
└── docs/                   # Documentation
```

**Tech Stack:** Java 21 + ActiveJ, React, Data Cloud, Kernel public contracts, OpenAI/Ollama-compatible AI integrations
Quick Commands

See all available commands:
```bash
make help
```

Common workflows:
```bash
make dev              # Start full dev environment
make build            # Build all services
make test             # Run all tests
make lint             # Lint all code
make format           # Format all code
make clean            # Clean build artifacts
```

### 
See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for complete architecture details.

---

## 🔧 Development

### Building

```bash
# Build all modules
./gradlew clean build

# Run tests
./gradlew test

# Run focused YAPPC frontend regression checks
pnpm -C frontend/web test:regression
```

### Code Standards

- Java: Google Java Style, ActiveJ for async
- TypeScript: ESLint + Prettier
- Documentation: JavaDoc/JSDoc required
- Testing: EventloopTestBase for async tests

See [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) for complete guidelines.

---

## 🤝 Contributing

See [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) for:
- Code review checklist
- Commit conventions
- Pull request process

---

## Status

Canonical status and maturity evidence live in [docs/YAPPC_BACKLOG_PROGRESS.md](docs/YAPPC_BACKLOG_PROGRESS.md) and [docs/RELEASE_EVIDENCE_BUNDLE.md](docs/RELEASE_EVIDENCE_BUNDLE.md).

| Surface | Status | Evidence |
|--------|--------|----------|
| Kernel contract and handoff | Complete in backlog ledger | `YAPPC-P0-001` through `YAPPC-P0-004`, `YAPPC-P1-013`, `YAPPC-P1-026` |
| Lifecycle phase services and UI | Complete in backlog ledger | `YAPPC-P0-010` through `YAPPC-P0-017`, `YAPPC-P1-001` through `YAPPC-P1-040` |
| Security, privacy, governance | Complete in backlog ledger | `YAPPC-P2-035`, `YAPPC-P2-036`, `YAPPC-P2-037` |
| Release evidence and docs evidence | Complete in backlog ledger | `YAPPC-P2-039`, `YAPPC-P2-040` |
| frontend/web | Evidence-backed | `docs/TEST_SUITES.md` and `docs/YAPPC_BACKLOG_PROGRESS.md` |

**Recent:** P0, P1, and P2 audit items are complete in the evidence ledger; P3 documentation cleanup is in progress.
---

## 📝 License

Proprietary - © 2025-2026 Ghatana Inc.

---

**Maintained by:** YAPPC Core Team  
**Support:** #yappc-dev on Slack
