# LOW-LEVEL DESIGN: D-07 COMPLIANCE ENGINE

**Module**: D-07 Compliance Engine  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Compliance & Regulatory Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Compliance Engine is the **regulatory gatekeeper** of the platform, enforcing trading rules, lock-in periods, KYC/AML controls, and jurisdiction-specific compliance via K-03 Rules Engine.

**Core Responsibilities**:
- Pre-trade compliance validation (lock-in period enforcement, insider trading checks)
- KYC/AML onboarding and ongoing due diligence
- Regulatory rule orchestration via K-03 Rules Engine
- Lock-in period enforcement with dual-calendar awareness
- Beneficial ownership tracking and threshold alerting
- Restricted list management (insider lists, sanctions overlap with D-14)
- Compliance attestation and certification workflows
- Regulatory change management — rule version tracking

**Invariants**:
1. No trade may execute without compliance pre-check PASS
2. Lock-in periods MUST be enforced to the day (BS calendar-aware)
3. KYC status MUST be current — expired KYC blocks trading
4. Beneficial ownership changes MUST trigger threshold alerts
5. All compliance overrides REQUIRE dual approval + regulatory justification

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-01 IAM | User identity, KYC status | K-01 stable |
| K-02 Config Engine | Compliance rule configs | K-02 stable |
| K-03 Rules Engine | Rule evaluation pipeline | K-03 stable |
| K-05 Event Bus | ComplianceViolationEvent | K-05 stable |
| K-07 Audit Framework | Compliance decision audit | K-07 stable |
| K-15 Dual-Calendar | Lock-in date calculation | K-15 stable |
| K-18 Resilience | Compliance service resilience | K-18 stable |
| D-14 Sanctions | Sanctions screening integration | D-14 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/compliance/pre-trade-check
Authorization: Bearer {service_token}

Request:
{
  "order_id": "ORD-001",
  "account_id": "ACC-001",
  "instrument_id": "NABIL",
  "side": "SELL",
  "quantity": 1000,
  "trader_id": "USR-001"
}

Response 200:
{
  "order_id": "ORD-001",
  "approved": false,
  "checks": [
    { "rule": "LOCK_IN_PERIOD", "status": "FAIL", "message": "Lock-in expires 2082-12-15 BS", "lock_in_end_bs": "2082-12-15" },
    { "rule": "KYC_STATUS", "status": "PASS" },
    { "rule": "INSIDER_LIST", "status": "PASS" },
    { "rule": "SANCTIONS", "status": "PASS" }
  ],
  "latency_ms": 8.1
}
```

```yaml
GET /api/v1/compliance/kyc/{account_id}

Response 200:
{
  "account_id": "ACC-001",
  "kyc_status": "VERIFIED",
  "kyc_level": "ENHANCED",
  "last_verified_bs": "2082-08-01",
  "next_review_bs": "2083-08-01",
  "documents": [
    { "type": "CITIZENSHIP", "status": "VERIFIED", "expiry_bs": "2090-01-01" },
    { "type": "PAN_CARD", "status": "VERIFIED" }
  ]
}
```

```yaml
POST /api/v1/compliance/beneficial-ownership/declare
Authorization: Bearer {user_token}

Request:
{
  "account_id": "ACC-001",
  "instrument_id": "NABIL",
  "ownership_pct": 5.2,
  "declaration_date_bs": "2082-11-25"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.compliance.v1;

service ComplianceService {
  rpc PreTradeCheck(ComplianceCheckRequest) returns (ComplianceCheckResult);
  rpc GetKYCStatus(KYCRequest) returns (KYCStatus);
  rpc DeclareOwnership(OwnershipDeclaration) returns (OwnershipResult);
  rpc ManageRestrictedList(RestrictedListRequest) returns (RestrictedListResult);
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE kyc_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) NOT NULL,
    kyc_level VARCHAR(30) NOT NULL, -- BASIC, STANDARD, ENHANCED
    status VARCHAR(20) NOT NULL, -- PENDING, VERIFIED, EXPIRED, REJECTED
    verified_by VARCHAR(100),
    verified_at TIMESTAMPTZ,
    next_review_date DATE,
    next_review_date_bs VARCHAR(20),
    documents JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE lock_in_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    lock_in_start_bs VARCHAR(20) NOT NULL,
    lock_in_end_bs VARCHAR(20) NOT NULL,
    lock_in_start DATE NOT NULL,
    lock_in_end DATE NOT NULL,
    reason VARCHAR(100), -- IPO, PROMOTER, FPO, RIGHTS
    quantity INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

CREATE TABLE beneficial_ownership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    ownership_pct DECIMAL(8,4) NOT NULL,
    threshold_triggered BOOLEAN DEFAULT false,
    declaration_date_bs VARCHAR(20),
    declaration_date DATE,
    regulatory_filing_status VARCHAR(30),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE restricted_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_type VARCHAR(30) NOT NULL, -- INSIDER, RESTRICTED, WATCH
    entity_id VARCHAR(100) NOT NULL,
    instrument_id VARCHAR(50),
    reason TEXT,
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    added_by VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE'
);
```

### 3.2 Event Schemas

```json
{ "event_type": "ComplianceViolationEvent", "data": { "order_id": "ORD-001", "rule": "LOCK_IN_PERIOD", "account_id": "ACC-001" } }
```
```json
{ "event_type": "BeneficialOwnershipThresholdEvent", "data": { "account_id": "ACC-001", "instrument_id": "NABIL", "pct": 5.2, "threshold": 5.0 } }
```

---

## 4. CONTROL FLOW

### 4.1 Pre-Trade Compliance Pipeline
```
1. Order arrives from D-01 OMS (after risk check)
2. K-03 evaluates compliance rule chain:
   a. KYC status check (must be VERIFIED)
   b. Lock-in period check (BS-aware date comparison)
   c. Insider / restricted list check
   d. Sanctions screening (D-14)
   e. Beneficial ownership threshold check
3. Return PASS/FAIL with rule-by-rule details
4. FAIL results emit ComplianceViolationEvent
```

### 4.2 Lock-In Enforcement
```
function checkLockIn(account, instrument, side):
  if side != SELL: return PASS
  locks = getLockInRecords(account, instrument, status=ACTIVE)
  today_bs = K-15.todayBS()
  for lock in locks:
    if today_bs < lock.lock_in_end_bs:
      return FAIL("Lock-in expires " + lock.lock_in_end_bs)
  return PASS
```

---

## 5–10. ALGORITHMS, NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: Pre-trade check <10ms; KYC lookup <50ms; 99.99% uptime
- **Security**: K-01 RBAC; PII encryption at rest; compliance override dual-approval; audit trail immutable
- **Metrics**: `compliance_pretrade_latency_ms`, `compliance_violations_total`, `compliance_kyc_expired_count`
- **Extension Points**: T2 jurisdiction-specific compliance rules via K-03; T3 regulatory portal adapters
- **Tests**: Lock-in enforcement across BS calendar boundaries; KYC expiry blocking; insider list matching; beneficial ownership threshold triggers
