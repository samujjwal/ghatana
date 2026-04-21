# Shared-Services Architecture Documentation

## Overview

This document describes the architecture for shared services across products in the Ghatana platform and provides guidance for cross-product integration patterns.

## Background

The platform folder audit (April 2025) mentioned `platform/shared-services` as one of the 5 major areas audited, with 3 empty kernel bridges. However, investigation revealed:

- **`platform/shared-services` directory does not exist** - it was likely never created at the platform level
- **Kernel bridges exist under products/**:
  - `products/aep/kernel-bridge/` - AEP kernel bridge implementation
  - `products/data-cloud/kernel-bridge/` - Data Cloud kernel bridge implementation
  - `products/yappc/kernel-bridge/` - YAPPC kernel bridge implementation
- **Root `shared-services/` directory exists** with actual deployable services:
  - `auth-gateway/` - Authentication gateway service
  - `user-profile-service/` - User profile management service
  - `ai-inference-service/` - AI inference service
  - `auth-service/` - Authentication service
  - `incident-service/` - Incident management service

## Intended Purpose of Shared Services

The original concept for `shared-services` was to provide:

1. **Cross-Product Communication**
   - Bridges for inter-service communication between AEP, Data Cloud, and YAPPC
   - Shared message schemas and event definitions
   - Common authentication and authorization patterns

2. **Kernel Integration**
   - Abstractions for product kernel integration
   - Shared event bus connectors
   - Tenant context propagation

3. **Service Orchestration**
   - Workflow coordination across products
   - Distributed transaction management
   - Saga pattern implementations

### Clarification

The audit document's reference to "empty shared-services kernel bridges" was likely referring to a planned but never-created `platform/shared-services` directory. The actual kernel bridges are properly implemented under their respective products:

- `products/aep/kernel-bridge/` - Provides AEP kernel integration
- `products/data-cloud/kernel-bridge/` - Provides Data Cloud kernel integration
- `products/yappc/kernel-bridge/` - Provides YAPPC kernel integration

These kernel bridges follow the proper dependency direction:

```
kernel-core (no product knowledge)
    ↑
platform-plugins (platform-kernel aware, product-agnostic)
    ↑
data-cloud-kernel-bridge / aep-kernel-bridge / yappc-kernel-bridge
    ↑
products/data-cloud / products/aep / products/yappc (provide bridge implementations)
```

## Recommended Architecture for Cross-Product Integration

Instead of a centralized `shared-services` directory, cross-product integration should be handled through:

### 1. Platform-Level Shared Modules

Use `platform/java` and `platform/typescript` modules for shared infrastructure:

```
platform/
├── java/
│   ├── http/          # Shared HTTP client
│   ├── database/      # Shared database abstractions
│   ├── observability/ # Shared metrics/tracing
│   └── security/      # Shared auth/security
└── typescript/
    ├── state/         # Shared state management
    ├── theme/         # Shared theming
    └── tokens/        # Shared design tokens
```

### 2. Product-to-Product Direct Integration

When products need to communicate, use:

**Event-Driven Communication:**

- Use platform contracts (`platform/contracts`) for shared event schemas
- Implement product-specific event publishers/subscribers
- Leverage existing message brokers (Kafka, Redis Streams)

**HTTP APIs:**

- Define OpenAPI specs in `platform/contracts`
- Implement product-specific API clients
- Use shared HTTP utilities from `platform/java:http`

**Shared Database Access:**

- Use shared database abstractions from `platform/java:database`
- Define shared data models in `platform/contracts`
- Implement product-specific repositories

### 3. Integration Layer Pattern

Each product should implement its own integration layer:

```
products/
├── aep/
│   └── src/
│       └── integration/
│           ├── data-cloud-client.ts
│           └── yapppc-bridge.ts
├── data-cloud/
│   └── src/
│       └── integration/
│           ├── aep-client.ts
│           └── yapppc-bridge.ts
└── yappc/
    └── src/
        └── integration/
            ├── aep-client.ts
            └── data-cloud-client.ts
```

### 4. Shared Contracts

All cross-product contracts should be defined in `platform/contracts`:

```
platform/contracts/
├── events/          # Shared event schemas
├── apis/            # OpenAPI specs
├── protobuf/        # Protobuf definitions
└── schemas/         # JSON schemas
```

## Migration Guide

If you need to create integration between products:

1. **Check Platform Modules First**
   - Review `platform/java` and `platform/typescript` for existing utilities
   - Reuse before creating new code

2. **Define Contracts in platform/contracts**
   - Event schemas in `platform/contracts/events/`
   - API specs in `platform/contracts/apis/`
   - Run CI validation to ensure schema compliance

3. **Implement Product-Specific Integration**
   - Create integration layer in the consuming product
   - Use platform HTTP/database/security modules
   - Keep integration logic product-local

4. **Document Integration Points**
   - Create ADR explaining the integration architecture
   - Document data flow and error handling
   - Specify ownership and maintenance responsibilities

## Example: Data Cloud to AEP Integration

### Step 1: Define Contract

```protobuf
// platform/contracts/events/data-pipeline.proto
message DataPipelineEvent {
  string pipeline_id = 1;
  string tenant_id = 2;
  bytes payload = 3;
}
```

### Step 2: Implement in Data Cloud

```typescript
// products/data-cloud/src/integration/aep-client.ts
import { httpClient } from "@ghatana/http";
import { DataPipelineEvent } from "@ghatana/contracts/events";

export class AEPClient {
  async sendPipelineEvent(event: DataPipelineEvent) {
    await httpClient.post("/api/v1/pipeline/events", event);
  }
}
```

### Step 3: Implement in AEP

```java
// products/aep/src/main/java/com/ghatana/aep/integration/DataCloudController.java
import com.ghatana.platform.http.HttpHandler;
import com.ghatana.platform.observability.Metrics;

public class DataCloudController {
    @Monitored
    public void handlePipelineEvent(DataPipelineEvent event) {
        // Process event
    }
}
```

## Governance

### Integration Creation Checklist

Before creating cross-product integration:

- [ ] Verify no existing platform module provides needed functionality
- [ ] Define contracts in `platform/contracts`
- [ ] Create ADR explaining integration architecture
- [ ] Identify owning team for each side
- [ ] Document error handling and retry strategies
- [ ] Add observability (metrics, tracing, logging)
- [ ] Plan for versioning and backward compatibility

### Integration Review

- All cross-product integrations require platform team review
- Contracts must pass CI validation
- Integration points must be documented
- Performance and security implications must be assessed

## Future Considerations

If the need for centralized shared services emerges in the future:

1. **Clear Business Case**: Document specific business requirements
2. **Owner Identified**: Assign team ownership and maintenance responsibility
3. **Implementation Plan**: Define timeline and milestones
4. **Platform Module**: Create as a proper platform module, not empty stubs

## References

- Platform folder audit: `docs/platform-folder-audit-4-21.md`
- Copilot instructions: `.github/copilot-instructions.md`
- Java stub criteria: `platform/java/STUB_VS_IMPLEMENTATION_CRITERIA.md`
- TypeScript stub criteria: `platform/typescript/STUB_VS_IMPLEMENTATION_CRITERIA.md`
- Kernel capability map: `docs/architecture/PRODUCT_KERNEL_CAPABILITY_MAP.md`
