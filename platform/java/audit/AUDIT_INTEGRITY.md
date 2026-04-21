# Audit Integrity Guarantees

## Overview

The audit module implements hash-chain integrity verification to ensure audit logs cannot be tampered with. This provides append-only guarantees for audit trails.

## Implementation

### Hash-Chain Mechanism

Each audit event contains two hash fields:
- `previous_hash`: The chain hash of the previous audit event (null for first event)
- `chain_hash`: SHA-256 hash computed from all event fields including the previous hash

### Hash Computation

The chain hash is computed as:
```
SHA-256(previous_hash + "|" + id + "|" + tenantId + "|" + eventType + "|" + principal + "|" + resourceType + "|" + resourceId + "|" + success + "|" + timestamp + "|" + detailsJson)
```

This creates a chain where each event cryptographically references the previous event, making it impossible to modify or delete events without breaking the chain.

### Components

- `AuditIntegrityService`: Computes and verifies hash-chain integrity
- `AuditEventEntity`: Stores `previous_hash` and `chain_hash` fields
- `JpaAuditService`: Integrates hash computation when recording events

### Verification

The `AuditIntegrityService.verifyChainIntegrity()` method:
1. Iterates through events in order (oldest to newest)
2. Verifies each event's chain hash matches the computed hash
3. Returns false if any hash mismatch is detected

### Usage

```java
// Recording events (automatic hash computation)
AuditEvent event = AuditEvent.builder()
    .tenantId("tenant-1")
    .eventType("USER_LOGIN")
    .principal("user-1")
    .build();
auditService.record(event);

// Verifying chain integrity
List<AuditEventEntity> events = auditService.findByTenantId("tenant-1");
boolean isIntact = integrityService.verifyChainIntegrity(events);
```

### Database Migration

The following columns were added to the `audit_events` table:
- `previous_hash` VARCHAR(64) - Hash of previous event
- `chain_hash` VARCHAR(64) NOT NULL - Computed hash of this event

A database migration script should be added to add these columns.

## Security Properties

- **Append-only**: Events cannot be deleted without breaking the chain
- **Tamper-evident**: Any modification to event data breaks the chain
- **Cryptographic**: Uses SHA-256 for hash computation
- **Tenant-isolated**: Hash chains are per-tenant

## Testing

See `AuditIntegrityServiceTest` for comprehensive test coverage of hash computation and chain verification.

## Limitations

- Hash-chain verification requires ordered events
- Chain breaks are detected but not automatically repaired
- Previous events must be available for verification
