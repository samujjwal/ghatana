# 15. DevSecOps Persona Dashboard – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/devsecops/persona.$slug.tsx` | Persona-specific dashboard route |
| `src/routes/devsecops/_layout.tsx` | Parent layout with TopNav |
| `src/routes/devsecops/atoms.ts` | Jotai atoms including `personaDashboardsAtom` |
| `src/routes/devsecops/components/UnifiedPersonaDashboard.tsx` | Shared persona view component |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/devsecops/persona/:slug` | Persona-specific dashboard (security, developer, sre, executive, admin) |

**Persona Types:**

| Slug | Display Name | Focus Areas |
|------|--------------|-------------|
| `executive` | Executive | Portfolio health, strategic metrics |
| `developer` | Developer | Code quality, PR status, tasks |
| `security` | Security Lead | Vulnerabilities, compliance, risk |
| `operations` | Operations/SRE | Alerts, SLOs, incidents |
| `admin` | Admin | System configuration, tenants |

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **persona-specific DevSecOps view** (e.g., Security Lead, SRE, Developer) with tailored KPIs, focus areas, and suggested actions.

**Primary goals:**

- Show metrics and insights that matter most to a specific persona.
- Highlight focus areas (e.g., "Critical vulnerabilities", "Failed checks").
- Offer links into relevant DevSecOps and project views.

**Non-goals:**

- Replace the global DevSecOps dashboard; this is persona-specific.

---

## 2. Users, Personas, and Real-World Scenarios

This page is **per persona** by design.

**Example personas:**

- Security Lead.
- SRE / Ops.
- Engineering Manager.
- Developer.

**Key scenarios:**

1. **Security Lead view**
   - Sees counts of open high/critical vulnerabilities.
   - Focus areas direct them to specific phases or projects.

2. **SRE view**
   - Sees alert/incident metrics.
   - Focus areas link to Monitor or Run/Operate phases.

---

## 3. Content & Layout Overview

- **Persona header:**
  - Persona name, description, maybe avatar/icon.
- **KPI cards:**
  - Metrics relevant to the persona (open risks, failing checks, incidents, etc.).
- **Focus area cards:**
  - Each focus area summarises a problem area and links into phase boards, reports, or project views.
- **Suggested actions:**
  - Links like "Review failing checks", "Prioritize open risks".

---

## 4. UX Requirements – User-Friendly and Valuable

- **Plain persona descriptions:**
  - Clarify what each persona typically cares about.
- **Actionable focus areas:**
  - Each focus area card should clearly state what the user can do next.

---

## 5. Completeness and Real-World Coverage

Persona dashboards should:

1. Surface the **top 3–5 concerns** for that persona.
2. Work across multiple projects/workspaces.
3. Provide **entry points** into detailed DevSecOps views and App Creator projects.

---

## 6. Modern UI/UX Nuances and Features

- **Persona switcher:**
  - Quick way to move between personas.
- **Visual identity:**
  - Each persona may have subtle color/icon distinctions.

---

## 7. Coherence and Consistency Across the App

- Metrics and labels must line up with DevSecOps dashboard, reports, and items.
- Links should use the same routes as other DevSecOps surfaces.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops`
- Route implementation: `src/routes/devsecops/persona/$slug.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Show which concrete projects each persona view is summarizing.
2. Add drill-down by workspace or environment.
3. Provide export/share for persona-specific summaries.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Security Lead
Subtitle: Posture, risks, and priorities for security leadership.

[ Persona: ● Security Lead   ○ SRE / Ops   ○ Engineering Manager   ○ Developer ]

[ KPI Row ]
-------------------------------------------------------------------------------
| Critical vulns: 3  | High vulns: 12 | Non‑compliant pipelines: 2 | MTTR: 36m |
-------------------------------------------------------------------------------

[ Focus Areas ]
-------------------------------------------------------------------------------
| Critical vulns in Prod                          | Policy coverage gaps       |
-------------------------------------------------------------------------------
| 3 Critical findings across 2 services           | 4 repos missing required   |
| • payments‑service                              | checks (SAST, secrets).    |
| • checkout‑service                              |                            |
| [View affected services] [Open report]          | [View repos] [Open report] |
-------------------------------------------------------------------------------

[ Suggested Actions ]
- "Review critical vulns in Prod" → opens Security & Compliance report, filtered to Prod.
- "Harden branch protection rules" → opens DevSecOps Settings → Integrations.
```
