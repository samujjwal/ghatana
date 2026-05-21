# Data Cloud Plane Boundary Checks

This document describes the boundary checks enforced for the Data Cloud plane to ensure proper integration with platform provider contracts and runtime-truth drift gate.

## Overview

Data Cloud must respect platform provider contracts and not bypass platform services. These boundary checks are enforced via ArchUnit tests to maintain architectural integrity.

## Boundary Test Files

### 1. AepCrossProductBoundaryTest.java

**Location**: `products/data-cloud/planes/action/server/src/test/java/com/ghatana/aep/server/arch/AepCrossProductBoundaryTest.java`

**Purpose**: Enforces AEP ↔ Data-Cloud cross-product boundary rules.

**Covered Rules**:
- AEP must not import Data Cloud launcher internals
- AEP must not import Data Cloud governance internals
- AEP must not import Data Cloud infrastructure internals
- AEP server HTTP handlers must not import other product UI/handlers
- AEP server must not use Spring Reactor or WebFlux
- AEP server must not use CompletableFuture in production code

**Allowed Integration Points**:
- `com.ghatana.datacloud.spi..` — Data Cloud SPI (canonical public API)
- `com.ghatana.datacloud.DataCloud*` — top-level client façade
- `com.ghatana.datacloud.client..` — DataCloudClientFactory and client module
- `com.ghatana.datacloud.agent.registry..` — agent-registry module (explicit dep)
- `com.ghatana.datacloud.deployment..` — deployment config (platform-launcher)
- `com.ghatana.datacloud` (root package only) — top-level types

### 2. DataCloudProviderBoundaryTest.java

**Location**: `products/data-cloud/planes/action/server/src/test/java/com/ghatana/aep/server/arch/DataCloudProviderBoundaryTest.java`

**Purpose**: Enforces Data Cloud provider contract boundary rules.

**Covered Rules**:
- Data Cloud must not import kernel provider internals directly
- Data Cloud must use provider health matrix for health checks
- Data Cloud must not bypass provider mode checks
- Data Cloud must respect provider readiness states
- Data Cloud must use runtime-truth service for route validation
- Data Cloud must not have direct route configuration without validation

**Allowed Integration Points**:
- `com.ghatana.kernel.providers..` — Platform provider public contracts
- `com.ghatana.kernel.provider.contracts..` — Provider contract interfaces
- `com.ghatana.kernel.health..` — Health matrix and monitoring contracts
- `com.ghatana.kernel.runtime.truth..` — Runtime-truth service for route validation

### 3. route-terminology-boundary.test.ts

**Location**: `products/data-cloud/planes/action/ui/src/__tests__/route-terminology-boundary.test.ts`

**Purpose**: Enforces route terminology boundary checks for Data Cloud UI.

**Covered Rules**:
- Build pipeline route must be marked as orchestration runtime surface
- Workflow catalog route must be marked as orchestration template surface

## CI Integration

### Running Boundary Checks

The boundary checks are run as part of the Data Cloud test suite:

```bash
# Run all Data Cloud boundary checks
cd products/data-cloud/planes/action/server
./gradlew test --tests "*BoundaryTest"

# Run specific boundary test
./gradlew test --tests AepCrossProductBoundaryTest
./gradlew test --tests DataCloudProviderBoundaryTest
```

### CI Pipeline Integration

These boundary checks are integrated into the CI pipeline via the `check:data-cloud-platform-providers` validation command:

```bash
# Run Data Cloud platform provider checks
pnpm check:data-cloud-platform-providers
```

## Runtime-Truth Drift Gate

The runtime-truth drift gate ensures that route configuration is validated against the actual deployed state. This prevents configuration drift and maintains consistency between:

1. **Route Configuration**: The intended route definitions
2. **Runtime Truth**: The actual deployed route state
3. **Provider Health**: The current health status of providers

### Enforcement Points

- Route validation must use the platform runtime-truth service
- Direct route configuration without validation is forbidden
- Provider health checks must use the provider health matrix
- Provider mode enforcement must be respected

## Provider Contract Integration

Data Cloud integrates with platform provider contracts through:

1. **Health Matrix**: Uses `KernelProviderHealthMatrix` for provider health aggregation
2. **Mode Enforcement**: Respects `ProviderModeEnforcer` for fail-closed platform mode
3. **Readiness States**: Uses provider readiness contracts for capability checks
4. **Runtime Truth**: Uses runtime-truth service for route validation

## Failure Handling

### Boundary Check Failures

If a boundary check fails:

1. The CI pipeline will fail
2. The violating class/package will be identified
3. A clear explanation of the violation will be provided
4. Suggested remediation steps will be included

### Common Violations

1. **Importing Provider Internals**: Use public provider contracts instead
2. **Bypassing Health Matrix**: Use `KernelProviderHealthMatrix` for health checks
3. **Direct Route Configuration**: Use runtime-truth service for validation
4. **Ignoring Provider Mode**: Use `ProviderModeEnforcer` for mode enforcement

## Maintenance

### Adding New Boundary Checks

When adding new boundary checks:

1. Add the test to the appropriate boundary test file
2. Document the rule in this file
3. Update the CI integration if needed
4. Run the tests to verify they pass
5. Update the validation command if needed

### Updating Allowed Integration Points

When updating allowed integration points:

1. Update the documentation in the boundary test file
2. Update this documentation file
3. Ensure all existing code complies with the new rules
4. Run the tests to verify compliance

## References

- [Kernel Provider Contracts](/platform/java/kernel-providers/README.md)
- [Provider Mode Enforcement](/platform/java/kernel-providers/src/main/java/com/ghatana/kernel/providers/health/ProviderModeEnforcer.java)
- [Runtime-Truth Service](/platform/java/kernel-runtime-truth/README.md)
- [ArchUnit Documentation](https://www.archunit.org/)
