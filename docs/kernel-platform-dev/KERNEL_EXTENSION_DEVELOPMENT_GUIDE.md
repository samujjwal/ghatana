# Kernel Extension Development Guide

## Preferred Base

Extensions should extend `AbstractKernelExtension` to get shared initialized/started state handling.

## Rules

- Use `onInitialize` for one-time setup.
- Use `requireStarted()` before runtime operations.
- Keep compatibility checks explicit.
- Keep contributed capabilities product-safe and narrowly scoped.

## Reference Implementations

- `HealthcareConsentKernelExtension`
- `ComplianceKernelExtension`
- `RiskManagementKernelExtension`
