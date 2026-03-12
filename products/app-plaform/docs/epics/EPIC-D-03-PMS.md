EPIC-ID: EPIC-D-03
EPIC NAME: Portfolio Management System (PMS)
LAYER: DOMAIN
MODULE: D-03 Portfolio Management System
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the D-03 Portfolio Management System (PMS), responsible for portfolio construction, NAV calculation, and rebalancing. This module enforces Principle 10 (Domain Subsystems) by integrating tightly with the Event Bus (K-05) to maintain real-time position views without directly coupling to the OMS or Post-Trade modules. It delegates all actual ledger tracking to K-16 (Ledger Framework) and pricing logic to D-05 (Pricing Engine).

---

#### Section 2 — Scope

- **In-Scope:**
  1. Real-time portfolio valuation (NAV calculation).
  2. Portfolio construction and modeling.
  3. Rebalancing workflows with drift detection.
  4. Integration with K-16 Ledger for definitive position data.
  5. AI-assisted portfolio optimization hooks.
- **Out-of-Scope:**
  1. Order execution (handled by D-01/D-02).
  2. The actual pricing algorithms (handled by D-05 Pricing Engine).
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework), EPIC-D-05 (Pricing Engine)
- **Kernel Readiness Gates:** K-02, K-05, K-15, K-16
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Position Ingestion:** The PMS must project real-time portfolio holdings by subscribing to `TradeExecuted` events (for intra-day) and reconciling against `LedgerPostedEvent` from K-16 (for start-of-day definitive).
2. **FR2 NAV Calculation:** Calculate Net Asset Value dynamically using pricing data streams from D-05.
3. **FR3 Rebalancing Engine:** Generate proposed order baskets to realign a portfolio with its target model based on configurable drift thresholds.
4. **FR4 Maker-Checker:** Rebalancing order generation for mutual funds requires dual approval before being sent to the OMS.
5. **FR5 Dual-Calendar Stamping:** NAV calculations and performance metrics must use dual-calendar (Gregorian and BS) for reporting.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The rebalancing math and NAV calculation framework are generic.
2. **Jurisdiction Plugin:** Fund management regulations (e.g., SEBON limits on holding % of a single stock) are defined in T2 Rule Packs.
3. **Resolution Flow:** K-02 provides the applicable fund rules.
4. **Hot Reload:** Changes to target models or constraints update immediately.
5. **Backward Compatibility:** Historical NAV records are immutable.
6. **Future Jurisdiction:** Only requires new T2 compliance constraints for the rebalancing engine.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `Portfolio`: `{ portfolio_id: UUID, target_model_id: String, drift_tolerance: Decimal }`
  - `NAVRecord`: `{ portfolio_id: UUID, nav_value: Decimal, calculated_at_greg: Timestamp, calculated_at_bs: String }`
- **Dual-Calendar Fields:** `calculated_at_bs` in `NAVRecord`.
- **Event Schema Changes:** `RebalanceProposed`, `NavCalculated`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                             |
| ----------------- | ----------------------------------------------------------------------- |
| Event Name        | `RebalanceProposed`                                                     |
| Schema Version    | `v1.0.0`                                                                |
| Trigger Condition | Portfolio drift exceeds the configured tolerance threshold.             |
| Payload           | `{ "portfolio_id": "...", "drift_pct": 5.2, "proposed_orders": [...] }` |
| Consumers         | Operator Dashboard (for approval), Compliance (D-07)                    |
| Idempotency Key   | `hash(portfolio_id + calculation_timestamp)`                            |
| Replay Behavior   | Suppressed; purely informative.                                         |
| Retention Policy  | 10 years.                                                               |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                                |
| ---------------- | -------------------------------------------------------------------------- |
| Command Name     | `RebalanceCommand`                                                         |
| Schema Version   | `v1.0.0`                                                                   |
| Validation Rules | Portfolio exists, target allocation valid, maker-checker approval obtained |
| Handler          | `RebalanceCommandHandler` in D-03 PMS                                      |
| Success Event    | `RebalanceInitiated`                                                       |
| Failure Event    | `RebalanceFailed`                                                          |
| Idempotency      | Command ID must be unique; duplicate commands return original result       |

| Field            | Description                                                    |
| ---------------- | -------------------------------------------------------------- |
| Command Name     | `CalculateNAVCommand`                                          |
| Schema Version   | `v1.0.0`                                                       |
| Validation Rules | Portfolio exists, valuation date valid, pricing data available |
| Handler          | `NAVCommandHandler` in D-03 PMS                                |
| Success Event    | `NAVCalculated`                                                |
| Failure Event    | `NAVCalculationFailed`                                         |
| Idempotency      | Same valuation date returns cached NAV                         |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CreatePortfolioCommand`                                             |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Portfolio name unique, strategy valid, requester authorized          |
| Handler          | `PortfolioCommandHandler` in D-03 PMS                                |
| Success Event    | `PortfolioCreated`                                                   |
| Failure Event    | `PortfolioCreationFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist / Optimizer
- **Workflow Steps Exposed:** Target model construction and rebalance order generation.
- **Model Registry Usage:** `portfolio-optimizer-v1`
- **Explainability Requirement:** AI suggests an optimized rebalancing path minimizing TCA (Transaction Cost Analysis); must explain the expected savings.
- **Human Override Path:** Fund manager reviews and adjusts the generated basket manually.
- **Drift Monitoring:** Tracks optimizer performance vs standard logic.
- **Fallback Behavior:** Standard proportional rebalancing math.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                       |
| ------------------------- | ---------------------------------------------------------------------- |
| Latency / Throughput      | NAV calc < 10ms for 1000 positions; 5000 calc/sec                      |
| Scalability               | Horizontally scalable computation tier                                 |
| Availability              | 99.99% uptime                                                          |
| Consistency Model         | Eventual consistency for real-time NAV; strong consistency for EOD NAV |
| Security                  | Row-level tenant isolation                                             |
| Data Residency            | Handled via K-08                                                       |
| Data Retention            | Retain historical NAV permanently                                      |
| Auditability              | Rebalance approvals logged [LCA-AUDIT-001]                             |
| Observability             | Metrics: `nav.calc.latency`, `rebalance.count`                         |
| Extensibility             | Custom rebalancing algorithms via T3 Packs                             |
| Upgrade / Compatibility   | N/A                                                                    |
| On-Prem Constraints       | Fully functional locally                                               |
| Ledger Integrity          | Relies strictly on K-16 for accounting                                 |
| Dual-Calendar Correctness | EOD NAV timestamps                                                     |

---

#### Section 10 — Acceptance Criteria

1. **Given** a portfolio with a target model of 50% Equity, **When** market price movements cause Equity to hit 56% (above 5% tolerance), **Then** the PMS emits a `RebalanceProposed` event.
2. **Given** a generated rebalancing basket for a SEBON-regulated Mutual Fund, **When** the fund manager hits "Execute", **Then** the system requires a checker to approve before sending orders to D-01.
3. **Given** an EOD NAV calculation, **When** saved, **Then** it records both the Gregorian date and the Bikram Sambat date.

---

#### Section 11 — Failure Modes & Resilience

- **Pricing Engine Lag:** PMS flags NAV as "Stale" if price updates are older than a configured threshold.
- **Ledger Divergence:** Runs continuous reconciliation between K-05 trade events and K-16 ledger positions; alerts on drift.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                |
| ------------------- | ----------------------------------------------- |
| Metrics             | `nav.stale.count`, `rebalance.approval.latency` |
| Logs                | Calculation exceptions                          |
| Traces              | Span `PMS.calcNav`                              |
| Audit Events        | Action: `ApproveRebalance`                      |
| Regulatory Evidence | Daily NAV history [ASR-RPT-001].                |

---

#### Section 13 — Compliance & Regulatory Traceability

- Maker-checker on fund operations [LCA-SOD-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `PMSClient.getNav(portfolioId)`, `PMSClient.getPositions(portfolioId)`, `PMSClient.getPerformance(portfolioId, period)`.
- **Jurisdiction Plugin Extension Points:** T2 Rule Packs for holding limits (e.g., SEBON single-stock concentration limits).

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `PortfolioRevalued` | NAV recalculated | `portfolio_id`, `nav`, `nav_change_pct`, `valuation_date_bs` |
| `PositionBreachDetected` | Holding limit exceeded | `portfolio_id`, `instrument_id`, `breach_type`, `current_pct`, `limit_pct` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `TradeExecuted` | D-01 OMS | Position updates |
| `PositionUpdated` | D-01 OMS | Real-time position sync |
| `PriceCalculated` | D-05 Pricing | NAV recalculation |
| `CorporateActionProcessed` | D-12 Corp Actions | Entitlement adjustments |

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                                 |
| ---------------------------------------------------- | ----------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                                            |
| Can a new instrument type be added?                  | Yes, transparent to PMS if D-05 provides price. |
| Can this run in an air-gapped deployment?            | Yes, with local pricing feeds.                  |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **NAV Manipulation**
   - **Threat:** Attacker manipulates NAV calculation to inflate/deflate fund value.
   - **Mitigation:** NAV calculations use audited pricing from D-05; all calculations logged to K-07; independent verification; maker-checker for manual overrides.
   - **Residual Risk:** Compromised pricing source.

2. **Unauthorized Rebalancing**
   - **Threat:** Unauthorized rebalancing causes significant portfolio losses.
   - **Mitigation:** Maker-checker approval required for all rebalancing; RBAC limits who can initiate; all rebalancing orders audited; AI monitors for unusual rebalancing patterns.
   - **Residual Risk:** Collusion between maker and checker.

3. **Portfolio Data Exfiltration**
   - **Threat:** Competitor or malicious actor steals portfolio composition and strategy.
   - **Mitigation:** Encryption at rest; strict RBAC; all access logged; data classification via K-08; network segmentation.
   - **Residual Risk:** Insider threat with legitimate access.

4. **Performance Attribution Fraud**
   - **Threat:** False performance reporting to mislead investors.
   - **Mitigation:** Performance calculations based on immutable K-16 Ledger data; all calculations auditable; independent verification against external benchmarks.
   - **Residual Risk:** Coordinated manipulation across multiple systems.

5. **Rebalancing Front-Running**
   - **Threat:** Internal actor views rebalancing plan and trades ahead of execution.
   - **Mitigation:** Rebalancing plans encrypted until execution; strict RBAC; all access logged; time-based access controls.
   - **Residual Risk:** Timing analysis reveals patterns.

**Security Controls:**

- Maker-checker for all rebalancing
- Encryption of portfolio data at rest
- Immutable ledger integration (K-16)
- Audit logging of all NAV calculations
- RBAC with principle of least privilege
- Independent performance verification

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
