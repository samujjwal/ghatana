# 14. DevSecOps Phase Board – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/devsecops/phase.$phaseId.tsx` | Phase board route |
| `src/routes/devsecops/_layout.tsx` | Parent layout with TopNav |
| `src/routes/devsecops/atoms.ts` | Jotai atoms for items and phases |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/devsecops/phase/:phaseId` | Phase-specific board view |

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **phase-focused board** (Kanban/Timeline/Table) for DevSecOps items so teams can manage work within a single phase of the lifecycle.

**Primary goals:**

- Show items in a given DevSecOps phase (e.g., Plan, Build, Run).
- Allow switching between views: Kanban, Timeline, Table.
- Support filtering, searching, and side-panel details.

**Non-goals:**

- Represent all phases at once (that is the dashboard’s job).
- Replace core issue tracking tools; instead, aggregate from or link to them.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **DevSecOps Engineers / Security Leads:** Focus on a specific phase.
- **SREs:** Work the Run/Operate phases.

**Key scenarios:**

1. **Working a single phase**
   - Engineer opens the Build phase board.
   - Filters items by priority and status.
   - Opens side panel to view details for a specific item.

2. **Rebalancing work**
   - Lead uses Kanban view to see distribution of items and reassign owners.

---

## 3. Content & Layout Overview

- **View mode switcher:**
  - Kanban, Timeline, Table.
- **Filters + search:**
  - Status, owner, severity, tags.
- **Item list/board:**
  - Cards or rows with title, status, owner, severity, links.
- **Side panel:**
  - Opens when an item is selected, showing details and actions.
- **Real-time simulation:**
  - Timer-based updates to mimic live changes for demo purposes.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear phase context:**
  - Header must indicate which phase (`$phaseId`) is active.
- **Easy view switching:**
  - Changing view type should preserve filters.
- **Accessible interactions:**
  - Cards/rows and filters should be keyboard- and screen-reader-friendly.

---

## 5. Completeness and Real-World Coverage

Phase board should support:

1. **Many items** (20–200) without overwhelming the UI.
2. **Different working styles** (Kanban vs table vs timeline).
3. **Deep links** from other parts of the app into specific items.

---

## 6. Modern UI/UX Nuances and Features

- **Sticky filters header** when scrolling.
- **Color and icon cues** for severity and status.
- **Smooth side-panel animations** when opening/closing.

---

## 7. Coherence and Consistency Across the App

- Item statuses and severities must match the DevSecOps dashboard and reports.
- Item detail must be consistent with the DevSecOps item detail route.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/phase/$phaseId.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Make item → project/canvas linkage explicit.
2. Add export or share options for filtered views.
3. Connect to real data sources instead of demo fixtures.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Build Phase
Subtitle: Work items in the Build phase of the DevSecOps lifecycle.

[ View: ● Kanban   ○ Timeline   ○ Table ]   [ Status ▼ ] [ Owner ▼ ] [ Search 🔍 ]

Kanban board (Build phase)
-------------------------------------------------------------------------------
|  TODO                        |  IN PROGRESS             |  BLOCKED          |  DONE               |
-------------------------------------------------------------------------------
| [ ] DSO‑231 Add SAST check   | [ ] DSO‑210 Fix flaky    | [ ] DSO‑225 Docker| [x] DSO‑190 Enforce |
| for checkout-service builds  | unit tests in pipeline   | base image policy | signed artifacts    |
| Severity: Medium             | Severity: Low            | Severity: High    | Severity: Medium    |
| Owner: unassigned            | Owner: dev‑team‑a        | Owner: sre‑team   | Owner: platform     |
-------------------------------------------------------------------------------
| [ ] DSO‑232 Configure        | [ ] DSO‑211 Add codecov  |                    | [x] DSO‑188 Require |
| build cache for monorepo    | upload step              |                    | status checks       |
-------------------------------------------------------------------------------

Selecting a card opens a right-hand side panel with:
- Overview: description, severity, status, owner, phase.
- Links: related pipelines, associated project(s), GitHub issue.
- Actions: [Change status] [Reassign] [Open in DevSecOps item detail].
```
