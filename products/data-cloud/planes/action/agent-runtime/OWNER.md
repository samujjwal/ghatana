# Owner: Data Cloud Action Plane — Agent Runtime

**Parent:** Data Cloud Action Plane  
**Team:** Data Cloud Platform Team  
**Slack:** #data-cloud-platform  
**On-call:** Data Cloud on-call rotation

## Responsibility

Agent execution runtime for Data Cloud Action Plane. Provides the execution environment for agents within pipelines and workflows. This module is the product-specific agent runtime implementation within Data Cloud.

**Note:** This module was historically part of AEP (Agentic Event Processor). AEP is now the runtime implementation within Data Cloud Action Plane, not a standalone product.

- Agent execution context management
- Agent lifecycle (initialize, execute, cleanup)
- Agent resource isolation
- Execution sandboxing
- Agent result handling
- Agent memory plane integration
- Agent governance and kill-switch

## Key Components

| Component | Purpose |
|-----------|---------|
| `AgentRuntime` | Execution environment |
| `AgentContext` | Per-execution context |
| `AgentSandbox` | Resource isolation |
| `AgentDispatcher` | Request routing |
| `AgentMemoryClient` | Memory plane integration |

## Dependencies

- `platform:java:agent-core` — Generic agent primitives (shared platform)
- `platform:java:tool-runtime` — Tool execution framework
- `products:data-cloud:planes:action:operator-contracts` — Action Plane contracts
- `products:data-cloud:planes:action:registry` — Agent registry

## Migration Note

- **DEPRECATED:** `products:aep:aep-operator-contracts` — Migrated to `products:data-cloud:planes:action:operator-contracts`
- The AEP standalone product boundary has been retired; all AEP functionality is now part of Data Cloud Action Plane.

## Audit Status

- Last audited: 2026-05-19
- Source files: 194 | Test files: 30
- Coverage: Production readiness review completed (DC-P1-10)
