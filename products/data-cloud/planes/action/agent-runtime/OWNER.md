# Owner: Data Cloud Action Plane — Agent Runtime

**Parent:** Data Cloud Action Plane  
**Team:** Data Cloud Platform Team  
**Slack:** #data-cloud-platform  
**On-call:** Data Cloud on-call rotation

## Responsibility

Agent execution runtime currently co-located in the Data-Cloud Action Plane. It provides the execution environment for agents within pipelines and workflows while AEP remains the semantic owner for EventCloud, PatternSpec, EventOperator, EventOperatorCapability, pattern learning, and replay semantics.

**Boundary note:** Keep this implementation here for now, but do not describe AEP as retired into Data-Cloud. Data-Cloud may host compatibility/runtime code during migration; AEP owns adaptive event intelligence semantics and may later move to its canonical product tree.

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
- Current implementation is temporarily co-located under Data-Cloud Action Plane. Product docs must still preserve the AEP/Data-Cloud/EventCloud boundary from `docs/adr/ADR-aep-datacloud-eventcloud-boundaries.md`.

## Audit Status

- Last audited: 2026-05-19
- Source files: 194 | Test files: 30
- Coverage: Production readiness review completed (DC-P1-10)
