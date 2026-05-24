# Phase 3: Governance Plane Hardening Plan

## Current State

The Governance Plane has:

- ✅ `AuditLogger` and `AuditStore` with test coverage
- ✅ `PolicyEvaluator` and `PolicyService` with test coverage
- ✅ `PolicyRecommendationService` with test coverage
- ✅ Retention classification tests
- ✅ Purge and rollback tests

## Target State

- **Tenant Isolation**: Centralized tenant isolation enforcement
- **Policy Checks**: Policy checks for every route category
- **Audit Events**: Audit events for all mutating operations
- **Encryption/Redaction**: Encryption and redaction tests
- **Policy Enforcement**: Comprehensive policy enforcement tests

## Implementation Tasks

### 1. Centralize Tenant Isolation

**Status**: Partial (audit logging has tenant context)

**Current State**:

- Audit logging includes tenant context
- Policy evaluation may need tenant-aware policies

**Required**:

- Central tenant isolation service
- Tenant context propagation
- Tenant boundary enforcement
- Cross-tenant access prevention

**Implementation**:

- Create `TenantIsolationService` in governance core
- Add tenant context to all governance operations
- Add cross-tenant access tests
- Integrate with existing audit logging

### 2. Policy Checks for Every Route Category

**Status**: Partial (PolicyEvaluator exists)

**Current State**:

- `PolicyEvaluator` exists with test coverage
- `PolicyService` provides policy evaluation

**Required**:

- Policy checks for all route categories:
  - Entity CRUD routes
  - Event append/read routes
  - Configuration routes
  - Governance routes
  - Operations routes

**Implementation**:

- Define route category taxonomy
- Add policy evaluation to route handlers
- Add policy violation detection
- Add policy enforcement tests

### 3. Audit Events for All Mutating Operations

**Status**: Partial (AuditLoggingTest exists)

**Current State**:

- `AuditLogger` captures read/write events
- `AuditStore` provides event storage
- Tests cover basic audit logging

**Required**:

- Audit events for:
  - Entity create/update/delete
  - Event append
  - Configuration changes
  - Policy changes
  - Retention changes
  - Purge operations

**Implementation**:

- Extend `AuditLogger` with additional event types
- Add audit event metadata (user, timestamp, operation, resource)
- Add audit event ordering guarantees
- Add audit event retention policies
- Add comprehensive audit tests

### 4. Encryption/Redaction Tests

**Status**: Pending

**Required**:

- Encryption at rest tests
- Encryption in transit tests
- Data redaction tests
- Key rotation tests
- Encryption failure handling

**Implementation**:

- Add encryption service tests
- Add redaction service tests
- Add key management tests
- Add encryption compliance tests
- Integrate with existing governance tests

### 5. Policy Enforcement Tests

**Status**: Partial (PolicyEvaluatorTest exists)

**Current State**:

- `PolicyEvaluatorTest` covers basic policy evaluation
- `PolicyRecommendationServiceTest` covers policy recommendations

**Required**:

- Policy enforcement tests for:
  - Data access policies
  - Retention policies
  - Purge policies
  - Encryption policies
  - Tenant isolation policies

**Implementation**:

- Extend `PolicyEvaluatorTest` with enforcement scenarios
- Add policy violation tests
- Add policy override tests
- Add policy rollback tests
- Add policy compliance tests

## Exit Criteria

- [ ] Tenant isolation is centralized and enforced
- [ ] Policy checks exist for every route category
- [ ] Audit events capture all mutating operations
- [ ] Encryption/redaction tests are comprehensive
- [ ] Policy enforcement tests cover all scenarios

## Dependencies

- Phase 3: Data Plane hardening (completed)
- Phase 3: Event Plane hardening (completed)
- Phase 3: Operations Plane hardening (pending)

## Related Files

- `products/data-cloud/planes/governance/core/src/main/java/com/ghatana/datacloud/governance/PolicyEvaluator.java`
- `products/data-cloud/planes/governance/core/src/main/java/com/ghatana/datacloud/governance/PolicyService.java`
- `products/data-cloud/planes/governance/core/src/test/java/com/ghatana/datacloud/governance/audit/AuditLoggingTest.java`
- `products/data-cloud/planes/governance/core/src/test/java/com/ghatana/datacloud/governance/PolicyEvaluatorTest.java`

## Notes

The Governance Plane has a solid foundation with existing audit logging and policy evaluation. The main gaps are:

1. Centralized tenant isolation service
2. Route category-specific policy checks
3. Comprehensive audit event coverage
4. Encryption/redaction tests
5. Policy enforcement tests

These should be implemented incrementally with each addition having corresponding tests.
