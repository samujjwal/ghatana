# LOW-LEVEL DESIGN: D-06 RISK ENGINE

**Module**: D-06 Risk Engine  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Risk Management Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Risk Engine provides **pre-trade risk checks, real-time position risk monitoring, margin calculation, margin call management, and forced liquidation orchestration**.

**Core Responsibilities**:
- Pre-trade risk validation via K-03 Rules Engine pipeline
- Real-time exposure and concentration monitoring
- VaR (Value at Risk) calculation — parametric, historical, Monte Carlo
- Margin calculation (initial, variation, maintenance)
- Margin call generation and escalation workflow
- Forced liquidation trigger and D-02 EMS integration
- Stress testing and scenario analysis
- Risk limit management with breach alerting
- Dual-calendar risk period alignment

**Invariants**:
1. Pre-trade risk check MUST complete within 5ms for pass/fail
2. Margin calculations MUST reconcile with exchange requirements
3. Margin calls MUST be issued before settlement cutoff
4. Forced liquidation MUST follow regulatory priority rules
5. All risk overrides REQUIRE dual approval via K-07

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| D-03 PMS | Portfolio positions | D-03 stable |
| D-05 Pricing Engine | MTM values, valuations | D-05 stable |
| K-02 Config Engine | Risk parameters, limits | K-02 stable |
| K-03 Rules Engine | Pre-trade risk rule pipeline | K-03 stable |
| K-05 Event Bus | MarginCallEvent, RiskBreachEvent | K-05 stable |
| K-07 Audit Framework | Risk override audit | K-07 stable |
| K-15 Dual-Calendar | Risk period dates | K-15 stable |
| K-16 Ledger | Collateral balances | K-16 stable |
| K-18 Resilience | Circuit breaker for risk calc | K-18 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/risk/pre-trade-check
Authorization: Bearer {service_token}

Request:
{
  "order_id": "ORD-001",
  "account_id": "ACC-001",
  "instrument_id": "NABIL",
  "side": "BUY",
  "quantity": 1000,
  "price": 1250.00
}

Response 200:
{
  "order_id": "ORD-001",
  "approved": true,
  "checks": [
    { "rule": "BUYING_POWER", "status": "PASS", "available": 5000000, "required": 1250000 },
    { "rule": "CONCENTRATION_LIMIT", "status": "PASS", "current_pct": 8.5, "limit_pct": 25.0 },
    { "rule": "CIRCUIT_BREAKER", "status": "PASS" }
  ],
  "latency_ms": 3.2
}
```

```yaml
GET /api/v1/risk/exposure/{account_id}

Response 200:
{
  "account_id": "ACC-001",
  "total_exposure": 25000000,
  "net_exposure": 12500000,
  "margin_required": 3750000,
  "margin_available": 5000000,
  "margin_utilization_pct": 75.0,
  "var_95_1d": 750000,
  "concentration": [
    { "sector": "Banking", "pct": 42.0, "limit": 50.0 },
    { "sector": "Insurance", "pct": 18.0, "limit": 30.0 }
  ]
}
```

```yaml
POST /api/v1/risk/margin-call
Authorization: Bearer {service_token}

Request:
{
  "account_id": "ACC-002",
  "shortfall": 500000,
  "deadline_bs": "2082-11-27",
  "type": "MARGIN_CALL"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.risk.v1;

service RiskService {
  rpc PreTradeCheck(PreTradeRequest) returns (PreTradeResult);
  rpc GetExposure(ExposureRequest) returns (ExposureReport);
  rpc CalculateVaR(VaRRequest) returns (VaRResult);
  rpc RunStressTest(StressTestRequest) returns (StressTestResult);
  rpc IssueMarginCall(MarginCallRequest) returns (MarginCallResult);
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE risk_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) NOT NULL,
    limit_type VARCHAR(50) NOT NULL, -- CONCENTRATION, EXPOSURE, VAR, SECTOR
    limit_value DECIMAL(18,4) NOT NULL,
    current_value DECIMAL(18,4) DEFAULT 0,
    currency VARCHAR(10),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE margin_calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) NOT NULL,
    call_type VARCHAR(30) NOT NULL, -- MARGIN_CALL, FORCED_LIQUIDATION
    shortfall DECIMAL(18,4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    deadline TIMESTAMPTZ,
    deadline_bs VARCHAR(20),
    status VARCHAR(30) DEFAULT 'PENDING', -- PENDING, MET, ESCALATED, LIQUIDATING
    created_at TIMESTAMPTZ DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE TABLE var_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) NOT NULL,
    calculation_date DATE NOT NULL,
    method VARCHAR(30) NOT NULL, -- PARAMETRIC, HISTORICAL, MONTE_CARLO
    confidence_level DECIMAL(5,4),
    holding_period_days INTEGER,
    var_value DECIMAL(18,4),
    cvar_value DECIMAL(18,4),
    created_at TIMESTAMPTZ DEFAULT now()
);
```

### 3.2 Event Schemas

```json
{ "event_type": "RiskBreachEvent", "data": { "account_id": "ACC-001", "breach_type": "CONCENTRATION_LIMIT", "current": 52.0, "limit": 50.0 } }
```
```json
{ "event_type": "MarginCallEvent", "data": { "account_id": "ACC-002", "shortfall": 500000, "deadline_bs": "2082-11-27" } }
```
```json
{ "event_type": "ForcedLiquidationEvent", "data": { "account_id": "ACC-002", "instruments": ["NABIL"], "reason": "MARGIN_CALL_UNMET" } }
```

---

## 4. CONTROL FLOW

### 4.1 Pre-Trade Risk Pipeline
```
1. Order arrives from D-01 OMS
2. K-03 Rules Engine evaluates risk rule chain:
   a. Buying power check (K-16 ledger balance)
   b. Concentration limit check
   c. Circuit breaker status check (D-04)
   d. Account-level exposure limit
3. Return PASS/FAIL with detailed reasons
4. Target: <5ms total latency
```

### 4.2 Margin Call Escalation
```
1. EOD aggregation detects margin shortfall
2. Issue MarginCallEvent with deadline
3. T+0: Notification sent via K-05
4. T+1: If unmet, escalate to risk manager
5. T+2: If still unmet, trigger ForcedLiquidationEvent
6. D-02 EMS executes liquidation orders
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Parametric VaR
$$\text{VaR}_{\alpha} = \mu_P + z_{\alpha} \cdot \sigma_P$$

Where $\mu_P$ = portfolio expected return, $\sigma_P$ = portfolio standard deviation, $z_{\alpha}$ = z-score at confidence level.

### 5.2 Historical VaR
Sort historical P&L, take the percentile loss at confidence level.

---

## 6–10. NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: Pre-trade <5ms; VaR calc <30s for 10K positions; 99.99% uptime
- **Security**: K-01 RBAC; risk overrides dual-approval; margin data encryption at rest
- **Metrics**: `risk_pretrade_latency_ms`, `risk_var_calculation_seconds`, `risk_margin_calls_active`, `risk_breaches_total`
- **Extension Points**: T2 jurisdiction-specific margin rules; T3 custom risk model plugins
- **Tests**: Pre-trade latency SLA; margin calculation accuracy; VaR backtesting; forced liquidation priority ordering
