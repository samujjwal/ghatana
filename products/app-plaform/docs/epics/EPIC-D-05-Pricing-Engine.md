EPIC-ID: EPIC-D-05
EPIC NAME: Pricing Engine
LAYER: DOMAIN
MODULE: D-05 Pricing Engine
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the D-05 Pricing Engine, responsible for evaluating and calculating instrument prices, generating yield curves, and managing execution of valuation models. This subsystem strictly separates the generic execution framework from the actual quantitative models (which are hosted as T3 Executable Packs), fulfilling Principle 10 (First-Party Subsystem) and Principle 20 (Plugin Taxonomy).

---

#### Section 2 — Scope

- **In-Scope:**
  1. Real-time and end-of-day instrument pricing calculation framework.
  2. Integration with T3 Pricing Model Packs (e.g., custom valuation models).
  3. Curve management (yield curves, volatility surfaces).
  4. Integration with D-04 Market Data for underlying ticks.
  5. AI model governance hooks for machine-learned pricing estimates.
- **Out-of-Scope:**
  1. The specific quantitative formulas (e.g., Black-Scholes implementations are T3 packs).
- **Dependencies:** EPIC-D-04 (Market Data), EPIC-K-02 (Config Engine), EPIC-K-04 (Plugin Runtime), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-09 (AI Governance), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** K-02, K-04, K-05, K-09
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Pricing Framework:** The engine must provide a standardized API to query the price of any supported instrument (equities, debentures, mutual funds).
2. **FR2 Model Execution:** To price complex/illiquid instruments, the engine must invoke the appropriate T3 Pricing Model Pack via K-04.
3. **FR3 Yield Curves:** The engine must compute and store yield curves dynamically based on underlying market data.
4. **FR4 EOD Mark-to-Market:** The engine must compute official End-of-Day prices for all instruments and emit `PriceUpdatedEvent`.
5. **FR5 Dual-Calendar Stamping:** EOD prices must be stamped with dual-calendar dates.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The calculation orchestration and API are generic.
2. **Jurisdiction Plugin:** Specific models required by regulators (e.g., specific amortized cost rules for Nepal debentures) are T3 Packs.
3. **Resolution Flow:** Config Engine maps instrument classes to specific T3 pricing packs.
4. **Hot Reload:** New pricing packs can be swapped dynamically.
5. **Backward Compatibility:** Historical prices are immutable.
6. **Future Jurisdiction:** Handled by adding new T3 pricing models if the jurisdiction uses novel instruments.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `InstrumentPrice`: `{ instrument_id: String, price: Decimal, valuation_method: String, calculated_at_greg: Timestamp, calculated_at_bs: String }`
- **Dual-Calendar Fields:** `calculated_at_bs` in `InstrumentPrice`.
- **Event Schema Changes:** `PriceUpdatedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                        |
| ----------------- | ---------------------------------------------------------------------------------- |
| Event Name        | `PriceUpdatedEvent`                                                                |
| Schema Version    | `v1.0.0`                                                                           |
| Trigger Condition | EOD calculation finishes or a significant intra-day pricing threshold is crossed.  |
| Payload           | `{ "instrument_id": "...", "price": 245.5, "type": "EOD", "timestamp_bs": "..." }` |
| Consumers         | PMS (NAV calc), Risk Engine (Margin calc)                                          |
| Idempotency Key   | `hash(instrument_id + calculation_timestamp)`                                      |
| Replay Behavior   | Updates materialized view of latest prices.                                        |
| Retention Policy  | Permanent.                                                                         |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                      |
| ---------------- | ---------------------------------------------------------------- |
| Command Name     | `CalculatePriceCommand`                                          |
| Schema Version   | `v1.0.0`                                                         |
| Validation Rules | Instrument exists, valuation date valid, pricing model available |
| Handler          | `PricingCommandHandler` in D-05 Pricing Engine                   |
| Success Event    | `PriceCalculated`                                                |
| Failure Event    | `PriceCalculationFailed`                                         |
| Idempotency      | Same instrument + date returns cached price                      |

| Field            | Description                                                                  |
| ---------------- | ---------------------------------------------------------------------------- |
| Command Name     | `OverridePriceCommand`                                                       |
| Schema Version   | `v1.0.0`                                                                     |
| Validation Rules | Instrument exists, override reason provided, maker-checker approval obtained |
| Handler          | `PricingCommandHandler` in D-05 Pricing Engine                               |
| Success Event    | `PriceOverridden`                                                            |
| Failure Event    | `PriceOverrideFailed`                                                        |
| Idempotency      | Command ID must be unique; duplicate commands return original result         |

| Field            | Description                                                         |
| ---------------- | ------------------------------------------------------------------- |
| Command Name     | `BuildYieldCurveCommand`                                            |
| Schema Version   | `v1.0.0`                                                            |
| Validation Rules | Market data available, curve definition valid, valuation date valid |
| Handler          | `YieldCurveHandler` in D-05 Pricing Engine                          |
| Success Event    | `YieldCurveBuilt`                                                   |
| Failure Event    | `YieldCurveBuildFailed`                                             |
| Idempotency      | Same date + curve returns cached result                             |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Predictive Model
- **Workflow Steps Exposed:** Pricing of illiquid or OTC instruments.
- **Model Registry Usage:** `illiquid-pricer-v1`
- **Explainability Requirement:** AI provides an estimated fair value based on comparables; must output the weights of comparables used.
- **Human Override Path:** Trader can manually override the AI estimated price (audited).
- **Drift Monitoring:** AI predictions compared against eventual actual trades.
- **Fallback Behavior:** Last known traded price.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                      |
| ------------------------- | ----------------------------------------------------- |
| Latency / Throughput      | Price query < 5ms; handle 10,000 queries/sec          |
| Scalability               | Horizontally scalable computation                     |
| Availability              | 99.99% uptime                                         |
| Consistency Model         | Strong consistency for EOD prices                     |
| Security                  | Row-level tenant isolation                            |
| Data Residency            | Enforced via K-08                                     |
| Data Retention            | 10 years for official EOD prices                      |
| Auditability              | Manual price overrides logged                         |
| Observability             | Metrics: `pricing.calc.latency`, `pricing.error.rate` |
| Extensibility             | New models via T3 packs                               |
| Upgrade / Compatibility   | N/A                                                   |
| On-Prem Constraints       | Fully functional locally                              |
| Ledger Integrity          | N/A                                                   |
| Dual-Calendar Correctness | EOD timestamps correct                                |

---

#### Section 10 — Acceptance Criteria

1. **Given** a request for the EOD price of a Nepal Mutual Fund, **When** the Pricing Engine is called, **Then** it invokes the specific NAV lookup adapter and returns the price with dual dates.
2. **Given** an illiquid debenture, **When** requested, **Then** the engine executes the T3 Amortized Cost model and returns the result in < 5ms.

---

#### Section 11 — Failure Modes & Resilience

- **Model Pack Crash:** Sandbox restarts; if it fails repeatedly, engine falls back to 'Last Known Price' and raises an alert.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                          |
| ------------------- | ----------------------------------------- |
| Metrics             | `price.staleness`, `model.execution.time` |
| Logs                | Calculation errors                        |
| Traces              | Span `Pricing.calculate`                  |
| Audit Events        | Action: `ManualPriceOverride`             |
| Regulatory Evidence | Mark-to-market justification trails.      |

---

#### Section 13 — Compliance & Regulatory Traceability

- Valuation integrity [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `PricingClient.getPrice(instrumentId)`, `PricingClient.getBatchPrices(instrumentIds[])`, `PricingClient.getGreeks(instrumentId)`.
- **Jurisdiction Plugin Extension Points:** T3 Pricing Models (e.g., Black-Scholes, Monte Carlo, Nepal-specific fixed-income models).

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `PriceCalculated` | New price computed | `instrument_id`, `price`, `model_id`, `model_version`, `confidence` |
| `PricingModelSwapped` | Model version updated | `instrument_id`, `old_model`, `new_model`, `reason` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `MarketDataUpdated` | D-04 Market Data | Input data for pricing models |
| `RulePackActivated` | K-03 Rules Engine | Updated pricing rules |
| `PluginRegistered` | K-04 Plugin Runtime | New T3 pricing model available |

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                 |
| ---------------------------------------------------- | ------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                            |
| Can a new pricing model be added?                    | Yes, via T3 Pack.               |
| Can this run in an air-gapped deployment?            | Yes, with local pricing models. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Pricing Model Manipulation**
   - **Threat:** Attacker manipulates pricing models to generate fraudulent valuations.
   - **Mitigation:** T3 Pricing Model Packs cryptographically signed; P-01 certification required; all model invocations logged; independent price verification.
   - **Residual Risk:** Compromised model author.

2. **Price Override Fraud**
   - **Threat:** Unauthorized price overrides to manipulate valuations.
   - **Mitigation:** Maker-checker required for all overrides; justification mandatory; all overrides audited to K-07; AI flags unusual override patterns.
   - **Residual Risk:** Collusion between maker and checker.

3. **Yield Curve Manipulation**
   - **Threat:** False yield curves cause incorrect bond pricing.
   - **Mitigation:** Yield curves built from multiple data sources; validation against market benchmarks; anomaly detection; all curves versioned and auditable.
   - **Residual Risk:** Coordinated manipulation of input data.

4. **Pricing Data Exfiltration**
   - **Threat:** Competitor steals proprietary pricing models or data.
   - **Mitigation:** Encryption at rest; strict RBAC; T3 models sandboxed; all access logged; data classification via K-08.
   - **Residual Risk:** Insider threat with model access.

5. **Model Backdoor**
   - **Threat:** Malicious pricing model contains backdoor for selective mispricing.
   - **Mitigation:** P-01 security scanning; code review; behavioral monitoring; model output validation; regular audits.
   - **Residual Risk:** Sophisticated backdoor evading detection.

**Security Controls:**

- Cryptographic signing of pricing models
- P-01 certification pipeline
- Maker-checker for price overrides
- Model sandboxing via K-04
- Independent price verification
- Audit logging of all pricing operations
- Encryption of proprietary models

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
