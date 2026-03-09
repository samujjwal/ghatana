# Web – Global Concepts & UX Contracts

This document centralizes **cross-cutting concepts and UX contracts** that all web page specs must follow.

Use this as a shared reference when:

- Designing or updating any page spec in `web-page-specs/`.
- Implementing new routes/components that should behave consistently with existing surfaces.

---

## 1. Navigation Model

### 1.1 Primary vs Secondary Routes

- **Primary sidebar navigation** (always visible in the main shell sidebar):
  - `/` – Dashboard
  - `/departments` – Departments
  - `/workflows` – Workflows
  - `/hitl` – HITL Console
  - `/simulator` – Event Simulator
  - `/reports` – Reports
  - `/ai` – AI Intelligence
  - `/security` – Security

- **Secondary/contextual routes** (typically reached via in-page CTAs, buttons, or header icons):
  - `/ml-observatory` – ML Observatory
  - `/realtime-monitor` – Real-Time Monitor
  - `/automation-engine` – Automation Engine
  - `/models` – Model Catalog
  - `/settings` – Settings & Preferences
  - `/help` – Help Center
  - `/export` – Data Export Utility

**Contract:**

- Primary routes must remain stable and predictable over time.
- Secondary routes may evolve more frequently but should always be reachable from at least one primary surface in a way that matches user mental models (e.g., Automation Engine from Workflow Explorer or AI insights).

---

## 2. Page Header Pattern

All feature pages rendered inside the shell follow a shared header pattern:

- **H1:** Clear, human-readable page title.
- **Subtitle:** One-line, layman description of what the page is for.
- **Optional CTAs:** Primary and secondary actions near the header.

**Examples:**

- Dashboard: `Control Tower` – "Organization-wide metrics and AI insights."
- Event Simulator: `Event Simulator` – "Compose and emit simulated events for testing pipelines and workflows."
- Security: `Security & Compliance` – "Central view of access, audit events and compliance status."
- Real-Time Monitor: `Real-Time Monitor` – "Live metrics, anomalies and alerts for your services."

**Contract:**

- Every new spec must define an H1 + subtitle pair in this style.
- Subtitles must use plain, non-jargon language aimed at mixed-technical audiences.

---

## 3. Global Context & Filters

Several pages share the same context concepts: **tenant**, **environment**, and **time range**.

### 3.1 Tenant & Environment

- **Tenant selector** appears in the shell header and on certain pages (Dashboard, Reports, etc.).
- Environments (e.g., `Production`, `Staging`, `Development`) appear in the **global context banner** once implemented.

**Contract:**

- Tenant and environment must:
  - Use consistent labels and value sets across pages.
  - Influence only tenant/environment-scoped data.
  - Be visible near the top of the page (header + global context banner).

### 3.2 Time Range

- Time range controls exist on:
  - Dashboard (e.g., `Last 7 days`, `Last 30 days`, `Last 90 days`, `Custom`).
  - Reporting.
  - Data Export.
  - Potentially Real-Time Monitor (shorter windows like `Last 15 min`).

**Contract:**

- Time range options must be conceptually consistent across pages (e.g., `Last 7 days` always means the same thing).
- Comparison modes (e.g., Dashboard Compare Mode) must clearly explain the reference period.

---

## 4. Severity & Priority Semantics

Multiple surfaces represent urgency and risk:

- **Real-Time Monitor** – alert severities: `Critical`, `Warning`, `Info`.
- **HITL Console** – action priorities: `P0`, `P1`, `P2`.
- **Security / Reporting** – issue severities: `Critical`, `High`, `Medium`, etc.

**Contract:**

- `P0` actions in HITL correspond to **Critical** alerts/incidents.
- `P1` corresponds broadly to **Warning**-level issues.
- `P2` corresponds broadly to **Info** / routine or low-risk suggestions.
- Where both labels appear (e.g., in copy or tooltips), the relationship should be made explicit so operators see a single urgency model.

Specs that rely on this:

- Real-Time Monitor (`15_real_time_monitor.md`).
- HITL Console (`05_hitl_console.md`).
- Security Dashboard (`08_security_dashboard.md`).
- Reporting Dashboard (`07_reporting_dashboard.md`).

---

## 5. Decision & Approval Flows

Several surfaces let users **Approve / Defer / Reject** AI-generated insights or actions:

- Dashboard – AI Insights cards.
- AI Intelligence – insights feed and detail panel.
- HITL Console – AI-proposed actions.
- Automation Engine – workflows that may be triggered by insights or alerts.

**Contract:**

- The meaning of decisions is shared across these surfaces:
  - **Approve:** The system is allowed to execute or consider the recommendation/action as accepted.
  - **Defer:** No immediate change; keep item open for later review.
  - **Reject:** Explicitly decline the recommendation/action; should be reflected in history.
- All decisions should:
  - Generate a **decision record** (who, what, when, where from).
  - Be auditable via history in AI/HITL/Automation contexts.
  - Use consistent labels and button ordering (Approve → Defer → Reject or similar).

---

## 6. Data & Entity Consistency

Key shared entities include **departments**, **workflows**, **incidents**, **alerts**, **models**, and **experiments**.

**Contract:**

- **Departments:**
  - Identifiers and names must match across Departments Directory, Department Detail, Workflow Explorer, HITL, and Reports.
- **Workflows:**
  - Status and success metrics must align between Workflow Explorer and Automation Engine.
- **Models:**
  - Names and versions must match between Model Catalog and ML Observatory, and references from AI Intelligence.
- **Incidents/Alerts:**
  - Should be represented consistently across Dashboard, Real-Time Monitor, HITL, Security, and Reports.

Where specs introduce mock data for these entities, they should reuse consistent IDs/names where practical (e.g., `FraudDetector-v3`, `Payments Engineering`, `checkout-api`).

---

## 7. Error Handling & Help

Error handling and help are shared across the app:

- Global error boundary shows a friendly `Something went wrong` screen with:
  - Short human-readable error text.
  - `Reload Page` primary action.
  - `View troubleshooting guide` secondary action → `/help#errors-and-troubleshooting`.
- The shell will expose a **help icon** linking to Help Center (`/help`) with route-specific anchors where available.

**Contract:**

- Feature-level error states (load failures, empty states) should:
  - Use short, human copy.
  - Offer a clear next step (retry, change filters, contact support, or open Help Center).
- Help Center content should reference the same route names and terminology used in specs and UI.

---

## 8. Using This Document

When creating or updating any `web-page-specs/*.md` file:

- Reference this document for:
  - Navigation decisions (primary vs secondary).
  - Header and subtitle phrasing.
  - How to represent tenant/environment/time.
  - How to label severity, priority, and decisions.
- If a spec needs to diverge from these contracts for a good reason, it should explicitly call out the exception and why it exists.
