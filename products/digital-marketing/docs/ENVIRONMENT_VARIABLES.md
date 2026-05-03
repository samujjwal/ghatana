# DMOS Environment Variables Reference

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
| `JWT_SECRET` | JWT signing secret | - | Yes |
| `API_KEY_HASH_SALT` | Salt for API key hashing | - | Yes |

## Application

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DEMO_MODE` | Enable demo mode with seed data | false | No |
| `KERNEL_ENDPOINT` | AgentOrchestrator endpoint | http://localhost:8080 | No |
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
