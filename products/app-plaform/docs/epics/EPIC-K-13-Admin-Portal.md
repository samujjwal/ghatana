EPIC-ID: EPIC-K-13
EPIC NAME: Admin Portal
LAYER: KERNEL
MODULE: K-13 Admin Portal
VERSION: 1.1.1

---

#### Section 1 — Objective

Deploy the K-13 Admin Portal, fulfilling Principle 12 (Single Pane of Glass). This unified web console provides a central interface for tenant management, config pack deployment, plugin lifecycle management, health dashboards, and audit viewing. It eliminates fragmented, domain-specific administrative UIs and ensures all operator actions are governed by strict RBAC and generate immutable audit events.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Unified web frontend (React/Angular or equivalent).
  2. Integration with K-01 IAM for authentication and RBAC.
  3. UI for Config Engine (K-02) to manage and deploy config packs.
  4. UI for Plugin Runtime (K-04) to install/upgrade/disable plugins.
  5. UI for Observability (K-06) health dashboards and alerts.
  6. UI for Audit Framework (K-07) to view logs and generate regulatory exports.
  7. Dual-calendar date picking and display tools (K-15 integration).
- **Out-of-Scope:**
  1. End-user/investor portals (e.g., trading screens) - these are Operator Packs or external clients.
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Config Engine), EPIC-K-04 (Plugin Runtime), EPIC-K-06 (Observability), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Unified Authentication:** Operators must log in via K-01 IAM, with MFA enforced based on role criticality.
2. **FR2 Role-Based Views:** The UI must dynamically hide/show modules and actions based on the operator's ABAC permissions.
3. **FR3 Maker-Checker Workflows:** The UI must support initiating, viewing, and approving maker-checker requests for sensitive actions (e.g., config changes, plugin upgrades).
4. **FR4 Dual-Calendar Interface:** All date pickers must allow input in either BS or Gregorian, instantly converting and displaying the other, leveraging K-15.
5. **FR5 Audit Viewer:** Provide a dedicated screen to query, filter, and export the immutable K-07 audit logs.
6. **FR6 AI Insights Dashboard:** Display AI-generated anomaly alerts (e.g., from K-09 or K-06) and provide interfaces for human override.
7. **FR7 Schema-Driven Forms:** The portal must render operator forms, approval tasks, and configuration editors from server-provided schemas and UI metadata rather than requiring bespoke frontend code for each variation.
8. **FR8 Dynamic Value Catalogs:** The portal must resolve and render dynamic option sets, bounded numeric controls, and tenant-scoped catalogs supplied by K-02 with effective-date and permission awareness.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The portal framework is jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Specific reporting templates or compliance dashboards can be dynamically loaded into the portal via Operator Packs (T3) or Jurisdiction Rule Packs (T2).
3. **Resolution Flow:** N/A
4. **Hot Reload:** The UI dynamically updates when new capabilities, schemas, or value catalogs are registered via the API Gateway after compatibility validation.
5. **Backward Compatibility:** In-flight approvals, operator tasks, and case workflows remain pinned to the schema version resolved at start time unless an approved migration rule is provided by K-02/W-01.
6. **Future Jurisdiction:** N/A

---

#### Section 5 — Data Model Impact

- **New Entities:** N/A (UI layer, no local storage; interacts via API Gateway)
- **Dual-Calendar Fields:** UI components handle `DualDate` natively.
- **Event Schema Changes:** N/A

---

#### Section 6 — Event Model Definition

- N/A (Emits API calls which backend services translate into events).

---

#### Section 7 — Command Model Definition

**Note:** The Admin Portal is a UI layer that invokes commands on underlying services (K-01, K-02, K-04, K-07, etc.). It does not define its own domain commands but rather provides user-friendly interfaces to trigger commands in other modules.

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CreateTenantCommand`                                                |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Tenant name unique, configuration valid, requester authorized        |
| Handler          | Delegates to K-01 IAM and K-02 Config Engine                         |
| Success Event    | `TenantCreated` (from K-01)                                          |
| Failure Event    | `TenantCreationFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Copilot Assist
- **Workflow Steps Exposed:** Dashboard views, search queries.
- **Model Registry Usage:** `admin-copilot-v1`
- **Explainability Requirement:** AI assists operators in finding specific audit trails or explaining complex config interactions using natural language.
- **Human Override Path:** AI provides suggestions; operator takes action.
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard search and navigation.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                      |
| ------------------------- | ----------------------------------------------------- |
| Latency / Throughput      | Page load < 1s; API responses < 200ms                 |
| Scalability               | Horizontally scalable stateless frontend              |
| Availability              | 99.99% uptime                                         |
| Consistency Model         | Read-your-writes for UI interactions                  |
| Security                  | Enforces secure sessions; strict CORS/CSP policies    |
| Data Residency            | UI served globally; data fetched adheres to residency |
| Data Retention            | N/A                                                   |
| Auditability              | All operator API requests audited via backend         |
| Observability             | Frontend metrics (Time to Interactive, API errors)    |
| Extensibility             | Micro-frontend architecture allowing pack injection   |
| Upgrade / Compatibility   | N/A                                                   |
| On-Prem Constraints       | Static assets bundled in deployment manifests         |
| Ledger Integrity          | N/A                                                   |
| Dual-Calendar Correctness | UI validation matches K-15                            |

---

#### Section 10 — Acceptance Criteria

1. **Given** an operator with read-only permissions, **When** they log into the portal, **Then** they can view the Audit Log and System Health but the "Deploy Config" button is disabled/hidden.
2. **Given** a new BS date is selected in the Date Picker for a report query, **When** selected, **Then** the Gregorian equivalent is displayed adjacent to it instantly.
3. **Given** a pending maker-checker request for a plugin upgrade, **When** the operator who created the request tries to approve it, **Then** the UI blocks the action and prompts that a different user must approve.
4. **Given** a task form or configuration editor changes by metadata only, **When** the portal reloads the schema, **Then** the new fields, allowed values, and validation hints appear without a frontend rebuild.
5. **Given** an in-flight approval created under schema version `v1`, **When** schema version `v2` is activated with additive changes only, **Then** the existing approval remains pinned to `v1` while newly created approvals render `v2`.

---

#### Section 11 — Failure Modes & Resilience

- **API Gateway Down:** UI displays a graceful offline message and attempts automatic reconnection.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                               |
| ------------------- | ---------------------------------------------- |
| Metrics             | Frontend load times, API error rates           |
| Logs                | Client-side error logs sent to K-06            |
| Traces              | Distributed trace starts at the browser client |
| Audit Events        | N/A                                            |
| Regulatory Evidence | N/A                                            |

---

#### Section 13 — Compliance & Regulatory Traceability

- System access controls and monitoring [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** N/A
- **Schema/UI Extension Points:** Dynamic form schemas, field widget registries, task-view descriptors, and schema-compatibility metadata resolved from backend metadata.
- **Jurisdiction Plugin Extension Points:** Micro-frontend injection points for custom dashboards.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                                                     | Expected Answer                                                                                                                 |
| -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                                         | Yes.                                                                                                                            |
| Can admins add or adjust operator forms and choice lists without shipping new frontend code? | Yes. Schema-driven rendering and value-catalog resolution handle those changes.                                                 |
| Can this run in an air-gapped deployment?                                                    | Yes, served locally.                                                                                                            |
| Can this module handle digital assets (tokenized securities, crypto)?                        | Yes. Admin UI renders token registries, wallet management, and digital asset configuration screens via dynamic form generation. |
| Is the design ready for CBDC integration or T+0 settlement?                                  | Yes. Real-time settlement status dashboards and instant config toggles are supported.                                           |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format where applicable.
- Restored the current `Admin Portal` naming in the epic header and module metadata.
