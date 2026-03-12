EPIC-ID: EPIC-D-13
EPIC NAME: Client Money Reconciliation
LAYER: DOMAIN
MODULE: D-13 Client Money Reconciliation
VERSION: 1.0.1
ARB-REF: P1-11

---

#### Section 1 — Objective

Deliver the D-13 Client Money Reconciliation module to ensure that client funds held by the platform are accurately tracked, segregated, and reconciled against external bank and custodian statements on a daily basis. This epic directly remediates ARB finding P1-11 and Regulatory Architecture Document GAP-001 (No Client Money Reconciliation Workflow), fulfilling a critical regulatory requirement for broker-dealers and investment managers.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Daily automated reconciliation of client money positions against bank/custodian statements.
  2. Break detection, classification, and escalation workflows.
  3. Client money segregation verification (client funds vs. firm funds).
  4. Integration with K-16 Ledger Framework for internal position data.
  5. Integration with K-05 Event Bus for event-driven reconciliation triggers.
  6. Reconciliation reporting for regulators (via R-01 Regulator Portal).
- **Out-of-Scope:**
  1. Actual bank connectivity (handled by T3 Bank Adapter Packs).
  2. Securities reconciliation (handled by D-09 Post-Trade reconciliation engine).
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework), EPIC-K-18 (Resilience Patterns)
- **Kernel Readiness Gates:** K-05, K-16 must be stable.
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Daily Automated Reconciliation:** Execute daily reconciliation at a configurable time (default: T+0 18:00 local time) comparing internal client money ledger balances (from K-16) against external bank/custodian statement data (ingested via T3 Bank Adapter).
2. **FR2 Statement Ingestion:** Support ingestion of external statements in multiple formats (MT940, CAMT.053, CSV) via configurable T3 Adapter Packs. Validate statement integrity (checksums, record counts) before processing.
3. **FR3 Matching Engine:** Match internal transactions against external statement entries using configurable matching rules (amount, date, reference). Support: (a) one-to-one matching, (b) one-to-many matching (split settlements), (c) many-to-one matching (aggregated bank entries). Matching rules are defined in T2 Rule Packs.
4. **FR4 Break Detection & Classification:** Identify unmatched items and classify breaks: (a) Timing breaks (expected to auto-resolve within T+N days), (b) Amount breaks (partial match), (c) Missing internal (statement has entry, ledger does not), (d) Missing external (ledger has entry, statement does not), (e) Unresolved (requires investigation). Emit `ClientMoneyBreakDetectedEvent` for each break.
5. **FR5 Segregation Verification:** Verify daily that total client money held in segregated bank accounts ≥ total client money obligations per internal ledger. Any shortfall emits `ClientMoneySegregationBreachEvent` (CRITICAL severity).
6. **FR6 Escalation Workflow:** Breaks unresolved after configurable deadline (default: 3 business days) auto-escalate: (a) Day 1: assigned to operations team, (b) Day 3: escalated to compliance officer, (c) Day 5: escalated to senior management, (d) Day 10: flagged for regulator notification via R-02. All escalations audited in K-07.
7. **FR7 Reconciliation Reporting:** Generate daily reconciliation reports including: matched count/value, break count/value by category, aging analysis, and segregation verification result. Reports available via R-01 Regulator Portal.
8. **FR8 Dual-Calendar Support:** Reconciliation dates, break creation dates, and reporting periods support dual-calendar (BS/Gregorian) via K-15.
9. **FR9 Historical Recon:** Support re-running reconciliation for any historical date (with the statement and ledger snapshot as of that date) for audit and investigation purposes.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The matching engine, break detection, escalation workflow, and reporting framework are generic.
2. **Jurisdiction Plugin:** Matching rules, segregation thresholds, escalation timelines, and statement formats are defined in T1 Config Packs and T2 Rule Packs per jurisdiction.
3. **Resolution Flow:** K-02 Config Engine determines which reconciliation rules apply per operator/jurisdiction.
4. **Hot Reload:** Matching rules and escalation timelines hot-reloadable.
5. **Backward Compatibility:** Historical reconciliation results are immutable.
6. **Future Jurisdiction:** New jurisdiction requires new matching rule pack and bank adapter.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `ReconciliationRun`: `{ run_id: UUID, recon_date: DualDate, status: Enum(RUNNING, COMPLETED, FAILED), matched_count: Int, break_count: Int, segregation_status: Enum(PASS, BREACH), completed_at: DualDate }`
  - `ReconciliationBreak`: `{ break_id: UUID, run_id: UUID, category: Enum, internal_ref: String, external_ref: String, amount_diff: Decimal, status: Enum(OPEN, INVESTIGATING, RESOLVED, ESCALATED), assigned_to: String, created_at: DualDate }`
  - `ExternalStatement`: `{ statement_id: UUID, source: String, format: String, entries: List<StatementEntry>, ingested_at: DualDate }`
- **Dual-Calendar Fields:** `recon_date`, `created_at`, `completed_at` use `DualDate`.
- **Event Schema Changes:** `ClientMoneyBreakDetectedEvent`, `ClientMoneySegregationBreachEvent`, `ReconciliationCompletedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                             |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `ClientMoneySegregationBreachEvent`                                                                                                     |
| Schema Version    | `v1.0.0`                                                                                                                                |
| Trigger Condition | Daily segregation check detects client money shortfall.                                                                                 |
| Payload           | `{ "recon_date": "...", "total_obligation": "1000000.00", "total_held": "990000.00", "shortfall": "10000.00", "severity": "CRITICAL" }` |
| Consumers         | K-06 Alerting (CRITICAL), R-02 Incident Response & Escalation, Compliance Officers, Senior Management                                   |
| Idempotency Key   | `hash(recon_date + operator_id)`                                                                                                        |
| Replay Behavior   | N/A (alert event).                                                                                                                      |
| Retention Policy  | Permanent.                                                                                                                              |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                |
| ---------------- | ---------------------------------------------------------- |
| Command Name     | `RunReconciliationCommand`                                 |
| Schema Version   | `v1.0.0`                                                   |
| Validation Rules | Recon date valid, statement ingested, requester authorized |
| Handler          | `ReconciliationCommandHandler` in D-13                     |
| Success Event    | `ReconciliationCompleted`                                  |
| Failure Event    | `ReconciliationFailed`                                     |
| Idempotency      | Run ID must be unique per recon_date + operator            |

| Field            | Description                                                                     |
| ---------------- | ------------------------------------------------------------------------------- |
| Command Name     | `ResolveBreakCommand`                                                           |
| Schema Version   | `v1.0.0`                                                                        |
| Validation Rules | Break exists, resolution reason provided, maker-checker for amounts > threshold |
| Handler          | `BreakResolutionHandler` in D-13                                                |
| Success Event    | `BreakResolved`                                                                 |
| Failure Event    | `BreakResolutionFailed`                                                         |
| Idempotency      | Command ID must be unique                                                       |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Pattern Recognition / Anomaly Detection
- **Workflow Steps Exposed:** Break classification and matching.
- **Model Registry Usage:** `recon-break-classifier-v1`
- **Explainability Requirement:** AI suggests likely break causes and matching candidates; human must approve resolution.
- **Human Override Path:** Operator can override AI matching suggestions.
- **Drift Monitoring:** Match rate < 95% triggers retrain alert.
- **Fallback Behavior:** Rule-based matching only.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                               |
| ------------------------- | ---------------------------------------------------------------------------------------------- |
| Latency / Throughput      | Daily recon for 100K transactions completes in < 30 minutes                                    |
| Scalability               | Parallel matching workers; scales with transaction volume                                      |
| Availability              | 99.99% uptime                                                                                  |
| Consistency Model         | Strong consistency for break state                                                             |
| Security                  | Statement data encrypted at rest; access restricted to operations/compliance roles             |
| Data Residency            | Reconciliation data follows K-08 residency rules                                               |
| Data Retention            | Reconciliation records retained per audit policy (minimum 10 years)                            |
| Auditability              | All break resolutions and escalations logged to K-07                                           |
| Observability             | Metrics: `recon.match.rate`, `recon.break.count`, `recon.segregation.status`, `recon.duration` |
| Extensibility             | New statement formats via T3 Adapter Packs                                                     |
| Upgrade / Compatibility   | Backward compatible API                                                                        |
| On-Prem Constraints       | Operates with local statement file ingestion                                                   |
| Ledger Integrity          | Validates against K-16 ledger balances                                                         |
| Dual-Calendar Correctness | Reconciliation dates correctly mapped to BS fiscal periods                                     |

---

#### Section 10 — Acceptance Criteria

1. **Given** end of trading day, **When** the daily recon job runs, **Then** it matches internal ledger positions against the bank statement and produces a reconciliation report within 30 minutes.
2. **Given** a $10,000 break detected, **When** unresolved for 3 business days, **Then** it auto-escalates to compliance officer with full audit trail.
3. **Given** client money held = $990,000 and obligations = $1,000,000, **When** segregation check runs, **Then** `ClientMoneySegregationBreachEvent` is emitted with CRITICAL severity.
4. **Given** a historical date, **When** an auditor requests re-reconciliation, **Then** the system re-runs using the ledger and statement snapshots for that date.
5. **Given** a break resolved by an operator, **When** the resolution amount exceeds $50,000, **Then** maker-checker approval is required.

---

#### Section 11 — Failure Modes & Resilience

- **Statement Ingestion Failure:** Recon job retries ingestion 3 times with backoff; if all fail, recon is marked FAILED and alert raised. Recon can be re-triggered manually once statement is available.
- **K-16 Ledger Unavailable:** Recon job pauses; retries after K-16 recovery; alert raised.
- **Break Escalation Service Down:** Escalations queued locally; processed on recovery.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                           |
| ------------------- | ------------------------------------------------------------------------------------------ |
| Metrics             | `recon.run.duration`, `recon.match.count`, `recon.break.count`, `recon.segregation.status` |
| Logs                | Structured: `run_id`, `recon_date`, `status`, `break_count`                                |
| Traces              | Span per reconciliation run                                                                |
| Audit Events        | `ReconciliationCompleted`, `BreakResolved`, `BreakEscalated` [LCA-AUDIT-001]               |
| Regulatory Evidence | Daily reconciliation reports and segregation verification [LCA-RECON-001]                  |

---

#### Section 13 — Compliance & Regulatory Traceability

- Client money segregation [LCA-SEG-001]
- Daily reconciliation requirement [LCA-RECON-001]
- Audit trails [LCA-AUDIT-001]
- Break escalation and resolution [LCA-OPS-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `ReconClient.runRecon(date)`, `ReconClient.getBreaks(runId)`, `ReconClient.resolveBreak(breakId, resolution)`.
- **Jurisdiction Plugin Extension Points:** T3 Bank Adapter Packs for statement ingestion; T2 Rule Packs for matching rules.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                                    |
| ---------------------------------------------------- | -------------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes, via new bank adapter and matching rule packs. |
| Can new bank formats be added?                       | Yes, via T3 Adapter Packs.                         |
| Can this run in an air-gapped deployment?            | Yes, with local file-based statement ingestion.    |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Statement Tampering**

- **Threat:** Attacker alters inbound bank or custodian statements to hide breaks or segregation breaches.
- **Mitigation:** Signed or checksum-verified statement ingestion; source-level integrity checks; immutable storage of original statement artifacts; reconciliation reruns preserved for audit.
- **Residual Risk:** Compromised upstream banking source.

2. **Break Resolution Fraud**

- **Threat:** Operator resolves breaks incorrectly to conceal misappropriation or operational errors.
- **Mitigation:** Maker-checker for material resolutions; all resolutions and escalations logged to K-07; aging and repeat-break analytics; supervisory review for unresolved critical breaks.
- **Residual Risk:** Collusion between multiple reviewers.

3. **Segregation Breach Suppression**

- **Threat:** Critical shortfalls are detected but alerts are suppressed or delayed.
- **Mitigation:** CRITICAL breach events emitted via K-05/K-06; immutable evidence retained; escalation timers enforced independently of operator action; regulator-notification path through R-02.
- **Residual Risk:** Coordinated failure across alerting and escalation channels.

4. **Historical Recon Manipulation**

- **Threat:** Historical reconciliation reruns are altered to misrepresent prior control effectiveness.
- **Mitigation:** Point-in-time ledger and statement snapshots retained; rerun metadata versioned; prior outputs preserved; audit trail records who initiated reruns and why.
- **Residual Risk:** Corruption of historical snapshots before retention controls apply.

5. **Sensitive Financial Data Exposure**

- **Threat:** Reconciliation data leaks client balances, bank references, or break details.
- **Mitigation:** Encryption at rest; strict RBAC for operations and compliance; masked exports; access auditing; network segmentation for statement-ingestion endpoints.
- **Residual Risk:** Insider misuse with legitimate access.

**Security Controls:**

- Integrity validation for inbound statements
- Maker-checker for material break resolutions
- Immutable reconciliation evidence retention
- Escalation automation with audit trails
- Encryption of reconciliation and statement data
- RBAC for operations and compliance roles
- Historical snapshot preservation for reruns

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Normalized client-money segregation traceability to the shared segregation control code.
- Added changelog metadata for future epic maintenance.
