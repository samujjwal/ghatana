# aep-agent-runtime

## Purpose

`products/data-cloud/planes/action/agent-runtime` is the secure agent execution sandbox. It owns:

- Memory security management for agent contexts (`MemorySecurityManager`)
- PII/secret redaction for agent inputs and outputs (`RedactionPatternProvider`, `YamlRedactionPatternProvider`)
- Agent lifecycle — preparation, execution, teardown — within a sandboxed context

This module is the boundary between the pipeline orchestrator and individual agent implementations.

## Boundaries

- **Uses:** `platform:java:security` for redaction primitives; `aep-operator-contracts` for agent context models
- **Does not own:** registry look-ups — those are in `aep-registry` and `aep-central-runtime`
- **Does not own:** HTTP transport or gRPC serving — those belong to `server`
- **Redaction is fail-closed:** if pattern loading fails the agent invocation is rejected, not skipped

## Key classes

| Class | Role |
|---|---|
| `MemorySecurityManager` | Enforces memory access boundaries between agents |
| `YamlRedactionPatternProvider` | Loads YAML-defined redaction patterns at startup |
| `RedactionPatternProvider` | SPI interface for pluggable redaction pattern sources |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:agent-runtime:test
```
