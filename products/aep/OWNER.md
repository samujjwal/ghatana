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
| `platform-engine` | Core pipeline execution engine |
| `platform-core` | Platform facades and entry points |
| `platform-bundle` | All-in-one dependency bundle |
| `platform-analytics` | Pipeline observability and metrics |
| `platform-registry` | Operator registry |
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

- [ ] Rename `platform-*` modules to domain-reflecting names (e.g. `platform-engine` → `event-processing-engine`)
- [ ] Document rationale for `platform-*` naming if intentional
