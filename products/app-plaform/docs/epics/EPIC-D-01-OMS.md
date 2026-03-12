EPIC-ID: EPIC-D-01
EPIC NAME: Order Management System (OMS)
LAYER: DOMAIN
MODULE: D-01 Order Management System
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the D-01 Order Management System (OMS), an AI-native domain subsystem responsible for the complete order lifecycle (capture, validation, routing, position tracking). The OMS adheres to Principle 4 (Generic Core Purity) and Principle 10 (First-Party Subsystem) by offloading all regulatory compliance, routing preferences, and specific exchange interactions to externalized rule packs and adapters.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Complete order lifecycle state machine (New, Pending, Partial, Filled, Cancelled, Rejected).
  2. Pre-trade risk/compliance integration (via D-06 and D-07 hooks).
  3. Client position tracking and margin check triggers.
  4. Maker-checker approval workflows for restricted order types.
  5. AI hooks for order intent classification and anomaly detection.
- **Out-of-Scope:**
  1. Specific exchange connectivity (handled by EMS / Exchange Adapters).
  2. The actual compliance rules (handled by K-03 and Jurisdiction Rule Packs).
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Configuration Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework), EPIC-K-18 (Resilience Patterns)
- **Kernel Readiness Gates:** K-01, K-02, K-03, K-05, K-07, K-15, K-16 must be stable.
- **Module Classification:** Domain Subsystem (Generic Core with Extension Points)

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Order Capture:** Accept order instructions via the Unified API Gateway, generating an immutable `OrderPlaced` event.
2. **FR2 State Machine:** Manage the order state machine strictly through K-05 events. Direct database updates without events are prohibited.
3. **FR3 Pre-Trade Evaluation:** Before routing, the OMS must invoke pre-trade compliance and risk checks exclusively through K-03 Rules Engine orchestration. The OMS sends a single `EvaluatePreTradeCommand` to K-03, which orchestrates evaluation of both compliance rules (D-07 scope) and risk rules (D-06 scope) in a unified pipeline. The OMS must NOT call D-06 or D-07 directly — all pre-trade logic is consolidated in K-03 Rule Packs. This prevents duplication of pre-trade checks across modules. [ARB D.2]
4. **FR4 Maker-Checker:** If K-03 flags an order as requiring approval (e.g., restricted promoter shares), route it to a maker-checker queue.
5. **FR5 Order Routing:** Approved orders are passed to the EMS (D-02) for execution venue selection.
6. **FR6 Position Updates:** Update materialized read-models of client positions based on `TradeExecuted` events.
7. **FR7 Dual-Calendar Fields:** Order timestamps must record `timestamp_bs` and `timestamp_gregorian`.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The order state machine and API definitions are generic.
2. **Jurisdiction Plugin:** Order validation rules (e.g., minimum lot size = 10 for Nepal, valid order types for NEPSE) are defined in Jurisdiction Rule Packs (T2) and Exchange Config Packs (T1).
3. **Resolution Flow:** K-02 provides the applicable validation ruleset for the `tenant_id` and `jurisdiction`.
4. **Hot Reload:** Changes to valid order types or lot sizes apply instantly to new orders.
5. **Backward Compatibility:** In-flight orders are evaluated using the rule version active at submission.
6. **Future Jurisdiction:** A new market simply requires a new T2 validation rule pack; OMS core is untouched.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `Order`: `{ order_id: UUID, client_id: UUID, instrument_id: String, side: Enum, qty: Int, type: Enum, status: Enum, submitted_at: DualDate, updated_at: DualDate }`
  - `Position`: `{ client_id: UUID, instrument_id: String, available_qty: Int, locked_qty: Int }`
- **Dual-Calendar Fields:** `submitted_at` and `updated_at` use `DualDate`.
- **Event Schema Changes:** `OrderPlaced`, `OrderModified`, `OrderCancelled`, `OrderStateChanged`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                 |
| ----------------- | ------------------------------------------------------------------------------------------- |
| Event Name        | `OrderStateChanged`                                                                         |
| Schema Version    | `v1.0.0`                                                                                    |
| Trigger Condition | Any transition in the order lifecycle (e.g., PENDING -> FILLED).                            |
| Payload           | `{ "order_id": "...", "previous_state": "...", "new_state": "...", "timestamp_bs": "..." }` |
| Consumers         | Client Portal (UI updates), Trade Surveillance, Post-Trade                                  |
| Idempotency Key   | `hash(order_id + new_state + timestamp)`                                                    |
| Replay Behavior   | Rebuilds the current state of the order book and position projections.                      |
| Retention Policy  | Permanent.                                                                                  |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                                |
| ---------------- | -------------------------------------------------------------------------- |
| Command Name     | `PlaceOrderCommand`                                                        |
| Schema Version   | `v1.0.0`                                                                   |
| Validation Rules | Client authorized, instrument valid, quantity > 0, pre-trade checks passed |
| Handler          | `OrderCommandHandler` in D-01 OMS                                          |
| Success Event    | `OrderPlaced`                                                              |
| Failure Event    | `OrderRejected`                                                            |
| Idempotency      | Order ID must be unique; duplicate commands are rejected                   |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CancelOrderCommand`                                                 |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Order exists, order cancelable, requester authorized                 |
| Handler          | `OrderCommandHandler` in D-01 OMS                                    |
| Success Event    | `OrderCancelled`                                                     |
| Failure Event    | `OrderCancellationFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                                  |
| ---------------- | ---------------------------------------------------------------------------- |
| Command Name     | `AmendOrderCommand`                                                          |
| Schema Version   | `v1.0.0`                                                                     |
| Validation Rules | Order exists, order amendable, new parameters valid, pre-trade checks passed |
| Handler          | `OrderCommandHandler` in D-01 OMS                                            |
| Success Event    | `OrderAmended`                                                               |
| Failure Event    | `OrderAmendmentFailed`                                                       |
| Idempotency      | Command ID must be unique; duplicate commands return original result         |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist / Anomaly Detection
- **Workflow Steps Exposed:** Order intent capture (chat-to-trade) and pre-trade anomaly check.
- **Model Registry Usage:** `trade-intent-parser-v1`, `fat-finger-detector-v1`
- **Explainability Requirement:** If AI blocks an order as a likely "fat finger" error, the explanation is stored in K-07.
- **Human Override Path:** Trader can acknowledge the warning and force submission.
- **Drift Monitoring:** Track ratio of forced submissions vs AI warnings.
- **Fallback Behavior:** Standard static limit checks.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                                                                                                                                                    |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Latency / Throughput      | OMS internal processing latency < 2ms P99 (capture → event emission, excludes pre-trade evaluation); total order path including K-03 pre-trade (D-06 risk + D-07 compliance) end-to-end < 12ms P99; 50,000 TPS peak |
| Scalability               | Horizontally scalable based on order queue depth; auto-scale at >70% partition lag                                                                                                                                  |
| Availability              | 99.999% uptime during trading hours                                                                                                                                                                                 |
| Consistency Model         | Eventual consistency for read models; strong for state transitions                                                                                                                                                  |
| Security                  | Row-level tenant isolation; K-01 IAM RBAC; mTLS inter-service                                                                                                                                                       |
| Data Residency            | Order data resides in-jurisdiction per K-02 config                                                                                                                                                                  |
| Data Retention            | 10 years minimum (SEBON/SEBI cross-jurisdiction safety margin)                                                                                                                                                      |
| Auditability              | Every order state change is audited via K-07                                                                                                                                                                        |
| Observability             | Metrics: `order.latency`, `order.rejection.rate`                                                                                                                                                                    |
| Extensibility             | New order types supported via T1 schema updates                                                                                                                                                                     |
| Upgrade / Compatibility   | N/A                                                                                                                                                                                                                 |
| On-Prem Constraints       | Fully functional locally                                                                                                                                                                                            |
| Ledger Integrity          | Interacts with K-16 via Post-Trade module                                                                                                                                                                           |
| Dual-Calendar Correctness | Correct timestamping                                                                                                                                                                                                |

---

#### Section 10 — Acceptance Criteria

1. **Given** a valid limit order request, **When** submitted, **Then** an `OrderPlaced` event is generated, and the status becomes PENDING.
2. **Given** an order attempting to sell 5 kitta, **When** the Nepal T2 Validation Pack specifies a minimum lot of 10, **Then** the order is synchronously rejected.
3. **Given** an order for restricted promoter shares, **When** evaluated, **Then** K-03 flags it for maker-checker, and it remains in PENDING_APPROVAL until a second authorized user approves.
4. **Given** a `TradeExecuted` event from the EMS, **When** processed, **Then** the OMS correctly updates the client's `Position` projection.

---

#### Section 11 — Failure Modes & Resilience

- **Rules Engine Down:** Reject all new orders safely; circuit breaker opens after 5 consecutive failures; K-18 resilience pattern: fail-closed.
- **Event Bus Partition:** Disconnect client and return 503 rather than accepting an order that cannot be persisted; outbox pattern ensures no event loss.
- **Market Halt / Exchange Down:** OMS queues orders locally; state machine transitions to PENDING_HALT; automatic resume on `MarketResumed` event from D-04.
- **Database Failover:** Read replica promoted; in-flight orders replayed from event store; position projections rebuilt from last snapshot.
- **DLQ Overflow:** K-19 DLQ management escalates; affected order events flagged for manual review; no silent drops.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                         |
| ------------------- | ------------------------------------------------------------------------ |
| Metrics             | `order.count`, `order.latency.p99`, dimensions: `status`, `jurisdiction` |
| Logs                | Structured logs tracking `order_id` through the state machine.           |
| Traces              | Span `OMS.processOrder`                                                  |
| Audit Events        | Action: `ApproveOrder`, `RejectOrder` [LCA-AUDIT-001]                    |
| Regulatory Evidence | Complete order lifecycle history for best execution [LCA-BESTEX-001].    |

---

#### Section 13 — Compliance & Regulatory Traceability

- Maker-checker controls [LCA-SOD-001]
- Order record retention [LCA-RET-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `OrderClient.place(PlaceOrderCommand)` → `OrderPlaced`, `OrderClient.cancel(CancelOrderCommand)` → `OrderCancelled`, `OrderClient.amend(AmendOrderCommand)` → `OrderAmended`, `OrderClient.getOrder(orderId)` → `Order`, `OrderClient.getPositions(clientId)` → `Position[]`
- **REST API:** Exposed via K-11 API Gateway at `/api/v1/orders/*`
- **Jurisdiction Plugin Extension Points:**
  - Order Validation hooks (T2 Rule Pack) — lot size, price limits, restricted securities
  - Routing Preference hooks (T2 Rule Pack) — best execution routing rules per jurisdiction
  - Exchange Adapter interfaces (T3 Executable Pack) — NEPSE, NSE, BSE connectivity
- **Events Emitted:** `OrderPlaced`, `OrderModified`, `OrderCancelled`, `OrderStateChanged`, `OrderAmended`, `OrderRejected` — all conform to K-05 standard envelope (`event_id`, `event_type`, `aggregate_id`, `aggregate_type`, `sequence_number`, `timestamp`, `timestamp_bs`, `payload`)
- **Events Consumed:** `TradeExecuted` (from D-02 EMS), `PreTradeEvaluationResult` (from K-03), `MarketHalt`/`MarketResumed` (from D-04), `ConfigUpdated` (from K-02)
- **Webhook Extension Points:** `POST /webhooks/order-events` for external order management system integration

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                    |
| ---------------------------------------------------- | ---------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes, validation logic is external. |
| Can order rules change without redeploy?             | Yes, via K-03 Rule Packs.          |
| Can this run in an air-gapped deployment?            | Yes.                               |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Order Spoofing / Market Manipulation**
   - **Threat:** Malicious actor places large orders to manipulate market perception, then cancels before execution.
   - **Mitigation:** D-08 Surveillance monitors order-to-cancel ratios; excessive cancellations trigger alerts. Rate limiting on order placement per client.
   - **Residual Risk:** Sophisticated actors may stay below detection thresholds.

2. **Unauthorized Order Placement**
   - **Threat:** Compromised credentials used to place unauthorized orders.
   - **Mitigation:** K-01 IAM enforces MFA for trading; anomaly detection flags unusual trading patterns (location, time, volume). All orders audited to K-07.
   - **Residual Risk:** Insider threat with legitimate credentials.

3. **Fat Finger Errors**
   - **Threat:** Accidental order entry with incorrect quantity/price causing significant loss.
   - **Mitigation:** AI-based fat-finger detection (FR7); configurable order limits per client; maker-checker for large orders.
   - **Residual Risk:** Errors within acceptable thresholds may pass through.

4. **Front-Running**
   - **Threat:** Internal actor or compromised system views pending orders and trades ahead of them.
   - **Mitigation:** Strict RBAC limiting order book access; all access logged to K-07; network segmentation; encrypted order data at rest.
   - **Residual Risk:** Determined insider with elevated privileges.

5. **Order Injection via API**
   - **Threat:** Attacker exploits API vulnerability to inject malicious orders.
   - **Mitigation:** K-11 API Gateway enforces authentication, rate limiting, input validation; all orders validated against schema; SQL injection prevention.
   - **Residual Risk:** Zero-day vulnerabilities in validation logic.

6. **Tenant Isolation Breach**
   - **Threat:** Tenant A accesses or modifies Tenant B's orders.
   - **Mitigation:** Row-level tenant_id filtering enforced at database layer; all queries include tenant_id; T-01 includes tenant isolation tests.
   - **Residual Risk:** Database-level privilege escalation.

**Security Controls:**

- Authentication via K-01 IAM with MFA
- Authorization via RBAC with principle of least privilege
- Encryption in transit (mTLS) and at rest
- Audit logging of all state changes to K-07
- Input validation and sanitization
- Rate limiting per client/tenant
- Anomaly detection via K-09 AI Governance
- Network segmentation and firewall rules
- Regular security scanning via T-01

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
