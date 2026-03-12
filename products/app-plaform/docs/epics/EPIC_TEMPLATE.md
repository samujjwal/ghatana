EPIC-ID: EPIC-{LAYER}-{NUMBER}
EPIC NAME: {Descriptive Name}
LAYER: {KERNEL|DOMAIN|WORKFLOW|OPERATIONS|PACKS|REGULATORY|TESTING|PLATFORM-UNITY}
MODULE: {Layer}-{Number} {Module Name}
VERSION: 1.0.0

---

#### Section 1 — Objective

{Provide a clear, concise statement of what this epic delivers and why it exists. Reference relevant architectural principles (e.g., Principle 4: Generic Core Purity, Principle 10: First-Party Subsystem). Explain how this module fits into the overall platform architecture.}

Example:
"Deliver the K-01 Identity & Access Management (IAM) module, providing multi-tenant authentication, authorization, and session management for the platform. This epic enforces Principle 11 (No Kernel Duplication) by providing a unified AuthZ SDK for all domain modules."

---

#### Section 2 — Scope

- **In-Scope:**
  1. {List specific capabilities, features, and responsibilities this module handles}
  2. {Be explicit about what IS included}
  3. {Typically 5-8 items}
- **Out-of-Scope:**
  1. {List what this module explicitly does NOT handle}
  2. {Clarify boundaries with other modules}
  3. {Prevent scope creep}
- **Dependencies:** {List all epic dependencies, e.g., EPIC-K-01 (IAM), EPIC-K-05 (Event Bus)}
- **Kernel Readiness Gates:** {List kernel modules that must be stable before implementing this epic, or N/A for kernel epics}
- **Module Classification:** {Generic Core | Domain Subsystem | Cross-Cutting Layer}

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 {Requirement Name}:** {The module must... Clear, testable requirement statement.}
2. **FR2 {Requirement Name}:** {Use "must" for mandatory requirements, "should" for recommended.}
3. **FR3 {Requirement Name}:** {Each FR should be independently testable.}
4. **FR4 {Requirement Name}:** {Typically 6-10 functional requirements per epic.}
5. **FR5 {Requirement Name}:** {Cover happy paths, edge cases, and integration points.}
6. **FR6 {Requirement Name}:** {Reference other modules/epics where applicable.}
7. **FR7 {Requirement Name}:** {Include dual-calendar requirements if applicable.}
8. **FR8 {Requirement Name}:** {Include maker-checker requirements if applicable.}
9. **FR9 {Requirement Name}:** {Include ledger impact if applicable.}
10. **FR10 {Requirement Name}:** {Include AI integration hooks if applicable.}

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** {Describe what parts of this module are jurisdiction-agnostic. This should be the majority of the code.}
2. **Jurisdiction Plugin:** {Describe what jurisdiction-specific logic is externalized to T1/T2/T3 packs. Give specific examples.}
3. **Resolution Flow:** {Explain how the Config Engine (K-02) determines which jurisdiction-specific rules/configs apply.}
4. **Hot Reload:** {Describe how jurisdiction-specific changes can be applied without service restart.}
5. **Backward Compatibility:** {Explain how historical data/transactions remain valid when jurisdiction rules change.}
6. **Future Jurisdiction:** {Demonstrate how a new country (e.g., India, Bangladesh) can be added via plugins without core changes.}

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `EntityName`: `{ field1: Type, field2: Type, field3: Type, ... }`
  - `AnotherEntity`: `{ field1: Type, field2: Type, ... }`
- **Dual-Calendar Fields:** {List which fields use DualDate (e.g., created_at, updated_at, executed_at)}
- **Event Schema Changes:** {List new event types introduced by this epic}

Example:

```
- **New Entities:**
  - `Order`: `{ order_id: UUID, client_id: UUID, instrument_id: String, side: Enum, qty: Int, type: Enum, status: Enum, submitted_at: DualDate, updated_at: DualDate }`
  - `Position`: `{ client_id: UUID, instrument_id: String, available_qty: Int, locked_qty: Int }`

- **Dual-Calendar Fields:** `submitted_at` and `updated_at` use `DualDate`.
- **Event Schema Changes:** `OrderPlaced`, `OrderModified`, `OrderCancelled`, `OrderStateChanged`.
```

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                               |
| ----------------- | ----------------------------------------------------------------------------------------- |
| Event Name        | `{EventName}` (use past tense, e.g., OrderPlaced, TradeExecuted)                          |
| Schema Version    | `v1.0.0`                                                                                  |
| Trigger Condition | {When is this event emitted? Be specific.}                                                |
| Payload           | `{ "field1": "...", "field2": "...", "timestamp_bs": "..." }` (include dual-calendar)     |
| Consumers         | {Which modules/services consume this event?}                                              |
| Idempotency Key   | `hash(field1 + field2 + timestamp)`                                                       |
| Replay Behavior   | {What happens when this event is replayed? Suppress side-effects or rebuild projections?} |
| Retention Policy  | {How long is this event retained? Permanent, 10 years, etc.}                              |

{Add additional event definitions as needed. Most epics have 3-5 event types.}

---

#### Section 7 — Command Model Definition

| Field            | Description                                                           |
| ---------------- | --------------------------------------------------------------------- |
| Command Name     | `{CommandName}Command` (use imperative verb, e.g., PlaceOrderCommand) |
| Schema Version   | `v1.0.0`                                                              |
| Validation Rules | {What validations are performed before executing this command?}       |
| Handler          | `{HandlerName}` in {Module Name}                                      |
| Success Event    | `{EventName}`                                                         |
| Failure Event    | `{EventName}Failed`                                                   |
| Idempotency      | {How are duplicate commands handled?}                                 |

{Add additional command definitions as needed. Most epics have 3-5 command types.}

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** {Copilot Assist | Anomaly Detection | Predictive Analytics | Code Generation | N/A}
- **Workflow Steps Exposed:** {Which workflow steps have AI integration?}
- **Model Registry Usage:** `{model-name-v1}` (reference to K-09 AI Governance)
- **Explainability Requirement:** {How does AI explain its decisions/predictions?}
- **Human Override Path:** {How can humans override AI decisions?}
- **Drift Monitoring:** {How is model drift detected and handled?}
- **Fallback Behavior:** {What happens if AI is unavailable?}

{If no AI integration, mark all as N/A}

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                 |
| ------------------------- | ---------------------------------------------------------------- |
| Latency / Throughput      | {P99 < Xms; Y TPS}                                               |
| Scalability               | {Horizontal scaling approach, auto-scaling triggers}             |
| Availability              | {99.9% / 99.99% / 99.999% uptime target as applicable}           |
| Consistency Model         | {Strong / Eventual / Read-your-writes consistency as applicable} |
| Security                  | {Authentication, authorization, encryption requirements}         |
| Data Residency            | {Jurisdiction-specific data storage requirements}                |
| Data Retention            | {Minimum retention periods per data type}                        |
| Auditability              | {Audit logging requirements, compliance codes}                   |
| Observability             | {Metrics, logs, traces, dimensions}                              |
| Extensibility             | {Plugin support, extension points}                               |
| Upgrade / Compatibility   | {Backward compatibility guarantees, versioning}                  |
| On-Prem Constraints       | {Air-gapped deployment support, local dependencies}              |
| Ledger Integrity          | {Integration with K-16 Ledger Framework}                         |
| Dual-Calendar Correctness | {Dual-calendar handling accuracy}                                |

---

#### Section 10 — Acceptance Criteria

1. **Given** {precondition}, **When** {action}, **Then** {expected outcome}.
2. **Given** {precondition}, **When** {action}, **Then** {expected outcome}.
3. **Given** {precondition}, **When** {action}, **Then** {expected outcome}.
4. **Given** {precondition}, **When** {action}, **Then** {expected outcome}.
5. **Given** {precondition}, **When** {action}, **Then** {expected outcome}.
6. **Given** {precondition}, **When** {action}, **Then** {expected outcome}.
7. **Given** {precondition}, **When** {action}, **Then** {expected outcome}.

{Typically 5-10 acceptance criteria. Cover happy paths, edge cases, failure scenarios, and integration points. Make them specific and testable.}

---

#### Section 11 — Failure Modes & Resilience

- **{Failure Scenario}:** {Description of how the system handles this failure. Include retry logic, circuit breakers, fallback behavior, etc.}
- **{Failure Scenario}:** {Be specific about error handling, timeouts, and recovery mechanisms.}
- **{Failure Scenario}:** {Consider network partitions, service unavailability, data corruption, etc.}
- **{Failure Scenario}:** {Typically 3-5 failure modes per epic.}

Example:

```
- **Rules Engine Down:** Reject all new orders safely; circuit breaker opens after 5 failures.
- **Event Bus Partition:** Disconnect client and return 503 rather than accepting an order that cannot be persisted.
- **Timeout:** Abort evaluation after 50ms and return default safe posture (deny for compliance).
```

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                           |
| ------------------- | -------------------------------------------------------------------------- |
| Metrics             | `metric.name`, `metric.name.count`, dimensions: `dimension1`, `dimension2` |
| Logs                | Structured logs with: `trace_id`, `field1`, `field2`, `duration_ms`        |
| Traces              | Span `ModuleName.operation` with parent/child relationships                |
| Audit Events        | Action: `ActionName`, `before_state`, `after_state` [LCA-AUDIT-001]        |
| Regulatory Evidence | {What evidence is captured for regulatory compliance?}                     |

---

#### Section 13 — Compliance & Regulatory Traceability

- {Compliance requirement description} [LCA-XXX-###]
- {Compliance requirement description} [ASR-XXX-###]
- {Compliance requirement description} [LCA-XXX-###]

{Reference specific compliance codes from COMPLIANCE_CODE_REGISTRY.md. Typically 2-5 compliance requirements per epic.}

Example:

```
- Maker-checker controls for sensitive operations [LCA-SOD-001]
- Comprehensive audit trail for all state changes [LCA-AUDIT-001]
- Data retention for 10 years minimum (SEBON/SEBI cross-jurisdiction safety margin) [LCA-RET-001]
```

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** {Describe the SDK/API contract this module exposes. Include method signatures.}
- **Jurisdiction Plugin Extension Points:** {Describe T1/T2/T3 extension points. Give examples.}
- **Events Emitted:** {List all events this module emits — must conform to K-05 standard envelope: `event_id`, `event_type`, `aggregate_id`, `aggregate_type`, `sequence_number`, `timestamp`, `timestamp_bs`, `payload`}
- **Events Consumed:** {List all events this module consumes, with producing module reference}
- **Webhook Extension Points:** {List HTTP callback extension points for external integrations}

Example:

```
- **SDK Contract:** `AuthZClient.hasPermission(userId, resource, action)` returns boolean
- **Jurisdiction Plugin Extension Points:** `NationalIdValidator` interface for T3 Adapters
- **Events Emitted:** `UserAuthenticated`, `RoleGranted`, `RoleRevoked` (K-05 envelope)
- **Events Consumed:** `ConfigUpdated` (from K-02), `SecretRotated` (from K-14)
- **Webhook Extension Points:** `POST /webhooks/auth-events` for external SIEM integration
```

**Invariant:** Every event listed in "Events Consumed" must appear in the producing module's "Events Emitted" list. Cross-reference with DEPENDENCY_MATRIX.md.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer           |
| ---------------------------------------------------- | ------------------------- |
| Can this module support India/Bangladesh via plugin? | {Yes/No with explanation} |
| Can a new regulator be added?                        | {Yes/No with explanation} |
| Can tax rules change without redeploy?               | {Yes/No with explanation} |
| Can this run in an air-gapped deployment?            | {Yes/No with explanation} |

{Add module-specific questions. Typically 4-6 questions. All answers should demonstrate plugin-based extensibility.}

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **{Threat Name}**
   - **Threat:** {Description of the attack vector and potential impact}
   - **Mitigation:** {Specific controls to prevent or detect this threat}
   - **Residual Risk:** {What risk remains after mitigation?}

2. **{Threat Name}**
   - **Threat:** {Description}
   - **Mitigation:** {Controls}
   - **Residual Risk:** {Remaining risk}

3. **{Threat Name}**
   - **Threat:** {Description}
   - **Mitigation:** {Controls}
   - **Residual Risk:** {Remaining risk}

{Typically 5-8 threat scenarios. Cover authentication, authorization, data integrity, availability, confidentiality.}

**Security Controls:**

- {List of security controls implemented}
- {Authentication mechanisms}
- {Authorization mechanisms}
- {Encryption (in transit and at rest)}
- {Audit logging}
- {Anomaly detection}
- {Rate limiting}
- {Input validation}
- {Network segmentation}
- {Regular security assessments}

{Include this section for all security-critical modules. Optional for others.}

---

## Changelog

### Version 1.0.0 (YYYY-MM-DD)

**Type:** MAJOR  
**Author:** {Your Name}  
**Reviewer:** {Reviewer Name}  
**Changes:**

- Initial release

---

## Usage Instructions

### For Epic Authors

1. **Copy this template** to create a new epic
2. **Replace all placeholders** in {curly braces} with actual content
3. **Delete instructional text** in {curly braces}
4. **Fill all sections** - do not leave sections empty (use N/A if not applicable)
5. **Follow naming conventions:**
   - Epic ID: `EPIC-{LAYER}-{NUMBER}` (e.g., EPIC-K-17, EPIC-D-13)
   - File name: `EPIC-{LAYER}-{NUMBER}-{NAME}.md` (e.g., EPIC-K-17-Cache-Manager.md)
6. **Version:** Start at 1.0.0 for new epics
7. **Review checklist:**
   - [ ] All sections completed
   - [ ] Functional requirements numbered and testable
   - [ ] NFR targets quantified
   - [ ] Acceptance criteria in Given/When/Then format
   - [ ] Dependencies listed and validated
   - [ ] Compliance codes referenced
   - [ ] Threat model included (if security-critical)
   - [ ] Jurisdiction isolation demonstrated
   - [ ] Dual-calendar support specified
   - [ ] Event and command models defined

### Section-Specific Guidance

**Section 1 (Objective):**

- 2-3 sentences maximum
- Reference architectural principles
- Explain module's role in platform

**Section 2 (Scope):**

- Be explicit about boundaries
- List 5-8 in-scope items
- List 2-4 out-of-scope items
- Validate dependencies exist

**Section 3 (Functional Requirements):**

- Use "must" for mandatory, "should" for recommended
- Number sequentially (FR1, FR2, ...)
- Make each requirement independently testable
- Typically 6-10 requirements

**Section 4 (Jurisdiction Isolation):**

- Demonstrate plugin architecture
- Give concrete examples
- Show how new jurisdictions are added

**Section 5 (Data Model):**

- Define all new entities
- Specify field types
- Identify dual-calendar fields

**Section 6 (Event Model):**

- Use past tense for event names
- Include dual-calendar timestamps
- Define idempotency strategy
- Specify retention policy

**Section 7 (Command Model):**

- Use imperative verb for command names
- Define validation rules
- Map to success/failure events

**Section 8 (AI Integration):**

- Mark N/A if no AI integration
- Reference K-09 AI Governance
- Define explainability requirements

**Section 9 (NFRs):**

- Quantify all targets (numbers, not vague terms)
- Use P99 for latency, TPS for throughput
- Specify availability percentage
- Reference compliance codes

**Section 10 (Acceptance Criteria):**

- Use Given/When/Then format
- Make testable and specific
- Cover happy paths and edge cases
- Typically 5-10 criteria

**Section 11 (Failure Modes):**

- Consider all failure scenarios
- Define retry/fallback behavior
- Specify circuit breaker logic

**Section 12 (Observability):**

- Define specific metric names
- Specify log structure
- Define trace spans
- Reference audit requirements

**Section 13 (Compliance):**

- Reference codes from COMPLIANCE_CODE_REGISTRY.md
- Typically 2-5 compliance requirements

**Section 14 (Extension Points):**

- Define SDK contracts with signatures
- Specify plugin interfaces
- List events emitted/consumed

**Section 15 (Future-Safe):**

- Ask jurisdiction extensibility questions
- All answers should demonstrate plugin architecture

**Section 16 (Threat Model):**

- Required for security-critical modules
- **Strongly recommended for all modules** — financial platforms are high-value targets
- Typically 5-8 threat scenarios
- Include security controls list

---

### Cross-Document Traceability

Every epic must be traceable to:

- **Architecture Spec**: Section number in ARCHITECTURE*SPEC_PART*\*.md
- **LLD**: Corresponding LLD\_\*.md file (if exists)
- **C4 Diagrams**: Component in C4_C3_COMPONENT_SIDDHANTA.md
- **Dependency Matrix**: Row in DEPENDENCY_MATRIX.md
- **Compliance Codes**: Entries in COMPLIANCE_CODE_REGISTRY.md
- **ARB Findings**: ARB reference ID (if applicable, e.g., [ARB P0-01])

If a corresponding LLD does not yet exist, note: `LLD: PENDING — tracked as LLD_{MODULE_ID}.md`

---

## Quality Checklist

Before submitting an epic for review:

- [ ] **Completeness:** All sections filled (no {placeholders} remaining)
- [ ] **Consistency:** Terminology matches GLOSSARY.md
- [ ] **Clarity:** Requirements are clear and unambiguous
- [ ] **Testability:** Acceptance criteria are specific and testable
- [ ] **Traceability:** Dependencies validated in DEPENDENCY_MATRIX.md
- [ ] **Compliance:** All compliance codes referenced in COMPLIANCE_CODE_REGISTRY.md
- [ ] **Architecture:** Jurisdiction isolation demonstrated
- [ ] **NFRs:** All targets quantified with numbers
- [ ] **Events:** Event and command models fully defined
- [ ] **Security:** Threat model included (if applicable)
- [ ] **Versioning:** Version header correct, changelog present
- [ ] **Review:** Peer review completed and feedback addressed

---

**Template Version:** 1.1.0  
**Last Updated:** March 10, 2026  
**Owner:** Platform Architecture Team  
**Approver:** Chief Technology Officer
