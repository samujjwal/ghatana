# 17. DevSecOps Reports Hub – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/devsecops/reports.tsx` | Reports hub route |
| `src/routes/devsecops/reports.$reportId.tsx` | Individual report detail |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/devsecops/reports` | Reports hub with report type cards |
| `/devsecops/reports/:reportId` | Individual report detail view |

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **hub of DevSecOps report types** (Executive Summary, Release Readiness, Security & Compliance, Operational Health) with filters and navigation to detailed reports.

**Primary goals:**

- List available report types as cards.
- Provide filters (e.g., time range, environment, workspace/project).
- Navigate to specific detailed reports.

**Non-goals:**

- Display all metrics inline (detailed views live in report detail pages).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Executives and managers reviewing summaries.
- Security and SRE leads reviewing specific report types.

**Key scenarios:**

1. **Executive summary**
   - Director picks Executive Summary report and filters by time range.

2. **Release readiness**
   - Release manager checks readiness before launch.

---

## 3. Content & Layout Overview

- **Report cards grid:**
  - Title, short description, key tags.
  - CTA: "View report".
- **Filters:**
  - Dropdowns or inputs for environment, time range, workspace/project selection.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Plain report names:**
  - Titles must be self-explanatory.
- **Filter persistence:**
  - Filters should remain applied when navigating back from detail pages.

---

## 5. Completeness and Real-World Coverage

Reports hub should support:

1. Multiple report types.
2. Different audiences (executive vs technical).
3. Quick navigation into details.

---

## 6. Modern UI/UX Nuances and Features

- **Card layout:**
  - Responsive and visually distinct for each report type.
- **Empty state:**
  - If no reports available for given filters, show helpful messaging.

---

## 7. Coherence and Consistency Across the App

- Report names and scopes must match the detailed report route.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/reports.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Clarify mapping between reports and concrete pipelines/projects.
2. Support exporting reports and sharing links.

---

## 10. Mockup / Expected Layout & Content

```text
H1: DevSecOps Reports
Subtitle: Summaries and insights for security, quality, and operations.

[ Filters ]
- Environment: [All | Dev | Stg | Prod]
- Time range: [7d | 30d | 90d | Custom]
- Workspace: [All | Team Alpha | Team Beta]

[ Report Cards Grid ]
-------------------------------------------------------------------------------
| Executive Summary                      | Release Readiness                  |
-------------------------------------------------------------------------------
| Audience: Leadership                   | Audience: Release managers         |
| Highlights: overall risk posture,      | Highlights: failing checks,        |
| top incidents, MTTR, SLA adherence.    | deployment history, open blockers. |
| [View report]                          | [View report]                      |
-------------------------------------------------------------------------------
| Security & Compliance                  | Operational Health                 |
-------------------------------------------------------------------------------
| Audience: Security / Compliance        | Audience: SRE / Ops                |
| Highlights: vulns, policies, coverage. | Highlights: alerts, SLOs, errors.  |
| [View report]                          | [View report]                      |
-------------------------------------------------------------------------------
```
