# LOW-LEVEL DESIGN: K-14 SECRETS MANAGEMENT SERVICE

**Module**: K-14 Secrets Management  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Architecture Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Secrets Management Service provides a **unified secrets API abstracting multiple vault providers** with automatic rotation, certificate lifecycle management, break-glass access, and customer-managed key (CMK) support.

**Core Responsibilities**:
- Unified secrets API abstracting provider differences
- Multi-provider support (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager)
- Automatic secret rotation with configurable schedules
- Certificate lifecycle management (issuance, renewal, revocation)
- Secret versioning with rollback capability
- RBAC for secret access (K-01 integration)
- Break-glass emergency access with full audit trail
- Customer-managed key (CMK) support
- HSM integration for high-security key storage
- Dual-calendar on all rotation schedules
- Full audit trail for all secret access and changes (K-07)

**Invariants**:
1. Secret values MUST never be logged or exposed in error messages
2. Secret access MUST be authenticated and authorized via K-01
3. All secret operations MUST be audited via K-07
4. Rotation schedules MUST be enforced — expired secrets MUST trigger alerts
5. Break-glass access MUST require post-incident review

### 1.2 Explicit Non-Goals

- ❌ Application configuration management (K-02 Config Engine)
- ❌ Data classification (K-08 Data Governance)
- ❌ PKI certificate authority operation (external CA integration)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-01 IAM | Access control for secrets | K-01 stable |
| K-02 Config Engine | Rotation policies, provider config | K-02 stable |
| K-07 Audit Framework | Secret access audit trail | K-07 stable |
| K-08 Data Governance | Encryption key coordination | K-08 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/secrets/{secret_path}
Authorization: Bearer {service_token}

Response 200:
{
  "secret_path": "services/oms/database/password",
  "version": 5,
  "value": "<encrypted_value>",
  "metadata": {
    "created_at_gregorian": "2026-03-01T00:00:00Z",
    "created_at_bs": "2082-11-18",
    "rotation_due_gregorian": "2026-04-01T00:00:00Z",
    "rotation_due_bs": "2082-12-19",
    "provider": "hashicorp_vault"
  }
}
```

```yaml
POST /api/v1/secrets/{secret_path}/rotate
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "reason": "Scheduled rotation",
  "new_value": "<optional_specific_value>"
}

Response 200:
{
  "secret_path": "services/oms/database/password",
  "previous_version": 5,
  "new_version": 6,
  "rotated_at_gregorian": "2026-03-08T10:00:00Z",
  "rotated_at_bs": "2082-11-25"
}
```

```yaml
POST /api/v1/certificates/issue
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "common_name": "oms-service.siddhanta.internal",
  "san": ["oms-service", "oms-service.siddhanta-kernel.svc.cluster.local"],
  "validity_days": 365,
  "key_algorithm": "ECDSA_P256"
}

Response 201:
{
  "certificate_id": "cert_abc123",
  "common_name": "oms-service.siddhanta.internal",
  "serial_number": "0A:1B:2C:3D",
  "not_before": "2026-03-08T00:00:00Z",
  "not_after": "2027-03-08T00:00:00Z",
  "fingerprint_sha256": "AB:CD:EF:..."
}
```

```yaml
POST /api/v1/secrets/break-glass
Authorization: Bearer {admin_token} + MFA verification
Content-Type: application/json

Request:
{
  "secret_path": "services/critical/master-key",
  "reason": "Production incident INC-2026-0001",
  "incident_id": "INC-2026-0001",
  "expected_duration_minutes": 60
}

Response 200:
{
  "access_id": "bg_xyz789",
  "secret_value": "<value>",
  "expires_at": "2026-03-08T11:00:00Z",
  "review_required": true,
  "review_due_at": "2026-03-09T10:00:00Z"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.secrets.v1;

service SecretsService {
  rpc GetSecret(GetSecretRequest) returns (SecretResponse);
  rpc SetSecret(SetSecretRequest) returns (SecretResponse);
  rpc RotateSecret(RotateSecretRequest) returns (RotationResponse);
  rpc DeleteSecret(DeleteSecretRequest) returns (DeleteResponse);
  rpc ListSecrets(ListSecretsRequest) returns (ListSecretsResponse);
  rpc IssueCertificate(IssueCertRequest) returns (CertificateResponse);
  rpc RenewCertificate(RenewCertRequest) returns (CertificateResponse);
  rpc RevokeCertificate(RevokeCertRequest) returns (RevokeResponse);
  rpc ActivateBreakGlass(BreakGlassRequest) returns (BreakGlassResponse);
}
```

### 2.3 SDK Method Signatures

```typescript
interface SecretsClient {
  getSecret(path: string, version?: number): Promise<Secret>;
  setSecret(path: string, value: string, metadata?: SecretMetadata): Promise<Secret>;
  rotateSecret(path: string, reason: string): Promise<RotationResult>;
  deleteSecret(path: string): Promise<void>;
  listSecrets(prefix: string): Promise<SecretMetadata[]>;
  
  getCertificate(certId: string): Promise<Certificate>;
  issueCertificate(request: IssueCertRequest): Promise<Certificate>;
  renewCertificate(certId: string): Promise<Certificate>;
  revokeCertificate(certId: string, reason: string): Promise<void>;
  
  activateBreakGlass(path: string, incidentId: string, reason: string): Promise<BreakGlassAccess>;
}
```

---

## 3. DATA MODEL

### 3.1 Storage Tables

```sql
CREATE TABLE secret_metadata (
    secret_path VARCHAR(500) PRIMARY KEY,
    current_version INTEGER NOT NULL DEFAULT 1,
    provider VARCHAR(50) NOT NULL CHECK (provider IN ('hashicorp_vault','aws_sm','azure_kv','gcp_sm')),
    rotation_policy VARCHAR(100),
    rotation_interval_days INTEGER DEFAULT 90,
    last_rotated_at TIMESTAMPTZ,
    next_rotation_due TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_at_bs VARCHAR(20),
    owner_service VARCHAR(255),
    classification VARCHAR(50) DEFAULT 'CONFIDENTIAL'
);

CREATE TABLE secret_access_log (
    access_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_path VARCHAR(500) NOT NULL,
    accessor_service VARCHAR(255) NOT NULL,
    accessor_user_id UUID,
    access_type VARCHAR(50) NOT NULL CHECK (access_type IN ('READ','WRITE','ROTATE','DELETE','BREAK_GLASS')),
    access_granted BOOLEAN NOT NULL,
    secret_version INTEGER,
    timestamp_gregorian TIMESTAMPTZ DEFAULT NOW(),
    timestamp_bs VARCHAR(20),
    client_ip VARCHAR(45),
    trace_id VARCHAR(255)
);
CREATE INDEX idx_secret_access_time ON secret_access_log(timestamp_gregorian);
CREATE INDEX idx_secret_access_path ON secret_access_log(secret_path, timestamp_gregorian);

CREATE TABLE certificate_metadata (
    certificate_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    common_name VARCHAR(500) NOT NULL,
    san TEXT[],
    serial_number VARCHAR(100) UNIQUE NOT NULL,
    issuer VARCHAR(500),
    not_before TIMESTAMPTZ NOT NULL,
    not_after TIMESTAMPTZ NOT NULL,
    key_algorithm VARCHAR(50) NOT NULL,
    fingerprint_sha256 VARCHAR(100),
    status VARCHAR(50) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','EXPIRED','REVOKED','PENDING_RENEWAL')),
    auto_renew BOOLEAN DEFAULT true,
    renewal_threshold_days INTEGER DEFAULT 30
);

CREATE TABLE break_glass_sessions (
    access_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_path VARCHAR(500) NOT NULL,
    requester_id UUID NOT NULL,
    incident_id VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL,
    activated_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    review_required BOOLEAN DEFAULT true,
    review_due_at TIMESTAMPTZ,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    review_outcome VARCHAR(50) CHECK (review_outcome IN ('APPROVED','FLAGGED','UNDER_INVESTIGATION'))
);
```

### 3.2 Event Schemas

```json
{
  "event_type": "SecretRotatedEvent",
  "data": {
    "secret_path": "services/oms/database/password",
    "previous_version": 5,
    "new_version": 6,
    "rotation_trigger": "SCHEDULED",
    "provider": "hashicorp_vault"
  }
}
```

```json
{
  "event_type": "CertificateRenewedEvent",
  "data": {
    "certificate_id": "cert_abc123",
    "common_name": "oms-service.siddhanta.internal",
    "new_expiry_gregorian": "2027-03-08T00:00:00Z",
    "new_expiry_bs": "2083-11-24"
  }
}
```

```json
{
  "event_type": "BreakGlassActivatedEvent",
  "data": {
    "access_id": "bg_xyz789",
    "secret_path": "services/critical/master-key",
    "requester_id": "admin_001",
    "incident_id": "INC-2026-0001",
    "review_due_at": "2026-03-09T10:00:00Z"
  }
}
```

---

## 4. CONTROL FLOW

### 4.1 Secret Retrieval Flow
```
1. Service requests secret via SecretsClient.getSecret(path)
2. Authenticate caller via K-01 (service identity)
3. Check RBAC permission for path
4. Resolve provider (Vault/AWS/Azure/GCP) from secret_metadata
5. Fetch value from provider (with local cache check)
6. Log access to secret_access_log
7. Return decrypted secret value
```

### 4.2 Automatic Rotation Flow
```
1. Scheduler checks next_rotation_due for all secrets
2. For each due rotation:
   a. Generate new secret value (or delegate to provider)
   b. Store new version in provider
   c. Update secret_metadata (current_version, last_rotated_at, next_rotation_due)
   d. Publish SecretRotatedEvent
   e. Dependent services reload secret on event
3. Verify rotation success
4. Log to K-07 audit trail
```

### 4.3 Break-Glass Flow
```
1. Admin requests break-glass access (requires MFA)
2. Verify incident_id against incident management system
3. Grant temporary access with time-bounded expiry
4. Log BreakGlassActivatedEvent (P1 alert to security team)
5. Access auto-revoked at expiry
6. Post-incident review required within review_due_at
7. Review outcome recorded (APPROVED/FLAGGED/UNDER_INVESTIGATION)
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Provider Abstraction
```typescript
interface VaultProvider {
  getSecret(path: string, version?: number): Promise<string>;
  setSecret(path: string, value: string): Promise<number>;
  rotateSecret(path: string): Promise<{ version: number; value: string }>;
  deleteSecret(path: string): Promise<void>;
}

// Implementations
class HashiCorpVaultProvider implements VaultProvider { ... }
class AWSSecretsManagerProvider implements VaultProvider { ... }
class AzureKeyVaultProvider implements VaultProvider { ... }
class GCPSecretManagerProvider implements VaultProvider { ... }
```

### 5.2 Secret Caching
```
TTL-based cache:
  - Hot secrets (frequently accessed): 60s TTL
  - Warm secrets: 300s TTL
  - Cold secrets: fetch on demand
  - Cache invalidation on SecretRotatedEvent
```

---

## 6. NFR BUDGETS

| Operation | P99 Latency | Throughput | Timeout |
|-----------|-------------|------------|---------|
| getSecret() (cached) | 1ms | 100,000/s | 50ms |
| getSecret() (fetch) | 10ms | 10,000/s | 500ms |
| rotateSecret() | 100ms | 100/s | 5000ms |
| issueCertificate() | 500ms | 50/s | 10000ms |
| activateBreakGlass() | 50ms | 10/s | 5000ms |

**Availability**: 99.999%  
**HSM Support**: FIPS 140-2 Level 3

---

## 7. SECURITY DESIGN

- **Zero Logging of Values**: Secret values never appear in logs
- **mTLS**: All provider communication over mTLS
- **Encryption at Rest**: Provider-native encryption (AES-256)
- **Access Control**: K-01 RBAC with path-based permissions
- **Break-Glass**: MFA required + time-bounded + mandatory review
- **HSM Integration**: Hardware security modules for master keys

---

## 8. OBSERVABILITY & AUDIT

### Metrics
- `secrets_access_total` — Secret access count by path/type/granted
- `secrets_rotation_total` — Rotations by trigger (scheduled/manual)
- `secrets_rotation_overdue_total` — Overdue rotations (alert metric)
- `certificates_expiring_soon` — Certificates expiring within 30 days
- `break_glass_activations_total` — Break-glass events (should be rare)

### Alerts
- Secret rotation overdue → P2 alert
- Certificate expiring in <7 days → P1 alert
- Break-glass activation → P1 immediate notification to security team
- Break-glass review overdue → P1 alert
- Unauthorized secret access attempt → P1 alert

---

## 9. EXTENSIBILITY & EVOLUTION

### Extension Points
- `VaultProvider` adapter interface for new vault backends (T3)
- T1 Config Packs for rotation policies per jurisdiction
- Custom rotation generators for service-specific secrets (T3)
- Certificate authority integrations (T3)

---

## 10. TEST PLAN

### Unit Tests
- Provider abstraction for each vault backend
- Secret caching and TTL expiry
- Rotation scheduling logic
- Break-glass access lifecycle (activate → expire → review)
- Certificate renewal threshold calculation

### Integration Tests
- HashiCorp Vault integration (get/set/rotate)
- AWS Secrets Manager integration
- K-01 RBAC enforcement for secret paths
- K-07 audit trail for all secret operations
- K-05 event publication (rotation, break-glass)

### Security Tests
- Unauthorized secret access → 403
- Break-glass without MFA → rejected
- Secret values not present in logs or error messages
- Expired break-glass session → access denied

### Performance Tests
- Cached secret retrieval at 100K/s — P99 <1ms
- Provider fetch at 10K/s — P99 <10ms
- Concurrent rotation of 1000 secrets — complete within SLA
