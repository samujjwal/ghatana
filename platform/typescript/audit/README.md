# @ghatana/audit

Shared platform audit-logging primitives for Ghatana products.

## Purpose

Provides structured audit-log types, interfaces, and utilities used across Ghatana products to record security-relevant and compliance-relevant events in a consistent, queryable format.

## Usage

```ts
import { AuditEvent, AuditLogger, AuditSeverity } from "@ghatana/audit";
```

## API

- `AuditEvent` — base audit event type with `eventId`, `tenantId`, `actorId`, `action`, `timestamp`, `severity`
- `AuditLogger` — interface for writing audit events to a durable sink
- `AuditSeverity` — enum: `INFO`, `WARN`, `ERROR`, `CRITICAL`

## Ownership

Platform Engineering. Part of the `@ghatana/design-system` platform layer — see [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
