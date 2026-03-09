# 5. HITL Console – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 5. `/hitl` – HITL Console](../WEB_PAGE_FEATURE_INVENTORY.md#5-hitl--hitl-console)

**Code file:**

- `src/features/hitl/HitlConsole.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a prioritized queue where humans can review, approve, defer, or reject AI-proposed actions before they affect production systems.

**Primary goals:**

- Show a **prioritized list of AI actions** requiring review.
- Surface **key context**: confidence, impact, reasoning, priority, department.
- Allow operators to **approve, defer, or reject** quickly and safely.

**Non-goals:**

- Editing underlying AI models (handled elsewhere).
- Managing long-term policy (belongs in Settings/Automation).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **On-call SRE / Ops engineer.**
- **Security analyst** (for AI security actions).
- **Change manager** (for production changes).

**Scenarios:**

1. **Handling urgent remediation suggestions**
   - GIVEN: AI proposes a high-priority remediation (P0) for a production incident.
   - WHEN: On-call engineer opens HITL Console and filters to `P0`.
   - THEN: They review the top action, inspect impact and reasoning, and click `Approve` or `Reject` with confidence.

2. **Triaging routine suggestions**
   - GIVEN: Many non-urgent suggestions (e.g., refactorings).
   - WHEN: Engineer filters by `Type = refactor` and/or department.
   - THEN: They bulk-review actions, deferring or rejecting low-value changes.

3. **Auditing decisions** (future)
   - GIVEN: Security team wants to see how AI actions were handled.
   - WHEN: They filter by `Type = quarantine` and check recent decisions.
   - THEN: They can verify that policy is followed.

---

## 3. Content & Layout Overview

From `HitlConsole.tsx`:

- **Hero stats (top row):**
  - Pending actions.
  - Avg response time.
  - Open incidents.
  - SLA breaches.

- **Main layout (full-screen dark console):**
  - Left (2/3): **Action Queue**.
  - Right (1/3): **Detail panel** (ActionDetailDrawer).

- **Filters & search (above queue):**
  - Priority filter: `All`, `P0 Only`, `P1 Only`, `P2 Only`.
  - Type filter: `All`, `Auto-remediate`, `Quarantine`, `Refactor`.
  - Department filter: `All`, `Engineering`, `QA`, `DevOps`.
  - Search: `Search actions...`.
  - Keyboard shortcut hint: `A=Approve, D=Defer, R=Reject`.

- **ActionQueue table:**
  - Virtualized list (from `ActionQueue` component).
  - Displays rows of actions with priority, type, department, brief summary.

- **ActionDetailDrawer (right):**
  - Shows details for `selectedActionId`.
  - Exposes actions: `Approve`, `Defer`, `Reject` (via `onApprove`/etc.).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Prioritization:**
  - Actions must be clearly grouped by priority (P0/P1/P2) with color and ordering.
- **Fast scanning:**
  - Table should expose key columns at a glance (time, type, risk level, target system).
- **Keyboard-friendly:**
  - Support `A`, `D`, `R` shortcuts once an action is selected.
- **Safe decisions:**
  - Approve/Reject should confirm or be undoable for high-impact actions.

---

## 5. Completeness and Real-World Coverage

The console should let teams:

- See **all pending AI actions** in one place.
- Focus on **highest priority** suggestions first.
- Avoid accidental or unreviewed changes to production.

Over time it should integrate with:

- A decision log (for auditing).
- Automation Engine (for mapping actions to workflows).
- AI Intelligence (for linking from insights → specific actions).

---

## 6. Modern UI/UX Nuances and Features

- Dark theme suitable for NOC-style monitoring.
- Sticky filter row above the ActionQueue.
- Smooth transitions when selecting different actions.
- Empty state text when no actions match filters.

---

## 7. Coherence and Consistency Across the App

- Priority levels `P0` / `P1` / `P2` map to Real-Time Monitor alert severities (`Critical` / `Warning` / `Info`) so operators see a consistent sense of urgency across surfaces.
- Action types (remediate/quarantine/refactor) align with Automation Engine workflows.
- Fits visually into the overall shell but with a more dense, console-style look.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#5-hitl--hitl-console`
- Implementation: `src/features/hitl/HitlConsole.tsx`
- Action queue implementation: `src/features/hitl/components/ActionQueue.tsx`
- Detail drawer implementation: `src/features/hitl/components/ActionDetailDrawer.tsx`

---

## 9. Open Gaps & Enhancement Plan

- Wire to real `useHitlActions` and `useHitlStats` hooks.
- Add historical decision log and filters by outcome.
- Provide a compact summary of risk/impact in the detail drawer.
- Add bulk actions (Approve/Defer/Reject groups) with care.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
[Hero Stats]
Pending: 12      Avg Response: 8m 23s      Open Incidents: 3      SLA Breaches: 0

[Filters]
Priority:  [ All ▼ ]   Type: [ All ▼ ]   Dept: [ All ▼ ]   [ Search actions...        ]
Keyboard shortcuts: A=Approve | D=Defer | R=Reject

[Main]
+------------------------------------------+-----------------------------+
| Action Queue (left, 2/3)                | Action Details (right, 1/3) |
+------------------------------------------+-----------------------------+
```

### 10.2 Sample Hero Stats

From mock `stats` object:

- Pending: `12`
- Avg Response: `8m 23s`
- Open Incidents: `3`
- SLA Breaches: `0`
- Trend hints: `↑ 2 from 1h` (pending), `↓ 45% (7d)` (avg response), `⚠️ 1 P1` (incidents), `✓ On track` (SLA).

### 10.3 Sample Action Queue Rows (Conceptual)

1. **P0 – Auto-remediate database CPU spike**
   - Priority: `P0` (red badge).
   - Type: `Auto-remediate`.
   - Dept: `DevOps`.
   - Summary: `Scale db-primary from 3→6 replicas due to sustained 90% CPU`.
   - Confidence: `96%`.
   - Age: `2m`.

2. **P1 – Quarantine suspicious service**
   - Priority: `P1` (orange badge).
   - Type: `Quarantine`.
   - Dept: `Security`.
   - Summary: `Quarantine payments-api pod with repeated auth failures`.
   - Confidence: `91%`.
   - Age: `7m`.

3. **P2 – Refactor flaky test**
   - Priority: `P2` (green badge).
   - Type: `Refactor`.
   - Dept: `QA`.
   - Summary: `Refactor test_payment_timeout (flakiness 18% over 7d)`.
   - Confidence: `88%`.
   - Age: `42m`.

### 10.4 Sample Detail Drawer for P0 Action

```text
[Action ID: act-2025-11-20-001]
Priority: P0 (Production Risk)
Type: Auto-remediate
Department: DevOps

Proposed Action
Scale db-primary from 3→6 replicas in us-east-1

Reasoning
- CPU usage > 90% for 15 minutes
- Error rate starting to rise on checkout-api
- Similar remediation reduced incident duration by 40% on 2025-11-13

Expected Impact
- Reduce P95 latency from 950ms → ~450ms
- Avoid potential P1 incident

Confidence: 96%

[ Approve ]  [ Defer ]  [ Reject ]
```

This mockup gives detailed expectations for how HITL Console should look and behave with realistic values.
