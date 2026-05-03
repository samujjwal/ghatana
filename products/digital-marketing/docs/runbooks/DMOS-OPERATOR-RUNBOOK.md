# DMOS Operator Runbook

**Product**: Digital Marketing Operating System (DMOS)  
**Version**: 1.0.0  
**Last Updated**: 2026-05-01  
**Escalation Path**: Platform Engineering → DMOS Product Lead → On-Call SRE

---

## Table of Contents

1. [Service Overview](#service-overview)
2. [Starting DMOS Locally](#starting-dmos-locally)
3. [Health and Readiness](#health-and-readiness)
4. [Common Alerts and Remediation](#common-alerts-and-remediation)
5. [Kill Switch Activation](#kill-switch-activation)
6. [Connector OAuth Reconnection](#connector-oauth-reconnection)
7. [Durable Workflow Recovery](#durable-workflow-recovery)
8. [Database Operations](#database-operations)
9. [Build and Deployment](#build-and-deployment)
10. [Configuration Reference](#configuration-reference)

---

## Service Overview

DMOS is a multi-tenant digital marketing management system built on ActiveJ. It provides:
- AI-assisted intake, strategy, and budget recommendation
- Google Ads connector with OAuth token management
- Durable campaign workflow orchestration
- Website audit, lead scoring, and content generation

All API routes are served by `DmosHttpServer` (entry point). The default port is `8080`.

---

## Starting DMOS Locally

```bash
# From repo root
./gradlew :products:digital-marketing:dm-api:run

# Or with Docker Compose (if compose file is available):
docker compose -f docker-compose.yml up dmos-api
```

**Environment variables for local development** (see `DmosProductConfig` for full list):
```
DMOS_ENV=development
DMOS_AI_ENABLED=true
DMOS_GOOGLE_ADS_CONNECTOR_ENABLED=false   # disable real ads connector locally
DMOS_KILL_SWITCH_ENABLED=true
```

---

## Health and Readiness

**Readiness check:**
```bash
curl -s http://localhost:8080/health/ready
# Expected: HTTP 200 {"status":"ready"}
```

**Liveness check:**
```bash
curl -s http://localhost:8080/health/live
# Expected: HTTP 200 {"status":"ok"}
```

If the readiness check returns 503, check:
1. Database connectivity (connection pool exhausted or schema migration pending)
2. Required environment variables (`DMOS_ENV`, `DMOS_VERSION`)
3. Application logs for startup errors

---

## Common Alerts and Remediation

### Alert: `dmos.service.errors` rate > 1% for 5 minutes

**Cause**: Application errors in service layer  
**Remediation**:
1. Check structured logs for `[DMOS] * failed` entries with tenant and operation context
2. Identify which operation is failing (`dmos.operation` MDC key)
3. If connector-related: check OAuth token validity (see [Connector OAuth Reconnection](#connector-oauth-reconnection))
4. If database-related: check connection pool metrics and schema status

### Alert: `dmos.workflow.stuck` — workflow step not progressing for > 10 minutes

**Cause**: Durable workflow step timeout or executor failure  
**Remediation**:
1. Query the workflow store for `status = EXECUTING` records older than 10 minutes
2. Check the application log for the workflow instance ID
3. If the external connector is unavailable, activate the kill switch to pause execution

### Alert: `dmos.connector.auth_failure` — OAuth token invalid

**Cause**: Token expired and refresh failed  
**Remediation**: See [Connector OAuth Reconnection](#connector-oauth-reconnection)

### Alert: High response latency > 2s on `/v1/workspaces/*/strategy`

**Cause**: Strategy generation is CPU-intensive at high concurrency  
**Remediation**:
1. Check thread pool utilization
2. Verify no blocking I/O is happening on the event loop (check logs for `Promise.ofBlocking` timeout warnings)
3. Scale out the DMOS API instance if under sustained load

---

## Kill Switch Activation

The kill switch (`DmosKillSwitch`) stops all active campaign execution for a workspace or tenant.

**Activate via API:**
```bash
# Per-workspace kill switch
curl -X POST http://localhost:8080/v1/workspaces/{workspaceId}/kill-switch \
  -H "X-Tenant-ID: {tenantId}" \
  -H "X-Principal-ID: {adminPrincipal}" \
  -H "X-Idempotency-Key: ks-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Emergency: connector auth failure"}'

# Expected: HTTP 200 {"status":"KILLED","killedAt":"...","reason":"..."}
```

**Verify kill switch is active:**
```bash
curl http://localhost:8080/v1/workspaces/{workspaceId}/kill-switch \
  -H "X-Tenant-ID: {tenantId}"
```

**Reactivate after resolution:**
```bash
curl -X DELETE http://localhost:8080/v1/workspaces/{workspaceId}/kill-switch \
  -H "X-Tenant-ID: {tenantId}" \
  -H "X-Idempotency-Key: ks-reactivate-$(date +%s)"
```

---

## Connector OAuth Reconnection

When a Google Ads connector loses OAuth credentials:

1. **Identify the affected connector:**
   ```bash
   curl http://localhost:8080/v1/workspaces/{workspaceId}/connectors \
     -H "X-Tenant-ID: {tenantId}"
   ```

2. **Suspend the connector:**
   ```bash
   curl -X POST http://localhost:8080/v1/workspaces/{workspaceId}/connectors/{connectorId}/suspend \
     -H "X-Tenant-ID: {tenantId}" \
     -H "X-Idempotency-Key: suspend-$(date +%s)"
   ```

3. **Tenant re-connects via UI or direct re-authorization API** (OAuth flow initiated by product UI)

4. **Reactivate the connector after successful reconnection:**
   ```bash
   curl -X POST http://localhost:8080/v1/workspaces/{workspaceId}/connectors/{connectorId}/reactivate \
     -H "X-Tenant-ID: {tenantId}" \
     -H "X-Idempotency-Key: reactivate-$(date +%s)"
   ```

---

## Durable Workflow Recovery

If a workflow is stuck in `EXECUTING` state:

1. Query for stuck workflows (application-level query, exact SQL depends on DB schema)
2. Check application logs for the workflow instance ID
3. If the root cause is resolved (e.g., connector reconnected), resume by re-sending the trigger event
4. If unrecoverable, mark as `FAILED` via the command store and trigger a compensating workflow

---

## Database Operations

**Schema migration:**
```bash
./gradlew :products:digital-marketing:dm-api:flywayMigrate
```

**Backup (production — coordinate with DBA team):**
```bash
pg_dump -Fc --schema=dmos dmos_production > dmos_$(date +%Y%m%d).dump
```

**Restore (staging only):**
```bash
pg_restore -d dmos_staging dmos_$(date +%Y%m%d).dump
```

---

## Build and Deployment

**Build all DMOS modules:**
```bash
./gradlew :products:digital-marketing:dm-domain:build \
           :products:digital-marketing:dm-application:build \
           :products:digital-marketing:dm-api:build
```

**Run all checks (quality gates + tests):**
```bash
./gradlew --no-build-cache \
  :products:digital-marketing:dm-domain:check \
  :products:digital-marketing:dm-application:check \
  :products:digital-marketing:dm-api:check
```

**Quality gates enforced by `dmos-quality-gates.gradle.kts`:**
- `validateNoProductionStubs` — no TODO/FIXME/STUB/UnsupportedOperationException in production code
- `validateNoTestTheatre` — no `assertTrue(true)` or `@Disabled` in tests
- `validateNoMockingFrameworkUsage` — no Mockito in DMOS tests
- `validateNoSecurityAntiPatterns` — no hardcoded secrets or disabled SSL verification

---

## Configuration Reference

| Environment Variable | Default | Description |
|---|---|---|
| `DMOS_ENV` | `development` | Deployment environment name |
| `DMOS_VERSION` | `local` | Product version string |
| `DMOS_AI_ENABLED` | `true` | Enable AI-powered generation features |
| `DMOS_GOOGLE_ADS_CONNECTOR_ENABLED` | `true` | Enable Google Ads connector runtime |
| `DMOS_KILL_SWITCH_ENABLED` | `true` | Enable kill-switch enforcement |
| `DMOS_ROLLBACK_ENABLED` | `true` | Enable rollback/compensating-action workflow |
| `DMOS_OBSERVABILITY_ENABLED` | `true` | Enable structured logging and metrics |
| `DMOS_MAX_INTAKE_FIELDS` | `50` | Max fields per intake submission |
| `DMOS_MAX_AD_COPY_VARIANTS` | `5` | Max ad copy variants per request |
| `DMOS_CONNECTOR_TOKEN_REFRESH_MINUTES` | `5` | OAuth token refresh window (minutes) |
| `DMOS_WORKFLOW_STEP_TIMEOUT_SECONDS` | `30` | Durable workflow step timeout (seconds) |
| `DMOS_MAX_BUDGET_CAP` | `100000` | Maximum budget recommendation cap |

---

*For escalation or incident support, contact the DMOS product team or file an incident in the on-call rotation.*
