EPIC-ID: EPIC-D-04
EPIC NAME: Market Data
LAYER: DOMAIN
MODULE: D-04 Market Data
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the D-04 Market Data module to ingest, normalize, and distribute real-time and historical market data feeds across the platform. This epic adheres to Principle 3 (Exchange Adapters) by ensuring that specific feed protocols (e.g., NEPSE multicast, Bloomberg B-PIPE) are handled by T3 Adapter Packs, while D-04 provides the generic normalization, distribution, and circuit breaker detection framework.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Real-time feed normalization to standard internal formats.
  2. Order book building (L1/L2/L3) and dissemination.
  3. Historical data storage and retrieval.
  4. Market halt/circuit breaker detection and event emission.
  5. Integration with T3 Market Data Adapters.
- **Out-of-Scope:**
  1. Proprietary exchange feed protocol parsing (handled by T3 Adapters).
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-04 (Plugin Runtime), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** K-02, K-03, K-04, K-05
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Data Normalization:** Convert disparate feed formats from T3 Adapters into a standard internal `MarketTick` schema.
2. **FR2 Distribution:** Publish normalized ticks and aggregated order books to K-05 Event Bus for consumption by OMS, EMS, and Pricing modules.
3. **FR3 Circuit Breaker Detection:** Monitor price movements and compare against static limits (e.g., ±10%) loaded via K-02 Config Engine. Emit `MarketHaltEvent` if breached.
4. **FR4 Feed Arbitration:** Support primary/secondary feed arbitration to handle drops or latency spikes in upstream feeds transparently.
5. **FR5 Historical Storage:** Persist tick data and end-of-day floorsheets for historical replays and backtesting.
6. **FR6 Dual-Calendar:** Ensure that daily OHLC (Open, High, Low, Close) candles are indexed with both `date_gregorian` and `date_bs`.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The normalization engine, order book builder, and distribution mechanism are generic.
2. **Jurisdiction Plugin:** Specific feed connections (e.g., NEPSE real-time feed) are T3 Adapter Packs. Circuit breaker rules (e.g., Nepal's 4%, 5%, 6% halt thresholds) are T2 Rule Packs or T1 Config Packs.
3. **Resolution Flow:** Config Engine dictates which circuit breaker rules apply to which exchange.
4. **Hot Reload:** Dynamic updates to index constituents or circuit thresholds.
5. **Backward Compatibility:** Historical market data stored in generic schema permanently.
6. **Future Jurisdiction:** A new exchange just requires a new T3 Feed Adapter and T1 Circuit Breaker Config.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `MarketTick`: `{ symbol: String, price: Decimal, size: Int, timestamp: Timestamp, feed_source: String }`
  - `DailyOHLC`: `{ symbol: String, open: Decimal, high: Decimal, low: Decimal, close: Decimal, volume: BigInt, date_greg: Date, date_bs: String }`
- **Dual-Calendar Fields:** `date_bs` in historical daily summaries.
- **Event Schema Changes:** `MarketTickEvent`, `MarketHaltEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                          |
| ----------------- | ---------------------------------------------------------------------------------------------------- |
| Event Name        | `MarketHaltEvent`                                                                                    |
| Schema Version    | `v1.0.0`                                                                                             |
| Trigger Condition | Price movement exceeds configured circuit breaker limit, or exchange adapter reports a manual halt.  |
| Payload           | `{ "symbol": "NABIL", "exchange": "NEPSE", "halt_reason": "CIRCUIT_BREAKER", "resume_time": "..." }` |
| Consumers         | OMS, EMS, Client Portal                                                                              |
| Idempotency Key   | `hash(symbol + exchange + halt_timestamp)`                                                           |
| Replay Behavior   | Ignored for live systems; updates historical halt records.                                           |
| Retention Policy  | Permanent.                                                                                           |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `SubscribeCommand`                                                   |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Symbol valid, feed available, subscriber authorized                  |
| Handler          | `SubscriptionCommandHandler` in D-04 Market Data                     |
| Success Event    | `SubscriptionCreated`                                                |
| Failure Event    | `SubscriptionFailed`                                                 |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `UnsubscribeCommand`                                                 |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Subscription exists, requester authorized                            |
| Handler          | `SubscriptionCommandHandler` in D-04 Market Data                     |
| Success Event    | `SubscriptionCancelled`                                              |
| Failure Event    | `UnsubscribeFailed`                                                  |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `HaltMarketCommand`                                                  |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Circuit breaker triggered, symbol valid, requester authorized        |
| Handler          | `CircuitBreakerHandler` in D-04 Market Data                          |
| Success Event    | `MarketHaltEvent`                                                    |
| Failure Event    | `MarketHaltFailed`                                                   |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Inbound feed processing.
- **Model Registry Usage:** `feed-anomaly-detector-v1`
- **Explainability Requirement:** AI detects bad ticks (e.g., decimal place error from upstream) and suppresses them before they hit the order book, logging the suppression reason.
- **Human Override Path:** Operator can force-accept a rejected tick.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard standard deviation bound checks.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                |
| ------------------------- | --------------------------------------------------------------- |
| Latency / Throughput      | Ingest-to-publish latency < 500 microseconds; 100,000 ticks/sec |
| Scalability               | Multicast or low-latency pub/sub internally                     |
| Availability              | 99.999% during market hours                                     |
| Consistency Model         | Strict ordering per symbol                                      |
| Security                  | Internal mTLS                                                   |
| Data Residency            | N/A (public data)                                               |
| Data Retention            | 10 years minimum for tick data                                  |
| Auditability              | Manual price corrections logged                                 |
| Observability             | Metrics: `feed.latency`, `tick.drop.rate`                       |
| Extensibility             | New feed adapter < 1 sprint                                     |
| Upgrade / Compatibility   | N/A                                                             |
| On-Prem Constraints       | Can run local hardware acceleration for feeds                   |
| Ledger Integrity          | N/A                                                             |
| Dual-Calendar Correctness | Correct OHLC bucket mappings                                    |

---

#### Section 10 — Acceptance Criteria

1. **Given** a raw FIX message from a T3 adapter, **When** ingested, **Then** it is normalized to `MarketTick` and published in < 500 microseconds.
2. **Given** a Nepal T1 config defining a 10% daily price limit, **When** a trade hits +10.1%, **Then** the module immediately suppresses further order book updates and emits `MarketHaltEvent`.
3. **Given** an EOD process, **When** saving the daily summary, **Then** both Gregorian and Bikram Sambat dates are persisted correctly.

---

#### Section 11 — Failure Modes & Resilience

- **Primary Feed Down:** Instantly failover to secondary feed (e.g., from direct exchange feed to Reuters/Bloomberg) with an alert.
- **Stale Data:** Mark data as 'Stale' if no ticks received within heartbeat window.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                   |
| ------------------- | -------------------------------------------------- |
| Metrics             | `tick.processing.latency`, `feed.disconnect.count` |
| Logs                | Halt events                                        |
| Traces              | N/A (Tracing skipped for high-freq ticks)          |
| Audit Events        | Action: `ManualHaltOverride`                       |
| Regulatory Evidence | Floorsheet generation [ASR-RPT-001]                |

---

#### Section 13 — Compliance & Regulatory Traceability

- Market integrity and circuit breaker compliance [ASR-CB-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `MarketDataClient.subscribe(symbol)`, `MarketDataClient.getSnapshot(symbol)`, `MarketDataClient.getOHLC(symbol, interval)`.
- **Jurisdiction Plugin Extension Points:** T3 Feed Adapters (e.g., NEPSE real-time feed, Bloomberg B-PIPE).

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `MarketDataUpdated` | New tick/quote received | `instrument_id`, `bid`, `ask`, `last`, `volume`, `exchange`, `timestamp_bs` |
| `MarketDataStale` | Feed timeout exceeded | `instrument_id`, `exchange`, `last_update_age_ms` |
| `CircuitBreakerTriggered` | Exchange circuit breaker hit | `instrument_id`, `exchange`, `breaker_type`, `trigger_price` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `InstrumentActivated` | D-11 Reference Data | Subscribe to new instruments |
| `TradingDayStarted` | K-15 Dual-Calendar | Feed initialization |

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                             |
| ---------------------------------------------------- | ------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                                        |
| Can a new exchange be connected?                     | Yes, via T3 Adapter.                        |
| Can this run in an air-gapped deployment?            | Partially; requires external feed adapters. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Market Data Poisoning**
   - **Threat:** Attacker injects false market data to manipulate trading decisions.
   - **Mitigation:** Feed arbitration across multiple sources; AI anomaly detection flags suspicious ticks; cryptographic signing of feed data; feed integrity monitoring.
   - **Residual Risk:** Coordinated attack across multiple feed providers.

2. **Feed Adapter Compromise**
   - **Threat:** Malicious T3 Feed Adapter manipulates or exfiltrates market data.
   - **Mitigation:** K-04 sandbox isolation; cryptographic signing; network access restrictions; all adapter calls logged; feed data validation.
   - **Residual Risk:** Supply chain attack on adapter.

3. **Denial of Service via Data Flood**
   - **Threat:** Attacker floods system with market data to cause performance degradation.
   - **Mitigation:** Rate limiting per feed; circuit breakers; backpressure handling; resource quotas per adapter.
   - **Residual Risk:** Distributed attack from multiple sources.

4. **Market Data Theft**
   - **Threat:** Unauthorized access to proprietary market data feeds.
   - **Mitigation:** Encryption in transit and at rest; strict RBAC; entitlement management; all access logged; data classification.
   - **Residual Risk:** Insider threat with legitimate entitlements.

5. **Circuit Breaker Manipulation**
   - **Threat:** False circuit breaker triggers to halt trading.
   - **Mitigation:** Circuit breaker logic in T2 Rule Packs with maker-checker; multiple data sources required for confirmation; manual override capability.
   - **Residual Risk:** Compromised rule pack.

**Security Controls:**

- Feed arbitration and validation
- T3 adapter sandboxing
- Cryptographic feed signing
- Rate limiting and circuit breakers
- Entitlement management
- Encryption at rest and in transit
- Audit logging of all feed access

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Registered circuit-breaker traceability under the compliance code registry.
- Added changelog metadata for future epic maintenance.
