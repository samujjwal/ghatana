# @ghatana/data-access-context

Kernel-owned data access context for database operations. Provides tenant, principal, and request correlation information for audit logging, row-level security, and observability.

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
  .build();
```

### Using the helper function

```typescript
import { createDataAccessContext } from '@ghatana/data-access-context';

const context = createDataAccessContext('tenant-123', 'user-456', {
  correlationId: 'req-789',
  clientIp: '192.168.1.1',
});
```

## Fields

- `tenantId` - Required tenant ID for multi-tenancy
- `principalId` - Required principal/user ID
- `correlationId` - Optional correlation ID for request tracing
- `requestId` - Optional request ID for tracking
- `clientIp` - Optional client IP address
- `userAgent` - Optional user agent string

## Purpose

This context is used throughout the system to provide consistent tenant and principal information to:
- Database operations for row-level security
- Audit logging for compliance
- Observability for request tracing
- Multi-tenancy enforcement
