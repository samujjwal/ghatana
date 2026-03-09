# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) for the Ghatana platform.
ADRs document significant architectural decisions made during the AEP + Event-Cloud stabilization.

## Index

| ADR | Title | Status | Phase |
|-----|-------|--------|-------|
| [ADR-001](ADR-001-typed-agent-framework.md) | Six-Type Agent Framework with Generic TypedAgent Interface | Accepted | 2 |
| [ADR-002](ADR-002-dag-pipeline-execution.md) | DAG-Based Pipeline Execution with Topological Sort | Accepted | 1 |
| [ADR-003](ADR-003-four-tier-event-cloud.md) | Four-Tier Event-Cloud Storage with Automatic Lifecycle | Accepted | 1 |
| [ADR-004](ADR-004-activej-framework.md) | ActiveJ as Core Async and DI Framework | Accepted | 0 |
| [ADR-005](ADR-005-multi-tenant-isolation.md) | Multi-Tenant Isolation via Thread-Local TenantContext | Accepted | 4 |
| [ADR-006](ADR-006-checkpoint-recovery.md) | Checkpoint-Based Pipeline Recovery with Exactly-Once Semantics | Accepted | 4 |
| [ADR-007](ADR-007-observability-stack.md) | Micrometer + OpenTelemetry Dual Observability Stack | Accepted | 4 |
| [ADR-008](ADR-008-datacloud-spi.md) | Data-Cloud SPI with ServiceLoader Discovery | Accepted | 1 |
| [ADR-009](ADR-009-configuration-first.md) | Configuration-First Architecture with YAML and JSON Schema | Accepted | 3 |
| [ADR-010](ADR-010-flyway-migrations.md) | Flyway for Database Schema Management | Accepted | 4 |

## ADR Format

Each ADR follows this template:
- **Status**: Proposed → Accepted → Deprecated → Superseded
- **Context**: What prompted the decision
- **Decision**: What was decided and how
- **Rationale**: Why this approach was chosen
- **Consequences**: Trade-offs and implications
- **Alternatives Considered**: What else was evaluated
