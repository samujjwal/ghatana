# ADR: Kernel Contract Registry Import

**Status**: Accepted  
**Date**: 2026-05-27  
**Applies to**: `YAPPC-P0-001`, `YAPPC-P0-002`, `YAPPC-P0-003`, `YAPPC-P1-013`

---

## Context

YAPPC emits and validates `ProductUnitIntent` payloads that are consumed by the Kernel. Provider IDs, lifecycle profiles, surface types, product unit kinds, and implementation statuses belong to the Kernel public contract. Keeping those values as YAPPC-local constants creates drift risk: YAPPC can accept or export values that the Kernel no longer recognizes, or reject values that the Kernel has added.

YAPPC still needs a local registry object for validation and export services, but the registry must be populated from imported Kernel contract data rather than manually maintained local lists.

---

## Decision

`ProductUnitKernelContractRegistry` loads the imported Kernel ProductUnit contract resource `kernel-product-unit-contract.json` and exposes registry methods over that contract. YAPPC validation and export flows depend on this registry instead of defining independent provider/profile/surface sets.

The contract import is treated as a public boundary:

- Kernel-owned values are imported into a resource consumed by YAPPC.
- YAPPC validates provider, lifecycle profile, surface, product unit kind, and implementation status through the imported contract.
- ProductUnitIntent export builds typed DTOs and validates the exported payload against the same registry-backed service before handoff.
- Parity tests compare the imported registry values with Kernel public constants so drift fails in tests.

---

## Consequences

- Kernel remains the owner of ProductUnit public contract values.
- YAPPC can keep a small product-local registry API without owning the data it exposes.
- New Kernel contract values require a contract import update and parity test evidence.
- New YAPPC ProductUnitIntent fields must be backed by typed DTO/schema handling before being exported or accepted by the backend handoff API.
- Hand-coded provider/profile/surface constants are not allowed in new YAPPC ProductUnitIntent validation or export code.

---

## Validation

- `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/ProductUnitKernelContractRegistry.java`
- `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/ProductUnitIntentValidationService.java`
- `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/ProductUnitIntentExporter.java`
- `products/yappc/core/scaffold/api/src/test/java/com/ghatana/yappc/kernel/ProductUnitKernelContractRegistryTest.java`
- `products/yappc/core/scaffold/api/src/test/java/com/ghatana/yappc/kernel/ProductUnitIntentExporterTest.java`
- `products/yappc/docs/YAPPC_BACKLOG_PROGRESS.md`
