# orchestrator

## Purpose

`products/data-cloud/planes/action/orchestrator` drives multi-agent workflow execution within AEP. It owns:

- `AepOrchestrationModule` — ActiveJ module that wires orchestration services into the runtime
- `AepRegistryModule` — ActiveJ module that wires registry connectivity for the orchestrator
- `DataCloudPipelineRegistryClientImpl` — typed client for fetching pipeline definitions from the Data Cloud registry
- `CatalogRegistryContractAdapter` — translates between the AEP catalog contract and the internal pipeline descriptor

The orchestrator is responsible for sequencing agents, managing inter-agent data flow, and handling orchestration errors.

## Boundaries

- **Uses:** `aep-central-runtime` for agent resolution; `aep-operator-contracts` for pipeline models; `aep-agent-runtime` for agent execution
- **Does not own:** pipeline storage — that is `aep-registry`; HTTP endpoints — those are in `server`

## Key classes

| Class | Role |
|---|---|
| `AepOrchestrationModule` | Wires all orchestration services into the ActiveJ DI container |
| `DataCloudPipelineRegistryClientImpl` | Fetches pipeline definitions from Data Cloud |
| `CatalogRegistryContractAdapter` | Contract → internal model adapter |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:orchestrator:test
```
