# Polyglot Fixture Product

A demonstration product showcasing Ghatana's multi-language platform support through four distinct service surfaces:

- **Java Service** - Spring Boot backend service
- **TypeScript Service** - Express.js web service
- **Rust Service** - Actix-web high-performance service
- **Python Worker** - FastAPI async worker

## Purpose

This product serves as a canonical fixture for validating that the Ghatana platform can:

1. Build and package services across multiple languages
2. Apply consistent lifecycle management to polyglot workloads
3. Enforce platform policies (security, observability, audit) uniformly
4. Generate correct manifests and artifacts for each surface type
5. Validate adapter conformance for each language ecosystem

## Surface Details

### Java Service
- **Framework**: Spring Boot 3.2
- **Java Version**: 21
- **Port**: 8080
- **Build**: Gradle (Kotlin DSL)
- **Endpoints**:
  - `GET /health` - Health check
  - `GET /api/ping` - Connectivity test

### TypeScript Service
- **Framework**: Express.js
- **TypeScript Version**: 5.3
- **Port**: 3001
- **Build**: TypeScript compiler
- **Endpoints**:
  - `GET /health` - Health check
  - `GET /api/ping` - Connectivity test

### Rust Service
- **Framework**: Actix-web 4.4
- **Rust Edition**: 2021
- **Port**: 3002
- **Build**: Cargo
- **Endpoints**:
  - `GET /health` - Health check
  - `GET /api/ping` - Connectivity test

### Python Worker
- **Framework**: FastAPI
- **Python Version**: 3.11+
- **Port**: 3003
- **Build**: setuptools
- **Endpoints**:
  - `GET /health` - Health check
  - `GET /api/ping` - Connectivity test

## Building

Each surface can be built independently:

```bash
# Java
cd surfaces/java
./gradlew build

# TypeScript
cd surfaces/typescript
pnpm install
pnpm build

# Rust
cd surfaces/rust
cargo build

# Python
cd surfaces/python
pip install -e ".[dev]"
```

## Testing

Each surface includes unit tests:

```bash
# Java
cd surfaces/java
./gradlew test

# TypeScript
cd surfaces/typescript
pnpm test

# Rust
cd surfaces/rust
cargo test

# Python
cd surfaces/python
pytest
```

## Platform Integration

This product is registered in the canonical product registry and participates in:

- Kernel lifecycle management
- Platform plugin bindings (audit, observability, security)
- Policy pack enforcement
- Telemetry and metrics collection
- Manifest generation for all lifecycle phases

## Domain Invariants

See `domain-invariants.json` for the declared invariants covering:

- Build success for each surface
- Cross-surface integration through the broker
- Tenant isolation across all surfaces
