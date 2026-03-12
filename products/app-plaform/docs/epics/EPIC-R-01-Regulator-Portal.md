EPIC-ID: EPIC-R-01
EPIC NAME: Regulator Portal & Evidence Export
LAYER: REGULATORY
MODULE: R-01 Regulator Interface
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the R-01 Regulator Portal & Evidence Export module, providing a secure, read-only interface for regulatory authorities (SEBON, NRB, etc.) to access audit trails, transaction records, and compliance evidence. This epic addresses the P1 gap identified in the platform review by building on D-10 Regulatory Reporting to add ad-hoc query capabilities, tamper-evident evidence package generation, regulator notification workflows, and compliance attestation management. It ensures that regulators have timely, secure, and verifiable access to all required information without compromising platform security or operational integrity.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Secure regulator portal with read-only access and RBAC.
  2. Ad-hoc query builder for audit trails, transactions, and compliance records.
  3. Evidence package generation (tamper-evident ZIP with manifests and cryptographic signatures).
  4. Regulator notification workflows (scheduled reports, incident notifications, compliance attestations).
  5. Compliance attestation management (periodic certifications, audit responses).
  6. Data export in multiple formats (CSV, PDF, XBRL, JSON).
  7. Access audit trail for all regulator activities.
- **Out-of-Scope:**
  1. Scheduled regulatory reporting (handled by D-10 Regulatory Reporting).
  2. Regulator onboarding and identity verification (handled by K-01 IAM).
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework), EPIC-D-10 (Regulatory Reporting)
- **Kernel Readiness Gates:** K-01, K-02, K-05, K-07, K-15, K-16, D-10
- **Module Classification:** Cross-Cutting Regulatory Layer

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Regulator Portal:** The module must provide a secure web portal for regulators with read-only access to platform data, enforced via K-01 RBAC.
2. **FR2 Ad-Hoc Query Builder:** The module must provide a query builder interface for regulators to search audit trails, transactions, and compliance records with filters (date range, entity, transaction type, jurisdiction).
3. **FR3 Evidence Package Generation:** The module must generate tamper-evident evidence packages (ZIP files) containing requested data, manifest files, and cryptographic signatures for integrity verification.
4. **FR4 Data Export Formats:** The module must support data export in multiple formats (CSV for analysis, PDF for reports, XBRL for structured data, JSON for APIs).
5. **FR5 Regulator Notifications:** The module must send automated notifications to regulators (scheduled report availability, critical incidents, compliance deadline reminders).
6. **FR6 Compliance Attestations:** The module must provide workflows for submitting and tracking compliance attestations (quarterly certifications, audit responses, remediation plans).
7. **FR7 Access Audit Trail:** The module must log all regulator activities (logins, queries, downloads, exports) to K-07 Audit Framework with full traceability.
8. **FR8 Data Residency Compliance:** The module must ensure that regulator access respects data residency rules (e.g., Nepal regulator can only access Nepal jurisdiction data).
9. **FR9 Query Performance:** The module must optimize queries for large datasets (millions of transactions) with pagination, indexing, and caching.
10. **FR10 Dual-Calendar Support:** All date filters and exports must support dual-calendar (Gregorian and BS) for operational convenience.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The portal framework, query engine, and evidence generation are jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Regulator-specific data access rules (e.g., SEBON can access Nepal data only) are defined in K-01 RBAC policies.
3. **Resolution Flow:** K-01 IAM enforces jurisdiction-based access control.
4. **Hot Reload:** Access policies can be updated dynamically.
5. **Backward Compatibility:** Historical evidence packages remain accessible.
6. **Future Jurisdiction:** New regulators added via K-01 IAM with appropriate RBAC policies.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `RegulatorAccess`: `{ access_id: UUID, regulator_id: String, jurisdiction: String, granted_at: DualDate, expires_at: DualDate, status: Enum }`
  - `EvidencePackage`: `{ package_id: UUID, regulator_id: String, query_params: JSON, generated_at: DualDate, file_path: String, signature: String, status: Enum }`
  - `ComplianceAttestation`: `{ attestation_id: UUID, regulator_id: String, type: Enum, period_start: DualDate, period_end: DualDate, submitted_at: DualDate, status: Enum }`
- **Dual-Calendar Fields:** `granted_at`, `expires_at`, `generated_at`, `period_start`, `period_end`, `submitted_at` use `DualDate`.
- **Event Schema Changes:** `RegulatorAccessGranted`, `EvidencePackageGenerated`, `AttestationSubmitted`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                               |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `EvidencePackageGenerated`                                                                                                                |
| Schema Version    | `v1.0.0`                                                                                                                                  |
| Trigger Condition | A regulator requests an evidence package and it is successfully generated.                                                                |
| Payload           | `{ "package_id": "...", "regulator_id": "SEBON", "query_params": {...}, "file_size_mb": 125, "signature": "...", "timestamp_bs": "..." }` |
| Consumers         | Audit Framework, Notification Service, Observability                                                                                      |
| Idempotency Key   | `hash(package_id)`                                                                                                                        |
| Replay Behavior   | Updates the materialized view of generated packages.                                                                                      |
| Retention Policy  | Permanent.                                                                                                                                |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                            |
| ---------------- | ---------------------------------------------------------------------- |
| Command Name     | `ExecuteQueryCommand`                                                  |
| Schema Version   | `v1.0.0`                                                               |
| Validation Rules | Query syntax valid, requester authorized, jurisdiction access verified |
| Handler          | `QueryCommandHandler` in R-01 Regulator Portal                         |
| Success Event    | `QueryExecuted`                                                        |
| Failure Event    | `QueryExecutionFailed`                                                 |
| Idempotency      | Same query parameters return cached result                             |

| Field            | Description                                                           |
| ---------------- | --------------------------------------------------------------------- |
| Command Name     | `GenerateEvidencePackageCommand`                                      |
| Schema Version   | `v1.0.0`                                                              |
| Validation Rules | Query parameters valid, requester authorized, export format supported |
| Handler          | `EvidenceCommandHandler` in R-01 Regulator Portal                     |
| Success Event    | `EvidencePackageGenerated`                                            |
| Failure Event    | `EvidencePackageGenerationFailed`                                     |
| Idempotency      | Command ID must be unique; duplicate commands return cached package   |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `SubmitAttestationCommand`                                           |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Attestation type valid, period valid, requester authorized           |
| Handler          | `AttestationCommandHandler` in R-01 Regulator Portal                 |
| Success Event    | `AttestationSubmitted`                                               |
| Failure Event    | `AttestationSubmissionFailed`                                        |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Query Assistance / Anomaly Detection
- **Workflow Steps Exposed:** Query builder assistance, suspicious access detection.
- **Model Registry Usage:** `query-assistant-v1`, `regulator-access-anomaly-v1`
- **Explainability Requirement:** AI assists regulators in building complex queries (e.g., "Show all trades by Client X in Q1 2082"). AI detects suspicious regulator access patterns (e.g., excessive data downloads, unusual query patterns).
- **Human Override Path:** Compliance officer reviews AI alerts and can approve legitimate access.
- **Drift Monitoring:** Query assistance quality tracked via user feedback.
- **Fallback Behavior:** Manual query building; standard access control.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                                  |
| ------------------------- | ------------------------------------------------------------------------------------------------- |
| Latency / Throughput      | Query execution P99 < 5 seconds for 1M records; evidence package generation < 2 minutes for 100MB |
| Scalability               | Support 100 concurrent regulator sessions                                                         |
| Availability              | 99.9% uptime                                                                                      |
| Consistency Model         | Strong consistency for audit trail queries                                                        |
| Security                  | MFA required for regulator login; all access logged; data encrypted in transit and at rest        |
| Data Residency            | Enforced via K-08; regulators can only access their jurisdiction data                             |
| Data Retention            | Evidence packages retained 10 years                                                               |
| Auditability              | All regulator actions logged [LCA-AUDIT-001]                                                      |
| Observability             | Metrics: `regulator.query.count`, `evidence.generation.duration`, `regulator.login.count`         |
| Extensibility             | New export formats via plugin                                                                     |
| Upgrade / Compatibility   | Portal versioned independently                                                                    |
| On-Prem Constraints       | Portal can run locally for on-prem deployments                                                    |
| Ledger Integrity          | Queries K-16 for transaction data                                                                 |
| Dual-Calendar Correctness | Date filters accurate                                                                             |

---

#### Section 10 — Acceptance Criteria

1. **Given** a SEBON regulator, **When** they log in to the portal, **Then** they can only access Nepal jurisdiction data and all other jurisdictions are hidden.
2. **Given** a regulator query for all trades by Client X in BS Q1 2082, **When** executed, **Then** the system returns matching records in < 5 seconds with pagination.
3. **Given** a regulator requests an evidence package for audit trail data, **When** generated, **Then** it creates a ZIP file with data files, manifest.json, and cryptographic signature, and notifies the regulator.
4. **Given** an evidence package, **When** the regulator verifies the signature, **Then** it matches the platform's public key, confirming data integrity.
5. **Given** a compliance attestation workflow, **When** a quarterly certification is due, **Then** the system notifies the compliance team and provides a submission form.
6. **Given** a regulator downloads a large dataset, **When** the download completes, **Then** the action is logged in K-07 with regulator identity, query parameters, and timestamp.
7. **Given** AI detects unusual regulator access (e.g., 10x normal query volume), **When** flagged, **Then** the compliance officer is alerted and can review the activity.

---

#### Section 11 — Failure Modes & Resilience

- **Query Timeout:** Query aborted after 30 seconds; regulator notified to refine query.
- **Evidence Generation Failure:** Retry with exponential backoff; if persistent failure, alert operations and provide manual export option.
- **Portal Unavailable:** Regulator notified via email; fallback to manual evidence request process.
- **Data Residency Violation Attempt:** Access denied immediately; security event logged; compliance officer alerted.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                                             |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Metrics             | `regulator.query.latency`, `evidence.package.size`, `regulator.session.duration`, dimensions: `regulator_id`, `jurisdiction` |
| Logs                | Structured: `regulator_id`, `query`, `result_count`, `duration_ms`                                                           |
| Traces              | Span `RegulatorPortal.executeQuery`                                                                                          |
| Audit Events        | Action: `RegulatorLogin`, `ExecuteQuery`, `DownloadEvidence` [LCA-AUDIT-001]                                                 |
| Regulatory Evidence | Portal access logs for compliance audits                                                                                     |

---

#### Section 13 — Compliance & Regulatory Traceability

- Regulator access control and audit [LCA-AUDIT-001]
- Data residency enforcement [ASR-DATA-001]
- Evidence integrity and tamper-proofing [ASR-EVID-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `RegulatorPortalClient.executeQuery(query)`, `RegulatorPortalClient.generateEvidencePackage(params)`, `RegulatorPortalClient.submitAttestation(data)`.
- **Export Format Interface:** `ExportFormatter` interface for adding new export formats.
- **Jurisdiction Plugin Extension Points:** Access policies via K-01 RBAC.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                                                |
| ---------------------------------------------------- | -------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes, via K-01 RBAC policies for new regulators.                |
| Can new export formats be added?                     | Yes, via ExportFormatter interface.                            |
| Can this run in an air-gapped deployment?            | Yes, portal runs locally; evidence packages exported manually. |
| Can regulators access historical data?               | Yes, full audit trail history accessible.                      |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Unauthorized Regulator Access**
   - **Threat:** Attacker impersonates regulator to access sensitive audit data.
   - **Mitigation:** MFA required for all regulator logins; strict RBAC via K-01; jurisdiction-based access control; all access logged immutably to K-07; anomaly detection on access patterns; certificate-based authentication for API access.
   - **Residual Risk:** Compromised regulator credentials.

2. **Data Residency Violation**
   - **Threat:** Regulator from Jurisdiction A accesses data from Jurisdiction B.
   - **Mitigation:** Jurisdiction filtering enforced at database layer; row-level security; all queries include jurisdiction filter; access attempts logged; automated alerts on cross-jurisdiction access attempts; regular access audits.
   - **Residual Risk:** Database-level privilege escalation.

3. **Evidence Package Tampering**
   - **Threat:** Attacker modifies evidence package to hide or alter compliance violations.
   - **Mitigation:** Cryptographic signing of all evidence packages; manifest files with checksums; tamper-evident packaging; signature verification required; immutable audit trail of package generation; version control for evidence.
   - **Residual Risk:** Compromise of signing keys.

4. **Excessive Data Exfiltration**
   - **Threat:** Malicious regulator downloads excessive data beyond legitimate need.
   - **Mitigation:** Query result size limits; rate limiting on downloads; anomaly detection on download volumes (AI-based); all downloads logged with justification; alerts on unusual patterns; export approval workflow for large datasets.
   - **Residual Risk:** Slow exfiltration within normal thresholds.

5. **Query Injection / Data Exposure**
   - **Threat:** Attacker crafts malicious queries to access unauthorized data or cause DoS.
   - **Mitigation:** Parameterized queries only; query builder with whitelist approach; no raw SQL access; query complexity limits; timeout enforcement; input validation; SQL injection prevention; query audit logging.
   - **Residual Risk:** Logic errors in query builder.

6. **Portal Availability Attack**
   - **Threat:** Attacker floods portal with requests to deny regulator access.
   - **Mitigation:** Rate limiting per regulator account; DDoS protection; auto-scaling; circuit breakers; fallback to manual evidence request process; dedicated regulator infrastructure; priority queuing for critical queries.
   - **Residual Risk:** Sophisticated distributed attacks.

7. **Insider Threat - Platform Operator**
   - **Threat:** Platform operator modifies audit data before regulator access.
   - **Mitigation:** Immutable audit trail in K-07; cryptographic hashing of audit records; blockchain-style chaining; operator actions logged separately; separation of duties; no operator access to modify historical data; regular integrity checks.
   - **Residual Risk:** Root-level infrastructure compromise.

8. **Evidence Package Interception**
   - **Threat:** Attacker intercepts evidence packages in transit to regulator.
   - **Mitigation:** TLS 1.3+ for all downloads; evidence packages encrypted with regulator's public key; secure file transfer protocols; download link expiry (24 hours); one-time download tokens; notification on package access.
   - **Residual Risk:** Compromised regulator endpoint.

**Security Controls:**

- MFA required for all regulator authentication
- Jurisdiction-based RBAC via K-01 IAM
- Row-level data residency enforcement
- Cryptographic signing of evidence packages
- Comprehensive access audit logging to K-07
- Anomaly detection on access patterns (K-09)
- Query parameterization and injection prevention
- Rate limiting and DDoS protection
- Encrypted evidence packages
- Immutable audit trail with integrity verification
- Separation of duties (operators cannot modify audit data)
- Regular security assessments and penetration testing
- Secure file transfer with expiring links
- Query complexity and timeout limits

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
