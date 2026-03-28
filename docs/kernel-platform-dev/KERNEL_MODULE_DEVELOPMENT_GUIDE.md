# Kernel Module Development Guide

## Preferred Base

New product modules should extend `AbstractKernelModule` unless they have a strong reason not to.

## Benefits

- Shared initialize/start/stop orchestration
- Consistent health-check aggregation
- Interface-based service startup instead of `instanceof` chains
- Cleaner composition roots for products

## Implementation Steps

1. Declare capabilities and dependencies.
2. Validate required kernel dependencies in `validateDependencies`.
3. Register services in `registerServices`.
4. Register contracts in `registerModuleContract`.
5. Keep product-specific event/config wiring in the dedicated hooks.
