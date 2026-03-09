# DCMaar – Guardian App – Operations Guide

## 1. Overview

`@dcmaar/guardian` is an umbrella workspace for Guardian parental-control components (apps, backend, libs). Operations focus on building, testing, and deploying these components via the provided scripts.

## 2. Build & Test

- Use the root `package.json` scripts:
  - `build`, `build:dashboard`, `build:backend`, `build:prod`, `build:quick`.
  - `test`, `lint`, `type-check` to run checks across apps, backend, and libs.

## 3. Deployment

- Use `deploy` scripts:
  - `deploy`, `deploy:dev`, `deploy:prod` for environment-specific deployment.
  - `deploy:up`, `deploy:down`, `deploy:logs`, `deploy:status` for lifecycle operations.
- Docker helpers: `docker:build`, `docker:up`, `docker:down`, `docker:logs`, `docker:ps`.

## 4. Monitoring & Maintenance

- Monitor Guardian services via standard DCMaar observability (metrics, logs, traces) once deployed.
- Use workspace scripts to keep dependencies and tooling within supported versions.

## 5. Runbooks and Common Operational Tasks

### 5.1 Health Checks

- Backend health: `GET /health` (should return 200 and a JSON payload).
- Database connectivity: verify `psql $DATABASE_URL` and `docker compose logs db`.

### 5.2 Log Collection

- Use `pnpm deploy:logs` to stream logs for all services.
- For targeted troubleshooting, use `docker compose logs --follow <service>`.

### 5.3 Database Migrations

- All migrations live in `apps/backend/migrations` (Flyway / Liquibase depending on backend implementation).
- Typical flow:

```bash
# Generate migration
pnpm --filter @yappc/guardian-backend run migration:generate --name=add-policy-field

# Apply migrations (during deploy step)
pnpm deploy:prod  # scripts invoke migration steps
```

### 5.4 Backups and Restore

- PostgreSQL backups: schedule `pg_dump` to a secure storage location and verify restores periodically.
- ClickHouse backups: use the recommended ClickHouse backup/restore strategy for your version.

### 5.5 Incident Response Checklist (quick)

1. Identify impacted services via `docker compose ps` and logs.
2. Check resource usage on hosts (disk, memory, CPU).
3. Check connectivity to DB and Redis.
4. If the backend is unhealthy, tail the backend logs and check recent deployment or migration changes.
5. If required, roll back to last known-good release (container image tag) and re-run migrations in a staging environment first.

## 6. Maintenance Windows

- Schedule maintenance windows for schema changes and major upgrades. Notify stakeholders and prepare rollback steps.

This operations guide now contains practical runbook items for common tasks and incident handling.
