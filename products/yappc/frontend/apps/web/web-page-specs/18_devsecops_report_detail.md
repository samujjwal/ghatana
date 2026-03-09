# 18. DevSecOps Report Detail – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

- `src/routes/devsecops/reports/$reportId.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Show a **detailed DevSecOps report** for a specific report type and context, including key metrics, findings, and risks, with options to export and share.

**Primary goals:**

- Present KPIs and detailed findings for a selected report.
- Show risks, recommendations, and affected entities.
- Allow refreshing data and exporting the report.

**Non-goals:**

- Replace all underlying observability/CI tooling; it summarizes and links to them.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Executives, managers, security leads, SREs.

**Key scenarios:**

1. **Quarterly review**
   - Executive views Executive Summary report.
   - Reads top risks and key metrics.

2. **Release readiness**
   - Release manager opens Release Readiness report.
   - Reviews open issues and recommendations.

---

## 3. Content & Layout Overview

- **Header:**
  - Report title, context (environment, time range, workspace/project).
- **KPI row:**
  - Key counts/ratios for the report type.
- **Findings / risks list:**
  - Detailed items with severity, description, and suggested actions.
- **Actions bar:**
  - Refresh, export, share.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Readable findings:**
  - Use layman-friendly text for risks and recommendations.
- **Severity visualization:**
  - Clear severity labels and colors.

---

## 5. Completeness and Real-World Coverage

Report detail should support:

1. Multiple risk/findings categories.
2. Direct links to underlying systems or items.

---

## 6. Modern UI/UX Nuances and Features

- **Export formats:**
  - PDF or CSV export (future behavior), with sensible defaults.
- **Sharing:**
  - Copy link or share with specific people (future).

---

## 7. Coherence and Consistency Across the App

- Severity and risk terminology must align with Monitor, DevSecOps dashboard, and HITL-like views.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/reports/$reportId.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Drill-down from report findings to specific pipelines/projects.
2. Add comparison against previous periods.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Release Readiness – Workspace A – Last 7 days

[ KPI Row ]
-------------------------------------------------------------------------------
| Passing pipelines: 12 / 15   | Open critical risks: 2 | Changes since last 7d |
-------------------------------------------------------------------------------

[ Findings ]
-------------------------------------------------------------------------------
Severity   Area          Summary
-------------------------------------------------------------------------------
Critical   Security      Unpatched library in payments-service (CVE‑2025‑1234)
High       Quality       Flaky test suite in pipeline `checkout‑e2e`
Medium     Compliance    Missing SAST check for repo `legacy‑admin`
-------------------------------------------------------------------------------

[ Actions ]
[ Refresh ]   [ Export PDF ]   [ Export CSV ]   [ Share link ]
```
