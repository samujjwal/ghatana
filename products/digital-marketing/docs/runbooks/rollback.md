# Rollback Runbook

## Trigger

- Release gate miss, production regression, failed campaign launch workflow, migration issue, connector incident, or privacy/security incident requires rollback or fix-forward.

## Immediate Containment

1. Stop unsafe deploys and freeze affected workflow/connector paths.
2. Decide rollback versus fix-forward with incident lead and owning engineer.
3. Preserve release SHA, migration version, feature flags, workflow IDs, and correlation IDs.
4. Activate kill switch when external execution or data processing is unsafe.

## Investigation

1. Identify changed code, schema, config, feature flags, and generated artifacts.
2. Determine whether database changes are backward compatible.
3. Check whether external provider state needs compensation.
4. Confirm customer-facing impact and affected tenants/workspaces.

## Recovery

1. Roll back application version when schema and contracts allow it.
2. Prefer forward migration repair when data shape cannot safely roll back.
3. Reconcile external connector state before resuming commands.
4. Re-run release gates and focused regression tests.

## Verification

- Health/readiness checks pass.
- Release gate and affected product tests pass.
- Audit and telemetry confirm no new unsafe executions.
- Incident record captures rollback decision, evidence, and residual risk.
