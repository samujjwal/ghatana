# Virtual Org — AI-Powered Virtual Organisation Platform

**Product Owner:** @ghatana/virtual-org-team  
**Status:** Active (Implementation Phase 2)  
**Stack:** Java 21 + ActiveJ + Kotlin

## Purpose

**Virtual Org** models an AI-powered virtual organisation where autonomous agents occupy roles (engineer, manager, analyst, etc.), collaborate on tasks, and execute workflows. It is the flagship consumer of the GAA (Generic Adaptive Agent) framework from `libs:agent-framework`.

See [`IMPLEMENTATION_SUMMARY.md`](IMPLEMENTATION_SUMMARY.md) and [`PHASE_2_IMPLEMENTATION_GUIDE.md`](PHASE_2_IMPLEMENTATION_GUIDE.md) for current implementation status.

## Architecture

```
Launcher  →  AgentOrchestrator  →  AgentRegistry (product-scoped)
                               →  WorkflowEngine → OperatorPipeline
                               →  IntegrationLayer → External APIs

engine/        — Core GAA pipeline (perceive, reason, act, capture, reflect)
modules/agent/ — Agent domain model (roles, goals, memory)
modules/framework/ — Virtual-org-specific framework (AgentRegistry, BaseAgent)
modules/operator-adapter/ — Adapts platform operators to virtual-org DSL
modules/integration/ — External service integrations
modules/workflow/ — Workflow definition and execution
```

### Modules

| Module | Package | Purpose |
|--------|---------|---------|
| `modules/agent/` | `com.ghatana.virtualorg.agent` | Agent domain: roles, goals, episodic memory |
| `modules/framework/` | `com.ghatana.virtualorg.framework` | `AgentRegistry` (product SPI), `BaseAgent` extension |
| `modules/workflow/` | `com.ghatana.virtualorg.workflow` | Workflow DSL, step execution |
| `modules/operator-adapter/` | `com.ghatana.virtualorg.operator` | Adapts `libs:agent-framework` operators |
| `modules/integration/` | `com.ghatana.virtualorg.integration` | External API adapters |
| `engine/` | `com.ghatana.virtualorg.engine` | Orchestration engine + GAA lifecycle |
| `launcher/` | `com.ghatana.virtualorg.launcher` | ActiveJ `Launcher` entrypoint |

## Prerequisites

- Java 21
- Docker (for PostgreSQL + Redis)
- `./gradlew` (Gradle 9.2.1 wrapper)

## Build & Run

```bash
# Build all modules
./gradlew :products:virtual-org:build

# Run tests
./gradlew :products:virtual-org:test

# Format
./gradlew :products:virtual-org:spotlessApply

# Launch (Docker)
docker-compose up -d
```

## Known Issues

See [`COMPILATION_ISSUES.md`](COMPILATION_ISSUES.md) for any outstanding compilation problems and their resolutions.

## Architecture Decisions

- `AgentRegistry` in `modules/framework` is **product-scoped** — it is NOT the platform SPI (`com.ghatana.agent.registry.AgentRegistry`). Cross-product use is forbidden.
- All agents MUST extend `BaseAgent` from `libs:agent-framework`.
- All async operations MUST use `ActiveJ Promise` — no `CompletableFuture` or Reactor.
- Tests MUST extend `EventloopTestBase`.

## GAA Lifecycle

Agent execution follows the standard GAA pipeline:

```
PERCEIVE → REASON → ACT → CAPTURE → REFLECT
```

Each stage is an `ActiveJ Promise`-based async step. Reflection is fire-and-forget and never blocks the user-facing response.
