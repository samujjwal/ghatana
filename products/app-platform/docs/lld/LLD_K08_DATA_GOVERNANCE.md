# LOW-LEVEL DESIGN: K-08 DATA GOVERNANCE SERVICE

**Module**: K-08 Data Governance  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Architecture Team

> **Implementation alignment**: Per [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md), shared data-governance and storage abstraction concerns should align to Ghatana `products:data-cloud:platform` and `products:data-cloud:spi`.

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Data Governance Service provides **data lineage tracking, residency enforcement, retention lifecycle management, encryption abstraction, and data subject rights (GDPR/PDPA)** for all data across Project Siddhanta.

**Core Responsibilities**:

- Data classification (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED, PII)
- Data lineage tracking across service boundaries
- Residency enforcement — ensure data stays in designated jurisdictions
- Retention lifecycle management (creation → active → archive → purge)
- Encryption-at-rest abstraction via K-14 integration
- Key rotation coordination across all encrypted stores
- Row-Level Security (RLS) enforcement policy management
- Data breach response workflow orchestration
- Data subject rights (erasure, portability, access requests)
- Dual-calendar timestamps on all governance metadata

**Invariants**:

1. Data MUST be classified before storage
2. Residency rules MUST be enforced at write time — no cross-border writes without policy approval
3. Retention policies MUST be applied to all data stores
4. PII data MUST be encrypted at field level
5. All governance actions MUST be audited via K-07

### 1.2 Explicit Non-Goals

- ❌ Encryption key storage (K-14 Secrets Management)
- ❌ Authentication/authorization enforcement (K-01 IAM)
- ❌ Data storage implementation (service-specific databases)

### 1.3 Dependencies

| Dependency           | Purpose                                                    | Readiness Gate       |
| -------------------- | ---------------------------------------------------------- | -------------------- |
| K-01 IAM             | Access control for governance operations                   | K-01 stable          |
| K-02 Config Engine   | Governance policies, classification rules                  | K-02 stable          |
| K-05 Event Bus       | Governance events, lineage events                          | K-05 stable          |
| K-06 Observability   | Governance metrics, alerting                               | K-06 stable          |
| K-07 Audit Framework | Governance audit trail                                     | K-07 stable          |
| K-14 Secrets Mgmt    | Encryption key operations                                  | K-14 stable          |
| Ghatana Data Cloud   | Shared lineage, storage abstraction, lifecycle integration | Data Cloud available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/data-governance/classify
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "data_source": "iam_service",
  "table_name": "users",
  "field_name": "email",
  "classification": "PII",
  "jurisdiction": "NP",
  "retention_policy": "10_YEARS",
  "encryption_required": true
}

Response 201:
{
  "classification_id": "cls_abc123",
  "status": "ACTIVE",
  "created_at_bs": "2082-11-25",
  "created_at_gregorian": "2026-03-08T10:00:00Z"
}
```

```yaml
GET /api/v1/data-governance/lineage/{entity_id}
Authorization: Bearer {admin_token}

Response 200:
{
  "entity_id": "order_123",
  "entity_type": "Order",
  "lineage": [
    {
      "service": "oms_service",
      "operation": "CREATE",
      "timestamp_gregorian": "2026-03-08T10:00:00Z",
      "timestamp_bs": "2082-11-25"
    },
    {
      "service": "risk_engine",
      "operation": "READ",
      "timestamp_gregorian": "2026-03-08T10:00:01Z",
      "timestamp_bs": "2082-11-25"
    }
  ]
}
```

```yaml
POST /api/v1/data-governance/erasure-request
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "subject_id": "user_456",
  "request_type": "ERASURE",
  "jurisdiction": "EU",
  "reason": "GDPR Article 17 request"
}

Response 202:
{
  "request_id": "era_789",
  "status": "PENDING_REVIEW",
  "estimated_completion": "2026-03-15T00:00:00Z"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.datagovernance.v1;

service DataGovernanceService {
  rpc Classify(ClassifyRequest) returns (ClassifyResponse);
  rpc GetLineage(GetLineageRequest) returns (LineageResponse);
  rpc GetClassification(GetClassificationRequest) returns (Classification);
  rpc SetRetentionPolicy(RetentionPolicyRequest) returns (RetentionPolicyResponse);
  rpc RequestErasure(ErasureRequest) returns (ErasureResponse);
  rpc GetResidencyPolicy(ResidencyPolicyRequest) returns (ResidencyPolicy);
  rpc TriggerKeyRotation(KeyRotationRequest) returns (KeyRotationResponse);
}
```

### 2.3 SDK Method Signatures

```typescript
interface DataGovernanceClient {
  classify(request: ClassifyRequest): Promise<Classification>;
  getLineage(entityId: string, entityType: string): Promise<Lineage>;
  getClassification(
    source: string,
    table: string,
    field: string,
  ): Promise<Classification>;
  setRetentionPolicy(policy: RetentionPolicy): Promise<void>;
  requestErasure(
    subjectId: string,
    jurisdiction: string,
  ): Promise<ErasureRequest>;
  getResidencyPolicy(jurisdiction: string): Promise<ResidencyPolicy>;
  triggerKeyRotation(scope: string): Promise<KeyRotationResult>;
  checkResidency(data: any, targetRegion: string): Promise<ResidencyCheck>;
}
```

---

## 3. DATA MODEL

### 3.1 Storage Tables

```sql
CREATE TABLE data_classifications (
    classification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    field_name VARCHAR(255),
    classification VARCHAR(50) NOT NULL CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','PII')),
    jurisdiction VARCHAR(10) NOT NULL,
    retention_policy VARCHAR(50) NOT NULL,
    encryption_required BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_at_bs VARCHAR(20),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(data_source, table_name, field_name)
);

CREATE TABLE data_lineage_events (
    lineage_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    operation VARCHAR(50) NOT NULL CHECK (operation IN ('CREATE','READ','UPDATE','DELETE','TRANSFER','ARCHIVE')),
    source_jurisdiction VARCHAR(10),
    target_jurisdiction VARCHAR(10),
    trace_id VARCHAR(255),
    timestamp_gregorian TIMESTAMPTZ NOT NULL,
    timestamp_bs VARCHAR(20),
    metadata JSONB DEFAULT '{}'
);
CREATE INDEX idx_lineage_entity ON data_lineage_events(entity_id, entity_type);
CREATE INDEX idx_lineage_time ON data_lineage_events(timestamp_gregorian);

CREATE TABLE retention_policies (
    policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_name VARCHAR(255) UNIQUE NOT NULL,
    retention_days INTEGER NOT NULL,
    archive_after_days INTEGER,
    purge_after_days INTEGER NOT NULL,
    jurisdiction VARCHAR(10) NOT NULL,
    data_classification VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE erasure_requests (
    request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id VARCHAR(255) NOT NULL,
    request_type VARCHAR(50) NOT NULL CHECK (request_type IN ('ERASURE','PORTABILITY','ACCESS')),
    jurisdiction VARCHAR(10) NOT NULL,
    reason TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewer_id UUID,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_at_bs VARCHAR(20)
);

CREATE TABLE residency_policies (
    policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jurisdiction VARCHAR(10) NOT NULL,
    allowed_regions TEXT[] NOT NULL,
    data_classification VARCHAR(50) NOT NULL,
    enforcement_mode VARCHAR(20) DEFAULT 'STRICT' CHECK (enforcement_mode IN ('STRICT','WARN','AUDIT_ONLY')),
    is_active BOOLEAN DEFAULT true
);
```

### 3.2 Event Schemas

```json
{
  "event_type": "DataClassifiedEvent",
  "event_version": "1.0.0",
  "data": {
    "classification_id": "cls_abc123",
    "data_source": "iam_service",
    "table_name": "users",
    "field_name": "email",
    "classification": "PII",
    "jurisdiction": "NP"
  }
}
```

```json
{
  "event_type": "DataErasedEvent",
  "event_version": "1.0.0",
  "data": {
    "request_id": "era_789",
    "subject_id": "user_456",
    "services_affected": ["iam_service", "oms_service"],
    "fields_erased": 15,
    "jurisdiction": "EU"
  }
}
```

```json
{
  "event_type": "EncryptionKeyRotatedEvent",
  "event_version": "1.0.0",
  "data": {
    "scope": "iam_service.users.pii",
    "previous_key_version": "v3",
    "new_key_version": "v4",
    "records_re_encrypted": 50000
  }
}
```

---

## 4. CONTROL FLOW

### 4.1 Data Classification Flow

```
1. Admin submits classification request
2. Validate classification parameters
3. Check for existing classification (upsert)
4. Store classification in data_classifications
5. Publish DataClassifiedEvent to K-05
6. Log to K-07 audit trail
7. If encryption_required, coordinate with K-14 for key provisioning
```

### 4.2 Erasure Request Flow

```
1. Data subject submits erasure request
2. Create erasure_request record (PENDING_REVIEW)
3. Reviewer approves/rejects (maker-checker)
4. If approved: enumerate all services holding subject data
5. For each service: issue erasure command
6. Collect confirmation from each service
7. Update request status to COMPLETED
8. Publish DataErasedEvent to K-05
9. Log completion to K-07
```

### 4.3 Key Rotation Flow

```
1. Rotation trigger (scheduled or manual)
2. Generate new key version via K-14
3. For each affected data store:
   a. Begin re-encryption batch job
   b. Re-encrypt records with new key
   c. Verify integrity
4. Deprecate old key version
5. Publish EncryptionKeyRotatedEvent
6. Log rotation to K-07
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Residency Enforcement Algorithm

```
function checkResidency(data, targetRegion, jurisdiction):
  policy = getResidencyPolicy(jurisdiction, data.classification)
  if targetRegion NOT IN policy.allowed_regions:
    if policy.enforcement_mode == 'STRICT':
      REJECT write with ResidencyViolationError
    elif policy.enforcement_mode == 'WARN':
      LOG warning, ALLOW write
    elif policy.enforcement_mode == 'AUDIT_ONLY':
      LOG audit event, ALLOW write
  return ALLOW
```

### 5.2 Retention Lifecycle

```
ACTIVE (0 → archive_after_days) → ARCHIVED (archive_after_days → purge_after_days) → PURGED
```

---

## 6. NFR BUDGETS

| Operation           | P99 Latency | Throughput | Timeout |
| ------------------- | ----------- | ---------- | ------- |
| classify()          | 5ms         | 10,000/s   | 500ms   |
| getLineage()        | 10ms        | 5,000/s    | 1000ms  |
| checkResidency()    | 1ms         | 100,000/s  | 100ms   |
| getClassification() | 2ms         | 50,000/s   | 100ms   |
| requestErasure()    | 50ms        | 100/s      | 5000ms  |

**Availability**: 99.999%

---

## 7. SECURITY DESIGN

- **Access Control**: K-01 RBAC — only governance admins can classify/modify
- **Encryption**: All PII classifications trigger field-level encryption via K-14
- **Audit**: Every governance action logged to K-07 with hash-chain
- **Tenant Isolation**: RLS policies managed per tenant

---

## 8. OBSERVABILITY & AUDIT

### Metrics

- `data_governance_classifications_total` — Total classifications by type
- `data_governance_erasure_requests_total` — Erasure requests by status
- `data_governance_key_rotations_total` — Key rotations by scope
- `data_governance_residency_violations_total` — Residency policy violations

### Alerts

- Erasure request pending > 72 hours → P2 alert
- Key rotation failure → P1 alert
- Residency violation in STRICT mode → P1 alert

---

## 9. EXTENSIBILITY & EVOLUTION

### Extension Points

- T1 Config Packs: Jurisdiction-specific retention policies, residency rules
- T2 Rule Packs: Custom classification rules per data type
- Custom erasure handlers per service (T3)

### Versioning

- API versioned (/v1/, /v2/)
- Event schema versioned with backward compatibility

---

## 10. TEST PLAN

### Unit Tests

- Classification CRUD operations
- Residency enforcement logic (STRICT/WARN/AUDIT_ONLY)
- Retention lifecycle state transitions
- Erasure workflow state machine

### Integration Tests

- K-14 integration for key rotation
- K-07 audit trail verification
- K-05 event publication
- Cross-service erasure coordination

### Security Tests

- Unauthorized classification attempt → 403
- PII data without encryption → validation error
- Cross-tenant data access → blocked by RLS

### Performance Tests

- checkResidency() at 100K TPS — P99 <1ms
- Bulk key rotation for 1M records — complete within SLA
