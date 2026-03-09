# 6. Event Simulator – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 6. `/simulator` – Event Simulator](../WEB_PAGE_FEATURE_INVENTORY.md#6-simulator--event-simulator)

**Code file:**

- `src/features/simulator/EventSimulator.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Let engineers safely compose, validate, and send simulated events to test how downstream systems, dashboards, and workflows react without using real production data.

**Primary goals:**

- Provide **ready-made templates** for common event types.
- Let users edit **JSON payloads** with validation.
- Show **history** of recently sent events for debugging.

**Non-goals:**

- Visualizing full event routes (handled by observability tools).
- Long-term storage of simulated events.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Backend engineer** testing new event handlers.
- **SRE** simulating incident scenarios for runbooks.
- **Data/ML engineer** verifying feature pipelines.

**Scenarios:**

1. **Testing a new deployment event handler**
   - GIVEN: A new handler listens for `deployment.completed`.
   - WHEN: Engineer loads the `Deployment` template, tweaks the payload, and clicks `Send Event`.
   - THEN: They can verify logs, dashboards, and workflows behaved correctly.

2. **Simulating a security alert**
   - GIVEN: Security wants to test detection and response workflows.
   - WHEN: They select `Security Alert` template and send events for a specific service.
   - THEN: They confirm that alerts show up in Security Dashboard and HITL/Automation flows.

3. **Reproducing a test failure scenario**
   - GIVEN: A flaky test is suspected.
   - WHEN: QA engineer sends repeated `test.failed` events with different payloads.
   - THEN: They see how AI insights and dashboards interpret that pattern.

---

## 3. Content & Layout Overview

From `EventSimulator.tsx`:

- **Header:**
  - Title: `Event Simulator`.
  - Subtitle: `Compose and emit simulated events for testing pipelines and workflows`.

- **Main grid (3 columns on desktop):**
  - Left: **Event Templates** list.
  - Right (2 columns wide): **JSON editor + actions + stats**.

- **Templates section:**
  - Buttons with label and example `type` (e.g., `deployment.completed`).

- **JSON editor section:**
  - Label: `Event Payload (JSON)`.
  - Multiline textarea containing formatted JSON.
  - Error message box if JSON is invalid.

- **Actions row:**
  - `Send Event` primary button.
  - `Reset` button to revert to default payload (first template).

- **Stats row:**
  - Small stat cards: Sent count, Last Type, Templates count, Status.

- **Event history section:**
  - Title: `Event History (Last 50)`.
  - Scrollable list of events with timestamp, type, and pretty-printed JSON.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Templates feel like shortcuts:**
  - Clicking a template immediately loads its JSON into the editor.
- **Validate early:**
  - JSON errors are caught and shown clearly without sending anything.
- **History is readable:**
  - Use monospace font and indentation for payloads.
- **Safe and clear:**
  - Provide a short explanation that these are simulated events and do not impact real systems directly (once wired, ensure they hit a non-production environment by default).

---

## 5. Completeness and Real-World Coverage

The simulator should support at least:

- Deployment events (`deployment.completed`).
- Security alerts (`security.alert`).
- Test failures (`test.failed`).
- Performance issues (`performance.degraded`).

Engineers should be able to reproduce a sequence of events to:

- Test dashboards.
- Trigger alerts or automation.
- Validate pattern matching and correlation logic.

---

## 6. Modern UI/UX Nuances and Features

- Syntax highlighting for JSON (future enhancement).
- Clear error styling (red background, concise message) for invalid JSON.
- Scrollable history with copy-to-clipboard behavior.
- Responsive layout that stacks templates above the editor on small screens.

---

## 7. Coherence and Consistency Across the App

- Event `type` values should align with those used by Real-Time Monitor, ML Observatory, and Automation Engine.
- The simulator should conceptually connect to pattern/event-processing docs in the rest of the product.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#6-simulator--event-simulator`
- Implementation: `src/features/simulator/EventSimulator.tsx`
- Shared API client: `src/services/api/index.ts` (future integration for `/api/v1/events/simulate`).

---

## 9. Open Gaps & Enhancement Plan

- Wire `send` function to real simulation API.
- Add ability to save and reuse custom templates.
- Show which downstream systems reacted (e.g., small badges or links in history rows).

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Event Simulator
Subtitle: Compose and emit simulated events for testing pipelines and workflows

[3-column layout]
+----------------------+-----------------------------------------------+
| Event Templates      | Event Payload (JSON) + Actions + Stats       |
+----------------------+-----------------------------------------------+

[Event History (Last 50)]
```

### 10.2 Sample Templates

1. **Deployment**
   - Label: `Deployment`
   - Value:
     ```json
     {
       "type": "deployment.completed",
       "service": "api",
       "version": "v2.1.0",
       "duration": "2m 45s",
       "status": "success"
     }
     ```

2. **Security Alert**
   - Label: `Security Alert`
   - Value:
     ```json
     {
       "type": "security.alert",
       "severity": "high",
       "rule": "SQL Injection Detected",
       "count": 3,
       "service": "payments"
     }
     ```

3. **Test Failed**
   - Label: `Test Failed`
   - Value:
     ```json
     {
       "type": "test.failed",
       "suite": "payment-integration",
       "test": "test_transaction_timeout",
       "duration": "12.3s"
     }
     ```

4. **Performance Degradation**
   - Label: `Performance Degradation`
   - Value:
     ```json
     {
       "type": "performance.degraded",
       "metric": "p99_latency",
       "value": 850,
       "threshold": 500,
       "service": "checkout"
     }
     ```

### 10.3 Example Editor & Error States

**Initial editor content (Deployment template):**

```json
{
  "type": "deployment.completed",
  "service": "api",
  "version": "v2.1.0",
  "duration": "2m 45s",
  "status": "success"
}
```

**Invalid JSON error banner:**

```text
[ Invalid JSON: Unexpected token } in JSON at position 128 ]
```

### 10.4 Sample Event History Rows

After sending a few events:

```text
11:02:15  deployment.completed  (Sent)
{
  "type": "deployment.completed",
  "service": "api",
  "version": "v2.1.0",
  "duration": "2m 45s",
  "status": "success"
}

11:05:42  security.alert  (Sent)
{
  "type": "security.alert",
  "severity": "high",
  "rule": "SQL Injection Detected",
  "count": 3,
  "service": "payments"
}
```

This mockup ties the UI layout directly to realistic event payloads and histories.
