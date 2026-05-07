# Owner: AEP Agent Runtime

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Agent execution runtime for AEP. Provides the execution environment for agents within pipelines.

- Agent execution context management
- Agent lifecycle (initialize, execute, cleanup)
- Agent resource isolation
- Execution sandboxing
- Agent result handling

## Key Components

| Component | Purpose |
|-----------|---------|
| `AgentRuntime` | Execution environment |
| `AgentContext` | Per-execution context |
| `AgentSandbox` | Resource isolation |

## Dependencies

- `platform:java:agent-core`
- `platform:java:tool-runtime`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- Source files: 194 | Test files: 30
