# 7. Workflow List Page – Legacy Stub – Deep-Dive Spec

Related routes & files:

- Page component: `src/pages/WorkflowList/index.tsx` (not currently wired in `App.tsx`)

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a simple **list view placeholder** for workflows; likely a legacy or experimental page superseded by `WorkflowsPage`.

**Current state:**

- The component renders a header `Workflows` and a white card with the message "Workflow list will be displayed here".
- It is not currently connected to any route in `App.tsx`.

---

## 2. Users, Personas, and Real-World Scenarios

- Historically intended for the same personas as `WorkflowsPage`:
  - Workflow designers and operators.
- Today, it is effectively a **stub/backup** and not used.

---

## 3. Content & Layout Overview

From `WorkflowList/index.tsx`:

- Simple layout:
  - H1 `Workflows`.
  - Card with placeholder text.

---

## 4. UX Requirements – User-Friendly and Valuable

- Currently minimal and not production-ready.
- If revived, it would need the richer behavior present in `WorkflowsPage`.

---

## 5. Completeness and Real-World Coverage

- This page is not a complete workflows view; see `05_workflows_page.md` for the real workflows list spec.

---

## 6. Modern UI/UX Nuances and Features

- None at present beyond basic layout.

---

## 7. Coherence with App Creator / Canvas & Platform

- If used again, it should be aligned or merged with the primary Workflows page and canvas.

---

## 8. Links to More Detail & Working Entry Points

- Preferred workflows list: `src/pages/WorkflowsPage.tsx`.
- Canvas: `src/pages/WorkflowDesigner/index.tsx`.

---

## 9. Gaps & Enhancement Plan

1. **Decommission or merge:**
   - Decide whether to remove this page or wire it to share implementation with `WorkflowsPage`.

2. **Avoid confusion:**
   - Ensure only one workflows list page is routed in production.

---

## 10. Mockup / Expected Layout & Content (Current Stub)

```text
H1: Workflows

[Card]
-------------------------------------------------------------------------------
"Workflow list will be displayed here"
-------------------------------------------------------------------------------
```
