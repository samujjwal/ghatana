# Ghatana - Simplified Architecture

## Overview

This is the new simplified architecture for the Ghatana monorepo. It follows clean architecture principles with clear separation between platform (shared) code and product-specific code.

## Key Improvements

| Metric                    | Original | New  | Improvement       |
| ------------------------- | -------- | ---- | ----------------- |
| settings.gradle.kts lines | 734      | 91   | **88% reduction** |
| Conditional dependencies  | Multiple | 0    | **Eliminated**    |
| Build predictability      | Low      | High | **Deterministic** |

## Architecture Principles

1. **DETERMINISTIC**: Same code → same build result, always
2. **EXPLICIT**: All dependencies declared, no magic
3. **SIMPLE**: Easy to understand and maintain
4. **EXTENSIBLE**: Easy to add new modules without changing build logic

## Directory Structure

```
ghatana-new/
├── platform/                    # TRUE shared infrastructure
│   └── java/
│       ├── core/               # Basic utilities, types, patterns
│       ├── database/           # Database abstractions, caching
│       ├── http/               # HTTP client/server (ActiveJ)
│       ├── auth/               # JWT, password hashing, RBAC
│       ├── observability/      # Metrics, tracing, health checks
│       └── testing/            # Common testing utilities
├── products/                    # Product-specific code
│   ├── aep/                    # Autonomous Event Processing
│   ├── data-cloud/             # Multi-tenant Metadata Management
│   ├── yappc/                  # Platform Creator
│   ├── flashit/                # Context Capture
│   ├── software-org/           # Software Organization Simulation
│   ├── virtual-org/            # Virtual Organization Framework
│   └── security-gateway/       # OAuth/RBAC Security
├── apps/                        # Cross-product applications
└── migration/                   # Migration tracking
```

## Platform Modules

### core

- `Feature` - Feature flag enumeration
- `FeatureService` - Runtime feature management
- `Preconditions` - Argument validation utilities
- `Result` - Functional result type (Success/Failure)
- `Id` - Strongly-typed identifier patterns
- `Timestamp` - Immutable timestamp wrapper
- `ValidationResult` - Validation result aggregation

### database

- `DataSourceConfig` - Database connection configuration
- `ConnectionPool` - HikariCP connection pool wrapper
- `Cache` - Generic cache interface
- `InMemoryCache` - Thread-safe in-memory cache with TTL

### http

- `HttpResponse` - Standard API response wrapper
- `JsonServlet` - Base class for JSON HTTP handlers

### auth

- `JwtService` - JWT token creation and validation
- `PasswordService` - BCrypt password hashing

### observability

- `MetricsRegistry` - Micrometer metrics wrapper
- `HealthCheck` - Health check result types

### testing

- `TestFixture` - Test fixture pattern interface

## Building

```bash
# Build all platform modules
./gradlew build

# Build specific module
./gradlew :platform:java:core:build

# Run tests
./gradlew test
```

## Feature Flags

Features can be enabled/disabled at runtime via:

- Environment variables: `FEATURE_AEP_ADVANCED_PATTERNS=true`
- System properties: `-Dfeature.aep.advanced.patterns=true`
- Programmatic configuration

```java
FeatureService features = FeatureService.withDefaults();

if (features.isEnabled(Feature.AEP_ADVANCED_PATTERNS)) {
    // Use advanced patterns
}

features.ifEnabled(Feature.YAPPC_SCAFFOLDING, () -> {
    // Execute scaffolding logic
});
```

## Migration Status

### Platform Modules (TRUE shared code - 6 modules)

- ✅ **Platform Core** - StringUtils, JsonUtils, PlatformException, ErrorCode, Validation, Result, Feature flags
- ✅ **Platform Database** - ConnectionPool (HikariCP), Cache, InMemoryCache, DataSourceConfig
- ✅ **Platform HTTP** - JsonServlet, HttpResponse (ActiveJ 6.x)
- ✅ **Platform Auth** - JwtService (JWT), PasswordService (BCrypt)
- ✅ **Platform Observability** - MetricsRegistry (Micrometer), HealthCheck
- ✅ **Platform Testing** - TestFixture, TestContainers integration

### Product Platforms (Product-specific shared code - 3 platforms)

#### ✅ AEP Platform (Autonomous Event Processing)

**Modules**: operators, events, agents, workflow, core

- **Operators**: Operator, OperatorType, OperatorResult, OperatorConfig
- **Events**: Event, GenericEvent, EventStream
- **Agents**: Agent, AgentType, AgentState, AgentConfig
- **Workflow**: Pipeline, PipelineState
- **Core**: AepErrorCode

#### ✅ Data-Cloud Platform (Multi-tenant Metadata)

**Modules**: storage, core

- **Storage**: StateStore, InMemoryStateStore
- **Core**: DataCloudErrorCode

#### ✅ Shared-Services Platform (Cross-product capabilities)

**Modules**: ai, connectors

- **AI**: AiProvider, AiRequest, AiResponse (OpenAI, Anthropic, Google, Ollama)
- **Connectors**: Connector, ConnectorType, ConnectorConfig, ConnectorResult, ConnectorException

### Build Metrics

| Metric                  | Value    | Improvement            |
| ----------------------- | -------- | ---------------------- |
| **Total Java files**    | 75       | Clean foundation       |
| **Gradle modules**      | 9        | Simple, focused        |
| **Build tasks**         | 47       | All passing ✅         |
| **settings.gradle.kts** | 95 lines | 87% reduction from 734 |
| **Conditional deps**    | 0        | Eliminated ✅          |
| **Build time**          | ~29s     | Optimized              |

### Architecture Principles Achieved

✅ **Simple** - Single responsibility per module, clear boundaries
✅ **Correct** - Proper separation of platform vs product code
✅ **Extensible** - Easy to add new products without touching platform
✅ **Flexible** - Products can evolve independently

## Next Steps

1. ✅ Create platform foundation (6 modules)
2. ✅ Migrate essential utilities from common-utils
3. ✅ Create AEP product platform (operators, events, agents, workflow)
4. ✅ Create Data-Cloud product platform (storage)
5. ✅ Create Shared-Services platform (AI, connectors)
6. ⏳ Migrate remaining implementations from original libs/java
7. ⏳ Update product services to use new platform dependencies
8. ⏳ Final validation and switch-over
