EPIC-ID: EPIC-O-01
EPIC NAME: Operator Console
LAYER: OPERATIONS
MODULE: O-01 Operator Console
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the O-01 Operator Console module, providing the unified operator-facing control surface for managing the Siddhanta platform in production. This epic addresses the P1 gap identified in the platform review by building on K-13 Admin Portal, K-02 metadata governance, and W-01 workflow orchestration to add incident response workflows, runbook-guided procedures, change management, capacity planning, disaster recovery, and on-call management. It ensures that operations teams can execute routine and exceptional procedures through schema-driven tasks, governed value catalogs, and auditable workflow state rather than ad hoc portal rewrites.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Incident response workflows (detection, triage, escalation, resolution, post-mortem).
  2. Automated and operator-guided response procedures (restart service, clear cache, scale resources, rollback deployment).
  3. Change management workflows (change request, approval, execution, verification, rollback).
  4. Capacity planning and forecasting (resource utilization trends, growth projections, scaling recommendations).
  5. Disaster recovery procedures (backup, restore, failover, data recovery).
  6. On-call rotation and escalation management.
  7. Operational dashboards and health checks.
  8. Knowledge base and documentation management.
  9. Metadata-driven operator forms, resolution codes, and tenant-scoped value catalogs rendered through K-13.
- **Out-of-Scope:**
  1. Platform monitoring (handled by K-06 Observability).
  2. Platform development (handled by engineering teams).
- **Dependencies:** EPIC-K-06 (Observability), EPIC-K-07 (Audit Framework), EPIC-K-10 (Deployment Abstraction), EPIC-K-13 (Admin Portal), EPIC-PU-004 (Platform Manifest)
- **Kernel Readiness Gates:** K-06, K-07, K-10, K-13, PU-004
- **Module Classification:** Cross-Cutting Operations Layer

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Incident Management:** The module must provide incident tracking (creation, assignment, escalation, resolution) with integration to K-06 alerting and PagerDuty/OpsGenie.
2. **FR2 Response Procedure Library:** The module must provide a library of automated and operator-guided response procedures (restart service, clear cache, scale deployment, rollback release) executable via K-13 Admin Portal or CLI.
3. **FR3 Procedure Execution Engine:** The module must execute response procedures safely with pre-checks, execution steps, schema-driven human tasks, post-checks, and automatic rollback on failure.
4. **FR4 Change Management:** The module must provide change request workflows (submit, review, approve, schedule, execute, verify) with maker-checker controls for production changes.
5. **FR5 Capacity Planning:** The module must analyze resource utilization trends (CPU, memory, disk, network) and generate capacity forecasts and scaling recommendations.
6. **FR6 Disaster Recovery:** The module must provide DR procedures (backup verification, restore testing, failover execution, RTO/RPO tracking) with automated testing.
7. **FR7 On-Call Management:** The module must manage on-call rotations, escalation policies, and shift handoffs with integration to PagerDuty/OpsGenie.
8. **FR8 Health Checks:** The module must provide operational health dashboards (service status, dependency health, SLO compliance, incident trends).
9. **FR9 Knowledge Base:** The module must maintain a searchable knowledge base of runbooks, troubleshooting guides, architecture diagrams, and operational procedures.
10. **FR10 Post-Incident Review Workflow:** The module must provide post-incident review templates and workflows (incident timeline, root cause analysis, action items, follow-up tracking) from versioned metadata so additive field changes and resolution-code updates do not require portal rebuilds.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The operational framework and automation are jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Jurisdiction-specific operational procedures (e.g., Nepal market hours, regulatory reporting deadlines) are configuration data.
3. **Resolution Flow:** N/A
4. **Hot Reload:** Procedures, review schemas, and allowed value catalogs can be updated dynamically.
5. **Backward Compatibility:** In-flight incidents and change records remain pinned to the metadata version active when work began; additive updates apply only to new work items unless explicitly migrated.
6. **Future Jurisdiction:** New jurisdiction operational procedures added as configuration.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `Incident`: `{ incident_id: UUID, severity: Enum, title: String, description: String, status: Enum, assigned_to: String, created_at: Timestamp, resolved_at: Timestamp }`
  - `Runbook`: `{ runbook_id: String, name: String, description: String, steps: List<RunbookStep>, version: Int, last_executed: Timestamp }`
  - `OperatorTaskSchemaRef`: `{ schema_key: String, schema_version: String, workflow_type: String, compatibility_class: Enum }`
  - `ChangeRequest`: `{ change_id: UUID, type: Enum, description: String, risk_level: Enum, scheduled_at: Timestamp, approved_by: String, status: Enum }`
  - `CapacityForecast`: `{ forecast_id: UUID, resource_type: Enum, current_utilization: Decimal, projected_utilization: Decimal, forecast_date: Date, recommendation: String }`
- **Dual-Calendar Fields:** N/A (operational infrastructure)
- **Event Schema Changes:** `IncidentCreated`, `IncidentResolved`, `RunbookExecuted`, `ChangeRequestApproved`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                   |
| ----------------- | ------------------------------------------------------------------------------------------------------------- |
| Event Name        | `IncidentCreated`                                                                                             |
| Schema Version    | `v1.0.0`                                                                                                      |
| Trigger Condition | A new incident is created (manually or via alert).                                                            |
| Payload           | `{ "incident_id": "...", "severity": "CRITICAL", "title": "...", "assigned_to": "...", "created_at": "..." }` |
| Consumers         | On-Call System, Notification Service, Audit Framework                                                         |
| Idempotency Key   | `hash(incident_id)`                                                                                           |
| Replay Behavior   | Updates the materialized view of active incidents.                                                            |
| Retention Policy  | Permanent.                                                                                                    |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CreateIncidentCommand`                                              |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Severity valid, title provided, requester authorized                 |
| Handler          | `IncidentCommandHandler` in O-01 Operator Console                    |
| Success Event    | `IncidentCreated`                                                    |
| Failure Event    | `IncidentCreationFailed`                                             |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                                                                    |
| ---------------- | -------------------------------------------------------------------------------------------------------------- |
| Command Name     | `ExecuteRunbookCommand`                                                                                        |
| Schema Version   | `v1.0.0`                                                                                                       |
| Validation Rules | Runbook exists, parameters valid, requester authorized, schema version resolvable, MFA verified for production |
| Handler          | `RunbookCommandHandler` in O-01 Operator Console                                                               |
| Success Event    | `RunbookExecuted`                                                                                              |
| Failure Event    | `RunbookExecutionFailed`                                                                                       |
| Idempotency      | Command ID must be unique; duplicate commands return original result                                           |

| Field            | Description                                                           |
| ---------------- | --------------------------------------------------------------------- |
| Command Name     | `ApproveChangeCommand`                                                |
| Schema Version   | `v1.0.0`                                                              |
| Validation Rules | Change request exists, risk assessed, maker-checker approval obtained |
| Handler          | `ChangeCommandHandler` in O-01 Operator Console                       |
| Success Event    | `ChangeRequestApproved`                                               |
| Failure Event    | `ChangeApprovalFailed`                                                |
| Idempotency      | Command ID must be unique; duplicate commands return original result  |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Predictive Analytics / Root Cause Analysis
- **Workflow Steps Exposed:** Incident triage, root cause analysis, capacity forecasting.
- **Model Registry Usage:** `incident-classifier-v1`, `rca-analyzer-v1`, `capacity-forecaster-v1`
- **Explainability Requirement:** AI classifies incident severity and suggests likely root causes based on symptoms and historical patterns. AI forecasts capacity needs based on growth trends.
- **Human Override Path:** Operator reviews AI suggestions and makes final decisions.
- **Drift Monitoring:** Prediction accuracy tracked against actual outcomes.
- **Fallback Behavior:** Manual incident triage and capacity planning.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                        |
| ------------------------- | ----------------------------------------------------------------------- |
| Latency / Throughput      | Runbook execution initiation < 5 seconds                                |
| Scalability               | Support 1,000 concurrent runbook executions                             |
| Availability              | 99.9% uptime                                                            |
| Consistency Model         | Strong consistency for change requests                                  |
| Security                  | Runbook execution requires MFA for production; all actions audited      |
| Data Residency            | Operational data stored globally                                        |
| Data Retention            | Incidents retained 2 years; runbook execution logs 1 year               |
| Auditability              | All operational actions logged [LCA-AUDIT-001]                          |
| Observability             | Metrics: `incident.mttr`, `runbook.success_rate`, `change.failure_rate` |
| Extensibility             | New procedures, forms, and value catalogs via metadata and DSL          |
| Upgrade / Compatibility   | Procedure and task-schema versioning supported                          |
| On-Prem Constraints       | Fully functional locally                                                |
| Ledger Integrity          | N/A                                                                     |
| Dual-Calendar Correctness | N/A                                                                     |

---

#### Section 10 — Acceptance Criteria

1. **Given** a critical alert from K-06, **When** the threshold is breached, **Then** an incident is automatically created, assigned to the on-call engineer, and a page is sent.
2. **Given** a response procedure for restarting a failed service, **When** executed, **Then** it verifies the service is down, restarts it, waits for health checks to pass, and logs the execution.
3. **Given** a change request for a production deployment, **When** submitted, **Then** it requires approval from two reviewers before execution is allowed.
4. **Given** a capacity forecast showing 80% CPU utilization in 30 days, **When** generated, **Then** it recommends scaling up and creates a change request for review.
5. **Given** a disaster recovery test, **When** executed, **Then** it restores a backup to a test environment, verifies data integrity, and measures RTO/RPO.
6. **Given** an incident resolved, **When** the post-incident review workflow is triggered, **Then** it creates a metadata-driven template with incident timeline, root cause fields, and action item tracking.
7. **Given** a new additive field is activated for the post-incident review schema, **When** the next review opens, **Then** the field renders without affecting already-closed reviews pinned to the older schema version.
8. **Given** an on-call shift handoff, **When** the shift ends, **Then** the system notifies the next on-call engineer and transfers active incidents.

---

#### Section 11 — Failure Modes & Resilience

- **Runbook Execution Failure:** Automatic rollback to pre-execution state; incident created for manual intervention.
- **On-Call System Unavailable:** Fallback to email/SMS notifications; manual escalation.
- **Change Execution Failure:** Automatic rollback to previous platform manifest version; incident created.
- **Capacity Forecast Inaccurate:** Human review of recommendations before execution.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                                             |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Metrics             | `incident.count`, `incident.mttr`, `runbook.execution.duration`, `change.success_rate`, dimensions: `severity`, `runbook_id` |
| Logs                | Structured: `incident_id`, `runbook_id`, `change_id`, `action`, `status`                                                     |
| Traces              | Span `Runbook.execute`                                                                                                       |
| Audit Events        | Action: `ExecuteRunbook`, `ApproveChange`, `ResolveIncident` [LCA-AUDIT-001]                                                 |
| Regulatory Evidence | Change management audit trail for compliance                                                                                 |

---

#### Section 13 — Compliance & Regulatory Traceability

- Change management and release control [LCA-AUDIT-001]
- Operational resilience [ASR-OPS-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `OpsClient.createIncident(details)`, `OpsClient.executeRunbook(runbookId, params)`, `OpsClient.submitChangeRequest(details)`.
- **Runbook DSL:** YAML/JSON schema for defining runbook steps.
- **Schema/UI Extension Points:** K-02 task schemas, resolution-code catalogs, and response-procedure descriptors rendered through K-13.
- **Jurisdiction Plugin Extension Points:** N/A

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                                    | Expected Answer                                                       |
| --------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                        | Yes, operational procedures are configurable.                         |
| Can new response procedures and review forms be added without code changes? | Yes, via runbook DSL plus metadata-driven schemas and value catalogs. |
| Can this run in an air-gapped deployment?                                   | Yes, fully self-contained.                                            |
| Can runbooks be tested before production use?                               | Yes, dry-run mode supported.                                          |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **Privileged Runbook Abuse**

- **Threat:** An operator or attacker executes high-impact runbooks to disrupt services or conceal incidents.
- **Mitigation:** MFA for production execution; RBAC and maker-checker for risky changes; dry-run validation; immutable execution audit trails.
- **Residual Risk:** Compromised privileged operator account.

2. **Workflow Metadata Tampering**

- **Threat:** Runbook steps, task schemas, or value catalogs are altered to bypass controls or misroute operations.
- **Mitigation:** Version-pinned metadata, governed approvals, manifest-backed deployments, and audit logging of schema/catalog changes.
- **Residual Risk:** Supply-chain compromise of approved metadata artifacts.

3. **Incident Suppression**

- **Threat:** Critical incidents are hidden or downgraded within the console.
- **Mitigation:** Independent alert ingestion from K-06; immutable incident-history records; escalation timers; cross-checks with audit and notification trails.
- **Residual Risk:** Coordinated compromise across multiple operational systems.

4. **Knowledge Base Poisoning**

- **Threat:** Malicious or stale runbook content leads operators into unsafe remediation steps.
- **Mitigation:** Versioned runbook reviews, approval workflow, execution-history feedback loops, and deprecation of stale procedures.
- **Residual Risk:** Human reliance on recently approved but flawed procedures.

5. **Operational Data Exposure**

- **Threat:** Console access leaks sensitive infrastructure, incident, or change-management data.
- **Mitigation:** Least-privilege RBAC, encryption at rest and in transit, tenant-aware scoping, and full access auditing.
- **Residual Risk:** Insider misuse of legitimate access.

**Security Controls:**

- MFA and RBAC for privileged operations
- Maker-checker for high-risk changes
- Version-pinned schemas and catalogs
- Immutable audit logging for runbooks and incidents
- Dry-run validation for operational procedures
- Encryption of console and workflow data

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added a threat-model section for operator-console control risks.
- Added changelog metadata for future epic maintenance.
