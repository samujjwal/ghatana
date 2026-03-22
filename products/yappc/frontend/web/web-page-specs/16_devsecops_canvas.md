# 16. DevSecOps Canvas – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/devsecops/canvas.tsx` | DevSecOps canvas route |
| `src/routes/devsecops/_layout.tsx` | Parent layout with TopNav |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/devsecops/canvas` | Visual DevSecOps modeling canvas |

**Navigation:**
- Accessed via TopNav "Canvas" link
- Returns to dashboard via breadcrumb or TopNav

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **visual DevSecOps canvas** for a demo project, showing phases, flows, and dependencies.

**Primary goals:**

- Visualize DevSecOps workflows and entities for a demo project.
- Serve as a reference for how canvas can be used for DevSecOps modeling.

**Non-goals:**

- Be a full production editor for all DevSecOps configurations (currently a demo).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- DevSecOps engineers, architects, and platform engineers exploring canvas-based modeling.

**Key scenarios:**

1. **Exploration**
   - User opens DevSecOps canvas to understand how phases and controls relate visually.

2. **Template reference**
   - Engineers reference this canvas when designing new DevSecOps templates.

---

## 3. Content & Layout Overview

- Thin route wrapper that mounts `DevSecOpsCanvas` component under DevSecOps layout.
- Uses a stable demo project ID and optional `template` query parameter.
- Canvas likely shows nodes representing phases, checks, pipelines, and dependencies.

---

## 4. UX Requirements – User-Friendly and Valuable

- Clear visual distinction between phases and item types.
- Legible node labels and edges.

---

## 5. Completeness and Real-World Coverage

For a demo, canvas should include:

1. All major DevSecOps phases.
2. Representative checks and flows.
3. Support for zooming/panning.

---

## 6. Modern UI/UX Nuances and Features

- Smooth interactions and animations.
- Readable in both light and dark themes.

---

## 7. Coherence and Consistency Across the App

- Node labels and statuses should match DevSecOps items and reports.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/canvas.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Show relationship between DevSecOps canvas and real App Creator projects.
2. Support templates originating from canvas designs.

---

## 10. Mockup / Expected Layout & Content

```text
H1: DevSecOps Canvas
Subtitle: Visual model of phases, checks, and flows for a demo project.

[ Canvas Layout ]
-------------------------------------------------------------------------------
|  Palette (left)             |  DevSecOps Canvas (center)                   |
-------------------------------------------------------------------------------
|  Phases                     |   [Plan] ──> [Code] ──> [Build] ──> [Test]  |
|   • Plan phase node         |       \                     \               |
|   • Code phase node         |        \                     └─> [Security] |
|   • Build phase node        |         \                                     |
|   • Test phase node         |          └─> [Risk Register]                 |
|   • Deploy phase node       |                                             |
|   • Run phase node          |   Each node shows status badge (Healthy,   |
|                             |   At Risk, Failing) based on demo data.    |
-------------------------------------------------------------------------------

Interactions (demo):
- Drag additional checks (e.g., "SAST", "Secrets Scan") from palette onto Build.
- Select a phase node to see associated DevSecOps items in a side panel.
- Zoom/pan to focus on specific parts of the lifecycle.
```
