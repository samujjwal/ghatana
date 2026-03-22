# 6. Build – CI/CD Build Pipeline – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Added reuse-first guidance

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.5 Build](../APP_CREATOR_PAGE_SPECS.md#25-build----buildtsx)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/app/project/build.tsx` | Build pipeline route |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/app/w/:workspaceId/p/:projectId/build` | Project build pipeline |

---

## 🔁 REUSE-FIRST: Required Components

**MUST use these from `@yappc/ui`:**

| Component | Import | Purpose |
|-----------|--------|---------|
| `DataTable` | `@yappc/ui/components/DevSecOps` | Build runs table |
| `KPICard` | `@yappc/ui/components/DevSecOps` | Build metrics |
| `Timeline` | `@yappc/ui/components/DevSecOps` | Build timeline view |

**One-Click Action:** "Run Build" button should trigger build in ONE click.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **build pipeline dashboard** for each project, showing current and recent build runs with real-time updates and bulk operations.

**Primary goals:**

- Show a table of recent builds with status and key metadata.
- Enable selecting multiple builds for bulk actions (re-run, cancel, etc.).
- Stream live updates via WebSocket where available.

**Non-goals:**

- Replace full CI server UI; this is a focused project-level view.
- Manage deployments (that lives in the Deploy tab).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Developers:** Check whether their changes built successfully.
- **Release Engineers:** Monitor build stability before releases.
- **SREs:** Investigate build problems alongside incidents.

**Key scenarios:**

1. **Checking last build**
   - Developer opens Build tab after pushing.
   - Finds their latest build run and sees pass/fail, duration, branch.

2. **Bulk re-run after infrastructure issue**
   - Infra issue is resolved.
   - Engineer selects failing builds and triggers a bulk re-run.

3. **Investigating flaky builds**
   - Tech lead filters by status or branch (future) to spot patterns.

---

## 3. Content & Layout Overview

- **GraphQL query for project builds:**
  - Fetches recent builds and metadata.
- **WebSocket stream for real-time updates:**
  - Pushes new statuses into the table.
- **DataTable / SelectableTable UI:**
  - Columns: ID, status, branch, commit, duration, triggered by, started/finished times.
  - Row selection with checkboxes.
- **BulkActionBar:**
  - Appears when rows are selected.
  - Provides actions like re-run or cancel (actual actions depend on implementation).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Status clarity:**
  - Use clear labels (Succeeded, Failed, Running, Queued) with icon + color.
- **Selection feedback:**
  - Selecting builds should clearly highlight rows and show count.
- **Empty / error states:**
  - If no builds exist, show guidance ("No builds yet – trigger your first pipeline").
  - On error, show a non-technical message with retry.

---

## 5. Completeness and Real-World Coverage

The Build tab should support:

1. **Dozens to hundreds of builds** with pagination or lazy loading.
2. **Multiple branches and pipelines** (e.g., main, release/\*).
3. **Correlation** with Deploy and Monitor via shared identifiers (future).

---

## 6. Modern UI/UX Nuances and Features

- **Live updates:**
  - WebSocket updates should smoothly update row statuses without full reloads.
- **Sorting and filtering:**
  - Future: sort by date/status, filter by branch.
- **Responsive table design:**
  - Collapse less-critical columns on smaller screens.

---

## 7. Coherence and Consistency Across the App

- Build statuses and icons should match those used in Deploy and DevSecOps.
- Bulk actions and selection patterns should match Deploy tab behavior.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#25-build----buildtsx`
- Route implementation: `src/routes/app/project/build.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Align bulk actions with concrete workflows (e.g., "Re-run failed on main").
2. Add summary KPIs (e.g., success rate, average duration) at top of page.
3. Link builds to deployments and incidents.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Builds
Subtitle: Recent CI runs for this project.

[ Filters: Branch ▼ (main) | Status ▼ (All) | Time range ▼ (Last 24h) ]

[ Summary KPIs ]
- Success rate: 92% (last 50 builds)
- Avg duration: 6m 30s
- Running: 1   |  Failed: 3   |  Queued: 0

[ Build Table ]
-------------------------------------------------------------------------------
□  Build   Status      Branch   Commit    Started      Duration   By
-------------------------------------------------------------------------------
□  #1240   Running     main     9ac12f    11:42        3m 10s    ci-bot
□  #1239   Succeeded   main     1df4a8    11:15        5m 58s    alice
□  #1238   Failed      feature  7b22c1    10:50        2m 03s    bob
□  #1237   Succeeded   main     0aa931    10:12        6m 10s    alice
-------------------------------------------------------------------------------

Row interactions:
- Click build ID (#1238) → opens build detail drawer or route.
- Hover row → shows inline actions ("View logs", "View artifacts").

Bulk selection:
- When 1+ rows checked:
  [ Re-run selected ]  [ Cancel selected ]  (with confirmation for destructive actions)
```
