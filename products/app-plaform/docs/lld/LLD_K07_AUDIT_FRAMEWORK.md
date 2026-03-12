# LOW-LEVEL DESIGN: K-07 AUDIT FRAMEWORK

**Module**: K-07 Audit Framework  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Audit Framework provides **immutable, cryptographically-chained audit logging** for all critical actions across Project Siddhanta, ensuring regulatory compliance and forensic analysis capabilities.

**Core Responsibilities**:
- SDK-enforced audit logging for all services
- Immutable audit trail with cryptographic chaining
- Standardized audit event schema
- Evidence export for regulatory compliance
- Retention policy enforcement
- Maker-checker workflow linkage
- Dual-calendar timestamping (BS + Gregorian)
- Tamper detection via hash chain verification

**Invariants**:
1. Audit logs MUST be immutable (append-only)
2. All audit events MUST be cryptographically chained
3. Audit events MUST have dual-calendar timestamps
4. Audit trail MUST be tamper-evident
5. Retention policies MUST be enforced automatically

### 1.2 Explicit Non-Goals

- ❌ Real-time analytics on audit data (use separate analytics engine)
- ❌ Business intelligence/reporting (use BI tools)
- ❌ Application logging (use structured logging framework)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Retention policy configuration | K-02 stable |
| K-05 Event Bus | Audit event publication | K-05 stable |
| PostgreSQL | Audit log storage | DB available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/audit/log
Authorization: Bearer {service_token}
Content-Type: application/json

Request:
{
  "action": "ORDER_PLACED",
  "actor": {
    "user_id": "user_456",
    "role": "TRADER",
    "ip_address": "192.168.1.100"
  },
  "resource": {
    "type": "Order",
    "id": "order_123"
  },
  "details": {
    "instrument_id": "NABIL",
    "quantity": 100,
    "price": 1250.50
  },
  "outcome": "SUCCESS",
  "tenant_id": "tenant_np_1",
  "trace_id": "abc-123",
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}

Response 201:
{
  "audit_id": "aud_7a8b9c0d",
  "sequence_number": 15234,
  "previous_hash": "sha256:abc123...",
  "current_hash": "sha256:def456...",
  "timestamp": "2025-03-02T10:30:00.123Z"
}
```

```yaml
GET /api/v1/audit/search
Authorization: Bearer {admin_token}

Query Parameters:
  - tenant_id: tenant_np_1
  - action: ORDER_PLACED
  - actor_user_id: user_456
  - from_date: 2025-03-01T00:00:00Z
  - to_date: 2025-03-02T23:59:59Z
  - limit: 100

Response 200:
{
  "audit_logs": [
    {
      "audit_id": "aud_7a8b9c0d",
      "action": "ORDER_PLACED",
      "actor": {...},
      "resource": {...},
      "details": {...},
      "outcome": "SUCCESS",
      "timestamp_gregorian": "2025-03-02T10:30:00Z",
      "timestamp_bs": "2081-11-17"
    }
  ],
  "total": 1,
  "next_cursor": null
}
```

```yaml
POST /api/v1/audit/export
Authorization: Bearer {admin_token}

Request:
{
  "tenant_id": "tenant_np_1",
  "from_date": "2025-01-01T00:00:00Z",
  "to_date": "2025-03-31T23:59:59Z",
  "format": "CSV",
  "include_evidence": true
}

Response 202:
{
  "export_id": "exp_123",
  "status": "PROCESSING",
  "estimated_completion": "2025-03-02T10:35:00Z"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.audit.v1;

service AuditService {
  rpc LogAuditEvent(LogAuditEventRequest) returns (LogAuditEventResponse);
  rpc SearchAuditLogs(SearchAuditLogsRequest) returns (SearchAuditLogsResponse);
  rpc VerifyAuditChain(VerifyAuditChainRequest) returns (VerifyAuditChainResponse);
  rpc ExportAuditLogs(ExportAuditLogsRequest) returns (ExportAuditLogsResponse);
}

message LogAuditEventRequest {
  string action = 1;
  Actor actor = 2;
  Resource resource = 3;
  google.protobuf.Struct details = 4;
  string outcome = 5;
  string tenant_id = 6;
  string trace_id = 7;
  string timestamp_bs = 8;
  google.protobuf.Timestamp timestamp_gregorian = 9;
}

message Actor {
  string user_id = 1;
  string role = 2;
  string ip_address = 3;
  optional string session_id = 4;
}

message Resource {
  string type = 1;
  string id = 2;
  optional string parent_id = 3;
}

message LogAuditEventResponse {
  string audit_id = 1;
  int64 sequence_number = 2;
  string previous_hash = 3;
  string current_hash = 4;
  google.protobuf.Timestamp timestamp = 5;
}

message VerifyAuditChainRequest {
  string tenant_id = 1;
  optional int64 from_sequence = 2;
  optional int64 to_sequence = 3;
}

message VerifyAuditChainResponse {
  bool valid = 1;
  repeated ChainViolation violations = 2;
}

message ChainViolation {
  int64 sequence_number = 1;
  string expected_hash = 2;
  string actual_hash = 3;
}
```

### 2.3 SDK Method Signatures

```typescript
interface AuditClient {
  /**
   * Log audit event
   * @throws AuditError
   */
  log(event: AuditEvent): Promise<AuditReceipt>;

  /**
   * Log with automatic actor context
   */
  logWithContext(
    action: string,
    resource: Resource,
    details?: unknown
  ): Promise<AuditReceipt>;

  /**
   * Search audit logs
   */
  search(criteria: AuditSearchCriteria): Promise<AuditLog[]>;

  /**
   * Verify audit chain integrity
   */
  verifyChain(
    tenantId: string,
    fromSequence?: number,
    toSequence?: number
  ): Promise<ChainVerificationResult>;

  /**
   * Export audit logs
   */
  export(request: ExportRequest): Promise<ExportJob>;
}

interface AuditEvent {
  action: string;
  actor: Actor;
  resource: Resource;
  details?: unknown;
  outcome: 'SUCCESS' | 'FAILURE' | 'PARTIAL';
  tenantId: string;
  traceId?: string;
  timestampBs: string;
  timestampGregorian: Date;
}

interface AuditReceipt {
  auditId: string;
  sequenceNumber: number;
  previousHash: string;
  currentHash: string;
  timestamp: Date;
}

interface Actor {
  userId: string;
  role: string;
  ipAddress: string;
  sessionId?: string;
}

interface Resource {
  type: string;
  id: string;
  parentId?: string;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| AUDIT_E001 | 400 | No | Invalid audit event schema |
| AUDIT_E002 | 500 | Yes | Audit store unavailable |
| AUDIT_E003 | 500 | No | Hash chain broken |
| AUDIT_E004 | 403 | No | Insufficient permissions |
| AUDIT_E005 | 400 | No | Invalid date range |
| AUDIT_E006 | 500 | Yes | Export job failed |

---

## 3. DATA MODEL

### 3.1 Event Schemas

#### AuditLogCreatedEvent v1.0.0

> **K-05 Envelope Compliant** — all events use the standard envelope from LLD_K05_EVENT_BUS §3.1.

```json
{
  "event_id": "uuid",
  "event_type": "AuditLogCreated",
  "event_version": "1.0.0",
  "aggregate_id": "aud_7a8b9c0d",
  "aggregate_type": "AuditLog",
  "sequence_number": 1,
  "timestamp_bs": "2081-11-17T10:30:00",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "metadata": {
    "trace_id": "trace-uuid",
    "causation_id": "cmd-uuid",
    "correlation_id": "corr-uuid",
    "tenant_id": "tenant_np_1"
  },
  "data": {
    "audit_id": "aud_7a8b9c0d",
    "action": "ORDER_PLACED",
    "actor": {
      "user_id": "user_456",
      "role": "TRADER",
      "ip_address": "192.168.1.100"
    },
    "resource": {
      "type": "Order",
      "id": "order_123"
    },
    "outcome": "SUCCESS",
    "audit_sequence_number": 15234,
    "current_hash": "sha256:def456..."
  }
}
```

> **Note**: `data.audit_sequence_number` is the domain-level hash-chain sequence (tamper-evidence), distinct from the envelope-level `sequence_number` (event bus ordering).

### 3.2 Storage Tables

#### audit_logs

```sql
CREATE TABLE audit_logs (
  audit_id VARCHAR(255) PRIMARY KEY,
  sequence_number BIGSERIAL NOT NULL,
  action VARCHAR(255) NOT NULL,
  actor JSONB NOT NULL,
  resource JSONB NOT NULL,
  details JSONB,
  outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE', 'PARTIAL')),
  tenant_id VARCHAR(255) NOT NULL,
  trace_id VARCHAR(255),
  previous_hash VARCHAR(64) NOT NULL,
  current_hash VARCHAR(64) NOT NULL,
  timestamp_bs VARCHAR(10) NOT NULL,
  timestamp_gregorian TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (tenant_id, sequence_number)
);

CREATE INDEX idx_audit_logs_tenant_seq ON audit_logs(tenant_id, sequence_number);
CREATE INDEX idx_audit_logs_action ON audit_logs(action, timestamp_gregorian);
CREATE INDEX idx_audit_logs_actor ON audit_logs((actor->>'user_id'), timestamp_gregorian);
CREATE INDEX idx_audit_logs_resource ON audit_logs((resource->>'type'), (resource->>'id'));
CREATE INDEX idx_audit_logs_trace ON audit_logs(trace_id);
```

#### audit_retention_policies

```sql
CREATE TABLE audit_retention_policies (
  policy_id VARCHAR(255) PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  action_pattern VARCHAR(255) NOT NULL,
  retention_days INT NOT NULL,
  archive_after_days INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (tenant_id, action_pattern)
);

CREATE INDEX idx_retention_tenant ON audit_retention_policies(tenant_id);
```

#### audit_exports

```sql
CREATE TABLE audit_exports (
  export_id VARCHAR(255) PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  from_date TIMESTAMPTZ NOT NULL,
  to_date TIMESTAMPTZ NOT NULL,
  format VARCHAR(20) NOT NULL CHECK (format IN ('CSV', 'JSON', 'PDF')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
  file_url VARCHAR(500),
  requested_by VARCHAR(255) NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);

CREATE INDEX idx_exports_tenant ON audit_exports(tenant_id, requested_at);
```

---

## 4. CONTROL FLOW

### 4.1 Audit Logging Flow

```
Service → AuditClient.log(event)
  ↓
AuditClient → Validate event schema
  ↓ [Valid]
AuditClient → Get previous hash for tenant
  ↓
AuditClient → Calculate current hash
  ↓  hash = SHA256(previous_hash + event_data)
AuditClient → Assign sequence number (per tenant)
  ↓
AuditClient → Insert into audit_logs table
  ↓ [Transaction committed]
AuditClient → Publish AuditLogCreatedEvent to K-05
  ↓
AuditClient → Return AuditReceipt
```

### 4.2 Hash Chain Calculation

```
Calculate Hash:
  ↓
Get previous audit log for tenant
  ↓
Extract previous_hash
  ↓
Serialize current event:
  data = {
    action,
    actor,
    resource,
    details,
    outcome,
    timestamp_gregorian,
    sequence_number
  }
  ↓
Concatenate: message = previous_hash + JSON.stringify(data)
  ↓
Hash: current_hash = SHA256(message)
  ↓
Return current_hash
```

### 4.3 Chain Verification Flow

```
Admin → AuditClient.verifyChain(tenant_id, from_seq, to_seq)
  ↓
AuditClient → Query audit_logs for range
  ↓
AuditClient → Iterate through logs in sequence order
  ↓
For each log:
  ↓
  Calculate expected hash from previous_hash + event_data
  ↓
  Compare with stored current_hash
  ↓
  If mismatch → Record violation
  ↓
AuditClient → Return verification result
```

### 4.4 Retention Policy Enforcement

```
Scheduler (daily) → Run retention job
  ↓
RetentionJob → Query audit_retention_policies
  ↓
For each policy:
  ↓
  Calculate cutoff date (NOW - retention_days)
  ↓
  Query audit_logs matching action_pattern and older than cutoff
  ↓
  If archive_after_days set:
    ↓
    Archive to cold storage (S3 Glacier)
    ↓
    Delete from primary table
  Else:
    ↓
    Delete from primary table
  ↓
RetentionJob → Log retention actions
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Hash Chain Algorithm

```python
import hashlib
import json

class AuditHashChain:
    """
    Cryptographic hash chain for audit logs.
    """
    
    GENESIS_HASH = "0" * 64  # Initial hash for first log
    
    @staticmethod
    def calculate_hash(previous_hash: str, event: AuditEvent) -> str:
        """
        Calculate hash for audit event.
        """
        # Serialize event deterministically
        event_data = {
            'action': event.action,
            'actor': event.actor,
            'resource': event.resource,
            'details': event.details,
            'outcome': event.outcome,
            'timestamp': event.timestamp_gregorian.isoformat(),
            'sequence_number': event.sequence_number
        }
        
        # Sort keys for deterministic serialization
        serialized = json.dumps(event_data, sort_keys=True)
        
        # Concatenate previous hash and event data
        message = f"{previous_hash}{serialized}".encode()
        
        # Calculate SHA-256 hash
        current_hash = hashlib.sha256(message).hexdigest()
        
        return current_hash
    
    @staticmethod
    def verify_chain(logs: list[AuditLog]) -> tuple[bool, list[ChainViolation]]:
        """
        Verify integrity of audit log chain.
        """
        violations = []
        
        for i, log in enumerate(logs):
            # Get expected previous hash
            if i == 0:
                expected_previous = AuditHashChain.GENESIS_HASH
            else:
                expected_previous = logs[i - 1].current_hash
            
            # Verify previous hash matches
            if log.previous_hash != expected_previous:
                violations.append(ChainViolation(
                    sequence_number=log.sequence_number,
                    expected_hash=expected_previous,
                    actual_hash=log.previous_hash
                ))
            
            # Recalculate current hash
            expected_current = AuditHashChain.calculate_hash(
                log.previous_hash,
                log
            )
            
            # Verify current hash matches
            if log.current_hash != expected_current:
                violations.append(ChainViolation(
                    sequence_number=log.sequence_number,
                    expected_hash=expected_current,
                    actual_hash=log.current_hash
                ))
        
        return (len(violations) == 0, violations)
```

### 5.2 Retention Policy Matching

```python
import re
from datetime import datetime, timedelta

class RetentionPolicyMatcher:
    """
    Match audit logs to retention policies.
    """
    
    @staticmethod
    def match_policy(action: str, policies: list[RetentionPolicy]) -> RetentionPolicy:
        """
        Find matching retention policy for action.
        Policies are evaluated in order, first match wins.
        """
        for policy in policies:
            # Convert glob pattern to regex
            pattern = policy.action_pattern.replace('*', '.*')
            regex = re.compile(f'^{pattern}$')
            
            if regex.match(action):
                return policy
        
        # Default policy if no match
        return RetentionPolicy(
            policy_id='default',
            action_pattern='*',
            retention_days=3650  # 10 years default
        )
    
    @staticmethod
    def should_archive(log: AuditLog, policy: RetentionPolicy) -> bool:
        """
        Check if log should be archived.
        """
        if not policy.archive_after_days:
            return False
        
        archive_date = datetime.now() - timedelta(days=policy.archive_after_days)
        return log.timestamp_gregorian < archive_date
    
    @staticmethod
    def should_delete(log: AuditLog, policy: RetentionPolicy) -> bool:
        """
        Check if log should be deleted.
        """
        retention_date = datetime.now() - timedelta(days=policy.retention_days)
        return log.timestamp_gregorian < retention_date
```

### 5.3 Evidence Export

```python
import csv
import io

class AuditExporter:
    """
    Export audit logs for regulatory compliance.
    """
    
    async def export_to_csv(
        self,
        tenant_id: str,
        from_date: datetime,
        to_date: datetime
    ) -> bytes:
        """
        Export audit logs to CSV format.
        """
        # Query logs
        logs = await self.query_logs(tenant_id, from_date, to_date)
        
        # Create CSV
        output = io.StringIO()
        writer = csv.DictWriter(output, fieldnames=[
            'audit_id',
            'sequence_number',
            'action',
            'actor_user_id',
            'actor_role',
            'resource_type',
            'resource_id',
            'outcome',
            'timestamp_gregorian',
            'timestamp_bs',
            'current_hash'
        ])
        
        writer.writeheader()
        
        for log in logs:
            writer.writerow({
                'audit_id': log.audit_id,
                'sequence_number': log.sequence_number,
                'action': log.action,
                'actor_user_id': log.actor['user_id'],
                'actor_role': log.actor['role'],
                'resource_type': log.resource['type'],
                'resource_id': log.resource['id'],
                'outcome': log.outcome,
                'timestamp_gregorian': log.timestamp_gregorian.isoformat(),
                'timestamp_bs': log.timestamp_bs,
                'current_hash': log.current_hash
            })
        
        return output.getvalue().encode('utf-8')
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Timeout |
|-----------|-----|-----|-----|---------|
| log() | 3ms | 10ms | 30ms | 1000ms |
| search() | 20ms | 100ms | 300ms | 5000ms |
| verifyChain() | 50ms | 200ms | 500ms | 10000ms |
| export() - async | N/A | N/A | N/A | 300000ms |

### 6.2 Throughput Targets

| Operation | Target TPS | Peak TPS |
|-----------|------------|----------|
| log() | 50,000 | 100,000 |
| search() | 1,000 | 5,000 |

### 6.3 Storage Estimates

**Per Audit Log**: ~2KB (average)

**Daily Volume** (100K TPS peak):
- 100,000 TPS × 3,600s × 24h = 8.64B events/day
- 8.64B × 2KB = 17.28 TB/day

**Retention** (10 years):
- 17.28 TB/day × 365 days × 10 years = 63.1 PB

**Mitigation**:
- Archive to S3 Glacier after 90 days
- Compress archived logs (10:1 ratio)
- Effective storage: ~6.3 PB

---

## 7. SECURITY DESIGN

### 7.1 Immutability Enforcement

```sql
-- Prevent updates and deletes on audit_logs
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'Audit logs are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_immutable_update
  BEFORE UPDATE ON audit_logs
  FOR EACH ROW
  EXECUTE FUNCTION prevent_audit_modification();

CREATE TRIGGER audit_logs_immutable_delete
  BEFORE DELETE ON audit_logs
  FOR EACH ROW
  EXECUTE FUNCTION prevent_audit_modification();
```

### 7.2 Access Control

```typescript
interface AuditPermissions {
  canLog: (userId: string, tenantId: string) => boolean;
  canSearch: (userId: string, tenantId: string) => boolean;
  canExport: (userId: string, tenantId: string) => boolean;
  canVerifyChain: (userId: string, tenantId: string) => boolean;
}

class AuditAccessControl implements AuditPermissions {
  canLog(userId: string, tenantId: string): boolean {
    // All authenticated services can log
    return this.isAuthenticatedService(userId);
  }

  canSearch(userId: string, tenantId: string): boolean {
    // Only admins and auditors can search
    return this.hasRole(userId, ['ADMIN', 'AUDITOR'], tenantId);
  }

  canExport(userId: string, tenantId: string): boolean {
    // Only admins can export
    return this.hasRole(userId, ['ADMIN'], tenantId);
  }

  canVerifyChain(userId: string, tenantId: string): boolean {
    // Admins and auditors can verify
    return this.hasRole(userId, ['ADMIN', 'AUDITOR'], tenantId);
  }
}
```

### 7.3 Encryption at Rest

```python
from cryptography.fernet import Fernet

class AuditEncryption:
    """
    Encrypt sensitive audit log details.
    """
    
    def __init__(self, encryption_key: bytes):
        self.cipher = Fernet(encryption_key)
    
    def encrypt_details(self, details: dict) -> str:
        """
        Encrypt audit event details.
        """
        plaintext = json.dumps(details).encode()
        ciphertext = self.cipher.encrypt(plaintext)
        return ciphertext.decode()
    
    def decrypt_details(self, encrypted_details: str) -> dict:
        """
        Decrypt audit event details.
        """
        ciphertext = encrypted_details.encode()
        plaintext = self.cipher.decrypt(ciphertext)
        return json.loads(plaintext.decode())
```

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```yaml
metrics:
  - name: audit_logs_created_total
    type: counter
    labels: [action, outcome, tenant_id]
  
  - name: audit_log_latency_seconds
    type: histogram
    labels: [action]
    buckets: [0.001, 0.005, 0.01, 0.05, 0.1]
  
  - name: audit_chain_verification_total
    type: counter
    labels: [tenant_id, result]
  
  - name: audit_retention_deleted_total
    type: counter
    labels: [tenant_id, action_pattern]
  
  - name: audit_export_duration_seconds
    type: histogram
    labels: [format, status]
```

### 8.2 Structured Logs

```json
{
  "timestamp": "2025-03-02T10:30:00.123Z",
  "level": "INFO",
  "service": "audit-framework",
  "trace_id": "abc-123",
  "action": "AUDIT_LOG_CREATED",
  "audit_id": "aud_7a8b9c0d",
  "sequence_number": 15234,
  "tenant_id": "tenant_np_1",
  "hash_verified": true
}
```

### 8.3 Alerting

```yaml
alerts:
  - name: AuditChainBroken
    condition: audit_chain_verification_total{result="invalid"} > 0
    severity: CRITICAL
    description: Audit log hash chain integrity violated
  
  - name: AuditLogHighLatency
    condition: histogram_quantile(0.99, audit_log_latency_seconds) > 0.1
    severity: WARNING
    description: Audit logging P99 latency exceeds 100ms
  
  - name: AuditExportFailed
    condition: audit_export_duration_seconds{status="FAILED"} > 0
    severity: HIGH
    description: Audit log export failed
```

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Audit Actions

```typescript
/**
 * Register custom audit action
 */
async function registerAuditAction(
  action: string,
  schema: JSONSchema
): Promise<void> {
  // Validate schema
  if (!schema.type || !schema.properties) {
    throw new Error('Invalid audit action schema');
  }

  // Store in K-02 Config Engine
  await configClient.set({
    key: `audit_actions/${action}`,
    value: schema,
    scope: 'GLOBAL'
  });

  logger.info(`Registered audit action: ${action}`);
}
```

### 9.2 Audit Event Enrichment

```typescript
interface AuditEnricher {
  enrich(event: AuditEvent): Promise<AuditEvent>;
}

class GeoLocationEnricher implements AuditEnricher {
  async enrich(event: AuditEvent): Promise<AuditEvent> {
    if (event.actor.ipAddress) {
      const geoData = await this.lookupGeoLocation(event.actor.ipAddress);
      
      return {
        ...event,
        details: {
          ...event.details,
          geo_location: {
            country: geoData.country,
            city: geoData.city,
            coordinates: geoData.coordinates
          }
        }
      };
    }
    
    return event;
  }

  private async lookupGeoLocation(ip: string): Promise<GeoData> {
    // Implementation
  }
}

// Register enricher
auditClient.registerEnricher(new GeoLocationEnricher());
```

### 9.3 Compliance Report Templates

```typescript
interface ComplianceReport {
  name: string;
  description: string;
  query: AuditSearchCriteria;
  format: 'CSV' | 'JSON' | 'PDF';
  schedule?: string; // Cron expression
}

const nepseComplianceReport: ComplianceReport = {
  name: 'NEPSE Daily Trading Report',
  description: 'Daily report of all trading activities for NEPSE compliance',
  query: {
    actions: ['ORDER_PLACED', 'ORDER_EXECUTED', 'ORDER_CANCELLED'],
    fromDate: 'TODAY_START',
    toDate: 'TODAY_END'
  },
  format: 'PDF',
  schedule: '0 18 * * *' // Daily at 6 PM
};

// Register report template
await auditClient.registerReportTemplate(nepseComplianceReport);
```

---

## 10. TEST PLAN

### 10.1 Unit Tests

```typescript
describe('AuditHashChain', () => {
  it('should calculate hash correctly', () => {
    const previousHash = '0'.repeat(64);
    const event: AuditEvent = {
      action: 'ORDER_PLACED',
      actor: { userId: 'user_1', role: 'TRADER', ipAddress: '192.168.1.1' },
      resource: { type: 'Order', id: 'order_1' },
      outcome: 'SUCCESS',
      sequenceNumber: 1,
      timestampGregorian: new Date('2025-03-02T10:30:00Z')
    };

    const hash = AuditHashChain.calculateHash(previousHash, event);

    expect(hash).toHaveLength(64);
    expect(hash).toMatch(/^[a-f0-9]{64}$/);
  });

  it('should verify valid chain', () => {
    const logs = [
      createAuditLog(1, '0'.repeat(64)),
      createAuditLog(2, logs[0].currentHash),
      createAuditLog(3, logs[1].currentHash)
    ];

    const [valid, violations] = AuditHashChain.verifyChain(logs);

    expect(valid).toBe(true);
    expect(violations).toHaveLength(0);
  });

  it('should detect tampered log', () => {
    const logs = [
      createAuditLog(1, '0'.repeat(64)),
      createAuditLog(2, logs[0].currentHash),
      createAuditLog(3, logs[1].currentHash)
    ];

    // Tamper with middle log
    logs[1].action = 'TAMPERED_ACTION';

    const [valid, violations] = AuditHashChain.verifyChain(logs);

    expect(valid).toBe(false);
    expect(violations.length).toBeGreaterThan(0);
  });
});
```

### 10.2 Integration Tests

```typescript
describe('Audit Framework Integration', () => {
  it('should log audit event and maintain chain', async () => {
    const event1: AuditEvent = {
      action: 'ORDER_PLACED',
      actor: { userId: 'user_1', role: 'TRADER', ipAddress: '192.168.1.1' },
      resource: { type: 'Order', id: 'order_1' },
      outcome: 'SUCCESS',
      tenantId: 'tenant_1',
      timestampBs: '2081-11-17',
      timestampGregorian: new Date()
    };

    const receipt1 = await auditClient.log(event1);
    expect(receipt1.sequenceNumber).toBe(1);

    const event2 = { ...event1, resource: { type: 'Order', id: 'order_2' } };
    const receipt2 = await auditClient.log(event2);
    
    expect(receipt2.sequenceNumber).toBe(2);
    expect(receipt2.previousHash).toBe(receipt1.currentHash);

    // Verify chain
    const result = await auditClient.verifyChain('tenant_1');
    expect(result.valid).toBe(true);
  });

  it('should enforce retention policy', async () => {
    // Create retention policy
    await createRetentionPolicy({
      tenantId: 'tenant_1',
      actionPattern: 'ORDER_*',
      retentionDays: 1
    });

    // Create old audit log
    const oldLog = await createAuditLog({
      action: 'ORDER_PLACED',
      tenantId: 'tenant_1',
      timestampGregorian: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000) // 2 days ago
    });

    // Run retention job
    await runRetentionJob();

    // Verify log deleted
    const logs = await auditClient.search({
      tenantId: 'tenant_1',
      auditId: oldLog.auditId
    });

    expect(logs).toHaveLength(0);
  });
});
```

### 10.3 Security Tests

```typescript
describe('Audit Security', () => {
  it('should prevent audit log modification', async () => {
    const log = await createAuditLog({
      action: 'ORDER_PLACED',
      tenantId: 'tenant_1'
    });

    // Attempt to update
    await expect(
      db.execute('UPDATE audit_logs SET action = ? WHERE audit_id = ?', [
        'TAMPERED',
        log.auditId
      ])
    ).rejects.toThrow('Audit logs are immutable');
  });

  it('should prevent audit log deletion', async () => {
    const log = await createAuditLog({
      action: 'ORDER_PLACED',
      tenantId: 'tenant_1'
    });

    // Attempt to delete
    await expect(
      db.execute('DELETE FROM audit_logs WHERE audit_id = ?', [log.auditId])
    ).rejects.toThrow('Audit logs are immutable');
  });

  it('should enforce access control on search', async () => {
    const regularUser = { userId: 'user_1', role: 'TRADER' };

    await expect(
      auditClient.search(
        { tenantId: 'tenant_1' },
        { userId: regularUser.userId }
      )
    ).rejects.toThrow(InsufficientPermissionsError);
  });
});
```

### 10.4 Performance Tests

```typescript
describe('Audit Performance', () => {
  it('should handle high throughput logging', async () => {
    const startTime = Date.now();
    const numLogs = 10000;

    const promises = [];
    for (let i = 0; i < numLogs; i++) {
      promises.push(
        auditClient.log({
          action: 'ORDER_PLACED',
          actor: { userId: 'user_1', role: 'TRADER', ipAddress: '192.168.1.1' },
          resource: { type: 'Order', id: `order_${i}` },
          outcome: 'SUCCESS',
          tenantId: 'tenant_1',
          timestampBs: '2081-11-17',
          timestampGregorian: new Date()
        })
      );
    }

    await Promise.all(promises);

    const duration = Date.now() - startTime;
    const throughput = numLogs / (duration / 1000);

    expect(throughput).toBeGreaterThan(1000); // > 1K TPS
  });
});
```

---

## 11. VALIDATION QUESTIONS & ASSUMPTIONS

### Assumptions

1. **[ASSUMPTION]** Hash chain is per-tenant (not global)
   - **Validation**: Should there be a global hash chain across tenants?
   - **Impact**: May need multi-tenant chain verification

2. **[ASSUMPTION]** Audit logs are never deleted (only archived)
   - **Validation**: Are there GDPR right-to-erasure requirements?
   - **Impact**: May need pseudonymization instead of deletion

3. **[ASSUMPTION]** Chain verification is periodic (not real-time)
   - **Validation**: Should every log write verify the chain?
   - **Impact**: May impact write performance

4. **[ASSUMPTION]** Export format is CSV/JSON/PDF
   - **Validation**: Are other formats required (e.g., XML for regulators)?
   - **Impact**: May need additional export formatters

---

**END OF LLD: K-07 AUDIT FRAMEWORK**
