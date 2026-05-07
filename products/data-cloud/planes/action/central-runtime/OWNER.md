# Owner: AEP Central Runtime

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Centralized runtime coordination for AEP. Manages shared runtime resources across the AEP cluster.

- Central runtime state management
- Cluster-wide coordination
- Shared resource scheduling
- Runtime topology management

## Key Components

| Component | Purpose |
|-----------|---------|
| `CentralRuntimeCoordinator` | Cluster coordination |
| `RuntimeTopologyManager` | Node topology tracking |

## Dependencies

- `platform:java:runtime`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- Module size: Small (6 items)
