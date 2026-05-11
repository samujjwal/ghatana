# Campaign Launch Failure Runbook

## Trigger

- Campaign enters `LAUNCH_FAILED`, `EXTERNAL_EXECUTION_BLOCKED`, or remains in `LAUNCH_RUNNING` beyond the workflow SLA.
- Google Search launch/export does not produce an external ID mapping.
- Preflight, approval, connector command, retry, or DLQ checks fail.

## Immediate Containment

1. Pause further launch attempts for the affected tenant/workspace.
2. Confirm whether the workspace or connector kill switch is active.
3. Preserve the campaign ID, workflow command ID, correlation ID, preflight result, and approval snapshot.
4. If any external campaign was partially created, stop connector execution before retrying.

## Investigation

1. Inspect the durable command/workflow state for approval, preflight, connector command, retry count, DLQ status, rollback status, and external ID mapping.
2. Check audit events for the same correlation and causation IDs.
3. Confirm the latest approval decision and immutable snapshot.
4. Confirm Google Ads credentials, account access, budget config, targeting config, and connector health.

## Recovery

1. If preflight failed, remediate the failed checklist item and create a new launch command.
2. If connector execution failed before external creation, retry the durable command with the same idempotency key.
3. If external creation partially succeeded, reconcile external ID mapping before retry.
4. If rollback is required, execute the rollback workflow and verify `ROLLED_BACK` state.

## Verification

- Campaign state is `PENDING_LAUNCH`, `LAUNCHED`, `LAUNCH_FAILED`, `EXTERNAL_EXECUTION_BLOCKED`, or `ROLLED_BACK`, never a silent success.
- Audit trail contains approval, command, connector, and final state events.
- External ID mapping is present for launched campaigns.
- Dashboard/reporting surfaces show the final state and correlation ID.
