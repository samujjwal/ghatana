# 17. Alerts & Notifications Page – Deep-Dive Spec

> **Status:** Partially implemented. The current `/alerts` route is a live launcher-backed operator triage surface for list, acknowledge, resolve, grouping, suggestions, rules, and stream health, while broader channel-management and incident workflows remain future work.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide the **alerts and notifications operating model** through a live operator triage surface without pretending the broader incident-management roadmap is already complete.

**Primary goals:**

- Support live alert triage, acknowledge, resolve, grouping, rule visibility, and route/stream truth from the launcher.
- Keep channel management, saved views, and broader incident workflows explicitly future-scoped.

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
   - User opens Alerts and sees current incidents, grouped alert clusters, suggestions, and route/stream truth for the current deployment.

2. **Managing alert rules**
   - User reviews or edits launcher-backed alert rules while calmer disclosure keeps active incidents primary.

3. **Subscribing a channel**
   - Future state: user adds a Slack channel and email list after delivery-channel integrations are productized.

---

## 3. Content & Layout Overview

Current shipped layout:

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
   - Future state only: settings for Slack/webhook/email integration.

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
5. Integrate with operator insights and query/runtime anomalies once those alert contracts are live.

---

## 6. Modern UI/UX Nuances and Features

- **Real-time updates:**
   - Future-state only: use websockets/EventSource to push new alerts without page reload once canonical stream contracts exist.
- **Saved views:**
   - Future-state only: allow users to save filters as named views (e.g., "My team’s alerts").
- **Keyboard and accessibility:**
   - Support navigation, filtering, and acknowledgments via keyboard once the page is backed by live contracts.

---

## 7. Coherence with App Creator / Canvas & Platform

- Alerts may be generated from workflows created in Data Cloud and from apps/pipelines across the platform.
- App Creator and AEP UIs should be able to deep-link into this page with pre-filtered contexts.
- Alert rules should be aware of governance settings (e.g., extra sensitivity for PII-related metrics).

---

## 8. Links to More Detail & Working Entry Points

- Intelligent Hub and insights surfaces: `01_dashboard_page.md` plus operator-only pages that would hand off into alerts when the runtime supports it.
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
