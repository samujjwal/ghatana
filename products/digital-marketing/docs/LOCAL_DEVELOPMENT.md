# DMOS Local Development Guide

## Prerequisites

- Java 21
- PostgreSQL 15+
- Node.js 20+
- pnpm 9+

## Setup

### 1. Clone the Repository

```bash
git clone https://github.com/ghatana/digital-marketing.git
cd digital-marketing
```

### 2. Start PostgreSQL

```bash
# Using Docker
docker run -d --name dmos-postgres -p 5432:5432 \
  -e POSTGRES_PASSWORD=dmos \
  -e POSTGRES_DB=dmos \
  postgres:15
```

### 3. Run Database Migrations

```bash
cd products/digital-marketing
../../gradlew :dm-persistence:flywayMigrate
```

### 4. Build the Backend

```bash
../../gradlew build
```

### 5. Start the Backend API

```bash
../../gradlew :dm-api:run
```

The API will be available at `http://localhost:8080`

### 6. Start the UI

```bash
cd ui
pnpm install
pnpm dev
```

The UI will be available at `http://localhost:5174`

## Demo Mode

To start with seed data:

```bash
export DEMO_MODE=true
../../gradlew :dm-api:run
```

This will load demo tenant, workspace, users, and approvals.

## Running Tests

### Backend Tests

```bash
../../gradlew test
```

### UI Tests

```bash
cd ui
pnpm test
```

### E2E Tests

```bash
cd ui
pnpm test:e2e
```

### Accessibility Tests

```bash
cd ui
pnpm test:e2e:a11y
```

## Debugging

### Backend Debugging

```bash
../../gradlew :dm-api:run --debug-jvm
```

Attach debugger to port 5005.

### UI Debugging

The UI runs with Vite dev server with HMR enabled.

## Common Issues

### Port Conflicts

- Backend: Change port in `dm-api/src/main/resources/application.conf`
- UI: Change port in `ui/vite.config.ts`

### Database Connection

- Verify PostgreSQL is running: `docker ps`
- Check connection string in environment variables

### Build Failures

- Clean build: `../../gradlew clean build`
- Clear Gradle cache: `rm -rf ~/.gradle/caches/`
