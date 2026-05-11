# DMOS Environment Variables Reference

This file is an operational reference. Canonical production readiness rules live in `docs/canonical/05-OPERATIONS.md`.

## Production Minimums

Production must fail closed when any required production variable is missing or points to local/dev infrastructure.

| Variable | Description | Production Required |
|----------|-------------|---------------------|
| `DMOS_ENV` | Runtime environment; production must be exactly `production` | Yes |
| `PORT` | API listen port | Yes |
| `DATABASE_URL` | PostgreSQL connection string | Yes |
| `DATABASE_USER` | Database username | Yes |
| `DATABASE_PASSWORD` | Database password | Yes |
| `DMOS_OPA_URL` | OPA/policy service URL for fail-closed authorization | Yes |
| `DMOS_PII_HMAC_KEY` | HMAC key for deterministic privacy-safe identifiers | Yes |
| `DMOS_CONTACT_ENCRYPTION_KEY` | Encryption key for contact PII | Yes |
| `DMOS_GOVERNED_AI_ENABLED` | Must remain `true` in production | Yes |
| `DMOS_KERNEL_AGENT_ENDPOINT` | Governed AI/kernel endpoint; no localhost production default | Yes |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Primary OTLP telemetry endpoint | Yes, unless `OTEL_COLLECTOR_ENDPOINT` is set |
| `OTEL_COLLECTOR_ENDPOINT` | Alternate telemetry collector endpoint | Yes, unless `OTEL_EXPORTER_OTLP_ENDPOINT` is set |
| `OTEL_SERVICE_NAME` | Service name for traces and metrics | Yes |
| `GOOGLE_ADS_CLIENT_ID` | Google Ads OAuth client ID | Required when Google Ads launch/export is enabled |
| `GOOGLE_ADS_CLIENT_SECRET` | Google Ads OAuth client secret | Required when Google Ads launch/export is enabled |
| `GOOGLE_ADS_DEVELOPER_TOKEN` | Google Ads developer token | Required when Google Ads launch/export is enabled |
| `VITE_API_BASE_URL` | Browser API base URL | Yes for UI deployment |

## Database

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DATABASE_URL` | PostgreSQL connection string | - | Yes |
| `DATABASE_USER` | Database username | - | Yes |
| `DATABASE_PASSWORD` | Database password | - | Yes |
| `DATABASE_POOL_SIZE` | Connection pool size | 10 | No |

## Security

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `ENCRYPTION_KEY` | AES-256 encryption key for sensitive data | - | Yes |
| `HMAC_KEY` | HMAC-SHA256 key for hashing | - | Yes |
| `DMOS_PII_HMAC_KEY` | HMAC key for contact identifiers | - | Yes in production |
| `DMOS_CONTACT_ENCRYPTION_KEY` | Encryption key for contact PII | - | Yes in production |
| `JWT_SECRET` | JWT signing secret | - | Yes |
| `API_KEY_HASH_SALT` | Salt for API key hashing | - | Yes |

## Application

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DMOS_ENV` | Runtime environment | development | Yes in production |
| `DEMO_MODE` | Enable demo mode with seed data | false | No |
| `KERNEL_ENDPOINT` | AgentOrchestrator endpoint | http://localhost:8080 | No |
| `DMOS_KERNEL_AGENT_ENDPOINT` | Governed AI/kernel endpoint | - | Yes in production |
| `DMOS_GOVERNED_AI_ENABLED` | Enable governed AI path | true | Yes in production |
| `DMOS_OPA_URL` | OPA/policy service URL | - | Yes in production |
| `KERNEL_ENABLED` | Enable Kernel integration | true | No |
| `PORT` | API server port | 8080 | No |

## Observability

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry endpoint | - | No |
| `OTEL_SERVICE_NAME` | Service name for traces | dmos-api | No |
| `LOG_LEVEL` | Logging level | INFO | No |

## Google Ads Connector

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `GOOGLE_ADS_CLIENT_ID` | OAuth client ID | - | Yes |
| `GOOGLE_ADS_CLIENT_SECRET` | OAuth client secret | - | Yes |
| `GOOGLE_ADS_DEVELOPER_TOKEN` | Developer token | - | Yes |

## UI

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `VITE_API_BASE_URL` | API base URL | http://localhost:8080 | No |
| `VITE_ENABLE_DEMO_MODE` | Enable demo mode in UI | false | No |

## Session

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SESSION_EXPIRY_MINUTES` | Session expiry time | 30 | No |
| `SESSION_REFRESH_MINUTES` | Session refresh interval | 5 | No |

## Privacy

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DSR_RETENTION_DAYS` | Data subject request retention | 90 | No |
| `SUPPRESSION_RETENTION_DAYS` | Suppression entry retention | 365 | No |
