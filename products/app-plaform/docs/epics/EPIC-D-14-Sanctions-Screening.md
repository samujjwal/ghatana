EPIC-ID: EPIC-D-14
EPIC NAME: Sanctions Screening
LAYER: DOMAIN
MODULE: D-14 Sanctions Screening
VERSION: 1.0.1
ARB-REF: P1-13

---

#### Section 1 — Objective

Deliver the D-14 Sanctions Screening module to prevent the platform from facilitating transactions with sanctioned entities. This epic directly remediates ARB finding P1-13 and Regulatory Architecture Document GAP-003, ensuring real-time screening against global sanctions lists (OFAC, UN, EU, and jurisdiction-specific lists) at critical workflow touchpoints.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Real-time sanctions screening at client onboarding, order placement, and settlement.
  2. Batch screening of existing client base against updated sanctions lists.
  3. Integration with external sanctions data providers (via T3 Adapter Packs).
  4. Local cache of sanctions lists for low-latency screening and air-gap support.
  5. Match review workflow for potential matches (fuzzy matching generates candidates).
  6. Integration with D-07 Compliance for pre-trade check pipeline via K-03.
- **Out-of-Scope:**
  1. AML transaction monitoring (handled by D-07 Compliance and D-08 Surveillance).
  2. KYC document verification (handled by K-01 IAM and T3 adapters).
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-18 (Resilience Patterns)
- **Kernel Readiness Gates:** K-03, K-05 must be stable.
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Real-Time Screening:** Screen entities (clients, counterparties, beneficiaries) against active sanctions lists at: (a) client onboarding, (b) order placement (as part of K-03 pre-trade pipeline), (c) settlement instruction creation, (d) wire transfer initiation. Screening must complete within P99 < 50ms using local cache.
2. **FR2 Sanctions List Management:** Ingest and maintain sanctions lists from configurable sources: (a) OFAC SDN/Consolidated, (b) UN Security Council, (c) EU Consolidated, (d) Jurisdiction-specific lists (loaded via T1 Config Packs). Lists are refreshed at configurable intervals (default: every 6 hours) with delta updates.
3. **FR3 Fuzzy Matching:** Support configurable fuzzy matching algorithms (Levenshtein distance, Jaro-Winkler, phonetic matching) to catch name variations, transliterations, and aliases. Match sensitivity is configurable per jurisdiction via T1 Config.
4. **FR4 Match Review Workflow:** When a potential match is detected: (a) transaction is held pending review, (b) `SanctionsMatchDetectedEvent` emitted, (c) compliance officer reviews match with entity details side-by-side, (d) officer marks as TRUE_MATCH (block) or FALSE_POSITIVE (release with audit), (e) all decisions audited in K-07 with maker-checker for TRUE_MATCH actions.
5. **FR5 Batch Screening:** Support periodic batch screening of the entire client base against updated lists. New matches emit alerts and trigger account review workflows.
6. **FR6 Screening Audit Trail:** Every screening decision (clear, potential match, confirmed match, false positive) must be logged immutably in K-07 with: entity screened, lists checked, match details, decision, decision maker, and timestamp (dual-calendar).
7. **FR7 Air-Gap Support:** Sanctions lists can be loaded from signed offline bundles for air-gapped deployments. List freshness is tracked and alerts raised if lists exceed configurable staleness threshold (default: 24 hours).
8. **FR8 Dual-Calendar Support:** All screening timestamps and list effective dates use dual-calendar via K-15.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The screening engine, fuzzy matching, and review workflow are generic.
2. **Jurisdiction Plugin:** Specific sanctions lists, matching thresholds, and screening touchpoints are defined in T1 Config Packs. External list provider integrations are T3 Adapter Packs.
3. **Resolution Flow:** K-02 Config Engine determines which lists and thresholds apply per jurisdiction.
4. **Hot Reload:** List updates and threshold changes apply immediately.
5. **Backward Compatibility:** Historical screening decisions retain the list version active at screening time.
6. **Future Jurisdiction:** New jurisdiction requires new list source adapter and threshold configuration.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `SanctionsList`: `{ list_id: String, source: String, version: String, last_updated: DualDate, entry_count: Int, status: Enum(ACTIVE, STALE, EXPIRED) }`
  - `SanctionsEntry`: `{ entry_id: UUID, list_id: String, entity_name: String, aliases: List<String>, entity_type: Enum(INDIVIDUAL, ORGANIZATION, VESSEL), identifiers: Map<String,String>, country: String }`
  - `ScreeningResult`: `{ screening_id: UUID, entity_screened: String, lists_checked: List<String>, result: Enum(CLEAR, POTENTIAL_MATCH, CONFIRMED_MATCH, FALSE_POSITIVE), match_score: Float, reviewed_by: String, decision_at: DualDate }`
- **Dual-Calendar Fields:** `last_updated` in `SanctionsList`; `decision_at` in `ScreeningResult`.
- **Event Schema Changes:** `SanctionsMatchDetectedEvent`, `SanctionsScreeningClearedEvent`, `SanctionsListUpdatedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                                                      |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `SanctionsMatchDetectedEvent`                                                                                                                                    |
| Schema Version    | `v1.0.0`                                                                                                                                                         |
| Trigger Condition | Fuzzy matching detects a potential sanctions match.                                                                                                              |
| Payload           | `{ "screening_id": "...", "entity_name": "...", "matched_entry": "...", "match_score": 0.87, "list_source": "OFAC_SDN", "transaction_id": "...", "held": true }` |
| Consumers         | Compliance Dashboard, K-06 Alerting, D-07 Compliance                                                                                                             |
| Idempotency Key   | `hash(entity_name + matched_entry + transaction_id)`                                                                                                             |
| Replay Behavior   | Re-creates the match for review.                                                                                                                                 |
| Retention Policy  | Permanent.                                                                                                                                                       |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                              |
| ---------------- | ------------------------------------------------------------------------ |
| Command Name     | `ScreenEntityCommand`                                                    |
| Schema Version   | `v1.0.0`                                                                 |
| Validation Rules | Entity details provided, sanctions lists available, requester authorized |
| Handler          | `ScreeningCommandHandler` in D-14                                        |
| Success Event    | `ScreeningCompleted`                                                     |
| Failure Event    | `ScreeningFailed`                                                        |
| Idempotency      | Screening ID must be unique; duplicate commands return cached result     |

| Field            | Description                                                            |
| ---------------- | ---------------------------------------------------------------------- |
| Command Name     | `ReviewMatchCommand`                                                   |
| Schema Version   | `v1.0.0`                                                               |
| Validation Rules | Match exists, reviewer authorized (compliance role), decision provided |
| Handler          | `MatchReviewHandler` in D-14                                           |
| Success Event    | `MatchReviewed`                                                        |
| Failure Event    | `MatchReviewFailed`                                                    |
| Idempotency      | Command ID must be unique                                              |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Pattern Recognition / NLP
- **Workflow Steps Exposed:** Entity name matching and alias resolution.
- **Model Registry Usage:** `sanctions-name-matcher-v1`
- **Explainability Requirement:** AI must explain why a match was flagged (matching features, score breakdown).
- **Human Override Path:** Compliance officer makes final match/no-match decision.
- **Drift Monitoring:** False positive rate monitored; retrain if > 10%.
- **Fallback Behavior:** Rule-based fuzzy matching (Levenshtein + phonetic).

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                                     |
| ------------------------- | ---------------------------------------------------------------------------------------------------- |
| Latency / Throughput      | Real-time screening P99 < 50ms; batch screening 100K entities in < 1 hour                            |
| Scalability               | Horizontally scalable screening workers                                                              |
| Availability              | 99.999% uptime (critical compliance path)                                                            |
| Consistency Model         | Strong consistency for screening decisions                                                           |
| Security                  | Sanctions lists encrypted at rest; screening results access-restricted                               |
| Data Residency            | Screening results follow K-08 residency rules                                                        |
| Data Retention            | Screening records retained per audit policy (minimum 10 years)                                       |
| Auditability              | All screening decisions logged to K-07 [LCA-AUDIT-001]                                               |
| Observability             | Metrics: `screening.latency`, `screening.match.rate`, `screening.list.staleness`, `screening.volume` |
| Extensibility             | New list sources via T3 Adapter Packs                                                                |
| Upgrade / Compatibility   | Backward compatible screening API                                                                    |
| On-Prem Constraints       | Local cache with offline list bundles                                                                |
| Ledger Integrity          | N/A                                                                                                  |
| Dual-Calendar Correctness | All timestamps use DualDate                                                                          |

---

#### Section 10 — Acceptance Criteria

1. **Given** a client named "John Smith", **When** screened against OFAC SDN containing "Jon Smyth", **Then** a potential match is flagged with match score > 0.8 and the transaction is held.
2. **Given** an updated OFAC list, **When** batch screening runs, **Then** existing client "ABC Corp" newly matching a sanctioned entity triggers an alert and account review.
3. **Given** an air-gapped deployment with a 12-hour-old sanctions list, **When** the staleness threshold (24h) is not exceeded, **Then** screening proceeds normally.
4. **Given** a compliance officer reviews a potential match, **When** they mark it as FALSE_POSITIVE, **Then** the held transaction is released and the decision is audited in K-07.
5. **Given** a confirmed sanctions match, **When** the compliance officer marks TRUE_MATCH, **Then** a second compliance officer must approve (maker-checker) and the account is blocked.

---

#### Section 11 — Failure Modes & Resilience

- **Sanctions Provider Unavailable:** Screen against local cache; alert raised if cache staleness > threshold. Screening continues in degraded mode.
- **Screening Service Down:** All transactions requiring screening are held pending; circuit breaker prevents cascading failure. CRITICAL alert raised.
- **Cache Corruption:** Full list re-download triggered; screening pauses until cache rebuilt.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------------------- |
| Metrics             | `screening.latency.p99`, `screening.match.count`, `screening.clear.count`, `list.staleness.hours` |
| Logs                | Structured: `screening_id`, `entity`, `result`, `match_score`                                     |
| Traces              | Span per screening operation                                                                      |
| Audit Events        | `ScreeningCompleted`, `MatchReviewed`, `ListUpdated` [LCA-AUDIT-001]                              |
| Regulatory Evidence | Screening records for AML/sanctions compliance audit [LCA-SANCTIONS-001]                          |

---

#### Section 13 — Compliance & Regulatory Traceability

- Sanctions compliance [LCA-SANCTIONS-001]
- AML/CFT requirements [LCA-AMLKYC-001]
- Audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `SanctionsClient.screen(entity)`, `SanctionsClient.reviewMatch(matchId, decision)`, `SanctionsClient.getListStatus()`.
- **Jurisdiction Plugin Extension Points:** T3 Adapter Packs for list providers; T1 Config Packs for thresholds and list selection.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                                                    |
| ---------------------------------------------------- | ------------------------------------------------------------------ |
| Can this module support India/Bangladesh via plugin? | Yes, via jurisdiction-specific list sources and threshold configs. |
| Can new sanctions lists be added?                    | Yes, via T3 Adapter Packs.                                         |
| Can this run in an air-gapped deployment?            | Yes, with offline signed list bundles.                             |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Sanctions Match Evasion**

- **Threat:** Counterparties exploit aliases, transliterations, or sparse identifiers to avoid detection.
- **Mitigation:** Configurable fuzzy matching; alias expansion; multiple identifier comparisons; periodic batch re-screening; human review for borderline scores.
- **Residual Risk:** Novel alias structures not yet represented in source lists.

2. **List Freshness Degradation**

- **Threat:** Stale or failed list updates allow newly sanctioned entities to transact.
- **Mitigation:** Freshness monitoring with alerts; signed update bundles; degraded-mode thresholds; offline bundle validation for air-gapped environments; audit evidence of active list version.
- **Residual Risk:** Extended provider outage across all configured sources.

3. **False Positive Abuse**

- **Threat:** Excessive false positives create operational pressure to weaken controls or auto-clear risky cases.
- **Mitigation:** Match-review workflow with evidence capture; threshold tuning via governed config; AI explainability for score drivers; maker-checker for confirmed-match actions.
- **Residual Risk:** Review fatigue during high-volume screening periods.

4. **Screening Decision Tampering**

- **Threat:** A reviewer alters screening outcomes to release blocked entities improperly.
- **Mitigation:** Immutable K-07 audit trail; dual approval for true-match outcomes; reviewer attribution retained with timestamps; case-history reconstruction for all overrides.
- **Residual Risk:** Collusion among privileged reviewers.

5. **Sanctions Data Exfiltration**

- **Threat:** Attackers steal cached list data or screening results to infer control coverage or sensitive investigations.
- **Mitigation:** Encryption at rest; access-restricted screening stores; masked operational views where possible; network segmentation; access logging and anomaly detection.
- **Residual Risk:** Insider extraction of legitimate exports.

**Security Controls:**

- Signed sanctions-list ingestion and freshness monitoring
- Fuzzy matching with governed thresholds
- Maker-checker for confirmed-match blocking actions
- Immutable audit trail of screening decisions
- Encryption of cached lists and screening results
- RBAC for compliance reviewers
- Air-gap bundle validation controls

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Registered sanctions-screening traceability under the compliance code registry.
- Added changelog metadata for future epic maintenance.
