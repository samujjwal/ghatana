# Owner: AEP Orchestrator

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Multi-tenant orchestration layer. Manages pipeline execution lifecycle across tenants.

- Agent execution routing and scheduling
- Multi-tenant isolation and resource management
- Pipeline lifecycle management (start, pause, resume, stop)
- Execution guard behavior (discovery-only agent blocking)
- Checkpoint and recovery coordination

## Key Components

| Component | Purpose |
|-----------|---------|
| `AgentExecutionService` | Agent execution routing and guards |
| `PipelineOrchestrator` | Pipeline lifecycle management |
| `TenantIsolationManager` | Multi-tenant resource boundaries |
| `CheckpointManager` | State checkpoint and recovery |

## Critical Tests

| Test | Purpose | CI Status |
|------|---------|-----------|
| `AgentExecutionServiceTest` | Validates discovery-only agent blocking | **PINNED** (AEP-A2, AEP-A8) |
| `RegistryAndFactoryTest` | SPI behavior validation | Has disabled tests (AEP-A7) |

## Dependencies

- `platform:java:workflow`
- `platform:java:agent-core`
- `products:aep:aep-registry`
- `products:aep:aep-engine`

## Audit Status

- Last audited: 2026-04-29
- AgentExecutionServiceTest: PASS, pinned in CI
- Source files: 92 | Test files: 30
