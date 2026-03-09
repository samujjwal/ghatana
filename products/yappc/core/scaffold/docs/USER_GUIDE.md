# YAPPC Scaffold User Guide

> **Version:** 1.0.0  
> **Last Updated:** 2025-12-06

## Table of Contents

1. [Introduction](#introduction)
2. [Quick Start](#quick-start)
3. [Available Packs](#available-packs)
4. [Creating Projects](#creating-projects)
5. [Feature Packs](#feature-packs)
6. [Full-Stack Projects](#full-stack-projects)
7. [Configuration](#configuration)
8. [CLI Reference](#cli-reference)
9. [Troubleshooting](#troubleshooting)

---

## Introduction

YAPPC Scaffold is a polyglot project generator that creates production-ready projects across multiple languages, frameworks, and platforms. It supports:

- **Languages:** Java, TypeScript, Rust, Go
- **Platforms:** Web, Server, Desktop (Tauri), Mobile (React Native)
- **Project Types:** Services, Libraries, Full-Stack Apps, Middleware

### Philosophy

- **Production-Ready:** Every generated project includes Docker, CI/CD, testing, and observability
- **Best Practices:** Projects follow industry standards and patterns
- **Composable:** Mix and match packs to create custom stacks
- **Extensible:** Create your own packs for custom needs

---

## Quick Start

### Installation

```bash
# Clone the repository
git clone https://github.com/ghatana/yappc.git
cd yappc/products/yappc

# Build the CLI
./gradlew :cli:build

# Add to PATH (optional)
export PATH=$PATH:$(pwd)/cli/build/install/yappc/bin
```

### Create Your First Project

```bash
# Interactive mode
yappc create

# With options
yappc create --pack ts-node-fastify --name my-api --output ./projects
```

---

## Available Packs

### Backend Services

| Pack | Language | Framework | Description |
|------|----------|-----------|-------------|
| `java-service-spring-gradle` | Java 21 | Spring Boot 3.3 | Production Spring Boot service |
| `java-service-activej-gradle` | Java 21 | ActiveJ | High-performance async service |
| `ts-node-fastify` | TypeScript | Fastify 4 | Fast Node.js API server |
| `rust-service-axum-cargo` | Rust | Axum 0.7 | Async Rust web service |
| `go-service-chi` | Go 1.23 | Chi 5 | Lightweight Go HTTP service |

### Frontend Applications

| Pack | Language | Framework | Description |
|------|----------|-----------|-------------|
| `ts-react-vite` | TypeScript | React 18 + Vite | Modern React SPA |
| `ts-react-nextjs` | TypeScript | Next.js 14 | Full-stack React framework |

### Platform Applications

| Pack | Languages | Framework | Description |
|------|-----------|-----------|-------------|
| `tauri-desktop` | Rust + TypeScript | Tauri 2.0 | Cross-platform desktop app |
| `react-native-mobile` | TypeScript | Expo 52 | iOS/Android mobile app |

### Middleware

| Pack | Language | Framework | Description |
|------|----------|-----------|-------------|
| `middleware-gateway` | TypeScript | Fastify | API gateway with routing, rate limiting |
| `graphql-mesh` | TypeScript | GraphQL Mesh | GraphQL federation gateway |

### Full-Stack

| Pack | Backend | Frontend | Description |
|------|---------|----------|-------------|
| `fullstack-java-react` | Spring Boot | React + Vite | Java monorepo |
| `fullstack-rust-react` | Axum | React + Vite | Rust monorepo |
| `fullstack-go-react` | Chi | React + Vite | Go monorepo |

---

## Creating Projects

### Basic Usage

```bash
yappc create --pack <pack-name> --name <project-name> [options]
```

### Options

| Option | Short | Description |
|--------|-------|-------------|
| `--pack` | `-p` | Pack to use for generation |
| `--name` | `-n` | Project name |
| `--output` | `-o` | Output directory (default: current) |
| `--dry-run` | | Preview files without creating |
| `--force` | `-f` | Overwrite existing files |
| `--var` | | Set variable (key=value) |

### Examples

```bash
# Create a Rust Axum service
yappc create --pack rust-service-axum-cargo --name order-service

# Create with custom port
yappc create --pack go-service-chi --name api \
  --var port=8080 --var moduleName=github.com/myorg/api

# Dry run to preview
yappc create --pack ts-node-fastify --name demo --dry-run

# Full-stack project
yappc create --pack fullstack-java-react --name ecommerce \
  --var backendPort=8080 --var frontendPort=3000
```

---

## Feature Packs

Feature packs add capabilities to existing projects. They are language-aware and provide implementations for all supported languages.

### Available Feature Packs

| Pack | Description | Supported Languages |
|------|-------------|---------------------|
| `feature-database` | Database connectivity, migrations, ORM | Java, TypeScript, Rust, Go |
| `feature-auth` | JWT/OAuth2 authentication | Java, TypeScript, Rust, Go |
| `feature-observability` | Logging, metrics, tracing | Java, TypeScript, Rust, Go |

### Applying Feature Packs

```bash
# Add database support
yappc add-feature --feature database --to ./my-project \
  --var databaseType=postgresql --var databaseName=mydb

# Add authentication
yappc add-feature --feature auth --to ./my-project \
  --var authType=jwt --var jwtExpiration=24h

# Add observability
yappc add-feature --feature observability --to ./my-project \
  --var serviceName=my-service --var enableTracing=true
```

### Feature Pack Variables

#### Database (`feature-database`)

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `databaseType` | string | postgresql | postgresql, mysql, sqlite |
| `databaseHost` | string | localhost | Database host |
| `databasePort` | number | 5432 | Database port |
| `databaseName` | string | (required) | Database name |
| `enableMigrations` | boolean | true | Enable migrations |
| `connectionPoolSize` | number | 10 | Pool size |

#### Auth (`feature-auth`)

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `authType` | string | jwt | jwt, oauth2, session |
| `jwtExpiration` | string | 24h | Access token expiration |
| `refreshTokenExpiration` | string | 7d | Refresh token expiration |
| `enableRefreshTokens` | boolean | true | Enable refresh tokens |
| `enableRoles` | boolean | true | Enable RBAC |

#### Observability (`feature-observability`)

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `serviceName` | string | (required) | Service name |
| `enableTracing` | boolean | true | Enable OpenTelemetry |
| `enableMetrics` | boolean | true | Enable Prometheus |
| `metricsPort` | number | 9090 | Metrics endpoint port |
| `logLevel` | string | info | debug, info, warn, error |
| `logFormat` | string | json | json, text |
| `otlpEndpoint` | string | http://localhost:4318 | OTLP endpoint |

---

## Full-Stack Projects

Full-stack packs create monorepo projects with coordinated backend and frontend.

### Project Structure

```
my-fullstack-app/
├── backend/           # Backend service
│   ├── src/
│   └── Dockerfile
├── frontend/          # React frontend
│   ├── src/
│   └── Dockerfile
├── docker-compose.yml # Container orchestration
├── Makefile          # Build commands
└── README.md
```

### Development Commands

```bash
# Start development (both backend and frontend)
make dev

# Build all
make build

# Run tests
make test

# Start with Docker
docker-compose up --build
```

### Backend-Frontend Communication

All full-stack projects include:
- **API Proxy:** Frontend dev server proxies `/api` to backend
- **CORS:** Backend configured for frontend origin
- **Type Safety:** Shared types where applicable
- **Health Checks:** Both services expose health endpoints

---

## Configuration

### Pack Variables

Each pack defines variables that customize the generated project. View pack variables:

```bash
yappc info --pack ts-node-fastify
```

### Global Configuration

Create `~/.yappc/config.yaml`:

```yaml
defaults:
  author: "Your Name"
  license: "MIT"
  
templates:
  # Custom template search paths
  paths:
    - ~/.yappc/templates
    
preferences:
  # Default pack preferences
  java:
    version: "21"
  node:
    packageManager: "pnpm"
```

### Environment Variables

| Variable | Description |
|----------|-------------|
| `YAPPC_HOME` | YAPPC installation directory |
| `YAPPC_PACKS_PATH` | Additional packs search path |
| `YAPPC_NO_COLOR` | Disable colored output |

---

## CLI Reference

### `yappc create`

Create a new project from a pack.

```bash
yappc create [options]

Options:
  -p, --pack <name>      Pack name (required unless interactive)
  -n, --name <name>      Project name
  -o, --output <dir>     Output directory
  --var <key=value>      Set pack variable (repeatable)
  --dry-run              Preview without creating files
  -f, --force            Overwrite existing files
  -i, --interactive      Interactive mode (default if no pack)
```

### `yappc list`

List available packs.

```bash
yappc list [options]

Options:
  --category <cat>       Filter by category
  --language <lang>      Filter by language
  --json                 Output as JSON
```

### `yappc info`

Show pack information.

```bash
yappc info --pack <name>

Shows:
  - Description
  - Category
  - Supported languages
  - Required/optional variables
  - Generated file list
```

### `yappc add-feature`

Add a feature pack to an existing project.

```bash
yappc add-feature [options]

Options:
  --feature <name>       Feature pack name
  --to <path>            Project path
  --var <key=value>      Set feature variable
  --dry-run              Preview changes
```

---

## Troubleshooting

### Common Issues

**"Pack not found"**
```bash
# List available packs
yappc list

# Check pack path
echo $YAPPC_PACKS_PATH
```

**"Variable required"**
```bash
# View pack variables
yappc info --pack <pack-name>

# Provide required variables
yappc create --pack <name> --var key=value
```

**"Permission denied"**
```bash
# Check output directory permissions
ls -la ./output

# Use force flag cautiously
yappc create --pack <name> --force
```

### Debug Mode

```bash
# Enable verbose logging
YAPPC_DEBUG=true yappc create --pack <name>

# Show generated file paths
yappc create --pack <name> --dry-run
```

### Getting Help

- **GitHub Issues:** Report bugs and feature requests
- **Documentation:** Check `/docs` directory
- **Pack Authoring:** See `PACK_AUTHORING_GUIDE.md`

---

## What's Next?

- [Pack Authoring Guide](./PACK_AUTHORING_GUIDE.md) - Create custom packs
- [POLYGLOT_SCAFFOLD_PLAN.md](./POLYGLOT_SCAFFOLD_PLAN.md) - Implementation roadmap
- [Contributing](../CONTRIBUTING.md) - Contribute to YAPPC
