# DMOS Operations

## Purpose

This document defines the self-contained operational model for DMOS: environments, configuration, release gates, monitoring, incident response, connector operations, privacy operations, and production readiness.

## Operational Goals

DMOS operations must ensure:

- Safe production startup.
- Clear release readiness.
- Runtime visibility across UI, API, services, persistence, connectors, and AI providers.
- Fast diagnosis through correlation IDs and structured telemetry.
- Safe connector execution with retries, kill switches, and rollback/compensation.
- Privacy and security controls for PII, secrets, audit logs, and AI inputs/outputs.
- No production dependency on local/dev adapters or seed/demo data.

## Environments

| Environment | Purpose | Allowed Behavior |
|---|---|---|
| Local | Developer iteration | In-memory adapters allowed if clearly non-production |
| Test | Automated tests | Test fixtures and mocks allowed only inside test scope |
| Staging | Production-like verification | Real persistence, feature flags, telemetry, safe connector sandbox |
| Production | Customer use | Real adapters only or fail closed; no fake data or dev login |

## Startup Validation

Production startup must fail closed when:

- Database connection is missing.
- Migrations are not applied.
- Identity provider is not configured.
- PII key material is missing where PII is processed.
- Required feature-flag provider is unavailable.
- Required policy packs are not loaded.
- Default-deny policy is missing.
- Connector credentials are malformed.
- Telemetry configuration is invalid when required.
- Production profile attempts to load in-memory repositories.
- Test-only utilities are present in production classpath.

## Configuration Categories

| Category | Required Items |
|---|---|
| Identity | Token verifier, issuer/audience, session resolver |
| Persistence | JDBC URL, credentials, pool settings, migration flags |
| Security | PII HMAC/encryption keys, CORS/CSRF config, rate limits |
| Connectors | OAuth client IDs/secrets, redirect URIs, sandbox/prod mode |
| Feature Flags | Provider, defaults, kill switches, capability map |
| Observability | OTLP endpoint, service name, log level, metric export |
| Notifications | Provider config, retry/DLQ config |
| Retention | Data retention periods, DSAR workflow config |
| UI | API base URL, production auth mode, feature availability source |

### Required Production Environment Variables

Production bootstrap must validate these variables before accepting traffic:

| Area | Required Variables |
|---|---|
| Runtime | `DMOS_ENV=production`, `PORT` |
| Persistence | `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` |
| Policy/Auth | `DMOS_OPA_URL`, identity provider issuer/audience/session config |
| Privacy | `DMOS_PII_HMAC_KEY`, `DMOS_CONTACT_ENCRYPTION_KEY` |
| AI Governance | `DMOS_GOVERNED_AI_ENABLED=true`, `DMOS_KERNEL_AGENT_ENDPOINT` |
| Observability | `OTEL_EXPORTER_OTLP_ENDPOINT` or `OTEL_COLLECTOR_ENDPOINT`, `OTEL_SERVICE_NAME`, `LOG_LEVEL` |
| Google Ads | `GOOGLE_ADS_CLIENT_ID`, `GOOGLE_ADS_CLIENT_SECRET`, `GOOGLE_ADS_DEVELOPER_TOKEN` |
| UI | `VITE_API_BASE_URL`, production auth provider flags |

## Release Gates

A release is allowed only when:

- Build and typecheck pass.
- Product unit tests pass.
- Integration tests pass.
- API contract drift tests pass.
- Browser E2E critical journeys pass.
- Security/privacy tests pass.
- Static scan proves no production mocks/stubs/test utilities.
- Migration validation passes.
- Production bootstrap validation passes.
- Feature flags and kill switches are configured.
- Release checklist is completed.
- Rollback plan is documented.

## Observability

Every request and background operation must include:

- Correlation ID.
- Tenant ID and workspace ID where safe to log.
- Operation name.
- Outcome status.
- Error code.
- Duration.
- Retry count where applicable.
- Connector/provider name where applicable.
- Redacted user/principal identifier where required.

### Metrics

Minimum metrics:

- API request count, latency, and error rate by route family.
- Authorization failures.
- Feature-disabled attempts.
- Campaign lifecycle transitions.
- Approval queue size and decision latency.
- AI generation count, latency, error rate, and provider/model distribution.
- Connector execution success/failure/retry/DLQ counts.
- Rate limiting events.
- Database query latency for dashboard and reports.
- Migration status.
- DSAR/retention job outcomes.

### Logs

Logs must be structured and must not include:

- Access tokens.
- Refresh tokens.
- Raw secrets.
- Unredacted PII.
- Full prompts or outputs containing sensitive data unless explicitly redacted and allowed.
- Cross-tenant data in a single log event.

### Traces

Distributed traces should cover:

- UI request correlation.
- API servlet handling.
- Application service execution.
- Persistence calls.
- Connector calls.
- Kernel bridge/plugin calls.
- AI/model provider calls.
- Approval and audit event emission.

## Connector Operations

Connector execution requires:

- Explicit connector status.
- OAuth/token health.
- Sandbox/prod mode clarity.
- Per-workspace authorization.
- Outbox-backed execution.
- Retry policy.
- DLQ for exhausted failures.
- Kill switch.
- Idempotency.
- External ID storage.
- Rollback/compensation design.
- Manual reconciliation runbook.

Connector failures must surface:

- User-safe message.
- Retryability.
- Last attempted time.
- Correlation ID.
- External provider error category.
- Suggested next action.

## Incident Response

### Severity model

| Severity | Example | Response |
|---|---|---|
| SEV-1 | Tenant data leak, unauthorized connector execution | Stop affected paths, revoke tokens, incident lead, customer notification path |
| SEV-2 | Campaign launch failures, approval bypass, data corruption risk | Disable feature/connector, hotfix, audit impacted records |
| SEV-3 | Dashboard/report outage, degraded AI generation | Communicate degradation, rollback or disable provider |
| SEV-4 | Minor UI issue or non-critical telemetry gap | Normal fix flow |

### Incident checklist

1. Identify affected tenants/workspaces.
2. Capture correlation IDs and audit events.
3. Disable unsafe feature or connector if needed.
4. Preserve evidence.
5. Roll back or hotfix.
6. Reconcile external connector state.
7. Add regression tests.
8. Update runbook and postmortem.

## Privacy Operations

DMOS must support:

- PII discovery and classification.
- PII redaction in logs, traces, and AI action records.
- HMAC hashing or encryption for sensitive identifiers where required.
- Data retention policies.
- DSAR export.
- Right-to-delete/right-to-forget workflow.
- Connector token revocation.
- Audit trail retention policy.
- Legal hold exception handling where applicable.

## Backup and Recovery

Operational requirements:

- Database backups.
- Migration rollback or forward-fix plan.
- Recovery test schedule.
- Connector state reconciliation.
- Audit log durability.
- Recovery point and time objectives documented per environment.

## Operational Acceptance Criteria

DMOS is production-operable when:

- Production bootstrap fails closed for unsafe config.
- Release gates are automated.
- Dashboards and alerts cover critical flows.
- Every incident class has a runbook.
- Connector execution has retry, DLQ, kill switch, and reconciliation.
- Privacy operations are documented and tested.
- Production logs and traces are redacted.
- Rollback/fix-forward procedures are documented.

## Runbook Index

Every incident class below must have a concrete runbook before production release:

| Incident Class | Runbook |
|---|---|
| Campaign launch failure | `docs/runbooks/launch-failure.md` |
| Connector outage | `docs/runbooks/connector-outage.md` |
| Kill switch activation/removal | `docs/runbooks/kill-switch.md` |
| Data freshness incident | `docs/runbooks/data-freshness-incident.md` |
| Privacy incident | `docs/runbooks/privacy-incident.md` |
| DSAR request | `docs/runbooks/dsar-request.md` |
| Rollback/fix-forward | `docs/runbooks/rollback.md` |
| Release gate failure | `docs/runbooks/release-gate-failure.md` |

## Recovered Operational Requirements

## Connector Health Statuses

Connector health states:

| Status | Meaning |
|---|---|
| `HEALTHY` | Auth valid, last sync successful, error rate acceptable |
| `DEGRADED` | Partial failures, rate limits, delayed sync, or retrying commands |
| `UNHEALTHY` | Auth invalid, repeated failures, DLQ growth, or blocked external execution |
| `UNKNOWN` | Health check unavailable or never completed |

Operators must see:

- Authentication status.
- Rate-limit status.
- Last successful sync timestamp.
- Error rate by operation type.
- Queue depth.
- DLQ count.
- Last external error category.
- Active kill switch state.

## Data Retention Targets

Default retention expectations:

| Data Type | Default Retention |
|---|---|
| Campaign data | At least 2 years unless tenant policy differs |
| Audit logs | At least 7 years where compliance requires |
| PII | Per consent, regional law, and workspace policy |
| Analytics data | Configurable by workspace |
| Connector tokens | Until revoked/disconnected; never exposed raw |
| DSAR records | According to legal/compliance policy |
| Proposal/contract drafts | Per commercial/legal policy |

## DSAR Operational SLA Targets

| Request | Target |
|---|---|
| Export/know request | 30 days |
| Delete/anonymize request | 30 days where legally allowed |
| Correct request | 30 days |
| Restrict/limit request | 30 days |
| Marketing opt-out | 10 days or stricter regional requirement |

## Incident-Specific Kill Switch Actions

| Trigger | Action |
|---|---|
| Budget overrun | Pause campaign/workspace execution |
| Compliance violation | Block publishing and connector execution |
| Connector auth failure | Stop connector jobs and request reauth |
| Platform policy violation | Disable affected channel/action |
| Security incident | Tenant/global kill switch as appropriate |
| Data leakage suspicion | Stop affected scopes and preserve evidence |
| Rate-limit exhaustion | Throttle or pause connector queue |
