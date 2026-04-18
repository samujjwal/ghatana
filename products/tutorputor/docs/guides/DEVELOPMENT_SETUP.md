# TutorPutor Development Setup

## Prerequisites

- **Node.js** 18+ (with Corepack enabled)
- **Node.js** 22+ (matches the platform and gateway package requirements)
- **pnpm** (install with `npm install -g pnpm`)
- **Docker Desktop**
- **Java** 21+ (for content generation service)
- **Git**

## Quick Start

### 1. Clone and Setup

```bash
git clone <repository>
cd products/tutorputor
```

### 2. Install Dependencies

```bash
pnpm install
cd libs/tutorputor-core && pnpm install
```

### 3. Environment Configuration

```bash
cp .env.example .env
# Edit .env with your settings
```

### 4. Start Development Environment

```bash
# Using ttr (recommended)
ttr dev

# Or manually
./bin/ttr-dev
```

The dev environment starts:
- PostgreSQL (Docker)
- Redis (Docker)
- API Gateway (port 3200)
- Web App (port 3201)
- Admin App (port 3202)
- Direct Platform Service (port 7105)

Canonical local validation topology:

- Gateway: `http://127.0.0.1:3200`
- Learner app: `http://127.0.0.1:3201`
- Admin app: `http://127.0.0.1:3202`
- Direct platform service: `http://127.0.0.1:7105`

## Using `ttr` Commands

The `ttr` command provides unified access to all operations:

```bash
ttr dev                    # Start development
ttr dev --no-seed          # Skip database seeding
ttr dev --with-kafka       # Enable Kafka
ttr dev --monitoring       # Enable Prometheus/Grafana

ttr test                   # Run all tests
ttr test --unit            # Unit tests only
ttr test --e2e -f "auth"   # E2E tests matching "auth"
ttr test --watch           # Watch mode

ttr doctor                 # System health check
ttr logs platform          # View platform logs
ttr logs platform -f       # Follow logs
ttr migrate                # Run migrations
ttr seed                   # Seed database
ttr stop                   # Stop all services
ttr clean --logs           # Clean log files
```

## Manual Setup (Alternative)

If you prefer not to use `ttr`:

### Start Infrastructure

```bash
docker compose up -d postgres redis

# Or boot the full supported local stack inside containers
docker compose --profile app-stack up -d
```

### Run Migrations

```bash
cd libs/tutorputor-core
npx prisma migrate dev
npx prisma generate
```

### Seed Database

```bash
cd libs/tutorputor-core
npx prisma db seed
```

### Start Services

```bash
# Terminal 1: Platform service
cd services/tutorputor-platform
PORT=7105 pnpm dev

# Terminal 2: API gateway
cd apps/api-gateway
PORT=3200 CONTENT_WORKER_ENABLED=false CONTENT_QUEUE_DISABLED=true pnpm dev

# Terminal 3: Web app
cd apps/tutorputor-web
VITE_API_BASE_URL=http://127.0.0.1:3200/api pnpm dev --host 0.0.0.0 --port 3201

# Terminal 4: Admin app
cd apps/tutorputor-admin
VITE_API_BASE_URL=http://127.0.0.1:3200 VITE_DEV_AUTH_BYPASS=true VITE_TUTORPUTOR_TENANT_ID=default pnpm dev --host 0.0.0.0 --port 3202
```

## Development Workflow

### Typical Day

```bash
# 1. Start environment
ttr dev

# 2. Run tests while developing
ttr test --unit --watch

# 3. Check logs as needed
ttr logs platform

# 4. Stop when done
ttr stop
```

### Database Changes

```bash
# After modifying schema.prisma
cd libs/tutorputor-core
npx prisma migrate dev --name your_change
npx prisma generate
```

### Testing

```bash
# Quick smoke tests
ttr test --quick

# Full test suite
ttr test --ci --coverage

# Specific test
ttr test --unit -f "example"
```

## Troubleshooting

### Port Already in Use

```bash
# Find and kill process
lsof -ti :3200 | xargs kill
lsof -ti :3201 | xargs kill
lsof -ti :3202 | xargs kill
lsof -ti :7105 | xargs kill

# Or use ttr stop
ttr stop
```

### Database Issues

```bash
# Reset database
ttr migrate --reset

# Or full reset
ttr stop
docker compose down -v
ttr dev
```

### Dependency Issues

```bash
# Clean and reinstall
ttr clean --node-modules
pnpm install
ttr dev
```

### Check System Health

```bash
ttr doctor
```

## IDE Setup

### VS Code

Recommended extensions:
- ESLint
- Prettier
- Prisma
- Tailwind CSS IntelliSense

### IntelliJ/WebStorm

- Enable ESLint integration
- Configure Prettier
- Set up Prisma plugin

## Environment Variables

Key variables in `.env`:

```bash
# Database
DATABASE_URL="postgresql://postgres:postgres@localhost:5432/tutorputor"

# Redis
REDIS_URL="redis://localhost:6379"

# JWT
JWT_SECRET="your-secret-key"

# Payments
STRIPE_SECRET_KEY="sk_test_..."

# AI (optional)
OLLAMA_HOST="http://localhost:11434"
```

## Project Structure

```
tutorputor/
├── apps/                    # Frontend applications
│   ├── tutorputor-web/    # Student web app
│   ├── tutorputor-admin/  # Admin dashboard
│   └── api-gateway/         # API gateway
├── services/                # Backend services
│   └── tutorputor-platform/  # Main platform
├── libs/                    # Shared libraries
│   ├── tutorputor-core/   # Prisma schema & client
│   ├── tutorputor-ai/     # AI utilities
│   ├── tutorputor-ui/     # UI components
│   └── tutorputor-simulation/  # Simulation engine
├── contracts/               # TypeScript contracts
├── bin/                     # CLI scripts (ttr)
└── docs/                    # Documentation
```

## Additional Resources

- [Architecture Overview](../architecture/README.md)
- [Product Specification](../architecture/specs/PRODUCT_SPEC.md)
- [Coding Standards](../guidelines/CODING.md)
- [bin/README.md](../../bin/README.md) - Full CLI reference
