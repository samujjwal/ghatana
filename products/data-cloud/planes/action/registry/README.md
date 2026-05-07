# aep-registry

## Purpose

`products/data-cloud/planes/action/registry` is the agent and pipeline registry for AEP. It stores, retrieves, and validates:

- Agent descriptors (capabilities, execution mode, registration mode)
- Pipeline definitions and their stage graphs
- Pattern repositories for analytics pattern matching

This is the **authoritative source of truth** for what agents and pipelines are known to the AEP runtime.

## Boundaries

- **Uses:** `platform:java:database` for persistence; `aep-engine` for pipeline domain models
- **Does not own:** agent execution — that is `aep-agent-runtime` and `aep-central-runtime`
- **Does not own:** HTTP transport — that is `aep-api`
- **Does not own:** observability instrumentation — that is `aep-observability`

## Key classes

| Class | Role |
|---|---|
| `AepCentralRegistryService` (in `aep-central-runtime`) | Canonical service façade over registry repositories |
| `PatternRepository` | Read/write for analytics pattern descriptors |
| `PipelineRepository` / `InMemoryPipelineRepository` | Pipeline CRUD with in-memory dev profile |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:registry:test
```
