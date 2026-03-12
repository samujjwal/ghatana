EPIC-ID: EPIC-D-11
EPIC NAME: Reference Data
LAYER: DOMAIN
MODULE: D-11 Reference Data
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the D-11 Reference Data module, serving as the authoritative source for instruments, counterparties, legal entities, and benchmarks. This epic ensures that all reference data is versioned, dual-calendar aware, and synchronized across the platform via K-05 Event Bus. It implements Principle 10 (First-Party Subsystem) by providing a centralized, event-sourced master data management capability.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Instrument master data (ISIN, scrip codes, attributes).
  2. Legal entity master (issuers, counterparties, RTAs).
  3. Benchmark and index definitions.
  4. Data versioning and change tracking.
  5. Integration with external data providers via T3 Adapters.
- **Out-of-Scope:**
  1. The actual external data feeds (e.g., Bloomberg, Reuters) - handled by T3 Adapters.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** K-02, K-05, K-07, K-15
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Master Data Storage:** The module must maintain versioned records for all instruments and entities.
2. **FR2 Change Events:** Any modification to reference data must emit a `ReferenceDataUpdated` event to K-05.
3. **FR3 External Feed Integration:** The module must consume normalized data from T3 Data Provider Adapters and reconcile it against internal records.
4. **FR4 Dual-Calendar Fields:** Listing dates, maturity dates, and other temporal attributes must use `DualDate`.
5. **FR5 Maker-Checker:** Changes to critical reference data (e.g., ISIN mappings) require dual approval.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The versioning, event emission, and storage framework are generic.
2. **Jurisdiction Plugin:** Instrument taxonomies (e.g., Nepal sector classifications) are T1 Config Packs.
3. **Resolution Flow:** Config Engine provides the active taxonomy.
4. **Hot Reload:** New instrument types can be added dynamically.
5. **Backward Compatibility:** Historical versions of reference data remain accessible.
6. **Future Jurisdiction:** A new country's instrument types are simply new taxonomy entries.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `Instrument`: `{ isin: String, scrip_code: String, name: String, type: Enum, listing_date: DualDate, attributes: JSON }`
  - `LegalEntity`: `{ entity_id: UUID, name: String, jurisdiction: String, registration_number: String }`
- **Dual-Calendar Fields:** `listing_date`, `maturity_date`.
- **Event Schema Changes:** `ReferenceDataUpdated`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                           |
| ----------------- | ----------------------------------------------------------------------------------------------------- |
| Event Name        | `ReferenceDataUpdated`                                                                                |
| Schema Version    | `v1.0.0`                                                                                              |
| Trigger Condition | An instrument or entity record is created or modified.                                                |
| Payload           | `{ "entity_type": "INSTRUMENT", "entity_id": "...", "change_type": "UPDATE", "timestamp_bs": "..." }` |
| Consumers         | OMS, PMS, Pricing, Compliance                                                                         |
| Idempotency Key   | `hash(entity_id + version)`                                                                           |
| Replay Behavior   | Updates materialized views.                                                                           |
| Retention Policy  | Permanent.                                                                                            |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                           |
| ---------------- | --------------------------------------------------------------------- |
| Command Name     | `UpdateInstrumentCommand`                                             |
| Schema Version   | `v1.0.0`                                                              |
| Validation Rules | Instrument exists or new, data valid, maker-checker approval obtained |
| Handler          | `InstrumentCommandHandler` in D-11 Reference Data                     |
| Success Event    | `ReferenceDataUpdated`                                                |
| Failure Event    | `InstrumentUpdateFailed`                                              |
| Idempotency      | Command ID must be unique; duplicate commands return original result  |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CreateEntityCommand`                                                |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Entity identifier unique, entity type valid, requester authorized    |
| Handler          | `EntityCommandHandler` in D-11 Reference Data                        |
| Success Event    | `EntityCreated`                                                      |
| Failure Event    | `EntityCreationFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                           |
| ---------------- | --------------------------------------------------------------------- |
| Command Name     | `SyncExternalDataCommand`                                             |
| Schema Version   | `v1.0.0`                                                              |
| Validation Rules | External source configured, sync schedule valid, requester authorized |
| Handler          | `SyncCommandHandler` in D-11 Reference Data                           |
| Success Event    | `ExternalDataSynced`                                                  |
| Failure Event    | `ExternalDataSyncFailed`                                              |
| Idempotency      | Command ID must be unique; duplicate commands return original result  |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Data Quality / Anomaly Detection
- **Workflow Steps Exposed:** External feed ingestion.
- **Model Registry Usage:** `refdata-quality-checker-v1`
- **Explainability Requirement:** AI flags suspicious changes (e.g., sudden ISIN reassignment) for review.
- **Human Override Path:** Data steward approves or rejects the change.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard schema validation.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                          |
| ------------------------- | --------------------------------------------------------- |
| Latency / Throughput      | Lookup < 1ms; 50,000 TPS                                  |
| Scalability               | Horizontally scalable read replicas                       |
| Availability              | 99.999%                                                   |
| Consistency Model         | Strong consistency for writes                             |
| Security                  | Row-level tenant isolation                                |
| Data Residency            | Enforced via K-08                                         |
| Data Retention            | Permanent                                                 |
| Auditability              | All changes logged                                        |
| Observability             | Metrics: `refdata.lookup.latency`, `refdata.update.count` |
| Extensibility             | New entity types via schema evolution                     |
| Upgrade / Compatibility   | N/A                                                       |
| On-Prem Constraints       | Fully functional locally                                  |
| Ledger Integrity          | N/A                                                       |
| Dual-Calendar Correctness | Correct date storage                                      |

---

#### Section 10 — Acceptance Criteria

1. **Given** a new instrument listing, **When** created in D-11, **Then** a `ReferenceDataUpdated` event is emitted and all subscribers receive the update.
2. **Given** an external Bloomberg feed update, **When** ingested via the T3 Adapter, **Then** D-11 reconciles it and flags discrepancies for review.

---

#### Section 11 — Failure Modes & Resilience

- **External Feed Down:** D-11 continues serving cached data; alerts data operations.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                             |
| ------------------- | -------------------------------------------- |
| Metrics             | `refdata.cache.hit_rate`, `refdata.feed.lag` |
| Logs                | Reconciliation breaks                        |
| Traces              | Span `RefData.lookup`                        |
| Audit Events        | Action: `UpdateInstrument`                   |
| Regulatory Evidence | Instrument master audit trail.               |

---

#### Section 13 — Compliance & Regulatory Traceability

- Data integrity [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `RefDataClient.getInstrument(isin)`, `RefDataClient.searchInstruments(query)`, `RefDataClient.getExchangeCalendar(exchange, month_bs)`.
- **Jurisdiction Plugin Extension Points:** T1 Taxonomy Packs (e.g., Nepal instrument classification, GICS sector mapping).

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `InstrumentActivated` | New instrument added to universe | `instrument_id`, `isin`, `symbol`, `exchange`, `instrument_type` |
| `InstrumentDeactivated` | Instrument delisted or suspended | `instrument_id`, `reason`, `effective_date_bs` |
| `ReferenceDataUpdated` | Entity/instrument data changed | `entity_type`, `entity_id`, `changed_fields[]` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `CorporateActionAnnounced` | D-12 Corp Actions | Instrument metadata updates |
| `RulePackActivated` | K-03 Rules Engine | Updated classification rules |

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                                          |
| ---------------------------------------------------- | -------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                                                     |
| Can a new instrument type be added?                  | Yes, via schema extension.                               |
| Can this run in an air-gapped deployment?            | Partially; requires external data provider connectivity. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Reference Data Poisoning**
   - **Threat:** Attacker injects false instrument or entity data causing widespread system errors.
   - **Mitigation:** Data validation against multiple sources; maker-checker for manual entries; AI anomaly detection; all changes audited; version control.
   - **Residual Risk:** Coordinated attack across multiple data sources.

2. **ISIN/Identifier Manipulation**
   - **Threat:** False identifier mappings cause incorrect trade execution or settlement.
   - **Mitigation:** Independent verification with external registries; maker-checker for changes; all mappings audited; reconciliation checks.
   - **Residual Risk:** Compromised external registry.

3. **External Feed Compromise**
   - **Threat:** Malicious T3 Data Provider Adapter injects false data.
   - **Mitigation:** K-04 sandbox isolation; cryptographic signing; data validation; cross-source verification; all adapter calls logged.
   - **Residual Risk:** Supply chain attack on adapter.

4. **Master Data Theft**
   - **Threat:** Competitor steals proprietary instrument or entity data.
   - **Mitigation:** Encryption at rest; strict RBAC; all access logged; data classification; network segmentation.
   - **Residual Risk:** Insider threat with legitimate access.

5. **Entity Relationship Fraud**
   - **Threat:** False entity relationships hide beneficial ownership or related parties.
   - **Mitigation:** Maker-checker for relationship changes; AI detects suspicious patterns; all relationships audited; periodic verification.
   - **Residual Risk:** Sophisticated corporate structure obfuscation.

**Security Controls:**

- Maker-checker for all changes
- Multi-source validation
- T3 adapter sandboxing
- Encryption at rest
- AI-based anomaly detection
- Audit logging of all operations
- Version control and rollback capability

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
