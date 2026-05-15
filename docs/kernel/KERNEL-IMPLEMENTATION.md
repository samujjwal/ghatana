# Kernel Implementation Reference

> **Current Implementation Plan**: The canonical implementation plan for Kernel lifecycle, Data Cloud platform providers, YAPPC handoff, Studio integration, and Digital Marketing pilot is maintained in:
> 
> `products/yappc/docs/audits/yappc-todos.md`
>
> That document contains the granular implementation plan grounded in the current repo state, with workstreams covering:
> - ProductUnitIntent durable handoff
> - Kernel lifecycle truth and provider modes
> - Kernel API and Data Cloud gateway integration
> - Data Cloud platform providers
> - Digital Marketing end-to-end lifecycle pilot
> - Ghatana Studio foundation and UX consistency
> - Agentic lifecycle governance
> - Artifact intelligence integration
> - Product-shape matrix and future product enablement
> - CI/CD and workflow hardening
> - Documentation and cleanup

## Authoritative Architecture

The authoritative Kernel architecture documentation is in:

`docs/kernel/01-ARCHITECTURE.md`

All other Kernel documentation should defer to that document for architectural truth.

## Historical TODO Docs

The following historical TODO docs have been removed as they referenced an older commit snapshot and are superseded by the current implementation plan:
- KERNEL-TODOS.md (removed - outdated)
- KERNEL-TODOS-REMAINING.md (removed - all items completed)
- KERNEL_REMAINING_TASKS_TRACKER.md (removed - superseded)

## Validation

Run the final validation suite after any major changes:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-product-boundary-audit
pnpm check:kernel-platform-lifecycle
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
./gradlew build
./gradlew check
```
