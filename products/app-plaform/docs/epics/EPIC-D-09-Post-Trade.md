EPIC-ID: EPIC-D-09
EPIC NAME: Post-Trade & Settlement
LAYER: DOMAIN
MODULE: D-09 Post-Trade & Settlement
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the D-09 Post-Trade & Settlement module, responsible for trade confirmation, netting, and settlement instructions. This epic implements Principle 3 (Depository Adapters) by ensuring that specific depository interactions (e.g., CDSC APIs, SWIFT messages) are handled by T3 Adapter Packs, while D-09 manages the generic settlement state machine. It integrates tightly with the K-16 Ledger Framework to record the financial impact of settlements.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Trade matching and confirmation processing.
  2. Netting of settlement obligations (cash and securities).
  3. Orchestration of the settlement lifecycle (T+n calculation via K-15).
  4. Integration with K-16 Ledger Framework for final posting.
  5. Exception management (failed trades, short deliveries).
- **Out-of-Scope:**
  1. The actual API calls to CDSC or settlement banks - handled by T3 Adapters.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-16 (Ledger Framework)
- **Kernel Readiness Gates:** K-02, K-03, K-05, K-07, K-15, K-16
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Settlement Cycle Calculation:** The module must determine the settlement date for a trade by querying K-15 `CalendarClient`, using the `settlement_cycle` defined in the Jurisdiction T1 Config Pack (e.g., T+2 for NEPSE).
2. **FR2 Netting Engine:** Aggregate individual `TradeExecuted` events into net settlement obligations per account per settlement date.
3. **FR3 Adapter Orchestration:** Generate standardized settlement instructions and send them to the appropriate T3 Depository Adapter via Event Bus.
4. **FR4 Ledger Posting:** Upon confirmation of settlement from the Adapter, generate the balancing debit/credit entries and post them to the K-16 Ledger Framework.
5. **FR5 Exception Workflow:** If an adapter reports a failure (e.g., missing EDIS), emit a `SettlementFailedEvent` and route it to an operator queue.
6. **FR6 Dual-Calendar Tracking:** Settlement dates must be explicitly tracked using both `timestamp_gregorian` and `timestamp_bs`.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The netting logic, state machine, and ledger posting preparation are generic.
2. **Jurisdiction Plugin:** Settlement cycles (T+2), holiday exclusions, and the actual depository integration (CDSC adapter) are T1 Configs and T3 Adapters.
3. **Resolution Flow:** Config Engine determines the settlement cycle based on `instrument_id` and `jurisdiction`.
4. **Hot Reload:** Changes to settlement rules (e.g., moving from T+3 to T+2) apply to new trades immediately.
5. **Backward Compatibility:** Trades in flight retain their original calculated settlement date.
6. **Future Jurisdiction:** A new depository simply requires a new T3 Adapter.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `SettlementObligation`: `{ obligation_id: UUID, account_id: UUID, asset_id: String, net_qty: Decimal, settlement_date_greg: Date, settlement_date_bs: String, status: Enum }`
- **Dual-Calendar Fields:** `settlement_date_bs` in `SettlementObligation`.
- **Event Schema Changes:** `ObligationCalculated`, `SettlementCompleted`, `SettlementFailed`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                            |
| ----------------- | -------------------------------------------------------------------------------------- |
| Event Name        | `SettlementCompleted`                                                                  |
| Schema Version    | `v1.0.0`                                                                               |
| Trigger Condition | T3 Adapter confirms the settlement, and D-09 successfully posts it to the K-16 Ledger. |
| Payload           | `{ "obligation_id": "...", "status": "SETTLED", "settlement_date_bs": "..." }`         |
| Consumers         | OMS (unlock funds/shares), Regulatory Reporting, Client Notification                   |
| Idempotency Key   | `hash(obligation_id + status)`                                                         |
| Replay Behavior   | Updates the read model of obligation status.                                           |
| Retention Policy  | Permanent.                                                                             |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                   |
| ---------------- | ------------------------------------------------------------- |
| Command Name     | `CalculateObligationCommand`                                  |
| Schema Version   | `v1.0.0`                                                      |
| Validation Rules | Trade exists, settlement cycle configured, counterparty valid |
| Handler          | `SettlementCommandHandler` in D-09 Post-Trade                 |
| Success Event    | `ObligationCalculated`                                        |
| Failure Event    | `ObligationCalculationFailed`                                 |
| Idempotency      | Same trade returns cached obligation                          |

| Field            | Description                                                            |
| ---------------- | ---------------------------------------------------------------------- |
| Command Name     | `SettleCommand`                                                        |
| Schema Version   | `v1.0.0`                                                               |
| Validation Rules | Obligation exists, settlement date reached, funds/securities available |
| Handler          | `SettlementCommandHandler` in D-09 Post-Trade                          |
| Success Event    | `SettlementCompleted`                                                  |
| Failure Event    | `SettlementFailed`                                                     |
| Idempotency      | Command ID must be unique; duplicate commands return original result   |

| Field            | Description                                                                       |
| ---------------- | --------------------------------------------------------------------------------- |
| Command Name     | `HandleExceptionCommand`                                                          |
| Schema Version   | `v1.0.0`                                                                          |
| Validation Rules | Settlement failure exists, exception type identified, resolution action specified |
| Handler          | `ExceptionHandler` in D-09 Post-Trade                                             |
| Success Event    | `ExceptionResolved`                                                               |
| Failure Event    | `ExceptionResolutionFailed`                                                       |
| Idempotency      | Command ID must be unique; duplicate commands return original result              |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Predictive Model
- **Workflow Steps Exposed:** Settlement failure prediction.
- **Model Registry Usage:** `settlement-fail-predictor-v1`
- **Explainability Requirement:** AI predicts if a trade is likely to fail settlement (e.g., missing EDIS authorization patterns) and flags it for early intervention.
- **Human Override Path:** Operator can manually force an early buy-in or override the alert.
- **Drift Monitoring:** Prediction accuracy tracked against actual fails.
- **Fallback Behavior:** Standard reactive failure handling.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                         |
| ------------------------- | -------------------------------------------------------- |
| Latency / Throughput      | Netting calculation < 100ms for a batch of 10,000 trades |
| Scalability               | Batch processing scaled horizontally                     |
| Availability              | 99.99%                                                   |
| Consistency Model         | Strong consistency for ledger postings                   |
| Security                  | Row-level tenant isolation                               |
| Data Residency            | Enforced via K-08                                        |
| Data Retention            | Retain settlement history 10 years                       |
| Auditability              | All manual interventions logged                          |
| Observability             | Metrics: `settlement.fail.rate`, `netting.duration`      |
| Extensibility             | Connect new depository via T3                            |
| Upgrade / Compatibility   | N/A                                                      |
| On-Prem Constraints       | Runs batch jobs locally                                  |
| Ledger Integrity          | Posts directly to K-16                                   |
| Dual-Calendar Correctness | Correct T+n calculations                                 |

---

#### Section 10 — Acceptance Criteria

1. **Given** 10 buys and 5 sells of the same ISIN by the same account today, **When** the EOD netting job runs, **Then** it creates a single `SettlementObligation` for the net 5 buys.
2. **Given** a trade executed on Sunday, **When** calculating settlement date against a Nepal T1 Calendar Pack (Sun-Thu trading, T+2 cycle), **Then** it accurately calculates Tuesday as the dual-calendar settlement date.
3. **Given** a confirmation from the CDSC adapter, **When** processed, **Then** D-09 sends a balanced debit/credit instruction to K-16 Ledger and emits `SettlementCompleted`.

---

#### Section 11 — Failure Modes & Resilience

- **Ledger K-16 Unreachable:** D-09 retries posting with exponential backoff; keeps obligation in `PENDING_POST` state.
- **Adapter Down:** Obligation marked as `PENDING_INSTRUCTION`; alerts operations.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                    |
| ------------------- | --------------------------------------------------- |
| Metrics             | `settlement.success.count`, `settlement.fail.count` |
| Logs                | Netting errors                                      |
| Traces              | Span `Settlement.process`                           |
| Audit Events        | Action: `ManualSettle`, `OverrideFail`              |
| Regulatory Evidence | Settlement logs for depository reconciliation.      |

---

#### Section 13 — Compliance & Regulatory Traceability

- Clearing and Settlement tracking [ASR-OPS-001]
- Asset segregation verification [LCA-SEG-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `PostTradeClient.getSettlementStatus(tradeId)`, `PostTradeClient.initiateSettlement(tradeId)`, `PostTradeClient.getSettlementSchedule(date_bs)`.
- **Jurisdiction Plugin Extension Points:** T3 Depository Adapters (e.g., CDS&C Nepal, NSDL/CDSL India).

**K-05 Events Emitted (standard envelope compliant):**
| Event Type | Trigger | Key Payload Fields |
|---|---|---|
| `SettlementInstructionCreated` | Trade matched for settlement | `trade_id`, `settlement_date_bs`, `depository`, `instruction_type` |
| `SettlementCompleted` | Funds/securities delivered | `trade_id`, `settlement_id`, `completed_date_bs` |
| `SettlementFailed` | Settlement could not complete | `trade_id`, `failure_reason`, `retry_count` |

**K-05 Events Consumed:**
| Event Type | Source | Purpose |
|---|---|---|
| `TradeExecuted` | D-01 OMS | Trigger settlement processing |
| `LedgerEntryPosted` | K-16 Ledger | Verify fund availability |
| `TradingDayEnded` | K-15 Dual-Calendar | End-of-day settlement batch |

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                              |
| ---------------------------------------------------- | -------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes, via new T3 Adapters (NSDL/CDSL).        |
| Can settlement cycle change without redeploy?        | Yes, handled via T1 Config Pack.             |
| Can this run in an air-gapped deployment?            | Partially; requires depository connectivity. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Settlement Instruction Manipulation**
   - **Threat:** Attacker modifies settlement instructions to redirect funds/securities.
   - **Mitigation:** Cryptographic signing of instructions; maker-checker for manual changes; all instructions audited; reconciliation with trade data.
   - **Residual Risk:** Compromised signing keys.

2. **Depository Adapter Compromise**
   - **Threat:** Malicious T3 Depository Adapter exfiltrates data or manipulates settlements.
   - **Mitigation:** K-04 sandbox isolation; cryptographic signing; network access restrictions; all adapter calls logged; reconciliation checks.
   - **Residual Risk:** Supply chain attack on adapter.

3. **Settlement Failure Fraud**
   - **Threat:** False settlement failure claims to avoid obligations.
   - **Mitigation:** Independent verification with depository; immutable ledger posting (K-16); all failures audited; exception workflow with maker-checker.
   - **Residual Risk:** Collusion with depository personnel.

4. **Netting Manipulation**
   - **Threat:** Attacker manipulates netting calculations for financial gain.
   - **Mitigation:** Netting algorithms audited; all calculations logged; independent verification; immutable trade data from K-05.
   - **Residual Risk:** Sophisticated algorithm exploitation.

5. **Ledger Posting Fraud**
   - **Threat:** False ledger postings to misrepresent settlement status.
   - **Mitigation:** K-16 Ledger immutability; double-entry validation; reconciliation with external depository; all postings audited.
   - **Residual Risk:** Coordinated attack across multiple systems.

**Security Controls:**

- Cryptographic signing of instructions
- Maker-checker for exceptions
- T3 adapter sandboxing
- Immutable ledger integration (K-16)
- Independent reconciliation
- Audit logging of all operations
- Network segmentation for depository connectivity

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
