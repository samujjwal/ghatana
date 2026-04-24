# Audit Trail Plugin

This module provides audit trail plugin implementations with explicit variant semantics.

## Implementations

- `StandardAuditTrailPlugin`
  - Variant: `standard-in-memory`
  - Durability: `non-durable`
  - Intended use: local development, lightweight tests, non-critical runtime paths
- `DurableAuditTrailPlugin`
  - Variant: `durable-jdbc`
  - Durability: `durable`
  - Intended use: production paths requiring restart-safe audit persistence

## Export Behavior Contract

Both implementations declare export support in `PluginMetadata.properties`.

- `supportedExportFormats`: `JSON`, `CSV`, `XML`
- `unsupportedExportFormats`: `PDF`
- `unsupportedExportReason`: variant-specific explanation

The runtime behavior matches metadata:

- `exportTrail(..., JSON|CSV|XML, ...)` succeeds.
- `exportTrail(..., PDF, ...)` fails with `UnsupportedOperationException`.

## Schema and Durability Notes

`DurableAuditTrailPlugin` requires `ensureSchema()` before start-up. This operation is idempotent and safe to call at each application boot.
