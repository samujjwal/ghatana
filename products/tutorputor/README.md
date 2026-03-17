# TutorPutor — AI-Powered Tutoring Platform

**Product Owner:** @ghatana/tutorputor-team  
**Status:** Active  
**Stack:** TypeScript / React 19 / Java 21 / ActiveJ

## Purpose

**TutorPutor** is an adaptive AI tutoring platform that delivers personalised learning content to students and provides administrative tools for educators. It uses the ghatana AI integration layer (`libs:ai-integration`) for content generation and adaptive curriculum planning.

## Architecture

```
tutorputor-student (React)  ─┐
tutorputor-admin   (React)  ─┤→  tutorputor API (Java / ActiveJ)  →  PostgreSQL
tutorputor-mobile  (RN)     ─┤→  content-explorer service
api-gateway        (Node)   ─┘→  @ghatana/ai (LLM integration)
```

### Module Map

| Path | Type | Purpose |
|------|------|---------|
| `apps/tutorputor-student/` | React app | Student-facing learning UI |
| `apps/tutorputor-admin/` | React app | Educator / admin dashboard |
| `apps/tutorputor-web/` | React app | Marketing & public-facing site |
| `apps/tutorputor-mobile/` | React Native | Mobile student app |
| `apps/api-gateway/` | Node.js | API gateway + BFF layer |
| `apps/content-explorer/` | React app | Content browsing & curation tool |
| `contracts/` | Protobuf + OpenAPI | API contracts |
| `libs/` | Shared TS/Java libs | Domain model, utilities |
| `content/` | Markdown / structured data | Curriculum content |
| `tools/tutorputor-domain-loader/` | CLI tool | Batch-loads domain content |

## Prerequisites

- Node.js 18+ / pnpm 10+
- Java 21
- PostgreSQL
- Docker + Docker Compose

## Local Development

```bash
# Start all services
./run-dev.sh

# Start without seed data
./run-dev-no-seed.sh

# Seed content
./run-seed.sh

# Build Java backend
./gradlew build

# Frontend only
pnpm install
pnpm --filter tutorputor-student dev
pnpm --filter tutorputor-admin dev
```

## Testing

See [`docs/guidelines/TESTING.md`](docs/guidelines/TESTING.md) for full testing conventions.

```bash
# Backend tests
./gradlew test

# Frontend tests
pnpm test

# E2E
pnpm --filter tutorputor-student test:e2e
```

## Key Documentation

| Document | Path |
|----------|------|
| Architecture & Design | [`docs/architecture/DESIGN_ARCHITECTURE.md`](docs/architecture/DESIGN_ARCHITECTURE.md) |
| Module Inventory | [`docs/architecture/TUTORPUTOR_MODULE_INVENTORY.md`](docs/architecture/TUTORPUTOR_MODULE_INVENTORY.md) |
| Flow Map | [`docs/architecture/TUTORPUTOR_FLOW_MAP.md`](docs/architecture/TUTORPUTOR_FLOW_MAP.md) |
| User Manual | [`docs/usage/USER_MANUAL.md`](docs/usage/USER_MANUAL.md) |
| Operations | [`docs/operations/OPERATIONS.md`](docs/operations/OPERATIONS.md) |
| Coding Guidelines | [`docs/guidelines/CODING.md`](docs/guidelines/CODING.md) |
