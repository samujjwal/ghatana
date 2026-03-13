EPIC-ID: EPIC-K-08
EPIC NAME: Data Governance
LAYER: KERNEL
MODULE: K-08 Data Governance
VERSION: 1.1.1

---

#### Section 1 — Objective

Implement the K-08 Data Governance module to enforce strict data lineage, data residency, retention lifecycles, and encryption abstraction across the platform. This epic fulfills the requirement for hybrid deployment flexibility (Principle 19) by ensuring that sensitive regulatory and PII data is physically stored in jurisdiction-compliant zones while allowing non-sensitive analytics to flow globally. It acts as the gatekeeper for all data at rest.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Data lineage tracking across modules and pipelines.
  2. Data residency enforcement via configurable routing rules.
  3. Automated retention and deletion lifecycles.
  4. Encryption abstraction (KMS integration, customer-managed keys, rotation).
  5. **FR5 Key Rotation:** Provide zero-downtime key rotation capabilities for tenant-managed and platform-managed keys.
  6. **FR6 Multi-Calendar Date Math:** Expiry and retention calculations must correctly interpret `CalendarDate` values via K-15. Configurable retention periods may be expressed in any calendar system (e.g., Gregorian years, BS fiscal years) with K-15 resolving the canonical UTC boundary.
  7. **FR7 Encryption Abstraction:** Provide a unified API for encryption at rest, supporting multiple KMS backends (AWS KMS, Azure Key Vault, HashiCorp Vault).
  8. **FR8 Data Lineage Graph:** Maintain a queryable data lineage graph showing data flow from source to destination across all modules, enabling impact analysis for data changes.
  9. **FR9 Data Subject Rights API:** Provide GDPR-compliant APIs for data subject rights: Right to Access (export all data for a subject), Right to Erasure (delete all data for a subject with audit trail), Right to Rectification (update incorrect data), and Right to Portability (export in machine-readable format). All operations must respect retention policies and legal holds.
- **Out-of-Scope:**
  1. Application-level authorization (handled by K-01).
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-06 (Observability), EPIC-K-07 (Audit Framework), EPIC-R-02 (Incident Response & Escalation)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Data Lineage:** The system must attach lineage metadata (source module, transformation steps) to all persistent records.
2. **FR2 Residency Enforcement:** The data access layer must route write operations to physical storage clusters based on the `jurisdiction` tag and Config Engine rules.
3. **FR3 Retention Lifecycle:** A background orchestrator must permanently delete or archive records that exceed their jurisdiction-specific retention period.
4. **FR4 Encryption at Rest:** All data stores must integrate with the KMS abstraction layer, ensuring transparent encryption.
5. **FR5 Key Rotation:** Provide zero-downtime key rotation capabilities for tenant-managed and platform-managed keys.
6. **FR6 Multi-Calendar Date Math:** Expiry and retention calculations must correctly interpret `CalendarDate` values via K-15. Configurable retention periods may be expressed in any calendar system (e.g., Gregorian years, BS fiscal years) with K-15 resolving the canonical UTC boundary.
7. **FR7 Database-Level Residency Enforcement:** In addition to application-layer routing (FR2), enforce mandatory database-level Row-Level Security (RLS) policies on all data stores. Every table containing jurisdiction-sensitive data must have an RLS policy filtering by `jurisdiction` column. A daily compliance scan job must verify zero cross-jurisdiction data leakage by comparing application routing logs against actual database row locations. Any violation triggers a CRITICAL alert and automated incident creation. [ARB P1-06]
8. **FR8 Data Breach Response Workflow:** Provide an automated breach response framework: (a) Detection: integrate with K-06 Observability for anomaly detection triggers (unusual data access patterns, bulk exports, unauthorized queries), (b) Containment: API to immediately revoke access tokens, rotate encryption keys, and isolate affected data stores, (c) Assessment: automated impact analysis using data lineage graph to identify all affected data subjects and records, (d) Notification: integration with EPIC-R-02 Incident Response & Escalation for automated regulator notification within configurable SLA (default: 72 hours per GDPR), (e) Remediation: audit trail of all breach response actions. [ARB P2-20]
9. **FR9 AI Training Data Lineage:** K-08 must track data lineage through to model training usage. Every record used as a training sample for a K-09-registered model must carry a `training_provenance: { model_id, dataset_version, used_at }` tag in its lineage graph. This ensures: (a) Data-subject erasure requests can propagate to fine-tuned model invalidation (if the erasure removes enough training samples, the affected model version must be re-evaluated), (b) Regulatory auditors can verify that training data for a deployed model was collected lawfully with appropriate consents.
10. **FR10 Feature Labeling Pipeline:** Provide a feature labeling API that allows K-09's fine-tuning pipeline to request labels for records within K-08's governance scope. Pipeline: (a) K-09 submits a `LabelingRequest` naming a dataset query, label schema, and assignees; (b) K-08 applies residency and PII masking before presenting data to labelers; (c) Labeler outputs are stored as `LabeledSample` records linked back to the originating event IDs; (d) Completed label batches are returned to K-09 as a `FineTuningDataset`. No raw PII is exposed to the labeling interface.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The routing engine and KMS abstractions are generic.
2. **Jurisdiction Plugin:** Data residency rules (e.g., "Nepal investor PII must stay in ap-south-1a") are defined via T1 Config Packs.
3. **Resolution Flow:** Config Engine supplies the routing table; data access layer applies it.
4. **Hot Reload:** Changes to routing rules take effect immediately for new writes.
5. **Backward Compatibility:** Data already written must be migrated if rules change.
6. **Future Jurisdiction:** New jurisdictions simply define their physical sink targets in the config.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `DataPolicy`: `{ policy_id: String, jurisdiction: String, data_class: Enum, storage_target: String, retention_days: Int }`
- **Multi-Calendar Fields:** N/A directly; uses K-15 `CalendarClient` for date math.
- **Event Schema Changes:** `DataArchivedEvent`, `DataDeletedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                |
| ----------------- | ---------------------------------------------------------------------------------------------------------- |
| Event Name        | `DataDeletedEvent`                                                                                         |
| Schema Version    | `v1.0.0`                                                                                                   |
| Trigger Condition | Data reaches the end of its legal retention period and is purged.                                          |
| Payload           | `{ "resource_id": "...", "data_class": "...", "deleted_at": "2025-03-02T10:30:00Z", "calendar_date": {} }` |
| Consumers         | Audit Framework                                                                                            |
| Idempotency Key   | `hash(resource_id + action)`                                                                               |
| Replay Behavior   | Ignored.                                                                                                   |
| Retention Policy  | Permanent (to prove deletion occurred).                                                                    |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `ClassifyDataCommand`                                                |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Data entity exists, classification level valid, requester authorized |
| Handler          | `DataGovernanceCommandHandler` in K-08 Data Governance               |
| Success Event    | `DataClassified`                                                     |
| Failure Event    | `DataClassificationFailed`                                           |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                                       |
| ---------------- | --------------------------------------------------------------------------------- |
| Command Name     | `EraseDataCommand`                                                                |
| Schema Version   | `v1.0.0`                                                                          |
| Validation Rules | Data subject request valid, retention policy allows erasure, requester authorized |
| Handler          | `DataErasureHandler` in K-08 Data Governance                                      |
| Success Event    | `DataErased`                                                                      |
| Failure Event    | `DataErasureFailed`                                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result              |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RotateEncryptionKeyCommand`                                         |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Key exists, new key generated, requester authorized                  |
| Handler          | `KeyManagementHandler` in K-08 Data Governance                       |
| Success Event    | `EncryptionKeyRotated`                                               |
| Failure Event    | `KeyRotationFailed`                                                  |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Classification / Pattern Recognition / Training Data Substrate
- **Primary Use Case:** K-08 is the AI data governance layer — it governs what data can be used for model training, enforces PII/residency constraints on feature sets, and provides the labeling pipeline for HITL feedback. It also uses AI to auto-classify incoming data schemas.
- **Inference Mode:** Asynchronous (schema classification runs in background on schema registration events).
- **Workflow Steps Exposed:** (a) Data schema registration triggers NER-based PII classification; (b) New data entity ingestion triggers sensitivity scoring; (c) Feature labeling requests from K-09 are processed through K-08's masking layer before presentation.
- **Model Registry Usage:** `pii-classifier-v1` (NER-based PII detection in schemas and unstructured fields), `data-sensitivity-scorer-v1` (classifies records into PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED)
- **Input Data / Feature Set:** Table schemas, field names, sample values (after PII masking), data lineage graph structure.
- **Output / AI Annotation:** Classification tag written to `DataPolicy` entity; `DataClassificationChangedEvent` emitted if AI auto-reclassifies a field.
- **Explainability Requirement:** Auto-classification must output the specific field names and patterns that triggered the PII or sensitivity classification. Explanation written to K-07.
- **Confidence Threshold:** Confidence < 0.7 → escalate to Data Steward for manual review. Confidence ≥ 0.7 → auto-tag with `PENDING_REVIEW` status. Manually reviewed tags replace AI tags.
- **Human Override Path:** Data Architect may override AI classification via `ClassifyDataCommand` (maker-checker required for downgrading sensitivity level).
- **Feedback Loop:** Human overrides of AI classification are fed to K-09 as labeled samples for `pii-classifier-v1` fine-tuning.
- **Drift Monitoring:** Classification confidence score distribution is monitored daily; if mean confidence drops below 0.75, a retraining job is triggered.
- **Fallback Behavior:** If AI classifier is offline, default to `CONFIDENTIAL` (highest applicable restriction) for all unclassified fields. Human review queue is populated for manual classification.
- **Tenant Isolation:** PII patterns can be extended per-jurisdiction via T2 Rule Packs (e.g., Nepal National ID format is only flagged by the Nepal pack).

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                  |
| ------------------------- | ------------------------------------------------- |
| Latency / Throughput      | Data routing overhead < 1ms                       |
| Scalability               | Horizontally scalable background workers          |
| Availability              | 99.999% uptime                                    |
| Consistency Model         | Eventual consistency for background deletions     |
| Security                  | Multi-KMS support (AWS/GCP/Azure/HashiCorp)       |
| Data Residency            | 100% strict enforcement per T1 rules              |
| Data Retention            | Automated purging                                 |
| Auditability              | All lifecycle changes logged                      |
| Observability             | Metrics: `data.purge.count`, `data.route.latency` |
| Extensibility             | N/A                                               |
| Upgrade / Compatibility   | N/A                                               |
| On-Prem Constraints       | Supports local HSMs via standard PKCS#11          |
| Ledger Integrity          | N/A                                               |
| Dual-Calendar Correctness | Deletion dates evaluated correctly                |

---

#### Section 10 — Acceptance Criteria

1. **Given** a data record tagged with `jurisdiction: NP`, **When** the OMS writes it, **Then** K-08 routes it exclusively to the Nepal-designated database cluster.
2. **Given** an audit record that reaches its 10-year retention limit, **When** the daily lifecycle job runs, **Then** the record is purged and a `DataDeletedEvent` is emitted to the Audit Framework.
3. **Given** a tenant opting for a Customer-Managed Key (CMK), **When** they revoke the key in their HSM, **Then** all subsequent reads of their encrypted data fail immediately.

---

#### Section 11 — Failure Modes & Resilience

- **Storage Target Unreachable:** Write fails synchronously; caller handles retry (or queues via K-05).
- **KMS Unreachable:** Data access halts to prevent unencrypted writes or failed reads. Alert triggered.
- **Cross-Jurisdiction Data Leak Detected:** CRITICAL alert; affected records quarantined; data migration initiated to correct jurisdiction; incident created via R-02. [ARB P1-06]
- **Data Breach Detected:** Automated containment (access revocation, key rotation); impact assessment via lineage graph; regulator notification via R-02 within SLA. [ARB P2-20]

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                        |
| ------------------- | --------------------------------------- |
| Metrics             | `kms.latency`, `lifecycle.job.duration` |
| Logs                | Internal engine logs                    |
| Traces              | N/A                                     |
| Audit Events        | `DataDeletedEvent`, `KeyRotatedEvent`   |
| Regulatory Evidence | Proof of data destruction [LCA-RET-001] |

---

#### Section 13 — Compliance & Regulatory Traceability

- Data Residency Enforcement [ASR-DATA-001]
- Data Retention and Purging [LCA-RET-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `DataGovernanceClient` (transparently wraps DB drivers).
- **Jurisdiction Plugin Extension Points:** T1 Config Pack for Data Policies.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                           |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, via routing config.                                                                  |
| Can a new regulator be added?                                         | Yes.                                                                                      |
| Can this run in an air-gapped deployment?                             | Yes, with local HSM support.                                                              |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes, tokenized asset data classifications are configured as T1 packs in the data catalog. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes, CBDC data residency rules are jurisdiction-specific policies driven by K-02 config.  |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Corrected the data residency compliance reference to the authoritative code registry.
