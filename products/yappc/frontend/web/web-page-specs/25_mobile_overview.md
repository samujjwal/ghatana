# 25. Mobile Overview – Dashboard – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 5. Mobile Shell & Views](../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile)

**Code files:**

- `src/routes/mobile/overview.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **mobile dashboard** with high-level KPIs and recent activity so users can quickly check platform health from their phone.

**Primary goals:**

- Show key metrics (total projects, builds, success rate, open issues).
- Show a simplified recent activity feed.
- Integrate with Capacitor for local notifications.

**Non-goals:**

- Replace detailed Build/Deploy/Monitor or DevSecOps dashboards.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- On-call engineers, leads, and developers wanting a quick overview.

**Key scenarios:**

1. **On-call check**
   - User opens overview to see if there are open issues before bed.

2. **Morning snapshot**
   - Lead quickly checks metrics while commuting.

---

## 3. Content & Layout Overview

- **Header KPIs:**
  - Total projects, active builds, success rate, open issues.
- **Recent activity:**
  - List of recent builds/deploys/incidents.
- **Swipeable drawer:**
  - For more detailed activity or filters.
- **Local notifications integration:**
  - Triggers or displays notifications via Capacitor APIs.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Glanceable metrics:**
  - KPIs must be large and readable.
- **Simple navigation:**
  - Tapping items should route to relevant mobile views.

---

## 5. Completeness and Real-World Coverage

Mobile overview should:

1. Tie metrics to specific projects/pipelines (even if indirectly at first).
2. Be responsive to connectivity changes.

---

## 6. Modern UI/UX Nuances and Features

- **Gestures:**
  - Swipe to open/close drawers.
- **Dark-mode friendly:**
  - Metrics and cards must be legible in dark theme.

---

## 7. Coherence and Consistency Across the App

- KPIs must align with desktop dashboards and DevSecOps metrics.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#5-mobile-shell--views--mobile`
- Route implementation: `src/routes/mobile/overview.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Explicitly tie metrics to specific projects/pipelines.
2. Add navigation from metrics to project-level views.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Overview
Subtitle: Quick health snapshot for App Creator on mobile.

Top section (KPIs)
-------------------------------------------------------------------------------
| Projects: 12     | Active builds: 3   | Success rate: 91%   | Open issues: 7 |
-------------------------------------------------------------------------------

Recent activity (list)
-------------------------------------------------------------------------------
- 10:12 – Build #1239 Succeeded on main (e‑commerce‑web)
- 09:50 – Deploy #78 rolled out to Staging
- 09:30 – Alert resolved: "Checkout error spike" (P1)
-------------------------------------------------------------------------------

Swipeable drawer (conceptual)
- Pull up to see more detailed activity and filters (env, service).

Bottom navigation (from mobile shell)
- Tabs: Dashboard / Projects / Alerts (Overview is the Dashboard tab).
```
