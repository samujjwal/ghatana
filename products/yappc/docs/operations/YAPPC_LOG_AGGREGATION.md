# YAPPC Log Aggregation Standard

## Objective
Provide centralized, queryable, and retained logs for YAPPC runtime and operational diagnostics.

## Stack
- Collector: Fluent Bit (Kubernetes daemonset).
- Aggregation store: Loki.
- Visualization: Grafana Loki panels.

## Config Sources
- Kubernetes collector config: products/yappc/deployment/kubernetes/base/fluent-bit.yaml.
- Promtail sidecar/reference config: products/yappc/deployment/monitoring/logging/promtail-yappc.yml.
- Log dashboard: monitoring/grafana/dashboards/yappc/yappc-log-observability.json.

## Retention Policy
- Hot logs: 14 days.
- Warm searchable logs: 90 days.
- Long-term compliance archive: 365 days where required.

## Search and Indexing Guidelines
- Every log entry must include service, product, and correlationId where available.
- Error logs must include failure category and tenant context when applicable.
- Auth/security logs must be retained and searchable for incident investigations.

## Operational Checks
- Verify Fluent Bit daemonset healthy on all nodes.
- Verify Loki ingest latency is below 30 seconds.
- Verify dashboard query for {product="yappc"} returns current logs.
- Verify on-call runbook references log dashboard for incident triage.
