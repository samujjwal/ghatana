# MILESTONE 2A — DOMAIN TRADING FOUNDATION

## Sprints 5–9 | 125 Stories | D-11, D-04, D-01, D-07, D-14, D-06, D-02

> **Story Template**: Each story includes ID, title, feature ref, points, sprint, team, description, Given/When/Then ACs, key tests, and dependencies.

---

# EPIC D-11: REFERENCE DATA SERVICE (13 Stories)

## Feature D11-F01 — Instrument Master Data Management (3 Stories)

---

### STORY-D11-001: Create instrument master schema and CRUD API

**Feature**: D11-F01 · **Points**: 5 · **Sprint**: 5 · **Team**: Gamma

Create instrument_master table: id UUID PK, symbol VARCHAR UNIQUE(symbol, exchange), isin VARCHAR, name VARCHAR, type ENUM(EQUITY, BOND, MF, DERIVATIVE, ETF), status ENUM(ACTIVE, SUSPENDED, DELISTED, PENDING_APPROVAL), sector VARCHAR, lot_size INT, tick_size DECIMAL, currency VARCHAR(3), exchange VARCHAR, effective_from DATE, effective_to DATE, created_at_utc, created_at_bs, metadata JSONB. Implement REST CRUD: GET /instruments, GET /instruments/:id, POST /instruments (creates in PENDING_APPROVAL), PUT /instruments/:id (maker-checker required). Redis cache layer with 5-min TTL for hot lookups.

**ACs**:

1. Given POST /instruments with valid data, When created, Then status = PENDING_APPROVAL, maker-checker workflow initiated
2. Given GET /instruments/:id for cached instrument, When queried, Then response < 1ms from Redis
3. Given instrument with duplicate (symbol+exchange), When POST attempted, Then 409 Conflict returned

**Tests**: create_valid_pending · get_cached_sub1ms · get_uncached_fromDb · duplicate_symbol_exchange_409 · update_requiresMakerChecker · delete_softDelete · perf_lookup_1ms

**Dependencies**: K-01, K-02, K-05, K-07

---

### STORY-D11-002: Implement temporal validity and point-in-time queries

**Feature**: D11-F01 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Add temporal validity support: every instrument record has effective_from and effective_to. Implement GET /instruments/:id?as_of=2081-06-15 query returning data valid at that BS date. Historical records preserved via SCD Type 2 (new row per change, close previous effective_to). Dual-calendar as_of parameter accepts both Gregorian and BS formats.

**ACs**:

1. Given instrument changed on 2081-03-01, When as_of=2081-02-15, Then returns pre-change version
2. Given as_of in BS format, When queried, Then K-15 converts to Gregorian for lookup
3. Given no as_of parameter, When queried, Then returns currently effective version

**Tests**: asOf_preChange_returnsOld · asOf_postChange_returnsNew · asOf_bsFormat_converts · asOf_noParam_current · scd2_newRow_closesOld

**Dependencies**: STORY-D11-001, K-15

---

### STORY-D11-003: Implement instrument status lifecycle and event emission

**Feature**: D11-F01 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Implement instrument status transitions: PENDING_APPROVAL → ACTIVE → SUSPENDED → ACTIVE, ACTIVE → DELISTED. Each transition emits domain event (InstrumentApproved, InstrumentSuspended, InstrumentDelisted, InstrumentReactivated) to K-05. Suspension must propagate notification to OMS/EMS to halt trading.

**ACs**:

1. Given instrument approved, When status → ACTIVE, Then InstrumentApproved event emitted
2. Given instrument suspended, When InstrumentSuspended emitted, Then OMS/EMS consumers notified
3. Given invalid transition (DELISTED → ACTIVE), When attempted, Then rejected with INVALID_TRANSITION

**Tests**: approve_emitsEvent · suspend_emitsEvent · delist_emitsEvent · reactivate_emitsEvent · invalidTransition_rejected · consumer_omsNotified

**Dependencies**: STORY-D11-001, K-05

## Feature D11-F02 — Entity Master (2 Stories)

---

### STORY-D11-004: Implement entity master data management

**Feature**: D11-F02 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Create entity_master table: id, entity_type ENUM(ISSUER, BROKER, CUSTODIAN, EXCHANGE, REGULATOR, BANK), name, short_name, registration_number, country, status, metadata JSONB, effective_from, effective_to. CRUD API: GET/POST/PUT /entities with maker-checker. Entity-to-instrument relationships (issuer → equity). Redis-cached lookups.

**ACs**:

1. Given POST /entities with valid data, When created, Then entity stored with PENDING_APPROVAL status
2. Given entity linked to instruments, When GET /entities/:id?include=instruments, Then related instruments returned
3. Given entity name search, When GET /entities?q=nepal, Then full-text search returns matching entities

**Tests**: create_entity_valid · get_withInstruments · search_fullText · update_makerChecker · entity_instrumentLink

**Dependencies**: STORY-D11-001, K-01

---

### STORY-D11-005: Implement entity relationship graph

**Feature**: D11-F02 · **Points**: 2 · **Sprint**: 5 · **Team**: Gamma

Build entity relationship model: entity_relationships table (parent_entity_id, child_entity_id, relationship_type ENUM(SUBSIDIARY, CUSTODIAN_FOR, MARKET_MAKER, ISSUER_OF), effective_from, effective_to). API: GET /entities/:id/relationships. Graph traversal for beneficial ownership queries. EntityRelationshipChanged events.

**ACs**:

1. Given entity A is subsidiary of entity B, When GET /entities/A/relationships, Then relationship with B returned
2. Given relationship with effective_to in past, When queried as_of=today, Then expired relationship excluded
3. Given relationship change, When saved, Then EntityRelationshipChanged event emitted

**Tests**: relationship_create · relationship_query · relationship_expired_excluded · relationship_graph_traversal · relationship_event

**Dependencies**: STORY-D11-004

## Feature D11-F03 — Benchmark/Index Definitions (2 Stories)

---

### STORY-D11-006: Implement benchmark and index data model

**Feature**: D11-F03 · **Points**: 2 · **Sprint**: 5 · **Team**: Gamma

Create benchmark_definitions table: id, name (e.g., NEPSE Index, NEPSE Sensitive), type ENUM(PRICE_INDEX, TOTAL_RETURN, SECTOR), base_date, base_value, currency, calculation_method, status. Create benchmark_constituents table: benchmark_id, instrument_id, weight, effective_from, effective_to. CRUD API: GET/POST /benchmarks.

**ACs**:

1. Given POST /benchmarks with constituents, When created, Then constituent weights sum to 1.0 (±0.001 tolerance)
2. Given benchmark query, When GET /benchmarks/:id?include=constituents, Then all active constituents with weights returned
3. Given constituent weight change, When updated, Then previous weight preserved with effective_to

**Tests**: create_benchmark_valid · constituents_sumTo1 · get_withConstituents · weight_update_preservesHistory · invalid_weights_rejected

**Dependencies**: STORY-D11-001

---

### STORY-D11-007: Implement benchmark value history and calculation

**Feature**: D11-F03 · **Points**: 2 · **Sprint**: 5 · **Team**: Gamma

Create benchmark_values table: benchmark_id, date_utc, date_bs, open_value, high_value, low_value, close_value, volume. Ingest benchmark values from market data feed and manual entry. Calculate daily return. API: GET /benchmarks/:id/values?from=&to=.

**ACs**:

1. Given benchmark values for date range, When queried, Then all values returned in chronological order
2. Given Manual entry, When admin submits value via maker-checker, Then value stored with source=MANUAL
3. Given daily close, When return calculated, Then return = (close_today - close_yesterday) / close_yesterday

**Tests**: values_query_range · values_manual_entry · values_return_calculation · values_ingestion_fromFeed

**Dependencies**: STORY-D11-006

## Feature D11-F04 — External Feed Adapters (2 Stories)

---

### STORY-D11-008: Implement T3 reference data feed adapter framework

**Feature**: D11-F04 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Build T3 plugin adapter framework for external reference data feeds. Define RefDataFeedAdapter interface: connect(), disconnect(), fetchInstruments(), fetchEntities(), onUpdate(callback). Plugin receives sandbox capabilities (network access to feed URL only). Adapter registry: register/deregister adapters. Scheduled sync (configurable interval, default hourly).

**ACs**:

1. Given T3 adapter registered, When sync triggers, Then fetchInstruments() called, results merged with master
2. Given adapter returns new instrument not in master, When processed, Then instrument created in PENDING_APPROVAL
3. Given adapter network error, When sync fails, Then error logged, retry scheduled, alert if 3 consecutive failures

**Tests**: adapter_register · adapter_sync_merges · adapter_newInstrument_pending · adapter_networkError_retry · adapter_deregister · adapter_sandbox_noExfiltration

**Dependencies**: K-04

---

### STORY-D11-009: Implement NEPSE/CDSC reference data adapter

**Feature**: D11-F04 · **Points**: 2 · **Sprint**: 5 · **Team**: Gamma

Implement concrete T3 adapter for NEPSE (Nepal Stock Exchange) and CDSC (Central Depository System). Parse NEPSE instrument list (symbol, sector, lot_size), parse CDSC depository data (ISIN mapping). Map to instrument_master schema. Daily sync at market open - 30 minutes.

**ACs**:

1. Given NEPSE data available, When daily sync runs, Then all listed instruments updated in instrument_master
2. Given CDSC ISIN data, When mapped, Then instrument.isin populated correctly
3. Given new listing on NEPSE, When detected, Then InstrumentCreated event emitted for review

**Tests**: nepse_sync_updatesAll · cdsc_isin_mapping · newListing_detected · sync_schedule_preMarket · parse_error_graceful

**Dependencies**: STORY-D11-008

## Feature D11-F05 — Point-in-Time Historical Queries (2 Stories)

---

### STORY-D11-010: Implement reference data snapshot service

**Feature**: D11-F05 · **Points**: 2 · **Sprint**: 5 · **Team**: Gamma

Implement daily snapshot of all reference data (instruments, entities, benchmarks) taken at EOD. Snapshots stored in PostgreSQL partitioned table. API: GET /snapshots/:date for complete reference data state at that date. Supports regulatory reporting "as-of" requirements.

**ACs**:

1. Given EOD trigger, When snapshot taken, Then all instruments, entities, benchmarks serialized to snapshot table
2. Given GET /snapshots/2081-06-15, When queried, Then returns complete reference data state at that BS date
3. Given no snapshot for requested date, When queried, Then returns nearest earlier snapshot with flag

**Tests**: snapshot_eod_captures · snapshot_query_byDate · snapshot_missing_nearestEarlier · snapshot_partitioning_byMonth

**Dependencies**: STORY-D11-001, STORY-D11-004, STORY-D11-006

---

### STORY-D11-011: Implement change audit trail for reference data

**Feature**: D11-F05 · **Points**: 2 · **Sprint**: 5 · **Team**: Gamma

Track all reference data changes with full audit: who changed what, when, old/new values. GET /instruments/:id/history returns full change history. GET /entities/:id/history similarly. Integrates with K-07 audit framework. Supports regulatory inquiries: "show me all changes to instrument X in the last 90 days".

**ACs**:

1. Given instrument updated, When change recorded, Then old_value, new_value, actor, timestamp in audit trail
2. Given GET /instruments/:id/history?days=90, When queried, Then all changes within 90 days returned
3. Given change by system adapter, When recorded, Then actor = adapter_name, source = EXTERNAL

**Tests**: change_recorded_onUpdate · history_query_byDays · history_systemActor · history_export_csv · audit_k07_integration

**Dependencies**: STORY-D11-001, K-07

---

# EPIC D-04: MARKET DATA SERVICE (15 Stories)

## Feature D04-F01 — Feed Normalization Engine (3 Stories)

---

### STORY-D04-001: Design normalized market data schema and write path

**Feature**: D04-F01 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Design unified market data schema: MarketTick { instrument_id, timestamp_utc, timestamp_bs, bid, ask, last, volume, open, high, low, close, source, sequence }. Create normalized_ticks table in TimescaleDB with hypertable compression. Write API: ingestTick(tick) → validates, normalizes, stores. Topic: siddhanta.marketdata.ticks.

**ACs**:

1. Given raw tick from feed, When ingested, Then normalized MarketTick stored in TimescaleDB
2. Given tick with missing bid/ask, When ingested, Then fills from last known values (stale data flagged)
3. Given duplicate sequence from same source, When ingested, Then deduplicated (idempotent)

**Tests**: ingest_valid_stores · ingest_missingFields_fills · ingest_duplicate_idempotent · ingest_invalidInstrument_rejects · perf_50kTicksPerSec

**Dependencies**: K-05, D-11, TimescaleDB provisioned

---

### STORY-D04-002: Implement multi-source feed normalization

**Feature**: D04-F01 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Support multiple market data feed sources (primary: NEPSE live, secondary: delayed feed, tertiary: manual). Each source has adapter (T3 plugin). Normalization: map vendor-specific schemas to unified MarketTick. Source priority configuration via K-02 (primary > secondary > manual). Feed adapter interface: connect(), subscribe(instruments), onTick(callback), disconnect().

**ACs**:

1. Given primary and secondary feeds, When both provide ticks, Then primary tick used, secondary discarded
2. Given primary feed down, When secondary provides tick, Then secondary tick used with source flag
3. Given vendor-specific field names, When normalized, Then mapped to unified schema correctly

**Tests**: multiSource_primaryWins · multiSource_failover · normalization_vendorMapping · adapter_connect · adapter_subscribe · adapter_disconnect

**Dependencies**: STORY-D04-001, K-04

---

### STORY-D04-003: Implement tick validation and anomaly detection

**Feature**: D04-F01 · **Points**: 2 · **Sprint**: 5 · **Team**: Gamma

Validate incoming ticks: price within ±20% of last close (configurable), volume non-negative, timestamp within acceptable window (no future ticks, no stale > 5min). Anomalous ticks flagged but not discarded (stored with anomaly_flag). Alert emitted for repeated anomalies. AnomalyDetected event to K-05.

**ACs**:

1. Given tick with price 50% above last close, When validated, Then stored with anomaly_flag = PRICE_SPIKE
2. Given tick with future timestamp, When validated, Then rejected with FUTURE_TIMESTAMP error
3. Given 5 consecutive anomalous ticks, When detected, Then alert emitted to observability

**Tests**: valid_tick_passes · price_spike_flagged · future_timestamp_rejected · stale_tick_flagged · consecutive_anomalies_alert · volume_negative_rejected

**Dependencies**: STORY-D04-001

## Feature D04-F02 — L1/L2/L3 Order Book Distribution (3 Stories)

---

### STORY-D04-004: Implement L1 top-of-book distribution

**Feature**: D04-F02 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Implement Level 1 (top-of-book) data distribution: best bid, best ask, last trade price, volume. Maintain in-memory L1 cache per instrument. Update on every tick. Publish L1 snapshots to Kafka topic siddhanta.marketdata.l1. REST API: GET /marketdata/l1/:instrumentId for current snapshot.

**ACs**:

1. Given new tick, When L1 updated, Then best bid/ask/last/volume reflected within 1ms
2. Given GET /marketdata/l1/:instrumentId, When queried, Then returns current L1 snapshot
3. Given subscriber on Kafka L1 topic, When tick arrives, Then L1 update published within 5ms

**Tests**: l1_update_onTick · l1_rest_query · l1_kafka_publish · l1_cache_consistency · perf_l1_update_sub1ms

**Dependencies**: STORY-D04-001

---

### STORY-D04-005: Implement L2 depth-of-book aggregation

**Feature**: D04-F02 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Implement Level 2 (depth-of-book): aggregated order book with top N price levels (N configurable, default 10). Maintain in-memory order book per instrument. Update on order book change events. Publish to siddhanta.marketdata.l2. REST: GET /marketdata/l2/:instrumentId.

**ACs**:

1. Given order book changes, When L2 aggregated, Then top 10 bid/ask levels with quantities shown
2. Given L2 query, When GET /marketdata/l2/:instrumentId, Then current depth snapshot returned
3. Given instrument with < 10 levels, When queried, Then available levels returned (no padding)

**Tests**: l2_aggregate_top10 · l2_rest_query · l2_lessThan10_levels · l2_kafka_publish · l2_update_onOrderBookChange

**Dependencies**: STORY-D04-004

---

### STORY-D04-006: Implement L3 full order book feed

**Feature**: D04-F02 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Implement Level 3 (full order book): individual order-level data for subscribers requiring full transparency. L3 stream published to separate Kafka topic siddhanta.marketdata.l3 (high bandwidth). Rate-limited to authorized subscribers. Includes order ID, price, quantity, side, timestamp for each book entry.

**ACs**:

1. Given L3 subscriber authorized, When order book event occurs, Then individual order entry streamed
2. Given unauthorized subscriber attempt, When subscribing to L3, Then 403 returned
3. Given L3 topic, When high volume, Then backpressure applied (consumer group lag monitored)

**Tests**: l3_stream_individual_orders · l3_unauthorized_403 · l3_backpressure · l3_rate_limit

**Dependencies**: STORY-D04-005, K-01

## Feature D04-F03 — Feed Arbitration (2 Stories)

---

### STORY-D04-007: Implement feed arbitration with primary/secondary failover

**Feature**: D04-F03 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Implement feed arbitration engine: monitor primary feed health (heartbeat, staleness). If primary feed stale > configurable threshold (default 10s), automatically switch to secondary. When primary recovers, switch back after 60s stability window. State transitions: PRIMARY → SECONDARY → PRIMARY. Emit FeedFailover/FeedRecovery events.

**ACs**:

1. Given primary feed stale > 10s, When detected, Then automatic failover to secondary, FeedFailover event emitted
2. Given primary recovers, When stable for 60s, Then switch back, FeedRecovery event emitted
3. Given both feeds down, When detected, Then MarketDataUnavailable alert emitted, last known data served with STALE flag

**Tests**: failover_primaryStale · failover_recovery · failover_bothDown_alert · failover_stateTransitions · failover_stabilityWindow

**Dependencies**: STORY-D04-002

---

### STORY-D04-008: Implement feed quality metrics and monitoring

**Feature**: D04-F03 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Track feed quality metrics per source: tick_rate (ticks/sec), latency (feed → store), gap_count (missing sequences), error_rate. Expose via Prometheus: marketdata_tick_rate, marketdata_latency_ms, marketdata_gaps_total, marketdata_errors_total. Grafana dashboard: feed health, latency histogram, gap alerting.

**ACs**:

1. Given feed producing ticks, When metrics collected, Then tick_rate, latency, gaps exposed in Prometheus
2. Given feed gap detected (missing sequence), When gap_count increments, Then alert emitted if > 10 gaps/minute
3. Given Grafana dashboard, When viewed, Then realtime feed health for all sources displayed

**Tests**: metrics_tickRate · metrics_latency · metrics_gaps_detected · metrics_errors · dashboard_renders

**Dependencies**: STORY-D04-007, K-06

## Feature D04-F04 — Circuit Breaker Detection (2 Stories)

---

### STORY-D04-009: Implement market circuit breaker detection

**Feature**: D04-F04 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Detect exchange-level circuit breakers: price limit hit, trading halt, market-wide circuit breaker (NEPSE: ±10% individual, ±5% index). Parse circuit breaker signals from feed. Emit MarketHalted/MarketResumed events to K-05. Update instrument status to HALTED in instrument cache. Notify OMS/EMS to stop routing orders for halted instruments.

**ACs**:

1. Given instrument hits ±10% limit, When circuit breaker detected, Then MarketHalted event emitted
2. Given market-wide circuit breaker, When index drops > 5%, Then all instruments marked HALTED
3. Given trading resumed signal, When detected, Then MarketResumed event emitted, instruments reactivated

**Tests**: individual_circuitBreaker · marketWide_circuitBreaker · resume_signal · omsNotified · instrumentCache_halted

**Dependencies**: STORY-D04-001, K-05

---

### STORY-D04-010: Implement trading session management

**Feature**: D04-F04 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Track market trading sessions: PRE_OPEN, OPEN_AUCTION, CONTINUOUS_TRADING, CLOSE_AUCTION, POST_CLOSE, CLOSED. Session schedule from K-02 config with K-15 holiday calendar awareness. Emit SessionStateChanged events. Reject order routing during non-trading sessions.

**ACs**:

1. Given market opens at 11:00 NPT, When session transitions, Then SessionStateChanged(CONTINUOUS_TRADING) emitted
2. Given holiday per K-15 calendar, When date is holiday, Then session never transitions from CLOSED
3. Given OMS queries session state, When CLOSED, Then orders rejected with MARKET_CLOSED

**Tests**: session_open · session_close · session_holiday · session_schedule · session_omsRejection · session_transition_events

**Dependencies**: K-02, K-15

## Feature D04-F05 — Historical Tick Storage (3 Stories)

---

### STORY-D04-011: Implement TimescaleDB historical tick storage

**Feature**: D04-F05 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Configure TimescaleDB hypertable for tick storage with automated compression (compress chunks older than 7 days). Retention policy: raw ticks kept for 1 year, compressed for 5 years. OHLCV continuous aggregates at 1-min, 5-min, 15-min, 1-hour, 1-day intervals. API: GET /marketdata/history/:instrumentId?interval=1h&from=&to=.

**ACs**:

1. Given 6 months of tick data, When 1-hour OHLCV queried, Then aggregated candles returned within 50ms
2. Given data older than 7 days, When compression runs, Then storage reduced by ~90%
3. Given data older than 1 year, When retention policy runs, Then raw ticks purged, aggregates retained

**Tests**: ohlcv_1min · ohlcv_1hour · ohlcv_1day · compression_reduces90pct · retention_purgesOld · query_performance_sub50ms

**Dependencies**: STORY-D04-001, TimescaleDB provisioned

---

### STORY-D04-012: Implement tick data backfill and gap recovery

**Feature**: D04-F05 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Implement historical data backfill: bulk import historical ticks from CSV/exchange dump. Gap detection: identify missing tick sequences per day per instrument. Gap recovery: request missing data from secondary feed or manual import. BackfillCompleted event emitted.

**ACs**:

1. Given CSV with 1M historical ticks, When bulk import runs, Then all ticks stored in TimescaleDB within 60s
2. Given gap in tick sequence, When gap detected, Then GapDetected alert raised with instrument, date, range
3. Given gap recovery data, When imported, Then gap marked as resolved

**Tests**: backfill_csv_1M · gap_detection · gap_recovery · backfill_idempotent · backfill_event

**Dependencies**: STORY-D04-011

---

### STORY-D04-013: Implement end-of-day data reconciliation

**Feature**: D04-F05 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

EOD reconciliation: compare stored tick data against official exchange EOD data (closing price, volume, OHLC). Flag discrepancies. Reconciliation report: per-instrument match/mismatch for each field. Emit ReconciliationCompleted event. Admin UI for manual resolution of breaks.

**ACs**:

1. Given official EOD data, When reconciled, Then each instrument's close/volume compared and match status recorded
2. Given mismatch in closing price, When detected, Then break flagged, ReconciliationBreak alert emitted
3. Given admin resolves break manually, When resolved, Then break marked RESOLVED with reason

**Tests**: recon_allMatch · recon_priceMismatch_flagged · recon_admin_resolve · recon_report_generation · recon_event

**Dependencies**: STORY-D04-011, D-11

## Feature D04-F06 — WebSocket Streaming (2 Stories)

---

### STORY-D04-014: Implement WebSocket streaming gateway

**Feature**: D04-F06 · **Points**: 3 · **Sprint**: 5 · **Team**: Gamma

Build WebSocket gateway for real-time market data distribution. Client connection: WSS /ws/marketdata. Subscribe: { action: "subscribe", instruments: ["NABIL", "NLIC"], levels: ["L1"] }. Unsubscribe: { action: "unsubscribe", instruments: [...] }. Server-Sent: tick updates to subscribed clients. Connection-level auth (JWT in query param or first message). Max 100 subscriptions per connection.

**ACs**:

1. Given authenticated client connects, When subscribes to NABIL L1, Then receives L1 updates within 10ms of tick arrival
2. Given > 100 subscriptions attempted, When 101st subscribe sent, Then error: MAX_SUBSCRIPTIONS_EXCEEDED
3. Given invalid JWT, When connection attempted, Then 401 and connection closed

**Tests**: ws_connect_auth · ws_subscribe_l1 · ws_unsubscribe · ws_maxSubscriptions · ws_invalidAuth_401 · ws_reconnect · perf_10k_concurrent_connections

**Dependencies**: K-01, STORY-D04-004

---

### STORY-D04-015: Implement WebSocket backpressure and client management

**Feature**: D04-F06 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Handle slow consumers: if client's WebSocket send buffer > 1000 messages, apply backpressure (skip intermediate ticks, send latest snapshot). Track per-client metrics: connection duration, messages sent, buffer size. Admin API: GET /admin/websocket/connections for monitoring. Graceful disconnect on client inactivity > 30 minutes.

**ACs**:

1. Given slow consumer, When buffer > 1000, Then skip intermediate ticks, send latest L1 snapshot
2. Given client inactive > 30 min, When timeout, Then connection closed with reason INACTIVITY
3. Given admin queries connections, When GET /admin/websocket/connections, Then all active connections with metrics

**Tests**: backpressure_skipOld · inactivity_timeout · admin_connections_list · client_metrics · graceful_disconnect

**Dependencies**: STORY-D04-014

---

# EPIC D-01: ORDER MANAGEMENT SYSTEM (21 Stories)

## Feature D01-F01 — Order Capture & Validation (3 Stories)

---

### STORY-D01-001: Implement order capture REST API

**Feature**: D01-F01 · **Points**: 5 · **Sprint**: 6 · **Team**: Beta

Implement POST /orders endpoint: { instrument_id, quantity, side (BUY/SELL), order_type (MARKET/LIMIT/STOP/STOP_LIMIT), price?, stop_price?, client_id, time_in_force (DAY/GTC/IOC/FOK), idempotency_key, account_id }. Validate required fields. Instrument lookup via D-11 (must be ACTIVE). Lot size validation via K-02 config (quantity % lot_size == 0). Create order in PENDING state. Emit OrderPlaced event to K-05. Assign dual-calendar timestamps via K-15.

**ACs**:

1. Given valid BUY LIMIT order, When POST /orders, Then order created with status=PENDING, 201 returned with order_id
2. Given quantity not divisible by lot_size (e.g., 7 where lot=10), When POST /orders, Then 422 BELOW_MIN_LOT
3. Given suspended instrument, When POST /orders, Then 422 INSTRUMENT_SUSPENDED

**Tests**: create_valid_pending · create_belowMinLot_422 · create_suspendedInstr_422 · create_eventEmitted · create_dualTimestamp · create_idempotent · create_missingFields_400 · perf_10kOrdersPerSec

**Dependencies**: K-01, K-02, K-05, K-15, D-11

---

### STORY-D01-002: Implement order field validation and enrichment

**Feature**: D01-F01 · **Points**: 3 · **Sprint**: 6 · **Team**: Beta

Validate order-type-specific fields: LIMIT requires price, STOP requires stop_price, STOP_LIMIT requires both. Validate price > 0, quantity > 0, price within tick_size grid (price % tick_size == 0). Enrich order with: instrument details (symbol, exchange, currency), client details (name, account type), current market price (D-04). Track order_value = quantity × price for risk/reporting.

**ACs**:

1. Given LIMIT order without price, When validated, Then 422 PRICE_REQUIRED
2. Given price not on tick grid (e.g., 101.3 where tick=0.5), When validated, Then 422 INVALID_TICK_SIZE
3. Given valid order, When enriched, Then instrument_name, client_name, market_price populated

**Tests**: validate_limitNoPrice_422 · validate_tickSize · validate_priceZero · validate_quantityZero · enrich_instrumentDetails · enrich_marketPrice · enrich_orderValue

**Dependencies**: STORY-D01-001, D-04, D-11

---

### STORY-D01-003: Implement market hours and session validation

**Feature**: D01-F01 · **Points**: 2 · **Sprint**: 6 · **Team**: Beta

Validate order submission against market session: reject if session = CLOSED or PRE_OPEN (unless IOC/GTC). Query D-04 for current session state. Check K-15 holiday calendar. GTC orders accepted anytime but queued for next open session. IOC orders during auction sessions → rejected.

**ACs**:

1. Given market CLOSED, When DAY order submitted, Then 422 MARKET_CLOSED
2. Given market CLOSED, When GTC order submitted, Then accepted, queued for next open
3. Given market in OPEN_AUCTION, When IOC order submitted, Then 422 IOC_NOT_ALLOWED_IN_AUCTION

**Tests**: closed_dayOrder_rejected · closed_gtcOrder_queued · auction_ioc_rejected · open_allTypes_accepted · holiday_rejected

**Dependencies**: STORY-D01-001, D-04, K-15

## Feature D01-F02 — Order State Machine (4 Stories)

---

### STORY-D01-004: Implement 9-state order lifecycle machine

**Feature**: D01-F02 · **Points**: 5 · **Sprint**: 6 · **Team**: Beta

Implement order state machine with 9 states: DRAFT, PENDING, PENDING_APPROVAL, APPROVED, ROUTED, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED. Define allowed transition matrix. Command handlers: submit(), approve(), reject(), route(), partialFill(qty, price), fill(), cancel(). Each transition emits domain event. Validate transitions — reject invalid ones.

**ACs**:

1. Given order in PENDING, When approve() called, Then state → APPROVED, OrderApproved event emitted
2. Given order in FILLED, When cancel() attempted, Then InvalidTransitionError, no event emitted
3. Given order in ROUTED, When partialFill(50) on qty=100, Then state → PARTIALLY_FILLED, filled_qty=50

**Tests**: all_valid_transitions · all_invalid_transitions (81-9=72 invalid pairs) · partialFill_updatesFilled · events_perTransition · concurrentTransition_firstWins

**Dependencies**: STORY-D01-001, K-05

---

### STORY-D01-005: Implement event-sourced order reconstruction

**Feature**: D01-F02 · **Points**: 3 · **Sprint**: 6 · **Team**: Beta

Reconstruct order state from event stream. Order aggregate loads events from K-05 event store, applies each event to rebuild current state. Supports snapshotting (every 10 events, store snapshot to avoid full replay). Snapshot stored alongside events.

**ACs**:

1. Given order with 15 events, When reconstructed, Then snapshot at event 10 used, events 11-15 replayed
2. Given replay from scratch (no snapshot), When all events applied, Then final state matches live state
3. Given concurrent read during write, When reconstruction runs, Then consistent snapshot returned

**Tests**: reconstruct_fromEvents · reconstruct_withSnapshot · reconstruct_consistency · snapshot_every10 · snapshot_recovery

**Dependencies**: STORY-D01-004, K-05

---

### STORY-D01-006: Implement order amendment support

**Feature**: D01-F02 · **Points**: 3 · **Sprint**: 6 · **Team**: Beta

Support order amendments: change price, quantity (only increase or decrease within limits), time_in_force. Amend only allowed in states: PENDING, PENDING_APPROVAL, APPROVED (not yet ROUTED). Amendment creates OrderAmended event with old/new values. Amend must re-validate: lot size, tick size, risk checks. Maker-checker required if amendment increases order value by > 50%.

**ACs**:

1. Given order in APPROVED state, When price amended, Then OrderAmended event with old_price, new_price emitted
2. Given order in ROUTED state, When amend attempted, Then 422 CANNOT_AMEND_ROUTED
3. Given amendment increases value > 50%, When amend submitted, Then routed to maker-checker approval

**Tests**: amend_price_valid · amend_quantity_valid · amend_routed_rejected · amend_revalidates · amend_makerChecker_threshold · amend_event

**Dependencies**: STORY-D01-004

---

### STORY-D01-007: Implement order cancellation with cascade

**Feature**: D01-F02 · **Points**: 3 · **Sprint**: 6 · **Team**: Beta

Implement order cancellation: cancel allowed in PENDING, PENDING_APPROVAL, APPROVED, ROUTED (sends cancel request to exchange), PARTIALLY_FILLED (cancels remaining qty). Cancel emits OrderCancelled event with reason (USER_REQUESTED, SYSTEM_EOD, ADMIN_CANCEL). For ROUTED orders, send CancelRequest to EMS → exchange, await CancelConfirmed or CancelRejected. EOD auto-cancel for DAY orders.

**ACs**:

1. Given order in APPROVED, When cancel requested, Then immediately CANCELLED, OrderCancelled event
2. Given order ROUTED, When cancel requested, Then CancelRequest sent to EMS, state → PENDING_CANCEL
3. Given DAY order at market close, When EOD runs, Then auto-cancelled with reason SYSTEM_EOD

**Tests**: cancel_approved_immediate · cancel_routed_sendsToEms · cancel_partialFilled_remainingOnly · cancel_eod_dayOrders · cancel_filled_rejected · cancel_event

**Dependencies**: STORY-D01-004, D-02

## Feature D01-F03 — Pre-Trade Evaluation Pipeline (3 Stories)

---

### STORY-D01-008: Implement compliance check integration

**Feature**: D01-F03 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Integrate D-07 compliance engine into pre-trade pipeline. Before routing, call D-07 ComplianceCheck: restricted list check, lock-in period check, beneficial ownership limit check, KYC status check. Compliance result: PASS, FAIL(reasons), or REVIEW(requires manual). On FAIL → order REJECTED. On REVIEW → order to PENDING_APPROVAL.

**ACs**:

1. Given order for restricted instrument, When compliance check runs, Then FAIL with RESTRICTED_INSTRUMENT, order REJECTED
2. Given client in lock-in period for instrument, When checked, Then FAIL with LOCK_IN_PERIOD
3. Given all compliance checks pass, When pipeline continues, Then order proceeds to risk check

**Tests**: compliance_restricted_rejects · compliance_lockIn_rejects · compliance_kycExpired_rejects · compliance_allPass · compliance_review_pendingApproval · compliance_timeout_circuitBreaker

**Dependencies**: D-07, STORY-D01-004

---

### STORY-D01-009: Implement risk check integration

**Feature**: D01-F03 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Integrate D-06 risk engine into pre-trade pipeline. After compliance pass, call D-06 PreTradeRiskCheck: margin sufficiency, position limit, concentration limit. Must complete < 5ms P99. On DENY → order REJECTED with risk reason. Cache risk data in Redis for speed.

**ACs**:

1. Given insufficient margin, When risk check runs, Then DENY with INSUFFICIENT_MARGIN, order REJECTED
2. Given all risk checks pass, When completed, Then order → APPROVED within 5ms
3. Given D-06 unavailable, When circuit breaker open, Then configurable: DENY_ALL or DEGRADE

**Tests**: risk_marginFail_rejects · risk_positionLimit_rejects · risk_concentrationLimit_rejects · risk_allPass · risk_timeout_circuitBreaker · perf_risk_sub5ms

**Dependencies**: D-06, STORY-D01-008

---

### STORY-D01-010: Implement configurable pre-trade pipeline orchestration

**Feature**: D01-F03 · **Points**: 2 · **Sprint**: 7 · **Team**: Beta

Make pre-trade pipeline configurable per jurisdiction via K-02. Pipeline steps: [ComplianceCheck, RiskCheck, MakerCheckerCheck, CustomPluginCheck(T2)]. Order of steps configurable. Each step can be enabled/disabled. Custom T2 plugin check: jurisdiction-specific plugins can add validation steps. Pipeline result aggregation: all steps must PASS; first FAIL stops pipeline.

**ACs**:

1. Given jurisdiction "NP" config with 3 steps, When pipeline runs, Then steps executed in configured order
2. Given step disabled in config, When pipeline runs, Then disabled step skipped
3. Given T2 plugin registered for pre-trade, When pipeline runs, Then plugin invoked in sandbox

**Tests**: pipeline_configOrder · pipeline_disabledStep_skipped · pipeline_t2Plugin · pipeline_firstFail_stops · pipeline_allPass_approved · pipeline_customJurisdiction

**Dependencies**: STORY-D01-008, STORY-D01-009, K-02, K-04

## Feature D01-F04 — Maker-Checker for Large Orders (2 Stories)

---

### STORY-D01-011: Implement order approval workflow

**Feature**: D01-F04 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Implement maker-checker approval for orders exceeding configurable thresholds (K-02): order_value > X, quantity > Y, or flagged by compliance/risk. Order state: PENDING → PENDING_APPROVAL → (APPROVED | REJECTED). Multi-level approval: L1 (supervisor), L2 (compliance officer) for high-value. Notification to approvers via K-05 event. Approval timeout: configurable (default 4 hours) → auto-expire to REJECTED.

**ACs**:

1. Given order value > threshold, When submitted, Then state = PENDING_APPROVAL, approver notified
2. Given L1 approver approves, When approval recorded, Then state → APPROVED, OrderApproved event emitted
3. Given approval timeout (4 hours), When expired, Then order → REJECTED with APPROVAL_TIMEOUT

**Tests**: approval_triggered_byValue · approval_l1_approves · approval_l2_required_highValue · approval_timeout_rejects · approval_rejection_recorded · approval_notification

**Dependencies**: STORY-D01-004, K-01, K-02

---

### STORY-D01-012: Implement approval dashboard and history

**Feature**: D01-F04 · **Points**: 2 · **Sprint**: 7 · **Team**: Beta

Build approval queue API: GET /orders/pending-approval?assignee=me for approvers. Show pending orders with age, value, client details, risk flags. Approval history: GET /orders/:id/approvals showing all approval/rejection actions with actor, timestamp, reason. Dashboard card count API for admin portal.

**ACs**:

1. Given approver queries pending queue, When 5 orders pending, Then all 5 returned sorted by age (oldest first)
2. Given order approved by L1, When history queried, Then approval action with actor and timestamp shown
3. Given dashboard card query, When called, Then returns { pending_count, avg_age_minutes, overdue_count }

**Tests**: queue_list · queue_sortByAge · history_showsActions · dashboard_counts · queue_filterByAssignee

**Dependencies**: STORY-D01-011

## Feature D01-F05 — Order Routing Engine (3 Stories)

---

### STORY-D01-013: Implement order routing to EMS

**Feature**: D01-F05 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

After APPROVED, route order to D-02 EMS for execution. Routing logic: select exchange based on instrument.exchange (from D-11). Create routing record: routing_id, order_id, exchange, routed_at, status. State → ROUTED. Emit OrderRouted event. Handle EMS rejection (exchange down, invalid order) → update order to REJECTED.

**ACs**:

1. Given order APPROVED, When routed, Then routing record created, state → ROUTED, OrderRouted event emitted
2. Given EMS rejects (exchange down), When rejection received, Then order → REJECTED with EMS_REJECTION
3. Given multiple executions venues (future), When routing, Then venue selected based on SOR rules

**Tests**: route_approved_success · route_emsRejection · route_event · route_venueSelection · route_idempotent

**Dependencies**: STORY-D01-004, D-02

---

### STORY-D01-014: Implement fill processing from EMS

**Feature**: D01-F05 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Process execution reports (fills) from EMS. Handle full fill: state → FILLED. Handle partial fill: state → PARTIALLY_FILLED, update filled_qty, remaining_qty, avg_fill_price. Multiple partial fills → accumulate. Final fill → FILLED. Each fill creates FillReceived event with fill details. Calculate average price: weighted by fill quantities.

**ACs**:

1. Given full fill for 100 qty, When processed, Then state → FILLED, avg_price calculated
2. Given partial fill of 50/100, When processed, Then state → PARTIALLY_FILLED, filled_qty=50, remaining=50
3. Given 3 partial fills (30+30+40) at different prices, When accumulated, Then avg_price = weighted average

**Tests**: fill_full · fill_partial · fill_multiple_partials · fill_avgPrice_weighted · fill_event · fill_idempotent_byExecId

**Dependencies**: STORY-D01-013, D-02

---

### STORY-D01-015: Implement order timeout and expiry management

**Feature**: D01-F05 · **Points**: 2 · **Sprint**: 7 · **Team**: Beta

Manage order timeouts: DAY orders expire at market close, IOC orders expire if not immediately filled, GTC orders expire after configurable period (default 30 days). Scheduled job: check pending/routed/partially-filled orders against expiry rules. State → EXPIRED (sub-state of CANCELLED). Emit OrderExpired event.

**ACs**:

1. Given DAY order still ROUTED at market close, When EOD job runs, Then state → EXPIRED
2. Given IOC order ROUTED with no fill in 5 seconds, When timeout, Then cancel request to EMS
3. Given GTC order, When 30 days elapsed, Then EXPIRED with reason GTC_MAX_DURATION

**Tests**: day_expiry_atClose · ioc_timeout_5s · gtc_max30days · partial_fill_then_expire_remaining · expiry_event

**Dependencies**: STORY-D01-013, K-15

## Feature D01-F06 — Position Update Projections (3 Stories)

---

### STORY-D01-016: Implement real-time position projection

**Feature**: D01-F06 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Build CQRS read-side projection: consume FillReceived events → update position records. Position: { client_id, instrument_id, account_id, quantity, avg_cost, unrealized_pnl, realized_pnl }. Store in PostgreSQL read model. On fill: update quantity (+ for BUY, - for SELL), recalculate avg_cost (weighted), update P&L. Redis cache for real-time queries.

**ACs**:

1. Given BUY fill for 100 qty at 500, When projected, Then position.quantity += 100, avg_cost = 500
2. Given SELL fill for 50 qty at 600, When projected, Then position.quantity -= 50, realized_pnl calculated
3. Given position query, When Redis cache hit, Then response < 1ms

**Tests**: position_buy_increases · position_sell_decreases · position_avgCost_weighted · position_pnl_calculation · position_cache_sub1ms · position_zero_sells_all

**Dependencies**: STORY-D01-014, K-05

---

### STORY-D01-017: Implement position limit monitoring

**Feature**: D01-F06 · **Points**: 2 · **Sprint**: 7 · **Team**: Beta

Monitor positions against limits in real-time. Position limits per K-02 config: max_quantity per instrument per client, max_value per instrument, max_concentration (% of portfolio). On limit breach: emit PositionLimitBreach event. Dashboard: positions approaching limits (>80% of limit).

**ACs**:

1. Given position reaches 90% of limit, When checked, Then WARNING alert emitted
2. Given position exceeds limit (via external transfer), When detected, Then PositionLimitBreach event emitted
3. Given dashboard query, When called, Then positions > 80% of any limit returned

**Tests**: limit_warning_90pct · limit_breach_event · limit_dashboard · limit_multiInstrument · limit_configurable

**Dependencies**: STORY-D01-016, K-02

---

### STORY-D01-018: Implement order blotter and query API

**Feature**: D01-F06 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Build order query API: GET /orders?client_id=&status=&instrument_id=&from=&to=&page=&size=. Supports filtering, sorting (by created_at desc default), pagination. Blotter view: recent orders with real-time status updates via WebSocket. GET /orders/:id for full order detail including fills, approvals, events. Performance: < 50ms for paginated queries.

**ACs**:

1. Given 1000 orders for client, When GET /orders?client_id=X&page=1&size=20, Then first 20 orders returned in < 50ms
2. Given order with 3 fills, When GET /orders/:id, Then order detail with all fills and approval history
3. Given WebSocket subscription to order status, When fill received, Then real-time update pushed

**Tests**: query_paginated · query_filtered · query_sorted · detail_withFills · websocket_statusUpdate · perf_sub50ms

**Dependencies**: STORY-D01-016

---

# EPIC D-07: COMPLIANCE ENGINE (17 Stories)

## Feature D07-F01 — Rule Orchestration Pipeline (3 Stories)

---

### STORY-D07-001: Implement compliance rule orchestration framework

**Feature**: D07-F01 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Build compliance rule orchestration engine that evaluates a configurable set of rules against trade/order data. Uses K-03 rules engine for OPA/Rego evaluation. Pipeline: iterate through rule set, evaluate each, aggregate results. ComplianceCheckRequest { order, client, instrument, positions } → ComplianceCheckResult { status: PASS|FAIL|REVIEW, rules_evaluated[], reasons[] }.

**ACs**:

1. Given 5 compliance rules configured, When check executed, Then all 5 evaluated, result aggregated
2. Given first rule FAILs, When pipeline runs, Then continues all rules (aggregates all failures)
3. Given all rules PASS, When result returned, Then status=PASS with rules_evaluated list

**Tests**: pipeline_allPass · pipeline_oneFail · pipeline_multipleFails · pipeline_review · pipeline_emptyRules_defaultPass · perf_pipeline_sub10ms

**Dependencies**: K-03, K-02

---

### STORY-D07-002: Implement jurisdiction-specific rule routing

**Feature**: D07-F01 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Route compliance checks to jurisdiction-specific rule sets. Determine jurisdiction from: order → instrument → exchange → jurisdiction mapping (K-02). Load jurisdiction rule pack from K-03. Supports multiple jurisdictions simultaneously (for cross-border trades). Fallback: if jurisdiction rules not available, use GLOBAL rule set.

**ACs**:

1. Given order for NEPSE instrument, When evaluated, Then Nepal jurisdiction rules applied
2. Given unknown jurisdiction, When evaluated, Then GLOBAL rule set used as fallback
3. Given jurisdiction rule pack updated, When next check runs, Then updated rules applied

**Tests**: route_nepal_rules · route_global_fallback · route_multiJurisdiction · route_ruleUpdate · route_unknown_fallback

**Dependencies**: STORY-D07-001, K-02, K-03

---

### STORY-D07-003: Implement compliance check audit trail

**Feature**: D07-F01 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Record every compliance check with full details: order_id, rules_evaluated, individual_results, overall_result, evaluation_duration_ms, jurisdiction, timestamp. Store in compliance_checks table. Emit ComplianceCheckCompleted event (K-05). Integrate with K-07 audit. API: GET /compliance/checks/:orderId for audit inquiry.

**ACs**:

1. Given compliance check completed, When recorded, Then all rule results with durations stored
2. Given audit inquiry, When GET /compliance/checks/:orderId, Then full evaluation details returned
3. Given regulator query, When checking order compliance, Then complete trail available

**Tests**: audit_recorded · audit_query · audit_allDetails · audit_event · audit_regulatorExport

**Dependencies**: STORY-D07-001, K-05, K-07

## Feature D07-F02 — Lock-In Period Enforcement (2 Stories)

---

### STORY-D07-004: Implement lock-in period rule engine

**Feature**: D07-F02 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Enforce lock-in periods for promoter/insider shares using BS calendar (K-15). Rules: promoter shares locked for 3 years from allotment, IPO shares locked for specific periods, bonus shares may have lock-in. Data source: instrument_lock_ins table (client_id, instrument_id, quantity, lock_in_start_bs, lock_in_end_bs, type). SELL order → check if (position - locked_qty) >= sell_qty.

**ACs**:

1. Given client with 1000 qty, 500 locked until 2082-06-01, When SELL 800 on 2081-12-01, Then FAIL: LOCK_IN_ACTIVE (only 500 available)
2. Given all locked shares expired, When SELL attempted, Then PASS (full quantity available)
3. Given BS lock-in dates, When evaluated, Then K-15 conversion used correctly

**Tests**: lockIn_activeBlock · lockIn_partialAvailable · lockIn_expired_passes · lockIn_bsCalendar · lockIn_multipleEntries · lockIn_zeroAvailable

**Dependencies**: STORY-D07-001, K-15

---

### STORY-D07-005: Implement lock-in data management API

**Feature**: D07-F02 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

CRUD API for lock-in records: POST /compliance/lock-ins (maker-checker). Bulk import from CDSC/exchange data. Auto-import from corporate actions (bonus share lock-in). GET /compliance/lock-ins?client_id=&instrument_id= for queries. Lock-in expiry notifications: 30-day advance warning.

**ACs**:

1. Given lock-in created via API, When approved by checker, Then active lock-in recorded
2. Given bulk import from CDSC, When processed, Then all lock-ins created matching source data
3. Given lock-in expiring in 30 days, When scheduled check runs, Then notification sent to client

**Tests**: create_with_makerChecker · bulkImport · autoImport_fromCA · query_byClient · expiry_notification_30day

**Dependencies**: STORY-D07-004

## Feature D07-F03 — KYC/AML Gateway (3 Stories)

---

### STORY-D07-006: Implement KYC status validation

**Feature**: D07-F03 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Check client KYC status before trade execution. KYC statuses: VERIFIED, PENDING, EXPIRED, REJECTED, SUSPENDED. Rules: only VERIFIED clients can trade. EXPIRED: allow existing positions management but no new purchases. KYC data from K-01 client profile. KYC expiry check against BS calendar (annual renewal in Nepal).

**ACs**:

1. Given client with KYC_STATUS=VERIFIED, When trade checked, Then PASS
2. Given client with KYC_STATUS=EXPIRED, When BUY order checked, Then FAIL: KYC_EXPIRED
3. Given client with KYC_STATUS=EXPIRED, When SELL order checked, Then PASS (exit position allowed)

**Tests**: kyc_verified_passes · kyc_expired_buyBlocked · kyc_expired_sellAllowed · kyc_pending_blocked · kyc_rejected_blocked · kyc_suspeneded_blocked

**Dependencies**: K-01, STORY-D07-001

---

### STORY-D07-007: Implement AML risk scoring integration

**Feature**: D07-F03 · **Points**: 3 · **Sprint**: 7 · **Team**: Gamma

Integrate AML risk scoring: each client has aml_risk_score (LOW, MEDIUM, HIGH, CRITICAL). Rules based on risk level: LOW → standard processing, MEDIUM → enhanced monitoring, HIGH → maker-checker required for all trades, CRITICAL → trading suspended. Risk score from external AML provider (T3 adapter) or manual assessment. Score changes emit ClientRiskChanged event.

**ACs**:

1. Given client with aml_risk=HIGH, When order submitted, Then routed to maker-checker regardless of value
2. Given client with aml_risk=CRITICAL, When order submitted, Then FAIL: TRADING_SUSPENDED_AML
3. Given risk score changes, When ClientRiskChanged event, Then compliance cache updated

**Tests**: aml_low_standard · aml_high_makerChecker · aml_critical_suspended · aml_riskChange_cacheUpdate · aml_externalProvider

**Dependencies**: STORY-D07-006, K-04

---

### STORY-D07-008: Implement enhanced due diligence triggers

**Feature**: D07-F03 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

Trigger enhanced due diligence (EDD) for: large value transactions (> threshold), unusual patterns (10x normal volume), PEP (Politically Exposed Person) trades, cross-border transactions. EDD creates case in compliance workflow. Block trade until EDD resolution for CRITICAL triggers. Log all EDD triggers in audit.

**ACs**:

1. Given transaction > EDD threshold, When triggered, Then EDD case created, trade held pending review
2. Given PEP client trade, When detected, Then EDD auto-triggered regardless of value
3. Given EDD resolved with APPROVE, When decision recorded, Then trade released to execution

**Tests**: edd_largeValue · edd_pep · edd_unusualVolume · edd_resolve_approve · edd_resolve_reject · edd_audit

**Dependencies**: STORY-D07-007

## Feature D07-F04 — Beneficial Ownership Tracking (2 Stories)

---

### STORY-D07-009: Implement beneficial ownership calculation

**Feature**: D07-F04 · **Points**: 3 · **Sprint**: 7 · **Team**: Gamma

Calculate beneficial ownership percentages: aggregate positions across related entities (from D-11 entity graph). Ownership = (total_shares_held_by_group / total_outstanding_shares) × 100. Track against regulatory thresholds: >5% = disclosure required, >10% = enhanced monitoring, >25% = board notification. Recalculate on every fill event.

**ACs**:

1. Given entity group holding 6% of NABIL, When calculated, Then DISCLOSURE_REQUIRED flag set
2. Given fill increases group ownership from 4.9% to 5.1%, When recalculated, Then OwnershipThresholdCrossed event emitted
3. Given multiple entities in group, When calculated, Then all related positions aggregated

**Tests**: ownership_singleEntity · ownership_group_aggregated · ownership_threshold_5pct · ownership_threshold_10pct · ownership_recalc_onFill · ownership_event

**Dependencies**: D-11, STORY-D01-016, STORY-D07-001

---

### STORY-D07-010: Implement beneficial ownership disclosure workflow

**Feature**: D07-F04 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

When ownership threshold crossed: auto-generate disclosure document. Workflow: detection → notification to compliance officer → document generation → submission to regulator (D-10). Track disclosure status: PENDING, SUBMITTED, ACKNOWLEDGED. Disclosure deadline tracking (per K-15 BS calendar).

**ACs**:

1. Given OwnershipThresholdCrossed event, When processed, Then disclosure workflow initiated
2. Given disclosure due in 2 BS days, When deadline approaching, Then escalation alert sent
3. Given disclosure submitted, When regulator ACK received, Then status → ACKNOWLEDGED

**Tests**: disclosure_autoGenerate · disclosure_deadline · disclosure_submit · disclosure_ack · disclosure_escalation

**Dependencies**: STORY-D07-009, D-10

## Feature D07-F05 — Restricted List Management (2 Stories)

---

### STORY-D07-011: Implement restricted and watch list management

**Feature**: D07-F05 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Manage restricted lists (instruments that specific groups cannot trade) and watch lists (enhanced monitoring). Data model: restricted_lists table (list_type: RESTRICTED|WATCH|GREY, instrument_id, entity_group_id, reason, start_date_bs, end_date_bs). CRUD API with maker-checker. Pre-trade check: if instrument in client's restricted list → FAIL.

**ACs**:

1. Given instrument on client's restricted list, When order submitted, Then FAIL: RESTRICTED_INSTRUMENT
2. Given instrument on watch list, When order submitted, Then PASS but flagged for surveillance (D-08)
3. Given restricted list entry expired, When checked, Then not applied

**Tests**: restricted_blocks · watch_flags · grey_enhanced · expired_notApplied · create_makerChecker · query_byClient

**Dependencies**: STORY-D07-001, D-11

---

### STORY-D07-012: Implement insider trading prevention checks

**Feature**: D07-F05 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

Detect potential insider trading: cross-reference trades against insider lists (corporate officers, board members, related persons). Insider list from D-11 entity relationships. Rules: insiders cannot trade during blackout periods (around earnings, corporate actions). Auto-detect corporate action announcements → auto-apply blackout windows.

**ACs**:

1. Given insider during blackout period, When order submitted, Then FAIL: BLACKOUT_PERIOD
2. Given corporate action announced, When detected, Then blackout window auto-applied for insiders
3. Given non-insider during blackout, When order submitted, Then PASS (blackout only applies to insiders)

**Tests**: insider_blackout_blocked · insider_outsideBlackout_passes · nonInsider_passes · autoBlackout_onCA · blackout_window · insider_crossRef

**Dependencies**: STORY-D07-011, D-11, D-12

## Feature D07-F06 — Compliance Attestation Workflows (2 Stories)

---

### STORY-D07-013: Implement periodic compliance attestation

**Feature**: D07-F06 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

Implement compliance attestation: clients must periodically attest to compliance declarations (annual, on regulatory change). Attestation workflow: generate attestation → notify client → client signs → compliance reviews → approved. Track: attestation_type, client_id, due_date_bs, status (PENDING, SIGNED, REVIEWED, EXPIRED). Escalation for overdue attestations.

**ACs**:

1. Given annual attestation due, When generated, Then client notified with attestation document
2. Given client signs attestation, When submitted, Then status → SIGNED, compliance officer notified for review
3. Given attestation overdue > 7 days, When checked, Then escalation to compliance manager

**Tests**: attestation_generate · attestation_sign · attestation_review · attestation_overdue_escalation · attestation_expiry

**Dependencies**: K-01, K-15, W-01 _(Note: W-01 Workflow Orchestration delivers Sprint 13; initial Sprint 7 implementation uses K-05 event-triggered scheduling for attestation workflow steps — W-01 integration in Sprint 13 adds durable workflow state, SLA tracking, and audit trail persistence)_

---

### STORY-D07-014: Implement compliance dashboard and reporting

**Feature**: D07-F06 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

Build compliance dashboard API: compliance check pass/fail rates, pending attestations, EDD cases open, restricted list stats, lock-in expirations upcoming. GET /compliance/dashboard for aggregate metrics. GET /compliance/reports?type=daily_summary&date= for daily compliance activity report. CSV/PDF export for regulator submissions.

**ACs**:

1. Given dashboard query, When called, Then returns { checks_today, pass_rate, edd_open, attestations_pending }
2. Given daily summary report, When generated, Then includes all compliance actions with outcomes
3. Given CSV export, When downloaded, Then all relevant data in regulator-compatible format

**Tests**: dashboard_metrics · report_dailySummary · report_export_csv · report_export_pdf · dashboard_realtime

**Dependencies**: STORY-D07-003

---

# EPIC D-14: SANCTIONS SCREENING (16 Stories)

## Feature D14-F01 — Real-Time Screening Engine (3 Stories)

---

### STORY-D14-001: Implement screening engine core with in-memory list

**Feature**: D14-F01 · **Points**: 5 · **Sprint**: 6 · **Team**: Gamma

Build real-time sanctions screening engine. Load sanctions lists (OFAC SDN, UN, EU, local NRB) into in-memory trie/prefix structure at startup. ScreeningRequest { name, type: INDIVIDUAL|ENTITY, nationality?, dob?, identifiers[] } → ScreeningResult { match_found, matches: [{ list, entry, score, match_type }] }. Must complete < 50ms P99. Atomic list swap on update (no partial state).

**ACs**:

1. Given exact name match on OFAC SDN, When screened, Then match_found=true, score=1.0, match_type=EXACT
2. Given name not on any list, When screened, Then match_found=false, < 50ms
3. Given list update in progress, When screening concurrent, Then uses old list until swap complete

**Tests**: exact_match · no_match · list_atomicSwap · concurrent_screening · perf_10kPerSec_sub50ms · multiList_aggregate

**Dependencies**: K-01, K-02, K-05, K-07

---

### STORY-D14-002: Implement screening API and pre-trade integration

**Feature**: D14-F01 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

REST API: POST /screening/check for on-demand screening. Integrate into OMS pre-trade pipeline: screen client + counterparty before order routing. Integrate into W-02 onboarding: screen applicant during KYC. Integrate into D-09 settlement: screen parties before settlement. Screening result stored with reference (order_id, onboarding_id, etc.).

**ACs**:

1. Given order submitted, When pre-trade screening runs, Then client name screened against all lists
2. Given screening match found, When result returned, Then order blocked pending match review
3. Given settlement party, When screened, Then screening result attached to settlement record

**Tests**: api_onDemand · preTrade_integration · onboarding_integration · settlement_integration · result_stored · match_blocksOrder

**Dependencies**: STORY-D14-001, D-01, D-09, W-02

---

### STORY-D14-003: Implement screening result classification and scoring

**Feature**: D14-F01 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Classify screening results by confidence score: >0.95 = AUTO_BLOCK (immediate block), 0.85-0.95 = HIGH (requires review), 0.70-0.85 = MEDIUM (requires review), <0.70 = LOW (auto-dismiss). Thresholds configurable via K-02. Score factors: name match strength, alias matches, DOB match, nationality match. Combined weighted score.

**ACs**:

1. Given score > 0.95, When classified, Then AUTO_BLOCK, transaction immediately stopped
2. Given score 0.80, When classified, Then MEDIUM, routed to review queue
3. Given score 0.60, When classified, Then LOW, auto-dismissed but logged

**Tests**: classify_autoBlock · classify_high · classify_medium · classify_low · score_factors · threshold_configurable

**Dependencies**: STORY-D14-001

## Feature D14-F02 — Fuzzy Matching (3 Stories)

---

### STORY-D14-004: Implement Levenshtein distance matching

**Feature**: D14-F02 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Implement Levenshtein (edit distance) algorithm for name matching. Normalize names: lowercase, strip diacritics, remove common suffixes (Ltd, Corp, Inc, Pvt). Calculate edit distance between normalized names. Score = 1 - (distance / max_length). Threshold: configurable minimum score (default 0.70). Handle multi-word names: match token sets (reordering allowed).

**ACs**:

1. Given name "Mohammed" vs list entry "Mohammad", When matched, Then score ≈ 0.875 (1 edit, length 8)
2. Given "John Smith" vs "Smith, John", When token set matched, Then score = 1.0 (all tokens match)
3. Given completely different names, When matched, Then score < 0.5, below threshold

**Tests**: levenshtein_similarNames · levenshtein_identical · levenshtein_differentNames · tokenSet_reorder · normalize_diacritics · normalize_suffixes · perf_1M_comparisons

**Dependencies**: STORY-D14-001

---

### STORY-D14-005: Implement Jaro-Winkler similarity matching

**Feature**: D14-F02 · **Points**: 3 · **Sprint**: 6 · **Team**: Gamma

Implement Jaro-Winkler distance algorithm: emphasizes matching at start of strings (prefix bonus). Better for personal names than Levenshtein for short strings. Score range: 0.0-1.0. Combine with Levenshtein: final_score = max(levenshtein_score, jaro_winkler_score) for best match. Add phonetic matching (Soundex/Metaphone) as additional signal.

**ACs**:

1. Given "Stephen" vs "Steven", When Jaro-Winkler scored, Then score > 0.85 (common prefix bonus)
2. Given "Martha" vs "Marhta", When scored, Then high score (transposition handling)
3. Given combined scoring, When both algorithms run, Then highest score used

**Tests**: jaroWinkler_similarStart · jaroWinkler_transposition · combined_maxScore · phonetic_soundex · phonetic_metaphone · perf_combined_sub1ms

**Dependencies**: STORY-D14-004

---

### STORY-D14-006: Implement name transliteration and alias expansion

**Feature**: D14-F02 · **Points**: 2 · **Sprint**: 6 · **Team**: Gamma

Support non-Latin name handling: transliterate Devanagari (Nepali) ↔ Latin for matching. Expand aliases: sanctions lists include known aliases (AKA). Match against all aliases. Nickname expansion: common variations (e.g., "Ram" ↔ "Rama", "Shyam" ↔ "Shyama"). Store alias mappings in configuration.

**ACs**:

1. Given Nepali Devanagari name, When transliterated to Latin, Then matches against Latin list entries
2. Given list entry with 3 aliases, When screening, Then all aliases checked
3. Given common nickname, When expanded, Then variation matched against list

**Tests**: transliterate_devanagari · alias_expansion · nickname_expansion · multiAlias_match · transliterate_roundTrip

**Dependencies**: STORY-D14-004

## Feature D14-F03 — Match Review Workflow (2 Stories)

---

### STORY-D14-007: Implement match review queue and workflow

**Feature**: D14-F03 · **Points**: 3 · **Sprint**: 7 · **Team**: Gamma

Build match review workflow for screening hits requiring human review. Review queue: GET /screening/reviews?status=PENDING. Reviewer actions: CONFIRM_MATCH (true positive → block client), DISMISS (false positive → whitelist), ESCALATE (uncertain → senior review). Maker-checker: all CONFIRM_MATCH decisions require checker. Time SLA: review within 4 hours. Escalation after SLA breach.

**ACs**:

1. Given match with score 0.82, When reviewer confirms, Then client transaction blocked, ClientBlocked event emitted
2. Given match dismissed as false positive, When recorded, Then name pair added to whitelist for future screenings
3. Given review pending > 4 hours, When SLA breached, Then auto-escalation to compliance manager

**Tests**: review_confirm · review_dismiss · review_escalate · review_makerChecker · review_sla_escalation · whitelist_applied

**Dependencies**: STORY-D14-003, K-01

---

### STORY-D14-008: Implement screening decision audit and evidence

**Feature**: D14-F03 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

Record complete audit trail for every screening decision: who reviewed, when, decision, rationale (free text), evidence attached. Evidence package: original screening request, match details, list entry details, reviewer notes. Export as PDF for regulatory evidence. Integrate with K-07 audit.

**ACs**:

1. Given review decision made, When recorded, Then all details stored in audit trail
2. Given evidence export requested, When generated, Then PDF with complete screening → review → decision chain
3. Given regulatory inquiry, When audited, Then complete trail from screening to final decision available

**Tests**: audit_decision_recorded · evidence_pdf_export · audit_allFields · audit_k07_integration · evidence_completeness

**Dependencies**: STORY-D14-007, K-07

## Feature D14-F04 — Sanctions List Management (2 Stories)

---

### STORY-D14-009: Implement sanctions list ingestion and management

**Feature**: D14-F04 · **Points**: 3 · **Sprint**: 7 · **Team**: Gamma

Ingest sanctions lists from multiple sources: OFAC SDN (XML), UN Consolidated (XML), EU sanctions (XML), NRB local list (CSV). Scheduled ingestion: daily at 02:00 UTC. Parse, normalize, store in sanctions_entries table. Update in-memory structure atomically. Track list versions. API: GET /screening/lists for status, list counts, last updated.

**ACs**:

1. Given OFAC SDN XML published, When daily ingestion runs, Then all entries parsed and loaded
2. Given list version changes, When new version ingested, Then old version archived, counters updated
3. Given API query, When called, Then returns list metadata: { lists, entry_counts, last_updated }

**Tests**: ingest_ofac_xml · ingest_un_xml · ingest_eu_xml · ingest_local_csv · version_tracking · api_listStatus · ingest_schedule

**Dependencies**: STORY-D14-001

---

### STORY-D14-010: Implement list change detection and impact analysis

**Feature**: D14-F04 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

On list update: detect changes (new entries, removed entries, modified entries). For new entries: trigger batch re-screening of all active clients against new entries. Impact analysis: count of potential matches before full screening. Notification: "OFAC SDN updated: 5 new entries, 2 removed, 3 modified. Re-screening 10,000 clients — estimated 30 min."

**ACs**:

1. Given new OFAC entries, When detected, Then batch re-screening initiated for all active clients
2. Given entry removed from list, When detected, Then previously blocked clients flagged for review/unblock
3. Given list update, When notification generated, Then includes change summary and re-screening ETA

**Tests**: changeDetection_new · changeDetection_removed · changeDetection_modified · reScreening_trigger · notification_summary · impact_estimate

**Dependencies**: STORY-D14-009

## Feature D14-F05 — Batch Re-Screening (2 Stories)

---

### STORY-D14-011: Implement batch re-screening engine

**Feature**: D14-F05 · **Points**: 3 · **Sprint**: 7 · **Team**: Gamma

Batch screening: screen all active clients/entities against updated sanctions lists. Parallelized: partition client list across workers (configurable concurrency, default 10). Progress tracking: { total, processed, matches, errors }. Resume capability: if interrupted, resume from last checkpoint. Run after every list update and on-demand.

**ACs**:

1. Given 50,000 active clients, When batch screening runs, Then all screened within 30 minutes
2. Given screening interrupted at 25,000, When resumed, Then continues from checkpoint without re-screening completed
3. Given batch completes, When results aggregated, Then new matches routed to review queue

**Tests**: batch_allClients · batch_parallel · batch_resume · batch_progress · batch_newMatches · perf_50k_30min

**Dependencies**: STORY-D14-001, STORY-D14-004

---

### STORY-D14-012: Implement batch screening scheduling and reporting

**Feature**: D14-F05 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

Schedule batch re-screening: after every list update (auto-trigger), daily at 03:00 UTC, on-demand via admin API. Generate batch screening report: total screened, matches found by list, match distribution by score, new matches vs. existing. Store reports for audit. Notify compliance team of results.

**ACs**:

1. Given list update event, When received, Then batch re-screening auto-triggered
2. Given batch complete, When report generated, Then breakdown by list, score distribution, new vs existing matches
3. Given compliance notification, When sent, Then includes summary and link to full report

**Tests**: schedule_autoOnListUpdate · schedule_daily · schedule_onDemand · report_generation · report_breakdown · notification_sent

**Dependencies**: STORY-D14-011

## Feature D14-F06 — Air-Gap Signed List Bundles (2 Stories)

---

### STORY-D14-013: Implement air-gap sanctions list bundle signing

**Feature**: D14-F06 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

For air-gap deployments: package sanctions lists as signed bundles. Bundle format: { lists: [...], version, generated_at, signature }. Signed with Ed25519 key (per K-14). Verification: on import, verify signature before loading. Bundle includes metadata: list source, entry count, generation timestamp.

**ACs**:

1. Given sanctions lists packaged, When bundle signed, Then Ed25519 signature attached with metadata
2. Given signed bundle imported on air-gap system, When verified, Then signature valid → lists loaded
3. Given tampered bundle, When verification fails, Then import rejected, alert emitted

**Tests**: bundle_sign · bundle_verify · bundle_tampered_rejected · bundle_metadata · bundle_import_airgap

**Dependencies**: K-14, STORY-D14-009

---

### STORY-D14-014: Implement list bundle transport and import workflow

**Feature**: D14-F06 · **Points**: 2 · **Sprint**: 7 · **Team**: Gamma

Workflow for air-gap list delivery: export bundle from online system (USB/secure media). Import workflow on air-gap system: upload bundle → verify signature → preview changes (new/removed entries) → maker-checker approve → load into screening engine. Audit trail: who imported, when, bundle version, approval.

**ACs**:

1. Given bundle uploaded to air-gap system, When maker-checker approves, Then list loaded, ScreeningListUpdated event
2. Given preview of changes, When displayed, Then shows "15 new entries, 3 removed" before approval
3. Given import audit, When queried, Then full trail: upload → verify → approve → load

**Tests**: import_upload · import_verify · import_preview · import_approve · import_audit · import_reject

**Dependencies**: STORY-D14-013

---

# EPIC D-06: RISK ENGINE (21 Stories)

## Feature D06-F01 — Pre-Trade Risk Checks (3 Stories)

---

### STORY-D06-001: Implement margin sufficiency check

**Feature**: D06-F01 · **Points**: 5 · **Sprint**: 7 · **Team**: Beta

Build margin sufficiency check: calculate required margin for order (quantity × price × margin_rate). Check against available_margin (deposited_margin - used_margin). Margin rates configurable per instrument type (K-02): equity 50%, derivatives 10-20%. Data in Redis for speed. Return APPROVE/DENY with available/required amounts.

**ACs**:

1. Given order requiring 50K margin with 30K available, When checked, Then DENY with INSUFFICIENT_MARGIN, showing required=50K, available=30K
2. Given order requiring 20K margin with 30K available, When checked, Then APPROVE in < 2ms
3. Given margin rate change in config, When next check runs, Then new rate applied

**Tests**: margin_insufficient_deny · margin_sufficient_approve · margin_rateConfig · margin_redis_sub2ms · margin_concurrent_atomic · margin_derivatives_rate

**Dependencies**: K-02, K-05, Redis

---

### STORY-D06-002: Implement position limit check

**Feature**: D06-F01 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Check position limits: max_qty per instrument per client (K-02 config). Calculate: current_position + order_quantity ≤ max_position. For SELL: current_position - order_quantity ≥ 0 (no short selling unless configured). Position data from Redis cache (real-time projection from D-01).

**ACs**:

1. Given position 900/1000 limit, When BUY 200 checked, Then DENY: POSITION_LIMIT_EXCEEDED
2. Given position 900/1000 limit, When BUY 50 checked, Then APPROVE (950 < 1000)
3. Given no short selling, When SELL exceeds position, Then DENY: SHORT_SELLING_NOT_ALLOWED

**Tests**: limit_exceeded_deny · limit_withinBounds · noShort_deny · limit_configurable · limit_redis_cache · limit_multiInstrument

**Dependencies**: STORY-D06-001, D-01

---

### STORY-D06-003: Implement concentration limit check

**Feature**: D06-F01 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Check portfolio concentration: (position_value_in_instrument / total_portfolio_value) ≤ max_concentration_pct (K-02 config, default 10%). Calculate position_value = quantity × current_price (from D-04 market data). Calculate total_portfolio_value = sum of all position values. New order would change concentration — project new values.

**ACs**:

1. Given order would push concentration from 9% to 12%, When max=10%, Then DENY: CONCENTRATION_LIMIT
2. Given order keeps concentration at 8%, When max=10%, Then APPROVE
3. Given D-04 market price unavailable, When fallback to last close, Then warn but proceed with stale price

**Tests**: concentration_exceeded · concentration_withinLimit · concentration_projection · concentration_stalePriceFallback · concentration_multiInstrument · concentration_sellReduces

**Dependencies**: STORY-D06-002, D-04

## Feature D06-F02 — VaR Calculation (5 Stories)

---

### STORY-D06-004: Implement parametric VaR calculation

**Feature**: D06-F02 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

Implement parametric (variance-covariance) VaR: VaR = Z × σ × V × √t where Z = confidence z-score, σ = daily volatility, V = portfolio value, t = holding period. Calculate daily volatility from historical returns (D-04). Covariance matrix for portfolio VaR. Confidence levels: 95%, 99%. Holding periods: 1-day, 10-day.

**ACs**:

1. Given portfolio with 5 positions, When 1-day 99% VaR calculated, Then result within ±5% of manual calculation
2. Given single equity position, When VaR calculated, Then VaR = Z(99%) × σ_daily × market_value
3. Given portfolio, When covariance matrix computed, Then properly captures diversification benefit

**Tests**: var_singlePosition · var_portfolio_diversification · var_99_confidence · var_95_confidence · var_10day_scaling · var_covariance_matrix · perf_portfolio_50positions_sub100ms

**Dependencies**: D-04, D-01

---

### STORY-D06-005: Implement historical simulation VaR

**Feature**: D06-F02 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

Implement historical simulation VaR: use actual historical returns over lookback window (default 252 trading days). Apply historical returns to current portfolio. Sort resulting P&L. VaR = nth percentile loss. No distributional assumptions. Data from D-04 historical ticks.

**ACs**:

1. Given 252 days of returns, When 95% VaR calculated, Then VaR = 13th worst loss (252 × 0.05)
2. Given portfolio with non-normal returns, When compared to parametric, Then historical captures tail risk better
3. Given insufficient history (< 100 days), When requested, Then warning flag, result marked INSUFFICIENT_DATA

**Tests**: histVar_252days · histVar_percentile · histVar_thinHistory_warning · histVar_portfolio · histVar_vs_parametric · perf_histVar_sub500ms

**Dependencies**: STORY-D06-004, D-04

---

### STORY-D06-006: Implement Monte Carlo VaR simulation

**Feature**: D06-F02 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

Implement Monte Carlo VaR: generate 10,000 random scenarios using Geometric Brownian Motion or bootstrapped historical returns. Run portfolio valuation per scenario. Sort P&L distribution. VaR = percentile of simulated losses. Configurable: simulation count, correlation model, distribution type.

**ACs**:

1. Given 10,000 simulations, When 99% VaR calculated, Then result converges (within ±10% of 50,000 simulation result)
2. Given correlated assets, When simulated, Then correlation structure preserved (Cholesky decomposition)
3. Given simulation completes, When timed, Then < 5 seconds for 50-position portfolio with 10K scenarios

**Tests**: mc_convergence · mc_correlation · mc_distribution · mc_scenarios_10k · mc_comparison_parametric · perf_mc_sub5s

**Dependencies**: STORY-D06-005

---

### STORY-D06-007: Implement VaR backtesting and validation

**Feature**: D06-F02 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Backtest VaR predictions: compare actual daily P&L against predicted VaR. Count exceptions (days where loss > VaR). At 99% confidence, expect ~2.5 exceptions per 250 days. Traffic light system: Green (0-4 exceptions), Yellow (5-9), Red (10+). Basel II backtesting framework. Report: daily VaR vs actual P&L chart.

**ACs**:

1. Given 250 trading days of VaR predictions, When backtested, Then exception count and traffic light status calculated
2. Given Red zone (>10 exceptions), When detected, Then alert emitted: VaR model recalibration needed
3. Given backtest report, When generated, Then includes VaR vs actual P&L time series chart

**Tests**: backtest_green · backtest_yellow · backtest_red · backtest_exceptionCount · backtest_report · backtest_alert

**Dependencies**: STORY-D06-004, STORY-D06-005

---

### STORY-D06-008: Implement VaR API and reporting

**Feature**: D06-F02 · **Points**: 2 · **Sprint**: 9 · **Team**: Beta

REST API: GET /risk/var?portfolio_id=&method=parametric&confidence=99&holding_period=1. Batch VaR calculation: EOD job computes VaR for all portfolios. VaR history storage for trend analysis. VaR decomposition: per-instrument contribution to total VaR. Dashboard card: current VaR, trend, confidence band.

**ACs**:

1. Given API request, When parametric VaR called, Then returns VaR amount, method, confidence, decomposition
2. Given EOD batch, When run, Then all portfolio VaRs computed and stored
3. Given VaR decomposition, When queried, Then per-instrument VaR contribution shown

**Tests**: api_parametric · api_historical · api_montecarlo · eod_batch · var_history · var_decomposition · dashboard_card

**Dependencies**: STORY-D06-004, STORY-D06-005, STORY-D06-006

## Feature D06-F03 — Margin Calculation Engine (3 Stories)

---

### STORY-D06-009: Implement initial margin calculation

**Feature**: D06-F03 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Calculate initial margin requirements per position: margin = quantity × price × initial_margin_rate. Rates per instrument type (K-02): equities 50%, government bonds 10%, corporate bonds 20%, MF/ETF 30%. Aggregate: total_required_margin = sum of all position margins. Margin utilization = used_margin / deposited_margin.

**ACs**:

1. Given equity position 100 × 500, When margin calculated, Then required = 100 × 500 × 50% = 25,000
2. Given multi-asset portfolio, When aggregated, Then total margin = sum of per-position margins
3. Given margin utilization > 80%, When detected, Then WARNING alert emitted

**Tests**: margin_equity · margin_bond · margin_aggregate · margin_utilization_warning · margin_configRates · margin_event

**Dependencies**: STORY-D06-001, K-02

---

### STORY-D06-010: Implement maintenance margin and mark-to-market

**Feature**: D06-F03 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

Calculate maintenance margin: lower than initial (typically 30% for equities). Daily mark-to-market: recalculate margin requirements based on current market prices (D-04). If equity falls below maintenance margin → trigger margin call. Margin excess = equity - maintenance_margin (positive = safe, negative = margin call).

**ACs**:

1. Given position MTM shows equity < maintenance margin, When calculated, Then margin_excess negative, MarginDeficit event
2. Given EOD mark-to-market, When prices updated, Then all position margins recalculated
3. Given margin_excess > 0, When checked, Then SAFE status

**Tests**: maintenance_deficit · maintenance_safe · mtm_priceChange · mtm_eod_batch · margin_excess_calculation · multipPosition_mtm

**Dependencies**: STORY-D06-009, D-04

---

### STORY-D06-011: Implement margin calculation for derivatives

**Feature**: D06-F03 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Extend margin calculation for derivatives: SPAN-like methodology. Initial margin based on worst case loss across price scenarios. Variation margin: daily P&L settlement. Spread margin credits for offsetting positions. Options: add premium margin. Support future extensions via T2 plugin for custom margin models.

**ACs**:

1. Given futures position, When initial margin calculated, Then uses scenario-based worst case loss
2. Given offsetting positions, When spread credit applied, Then margin reduced by credit amount
3. Given T2 margin plugin registered, When calculation runs, Then plugin result used (with fallback to default)

**Tests**: derivatives_initial · derivatives_variation · derivatives_spread_credit · derivatives_options · derivatives_t2Plugin · derivatives_fallback

**Dependencies**: STORY-D06-009, K-04

## Feature D06-F04 — Margin Call Generation & Escalation (3 Stories)

---

### STORY-D06-012: Implement margin call generation

**Feature**: D06-F04 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

When margin deficit detected: generate margin call notification to client. Margin call data: client_id, deficit_amount, deadline (T+1 by default, configurable), required_action (deposit cash/liquidate). Store in margin_calls table: id, client_id, deficit_amount, deadline_bs, deadline_utc, status (ISSUED, MET, BREACHED, LIQUIDATING). Emit MarginCallIssued event.

**ACs**:

1. Given margin deficit of 50K, When margin call generated, Then client notified with deficit amount and deadline
2. Given client deposits funds before deadline, When margin recalculated, Then deficit resolved → MarginCallMet
3. Given deadline reached with deficit unresolved, When checked, Then status → BREACHED, escalation triggered

**Tests**: marginCall_generate · marginCall_met · marginCall_breached · marginCall_notification · marginCall_deadline · marginCall_event

**Dependencies**: STORY-D06-010

---

### STORY-D06-013: Implement margin call escalation workflow

**Feature**: D06-F04 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

Escalation tiers: T0 (margin call issued, client notified), T+1 (reminder if unmet), T+2 (risk manager notified, trading restricted), T+3 (forced liquidation initiated). Configurable escalation timing via K-02. Notifications: email + SMS + in-app at each tier. Escalation respects BS calendar business days (K-15).

**ACs**:

1. Given margin call unmet after T+1 BS day, When escalation runs, Then reminder sent to client
2. Given margin call unmet after T+2, When escalated, Then risk manager notified, client trading restricted
3. Given escalation timing uses BS days, When holiday, Then deadline extended to next business day

**Tests**: escalation_t0_notify · escalation_t1_reminder · escalation_t2_restrict · escalation_t3_liquidation · escalation_bsCalendar · escalation_holiday

**Dependencies**: STORY-D06-012, K-15

---

### STORY-D06-014: Implement margin call dashboard and reporting

**Feature**: D06-F04 · **Points**: 2 · **Sprint**: 8 · **Team**: Beta

Dashboard API: GET /risk/margin-calls?status=ISSUED for risk officers. Aggregate metrics: { open_calls, total_deficit, avg_age, overdue_count }. Daily margin report: all calls issued, met, breached. Client margin status: GET /risk/margin/:clientId showing utilization, deficit, call history.

**ACs**:

1. Given risk officer queries calls, When status=ISSUED, Then all open margin calls with details returned
2. Given daily report, When generated, Then new calls, resolved calls, ongoing calls summarized
3. Given client query, When margin status retrieved, Then utilization %, deficit, call history shown

**Tests**: dashboard_openCalls · dashboard_metrics · report_daily · client_marginStatus · client_callHistory

**Dependencies**: STORY-D06-012

## Feature D06-F05 — Forced Liquidation Trigger (2 Stories)

---

### STORY-D06-015: Implement forced liquidation engine

**Feature**: D06-F05 · **Points**: 5 · **Sprint**: 9 · **Team**: Beta

Implement forced liquidation: triggered when margin call breached after escalation period. Liquidation strategy: sell positions in order of liquidity (most liquid first) until margin restored. Create liquidation orders in OMS (D-01) as SYSTEM orders (bypass normal approval). Liquidation amount = deficit + buffer (default 10%). Emit ForcedLiquidation event. Audit trail for regulatory compliance.

**ACs**:

1. Given margin call breached at T+3, When liquidation triggered, Then SELL orders created for most liquid positions
2. Given liquidation orders, When routed to OMS, Then created as SYSTEM type, bypass maker-checker
3. Given liquidation sufficient to cover deficit, When completed, Then margin restored, ForcedLiquidationComplete event

**Tests**: liquidation_trigger · liquidation_orderCreation · liquidation_systemBypass · liquidation_sufficientCover · liquidation_event · liquidation_audit · liquidation_mostLiquidFirst

**Dependencies**: STORY-D06-013, D-01

---

### STORY-D06-016: Implement liquidation safeguards and circuit breakers

**Feature**: D06-F05 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Safeguards: max liquidation per client per day (configurable), cooling period between liquidation batches (30 min), market impact check (don't liquidate > 5% of daily volume), manual override option for risk manager. Circuit breaker: if market in halt → pause liquidation. Notification to client before each batch.

**ACs**:

1. Given liquidation would exceed 5% of daily volume, When checked, Then partial liquidation, remainder deferred
2. Given market halted, When liquidation pending, Then paused until market reopens
3. Given risk manager override, When approved, Then liquidation modified/cancelled per override

**Tests**: safeguard_maxPerDay · safeguard_coolingPeriod · safeguard_marketImpact · safeguard_marketHalt · safeguard_manualOverride · safeguard_notification

**Dependencies**: STORY-D06-015

## Feature D06-F06 — Stress Testing & Scenario Analysis (3 Stories)

---

### STORY-D06-017: Implement stress testing framework

**Feature**: D06-F06 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Build stress testing framework: apply hypothetical market scenarios to current portfolio and calculate impact. Pre-defined scenarios: market crash (-20%), sector decline (-15%), interest rate shock (+200bps), currency devaluation (-10%). Custom scenarios via API. Run nightly for all portfolios. Output: P&L impact, margin impact, VaR change.

**ACs**:

1. Given market crash scenario (-20%), When applied to portfolio, Then projected P&L loss calculated
2. Given custom scenario via API, When executed, Then scenario results returned within 30 seconds
3. Given nightly run, When completed, Then all portfolios stressed, results stored and reported

**Tests**: stress_marketCrash · stress_sectorDecline · stress_customScenario · stress_nightlyBatch · stress_resultStorage · stress_marginImpact

**Dependencies**: STORY-D06-004, D-04

---

### STORY-D06-018: Implement scenario analysis with historical events

**Feature**: D06-F06 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Historical scenario replay: apply actual market movements from historical crises to current portfolio. Pre-loaded scenarios: NEPSE 2008 crash, COVID-19 March 2020, 2015 Nepal earthquake market impact. Configurable date ranges. Compare current portfolio resilience against historical events.

**ACs**:

1. Given "NEPSE 2008 crash" scenario, When applied, Then actual NEPSE price movements used for P&L projection
2. Given scenario date range, When returns extracted from D-04 history, Then applied to current positions
3. Given multiple scenarios run, When compared, Then worst-case identified and highlighted

**Tests**: historical_nepse2008 · historical_covid · historical_earthquake · historical_dateRange · historical_comparison · historical_worstCase

**Dependencies**: STORY-D06-017, D-04

---

### STORY-D06-019: Implement risk dashboard and reporting API

**Feature**: D06-F06 · **Points**: 2 · **Sprint**: 9 · **Team**: Beta

Unified risk dashboard API: GET /risk/dashboard returning { var_total, var_change_1d, margin_utilization_pct, margin_calls_open, stress_worst_case, top_risk_positions[], risk_alerts[] }. Risk reports: daily risk summary, weekly stress test, monthly VaR backtest. CSV/PDF export. Drill-down: per-client, per-instrument risk metrics.

**ACs**:

1. Given dashboard query, When called, Then returns all aggregate risk metrics in < 200ms
2. Given daily risk report, When generated, Then includes VaR, margin, stress, positions, alerts
3. Given drill-down per client, When queried, Then client-specific risk metrics with positions

**Tests**: dashboard_aggregates · dashboard_performance · report_dailyRisk · report_weeklyStress · report_export · drilldown_perClient

**Dependencies**: STORY-D06-008, STORY-D06-014, STORY-D06-017

---

# EPIC D-02: EXECUTION MANAGEMENT SYSTEM (22 Stories)

## Feature D02-F01 — Smart Order Router (4 Stories)

---

### STORY-D02-001: Implement SOR engine with venue selection

**Feature**: D02-F01 · **Points**: 5 · **Sprint**: 7 · **Team**: Beta

Build Smart Order Router: receive order from OMS → select best execution venue. Current: single venue (NEPSE). Architecture supports multi-venue (future). Venue selection criteria: price, latency, fill probability, cost. Venue registry: registered venues with capabilities and status. Route to selected venue via exchange adapter. Emit OrderRouted event.

**ACs**:

1. Given order from OMS, When SOR processes, Then venue selected based on criteria, order routed
2. Given single venue (NEPSE) registered, When routing, Then NEPSE selected automatically
3. Given venue unavailable (connection down), When routing fails, Then retry or reject with VENUE_UNAVAILABLE

**Tests**: sor_singleVenue · sor_venueDown_retry · sor_venueSelection · sor_orderRouted_event · sor_multiVenue_bestPrice · perf_sor_sub1ms

**Dependencies**: D-01, K-05

---

### STORY-D02-002: Implement order splitting and child order management

**Feature**: D02-F01 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Support order splitting: large orders split across venues or into smaller child orders for algorithms. Child order tracking: parent_order_id → child_orders[]. Status aggregation: parent status derived from children (all filled → parent filled, any child rejected → handle remainder). Child order lifecycle follows same state machine.

**ACs**:

1. Given large order split into 3 children, When all children filled, Then parent order → FILLED
2. Given child order rejected, When remaining qty rerouted, Then new child created for remainder
3. Given parent order cancelled, When cascade, Then all active children cancelled

**Tests**: split_createChildren · split_allFilled_parentFilled · split_childRejected_remainder · split_parentCancel_cascade · split_statusAggregation

**Dependencies**: STORY-D02-001, D-01

---

### STORY-D02-003: Implement best execution analysis

**Feature**: D02-F01 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

Best execution analysis: compare actual execution against arrival price. Metrics: implementation shortfall, market impact, timing cost. Record for every order: arrival_price (market price at order receipt), exec_price, slippage. MiFID II-style best execution reporting (jurisdiction-neutral). API: GET /execution/analysis/:orderId.

**ACs**:

1. Given fill at 505 with arrival price 500, When analyzed, Then implementation shortfall = 5 NPR per share
2. Given analysis for order, When queried, Then arrival_price, exec_price, slippage, market_impact returned
3. Given daily best execution report, When generated, Then aggregate stats for all executions

**Tests**: analysis_shortfall · analysis_slippage · analysis_marketImpact · analysis_perOrder · analysis_dailyReport · analysis_api

**Dependencies**: STORY-D02-001, D-04

---

### STORY-D02-004: Implement SOR configuration and routing rules

**Feature**: D02-F01 · **Points**: 2 · **Sprint**: 8 · **Team**: Beta

Configurable SOR rules via K-02: default venue per instrument type, venue preference order, minimum fill size per venue, time-based rules (route to primary venue during continuous trading, secondary during auction). T2 plugin support for custom routing logic. Routing rules version controlled.

**ACs**:

1. Given routing rule "equities → NEPSE", When equity order received, Then routed to NEPSE
2. Given T2 custom routing plugin, When registered, Then plugin logic invoked for routing decision
3. Given rule update via maker-checker, When approved, Then new rules applied immediately

**Tests**: rules_defaultVenue · rules_t2Plugin · rules_timeBased · rules_makerChecker · rules_versionControl

**Dependencies**: STORY-D02-001, K-02, K-04

## Feature D02-F02 — Execution Algorithms (5 Stories)

---

### STORY-D02-005: Implement VWAP algorithm

**Feature**: D02-F02 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

Implement Volume-Weighted Average Price algorithm: distribute order execution across the trading day to match VWAP benchmark. Inputs: target_quantity, start_time, end_time, participation_rate (default 20%). Use historical volume profile from D-04 to predict intraday volume distribution. Generate child orders at intervals matching volume pattern.

**ACs**:

1. Given VWAP order for 10,000 qty over 4 hours, When algorithm runs, Then child orders distributed matching volume profile
2. Given actual VWAP at end of day, When compared to execution VWAP, Then slippage < 5bps
3. Given low market volume, When participation > 20%, Then algorithm slows to stay within rate limit

**Tests**: vwap_distribution · vwap_participationRate · vwap_slippage_sub5bps · vwap_lowVolume · vwap_childOrders · vwap_cancel · perf_vwap_recalc_sub10ms

**Dependencies**: STORY-D02-001, D-04

---

### STORY-D02-006: Implement TWAP algorithm

**Feature**: D02-F02 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

Implement Time-Weighted Average Price algorithm: distribute order evenly over time period. Simpler than VWAP. Inputs: target_quantity, start_time, end_time, slice_interval (e.g., every 5 min). Each slice: submit quantity = total / number_of_slices. Randomize ±10% per slice to avoid detection.

**ACs**:

1. Given TWAP for 1000 qty over 2 hours in 5-min slices, When running, Then 24 child orders of ~42 each
2. Given randomization, When slices created, Then each slice varies ±10% from average
3. Given market closes before completion, When detected, Then remaining qty submitted in final slice

**Tests**: twap_evenDistribution · twap_randomization · twap_marketClose · twap_cancel · twap_childOrders · twap_residual

**Dependencies**: STORY-D02-001

---

### STORY-D02-007: Implement Implementation Shortfall algorithm

**Feature**: D02-F02 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

Implement Implementation Shortfall (IS) algorithm: minimize total execution cost including market impact and timing risk. Adaptive: adjusts aggressiveness based on real-time market conditions. Inputs: target_quantity, urgency (LOW, MEDIUM, HIGH), risk_aversion. High urgency → front-load execution. LOW urgency → back-load to reduce impact.

**ACs**:

1. Given HIGH urgency, When algorithm runs, Then 60% executed in first quarter of time window
2. Given LOW urgency, When algorithm runs, Then evenly distributed with opportunity-based acceleration
3. Given market volatility spike, When detected, Then algorithm adjusts pace (decrease pace in high vol)

**Tests**: is_highUrgency_frontLoad · is_lowUrgency_even · is_volatilityAdaptive · is_marketImpact_minimized · is_childOrders · is_cancel

**Dependencies**: STORY-D02-001, D-04

---

### STORY-D02-008: Implement algorithm monitoring and dashboard

**Feature**: D02-F02 · **Points**: 2 · **Sprint**: 8 · **Team**: Beta

Real-time algorithm monitoring: track progress (qty_filled / qty_target), slippage (exec_vwap vs market_vwap), participation rate, estimated completion time. Dashboard API: GET /execution/algorithms/:orderId/status. Algorithm events: AlgoSliceExecuted, AlgoCompleted, AlgoAborted. Alert if algorithm falls behind schedule.

**ACs**:

1. Given running algorithm, When status queried, Then progress %, slippage, ETA returned
2. Given algorithm falls behind by > 20%, When detected, Then alert emitted
3. Given algorithm completes, When finished, Then AlgoCompleted event with summary stats

**Tests**: monitor_progress · monitor_slippage · monitor_behind_alert · monitor_complete_event · monitor_api · monitor_abort

**Dependencies**: STORY-D02-005, STORY-D02-006, STORY-D02-007

---

### STORY-D02-009: Implement algorithm factory and configuration

**Feature**: D02-F02 · **Points**: 2 · **Sprint**: 8 · **Team**: Beta

Algorithm factory: select algorithm based on order parameters. Mapping: { VWAP, TWAP, IS, MARKET (immediate), LIMIT (passive) }. Algorithm configuration per broker/client via K-02: allowed algorithms, default algorithm, parameter constraints. T3 plugin for custom algorithms.

**ACs**:

1. Given order with algo=VWAP, When factory creates, Then VWAP algorithm instantiated with order params
2. Given client restricted from IS algo, When IS requested, Then FALL_BACK to TWAP or reject
3. Given T3 custom algorithm registered, When selected, Then custom algorithm executed

**Tests**: factory_vwap · factory_twap · factory_is · factory_restriction · factory_t3Custom · factory_defaultAlgo

**Dependencies**: STORY-D02-005, K-02, K-04

## Feature D02-F03 — T3 Exchange Adapter Framework (4 Stories)

---

### STORY-D02-010: Implement FIX 4.4/5.0 protocol engine

**Feature**: D02-F03 · **Points**: 5 · **Sprint**: 7 · **Team**: Beta

Implement FIX protocol engine supporting FIX 4.4 and 5.0. Session management: logon, heartbeat, logout. Message types: NewOrderSingle (D), ExecutionReport (8), OrderCancelRequest (F), OrderCancelReplaceRequest (G), MarketDataRequest (V). Sequence number management with gap fill. SSL/TLS connection.

**ACs**:

1. Given FIX session established, When NewOrderSingle sent, Then ExecutionReport received
2. Given sequence gap detected, When ResendRequest sent, Then missing messages replayed
3. Given connection drop, When reconnected, Then sequence numbers continued from last known

**Tests**: fix_logon · fix_newOrder · fix_execReport · fix_cancelOrder · fix_gapFill · fix_reconnect · fix_heartbeat · fix_tls

**Dependencies**: None (foundational protocol)

---

### STORY-D02-011: Implement exchange adapter plugin interface

**Feature**: D02-F03 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Define T3 ExchangeAdapter plugin interface: connect(config), disconnect(), submitOrder(order), cancelOrder(orderId), amendOrder(orderId, changes), getOrderStatus(orderId), onExecutionReport(callback), onMarketData(callback). Plugin capabilities: NETWORK (exchange connectivity), TIMER (heartbeat). Plugin verified via Ed25519 signature.

**ACs**:

1. Given T3 adapter plugin, When registered, Then capabilities verified (NETWORK allowed)
2. Given adapter submitOrder called, When exchange accepts, Then execution report forwarded to EMS
3. Given adapter disconnected, When reconnection fails 3 times, Then circuit breaker opens, alert emitted

**Tests**: adapter_register · adapter_submit · adapter_cancel · adapter_execReport · adapter_reconnect · adapter_signature · adapter_circuitBreaker

**Dependencies**: STORY-D02-010, K-04

---

### STORY-D02-012: Implement NEPSE trading adapter

**Feature**: D02-F03 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Concrete exchange adapter for NEPSE: implement NEPSE-specific protocol (FIX or proprietary API). Map NEPSE order types to OMS order types. Handle NEPSE session schedule. Parse NEPSE execution reports. Handle NEPSE-specific error codes and rejection reasons. NEPSE market data integration.

**ACs**:

1. Given order for NEPSE instrument, When submitted via adapter, Then NEPSE order placed successfully
2. Given NEPSE execution report, When received, Then parsed and mapped to OMS fill format
3. Given NEPSE-specific rejection, When received, Then mapped to standard rejection reason

**Tests**: nepse_submit · nepse_execReport · nepse_rejection · nepse_session · nepse_errorCodes · nepse_marketData

**Dependencies**: STORY-D02-011

---

### STORY-D02-013: Implement exchange adapter health monitoring

**Feature**: D02-F03 · **Points**: 2 · **Sprint**: 7 · **Team**: Beta

Monitor exchange adapter health: connection status, message rates, latency, rejections, heartbeat. Expose metrics: exchange_connected (gauge), exchange_latency_ms (histogram), exchange_rejections_total (counter). Alert on: connection loss, latency > threshold, rejection rate spike. Admin API: GET /execution/adapters for all adapter statuses.

**ACs**:

1. Given adapter connected, When metrics queried, Then latency, message rate, rejection rate available
2. Given connection lost, When detected, Then alert emitted, exchange_connected=0
3. Given admin query, When GET /execution/adapters, Then all adapters with status and metrics

**Tests**: health_connected · health_disconnected · health_latency · health_rejections · health_adminApi · health_alert

**Dependencies**: STORY-D02-011, K-06

## Feature D02-F04 — Transaction Cost Analysis (3 Stories)

---

### STORY-D02-014: Implement TCA data collection

**Feature**: D02-F04 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Collect TCA data for every execution: arrival_price, decision_price, exec_price, exec_time, market_conditions (volume, volatility, spread). Store in tca_records table. Link to order: order_id, fill_id. Capture market snapshots at key timestamps (order received, order routed, first fill, last fill).

**ACs**:

1. Given fill received, When TCA data collected, Then all price snapshots and market conditions stored
2. Given multi-fill order, When all fills received, Then aggregate TCA metrics calculated
3. Given TCA record, When queried, Then complete execution timeline with market context

**Tests**: tca_singleFill · tca_multiFill · tca_marketSnapshot · tca_aggregate · tca_storage · tca_query

**Dependencies**: STORY-D02-001, D-04

---

### STORY-D02-015: Implement TCA metrics calculation

**Feature**: D02-F04 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Calculate TCA metrics: implementation shortfall (exec_price - decision_price), market impact (exec_vwap - arrival_vwap), timing cost, opportunity cost. Benchmark comparison: vs VWAP, vs TWAP, vs arrival. Basis points calculation. Per-algo analysis: which algorithm performs best by metric.

**ACs**:

1. Given execution data, When shortfall calculated, Then correct bps relative to decision price
2. Given VWAP benchmark, When compared, Then excess cost vs VWAP in bps and amounts
3. Given multiple algorithms, When compared, Then ranked by average slippage

**Tests**: tca_shortfall · tca_marketImpact · tca_timingCost · tca_benchmarkComparison · tca_algoRanking · tca_bpsCalculation

**Dependencies**: STORY-D02-014

---

### STORY-D02-016: Implement TCA reporting and analytics

**Feature**: D02-F04 · **Points**: 2 · **Sprint**: 9 · **Team**: Beta

TCA reports: per-order analysis, daily summary, monthly broker scorecard. API: GET /execution/tca/:orderId, GET /execution/tca/report?type=daily&date=. Charts: execution scatter plot, slippage distribution, market impact by order size. PDF/CSV export.

**ACs**:

1. Given per-order TCA, When queried, Then full execution analysis with all metrics and timeline
2. Given daily report, When generated, Then aggregate TCA stats across all orders
3. Given CSV export, When downloaded, Then all TCA data for regulatory analysis

**Tests**: tca_perOrderReport · tca_dailySummary · tca_monthlyBroker · tca_export_csv · tca_export_pdf

**Dependencies**: STORY-D02-015

## Feature D02-F05 — Circuit Breaker Handling (2 Stories)

---

### STORY-D02-017: Implement exchange circuit breaker response

**Feature**: D02-F05 · **Points**: 2 · **Sprint**: 8 · **Team**: Beta

Handle exchange circuit breakers: when MarketHalted event received from D-04, EMS pauses all active algorithms, holds pending orders. When MarketResumed received, resume algorithms with recalculated pace. For individual instrument halts: pause only that instrument's orders. Queue new orders received during halt for execution on resume.

**ACs**:

1. Given MarketHalted event, When received, Then all active algorithms paused, pending orders held
2. Given MarketResumed event, When received, Then algorithms resume with adjusted pace
3. Given instrument-level halt, When received, Then only that instrument's orders paused

**Tests**: halt_pauseAlgos · halt_holdOrders · resume_algosResume · instrumentHalt_selective · queueDuringHalt · resume_pace_adjusted

**Dependencies**: D-04, STORY-D02-005

---

### STORY-D02-018: Implement execution failsafe and position reconciliation

**Feature**: D02-F05 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

Execution failsafe: if exchange adapter reports uncertain state (no response to order), implement position reconciliation. Query exchange for order status (getOrderStatus). Compare EMS state vs exchange state. Resolve discrepancies: if exchange filled but EMS unaware → create fill; if EMS shows filled but exchange doesn't → flag for manual review. EOD reconciliation.

**ACs**:

1. Given EMS uncertain about order, When exchange queried, Then states reconciled
2. Given exchange shows fill, EMS doesn't, When reconciled, Then fill created retroactively
3. Given EOD reconciliation, When run, Then all EMS vs exchange positions compared and breaks flagged

**Tests**: failsafe_queryExchange · failsafe_missingFill · failsafe_discrepancy_flagged · eod_recon · eod_matchAll · eod_breaks

**Dependencies**: STORY-D02-011, D-01

## Feature D02-F06 — Partial Fill Aggregation (2 Stories)

---

### STORY-D02-019: Implement fill aggregation and average price calculation

**Feature**: D02-F06 · **Points**: 2 · **Sprint**: 8 · **Team**: Beta

Aggregate multiple partial fills into parent order status. Track per fill: fill_id, exec_id (exchange), fill_qty, fill_price, fill_timestamp, fees. Calculate weighted average price: avg_price = Σ(fill_qty × fill_price) / Σ(fill_qty). Track total_filled, remaining_qty. Emit FillAggregated event.

**ACs**:

1. Given 3 fills (100@500, 200@510, 100@505), When aggregated, Then avg_price = (50000+102000+50500)/400 = 505.625
2. Given fill aggregated, When remaining_qty = 0, Then parent order → FILLED
3. Given fills from different sources, When aggregated, Then all included in avg_price

**Tests**: aggregate_multiFill · aggregate_avgPrice · aggregate_remaining · aggregate_zerRemaining_filled · aggregate_event · aggregate_fees

**Dependencies**: STORY-D02-001, D-01

---

### STORY-D02-020: Implement execution report generation

**Feature**: D02-F06 · **Points**: 2 · **Sprint**: 9 · **Team**: Beta

Generate execution reports for each fill: trade confirmation details, fees breakdown, settlement info. Report includes: order_id, fill_id, instrument, side, qty, price, fees, net_amount, settlement_date (from K-15 T+n calc), trade_date. Push to D-09 post-trade. Client-visible execution report API.

**ACs**:

1. Given fill received, When execution report generated, Then all trade details with settlement date
2. Given settlement date calculation, When T+2 from fill_date, Then K-15 BS business day calculation used
3. Given client queries executions, When GET /execution/reports/:orderId, Then all fills with reports

**Tests**: report_generation · report_settlementDate · report_fees · report_pushToPostTrade · report_clientApi · report_bsCalendar

**Dependencies**: STORY-D02-019, D-09, K-15

---

## Feature D01-F07 — AI Pre-Trade Intelligence (3 Stories)

---

### STORY-D01-019: Implement ML pre-trade market impact prediction

**Feature**: D01-F07 · **Points**: 5 · **Sprint**: 7 · **Team**: Beta

Integrate an ML model (gradient-boosted regressor, governed by K-09) that predicts expected market impact (basis points) before an order is released to D-02 EMS. Features: order_size ÷ ADV, instrument volatility (σ), bid-ask spread, current order-book depth (L1/L2 from D-04), hour-of-day, recent participation rate. Output: predicted_impact_bps (± confidence interval). Impact estimate surfaced in OMS order blotter and pre-trade risk check (D01-F03): if predicted_impact > configurable threshold, trigger maker-checker escalation. Model trained nightly on D-04 historical fills + D-06 volatility surfaces; registered and monitored via K-09 (drift, SHAP explainability, HITL).

**ACs**:

1. Given a large order (quantity > 5% of ADV), When submitted, Then predicted_impact_bps computed and attached to order, and if > 50bps threshold, maker-checker escalation triggered automatically
2. Given predicted impact model, When SHAP explanation requested, Then per-feature contributions returned (e.g., "order size/ADV contributed +35bps, low liquidity contributed +12bps")
3. Given market regime shift detected by K-09 drift monitor (PSI > 0.2 on feature distributions), When triggered, Then retraining pipeline runs automatically; model rollback available if new model underperforms

**Tests**: impact_large_order_escalation · impact_small_order_passes · shap_feature_contributions · maker_checker_trigger · drift_retrain · model_k09_registration · perf_prediction_under_100ms

**Dependencies**: K-09 (AI governance), D-04 (market data), D-06 (volatility), D01-F03 (pre-trade pipeline)

---

### STORY-D01-020: Implement AI order type and execution strategy suggester

**Feature**: D01-F07 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

Advisory AI model that recommends optimal order type (LIMIT / MARKET / ICEBERG / TWAP / VWAP) and execution parameters based on real-time market conditions and order intent. Features: urgency_flag (client-provided), order_size_ratio, realized_spread, current_volume_pace (from D-04), time_to_close, instrument_sector. Recommendation presented to trader/PM in OMS UI with confidence score and SHAP rationale; final decision always human. Governed by K-09 as an Advisory-tier model. Recommendation logged via K-07 for audit.

**ACs**:

1. Given an order with urgency = NORMAL and size = 3% ADV in a high-spread environment, When AI suggests, Then recommendation = TWAP with 2hr execution window, confidence ≥ 0.75, and SHAP showing "high spread" as top factor
2. Given recommendation displayed to trader, When trader accepts, Then selected strategy applied and acceptance recorded; When trader overrides, Then override reason captured and fed back to K-09 HITL feedback loop
3. Given K-09 HITL override rate for this model exceeds 30%, When threshold breached, Then ModelOverrideRateAlert emitted and model flagged for retraining review

**Tests**: suggest_twap_high_spread · suggest_market_urgent · suggest_iceberg_large · hitl_override_capture · override_rate_alert · shap_rationale · k09_advisory_tier · audit_log_via_k07

**Dependencies**: D-04, K-09 (AI governance — K09-012, K09-006), K-07

---

### STORY-D01-021: Implement pre-trade ML cost estimation model

**Feature**: D01-F07 · **Points**: 3 · **Sprint**: 7 · **Team**: Beta

ML model predicting total expected execution cost (implementation shortfall) before order placement: total_cost_bps = impact_bps + spread_cost + timing_risk∓ fee. Built on top of STORY-D01-019 impact model; adds spread cost (from D-04 real-time spread), timing risk (σ × √T), and T3 broker fee schedule. Estimate compared post-execution against actual TCA (D-02-F04) to continuously measure model error; RMSE tracked in K-09 performance metrics. Cost estimate surfaces in the order blotter, and in pre-trade compliance check: if estimated cost exceeds client mandate cost limits, order blocked with reason.

**ACs**:

1. Given order with known spread, impact estimate, and fee schedule, When cost model runs, Then total_cost_bps = impact + spread + timing_risk + fee with ± 95% confidence interval returned
2. Given estimated cost > client mandate cost limit (e.g., > 75bps), When pre-trade check runs, Then order blocked with reason "estimated execution cost 82bps exceeds mandate limit 75bps"
3. Given post-execution TCA data available, When RMSE evaluated, Then model error tracked in K-09 performance dashboard; if RMSE degrades >20% over 30-day rolling window, ConceptDriftDetected event triggered

**Tests**: cost_estimate_accuracy · cost_estimate_confidence_interval · mandate_limit_block · tca_reconciliation · rmse_tracking · concept_drift_trigger · k09_performance_dashboard · perf_estimate_under_50ms

**Dependencies**: STORY-D01-019, D-02-F04 (TCA), D-04 (spread), K-09 (AI governance)

---

## Feature D02-F07 — ML-Adaptive Smart Order Routing (2 Stories)

---

### STORY-D02-021: Implement ML venue toxicity and opportunity scoring

**Feature**: D02-F07 · **Points**: 5 · **Sprint**: 9 · **Team**: Beta

Replace static venue scoring in D-02-F01 with a real-time ML venue opportunity model. Features per venue (rolling 5T window): fill_rate, average_slippage_bps, queue_position_estimate, adverse_selection_ratio (fills followed by unfavorable price move within 100ms), latency_percentile. Model: LightGBM classifier outputs opportunity_score (0–1) per venue per instrument segment. Toxic venue (adverse selection > threshold) dynamically de-ranked. Model governed by K-09; SHAP values show why venue is scored high/low. Venue score updates every 5 seconds from D-04 L2 feed.

**ACs**:

1. Given venue A showing 40% adverse selection ratio in the last 5T, When ML scorer runs, Then venue A opportunity_score drops below 0.3 and orders routed away from it; SHAP shows "adverse_selection_ratio" as top negative feature
2. Given venue B improving fill_rate from 65% to 88% over 30T, When model re-evaluates, Then venue B score increases and it receives proportionally more order flow
3. Given model registered in K-09, When drift on venue feature distributions detected (new venue added, microstructure change), Then FeatureDriftDetected event emitted; model auto-retrained within 24h

**Tests**: toxic_venue_demotion · improving_venue_promotion · shap_per_venue · adverse_selection_detection · feature_drift_retrain · score_update_frequency · perf_scoring_under_50ms · k09_drift_integration

**Dependencies**: D02-F01 (SOR), D-04 (L2 feed), K-09 (AI governance), K-05

---

### STORY-D02-022: Implement reinforcement learning execution strategy optimizer

**Feature**: D02-F07 · **Points**: 8 · **Sprint**: 9 · **Team**: Beta

Deploy a contextual bandit / RL agent (PPO or Deep Q-Network) that learns optimal child-order scheduling for VWAP/TWAP/IS strategies by maximizing implementation shortfall minimization vs a VWAP benchmark. State: remaining_quantity, elapsed_time_fraction, real-time volume_pace, bid-ask spread, order book imbalance. Action: participation_rate for next interval (5T bins). Reward: negative implementation shortfall vs VWAP. Policy trained offline on 90 days of historical execution data; updated monthly. Online exploration via epsilon-greedy (ε = 0.05 in production). Governed by K-09 (risk-tiered TIER_3 HITL requirement for live trading); shadow mode available for evaluation before production promotion. Human override available at any time via D-01 blotter.

**ACs**:

1. Given a VWAP order in a day with unusual volume distribution (e.g., single large block trade shifts volume curve), When RL agent adapts participation rate in response, Then implementation shortfall ≤ benchmark VWAP + 2bps vs ≥ 8bps for static schedule
2. Given RL agent in production, When human trader overrides its participation rate via blotter, Then override takes effect immediately, override logged via K-07, and feedback incorporated into offline reward function for subsequent training
3. Given K-09 TIER_3 model governance, When RL agent model version updated, Then requires champion-challenger shadow evaluation for 5 trading days, senior approval via maker-checker before promotion to live

**Tests**: vwap_improvement_vs_static · volume_curve_adaptation · human_override_immediate · hitl_tier3_requirement · shadow_evaluation · champion_challenger · offline_training · perf_action_under_10ms · k09_tier3_governance

**Dependencies**: D02-F02 (execution algorithms), K-09 (AI governance — K09-014, K09-011), K-07, D-04

---

## Feature D06-F07 — AI Risk Intelligence (2 Stories)

---

### STORY-D06-020: Implement ML market regime detection for conditional VaR

**Feature**: D06-F07 · **Points**: 5 · **Sprint**: 8 · **Team**: Gamma

Deploy a Hidden Markov Model (HMM) with 3 regimes (low-volatility/trending, high-volatility/mean-reverting, crisis/tail) trained on NEPSE market returns, volatility, and liquidity indicators from D-04. Current regime inferred in real time. Regime conditions D-06-F02 VaR calculation: each regime uses its own calibrated covariance matrix and tail-risk parameters — crisis regime uses fat-tailed (Student-t) distribution and higher correlation assumptions. Regime label and transition probabilities surfaced in risk dashboard (D06-019). RegimeChanged event emitted on transition with SHAP explanation of driving factors. Governed by K-09.

**ACs**:

1. Given market data suggesting elevated volatility and falling liquidity (crisis regime indicators), When HMM classifies regime, Then regime = CRISIS emitted, VaR engine switches to fat-tailed parameters, and estimated VaR increases by ≥ 30% vs normal-regime VaR for the same portfolio
2. Given RegimeChanged event (normal → high-volatility), When risk dashboard refreshed, Then regime indicator updated, SHAP explanation shows "realized_vol (+40%), bid-ask spread (+60%) drove regime transition"
3. Given regime model registered in K-09, When daily drift check shows feature PSI > 0.2 (market structure change), Then automatic retraining triggered on latest 180 days of data

**Tests**: regime_hmm_3_state · crisis_var_increase · regime_changed_event · shap_transition_factors · dashboard_regime_label · drift_retrain · fat_tail_params · perf_regime_inference_under_50ms

**Dependencies**: D06-F02 (VaR), D-04 (market data), K-09 (AI governance), K-05

---

### STORY-D06-021: Implement AI-generated stress scenario library

**Feature**: D06-F07 · **Points**: 3 · **Sprint**: 8 · **Team**: Gamma

Use a generative ML approach (Variational Autoencoder trained on historical crisis episodes: NEPSE 2008-2010, 2016 liquidity crisis, COVID-2020 crash) to synthesize novel stress scenarios beyond the hard-coded historical set in D06-F06. VAE latent space allows interpolation between crises and extrapolation of extreme-but-plausible tail scenarios. Output: 50 AI-generated scenario vectors per run (return shocks, vol spikes, correlation breaks, liquidity haircuts) in addition to the standard D06-F06 historical scenarios. Scenarios validated by K-09 bias check (no scenario more extreme than a 10-sigma event). Risk officer reviews AI scenarios via HITL workflow before use in regulatory stress reports. All scenarios stored in K-08-cataloged scenario library.

**ACs**:

1. Given VAE trained on historical crisis data, When scenario generation runs, Then 50 novel scenario vectors produced: each validated to be within plausible range (< 10-sigma), no clones of historical scenarios (cosine similarity < 0.95 to any historical)
2. Given AI-generated scenarios, When submitted to HITL review, Then risk officer sees scenario in portfolio impact terms (e.g., "−18% NAV, spread widening +200bps"), approves or rejects; approved scenarios enter regulatory stress library
3. Given AI scenario library used in quarterly stress report, When generated, Then all scenario sources labelled (HISTORICAL vs AI_GENERATED) with K-09 model version reference for auditability

**Tests**: vae_generate_50_scenarios · plausibility_10_sigma_cap · no_historical_clones · hitl_review_workflow · scenario_portfolio_impact · regulatory_report_labelling · k09_model_ref · bias_check

**Dependencies**: D06-F06 (stress testing), K-09 (AI governance — K09-012, K09-007), K-08

---

## Feature D07-F07 — AI-Assisted Compliance Intelligence (3 Stories)

---

### STORY-D07-015: Implement NLP adverse media screening engine

**Feature**: D07-F07 · **Points**: 5 · **Sprint**: 7 · **Team**: Gamma

Deploy NLP-powered adverse media screening as part of the KYC/AML pipeline (D07-F03). On client onboarding and periodic review: query configurable news/media sources (structured RSS feeds, curated news API) for client entity name + aliases. Screening model: fine-tuned BERT-class classifier (e.g., `finbert-adverse-media`) categorising articles as: ADVERSE_FINANCIAL_CRIME, ADVERSE_REGULATORY, ADVERSE_POLITICAL_EXPOSURE, ADVERSE_LITIGATION, NONE. Results aggregated per client: risk_signal_count per category. High-risk signals → escalate to EDD workflow. Model governed by K-09 (advisory tier, SHAP rationale for each article classification).

**ACs**:

1. Given client entity "Acme Securities" with 3 news articles — one discussing money laundering charges in India, one routine market update, one about regulatory fine — When screened, Then 1 ADVERSE_FINANCIAL_CRIME and 1 ADVERSE_REGULATORY signals detected; the routine article classified NONE; EDD escalation triggered due to financial crime signal
2. Given classification of the money laundering article, When SHAP explanation requested, Then top contributing tokens returned (e.g., "laundering" +0.42, "charged" +0.31, "Nepal Rastra Bank" +0.18)
3. Given article in Nepali language, When screened, Then translated (via translation preprocessor) and screened; translated_text stored with result for audit

**Tests**: adverse_financial_crime_detection · adverse_regulatory_detection · none_classification · edd_escalation_trigger · shap_token_contributions · nepali_translation_preprocessing · multi_article_aggregation · audit_log · perf_screening_under_2sec_per_entity

**Dependencies**: D07-F03 (KYC/AML), K-09 (AI governance — K09-001, K09-004), K-07 (audit)

---

### STORY-D07-016: Implement ML suspicious transaction risk scoring

**Feature**: D07-F07 · **Points**: 5 · **Sprint**: 7 · **Team**: Gamma

Continuously score every transaction for AML suspicion risk using a gradient-boosted risk model (XGBoost, governed by K-09 TIER_3 supervised). Features: transaction_amount_velocity (24h rolling), unusual_counterparty (first time seen), instrument_type_mismatch (unusual for client's historical profile), time_of_day_anomaly, jurisdiction_risk_score, round_number_amount (structuring indicator), deviation_from_peer_group_behaviour. Score 0–1: < 0.4 = LOW, 0.4–0.7 = MEDIUM (flag for monitoring), ≥ 0.7 = HIGH (STR candidate). HIGH-scored transactions → auto-created STR case in compliance workflow. SHAP per transaction for compliance officer explanation. False positives tracked; feedback loop retrains model monthly.

**ACs**:

1. Given a transaction with round-number amount in a jurisdiction with high risk score, placed at 2:00 AM by a client whose typical trading is 9 AM–4 PM, When model scores it, Then risk_score ≥ 0.7 (HIGH), STR case auto-created with SHAP pointing to "time_of_day +0.28, round_number +0.22, jurisdiction_risk +0.18"
2. Given compliance officer marks an STR case as FALSE_POSITIVE with reason, When feedback submitted, Then case label stored and feeds into next monthly model retraining; false_positive_rate tracked in K-09 governance dashboard
3. Given TIER_3 K-09 governance, When model deployed, Then HITL review required before any STR auto-creation; model card and bias report required; approval by Compliance Head

**Tests**: high_risk_str_autocreation · medium_risk_flag · low_risk_pass · shap_per_transaction · false_positive_feedback · monthly_retrain · bias_check · hitl_tier3_approval · perf_scoring_under_200ms

**Dependencies**: D07-F03 (KYC/AML), K-09 (AI governance — K09-012, K09-013, K09-014), K-07

---

### STORY-D07-017: Implement AI-assisted EDD due diligence copilot

**Feature**: D07-F07 · **Points**: 3 · **Sprint**: 7 · **Team**: Gamma

LLM-powered copilot (RAG-based, retrieval over K-07 audit records, prior EDD reports, adverse media results, K-03 compliance rules) that assists compliance officers during Enhanced Due Diligence. Capabilities: (1) auto-generate a structured EDD draft report from all available signals (adverse media results, STR history, ownership structure, jurisdiction); (2) answer natural-language compliance queries ("What are the FATF red flags for this client?"); (3) suggest specific verification steps based on risk profile. Copilot responses are advisory only — human compliance officer reviews and signs off. All LLM interactions logged via K-07. Model (LLM call + retrieval) governed by K-09 advisory tier.

**ACs**:

1. Given client in EDD review with 2 adverse media signals and 1 prior STR, When compliance officer opens EDD copilot, Then structured draft report auto-generated covering: risk summary, adverse media findings, STR context, suggested verification steps — all sourced from retrieved records with citations
2. Given compliance officer asks "Is this client's transaction pattern consistent with FATF Recommendation 16 red flags?", When copilot responds, Then answer cites specific K-03 compliance rules and relevant prior audit events with source references
3. Given EDD copilot interaction, When logged, Then full prompt, retrieved context snippets, LLM response, and officer action (accept/modify/reject) all stored immutably in K-07 audit trail

**Tests**: draft_edd_report_generation · citation_accuracy · fatf_query_answer · compliance_rule_citation · audit_log_prompt_response · officer_override_logged · rag_retrieval_accuracy · k09_advisory_tier · k07_immutable_log

**Dependencies**: D07-F03 (KYC/AML), D07-015, D07-016, K-07 (audit), K-09 (AI governance), K-03 (rules)

---

## Feature D14-F07 — Vector Embedding Sanctions Screening (2 Stories)

---

### STORY-D14-015: Implement transformer embedding-based entity name matching

**Feature**: D14-F07 · **Points**: 5 · **Sprint**: 7 · **Team**: Gamma

Replace/augment rule-based fuzzy string matching (D14-F02) in the sanctions screening engine with transformer embedding-based semantic similarity. Model: multilingual sentence-transformer (e.g., `paraphrase-multilingual-MiniLM-L12-v2`) fine-tuned on sanctions entity pairs (positive: same entity with transliterations/aliases; negative: different entities with similar names). Screening flow: query entity name → encode to 384-dim embedding → ANN (Approximate Nearest Neighbour) search via pgvector against pre-encoded sanctions list embeddings. Candidates ranked by cosine similarity; threshold configurable per screening context (onboarding: 0.85; transaction: 0.80). Captures: transliterations (Nepali/Devanagari↔Latin), abbreviations, aliases, spelling variants — which rule-based Levenshtein misses. Governed by K-09.

**ACs**:

1. Given query name "Ram Prasad Shrestha" vs sanctioned entity "R.P. Srestha" (transliteration variant), When embedding-based screened, Then match returned with similarity 0.88 (above threshold 0.85); rule-based Levenshtein would score < 0.5 due to abbreviation
2. Given query name "Himalayan Trade Corp" vs sanctioned "Himalayan Trading Corporation", When screened, Then match with similarity 0.91; vs unrelated "Himalayan Bank Ltd", Then similarity 0.61 (below threshold, no alert)
3. Given sanctions list updated (new entries added), When re-indexing runs, Then all new entries encoded and added to pgvector index within 2 minutes; screening resumes with updated index

**Tests**: transliteration_match · abbreviation_match · false_positive_dissimilar · threshold_configurable · multilingual_devanagari · ann_search_accuracy · index_update_latency · perf_screening_under_100ms_per_entity · k09_model_registration

**Dependencies**: D14-F02 (fuzzy matching), K-09 (AI governance), pgvector

---

### STORY-D14-016: Implement graph network risk scoring for PEP networks

**Feature**: D14-F07 · **Points**: 5 · **Sprint**: 7 · **Team**: Gamma

Build a graph-based PEP and sanctions network risk scorer using Graph Neural Networks (GNN — GraphSAGE variant). Graph: nodes = entities (clients, beneficial owners, counterparties, directors); edges = relationships (ownership %, director, family, business partner). GNN encodes each entity's network context; entity risk score elevated if within N-hops of a known PEP, sanctioned entity, or high-risk jurisdiction entity. Risk propagation: direct association (+high risk), 2-hop (+medium), 3-hop (+low). Graph updated nightly from K-08 beneficial ownership data and D-07 entity relationships. Network risk score added to overall sanction/PEP risk profile. SHAP (via GNN explainability) shows which connections drove the score. Governed by K-09.

**ACs**:

1. Given client X who is 2-hop connected to known PEP (client X → Company A → PEP director), When GNN scores X, Then network_risk_score = MEDIUM (2-hop), with SHAP explanation: "connected to PEP [name] via Company A, 2 hops — contributed +0.35 to risk score"
2. Given client with no PEP/sanction connections in graph, When scored, Then network_risk_score = LOW; adding a new known-sanctioned entity as 1-hop counterparty in K-08 triggers re-scoring within 4 hours
3. Given GNN model and graph updated with new PEP list entries, When K-09 drift monitor checks, Then if entity distribution shifts (new political class, restructured ownership), model retraining triggered

**Tests**: direct_pep_high_risk · two_hop_medium_risk · no_connection_low_risk · shap_network_explanation · graph_update_retrigger_scoring · nightly_graph_refresh · perf_scoring_under_500ms · k09_gnn_governance

**Dependencies**: D14-F02 (sanctions screening), D07-F04 (beneficial ownership), K-08 (data governance), K-09 (AI governance)

---

## Feature D11-F06 — ML Reference Data Intelligence (2 Stories)

---

### STORY-D11-012: Implement ML entity resolution and deduplication

**Feature**: D11-F06 · **Points**: 5 · **Sprint**: 7 · **Team**: Gamma

Deploy an ML entity resolution model (blocking + record linkage using a fine-tuned BERT model or gradient boosting on feature pairs) to detect and merge duplicate entity records in the reference data master (D11-F02). Feature pairs: name_similarity (transformer cosine), address_similarity (Jaro-Winkler), registration_number_match, jurisdiction_match, bvs_id_crossref. Blockers: first 3 chars of cleaned name, jurisdiction. Linkage model outputs match probability 0–1; > 0.92 = automatic merge candidate (reviewed via maker-checker), 0.75–0.92 = manual review queue. Duplicate_EntityResolutionAlert event. Governed by K-09 (autonomous tier). Runs nightly and on new entity registration.

**ACs**:

1. Given two entity records "ABC Securities Pvt Ltd" and "A.B.C. Securities Private Limited" with different registration IDs but same jurisdiction and address, When ML entity resolution runs, Then match probability 0.94 (> 0.92), merged as duplicate candidate flagged for maker-checker
2. Given match probability 0.80 (medium confidence), When classified, Then added to manual review queue with side-by-side comparison and model confidence; reviewer can merge, keep-separate, or mark as needs-more-info
3. Given new entity registered, When triggered, Then ML entity resolution runs for the new entity against all existing within same jurisdiction blockers within 60 seconds; result surfaced before entity marked ACTIVE

**Tests**: high_confidence_auto_flag · medium_confidence_manual_queue · new_entity_realtime_check · blocking_efficiency · maker_checker_merge · false_positive_rate · k09_autonomous_tier · perf_nightly_100k_entities_under_30min

**Dependencies**: D11-F02 (entity master), K-09 (AI governance)

---

### STORY-D11-013: Implement AI-assisted instrument classification and enrichment

**Feature**: D11-F06 · **Points**: 3 · **Sprint**: 7 · **Team**: Gamma

Deploy an NLP classification model that automatically classifies new instruments and enriches their reference data attributes. On ingestion of a new instrument record with minimal metadata (name, issuer, prospectus text snippet), model infers: asset_class (EQUITY/BOND/MUTUAL_FUND/ETF/DERIVATIVE), sector (BANKING/ENERGY/TELECOM/…), instrument_risk_tier (LOW/MEDIUM/HIGH). Features: text embedding of instrument name + description (sentence-transformer), rule-based regex signals (ISIN prefix, maturity_date presence → BOND), issuer_type from D-11 entity master. Multi-label classifier. Result populated into instrument master; analyst reviews and confirms via HITL workflow (K-09 advisory). Data quality gate: if classification confidence < 0.70, mandatory manual classification. Governed by K-09.

**ACs**:

1. Given new NEPSE-listed equity "Sunrise Bank Limited" ingested with issuer_type = BANK, When AI classifier runs, Then asset_class = EQUITY, sector = BANKING, risk_tier = LOW with confidence 0.91; record marked pending-analyst-confirmation
2. Given instrument with confidence = 0.60 (< 0.70 threshold), When data quality gate evaluates, Then instrument flagged for mandatory manual classification, not auto-populated, alert to data steward
3. Given analyst confirms/overrides AI classification, When submitted, Then final classification stored, original AI classification + confidence preserved as metadata, override logged in K-07 for training feedback

**Tests**: equity_classification_high_confidence · bond_classification_from_maturity · confidence_threshold_mandatory_manual · multi_label_accuracy · hitl_confirmation · override_feedback_logged · k07_audit · k09_advisory_tier · perf_classification_under_500ms

**Dependencies**: D11-F02 (instrument master), K-09 (AI governance — K09-012), K-07

---

# MILESTONE 2A SUMMARY

| Epic                     | Feature Count | Story Count | Total SP |
| ------------------------ | ------------- | ----------- | -------- |
| D-11 Reference Data      | 6             | 13          | 35       |
| D-04 Market Data         | 6             | 15          | 37       |
| D-01 OMS                 | 7             | 21          | 64       |
| D-07 Compliance          | 7             | 17          | 46       |
| D-14 Sanctions Screening | 7             | 16          | 47       |
| D-06 Risk Engine         | 7             | 21          | 70       |
| D-02 EMS                 | 7             | 22          | 73       |
| **TOTAL**                | **47**        | **125**     | **372**  |

**Sprint 5**: D-11 (001-011), D-04 (001-004,014), D-07 (001-005,011) (~25 stories)
**Sprint 6**: D-04 (005-013,015), D-01 (001-007), D-07 (006), D-14 (001-006), D-06 (001-003,009) (~35 stories)
**Sprint 7**: D-01 (008-021), D-07 (007-017), D-14 (007-016), D-06 (002-003,020-021), D-02 (001-002,010-013), D-11 (012-013) (~50 stories — includes all AI intelligence stories)
**Sprint 8**: D-06 (004-006,010,012-014), D-02 (003-009,017-019) (~15 stories)
**Sprint 9**: D-02 (021-022) (~2 stories)
