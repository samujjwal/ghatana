# aep-central-runtime

## Purpose

`products/data-cloud/planes/action/central-runtime` is the AEP central registry and agent materialization layer. It owns:

- `AepCentralRegistryService` — the canonical service façade for agent and pipeline look-ups from all consumers (server, orchestrator, kernel-bridge)
- `AgentMaterializer` — converts a stored agent descriptor into an executable agent instance ready for the runtime

This module is the **single look-up point** for any component that needs to resolve an agent or pipeline by ID.

## Boundaries

- **Replaces:** the deprecated `aep-runtime-core` (deleted); all references must point here
- **Uses:** `aep-registry` for storage; `aep-engine` for pipeline domain models
- **Does not own:** execution — that belongs to `aep-agent-runtime`; HTTP serving — that belongs to `server`

## Key classes

| Class | Role |
|---|---|
| `AepCentralRegistryService` | Read/write registry API used by all runtime consumers |
| `AgentMaterializer` | Descriptor → executable agent instance |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:central-runtime:test
```
