# Data Cloud Production Profile Checklist

Use this checklist before promoting any Data Cloud deployment to a non-local environment. Each item must be explicitly verified. Incomplete items must be tracked as open issues before go-live.

---

## Auth

- [ ] `DC_API_KEY` env var is set (non-empty) OR JWT provider is configured.
- [ ] `AuthFilter` is wired before any route handler in the launcher.
- [ ] `/health`, `/ready`, `/live`, and `/metrics` are excluded from auth (operator probes must not require credentials).
- [ ] JWT issuer/JWKS endpoint is reachable from the service at startup.
- [ ] API key list is sourced from secrets management (not hardcoded or in config files checked into VCS).

---

## Audit

- [ ] `AuditLoggingFilter` is active and wired into the HTTP pipeline.
- [ ] Audit records include `tenantId`, `userId`, `requestId`, `action`, `resource`, `timestamp`.
- [ ] Audit log is written to a **durable** store (DB or external audit sink) — not in-memory.
- [ ] Audit log retention policy is set and enforced.

---

## Policy / Governance

- [ ] Tenant autonomy levels are configured and enforced by `AutonomyPolicyFilter`.
- [ ] OPA policy endpoint is reachable (if OPA is in use).
- [ ] Rate limits are configured for all external-facing routes (`X-RateLimit-*` headers returned).
- [ ] Data product access policy is enforced at the subscription layer.

---

## Durable Stores

- [ ] `SPRING_PROFILES_ACTIVE` (or equivalent) is `sovereign` or `production` — NOT `local` or `test`.
- [ ] PostgreSQL connection string, pool size, and credentials are set.
- [ ] Flyway migrations ran successfully at startup (check logs for `Successfully applied N migration(s)`).
- [ ] Redis is reachable and `REDIS_URL` is configured.
- [ ] RocksDB data directory is on a persistent volume (not ephemeral container storage).
- [ ] S3 / cold-tier storage credentials are configured and bucket exists.
- [ ] ClickHouse or OpenSearch endpoint is configured (if analytics plane is enabled).
- [ ] In-memory store startup warning (`WARN … running with in-memory stores`) does NOT appear in logs.

---

## Tracing

- [ ] OpenTelemetry exporter endpoint is configured (`OTEL_EXPORTER_OTLP_ENDPOINT`).
- [ ] Service name, version, and environment attributes are set in the resource.
- [ ] Sampling rate is appropriate for production (e.g. 1% trace-id-ratio for high-traffic).
- [ ] Correlation ID (`X-Correlation-ID`) is propagated across service boundaries.

---

## Metrics

- [ ] `/metrics` endpoint returns Prometheus-format metrics.
- [ ] Prometheus scrape is configured and metrics are visible in Grafana.
- [ ] Business KPIs are emitting: entity writes, event appends, query latency, cache hit rate.
- [ ] Error rate alerts are configured.

---

## Operations

- [ ] `/ready` returns `200` only when all required subsystems are up (PostgreSQL, Redis, event log).
- [ ] `/health/detail` returns the state of each subsystem separately.
- [ ] Container resource limits (CPU/memory) are set.
- [ ] Log level is `INFO` (not `DEBUG`) in production.
- [ ] Structured JSON logging is enabled and includes `correlationId`, `tenantId` where applicable.
- [ ] Runbook (`docs/operations/RUNBOOK.md`) is up to date and linked from the deployment manifest.

---

## Security

- [ ] TLS is enforced on all inbound connections (no plain HTTP from external clients).
- [ ] Secrets are injected via environment variables or a secrets manager — not in `application.properties` or `local.properties`.
- [ ] Dependency vulnerability scan (`./gradlew dependencyCheckAnalyze`) passed with no CRITICAL/HIGH findings.
- [ ] OWASP suppression file (`config/owasp-suppressions.xml`) has been reviewed and is current.

---

## Sign-Off

| Role | Name | Date | Status |
| --- | --- | --- | --- |
| Platform Engineer | | | |
| Security Reviewer | | | |
| QA Lead | | | |
| Product Owner | | | |
