# @ghatana/data-access-context

Kernel-owned data access and idempotency contracts for TypeScript products. Product adapters map authenticated transport requests into this context before persistence, audit, telemetry, and mutating operations run.

## Installation

```bash
pnpm add @ghatana/data-access-context
```

## Usage

### Using the builder pattern

```typescript
import { DataAccessContextBuilder } from '@ghatana/data-access-context';

const context = new DataAccessContextBuilder()
  .setTenantId('tenant-123')
  .setPrincipalId('user-456')
  .setCorrelationId('req-789')
  .setAuditClassification('PERSONAL_MEMORY_WRITE')
  .setDataOwnerScope('flashit:moment:moment-123')
  .setIdempotencyKey('idem-123')
  .build();
```

### Using the helper function

```typescript
import { createDataAccessContext } from '@ghatana/data-access-context';

const context = createDataAccessContext('tenant-123', 'user-456', {
  correlationId: 'req-789',
  clientIp: '192.168.1.1',
  auditClassification: 'PERSONAL_MEMORY_WRITE',
  dataOwnerScope: 'flashit:moment:moment-123',
  idempotencyKey: 'idem-123',
  requireIdempotencyKey: true,
});
```

## Fields

- `tenantId` - Required tenant ID for multi-tenancy
- `principalId` - Required principal/user ID
- `correlationId` - Optional correlation ID for request tracing
- `requestId` - Optional request ID for tracking
- `clientIp` - Optional client IP address
- `userAgent` - Optional user agent string
- `auditClassification` - Product or platform audit classification
- `dataOwnerScope` - Product-scoped data owner/resource scope
- `idempotencyKey` - Caller-supplied key for mutating operations
- `metadata` - Safe scalar metadata for audit and telemetry enrichment

## Idempotent Mutations

```typescript
import {
  createIdempotencyFingerprint,
  createInMemoryIdempotencyStore,
  runIdempotentMutation,
} from '@ghatana/data-access-context';

const store = createInMemoryIdempotencyStore<{ id: string }>();
const fingerprint = createIdempotencyFingerprint(['POST', '/moments', { title: 'Launch' }]);

const result = await runIdempotentMutation({
  context,
  fingerprint,
  ttlMs: 24 * 60 * 60 * 1000,
  store,
  execute: async () => ({ id: 'moment-123' }),
});
```

`runIdempotentMutation` replays a completed response for the same key and fingerprint, rejects same-key requests with a different fingerprint, expires old keys, and returns audit metadata for each path.

## Purpose

This context is used throughout the system to provide consistent tenant and principal information to:
- Database operations for row-level security
- Audit logging for compliance
- Observability for request tracing
- Multi-tenancy enforcement
- Idempotency enforcement for mutating operations
