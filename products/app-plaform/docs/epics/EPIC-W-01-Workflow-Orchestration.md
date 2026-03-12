EPIC-ID: EPIC-W-01
EPIC NAME: Cross-Domain Workflow Orchestration
LAYER: WORKFLOW
MODULE: W-01 Workflow Orchestration
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the W-01 Cross-Domain Workflow Orchestration module, providing a declarative framework for defining, executing, and monitoring complex multi-domain workflows that span multiple subsystems (e.g., Client Onboarding spanning IAM → Compliance → Risk → OMS, Corporate Action Processing spanning Reference Data → Corporate Actions → Post-Trade → Regulatory Reporting). This epic addresses the P1 gap identified in the platform review by building on K-05's saga primitives to provide a higher-level workflow DSL, human task integration, workflow versioning, and comprehensive SLA tracking. It ensures that complex business processes are explicitly modeled, auditable, and resilient.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Declarative workflow definition DSL (YAML/JSON based).
  2. Long-running workflow state management (days to weeks).
  3. Human task integration (approvals, exceptions, data entry).
  4. Workflow versioning and migration (in-flight workflow handling).
  5. Workflow monitoring, SLA tracking, and alerting.
  6. Compensation and rollback logic for failed workflows.
  7. Integration with K-05 Event Bus for saga orchestration.
  8. Metadata-driven step catalog and typed task/form schemas.
  9. Runtime-resolved value catalogs for workflow parameters, choices, and routing policies.
- **Out-of-Scope:**
  1. Domain-specific business logic (handled by domain modules).
  2. Simple single-domain workflows (handled directly by domain modules).
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-13 (Admin Portal), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** K-01, K-02, K-05, K-07, K-13, K-15
- **Module Classification:** Cross-Cutting Orchestration Layer

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Workflow Definition DSL:** The module must support declarative workflow definitions in YAML/JSON specifying steps, transitions, conditions, timeouts, and compensation logic.
2. **FR2 Workflow Execution Engine:** The module must execute workflows by orchestrating calls to domain modules via K-05 Event Bus, maintaining workflow state in a durable store.
3. **FR3 Human Task Management:** The module must support human tasks (approvals, data entry, exception handling) with task assignment, escalation, and deadline tracking.
4. **FR4 Workflow Versioning:** The module must support multiple versions of a workflow definition running concurrently, with in-flight workflows completing on their original version.
5. **FR5 Workflow Migration:** The module must provide APIs to migrate in-flight workflows from one version to another (with validation).
6. **FR6 SLA Tracking:** The module must track workflow SLAs (total duration, step duration) and emit alerts when thresholds are breached.
7. **FR7 Compensation Logic:** The module must execute compensation steps (rollback, cleanup) when a workflow fails or is cancelled.
8. **FR8 Workflow Monitoring:** The module must provide real-time visibility into workflow execution (active workflows, pending tasks, failed steps) via K-13 Admin Portal.
9. **FR9 Event-Driven Triggers:** The module must support workflows triggered by K-05 events (e.g., `OrderPlaced` triggers pre-trade workflow).
10. **FR10 Dual-Calendar Support:** Workflow deadlines and SLAs must support dual-calendar (Gregorian and BS) for operational planning.
11. **FR11 Step Catalog Resolution:** Workflow definitions must reference registered step handlers or templates so new process steps can be introduced through governed metadata and contract registration instead of orchestration-engine code changes.
12. **FR12 Dynamic Human Task Schemas:** Human tasks must support schema-driven forms, validations, and field-level policies resolved at runtime.
13. **FR13 Value Catalog Integration:** Workflow decisions, task choices, and routing parameters must be able to bind to versioned value catalogs from K-02 with scoped overrides and effective-date activation.
14. **FR14 Pinned Metadata Migration Rules:** In-flight workflow instances must pin workflow, step-template, task-schema, and value-catalog versions at start or at defined migration checkpoints, with explicit rules for additive versus breaking metadata changes.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The workflow orchestration runtime, DSL parser, and state management are jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Specific workflow definitions (e.g., Nepal client onboarding with NRB KYC requirements) are T1 Config Packs or T2 Rule Packs.
3. **Resolution Flow:** Config Engine determines which workflow definition, task schema, and value catalog apply based on tenant/jurisdiction.
4. **Hot Reload:** New workflow versions can be deployed dynamically; in-flight workflows continue on old version.
5. **Backward Compatibility:** Workflow state schema must be forward/backward compatible.
6. **Future Jurisdiction:** A new country's workflows are simply new workflow definitions in the DSL.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `WorkflowDefinition`: `{ workflow_id: String, version: Int, dsl: JSON, created_at: DualDate, status: Enum }`
  - `WorkflowInstance`: `{ instance_id: UUID, workflow_id: String, version: Int, status: Enum, started_at: DualDate, completed_at: DualDate, current_step: String, context: JSON }`
  - `HumanTask`: `{ task_id: UUID, instance_id: UUID, assignee: String, deadline: DualDate, status: Enum, payload: JSON }`
  - `WorkflowStepCatalog`: `{ step_key: String, version: Int, input_schema: JSONSchema, output_schema: JSONSchema, handler_ref: String, status: Enum }`
  - `WorkflowValueCatalogRef`: `{ catalog_key: String, version: Int, scope: Enum, effective_at: DualDate }`
- **Dual-Calendar Fields:** `created_at`, `started_at`, `completed_at`, `deadline` use `DualDate`.
- **Event Schema Changes:** `WorkflowStarted`, `WorkflowCompleted`, `WorkflowFailed`, `HumanTaskAssigned`, `HumanTaskCompleted`, `WorkflowSlaBreached`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                             |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `WorkflowStarted`                                                                                                       |
| Schema Version    | `v1.0.0`                                                                                                                |
| Trigger Condition | A workflow instance is initiated (manually or via event trigger).                                                       |
| Payload           | `{ "instance_id": "...", "workflow_id": "client_onboarding", "version": 3, "initiator": "...", "timestamp_bs": "..." }` |
| Consumers         | Workflow Orchestration (state tracking), Audit Framework, Observability                                                 |
| Idempotency Key   | `hash(instance_id)`                                                                                                     |
| Replay Behavior   | Updates the materialized view of active workflows.                                                                      |
| Retention Policy  | Permanent.                                                                                                              |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                                     |
| ---------------- | ------------------------------------------------------------------------------- |
| Command Name     | `StartWorkflowCommand`                                                          |
| Schema Version   | `v1.0.0`                                                                        |
| Validation Rules | Workflow definition exists, initial context valid, requester authorized         |
| Handler          | `WorkflowCommandHandler` in W-01 Workflow Orchestration                         |
| Success Event    | `WorkflowStarted`                                                               |
| Failure Event    | `WorkflowStartFailed`                                                           |
| Idempotency      | Command ID must be unique; duplicate commands return original workflow instance |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CompleteTaskCommand`                                                |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Task exists, task assigned to requester, result data valid           |
| Handler          | `TaskCommandHandler` in W-01 Workflow Orchestration                  |
| Success Event    | `HumanTaskCompleted`                                                 |
| Failure Event    | `TaskCompletionFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CancelWorkflowCommand`                                              |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Workflow exists, workflow cancelable, requester authorized           |
| Handler          | `WorkflowCommandHandler` in W-01 Workflow Orchestration              |
| Success Event    | `WorkflowCancelled`                                                  |
| Failure Event    | `WorkflowCancellationFailed`                                         |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist / Predictive Analytics
- **Workflow Steps Exposed:** Workflow bottleneck detection, SLA prediction.
- **Model Registry Usage:** `workflow-optimizer-v1`
- **Explainability Requirement:** AI predicts which workflows are likely to breach SLA based on current step duration and historical patterns, suggesting proactive interventions.
- **Human Override Path:** Workflow operator can manually escalate or reassign tasks.
- **Drift Monitoring:** SLA prediction accuracy tracked.
- **Fallback Behavior:** Standard static SLA thresholds.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                         |
| ------------------------- | ---------------------------------------------------------------------------------------- |
| Latency / Throughput      | Workflow step transition < 100ms; 5,000 concurrent workflows                             |
| Scalability               | Horizontally scalable execution workers                                                  |
| Availability              | 99.99% uptime                                                                            |
| Consistency Model         | Strong consistency for workflow state transitions                                        |
| Security                  | Workflow context encrypted at rest; RBAC for workflow operations                         |
| Data Residency            | Workflow state stored per jurisdiction config                                            |
| Data Retention            | Completed workflows retained 10 years                                                    |
| Auditability              | Every workflow step logged [LCA-AUDIT-001]                                               |
| Observability             | Metrics: `workflow.active.count`, `workflow.sla.breach_rate`, `human_task.pending.count` |
| Extensibility             | New workflow types via DSL definitions                                                   |
| Upgrade / Compatibility   | Workflow DSL versioned; backward compatible                                              |
| On-Prem Constraints       | Fully functional locally                                                                 |
| Ledger Integrity          | N/A                                                                                      |
| Dual-Calendar Correctness | SLA calculations accurate                                                                |

---

#### Section 10 — Acceptance Criteria

1. **Given** a workflow definition for client onboarding, **When** an operator initiates it, **Then** the engine creates a `WorkflowInstance`, emits `WorkflowStarted`, and executes the first step.
2. **Given** a workflow step requiring human approval, **When** reached, **Then** the engine creates a `HumanTask`, assigns it to the configured role, and pauses workflow execution.
3. **Given** a human task with a 2-day deadline, **When** 2 days elapse without completion, **Then** the engine escalates the task to the next level and emits `WorkflowSlaBreached`.
4. **Given** a workflow step that fails, **When** retry limit is exceeded, **Then** the engine executes the compensation steps in reverse order and marks the workflow as FAILED.
5. **Given** a new version (v2) of a workflow is deployed, **When** a new instance is started, **Then** it uses v2, while in-flight v1 instances continue on v1.
6. **Given** a workflow instance, **When** queried via the Admin Portal, **Then** the current step, pending tasks, and elapsed time are displayed in real-time.
7. **Given** a workflow triggered by an `OrderPlaced` event, **When** the event is published, **Then** the engine automatically starts the pre-trade workflow instance.
8. **Given** a workflow definition referencing a registered step template and task schema, **When** the instance reaches that step, **Then** the engine resolves the pinned metadata version and executes or renders it without requiring a new orchestration-engine deployment.
9. **Given** an in-flight workflow instance with pinned task-schema and value-catalog versions, **When** an additive metadata update is published, **Then** the running instance continues on its pinned versions unless a declared safe migration checkpoint is reached and the compatibility policy permits upgrade.

---

#### Section 11 — Failure Modes & Resilience

- **Workflow Orchestration Crash:** Workflow state persisted durably; engine resumes in-flight workflows on restart.
- **Domain Module Unavailable:** Workflow step retries with exponential backoff; if timeout exceeded, compensation logic executes.
- **Human Task Abandoned:** Escalation policy triggers after deadline; if no escalation defined, workflow enters SUSPENDED state requiring manual intervention.
- **Compensation Failure:** Logged as critical error; workflow marked as FAILED_WITH_PARTIAL_ROLLBACK; manual cleanup required.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Metrics             | `workflow.duration.p99`, `workflow.step.retry_count`, `human_task.completion_time`, dimensions: `workflow_id`, `version`, `tenant_id` |
| Logs                | Structured: `instance_id`, `step`, `status`, `duration_ms`                                                                            |
| Traces              | Span `Workflow.executeStep` with parent workflow trace                                                                                |
| Audit Events        | Action: `StartWorkflow`, `CompleteHumanTask`, `CancelWorkflow` [LCA-AUDIT-001]                                                        |
| Regulatory Evidence | Workflow execution history for compliance audits                                                                                      |

---

#### Section 13 — Compliance & Regulatory Traceability

- Maker-checker workflow enforcement [LCA-SOD-001]
- Audit trails for business processes [LCA-AUDIT-001]
- SLA compliance for regulatory deadlines [ASR-OPS-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `WorkflowClient.startWorkflow(workflowId, context)`, `WorkflowClient.completeTask(taskId, result)`, `WorkflowClient.cancelWorkflow(instanceId)`.
- **Workflow DSL Schema:** YAML/JSON schema for workflow definitions with validation.
- **Step Catalog Contract:** Versioned step-handler registration with typed input/output schemas and backward-compatibility rules.
- **Task Schema Contract:** JSON Schema or equivalent for human-task payloads, validation rules, and UI hints.
- **Metadata Migration Contract:** Compatibility policy defining which metadata changes are additive, migratable at checkpoints, or breaking and therefore forbidden for active instances.
- **Jurisdiction Plugin Extension Points:** Workflow definitions and value catalogs via T1 Config Packs.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                                                   | Expected Answer                                                                                                             |
| ------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                                       | Yes, via workflow definition config packs.                                                                                  |
| Can a new workflow be added without code changes?                                          | Yes, via DSL definition deployment.                                                                                         |
| Can existing workflows change step order or operator choice lists without engine rewrites? | Yes, through versioned workflow definitions, task schemas, and value catalogs governed by K-02.                             |
| Can this run in an air-gapped deployment?                                                  | Yes, fully self-contained.                                                                                                  |
| Can workflows span multiple days?                                                          | Yes, designed for long-running processes.                                                                                   |
| Can this module handle digital assets (tokenized securities, crypto)?                      | Yes. Tokenized issuance, transfer, and redemption workflows are definable via the standard DSL with on-chain step adapters. |
| Is the design ready for CBDC integration or T+0 settlement?                                | Yes. Instant-settlement workflow templates with synchronous finality steps are supported.                                   |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Workflow Definition Tampering**

- **Threat:** A malicious workflow definition or step contract alters business flow, approvals, or compensation behavior.
- **Mitigation:** Versioned workflow DSL, approval controls for workflow publication, signature or manifest-backed promotion, and immutable audit logs for definition changes.
- **Residual Risk:** Authorized but incorrect workflow deployment.

2. **Human Task Spoofing**

- **Threat:** An attacker completes or reassigns human tasks without proper authority.
- **Mitigation:** Strong RBAC, assignee validation, maker-checker separation where required, and full audit traceability for task lifecycle changes.
- **Residual Risk:** Compromised user credentials.

3. **Compensation Failure Cascade**

- **Threat:** Workflow rollback fails partially, leaving inconsistent cross-domain state.
- **Mitigation:** Explicit compensation contracts, durable state persistence, failed-rollback marking, and operational escalation for manual cleanup.
- **Residual Risk:** External systems without compensating semantics.

4. **SLA Manipulation**

- **Threat:** Deadlines or escalation thresholds are changed to conceal overdue regulatory or operational workflows.
- **Mitigation:** Version-pinned value catalogs and workflow metadata, approval-controlled updates, and observability on SLA breaches and overrides.
- **Residual Risk:** Misconfigured but approved threshold updates.

5. **Cross-Tenant Workflow Data Exposure**

- **Threat:** Workflow context or human-task payloads leak across tenants or jurisdictions.
- **Mitigation:** Tenant-aware scoping, encrypted context storage, access controls on task data, and audit logging of workflow inspection and export operations.
- **Residual Risk:** Application-layer authorization defect.

**Security Controls:**

- Approved and versioned workflow-definition promotion
- RBAC and assignee validation for human tasks
- Durable state with explicit compensation semantics
- Version-pinned metadata and value catalogs
- Encryption of workflow context and task payloads
- Audit logging for workflow and task operations

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added a threat-model section for workflow-definition and human-task risks.
- Added changelog metadata for future epic maintenance.
