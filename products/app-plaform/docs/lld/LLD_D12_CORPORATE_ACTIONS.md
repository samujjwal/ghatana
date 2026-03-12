# LOW-LEVEL DESIGN: D-12 CORPORATE ACTIONS

**Module**: D-12 Corporate Actions  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Corporate Actions Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Corporate Actions module manages **bonus shares, dividends, rights issues, stock splits, mergers, and other corporate events** including entitlement calculation, tax withholding, and ledger posting.

**Core Responsibilities**:
- Corporate action announcement tracking and lifecycle management
- Entitlement calculation (bonus, dividend, rights, splits)
- T2 Rule Packs via K-03 for jurisdiction-specific entitlement logic
- Ex-date and record date enforcement (K-15 dual-calendar)
- Tax withholding calculation and deduction
- Ledger posting to K-16 (cash dividends, bonus shares)
- Holder identification and notification
- Election management (rights issues, optional dividends)
- Reconciliation with CSD/depository records

**Invariants**:
1. Entitlements MUST be calculated against record date positions only
2. Ex-date enforcement MUST use K-15 BS business day rules
3. Tax withholding MUST comply with jurisdiction-specific rates
4. All entitlement distributions MUST create K-16 ledger entries
5. Corporate action changes after announcement REQUIRE maker-checker approval

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| D-03 PMS | Position snapshots at record date | D-03 stable |
| D-11 Reference Data | Instrument master data | D-11 stable |
| K-02 Config Engine | Tax rates, entitlement configs | K-02 stable |
| K-03 Rules Engine | T2 entitlement rule packs | K-03 stable |
| K-05 Event Bus | CorporateActionEvent | K-05 stable |
| K-07 Audit Framework | Entitlement audit | K-07 stable |
| K-15 Dual-Calendar | Ex-date, record date, payment date | K-15 stable |
| K-16 Ledger | Cash and securities posting | K-16 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/corporate-actions
Authorization: Bearer {ca_admin_token}

Request:
{
  "instrument_id": "NABIL",
  "action_type": "CASH_DIVIDEND",
  "announcement_date_bs": "2082-10-15",
  "ex_date_bs": "2082-11-01",
  "record_date_bs": "2082-11-03",
  "payment_date_bs": "2082-11-15",
  "rate": 25.00,
  "rate_type": "PERCENTAGE",
  "tax_rate": 5.0,
  "currency": "NPR"
}

Response 201:
{
  "ca_id": "CA-2082-001",
  "status": "ANNOUNCED",
  "created_at": "2026-01-28T10:00:00Z"
}
```

```yaml
GET /api/v1/corporate-actions/{ca_id}/entitlements

Response 200:
{
  "ca_id": "CA-2082-001",
  "action_type": "CASH_DIVIDEND",
  "instrument_id": "NABIL",
  "entitlements": [
    {
      "account_id": "ACC-001",
      "holding_quantity": 10000,
      "gross_entitlement": 25000.00,
      "tax_withheld": 1250.00,
      "net_entitlement": 23750.00,
      "status": "CALCULATED"
    }
  ],
  "total_accounts": 5000,
  "total_gross": 125000000.00
}
```

```yaml
GET /api/v1/corporate-actions?instrument_id=NABIL&status=ANNOUNCED,PROCESSING

Response 200:
{
  "corporate_actions": [...],
  "total": 3
}
```

```yaml
POST /api/v1/corporate-actions/{ca_id}/elections
Authorization: Bearer {user_token}

Request:
{
  "account_id": "ACC-001",
  "election": "ACCEPT",
  "quantity": 500
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.corporateactions.v1;

service CorporateActionsService {
  rpc CreateCorporateAction(CorporateActionRequest) returns (CorporateAction);
  rpc GetEntitlements(EntitlementQuery) returns (EntitlementList);
  rpc ProcessDistribution(DistributionRequest) returns (DistributionResult);
  rpc SubmitElection(ElectionRequest) returns (ElectionResult);
  rpc Reconcile(ReconcileRequest) returns (ReconcileResult);
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE corporate_actions (
    id VARCHAR(100) PRIMARY KEY,
    instrument_id VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL, -- CASH_DIVIDEND, BONUS_SHARE, RIGHTS_ISSUE, STOCK_SPLIT, MERGER
    announcement_date DATE,
    announcement_date_bs VARCHAR(20),
    ex_date DATE,
    ex_date_bs VARCHAR(20),
    record_date DATE,
    record_date_bs VARCHAR(20),
    payment_date DATE,
    payment_date_bs VARCHAR(20),
    rate DECIMAL(18,8),
    rate_type VARCHAR(20), -- PERCENTAGE, RATIO, FIXED_AMOUNT
    ratio_from INTEGER,
    ratio_to INTEGER,
    tax_rate DECIMAL(8,4),
    currency VARCHAR(10),
    status VARCHAR(30) DEFAULT 'ANNOUNCED', -- ANNOUNCED, EX_DATE_APPLIED, RECORD_DATE_SET, PROCESSING, DISTRIBUTED, RECONCILED
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE entitlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ca_id VARCHAR(100) NOT NULL REFERENCES corporate_actions(id),
    account_id VARCHAR(100) NOT NULL,
    holding_quantity INTEGER NOT NULL,
    gross_entitlement DECIMAL(18,4),
    tax_withheld DECIMAL(18,4),
    net_entitlement DECIMAL(18,4),
    entitlement_type VARCHAR(20), -- CASH, SECURITIES
    securities_quantity INTEGER,
    fractional_handling VARCHAR(20), -- ROUND_DOWN, CASH_IN_LIEU
    fractional_cash DECIMAL(18,4),
    status VARCHAR(30) DEFAULT 'CALCULATED', -- CALCULATED, APPROVED, DISTRIBUTED, RECONCILED
    ledger_tx_id VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE ca_elections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ca_id VARCHAR(100) NOT NULL REFERENCES corporate_actions(id),
    account_id VARCHAR(100) NOT NULL,
    election VARCHAR(20) NOT NULL, -- ACCEPT, DECLINE, PARTIAL
    quantity INTEGER,
    deadline_bs VARCHAR(20),
    submitted_at TIMESTAMPTZ DEFAULT now(),
    status VARCHAR(20) DEFAULT 'SUBMITTED'
);
```

### 3.2 Event Schemas

```json
{ "event_type": "CorporateActionAnnouncedEvent", "data": { "ca_id": "CA-2082-001", "type": "CASH_DIVIDEND", "instrument_id": "NABIL" } }
```
```json
{ "event_type": "EntitlementCalculatedEvent", "data": { "ca_id": "CA-2082-001", "total_accounts": 5000, "total_gross": 125000000 } }
```
```json
{ "event_type": "CorporateActionDistributedEvent", "data": { "ca_id": "CA-2082-001", "distributed_amount": 118750000, "tax_withheld": 6250000 } }
```

---

## 4. CONTROL FLOW

### 4.1 Corporate Action Lifecycle
```
1. CA announced: create record, status=ANNOUNCED
2. Ex-date reached: apply ex-date adjustment to D-04 market data
3. Record date reached: snapshot positions from D-03 PMS
4. Calculate entitlements:
   a. For each holder with position at record date:
      - Calculate gross entitlement (rate × holding)
      - Apply tax withholding (jurisdiction-specific via K-03)
      - Handle fractional entitlements
   b. Store entitlements, status=CALCULATED
5. Approval: maker-checker review of entitlements
6. Payment date: distribute
   a. CASH_DIVIDEND: K-16 ledger credit cash, debit issuer
   b. BONUS_SHARE: K-16 ledger credit securities
   c. RIGHTS_ISSUE: process elections, create subscription entries
7. Publish CorporateActionDistributedEvent
8. Reconcile with CSD/depository records
```

### 4.2 Bonus Share Calculation
```
function calculateBonusEntitlement(holding, ratio_from, ratio_to):
  bonus_shares = floor(holding * ratio_to / ratio_from)
  fractional = (holding * ratio_to / ratio_from) - bonus_shares
  if fractional > 0:
    fractional_cash = fractional * face_value
  return { bonus_shares, fractional_cash }
```

### 4.3 Tax Withholding
```
function calculateTax(gross, action_type, jurisdiction):
  tax_rate = K-03.evaluate("TAX_WITHHOLDING", { action_type, jurisdiction })
  tax = gross * tax_rate
  net = gross - tax
  return { tax, net }
```

---

## 5–10. ALGORITHMS, NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: Entitlement calc <10 min for 100K holders; distribution <30 min; 99.99% uptime
- **Security**: K-01 RBAC; CA creation requires maker-checker; tax data encrypted; audit trail for all distributions
- **Metrics**: `corporate_actions_active`, `corporate_actions_distributed_total`, `corporate_actions_entitlement_errors`, `corporate_actions_reconciliation_breaks`
- **Extension Points**: T2 jurisdiction-specific tax rules and entitlement logic via K-03; T3 CSD adapters for reconciliation; custom fractional handling policies
- **Tests**: Bonus calculation accuracy including fractionals; tax withholding rate correctness; ex-date enforcement across BS calendar; rights issue election deadline enforcement; reconciliation break detection
