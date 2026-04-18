# TutorPutor Runner (ttr)

Simple, comprehensive command-line interface for managing TutorPutor development, testing, and production environments.

## Quick Start

### 1. Setup PATH (one time)

```bash
# Option A: Auto-setup (run from project root)
./bin/setup-path.sh

# Option B: Manual - Add to ~/.bashrc or ~/.zshrc
export PATH="$PATH:$(pwd)/bin"

# Then reload
source ~/.bashrc  # or ~/.zshrc
```

### 2. Run from anywhere

```bash
cd /Users/samujjwal/Development/ghatana/products/tutorputor  # Or wherever your project is

# Now use ttr commands
ttr dev
ttr test
ttr doctor
```

### 3. Or use directly (without PATH)

```bash
cd /Users/samujjwal/Development/ghatana/products/tutorputor
./bin/ttr dev
```

## Commands

| Command | Description |
|---------|-------------|
| `ttr dev` | Start development environment |
| `ttr test` | Run tests (flexible options) |
| `ttr prod` | Deploy to production |
| `ttr stop` | Stop all services |
| `ttr doctor` | Check system health |
| `ttr clean` | Clean build artifacts |
| `ttr logs` | View service logs |
| `ttr migrate` | Run database migrations |
| `ttr seed` | Seed development data |
| `ttr status` | Show service status |

## Development

```bash
# Full development environment
ttr dev

# Without seeding
ttr dev --no-seed

# With additional services
ttr dev --with-kafka --with-search

# Verbose output
ttr dev --verbose
```

## Testing

```bash
# Run all tests
ttr test

# Specific test types
ttr test --unit
ttr test --integration
ttr test --e2e
ttr test --contract

# With filtering
ttr test --unit -f "example"
ttr test --e2e -f "auth"

# Watch mode
ttr test --unit --watch

# CI mode with coverage
ttr test --ci --coverage
```

## Production

```bash
# Deploy locally with Docker
ttr prod

# Build only
ttr prod --build

# Deploy to Kubernetes
ttr prod --k8s
```

## Maintenance

```bash
# Check system health
ttr doctor

# View logs
ttr logs platform
ttr logs platform -f
ttr logs all

# Clean up
ttr clean --logs
ttr clean --build
ttr clean --all  # Full reset

# Database
ttr migrate
ttr migrate --reset
ttr seed
```

## Command Reference

### ttr dev [options]

Start the development environment.

Options:
- `--no-seed` - Skip database seeding
- `--no-services` - Don't start backend services
- `--no-apps` - Don't start frontend apps
- `--with-kafka` - Enable Kafka
- `--with-queue` - Enable job queues
- `--with-search` - Enable Elasticsearch
- `--monitoring` - Enable Prometheus/Grafana
- `--verbose` - Show all output

### ttr test [options]

Run tests with flexible filtering.

Options:
- `--unit` - Unit tests only
- `--integration` - Integration tests
- `--e2e` - End-to-end tests
- `--contract` - Contract tests
- `--performance` - Performance tests
- `--quick` - Quick smoke tests
- `-f, --filter` - Filter by pattern
- `--watch` - Watch mode
- `--coverage` - Generate coverage
- `--ci` - CI mode (no watch)
- `--serial` - Run serially

### ttr stop [options]

Stop services.

Options:
- `--services` - Stop backend only
- `--apps` - Stop apps only
- `--infra` - Stop infrastructure only

### ttr clean [options]

Clean build artifacts.

Options:
- `--node-modules` - Delete node_modules
- `--build` - Clean build artifacts
- `--logs` - Clean log files
- `--docker` - Clean Docker
- `--all` - Full reset (destructive)

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `TUTORPUTOR_ENV` | Environment mode | `development` |
| `PORT_API_GATEWAY` | API Gateway port | `3200` |
| `PORT_PLATFORM` | Direct platform port | `7105` |
| `PORT_WEB` | Web app port | `3201` |
| `PORT_ADMIN` | Admin app port | `3202` |
| `QUIET_CONSOLE` | Suppress output | `true` |
| `DATABASE_URL` | Database connection | (required for prod) |
| `REDIS_URL` | Redis connection | (required for prod) |
| `JWT_SECRET` | JWT signing secret | (required for prod) |

## Architecture

The `ttr` command delegates to individual scripts:

- `ttr-dev` - Development environment orchestration
- `ttr-test` - Test runner with flexible filtering
- `ttr-prod` - Production deployment
- `ttr-stop` - Service shutdown
- `ttr-doctor` - Health checks
- `ttr-clean` - Cleanup utilities
- `ttr-logs` - Log viewer
- `ttr-migrate` - Database migrations
- `ttr-seed` - Database seeding
- `ttr-status` - Status display

## Troubleshooting

### Command not found
```bash
# Make scripts executable
chmod +x bin/ttr*

# Add to PATH
export PATH="$PATH:$(pwd)/bin"
```

### Services won't start
```bash
# Check health
ttr doctor

# Clean and restart
ttr clean --all
pnpm install
ttr dev
```

### Port conflicts
```bash
# Find and kill processes
lsof -ti :3200 | xargs kill
lsof -ti :3201 | xargs kill
lsof -ti :3202 | xargs kill
lsof -ti :7105 | xargs kill
```
