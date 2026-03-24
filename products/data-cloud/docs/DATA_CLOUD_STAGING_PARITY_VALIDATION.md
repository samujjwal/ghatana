# Data-Cloud Staging Parity Validation

> Status: Automation ready
> Prepared on: 2026-03-24
> Scope: Live staging smoke execution for canary readiness

## Goal

Run the existing smoke matrix against a deployed staging Data-Cloud instance so canary promotion decisions are based on a real environment rather than localhost defaults.

## CI Wiring

The `smoke-e2e` job in `.github/workflows/data-cloud-ci.yml` already maps the required CI inputs:

| Script env | CI source |
|---|---|
| `DC_BASE_URL` | `vars.DC_STAGING_BASE_URL` |
| `DC_TENANT_ID` | `vars.DC_SMOKE_TENANT_ID` |
| `DC_API_TOKEN` | `secrets.DC_SMOKE_API_TOKEN` |
| `SMOKE_TIMEOUT` | hard-coded to `10` in workflow |

## Manual Execution

```bash
export DC_BASE_URL="https://staging.data-cloud.example"
export DC_TENANT_ID="smoke_tenant"
export DC_API_TOKEN="<token>"
export SMOKE_TIMEOUT="10"

bash products/data-cloud/scripts/run-smoke-e2e.sh
```

## Expected Outcome

1. `health:liveness` and `health:readiness` return PASS.
2. Entity CRUD, events, analytics, voice catalog, pipelines, agents, and governance checks return zero FAIL.
3. Any WARN findings are reviewed before canary promotion.

## Evidence to Capture

1. The workflow run URL or terminal transcript.
2. The exact staging base URL used.
3. The deployment image tag validated.
4. Any WARN findings and the disposition for each.

## Blocking Inputs

1. A deployed staging target reachable from CI or the operator machine.
2. Populated `DC_STAGING_BASE_URL`, `DC_SMOKE_TENANT_ID`, and `DC_SMOKE_API_TOKEN` values.
