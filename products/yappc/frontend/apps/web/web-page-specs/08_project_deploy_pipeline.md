# 8. Deploy – Deployment Pipeline – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Added reuse-first guidance

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.7 Deploy](../APP_CREATOR_PAGE_SPECS.md#27-deploy----deploytsx)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/app/project/deploy.tsx` | Deploy pipeline route |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/app/w/:workspaceId/p/:projectId/deploy` | Project deployments |

---

## 🔁 REUSE-FIRST: Required Components

**MUST use these from `@yappc/ui`:**

| Component | Import | Purpose |
|-----------|--------|---------|
| `DataTable` | `@yappc/ui/components/DevSecOps` | Deployment history |
| `KPICard` | `@yappc/ui/components/DevSecOps` | Deploy metrics |
| `Timeline` | `@yappc/ui/components/DevSecOps` | Deployment timeline |

**One-Click Deploy:** "Deploy to [Staging/Prod]" should be ONE click.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **deployment pipeline view** for each project, covering environments, recent deployments, and bulk operations such as rollback or promote.

**Primary goals:**

- Show recent deployments by environment and target.
- Allow selecting deployments and triggering actions.
- Stream deployment status via WebSocket where available.

**Non-goals:**

- Duplicate build logic (that lives in Build).
- Replace full release management tooling.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Developers:** Check whether their build was deployed.
- **Release Engineers / SREs:** Manage rollouts, rollbacks, and promotions.

**Key scenarios:**

1. **Verifying rollout**
   - After a successful build, developer opens Deploy tab.
   - Checks that the build reached Dev/Staging/Prod.

2. **Rolling back a bad deploy**
   - SRE notices issues in Monitor.
   - Opens Deploy tab, selects most recent deployment, clicks **Rollback**.

3. **Promoting canary to full rollout**
   - Release engineer promotes a canary deployment to full production.

---

## 3. Content & Layout Overview

- **GraphQL project data:**
  - Fetches deployments and environments.
- **WebSocket data:**
  - Updates deployment statuses in real time.
- **Deployment table:**
  - Columns: ID, environment, status, version, started/finished times, triggered by.
  - Row selection for bulk actions.
- **BulkActionBar:**
  - Actions like rollback, promote, cancel (depending on status).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Environment clarity:**
  - Use clear labels (Dev, Staging, Prod) and maybe color tags.
- **Status semantics:**
  - Distinguish between in-progress, succeeded, failed, rolled back.
- **Correlate with Monitor:**
  - Provide links from a deployment row to Monitor or DevSecOps report for that release.

---

## 5. Completeness and Real-World Coverage

The Deploy tab should support:

1. **Multiple environments** per project.
2. **Canary and blue/green strategies** (future), with clear indicators.
3. **Auditability:** who deployed what and when.

---

## 6. Modern UI/UX Nuances and Features

- **Timeline / ordering:**
  - Most recent deployments at top; timeline feel.
- **Bulk operations UX:**
  - Confirmation dialogs for destructive actions like rollback.
- **Responsive table:**
  - Hide less critical columns on small screens.

---

## 7. Coherence and Consistency Across the App

- Deployment statuses should align with DevSecOps reports and incident timelines.
- Bulk selection behavior should mirror the Build view.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#27-deploy----deploytsx`
- Route implementation: `src/routes/app/project/deploy.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Make environment labels and groupings explicit (Dev/Staging/Prod).
2. Correlate deployments with Monitor alerts and DevSecOps items.
3. Add environment-level summary cards (e.g., last successful deploy to Prod).

---

## 10. Mockup / Expected Layout & Content

```text
H1: Deployments
Subtitle: Recent releases by environment.

[ Filters: Environment ▼ (All) | Time range ▼ (Last 7 days) | Status ▼ (All) ]

[ Environment Summary ]
-------------------------------------------------------------------------------
| Dev      | Staging                      | Prod                             |
-------------------------------------------------------------------------------
| Last:    | Last:                        | Last:                            |
| 1h ago   | 2h ago                       | 3d ago                           |
| Status:  | Status:                      | Status:                          |
| Healthy  | Healthy (1 canary in prog.) | Warnings (elevated error rate)   |
-------------------------------------------------------------------------------

[ Deployments Table ]
-------------------------------------------------------------------------------
□  Env   Status       Version   Build   Started      Duration   By
-------------------------------------------------------------------------------
□  Prod  Succeeded    1.2.3     #1239   09:15        5m 02s    alice
□  Stg   Failed       1.2.4     #1240   10:22        2m 15s    bob
□  Dev   Succeeded    1.2.4     #1240   10:05        4m 48s    ci-bot
-------------------------------------------------------------------------------

Row interactions:
- Click a row → opens deployment detail (logs, steps, linked build).
- Badge/link for Build column → navigates to Build tab with that build selected.

Bulk selection:
- When 1+ rows checked:
  [ Roll back selected ]  [ Promote selected ]  [ Cancel selected ]
```
