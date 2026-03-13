# LOW-LEVEL DESIGN: K-16 LEDGER FRAMEWORK

**Module**: K-16 Ledger Framework  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

K-16 provides an **immutable, double-entry ledger primitive** for all financial tracking across domain modules (order settlement, client money, margin, fees, corporate actions).

**Core Responsibilities**:
- Double-entry bookkeeping with automatic balance enforcement
- Append-only, event-sourced journal entries (no updates, no deletes)
- Temporal balance queries with pre-computed snapshots
- Multi-currency and multi-asset support with configurable precision
- Built-in reconciliation engine for external system matching
- Nightly balance verification with integrity alerts
- Chart of accounts management (T1 config per jurisdiction)
- Dual-calendar timestamps on all ledger entries

**Invariants**:
1. Every journal entry MUST have equal debits and credits (balanced)
2. Entries are APPEND-ONLY — no updates or deletes permitted at DB level
3. Account balance MUST never go negative unless explicitly allowed by account type (e.g., liability)
4. All monetary values use `NUMERIC(18,6)` for precision
5. Every entry MUST carry both BS and Gregorian timestamps
6. Reconciliation breaks MUST be detected within 1 business day

### 1.2 Explicit Non-Goals

- ❌ Financial reporting / report generation (domain layer responsibility)
- ❌ Tax calculation (handled by T2 rule packs)
- ❌ Payment gateway integration (handled by domain adapters)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Chart of accounts, precision rules, reconciliation schedules | K-02 stable |
| K-05 Event Bus | Ledger events (JournalPosted, ReconciliationCompleted) | K-05 stable |
| K-07 Audit Framework | Immutable audit of all ledger mutations | K-07 stable |
| K-15 Dual-Calendar | BS timestamps for all entries | K-15 stable |
| K-17 DTC | Cross-module atomic postings | K-17 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/ledger/journal
Authorization: Bearer {service_token}
Content-Type: application/json

Request:
{
  "journal_id": "jnl_abc123",
  "description": "Trade settlement — Order ORD-001",
  "effective_date": "2025-03-02",
  "effective_date_bs": "2081-11-18",
  "entries": [
    { "account_id": "acc_client_cash", "debit": 125050.00, "credit": 0, "currency": "NPR" },
    { "account_id": "acc_settlement", "debit": 0, "credit": 125050.00, "currency": "NPR" }
  ],
  "metadata": {
    "source_module": "D-01-OMS",
    "source_id": "ORD-001",
    "tenant_id": "tenant_np_1",
    "trade_id": "TRD-001"
  }
}

Response 201:
{
  "journal_id": "jnl_abc123",
  "sequence_number": 10523,
  "status": "POSTED",
  "posted_at": "2025-03-02T10:30:00Z",
  "posted_at_bs": "2081-11-18 10:30:00"
}

Response 400:
{
  "error": "LEDGER_E001",
  "message": "Journal entry unbalanced: debits=125050.00, credits=125000.00",
  "difference": 50.00
}
```

```yaml
GET /api/v1/ledger/accounts/{account_id}/balance?as_of=2025-03-02
Authorization: Bearer {service_token}

Response 200:
{
  "account_id": "acc_client_cash",
  "account_name": "Client Cash — NABIL Broker",
  "balance": 5250000.00,
  "currency": "NPR",
  "as_of": "2025-03-02",
  "as_of_bs": "2081-11-18",
  "last_entry_at": "2025-03-02T10:30:00Z",
  "entry_count": 1523
}
```

```yaml
GET /api/v1/ledger/accounts/{account_id}/statement?from=2025-03-01&to=2025-03-02
Authorization: Bearer {service_token}

Response 200:
{
  "account_id": "acc_client_cash",
  "period": { "from": "2025-03-01", "to": "2025-03-02" },
  "opening_balance": 5125000.00,
  "entries": [
    {
      "journal_id": "jnl_abc123",
      "effective_date": "2025-03-02",
      "effective_date_bs": "2081-11-18",
      "description": "Trade settlement — Order ORD-001",
      "debit": 125050.00,
      "credit": 0,
      "running_balance": 5250050.00
    }
  ],
  "closing_balance": 5250050.00,
  "currency": "NPR"
}
```

```yaml
POST /api/v1/ledger/reconciliation/run
Authorization: Bearer {service_token}

Request:
{
  "reconciliation_type": "DEPOSITORY",
  "as_of_date": "2025-03-02",
  "external_data_source": "cdsc_api",
  "accounts": ["acc_client_holdings_*"]
}

Response 202:
{
  "reconciliation_id": "recon_xyz789",
  "status": "IN_PROGRESS",
  "started_at": "2025-03-02T18:00:00Z"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.ledger.v1;

service LedgerService {
  rpc PostJournal(PostJournalRequest) returns (PostJournalResponse);
  rpc GetBalance(GetBalanceRequest) returns (GetBalanceResponse);
  rpc GetStatement(GetStatementRequest) returns (stream StatementEntry);
  rpc RunReconciliation(ReconciliationRequest) returns (ReconciliationResponse);
  rpc VerifyIntegrity(IntegrityRequest) returns (IntegrityResponse);
}

message PostJournalRequest {
  string journal_id = 1;
  string description = 2;
  string effective_date = 3;
  string effective_date_bs = 4;
  repeated LedgerEntry entries = 5;
  map<string, string> metadata = 6;
}

message LedgerEntry {
  string account_id = 1;
  string debit = 2;   // string to preserve decimal precision
  string credit = 3;
  string currency = 4;
}
```

### 2.3 SDK Method Signatures

```typescript
interface LedgerClient {
  /** Post a balanced journal entry */
  postJournal(journal: JournalRequest): Promise<JournalResult>;

  /** Get account balance as of date */
  getBalance(accountId: string, asOf?: Date): Promise<Balance>;

  /** Get account statement for date range */
  getStatement(accountId: string, from: Date, to: Date): Promise<Statement>;

  /** Trigger reconciliation */
  runReconciliation(request: ReconciliationRequest): Promise<ReconciliationResult>;

  /** Verify ledger integrity (checksum) */
  verifyIntegrity(fromDate: Date, toDate: Date): Promise<IntegrityResult>;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| LEDGER_E001 | 400 | No | Journal entry unbalanced |
| LEDGER_E002 | 409 | No | Duplicate journal_id |
| LEDGER_E003 | 400 | No | Account not found |
| LEDGER_E004 | 400 | No | Insufficient balance (debit exceeds available) |
| LEDGER_E005 | 400 | No | Invalid currency for account |
| LEDGER_E006 | 500 | Yes | Ledger store unavailable |
| LEDGER_E007 | 400 | No | Effective date in the future (not allowed) |
| RECON_E001 | 500 | Yes | External data source unavailable |
| RECON_E002 | 400 | No | Reconciliation already in progress |

---

## 3. DATA MODEL

### 3.1 Core Tables

#### accounts

```sql
CREATE TABLE accounts (
  account_id VARCHAR(100) PRIMARY KEY,
  tenant_id UUID NOT NULL,
  account_name VARCHAR(255) NOT NULL,
  account_type VARCHAR(50) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
  currency VARCHAR(10) NOT NULL DEFAULT 'NPR',
  precision_digits INT NOT NULL DEFAULT 2,
  allow_negative BOOLEAN NOT NULL DEFAULT false,
  parent_account_id VARCHAR(100) REFERENCES accounts(account_id),
  metadata JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
CREATE POLICY accounts_tenant_isolation ON accounts
  USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

#### journal_entries (append-only)

```sql
CREATE TABLE journal_entries (
  entry_id BIGSERIAL PRIMARY KEY,
  journal_id VARCHAR(100) NOT NULL,
  account_id VARCHAR(100) NOT NULL REFERENCES accounts(account_id),
  tenant_id UUID NOT NULL,
  debit NUMERIC(18,6) NOT NULL DEFAULT 0,
  credit NUMERIC(18,6) NOT NULL DEFAULT 0,
  currency VARCHAR(10) NOT NULL,
  effective_date DATE NOT NULL,
  effective_date_bs VARCHAR(10) NOT NULL,
  description TEXT,
  source_module VARCHAR(50) NOT NULL,
  source_id VARCHAR(100),
  metadata JSONB DEFAULT '{}',
  posted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  posted_at_bs VARCHAR(30) NOT NULL,
  sequence_number BIGSERIAL NOT NULL,
  CHECK (debit >= 0 AND credit >= 0),
  CHECK (NOT (debit > 0 AND credit > 0))  -- entry is either debit or credit, not both
);

-- Append-only: revoke UPDATE and DELETE
REVOKE UPDATE, DELETE ON journal_entries FROM siddhanta_app;

CREATE INDEX idx_journal_account ON journal_entries(account_id, effective_date);
CREATE INDEX idx_journal_journal_id ON journal_entries(journal_id);
CREATE INDEX idx_journal_source ON journal_entries(source_module, source_id);
CREATE INDEX idx_journal_tenant_date ON journal_entries(tenant_id, effective_date);

ALTER TABLE journal_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY journal_tenant_isolation ON journal_entries
  USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

#### balance_snapshots

```sql
CREATE TABLE balance_snapshots (
  snapshot_id BIGSERIAL PRIMARY KEY,
  account_id VARCHAR(100) NOT NULL REFERENCES accounts(account_id),
  tenant_id UUID NOT NULL,
  balance NUMERIC(18,6) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  snapshot_date DATE NOT NULL,
  snapshot_date_bs VARCHAR(10) NOT NULL,
  entry_count BIGINT NOT NULL,
  last_entry_id BIGINT NOT NULL,
  computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  computed_at_bs VARCHAR(30) NOT NULL,
  UNIQUE (account_id, snapshot_date)
);
```

#### reconciliation_runs

```sql
CREATE TABLE reconciliation_runs (
  reconciliation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  reconciliation_type VARCHAR(50) NOT NULL,
  as_of_date DATE NOT NULL,
  as_of_date_bs VARCHAR(10) NOT NULL,
  status VARCHAR(50) NOT NULL CHECK (status IN ('IN_PROGRESS', 'MATCHED', 'BREAKS_FOUND', 'FAILED')),
  total_accounts INT,
  matched_accounts INT,
  break_count INT DEFAULT 0,
  break_details JSONB,
  external_source VARCHAR(100),
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at_bs VARCHAR(30) NOT NULL,
  completed_at TIMESTAMPTZ,
  completed_at_bs VARCHAR(30)
);
```

### 3.2 K-05 Event Schemas

```json
{
  "event_type": "JournalPosted",
  "event_version": "1.0.0",
  "aggregate_id": "jnl_abc123",
  "aggregate_type": "Journal",
  "data": {
    "journal_id": "jnl_abc123",
    "entry_count": 2,
    "total_amount": 125050.00,
    "currency": "NPR",
    "source_module": "D-01-OMS"
  },
  "metadata": {
    "trace_id": "tr_abc",
    "causation_id": "cmd_settle_001",
    "correlation_id": "corr_trade_001",
    "tenant_id": "tenant_np_1"
  },
  "timestamp_bs": "2081-11-18",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

---

## 4. CONTROL FLOW

### 4.1 Journal Posting Flow

```
Client → POST /ledger/journal
  → LedgerController.postJournal()
    → Validate: sum(debits) == sum(credits) → else LEDGER_E001
    → Validate: journal_id not duplicate → else LEDGER_E002
    → Validate: all account_ids exist and are ACTIVE → else LEDGER_E003
    → Validate: effective_date <= today → else LEDGER_E007
    → FOR each entry:
        → Validate: currency matches account currency → else LEDGER_E005
        → IF debit > 0 AND NOT account.allow_negative:
            → Check balance sufficient → else LEDGER_E004
    → BEGIN TRANSACTION
        → INSERT journal_entries (all entries)
        → UPDATE balance_snapshots (incremental)
        → EventBus.publish(JournalPostedEvent)  // K-05
        → AuditService.log("JOURNAL_POSTED")    // K-07
    → COMMIT
    → 201 { journal_id, sequence_number, status: "POSTED" }
```

### 4.2 Balance Query Flow (with Snapshots)

```
Client → GET /ledger/accounts/{id}/balance?as_of=2025-03-02
  → BalanceService.getBalance(accountId, asOfDate)
    → Find latest snapshot <= asOfDate:
        snapshot = balance_snapshots WHERE account_id AND snapshot_date <= asOfDate ORDER BY DESC LIMIT 1
    → Sum entries after snapshot:
        delta = SELECT SUM(debit) - SUM(credit) FROM journal_entries
                WHERE account_id AND effective_date > snapshot.snapshot_date AND effective_date <= asOfDate
    → balance = snapshot.balance + delta (for ASSET/EXPENSE) or snapshot.balance - delta (for LIABILITY/EQUITY/REVENUE)
    → RETURN balance
```

### 4.3 Nightly Reconciliation Flow

```
CRON (23:30 daily) → ReconciliationService.runNightly()
  → FOR each reconciliation_type:
      → Fetch internal balances from ledger
      → Fetch external balances from data source (T3 plugin — CDSC, bank, etc.)
      → FOR each account pair:
          → IF internal_balance == external_balance:
              → MATCH
          → ELSE:
              → Record break: { account, internal, external, difference }
      → Persist reconciliation_run with results
      → EventBus.publish(ReconciliationCompletedEvent)
      → IF breaks found:
          → AlertService.fire(P2, "Reconciliation breaks detected")
          → Escalation starts (3/5/10 business day tiers)
```

### 4.4 Nightly Balance Snapshot

```
CRON (01:00 daily) → SnapshotService.computeDailySnapshots()
  → FOR each active account:
      → Compute full balance as of yesterday
      → INSERT balance_snapshot
      → Verify: snapshot matches sum of all journal entries → else integrity alert
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Double-Entry Validation

```python
def validate_journal(journal: JournalRequest) -> None:
    total_debit = sum(e.debit for e in journal.entries)
    total_credit = sum(e.credit for e in journal.entries)
    
    if abs(total_debit - total_credit) > Decimal("0.000001"):  # tolerance for floating point
        raise LedgerError("LEDGER_E001", 
            f"Unbalanced: debits={total_debit}, credits={total_credit}, diff={total_debit - total_credit}")
    
    if len(journal.entries) < 2:
        raise LedgerError("LEDGER_E001", "Journal must have at least 2 entries")
```

### 5.2 Balance Snapshot Integrity Check

```python
def verify_integrity(account_id: str, as_of_date: date) -> IntegrityResult:
    """Verify balance snapshot matches full recalculation from journal entries."""
    snapshot_balance = snapshot_repo.get_latest(account_id, as_of_date).balance
    
    full_recalc = journal_repo.sum_all_entries(account_id, as_of_date)
    # full_recalc = SUM(debit) - SUM(credit) for ASSET/EXPENSE
    # full_recalc = SUM(credit) - SUM(debit) for LIABILITY/EQUITY/REVENUE
    
    if abs(snapshot_balance - full_recalc) > Decimal("0.000001"):
        alert_service.fire(P1, f"Ledger integrity failure: account={account_id}, "
                                f"snapshot={snapshot_balance}, recalc={full_recalc}")
        return IntegrityResult(valid=False, snapshot=snapshot_balance, recalc=full_recalc)
    
    return IntegrityResult(valid=True)
```

### 5.3 Reconciliation Break Aging

```python
BREAK_ESCALATION_TIERS = [
    {"days": 3, "severity": "P3", "action": "ops_notification"},
    {"days": 5, "severity": "P2", "action": "management_escalation"},
    {"days": 10, "severity": "P1", "action": "regulatory_notification"}
]

def check_break_aging(break_record):
    age_days = business_days_since(break_record.detected_date, jurisdiction)
    for tier in BREAK_ESCALATION_TIERS:
        if age_days >= tier["days"] and not break_record.escalated_to(tier["severity"]):
            escalation_service.escalate(break_record, tier)
            event_bus.publish(ReconciliationBreakEscalatedEvent(...))
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Max |
|-----------|-----|-----|-----|-----|
| Journal posting (2-entry) | 5ms | 15ms | 30ms | 100ms |
| Journal posting (10-entry) | 10ms | 30ms | 60ms | 200ms |
| Balance query (snapshot hit) | 2ms | 5ms | 10ms | 50ms |
| Balance query (no snapshot) | 20ms | 50ms | 100ms | 500ms |
| Statement query (1 month) | 10ms | 30ms | 80ms | 500ms |
| Reconciliation (1K accounts) | 5s | 15s | 30s | 120s |

### 6.2 Throughput Targets

| Metric | Target |
|--------|--------|
| Journal postings | 10K/sec |
| Balance queries | 50K/sec (cached) |
| Concurrent reconciliations | 10 per tenant |

### 6.3 Storage Estimates

| Data | Daily Volume | 10-Year Estimate |
|------|-------------|------------------|
| Journal entries | 500K entries/day | ~2B entries (200GB) |
| Balance snapshots | 50K snapshots/day | ~180M snapshots (20GB) |
| Reconciliation records | 10 runs/day | ~36K runs (1GB) |

---

## 7. SECURITY DESIGN

### 7.1 Immutability Enforcement

- `REVOKE UPDATE, DELETE` on journal_entries at PostgreSQL level
- Application code has no UPDATE/DELETE paths for journal data
- Database trigger logs any attempted UPDATE/DELETE to a tamper_attempt table

### 7.2 Segregation of Duties

- Journal posting: `ledger:post` permission
- Balance viewing: `ledger:view` permission
- Reconciliation execution: `ledger:reconcile` permission
- Chart of accounts modification: `ledger:admin` + maker-checker

### 7.3 Tenant Isolation

- Row-Level Security on all tables
- Cross-tenant postings are impossible at DB level
- K-17 DTC handles inter-tenant settlements via paired journals

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```
siddhanta_ledger_journal_posted_total{tenant, source_module}       counter
siddhanta_ledger_journal_post_duration_ms{tenant}                  histogram
siddhanta_ledger_balance_query_duration_ms{snapshot_hit}            histogram
siddhanta_ledger_active_accounts{tenant}                           gauge
siddhanta_ledger_reconciliation_break_count{tenant, type}          gauge
siddhanta_ledger_reconciliation_duration_ms{type}                  histogram
siddhanta_ledger_integrity_check_result{result}                    counter
siddhanta_ledger_snapshot_lag_entries{tenant}                       gauge
```

### 8.2 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| Integrity check failure | Any account fails integrity verify | P1 |
| Reconciliation breaks > threshold | break_count > K-02 configured threshold | P2 |
| Reconciliation break aged > 5 days | Unresolved break > 5 business days | P1 |
| Snapshot lag > 24h | No snapshot for active account in 24h | P2 |
| Journal posting failure rate | >1% error rate over 5min | P2 |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Chart of Accounts (T1 Config)

```json
{
  "content_pack_type": "T1",
  "name": "nepal-broker-coa",
  "jurisdiction": "NP",
  "accounts": [
    { "id": "1000", "name": "Assets", "type": "ASSET", "parent": null },
    { "id": "1100", "name": "Client Cash", "type": "ASSET", "parent": "1000" },
    { "id": "1200", "name": "Client Securities Holdings", "type": "ASSET", "parent": "1000" },
    { "id": "2000", "name": "Liabilities", "type": "LIABILITY", "parent": null },
    { "id": "2100", "name": "Client Money Obligations", "type": "LIABILITY", "parent": "2000" }
  ]
}
```

### 9.2 Reconciliation Adapters (T3 Plugins)

```typescript
interface ReconciliationAdapterPlugin {
  readonly metadata: {
    name: string;       // e.g., "cdsc-position-adapter"
    externalSystem: string;
    version: string;
  };

  /** Fetch external balances for reconciliation */
  fetchExternalBalances(
    accountIds: string[],
    asOfDate: Date
  ): Promise<ExternalBalance[]>;

  /** Health check */
  healthCheck(): Promise<boolean>;
}
```

### 9.3 Multi-Currency / Digital Assets

The `currency` field supports any ISO 4217 code plus digital asset identifiers:
- Traditional: `NPR`, `USD`, `INR`
- Digital assets: `BTC`, `ETH`, custom token symbols
- CBDC: `NPR_CBDC` (jurisdiction CBDC identifier)

Account precision is configurable per currency via K-02 (e.g., `NPR` → 2 digits, `BTC` → 8 digits).

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-LED-001 | Balanced journal accepted | 201 response |
| UT-LED-002 | Unbalanced journal rejected | LEDGER_E001 |
| UT-LED-003 | Duplicate journal rejected | LEDGER_E002 |
| UT-LED-004 | Negative balance prevented | LEDGER_E004 |
| UT-LED-005 | Balance with snapshot | Snapshot + delta = correct |
| UT-LED-006 | Balance without snapshot | Full scan = correct |
| UT-LED-007 | Multi-currency entries | Currency mismatch → LEDGER_E005 |
| UT-LED-008 | Integrity check pass | Full recalc matches snapshot |
| UT-LED-009 | Integrity check fail | Mismatch detected and alerted |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-LED-001 | Post + balance query | Balance reflects posted entry |
| IT-LED-002 | Statement generation | Entries ordered, running balance correct |
| IT-LED-003 | Reconciliation with mock adapter | Match/break detection works |
| IT-LED-004 | K-05 events published | JournalPosted event in standard envelope |
| IT-LED-005 | K-07 audit trail | Journal posting audited |
| IT-LED-006 | Cross-module posting via K-17 | Atomic multi-account posting |
| IT-LED-007 | Append-only enforcement | UPDATE/DELETE on journal_entries fails |

### 10.3 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-LED-001 | DB failure mid-transaction | Journal rolls back atomically |
| CT-LED-002 | Snapshot job failure | Balance still calculable from full scan (degraded perf) |
| CT-LED-003 | External reconciliation source down | Recon marked FAILED, retried next cycle |
| CT-LED-004 | Concurrent journal postings | Serialized via sequence_number, no data loss |

---

**END OF K-16 LEDGER FRAMEWORK LLD**
