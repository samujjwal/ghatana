# Kill Switch Runbook

## Trigger

- Unsafe connector execution, suspected data leak, budget overrun, compliance violation, or production gate failure.
- Operator needs to block external execution for a tenant, workspace, connector, or global scope.

## Immediate Containment

1. Determine the smallest safe scope: connector, workspace, tenant, or global.
2. Activate the kill switch with a human-readable reason and correlation ID.
3. Confirm campaign launch, audience sync, exports, and connector commands are blocked.
4. Notify on-call and product owner for the affected scope.

## Investigation

1. Review the trigger event, audit records, and latest workflow command.
2. Identify all affected tenants/workspaces and external resources.
3. Confirm no new connector commands are executing after activation.
4. Decide whether rollback, token revocation, or DSAR/privacy steps are required.

## Recovery

1. Fix the root cause and add regression coverage.
2. Reconcile any external provider state created before the kill switch.
3. Clear the kill switch only after approval from incident lead.
4. Resume queued commands deliberately; do not bulk replay without reconciliation.

## Verification

- Kill switch audit event records actor, scope, reason, and correlation ID.
- UI displays blocked state for affected actions.
- Connector command execution is blocked until switch removal.
- Post-recovery commands carry new audit and telemetry evidence.
