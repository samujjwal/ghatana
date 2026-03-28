# Kernel Service Development Guide

## Use The Shared Lifecycle

Kernel-managed services should implement `KernelLifecycleAware`. When lifecycle boilerplate is otherwise identical, prefer `AbstractKernelService`.

## Rules

- Keep `start()` idempotent.
- Keep `stop()` idempotent.
- Report health through `isHealthy()`.
- Do not hide failures during startup or shutdown.
- Put domain APIs on the concrete service, not on generic helpers.

## Data-Cloud Services

If a service persists records through Data-Cloud, prefer `AbstractDataCloudService` for common audit and adapter access patterns before adding local helpers.
