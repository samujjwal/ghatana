EPIC-ID: EPIC-D-06
EPIC NAME: Risk Engine
LAYER: DOMAIN
MODULE: D-06 Risk Engine
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the D-06 Risk Engine, providing pre-trade, post-trade, and real-time risk evaluation. This epic enforces Principle 6 (Margin Values are Configuration-Driven) by ensuring that all margin calculations, haircuts, and exposure limits are evaluated via T2 Rule Packs and T1 Config Packs, keeping the core engine purely mathematical and generic.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Pre-trade risk checks (margin adequacy, concentration limits).
  2. Real-time margin calculation and margin call generation.
  3. Integration with K-03 Rules Engine for risk rule evaluation.
  4. Integration with T3 Risk Model Packs (e.g., VaR models).
- **Out-of-Scope:**
  1. Setting the actual margin rules (e.g., SEBON 30% initial margin) - these are T1/T2 packs.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework), EPIC-K-18 (Resilience Patterns), EPIC-D-05 (Pricing Engine)
- **Kernel Readiness Gates:** K-02, K-03, K-16
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Pre-Trade Checks:** Evaluate incoming orders against available margin and exposure limits. Pre-trade risk checks are invoked exclusively by K-03 Rules Engine as part of the unified pre-trade evaluation pipeline (see D-01 FR3). D-06 exposes risk evaluation functions as K-03-callable rule modules, not as direct APIs called by D-01. [ARB D.2]
2. **FR2 Margin Calculation:** Continuously mark-to-market all margin positions using prices from D-05 to compute `margin_ratio`.
3. **FR3 Margin Calls:** Emit `MarginCallIssued` events when a client's margin falls below the T1-configured maintenance threshold.
4. **FR4 Forced Liquidation:** Emit `ForcedLiquidationInstruction` to D-01 if margin calls are unmet past the configured deadline.
5. **FR5 Rule Integration:** Delegate the evaluation of complex risk rules (e.g., acceptable collateral types) to K-03.
6. **FR6 Dual-Calendar:** Margin call deadlines must be calculated and stored using dual-calendar dates.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The calculation orchestration (Value = Qty _ Price _ Haircut) is generic.
2. **Jurisdiction Plugin:** Jurisdiction-specific margin rules (e.g., initial margin percentages, maintenance thresholds) are defined exclusively in T1 Config Packs. No jurisdiction-specific values (margin rates, regulatory references) are hardcoded in this epic or its implementation. [ARB D.5]
3. **Resolution Flow:** Config Engine maps `jurisdiction` to the correct margin rate tables.
4. **Hot Reload:** Changes to margin rates take effect instantly.
5. **Backward Compatibility:** Past margin calls retain the rules active at their creation time.
6. **Future Jurisdiction:** A new country implies a new Margin Config Pack.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `MarginAccount`: `{ account_id: UUID, total_value: Decimal, loan_amount: Decimal, margin_ratio: Decimal }`
  - `MarginCall`: `{ call_id: UUID, account_id: UUID, shortfall: Decimal, deadline: Timestamp, deadline_calendar: CalendarDate | null, status: Enum }`
- **Multi-Calendar Fields:** `deadline_calendar` (CalendarDate) in `MarginCall` for jurisdiction-native deadline display.
- **Event Schema Changes:** `MarginCallIssued`, `MarginCallMet`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                                        |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `MarginCallIssued`                                                                                                                                 |
| Schema Version    | `v1.0.0`                                                                                                                                           |
| Trigger Condition | Account margin ratio falls below maintenance margin threshold.                                                                                     |
| Payload           | `{ "account_id": "...", "shortfall": 50000, "deadline": "2025-03-05T00:00:00Z", "deadline_calendar": { "bs": "2081-11-21" }, "status": "ISSUED" }` |
| Consumers         | Notification Service, Client Portal, OMS (to block new buys)                                                                                       |
| Idempotency Key   | `hash(account_id + calculation_timestamp)`                                                                                                         |
| Replay Behavior   | Updates read model.                                                                                                                                |
| Retention Policy  | 10 years.                                                                                                                                          |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                 |
| ---------------- | ----------------------------------------------------------- |
| Command Name     | `EvaluateRiskCommand`                                       |
| Schema Version   | `v1.0.0`                                                    |
| Validation Rules | Order exists, risk rules configured, pricing data available |
| Handler          | `RiskCommandHandler` in D-06 Risk Engine                    |
| Success Event    | `RiskEvaluated`                                             |
| Failure Event    | `RiskEvaluationFailed`                                      |
| Idempotency      | Same order context returns cached evaluation                |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `IssueMarginCallCommand`                                             |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Account exists, margin shortfall calculated, deadline valid          |
| Handler          | `MarginCommandHandler` in D-06 Risk Engine                           |
| Success Event    | `MarginCallIssued`                                                   |
| Failure Event    | `MarginCallIssueFailed`                                              |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `ForceLiquidationCommand`                                            |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Margin call expired, positions exist, requester authorized           |
| Handler          | `LiquidationHandler` in D-06 Risk Engine                             |
| Success Event    | `ForcedLiquidationInstruction`                                       |
| Failure Event    | `ForcedLiquidationFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Predictive Model / Stress Scenario Generator / Pre-Trade Advisor
- **Primary Use Case:** (a) 24-hour margin breach prediction per account based on portfolio composition and volatility forecasts; (b) Intraday real-time portfolio risk trajectory forecasting (not just end-of-day exposure); (c) AI-generated novel stress scenarios beyond historical look-back (synthesized from macroeconomic text signals and historical analogue matching); (d) Pre-trade risk advisory: for a proposed order, AI estimates the probability that the resulting portfolio breaches any risk limit within the next trading session.
- **Inference Mode:** (a) Nightly batch (24-hour horizon, run at EOD); (b) Near-real-time every 30 seconds during market hours; (c) On-demand, triggered by Risk Officer or on market event triggers; (d) Synchronous, called during pre-trade check (budget: 1ms P99 AI component, cached for the session).
- **Workflow Steps Exposed:** (a) EOD margin call candidate list generation; (b) Intraday dashboard: live risk trajectory per account; (c) Stress scenario library generation + approval workflow; (d) Pre-trade: "This order will push portfolio VaR from X to Y (confidence: Z%)".
- **Model Registry Usage:** `margin-call-predictor-v1` (gradient boosted classifier: will account breach margin in 24h?), `portfolio-risk-forecaster-v1` (LSTM-based intraday VaR/PnL trajectory), `stress-scenario-generator-v1` (LLM + historical analogue: generates novel scenarios with mapped market factor shocks), `pre-trade-risk-advisor-v1` (fast cached estimator: incremental VaR/CVaR from proposed trade delta)
- **Input Data / Feature Set:** Account positions; historical PnL; current margin utilization; asset volatilities (realized + implied); correlation matrix (K-09 Feature Store); macro indicators; proposed trade details (for pre-trade advisor).
- **Output / AI Annotation:** Margin breach probability (0–1) per account + top-3 risk drivers. Intraday risk trajectory: VaR forecast at T+30min, T+1h, T+2h. Stress scenario: factor shock vector (e.g., "equity -15%, bond +2%, INR/NPR -8%") + scenario narrative + historical precedent. Pre-trade: delta VaR + limit breach probability.
- **Explainability Requirement:** Margin predictor: top-3 portfolio risk factors driving the prediction (e.g., "Concentrated 80% in NABIL.NP — high single-name risk"). Stress generator: must cite the historical precedent that most closely resembles the generated scenario and the LLM reasoning chain. Pre-trade advisor: must state which limit(s) are in danger and the confidence.
- **Confidence Threshold:** Margin breach prediction confidence ≥ 0.75 → add account to margin call candidate list for human review. Confidence ≥ 0.95 → proactively issue preliminary margin call notice. Pre-trade VaR breach probability > 30% → surface as advisory (non-blocking); > 70% → hard block requiring risk officer override.
- **Human Override Path:** Risk officer can issue discretionary margin calls or suppress AI-generated calls (with documented justification, logged in K-07). Stress scenarios require Risk Committee approval before entering the live library.
- **Feedback Loop:** Actual margin breach outcomes vs. predictions captured daily as labeled data and fed to `margin-call-predictor-v1` fine-tuning pipeline. Pre-trade estimates vs. actual post-trade risk outcomes tracked.
- **Drift Monitoring:** Daily: margin predictor classification report (precision, recall, F1). Alert if F1 drops below 0.82. Portfolio risk forecaster: compare predicted VaR vs realized daily loss; recalibrate if Kupiec test p-value < 0.05.
- **Fallback Behavior:** Standard deterministic margin calculation (current exposure / available margin). Pre-trade block uses static hard-coded VaR ceiling. Stress scenarios fall back to fixed regulatory stress tests.
- **Tenant Isolation:** Risk computations are tenant-isolated at the account and portfolio level. Stress scenario library is tenant-specific (a Nepal-market tenant gets NEPSE-specific scenarios).

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                   |
| ------------------------- | -------------------------------------------------- |
| Latency / Throughput      | Pre-trade risk check < 2ms                         |
| Scalability               | Horizontally scalable based on active accounts     |
| Availability              | 99.999% during market hours                        |
| Consistency Model         | Strong consistency for pre-trade blocks            |
| Security                  | Row-level tenant isolation                         |
| Data Residency            | Enforced via K-08                                  |
| Data Retention            | Retain margin call history 10 years                |
| Auditability              | Manual overrides logged                            |
| Observability             | Metrics: `risk.check.latency`, `margin.call.count` |
| Extensibility             | Custom risk models via T3                          |
| Upgrade / Compatibility   | N/A                                                |
| On-Prem Constraints       | Fully functional locally                           |
| Ledger Integrity          | Relies on K-16 for cash/collateral balances        |
| Dual-Calendar Correctness | Margin call deadlines accurate                     |

---

#### Section 10 — Acceptance Criteria

1. **Given** an account with 25% margin ratio, **When** evaluated against a SEBON T1 Config requiring 20% maintenance, **Then** no margin call is issued.
2. **Given** the market drops and the ratio hits 18%, **When** evaluated, **Then** a `MarginCallIssued` event is fired with a dual-calendar deadline.
3. **Given** a pre-trade order requiring $10,000 margin, **When** the account only has $5,000 available, **Then** D-06 returns a synchronous DENY to the OMS in < 2ms.

---

#### Section 11 — Failure Modes & Resilience

- **Pricing Feed Stale:** Risk Engine assumes worst-case historical volatility penalty until prices refresh.
- **Rules Engine Down:** Fails closed (blocks new orders) to prevent uncovered exposure.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                        |
| ------------------- | ------------------------------------------------------- |
| Metrics             | `risk.margin.breach_rate`, `risk.eval.latency`          |
| Logs                | Rejection reasons                                       |
| Traces              | Span `RiskEngine.evaluateOrder`                         |
| Audit Events        | Action: `IssueMarginCall`, `ForceLiquidation`           |
| Regulatory Evidence | SEBON Directive 2082 compliance reports [ASR-MARG-001]. |

---

#### Section 13 — Compliance & Regulatory Traceability

- Margin trading regulations [LCA-011]
- Systemic risk controls [ASR-MARG-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `RiskClient.evaluatePreTrade(order)`, `RiskClient.getExposure(clientId)`, `RiskClient.calculateMargin(portfolioId)`.
- **Jurisdiction Plugin Extension Points:** T2 Rule Packs for margin eligibility and supervisory thresholds (e.g., SEBON margin directives, NRB capital adequacy).

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `RiskCheckPassed` | Pre-trade risk approved | `order_id`, `check_type`, `latency_ms` |
| `RiskCheckFailed` | Pre-trade risk rejected | `order_id`, `check_type`, `failure_reasons[]` |
| `MarginCallIssued` | Margin threshold breached | `client_id`, `portfolio_id`, `margin_deficit`, `deadline_bs` |
| `ExposureLimitBreached` | Concentration or exposure limit hit | `client_id`, `instrument_id`, `limit_type`, `current_value`, `limit_value` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `OrderPlaced` | D-01 OMS | Pre-trade risk evaluation trigger |
| `PositionUpdated` | D-01 OMS | Real-time exposure calculation |
| `PriceCalculated` | D-05 Pricing | Mark-to-market for margin |
| `LedgerEntryPosted` | K-16 Ledger | Cash/collateral position |

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                |
| ---------------------------------------------------- | ------------------------------ |
| Can this module support India/Bangladesh via plugin? | Yes.                           |
| Can margin rates change without redeploy?            | Yes.                           |
| Can this run in an air-gapped deployment?            | Yes, with local pricing feeds. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Risk Calculation Manipulation**
   - **Threat:** Attacker manipulates risk calculations to bypass limits or hide exposure.
   - **Mitigation:** Risk rules in T2 Rule Packs with maker-checker; all calculations logged; independent verification; anomaly detection on risk metrics.
   - **Residual Risk:** Compromised rule pack author.

2. **Margin Call Evasion**
   - **Threat:** Client manipulates data to avoid margin calls.
   - **Mitigation:** Margin calculations based on immutable K-16 Ledger and D-05 Pricing; real-time monitoring; all margin calls audited; forced liquidation capability.
   - **Residual Risk:** Timing attack between calculation cycles.

3. **Forced Liquidation Abuse**
   - **Threat:** Malicious actor triggers false forced liquidations.
   - **Mitigation:** Multiple confirmations required; maker-checker for manual liquidations; all liquidations audited; client notification before execution.
   - **Residual Risk:** Compromised pricing data causing legitimate-looking liquidations.

4. **Exposure Data Theft**
   - **Threat:** Competitor steals client exposure and position data.
   - **Mitigation:** Encryption at rest; strict RBAC; all access logged; data classification; network segmentation.
   - **Residual Risk:** Insider threat with legitimate access.

5. **Risk Limit Bypass**
   - **Threat:** Client bypasses risk limits through multiple accounts or jurisdictions.
   - **Mitigation:** Cross-account risk aggregation; entity-level limits; real-time monitoring; AI detects related accounts; all limit breaches logged.
   - **Residual Risk:** Sophisticated identity obfuscation.

**Security Controls:**

- Immutable ledger integration (K-16)
- Maker-checker for critical operations
- Real-time risk monitoring
- Encryption of position data
- Cross-account aggregation
- Audit logging of all risk operations
- AI-based anomaly detection

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Registered margin-operation traceability codes and aligned risk evidence references.
- Added changelog metadata for future epic maintenance.
