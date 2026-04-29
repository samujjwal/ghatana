# aep-operator-contracts

## Purpose

`products/aep/aep-operator-contracts` defines the execution contracts that all AEP pipeline operators must satisfy. It owns:

- `Pipeline` — immutable pipeline descriptor used by engine and registry
- `PipelineStageExecutor` — SPI interface all stage implementations must implement
- `PipelineExecutionEngine` — the top-level execution entry point that drives stage sequencing

This module is deliberately minimal. It contains only contracts and the driving engine — no persistence, no HTTP, no AI.

## Boundaries

- **Used by:** `aep-engine`, `aep-agent-runtime`, `orchestrator`
- **Must not depend on:** any other AEP module or product — it is a shared SPI layer
- **Stability guarantee:** breaking changes require a versioned migration (no silent API drift)

## Key types

| Type | Role |
|---|---|
| `Pipeline` | Immutable graph of stages with metadata |
| `PipelineStageExecutor` | SPI interface for stage implementations |
| `PipelineExecutionEngine` | Orchestrates stage sequencing and result aggregation |

## Verification

```bash
./gradlew :products:aep:aep-operator-contracts:test
```
