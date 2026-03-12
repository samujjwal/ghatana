# REGULATORY ARCHITECTURE DOCUMENT

## Project Siddhanta - Control Framework & Evidence Architecture

**Document Type**: Regulatory Compliance Architecture  
**Audience**: Regulators, External Auditors, Compliance Officers  
**Version**: 2.1.0  
**Date**: 2026-03-10  
**ARB Remediation**: All P0/P1/P2 findings from `archive/reviews/2026-03/ARB_STRESS_TEST_REVIEW.md` addressed  
**Classification**: Confidential - Regulatory Use

---

## EXECUTIVE SUMMARY

Project Siddhanta is a modular, regulator-grade capital markets platform designed with **controls-first architecture**. The platform enforces investor protection, market integrity, and operational resilience through:

- **Immutable audit trails** with cryptographic tamper-evidence
- **Maker-checker workflows** for all critical operations
- **Jurisdiction-agnostic core** with externalized compliance rules
- **Metadata-driven operator and regulator workflows** with versioned task schemas, query templates, and value catalogs
- **AI governance** with human-in-the-loop oversight
- **Data residency enforcement** at database layer
- **Comprehensive evidence export** for regulatory examinations

The platform supports multiple operator models (broker-dealer, merchant banker, exchange, etc.) while maintaining strict segregation of duties and audit-grade evidence capture.

---

## 1. SYSTEM OVERVIEW

### 1.1 Platform Architecture

**Core Components**:

- **Financial Operating System (FOS)**: Generic kernel modules (K-01 to K-19)
- **Domain Subsystems**: Order management, surveillance, compliance, client money reconciliation, sanctions screening (D-01 to D-14)
- **Jurisdiction Packs**: Externalized rules and configurations (T1/T2/T3)
- **Regulatory Layer**: Regulator portal, reporting, evidence export, incident response & escalation (R-01, R-02)
- **Testing Framework**: Integration testing, chaos engineering & resilience testing (T-01, T-02)

**Key Architectural Principles**:

1. **Immutability**: All state changes captured as append-only events
2. **Traceability**: Every action linked to actor, timestamp (dual-calendar), and justification
3. **Segregation of Duties**: Maker-checker enforced at kernel level with approval rate limiting [ARB P1-09]
4. **Fail-Closed**: Compliance checks block operations on failure; circuit breakers enforce degraded modes [ARB P0-02]
5. **Evidence-First**: All regulatory evidence generated automatically with external hash anchoring [ARB P0-05]
6. **Resilience-By-Design**: Circuit breakers, bulkheads, retry policies standardized via K-18 Resilience Patterns Library
7. **Financial Precision**: Fixed-point arithmetic with configurable precision per currency [ARB P0-03]
8. **Governed Runtime Metadata**: Human-task forms, regulator request templates, routing policies, and operator choice lists are treated as controlled metadata assets with schema validation, maker-checker approval, and pinned-version execution.

### 1.2 Deployment Modes

| Mode                  | Description                              | Regulatory Implications               |
| --------------------- | ---------------------------------------- | ------------------------------------- |
| **SaaS Multi-Tenant** | Shared infrastructure, logical isolation | Data residency via row-level security |
| **Dedicated Cloud**   | Single-tenant deployment                 | Physical data isolation               |
| **On-Premises**       | Air-gapped installation                  | Full data sovereignty                 |

All modes maintain identical control framework and evidence generation capabilities.

---

## 2. CONTROL FRAMEWORK MAPPING

### 2.1 Pre-Trade Controls

| Area                      | Control Objective                      | Mechanism                                                                               | Evidence Produced                                            | Epic IDs         | Notes            |
| ------------------------- | -------------------------------------- | --------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ---------------- | ---------------- |
| **Client Verification**   | Prevent trading by unverified clients  | K-01 IAM KYC status check; D-07 blocks trades if `verification_status != VERIFIED`      | Audit log of blocked trade attempts with KYC status          | K-01, D-07       | [LCA-AMLKYC-001] |
| **Restricted Securities** | Enforce lock-in periods, insider lists | K-03 evaluates T2 Rule Pack (e.g., promoter lock-in until BS date); D-07 orchestrates   | Compliance check log with rule version, denial reason        | K-03, D-07       | [LCA-COMP-001]   |
| **Position Limits**       | Prevent concentration risk             | K-03 evaluates position limit rules; D-01 OMS rejects orders exceeding limits           | Pre-trade rejection log with current position, limit, excess | K-03, D-01, D-06 | [LCA-COMP-001]   |
| **Margin Sufficiency**    | Ensure adequate collateral             | D-06 Risk Engine calculates margin requirement; K-03 evaluates against available margin | Margin calculation log with haircuts, collateral values      | D-06, K-03       | [ASR-MARG-001]   |
| **Suitability**           | Match products to client risk profile  | D-07 evaluates suitability rules based on client risk rating                            | Suitability check log with client profile, product risk tier | D-07             | [LCA-SUIT-001]   |

### 2.2 Trade Execution Controls

| Area                       | Control Objective                  | Mechanism                                                                   | Evidence Produced                                     | Epic IDs | Notes            |
| -------------------------- | ---------------------------------- | --------------------------------------------------------------------------- | ----------------------------------------------------- | -------- | ---------------- |
| **Order Routing**          | Ensure best execution              | D-02 EMS routes to exchange per routing rules; logs routing decision        | Routing decision log with venue selection rationale   | D-02     | [LCA-BESTEX-001] |
| **Price Validation**       | Detect erroneous prices            | D-04 Market Data validates price against circuit breakers; rejects outliers | Price validation log with reference price, deviation  | D-04     | [ASR-CB-001]     |
| **Duplicate Detection**    | Prevent duplicate order submission | D-01 OMS checks `client_order_id` uniqueness; rejects duplicates            | Duplicate rejection log with original order reference | D-01     | [LCA-DUP-001]    |
| **Execution Confirmation** | Verify trade authenticity          | D-09 Post-Trade reconciles exchange confirmations against internal orders   | Reconciliation report with matched/unmatched trades   | D-09     | [LCA-RECON-001]  |

### 2.3 Post-Trade Controls

| Area                            | Control Objective               | Mechanism                                                                                                                            | Evidence Produced                                                         | Epic IDs   | Notes                           |
| ------------------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------- | ---------- | ------------------------------- |
| **Settlement Finality**         | Ensure DVP settlement           | D-09 Post-Trade tracks settlement status; alerts on failures                                                                         | Settlement status log with DVP confirmation                               | D-09       | [LCA-SETTL-001]                 |
| **Corporate Actions**           | Accurate entitlement processing | D-12 Corporate Actions applies entitlements per issuer announcements                                                                 | Entitlement calculation log with ex-date, record date                     | D-12       | [LCA-CA-001]                    |
| **Client Asset Segregation**    | Protect client funds            | K-16 Ledger maintains separate client money accounts; D-13 performs daily automated reconciliation against bank/custodian statements | Client asset reconciliation report, segregation verification report       | K-16, D-13 | [LCA-SEG-001]                   |
| **Client Money Reconciliation** | Ensure fund accuracy            | D-13 matches internal ledger against external bank statements daily; detects breaks; escalates unresolved breaks                     | Daily reconciliation report, break aging report, segregation verification | D-13, K-16 | [LCA-RECON-001] [ARB P1-11]     |
| **Sanctions Screening**         | Prevent sanctioned transactions | D-14 screens entities at onboarding, order placement, settlement against OFAC/UN/EU lists; fuzzy matching; review workflow           | Screening decision log, match review audit trail                          | D-14, D-07 | [LCA-SANCTIONS-001] [ARB P1-13] |
| **Position Reconciliation**     | Ensure position accuracy        | D-03 PMS reconciles positions against depository daily                                                                               | Position break report with discrepancies                                  | D-03, D-09 | [LCA-RECON-001]                 |

### 2.4 Surveillance & Monitoring Controls

| Area                          | Control Objective                | Mechanism                                                                               | Evidence Produced                                                | Epic IDs   | Notes          |
| ----------------------------- | -------------------------------- | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ---------- | -------------- |
| **Market Abuse Detection**    | Detect wash trades, spoofing     | D-08 Surveillance evaluates T2 Rule Packs + AI models; generates alerts                 | Surveillance alert with pattern description, confidence score    | D-08, K-09 | [ASR-SURV-001] |
| **Insider Trading Detection** | Flag suspicious trading patterns | D-08 cross-references trades against insider lists; AI detects anomalies                | Insider trading alert with entity relationships, timing analysis | D-08, K-09 | [ASR-SURV-001] |
| **Front-Running Detection**   | Detect order anticipation        | D-08 analyzes order sequences; flags temporal patterns                                  | Front-running alert with order timeline, price impact            | D-08       | [ASR-SURV-001] |
| **Case Management**           | Track investigation lifecycle    | D-08 maintains case workflow (Open → Investigating → Closed); maker-checker for closure | Case audit trail with status changes, investigator actions       | D-08, K-07 | [ASR-SURV-001] |

### 2.5 Operational Controls

| Area                          | Control Objective               | Mechanism                                                                               | Evidence Produced                                                     | Epic IDs                     | Notes          |
| ----------------------------- | ------------------------------- | --------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ---------------------------- | -------------- |
| **Maker-Checker Enforcement** | Prevent unauthorized actions    | K-02, K-03, K-09, D-01, D-07 enforce maker ≠ checker; blocks self-approval              | Maker-checker audit log with maker ID, checker ID, approval timestamp | K-02, K-03, K-09, D-01, D-07 | [LCA-SOD-001]  |
| **Configuration Changes**     | Control system behavior changes | K-02 Config Engine requires maker-checker for critical configs; versioned with rollback | Config change audit log with before/after state, approver             | K-02, K-07                   | [ASR-TECH-001] |
| **Rule Pack Deployment**      | Control compliance rule changes | K-03 Rules Engine requires maker-checker for T2 Rule Packs; cryptographically signed    | Rule deployment audit log with rule diff, test results, approver      | K-03, K-07                   | [ASR-TECH-001] |
| **Access Control**            | Prevent unauthorized access     | K-01 IAM enforces RBAC; all access attempts logged                                      | Access log with user, resource, action, result (allow/deny)           | K-01, K-07                   | [ASR-SEC-001]  |

### 2.6 Data Governance Controls

| Area               | Control Objective                     | Mechanism                                                                       | Evidence Produced                                      | Epic IDs   | Notes          |
| ------------------ | ------------------------------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------ | ---------- | -------------- |
| **Data Residency** | Enforce jurisdiction data sovereignty | K-08 Data Governance routes writes to jurisdiction-specific storage clusters    | Data routing log with jurisdiction, storage target     | K-08       | [ASR-DATA-001] |
| **Data Retention** | Comply with retention requirements    | K-08 enforces retention policies; blocks deletion before retention period       | Retention enforcement log with policy, retention date  | K-08, K-07 | [LCA-RET-001]  |
| **Data Deletion**  | Secure data disposal                  | K-08 executes deletion after retention period; emits `DataDeletedEvent`         | Deletion audit log with data class, deletion timestamp | K-08, K-07 | [LCA-RET-001]  |
| **Encryption**     | Protect data at rest                  | K-08 integrates with KMS; all sensitive data encrypted with tenant-managed keys | Encryption key rotation log                            | K-08       | [ASR-SEC-001]  |
| **Data Lineage**   | Track data flow                       | K-08 maintains lineage graph; enables impact analysis                           | Lineage graph export showing data transformations      | K-08       | [LCA-DATA-001] |

### 2.7 AI Governance Controls

| Area                  | Control Objective               | Mechanism                                                                     | Evidence Produced                                                            | Epic IDs   | Notes        |
| --------------------- | ------------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ---------- | ------------ |
| **Model Registry**    | Control AI model deployment     | K-09 maintains model registry; requires maker-checker approval for deployment | Model deployment log with version, risk tier, approver                       | K-09, K-07 | [LCA-AI-001] |
| **Prompt Governance** | Control AI prompt changes       | K-09 versions prompts; requires approval for production deployment            | Prompt approval log with template diff, approver                             | K-09, K-07 | [LCA-AI-001] |
| **Explainability**    | Ensure AI decision transparency | K-09 generates SHAP/LIME explanations for all AI decisions; stored in K-07    | AI decision log with input, output, explanation, confidence                  | K-09, K-07 | [LCA-AI-001] |
| **HITL Overrides**    | Enable human oversight          | K-09 provides override API; all overrides audited with reason                 | HITL override log with original prediction, override value, operator, reason | K-09, K-07 | [LCA-AI-001] |
| **Drift Detection**   | Monitor model performance       | K-09 calculates PSI drift score; alerts when threshold exceeded               | Drift detection log with PSI score, drifted features, recommendation         | K-09       | [LCA-AI-001] |

---

## 3. AUDITABILITY & EVIDENCE ARCHITECTURE

### 3.1 Immutable Audit Log Design

**Architecture**: Cryptographic hash chain (blockchain-style)

```
AuditRecord[N] = {
  record_id: UUID,
  previous_hash: SHA256(AuditRecord[N-1]),
  tenant_id: UUID,
  actor_id: String,
  action: String,
  resource_uri: String,
  before_state: JSON,
  after_state: JSON,
  timestamp_gregorian: Timestamp,
  timestamp_bs: String,
  current_hash: SHA256(previous_hash + record_data)
}
```

**Tamper-Evidence Mechanism**:

1. Each audit record contains hash of previous record
2. Background job continuously verifies hash chain integrity
3. Any modification breaks chain → triggers `AuditIntegrityCompromisedEvent`
4. Alert sent to Security Operations Center immediately
5. System enters investigation mode (optional: block new operations)

**External Hash Anchoring** [ARB P0-05]:

1. Hourly and end-of-day Merkle root hash computed over all audit records in the period
2. Hash anchored to external timestamp authority (options: regulator TSA, public blockchain, RFC 3161 TSA)
3. Anchoring receipt stored alongside audit chain
4. Standalone verification tool provided to regulators for independent chain verification without platform access
5. If external anchor unavailable: local verification continues; anchoring retries with exponential backoff

**Storage**:

- Primary: PostgreSQL with WORM (Write-Once-Read-Many) enforcement
- Backup: Immutable object storage (S3 Glacier / Azure Archive)
- Retention: Minimum 7 years (configurable per jurisdiction via K-02)

**Epic Reference**: K-07 Audit Framework

### 3.2 Evidence Export Formats

| Format   | Use Case                              | Machine-Readable | Regulator Preference        |
| -------- | ------------------------------------- | ---------------- | --------------------------- |
| **CSV**  | Data analysis, spreadsheet import     | Yes              | High (SEBON, NRB)           |
| **PDF**  | Human-readable reports                | No               | Medium                      |
| **JSON** | API integration, automated processing | Yes              | High (modern regulators)    |
| **XBRL** | Structured financial reporting        | Yes              | High (financial statements) |

**Evidence Package Structure**:

```
evidence_package_<timestamp>.zip
├── manifest.json                 # Package metadata, file checksums
├── signature.sig                 # Ed25519 signature of manifest
├── audit_trail.csv              # Audit logs for requested period
├── transactions.csv             # Transaction data
├── positions.csv                # Position snapshots
├── compliance_checks.csv        # Pre-trade compliance results
├── surveillance_alerts.csv      # Market abuse alerts
├── case_management.csv          # Investigation case details
└── public_key.pem               # Platform public key for verification
```

**Cryptographic Signing**:

- Algorithm: Ed25519 (post-quantum resistant alternative: Dilithium)
- Key Management: Platform private key in HSM; public key in evidence package
- Verification: Regulator verifies `signature.sig` against `manifest.json` using `public_key.pem`

**Epic Reference**: R-01 Regulator Portal, K-07 Audit Framework

### 3.3 Inspection Mode Workflow

**Regulator Access Process**:

1. **Authentication**: MFA-required login to R-01 Regulator Portal
2. **Jurisdiction Filtering**: K-01 IAM enforces row-level security (regulator sees only their jurisdiction data)
3. **Query Builder**: Ad-hoc query interface with filters (date range, entity, transaction type) plus regulator-approved metadata-driven query templates and export schemas
4. **Query Execution**: Optimized queries against K-05 Event Store, K-16 Ledger, K-07 Audit Framework
5. **Evidence Generation**: Async job creates evidence package with cryptographic signature
6. **Download**: Secure download link (TLS 1.3+, expires in 24 hours)
7. **Access Audit**: All regulator actions logged to K-07 with full traceability

**Metadata Governance Notes**:

1. Regulator-visible query presets, case-response forms, and notification preference schemas are versioned metadata assets governed by K-02.
2. Active investigations and formal regulator requests pin the metadata versions in force at case or request creation.
3. Additive metadata updates may be adopted at defined workflow checkpoints; breaking changes require a new version and must not invalidate already-open cases.

**Query Performance**:

- P99 latency: < 5 seconds for 1M records
- Pagination: 1000 records per page
- Export limit: 10M records per request (configurable)

**Epic Reference**: R-01 Regulator Portal

### 3.4 Maker-Checker Enforcement Points

| Module             | Operation                                         | Maker              | Checker                      | Evidence                                       |
| ------------------ | ------------------------------------------------- | ------------------ | ---------------------------- | ---------------------------------------------- |
| K-02 Config Engine | Critical config changes (e.g., margin thresholds) | Config admin       | Senior config admin          | Config change audit log with maker/checker IDs |
| K-03 Rules Engine  | T2 Rule Pack deployment                           | Compliance analyst | Compliance officer           | Rule deployment audit log with test results    |
| K-09 AI Governance | Model deployment                                  | ML engineer        | AI governance officer        | Model deployment log with eval metrics         |
| K-09 AI Governance | Prompt template approval                          | Product manager    | Compliance officer           | Prompt approval log                            |
| D-01 OMS           | Large orders (> threshold)                        | Trader             | Senior trader / Risk manager | Order approval log                             |
| D-07 Compliance    | Exception approvals                               | Compliance analyst | Compliance officer           | Exception approval log with justification      |
| D-08 Surveillance  | Case closure                                      | Investigator       | Compliance officer           | Case closure log with resolution notes         |

**Enforcement Mechanism**:

- SDK-level validation: `maker_id ≠ checker_id`
- Rejection on violation with error code `MAKER_CHECKER_VIOLATION`
- All attempts (successful and failed) logged to K-07

**Epic Reference**: K-01 IAM, K-02, K-03, K-07, K-09, D-01, D-07, D-08

---

## 4. DATA GOVERNANCE

### 4.1 Residency Enforcement

**Mechanism**: K-08 Data Governance routes writes to jurisdiction-specific storage clusters

**Example Configuration** (T1 Config Pack):

```json
{
  "data_residency_rules": [
    {
      "jurisdiction": "NP",
      "data_classes": ["PII", "TRANSACTION", "AUDIT"],
      "storage_target": "postgres://np-primary.ap-south-1.rds.amazonaws.com",
      "backup_target": "s3://siddhanta-np-backup-ap-south-1"
    },
    {
      "jurisdiction": "IN",
      "data_classes": ["PII", "TRANSACTION", "AUDIT"],
      "storage_target": "postgres://in-primary.ap-south-1.rds.amazonaws.com",
      "backup_target": "s3://siddhanta-in-backup-ap-south-1"
    }
  ]
}
```

**Enforcement Layer**:

- Database: Row-level security (PostgreSQL RLS) filters by `jurisdiction` column
- Application: K-08 Data Governance Client intercepts writes, routes to correct cluster
- Verification: Automated job validates data residency compliance daily

**Evidence**: Data routing log showing jurisdiction → storage target mapping for each write

**Epic Reference**: K-08 Data Governance

### 4.2 Retention Policies

**Policy Definition** (T1 Config Pack):

```json
{
  "retention_policies": [
    {
      "jurisdiction": "NP",
      "data_class": "AUDIT_LOG",
      "retention_days": 2555, // 7 years
      "archive_after_days": 90,
      "deletion_allowed": false // Permanent retention
    },
    {
      "jurisdiction": "NP",
      "data_class": "TRANSACTION",
      "retention_days": 2555,
      "archive_after_days": 365,
      "deletion_allowed": false
    },
    {
      "jurisdiction": "NP",
      "data_class": "CLIENT_PII",
      "retention_days": 2555,
      "archive_after_days": 1825, // 5 years
      "deletion_allowed": true // Subject to data subject rights
    }
  ]
}
```

**Lifecycle Automation**:

1. Daily job queries records older than `archive_after_days`
2. Archives to cold storage (S3 Glacier)
3. Deletes from primary storage (if `deletion_allowed`)
4. Emits `DataArchivedEvent` / `DataDeletedEvent` to K-07

**Retention Enforcement**:

- Deletion attempts before retention period → blocked with error `RETENTION_POLICY_VIOLATION`
- Legal hold support: Override retention policy for litigation/investigation

**Epic Reference**: K-08 Data Governance, K-07 Audit Framework

### 4.3 Deletion Lifecycle

**Data Subject Rights API** (GDPR-compliant):

```typescript
// Right to Erasure
async function eraseDataSubject(
  subjectId: string,
  jurisdiction: string,
): Promise<ErasureResult> {
  // 1. Verify retention policy allows erasure
  const policy = await getRetentionPolicy(jurisdiction, "CLIENT_PII");
  if (!policy.deletion_allowed) {
    throw new Error("Deletion not allowed per retention policy");
  }

  // 2. Check for legal holds
  const holds = await getLegalHolds(subjectId);
  if (holds.length > 0) {
    throw new Error("Cannot delete: active legal hold");
  }

  // 3. Identify all data for subject (via K-08 lineage graph)
  const dataRecords = await findDataBySubject(subjectId);

  // 4. Pseudonymize or delete
  for (const record of dataRecords) {
    if (record.data_class === "AUDIT_LOG") {
      // Pseudonymize (retain audit trail)
      await pseudonymize(record.id, subjectId);
    } else {
      // Hard delete
      await delete record.id;
    }
  }

  // 5. Emit DataErasedEvent
  await auditClient.log({
    action: "DATA_ERASED",
    resource: `data_subject:${subjectId}`,
    details: { record_count: dataRecords.length },
  });

  return { erased_records: dataRecords.length };
}
```

**Evidence**: `DataErasedEvent` in K-07 Audit Framework with record count, timestamp

**Epic Reference**: K-08 Data Governance

### 4.4 Encryption & Key Ownership Model

**Encryption Layers**:

| Layer                  | Mechanism   | Key Management              | Use Case                            |
| ---------------------- | ----------- | --------------------------- | ----------------------------------- |
| **In-Transit**         | TLS 1.3     | Platform-managed            | All network communication           |
| **At-Rest (Platform)** | AES-256-GCM | Platform-managed (AWS KMS)  | Non-sensitive data                  |
| **At-Rest (Tenant)**   | AES-256-GCM | Customer-managed keys (CMK) | Sensitive PII, transaction data     |
| **Field-Level**        | AES-256-GCM | Tenant-specific DEK         | Highly sensitive fields (e.g., SSN) |

**Key Rotation**:

- Platform keys: Automatic rotation every 90 days
- Customer-managed keys: Tenant controls rotation schedule
- Zero-downtime rotation: K-08 re-encrypts data with new key in background

**Key Revocation**:

- Tenant revokes CMK → immediate data access denial
- Platform cannot decrypt tenant data without CMK
- Evidence: Key rotation/revocation log in K-07

**Epic Reference**: K-08 Data Governance, K-14 Secrets Management

---

## 5. SURVEILLANCE & COMPLIANCE READINESS

### 5.1 Rule Packs (T2) for Compliance Checks

**T2 Rule Pack Structure** (OPA/Rego DSL):

```rego
package compliance.pre_trade.np

import future.keywords.if
import future.keywords.in

# Rule: Promoter Lock-In Enforcement
deny[msg] if {
  input.order.side == "SELL"
  input.client.shareholder_type == "PROMOTER"
  input.instrument.lock_in_expiry_bs > input.current_date_bs
  msg := sprintf("Promoter shares locked until %v (BS)", [input.instrument.lock_in_expiry_bs])
}

# Rule: Insider Trading Prevention
deny[msg] if {
  input.client.client_id in data.insider_lists[input.instrument.issuer_id]
  input.order.timestamp_gregorian < data.insider_lists_expiry[input.instrument.issuer_id]
  msg := "Client on insider list for this issuer"
}

# Rule: KYC Verification
deny[msg] if {
  input.client.verification_status != "VERIFIED"
  msg := "Client KYC not verified"
}

# Rule: Position Limit
deny[msg] if {
  new_position := input.current_position + input.order.quantity
  new_position > input.limits.max_position
  msg := sprintf("Position limit exceeded: %v > %v", [new_position, input.limits.max_position])
}
```

**Deployment Process**:

1. Compliance analyst authors T2 Rule Pack in Rego
2. Automated test suite validates rule logic
3. Maker-checker approval (analyst → compliance officer)
4. K-03 Rules Engine loads rule pack (hot-reload, no restart)
5. All evaluations logged with rule version to K-07

**Epic Reference**: K-03 Rules Engine, D-07 Compliance

### 5.2 Case Management Audit Trail

**Case Lifecycle States**:

```
OPEN → INVESTIGATING → ESCALATED → CLOSED_FALSE_POSITIVE | CLOSED_VIOLATION | CLOSED_REFERRED
```

**Audit Trail Schema**:

```json
{
  "case_id": "case_7a8b9c0d",
  "alert_id": "alert_123",
  "pattern": "WASH_TRADE",
  "entity_id": "client_456",
  "status": "CLOSED_VIOLATION",
  "timeline": [
    {
      "timestamp": "2025-03-02T10:30:00Z",
      "action": "CASE_OPENED",
      "actor": "surveillance_system",
      "details": "AI detected wash trade pattern (confidence: 0.95)"
    },
    {
      "timestamp": "2025-03-02T11:00:00Z",
      "action": "ASSIGNED_INVESTIGATOR",
      "actor": "compliance_manager",
      "details": "Assigned to investigator_789"
    },
    {
      "timestamp": "2025-03-02T14:30:00Z",
      "action": "EVIDENCE_COLLECTED",
      "actor": "investigator_789",
      "details": "Reviewed order book, confirmed wash trade"
    },
    {
      "timestamp": "2025-03-02T16:00:00Z",
      "action": "CASE_CLOSED",
      "actor": "compliance_officer",
      "details": "Violation confirmed, referred to SEBON",
      "maker_id": "investigator_789",
      "checker_id": "compliance_officer"
    }
  ],
  "resolution_notes": "Client executed 5 wash trades on 2025-03-01. Evidence package sent to SEBON.",
  "evidence_package_id": "pkg_abc123"
}
```

**Maker-Checker**: Case closure requires approval from compliance officer (checker ≠ investigator)

**Evidence**: Complete case audit trail exported to regulator on request

**Epic Reference**: D-08 Surveillance, K-07 Audit Framework

### 5.3 Alert Explainability

**AI-Generated Alert Structure**:

```json
{
  "alert_id": "alert_123",
  "pattern": "WASH_TRADE",
  "confidence": 0.95,
  "entity_id": "client_456",
  "trigger_events": [
    "order_789_buy_100_NABIL_1250.00",
    "order_790_sell_100_NABIL_1250.00"
  ],
  "explanation": {
    "method": "SHAP",
    "feature_importance": {
      "time_delta_seconds": 0.45, // 5 seconds between orders
      "price_match": 0.35, // Exact price match
      "quantity_match": 0.2 // Exact quantity match
    },
    "reasoning_steps": [
      "Client placed buy order for 100 NABIL at 1250.00",
      "5 seconds later, same client placed sell order for 100 NABIL at 1250.00",
      "No economic rationale for immediate reversal at same price",
      "Pattern matches SEBON wash trade definition"
    ]
  },
  "model_id": "market-abuse-detector-v1",
  "model_version": "2.1.0",
  "timestamp": "2025-03-02T10:30:00Z"
}
```

**Explainability Artifacts**:

- SHAP values showing feature importance
- Reasoning steps in natural language
- Model version and confidence score
- All stored in K-07 Audit Framework

**HITL Override**:

- Compliance officer can mark alert as false positive
- Override reason required
- Feedback loop to K-09 for model retraining

**Epic Reference**: D-08 Surveillance, K-09 AI Governance

---

## 6. OPERATIONAL RESILIENCE

### 6.1 RPO/RTO Targets

| Component                 | RPO (Data Loss)  | RTO (Recovery Time) | Backup Strategy                       |
| ------------------------- | ---------------- | ------------------- | ------------------------------------- |
| **K-07 Audit Framework**  | 0 (no data loss) | < 5 minutes         | Synchronous replication to 3 replicas |
| **K-16 Ledger**           | 0                | < 5 minutes         | Event sourcing + snapshots            |
| **K-05 Event Store**      | 0                | < 5 minutes         | Kafka replication factor 3            |
| **D-01 OMS**              | 0                | < 5 minutes         | Event-sourced, replay from K-05       |
| **D-08 Surveillance**     | < 1 minute       | < 10 minutes        | Stream processing checkpoints         |
| **R-01 Regulator Portal** | N/A (read-only)  | < 15 minutes        | Stateless, auto-scaling               |

**Disaster Recovery**:

- Multi-region deployment (primary: ap-south-1, DR: ap-southeast-1)
- Automated failover for critical services (K-07, K-16, K-05)
- Regular DR drills (quarterly)

**Epic Reference**: K-05 Event Bus, K-07 Audit Framework, K-16 Ledger Framework

### 6.2 Failover & Degraded Modes

**Degraded Mode Matrix**:

| Failure Scenario               | Degraded Behavior                                                                                                                                                                                         | Evidence Continuity                                         | Recovery Action                                                               |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- | ----------------------------------------------------------------------------- |
| **K-07 Audit unavailable**     | Block all state-changing operations (fail-closed)                                                                                                                                                         | Buffer audit logs locally in persistent queue               | Restore K-07, drain buffer                                                    |
| **K-03 Rules Engine down**     | Circuit breaker activates (K-18); compliance rules fail-closed (deny); fee calculations use cached results with `degraded=true` flag; all degraded decisions flagged for post-recovery review [ARB P0-02] | Audit log of blocked/degraded operations with degraded flag | Circuit breaker half-open probe; restore K-03; re-evaluate degraded decisions |
| **K-09 AI Governance down**    | Fall back to deterministic rules                                                                                                                                                                          | AI decisions logged as "FALLBACK_MODE"                      | Restore K-09, review fallback decisions                                       |
| **D-08 Surveillance lag**      | Process events based on embedded timestamps (not arrival time)                                                                                                                                            | Surveillance alerts delayed but accurate                    | Scale up stream processors                                                    |
| **R-01 Regulator Portal down** | Notify regulator via email, provide manual evidence request process                                                                                                                                       | Portal access attempts logged                               | Restore R-01                                                                  |

**Incident Response Evidence**:

- All failover events logged to K-07
- Runbook execution logs
- Alert timeline
- Recovery verification checklist

**Epic Reference**: K-03, K-07, K-09, D-08, R-01

### 6.3 Incident Response Evidence

**Incident Lifecycle**:

```
DETECTED → TRIAGED → INVESTIGATING → MITIGATED → RESOLVED → POST_MORTEM
```

**Evidence Package for Incident**:

```
incident_<id>_evidence.zip
├── incident_summary.pdf          # Executive summary
├── timeline.csv                  # Event timeline
├── alerts.csv                    # System alerts during incident
├── runbook_execution.log         # Runbook steps executed
├── system_metrics.csv            # CPU, memory, latency during incident
├── audit_trail.csv               # Audit logs during incident window
├── recovery_verification.pdf     # Post-recovery checks
└── post_mortem.md                # Root cause analysis, remediation
```

**Regulatory Notification** [ARB P1-15]:

- P0 incidents (data breach, client money shortfall, system-wide outage > 15 min) → notify regulator within 4 hours via R-02
- P1 incidents (partial outage > 30 min, sanctions match) → notify regulator within 24 hours via R-02
- Internal escalation: On-call (immediate) → Team lead (15 min) → Head of Ops (30 min) → CCO (1 hour) → Board (4 hours)
- Post-mortem report required for all P0 incidents within 5 business days
- Incident report includes evidence package

**Epic Reference**: K-06 Observability, K-07 Audit Framework, R-02 Incident Response & Escalation

---

## 7. CHANGE MANAGEMENT

### 7.1 Config Pack Approvals & Audit Trail

**Change Process**:

1. **Proposal**: Config admin proposes change via K-02 Config Engine
2. **Validation**: Automated validation against schema
3. **Impact Analysis**: K-08 lineage graph shows affected systems
4. **Approval**: Maker-checker (config admin → senior config admin)
5. **Canary Rollout**: Deploy to 5% of tenants first
6. **Monitoring**: Track error rates, latency for 24 hours
7. **Full Rollout**: Deploy to remaining 95% if canary successful
8. **Audit**: All steps logged to K-07

**Audit Trail Schema**:

```json
{
  "change_id": "cfg_change_123",
  "config_key": "margin_threshold.NABIL",
  "before_value": 0.25,
  "after_value": 0.3,
  "maker_id": "config_admin_456",
  "checker_id": "senior_config_admin_789",
  "approval_timestamp": "2025-03-02T10:30:00Z",
  "effective_date_bs": "2081-11-18",
  "effective_date_gregorian": "2025-03-03T00:00:00Z",
  "rollout_status": "COMPLETED",
  "affected_tenants": ["tenant_np_1", "tenant_np_2"]
}
```

**Rollback**:

- One-click rollback to previous version
- Rollback logged with reason
- Rollback does not require maker-checker (emergency action)

**Epic Reference**: K-02 Configuration Engine, K-07 Audit Framework

### 7.2 Rule Pack Approvals & Audit Trail

**Deployment Pipeline**:

1. **Authoring**: Compliance analyst writes T2 Rule Pack in Rego
2. **Testing**: Automated test suite (unit tests, integration tests)
3. **Approval**: Maker-checker (analyst → compliance officer)
4. **Signing**: Cryptographic signature (Ed25519)
5. **Deployment**: K-03 hot-loads rule pack (no restart)
6. **Verification**: Smoke tests confirm rule active
7. **Audit**: All steps logged to K-07

**Test Suite Example**:

```rego
test_promoter_lock_in_enforced {
  result := deny with input as {
    "order": {"side": "SELL"},
    "client": {"shareholder_type": "PROMOTER"},
    "instrument": {"lock_in_expiry_bs": "2083-01-01"},
    "current_date_bs": "2082-01-01"
  }
  count(result) > 0
}

test_non_promoter_allowed {
  result := deny with input as {
    "order": {"side": "SELL"},
    "client": {"shareholder_type": "PUBLIC"},
    "instrument": {"lock_in_expiry_bs": "2083-01-01"},
    "current_date_bs": "2082-01-01"
  }
  count(result) == 0
}
```

**Audit Trail**:

```json
{
  "rule_pack_id": "compliance.pre_trade.np",
  "version": "2.1.0",
  "maker_id": "compliance_analyst_123",
  "checker_id": "compliance_officer_456",
  "test_results": {
    "total_tests": 25,
    "passed": 25,
    "failed": 0
  },
  "signature": "ed25519:abc123...",
  "deployed_at": "2025-03-02T10:30:00Z",
  "affected_operations": ["pre_trade_check", "order_placement"]
}
```

**Epic Reference**: K-03 Rules Engine, K-07 Audit Framework

### 7.3 Plugin Certification & Signing Pipeline

**Plugin Certification Checklist**:

| Category        | Check                         | Pass Criteria                             | Evidence                          |
| --------------- | ----------------------------- | ----------------------------------------- | --------------------------------- |
| **Technical**   | Contract validation           | Plugin manifest matches schema            | Validation report                 |
| **Technical**   | No direct ledger mutation     | Static analysis confirms no ledger writes | Static analysis report            |
| **Technical**   | Idempotency verified          | Duplicate invocations produce same result | Idempotency test results          |
| **Technical**   | Load tested                   | Handles 10x normal traffic                | Load test report                  |
| **Technical**   | Security scan                 | No critical vulnerabilities               | Security scan report              |
| **Compliance**  | Audit logs emitted            | All actions logged to K-07                | Audit log sample                  |
| **Compliance**  | PII classification declared   | Manifest lists PII classes accessed       | Manifest review                   |
| **Compliance**  | Data retention rules mapped   | Plugin respects K-08 retention policies   | Retention policy compliance check |
| **Compliance**  | RBAC validated                | Plugin respects K-01 access controls      | RBAC test results                 |
| **Operational** | Rollback strategy tested      | Plugin can be disabled without data loss  | Rollback test results             |
| **Operational** | Monitoring metrics registered | Plugin exposes health/performance metrics | Metrics registration confirmation |
| **Operational** | Error handling documented     | Error scenarios documented                | Error handling guide              |
| **Operational** | Documentation provided        | User guide, API docs, troubleshooting     | Documentation review              |

**Signing Process**:

1. Plugin passes all certification checks
2. Platform signs plugin artifact with Ed25519 private key
3. Signature embedded in plugin manifest
4. K-04 Plugin Runtime verifies signature before loading

**Audit Trail**:

```json
{
  "plugin_id": "np_settlement_calculator",
  "version": "1.2.0",
  "certification_status": "CERTIFIED",
  "certification_date": "2025-03-02T10:30:00Z",
  "certifier_id": "plugin_reviewer_789",
  "signature": "ed25519:def456...",
  "test_results": {
    "technical": "PASS",
    "compliance": "PASS",
    "operational": "PASS"
  }
}
```

**Epic Reference**: K-04 Plugin Runtime, K-07 Audit Framework

### 7.4 Rollback Guarantees

**Rollback Capabilities**:

| Component          | Rollback Mechanism                | Rollback Time | Data Loss Risk       |
| ------------------ | --------------------------------- | ------------- | -------------------- |
| **K-02 Config**    | Revert to previous version        | < 1 minute    | None (versioned)     |
| **K-03 Rule Pack** | Load previous rule version        | < 1 minute    | None (versioned)     |
| **K-04 Plugin**    | Disable plugin, route to fallback | < 1 minute    | None (stateless)     |
| **K-09 AI Model**  | Switch model version in registry  | < 1 minute    | None (versioned)     |
| **D-01 OMS**       | Replay events from K-05           | < 5 minutes   | None (event-sourced) |

**Rollback Audit**:

- All rollbacks logged to K-07 with reason
- Rollback initiator identified
- Post-rollback verification checklist

**Epic Reference**: K-02, K-03, K-04, K-05, K-09

### 7.5 Deployment Mode Differences

| Aspect                   | SaaS Multi-Tenant            | Dedicated Cloud              | On-Premises                   |
| ------------------------ | ---------------------------- | ---------------------------- | ----------------------------- |
| **Config Updates**       | Hot-reload via K-05          | Hot-reload via K-05          | Manual bundle import (signed) |
| **Rule Pack Updates**    | Hot-reload via K-05          | Hot-reload via K-05          | Manual bundle import (signed) |
| **Plugin Updates**       | Hot-swap via K-04            | Hot-swap via K-04            | Manual upload + verification  |
| **Data Residency**       | Logical (row-level security) | Physical (dedicated cluster) | Full sovereignty              |
| **Audit Trail**          | Centralized K-07             | Dedicated K-07 instance      | Local K-07 instance           |
| **Evidence Export**      | Via R-01 portal              | Via R-01 portal              | Manual export + USB transfer  |
| **Regulatory Reporting** | Automated via D-10           | Automated via D-10           | Manual report generation      |

**Air-Gapped Deployment**:

- Signed config/rule bundles transferred via USB
- Cryptographic verification before import
- Offline audit log export for regulator inspection

**Epic Reference**: K-02, K-03, K-04, K-10 Deployment Abstraction

---

## 8. AI GOVERNANCE (REGULATOR VIEW)

### 8.1 Model Registry with Risk Tiers

**Risk Tier Classification**:

| Tier                  | Description                                 | Examples                                  | Approval Required           | Monitoring Frequency |
| --------------------- | ------------------------------------------- | ----------------------------------------- | --------------------------- | -------------------- |
| **TIER-1 (Critical)** | Autonomous decisions affecting client funds | Auto-execution models, margin calculation | Board-level approval        | Real-time            |
| **TIER-2 (High)**     | Recommendations requiring human approval    | Trade surveillance, credit scoring        | Compliance officer approval | Daily                |
| **TIER-3 (Medium)**   | Operational efficiency, no client impact    | Document classification, query assistance | ML team lead approval       | Weekly               |
| **TIER-4 (Low)**      | Internal tools, no regulatory impact        | Log analysis, performance optimization    | Self-service                | Monthly              |

**Model Registry Schema**:

```json
{
  "model_id": "market-abuse-detector-v1",
  "version": "2.1.0",
  "risk_tier": "TIER-2",
  "purpose": "Detect wash trades, spoofing, front-running",
  "framework": "PyTorch",
  "training_date": "2025-02-15",
  "training_dataset_size": 1000000,
  "performance_metrics": {
    "accuracy": 0.95,
    "precision": 0.93,
    "recall": 0.94,
    "false_positive_rate": 0.05
  },
  "explainability_method": "SHAP",
  "approved_by": "compliance_officer_456",
  "approved_at": "2025-03-01T10:00:00Z",
  "status": "ACTIVE",
  "deployment_date": "2025-03-02T00:00:00Z"
}
```

**Epic Reference**: K-09 AI Governance

### 8.2 Prompt Governance Approvals

**Prompt Template Lifecycle**:

1. **Authoring**: Product manager creates prompt template
2. **Testing**: Validate prompt with test inputs
3. **Review**: Compliance officer reviews for bias, accuracy
4. **Approval**: Maker-checker (product manager → compliance officer)
5. **Deployment**: K-09 activates prompt version
6. **Monitoring**: Track prompt performance, user feedback

**Prompt Template Example**:

```json
{
  "prompt_id": "order_summary_generator",
  "version": "1.2.0",
  "template": "Generate a summary for order {{order_id}} with {{quantity}} shares of {{instrument_id}} at price {{price}}. Include risk assessment and compliance notes.",
  "model_id": "gpt-4",
  "parameters": {
    "temperature": 0.7,
    "max_tokens": 150
  },
  "maker_id": "product_manager_123",
  "checker_id": "compliance_officer_456",
  "approved_at": "2025-03-02T10:30:00Z",
  "status": "ACTIVE"
}
```

**Audit Trail**: All prompt executions logged with input, output, model version

**Epic Reference**: K-09 AI Governance

### 8.3 Drift Detection & HITL

**Drift Monitoring**:

- **Metric**: Population Stability Index (PSI)
- **Threshold**: PSI < 0.1 (no action), 0.1 ≤ PSI < 0.2 (monitor), PSI ≥ 0.2 (retrain)
- **Frequency**: Hourly for TIER-1/TIER-2, daily for TIER-3/TIER-4

**Drift Alert Example**:

```json
{
  "alert_id": "drift_alert_123",
  "model_id": "market-abuse-detector-v1",
  "drift_score": 0.25,
  "drifted_features": ["transaction_volume", "price_volatility"],
  "recommendation": "RETRAIN",
  "detected_at": "2025-03-02T10:30:00Z"
}
```

**HITL Override Process**:

1. AI makes prediction (e.g., "FRAUD" with 0.87 confidence)
2. Human operator reviews evidence
3. Operator overrides to "NOT_FRAUD" with reason
4. Override logged to K-07
5. Feedback sent to K-09 for model retraining

**HITL Audit Trail**:

```json
{
  "decision_id": "dec_7a8b9c0d",
  "model_id": "fraud_detection_v2",
  "original_prediction": "FRAUD",
  "confidence": 0.87,
  "override_value": "NOT_FRAUD",
  "override_reason": "Customer verified via phone call",
  "overridden_by": "compliance_officer_456",
  "evidence": {
    "call_recording_id": "rec_123",
    "verification_method": "PHONE_OTP"
  },
  "overridden_at": "2025-03-02T10:35:00Z"
}
```

**Epic Reference**: K-09 AI Governance, K-07 Audit Framework

### 8.4 Explainability Artifacts for AI Decisions

**Explainability Methods**:

| Method         | Use Case                              | Output Format             | Regulator Readability |
| -------------- | ------------------------------------- | ------------------------- | --------------------- |
| **SHAP**       | Feature importance for tabular data   | Feature importance scores | High                  |
| **LIME**       | Local explanations for complex models | Feature contributions     | High                  |
| **Attention**  | Transformer model explanations        | Attention weights         | Medium                |
| **Rule-Based** | Deterministic decision trees          | Decision path             | Very High             |

**Explainability Artifact Example**:

```json
{
  "decision_id": "dec_7a8b9c0d",
  "model_id": "fraud_detection_v2",
  "input": {
    "transaction_amount": 50000,
    "merchant_category": "ELECTRONICS",
    "user_history_score": 0.8
  },
  "prediction": {
    "class": "FRAUD",
    "confidence": 0.87
  },
  "explanation": {
    "method": "SHAP",
    "feature_importance": {
      "transaction_amount": 0.45,
      "user_history_score": 0.35,
      "merchant_category": 0.2
    },
    "reasoning_steps": [
      "Transaction amount (50,000) is 3x user's average transaction",
      "User history score (0.8) is below fraud threshold (0.9)",
      "Merchant category (ELECTRONICS) has high fraud rate"
    ]
  },
  "timestamp": "2025-03-02T10:30:00Z"
}
```

**Storage**: All explainability artifacts stored in K-07 Audit Framework

**Regulator Access**: Exportable via R-01 Regulator Portal

**Epic Reference**: K-09 AI Governance, K-07 Audit Framework, R-01 Regulator Portal

### 8.5 Rollback & Incident Containment

**Model Rollback Process**:

1. Incident detected (e.g., high false positive rate)
2. Operator initiates rollback via K-09
3. K-09 switches traffic to previous model version
4. Rollback completes in < 1 minute (no restart)
5. Post-rollback verification (smoke tests)
6. Incident investigation (root cause analysis)

**Rollback Audit**:

```json
{
  "rollback_id": "rollback_123",
  "model_id": "fraud_detection_v2",
  "from_version": "2.1.0",
  "to_version": "2.0.0",
  "reason": "High false positive rate (15% vs. baseline 5%)",
  "initiated_by": "ml_engineer_789",
  "rollback_timestamp": "2025-03-02T10:30:00Z",
  "verification_status": "PASSED"
}
```

**Incident Containment**:

- Automatic rollback on critical metrics breach (e.g., error rate > 10%)
- Circuit breaker pattern: disable model if consecutive failures > threshold
- Fallback to deterministic rules if AI unavailable

**Epic Reference**: K-09 AI Governance, K-07 Audit Framework

---

## 9. EVIDENCE PACKAGE CHECKLIST

### 9.1 Standard Regulatory Examination Request

**Checklist for Evidence Package Generation**:

- [ ] **Audit Trail**
  - [ ] All audit logs for requested period (CSV/JSON)
  - [ ] Hash chain verification report
  - [ ] Tamper-evidence certificate
- [ ] **Transaction Data**
  - [ ] All trades (order placement, execution, settlement)
  - [ ] Pre-trade compliance checks (pass/fail with reasons)
  - [ ] Post-trade reconciliation reports
- [ ] **Position Data**
  - [ ] Daily position snapshots
  - [ ] Position reconciliation reports (broker vs. depository)
  - [ ] Client asset segregation reports
- [ ] **Compliance Records**
  - [ ] KYC/AML verification status
  - [ ] Restricted securities enforcement logs
  - [ ] Exception approvals with maker-checker trail
- [ ] **Surveillance Data**
  - [ ] Market abuse alerts (wash trades, spoofing, insider trading)
  - [ ] Case management records (investigation timeline, resolution)
  - [ ] AI explainability artifacts for alerts
- [ ] **Configuration & Rules**
  - [ ] Active config packs for period
  - [ ] Active rule packs for period
  - [ ] Config/rule change audit trail
- [ ] **AI Governance**
  - [ ] Active AI models for period
  - [ ] AI decision logs with explanations
  - [ ] HITL override records
  - [ ] Model drift detection reports
- [ ] **Access Logs**
  - [ ] User access logs (login, logout, failed attempts)
  - [ ] Privileged access logs (admin actions)
  - [ ] Regulator portal access logs
- [ ] **Incident Reports**
  - [ ] System incidents during period
  - [ ] Incident response evidence
  - [ ] Post-mortem reports
- [ ] **Cryptographic Verification**
  - [ ] Evidence package manifest with checksums
  - [ ] Ed25519 signature of manifest
  - [ ] Platform public key for verification
  - [ ] Signature verification instructions

### 9.2 Evidence Package Generation SLA

| Request Type                              | Generation Time | Delivery Method          | Retention |
| ----------------------------------------- | --------------- | ------------------------ | --------- |
| **Ad-hoc query** (< 1M records)           | < 5 minutes     | R-01 portal download     | 30 days   |
| **Standard examination** (1M-10M records) | < 2 hours       | Secure file transfer     | 90 days   |
| **Full audit** (> 10M records)            | < 24 hours      | Physical media + courier | 1 year    |

### 9.3 Evidence Integrity Verification

**Regulator Verification Steps**:

1. **Download Evidence Package**: `evidence_package_<timestamp>.zip`
2. **Extract Files**: Unzip to local directory
3. **Verify Signature**:
   ```bash
   # Using OpenSSL
   openssl dgst -sha256 -verify public_key.pem -signature signature.sig manifest.json
   ```
4. **Verify Checksums**:
   ```bash
   # Compare checksums in manifest.json with actual files
   sha256sum -c manifest.json
   ```
5. **Review Data**: Open CSV/JSON files in analysis tools
6. **Validate Completeness**: Ensure all requested data present

**Expected Output**: "Verified OK" for signature and checksums

---

## 10. GAPS & REQUIRED CONTROLS

### 10.1 Identified Gaps

| Gap ID      | Area                      | Description                                              | Severity | Mitigation Required                                                                                         | Epic Reference   |
| ----------- | ------------------------- | -------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------- | ---------------- |
| **GAP-001** | Client Money Segregation  | No explicit daily reconciliation workflow in epics       | HIGH     | ✅ RESOLVED: EPIC-D-13 Client Money Reconciliation created                                                  | D-13 [ARB P1-11] |
| **GAP-002** | Beneficial Ownership      | No beneficial ownership graph tracking in K-01 IAM       | MEDIUM   | ✅ RESOLVED: K-01 FR13 added (UBO graph with ownership %, DAG queries, maker-checker)                       | K-01             |
| **GAP-003** | Sanctions Screening       | No real-time sanctions screening integration             | HIGH     | ✅ RESOLVED: EPIC-D-14 Sanctions Screening created                                                          | D-14 [ARB P1-13] |
| **GAP-004** | Trade Reporting           | No trade reporting to exchange/regulator in real-time    | MEDIUM   | ✅ RESOLVED: D-10 FR7/FR8 added (real-time trade reporting, acknowledgment tracking, report reconciliation) | D-10             |
| **GAP-005** | Disaster Recovery Testing | No automated DR testing framework                        | MEDIUM   | ✅ RESOLVED: EPIC-T-02 Chaos Engineering & Resilience Testing created                                       | T-02 [ARB P2-19] |
| **GAP-006** | Penetration Testing       | No continuous penetration testing                        | MEDIUM   | ✅ RESOLVED: T-01 FR8 enhanced (continuous pen testing, vulnerability scanning, quarterly audits)           | T-01             |
| **GAP-007** | Regulator Notification    | No automated critical incident notification to regulator | HIGH     | ✅ RESOLVED: EPIC-R-02 Incident Response & Escalation created                                               | R-02 [ARB P1-15] |
| **GAP-008** | Data Breach Response      | No data breach response playbook                         | HIGH     | ✅ RESOLVED: K-08 FR8 Data Breach Response Workflow added                                                   | K-08 [ARB P2-20] |

### 10.2 Required Epics/Controls

**Recommended New Epics**:

1. ✅ **EPIC-D-13: Client Money Reconciliation** — CREATED
   - Daily reconciliation of client funds vs. bank/custodian balances
   - Automated break detection, classification, and escalation (3/5/10 day tiers)
   - Segregation verification (client funds ≥ obligations)
   - Integration with K-16 Ledger Framework

2. ✅ **EPIC-D-14: Sanctions Screening** — CREATED
   - Real-time screening against OFAC, UN, EU sanctions lists (P99 < 50ms)
   - Fuzzy matching (Levenshtein, Jaro-Winkler, phonetic)
   - Match review workflow with maker-checker
   - Air-gap support with offline signed list bundles

3. ✅ **D-10 FR7/FR8: Real-Time Trade Reporting** — RESOLVED (added to existing D-10 v1.1.0)
   - Real-time trade reporting to exchange/regulator (< 15 min SLA)
   - Acknowledgment tracking and retry logic (exponential backoff, max 3 retries)
   - Report reconciliation with break detection and escalation
   - Dashboard showing real-time reporting status

4. ✅ **EPIC-K-17: Distributed Transaction Coordinator** — CREATED [ARB P0-01]
   - Outbox pattern for transactional event publishing
   - Version vectors for cross-stream causal ordering
   - Compensation orchestration with retry and manual resolution

5. ✅ **EPIC-K-18: Resilience Patterns Library** — CREATED [ARB P0-02]
   - Circuit breakers, bulkheads, retry policies, timeout management
   - Pre-defined resilience profiles (CRITICAL_PATH, BEST_EFFORT, COMPLIANCE_SENSITIVE)
   - Dependency health dashboard

6. ✅ **EPIC-K-19: DLQ Management & Event Replay** — CREATED [ARB P0-04]
   - DLQ monitoring dashboard with threshold alerting
   - Root cause analysis requirement before replay
   - Poison pill quarantine and classification

7. ✅ **EPIC-R-02: Incident Response & Escalation** — CREATED [ARB P1-15]
   - Automated regulator notification (P0: 4 hours, P1: 24 hours)
   - Multi-level internal escalation with acknowledgment tracking
   - Post-mortem requirement for P0 incidents

8. ✅ **EPIC-T-02: Chaos Engineering & Resilience Testing** — CREATED [ARB P2-19]
   - Fault injection framework for controlled chaos experiments
   - Pre-defined scenarios for all ARB failure scenarios
   - DR drill automation with RTO/RPO measurement
   - Resilience scorecard integrated into CI/CD gates

### 10.3 Control Enhancements

**Recommended Enhancements to Existing Epics**:

| Epic                          | Enhancement                         | Rationale                                                 | Status                      |
| ----------------------------- | ----------------------------------- | --------------------------------------------------------- | --------------------------- |
| **K-01 IAM**                  | Add beneficial ownership graph      | Track ultimate beneficial owners (UBO) for AML compliance | ✅ K-01 FR13 [GAP-002]      |
| **K-07 Audit**                | Add blockchain-style consensus      | Multi-party verification of audit trail integrity         | ✅ K-07 FR7 [ARB P0-05]     |
| **K-08 Data Governance**      | Add data breach response playbook   | Regulatory requirement for breach notification            | ✅ K-08 FR8 [ARB P2-20]     |
| **D-07 Compliance**           | Add sanctions screening integration | Real-time sanctions compliance                            | ✅ D-14 created [ARB P1-13] |
| **D-09 Post-Trade**           | Add client money reconciliation     | Daily reconciliation of client funds                      | ✅ D-13 created [ARB P1-11] |
| **D-10 Regulatory Reporting** | Add real-time trade reporting       | Regulatory requirement for timely reporting               | ✅ D-10 FR7/FR8 [GAP-004]   |
| **R-01 Regulator Portal**     | Add automated incident notification | Proactive regulator engagement                            | ✅ R-02 created [ARB P1-15] |
| **T-01 Integration Testing**  | Add continuous penetration testing  | Security vulnerability management                         | ✅ T-01 FR8 [GAP-006]       |

### 10.4 Compliance Readiness Assessment

**Current State**:

| Compliance Area              | Readiness | Evidence                                                                               | Gaps                         |
| ---------------------------- | --------- | -------------------------------------------------------------------------------------- | ---------------------------- |
| **Audit Trail**              | ✅ READY  | K-07 immutable audit logs with hash chain                                              | None                         |
| **Maker-Checker**            | ✅ READY  | Enforced across K-02, K-03, K-09, D-01, D-07, D-08                                     | None                         |
| **Data Residency**           | ✅ READY  | K-08 enforces jurisdiction-specific storage                                            | None                         |
| **AI Governance**            | ✅ READY  | K-09 model registry, explainability, HITL                                              | None                         |
| **Surveillance**             | ✅ READY  | D-08 market abuse detection with AI                                                    | None                         |
| **Client Money Segregation** | ✅ READY  | K-16 Ledger + D-13 daily reconciliation with segregation verification                  | GAP-001 resolved [ARB P1-11] |
| **Sanctions Screening**      | ✅ READY  | D-14 real-time screening with fuzzy matching and review workflow                       | GAP-003 resolved [ARB P1-13] |
| **Trade Reporting**          | ✅ READY  | D-10 FR7/FR8 real-time trade reporting with acknowledgment tracking and reconciliation | GAP-004 resolved             |
| **Incident Response**        | ✅ READY  | R-02 automated notification with escalation workflow                                   | GAP-007 resolved [ARB P1-15] |
| **Data Breach Response**     | ✅ READY  | K-08 FR8 automated breach response workflow                                            | GAP-008 resolved [ARB P2-20] |
| **Distributed Transactions** | ✅ READY  | K-17 Outbox pattern with version vectors and compensation                              | [ARB P0-01]                  |
| **Resilience Patterns**      | ✅ READY  | K-18 circuit breakers, bulkheads, retry policies                                       | [ARB P0-02]                  |
| **Financial Precision**      | ✅ READY  | K-16 fixed-point arithmetic with configurable precision                                | [ARB P0-03]                  |
| **DLQ Management**           | ✅ READY  | K-19 monitoring, RCA requirement, replay tooling                                       | [ARB P0-04]                  |
| **Audit Anchoring**          | ✅ READY  | K-07 external hash anchoring to TSA/blockchain                                         | [ARB P0-05]                  |
| **Chaos Engineering**        | ✅ READY  | T-02 fault injection, DR drills, resilience scorecards                                 | [ARB P2-19]                  |
| **Beneficial Ownership**     | ✅ READY  | K-01 FR13 UBO graph with DAG queries, ownership %, maker-checker                       | GAP-002 resolved             |
| **Penetration Testing**      | ✅ READY  | T-01 FR8 continuous pen testing, vulnerability scanning, quarterly audits              | GAP-006 resolved             |

**Recommendation**: **All gaps resolved.** All 4 HIGH severity gaps (GAP-001, GAP-003, GAP-007, GAP-008) and all 4 MEDIUM severity gaps (GAP-002, GAP-004, GAP-005, GAP-006) have been addressed through new epics and epic enhancements. No remaining open items.

---

## 11. APPENDICES

### Appendix A: Acronyms & Definitions

| Term     | Definition                                                            |
| -------- | --------------------------------------------------------------------- |
| **ASR**  | Authoritative Source Register (external facts requiring verification) |
| **BS**   | Bikram Sambat (Nepali calendar)                                       |
| **CMK**  | Customer-Managed Key                                                  |
| **CQRS** | Command Query Responsibility Segregation                              |
| **DEK**  | Data Encryption Key                                                   |
| **DVP**  | Delivery Versus Payment                                               |
| **HITL** | Human-in-the-Loop                                                     |
| **HSM**  | Hardware Security Module                                              |
| **KMS**  | Key Management Service                                                |
| **LCA**  | Local Compliance Artifact (jurisdiction-specific control evidence)    |
| **PSI**  | Population Stability Index (drift detection metric)                   |
| **RBAC** | Role-Based Access Control                                             |
| **RPO**  | Recovery Point Objective (maximum acceptable data loss)               |
| **RTO**  | Recovery Time Objective (maximum acceptable downtime)                 |
| **SHAP** | SHapley Additive exPlanations (ML explainability method)              |
| **T1**   | Tier 1 Config Pack (jurisdiction-specific configuration)              |
| **T2**   | Tier 2 Rule Pack (jurisdiction-specific compliance rules)             |
| **T3**   | Tier 3 Executable Pack (jurisdiction-specific adapters/plugins)       |
| **UBO**  | Ultimate Beneficial Owner                                             |
| **WORM** | Write-Once-Read-Many                                                  |

### Appendix B: Reference Documents

| Document                   | Location                                                    | Purpose                        |
| -------------------------- | ----------------------------------------------------------- | ------------------------------ |
| **Platform Specification** | `docs/All_In_One_Capital_Markets_Platform_Specification.md` | High-level architecture        |
| **Epic Files**             | `epics/EPIC-*.md`                                           | Detailed module specifications |
| **LLD Documents**          | `lld/LLD_*.md`                                              | Low-level design documents     |
| **LLD Index**              | `lld/LLD_INDEX.md`                                          | Master index of all LLDs       |

### Appendix C: Contact Information

| Role                   | Contact    | Responsibility                             |
| ---------------------- | ---------- | ------------------------------------------ |
| **Platform Architect** | [REDACTED] | Architecture decisions, control framework  |
| **Compliance Officer** | [REDACTED] | Regulatory compliance, audit coordination  |
| **Security Lead**      | [REDACTED] | Security controls, incident response       |
| **Regulator Liaison**  | [REDACTED] | Regulator communication, evidence requests |

---

**END OF REGULATORY ARCHITECTURE DOCUMENT**

**Document Control**:

- **Version**: 2.0.0
- **Last Updated**: 2026-03-08
- **Next Review**: 2026-06-08 (Quarterly)
- **Approved By**: [PENDING]
- **Classification**: Confidential - Regulatory Use

**Disclaimer**: This document describes the control framework and evidence architecture of Project Siddhanta. Jurisdiction-specific regulatory requirements are referenced via ASR/LCA placeholders and must be validated against current regulations. This document does not constitute legal advice.
