# DMOS Deployment Guide

## Prerequisites

- Java 21
- PostgreSQL 15+
- Node.js 20+
- pnpm 9+
- Docker (for containerized deployment)

## Environment Variables

Production deployments must provide real service endpoints and secrets. Localhost defaults are allowed only for local/test runs.

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DMOS_ENV` | Runtime environment; production must be `production` | development | Yes |
| `DATABASE_URL` | PostgreSQL connection string | - | Yes |
| `DATABASE_USER` | Database username | - | Yes |
| `DATABASE_PASSWORD` | Database password | - | Yes |
| `DMOS_OPA_URL` | OPA/policy service URL | - | Yes in production |
| `DEMO_MODE` | Enable demo mode with seed data | false | No |
| `KERNEL_ENDPOINT` | AgentOrchestrator endpoint | http://localhost:8080 | No |
| `DMOS_KERNEL_AGENT_ENDPOINT` | Governed AI/kernel endpoint | - | Yes in production |
| `DMOS_GOVERNED_AI_ENABLED` | Governed AI enablement; cannot be false in production | true | Yes in production |
| `ENCRYPTION_KEY` | AES-256 encryption key for sensitive data | - | Yes |
| `HMAC_KEY` | HMAC-SHA256 key for hashing | - | Yes |
| `DMOS_PII_HMAC_KEY` | Privacy-safe identifier HMAC key | - | Yes in production |
| `DMOS_CONTACT_ENCRYPTION_KEY` | Contact PII encryption key | - | Yes in production |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Primary telemetry endpoint | - | Yes in production |
| `GOOGLE_ADS_CLIENT_ID` | Google Ads OAuth client ID | - | Required when connector enabled |
| `GOOGLE_ADS_CLIENT_SECRET` | Google Ads OAuth client secret | - | Required when connector enabled |
| `GOOGLE_ADS_DEVELOPER_TOKEN` | Google Ads developer token | - | Required when connector enabled |
| `VITE_API_BASE_URL` | UI API base URL | - | Yes for UI deployment |

## Deployment Steps

### 1. Build the Application

```bash
cd products/digital-marketing
../../gradlew build
```

### 2. Run Database Migrations

```bash
../../gradlew :dm-persistence:flywayMigrate
```

### 3. Start the Application

```bash
../../gradlew :dm-api:run
```

### 4. Build and Start the UI

```bash
cd ui
pnpm install
pnpm build
pnpm preview
```

## Docker Deployment

```bash
docker-compose -f docker-compose.yml up -d
```

## Health Checks

- Application health: `GET /health`
- Database health: `GET /health/db`
- Kernel health: `GET /health/kernel`

## Rollback Procedure

1. Stop the application
2. Restore database from backup
3. Revert to previous application version
4. Run database rollback if needed
5. Start the application
6. Verify health checks

## Troubleshooting

### Database Connection Issues

- Verify `DATABASE_URL` is correct
- Check PostgreSQL is running
- Ensure network connectivity

### Migration Failures

- Check Flyway schema history table
- Verify migration scripts are valid SQL
- Review logs for specific error messages

### UI Build Errors

- Clear node_modules and reinstall: `rm -rf node_modules && pnpm install`
- Verify Node.js version: `node --version`
