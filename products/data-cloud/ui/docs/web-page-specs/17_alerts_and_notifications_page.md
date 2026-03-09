# 17. Alerts & Notifications Page – Deep-Dive Spec

> **Status:** Planned page – no concrete implementation in CES UI yet. This spec corresponds to the Notifications & Alerts section in `frontend_todo (1).md`.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **central view and configuration UI for alerts and notifications**, allowing users to see active alerts, configure alert rules, and manage delivery channels.

**Primary goals:**

- Show current and historical alerts across the Data Cloud.
- Allow users to define and manage alert rules (conditions, thresholds, and destinations).
- Integrate with external channels (Slack, email, webhooks).

**Non-goals:**

- Replacing existing observability stacks (Prometheus, Grafana, etc.).
- Low-level incident management (incident runbooks live elsewhere).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Platform engineer / SRE** monitoring system health.
- **Data engineer** monitoring pipelines and dataset freshness.
- **Governance/compliance** watching for policy violations.

**Key scenarios:**

1. **Monitoring active alerts**
   - User opens Alerts page and sees current open alerts grouped by severity.

2. **Configuring an alert rule**
   - User defines a rule: "Alert if success rate of workflow `nightly_etl` drops below 95% for 3 runs".

3. **Subscribing a channel**
   - User adds a Slack channel and email list as destinations for a set of alerts.

---

## 3. Content & Layout Overview

Proposed layout:

1. **Alerts overview section**
   - Summary cards: total open alerts, critical/high/medium counts.
   - Quick filters by severity, source (workflows/datasets/optimizer/governance).

2. **Alerts list**
   - Table of alerts with columns:
     - Severity, source (e.g., workflow, dataset), title, status, first/last seen, assignee/owner.
   - Filters by time range, status (open/acknowledged/resolved), and tags.

3. **Alert details panel**
   - When an alert is selected:
     - Description, affected entities, timeline of occurrences.
     - Links to source pages (Workflow Designer, Dataset Detail, Governance hub).

4. **Alert rule management**
   - List of rules with name, scope, condition, destinations, and enabled/disabled state.
   - Rule editor with:
     - Condition builder UI (metrics, thresholds, time windows).
     - Destination configuration (Slack, email, webhook callbacks).

5. **Channel management**
   - Settings for Slack/webhook/email integration:
     - Test buttons and health indicators.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Prioritization:**
  - Make it easy to see and act on the most critical alerts first.
- **Actionability:**
  - Provide clear links and suggestions for resolving issues (e.g., go to Workflow runs, Dataset Detail, Optimizer actions).
- **Noise reduction:**
  - Support de-duplication, grouping, and muting of low-value alerts.

---

## 5. Completeness and Real-World Coverage

In a mature system, the Alerts & Notifications UI should:

1. Ingest alerts from multiple sources: workflows, datasets, optimizer, governance, system health.
2. Support correlation of related alerts into incidents (even if incident management UI is external).
3. Reflect alert state transitions (open → acknowledged → resolved).
4. Provide an audit trail of rule changes.
5. Integrate with dashboard metrics (e.g., link from Dashboard anomalies to alerts).

---

## 6. Modern UI/UX Nuances and Features

- **Real-time updates:**
  - Use websockets/EventSource to push new alerts without page reload.
- **Saved views:**
  - Allow users to save filters as named views (e.g., "My team’s alerts").
- **Keyboard and accessibility:**
  - Support navigation, filtering, and acknowledgments via keyboard.

---

## 7. Coherence with App Creator / Canvas & Platform

- Alerts may be generated from workflows created in CES and from apps/pipelines across the platform.
- App Creator and AEP UIs should be able to deep-link into this page with pre-filtered contexts.
- Alert rules should be aware of governance settings (e.g., extra sensitivity for PII-related metrics).

---

## 8. Links to More Detail & Working Entry Points

- Dashboard: `01_dashboard_page.md` (which surfaces high-level anomalies).
- Workflow-related specs: `05_workflows_page.md`, `06_workflow_designer_canvas.md`.
- Dataset-related specs: `11_dataset_explorer_list_page.md`, `12_dataset_detail_insights_page.md`.
- Governance hub: `16_governance_and_security_hub_page.md`.

---

## 9. Gaps & Enhancement Plan

1. **Backend alerting model:**
   - Define a unified alert representation and APIs for ingestion and updates.

2. **Rule language and UI:**
   - Design a rule DSL and how it’s exposed via the rule builder.

3. **Channel integration:**
   - Decide which external channels are supported first and how secrets/credentials are managed.

4. **Multi-tenant behavior:**
   - Ensure alerts and rules are properly partitioned by tenant/project.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Alerts & Notifications
"Monitor and configure alerts across your Data Cloud"

[ Open: 5 ]  [ Critical: 1 ] [ High: 2 ] [ Medium: 2 ] [ Low: 0 ]

Filters: [Severity ▾] [Source ▾] [Status ▾] [Time Range ▾]

Alerts list (table)
-----------------------------------------------------------------------------
Severity  Source      Title                             Status   Last seen
-----------------------------------------------------------------------------
Critical  Workflow    Nightly ETL success rate < 95%   Open     2m ago
High      Dataset     Orders dataset freshness issue   Open     15m ago
...
```
