# 16. Governance & Security Hub – Deep-Dive Spec

> **Status:** Planned page – no concrete implementation in CES UI yet. This spec corresponds to the Governance & Security UI section in `frontend_todo (1).md`.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **central hub for governance and security** where users can manage roles, permissions, policies, PII scan results, and audit logs for the Data Cloud.

**Primary goals:**

- Offer a single entry point for governance and security operations.
- Expose role and permission management in a clear, auditable way.
- Provide UIs for policy definition and PII scan inspection.
- Surface and explore audit logs related to data access and changes.

**Non-goals:**

- Implementing backend auth or crypto; this page assumes those services already exist.
- Managing infrastructure-level security (handled by ops tooling).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Data governance lead / steward** managing data access and policies.
- **Security engineer** overseeing enforcement of PII and access controls.
- **Compliance/audit team** reviewing logs and attestations.

**Key scenarios:**

1. **Granting access to a team**
   - Governance user navigates to Roles & Permissions.
   - Adds a team to a role with access to specific datasets / workflows.

2. **Reviewing PII scan results**
   - User opens PII Scan Results view.
   - Filters for new or high-risk findings and assigns owners to remediate.

3. **Investigating suspicious activity**
   - User opens Audit Logs explorer.
   - Searches for access to a sensitive dataset during a time window.

---

## 3. Content & Layout Overview

The Governance & Security Hub is a **sectioned page (or mini-portal)** with navigation to sub-views:

1. **Overview section**
   - High-level summary cards:
     - Number of roles, users, and teams.
     - Policies in effect, PII findings, recent critical audit events.

2. **Role Manager**
   - Table of roles with descriptions and scopes.
   - Ability to view and edit which users/teams belong to each role.

3. **Permission Management**
   - Views of permissions by dataset, by user/team, or by role.
   - Ability to grant/revoke dataset- and workflow-level access (within policy constraints).

4. **Policy Builder UI**
   - UI for defining high-level access and data handling policies (e.g., who can access PII fields, retention rules).
   - Templates for common policy types.

5. **PII Scan Results Viewer**
   - List of scan findings (dataset/column, classification, confidence, status).
   - Filters by severity and remediation status.

6. **Audit Logs Explorer**
   - Timeline or table of access and change events.
   - Filters by user, dataset, action type, and time range.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clarity of impact:**
  - When changing roles or permissions, clearly show what access is being granted or revoked.
- **Traceability:**
  - Every governance action should be associated with an actor and timestamp.
- **Safe defaults:**
  - Guardrails around granting broad or risky permissions.
- **Readable security language:**
  - Use layman-friendly labels (e.g., "Can read orders data") alongside technical policy expressions.

---

## 5. Completeness and Real-World Coverage

For real deployments, this hub should:

1. Connect to a **central identity and access management** system.
2. Respect and reflect **row/column-level security** and masking policies.
3. Integrate PII findings from scanning services.
4. Provide exportable views for audits and compliance reports.
5. Offer APIs/links for automation (e.g., IaC-managed policies still visible here).

---

## 6. Modern UI/UX Nuances and Features

- **Diff and history views:**
  - Show how a role or policy has changed over time.
- **What-if previews:**
  - Before applying a policy, show which users/datasets it affects.
- **Inline warnings:**
  - Warn when policy changes may break downstream workflows or dashboards.

---

## 7. Coherence with App Creator / Canvas & Platform

- Governance settings here should apply consistently across:
  - CES collections and workflows.
  - SQL Workspace.
  - App Creator and AEP pipelines.
- App and workflow canvases may surface **governance badges** that link back to details in this hub.

---

## 8. Links to More Detail & Working Entry Points

- Dashboard: `01_dashboard_page.md` (high-level compliance and audit signals).
- Dataset-related specs: `11_dataset_explorer_list_page.md`, `12_dataset_detail_insights_page.md`.
- Workflow specs: `05_workflows_page.md`, `06_workflow_designer_canvas.md`.
- Backend governance plans: see `backend_todo (1).md` – Metadata, Governance & Security.

---

## 9. Gaps & Enhancement Plan

1. **Concrete information architecture:**
   - Decide if this is a single page with tabs or a sub-application with multiple routes.

2. **Permission model UI representation:**
   - Design how backend permission models map into an understandable UI.

3. **Integration with external IdPs:**
   - Clarify how SSO/IdP roles map into Data Cloud roles.

4. **PII workflow integration:**
   - Define workflow for resolving PII findings, including assignment and status tracking.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Governance & Security
"Manage access, policies, and data protection for your Data Cloud"

Tabs: [Overview] [Roles & Permissions] [Policies] [PII Scan Results] [Audit Logs]

[Overview]
-----------------------------------------------------------------------------
- Roles: 12    Users: 145    Policies: 28    Open PII Findings: 6
- Recent critical events: 3 (link to Audit Logs)
```
