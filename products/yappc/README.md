# YAPPC - AI-Native Product Development Platform

**Status:** Active Development  
**Last Updated:** 2026-01-27  
**Version:** 2.0

---

## 🎯 What is YAPPC?

YAPPC (Yet Another Platform Product Creator) is an **AI-native platform** that orchestrates the complete software development lifecycle through an **8-phase approach**: Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve.

### Key Capabilities

- **AI-Powered Code Generation** - Generate production-ready code from natural language
- **Intelligent Scaffolding** - Multi-framework project generation (React, Node.js, Java, Python, etc.)
- **Knowledge Graph** - Semantic understanding of your codebase and dependencies
- **Visual Canvas** - Miro-like interface for product ideation and architecture
- **Automated Refactoring** - AI-driven code improvements and migrations
- **Full-Stack Observability** - Built-in monitoring, logging, and analytics
- **Agentic Workflows** - Multi-agent collaboration for complex tasks

---

## 🚀 Quick Start

### Prerequisites

- Java 21+ (Temurin recommended)
- Node.js 20+ with pnpm
- Docker & Docker Compose
- 8GB+ RAM recommended

**Verify your environment:**
```bash
./tools/scripts/verify-dev-environment.sh
```

### Run YAPPC

#### Option 1: Using Make (Recommended)

```bash
# First-time setup
make quick-start

# Start backend services
make start-backend

# Start frontend (in a new terminal)
make start-frontend
```

#### Option 2: Manual Setup

```bash
# Clone repository
git clone https://github.com/ghatana/ghatana.git
cd ghatana/products/yappc

# Start infrastructure (Redis, Postgres, etc.)
./start-infra.sh

# Start all backend services
docker compose --profile backend up -d

# Start frontend
cd app-creator
pnpm install
pnpm dev:web
```

#### Option 3: Docker Only

```bash
# Start everything with Docker Compose
docker compose --profile full up -d
```

Access YAPPC at **http://localhost:3000**

**Available Services:**
- Frontend: http://localhost:3000
- Backend API: http://localhost:8000
- Domain Service: http://localhost:8080
- AI Requirements: http://localhost:8081
- Lifecycle API: http://localhost:8082

For detailed setup, see [docs/guides/DEPLOYMENT_GUIDE.md](docs/guides/DEPLOYMENT_GUIDE.md)

---

## 📚 Documentation

### Getting Started

- **[Architecture Overview](docs/ARCHITECTURE.md)** - System design and module structure
- **[Service Architecture (ADR-001)](docs/architecture/ADR-001-service-architecture.md)** - Service boundaries and responsibilities
- **[Developer Guide](docs/DEVELOPER_GUIDE.md)** - Contributing, coding standards, testing
- **[Deployment Guide](docs/guides/DEPLOYMENT_GUIDE.md)** - Running YAPPC locally and in production
- **[API Reference](docs/API_REFERENCE.md)** - HTTP and gRPC API documentation

### Guides

- [Testing Guide](docs/guides/ACTIVEJ_TEST_MIGRATION_GUIDE.md) - Writing and running tests
- [Ollama Integration](docs/guides/OLLAMA_MANUAL_TESTING_GUIDE.md) - Local LLM setup
- [Terminology Reference](docs/TERMINOLOGY_REFERENCE.md) - YAPPC concepts and glossary
- [ActiveJ Version Standardization](docs/ACTIVEJ_VERSION_STANDARDIZATION.md) - Dependency management

### Engineering

- [Principal Engineer Analysis](PRINCIPAL_ENGINEER_ANALYSIS_2026-01-27.md) - Comprehensive quality audit
- [Library Structure](app-creator/libs/README.md) - Frontend library organization (35 libraries)

---

## 🏗️ Architecture

YAPPC is organized into clear layers:

```
yappc/
├── api/                    # HTTP/gRPC API endpoints
├── domain/                 # Core business logic
├── infrastructure/         # Data-Cloud integration
├── ai/                     # AI agents and workflows
├── core/                   # Platform capabilities
├── app-creator/            # React UI
├── libs/yappc-domain/      # Shared models
└── docs/                   # Documentation
```

**Tech Stack:** Java 21 + ActiveJ, React + Next.js, Data-Cloud, OpenAI/Ollama
Quick Commands

See all available commands:
```bash
make help
```

Common workflows:
```bash
make dev              # Start full dev environment
make build            # Build all services
make test             # Run all tests
make lint             # Lint all code
make format           # Format all code
make clean            # Clean build artifacts
```

### 
See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for complete architecture details.

---

## 🔧 Development

### Building

```bash
# Build all modules
./gradlew clean build

# Run tests
./gradlew test

# Start development server
./gradlew :products:yappc:domain:run
```

### Code Standards

- Java: Google Java Style, ActiveJ for async
- TypeScript: ESLint + Prettier
- Documentation: JavaDoc/JSDoc required
- Testing: EventloopTestBase for async tests

See [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) for complete guidelines.

---

## 🤝 Contributing

See [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) for:
- Code review checklist
- Commit conventions
- Pull request process

---

## 📊 Status

| Module | Status | Coverage |
|--------|--------|----------|
| domain | ✅ Stable | 85% |
| api | ✅ Stable | 78% |
| infrastructure | ✅ Stable | 82% |
| ai | 🟡 Active Dev | 65% |
| app-creator | ✅ Stable | 70% |

**Recent:** Documentation reorganized (172→15 files), ActiveJ migration complete

---

## 📝 License

Proprietary - © 2025-2026 Ghatana Inc.

---

**Maintained by:** YAPPC Core Team  
**Support:** #yappc-dev on Slack
