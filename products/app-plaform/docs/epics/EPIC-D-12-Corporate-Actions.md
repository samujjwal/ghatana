EPIC-ID: EPIC-D-12
EPIC NAME: Corporate Actions
LAYER: DOMAIN
MODULE: D-12 Corporate Actions
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the D-12 Corporate Actions module, responsible for processing bonus shares, rights issues, dividends, mergers, and other corporate events. This epic implements Principle 5 (All Tax Rules are Plugin-Driven) and Principle 2 (Full Externalization) by ensuring that entitlement calculations use jurisdiction-specific rules from T2 Rule Packs, while the core engine provides the event processing framework.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Corporate action event ingestion and lifecycle management.
  2. Entitlement calculation engine.
  3. Integration with K-16 Ledger for position updates.
  4. Integration with T3 Depository Adapters for share credits.
  5. Tax withholding calculation via T2 Tax Rule Packs.
- **Out-of-Scope:**
  1. The actual tax rates (e.g., Nepal 5% TDS on dividends) - these are T2 Rule Packs.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework)
- **Kernel Readiness Gates:** K-02, K-03, K-05, K-07, K-15, K-16
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Event Capture:** The module must ingest corporate action announcements (from issuers or external feeds) and create a `CorporateActionAnnounced` event.
2. **FR2 Record Date Processing:** The module must determine eligible shareholders as of the record date (using K-16 position snapshots).
3. **FR3 Entitlement Calculation:** The module must calculate entitlements (e.g., bonus shares, dividend amounts) using T2 Rule Packs for jurisdiction-specific logic.
4. **FR4 Tax Withholding:** The module must invoke K-03 to calculate TDS/withholding tax per T2 Tax Rule Packs.
5. **FR5 Ledger Posting:** The module must post the final net entitlements to K-16 Ledger.
6. **FR6 Dual-Calendar:** Record dates, ex-dates, and payment dates must use `DualDate`.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The event processing, entitlement math framework, and ledger posting are generic.
2. **Jurisdiction Plugin:** Specific rules (e.g., Nepal bonus share ratio calculations, TDS rates) are T2 Rule Packs.
3. **Resolution Flow:** Config Engine determines which rules apply based on the issuer's jurisdiction.
4. **Hot Reload:** New corporate action types can be added via Config Packs.
5. **Backward Compatibility:** Historical corporate actions retain the rules active at processing time.
6. **Future Jurisdiction:** A new country's dividend tax rules are simply a new T2 pack.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `CorporateAction`: `{ ca_id: UUID, issuer_id: UUID, type: Enum, record_date: DualDate, ex_date: DualDate, details: JSON }`
  - `Entitlement`: `{ entitlement_id: UUID, ca_id: UUID, account_id: UUID, gross_amount: Decimal, tax_withheld: Decimal, net_amount: Decimal }`
- **Dual-Calendar Fields:** `record_date`, `ex_date`, `payment_date`.
- **Event Schema Changes:** `CorporateActionAnnounced`, `EntitlementCalculated`, `DividendPaid`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                               |
| ----------------- | --------------------------------------------------------------------------------------------------------- |
| Event Name        | `EntitlementCalculated`                                                                                   |
| Schema Version    | `v1.0.0`                                                                                                  |
| Trigger Condition | Entitlements for a corporate action are finalized.                                                        |
| Payload           | `{ "ca_id": "...", "account_id": "...", "gross_amount": 10000, "tax_withheld": 500, "net_amount": 9500 }` |
| Consumers         | Ledger (K-16), Client Notification, Tax Reporting                                                         |
| Idempotency Key   | `hash(ca_id + account_id)`                                                                                |
| Replay Behavior   | Updates entitlement views.                                                                                |
| Retention Policy  | Permanent.                                                                                                |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `AnnounceCorporateActionCommand`                                     |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Instrument exists, CA type valid, dates valid, requester authorized  |
| Handler          | `CorporateActionCommandHandler` in D-12 Corporate Actions            |
| Success Event    | `CorporateActionAnnounced`                                           |
| Failure Event    | `CorporateActionAnnouncementFailed`                                  |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                     |
| ---------------- | --------------------------------------------------------------- |
| Command Name     | `CalculateEntitlementCommand`                                   |
| Schema Version   | `v1.0.0`                                                        |
| Validation Rules | CA exists, record date reached, shareholder positions available |
| Handler          | `EntitlementCommandHandler` in D-12 Corporate Actions           |
| Success Event    | `EntitlementCalculated`                                         |
| Failure Event    | `EntitlementCalculationFailed`                                  |
| Idempotency      | Same CA + record date returns cached entitlements               |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `ProcessPaymentCommand`                                              |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Entitlements calculated, payment date reached, funds available       |
| Handler          | `PaymentCommandHandler` in D-12 Corporate Actions                    |
| Success Event    | `PaymentProcessed`                                                   |
| Failure Event    | `PaymentProcessingFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist
- **Workflow Steps Exposed:** Entitlement reconciliation.
- **Model Registry Usage:** `ca-reconciliation-copilot-v1`
- **Explainability Requirement:** AI assists in reconciling discrepancies between calculated entitlements and external depository records.
- **Human Override Path:** Operator manually adjusts entitlements.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard manual reconciliation.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                   |
| ------------------------- | ------------------------------------------------------------------ |
| Latency / Throughput      | Calculate entitlements for 100,000 accounts in < 5 minutes         |
| Scalability               | Batch processing scaled horizontally                               |
| Availability              | 99.99%                                                             |
| Consistency Model         | Strong consistency for ledger postings                             |
| Security                  | Row-level tenant isolation                                         |
| Data Residency            | Enforced via K-08                                                  |
| Data Retention            | Retain CA history 10 years                                         |
| Auditability              | All calculations logged                                            |
| Observability             | Metrics: `ca.processing.duration`, `ca.reconciliation.break_count` |
| Extensibility             | New CA types via T2 Packs                                          |
| Upgrade / Compatibility   | N/A                                                                |
| On-Prem Constraints       | Fully functional locally                                           |
| Ledger Integrity          | Posts to K-16                                                      |
| Dual-Calendar Correctness | Correct record date handling                                       |

---

#### Section 10 — Acceptance Criteria

1. **Given** a bonus share announcement (1:5 ratio), **When** processed for an account holding 100 shares, **Then** D-12 calculates an entitlement of 20 bonus shares and emits `EntitlementCalculated`.
2. **Given** a cash dividend with Nepal T2 Tax Pack (5% TDS), **When** calculated for Rs 10,000 gross, **Then** the net entitlement is Rs 9,500.
3. **Given** a corporate action with a BS record date, **When** determining eligible shareholders, **Then** D-12 queries K-16 for positions as of that exact dual-calendar date.

---

#### Section 11 — Failure Modes & Resilience

- **Depository Confirmation Delay:** Entitlements calculated and queued; posted to ledger upon confirmation.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                 |
| ------------------- | ------------------------------------------------ |
| Metrics             | `ca.entitlement.count`, `ca.tax.total`           |
| Logs                | Calculation errors                               |
| Traces              | Span `CorporateActions.process`                  |
| Audit Events        | Action: `ApproveEntitlement`, `ManualAdjustment` |
| Regulatory Evidence | CA processing audit trail.                       |

---

#### Section 13 — Compliance & Regulatory Traceability

- Tax withholding compliance [LCA-TAX-001]
- Audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

**SDK Methods (Platform SDK):**

```
CorpActionClient.announceCorporateAction(payload: CAAnnouncementPayload): CAAnnouncement
CorpActionClient.calculateEntitlements(caId: string): EntitlementResult
CorpActionClient.getCASchedule(jurisdiction: string, dateRange: DateRange): CASchedule[]
CorpActionClient.processPayment(caId: string): PaymentResult
```

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `CorporateActionAnnounced` | New CA registered | `ca_id`, `ca_type`, `instrument_id`, `record_date_bs`, `ex_date_bs` |
| `EntitlementCalculated` | Entitlements computed from ledger positions | `ca_id`, `entitlement_count`, `total_amount` |
| `CorporateActionProcessed` | CA fully settled | `ca_id`, `processed_date_bs`, `ledger_entries_count` |
| `DividendPaid` | Dividend payments disbursed | `ca_id`, `payment_batch_id`, `total_disbursed` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `LedgerEntryPosted` | K-16 Ledger | Position data for entitlement calculation |
| `RulePackActivated` | K-03 Rules Engine | Updated tax/CA rules |
| `MarketDataUpdated` | D-04 Market Data | Instrument price for valuations |

**Jurisdiction Plugin Extension Points:** T2 Tax Rule Packs (e.g., Nepal 5% TDS on dividends); T2 CA Calculation Packs (e.g., bonus share ratio rules per exchange).

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                             |
| ---------------------------------------------------- | ------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                                        |
| Can tax rules change without redeploy?               | Yes, via T2 Rule Packs.                     |
| Can this run in an air-gapped deployment?            | Partially; requires external CA data feeds. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Entitlement Calculation Fraud**
   - **Threat:** Attacker manipulates entitlement calculations for financial gain.
   - **Mitigation:** Calculations based on immutable K-16 Ledger positions; T2 Rule Packs with maker-checker; all calculations logged; independent verification.
   - **Residual Risk:** Compromised rule pack author.

2. **Corporate Action Announcement Fraud**
   - **Threat:** False CA announcement causes incorrect entitlements or payments.
   - **Mitigation:** Announcements verified against multiple sources; maker-checker for manual entries; all announcements audited; reconciliation with external registries.
   - **Residual Risk:** Coordinated attack across multiple sources.

3. **Payment Diversion**
   - **Threat:** Attacker diverts dividend or bonus payments to wrong accounts.
   - **Mitigation:** Payment instructions cryptographically signed; maker-checker for manual changes; all payments audited; reconciliation with K-16 Ledger.
   - **Residual Risk:** Compromised signing keys.

4. **Tax Withholding Manipulation**
   - **Threat:** Incorrect tax calculations cause regulatory violations or financial loss.
   - **Mitigation:** Tax rules in T2 Rule Packs with maker-checker; all calculations logged; independent verification; reconciliation with tax authorities.
   - **Residual Risk:** Compromised tax rule pack.

5. **Record Date Manipulation**
   - **Threat:** False record date causes incorrect shareholder identification.
   - **Mitigation:** Record dates verified against external sources; maker-checker for changes; all dates audited; K-15 dual-calendar validation.
   - **Residual Risk:** Compromised external source.

**Security Controls:**

- Immutable ledger integration (K-16)
- Maker-checker for all critical operations
- Multi-source verification
- Cryptographic signing of payments
- T2 rule pack governance
- Audit logging of all operations
- Independent reconciliation

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
