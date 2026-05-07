# AEP Multi-Region Deployment

This directory contains active-active overlays for regional AEP deployments.

## Layout

- `eastus/`: overlay for the primary US region
- `westeurope/`: overlay for the secondary EU region

Each overlay extends the baseline manifests in `products/data-cloud/planes/action/k8s` and adds:

- region-scoped ingress hosts
- explicit region identity via `AEP_REGION`
- Data Cloud replica hints via `AEP_DATA_CLOUD_REPLICA_MODE`
- topology spread constraints so replicas do not collapse onto a single zone

## Apply

```bash
kustomize build products/data-cloud/planes/action/k8s/multi-region/eastus | kubectl apply -f -
kustomize build products/data-cloud/planes/action/k8s/multi-region/westeurope | kubectl apply -f -
```

## Failover Drill

1. Deploy both regions with the matching Helm or Kustomize regional values.
2. Confirm `/ready` is green in both regions.
3. Shift ingress or DNS traffic away from the primary region.
4. Confirm `/api/v1/metrics/pipelines` and `/api/v1/costs/summary` remain available.
5. Restore traffic and verify replication lag dashboards return to normal.