EPIC-ID: EPIC-D-07
EPIC NAME: Compliance & Controls
LAYER: DOMAIN
MODULE: D-07 Compliance & Controls
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the D-07 Compliance & Controls module, acting as the regulatory gatekeeper for all pre-trade and operational activities. This epic implements Principle 2 (Full Externalization via Jurisdiction Plugins) by ensuring that the core compliance engine contains no hardcoded rules. Instead, it acts as an orchestration layer that fetches T2 Compliance Rule Packs (e.g., SEBON insider trading lists, NRB lock-in rules) and evaluates them via the K-03 Rules Engine.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Pre-trade compliance checks (e.g., restricted lists, lock-in periods).
  2. Maker-checker workflow orchestration for policy violations.
  3. Integration with K-03 Rules Engine for declarative rule execution.
  4. Integration with K-01 IAM for identity-based compliance (KYC/AML status checks).
  5. Audit integration for regulatory rule evaluations.
- **Out-of-Scope:**
  1. Setting the specific compliance rules (e.g., NRB 3-year lock-in) - these belong in T2 Rule Packs.
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Configuration Engine), EPIC-K-03 (Rules Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar), EPIC-K-18 (Resilience Patterns)
- **Note:** D-07 compliance rules are invoked by K-03 as part of the unified pre-trade evaluation pipeline. D-07 does NOT receive direct API calls from D-01 OMS. Instead, D-07 registers its compliance rule modules with K-03, which orchestrates their evaluation. [ARB D.2]
- **Kernel Readiness Gates:** K-01, K-03, K-07
- **Module Classification:** Domain Subsystem

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Rule Orchestration:** The engine must register compliance rule modules with K-03 Rules Engine. When K-03 evaluates a pre-trade request (triggered by D-01 via the unified `EvaluatePreTradeCommand`), it invokes D-07's registered compliance functions to gather context (client identity, instrument attributes) and evaluate against the active Jurisdiction Compliance Pack. D-07 does not directly intercept D-01 requests. [ARB D.2]
2. **FR2 Lock-In Enforcement:** Must enforce complex attribute-based controls (e.g., promoter share lock-in periods based on BS dates).
3. **FR3 AML/KYC Gateway:** Check client `verification_status` via K-01 before allowing trades.
4. **FR4 Maker-Checker Trigger:** If K-03 returns `requires_approval`, route the request to a compliance officer queue.
5. **FR5 Immutable Compliance Log:** Every allow/deny decision must be logged in K-07 with the specific rule version that triggered the result.
6. **FR6 Dual-Calendar Enforcement:** Time-bound rules (e.g., lock-in expiry) must evaluate correctly using `DualDate` values.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The orchestration layer (fetching context and calling K-03) is generic.
2. **Jurisdiction Plugin:** The actual rules (e.g., SEBON vs. NRB lock-in conditions) are T2 Rule Packs.
3. **Resolution Flow:** K-02 Config Engine determines which rule pack applies to the trade.
4. **Hot Reload:** New compliance rules can be loaded dynamically.
5. **Backward Compatibility:** Past trades keep a record of the rules active at their execution.
6. **Future Jurisdiction:** Only requires writing a new T2 Rule Pack in the DSL.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `ComplianceCase`: `{ case_id: UUID, entity_id: UUID, rule_ref: String, status: Enum, created_at: DualDate, resolved_by: String }`
- **Dual-Calendar Fields:** `created_at` uses `DualDate`.
- **Event Schema Changes:** `ComplianceCheckFailed`, `ComplianceApprovalGranted`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                          |
| ----------------- | -------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `ComplianceCheckFailed`                                                                                              |
| Schema Version    | `v1.0.0`                                                                                                             |
| Trigger Condition | An order or operation is blocked by a compliance rule.                                                               |
| Payload           | `{ "resource_id": "...", "rule_pack": "np.sebon.compliance.v2", "reason": "LOCK_IN_ACTIVE", "timestamp_bs": "..." }` |
| Consumers         | OMS, Audit Framework, Admin Portal                                                                                   |
| Idempotency Key   | `hash(resource_id + rule_pack + timestamp)`                                                                          |
| Replay Behavior   | Updates compliance dashboard views.                                                                                  |
| Retention Policy  | Permanent.                                                                                                           |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                 |
| ---------------- | ----------------------------------------------------------- |
| Command Name     | `CheckComplianceCommand`                                    |
| Schema Version   | `v1.0.0`                                                    |
| Validation Rules | Order exists, compliance rules configured, client KYC valid |
| Handler          | `ComplianceCommandHandler` in D-07 Compliance               |
| Success Event    | `ComplianceCheckPassed` or `ComplianceCheckFailed`          |
| Failure Event    | `ComplianceCheckError`                                      |
| Idempotency      | Same order context returns cached check result              |

| Field            | Description                                                               |
| ---------------- | ------------------------------------------------------------------------- |
| Command Name     | `ApproveExceptionCommand`                                                 |
| Schema Version   | `v1.0.0`                                                                  |
| Validation Rules | Exception exists, maker-checker approval obtained, justification provided |
| Handler          | `ExceptionCommandHandler` in D-07 Compliance                              |
| Success Event    | `ExceptionApproved`                                                       |
| Failure Event    | `ExceptionApprovalFailed`                                                 |
| Idempotency      | Command ID must be unique; duplicate commands return original result      |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `BlockClientCommand`                                                 |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Client exists, reason provided, requester authorized                 |
| Handler          | `ClientCommandHandler` in D-07 Compliance                            |
| Success Event    | `ClientBlocked`                                                      |
| Failure Event    | `ClientBlockFailed`                                                  |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist
- **Workflow Steps Exposed:** Maker-checker exception review.
- **Model Registry Usage:** `compliance-copilot-v1`
- **Explainability Requirement:** AI summarizes why a trade was flagged (e.g., "Client is on a newly updated PEP list") and suggests approval/rejection based on historical precedent.
- **Human Override Path:** Compliance officer makes the final decision.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard manual queue review.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                                                                                                            |
| ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Latency / Throughput      | Pre-trade compliance evaluation < 5ms P99 (this is additive to D-01 OMS processing; total pre-trade path including D-06 risk + D-07 compliance via K-03 must be < 10ms P99) |
| Scalability               | Horizontally scalable                                                                                                                                                       |
| Availability              | 99.999% uptime                                                                                                                                                              |
| Consistency Model         | Strong consistency for blocks                                                                                                                                               |
| Security                  | Highest RBAC tier for overrides                                                                                                                                             |
| Data Residency            | Enforced via K-08                                                                                                                                                           |
| Data Retention            | 10 years for compliance logs (SEBON/SEBI cross-jurisdiction safety margin)                                                                                                  |
| Auditability              | Core function [LCA-AUDIT-001]                                                                                                                                               |
| Observability             | Metrics: `compliance.eval.latency`, `compliance.block.rate`                                                                                                                 |
| Extensibility             | New rules via T2 Packs                                                                                                                                                      |
| Upgrade / Compatibility   | N/A                                                                                                                                                                         |
| On-Prem Constraints       | Supported                                                                                                                                                                   |
| Ledger Integrity          | N/A                                                                                                                                                                         |
| Dual-Calendar Correctness | Correct lock-in date evaluations                                                                                                                                            |

---

#### Section 10 — Acceptance Criteria

1. **Given** an order to sell promoter shares locked until BS 2083-04-01, **When** submitted on BS 2082-01-01, **Then** D-07 synchronously rejects the order via K-03 evaluation and logs the denial.
2. **Given** a user whose KYC status is `PENDING`, **When** they attempt to trade, **Then** D-07 blocks the action based on the KYC rule pack.
3. **Given** a trade flagged for maker-checker, **When** the initiating trader tries to approve their own trade, **Then** it is rejected due to segregation of duties.

---

#### Section 11 — Failure Modes & Resilience

- **Rules Engine Down:** Fails closed (blocks all regulated actions) to ensure compliance.
- **Audit Framework Down:** Buffers logs locally; if buffer fills, blocks new actions.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                      |
| ------------------- | ----------------------------------------------------- |
| Metrics             | `compliance.checks.count`, `maker_checker.queue.size` |
| Logs                | Evaluation results                                    |
| Traces              | Span `Compliance.check`                               |
| Audit Events        | Action: `ComplianceOverride`, `ManualApproval`        |
| Regulatory Evidence | Rule evaluation trails [LCA-COMP-001].                |

---

#### Section 13 — Compliance & Regulatory Traceability

- AML/KYC readiness [LCA-AMLKYC-001]
- Pre-trade compliance [LCA-COMP-001]
- Segregation of duties [LCA-SOD-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `ComplianceClient.evaluate(action, context)` → `ComplianceResult`, `ComplianceClient.getCases(filter?)` → `ComplianceCase[]`, `ComplianceClient.approveException(caseId, justification)` → `ExceptionApproved`, `ComplianceClient.blockClient(clientId, reason)` → `ClientBlocked`
- **REST API:** Exposed via K-11 API Gateway at `/api/v1/compliance/*`
- **Jurisdiction Plugin Extension Points:** T2 Rule Packs — compliance rule definitions per jurisdiction (e.g., SEBON insider trading, NRB lock-in, SEBI margin)
- **Events Emitted:** `ComplianceCheckPassed`, `ComplianceCheckFailed`, `ComplianceApprovalGranted`, `ExceptionApproved`, `ClientBlocked` — all conform to K-05 standard envelope
- **Events Consumed:** `EvaluatePreTradeCommand` (from K-03 pipeline), `KYCStatusChanged` (from K-01), `ConfigUpdated` (from K-02), `SanctionsListUpdated` (from D-14)
- **Webhook Extension Points:** `POST /webhooks/compliance-events` for external compliance monitoring integration

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                   |
| ---------------------------------------------------- | --------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes.                              |
| Can tax rules change without redeploy?               | N/A (Compliance, not tax).        |
| Can this run in an air-gapped deployment?            | Yes, with local compliance rules. |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Compliance Rule Bypass**
   - **Threat:** Attacker bypasses compliance checks to execute prohibited trades.
   - **Mitigation:** Compliance rules in T2 Rule Packs with maker-checker; all checks logged; defense in depth with multiple validation layers; AI monitors for bypass attempts.
   - **Residual Risk:** Zero-day vulnerability in rule engine.

2. **AML/KYC Data Falsification**
   - **Threat:** Client provides false identity or source of funds information.
   - **Mitigation:** National ID verification via K-01 adapters; sanctions screening via external APIs; document verification AI; maker-checker for high-risk clients; all verifications audited.
   - **Residual Risk:** Sophisticated identity fraud with genuine-looking documents.

3. **Insider Trading Facilitation**
   - **Threat:** Compliance officer disables checks to enable insider trading.
   - **Mitigation:** Maker-checker for all exception approvals; all exceptions audited to K-07; AI monitors for unusual exception patterns; segregation of duties.
   - **Residual Risk:** Collusion between multiple insiders.

4. **Sanctions List Evasion**
   - **Threat:** Sanctioned entity uses aliases or shell companies to evade screening.
   - **Mitigation:** Fuzzy matching on names; entity relationship analysis; periodic re-screening; AI detects related entities; all screenings logged.
   - **Residual Risk:** Sophisticated corporate structure obfuscation.

5. **Compliance Data Tampering**
   - **Threat:** Attacker modifies compliance records to hide violations.
   - **Mitigation:** Immutable audit trail in K-07; cryptographic hash chaining; all modifications logged; regular integrity verification.
   - **Residual Risk:** Database-level privilege escalation.

**Security Controls:**

- Maker-checker for all exceptions
- Immutable audit trail (K-07)
- National ID verification
- Sanctions screening
- Segregation of duties
- AI-based anomaly detection
- Periodic re-screening

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
