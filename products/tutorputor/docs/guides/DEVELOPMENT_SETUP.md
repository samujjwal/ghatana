# TutorPutor Development Setup

## Prerequisites

- **Node.js** 18+ (with Corepack enabled)
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
- Platform Service (port 3200)
- Web App (port 3201)
- Admin App (port 3202)

## Using `ttr` Commands

The `ttr` command provides unified access to all operations:

```bash
ttr dev                    # Start development
ttr dev --no-seed          # Skip database seeding
ttr dev --with-kafka       # Enable Kafka
tr dev --monitoring       # Enable Prometheus/Grafana

ttr test                   # Run all tests
ttr test --unit            # Unit tests only
ttr test --e2e -f "auth"   # E2E tests matching "auth"
ttr test --watch           # Watch mode

ttr doctor                 # System health check
ttr logs platform          # View platform logs
ttr logs platform -f       # Follow logs
ttr migrate                # Run migrations
tr seed                   # Seed database
tr stop                   # Stop all services
tr clean --logs           # Clean log files
```

## Manual Setup (Alternative)

If you prefer not to use `ttr`:

### Start Infrastructure

```bash
docker compose up -d postgres redis
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
pnpm dev

# Terminal 2: Web app
cd apps/tutorputor-web
pnpm dev --port 3201

# Terminal 3: Admin app
cd apps/tutorputor-admin
pnpm dev --port 3202
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
