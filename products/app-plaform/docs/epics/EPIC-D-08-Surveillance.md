EPIC-ID: EPIC-D-08
EPIC NAME: Trade Surveillance
LAYER: DOMAIN
MODULE: D-08 Trade Surveillance
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the D-08 Trade Surveillance module, responsible for detecting market abuse, insider trading, and wash sales. This epic ensures compliance with Principle 17 (AI as Substrate) and Principle 2 (Full Externalization via Jurisdiction Plugins) by combining deterministic T2 Rule Packs (e.g., standard wash trade rules) with AI anomaly detection, generating structured alerts for human investigation.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Real-time and end-of-day pattern detection (wash trades, spoofing, front-running).
  2. Case management workflow for compliance officers.
  3. Integration with K-05 Event Bus to consume `OrderStateChanged` and `TradeExecuted`.
  4. AI model hooks for advanced pattern recognition.
- **Out-of-Scope:**
  1. The actual SEBON or NEPSE specific surveillance rules - these are T2 Rule Packs.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-06 (Observability), EPIC-K-07 (Audit Framework), EPIC-K-09 (AI Governance), EPIC-K-15 (Dual-Calendar), EPIC-K-18 (Resilience Patterns)
- **Note:** D-08 consumes D-01 and D-04 data exclusively through K-05 Event Bus events (`OrderStateChanged`, `TradeExecuted`, `MarketDataUpdated`). No direct API calls to D-01 or D-04 are permitted. [ARB D.1]
- **Kernel Readiness Gates:** K-03, K-05, K-09
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Pattern Ingestion:** Consume normalized orders and trades from K-05 and evaluate them against surveillance scenarios.
2. **FR2 Rule Evaluation:** Use K-03 to evaluate deterministic scenarios (e.g., wash sale logic defined in a T2 pack).
3. **FR3 Alert Generation:** Generate a `SurveillanceAlertGenerated` event when a pattern is detected.
4. **FR4 Case Management:** Group related alerts into a `SurveillanceCase` with workflow states (Open, Investigating, Closed-False-Positive, Escalated).
5. **FR5 Dual-Calendar Context:** Ensure case timelines and reported events use dual-calendar dates.
6. **FR6 Latency Budget:** Real-time pattern detection must complete within defined latency budgets: (a) simple patterns (single-order anomalies): P99 < 10ms, (b) complex patterns (cross-order wash trade detection across up to 1,000 orders): P99 < 500ms, (c) end-of-day batch analysis: complete within 30 minutes of market close. Patterns exceeding latency budget emit a performance alert via K-06 but do not drop the analysis. [ARB D.8]
7. **FR7 Event-Only Data Access:** All order and trade data must be consumed exclusively via K-05 event subscriptions. D-08 must maintain its own read projections of order/trade data built from events. Direct database queries or API calls to D-01 OMS or D-04 Market Data are strictly prohibited to maintain layering integrity. [ARB D.1]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The ingestion, alert generation, and case management framework are generic.
2. **Jurisdiction Plugin:** The specific patterns to monitor (e.g., SEBON's definition of a wash trade) are defined in T2 Rule Packs.
3. **Resolution Flow:** Config Engine determines which surveillance rules apply per jurisdiction.
4. **Hot Reload:** New detection rules apply instantly to the data stream.
5. **Backward Compatibility:** Closed cases retain the rule text active at the time of the alert.
6. **Future Jurisdiction:** A new country's regulatory parameters are simply a new T2 pack.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `SurveillanceAlert`: `{ alert_id: UUID, rule_ref: String, entity_id: UUID, trigger_events: List<UUID>, severity: Enum, created_at_bs: String }`
  - `SurveillanceCase`: `{ case_id: UUID, status: Enum, resolution_notes: String }`
- **Dual-Calendar Fields:** `created_at_bs` in alerts.
- **Event Schema Changes:** `SurveillanceAlertGenerated`, `SurveillanceCaseUpdated`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                 |
| ----------------- | ------------------------------------------------------------------------------------------- |
| Event Name        | `SurveillanceAlertGenerated`                                                                |
| Schema Version    | `v1.0.0`                                                                                    |
| Trigger Condition | A pattern matching a surveillance rule or AI model threshold is detected.                   |
| Payload           | `{ "alert_id": "...", "pattern": "WASH_TRADE", "confidence": 0.95, "timestamp_bs": "..." }` |
| Consumers         | Case Management UI, K-07 Audit                                                              |
| Idempotency Key   | `hash(rule_ref + entity_id + time_window)`                                                  |
| Replay Behavior   | Updates the case dashboard.                                                                 |
| Retention Policy  | Permanent.                                                                                  |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CreateAlertCommand`                                                 |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Scenario triggered, evidence collected, severity assigned            |
| Handler          | `AlertCommandHandler` in D-08 Surveillance                           |
| Success Event    | `SurveillanceAlertGenerated`                                         |
| Failure Event    | `AlertCreationFailed`                                                |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `UpdateCaseCommand`                                                  |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Case exists, status transition valid, requester authorized           |
| Handler          | `CaseCommandHandler` in D-08 Surveillance                            |
| Success Event    | `CaseUpdated`                                                        |
| Failure Event    | `CaseUpdateFailed`                                                   |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `EscalateCaseCommand`                                                |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Case exists, escalation reason provided, target reviewer assigned    |
| Handler          | `CaseCommandHandler` in D-08 Surveillance                            |
| Success Event    | `CaseEscalated`                                                      |
| Failure Event    | `CaseEscalationFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Autonomous Agent / Pattern Recognition
- **Workflow Steps Exposed:** Order book and trade feed analysis.
- **Model Registry Usage:** `market-abuse-detector-v1`
- **Explainability Requirement:** AI highlights the specific sequence of orders (e.g., layering/spoofing pattern) that led to the alert.
- **Human Override Path:** Compliance officer reviews the alert and can mark it as a False Positive, feeding back to the model.
- **Drift Monitoring:** False positive rate tracked continuously.
- **Fallback Behavior:** Deterministic T2 rules continue running.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                         |
| ------------------------- | -------------------------------------------------------- |
| Latency / Throughput      | Analyze events < 50ms behind live feed                   |
| Scalability               | Stream processing (e.g., Flink/Spark semantics)          |
| Availability              | 99.99%                                                   |
| Consistency Model         | Eventual consistency                                     |
| Security                  | Highly restricted access to case data                    |
| Data Residency            | Enforced via K-08                                        |
| Data Retention            | 10 years minimum                                         |
| Auditability              | All case actions logged                                  |
| Observability             | Metrics: `alert.generation.rate`, `false_positive.ratio` |
| Extensibility             | New patterns via T2 Packs or T3 AI Models                |
| Upgrade / Compatibility   | N/A                                                      |
| On-Prem Constraints       | Can run locally                                          |
| Ledger Integrity          | N/A                                                      |
| Dual-Calendar Correctness | Case timestamps                                          |

---

#### Section 10 — Acceptance Criteria

1. **Given** a SEBON T2 Wash Trade rule, **When** Client A buys and sells the same ISIN at the same price within 5 seconds, **Then** D-08 generates a `SurveillanceAlertGenerated` event.
2. **Given** an open case, **When** a compliance officer marks it as `Escalated`, **Then** the action is logged in K-07 and the case status updates.

---

#### Section 11 — Failure Modes & Resilience

- **Event Bus Lag:** D-08 processes events based on their embedded timestamps, not arrival time, ensuring accurate pattern matching even during network lag.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                   |
| ------------------- | -------------------------------------------------- |
| Metrics             | `surveillance.lag.ms`, `alerts.active`             |
| Logs                | Processing errors                                  |
| Traces              | N/A (Stream processing)                            |
| Audit Events        | Action: `CloseCase`, `EscalateCase`                |
| Regulatory Evidence | Surveillance logs for SEBON audits [ASR-SURV-001]. |

---

#### Section 13 — Compliance & Regulatory Traceability

- Market abuse detection [ASR-SURV-001]
- Audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

**SDK Methods (Platform SDK):**

```
SurveillanceClient.evaluateAlert(alertId: string): AlertDecision
SurveillanceClient.getAlertsByStatus(status: AlertStatus, page: Pagination): AlertPage
SurveillanceClient.getCaseHistory(caseId: string): CaseAuditTrail
SurveillanceClient.submitSAR(caseId: string, reportPayload: SARReport): SARSubmissionResult
```

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `AlertRaised` | Pattern match or ML anomaly detected | `alert_id`, `alert_type`, `severity`, `instrument_ids`, `pattern_match` |
| `CaseOpened` | Alert escalated to investigation | `case_id`, `alert_ids`, `assigned_to`, `priority` |
| `CaseClosed` | Investigation completed | `case_id`, `resolution`, `action_taken` |
| `SARFiled` | Suspicious Activity Report submitted | `sar_id`, `case_id`, `regulator`, `filing_date_bs` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `OrderStateChanged` | D-01 OMS | Trade pattern analysis |
| `TradeExecuted` | D-01 OMS | Execution surveillance |
| `MarketDataUpdated` | D-04 Market Data | Price manipulation detection |
| `AIDecisionMade` | K-09 AI Governance | ML model anomaly scoring |

**Jurisdiction Plugin Extension Points:** T2 Rule Packs for surveillance patterns (e.g., wash trading thresholds, spoofing detection windows per exchange).

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                     |
| ---------------------------------------------------- | ----------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                                |
| Can new AI models be swapped?                        | Yes, via K-09.                      |
| Can this run in an air-gapped deployment?            | Yes, with local surveillance rules. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Surveillance Evasion**
   - **Threat:** Sophisticated actor evades detection through pattern obfuscation.
   - **Mitigation:** Multiple detection methods (deterministic rules + AI); cross-market surveillance; entity relationship analysis; continuous model updates.
   - **Residual Risk:** Novel manipulation patterns not yet detected.

2. **False Alert Flooding**
   - **Threat:** Attacker generates false alerts to overwhelm compliance team.
   - **Mitigation:** Alert prioritization; AI-based triage; rate limiting on alert generation; anomaly detection on alert patterns.
   - **Residual Risk:** Legitimate high-volume trading triggering many alerts.

3. **Case Data Tampering**
   - **Threat:** Compliance officer modifies case data to hide violations.
   - **Mitigation:** Immutable audit trail in K-07; all case modifications logged; maker-checker for case closure; regular case audits.
   - **Residual Risk:** Database-level privilege escalation.

4. **Insider Collusion**
   - **Threat:** Trader and compliance officer collude to hide market abuse.
   - **Mitigation:** Segregation of duties; independent review of high-severity cases; AI monitors for unusual case handling patterns; external audits.
   - **Residual Risk:** Sophisticated collusion across multiple parties.

5. **Surveillance Data Theft**
   - **Threat:** Competitor steals surveillance patterns and detection logic.
   - **Mitigation:** Encryption at rest; strict RBAC; T2 rule packs protected; all access logged; data classification.
   - **Residual Risk:** Insider threat with legitimate access.

**Security Controls:**

- Immutable audit trail (K-07)
- Maker-checker for case closure
- Segregation of duties
- AI-based detection
- Encryption of surveillance rules
- Regular external audits
- Alert prioritization and triage

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Registered surveillance traceability under the compliance code registry.
- Added changelog metadata for future epic maintenance.
