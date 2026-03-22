# 9. Monitor – Observability & Search – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Added reuse-first guidance

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.8 Monitor](../APP_CREATOR_PAGE_SPECS.md#28-monitor----monitortsx)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/app/project/monitor.tsx` | Monitor route |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/app/w/:workspaceId/p/:projectId/monitor` | Project observability |

---

## 🔁 REUSE-FIRST: Required Components

**MUST use these from `@yappc/ui`:**

| Component | Import | Purpose |
|-----------|--------|---------|
| `KPICard` | `@yappc/ui/components/DevSecOps` | Health metrics |
| `DataTable` | `@yappc/ui/components/DevSecOps` | Logs/events table |
| `SearchBar` | `@yappc/ui/components/DevSecOps` | Log search |
| `Timeline` | `@yappc/ui/components/DevSecOps` | Event timeline |

**Dashboard should show health at a glance – no drilling required for basic status.**

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **project-level observability console** with search over logs/events/alerts so users can investigate production behavior and issues.

**Primary goals:**

- Allow searching and filtering observability data for a single project.
- Surface active alerts and important signals.
- Integrate with WebSocket to stream new events.

**Non-goals:**

- Replace full-featured observability stacks; this is focused on project-level usage.
- Act as a DevSecOps-wide posture view (that’s the DevSecOps dashboard).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Developers:** Debug errors and performance issues.
- **SREs / Ops:** Monitor alerts and triage incidents.
- **Tech Leads:** Spot recurring patterns across releases.

**Key scenarios:**

1. **Investigating an error spike**
   - Monitor tab shows increased error count.
   - Engineer filters by error type and time range.
   - Uses results to jump to relevant builds/deploys.

2. **Checking production health before a release**
   - Lead checks recent alerts and key metrics.

---

## 3. Content & Layout Overview

- **WebSocket data stream:**
  - Live feed of events/alerts.
- **Search components:**
  - `SearchProvider`, `SearchInterface`, `SearchResults` over mock alerts/errors.
- **Metrics panels (future):**
  - Error rate, latency, throughput for the project.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Search simplicity:**
  - Query language should be approachable (plain text, basic filters).
- **Highlight severity:**
  - Use colors/icons to distinguish critical vs warning vs info events.
- **Time-awareness:**
  - Provide time range controls and timestamps on results.

---

## 5. Completeness and Real-World Coverage

Monitor should support:

1. **Multiple event types**: logs, errors, alerts.
2. **High-volume streams** using pagination / infinite scroll.
3. **Correlation** with builds and deployments.

---

## 6. Modern UI/UX Nuances and Features

- **Real-time updates:**
  - New events should appear smoothly without jarring scroll jumps.
- **Pinning & starring:**
  - Future: allow pinning important queries or saved searches.
- **Dark-theme friendly:**
  - Observability views often benefit from dark mode; ensure contrast is good.

---

## 7. Coherence and Consistency Across the App

- Severity and priority semantics must match DevSecOps, HITL-style consoles, and any shared severity model.
- Filter vocabulary (time ranges, environment tags) should align with DevSecOps and Reports.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#28-monitor----monitortsx`
- Route implementation: `src/routes/app/project/monitor.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Add KPI cards for error rate, latency, and throughput.
2. Deep-link from Monitor events to specific deployments and canvas nodes.
3. Support saved searches and alert creation (future).

---

## 10. Mockup / Expected Layout & Content

```text
H1: Monitor
Subtitle: Search and alerts for this project's runtime behavior.

[ KPI Row ]
-------------------------------------------------------------------------------
| Error rate (5xx/min)   | P95 latency        | Active alerts                 |
| 3.2/min (↑)            | 420ms (↗ vs 7d)   | 1 Critical • 3 High • 5 Low  |
-------------------------------------------------------------------------------

[ Search & Filters ]
Query: [ status:ERROR AND service:checkout               ] [ Run ]
Time range: [ Last 1h ⌄ ]   Env: [ Prod ⌄ ]

[ Results ]
-------------------------------------------------------------------------------
Time       Severity   Service    Summary
-------------------------------------------------------------------------------
12:34:10   CRITICAL   checkout   HTTP 500 on /api/orders (traceId=abc‑123)
12:30:02   HIGH       checkout   Slow query on /api/cart (P95 2.3s)
12:28:45   MEDIUM     auth       Spike in 401s from /login
-------------------------------------------------------------------------------

Row interactions:
- Click summary → opens event detail drawer (stack trace, attributes, tags).
- "View related deployment" link inside detail → jumps to Deploy tab.
```
