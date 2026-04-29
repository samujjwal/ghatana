# aep-scaling

## Purpose

`products/aep/aep-scaling` manages horizontal scaling and distributed processing for AEP runtimes. It owns:

- `ClusterManagementSystem` — provisions and deprovisions AEP runtime nodes based on load signals
- `ClusterManagementModels` — typed models for cluster state, node descriptors, and scaling decisions
- `DistributedPatternProcessor` — partitions pattern evaluation work across the cluster

## Boundaries

- **Uses:** `platform:java:observability` for scaling-decision audit logs; `aep-engine` for pipeline domain models
- **Does not own:** agent execution — that is `aep-agent-runtime`; compliance — that is `aep-compliance`
- Scaling decisions are logged at INFO level with structured JSON so they can be correlated in Grafana

## Key classes

| Class | Role |
|---|---|
| `ClusterManagementSystem` | Manages node lifecycle: add, remove, rebalance |
| `DistributedPatternProcessor` | Fan-out pattern evaluation across active nodes |
| `ClusterManagementModels` | Typed value objects for cluster and scaling state |

## Verification

```bash
./gradlew :products:aep:aep-scaling:test
```
