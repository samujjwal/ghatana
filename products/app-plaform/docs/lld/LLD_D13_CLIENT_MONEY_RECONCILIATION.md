# LOW-LEVEL DESIGN: D-13 CLIENT MONEY RECONCILIATION

**Module**: D-13 Client Money Reconciliation  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Post-Trade & Compliance Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

D-13 provides **automated daily reconciliation of client money accounts**, ensuring segregation of client funds from proprietary funds as mandated by SEBON (Nepal) and equivalent regulators. The module detects breaks, classifies severity, escalates per aging tiers, generates regulatory evidence, and maintains a complete audit trail.

**Core Responsibilities**:
- Daily automated reconciliation of client money balances
- Break detection with severity classification (INFO, WARNING, CRITICAL)
- Aging tier escalation (3 / 5 / 10 business days)
- Segregation verification — client funds ≠ proprietary funds
- Multi-source reconciliation (bank statements, CDSC, custodian, internal ledger)
- Regulatory evidence package generation (PDF + JSON)
- Manual adjustment workflow with maker-checker approval
- Dual-calendar timestamps on all reconciliation records

**Invariants**:
1. Reconciliation MUST run daily before T+1 settlement cut-off
2. Breaks MUST NOT be silently cleared — require explicit resolution
3. Segregation ratio MUST be ≥ 100% at all times (client funds fully covered)
4. All adjustments MUST go through maker-checker approval
5. Reconciliation evidence MUST be retained for 10 years
6. Break aging MUST use business days (non-holiday) via K-15

### 1.2 Explicit Non-Goals

- ❌ Client account management — handled by CRM / account module
- ❌ Payment initiation — handled by payment gateway
- ❌ General ledger management — handled by K-16 Ledger Framework
- ❌ Trade settlement — handled by D-09 Post-Trade

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-05 Event Bus | Reconciliation events | K-05 stable |
| K-06 Observability | Metrics and alerting | K-06 stable |
| K-07 Audit Framework | Full reconciliation audit trail | K-07 stable |
| K-15 Dual-Calendar | Business day calculations for aging | K-15 stable |
| K-16 Ledger Framework | Internal balance source of truth | K-16 stable |
| K-18 Resilience | External source connectivity | K-18 stable |
| D-09 Post-Trade | Settlement data feed | D-09 stable |
| R-02 Regulatory Reporting | Evidence package submission | R-02 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/reconciliation/runs
Authorization: Bearer {admin_token}

Request:
{
  "reconciliation_date": "2025-03-02",
  "reconciliation_date_bs": "2081-11-18",
  "tenant_id": "tenant_np_1",
  "sources": ["INTERNAL_LEDGER", "BANK_STATEMENT", "CDSC"],
  "run_type": "SCHEDULED"
}

Response 202:
{
  "run_id": "recon_run_20250302_001",
  "status": "IN_PROGRESS",
  "started_at": "2025-03-02T18:00:00Z",
  "started_at_bs": "2081-11-18 18:00:00"
}
```

```yaml
GET /api/v1/reconciliation/runs/{run_id}
Authorization: Bearer {admin_token}

Response 200:
{
  "run_id": "recon_run_20250302_001",
  "status": "COMPLETED",
  "reconciliation_date": "2025-03-02",
  "reconciliation_date_bs": "2081-11-18",
  "summary": {
    "total_accounts": 12500,
    "matched": 12480,
    "breaks_found": 20,
    "breaks_by_severity": { "INFO": 12, "WARNING": 5, "CRITICAL": 3 },
    "segregation_ratio": 100.02,
    "total_client_funds": 45000000000.00,
    "total_segregated_funds": 45009000000.00,
    "currency": "NPR"
  },
  "duration_seconds": 342,
  "completed_at": "2025-03-02T18:05:42Z"
}
```

```yaml
GET /api/v1/reconciliation/breaks?status=OPEN&severity=CRITICAL&limit=50
Authorization: Bearer {admin_token}

Response 200:
{
  "breaks": [
    {
      "break_id": "brk_001",
      "run_id": "recon_run_20250302_001",
      "client_account": "CLT-ACC-001",
      "internal_balance": 5000000.00,
      "external_balance": 4950000.00,
      "difference": 50000.00,
      "difference_pct": 1.0,
      "severity": "CRITICAL",
      "status": "OPEN",
      "aging_business_days": 0,
      "escalation_tier": "TIER_1",
      "source_mismatch": "BANK_STATEMENT",
      "detected_at": "2025-03-02T18:04:00Z",
      "detected_at_bs": "2081-11-18 18:04:00"
    }
  ]
}
```

```yaml
POST /api/v1/reconciliation/breaks/{break_id}/resolve
Authorization: Bearer {admin_token}

Request:
{
  "resolution_type": "ADJUSTMENT",
  "adjustment": {
    "debit_account": "CLT-SUSPENSE-001",
    "credit_account": "CLT-ACC-001",
    "amount": 50000.00,
    "currency": "NPR",
    "narrative": "Bank credit posted late — confirmed via bank reference TXN-98765"
  },
  "evidence": ["bank_confirmation_txn98765.pdf"],
  "resolved_by": "ops_user_01"
}

Response 200:
{
  "break_id": "brk_001",
  "status": "PENDING_APPROVAL",
  "message": "Resolution submitted — awaiting maker-checker approval"
}
```

```yaml
POST /api/v1/reconciliation/breaks/{break_id}/approve
Authorization: Bearer {checker_token}

Request:
{
  "approved": true,
  "checker_comment": "Verified against bank statement — approved"
}

Response 200:
{
  "break_id": "brk_001",
  "status": "RESOLVED",
  "resolved_at": "2025-03-02T19:30:00Z"
}
```

```yaml
GET /api/v1/reconciliation/segregation-report?date=2025-03-02
Authorization: Bearer {compliance_token}

Response 200:
{
  "report_date": "2025-03-02",
  "report_date_bs": "2081-11-18",
  "segregation_status": "COMPLIANT",
  "segregation_ratio": 100.02,
  "client_money_total": 45000000000.00,
  "segregated_funds_total": 45009000000.00,
  "surplus": 9000000.00,
  "shortfall": 0.00,
  "currency": "NPR",
  "accounts_checked": 12500,
  "regulatory_reference": "SEBON/CLIENT-MONEY/2081"
}
```

### 2.2 SDK Method Signatures

```typescript
interface ReconciliationClient {
  /** Trigger reconciliation run */
  startRun(date: string, sources: DataSource[]): Promise<ReconciliationRun>;

  /** Get run status */
  getRunStatus(runId: string): Promise<ReconciliationRun>;

  /** List open breaks */
  listBreaks(filter: BreakFilter): Promise<PaginatedResult<Break>>;

  /** Submit break resolution */
  resolveBreak(breakId: string, resolution: Resolution): Promise<void>;

  /** Approve/reject break resolution (checker) */
  approveResolution(breakId: string, approved: boolean, comment: string): Promise<void>;

  /** Generate regulatory evidence package */
  generateEvidencePackage(runId: string): Promise<EvidencePackage>;

  /** Get segregation report */
  getSegregationReport(date: string): Promise<SegregationReport>;
}
```

### 2.3 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| RECON_E001 | 409 | No | Reconciliation already running for this date |
| RECON_E002 | 400 | No | External source unavailable |
| RECON_E003 | 409 | No | Break already resolved |
| RECON_E004 | 403 | No | Same user cannot be maker and checker |
| RECON_E005 | 400 | No | Segregation shortfall detected |
| RECON_E006 | 500 | Yes | Ledger query failed |

---

## 3. DATA MODEL

### 3.1 Reconciliation Runs

```sql
CREATE TABLE reconciliation_runs (
  run_id VARCHAR(100) PRIMARY KEY,
  tenant_id UUID NOT NULL,
  reconciliation_date DATE NOT NULL,
  reconciliation_date_bs VARCHAR(20) NOT NULL,
  run_type VARCHAR(20) NOT NULL CHECK (run_type IN ('SCHEDULED', 'MANUAL', 'AD_HOC')),
  status VARCHAR(30) NOT NULL CHECK (status IN (
    'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'PARTIAL'
  )),
  sources JSONB NOT NULL,          -- ["INTERNAL_LEDGER", "BANK_STATEMENT", "CDSC"]
  summary JSONB,                   -- post-completion summary
  total_accounts INT,
  matched_accounts INT,
  breaks_found INT DEFAULT 0,
  segregation_ratio DECIMAL(10, 4),
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at_bs VARCHAR(30) NOT NULL,
  completed_at TIMESTAMPTZ,
  completed_at_bs VARCHAR(30),
  error_details TEXT,
  UNIQUE(tenant_id, reconciliation_date)
);

ALTER TABLE reconciliation_runs ENABLE ROW LEVEL SECURITY;
CREATE POLICY recon_run_tenant ON reconciliation_runs
  USING (tenant_id = current_setting('app.current_tenant')::UUID);
```

### 3.2 Reconciliation Breaks

```sql
CREATE TABLE reconciliation_breaks (
  break_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id VARCHAR(100) NOT NULL REFERENCES reconciliation_runs(run_id),
  tenant_id UUID NOT NULL,
  client_account VARCHAR(100) NOT NULL,
  internal_balance DECIMAL(20, 4) NOT NULL,
  external_balance DECIMAL(20, 4) NOT NULL,
  difference DECIMAL(20, 4) NOT NULL,
  difference_pct DECIMAL(10, 4) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'NPR',
  severity VARCHAR(20) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
  status VARCHAR(30) NOT NULL CHECK (status IN (
    'OPEN', 'INVESTIGATING', 'PENDING_APPROVAL', 'RESOLVED', 'ESCALATED', 'WAIVED'
  )),
  source_mismatch VARCHAR(50) NOT NULL,   -- which external source disagrees
  aging_business_days INT NOT NULL DEFAULT 0,
  escalation_tier VARCHAR(20) NOT NULL DEFAULT 'TIER_1',
  resolution JSONB,
  resolved_by VARCHAR(100),
  approved_by VARCHAR(100),
  detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  detected_at_bs VARCHAR(30) NOT NULL,
  resolved_at TIMESTAMPTZ,
  resolved_at_bs VARCHAR(30)
);

CREATE INDEX idx_breaks_status ON reconciliation_breaks(status, severity);
CREATE INDEX idx_breaks_aging ON reconciliation_breaks(aging_business_days, escalation_tier);
CREATE INDEX idx_breaks_account ON reconciliation_breaks(client_account, tenant_id);

ALTER TABLE reconciliation_breaks ENABLE ROW LEVEL SECURITY;
CREATE POLICY breaks_tenant ON reconciliation_breaks
  USING (tenant_id = current_setting('app.current_tenant')::UUID);
```

### 3.3 External Source Snapshots

```sql
CREATE TABLE reconciliation_source_snapshots (
  snapshot_id BIGSERIAL PRIMARY KEY,
  run_id VARCHAR(100) NOT NULL REFERENCES reconciliation_runs(run_id),
  tenant_id UUID NOT NULL,
  source_type VARCHAR(50) NOT NULL,       -- BANK_STATEMENT, CDSC, CUSTODIAN
  source_reference VARCHAR(255),          -- file name, statement ID
  account_id VARCHAR(100) NOT NULL,
  balance DECIMAL(20, 4) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  as_of_date DATE NOT NULL,
  as_of_date_bs VARCHAR(20) NOT NULL,
  raw_data JSONB,                         -- original source record
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshot_run ON reconciliation_source_snapshots(run_id, source_type);
```

### 3.4 Segregation Reports

```sql
CREATE TABLE segregation_reports (
  report_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id VARCHAR(100) NOT NULL REFERENCES reconciliation_runs(run_id),
  tenant_id UUID NOT NULL,
  report_date DATE NOT NULL,
  report_date_bs VARCHAR(20) NOT NULL,
  segregation_status VARCHAR(20) NOT NULL CHECK (segregation_status IN ('COMPLIANT', 'SHORTFALL', 'SURPLUS')),
  client_money_total DECIMAL(22, 4) NOT NULL,
  segregated_funds_total DECIMAL(22, 4) NOT NULL,
  surplus_or_shortfall DECIMAL(22, 4) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'NPR',
  accounts_checked INT NOT NULL,
  regulatory_reference VARCHAR(100),
  evidence_package_url TEXT,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  generated_at_bs VARCHAR(30) NOT NULL
);
```

---

## 4. CONTROL FLOW

### 4.1 Daily Reconciliation Flow

```
Scheduler (K-02 cron: 18:00 NPT daily)
  → ReconciliationOrchestrator.startDailyRun(date, tenant)
    → [1] Fetch internal balances from K-16 Ledger
    → [2] Fetch external balances (parallel, with K-18 resilience):
        → BankStatementAdapter.fetch(date, accounts)
        → CDSCAdapter.fetch(date, accounts)
        → CustodianAdapter.fetch(date, accounts)
    → [3] Store source snapshots
    → [4] Match & Compare:
        → FOR each client_account:
            → Compare internal_balance vs each external_balance
            → IF difference > 0:
                → Classify severity
                → Create break record
            → ELSE: Record as matched
    → [5] Compute segregation ratio:
        → total_segregated = SUM(bank balances in designated client accounts)
        → total_client_money = SUM(K-16 client money ledger balances)
        → ratio = total_segregated / total_client_money
        → IF ratio < 1.0: Alert P0 CRITICAL
    → [6] Generate segregation report
    → [7] Update run summary
    → [8] Emit K-05 events:
        → siddhanta.reconciliation.run.completed
        → siddhanta.reconciliation.breaks.detected (if any)
        → siddhanta.reconciliation.segregation.shortfall (if ratio < 1.0)
```

### 4.2 Break Aging & Escalation (Daily)

```
AgingJob (daily after reconciliation):
  → FOR each OPEN break:
    → age = K-15.calculateBusinessDaysBetween(break.detected_at, today)
    → UPDATE aging_business_days = age
    → Escalation logic:
      → IF age >= 3 AND escalation_tier = 'TIER_1':
          → UPDATE escalation_tier = 'TIER_2'
          → Notify: Operations Manager
      → IF age >= 5 AND escalation_tier = 'TIER_2':
          → UPDATE escalation_tier = 'TIER_3'
          → Notify: Compliance Head
      → IF age >= 10 AND escalation_tier = 'TIER_3':
          → UPDATE escalation_tier = 'REGULATORY'
          → Notify: CEO + Board + Regulator (SEBON)
          → Auto-generate regulatory incident report
```

### 4.3 Maker-Checker Resolution Flow

```
Maker (Operations):
  → POST /api/v1/reconciliation/breaks/{break_id}/resolve
    → Validate: maker ≠ previous resolver (no self-approval)
    → UPDATE break: status='PENDING_APPROVAL', resolution=payload
    → Emit K-07 audit: BREAK_RESOLUTION_SUBMITTED

Checker (Senior Operations / Compliance):
  → POST /api/v1/reconciliation/breaks/{break_id}/approve
    → Validate: checker ≠ maker
    → IF approved:
        → Execute adjustment in K-16 Ledger
        → UPDATE break: status='RESOLVED', resolved_at=NOW()
        → Emit K-07 audit: BREAK_RESOLUTION_APPROVED
    → IF rejected:
        → UPDATE break: status='OPEN'
        → Emit K-07 audit: BREAK_RESOLUTION_REJECTED
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Break Severity Classification

```python
def classify_break_severity(difference: Decimal, balance: Decimal) -> str:
    """Classify break severity based on amount and percentage."""
    if balance == 0:
        return 'CRITICAL' if difference > 0 else 'INFO'
    
    pct = abs(difference / balance) * 100
    
    if abs(difference) >= 1_000_000 or pct >= 5.0:     # >= 10L NPR or >= 5%
        return 'CRITICAL'
    elif abs(difference) >= 100_000 or pct >= 1.0:      # >= 1L NPR or >= 1%
        return 'WARNING'
    else:
        return 'INFO'
```

### 5.2 Reconciliation Matching Algorithm

```python
def reconcile_accounts(
    internal: dict[str, Decimal],     # account_id → balance
    external: dict[str, Decimal],     # account_id → balance
    tolerance: Decimal = Decimal('0.01')  # rounding tolerance
) -> tuple[list[Match], list[Break]]:
    """Three-way match with configurable tolerance."""
    matches, breaks = [], []
    
    all_accounts = set(internal.keys()) | set(external.keys())
    
    for account_id in all_accounts:
        int_bal = internal.get(account_id, Decimal('0'))
        ext_bal = external.get(account_id, Decimal('0'))
        diff = int_bal - ext_bal
        
        if abs(diff) <= tolerance:
            matches.append(Match(account_id, int_bal, ext_bal))
        else:
            severity = classify_break_severity(diff, int_bal)
            breaks.append(Break(account_id, int_bal, ext_bal, diff, severity))
    
    return matches, breaks
```

### 5.3 Segregation Ratio Calculation

```python
def calculate_segregation_ratio(
    client_money_ledger: Decimal,      # K-16 total client money
    designated_bank_funds: Decimal     # Sum of designated client money bank accounts
) -> tuple[Decimal, str]:
    """Calculate segregation ratio and compliance status."""
    if client_money_ledger == 0:
        return Decimal('100.00'), 'COMPLIANT'
    
    ratio = (designated_bank_funds / client_money_ledger) * 100
    
    if ratio >= Decimal('100.00'):
        status = 'SURPLUS' if ratio > Decimal('100.00') else 'COMPLIANT'
    else:
        status = 'SHORTFALL'  # CRITICAL — regulatory breach
    
    return ratio.quantize(Decimal('0.01')), status
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Max |
|-----------|-----|-----|-----|-----|
| Full reconciliation run (12.5K accounts) | 3min | 5min | 8min | 15min |
| Break detection (per account) | 0.5ms | 2ms | 5ms | 10ms |
| Segregation calculation | 50ms | 200ms | 500ms | 1000ms |
| Break resolution submission | 10ms | 50ms | 100ms | 500ms |
| Evidence package generation | 5s | 15s | 30s | 60s |

### 6.2 Throughput & Scale

| Metric | Target |
|--------|--------|
| Max accounts per run | 500K |
| Max concurrent runs (across tenants) | 50 |
| Break retention | 10 years |
| Source snapshot retention | 10 years |
| Segregation report retention | 10 years |

---

## 7. SECURITY DESIGN

### 7.1 Access Control

| Operation | Required Permission |
|-----------|-------------------|
| Start reconciliation | `recon:run:start` |
| View reconciliation results | `recon:run:view` |
| View breaks | `recon:breaks:view` |
| Submit break resolution | `recon:breaks:resolve` (maker) |
| Approve break resolution | `recon:breaks:approve` (checker) |
| Generate evidence package | `recon:evidence:generate` |
| View segregation report | `recon:segregation:view` (compliance) |

### 7.2 Data Protection

- Client account balances are Confidential — encrypted at rest
- Bank statement data is Confidential — encrypted at rest and in transit
- Evidence packages are signed (SHA-256 hash + digital signature)
- Maker-checker segregation enforced at API level (different user_id required)

### 7.3 Tenant Isolation

- RLS on all reconciliation tables
- Cross-tenant balance comparison is impossible by design

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```
siddhanta_recon_run_duration_seconds{tenant, run_type}              histogram
siddhanta_recon_run_total{tenant, status}                           counter
siddhanta_recon_accounts_processed{tenant}                          gauge
siddhanta_recon_breaks_total{tenant, severity}                      counter
siddhanta_recon_breaks_open{tenant, severity, escalation_tier}      gauge
siddhanta_recon_breaks_aging_days{tenant}                           histogram
siddhanta_recon_segregation_ratio{tenant}                           gauge
siddhanta_recon_segregation_shortfall{tenant}                       gauge
siddhanta_recon_external_source_latency_ms{source}                  histogram
siddhanta_recon_external_source_errors{source}                      counter
```

### 8.2 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| Segregation shortfall | ratio < 100% | P0 (CRITICAL) |
| Reconciliation run failed | status = FAILED | P1 |
| Critical breaks detected | >0 CRITICAL breaks | P1 |
| Break aging > 5 days | Any break at TIER_3 | P1 |
| Break aging > 10 days | Any break at REGULATORY tier | P0 |
| Reconciliation not started by cut-off | No run by 18:30 NPT | P1 |
| External source unavailable | Bank/CDSC adapter failure | P2 |

### 8.3 K-07 Audit Events

| Event | Trigger |
|-------|---------|
| RECON_RUN_STARTED | Reconciliation run initiated |
| RECON_RUN_COMPLETED | Run completed |
| RECON_BREAK_DETECTED | New break found |
| RECON_BREAK_ESCALATED | Break moved to higher tier |
| RECON_BREAK_RESOLUTION_SUBMITTED | Maker submits resolution |
| RECON_BREAK_RESOLUTION_APPROVED | Checker approves resolution |
| RECON_BREAK_RESOLUTION_REJECTED | Checker rejects resolution |
| RECON_SEGREGATION_SHORTFALL | Segregation < 100% detected |
| RECON_EVIDENCE_GENERATED | Evidence package created |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 External Source Adapters (T3)

```typescript
interface ReconciliationSourceAdapter {
  /** Fetch balances from external source for given date */
  fetchBalances(date: string, accounts: string[]): Promise<ExternalBalance[]>;

  /** Validate adapter connectivity */
  healthCheck(): Promise<boolean>;
}

// Example: Nepal-specific CDSC adapter
class CDSCAdapter implements ReconciliationSourceAdapter {
  async fetchBalances(date: string, accounts: string[]): Promise<ExternalBalance[]> {
    // CDSC SOAP/REST API integration
  }
}
```

### 9.2 Custom Severity Rules (T2)

```rego
# OPA/Rego rule for jurisdiction-specific break severity
package reconciliation.severity

default severity = "INFO"

severity = "CRITICAL" {
    abs(input.difference) >= input.jurisdiction_thresholds.critical_amount
}

severity = "WARNING" {
    abs(input.difference) >= input.jurisdiction_thresholds.warning_amount
    abs(input.difference) < input.jurisdiction_thresholds.critical_amount
}
```

### 9.3 Custom Escalation Tiers (T1)

```json
{
  "content_pack_type": "T1",
  "jurisdiction": "NP",
  "name": "nepal-escalation-tiers",
  "escalation_config": {
    "TIER_1": { "business_days": 0, "notify": ["operations_team"] },
    "TIER_2": { "business_days": 3, "notify": ["operations_manager", "compliance_officer"] },
    "TIER_3": { "business_days": 5, "notify": ["compliance_head", "cfo"] },
    "REGULATORY": { "business_days": 10, "notify": ["ceo", "board", "sebon_liaison"] }
  }
}
```

### 9.4 Future: CBDC / Digital Asset Reconciliation

- Reconcile CBDC balances from NRB digital currency ledger
- Tokenized security position reconciliation vs blockchain
- Real-time (streaming) reconciliation for T+0 settlement

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-RECON-001 | Break severity classification | Correct severity for various amounts |
| UT-RECON-002 | Matching algorithm — tolerance | Differences within tolerance = match |
| UT-RECON-003 | Segregation ratio calculation | Correct ratio and status |
| UT-RECON-004 | Break aging calculation | Uses business days (excludes holidays) |
| UT-RECON-005 | Escalation tier transitions | Correct tier at 3, 5, 10 days |
| UT-RECON-006 | Maker-checker validation | Same user blocked from both roles |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-RECON-001 | Full reconciliation run E2E | Run completes, breaks detected correctly |
| IT-RECON-002 | Bank statement adapter | Real bank data parsed and matched |
| IT-RECON-003 | Break resolution workflow | Maker submits, checker approves, break resolved |
| IT-RECON-004 | K-16 Ledger integration | Internal balances fetched correctly |
| IT-RECON-005 | K-05 event emission | Reconciliation events published |
| IT-RECON-006 | Evidence package generation | PDF/JSON generated with correct data |
| IT-RECON-007 | Tenant isolation | Tenant A cannot see Tenant B's reconciliation data |

### 10.3 Regulatory Scenario Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| RT-RECON-001 | Segregation shortfall detection | P0 alert fired on shortfall |
| RT-RECON-002 | 10-day break escalation | REGULATORY tier reached, notification sent |
| RT-RECON-003 | Evidence package completeness | All regulatory fields present |
| RT-RECON-004 | SEBON audit trail | All required audit events exist |

### 10.4 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-RECON-001 | Bank API down during recon | Run completes with PARTIAL status, retries |
| CT-RECON-002 | K-16 Ledger unavailable | Run fails, alerts fired, auto-retry |
| CT-RECON-003 | 500K accounts volume test | Completes within 15min SLA |
| CT-RECON-004 | Concurrent runs (50 tenants) | All complete without interference |

---

**END OF D-13 CLIENT MONEY RECONCILIATION LLD**
