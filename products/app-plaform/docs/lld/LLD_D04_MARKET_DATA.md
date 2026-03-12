# LOW-LEVEL DESIGN: D-04 MARKET DATA SERVICE

**Module**: D-04 Market Data Service  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Market Data Domain Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Market Data Service provides **feed normalization from multiple sources, L1/L2/L3 order book distribution, circuit breaker detection, feed arbitration, and historical data storage**.

**Core Responsibilities**:
- Feed normalization from multiple exchange/vendor sources
- L1 (best bid/ask), L2 (depth), L3 (full order book) distribution
- Circuit breaker detection and MarketHaltEvent emission
- Feed arbitration — primary/secondary source selection with failover
- Historical tick storage (TimescaleDB)
- Dual-calendar OHLC aggregation (daily, weekly, monthly)
- WebSocket streaming for real-time subscribers
- Feed health monitoring and stale data detection

**Invariants**:
1. Market data MUST be normalized to standard format before distribution
2. Circuit breakers MUST be detected within 500μs of exchange notification
3. Feed failover MUST complete within 1 second
4. Historical ticks MUST be retained for 10 years
5. All feed switches MUST be audited via K-07

### 1.2 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Feed configs, instrument mappings | K-02 stable |
| K-04 Plugin Runtime | T3 feed adapter execution | K-04 stable |
| K-05 Event Bus | MarketTickEvent, MarketHaltEvent | K-05 stable |
| K-07 Audit Framework | Feed switch audit | K-07 stable |
| K-15 Dual-Calendar | OHLC aggregation dates | K-15 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/market-data/snapshot/{instrument_id}
Authorization: Bearer {user_token}

Response 200:
{
  "instrument_id": "NABIL",
  "last_price": 1250.00,
  "bid": 1249.50,
  "ask": 1250.50,
  "volume": 125000,
  "open": 1245.00,
  "high": 1255.00,
  "low": 1243.00,
  "close_previous": 1248.00,
  "change_pct": 0.16,
  "circuit_breaker_status": "NORMAL",
  "timestamp_gregorian": "2026-03-08T10:30:00Z",
  "timestamp_bs": "2082-11-25"
}
```

```yaml
GET /api/v1/market-data/historical/{instrument_id}?from=2082-10-01&to=2082-11-25&interval=daily&calendar=BS

Response 200:
{
  "instrument_id": "NABIL",
  "interval": "daily",
  "data": [
    { "date_bs": "2082-10-01", "date_gregorian": "2026-01-15", "open": 1200, "high": 1210, "low": 1195, "close": 1205, "volume": 100000 }
  ]
}
```

### 2.2 WebSocket Streaming

```yaml
WS /api/v1/market-data/stream
Subscribe: { "action": "subscribe", "instruments": ["NABIL", "SBL"], "depth": "L2" }

Message:
{
  "instrument_id": "NABIL",
  "type": "L2_UPDATE",
  "bids": [{ "price": 1249.50, "quantity": 500 }, { "price": 1249.00, "quantity": 1200 }],
  "asks": [{ "price": 1250.50, "quantity": 300 }, { "price": 1251.00, "quantity": 800 }],
  "timestamp": "2026-03-08T10:30:00.001Z"
}
```

### 2.3 gRPC Service Definition

```protobuf
syntax = "proto3";
package siddhanta.marketdata.v1;

service MarketDataService {
  rpc GetSnapshot(SnapshotRequest) returns (MarketSnapshot);
  rpc Subscribe(SubscribeRequest) returns (stream MarketUpdate);
  rpc GetHistorical(HistoricalRequest) returns (HistoricalData);
  rpc GetOrderBook(OrderBookRequest) returns (OrderBook);
}
```

### 2.4 SDK

```typescript
interface MarketDataClient {
  subscribe(instruments: string[], depth: 'L1'|'L2'|'L3', handler: (update: MarketUpdate) => void): Subscription;
  getSnapshot(instrumentId: string): Promise<MarketSnapshot>;
  getHistorical(instrumentId: string, from: DualDate, to: DualDate, interval: string): Promise<OHLCData[]>;
  getOrderBook(instrumentId: string, depth: number): Promise<OrderBook>;
}
```

---

## 3. DATA MODEL

```sql
-- TimescaleDB hypertable for tick data
CREATE TABLE market_ticks (
    instrument_id VARCHAR(50) NOT NULL,
    price DECIMAL(18,8) NOT NULL,
    quantity INTEGER,
    side VARCHAR(10),
    venue VARCHAR(100),
    tick_time TIMESTAMPTZ NOT NULL
);
SELECT create_hypertable('market_ticks', 'tick_time');

CREATE TABLE daily_ohlc (
    instrument_id VARCHAR(50) NOT NULL,
    date_gregorian DATE NOT NULL,
    date_bs VARCHAR(20),
    open_price DECIMAL(18,8),
    high_price DECIMAL(18,8),
    low_price DECIMAL(18,8),
    close_price DECIMAL(18,8),
    volume BIGINT,
    vwap DECIMAL(18,8),
    PRIMARY KEY (instrument_id, date_gregorian)
);
```

### 3.2 Event Schemas

```json
{ "event_type": "MarketTickEvent", "data": { "instrument_id": "NABIL", "price": 1250.00, "quantity": 100, "venue": "NEPSE" } }
```
```json
{ "event_type": "MarketHaltEvent", "data": { "instrument_id": "NABIL", "halt_type": "CIRCUIT_BREAKER_UPPER", "halt_price": 1300.00, "venue": "NEPSE" } }
```
```json
{ "event_type": "FeedSwitchEvent", "data": { "instrument_id": "NABIL", "from_source": "primary", "to_source": "secondary", "reason": "STALE_DATA" } }
```

---

## 4. CONTROL FLOW

### 4.1 Feed Normalization
```
1. T3 Feed Adapter receives raw data from exchange/vendor
2. Normalize to standard MarketTick format
3. Validate: price > 0, quantity >= 0, valid instrument
4. Store in market_ticks (TimescaleDB)
5. Publish MarketTickEvent to K-05
6. Push to WebSocket subscribers
```

### 4.2 Feed Arbitration
```
function selectFeed(instrument):
  primary = feeds[instrument].primary
  if primary.isHealthy and primary.staleness < 5s:
    return primary
  else:
    secondary = feeds[instrument].secondary
    publish FeedSwitchEvent
    return secondary
```

---

## 5–10. ALGORITHMS, NFR, SECURITY, OBSERVABILITY, EXTENSIBILITY, TESTS

- **NFR**: <500μs ingest-to-publish; 100K ticks/s; 99.999% availability
- **Security**: K-01 RBAC; feed credentials via K-14; T3 adapter sandboxed
- **Metrics**: `market_data_ticks_per_second`, `market_data_feed_latency_us`, `market_data_stale_feeds`
- **Extension Points**: T3 Feed Adapter plugins for new exchanges/vendors; T1 instrument mappings
- **Tests**: Feed normalization correctness; circuit breaker detection latency; feed failover <1s; OHLC aggregation accuracy; historical query performance
