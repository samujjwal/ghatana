# Software-Org Web App – Page, Feature, and Action Inventory

**Scope:** Routes defined in `src/app/Router.tsx` plus supporting layout shell.

**Goal:** For each page:

- Explain intention in plain language.
- List what content is shown.
- Explain why that content matters.
- List the main actions a user can take.
- Call out gaps + enhancement ideas that support representing:
  - Features & capabilities
  - Information & data flows
  - Workflows & decisions
  - Reporting & observability

---

## 0. Application Shell & Navigation

**Files:**

- `src/app/App.tsx`
- `src/app/Layout.tsx`

### Intention

- Provide a consistent frame around all pages: navigation sidebar, header, theme/tenant selectors, and error handling.

### Content Shown

- Persistent sidebar with links to:
  - Dashboard, Departments, Workflows, HITL Console, Event Simulator, Reports, AI Intelligence, Security.
- Header with:
  - Product title: "AI-First DevSecOps".
  - Tenant selector (multi-tenant filter).
  - Theme selector (System / Light / Dark).
  - Simple user menu icon.
- Global error boundary that shows a friendly message if something breaks.

### Why This Matters

- Sets the mental model: this is a DevSecOps control center.
- Sidebar labels become the "information architecture" for features.
- Tenant and theme establish personalization and multi-tenant context.

### Key Actions

- Switch between feature areas using sidebar links.
- Change tenant filter (affects dashboard and other pages using `selectedTenantAtom`).
- Change theme.
- Recover from unexpected errors via the error boundary reload button.

### Gaps & Enhancements

- **Global context banner:** Add a small banner that always explains _what organization/environment_ you are looking at (e.g., Tenant, Env, Region) to reinforce data context.
- **Active page description:** Show a short sentence under the page title explaining in layman terms what this specific page is for.
- **Cross-page help:** Add a contextual "?" icon per page that links directly into the relevant section of the Help Center.

---

## 1. `/` – Control Tower Dashboard

**File:** `src/features/dashboard/Dashboard.tsx`

### Intention (Plain Language)

Give leaders a **single-screen overview** of how software delivery and reliability are doing across the whole organization.

### Content Shown

- **Filters bar:**
  - Tenant selector (All / Tenant A / Tenant B).
  - Time range (7d / 30d / 90d / Custom).
  - "Compare Mode" toggle for period-over-period comparison.
- **Key Metrics section:** grid of KPI cards from `useOrgKpis`, including (by description):
  - Deployments, Change Failure Rate, Lead Time, MTTR, Security posture, Cost (exact labels driven by mock data).
- **Event Timeline:** visual timeline of recent events (deploys, tests, features, incidents).
- **AI Insights sidebar:**
  - Cards with title, insight text, confidence %, reasoning text, HITL-like status (pending / approved).
  - Actions: Approve, Defer, Reject (currently log to console).
- **Comparison mode info bar:** description when compare is enabled.

### Why This Content Makes Sense

- KPIs map directly to standard DevOps/DevSecOps metrics; this is how leadership evaluates system health.
- Tenant + time filters are the minimum needed to slice data by **who** and **when**.
- The timeline visually connects **events** (deploys, tests, incidents) to KPI movements.
- AI insights show _machine-suggested decisions_ with confidence and reasoning, aligning with the AI-first goal.

### Key Actions

- Change tenant/time range to inspect different slices of performance.
- Turn on Compare Mode to see trends versus previous period.
- Click KPI cards (today: just logs) as the natural place for drill-down into departments, services, or detailed reports.
- Review AI insights and mark them as approved / deferred / rejected (once wired to a backend).

### Gaps & Enhancements

- **Missing data lineage:** No way to click a KPI and see **which departments, workflows, or models** drive it. Add drill-down to Departments, Workflows, or Reports.
- **No decision history:** AI insight approvals are not persisted; we do not show which suggestions were applied, when, or by whom.
- **Limited incident visibility:** Timeline shows only a few mock event types; it does not connect directly to **Real-Time Monitor** or **Automation Engine** for remediation.
- **No explicit SLOs:** KPIs are shown, but target/SLO and breach state are not clearly surfaced to non-experts.

---

## 2. `/departments` – Departments Directory

**File:** `src/features/departments/DepartmentList.tsx`

### Intention

Show all software departments/teams, how automated they are, and let users jump into a specific department.

### Content Shown

- Page header:
  - Title: "Departments".
  - Summary: number of departments and total active agents.
- Search and filters:
  - Free-text search by name/description.
  - Filter by status (All / Active Only).
  - Sort by Name, Activity, or Automation.
- Department grid:
  - Cards showing name, description, active agents, automation level, and quick stats (via `DepartmentCard`).

### Why This Content Makes Sense

- Departments are the **organizational slice** of the platform (who owns what).
- Quick stats give a sense of **where automation is high or low** and where AI agents are active.
- Search + filters support common questions like "which team is least automated?".

### Key Actions

- Search and filter to find the right department.
- Click a department card to open the **Department Detail** page.

### Gaps & Enhancements

- **No creation/management:** There is no way to add, edit, or archive departments from this page.
- **Missing link to workflows:** We don’t show which workflows or automation playbooks are associated with each department.
- **No risk signal:** Automation level is shown, but there is no simple risk indicator (e.g., low automation + high incidents).

---

## 3. `/departments/:id` – Department Detail

**File:** `src/features/departments/DepartmentDetail.tsx`

### Intention

Provide a **deep view** into one department: performance, automation, and (later) its agents, workflows, and playbooks.

### Content Shown

- Header:
  - Back link to Departments list.
  - Department name and description.
  - "Configure Playbook" button (opens `PlaybookDrawer`).
- Quick stats tiles:
  - Teams count, Active agents, Automation %, status.
- Tabs:
  - **Overview:**
    - KPI grid (Deployment Frequency, Lead Time, MTTR, Change Failure Rate, Team Size, Active Agents).
    - Automation status card with an enable/disable toggle.
  - **Agents:** placeholder text.
  - **Workflows:** placeholder text.
  - **Playbooks:** placeholder + "Add Playbook" CTA.

### Why This Content Makes Sense

- Mirrors org-level KPIs but focused on **one department**, so leaders can see how this group contributes.
- Tabs separate concerns: people (agents), process (workflows), automation logic (playbooks).
- The automation toggle hints at controlling whether this department is managed by Automation Engine.

### Key Actions

- Turn automation on/off for this department (currently local state only).
- Open the Playbook configuration drawer / Add Playbook.
- (Future) Navigate through agents, workflows, playbooks when tab content is implemented.

### Gaps & Enhancements

- **Unimplemented tabs:** Agents, Workflows, and Playbooks content is missing; cannot see real **data flows or decision logic**.
- **Toggle not wired:** Automation toggle doesn’t persist or reflect actual automation status in Automation Engine.
- **No incident/report linkage:** Department KPIs don’t link to incidents, reports, or simulator events relevant to the department.

---

## 4. `/workflows` – Workflow Explorer

**File:** `src/features/workflows/WorkflowExplorer.tsx`

### Intention

Show a list of automation workflows/pipelines, their health, and allow users to inspect and run them.

### Content Shown

- Stats cards:
  - Active pipelines, Healthy count, Running count, Avg success rate.
- Filters:
  - Status filter (All, Healthy, Running, Degraded).
- Pipeline list:
  - For each pipeline: name, department, status badge, last run, duration, success rate, run count.
- Details panel:
  - Selected pipeline’s department, status, success rate, run stats.
  - Actions: "Run Now" and "Edit".

### Why This Content Makes Sense

- Represents **workflows as first-class objects** in the system.
- Health and success rate convey operational quality of automated processes.
- Run & Edit are the primary lifecycle actions on workflows.

### Key Actions

- Filter pipelines by health state.
- Select a pipeline to view detailed metrics.
- Trigger a run of a pipeline.
- (Future) Edit pipeline configuration.

### Gaps & Enhancements

- **No visual flow:** There is no visual representation of the workflow steps or data flow.
- **No link to Automation Engine:** This page is conceptually close to `/automation-engine` but is not clearly integrated (shared data/model).
- **No ownership details:** Pipelines list a department, but not owners, SLOs, or linked KPIs.

---

## 5. `/hitl` – HITL Console

**File:** `src/features/hitl/HitlConsole.tsx`

### Intention

Provide a **human-in-the-loop console** where people review and approve AI-proposed actions before they run.

### Content Shown

- Hero stats:
  - Pending actions, Average response time, Open incidents, SLA breaches.
- Filters:
  - Priority (P0–P2), Type (remediate, quarantine, refactor), Department, Free-text search.
- ActionQueue table (left pane):
  - Virtualized queue of AI actions requiring review.
- ActionDetailDrawer (right pane):
  - Detailed context, reasoning, impact, and controls per action.
- Keyboard shortcut tips (A=Approve, D=Defer, R=Reject).

### Why This Content Makes Sense

- Captures **decision points** where humans override or approve automation.
- Aligns with safety and compliance for high-risk actions (production changes, security remediation).
- Filters and keyboard shortcuts support high-volume triage.

### Key Actions

- Filter the queue by priority, type, or department.
- Search the queue.
- Select an action to view its details.
- Approve, defer, or reject actions (once wired).

### Gaps & Enhancements

- **No explicit link to origin:** We don’t show which page or workflow triggered each HITL item (e.g., AI Intelligence, Automation Engine, Real-Time Monitor).
- **No decision history:** Users cannot see past approvals, rejections, or reasons.
- **No risk scoring explanation:** Priority is shown (P0/P1/P2) but there isn’t a simple explanation of what that means to non-experts.

---

## 6. `/simulator` – Event Simulator

**File:** `src/features/simulator/EventSimulator.tsx`

### Intention

Let developers and operators **simulate events** (deployments, security alerts, test failures, performance issues) to test how the system reacts.

### Content Shown

- Event template buttons:
  - Deployment completed, Security alert, Test failed, Performance degradation.
- JSON editor for event payload:
  - Free-text JSON with validation.
- Send/Reset buttons.
- Stats cards:
  - Sent events count, Last event type, Number of templates, Status.
- Event history list:
  - Last 50 events with timestamp, type, JSON payload.

### Why This Content Makes Sense

- Represents **raw events** that drive dashboards, workflows, and automation.
- Allows safe testing without touching production sources.
- JSON editor gives full control for advanced scenarios.

### Key Actions

- Load a template event.
- Edit the JSON payload and validate.
- Send a simulated event and inspect the history.

### Gaps & Enhancements

- **No visible destination:** Page doesn’t show where simulated events go (which services, dashboards, or workflows are affected).
- **No linkage to patterns:** For Ghatana’s pattern engine, we don’t show which patterns or rules were triggered by each event.
- **No end-to-end trace:** Cannot jump from a simulated event to its impact on KPIs, alerts, or workflows.

---

## 7. `/reports` – Reporting Dashboard

**File:** `src/features/reporting/ReportingDashboard.tsx`

### Intention

Serve as a **report hub** where users can pick predefined reports, see high-level metrics, and export them.

### Content Shown

- Report templates list:
  - Weekly KPIs, Security Findings, Deployment Trends, Team Performance.
- Selected report panel:
  - Title, category, last updated.
  - Export buttons: Export PDF, Download CSV, Schedule.
  - Metrics cards (label, value, trend text).
  - Placeholder for detailed chart/visualization.

### Why This Content Makes Sense

- Summarizes **longer-term trends** beyond real-time dashboards.
- Splits reports by persona (Executive, Security, Engineering, Operations).
- Scheduling & export make it usable for regular review and stakeholder updates.

### Key Actions

- Choose a report template.
- Export as PDF or CSV.
- Schedule recurring reports (conceptually; currently only UI).

### Gaps & Enhancements

- **No custom builder:** Users cannot define their own report layouts or metrics.
- **No filter controls:** Time range, tenant, and environment filters are implicit or missing.
- **No link back to sources:** Reports don’t link to KPIs, incidents, models, or workflows that produce the numbers.

---

## 8. `/security` – Security & Compliance Dashboard

**File:** `src/features/security/pages/SecurityDashboard.tsx`

### Intention

Provide a **security operations and compliance** view: who can do what, what has happened, and whether we meet standards.

### Content Shown

- Header:
  - Title, overall status message, counts for active users and events.
- Tabs:
  - Access Control: `UserAccessPanel`.
  - Audit Log: `AuditLog`.
  - Compliance: `ComplianceStatus` (SOC2, GDPR, HIPAA, etc.).
- Footer actions:
  - Manage API Keys, User Management, Export Audit Log.

### Why This Content Makes Sense

- Groups all **security-related information** in one place.
- Access, audit, and compliance are the core pillars of security operations.
- Export supports external audits and regulators.

### Key Actions

- Switch between Access, Audit, and Compliance views.
- Manage API keys and users.
- Export the audit log.

### Gaps & Enhancements

- **No linkage to alerts:** Security incidents and alerts from Real-Time Monitor or AI Intelligence aren’t surfaced here.
- **Limited explanation:** Compliance badges and statuses may be hard to interpret without a plain-language explanation of risk and next steps.
- **No mapping to data flows:** It’s not obvious which systems and data flows each compliance status covers.

---

## 9. `/ai` – AI Intelligence

**File:** `src/features/ai/AiIntelligence.tsx`

### Intention

Surface **AI-generated insights and recommendations** across quality, performance, security, and anomalies, with human approval controls.

### Content Shown

- Stats cards:
  - Total Insights, Pending Review, Approved, Avg Confidence.
- Filters:
  - Status filter (All, Pending, Approved).
- Insights list:
  - Cards with title, short reasoning preview, confidence badge, category badge (Security, Quality, Performance, Anomaly).
- Insight details panel:
  - Full reasoning, recommendation, estimated benefit.
  - Buttons: Approve, Defer, Reject (or Approved badge).

### Why This Content Makes Sense

- Concentrates **AI reasoning** in one place, instead of scattering it across pages.
- Categories match core DevSecOps concerns.
- Approval workflow aligns with governance and change management.

### Key Actions

- Filter insights by status.
- Select an insight and read detailed reasoning.
- Approve, Defer, or Reject recommendations.

### Gaps & Enhancements

- **No traceability:** We do not show which metrics, events, or models each insight is based on.
- **No link to execution:** Approving an insight does not (yet) connect to Automation Engine, HITL Console, or specific workflows.
- **No historical view:** There is no list of past insights, decisions taken, and resulting impact.

---

## 10. `/models` – Model Catalog

**File:** `src/features/models/pages/ModelCatalog.tsx`

### Intention

Act as a **catalog and control panel for machine learning models**: versions, performance, deployment, testing, and comparison.

### Content Shown

- Header:
  - Title, buttons: + Deploy Model, Run Tests, Compare (multi-select aware).
- Catalog view:
  - List of models (from `useModelRegistry`) showing:
    - Name, status (Active/Testing/Deprecated/Archived), version.
    - Type, performance metrics (accuracy, precision, recall, F1), last updated, deployed time.
    - Checkbox per model for comparison.
- Alternate views:
  - Details view (`ModelDetails`) for a selected model.
  - Comparison view (`ModelComparison`) for multiple selected models.
  - Test view (`TestRunner`) for running evaluations.

### Why This Content Makes Sense

- Centralizes **ML lifecycle management** for all models powering automation and insights.
- Exposes the core metrics ML engineers care about.
- Comparison and test runner support safe rollout and regression detection.

### Key Actions

- Deploy a model.
- Open detailed view for a model.
- Select models and open comparison view.
- Run tests in the Test Runner.

### Gaps & Enhancements

- **No visible link to production impact:** Models are not directly linked to the KPIs, alerts, or workflows they influence.
- **No governance:** No approvals, owners, or lifecycle stages beyond status.
- **Partial overlap with ML Observatory:** It’s not obvious how `/models` relates to `/ml-observatory` for users.

---

## 11. `/settings` – Settings & Preferences

**File:** `src/features/settings/pages/SettingsPage.tsx`

### Intention

Let individual users configure **personal preferences, notifications, integrations, and account details**.

### Content Shown

- Sidebar tabs:
  - General, Notifications, Integrations, Account.
- General:
  - Theme (light/dark/auto), timezone, date format, display options.
- Notifications:
  - Email, desktop, Slack toggles.
  - Alert type preferences (High latency, Model training failed, SLA breach, Security alert).
- Integrations:
  - Slack, GitHub, PagerDuty, Datadog cards with connection status and buttons.
- Account:
  - Email, full name, active sessions, password and account controls.
- Footer actions:
  - Save Changes, Cancel.

### Why This Content Makes Sense

- Covers the **user-level configuration surface** that influences how they see data (timezone, theme) and how they are notified.
- Integrations align with typical DevOps tooling.

### Key Actions

- Change theme, timezone, date format, and display options.
- Enable/disable different notification channels and alert types.
- Manage third-party integrations.
- Review active sessions; sign out others.
- Trigger password change or account deletion.

### Gaps & Enhancements

- **Persistence:** Settings are local to the component; they are not yet saved via an API.
- **No preview:** Changing theme or notification preferences doesn’t show a clear before/after preview.
- **No tenant-awareness:** Settings are global to the user but do not clearly distinguish per-tenant or per-environment preferences.

---

## 12. `/help` – Help Center

**File:** `src/features/help/pages/HelpCenter.tsx`

### Intention

Provide **self-service support**: FAQs, guides, and quick links to docs and support.

### Content Shown

- Hero search bar: free-text search input.
- Quick links:
  - Documentation, Tutorials, Community, Support.
- Guides & Tutorials:
  - Cards such as "Getting Started with Model Deployment", "Understanding Model Metrics", etc.
- FAQ section:
  - Category filter chips.
  - Expandable FAQ items with question, answer, views, feedback buttons (Helpful / Not helpful).
- Sidebar:
  - "Still need help?" support CTA.
  - Trending articles.
  - Resource links (API Reference, GitHub Examples, Status Page, Changelog).

### Why This Content Makes Sense

- Aligns to **product onboarding and troubleshooting** needs.
- Connects users to deeper documentation, examples, and support channels.

### Key Actions

- Search for help topics (UI present; logic to connect search to content TBD).
- Browse guides and FAQs.
- Filter FAQs by category.
- Mark answers as helpful or not.
- Click CTAs to contact support or open external resources.

### Gaps & Enhancements

- **Search not wired:** The search query is captured but not used to filter content.
- **No deep links:** Guides and FAQs are static texts, not linked to actual pages/features inside the app.
- **No personalization:** Help is not filtered by role (PM, Dev, SRE, Security) or context (current page).

---

## 13. `/export` – Data Export Utility

**File:** `src/features/export/pages/DataExportUtil.tsx`

### Intention

Allow users to **export data** (incidents, metrics, audit logs, models, alerts) in various formats and manage export jobs.

### Content Shown

- Export form:
  - Format selection (PDF, CSV, JSON, Excel).
  - Data type (Incidents, Metrics, Audit Log, Models, Alerts).
  - Date range (Today, Last 7 days, Last 30 days, Custom).
  - Column selection per data type.
  - Schedule (one-time/daily/weekly/monthly) with informational note.
- Sidebar:
  - Quick export presets.
  - Recent export history list with status and download actions.
  - Tips card explaining export behavior.

### Why This Content Makes Sense

- Bridges **operational data** with external reporting and archiving needs.
- Column selection makes exports meaningful to both technical and non-technical recipients.
- Export history supports traceability.

### Key Actions

- Configure an export job and run it.
- Choose and edit included columns.
- Pick a schedule for recurring exports.
- Download completed exports.

### Gaps & Enhancements

- **No real backend:** Exporting and history are mock-only; no real data is pulled.
- **No data lineage:** It’s not clear which internal pages or data sources a given export corresponds to.
- **No role-based defaults:** Different roles might need different default presets (e.g., Security vs. SRE).

---

## 14. `/ml-observatory` – ML Observatory

**File:** `src/pages/MLObservatory.tsx`

### Intention

Provide a **unified monitoring dashboard for ML models** in production, including drift detection, feature importance, training jobs, and A/B tests.

### Content Shown

- Status summary cards:
  - Active models, Healthy models, Drift status, Training jobs.
- Models grid:
  - Uses `MLModelCard` with health score, selection, and deploy action.
- Drift Detection section:
  - `DriftIndicator` per model with severity, drift score, and recommendations.
- Feature Importance section:
  - `FeatureImportanceChart` for the primary model.
- Side panels:
  - ModelComparisonPanel – compare multiple models.
  - TrainingJobsMonitor – show training jobs.
  - AbTestDashboard – show A/B experiments.

### Why This Content Makes Sense

- Concentrates **all ML operational signals** (performance, drift, training, experiments) into one observability page.
- Directly supports data scientists, MLOps, and reliability engineers.

### Key Actions

- Select models and deploy them.
- Inspect drift per model and consider retraining.
- Review training jobs and A/B tests.

### Gaps & Enhancements

- **Advanced language:** Concepts like drift, feature importance, and A/B tests are not explained in layman terms.
- **Fragmented model story:** Users must mentally connect this to the Model Catalog; there is no explicit navigation between them.
- **Missing link to incidents:** ML issues here aren’t tied to Real-Time Monitor alerts or Dashboard KPIs.

---

## 15. `/realtime-monitor` – Real-Time Monitor

**File:** `src/pages/RealTimeMonitor.tsx`

### Intention

Provide a **live health view of infrastructure and services** with streaming metrics, alerts, and anomalies.

### Content Shown

- Header:
  - Title, connection status indicator (Live / Reconnecting).
- System Health cards:
  - CPU, Memory, Disk Usage, Uptime.
- Real-time Metrics section:
  - Cards for selected metrics derived from `systemHealth`.
- Metric chart:
  - Time-series trend of a selected metric using `MetricChart`.
- Anomalies section:
  - `AnomalyDetector` showing anomaly list and status.
- Alerts sidebar:
  - Alert filter tabs (All, Critical, Warning, Info).
  - `AlertPanel` listing alerts with acknowledge actions.

### Why This Content Makes Sense

- Serves as the **NOC-style screen** for SREs and on-call engineers.
- Real-time flags quickly show if the system is degraded.
- Alerts and anomalies are the main **decision triggers** for remediation.

### Key Actions

- Monitor live system metrics.
- Select a metric and inspect its trend.
- Filter alerts by severity.
- Acknowledge alerts.
- Reconnect if streaming disconnects.

### Gaps & Enhancements

- **Service context:** Metrics are generic; they don’t map clearly to services, tenants, or workflows.
- **No playbook link:** Alerts don’t link to playbooks, runbooks, or Automation Engine workflows.
- **No incident timeline:** There is no combined timeline correlating metrics, alerts, and events from Event Simulator.

---

## 16. `/automation-engine` – Automation Engine

**File:** `src/pages/AutomationEngine.tsx`

### Intention

Offer a **control center for workflow automation**, including templates, executions, triggers, stats, and history.

### Content Shown

- Header:
  - Title, description, "+ Create Workflow" button.
- Stats cards:
  - Active workflows vs. total, executions (7d), success rate, average duration.
- Workflow templates list:
  - `WorkflowTemplateCard` entries with name, status, and controls.
- Execution Monitor:
  - Current execution details with cancel action.
- Right-side panels (for selected workflow):
  - Triggers via `TriggerPanel`.
  - Performance via `WorkflowStatistics`.
  - Recent executions via `ExecutionHistory` with retry.

### Why This Content Makes Sense

- Represents the **automation plane** of the platform – how processes are run without manual intervention.
- Hooks into orchestration (`useAutomationOrchestration`) to manage workflows, triggers, executions, and stats.

### Key Actions

- Create and edit workflows (visual builder entry point).
- Execute workflows and monitor real-time progress.
- Add or remove triggers (scheduled, event-based, etc.).
- Review performance stats and recent run history.
- Cancel or retry executions.

### Gaps & Enhancements

- **No business-language description:** Workflows are listed, but there’s no simple explanation like "This workflow auto-rolls back failing deployments".
- **No topology view:** We don’t see the end-to-end data flow or which systems/tasks each workflow touches.
- **Disconnect from HITL & AI pages:** It’s not obvious which workflows are triggered by AI insights, HITL decisions, or Real-Time Monitor alerts.

---

## Cross-Cutting Gaps & Enhancement Themes

1. **Traceability Across Pages**
   - Today, each page is strong in its own lane but connections are mostly implicit.
   - **Enhancements:**
     - Add click-throughs and breadcrumbs between Dashboard ↔ Departments ↔ Workflows ↔ Automation Engine ↔ Real-Time Monitor.
     - Attach deep links from AI Intelligence insights and HITL items to the affected workflows/models/metrics.

2. **Data Flow & Workflow Visualization**
   - Workflows, automation, and event flows are described but not visualized.
   - **Enhancements:**
     - Introduce simple diagrams or "flow chips" on pages (e.g., "Events → Workflows → Decisions → Reports").
     - Add a mini-map or diagram component for Workflow Explorer and Automation Engine.

3. **Decision Logging & Governance**
   - AI recommendations and HITL approvals are not persisted or surfaced historically.
   - **Enhancements:**
     - Add a shared "Decision Log" concept, accessible from AI Intelligence, HITL Console, Security, and Reports.
     - Show impact summaries: "Approving this insight reduced MTTR by X%".

4. **Layman-Friendly Language & Explanations**
   - Some advanced pages (ML Observatory, Real-Time Monitor, Automation Engine) use specialist terms without explanation.
   - **Enhancements:**
     - Add short, plain-language tooltips or "What is this?" info boxes.
     - Include simple examples like "Drift means the model is seeing different kinds of data than it was trained on".

5. **Role- and Context-Awareness**
   - Navigation and content are largely one-size-fits-all.
   - **Enhancements:**
     - Tailor certain pages or default filters based on role (PM, SRE, Security, ML).
     - Use the current route to drive contextual help, presets, and exports.
