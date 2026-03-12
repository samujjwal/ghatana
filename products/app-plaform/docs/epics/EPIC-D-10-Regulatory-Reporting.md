EPIC-ID: EPIC-D-10
EPIC NAME: Regulatory Reporting & Filings
LAYER: DOMAIN
MODULE: D-10 Regulatory Reporting & Filings
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the D-10 Regulatory Reporting & Filings module, responsible for generating, validating, and submitting regulatory reports to SEBON, NRB, and other authorities. This epic implements Principle 2 (Full Externalization) by ensuring that report templates, filing formats, and submission protocols are defined in T1 Config Packs and T3 Adapter Packs, while the core engine provides the orchestration and data aggregation framework.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Report generation engine (pulling data from K-05 Event Store and K-16 Ledger).
  2. Template rendering (PDF/CSV/XBRL) using T1 Template Packs.
  3. Submission orchestration to regulatory portals via T3 Adapters.
  4. Acknowledgement tracking and retry logic.
  5. Dual-calendar date stamping on all reports.
- **Out-of-Scope:**
  1. The actual report formats (e.g., SEBON quarterly broker report schema) - these are T1 Template Packs.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework)
- **Kernel Readiness Gates:** K-02, K-05, K-07, K-15, K-16
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Data Aggregation:** The engine must query K-05 Event Store and K-16 Ledger to gather the required data for a report (e.g., all trades in a BS quarter).
2. **FR2 Template Rendering:** The engine must load the appropriate T1 Template Pack (e.g., SEBON Monthly Broker Report v2025) and render the data into the required format.
3. **FR3 Validation:** Before submission, the engine must validate the report against the schema defined in the Template Pack.
4. **FR4 Submission:** The engine must invoke the appropriate T3 Regulatory Portal Adapter to submit the report.
5. **FR5 Acknowledgement Tracking:** The engine must track submission status and retry failed submissions per configured retry policy.
6. **FR6 Dual-Calendar:** All report metadata (generation date, reporting period) must use `DualDate`.
7. **FR7 Real-Time Trade Reporting:** The engine must support real-time (near-real-time) trade reporting to exchange/regulator as trades execute. Subscribe to `TradeExecutedEvent` from K-05 Event Bus; transform trade data into the format required by the target regulator's T1 Template Pack; submit via T3 Adapter within configurable latency SLA (default: < 15 minutes post-execution, configurable per jurisdiction). Track acknowledgment per trade. On submission failure: queue for retry with exponential backoff (max 3 retries); escalate unacknowledged reports after configurable threshold. Emit `TradeReportSubmittedEvent` and `TradeReportAcknowledgedEvent`. Dashboard showing real-time reporting status, pending reports, and failure rates. [GAP-004]
8. **FR8 Report Reconciliation:** The engine must reconcile submitted trade reports against exchange/regulator acknowledgments daily. Detect missing acknowledgments, duplicate submissions, and rejected reports. Generate a `ReportReconciliationReport` with break classification. Escalate unresolved breaks after 3 business days. [GAP-004]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The aggregation, rendering, and submission orchestration are generic.
2. **Jurisdiction Plugin:** Report templates (T1) and portal adapters (T3) are jurisdiction-specific.
3. **Resolution Flow:** Config Engine determines which reports are required for a given `tenant_id` and `jurisdiction`.
4. **Hot Reload:** New report templates can be deployed dynamically.
5. **Backward Compatibility:** Historical reports retain the template version used at generation time.
6. **Future Jurisdiction:** A new regulator requires new T1 Templates and potentially a new T3 Portal Adapter.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `RegulatoryReport`: `{ report_id: UUID, template_id: String, period_start_bs: String, period_end_bs: String, status: Enum, submitted_at: DualDate }`
- **Dual-Calendar Fields:** `period_start_bs`, `period_end_bs`, `submitted_at`.
- **Event Schema Changes:** `ReportGenerated`, `ReportSubmitted`, `ReportAcknowledged`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                       |
| ----------------- | ------------------------------------------------------------------------------------------------- |
| Event Name        | `ReportSubmitted`                                                                                 |
| Schema Version    | `v1.0.0`                                                                                          |
| Trigger Condition | A report is successfully transmitted to the regulatory portal.                                    |
| Payload           | `{ "report_id": "...", "regulator": "SEBON", "submission_ref": "...", "submitted_at_bs": "..." }` |
| Consumers         | Audit Framework, Admin Portal                                                                     |
| Idempotency Key   | `hash(report_id + submission_ref)`                                                                |
| Replay Behavior   | Updates submission status view.                                                                   |
| Retention Policy  | Permanent.                                                                                        |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                     |
| ---------------- | --------------------------------------------------------------- |
| Command Name     | `GenerateReportCommand`                                         |
| Schema Version   | `v1.0.0`                                                        |
| Validation Rules | Report template exists, data period valid, requester authorized |
| Handler          | `ReportCommandHandler` in D-10 Regulatory Reporting             |
| Success Event    | `ReportGenerated`                                               |
| Failure Event    | `ReportGenerationFailed`                                        |
| Idempotency      | Same template + period returns cached report                    |

| Field            | Description                                                                       |
| ---------------- | --------------------------------------------------------------------------------- |
| Command Name     | `SubmitReportCommand`                                                             |
| Schema Version   | `v1.0.0`                                                                          |
| Validation Rules | Report validated, submission deadline not passed, maker-checker approval obtained |
| Handler          | `SubmissionCommandHandler` in D-10 Regulatory Reporting                           |
| Success Event    | `ReportSubmitted`                                                                 |
| Failure Event    | `ReportSubmissionFailed`                                                          |
| Idempotency      | Command ID must be unique; duplicate commands return original result              |

| Field            | Description                                      |
| ---------------- | ------------------------------------------------ |
| Command Name     | `ValidateReportCommand`                          |
| Schema Version   | `v1.0.0`                                         |
| Validation Rules | Report exists, validation schema available       |
| Handler          | `ValidationHandler` in D-10 Regulatory Reporting |
| Success Event    | `ReportValidated`                                |
| Failure Event    | `ReportValidationFailed`                         |
| Idempotency      | Same report returns cached validation result     |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist
- **Workflow Steps Exposed:** Report validation and pre-submission review.
- **Model Registry Usage:** `report-validator-copilot-v1`
- **Explainability Requirement:** AI flags potential data quality issues (e.g., missing mandatory fields, outlier values) before submission.
- **Human Override Path:** Operator can acknowledge the warning and proceed.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard schema validation.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                     |
| ------------------------- | -------------------------------------------------------------------- |
| Latency / Throughput      | Generate a 10,000-row report in < 30 seconds                         |
| Scalability               | Batch processing scaled horizontally                                 |
| Availability              | 99.99%                                                               |
| Consistency Model         | Strong consistency for report data snapshots                         |
| Security                  | Reports encrypted at rest                                            |
| Data Residency            | Enforced via K-08                                                    |
| Data Retention            | Retain submitted reports 10 years                                    |
| Auditability              | All submissions logged [LCA-AUDIT-001]                               |
| Observability             | Metrics: `report.generation.duration`, `report.submission.fail_rate` |
| Extensibility             | New templates via T1 Packs                                           |
| Upgrade / Compatibility   | N/A                                                                  |
| On-Prem Constraints       | Can generate reports locally                                         |
| Ledger Integrity          | Pulls from K-16                                                      |
| Dual-Calendar Correctness | Correct period boundaries                                            |

---

#### Section 10 — Acceptance Criteria

1. **Given** a SEBON T1 Template Pack for the Quarterly Broker Report, **When** the report generation job runs for BS Q1 2082, **Then** it queries K-16 for all relevant ledger entries and renders a valid PDF.
2. **Given** a generated report, **When** submitted to the SEBON portal via the T3 Adapter, **Then** the adapter returns a submission reference and D-10 emits `ReportSubmitted`.
3. **Given** a submission failure, **When** the retry policy triggers, **Then** D-10 retries up to 3 times with exponential backoff.

---

#### Section 11 — Failure Modes & Resilience

- **Portal Down:** Report queued locally; retries until success or manual intervention.
- **Data Corruption:** Validation catches schema violations before submission.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                           |
| ------------------- | ------------------------------------------ |
| Metrics             | `report.queue.size`, `report.ack.latency`  |
| Logs                | Submission errors                          |
| Traces              | Span `Reporting.generate`                  |
| Audit Events        | Action: `SubmitReport`, `RegenerateReport` |
| Regulatory Evidence | Core system for [ASR-RPT-001].             |

---

#### Section 13 — Compliance & Regulatory Traceability

- Regulatory filing accuracy [ASR-RPT-001]
- Audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

**SDK Methods (Platform SDK):**

```
ReportingClient.generateReport(reportType: string, jurisdiction: string, period: DateRange): ReportResult
ReportingClient.getReportStatus(reportId: string): ReportStatus
ReportingClient.listScheduledReports(jurisdiction: string): ScheduledReport[]
ReportingClient.submitToRegulator(reportId: string): SubmissionResult
```

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `RegReportGenerated` | Scheduled or ad-hoc report produced | `report_id`, `report_type`, `jurisdiction`, `period_bs`, `filing_deadline_bs` |
| `RegReportSubmitted` | Report sent to regulator portal | `report_id`, `regulator`, `submission_timestamp_bs`, `status` |
| `RegReportAccepted` | Regulator acknowledges receipt | `report_id`, `regulator`, `acceptance_ref` |
| `RegReportRejected` | Regulator rejects submission | `report_id`, `regulator`, `rejection_reasons` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `TradeExecuted` | D-01 OMS | Trade data for regulatory reports |
| `LedgerEntryPosted` | K-16 Ledger | Financial data for reporting |
| `CorporateActionProcessed` | D-12 Corp Actions | CA reporting |
| `ComplianceViolationDetected` | D-07 Compliance | Compliance incident reporting |

**Jurisdiction Plugin Extension Points:** T1 Template Packs for report formats (e.g., SEBON quarterly broker report schema); T3 Portal Adapters for regulator submission APIs.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                                    |
| ---------------------------------------------------- | -------------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes, via new T1/T3 packs.                          |
| Can report formats change without redeploy?          | Yes, via T1 Template Pack updates.                 |
| Can this run in an air-gapped deployment?            | Partially; requires regulator portal connectivity. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Report Data Falsification**
   - **Threat:** Attacker modifies report data to hide violations or misrepresent activity.
   - **Mitigation:** Reports generated from immutable K-05 Event Store and K-16 Ledger; all report generation logged; maker-checker for manual adjustments; cryptographic signing of reports.
   - **Residual Risk:** Compromised source data.

2. **Report Template Manipulation**
   - **Threat:** Malicious template causes incorrect or misleading reports.
   - **Mitigation:** Templates in T1 Config Packs with maker-checker; template validation; all template changes audited; version control.
   - **Residual Risk:** Sophisticated template logic exploitation.

3. **Submission Fraud**
   - **Threat:** False submission acknowledgments or failure to submit.
   - **Mitigation:** Submission via T3 Regulatory Portal Adapters with cryptographic signing; acknowledgment tracking; all submissions audited; automated retry logic.
   - **Residual Risk:** Compromised regulator portal.

4. **Report Data Theft**
   - **Threat:** Competitor steals sensitive regulatory reports.
   - **Mitigation:** Encryption at rest and in transit; strict RBAC; all access logged; data classification; secure transmission channels.
   - **Residual Risk:** Insider threat with legitimate access.

5. **Deadline Manipulation**
   - **Threat:** Attacker modifies submission deadlines to cause late filings.
   - **Mitigation:** Deadlines in T1 Config Packs with maker-checker; automated deadline monitoring; alerts for approaching deadlines; all changes audited.
   - **Residual Risk:** Compromised config pack.

**Security Controls:**

- Immutable source data (K-05, K-16)
- Maker-checker for templates and adjustments
- Cryptographic signing of reports
- T3 adapter sandboxing
- Encryption at rest and in transit
- Audit logging of all operations
- Automated deadline monitoring

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
