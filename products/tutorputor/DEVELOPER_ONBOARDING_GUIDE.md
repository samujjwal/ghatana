# Developer Onboarding Guide

Welcome to Tutorputor! This guide will help you get started with development on the Tutorputor platform.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Development Setup](#development-setup)
4. [Running the Application](#running-the-application)
5. [Development Workflow](#development-workflow)
6. [Testing](#testing)
7. [Code Style Guidelines](#code-style-guidelines)
8. [Common Tasks](#common-tasks)
9. [Troubleshooting](#troubleshooting)
10. [Resources](#resources)

## Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js**: v18 or higher (we recommend using nvm for version management)
- **pnpm**: v8 or higher (package manager)
- **Java**: 21 or higher (for platform modules)
- **PostgreSQL**: 14 or higher
- **Redis**: 6 or higher
- **Docker**: For containerized services
- **Git**: For version control

### Installing Prerequisites

```bash
# Install Node.js using nvm
nvm install 18
nvm use 18

# Install pnpm
npm install -g pnpm

# Install Java (macOS)
brew install openjdk@21

# Install PostgreSQL and Redis (macOS)
brew install postgresql redis
```

## Project Structure

The Tutorputor project is a monorepo with the following structure:

```
ghatana/
├── products/
│   └── tutorputor/              # Main product directory
│       ├── apps/                 # Frontend applications
│       │   ├── tutorputor-web/   # Main web application
│       │   └── tutorputor-admin/ # Admin dashboard
│       ├── services/             # Backend services
│       │   ├── tutorputor-platform/ # Core platform service
│       │   ├── tutorputor-payments/ # Payment processing
│       │   └── ...
│       ├── libs/                 # Shared libraries
│       │   └── tutorputor-core/   # Core database and types
│       └── tests/                # E2E tests
├── platform/                     # Shared platform modules
│   ├── java/                     # Java platform services
│   ├── typescript/               # TypeScript shared packages
│   └── contracts/               # Shared contracts
└── docs/                        # Documentation
```

### Key Directories

- **`apps/tutorputor-web`**: React-based web application for learners
- **`services/tutorputor-platform`**: Main backend API service (Node.js/TypeScript)
- **`libs/tutorputor-core`**: Shared database models, types, and utilities
- **`platform/java`**: Java-based platform services (kernel, database, http, etc.)

## Development Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/ghatana.git
cd ghatana
```

### 2. Install Dependencies

```bash
# Install root dependencies
pnpm install

# Install dependencies for specific workspaces
cd products/tutorputor
pnpm install
```

### 3. Configure Environment Variables

Create a `.env` file in the root of the project:

```bash
cp .env.example .env
```

Edit the `.env` file with your configuration:

```env
# Database
DATABASE_URL=postgresql://user:password@localhost:5432/tutorputor

# Redis
REDIS_URL=redis://localhost:6379

# JWT
JWT_SECRET=your-secret-key-here

# AI Services
OPENAI_API_KEY=your-openai-api-key

# Stripe (for payments)
STRIPE_SECRET_KEY=sk_test_your-stripe-key
```

### 4. Initialize the Database

```bash
# Create databases
psql -U postgres -c "CREATE DATABASE tutorputor;"

# Run migrations
cd products/tutorputor/libs/tutorputor-core
pnpm prisma migrate dev

# Seed the database (optional)
pnpm prisma db seed
```

### 5. Start Redis

```bash
# macOS
brew services start redis

# Linux
sudo systemctl start redis

# Docker
docker run -d -p 6379:6379 redis:alpine
```

## Running the Application

### Running the Platform Service

```bash
cd products/tutorputor/services/tutorputor-platform
pnpm dev
```

The platform service will start on `http://localhost:3000`

### Running the Web Application

```bash
cd products/tutorputor/apps/tutorputor-web
pnpm dev
```

The web application will start on `http://localhost:5173`

### Running All Services with Docker

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f
```

## Development Workflow

### Branching Strategy

- **`main`**: Production branch
- **`develop`**: Integration branch for features
- **`feature/*`**: Feature branches
- **`bugfix/*`**: Bug fix branches
- **`hotfix/*`**: Critical production fixes

### Creating a Feature Branch

```bash
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name
```

### Committing Changes

Follow the conventional commit format:

```
feat: add user authentication
fix: resolve memory leak in cache service
docs: update onboarding guide
refactor: optimize database queries
test: add integration tests for payment flow
```

### Pull Request Process

1. Push your branch to the remote repository
2. Create a pull request to `develop`
3. Ensure all CI checks pass
4. Request review from team members
5. Address review feedback
6. Merge when approved

## Testing

### Unit Tests

```bash
# Run all unit tests
pnpm test

# Run tests in watch mode
pnpm test:watch

# Run tests with coverage
pnpm test:coverage
```

### Integration Tests

```bash
# Run integration tests
cd services/tutorputor-platform
pnpm test:integration
```

### E2E Tests

```bash
# Run E2E tests
cd tests/e2e
pnpm test:e2e

# Run E2E tests with UI
pnpm test:e2e:ui
```

### Test Coverage

We aim for:
- Unit tests: 80%+ coverage
- Integration tests: Critical paths covered
- E2E tests: Key user journeys covered

## Code Style Guidelines

### TypeScript

- Use strict mode: `strict: true` in tsconfig.json
- No `any` types - use `unknown` with type guards for untyped data
- Use explicit function parameter types
- Use interfaces for object shapes

### Java

- Follow existing Java conventions in the codebase
- Use Java 21 features
- Add JavaDoc tags for public classes
- Follow the existing naming conventions

### General

- Use meaningful variable and function names
- Keep functions small and focused
- Add comments for complex logic
- Follow the existing code style in the module you're working on

## Common Tasks

### Adding a New API Endpoint

1. Define the route in the appropriate module (e.g., `src/modules/user/routes.ts`)
2. Implement the handler in the service file
3. Add validation schemas if needed
4. Write tests for the endpoint
5. Update API documentation

### Adding Database Migration

```bash
cd libs/tutorputor-core
pnpm prisma migrate dev --name your_migration_name
```

### Adding a New Module

1. Create the module directory under `services/tutorputor-platform/src/modules/`
2. Create the module file (e.g., `module.ts`)
3. Create routes, services, and types
4. Register the module in `setup.ts`
5. Write tests for the module

### Debugging

#### Debugging Node.js Services

```bash
# Run with Node debugger
node --inspect-brk src/server.ts
```

#### Debugging Java Services

```bash
# Run with JVM debug flags
./gradlew bootRun --debug-jvm
```

## Troubleshooting

### Common Issues

**Issue: Database connection failed**
- Solution: Check that PostgreSQL is running and the DATABASE_URL is correct

**Issue: Redis connection failed**
- Solution: Check that Redis is running and the REDIS_URL is correct

**Issue: Build fails with TypeScript errors**
- Solution: Run `pnpm tsc --noEmit` to see all TypeScript errors

**Issue: Tests fail with timeout**
- Solution: Increase test timeout or check if services are running

### Getting Help

- Check the documentation in `docs/`
- Search existing issues on GitHub
- Ask in the team Slack channel
- Create a new issue with detailed steps to reproduce

## Resources

### Internal Documentation

- [Architecture Documentation](docs/ARCHITECTURE.md)
- [API Documentation](docs/api/README.md)
- [Agent System Documentation](docs/agent-system/README.md)
- [Build Instructions](docs/BUILD.md)

### External Resources

- [Prisma Documentation](https://www.prisma.io/docs)
- [Fastify Documentation](https://fastify.dev/docs/latest/)
- [React Documentation](https://react.dev/)
- [BullMQ Documentation](https://docs.bullmq.io/)

### Team Resources

- Team Slack: #tutorputor-dev
- GitHub Repository: https://github.com/your-org/ghatana
- Project Board: https://github.com/your-org/ghatana/projects/1

## Next Steps

After completing this guide:

1. Explore the codebase and familiarize yourself with the architecture
2. Pick up a good first issue from GitHub
3. Set up your local development environment
4. Make your first contribution!
5. Join the team standup to introduce yourself

Welcome to the team! 🚀
