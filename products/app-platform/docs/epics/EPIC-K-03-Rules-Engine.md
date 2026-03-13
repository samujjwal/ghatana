EPIC-ID: EPIC-K-03
EPIC NAME: Policy / Rules Engine
LAYER: KERNEL
MODULE: K-03 Policy / Rules Engine
VERSION: 1.1.1

---

#### Section 1 — Objective

Deploy a centralized, high-performance Policy / Rules Engine (K-03) that evaluates declarative logic (e.g., compliance rules, margin requirements, fee calculations). This engine fulfills Principle 2 (Full Externalization via Rule Packs) by sandboxing all T2 Rule Packs (declarative logic in OPA/Rego or equivalent). It ensures that all jurisdiction-specific, operator-specific, and asset-specific rules are evaluated dynamically without hardcoding business logic in the Generic Core or Domain Subsystems.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Declarative policy DSL evaluator (e.g., OPA/Rego).
  2. Rule versioning with tenant-level and jurisdiction-level pinning.
  3. Hot-reload of rule updates without service restart.
  4. Rule evaluation audit logging.
  5. Integration with K-04 Plugin Runtime for T2 sandboxing.
- **Out-of-Scope:**
  1. Arbitrary code execution (T3 Executable Packs) — handled by K-04 sandbox.
  2. The actual rules (e.g., SEBON compliance rules) — these are authored in T2 Rule Packs.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-04 (Plugin Runtime), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-18 (Resilience Patterns)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Declarative Evaluation:** The engine must parse and evaluate declarative policies against provided JSON input state.
2. **FR2 T2 Sandboxing:** The engine must enforce memory and execution time limits per rule evaluation to prevent denial of service.
3. **FR3 Hot Reload:** The engine must reload T2 Rule Packs dynamically when notified via K-05 Event Bus.
4. **FR4 Audit Trail:** Every evaluation must log inputs, the specific rule version matched, the outcome, and latency to K-07.
5. **FR5 Jurisdiction Routing:** Evaluate rules based on the jurisdiction context provided in the payload.
6. **FR6 Maker-Checker Rule Evaluation:** Provide standard rulesets for determining if a workflow requires maker-checker approval.
7. **FR7 Event-Sourced Updates:** Rule pack deployments are immutable events.
8. **FR8 Multi-Calendar Support:** Rule evaluation context must accept `CalendarDate` (K-15) for time-bound rules. Calendar-aware comparisons (e.g., BS lock-in expiry) are delegated to `CalendarClient`.
9. **FR9 Circuit Breaker & Degraded Mode:** When the Rules Engine is overloaded or unavailable, dependent modules must activate a circuit breaker (via K-18 Resilience Patterns). Degraded mode behavior: (a) for compliance rules — fail closed (deny), (b) for fee calculations — use last-known cached result with `degraded=true` flag, (c) for margin rules — fail closed. Circuit breaker opens after 3 consecutive failures or P99 > 50ms for 30 seconds. Half-open state retries every 5 seconds. All degraded decisions are flagged for post-recovery review. [ARB P0-02]
10. **FR10 Mid-Session Rule Deployment:** When a T2 Rule Pack is deployed during an active trading session: (a) new rules apply only to evaluations initiated after deployment, (b) in-flight evaluations complete using the rule version active at evaluation start, (c) emit `RulePackMidSessionDeployedEvent` recording the version transition boundary. [ARB D.6]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The evaluation engine is jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Specific logic (e.g., SEBON Margin Rules) lives in T2 Rule Packs.
3. **Resolution Flow:** Domain modules call the Rules Engine specifying the required policy package (e.g., `compliance.pre_trade.np`).
4. **Hot Reload:** Seamless.
5. **Backward Compatibility:** Older rule versions remain accessible for auditing historical transactions.
6. **Future Jurisdiction:** New jurisdictions are supported simply by loading new T2 Rule Packs.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `RulePack`: `{ pack_id: String, jurisdiction: String, version: String, dsl_content: String, status: Enum }`
  - `EvaluationResult`: `{ request_id: UUID, pack_id: String, version: String, input: JSON, output: JSON, latency_ms: Int }`
- **Multi-Calendar Fields:** N/A natively; processes `CalendarDate` values supplied in rule evaluation context.
- **Event Schema Changes:** `RulePackDeployedEvent`, `RuleEvaluationFailedEvent`, `RulePackMidSessionDeployedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                    |
| ----------------- | -------------------------------------------------------------- |
| Event Name        | `RulePackDeployedEvent`                                        |
| Schema Version    | `v1.0.0`                                                       |
| Trigger Condition | A new T2 Rule Pack is approved and deployed.                   |
| Payload           | `{ "pack_id": "...", "version": "...", "jurisdiction": "NP" }` |
| Consumers         | Rules Engine (cache invalidation)                              |
| Idempotency Key   | `hash(pack_id + version)`                                      |
| Replay Behavior   | Reloads the specified rule pack into memory.                   |
| Retention Policy  | Permanent.                                                     |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                              |
| ---------------- | ------------------------------------------------------------------------ |
| Command Name     | `DeployRuleCommand`                                                      |
| Schema Version   | `v1.0.0`                                                                 |
| Validation Rules | Rule syntax valid, passes test suite, maker-checker approval if required |
| Handler          | `RuleCommandHandler` in K-03 Rules Engine                                |
| Success Event    | `RuleDeployed`                                                           |
| Failure Event    | `RuleDeploymentFailed`                                                   |
| Idempotency      | Command ID must be unique; duplicate commands return original result     |

| Field            | Description                                         |
| ---------------- | --------------------------------------------------- |
| Command Name     | `EvaluateRuleCommand`                               |
| Schema Version   | `v1.0.0`                                            |
| Validation Rules | Rule exists, input context valid                    |
| Handler          | `RuleEvaluationHandler` in K-03 Rules Engine        |
| Success Event    | `RuleEvaluated`                                     |
| Failure Event    | `RuleEvaluationFailed`                              |
| Idempotency      | Same input context returns cached evaluation result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `TestRuleCommand`                                                    |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Rule syntax valid, test fixtures provided                            |
| Handler          | `RuleTestHandler` in K-03 Rules Engine                               |
| Success Event    | `RuleTested`                                                         |
| Failure Event    | `RuleTestFailed`                                                     |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist / NLP Rule Synthesis / Semantic Match Enhancement
- **Primary Use Case:** (a) AI assists compliance authors in converting natural-language regulatory circulars into Rego DSL; (b) AI performs semantic similarity search across the rule catalog to detect duplicate or conflicting rules before deployment; (c) AI suggests rule optimizations (e.g., reordering expensive predicates to short-circuit early).
- **Inference Mode:** Synchronous for copilot suggestions (latency-tolerant authoring context); Asynchronous for conflict detection on rule submission.
- **Workflow Steps Exposed:** (a) Rule authoring: AI translates regulatory text to Rego draft; (b) Rule submission: AI scans for conflicts against existing rules; (c) Rule optimization: AI suggests evaluation order improvements.
- **Model Registry Usage:** `rule-authoring-copilot-v1` (LLM-based Rego generation from natural language), `rule-conflict-detector-v1` (semantic embedding-based duplicate/conflict detection using K-09 Embedding Service), `rego-optimizer-v1` (static analysis + ML-based predicate reordering)
- **Input Data / Feature Set:** Natural language regulatory text; existing Rego rule ASTs; rule evaluation telemetry (which predicates fail most often, relative cost).
- **Output / AI Annotation:** Rego draft (human must approve); conflict report with similarity scores; optimized predicate ordering suggestion.
- **Explainability Requirement:** For regulatory text → Rego translation: AI must output a clause-by-clause mapping showing which natural language sentence generated which Rego condition. For conflict detection: AI must show the embedding similarity score and the specific overlapping logic.
- **Confidence Threshold:** Rego generation confidence < 0.8 → AI flags the uncertain clause with `// AI: LOW_CONFIDENCE - requires human review`. Conflict similarity score ≥ 0.9 → block submission; 0.7–0.9 → warn.
- **Human Override Path:** Human always reviews and approves (or rejects) AI-generated Rego. Rule is never deployed without explicit human approval (maker-checker). Conflict warnings can be dismissed with documented justification.
- **Feedback Loop:** Rule authors who correct AI Rego drafts contribute corrected (natural language, Rego) pairs to K-09 fine-tuning pipeline for `rule-authoring-copilot-v1`.
- **Drift Monitoring:** Track ratio of AI-generated Rego accepted without modification vs. substantially edited. Substantial edits > 30% of generated code → trigger model quality review.
- **Fallback Behavior:** Standard manual Rego authoring. Conflict detection falls back to exact-match syntactic comparison.
- **Tenant Isolation:** Rule corpus is tenant-scoped; semantic search for conflict detection operates only within the same tenant's rule catalog.

---

#### Section 9 — NFRs

| NFR Category               | Required Targets                                           |
| -------------------------- | ---------------------------------------------------------- |
| Latency / Throughput       | P99 < 5ms per evaluation; 25,000 TPS                       |
| Scalability                | Horizontally scalable; sidecar deployment option           |
| Availability               | 99.999% uptime                                             |
| Consistency Model          | Read-your-writes for rule updates                          |
| Security                   | Execution sandboxed; no filesystem/network access from DSL |
| Data Residency             | Rule definitions stored globally                           |
| Data Retention             | Evaluation logs kept per audit policy                      |
| Auditability               | Every evaluation logged                                    |
| Observability              | Metrics: `rule.eval.latency`, `rule.eval.count`            |
| Extensibility              | Zero core changes for new rules                            |
| Upgrade / Compatibility    | Multiple rule versions run concurrently                    |
| On-Prem Constraints        | Rules bundled in signed deployment packs                   |
| Ledger Integrity           | N/A                                                        |
| Multi-Calendar Correctness | Correctly processes `CalendarDate` inputs from K-15        |

---

#### Section 10 — Acceptance Criteria

1. **Given** a deployed SEBON T2 Rule Pack, **When** the OMS sends a pre-trade compliance request, **Then** the engine evaluates the rule in < 5ms and returns allow/deny.
2. **Given** an infinite loop in a poorly written custom rule, **When** evaluated, **Then** the engine sandbox terminates it after 50ms and returns a failure event.
3. **Given** a new version of a Rule Pack deployed via Event Bus, **When** the next request arrives, **Then** it is evaluated using the new version without service restart.
4. **Given** a compliance audit, **When** queried, **Then** K-07 contains the exact input payload and rule version used for historical evaluations.

---

#### Section 11 — Failure Modes & Resilience

- **Syntax Error in Rule:** Rejected at deployment time via CI pipeline.
- **Engine Overload:** Sheds load; returns HTTP 429; triggers horizontal scaling. Circuit breaker activates after sustained overload (see FR9).
- **Timeout:** Aborts evaluation and returns default safe posture (e.g., deny for compliance, alert for fees).
- **Complete Outage:** Circuit breaker opens; dependent modules enter degraded mode with cached evaluations (time-bounded to 5 minutes). All degraded decisions are tagged for post-recovery re-evaluation. [ARB P0-02]

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                            |
| ------------------- | --------------------------------------------------------------------------- |
| Metrics             | `rule.eval.latency`, `rule.eval.errors`, dimensions: `pack_id`, `tenant_id` |
| Logs                | Structured: `trace_id`, `pack_id`, `result`                                 |
| Traces              | Span `RulesEngine.evaluate`                                                 |
| Audit Events        | Inputs, outputs, and rule version for regulated decisions [LCA-AUDIT-001]   |
| Regulatory Evidence | Rule evaluation trails for compliance checks                                |

---

#### Section 13 — Compliance & Regulatory Traceability

- Pre-trade compliance evaluation hooks [LCA-COMP-001]
- Audit trails of decision logic [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `RulesClient.evaluate(policyPath, inputJson)`
- **Jurisdiction Plugin Extension Points:** All T2 Rule Packs run here.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                       |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, natively designed for T2 Rule Packs.                                                                             |
| Can a new regulator be added?                                         | Yes, via new Rule Pack.                                                                                               |
| Can tax rules change without redeploy?                                | Yes, rules are hot-reloaded.                                                                                          |
| Can this run in an air-gapped deployment?                             | Yes, offline bundles supported.                                                                                       |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Token classification, fractional ownership, and smart-contract validation rules are deployable as T2 Rule Packs. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Atomic settlement rules and real-time finality checks can be expressed as Rego policies without engine changes.  |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
