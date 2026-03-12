EPIC-ID: EPIC-K-16
EPIC NAME: Ledger Framework
LAYER: KERNEL
MODULE: K-16 Ledger Framework
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the K-16 Ledger Framework, an immutable, double-entry ledger primitive shared across all domain modules requiring financial tracking (Post-Trade, PMS, Corp Actions, Tax, Fees). This module fulfills the Event-Sourced Principle by ensuring all financial state changes are immutable events, and prevents domain modules from implementing fragmented, siloed accounting logic.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Double-entry bookkeeping engine (debits/credits must always balance).
  2. Immutable, event-sourced ledger entries.
  3. Built-in reconciliation tooling for comparing ledger state against external sources (e.g., Bank, CDSC).
  4. Jurisdiction-configurable chart of accounts and currency handling.
  5. API for temporal balance queries (`getBalance(accountId, asOf)`).
- **Out-of-Scope:**
  1. Business rules governing _why_ a posting occurs (handled by domain modules and K-03 Rules Engine).
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Double-Entry Enforcement:** The engine must reject any posting transaction where the sum of debits does not exactly equal the sum of credits.
2. **FR2 Immutability:** Ledger entries are strictly append-only. Corrections require explicit compensating posting entries.
3. **FR3 Temporal Queries:** The API must support querying the exact balance of any account as of any historical timestamp (Gregorian or BS).
4. **FR4 Multi-Currency & Asset Support:** The ledger must support tracking multiple currencies (e.g., NPR, USD) and asset quantities (e.g., kitta of ISIN) using configurable precision.
5. **FR5 Reconciliation Engine:** Provide APIs to ingest external statements, match them against internal postings based on configured rules, and identify breaks.
6. **FR6 Configurable Chart of Accounts (CoA):** The structure of the ledger (account types, hierarchies) must be loaded dynamically via K-02 Config Engine.
7. **FR7 Event Emission:** Emit `LedgerPostedEvent` upon successful transaction commit.
8. **FR8 Precision & Rounding Rules:** All monetary amounts must use fixed-point arithmetic with configurable precision per currency/asset (loaded via K-02 Config Engine). Defaults: (a) NPR: 4 decimal places, (b) USD/EUR: 2 decimal places, (c) Crypto assets: 8 decimal places, (d) Securities quantities (kitta): 0 decimal places. Rounding mode: HALF_EVEN (Banker's rounding). The ledger must reject any transaction where amounts exceed the configured precision. A nightly balance verification job must recompute all account balances from event history and compare against cached balances; any discrepancy > 0 triggers a CRITICAL alert and `LedgerImbalanceDetectedEvent`. [ARB P0-03]
9. **FR9 Temporal Query Performance:** The `getBalance(accountId, asOf)` API must use pre-computed periodic snapshots (hourly and daily) to avoid full event replay. Snapshots are computed as materialized views. For any `asOf` query, the engine replays events only from the nearest snapshot forward. Target: temporal query P99 < 50ms for accounts with up to 1M historical events. Snapshot computation is a background job that does not block real-time operations. [ARB D.7]
10. **FR10 Idempotency Collision Handling:** When a `PostTransactionCommand` is received with a `transaction_id` that already exists: (a) if the payload hash matches the existing transaction, return the original result (idempotent success), (b) if the payload hash differs, reject with `DuplicateTransactionIdError` and log a security event — this indicates either a bug or an attempted replay attack. [ARB D.4]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The double-entry math, query engine, and reconciliation matching logic are purely generic.
2. **Jurisdiction Plugin:** The Chart of Accounts structure, rounding rules, and specific external reconciliation formats (e.g., CDSC recon format) are defined in Jurisdiction Config/Adapter Packs.
3. **Resolution Flow:** Config Engine supplies the CoA structure per tenant/jurisdiction.
4. **Hot Reload:** Additions to the CoA are hot-reloaded.
5. **Backward Compatibility:** Historical postings remain valid even if an account is later marked inactive.
6. **Future Jurisdiction:** A new country implies a new CoA config and potentially new reconciliation adapters, but zero core ledger changes.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `LedgerTransaction`: `{ transaction_id: UUID, posting_date_greg: Timestamp, posting_date_bs: String, source_module: String, entries: List<LedgerEntry> }`
  - `LedgerEntry`: `{ account_id: String, asset_id: String, amount: Decimal, type: Enum(DEBIT, CREDIT) }`
- **Dual-Calendar Fields:** `posting_date_bs` is stored natively to allow exact fiscal-year reporting based on Bikram Sambat.
- **Event Schema Changes:** `LedgerPostedEvent`, `ReconciliationBreakDetectedEvent`, `LedgerImbalanceDetectedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                               |
| ----------------- | ------------------------------------------------------------------------- |
| Event Name        | `LedgerPostedEvent`                                                       |
| Schema Version    | `v1.0.0`                                                                  |
| Trigger Condition | A balanced transaction is successfully committed to the ledger.           |
| Payload           | `{ "transaction_id": "...", "posting_date_bs": "...", "entries": [...] }` |
| Consumers         | Regulatory Reporting (D-10), Trade Surveillance (D-08), Audit Framework   |
| Idempotency Key   | `hash(source_event_id)` (The ID of the event that triggered the posting)  |
| Replay Behavior   | Suppressed for side-effects; rebuilds read-model balances.                |
| Retention Policy  | Permanent.                                                                |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                                                                                                                                          |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Command Name     | `PostTransactionCommand`                                                                                                                                                             |
| Schema Version   | `v1.0.0`                                                                                                                                                                             |
| Validation Rules | Transaction balanced (debits = credits), accounts exist, requester authorized                                                                                                        |
| Handler          | `LedgerCommandHandler` in K-16 Ledger Framework                                                                                                                                      |
| Success Event    | `LedgerPostedEvent`                                                                                                                                                                  |
| Failure Event    | `LedgerPostFailed`                                                                                                                                                                   |
| Idempotency      | Transaction ID must be unique; duplicate commands with matching payload return original result; duplicate commands with different payload are rejected with security event [ARB D.4] |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `ReconcileCommand`                                                   |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Account exists, external source specified, requester authorized      |
| Handler          | `ReconciliationHandler` in K-16 Ledger Framework                     |
| Success Event    | `ReconciliationCompleted`                                            |
| Failure Event    | `ReconciliationFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CreateAccountCommand`                                               |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Account number unique, CoA entry exists, requester authorized        |
| Handler          | `AccountCommandHandler` in K-16 Ledger Framework                     |
| Success Event    | `AccountCreated`                                                     |
| Failure Event    | `AccountCreationFailed`                                              |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Reconciliation break management.
- **Model Registry Usage:** `recon-break-analyzer-v1`
- **Explainability Requirement:** AI analyzes unmatched reconciliation breaks, suggests potential matches (fuzzy matching), and explains the reasoning.
- **Human Override Path:** Operator manually confirms or rejects the AI's suggested match.
- **Drift Monitoring:** Success rate of suggested matches is tracked.
- **Fallback Behavior:** Standard exact-match rule engine.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                    |
| ------------------------- | --------------------------------------------------- |
| Latency / Throughput      | Posting commit < 5ms; 10,000 TPS                    |
| Scalability               | Partitioned by `tenant_id` and `account_id`         |
| Availability              | 99.999% uptime                                      |
| Consistency Model         | Strong consistency for posting transactions         |
| Security                  | Row-level security per tenant                       |
| Data Residency            | Ledger data stored strictly in-jurisdiction         |
| Data Retention            | Permanent                                           |
| Auditability              | Immutable by design                                 |
| Observability             | Metrics: `ledger.post.latency`, `recon.break.count` |
| Extensibility             | Custom assets and currencies via Config             |
| Upgrade / Compatibility   | Precision changes handled carefully                 |
| On-Prem Constraints       | Backed by local relational/event-store DB           |
| Ledger Integrity          | Guaranteed mathematically on every write            |
| Dual-Calendar Correctness | Queries can use `asOfBsDate` directly               |

---

#### Section 10 — Acceptance Criteria

1. **Given** a posting request with $100 Debit and $99 Credit, **When** submitted to the Ledger SDK, **Then** it is synchronously rejected with an `UnbalancedTransactionError`.
2. **Given** a historical BS date inquiry, **When** `getBalance(account, "2081-04-15")` is called, **Then** the engine reconstructs and returns the exact balance as of that specific dual-calendar date.
3. **Given** an external CDSC position file is uploaded, **When** the recon engine processes it against internal ledger positions, **Then** it accurately flags a 10 kitta discrepancy and emits a `ReconciliationBreakDetectedEvent`.

---

#### Section 11 — Failure Modes & Resilience

- **Concurrent Modification:** Optimistic concurrency control (OCC) handles rapid writes to the same account; SDK auto-retries on OCC failure.
- **Event Bus Outage:** Write to local DB succeeds; outbox pattern ensures eventual emission of `LedgerPostedEvent`.
- **Balance Discrepancy Detected:** CRITICAL alert; affected accounts flagged for investigation; all new transactions on affected accounts blocked until resolved. [ARB P0-03]
- **Precision Overflow:** Transaction rejected synchronously with `PrecisionOverflowError`; caller must adjust amounts to configured precision.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                    |
| ------------------- | --------------------------------------------------- |
| Metrics             | `ledger.transaction.volume`, `recon.match.rate`     |
| Logs                | Exceptions on unbalanced postings                   |
| Traces              | Span `LedgerClient.post`                            |
| Audit Events        | Financial source of truth                           |
| Regulatory Evidence | Core data for all financial audits [LCA-AUDIT-001]. |

---

#### Section 13 — Compliance & Regulatory Traceability

- Client asset segregation [LCA-SEG-001]
- Audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `LedgerClient.post(transaction)` → `LedgerPostedEvent`, `LedgerClient.getBalance(accountId, asOf)` → `BalanceResponse`, `LedgerClient.reconcile(source, file)` → `ReconciliationResult`
- **Jurisdiction Plugin Extension Points:**
  - Chart of Accounts structure via T1 Config Pack
  - Reconciliation format adapters (e.g., CDSC, CCIL) via T3 Executable Pack
  - Rounding rules and precision per currency via T1 Config Pack
  - Tax calculation hooks for post-trade via T2 Rule Pack
- **Events Emitted:** `LedgerPostedEvent`, `ReconciliationBreakDetectedEvent`, `LedgerImbalanceDetectedEvent`, `AccountCreated`, `ReconciliationCompleted` — all conform to K-05 standard envelope (`event_id`, `event_type`, `aggregate_id`, `aggregate_type`, `sequence_number`, `timestamp`, `timestamp_bs`, `payload`)
- **Events Consumed:** `TradeSettled` (from D-09), `CorporateActionProcessed` (from D-12), `FeeCalculated` (from D-09/D-05), `ConfigUpdated` (from K-02)
- **Webhook Extension Points:** `POST /webhooks/ledger-events` for external accounting system integration

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                               | Expected Answer                                                                         |
| ------------------------------------------------------ | --------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?   | Yes — CoA is T1-configurable; reconciliation adapters are T3 plugins; zero core changes |
| Can a new instrument type be added?                    | Yes — `asset_id` is generic; precision rules configurable per asset type via T1         |
| Can this run in an air-gapped deployment?              | Yes — backed by local relational/event-store DB; no external dependencies               |
| Can digital assets/tokenized securities be supported?  | Yes — asset model is generic; crypto precision (8 decimals) already configurable        |
| Can CBDC settlement be integrated?                     | Yes — new settlement adapter (T3) and currency config (T1); core ledger unchanged       |
| Can multi-currency cross-border settlement be handled? | Yes — ledger supports multiple currencies with FX rates via T1 config                   |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Ledger Tampering / Data Manipulation**
   - **Threat:** Unauthorized modification of ledger entries to hide fraudulent transactions or alter balances
   - **Mitigation:** Append-only event store with cryptographic hash chain; no UPDATE/DELETE operations; nightly integrity verification job compares computed vs. cached balances
   - **Residual Risk:** Compromised DB admin could theoretically modify underlying storage; mitigated by K-07 audit logging of all DB operations and segregation of duty

2. **Double-Posting (Replay Attack)**
   - **Threat:** Attacker replays a legitimate `PostTransactionCommand` to duplicate financial transactions
   - **Mitigation:** FR10 idempotency enforcement — duplicate `transaction_id` with different payload triggers `DuplicateTransactionIdError` and security alert; matching payload returns idempotent success
   - **Residual Risk:** None — transaction_id uniqueness is enforced at DB constraint level

3. **Precision Manipulation**
   - **Threat:** Attacker submits transactions with computed rounding errors to accumulate fractional amounts ("salami slicing")
   - **Mitigation:** Fixed-point arithmetic with configurable precision per currency; HALF_EVEN rounding; nightly balance verification detects cumulative discrepancies > 0
   - **Residual Risk:** Rounding within configured precision is mathematically bounded

4. **Unauthorized Account Creation**
   - **Threat:** Creating phantom accounts to funnel or hide money
   - **Mitigation:** Maker-checker approval for account creation; CoA structure must match T1 config; K-01 IAM RBAC with fine-grained permissions
   - **Residual Risk:** Collusion between maker and checker; mitigated by segregation of duty policies and K-08 surveillance

5. **Reconciliation Data Injection**
   - **Threat:** Injecting false external statements to mask reconciliation breaks
   - **Mitigation:** External statement files must come from verified sources (signed uploads); reconciliation format adapters (T3) validate file structure and source; all recon activities audited
   - **Residual Risk:** Compromised external source; mitigated by cross-verification with multiple sources

6. **Balance Query Information Leakage**
   - **Threat:** Unauthorized users querying balances across tenant boundaries
   - **Mitigation:** Row-level security (RLS) per tenant; `getBalance()` API enforces tenant isolation at query layer; K-01 IAM authorization check on every query
   - **Residual Risk:** None within application layer; DB-level RLS provides defense-in-depth

7. **Denial of Service via Transaction Volume**
   - **Threat:** Flooding the ledger with valid but excessive transactions to degrade performance
   - **Mitigation:** Rate limiting per tenant at K-11 API Gateway; circuit breaker on ledger service; account-level transaction velocity alerts
   - **Residual Risk:** Legitimate high-volume clients during market events; mitigated by auto-scaling and priority queues

**Security Controls:**

- Append-only event store — no UPDATE/DELETE at any layer
- Cryptographic hash chain for ledger integrity verification
- Row-level security (RLS) per tenant
- K-01 IAM RBAC with fine-grained permissions
- Maker-checker for account creation and CoA changes
- TLS 1.3 for all API communications
- AES-256-GCM encryption at rest
- Comprehensive audit logging via K-07
- Nightly balance verification with CRITICAL alerting
- Rate limiting and circuit breakers

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
