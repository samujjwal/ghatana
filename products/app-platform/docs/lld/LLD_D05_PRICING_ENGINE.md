# LOW-LEVEL DESIGN: D-05 PRICING ENGINE

**Module**: D-05 Pricing Engine  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Quantitative Analytics Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Pricing Engine provides **instrument valuation, yield curve construction, mark-to-market (MTM), and model execution for complex derivatives and structured products**.

**Core Responsibilities**:
- Real-time and EOD instrument pricing across asset classes (equity, fixed income, derivatives)
- Yield curve bootstrapping and interpolation (Nelson-Siegel, cubic spline)
- T3 pricing model plugins for jurisdiction-specific instruments
- Mark-to-market (MTM) calculation and distribution
- Theoretical price computation for derivatives (Black-Scholes, binomial)
- Pricing snapshot versioning and audit trail
- Price challenge workflow with maker-checker approval
- Dual-calendar settlement date calculation for price adjustments

**Invariants**:
1. All priced instruments MUST have an auditable pricing trail
2. EOD MTM MUST complete before settlement cutoff (T+0 17:00 local)
3. Yield curves MUST use at least 3 market data points
4. Price overrides REQUIRE maker-checker approval via K-07
5. T3 pricing models MUST run in sandboxed plugin runtime

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| D-04 Market Data | Real-time prices, historical data | D-04 stable |
| K-02 Config Engine | Pricing model configs, curve params | K-02 stable |
| K-03 Rules Engine | Pricing policy rules | K-03 stable |
| K-04 Plugin Runtime | T3 pricing model execution | K-04 stable |
| K-05 Event Bus | PriceUpdateEvent, MTMCompleteEvent | K-05 stable |
| K-07 Audit Framework | Price override audit | K-07 stable |
| K-09 AI Governance | ML-based pricing model governance | K-09 stable |
| K-15 Dual-Calendar | Settlement date calculation | K-15 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/pricing/value
Authorization: Bearer {user_token}

Request:
{
  "instrument_id": "NABIL",
  "valuation_date_bs": "2082-11-25",
  "model": "last_traded",
  "params": {}
}

Response 200:
{
  "instrument_id": "NABIL",
  "value": 1250.00,
  "currency": "NPR",
  "model_used": "last_traded",
  "confidence": 0.99,
  "valuation_date_bs": "2082-11-25",
  "valuation_date_gregorian": "2026-03-08",
  "timestamp": "2026-03-08T17:00:01Z"
}
```

```yaml
GET /api/v1/pricing/yield-curve/{curve_id}?date=2082-11-25&calendar=BS

Response 200:
{
  "curve_id": "NPR_GOVT_ZERO",
  "date_bs": "2082-11-25",
  "tenors": ["1M","3M","6M","1Y","2Y","5Y","10Y"],
  "rates": [0.045, 0.048, 0.052, 0.055, 0.058, 0.063, 0.068],
  "interpolation": "cubic_spline"
}
```

```yaml
POST /api/v1/pricing/mtm/batch
Authorization: Bearer {service_token}

Request:
{
  "portfolio_ids": ["PF-001", "PF-002"],
  "valuation_date_bs": "2082-11-25"
}

Response 202:
{
  "batch_id": "MTM-2082-11-25-001",
  "status": "PROCESSING",
  "estimated_completion": "2026-03-08T17:05:00Z"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.pricing.v1;

service PricingService {
  rpc ValueInstrument(ValuationRequest) returns (ValuationResult);
  rpc GetYieldCurve(YieldCurveRequest) returns (YieldCurve);
  rpc RunMTM(MTMRequest) returns (MTMResult);
  rpc GetModelCatalog(Empty) returns (ModelCatalog);
}
```

### 2.3 SDK

```typescript
interface PricingClient {
  value(instrumentId: string, model: string, date: DualDate): Promise<Valuation>;
  getYieldCurve(curveId: string, date: DualDate): Promise<YieldCurve>;
  runMTM(portfolioIds: string[], date: DualDate): Promise<MTMBatch>;
  registerModel(model: PricingModelDescriptor): Promise<void>;
}
```

---

## 3. DATA MODEL

```sql
CREATE TABLE pricing_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id VARCHAR(50) NOT NULL,
    valuation_date DATE NOT NULL,
    valuation_date_bs VARCHAR(20),
    model_id VARCHAR(100) NOT NULL,
    price DECIMAL(18,8) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    confidence DECIMAL(5,4),
    inputs JSONB,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE yield_curves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    curve_id VARCHAR(100) NOT NULL,
    curve_date DATE NOT NULL,
    curve_date_bs VARCHAR(20),
    tenors JSONB NOT NULL,
    rates JSONB NOT NULL,
    interpolation_method VARCHAR(50),
    source VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (curve_id, curve_date)
);

CREATE TABLE pricing_models (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    model_type VARCHAR(50) NOT NULL, -- ANALYTICAL, MONTE_CARLO, T3_PLUGIN
    asset_classes VARCHAR(255)[], 
    plugin_id VARCHAR(100),
    version VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE'
);
```

### 3.2 Event Schemas

```json
{ "event_type": "PriceUpdateEvent", "data": { "instrument_id": "NABIL", "price": 1250.00, "model": "last_traded" } }
```
```json
{ "event_type": "MTMCompleteEvent", "data": { "batch_id": "MTM-2082-11-25-001", "portfolio_count": 2, "total_instruments": 150 } }
```

---

## 4. CONTROL FLOW

### 4.1 EOD MTM Flow
```
1. Scheduler triggers MTM batch at 17:00 local
2. Load all active portfolios and positions
3. For each instrument:
   a. Select pricing model (config-driven)
   b. Fetch market data from D-04
   c. Execute model (analytical or T3 plugin)
   d. Store pricing snapshot
4. Aggregate portfolio-level MTM
5. Publish MTMCompleteEvent
6. Distribute to downstream (D-03 PMS, D-06 Risk)
```

### 4.2 Yield Curve Bootstrap
```
1. Collect market data points (cash rates, futures, swaps)
2. Strip to zero rates using bootstrapping
3. Fit curve using configured method (Nelson-Siegel / cubic spline)
4. Validate: monotonicity check, rate bounds
5. Store versioned curve snapshot
6. Publish YieldCurveUpdateEvent
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Nelson-Siegel Yield Curve
$$r(t) = \beta_0 + \beta_1 \frac{1 - e^{-t/\tau}}{t/\tau} + \beta_2 \left(\frac{1 - e^{-t/\tau}}{t/\tau} - e^{-t/\tau}\right)$$

### 5.2 Black-Scholes Option Pricing
$$C = S_0 N(d_1) - K e^{-rT} N(d_2)$$
$$d_1 = \frac{\ln(S_0/K) + (r + \sigma^2/2)T}{\sigma\sqrt{T}},\quad d_2 = d_1 - \sigma\sqrt{T}$$

---

## 6–10. NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: EOD MTM <5 min for 10K instruments; single price <50ms; 99.99% uptime
- **Security**: K-01 RBAC; price override maker-checker; T3 model sandboxing; audit log for all overrides
- **Metrics**: `pricing_mtm_duration_seconds`, `pricing_model_execution_ms`, `pricing_curve_build_ms`
- **Extension Points**: T3 pricing model plugins; T2 jurisdiction-specific valuation rules; custom curve types
- **Tests**: Black-Scholes accuracy vs reference implementation; MTM batch completeness; yield curve interpolation tests; price override audit trail verification
