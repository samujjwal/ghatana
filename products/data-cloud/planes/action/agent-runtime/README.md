# aep-agent-runtime

## Purpose

`products/data-cloud/planes/action/agent-runtime` is the secure agent execution sandbox currently co-located in Data-Cloud. It implements AEP-owned AgentOperator runtime behavior while the repo keeps the Data-Cloud/AEP code split in transition.

- Memory security management for agent contexts (`MemorySecurityManager`)
- PII/secret redaction for agent inputs and outputs (`RedactionPatternProvider`, `YamlRedactionPatternProvider`)
- Agent lifecycle: preparation, execution, teardown within a sandboxed context
- Replay-safe agent execution records under `com.ghatana.agent.runtime.replay`

This module is the boundary between the pipeline orchestrator and individual agent implementations.

## Boundaries

- **Uses:** `platform:java:security` for redaction primitives; `products:data-cloud:planes:action:operator-contracts` for current co-located AEP operator contracts
- **Owns for now:** secure runtime implementation, replay snapshots, memory integration, dispatch, and safety checks
- **Does not own:** Data-Cloud storage semantics
- **Does not own:** registry look-ups — those are in `aep-registry` and `aep-central-runtime`
- **Does not own:** HTTP transport or gRPC serving — those belong to `server`
- **Redaction is fail-closed:** if pattern loading fails the agent invocation is rejected, not skipped

## Key classes

| Class | Role |
|---|---|
| `MemorySecurityManager` | Enforces memory access boundaries between agents |
| `YamlRedactionPatternProvider` | Loads YAML-defined redaction patterns at startup |
| `RedactionPatternProvider` | SPI interface for pluggable redaction pattern sources |
| `AgentExecutionRecord` | Captures model, prompt, retrieval, tool, and output snapshots for replay |
| `AgentReplayPlanner` | Chooses recorded-output or opt-in live replay behavior |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:agent-runtime:test
```
