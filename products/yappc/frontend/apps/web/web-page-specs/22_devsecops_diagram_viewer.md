# 22. DevSecOps Diagram Viewer – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

- `src/routes/devsecops/diagram/$diagramId.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **viewer for architecture/design/infrastructure diagrams** with layer controls, zoom, and annotations, within the DevSecOps context.

**Primary goals:**

- Display diagrams relevant to DevSecOps (architecture, infra, data flows).
- Allow toggling layers, zooming, and panning.
- Support annotations and comments.

**Non-goals:**

- Act as a full diagram editor; it’s primarily a viewer with light interaction.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Architects, SREs, security engineers.

**Key scenarios:**

1. **Reviewing architecture**
   - Architect reviews architecture diagram for a service, toggling layers.

2. **Incident investigation**
   - SRE consults infra diagram to understand where an issue might be occurring.

---

## 3. Content & Layout Overview

- **Diagram canvas:**
  - Main viewing area for diagram image or interactive component.
- **Layer controls:**
  - Toggles for architecture layers, security overlays, infra components.
- **Zoom & pan controls.**
- **Annotations & comments:**
  - Sidebar or overlay for comments on diagram regions.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Legible diagrams:**
  - Zooming and panning must allow reading labels comfortably.
- **Simple controls:**
  - Layer toggles should be clearly labeled and understandable.

---

## 5. Completeness and Real-World Coverage

Diagram viewer should:

1. Handle multiple diagram types.
2. Support collaboration via comments.

---

## 6. Modern UI/UX Nuances and Features

- Smooth zooming and panning.
- Highlighting of selected regions when commenting.

---

## 7. Coherence and Consistency Across the App

- Diagrams should tie back to App Creator projects and DevSecOps items where relevant.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/diagram/$diagramId.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Integrate with canvases so diagrams reflect live system structure.
2. Support versioning of diagrams and linking to project snapshots.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Payments Service Architecture

[ Layer Controls ]   [ Zoom - ] [ 100% ] [ Zoom + ] [ Fit ]

Layout
-------------------------------------------------------------------------------
| Layer Controls      | Diagram Canvas                        | Annotations   |
-------------------------------------------------------------------------------
| [x] App layer       |   +-------------------------------+   | 1. "Unclear  |
| [x] API layer       |   |  [Web] → [API] → [DB]         |   | DMZ boundary"|
| [ ] Data layer      |   |   |           \              |   |    – arch‑1  |
| [x] Security layer  |   |   v            v             |   |              |
|                     |   | [WAF]       [Auth Service]   |   | 2. "Add note |
|                     |   +-------------------------------+   | for PCI zone"|
-------------------------------------------------------------------------------

Users can:
- Toggle layers on/off to show different concerns.
- Zoom/pan to inspect specific nodes.
- Click diagram regions to add or view comments in the Annotations panel.
```
