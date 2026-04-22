# Owner: AEP (Agentic Event Processor)

**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation  
**Architecture lead:** Platform Engineering Lead  
**Boundary audit score:** 8/10 (2026-01-19) — World Class Report Phase 1 complete  

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
| `aep-event-cloud` | Data-Cloud bridge plugin for event processing |
| `aep-operator-contracts` | Shared operator and pipeline contracts |
| `aep-analytics` | Pipeline observability and metrics |
| `aep-registry` | Operator, pipeline and agent registry |
| `aep-observability` | AEP-specific tracing and orchestration instrumentation |
| `orchestrator` | Multi-tenant orchestration |
| `kernel-bridge` | Product kernel integration boundary |
| `server` | HTTP server entry point |

> Note: `aep-agent` was merged into `aep-registry` (2026-03-22). `aep-runtime` and the
> former `aep-runtime-core` compatibility facade have both been retired and removed.

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
- [x] Delete all empty `platform-*` directories (Phase 1 complete, 2026-01-19)
- [x] Wire `AgentController` to canonical `EventCloudAgentStore` (Phase 2 complete, 2026-01-19)
- [x] Fix OpenAPI contract port alignment (8081 → 8090, Phase 3 complete, 2026-01-19)
- [x] `AepRegistryModule` defaults to `DataCloudPipelineRegistryClientImpl` (Phase 2 complete)
- [x] UI operator cockpit (Phase 4 complete; monitoring, costs, run detail, and operator navigation shipped in `products/aep/ui`)
- [x] Learning loop real implementation (Phase 5 complete; episode reflection, evaluation gates, review queue, and policy promotion shipped in `products/aep/server`)
- [x] Observability/health checks (Phase 6 complete; `/health/deep`, `/metrics/slo`, alert rules, dashboards, and CI verification shipped for both server and embedded/library deployments)
