# LOW-LEVEL DESIGN: D-09 POST-TRADE PROCESSING

**Module**: D-09 Post-Trade Processing  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Settlement & Clearing Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Post-Trade Processing module handles **trade confirmation, netting, settlement lifecycle management (T+n), clearing integration, ledger posting via K-16, and T3 depository/CSD adapters**.

**Core Responsibilities**:
- Trade confirmation generation and distribution
- Bilateral and multilateral netting
- Settlement lifecycle management (T+0, T+1, T+2, T+n via K-15 dual-calendar)
- Settlement instruction generation for CSD/depository
- Clearing and CCP integration via T3 adapters
- Ledger posting to K-16 (securities and cash movements)
- Settlement failure management and buy-in procedures
- Reconciliation with exchange settlement reports

**Invariants**:
1. Every executed trade MUST generate a settlement instruction
2. Settlement dates MUST be calculated using K-15 dual-calendar (BS business days)
3. Netting MUST preserve audit trail of constituent trades
4. Ledger postings MUST be atomic (double-entry via K-16)
5. Settlement failures MUST trigger escalation within T+1 of failure

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| D-01 OMS | Trade execution events | D-01 stable |
| K-02 Config Engine | Settlement cycle configs (T+n per market) | K-02 stable |
| K-03 Rules Engine | Netting rules, settlement priority | K-03 stable |
| K-05 Event Bus | TradeExecutedEvent, SettlementEvent | K-05 stable |
| K-07 Audit Framework | Settlement audit trail | K-07 stable |
| K-15 Dual-Calendar | Settlement date calculation | K-15 stable |
| K-16 Ledger | Securities & cash ledger posting | K-16 stable |
| K-17 DTC | Distributed transaction coordination | K-17 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/post-trade/settlements?status=PENDING&settlement_date_bs=2082-11-27

Response 200:
{
  "settlements": [
    {
      "settlement_id": "STL-001",
      "trade_id": "TRD-001",
      "instrument_id": "NABIL",
      "buyer_account": "ACC-001",
      "seller_account": "ACC-002",
      "quantity": 1000,
      "amount": 1250000,
      "currency": "NPR",
      "settlement_date_bs": "2082-11-27",
      "settlement_date_gregorian": "2026-03-10",
      "status": "PENDING"
    }
  ]
}
```

```yaml
POST /api/v1/post-trade/netting/run
Authorization: Bearer {service_token}

Request:
{
  "netting_date_bs": "2082-11-25",
  "netting_type": "MULTILATERAL",
  "market": "NEPSE"
}

Response 200:
{
  "netting_batch_id": "NET-2082-11-25-001",
  "total_trades": 5000,
  "net_obligations": 1200,
  "compression_ratio": 0.76
}
```

```yaml
POST /api/v1/post-trade/confirm/{trade_id}
Authorization: Bearer {counterparty_token}

Request:
{
  "confirmation_status": "CONFIRMED",
  "counterparty_ref": "CP-REF-001"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.posttrade.v1;

service PostTradeService {
  rpc GetSettlements(SettlementQuery) returns (SettlementList);
  rpc RunNetting(NettingRequest) returns (NettingResult);
  rpc ConfirmTrade(TradeConfirmation) returns (ConfirmationResult);
  rpc GetSettlementFailures(FailureQuery) returns (FailureList);
  rpc GenerateSettlementInstruction(InstructionRequest) returns (SettlementInstruction);
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE trade_confirmations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id VARCHAR(100) NOT NULL,
    buyer_confirmed BOOLEAN DEFAULT false,
    seller_confirmed BOOLEAN DEFAULT false,
    buyer_confirmed_at TIMESTAMPTZ,
    seller_confirmed_at TIMESTAMPTZ,
    status VARCHAR(30) DEFAULT 'PENDING', -- PENDING, CONFIRMED, DISPUTED, CANCELLED
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE settlement_obligations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id VARCHAR(100) NOT NULL,
    netting_batch_id VARCHAR(100),
    instrument_id VARCHAR(50) NOT NULL,
    buyer_account VARCHAR(100) NOT NULL,
    seller_account VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    amount DECIMAL(18,4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    settlement_date DATE NOT NULL,
    settlement_date_bs VARCHAR(20),
    settlement_cycle VARCHAR(10) NOT NULL, -- T+0, T+1, T+2
    status VARCHAR(30) DEFAULT 'PENDING', -- PENDING, MATCHED, SETTLED, FAILED, BUY_IN
    csd_instruction_ref VARCHAR(100),
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE netting_batches (
    id VARCHAR(100) PRIMARY KEY,
    netting_date DATE NOT NULL,
    netting_date_bs VARCHAR(20),
    netting_type VARCHAR(30) NOT NULL, -- BILATERAL, MULTILATERAL
    market VARCHAR(50),
    total_gross_trades INTEGER,
    total_net_obligations INTEGER,
    compression_ratio DECIMAL(5,4),
    status VARCHAR(20) DEFAULT 'PROCESSING',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE settlement_failures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    obligation_id UUID NOT NULL REFERENCES settlement_obligations(id),
    failure_reason VARCHAR(100), -- INSUFFICIENT_SECURITIES, INSUFFICIENT_FUNDS, CSD_REJECTION
    failed_at TIMESTAMPTZ DEFAULT now(),
    buy_in_deadline DATE,
    buy_in_deadline_bs VARCHAR(20),
    resolution_status VARCHAR(30) DEFAULT 'OPEN'
);
```

### 3.2 Event Schemas

```json
{ "event_type": "SettlementCreatedEvent", "data": { "settlement_id": "STL-001", "trade_id": "TRD-001", "settlement_date_bs": "2082-11-27" } }
```
```json
{ "event_type": "SettlementCompletedEvent", "data": { "settlement_id": "STL-001", "ledger_tx_id": "LTX-001" } }
```
```json
{ "event_type": "SettlementFailureEvent", "data": { "settlement_id": "STL-001", "reason": "INSUFFICIENT_SECURITIES" } }
```

---

## 4. CONTROL FLOW

### 4.1 Settlement Lifecycle
```
1. TradeExecutedEvent consumed from K-05
2. Calculate settlement date: trade_date + T+n (K-15 BS business days)
3. Generate settlement obligation
4. Run netting if configured (multilateral for exchange trades)
5. Generate CSD instruction (T3 depository adapter)
6. On settlement date:
   a. Validate buyer has funds (K-16 ledger)
   b. Validate seller has securities (K-16 ledger)
   c. Execute DVP (Delivery vs Payment) via K-17 DTC
   d. Post ledger entries: debit/credit securities, debit/credit cash
7. Publish SettlementCompletedEvent
8. On failure: log failure, initiate buy-in countdown
```

### 4.2 Netting
```
1. Collect all trades for netting period
2. Group by instrument + counterparty pair
3. Calculate net obligation per pair:
   net_qty = sum(buy_qty) - sum(sell_qty)
   net_amount = sum(buy_amount) - sum(sell_amount)
4. Generate net settlement obligations
5. Store constituent trade mapping for audit
```

---

## 5–10. ALGORITHMS, NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: Netting batch <10 min for 50K trades; settlement instruction <100ms; 99.99% uptime
- **Security**: K-01 RBAC; settlement instructions encrypted in transit (mTLS); dual-approval for manual settlement overrides
- **Metrics**: `posttrade_settlement_pending`, `posttrade_settlement_failed`, `posttrade_netting_compression_ratio`, `posttrade_settlement_sla_breach`
- **Extension Points**: T3 CSD/depository adapters; T2 jurisdiction-specific netting rules; custom settlement cycle configurations
- **Tests**: DVP atomicity under failure; netting compression accuracy; settlement date calculation across BS holidays; buy-in escalation timing
