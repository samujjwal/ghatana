# Connector Outage Runbook

## Trigger

- Connector health is `UNHEALTHY` or `UNKNOWN` for a production workspace.
- Connector command retries are increasing or DLQ depth grows.
- Provider outage, OAuth failure, rate-limit exhaustion, or network timeout blocks execution.

## Immediate Containment

1. Activate the connector or workspace kill switch if external execution may be unsafe.
2. Stop new launch/export/audience-sync actions for affected workspaces.
3. Preserve provider status, connector health, retry counts, DLQ entries, and correlation IDs.
4. Communicate that affected connector actions are locked, not successful.

## Investigation

1. Check provider status pages and OAuth/token health.
2. Review connector command failures by error category.
3. Confirm whether failures are tenant-specific, provider-wide, or configuration-specific.
4. Inspect DLQ entries for exhausted commands and idempotency keys.

## Recovery

1. Reauthenticate or rotate connector credentials when auth is the cause.
2. Wait for provider recovery when provider outage is confirmed.
3. Retry DLQ entries only after kill switch and health state are cleared.
4. Reconcile external IDs and campaign state after retries.

## Verification

- Connector health returns to `HEALTHY` or explicitly `DEGRADED`.
- Retry and DLQ depth stop growing.
- Blocked UI/API paths return clear locked/unavailable states.
- Reconciled external state matches internal command state.
