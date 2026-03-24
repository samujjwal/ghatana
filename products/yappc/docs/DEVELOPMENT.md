# YAPPC Development Guide

## Prerequisites

- Java 21+ (Temurin recommended)
- Node.js 20+ with pnpm
- Docker & Docker Compose
- 8GB+ RAM

## Getting Started

```bash
# Clone repository
git clone https://github.com/ghatana/ghatana.git
cd ghatana/products/yappc

# Start infrastructure
./start-infra.sh

# Build backend
./gradlew clean build

# Start frontend
cd frontend
pnpm install
pnpm dev
```

## Project Structure

```
yappc/
├── core/           # Core domain modules (18 modules)
├── backend/        # Backend services
├── frontend/       # React frontend (10 libraries)
├── services/       # Service layer
├── docs/           # Documentation
└── scripts/        # Build and utility scripts
```

## Development Workflow

### Backend Development

```bash
# Build specific module
./gradlew :core:agents:runtime:build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :core:agents:runtime:test
```

### Frontend Development

```bash
cd frontend

# Start dev server
pnpm dev

# Run tests
pnpm test

# Type checking
pnpm typecheck

# Linting
pnpm lint
```

## Code Standards

### Java

- **Style:** Google Java Style Guide
- **Async:** ActiveJ Promise (no CompletableFuture)
- **Documentation:** JavaDoc required for public APIs
- **Testing:** JUnit 5 + Mockito

### TypeScript

- **Style:** ESLint + Prettier
- **Framework:** React 19 + Jotai
- **Documentation:** JSDoc for complex functions
- **Testing:** Vitest + Testing Library

## Testing

### Backend Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Run specific test class
./gradlew test --tests "com.ghatana.yappc.agents.CodeAnalysisAgentTest"
```

### Frontend Tests

```bash
# Run all tests
pnpm test

# Run with coverage
pnpm test:coverage

# Run E2E tests
pnpm test:e2e
```

## Commit Conventions

```
feat: Add new feature
fix: Bug fix
docs: Documentation changes
style: Code style changes
refactor: Code refactoring
test: Test changes
chore: Build/tooling changes
```

## Pull Request Process

1. Create feature branch from `main`
2. Make changes with clear commits
3. Run tests and linting
4. Update documentation
5. Submit PR with description
6. Address review feedback
7. Merge after approval

## Module Boundaries

Follow the dependency rules in [CORE_ARCHITECTURE.md](CORE_ARCHITECTURE.md):

- Foundation → Platform only
- AI/Knowledge → Foundation
- Agents → AI/Knowledge
- Scaffold → Agents
- Refactorer → Agents

**Forbidden:**
- Circular dependencies
- Cross-layer imports (skip layers)
- Product code in platform

## Troubleshooting

### Build Issues

```bash
# Clean build
./gradlew clean

# Clear Gradle cache
rm -rf ~/.gradle/caches

# Restart Gradle daemon
./gradlew --stop
```

### Frontend Issues

```bash
# Clear node_modules
rm -rf node_modules
pnpm install

# Clear build cache
pnpm clean
```

---

**Questions?** Ask in #yappc-dev on Slack
