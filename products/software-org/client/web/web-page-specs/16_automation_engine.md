# 16. Automation Engine – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 16. `/automation-engine` – Automation Engine](../WEB_PAGE_FEATURE_INVENTORY.md#16-automation-engine--automation-engine)

**Code file:**

- `src/pages/AutomationEngine.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Serve as the control center for workflow automation, where users can create, monitor, and tune workflows, triggers, and execution history.

**Primary goals:**

- Show **overall automation health** (active workflows, success rate, executions).
- List **workflow templates** with actions to create/edit.
- Monitor **current execution** and show **triggers, stats, and history** for a selected workflow.

**Non-goals:**

- Deep visual editing of every workflow step (could be a separate builder screen).
- Managing unrelated non-automation tasks.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **SRE / DevOps engineers** configuring auto-remediation.
- **Platform engineers** defining platform workflows.
- **Security engineers** automating responses to security alerts.

**Scenarios:**

1. **Configuring an auto-rollback workflow**
   - GIVEN: Team wants automatic rollback for failed deployments.
   - WHEN: SRE selects `Auto-Rollback Failed Deployment` template and customizes it.
   - THEN: Workflow runs automatically when deployment-related alerts are raised.

2. **Monitoring workflow executions**
   - GIVEN: An automation might be stuck.
   - WHEN: Engineer opens Automation Engine and checks `Current Execution` panel.
   - THEN: They see execution state and can cancel or retry.

3. **Tuning triggers**
   - GIVEN: Workflow triggers too often.
   - WHEN: Engineer edits triggers (thresholds, schedules).
   - THEN: Executions become more targeted.

---

## 3. Content & Layout Overview

From `AutomationEngine.tsx`:

- **Header:**
  - Title: `Automation Engine`.
  - Subtitle: `Manage workflows, triggers and automated run history`.
  - CTA: `+ Create Workflow`.

- **Stats row:**
  - Active Workflows / Total.
  - Executions (last 7 days).
  - Success Rate.
  - Avg Duration.

- **Main layout:**
  - Left (2/3): Workflow templates list + current execution monitor.
  - Right (1/3): Triggers, statistics, execution history for selected workflow.

- **Workflow templates list:**
  - `WorkflowTemplateCard` components with title, description, status, and actions.

- **Execution monitor:**
  - Shows current running workflow step, logs, and progress.

- **Right-side panels:**
  - `TriggerPanel` – event/time-based triggers.
  - `WorkflowStatistics` – aggregates like avg duration, success rate.
  - `ExecutionHistory` – list of recent runs with status, duration, and retry button.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear separation:**
  - Distinguish between templates (design time) and executions (runtime).
- **Status at a glance:**
  - Stats row and templates list should make it obvious whether automation is healthy.
- **Safe controls:**
  - Cancel/Retry buttons require confirmation for high-impact workflows.
- **Discoverability:**
  - Hover/tooltip hints explaining each panel.

---

## 5. Completeness and Real-World Coverage

Automation Engine should help answer:

- What automated workflows do we have, and what do they do?
- How often do they run, and how successful are they?
- What triggers them (alerts, schedules, API calls)?

Over time, it should integrate with:

- HITL Console (for HITL-approved runs).
- AI Intelligence (insights triggering workflows).
- Real-Time Monitor (alerts triggering remediation).

---

## 6. Modern UI/UX Nuances and Features

- Progress bar or step indicator for current execution.
- Badges for workflow categories (Deployments, Security, Maintenance, etc.).
- Collapsible sections on the right panel.
- Contextual help icon linking to Automation Engine docs.

---

## 7. Coherence and Consistency Across the App

- Uses same workflow terminology as Workflow Explorer.
- Status and success rate semantics align with Dashboard KPIs and Reports.
- Error/incident-triggered workflows connect conceptually to Real-Time Monitor.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#16-automation-engine--automation-engine`
- Implementation: `src/pages/AutomationEngine.tsx`
- Hooks: `src/features/automation/hooks/useAutomationOrchestration.ts`.

---

## 9. Open Gaps & Enhancement Plan

- Wire templates and executions to backend APIs.
- Introduce versioning for workflows.
- Add per-step metrics and logs for better debugging.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Automation Engine
Subtitle: Manage workflows, triggers and automated run history

[ + Create Workflow ]

[Stats]
Active: 6 / 8   Executions (7d): 142   Success Rate: 96.4%   Avg Duration: 2m 15s

+------------------------------------------+------------------------------+
| Workflow Templates & Execution Monitor  | Triggers / Stats / History  |
+------------------------------------------+------------------------------+
```

### 10.2 Sample Workflow Templates

1. **Auto-Rollback Failed Deployment**
   - Category: `Deployments`
   - Status: `Active`
   - Description: `Automatically roll back when error rate spikes after deploy.`

2. **Scale Out Payment Service**
   - Category: `Performance`
   - Status: `Active`
   - Description: `Increase replicas when CPU stays above 80% for 10 minutes.`

3. **Security Quarantine Suspicious Pods**
   - Category: `Security`
   - Status: `Active`
   - Description: `Quarantine pods with repeated auth failures.`

4. **Nightly Data Compaction**
   - Category: `Maintenance`
   - Status: `Paused`
   - Description: `Run compaction jobs at 2am UTC daily.`

### 10.3 Sample Current Execution Monitor

```text
Current Execution: Auto-Rollback Failed Deployment
Run ID: exec-2025-11-20-001
Status: Running (Step 2/3)

Steps:
1. Detect failure (done)
2. Trigger rollback (in progress)
3. Verify health (pending)

[ Cancel Execution ]
```

### 10.4 Sample Trigger Panel

```text
Triggers for: Auto-Rollback Failed Deployment

Event Triggers
- Source: Real-Time Monitor Alerts
- Condition: P1 incident where service = checkout-api AND alert = "Deployment failed"

Schedule Triggers
- None configured
```

### 10.5 Sample Workflow Statistics & History

**Workflow Statistics:**

- Runs (30d): `42`
- Success Rate: `95.2%`
- Avg Duration: `1m 48s`

**Execution History:**

```text
Recent Runs

2025-11-20 10:32   SUCCESS   1m 42s   [ View Logs ]
2025-11-19 21:05   SUCCESS   1m 55s   [ View Logs ]
2025-11-18 18:30   FAILED    0m 30s   [ View Logs ]  [ Retry ]
```

These examples complete the picture of Automation Engine as the automation control center, with realistic workflows, triggers, and history.
