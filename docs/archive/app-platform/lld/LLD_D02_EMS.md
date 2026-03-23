# LOW-LEVEL DESIGN: D-02 EXECUTION MANAGEMENT SYSTEM (EMS)

**Module**: D-02 Execution Management System  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Trading Domain Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Execution Management System provides **smart order routing, order slicing (VWAP/TWAP algorithms), venue connectivity through T3 exchange adapters, transaction cost analysis (TCA) capture, and circuit breaker handling**.

**Core Responsibilities**:
- Smart order routing to optimal execution venues
- Order slicing with algorithmic strategies (VWAP, TWAP, ICEBERG)
- T3 exchange adapter orchestration for venue connectivity
- `TradeExecuted` event emission on every fill
- Circuit breaker detection and trading halt enforcement
- Transaction cost analysis (TCA) capture per fill
- Partial fill aggregation and reporting
- Dual-calendar timestamps on all executions

**Invariants**:
1. All executions MUST emit `TradeExecuted` events via K-05
2. Circuit breaker MUST halt routing immediately on detection
3. TCA data MUST be captured per fill for regulatory reporting
4. All routing decisions MUST be audited via K-07
5. Exchange adapter failures MUST trigger K-18 circuit breaker

### 1.2 Explicit Non-Goals

- ❌ Order lifecycle management (D-01 OMS)
- ❌ Market data feed management (D-04 Market Data)
- ❌ Post-trade settlement (D-09 Post-Trade)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| D-01 OMS | Order source, execution updates | D-01 stable |
| D-04 Market Data | Venue pricing for routing decisions | D-04 stable |
| K-02 Config Engine | Routing rules, venue configs | K-02 stable |
| K-03 Rules Engine | Pre-execution validation | K-03 stable |
| K-04 Plugin Runtime | T3 exchange adapter execution | K-04 stable |
| K-05 Event Bus | TradeExecuted events | K-05 stable |
| K-07 Audit Framework | Execution audit trail | K-07 stable |
| K-15 Dual-Calendar | Dual timestamps on executions | K-15 stable |
| K-18 Resilience | Circuit breaker for venues | K-18 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/executions/route
Authorization: Bearer {service_token}
Content-Type: application/json

Request:
{
  "order_id": "ord_7a8b9c0d",
  "instrument_id": "NABIL",
  "side": "BUY",
  "quantity": 1000,
  "strategy": "VWAP",
  "strategy_params": {
    "start_time": "10:00:00",
    "end_time": "14:00:00",
    "participation_rate": 0.15
  },
  "venue_preference": ["NEPSE"],
  "urgency": "MEDIUM"
}

Response 202:
{
  "execution_id": "exec_abc123",
  "status": "ROUTING",
  "slices": 5,
  "estimated_completion": "14:00:00",
  "created_at_bs": "2082-11-25",
  "created_at_gregorian": "2026-03-08T10:00:00Z"
}
```

```yaml
GET /api/v1/executions/{execution_id}/tca
Authorization: Bearer {user_token}

Response 200:
{
  "execution_id": "exec_abc123",
  "order_id": "ord_7a8b9c0d",
  "instrument_id": "NABIL",
  "side": "BUY",
  "total_quantity": 1000,
  "filled_quantity": 1000,
  "average_price": 1248.75,
  "vwap_benchmark": 1250.00,
  "implementation_shortfall_bps": -10.0,
  "total_commission": 125.00,
  "market_impact_bps": 5.2,
  "fills": [
    { "fill_id": "fill_1", "quantity": 200, "price": 1247.00, "venue": "NEPSE", "timestamp_gregorian": "2026-03-08T10:15:00Z" },
    { "fill_id": "fill_2", "quantity": 300, "price": 1248.50, "venue": "NEPSE", "timestamp_gregorian": "2026-03-08T11:00:00Z" }
  ]
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.ems.v1;

service ExecutionManagementService {
  rpc RouteOrder(RouteOrderRequest) returns (RouteOrderResponse);
  rpc CancelExecution(CancelExecutionRequest) returns (CancelExecutionResponse);
  rpc GetExecutionStatus(GetStatusRequest) returns (ExecutionStatus);
  rpc GetTCA(GetTCARequest) returns (TCAReport);
  rpc ListExecutions(ListExecutionsRequest) returns (ListExecutionsResponse);
}
```

### 2.3 SDK Method Signatures

```typescript
interface EMSClient {
  routeOrder(request: RouteOrderRequest): Promise<ExecutionResponse>;
  cancelExecution(executionId: string, reason: string): Promise<void>;
  getExecutionStatus(executionId: string): Promise<ExecutionStatus>;
  getTCA(executionId: string): Promise<TCAReport>;
  listExecutions(filters: ExecutionFilters): Promise<Execution[]>;
}
```

---

## 3. DATA MODEL

### 3.1 Storage Tables

```sql
CREATE TABLE executions (
    execution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('BUY','SELL')),
    total_quantity INTEGER NOT NULL,
    filled_quantity INTEGER DEFAULT 0,
    strategy VARCHAR(50) NOT NULL CHECK (strategy IN ('MARKET','VWAP','TWAP','ICEBERG','SOR')),
    strategy_params JSONB DEFAULT '{}',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    venue_preference TEXT[],
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_at_bs VARCHAR(20),
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE fills (
    fill_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES executions(execution_id),
    venue VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(18,8) NOT NULL,
    commission DECIMAL(18,8) DEFAULT 0,
    exchange_order_id VARCHAR(255),
    filled_at TIMESTAMPTZ NOT NULL,
    filled_at_bs VARCHAR(20)
);
CREATE INDEX idx_fills_execution ON fills(execution_id);

CREATE TABLE tca_records (
    tca_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES executions(execution_id),
    vwap_benchmark DECIMAL(18,8),
    twap_benchmark DECIMAL(18,8),
    arrival_price DECIMAL(18,8),
    average_price DECIMAL(18,8),
    implementation_shortfall_bps DECIMAL(10,4),
    market_impact_bps DECIMAL(10,4),
    total_commission DECIMAL(18,8),
    calculated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 3.2 Event Schemas

```json
{
  "event_type": "TradeExecuted",
  "event_version": "1.0.0",
  "data": {
    "execution_id": "exec_abc123",
    "order_id": "ord_7a8b9c0d",
    "fill_id": "fill_1",
    "instrument_id": "NABIL",
    "side": "BUY",
    "quantity": 200,
    "price": 1247.00,
    "venue": "NEPSE",
    "commission": 12.47,
    "filled_at_bs": "2082-11-25",
    "filled_at_gregorian": "2026-03-08T10:15:00Z"
  }
}
```

```json
{ "event_type": "RoutingFailed", "data": { "execution_id": "exec_abc123", "reason": "CIRCUIT_BREAKER", "venue": "NEPSE" } }
```

```json
{ "event_type": "OrderSliced", "data": { "execution_id": "exec_abc123", "slices": 5, "strategy": "VWAP" } }
```

---

## 4. CONTROL FLOW

### 4.1 Smart Order Routing Flow
```
1. Receive RouteOrder request from D-01
2. Pre-execution validation via K-03
3. Select strategy (VWAP/TWAP/ICEBERG/SOR)
4. Slice order into child orders based on strategy
5. Publish OrderSliced event
6. For each slice:
   a. Select best venue (price, liquidity, latency)
   b. Route to venue via T3 Exchange Adapter (K-04)
   c. Await fill confirmation from adapter
   d. Record fill in fills table
   e. Publish TradeExecuted event
   f. Update D-01 with fill notification
7. Calculate TCA on completion
8. Log all routing decisions to K-07
```

### 4.2 Circuit Breaker Handling
```
1. D-04 Market Data publishes MarketHaltEvent
2. EMS receives halt event
3. Cancel all pending slices for halted instrument
4. Notify D-01 of execution suspension
5. Resume routing when MarketResumedEvent received
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 VWAP Algorithm
```
function calculateVWAPSlices(order, startTime, endTime, participationRate):
  historicalVolume = D04.getHistoricalVolume(order.instrument, 20)  // 20-day avg
  timeSlots = divideTimeRange(startTime, endTime, slotDuration=5min)
  for each slot in timeSlots:
    expectedVolume = historicalVolume.getVolumeProfile(slot) * participationRate
    sliceQty = min(expectedVolume, order.remainingQuantity)
    yield { time: slot, quantity: sliceQty }
```

### 5.2 TCA Calculation
```
function calculateTCA(execution, fills):
  avgPrice = weightedAverage(fills.price, fills.quantity)
  vwapBenchmark = D04.getPeriodVWAP(instrument, execution.startTime, execution.endTime)
  arrivalPrice = D04.getPriceAt(instrument, execution.startTime)
  implShortfall = (avgPrice - arrivalPrice) / arrivalPrice * 10000  // bps
  marketImpact = (avgPrice - vwapBenchmark) / vwapBenchmark * 10000  // bps
  return { avgPrice, vwapBenchmark, implShortfall, marketImpact, totalCommission }
```

---

## 6. NFR BUDGETS

| Operation | P99 Latency | Throughput | Timeout |
|-----------|-------------|------------|---------|
| routeOrder() | 5ms | 50,000/s | 1000ms |
| Venue routing (per slice) | 1ms | 100,000/s | 500ms |
| getTCA() | 10ms | 10,000/s | 1000ms |
| Fill processing | 0.5ms | 200,000/s | 100ms |

**Availability**: 99.999%

---

## 7. SECURITY DESIGN

- **Access Control**: K-01 RBAC — only authorized trading services can route
- **Audit**: Every routing decision and fill logged to K-07
- **T3 Adapter Isolation**: Exchange adapters run in K-04 sandbox
- **Encryption**: Venue credentials managed via K-14

---

## 8. OBSERVABILITY & AUDIT

### Metrics
- `ems_executions_total` — Executions by strategy/status
- `ems_fill_latency_seconds` — Fill confirmation latency by venue
- `ems_tca_shortfall_bps` — Implementation shortfall distribution
- `ems_circuit_breaker_activations_total` — Circuit breaker events

---

## 9. EXTENSIBILITY & EVOLUTION

### Extension Points
- `ExchangeAdapter` interface (T3 plugins for new venues)
- T2 routing Rule Packs for jurisdiction-specific best execution rules
- Custom algorithmic strategies (T3)

---

## 10. TEST PLAN

### Unit Tests
- VWAP/TWAP slice calculation
- TCA calculation with various fill scenarios
- Circuit breaker handling (halt → cancel → resume)
- Venue selection logic

### Integration Tests
- End-to-end order routing via T3 exchange adapter
- Fill event publication to K-05
- D-01 notification on fill
- K-07 audit trail for routing decisions

### Performance Tests
- 50K TPS routing throughput
- Fill processing at 200K/s
- VWAP slicing for 10K concurrent orders
