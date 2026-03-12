# Epic Cross-Document Analysis Report: Domain & Supporting Epic Review

**Generated:** March 5, 2026  
**Scope:** 28 epic files across Domain (D), Workflow (W), Pack (P), Regulatory (R), Testing (T), Operations (O), Platform-Unity (PU), and Kernel (K-16..K-19) layers  
**Method:** Section-by-section comparison against EPIC_TEMPLATE.md and EPIC_VERSIONING_STRATEGY.md

**Historical Note (March 8, 2026):** This report captures a review snapshot taken before the latest documentation cleanup. Some naming and dependency findings described below have since been corrected in the source epic files.

---

## 1. Structural Consistency

### 1.1 Template Adherence Summary

The canonical template (EPIC_TEMPLATE.md) defines these sections:

| Section | Name |
|---------|------|
| Header | EPIC-ID, NAME, LAYER, MODULE, VERSION |
| 1 | Objective |
| 2 | Scope (In/Out, Dependencies, Gates, Classification) |
| 3 | Functional Requirements |
| 4 | Jurisdiction Isolation Requirements |
| 5 | Data Model Impact |
| 6 | Event Model Definition |
| 6.5 | Command Model Definition |
| 7 | AI Integration Requirements |
| 8 | NFRs |
| 9 | Acceptance Criteria |
| 10 | Failure Modes & Resilience |
| 11 | Observability & Audit |
| 12 | Compliance & Regulatory Traceability |
| 13 | Extension Points & Contracts |
| 14 | Future-Safe Architecture Evaluation |
| 14.5 | Threat Model |

### 1.2 Section 14.5 (Threat Model) — Missing in Many Epics

The following epics **have** Section 14.5 Threat Model (complete with attack vectors, mitigations, residual risks, and security controls):

| Epic | Status |
|------|--------|
| D-01 OMS | ✅ Present |
| D-02 EMS | ✅ Present |
| D-03 PMS | ✅ Present |
| D-04 Market Data | ✅ Present |
| D-05 Pricing Engine | ✅ Present |
| D-06 Risk Engine | ✅ Present |
| D-07 Compliance | ✅ Present (though truncated) |
| D-08 Surveillance | ✅ Present (complete) |
| D-09 Post-Trade | ✅ Present (complete) |
| D-10 Regulatory Reporting | ✅ Present (complete) |
| D-11 Reference Data | ✅ Present (complete) |
| D-12 Corporate Actions | ✅ Present (complete) |
| R-01 Regulator Portal | ✅ Present (complete, 7 vectors) |

The following epics are **missing** Section 14.5 Threat Model entirely:

| Epic | Risk Level |
|------|------------|
| **D-13 Client Money Reconciliation** | **HIGH** — handles client funds; threat model critical |
| **D-14 Sanctions Screening** | **HIGH** — compliance-critical; sanctions evasion vectors needed |
| **W-01 Workflow Orchestration** | MEDIUM — workflow manipulation threats |
| **W-02 Client Onboarding** | **HIGH** — PII handling, identity fraud |
| **P-01 Pack Certification** | **HIGH** — supply chain attack vectors |
| **R-02 Incident Notification** | MEDIUM — notification suppression threats |
| **T-01 Integration Testing** | LOW — testing infrastructure |
| **T-02 Chaos Engineering** | LOW — testing infrastructure |
| **O-01 Operator Workflows** | MEDIUM — operational privilege abuse |
| **PU-004 Platform Manifest** | **HIGH** — configuration tampering |
| **K-16 Ledger Framework** | **CRITICAL** — financial data integrity |
| **K-17 Distributed Transaction Coordinator** | **HIGH** — transaction manipulation |
| **K-18 Resilience Patterns** | LOW — infrastructure library |
| **K-19 DLQ Management** | MEDIUM — data loss concealment |

**Finding:** 14 of 28 reviewed epics lack threat models. The most critical gaps are K-16 (Ledger Framework), D-13 (Client Money), D-14 (Sanctions), and PU-004 (Platform Manifest).

### 1.3 Header Field Inconsistencies

- **D-13** and **D-14** include an `ARB-REF` field not present in the template or other domain epics. This is useful context but should be standardized across all epics that remediate ARB findings (e.g., D-01 references `[ARB D.2]` inline but has no header `ARB-REF`).
- **T-02** also includes `ARB-REF: P2-19` and **R-02** includes `ARB-REF: P1-15`.
- **K-17** includes `ARB-REF: P0-01`, **K-18** `ARB-REF: P0-02`, **K-19** `ARB-REF: P0-04`.
- **PU-004** previously had a dual identity in the source docs, mixing the Secrets Management ID with the Platform Manifest ID.

### 1.4 Changelog Sections

Per EPIC_VERSIONING_STRATEGY.md, all epics with version > 1.0.0 must include a Changelog section. **None of the 1.1.0 epics (D-01, D-06, D-07, D-08, D-10, K-16) include a Changelog section**, violating the versioning strategy.

### 1.5 Section 6 Deviation

- **T-01 Integration Testing**: Section 6 is declared as `N/A` and Section 6.5 is absent. Acceptable for a testing module but should explicitly state "N/A — Testing infrastructure does not emit business events" as it does.

### 1.6 Dual-Calendar in Data Models

- **O-01 Operator Workflows** (Section 5): Uses plain `Timestamp` for `created_at`, `resolved_at`, `scheduled_at` instead of `DualDate`. Since O-01 manages incidents and change requests that may need to align with BS fiscal periods, this is a gap.
- **T-01 Integration Testing** (Section 5): Uses plain `Timestamp` — acceptable for test infrastructure.
- **T-02 Chaos Engineering** (Section 5): Uses `DualDate` for experiment timestamps — ✅ consistent.

---

## 2. Version Inconsistencies

### 2.1 Versioning Strategy vs. Actual File Versions

EPIC_VERSIONING_STRATEGY.md (dated March 2, 2026) declares **all epics at v1.0.0**. However, the following epics have been bumped to v1.1.0 in their actual files:

| Epic | File VERSION | Strategy VERSION | Delta |
|------|-------------|-----------------|-------|
| EPIC-D-01 OMS | **1.1.0** | 1.0.0 | ❌ Mismatch |
| EPIC-D-06 Risk Engine | **1.1.0** | 1.0.0 | ❌ Mismatch |
| EPIC-D-07 Compliance | **1.1.0** | 1.0.0 | ❌ Mismatch |
| EPIC-D-08 Surveillance | **1.1.0** | 1.0.0 | ❌ Mismatch |
| EPIC-D-10 Regulatory Reporting | **1.1.0** | 1.0.0 | ❌ Mismatch |
| EPIC-K-16 Ledger Framework | **1.1.0** | 1.0.0 | ❌ Mismatch |

**6 epics have version mismatches.** All bumped epics also lack the mandated Changelog section.

### 2.2 Likely Cause of Version Bumps

Cross-referencing content, the v1.1.0 epics appear to have been bumped in response to ARB (Architecture Review Board) findings:
- D-01 v1.1.0: Added `[ARB D.2]` pre-trade routing clarification
- D-06 v1.1.0: Added `[ARB D.2]` K-03 integration pattern and `[ARB D.5]` jurisdiction isolation
- D-07 v1.1.0: Added `[ARB D.2]` K-03 registration pattern
- D-08 v1.1.0: Added `[ARB D.1]` event-only data access and `[ARB D.8]` latency budgets
- D-10 v1.1.0: Added `[GAP-004]` real-time trade reporting (FR7, FR8)
- K-16 v1.1.0: Added `[ARB P0-03]` precision/rounding, `[ARB D.7]` temporal query performance, `[ARB D.4]` idempotency collision handling

### 2.3 Missing Epics in Versioning Strategy

The following epics are **not listed** in the versioning strategy's status table:
- EPIC-D-13, EPIC-D-14, EPIC-K-17, EPIC-K-18, EPIC-K-19, EPIC-R-02, EPIC-T-02, EPIC-PU-004

These were likely created after the versioning strategy was written (evidenced by ARB-REF tags), but the strategy needs updating.

---

## 3. Plugin/Content Model Usage

### 3.1 Pack Taxonomy Consistency

The T1/T2/T3 pack taxonomy is generally well-applied:

| Pack Type | Purpose | Example Usages |
|-----------|---------|----------------|
| T1 Config | Static configuration values | Margin rates (D-06), Settlement cycles (D-09), Circuit breaker thresholds (D-04) |
| T2 Rule | Declarative regulatory rules | Compliance rules (D-07), Surveillance patterns (D-08), Tax rates (D-12) |
| T3 Executable | Adapter/model code | Exchange adapters (D-02), Depository adapters (D-09), Pricing models (D-05) |

### 3.2 Pack Usage Gaps

| Epic | Issue | Section |
|------|-------|---------|
| D-03 PMS | Section 8 NFRs mention "Custom rebalancing algorithms via T3 Packs" but Section 4 (Jurisdiction Isolation) only mentions T2 Rule Packs. T3 should also be mentioned in Section 4 for algorithm extensibility. | Section 4 vs Section 8 |
| D-06 Risk Engine | Section 2 mentions "T3 Risk Model Packs (e.g., VaR models)" but Section 13 Extension Points only lists "T2 Rule Packs for margin eligibility" — missing T3 extension point. | Section 2 vs Section 13 |
| D-09 Post-Trade | Section 4 mentions T3 Depository Adapters but K-04 (Plugin Runtime) is not in the dependency list. T3 packs require K-04 to execute. | Section 2 Dependencies |
| D-10 Regulatory Reporting | Section 13 lists "T1 Template Packs, T3 Portal Adapters" but K-04 (Plugin Runtime) not in dependencies. | Section 2 Dependencies |
| D-11 Reference Data | T3 Data Provider Adapters mentioned but K-04 not in dependencies. | Section 2 Dependencies |
| D-12 Corporate Actions | T3 Depository Adapters mentioned but K-04 not in dependencies. | Section 2 Dependencies |

### 3.3 Pack Type Misclassification Risks

- **D-04 Market Data** Section 4 item 2: "Circuit breaker rules (e.g., Nepal's 4%, 5%, 6% halt thresholds) are **T2 Rule Packs or T1 Config Packs**" — ambiguous. Should decide: if thresholds are simple numeric values, they should be T1; if they involve conditional logic (different thresholds per sector), T2.
- **D-10 Regulatory Reporting**: Report templates classified as T1 Config Packs. However, complex templates with conditional logic (e.g., "include section X only if mutual fund trades exist") may warrant T2 classification.

---

## 4. Dependency Accuracy

### 4.1 Missing Dependencies (Systematic Gaps)

Many domain epics reference kernel modules in their text but fail to list them in the Section 2 Dependencies field:

| Epic | Missing Dependency | Evidence |
|------|-------------------|----------|
| **D-01 OMS** | K-02 (Config Engine) | Section 4: "K-02 provides the applicable validation ruleset" |
| **D-01 OMS** | K-07 (Audit Framework) | Section 7: "explanation is stored in K-07"; Section 11: audit events |
| **D-02 EMS** | K-15 (Dual-Calendar) | Section 3 FR6: "Timestamps for execution must log both" |
| **D-02 EMS** | K-07 (Audit Framework) | Section 11: audit events logged |
| **D-03 PMS** | K-03 (Rules Engine) | Section 4: regulatory rules evaluated; T2 Rule Packs require K-03 |
| **D-03 PMS** | K-15 (Dual-Calendar) | Section 3 FR5: dual-calendar stamping |
| **D-03 PMS** | D-01 (OMS) | Section 3 FR4: "before being sent to the OMS" |
| **D-04 Market Data** | K-15 (Dual-Calendar) | Section 3 FR6: dual-calendar OHLC |
| **D-04 Market Data** | K-02 (Config Engine) | Section 3 FR3: "loaded via K-02 Config Engine" |
| **D-05 Pricing Engine** | K-05 (Event Bus) | Section 6: emits PriceUpdatedEvent |
| **D-05 Pricing Engine** | K-15 (Dual-Calendar) | Section 3 FR5: dual-calendar stamping |
| **D-05 Pricing Engine** | K-02 (Config Engine) | Section 4: "Config Engine maps instrument classes" |
| **D-06 Risk Engine** | K-02 (Config Engine) | Section 4: "Config Engine maps jurisdiction to correct margin rate tables" |
| **D-06 Risk Engine** | K-15 (Dual-Calendar) | Section 3 FR6: dual-calendar for margin calls |
| **D-07 Compliance** | K-02 (Config Engine) | Section 4: "K-02 Config Engine determines which rule pack applies" |
| **D-07 Compliance** | K-15 (Dual-Calendar) | Section 3 FR6: dual-calendar enforcement |
| **D-08 Surveillance** | K-07 (Audit Framework) | Section 11/12: "All case actions logged"; "[LCA-AUDIT-001]" |
| **D-08 Surveillance** | K-02 (Config Engine) | Section 4: "Config Engine determines which surveillance rules apply" |
| **D-09 Post-Trade** | K-04 (Plugin Runtime) | T3 Depository Adapters require K-04 |
| **D-09 Post-Trade** | K-18 (Resilience Patterns) | Section 10: retry/backoff patterns implied |
| **D-10 Regulatory Reporting** | K-02 (Config Engine) | Section 4: "Config Engine determines which reports are required" |
| **D-10 Regulatory Reporting** | K-04 (Plugin Runtime) | T3 Portal Adapters require K-04 |
| **D-11 Reference Data** | K-04 (Plugin Runtime) | T3 Data Provider Adapters |
| **D-11 Reference Data** | K-02 (Config Engine) | Section 4: "Config Engine provides the active taxonomy" |
| **D-11 Reference Data** | K-07 (Audit Framework) | Section 11: audit events |
| **D-12 Corporate Actions** | K-04 (Plugin Runtime) | T3 Depository Adapters |
| **D-12 Corporate Actions** | K-02 (Config Engine) | Section 4: "Config Engine determines which rules apply" |

**Total: 27 missing dependency declarations across 12 epics.**

### 4.2 Kernel Readiness Gate Inconsistencies

Some epics list modules in Kernel Readiness Gates that aren't in Dependencies, and vice versa:

| Epic | In Readiness Gates but not Dependencies | In Dependencies but not Gates |
|------|----------------------------------------|-------------------------------|
| D-03 PMS | K-02 | D-05 (Pricing Engine) |
| D-01 OMS | — | K-18 (Resilience Patterns) |

### 4.3 Missing Reverse Dependencies

Some events are consumed by modules not listed in the producing epic's consumer list:
- **TradeExecuted** (D-02 EMS Section 6): Lists consumers as "OMS (Position Update), Post-Trade, Surveillance." Missing: **D-03 PMS** (consumes for position ingestion per D-03 FR1).
- **LedgerPostedEvent** (K-16 Section 6): Lists consumers as "Regulatory Reporting (D-10), Trade Surveillance (D-08), Audit Framework." Missing: **D-03 PMS** (reconciles against LedgerPostedEvent per D-03 FR1).
- **ReferenceDataUpdated** (D-11 Section 6): Lists consumers as "OMS, PMS, Pricing, Compliance." Missing: **D-02 EMS** (needs instrument-venue mappings) and **D-12 Corporate Actions** (needs instrument updates).

---

## 5. NFR Consistency

### 5.1 Availability Target Analysis

| Epic | Availability | Qualification |
|------|-------------|---------------|
| D-01 OMS | 99.999% | During trading hours |
| D-02 EMS | 99.999% | During market hours |
| D-04 Market Data | 99.999% | During market hours |
| D-06 Risk Engine | 99.999% | During market hours |
| D-07 Compliance | 99.999% | (unqualified) |
| D-11 Reference Data | 99.999% | (unqualified) |
| D-14 Sanctions Screening | 99.999% | (unqualified) |
| R-02 Incident Notification | 99.999% | (unqualified) |
| K-16 Ledger Framework | 99.999% | (unqualified) |
| K-17 Distributed Transaction | 99.999% | (unqualified) |
| PU-004 Platform Manifest | 99.999% | (unqualified) |
| D-03 PMS | 99.99% | (unqualified) |
| D-05 Pricing Engine | 99.99% | (unqualified) |
| D-08 Surveillance | 99.99% | (unqualified) |
| D-09 Post-Trade | 99.99% | (unqualified) |
| D-10 Regulatory Reporting | 99.99% | (unqualified) |
| D-12 Corporate Actions | 99.99% | (unqualified) |
| D-13 Client Money Recon | 99.99% | (unqualified) |
| K-19 DLQ Management | 99.99% | (unqualified) |
| W-01 Workflow Orchestration | 99.99% | (unqualified) |
| W-02 Client Onboarding | **99.9%** | (non-critical path) |
| P-01 Pack Certification | **99.9%** | (unqualified) |
| R-01 Regulator Portal | **99.9%** | (unqualified) |
| T-01 Integration Testing | **99%** | (unqualified) |
| T-02 Chaos Engineering | **99.9%** | (non-critical path) |
| O-01 Operator Workflows | **99.9%** | (unqualified) |

**Issues:**
1. **D-14 Sanctions Screening at 99.999%**: This module depends on external sanctions list providers. Achieving five nines while relying on external APIs is unrealistic without significant caching infrastructure. Should qualify as "99.999% for local cache screening; 99.9% for list refresh operations."
2. **D-07 Compliance at 99.999% unqualified**: Should be qualified "during trading hours" like D-01 and D-06 since it's in the pre-trade critical path.
3. **R-02 Incident Notification at 99.999%**: Stated as critical regulatory path, but this means the incident notification system itself must never go down — ambitious for a system that integrates with external channels (email, SMS, PagerDuty).

### 5.2 Latency Target Concerns

| Epic | Claimed Latency | Concern |
|------|----------------|---------|
| D-01 OMS | < 2ms, 50,000 TPS | Ambitious given pre-trade eval involves K-03 → D-06 → D-07 chain |
| D-06 Risk Engine | Pre-trade < 2ms | Must include K-03 orchestration overhead |
| D-07 Compliance | Pre-trade < 5ms | D-01 claims 2ms total but D-07 alone claims 5ms — **contradiction** |
| D-04 Market Data | < 500 microseconds | Highly aggressive — achievable with kernel bypass networking but not standard Java/containerized deployments |

**Critical Issue:** D-01 OMS FR3 says K-03 orchestrates both D-06 (< 2ms) and D-07 (< 5ms) pre-trade checks. The OMS claims total internal processing < 2ms. This is **impossible** if the pre-trade pipeline includes D-07's 5ms compliance check. Either:
- D-01's 2ms target excludes pre-trade pipeline (should be stated explicitly), or
- D-07's 5ms must be reduced to < 1ms, or
- The flow needs to be parallel (D-06 and D-07 evaluate concurrently via K-03).

### 5.3 Data Retention Inconsistencies

| Data Category | D-01 | D-04 | D-05 | D-09 | D-10 | D-12 |
|--------------|------|------|------|------|------|------|
| Retention Period | 7 years | **10 years** | **10 years** | **10 years** | **10 years** | **10 years** |

D-01 OMS retains order data for 7 years while D-04 Market Data retains tick data for 10 years. This creates a gap: an order from year 8 cannot be cross-referenced with its historical market data context 2 years after the order record is purged. **Recommendation:** Align all trading-related retention to 10 years minimum, or ensure order data references are preserved as part of market data.

---

## 6. Missing Real-World Scenarios

### 6.1 Critical Missing Operations

| # | Missing Capability | Impact | Suggested Epic |
|---|-------------------|--------|----------------|
| 1 | **IPO / Primary Market Operations** — Bookbuilding, subscription management, allotment, refund processing | Cannot serve investment banks or merchant bankers for primary issuance | D-15 Primary Market |
| 2 | **Fee & Commission Engine** — Broker fees, exchange levies, SEBON regulatory charges, DPSS charges, stamp duty | Revenue calculation and regulatory charge compliance impossible | D-16 Fee Engine or K-20 Fee Framework |
| 3 | **Client Notification / Contract Note** — Trade confirmations, contract notes, monthly statements, tax certificates | Regulatory requirement in all jurisdictions; client-facing | D-17 Client Communications |
| 4 | **Capital Gains Tax Computation** — Holding period computation (short-term vs long-term), cost basis tracking, tax statement generation | D-12 handles TDS on dividends only; no comprehensive CGT | Extend D-12 or new D-18 Tax Engine |
| 5 | **Securities Lending & Borrowing** — Loan creation, collateral management, recall, return, fee accrual | Major revenue stream for institutional platforms | D-19 SLB |
| 6 | **Collateral/Pledge Management** — Margin pledge/unpledge at depository, collateral valuation, haircut application | D-06 calculates margin but no pledge lifecycle | D-20 Collateral Management |
| 7 | **Payment Gateway / Fund Transfer** — Client-to-broker fund transfers, payout processing, bank integration | No money movement module; D-13 reconciles but doesn't move funds | D-21 Payment Operations |
| 8 | **Auction Market Operations** — Close-out auctions for failed settlements, penalty computation | SEBON mandates auction sessions for delivery failures | Extend D-09 or new module |
| 9 | **Multi-leg / Algorithmic Order Types** — Bracket orders, stop-loss triggers, conditional orders, iceberg orders | D-01 covers basic order lifecycle only | Extend D-01 |
| 10 | **Client Reporting & Dashboards** — P&L reports, portfolio performance attribution, investment summaries | No client-facing reporting module | D-22 Client Portal Backend |

### 6.2 Operational Gaps

| # | Gap | Reference |
|---|-----|-----------|
| 1 | No explicit **market opening/closing ceremony** automation (pre-market validation, EOD batch sequence) | O-01 has runbooks but no explicit market-day lifecycle |
| 2 | No **reconciliation orchestration** tying D-13 (cash), D-09 (securities), and K-16 (ledger) into a unified daily recon | Each module reconciles independently |
| 3 | No **commission sharing / revenue split** between sub-brokers, RTAs, and main brokers | Common in South Asian markets |

---

## 7. Regulatory Gaps

### 7.1 Missing Regulatory Coverage

| # | Regulation / Requirement | Current Coverage | Gap |
|---|-------------------------|-----------------|-----|
| 1 | **GDPR / Data Privacy** (DSAR, right to erasure, consent management) | Only R-02 mentions GDPR 72-hour breach notification | No data subject rights workflow, no consent management, no personal data inventory |
| 2 | **Beneficial Ownership (UBO)** transparency | D-11 has legal entity master but no UBO hierarchy | Required for AML compliance in most jurisdictions |
| 3 | **Position Limits Monitoring** (market-wide net position per client) | D-06 covers margin; no explicit position limit enforcement | SEBON and most regulators mandate position limits |
| 4 | **Short Selling Disclosure** | Not mentioned in any epic | Required in markets that permit short selling |
| 5 | **Trade Repository Reporting** (EMIR/Dodd-Frank equivalents) | D-10 covers regulatory reporting generally | No specific trade repository integration for derivatives |
| 6 | **FATCA / CRS** (tax information exchange) | Not mentioned | Required for cross-border client accounts |
| 7 | **Whistleblower Protection** | Not mentioned | Required under most securities regulations |
| 8 | **Market Maker Obligations** | Not mentioned | Needed if platform supports market making |
| 9 | **Investor Compensation Scheme** | Not mentioned | Required in many jurisdictions for client protection |
| 10 | **Business Continuity / Operational Resilience** (formal BCP) | T-02 covers DR drills; O-01 has some procedures | No formal BCP document epic |

### 7.2 Jurisdiction-Specific Regulatory Gaps (Nepal)

| # | Nepal-Specific Requirement | Current Status |
|---|--------------------------|---------------|
| 1 | **SEBON Broker Inspection Readiness** — regulators may require live system access | R-01 provides read-only portal; no on-site inspection protocol |
| 2 | **NRB Reporting** — specific NRB mandated reports for margin lending | D-10 is generic; no NRB-specific template coverage documented |
| 3 | **CDSC EDIS Integration** — Electronic Delivery Instruction Slip for settlements | D-09 mentions EDIS in passing but no detailed workflow |
| 4 | **Meroshare Integration** — Nepal's shareholder portal | Not mentioned in any epic |
| 5 | **CIT (Capital Income Tax)** computation per Nepal tax code | Only TDS on dividends covered (D-12) |

---

## 8. Future-Proofing Gaps

### 8.1 Emerging Market Capabilities

| # | Capability | Current Status | Recommendation |
|---|-----------|---------------|----------------|
| 1 | **Digital Assets / Tokenized Securities** | K-16 mentions 8 decimal precision for crypto; no custody, wallet, or token lifecycle | Add D-XX Digital Asset Custody epic or extend D-11 |
| 2 | **ESG Integration** | Not mentioned in any epic | Add ESG scoring to D-11, ESG-filtered screening to D-03 PMS, ESG reporting templates to D-10 |
| 3 | **T+0 / Real-Time Gross Settlement** | D-09 handles T+n but no RTGS workflow | Extend D-09 FR1 to support T+0 with streaming settlement |
| 4 | **Central Bank Digital Currency (CBDC)** | Not mentioned | K-16 should plan for CBDC as a currency type |
| 5 | **Open Banking / API Standards** | No PSD2/Open Banking API layer | Extend K-11 API Gateway with open banking profiles |
| 6 | **Quantum-Resistant Cryptography** | Not mentioned | P-01 Pack Certification should plan for post-quantum signing |
| 7 | **Embedded Finance APIs** | Not mentioned | Future K-11 extension |
| 8 | **AI-Native Trading Strategies** | Each epic has AI hooks for copilot/anomaly; no autonomous trading agent framework | Consider W-XX Algo Trading Workflow |
| 9 | **Cross-Chain / DeFi Interoperability** | Not mentioned | Long-term consideration for D-02 EMS |
| 10 | **Real-Time Risk (Streaming VaR)** | D-06 has real-time margin calculation but no streaming VaR | Extend D-06 for streaming risk models via T3 |

### 8.2 Section 14 Quality Assessment

Most Section 14 (Future-Safe Architecture Evaluation) tables are too terse. They typically ask only 3 questions. The template doesn't mandate specific questions. Recommendation: Standardize a minimum set:

1. Can this module support India/Bangladesh via plugin?
2. Can rules/configs change without redeploy?
3. Can this run in an air-gapped deployment?
4. Can this support digital assets / tokenized securities?
5. Can this support T+0 settlement?
6. Can this integrate with open banking APIs?

Only D-01, D-02, D-03 consistently ask the first three; none ask about digital assets or T+0.

---

## 9. Extension Points Completeness

### 9.1 Weak Extension Point Definitions

Several epics' Section 13 (Extension Points & Contracts) are too vague:

| Epic | Section 13 Content | Issue |
|------|-------------------|-------|
| D-08 Surveillance | "SDK Contract: Consumes K-05." | **No API exposed**. Other modules that need surveillance data (e.g., R-01 for compliance evidence) have no contract to query. |
| D-09 Post-Trade | "SDK Contract: Consumes `TradeExecuted` events." | **No API exposed**. No `SettlementClient.getStatus(obligationId)` or similar. |
| D-10 Regulatory Reporting | "SDK Contract: Consumes K-05 and K-16." | **No API exposed**. No `ReportClient.generateReport()` or `ReportClient.getStatus()`. |
| D-12 Corporate Actions | "SDK Contract: Consumes K-05 and K-16." | **No API exposed**. No `CorporateActionClient.getEntitlement()` or similar. |

### 9.2 Strong Extension Point Definitions (Exemplars)

| Epic | Quality | Notes |
|------|---------|-------|
| D-13 Client Money Recon | ✅ Excellent | `ReconClient.runRecon(date)`, `getBreaks(runId)`, `resolveBreak(breakId, resolution)` |
| D-14 Sanctions Screening | ✅ Excellent | `SanctionsClient.screen(entity)`, `reviewMatch(matchId, decision)`, `getListStatus()` |
| W-01 Workflow Orchestration | ✅ Good | `WorkflowClient.startWorkflow()`, `completeTask()`, `cancelWorkflow()` |
| W-02 Client Onboarding | ✅ Good | `OnboardingClient.submitApplication()`, `uploadDocument()`, `getApplicationStatus()` |
| K-16 Ledger Framework | ✅ Good | `LedgerClient.post(transaction)`, `getBalance(accountId, asOf)` |

**Recommendation:** D-08, D-09, D-10, D-12 should define explicit SDK contracts following the pattern of D-13/D-14.

---

## 10. Event Model Completeness

### 10.1 Missing Consumer Declarations

Events are produced but not all consumers are declared:

| Event | Producer | Declared Consumers | Missing Consumers |
|-------|----------|-------------------|-------------------|
| `TradeExecuted` | D-02 EMS | OMS, Post-Trade, Surveillance | **D-03 PMS** (FR1: subscribes to TradeExecuted), **D-06 Risk Engine** (real-time position monitoring), **D-10 Regulatory Reporting** (FR7: real-time trade reporting) |
| `LedgerPostedEvent` | K-16 | D-10, D-08, Audit Framework | **D-03 PMS** (FR1: reconciles against LedgerPostedEvent) |
| `ReferenceDataUpdated` | D-11 | OMS, PMS, Pricing, Compliance | **D-02 EMS** (venue-instrument mappings), **D-12 Corporate Actions** (instrument data) |
| `PriceUpdatedEvent` | D-05 | PMS, Risk Engine | **D-08 Surveillance** (price-based pattern detection), **D-10 Regulatory Reporting** (mark-to-market) |
| `MarginCallIssued` | D-06 | Notification, Client Portal, OMS | **D-10 Regulatory Reporting** (margin call reporting to regulators) |
| `SurveillanceAlertGenerated` | D-08 | Case Management UI, K-07 Audit | **R-02 Incident Notification** (P1 cases may require regulator notification), **D-10 Regulatory Reporting** (surveillance activity reporting) |
| `SettlementCompleted` | D-09 | OMS, Reg Reporting, Client Notification | **D-03 PMS** (position updates), **D-13 Client Money Recon** (settlement confirmation for reconciliation) |
| `ClientMoneySegregationBreachEvent` | D-13 | K-06 Alerting, R-02, Compliance, Management | ✅ Well-defined |

### 10.2 Missing Cross-Epic Events

| Event That Should Exist | Context | Impact |
|------------------------|---------|--------|
| `ClientActivatedEvent` | W-02 onwards to D-01, K-16 | W-02 creates accounts but no explicit event tells the ecosystem a new client is active |
| `SanctionsListUpdatedEvent` → batch re-screening trigger | D-14 mentions list refresh but no event to trigger batch re-screening | D-14 FR5 requires batch screening on list update but the trigger event is not in D-14's Section 6 |
| `ForcedLiquidationInitiated` | D-06 emits `ForcedLiquidationInstruction` but D-01 OMS doesn't list this as a consumed event | Gap in forced liquidation flow |
| `RebalanceOrdersSubmitted` | D-03 PMS → D-01 OMS: after approval, orders are sent but no explicit event defined | Rebalancing → OMS handoff is informal |
| `ComplianceCheckPassed` | D-07 defines this in Section 6.5 Command Model but it's not in Section 6 Event Model | Missing from the event catalog |

### 10.3 Event Schema Version Alignment

All events across all epics are at `v1.0.0`. This is consistent but lacks a strategy for event schema evolution (Avro/Protobuf schema registry, backward/forward compatibility). K-05 Event Bus epic should address this but it's not in our review scope.

---

## 11. Cross-Cutting Observations

### 11.1 Compliance Code Fragmentation

Compliance references across epics use different code systems:

| Code Style | Example | Epics Using |
|-----------|---------|-------------|
| `LCA-*` | LCA-AUDIT-001, LCA-SOD-001, LCA-BESTEX-001 | D-01 through D-14, W-01, W-02 |
| `ASR-*` | ASR-RPT-001, ASR-MARG-001, ASR-SURV-001 | D-03, D-04, D-06, D-08, D-10 |

No central compliance code registry document is referenced within the analyzed epics (COMPLIANCE_CODE_REGISTRY.md exists in the epics folder but is not cross-referenced).

### 11.2 K-02 Config Engine is Universally Under-Declared

**Every domain epic** (D-01 through D-14) references K-02 Config Engine in their Section 4 (Jurisdiction Isolation) for resolution flow, but only D-13 and D-14 list K-02 in their Section 2 Dependencies. K-02 is arguably the most universally-depended-upon kernel module and should be in every domain epic's dependency list.

### 11.3 K-07 Audit Framework is Under-Declared

**Most domain epics** reference K-07 in Section 11 (Observability & Audit) for audit events, but only D-07 and D-10 list K-07 in their dependencies.

### 11.4 Maker-Checker Pattern Inconsistency

Maker-checker is mentioned across many epics but with varying specificity:

| Epic | Maker-Checker Coverage | Specificity |
|------|----------------------|-------------|
| D-01 OMS | Restricted order types | ✅ Clear trigger conditions |
| D-03 PMS | Mutual fund rebalancing | ✅ Clear |
| D-05 Pricing | Price overrides | ✅ Clear (in command model) |
| D-07 Compliance | Policy violations | ✅ Clear |
| D-11 Reference Data | Critical ISIN changes | ✅ Clear |
| D-13 Client Money | Break resolution > threshold | ✅ Clear ($50K threshold) |
| D-14 Sanctions | TRUE_MATCH actions | ✅ Clear |
| D-12 Corporate Actions | Not explicitly mentioned | ❌ Missing for payment processing |
| D-09 Post-Trade | Not for settlement overrides | ❌ Manual settlement should require maker-checker |
| D-10 Regulatory Reporting | Not for report amendments | ❌ Report regeneration should require approval |

---

## 12. Summary of Critical Findings

### Priority 0 (Must Fix Immediately)

| # | Finding | Affected Epics |
|---|---------|---------------|
| 1 | **Latency arithmetic impossible:** D-01 claims < 2ms total but pre-trade pipeline includes D-07 (< 5ms) and D-06 (< 2ms) | D-01 §8, D-06 §8, D-07 §8 |
| 2 | **6 version mismatches** between epic files and EPIC_VERSIONING_STRATEGY.md; no changelogs | D-01, D-06, D-07, D-08, D-10, K-16 |
| 3 | **K-16 Ledger Framework missing Threat Model** — financial data integrity attack vectors undefined | K-16 §14.5 |
| 4 | **27 missing dependency declarations** across 12 epics (K-02 and K-07 systematically under-declared) | See §4.1 table |

### Priority 1 (Fix Before Implementation)

| # | Finding | Affected Epics |
|---|---------|---------------|
| 5 | **14 epics missing Threat Model** (Section 14.5) — especially D-13, D-14, K-17, PU-004 | See §1.2 table |
| 6 | **4 epics with vague extension points** — no SDK contracts defined | D-08, D-09, D-10, D-12 §13 |
| 7 | **6 missing consumer declarations** in event models — cross-epic data flows incomplete | See §10.1 table |
| 8 | **5 missing cross-epic events** — workflow handoffs not formalized | See §10.2 table |
| 9 | **K-04 Plugin Runtime missing** from 4 epic dependencies that use T3 packs | D-09, D-10, D-11, D-12 §2 |

### Priority 2 (Fix During Implementation)

| # | Finding | Affected Epics |
|---|---------|---------------|
| 10 | **10 missing real-world scenarios** — IPO, fees, client notifications, CGT, SLB, payments | All domain epics |
| 11 | **10 regulatory gaps** — GDPR rights, UBO, position limits, FATCA/CRS | D-07, D-08, D-10, D-11 |
| 12 | **D-14 99.999% availability unrealistic** for external API dependency | D-14 §8 |
| 13 | **Data retention misalignment** — D-01 (7 years) vs D-04/D-05/D-09/D-10 (10 years) | Cross-epic §5.3 |
| 14 | **O-01 missing DualDate** in data model for incident tracking | O-01 §5 |
| 15 | **8 epics missing from versioning strategy table** | EPIC_VERSIONING_STRATEGY.md |

### Priority 3 (Improve Over Time)

| # | Finding | Affected Epics |
|---|---------|---------------|
| 16 | **Future-proofing gaps** — digital assets, ESG, T+0, CBDC, quantum crypto, open banking | Section 14 across all epics |
| 17 | **ARB-REF header field** not standardized across all ARB-related epics | D-01, D-06, D-07, D-08, K-16 |
| 18 | **Compliance code registry** not cross-referenced from epics | All epics §12 |
| 19 | **Maker-checker gaps** in D-09 (manual settlement), D-10 (report amendments), D-12 (payment processing) | D-09, D-10, D-12 |
| 20 | **Section 14 tables** too terse — should standardize minimum 6 future-proofing questions | All epics §14 |

---

## Appendix A: Epic-by-Epic Checklist

| Epic | Version Match | Threat Model | All Deps Listed | SDK Contract | DualDate | Changelog |
|------|:---:|:---:|:---:|:---:|:---:|:---:|
| D-01 OMS | ❌ 1.1.0 | ✅ | ❌ (K-02, K-07) | ✅ | ✅ | ❌ |
| D-02 EMS | ✅ | ✅ | ❌ (K-15, K-07) | ✅ | ✅ | N/A |
| D-03 PMS | ✅ | ✅ | ❌ (K-03, K-15, D-01) | ✅ | ✅ | N/A |
| D-04 Market Data | ✅ | ✅ | ❌ (K-15, K-02) | ✅ | ✅ | N/A |
| D-05 Pricing | ✅ | ✅ | ❌ (K-05, K-15, K-02) | ✅ | ✅ | N/A |
| D-06 Risk | ❌ 1.1.0 | ✅ | ❌ (K-02, K-15) | ✅ | ✅ | ❌ |
| D-07 Compliance | ❌ 1.1.0 | ✅ | ❌ (K-02, K-15) | ✅ | ✅ | ❌ |
| D-08 Surveillance | ❌ 1.1.0 | ✅ | ❌ (K-07, K-02) | ❌ Vague | ✅ | ❌ |
| D-09 Post-Trade | ✅ | ✅ | ❌ (K-04, K-18) | ❌ Vague | ✅ | N/A |
| D-10 Reg Reporting | ❌ 1.1.0 | ✅ | ❌ (K-02, K-04) | ❌ Vague | ✅ | ❌ |
| D-11 Reference Data | ✅ | ✅ | ❌ (K-04, K-02, K-07) | ✅ | ✅ | N/A |
| D-12 Corp Actions | ✅ | ✅ | ❌ (K-04, K-02) | ❌ Vague | ✅ | N/A |
| D-13 Client Money | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| D-14 Sanctions | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| W-01 Workflow | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| W-02 Onboarding | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| P-01 Pack Cert | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| R-01 Regulator | ✅ | ✅ | ✅ | ✅ | ✅ | N/A |
| R-02 Incident | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| T-01 Integration | ✅ | ❌ | ✅ | ✅ | N/A | N/A |
| T-02 Chaos | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| O-01 Operator | ✅ | ❌ | ✅ | ✅ | ❌ | N/A |
| PU-004 Manifest | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| K-16 Ledger | ❌ 1.1.0 | ❌ | ✅ | ✅ | ✅ | ❌ |
| K-17 DTC | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |
| K-18 Resilience | ✅ | ❌ | ✅ | ✅ | N/A | N/A |
| K-19 DLQ | ✅ | ❌ | ✅ | ✅ | ✅ | N/A |

---

**Report Status:** COMPLETE  
**Total Findings:** 20 (4 P0, 5 P1, 6 P2, 5 P3)  
**Recommended Next Steps:**  
1. Fix P0 latency arithmetic contradiction before sprint planning  
2. Synchronize EPIC_VERSIONING_STRATEGY.md with actual file versions  
3. Add Threat Models to the 14 missing epics (prioritize K-16, D-13, D-14, PU-004)  
4. Systematically add K-02 and K-07 to all domain epic Dependencies  
5. Define explicit SDK contracts for D-08, D-09, D-10, D-12  
