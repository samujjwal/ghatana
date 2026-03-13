# LOW-LEVEL DESIGN: D-03 PORTFOLIO MANAGEMENT SYSTEM (PMS)

**Module**: D-03 Portfolio Management System  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Asset Management Domain Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Portfolio Management System provides **portfolio construction, NAV calculation, rebalancing with drift detection, and position management** integrated with K-16 Ledger Framework for authoritative position data.

**Core Responsibilities**:
- Portfolio construction and management
- Position tracking via K-05 event ingestion (TradeExecuted events)
- Net Asset Value (NAV) calculation engine
- Rebalancing engine with configurable drift thresholds
- Drift detection and alerting
- Maker-checker for rebalancing approval
- Portfolio analytics and performance attribution
- Dual-calendar on all portfolio snapshots

**Invariants**:
1. Positions MUST be derived from K-16 ledger entries (source of truth)
2. NAV calculations MUST use D-05 Pricing Engine prices
3. Rebalancing proposals MUST go through maker-checker
4. All portfolio changes MUST be audited via K-07
5. Drift thresholds MUST be configurable per jurisdiction (T1)

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Portfolio policies, drift thresholds | K-02 stable |
| K-05 Event Bus | TradeExecuted events, position updates | K-05 stable |
| K-07 Audit Framework | Portfolio audit trail | K-07 stable |
| K-15 Dual-Calendar | Dual timestamps on snapshots | K-15 stable |
| K-16 Ledger Framework | Authoritative position data | K-16 stable |
| D-05 Pricing Engine | Price data for NAV | D-05 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/portfolios/{portfolio_id}
Authorization: Bearer {user_token}

Response 200:
{
  "portfolio_id": "pf_001",
  "name": "Growth Portfolio",
  "holdings": [
    { "instrument_id": "NABIL", "quantity": 500, "market_value": 625000.00, "weight": 0.25, "cost_basis": 600000.00 },
    { "instrument_id": "SBL", "quantity": 1000, "market_value": 450000.00, "weight": 0.18, "cost_basis": 420000.00 }
  ],
  "total_nav": 2500000.00,
  "nav_date_bs": "2082-11-25",
  "nav_date_gregorian": "2026-03-08",
  "currency": "NPR"
}
```

```yaml
POST /api/v1/portfolios/{portfolio_id}/rebalance
Authorization: Bearer {user_token}
Content-Type: application/json

Request:
{
  "target_allocation": [
    { "instrument_id": "NABIL", "target_weight": 0.30 },
    { "instrument_id": "SBL", "target_weight": 0.20 }
  ],
  "rebalance_strategy": "PROPORTIONAL",
  "maker_comments": "Quarterly rebalancing"
}

Response 202:
{
  "rebalance_id": "reb_123",
  "status": "PENDING_APPROVAL",
  "proposed_trades": [
    { "instrument_id": "NABIL", "side": "BUY", "quantity": 100, "estimated_cost": 125000.00 }
  ]
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.pms.v1;

service PortfolioManagementService {
  rpc GetPortfolio(GetPortfolioRequest) returns (Portfolio);
  rpc CalculateNAV(CalculateNAVRequest) returns (NAVResult);
  rpc ProposeRebalance(RebalanceRequest) returns (RebalanceProposal);
  rpc ApproveRebalance(ApproveRebalanceRequest) returns (RebalanceResult);
  rpc GetHoldings(GetHoldingsRequest) returns (HoldingsResponse);
  rpc GetDriftReport(DriftReportRequest) returns (DriftReport);
  rpc GetPerformance(PerformanceRequest) returns (PerformanceReport);
}
```

---

## 3. DATA MODEL

### 3.1 Storage Tables

```sql
CREATE TABLE portfolios (
    portfolio_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    account_id UUID NOT NULL,
    portfolio_type VARCHAR(50) NOT NULL CHECK (portfolio_type IN ('GROWTH','INCOME','BALANCED','INDEX','CUSTOM')),
    currency VARCHAR(10) DEFAULT 'NPR',
    benchmark_id VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_at_bs VARCHAR(20)
);

CREATE TABLE portfolio_holdings (
    holding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios(portfolio_id),
    instrument_id VARCHAR(50) NOT NULL,
    quantity DECIMAL(18,8) NOT NULL,
    cost_basis DECIMAL(18,8) NOT NULL,
    target_weight DECIMAL(5,4),
    current_weight DECIMAL(5,4),
    last_updated TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(portfolio_id, instrument_id)
);

CREATE TABLE nav_history (
    nav_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios(portfolio_id),
    nav_value DECIMAL(18,8) NOT NULL,
    nav_per_unit DECIMAL(18,8),
    total_units DECIMAL(18,8),
    calculation_date_gregorian DATE NOT NULL,
    calculation_date_bs VARCHAR(20),
    calculated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(portfolio_id, calculation_date_gregorian)
);

CREATE TABLE rebalance_proposals (
    rebalance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios(portfolio_id),
    strategy VARCHAR(50) NOT NULL,
    proposed_trades JSONB NOT NULL,
    current_allocation JSONB NOT NULL,
    target_allocation JSONB NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING_APPROVAL',
    maker_id UUID NOT NULL,
    checker_id UUID,
    maker_comments TEXT,
    checker_comments TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    approved_at TIMESTAMPTZ
);
```

### 3.2 Event Schemas

```json
{ "event_type": "RebalanceProposed", "data": { "rebalance_id": "reb_123", "portfolio_id": "pf_001", "proposed_trades": [...] } }
```
```json
{ "event_type": "NavCalculated", "data": { "portfolio_id": "pf_001", "nav_value": 2500000.00, "date_bs": "2082-11-25" } }
```
```json
{ "event_type": "DriftDetected", "data": { "portfolio_id": "pf_001", "instrument_id": "NABIL", "current_weight": 0.28, "target_weight": 0.25, "drift_bps": 300 } }
```

---

## 4. CONTROL FLOW

### 4.1 NAV Calculation Flow
```
1. Trigger: Scheduled (EOD) or on-demand
2. For each holding in portfolio:
   a. Get current price from D-05 Pricing Engine
   b. market_value = quantity × current_price
3. total_nav = sum(market_values) + cash_balance
4. nav_per_unit = total_nav / total_units
5. Store in nav_history
6. Publish NavCalculated event
7. Check drift thresholds → if exceeded, publish DriftDetected
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Drift Detection
```
function checkDrift(portfolio):
  for each holding:
    current_weight = holding.market_value / portfolio.total_nav
    drift = abs(current_weight - holding.target_weight)
    if drift > K02.getThreshold(portfolio.jurisdiction, 'DRIFT_THRESHOLD'):
      publish DriftDetected event
```

---

## 6. NFR BUDGETS

| Operation | P99 Latency | Throughput |
|-----------|-------------|------------|
| calculateNAV() | 10ms (1K positions) | 5,000/s |
| getPortfolio() | 5ms | 10,000/s |
| proposeRebalance() | 50ms | 1,000/s |

**Availability**: 99.99%

---

## 7–10. SECURITY, OBSERVABILITY, EXTENSIBILITY, TEST PLAN

- **Security**: K-01 RBAC; maker-checker for rebalancing; tenant isolation via RLS
- **Metrics**: `pms_nav_calculations_total`, `pms_drift_detected_total`, `pms_rebalance_proposals_total`
- **Extension Points**: T2 holding limit Rule Packs; T1 drift threshold configs; custom analytics (T3)
- **Tests**: NAV calculation accuracy; drift detection thresholds; maker-checker workflow; K-16 position reconciliation
