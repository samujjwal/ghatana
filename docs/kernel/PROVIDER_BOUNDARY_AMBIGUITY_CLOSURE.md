# Provider Boundary Ambiguity Closure

**Purpose**: Document the closure of provider boundary ambiguity through provider capability negotiation and kernel-bridge architecture.

**Last Updated**: 2026-05-20

---

## Overview

This document describes how the Kernel platform closes provider boundary ambiguity through:
1. Provider capability negotiation contracts
2. Product-specific provider implementations behind kernel-bridge extensions
3. KernelProviderHealthMatrix for aggregate health monitoring

---

## Provider Capability Negotiation

### Capability Declaration

Providers declare their capabilities using the `ProviderCapabilityDeclaration` contract:

```typescript
interface ProviderCapabilityDeclaration {
  readonly capability: ProviderCapability;
  readonly available: boolean;
  readonly required: boolean;
  readonly version?: string;
  readonly constraints?: readonly string[];
}
```

### Supported Capabilities

The Kernel recognizes the following provider capabilities:
- `registry-read` - Read product registry entries
- `registry-write` - Write product registry entries
- `source-acquisition` - Acquire source code from repositories
- `lifecycle-events` - Emit and query lifecycle events
- `artifact-storage` - Store and retrieve build artifacts
- `artifact-fingerprinting` - Generate artifact fingerprints
- `approval-workflows` - Manage approval workflows
- `health-snapshots` - Store health snapshots
- `provenance-tracking` - Track provenance of artifacts
- `memory-storage` - Store agent memory
- `runtime-truth` - Provide runtime truth for UI
- `gate-evaluation` - Evaluate lifecycle gates
- `telemetry-emission` - Emit telemetry data
- `environment-provisioning` - Provision environments
- `secrets-management` - Manage secrets

### Capability Negotiation

When a provider registers with the Kernel, it declares its capabilities. The Kernel:
1. Validates that required capabilities are available
2. Checks capability versions for compatibility
3. Resolves capability constraints
4. Fails closed if required capabilities are missing

---

## Kernel-Bridge Architecture

### Purpose

The kernel-bridge pattern moves product-specific provider implementations behind product-local extensions, preventing boundary leaks from product code into platform Kernel.

### Architecture

```
kernel-core   ← [no knowledge of Data-Cloud]
      ↑ (imports)
data-cloud-kernel-bridge  ← [knows both kernel-core and data-cloud:spi]
      ↑ (imports)
data-cloud:spi            ← [concrete DataCloudClient implementations]
```

### Data Cloud Kernel-Bridge

**Location**: `products/data-cloud/extensions/kernel-bridge`

**Purpose**: Bridges Data Cloud into the Platform Kernel via the `KernelExtension` SPI.

**Key Components**:
- `DataCloudKernelExtension` - `AbstractKernelExtension` implementation
- `DataCloudKernelAdapterImpl` - Concrete adapter implementation
- `DataCloudBridgeCapabilities` - Canonical `KernelCapability` constants

**Provider Implementations**:
- `DataCloudHealthProvider` - Health snapshot persistence
- `DataCloudLifecycleEventProvider` - Lifecycle event emission
- `DataCloudArtifactProvider` - Artifact storage
- `DataCloudApprovalProvider` - Approval workflows
- `DataCloudProvenanceProvider` - Provenance tracking
- `DataCloudMemoryProvider` - Agent memory storage
- `DataCloudRuntimeTruthProvider` - Runtime truth for UI

### Boundary Rules

1. **Kernel-core** must not import product implementation code
2. **Kernel-bridge** may import both kernel-core and product SPI
3. **Product SPI** provides concrete implementations
4. **Providers** depend only on public Kernel contracts
5. **Providers** must not import from `products/*`

---

## KernelProviderHealthMatrix

### Purpose

The `KernelProviderHealthMatrix` aggregates health status from all registered Kernel providers for platform-wide health monitoring.

### Contract

```typescript
interface KernelProviderHealthMatrix {
  readonly schemaVersion: "1.0.0";
  readonly generatedAt: string;
  readonly overallStatus: ProviderHealthStatus;
  readonly totalProviders: number;
  readonly healthyProviders: number;
  readonly degradedProviders: number;
  readonly unhealthyProviders: number;
  readonly unknownProviders: number;
  readonly providers: readonly ProviderHealthEntry[];
  readonly providerMode: "bootstrap" | "platform";
  readonly missingCapabilities: readonly ProviderCapability[];
  readonly correlationId?: string;
}
```

### Provider Health Entry

```typescript
interface ProviderHealthEntry {
  readonly providerId: string;
  readonly providerKind: string;
  readonly status: ProviderHealthStatus;
  readonly checkedAt: string;
  readonly message: string;
  readonly capabilities: readonly ProviderCapabilityDeclaration[];
  readonly error?: string;
  readonly latencyMs?: number;
}
```

### Implementation

**Location**: `platform/typescript/kernel-providers/src/health/KernelProviderHealthMatrixProvider.ts`

**Features**:
- Register/unregister providers with health status
- Generate aggregate health matrix
- Identify missing required capabilities
- Query individual provider health
- Support both bootstrap and platform modes

### Health Status Levels

- **healthy** - Provider is fully operational
- **degraded** - Provider is operational but with limitations
- **unhealthy** - Provider is not operational
- **unknown** - Provider health cannot be determined

---

## Provider Mode Support

### Bootstrap Mode

- Uses file-backed providers
- `GhatanaFileRegistryProvider` for registry
- `FileHealthProvider` for health snapshots
- `FileArtifactProvider` for artifacts
- No external service dependencies

### Platform Mode

- Uses Data Cloud-backed providers via kernel-bridge
- `DataCloudHealthProvider` for health snapshots
- `DataCloudLifecycleEventProvider` for events
- `DataCloudArtifactProvider` for artifacts
- Requires Data Cloud platform provider health

### Fail-Closed Behavior

Platform mode fails closed if:
- Data Cloud provider health is missing or unhealthy
- Required capabilities are not available
- Provider capability negotiation fails
- KernelProviderHealthMatrix shows unhealthy status for required providers

---

## Current Status

### Completed
- ✅ Provider capability negotiation contract defined
- ✅ KernelProviderHealthMatrix contract defined
- ✅ KernelProviderHealthMatrixProvider implementation
- ✅ Data Cloud kernel-bridge architecture in place
- ✅ Data Cloud-specific providers behind kernel-bridge

### Verified
- ✅ Kernel-core does not import product implementation code
- ✅ Kernel-bridge follows dependency direction
- ✅ Providers depend only on public Kernel contracts
- ✅ No product imports in provider code

### Enforcement
- ✅ ESLint rule enforces package boundaries
- ✅ Architecture compliance checks validate boundaries
- ✅ CI gates prevent boundary leaks

---

## Usage Example

### Registering a Provider

```typescript
import { KernelProviderHealthMatrixProvider } from '@ghatana/kernel-providers';

const healthMatrix = new KernelProviderHealthMatrixProvider({
  providerMode: 'platform',
  timeoutMs: 30000,
});

healthMatrix.registerProvider({
  providerId: 'data-cloud-health-snapshots',
  providerKind: 'health',
  status: 'healthy',
  message: 'Data Cloud health snapshots operational',
  capabilities: [
    {
      capability: 'health-snapshots',
      available: true,
      required: true,
      version: '1.0.0',
    },
  ],
  latencyMs: 45,
});
```

### Generating Health Matrix

```typescript
const matrix = healthMatrix.generateHealthMatrix('correlation-123');

console.log(`Overall status: ${matrix.overallStatus}`);
console.log(`Healthy providers: ${matrix.healthyProviders}/${matrix.totalProviders}`);
console.log(`Missing capabilities: ${matrix.missingCapabilities.join(', ')}`);
```

---

## Conclusion

Provider boundary ambiguity is closed through:
1. Explicit capability negotiation contracts
2. Product-specific implementations behind kernel-bridge extensions
3. Aggregate health monitoring via KernelProviderHealthMatrix
4. Fail-closed behavior for missing or unhealthy providers
5. Architectural enforcement via linting and CI gates

This ensures that the Kernel platform remains product-neutral while allowing products to contribute provider implementations through well-defined extension points.
