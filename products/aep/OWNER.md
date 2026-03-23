# Owner: AEP (Agentic Event Processor)

**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation  
**Architecture lead:** Platform Engineering Lead  
**Boundary audit score:** 5/10 (2026-03-21) — active remediation in progress  

## Responsibility

AEP is the **event-driven agent orchestration runtime** for the Ghatana platform. It:

- Processes events from the Data-Cloud event backbone
- Dispatches events to registered agent pipelines
- Manages pipeline execution lifecycle (start, pause, checkpoint, recover)
- Provides the DAG-based pipeline execution engine
- Hosts the central Operator Catalog (all operators across all products)

**Domain boundary:** AEP owns event intake, routing, and pipeline execution. It does NOT own agent implementations (those live in product cores) or event store (lives in Data-Cloud).

## Modules

| Module | Purpose |
|--------|---------|
| `aep-engine` | Core pipeline execution engine |
| `aep-runtime-core` | Runtime facades and entry points |
| `aep-operator-contracts` | Shared operator and pipeline contracts |
| `aep-analytics` | Pipeline observability and metrics |
| `aep-registry` | Operator and pipeline registry |
| `orchestrator` | Multi-tenant orchestration |
| `server` | HTTP server entry point |

## Consumers

| Consumer | Usage |
|----------|-------|
| YAPPC | Agent workflow execution |
| Data-Cloud | Event processing pipelines |
| Virtual-Org | Organisation orchestration |
| App-Platform | Application-level pipelines |

## Open Remediation Items (BDY-7)

- [x] Rename `platform-*` modules to domain-reflecting AEP-owned names
- [x] Remove direct product consumers of deprecated AEP platform-* module paths
