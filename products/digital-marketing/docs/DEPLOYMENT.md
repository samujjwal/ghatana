# DMOS Deployment Guide

## Prerequisites

- Java 21
- PostgreSQL 15+
- Node.js 20+
- pnpm 9+
- Docker (for containerized deployment)

## Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DATABASE_URL` | PostgreSQL connection string | - | Yes |
| `DATABASE_USER` | Database username | - | Yes |
| `DATABASE_PASSWORD` | Database password | - | Yes |
| `DEMO_MODE` | Enable demo mode with seed data | false | No |
| `KERNEL_ENDPOINT` | AgentOrchestrator endpoint | http://localhost:8080 | No |
| `ENCRYPTION_KEY` | AES-256 encryption key for sensitive data | - | Yes |
| `HMAC_KEY` | HMAC-SHA256 key for hashing | - | Yes |

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
