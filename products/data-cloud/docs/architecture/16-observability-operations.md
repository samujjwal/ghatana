# Observability and Operations

See [06-backend-architecture.md](06-backend-architecture.md), [15-security-architecture.md](15-security-architecture.md), and [`diagrams/observability-flow.mmd`](diagrams/observability-flow.mmd).

## Signal Inventory

| Signal | Evidence | Scope |
|---|---|---|
| Structured logging | `platform-launcher/observability/DataCloudLogger.java`, widespread SLF4J usage | runtime and worker logs |
| Metrics facade | `MetricsCollector`, `MetricsCollectorFactory`, `DataCloudMetrics.java` | platform-wide metrics abstraction |
| Prometheus exposure | `DataCloudHttpLauncherBootstrap.java`, `/metrics`, Helm values, K8s annotations | standalone HTTP runtime |
| Health probes | `/health`, `/health/detail`, `/ready`, `/live`, DB subsystem probes | runtime health and readiness |
| Worker metrics | `FeatureStoreIngestLauncher` uses `MetricsCollector` | ingest pipeline |
| Plugin metrics | `platform-api/plugin/PluginRegistryImpl.java`, `DataCloudMetrics.java` | plugin lifecycle and health |

## Implemented

- HTTP runtime exposes health, readiness, liveness, info, and metrics endpoints.
- Standalone HTTP bootstrap creates a Prometheus registry-backed `MetricsCollector`.
- K8s manifests and Helm values are already aligned to scrape `/metrics`.
- Feature ingest uses metrics plus explicit logging.
- Agent registry and event publishers log operational outcomes and failures.

## Inferred

- The product is observability-aware at the code level, but not all subsystems appear equally instrumented end to end.

## Missing

- I did not find explicit distributed tracing wiring in the inspected product-local runtime path.
- There is no single product runbook mapping critical workflows to logs/metrics/alerts, beyond general operational docs like `DR_RUNBOOK.md` and `SCALING_GUIDE.md`.

## Recommended

- Add an observability coverage matrix for the top five workflows:
  - entity CRUD,
  - event ingest/query,
  - pipeline save,
  - feature ingestion,
  - agent registry persistence.

## Operational Diagnostics

| Capability | Implemented | Gap |
|---|---|---|
| Health detail | `/health/detail` plus subsystem suppliers | no single operator reference for meanings |
| Startup readiness | K8s startup/readiness/liveness probes | depends on HTTP transport enabled |
| Metrics | Prometheus/Micrometer | tracing visibility unclear |
| Error logs | broad SLF4J coverage | structured field consistency not guaranteed |
| Plugin health | plugin registry health model | runtime exposure path incomplete |

## Blind Spots

| Blind Spot | Evidence | Impact |
|---|---|---|
| Cross-service traces | no obvious tracer bootstrap in inspected startup path | hard root-cause analysis across dependencies |
| Security middleware status as metric | bootstrap only logs optional security state | silent exposure risk |
| Contract drift as runtime signal | only bash drift script found | no deploy-time visibility |

## Observability Findings

| Finding | Evidence | Impact |
|---|---|---|
| Ops endpoints are stronger than tracing story | launcher + K8s/Helm | good baseline, weak distributed diagnostics |
| Metrics abstractions are broad and reusable | `MetricsCollector`, `DataCloudMetrics` | good foundation |
| Blind spots align with architecture drift areas | split modules and duplicated contracts | hard to detect wrong-path usage |
