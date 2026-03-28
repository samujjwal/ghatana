# Kernel Audit Architecture

## Purpose

Kernel now provides a default tamper-evident audit service in `platform/java/kernel` so products do not have to reimplement hash-chain integrity, Merkle-style rooting, or verification semantics.

## Design

- `AuditTrailService` remains the product-facing contract.
- `DefaultAuditTrailService` owns canonical event normalization, hash generation, Merkle root computation, and integrity verification.
- `AuditTrailPersistence` is the single product extension point for storage.
- Products can reuse the default implementation and swap only persistence.

## Product Guidance

- Use the default implementation whenever audit semantics are shared.
- Keep persistence product-specific and dataset-specific.
- Never bypass the kernel audit service for domain actions that require compliance evidence.
- Treat event payloads as immutable once recorded.

## PHR Reference

PHR uses `PHRAuditTrailServiceImpl`, which extends `DefaultAuditTrailService` and binds storage to the `phr.audit` dataset path.
