# 4. Project Backlog – Kanban Board – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Added reuse-first guidance

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.3 Backlog](../APP_CREATOR_PAGE_SPECS.md#23-backlog----backlogtsx)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/app/project/backlog.tsx` | Backlog route |
| `src/routes/app/project/_shell.tsx` | Project header & tabs |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/app/w/:workspaceId/p/:projectId/backlog` | Project backlog kanban |

---

## 🔁 REUSE-FIRST: Required Components

**MUST use these from `@yappc/ui`:**

| Component | Import | Purpose |
|-----------|--------|---------|
| `KanbanBoard` | `@yappc/ui/components/DevSecOps` | Main kanban view |
| `ItemCard` | `@yappc/ui/components/DevSecOps` | Task cards |
| `SearchBar` | `@yappc/ui/components/DevSecOps` | Task search |
| `FilterPanel` | `@yappc/ui/components/DevSecOps` | Filter tasks |

**DO NOT create local versions of these components.**

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **simple Kanban-style backlog** grouped by status for each project so the team can see what work is planned, in progress, blocked, and done.

**Primary goals:**

- Show tasks grouped by status columns (TODO, IN_PROGRESS, DONE, BLOCKED).
- Make it easy to scan what needs attention next.
- Act as a bridge between **planning** and the **canvas / pipelines**.
- **One-click task creation** – simplest possible workflow.

**Non-goals:**

- Fully replace a dedicated issue tracker (Jira, GitHub Issues, etc.).
- Handle cross-project portfolio views (that belongs elsewhere).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Developers:** Track their own tasks and pick up work.
- **Tech Leads / PMs:** Get a quick view of project work status.

**Key scenarios:**

1. **Daily standup**
   - Team opens the project Backlog tab.
   - Scans TODO/IN_PROGRESS/BLOCKED to drive the conversation.

2. **Planning a new feature**
   - Lead adds tasks under TODO (future behavior) and links them to canvas nodes.

3. **Unblocking work**
   - SRE or lead looks at the BLOCKED column to identify what needs help.

---

## 3. Content & Layout Overview

- **Columns:**
  - One column per status: TODO, IN_PROGRESS, DONE, BLOCKED.
  - Each column uses consistent card styling.
- **Task cards (mock data for now):**
  - Title, description snippet.
  - Status chip with color (mapping function in code).
  - Optional metadata such as assignee or tags (future).
- **Project scoping:**
  - Tasks are filtered by `projectId` so each project sees its own board.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Readable statuses:**
  - Status labels must be short and plain-language.
- **Visual grouping:**
  - Columns should be clearly separated with headings and subtle background.
- **Empty states:**
  - If a column has no tasks, show friendly text (e.g., "Nothing here yet").

Future UX (not yet implemented but expected):

- Drag-and-drop between columns to change status.
- Click on a card to open a detail drawer.

---

## 5. Completeness and Real-World Coverage

A realistic backlog should support:

1. **Multiple statuses** beyond TODO/DONE (already modeled).
2. **Dozens of tasks** per project without visual clutter.
3. **Filtering/search** (future) by owner, tag, or priority.

---

## 6. Modern UI/UX Nuances and Features

- **Status color mapping:**
  - Colors must be consistent with other parts of the app (e.g., BLOCKED vs DONE).
- **Scroll behavior:**
  - Each column should scroll independently if tall; the header row stays visible.
- **Keyboard accessibility:**
  - Task cards should be focusable and navigable via keyboard.

---

## 7. Coherence and Consistency Across the App

- Backlog statuses should align with **DevSecOps** item statuses where possible.
- Severity/priority semantics (P0/P1) should match Monitor and DevSecOps if/when added.
- Links from canvas nodes or DevSecOps items should be able to point to specific tasks.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#23-backlog----backlogtsx`
- Route implementation: `src/routes/app/project/backlog.tsx`
- Project shell: `src/routes/app/project/_shell.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Replace mock tasks with real data from a backlog/issue service.
2. Add drag-and-drop between columns.
3. Implement task detail drawer with richer metadata.
4. Link tasks to canvas nodes and DevSecOps items.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Backlog
Subtitle: Tasks for this project grouped by status.

[ Filters: Assignee ▼  |  Label ▼  |  Search 🔍 "checkout" ]

-------------------------------------------------------------------------------
|  TODO                    |  IN PROGRESS             |  BLOCKED               |  DONE                    |
-------------------------------------------------------------------------------
| [ ] BK‑132 Improve       | [ ] BK‑118 Implement     | [ ] BK‑127 Payment     | [x] BK‑101 Wireframe     |
| checkout hero copy       | login form validation    | gateway timeout fix    | checkout flow            |
| Assignee: unassigned     | Assignee: alice          | Assignee: sre-oncall   | Assignee: design-lead    |
| Labels: content          | Labels: auth, frontend   | Labels: infra, p0      | Labels: ux, v1           |
| Due: –                   | Due: Today               | Due: Overdue 2d        | Completed: 2025‑10‑01    |
-------------------------------------------------------------------------------
| [ ] BK‑133 Add A/B test  | [ ] BK‑119 Add loading   |                         | [x] BK‑099 Create        |
| for hero layout          | skeleton for checkout    |                         | basic project skeleton   |
| Assignee: pm             | Assignee: bob            |                         | Assignee: alice          |
| Labels: experiment       | Labels: perf, ux         |                         | Labels: setup            |
| Due: Next week           | Due: Tomorrow            |                         | Completed: 2025‑09‑20    |
-------------------------------------------------------------------------------

Per-card actions (icon row on hover/focus):
- ✏️ Edit   • 🔗 Link to Canvas Node   • 🔁 Change Status   • ⋯ More

Empty state example:
- If BLOCKED has no items: show subtle text "No blocked tasks right now 🎉".
```
