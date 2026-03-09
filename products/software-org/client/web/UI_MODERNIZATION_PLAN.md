# Software-Org UI/UX Modernization Plan

> **Vision**: A "dead simple", AI-first interface for the entire lifecycle of a Virtual Software Organization: **Create**, **Manage**, and **Operate**.

## 1. Core Philosophy: The "Multi-Level Command Center"

The UI will shift from a flat "Admin Panel" to a **Role-Based Command Center** that adapts to the user's perspective (CEO, Manager, or IC).

### The 3-Pillar Flow (Across Levels)

1.  **CREATE (The Genesis)**:
    - **Input**: Vision, Mission, Industry, Scale.
    - **AI Action**: Generates Org Structure, Departments, Key Roles (Agents), and Initial Norms.
    - **UX**: Conversational Wizard / "God Mode" Setup.
2.  **MANAGE (The Blueprint)**:
    - **Input**: Tweaking the AI generation, Restructuring, Hiring.
    - **UX**: Visual Org Chart, Drag-and-Drop Agent Assignment, Natural Language Norm Editor.
    - **Levels**:
      - _CEO_: Reorg Departments, Set Global Norms.
      - _Manager_: Hire for Team, Set Team Norms, Budget Planning.
3.  **OPERATE (The Pulse)**:
    - **Input**: Directives, Approvals, Daily Work.
    - **UX**: Real-time Activity Stream, KPI Dashboard, Anomaly Alerts.
    - **Levels**:
      - _CEO_: Company Health, Strategic Initiatives.
      - _Manager_: Team Velocity, Performance Reviews, Incidents.
      - _IC_: Personal Growth, Time Off, Assigned Tasks.

---

## 2. Detailed UI/UX Plan

### Pillar 1: CREATE (Onboarding & Setup)

**Goal**: Go from "Zero" to "Fully Staffed Virtual Org" in < 5 minutes.

- **View**: `Landing / Genesis`
- **Components**:
  - **Vision Input**: "I want to build a fintech app for teenagers."
  - **AI Architect**: A chat interface that asks clarifying questions ("What tech stack?", "Compliance level?").
  - **Blueprint Preview**: Real-time generation of the proposed Org Structure (e.g., "Suggested: 1 CTO, 2 Backend Devs, 1 Compliance Officer").
- **Action**: "Materialize Organization" (Triggers backend to instantiate Holons/Agents).

### Pillar 2: MANAGE (Configuration & Structure)

**Goal**: Intuitive, visual management of the organization's "Physics" and "Staff".

- **View**: `Org Chart / Blueprint` (Replaces `/org/restructure`)
  - **Visual Hierarchy**: Interactive tree view of Departments (Holons).
  - **Agent Cards**: Click to see Agent Persona, Skills, and Current State.
  - **Drag-and-Drop**: Move agents between departments (Restructuring).
- **View**: `Culture & Norms`
  - **Natural Language Editor**: "Developers must review PRs within 24h" -> AI converts to OCL.
  - **Norm Library**: Pre-built templates (Agile, Waterfall, Startup Mode).
- **View**: `Hiring Hall` (Agent Marketplace) (Replaces `/build/agents`)
  - **Catalog**: Browse available Agent Templates (from `SoftwareAgentFactory`).
  - **Customization**: Tweak Agent "Personality" (Strict vs. Lenient Reviewer).
- **View**: `Budget & Planning` (Refactors `/budget/planning`)
  - **Fiscal Allocation**: Allocate budget to departments.
  - **Resource Planning**: Headcount planning vs. Budget.

### Pillar 3: OPERATE (Execution & Monitoring)

**Goal**: Monitor health, intervene when necessary, and measure outcomes.

- **View**: `Command Dashboard` (Context-aware)
  - **CEO View**: Health Scores (Productivity, Quality, Morale), Critical Alerts (Incidents).
  - **Manager View**: Team Velocity, Active Sprints, Performance Reviews (`/team/performance-reviews`).
  - **IC View**: My Tasks, Growth Path (`/ic/growth`), Time Off (`/ic/time-off`).
- **View**: `Live Operations` (The "Floor")
  - **Activity Feed**: Real-time stream of Agent actions.
  - **Work Queue**: Pending Approvals (`/operate/approvals`), HITL decisions (`/operate/queue`).
  - **Incidents**: Active War Rooms (`/operate/incidents`).
- **View**: `Intelligence & Insights`
  - **AI Analyst**: "Why is velocity down?" -> "Code Review bottleneck in Engineering."
  - **Simulation**: "What if we add 2 more QA agents?" (Predictive modeling).

---

## 3. Technical Implementation Strategy

### A. Route Restructuring (React Router v7)

We will consolidate the fragmented routes into a cohesive structure:

```typescript
export default [
  index("./routes/genesis.tsx"), // The "Create" Wizard

  // The Main App Layout (Sidebar with Role Switcher)
  layout("./layouts/cockpit.tsx", [
    // 1. Dashboard (Context-aware)
    route("dashboard", "./routes/operate/dashboard.tsx"),

    // 2. Manage (Structure & Resources)
    route("structure", "./routes/manage/org-chart.tsx"), // Visual Org Chart
    route("culture", "./routes/manage/norms.tsx"), // Norms Editor
    route("staff", "./routes/manage/agents.tsx"), // Hiring Hall
    route("budget", "./routes/manage/budget.tsx"), // Budget Planning

    // 3. Operate (Execution)
    route("live", "./routes/operate/live-feed.tsx"), // Activity Stream
    route("tasks", "./routes/operate/tasks.tsx"), // Work Queue / Approvals
    route("incidents", "./routes/operate/incidents.tsx"), // Incident Mgmt

    // 4. People (HR & Growth)
    route("people/reviews", "./routes/people/reviews.tsx"), // Performance Reviews
    route("people/growth", "./routes/people/growth.tsx"), // IC Growth Paths

    // 5. Insights (AI & Analytics)
    route("insights", "./routes/operate/insights.tsx"),
  ]),
];
```

### B. Migration of Existing Features

| Existing Route              | New Location      | Action                                             |
| --------------------------- | ----------------- | -------------------------------------------------- |
| `/operate/dashboard`        | `/dashboard`      | **Refactor**: Make context-aware (CEO/Mgr/IC).     |
| `/operate/queue`            | `/tasks`          | **Keep**: Integrate into "Work Queue".             |
| `/operate/approvals`        | `/tasks`          | **Merge**: Combine with Queue.                     |
| `/operate/incidents`        | `/incidents`      | **Keep**: Enhance with AI summaries.               |
| `/team/performance-reviews` | `/people/reviews` | **Keep**: Connect to Agent Performance data.       |
| `/budget/planning`          | `/budget`         | **Keep**: Integrate with Org Chart.                |
| `/ic/growth`                | `/people/growth`  | **Keep**: Make accessible to ICs.                  |
| `/org/restructure`          | `/structure`      | **Refactor**: Replace with Visual Org Chart.       |
| `/build/agents`             | `/staff`          | **Refactor**: Convert to "Hiring Hall".            |
| `/build/workflows`          | `/structure`      | **Refactor**: Attach workflows to Departments.     |
| `/admin/*`                  | `N/A`             | **Deprecate**: Move settings to specific contexts. |

### C. AI Integration Points

1.  **Genesis Wizard**: Calls `virtual-org` AI service to generate `OrganizationConfig`.
2.  **Norm Editor**: NLP-to-Norm translation service.
3.  **Insight Engine**: RAG over Agent Logs/Metrics.
4.  **Performance Reviews**: AI drafts reviews based on Agent activity logs.

### D. Component Library Updates

- **Visualizations**: Adopt `React Flow` or similar for the Org Chart and Process Maps.
- **Chat Interface**: Embedded "Co-pilot" in the sidebar for ad-hoc queries.
- **Metrics**: Simplified Sparklines and "Traffic Light" indicators (Red/Amber/Green).

---

## 4. Migration Steps

1.  ✅ **Scaffold New Routes**: Create the `genesis`, `manage`, `people`, and `operate` folder structures.
2.  ✅ **Build Genesis Wizard**: Implement the "Vision -> Config" flow (`/genesis`).
3.  ✅ **Visual Org Chart**: Replace list views with a visual tree (`/manage/org-chart`).
4.  ✅ **Norms Editor**: Natural language norm definition (`/manage/norms`).
5.  ✅ **Hiring Hall**: Agent marketplace (`/manage/agents`).
6.  ✅ **Budget Planning**: Resource allocation (`/manage/budget`).
7.  ✅ **Live Feed**: Real-time activity stream (`/operate/live-feed`).
8.  ✅ **Tasks Queue**: Unified work queue (`/operate/tasks`).
9.  ✅ **AI Insights**: Chat-based analytics (`/operate/insights`).
10. ✅ **Performance Reviews**: Agent reviews (`/people/reviews`).
11. ✅ **Growth Paths**: Skill development (`/people/growth`).
12. ✅ **Update Routes**: Updated `routes.ts` with new structure.
13. ✅ **Update Sidebar**: Updated `Layout.tsx` with new navigation.
14. ⏳ **Connect to Backend**: Ensure `software-org` backend exposes the necessary APIs.

## 5. Implementation Summary

### Files Created

| File                           | Purpose                                        |
| ------------------------------ | ---------------------------------------------- |
| `routes/genesis.tsx`           | AI-powered organization creation wizard        |
| `routes/manage/org-chart.tsx`  | Visual organization hierarchy with drag-drop   |
| `routes/manage/norms.tsx`      | Natural language norm definition               |
| `routes/manage/agents.tsx`     | Agent marketplace / Hiring Hall                |
| `routes/manage/budget.tsx`     | Budget & resource allocation                   |
| `routes/operate/live-feed.tsx` | Real-time activity stream                      |
| `routes/operate/tasks.tsx`     | Unified work queue (PRs, approvals, decisions) |
| `routes/operate/insights.tsx`  | AI Analyst chat interface                      |
| `routes/people/reviews.tsx`    | Performance reviews with AI summaries          |
| `routes/people/growth.tsx`     | Agent learning & development paths             |

### Updated Files

| File         | Changes                                                                 |
| ------------ | ----------------------------------------------------------------------- |
| `routes.ts`  | Added new routes, reorganized into CREATE/MANAGE/OPERATE/PEOPLE pillars |
| `Layout.tsx` | Updated sidebar navigation to match new architecture                    |

### New Sidebar Structure

```
CREATE
  ✨ Genesis

MANAGE
  🏢 Org Chart
  📜 Norms
  🤖 Agents
  💰 Budget

OPERATE
  📊 Dashboard
  📡 Live Feed
  📋 Tasks
  💡 Insights
  🚨 Incidents

PEOPLE
  ⭐ Reviews
  🌱 Growth

ADMIN (for admins)
  ⚙️ Config: Agents
  🔗 Config: Workflows
  🔧 Settings
```

## 6. Next Steps (Backend Integration)

1.  **Genesis API**: Connect wizard to `virtual-org` AI service for org generation.
2.  **Norm Translation**: Implement NLP-to-Norm service for natural language parsing.
3.  **Live Feed WebSocket**: Real-time activity stream from backend.
4.  **Insights RAG**: Connect to AI service for data-driven insights.
5.  **Performance Data**: Pull agent metrics for reviews and growth tracking.
