# 7. Workflow List Page – Legacy Workflow Stub – Deep-Dive Spec

Related routes & files:

- Page component: `src/pages/WorkflowList/index.tsx` (not currently wired in `App.tsx`)

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Document a simple **legacy list-view placeholder** for workflows that has been superseded by the canonical Pipelines surface.

**Current state:**

- The component renders a header `Legacy Workflow List` and a white card explaining that the canonical surface is `/pipelines`.
- It is not currently connected to any route in `App.tsx`.
- The production route is `/pipelines`, with legacy `/workflows` aliases mapped to `WorkflowsPage` instead of this stub.

---

## 2. Users, Personas, and Real-World Scenarios

- Historically intended for the same personas as `WorkflowsPage`:
  - Workflow designers and operators.
- Today, it is effectively a **stub/backup** and not used.

---

## 3. Content & Layout Overview

From `WorkflowList/index.tsx`:

- Simple layout:
  - H1 `Legacy Workflow List`.
  - Card with redirect-style stub text.

---

## 4. UX Requirements – User-Friendly and Valuable

- Currently minimal and not production-ready.
- If revived, it would need the richer behavior present in `WorkflowsPage`.

---

## 5. Completeness and Real-World Coverage

- This page is not a complete pipelines view; see `05_workflows_page.md` for the canonical pipelines list spec.

---

## 6. Modern UI/UX Nuances and Features

- None at present beyond basic layout.

---

## 7. Coherence with App Creator / Canvas & Platform

- If used again, it should be aligned or merged with the primary pipelines page and canvas.

---

## 8. Links to More Detail & Working Entry Points

- Preferred pipelines list: `src/pages/WorkflowsPage.tsx`.
- Advanced editor: `src/pages/WorkflowDesigner/index.tsx`.

---

## 9. Gaps & Enhancement Plan

1. **Decommission or merge:**
   - Decide whether to remove this page or wire it to share implementation with `WorkflowsPage`.

2. **Avoid confusion:**
  - Ensure only one pipelines list page is routed in production.

---

## 10. Mockup / Expected Layout & Content (Current Stub)

```text
H1: Legacy Workflow List

[Card]
-------------------------------------------------------------------------------
"This legacy stub is not the canonical pipelines list. Use the Pipelines surface instead."
-------------------------------------------------------------------------------
```
