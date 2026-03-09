# 10. Versions – Snapshots & History – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.9 Versions](../APP_CREATOR_PAGE_SPECS.md#29-versions----versionstsx)

**Code files:**

- `src/routes/app/project/versions.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Let users **create, browse, compare, and restore snapshots** of a project so they can experiment safely and understand how the project evolved.

**Primary goals:**

- Present a list/grid of snapshots with key metadata.
- Allow comparing snapshots.
- Allow restoring or exporting a snapshot.

**Non-goals:**

- Replace Git history; instead, complement it with higher-level snapshots.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Developers:** Safely experiment with big changes.
- **Tech Leads:** Capture before/after states for reviews.
- **Platform Engineers:** Provide template baselines.

**Key scenarios:**

1. **Before/after experiment**
   - Developer creates a snapshot before a large refactor.
   - After completing, they compare against the old state and optionally revert.

2. **Version catalog**
   - Lead browses key milestones and exports a snapshot for documentation.

---

## 3. Content & Layout Overview

- **Snapshot cards/grid:**
  - Name, description, created at/by, tags.
- **Actions per snapshot:**
  - Compare, restore, export, delete, edit.
- **Dialogs:**
  - For creating/editing snapshot metadata.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear consequences:**
  - Restoring a snapshot should include a clear warning and summary of what will change.
- **Comparison UX:**
  - Show high-level differences (e.g., canvas nodes changed, pipelines changed) rather than raw diffs.
- **Search/filter:**
  - Future: filter snapshots by tag (e.g., `release`, `experiment`).

---

## 5. Completeness and Real-World Coverage

Versions should cover:

1. **Canvas state** (layouts, nodes).
2. **Pipelines/configuration** (build/deploy/test config).
3. **Settings** that materially affect behavior.

---

## 6. Modern UI/UX Nuances and Features

- **Friendly naming:**
  - Encourage descriptive names like "Pre-migration" rather than cryptic IDs.
- **Visual cues:**
  - Icons for "current" vs historical snapshots.
- **Bulk operations:**
  - Future: bulk delete of very old snapshots.

---

## 7. Coherence and Consistency Across the App

- Snapshots should align with DevSecOps reports (e.g., versions referenced in reports).
- Links from Versions to Build/Deploy should be consistent.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#29-versions----versionstsx`
- Route implementation: `src/routes/app/project/versions.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Clarify relationships between snapshots and Git commits.
2. Make it explicit whether snapshots include runtime resources or just configuration.
3. Add richer comparison views (side-by-side, timeline).

---

## 10. Mockup / Expected Layout & Content

```text
H1: Versions
Subtitle: Saved snapshots of this project's configuration and design.

[ New Snapshot ]

[ Snapshot Cards ]
-------------------------------------------------------------------------------
| v1.0 Launch                              | Pre-migration                     |
-------------------------------------------------------------------------------
| Created by alice • 2025‑10‑01 10:15      | Created by bob • 2025‑10‑15 09:42 |
| Tags: [release] [prod]                   | Tags: [experiment] [schema‑v2]    |
| Notes: "Initial GA release"             | Notes: "Before db‑migration"     |
| [Compare] [Restore] [Export] [Delete]    | [Compare] [Restore] [Export]      |
-------------------------------------------------------------------------------

Comparison behaviour (conceptual):
- Select two snapshots → open comparison view:
  • Canvas: nodes added/removed/changed.
  • Pipelines: builds/deploys configuration diffs.
  • Settings: environment/integration changes.
```
