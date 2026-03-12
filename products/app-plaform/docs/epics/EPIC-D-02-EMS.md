EPIC-ID: EPIC-D-02
EPIC NAME: Execution Management System (EMS)
LAYER: DOMAIN
MODULE: D-02 Execution Management System
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the D-02 Execution Management System (EMS) responsible for smart order routing, execution algorithm management, and venue connectivity abstraction. This epic implements Principle 3 (Exchange Adapters) by ensuring that the core EMS logic does not contain any exchange-specific protocols (e.g., NEPSE APIs) or FIX variations. The EMS delegates actual venue communication to T3 Exchange Adapter Packs.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Smart order routing (SOR) logic based on configurable execution strategies.
  2. Lifecycle management of child orders (slicing parent orders).
  3. Integration with T3 Exchange Adapter Packs for venue connectivity.
  4. Execution quality and Transaction Cost Analysis (TCA) data capture.
  5. Handling circuit breaker events from D-04 Market Data.
- **Out-of-Scope:**
  1. Specific exchange protocol logic (e.g., FIX, native REST APIs) - handled by T3 Adapters.
- **Dependencies:** EPIC-D-01 (OMS), EPIC-D-04 (Market Data), EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-04 (Plugin Runtime), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** K-02, K-03, K-04, K-05
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Order Slicing:** The EMS must support slicing parent orders received from the OMS into child orders based on active execution strategies (e.g., VWAP, TWAP).
2. **FR2 Adapter Integration:** The EMS must invoke the appropriate T3 Exchange Adapter via K-04 Plugin Runtime to transmit orders to the market.
3. **FR3 Event Emission:** The EMS must emit `TradeExecuted` events containing fill details, mapping child fills back to the parent order.
4. **FR4 Circuit Breaker Handling:** Upon receiving a market halt event (from D-04 or the Adapter), the EMS must immediately halt routing to the affected venue and update active order status.
5. **FR5 Execution Quality:** Capture execution timing, spread capture, and slippage data for TCA reporting.
6. **FR6 Dual-Calendar:** Timestamps for execution must log both `timestamp_gregorian` and `timestamp_bs`.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The SOR logic and slicing algorithms are generic.
2. **Jurisdiction Plugin:** The specific connectivity to a jurisdiction's exchange (e.g., NEPSE TMS) is a T3 Executable Pack. Routing preference rules (e.g., prefer local exchange over dark pools) are T2 Rule Packs.
3. **Resolution Flow:** Config Engine determines active venues for a given instrument.
4. **Hot Reload:** Routing weights update dynamically via Config Engine.
5. **Backward Compatibility:** Order mapping structure allows seamless adapter upgrades.
6. **Future Jurisdiction:** Connecting to BSE/NSE requires only new T3 Adapters.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `ExecutionRoute`: `{ route_id: String, parent_order_id: UUID, venue_id: String, strategy: String, status: Enum }`
  - `Fill`: `{ fill_id: UUID, route_id: String, price: Decimal, qty: Int, executed_at: DualDate, venue_ref: String }`
- **Dual-Calendar Fields:** `executed_at` uses `DualDate`.
- **Event Schema Changes:** `TradeExecuted`, `RoutingFailed`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                            |
| ----------------- | ------------------------------------------------------------------------------------------------------ |
| Event Name        | `TradeExecuted`                                                                                        |
| Schema Version    | `v1.0.0`                                                                                               |
| Trigger Condition | An exchange adapter confirms a partial or full fill.                                                   |
| Payload           | `{ "order_id": "...", "fill_qty": 100, "fill_price": 245.5, "venue": "NEPSE", "timestamp_bs": "..." }` |
| Consumers         | OMS (Position Update), Post-Trade, Surveillance                                                        |
| Idempotency Key   | `hash(fill_id + venue_ref)`                                                                            |
| Replay Behavior   | Updates execution read models.                                                                         |
| Retention Policy  | Permanent.                                                                                             |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RouteOrderCommand`                                                  |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Order exists, routing strategy valid, venue available                |
| Handler          | `RoutingCommandHandler` in D-02 EMS                                  |
| Success Event    | `OrderRouted`                                                        |
| Failure Event    | `OrderRoutingFailed`                                                 |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CancelRouteCommand`                                                 |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Route exists, not fully executed, requester authorized               |
| Handler          | `RoutingCommandHandler` in D-02 EMS                                  |
| Success Event    | `RouteCancelled`                                                     |
| Failure Event    | `RouteCancellationFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `ExecuteAlgorithmCommand`                                            |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Algorithm exists, parameters valid, order sliceable                  |
| Handler          | `AlgorithmCommandHandler` in D-02 EMS                                |
| Success Event    | `AlgorithmStarted`                                                   |
| Failure Event    | `AlgorithmStartFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Recommendation / Strategy Selection
- **Workflow Steps Exposed:** Smart Order Routing venue selection.
- **Model Registry Usage:** `sor-optimizer-v1`
- **Explainability Requirement:** If AI overrides default routing rules (e.g., due to predicted liquidity dry-up), the reason is logged.
- **Human Override Path:** Trader can pin order to a specific venue, bypassing SOR.
- **Drift Monitoring:** Execution slippage compared to VWAP benchmark.
- **Fallback Behavior:** Static routing table rules.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                               |
| ------------------------- | ---------------------------------------------- |
| Latency / Throughput      | Routing decision < 1ms; 50,000 TPS             |
| Scalability               | Horizontally scalable                          |
| Availability              | 99.999% during market hours                    |
| Consistency Model         | Strong consistency for fills                   |
| Security                  | Internal mTLS                                  |
| Data Residency            | Enforced per Config                            |
| Data Retention            | Retain TCA data 10 years                       |
| Auditability              | All routing decisions logged                   |
| Observability             | Metrics: `ems.latency.route`, `ems.fill.ratio` |
| Extensibility             | Connect new exchange via T3 Adapter < 1 sprint |
| Upgrade / Compatibility   | T3 Adapter API strict versioning               |
| On-Prem Constraints       | Can run local exchange proxies                 |
| Ledger Integrity          | Feeds D-09 (Post-Trade) which posts to K-16    |
| Dual-Calendar Correctness | Fill timestamps                                |

---

#### Section 10 — Acceptance Criteria

1. **Given** an approved parent order from OMS, **When** processed by the EMS, **Then** it determines the venue via K-03 rules and invokes the correct T3 Adapter.
2. **Given** a response from the NEPSE adapter containing a fill, **When** processed, **Then** the EMS emits a `TradeExecuted` event matching the parent `order_id`.
3. **Given** a circuit breaker halt event for `venue: NEPSE`, **When** received, **Then** the EMS immediately suspends new routing to that venue and alerts the OMS.

---

#### Section 11 — Failure Modes & Resilience

- **Adapter Disconnect:** Adapter enters degraded state; EMS pauses routing to venue; circuit breaker pattern applied.
- **Event Bus Lag:** Fills processed locally in memory to ensure fast acknowledgment to venue; K-05 emission is async.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                             |
| ------------------- | -------------------------------------------- |
| Metrics             | `ems.route.latency`, `ems.venue.reject_rate` |
| Logs                | Routing decisions                            |
| Traces              | Span `EMS.route`                             |
| Audit Events        | Execution details [LCA-BESTEX-001]           |
| Regulatory Evidence | TCA reports and routing rationale.           |

---

#### Section 13 — Compliance & Regulatory Traceability

- Best execution evidence [LCA-BESTEX-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `ExchangeAdapter` interface (connect, send_order, cancel_order, handle_fill).
- **Jurisdiction Plugin Extension Points:** T3 Exchange Adapters (e.g., NEPSE FIX adapter, BSE native adapter).

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `OrderRouted` | Order sent to exchange | `order_id`, `exchange`, `route_reason`, `adapter_version` |
| `FillReceived` | Exchange confirms execution | `order_id`, `fill_id`, `fill_qty`, `fill_price`, `exchange_trade_id` |
| `OrderRejectedByExchange` | Exchange rejects order | `order_id`, `exchange`, `reject_code`, `reject_reason` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `OrderPlaced` | D-01 OMS | New order to route |
| `OrderCancelled` | D-01 OMS | Cancel request to exchange |
| `MarketDataUpdated` | D-04 Market Data | Smart routing decisions |

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                     |
| ---------------------------------------------------- | ----------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes, via new T3 Adapters (BSE/NSE). |
| Can a new exchange be connected?                     | Yes.                                |
| Can this run in an air-gapped deployment?            | Yes, with local exchange adapters.  |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Routing Manipulation**
   - **Threat:** Attacker manipulates routing logic to direct orders to disadvantageous venues.
   - **Mitigation:** Routing rules defined in T2 Rule Packs with maker-checker approval; all routing decisions logged; routing performance monitored.
   - **Residual Risk:** Compromised rule pack author.

2. **Exchange Adapter Compromise**
   - **Threat:** Malicious T3 Exchange Adapter exfiltrates order data or manipulates executions.
   - **Mitigation:** K-04 Plugin Runtime enforces sandbox isolation; adapters cryptographically signed; network access restricted; all adapter calls logged.
   - **Residual Risk:** Supply chain attack on adapter signing keys.

3. **Order Slicing Information Leakage**
   - **Threat:** Slicing algorithm reveals parent order intent, enabling front-running.
   - **Mitigation:** Randomized slicing parameters; time-based jitter; encrypted communication with venues.
   - **Residual Risk:** Statistical analysis of slice patterns.

4. **Venue Connectivity Hijacking**
   - **Threat:** Man-in-the-middle attack intercepts venue communication.
   - **Mitigation:** mTLS for all venue connections; certificate pinning; connection integrity monitoring.
   - **Residual Risk:** Compromised venue infrastructure.

5. **Execution Price Manipulation**
   - **Threat:** Attacker manipulates reported execution prices.
   - **Mitigation:** Cross-validation of fills against D-04 Market Data; reconciliation with venue reports; anomaly detection on execution quality.
   - **Residual Risk:** Coordinated attack across multiple data sources.

**Security Controls:**

- T3 adapter sandboxing via K-04
- Cryptographic signing of all adapters
- mTLS for venue connectivity
- Network segmentation per venue
- Execution quality monitoring
- Audit logging of all routing decisions
- Regular adapter security scans via P-01

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
