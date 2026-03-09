# 0. Application Shell & Navigation – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 0. Application Shell & Navigation](../WEB_PAGE_FEATURE_INVENTORY.md#0-application-shell--navigation)

**Code files:**

- `src/app/App.tsx`
- `src/app/Layout.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a consistent, reliable frame around every page – including navigation, context, theme, and error handling – so users always know **where they are, what they’re looking at, and how to move to the next step.**

**Primary goals:**

- **Global context:** Clearly show organization/tenant, environment, and product area.
- **Wayfinding:** Give a stable, predictable navigation structure across all screens.
- **Shared controls:** Centralize theme, tenant, and basic account controls.
- **Resilience:** Catch unexpected errors and recover gracefully.

**Non-goals:**

- Implement page-specific business logic or filters (these live in each feature page).
- Show detailed metrics or workflows (those belong to Dashboard, Monitor, Automation, etc.).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Engineering Manager / Director:** jumps across Dashboard, Departments, Reports.
- **SRE / On-call Engineer:** lives in Real-Time Monitor, Automation Engine, HITL Console.
- **Security Lead:** uses Security, AI Intelligence, Reports.
- **Data/ML Engineer:** toggles between Model Catalog, ML Observatory, Event Simulator.

**Key real-world scenarios:**

1. **Context switch during incident**
   - An SRE starts in **Real-Time Monitor**, sees CPU spikes, then quickly navigates to **Automation Engine** to run a remediation workflow and to **Reports** for historical patterns – all using the same sidebar and tenant context.

2. **Leadership review**
   - An Engineering Director opens the app, lands on **Dashboard**, verifies it’s the right **tenant / environment** from the header context, then jumps to **Departments** and **Reports** without getting lost.

3. **Security posture check**
   - A Security Lead opens **Security**, sees overall posture, then later checks **AI Intelligence** and **Reports**; the header and sidebar make it clear they are always inside the same product.

---

## 3. Content & Layout Overview

**Core layout elements (from `MainLayout` and `App`):**

- **Global Error Boundary (`App.tsx`):**
  - Wraps the entire app, shows a full-screen friendly error state with a “Reload Page” button.

- **Sidebar (left):**
  - Product name (Ghatana).
  - Navigation links (with emoji icons) to:
    - `/` – Dashboard
    - `/departments` – Departments
    - `/workflows` – Workflows
    - `/hitl` – HITL Console
    - `/simulator` – Event Simulator
    - `/reports` – Reports
    - `/ai` – AI Intelligence
    - `/security` – Security
  - Collapsible (wide ⇄ narrow) with persistent collapsed state in `localStorage`.

- **Header (top of main content):**
  - Static title: **“AI-First DevSecOps”**.
  - **Tenant selector** – drop-down with All Tenants / Tenant 1 / Tenant 2; state persisted to `localStorage`.
  - **Theme selector** – System / Light / Dark; applied via `data-theme` on `documentElement` and persisted.
  - User icon button (placeholder for account/actions).

- **Content area:**
  - Standard padding and scrolling behavior; renders the route content via `children`.

### 3.1 Navigation Structure (Primary vs Secondary Routes)

- **Primary sidebar navigation:**
  - Core product areas that appear in the left sidebar and are always available:
    - `/` – Dashboard
    - `/departments` – Departments
    - `/workflows` – Workflows
    - `/hitl` – HITL Console
    - `/simulator` – Event Simulator
    - `/reports` – Reports
    - `/ai` – AI Intelligence
    - `/security` – Security
- **Secondary/contextual routes:**
  - Advanced or supporting areas that are typically reached via in-page links, buttons, or header icons rather than the main sidebar:
    - `/ml-observatory` – ML Observatory
    - `/realtime-monitor` – Real-Time Monitor
    - `/automation-engine` – Automation Engine
    - `/models` – Model Catalog
    - `/settings` – Settings
    - `/help` – Help Center
    - `/export` – Data Export
- This distinction keeps the primary navigation stable and simple while still allowing deep, workflow-specific pages to be discoverable from the surfaces that need them.

---

## 4. UX Requirements – User-Friendly and Valuable

### 4.1 Navigation & Wayfinding

- **Highlight current section:**
  - Sidebar item for the active route should be visually distinguished (background, text weight) so users always see where they are.
- **Human-readable labels:**
  - Keep labels in plain language (Dashboard, Workflows, Security) and avoid jargon.
- **Consistent order:**
  - Sidebar order should remain constant across sessions to build muscle memory.
- **Responsive behavior:**
  - On smaller screens, sidebar should either collapse or be toggleable via a clear button.

### 4.2 Context & Orientation

- **Global context banner (to be added):**
  - A compact banner under the header summarizing:
    - Current **tenant/organization**.
    - Current **environment** (e.g., Staging vs Production – once available).
    - Optional **region** if multi-region is introduced.
  - Simple, non-technical text, e.g., “Viewing: Tenant A • Staging • us-east-1”.

- **Active page description:**
  - Every page should provide a one-line description below its h1 title, written in layman terms, e.g.:
    - "Control Tower – organization-wide health and AI insights for your software delivery."

### 4.3 Error Handling

- **Friendly wording:**
  - Current error copy is acceptable but can be tuned to:
    - Explain that the error is local to this browser session.
    - Suggest simple next steps (reload, contact support, link to Help Center).

- **Link to Help Center:**
  - Include a link like “View troubleshooting guide” that routes to `/help` with a pre-selected “Errors & Troubleshooting” category.

### 4.4 Accessibility

- **Keyboard navigation:**
  - Sidebar links, header controls, and collapse button must be reachable and usable via keyboard (Tab, Enter, Space).
- **ARIA & semantics:**
  - Mark sidebar as `nav` and main content as `main`.
  - Add `aria-label` to sidebar navigation and the collapse button.
- **Contrast:**
  - Ensure dark and light themes pass contrast requirements, especially for active nav item and focus states.

---

## 5. Completeness and Real-World Coverage

To be complete and realistic, the shell must support:

1. **Multi-tenant operations:**
   - Tenant selector should clearly affect all tenant-scoped data (Dashboard, Departments, Reports, etc.).
   - Display the **currently selected tenant** clearly in the header and global context banner.

2. **Environment separation:**
   - For real-world deployments, environments (prod, staging, dev) should appear in the global context; ideally colored subtly differently (e.g., Prod in red/orange banner) to prevent mistakes.

3. **Role-agnostic navigation:**
   - Sidebar should work equally well for SREs, Security, PMs, and ML engineers, without being biased toward one persona.

4. **Error containment:**
   - The error boundary must prevent a single bad page from blanking the whole shell where possible, while still allowing recovery.

---

## 6. Modern UI/UX Nuances and Features

**Required behaviors and polish for a modern experience:**

- **Smooth sidebar collapse/expand:**
  - Already present (Tailwind `transition-all duration-300`); ensure it remains smooth on low-end devices.

- **Persistent preferences:**
  - Sidebar collapsed state, theme, and tenant are persisted; verify consistency across reloads and sessions.

- **Hover and focus feedback:**
  - Sidebar links and header buttons must have hover and focus styles that are visually evident but not distracting.

- **Mobile-first layout:**
  - Main layout should gracefully degrade on narrow viewports:
    - Sidebar collapses to icons or a hamburger-triggered drawer.
    - Header controls wrap instead of overflowing.

- **Microcopy:**
  - Tooltip for collapse toggle (already present via `title`) should use clear wording like “Collapse navigation” / “Expand navigation”.

- **Performance:**
  - Layout should not introduce heavy re-renders on route change (keep shell static, only children updated).

---

## 7. Coherence and Consistency Across the App

The shell is responsible for enforcing consistency in:

- **Typography and spacing:**
  - Shared base font sizes for titles (`text-3xl` for primary page titles), subtitles, and body text.

- **Color usage:**
  - Shared palette across Dashboard, Monitor, Automation, etc., via `tokens.css` and theme provider.

- **Page header pattern:**
  - Every page uses: `h1` + short subtitle + optional CTAs.

- **Navigation semantics:**
  - A single mental model: "Left is _where_ I am, top is _how_ I see it (tenant/theme), center is _what_ I’m doing."

- **Global context & filters:**
  - Tenant selector, environment indicator, and time-range controls should use consistent labels and option sets across Dashboard, Reports, Real-Time Monitor, and Data Export.
- **Decision & approval flows:**
  - Approve / Defer / Reject semantics in Dashboard insights, AI Intelligence, HITL Console, and Automation Engine should share the same underlying workflow and audit trail.
- **Severity & priority semantics:**
  - Severity levels such as `Critical/Warning/Info` and priorities such as `P0/P1/P2` should be aligned across Real-Time Monitor, HITL Console, Security, and Reporting so users develop a single mental model of urgency.

If a new page is added, this spec should be referenced to ensure:

- It mounts within `MainLayout`.
- It provides its own h1 + subtitle.
- It uses existing atoms (tenant/theme) instead of inventing new context state.

---

## 8. Links to More Detail & Working Entry Points

**Internal links (docs & code):**

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#0-application-shell--navigation`
- Router configuration: `src/app/Router.tsx`
- Layout implementation: `src/app/Layout.tsx`
- Error boundary implementation: `src/app/App.tsx`

**User-facing navigation links from the shell:**

- Dashboard: `/`
- Departments: `/departments`
- Workflows: `/workflows`
- HITL Console: `/hitl`
- Event Simulator: `/simulator`
- Reports: `/reports`
- AI Intelligence: `/ai`
- Security: `/security`
- (Advanced) ML Observatory: `/ml-observatory`
- (Advanced) Real-Time Monitor: `/realtime-monitor`
- (Advanced) Automation Engine: `/automation-engine`

These paths must remain **stable** and be used consistently in docs, help center entries, and cross-links from other pages.

---

## 9. Open Gaps & Enhancement Plan

Summarizing key improvements for the shell itself:

1. **Add global context banner**
   - Implement a small bar under the header that shows Tenant, Environment, and Region.

2. **Expose active page description**
   - Standardize a `pageDescription` prop or component pattern that all pages use.

3. **Integrate contextual help**
   - Add a help icon in the header that routes to `/help` with query params or hash (e.g., `#/dashboard`), so Help Center can highlight the relevant section.

4. **Unify active state styling**
   - Ensure sidebar active state visually matches other navigations (e.g., in sub-pages or future breadcrumbs).

5. **Improve accessibility semantics**
   - Audit and add ARIA roles/labels for nav, main, and collapse controls.

This spec should be kept in sync as we refine the shell so it remains the single source of truth for how navigation and global context behave.

---

## 10. Mockup / Expected Layout & Content

This is a **textual mockup** describing how the Application Shell & Navigation should look and behave on a typical desktop screen.

### 10.1 Desktop Layout (Standard Width)

Overall layout:

```text
+--------------------------------------------------------------------------------+
| Header: [Product Name]      [Tenant ▼]  [Theme ▼]                 [User Icon] |
|        "AI-First DevSecOps"                                              |
+---------------------+--------------------------------------------------------+
| Sidebar             | Page Header                                           |
| (nav)               |  H1: <Page Title>                                     |
|                     |  Subtitle: <One-line, layman description>             |
|  Ghatana            |                                                        |
|  ─ Dashboard        | Main Content                                          |
|  ─ Departments      |  [Page-specific content rendered by route]           |
|  ─ Workflows        |                                                        |
|  ─ HITL Console     |                                                        |
|  ─ Event Simulator  |                                                        |
|  ─ Reports          |                                                        |
|  ─ AI Intelligence  |                                                        |
|  ─ Security         |                                                        |
|                     |                                                        |
| [Collapse ▸]        |                                                        |
+---------------------+--------------------------------------------------------+
```

**Header content (top bar):**

- Left side:
  - Product name (text only): `AI-First DevSecOps`.
  - Optional small brand mark (no heavy logo required in shell spec).
- Right side:
  - **Tenant dropdown** with label-less, but self-explanatory options:
    - `All Tenants`
    - `Tenant A`
    - `Tenant B`
  - **Theme dropdown** with options:
    - `System`
    - `Light`
    - `Dark`
  - **User icon button**, simple circular icon that will later open an account menu.

**Sidebar (expanded state):**

- Fixed width (~256px) on desktop.
- Content from top to bottom:
  - Product label: `Ghatana`.
  - Navigation items (each row is clickable, full width):
    - `📊  Dashboard`
    - `🏢  Departments`
    - `🔄  Workflows`
    - `✋  HITL Console`
    - `⚡  Event Simulator`
    - `📈  Reports`
    - `🤖  AI Intelligence`
    - `🔒  Security`
  - Footer: a full-width button `←` or `→` to collapse/expand sidebar.

**Sidebar (collapsed state):**

- Narrow width (~80px).
- Only icons are shown; labels are hidden but available via tooltip.
- The collapse button now shows `→` to signal expansion.

### 10.2 Mobile / Narrow Viewport Behavior (Conceptual)

On a narrow viewport (e.g., tablet portrait or mobile):

- Sidebar collapses into a **hamburger menu** placed near the top-left of the header.
- Tapping the hamburger reveals the same nav items in an overlay drawer.
- Header wraps controls into two rows if needed, keeping the **page title and subtitle visible** above the main content.

```text
┌───────────────────────────────────────────────┐
│ ☰  AI-First DevSecOps                        │
│ Tenant ▼    Theme ▼               [User]     │
└───────────────────────────────────────────────┘
│ H1: <Page Title>                              │
│ Subtitle: <Description>                       │
│                                               │
│ [Page-specific content]                       │
```

### 10.3 Error Boundary Mockup (Global Failure State)

When an unrecoverable error occurs in the React tree:

- The entire content area is replaced by a centered error screen; sidebar/header may remain or be replaced depending on severity (current implementation uses full-screen overlay).

```text
+--------------------------------------------------------------+
| [Pale red background]                                       |
|                                                              |
|   Something went wrong                                       |
|   --------------------------------------------------------   |
|   <Error message text>                                      |
|                                                              |
|   [ Reload Page ]                                            |
|   [ View troubleshooting guide ] (routes to /help)           |
+--------------------------------------------------------------+
```

Copy guidelines:

- Title: `Something went wrong`.
- Body: short, human message (no stack traces), e.g.:
  - "We couldn’t load this view. This might be a temporary issue."
- Actions:
  - Primary: `Reload Page` (refresh the current route).
  - Secondary: `View troubleshooting guide` → `/help#errors-and-troubleshooting`.

### 10.4 Sample Page Header Within the Shell

Example for the Dashboard page rendered inside the shell:

```text
Header: AI-First DevSecOps    [All Tenants ▼]  [Dark ▼]   [User]

H1: Control Tower
Subtitle: Organization-wide metrics and AI insights

[Dashboard-specific filters and content...]
```

Every other page will follow the same **header + subtitle** pattern, only changing:

- The `H1` text.
- The one-line subtitle in plain language.
- The page-specific content below.

This mockup should be used by designers and engineers as the baseline visual/interaction contract for the Application Shell & Navigation.
