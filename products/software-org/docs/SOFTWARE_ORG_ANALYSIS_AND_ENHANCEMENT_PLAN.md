# Software-Org – Comprehensive Analysis & Enhancement Plan

> **Document Version:** 1.0.0  
> **Created:** 2025-12-03  
> **Author:** AI Analysis Agent  
> **Scope:** Full product analysis, virtual-org dependencies, feature inventory, and enhancement roadmap

---

## Executive Summary

Software-Org is a **comprehensive organization management system** built on the **Virtual-Org framework** within the Ghatana ecosystem. It provides AI-driven DevSecOps operations with persona-based access control across 10 departments and 11 AI agents. This document analyzes the current implementation, identifies dependencies on Virtual-Org, and proposes a comprehensive enhancement plan following the **reuse-first policy** from `copilot-instructions.md`.

---

## Part 1: Current State Analysis

### 1.1 Architecture Overview

```
products/software-org/
├── apps/
│   ├── web/                     # React 18+ SPA (571+ files)
│   │   ├── src/
│   │   │   ├── app/            # Routing, guards, providers
│   │   │   ├── components/     # Reusable UI components
│   │   │   ├── features/       # 21 feature modules
│   │   │   ├── hooks/          # 35+ custom hooks
│   │   │   ├── pages/          # Page components
│   │   │   ├── services/       # API clients
│   │   │   ├── state/          # Jotai atoms
│   │   │   └── shared/         # Shared types
│   │   └── web-page-specs/     # 17 page specifications
│   ├── backend/                 # Node.js/Fastify backend
│   │   ├── prisma/             # PostgreSQL schema
│   │   └── src/                # Services, routes, WebSocket
│   └── desktop/                 # Tauri desktop app
├── libs/java/                   # Java domain services
│   ├── domain-models/          # Persona, hierarchy, DevSecOps
│   ├── framework/              # AI Decision Engine, flows
│   ├── departments/            # 10 department implementations
│   └── software-org/           # Core software-org logic
├── contracts/                   # API contracts
├── config/                      # YAML configurations
└── docs/                        # Product documentation
```

### 1.2 Technology Stack

| Layer          | Technology                                        | Status            |
| -------------- | ------------------------------------------------- | ----------------- |
| Frontend       | React 18+, React Router v7, Jotai, TanStack Query | ✅ Complete       |
| UI Components  | @ghatana/ui, @ghatana/theme, Tailwind CSS         | ✅ Using platform |
| Backend (Node) | Fastify, Prisma, PostgreSQL                       | ✅ 90% Complete   |
| Backend (Java) | ActiveJ, Virtual-Org framework                    | ✅ 80% Complete   |
| Desktop        | Tauri (Rust + WebView)                            | ✅ Skeleton       |
| Real-time      | WebSocket                                         | ⚠️ Partial        |
| State          | Jotai (atoms), TanStack Query (server)            | ✅ Complete       |

### 1.3 Completion Status

| Component             | Status           | Coverage                     |
| --------------------- | ---------------- | ---------------------------- |
| **UI Layer**          | ✅ 100% Complete | 4 persona layouts, 25+ pages |
| **Backend (Node.js)** | ✅ 90% Complete  | Persona, Workspace services  |
| **Java Domain**       | ✅ 80% Complete  | Roles, Agents, Workflows     |
| **Config System**     | ✅ 100% Complete | 10 departments, 11 agents    |
| **API Layer**         | ⚠️ 70% Complete  | Mock fallbacks active        |

---

## Part 2: Feature & Capability Inventory

### 2.1 Implemented Features ✅

| Feature                     | Location                        | Description                                      |
| --------------------------- | ------------------------------- | ------------------------------------------------ |
| **Persona System**          | `state/atoms/persona.atoms.ts`  | 5 personas: Owner, Executive, Manager, IC, Admin |
| **Persona Guards**          | `app/guards/PersonaGuard.tsx`   | Route protection by persona/permissions          |
| **Persona Layouts**         | `components/layouts/*.tsx`      | 5 distinct layouts per persona                   |
| **Control Tower Dashboard** | `features/dashboard/`           | KPIs, timeline, AI insights                      |
| **Departments Directory**   | `features/departments/`         | Department list with automation levels           |
| **Department Detail**       | `features/departments/`         | KPIs, agents, workflows tabs                     |
| **Workflow Explorer**       | `features/workflows/`           | Pipeline list, health, run actions               |
| **HITL Console**            | `features/hitl/`                | Human-in-the-loop action queue                   |
| **Event Simulator**         | `features/simulator/`           | Event template testing                           |
| **Reporting Dashboard**     | `features/reporting/`           | Cross-department reports                         |
| **Security Dashboard**      | `features/security/`            | Security posture, compliance                     |
| **AI Intelligence**         | `features/ai/`                  | AI recommendations, model insights               |
| **Model Catalog**           | `features/models/`              | ML model registry                                |
| **Settings**                | `features/settings/`            | Preferences, configuration                       |
| **Help Center**             | `features/help/`                | Documentation, support                           |
| **Data Export**             | `features/export/`              | Export utility                                   |
| **ML Observatory**          | `features/ml-observatory/`      | ML monitoring                                    |
| **Real-Time Monitor**       | `features/monitoring/`          | Live metrics, alerts                             |
| **Automation Engine**       | `features/automation-engine/`   | Automation playbooks                             |
| **Org Tree View**           | `pages/org/OrgOverviewPage.tsx` | Interactive org hierarchy                        |
| **Restructure Workspace**   | `pages/org/RestructurePage.tsx` | Hierarchy modifications                          |
| **Audit Trail**             | `pages/org/AuditPage.tsx`       | Change history, diff viewer                      |
| **Approvals System**        | `pages/approvals/`              | Multi-step approval workflow                     |
| **IC Work Items**           | `pages/ic/ICWorkItemsPage.tsx`  | Kanban board for tasks                           |

### 2.2 Pages & Routes

#### Primary Routes (Sidebar Navigation)

| Route              | Page                    | Persona Access |
| ------------------ | ----------------------- | -------------- |
| `/`                | Control Tower Dashboard | All            |
| `/departments`     | Departments Directory   | All            |
| `/departments/:id` | Department Detail       | All            |
| `/workflows`       | Workflow Explorer       | All            |
| `/hitl`            | HITL Console            | All            |
| `/simulator`       | Event Simulator         | All            |
| `/reports`         | Reporting Dashboard     | All            |
| `/ai`              | AI Intelligence         | All            |
| `/security`        | Security Dashboard      | All            |

#### Secondary Routes (Contextual)

| Route                | Page              | Persona Access |
| -------------------- | ----------------- | -------------- |
| `/ml-observatory`    | ML Observatory    | All            |
| `/realtime-monitor`  | Real-Time Monitor | All            |
| `/automation-engine` | Automation Engine | All            |
| `/models`            | Model Catalog     | All            |
| `/settings`          | Settings          | All            |
| `/help`              | Help Center       | All            |
| `/export`            | Data Export       | All            |

#### Persona-Specific Routes

| Persona       | Base Path    | Key Routes                                                      |
| ------------- | ------------ | --------------------------------------------------------------- |
| **Owner**     | `/owner`     | `/org/overview`, `/org/restructure`, `/org/audit`, `/approvals` |
| **Executive** | `/executive` | `/org/overview`, `/departments`, `/approvals`                   |
| **Manager**   | `/manager`   | `/team/:teamId`, `/department/:departmentId`, `/approvals`      |
| **IC**        | `/ic`        | `/tasks`, `/work-items/:id`, `/growth`, `/time-off`             |
| **Admin**     | `/admin`     | `/org/overview`, `/org/personas`, `/org/audit`                  |

### 2.3 Persona Hierarchy Model

```
┌─────────────────────────────────────────────────────────────────┐
│                        ORGANIZATION                              │
│                           (Root)                                  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                    OWNER (Executive Layer)                       │
│  • CEO, Board Members, Founders                                  │
│  • Permissions: ALL                                              │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                  EXECUTIVE (C-Suite Layer)                       │
│  • CTO, CPO, CFO, COO, CISO                                     │
│  • Permissions: department:*, budget:approve                     │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                   MANAGER (Management Layer)                     │
│  • Directors, Team Leads                                         │
│  • Permissions: team:*, approve:restructure                      │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                    ADMIN (Operations Layer)                      │
│  • System Admins, DevOps Leads                                  │
│  • Permissions: system:*, audit:view                             │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│              INDIVIDUAL CONTRIBUTOR (IC Layer)                   │
│  • Engineers, QA, Support, Sales                                │
│  • Permissions: task:own, view:team                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Part 3: Virtual-Org Framework Dependencies

### 3.1 Virtual-Org as Foundation

**Virtual-Org** is the **canonical event-driven simulation framework** for modeling organizations. Software-Org is built **on top of** Virtual-Org, using it as a framework layer.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LAYERED ARCHITECTURE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LAYER 4: Custom Extensions (Future)                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • Custom departments for specific domains                          │   │
│  │  • Custom agents                                                    │   │
│  │  • Custom integrations                                              │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ extends                                 │
│                                   ▼                                         │
│  LAYER 3: Software-Org (Domain Plugin)                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • 10 Departments (Engineering, QA, DevOps, etc.)                   │   │
│  │  • 11 Agents (Engineer, QA, DevOps, etc.)                           │   │
│  │  • AI Decision Engine                                               │   │
│  │  • Security Gates                                                   │   │
│  │  • Cross-Department Flows                                           │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ extends                                 │
│                                   ▼                                         │
│  LAYER 2: Virtual-Org Framework (IMMUTABLE)                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • AbstractOrganization                                             │   │
│  │  • Department (base class)                                          │   │
│  │  • BaseOrganizationalAgent                                          │   │
│  │  • WorkflowEngine                                                   │   │
│  │  • EventPublisher                                                   │   │
│  │  • TaskDefinition / TaskInstance                                    │   │
│  │  • IAgent contracts                                                 │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ depends on                              │
│                                   ▼                                         │
│  LAYER 1: Platform Services (libs/java/*)                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AEP │ Observability │ Auth │ State │ AI-Integration                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Virtual-Org Dependencies Catalog

| Component                   | Virtual-Org Module     | Purpose                 | Software-Org Usage                 |
| --------------------------- | ---------------------- | ----------------------- | ---------------------------------- |
| **AbstractOrganization**    | `libs/java/framework`  | Base organization class | Extended by `SoftwareOrganization` |
| **Department**              | `libs/java/framework`  | Department abstraction  | 10 department implementations      |
| **BaseOrganizationalAgent** | `libs/java/framework`  | Agent base class        | 11 agent implementations           |
| **WorkflowEngine**          | `libs/workflow`        | Workflow orchestration  | Cross-department flows             |
| **EventPublisher**          | `libs/java/org-events` | Event emission          | Org event publishing               |
| **TaskDefinition**          | `libs/java/framework`  | Task contracts          | Work item definitions              |
| **OrganizationEvent**       | `libs/java/org-events` | Event model             | All org events                     |
| **DepartmentKpiTracker**    | `libs/java/framework`  | KPI abstractions        | Department metrics                 |
| **AepEventBusConnector**    | `libs/integration`     | AEP integration         | Event bus connection               |
| **LLMAgentProvider**        | `libs/java/framework`  | AI agent provider       | AI-driven agents                   |
| **StateStoreAdapter**       | `libs/java/framework`  | State management        | Org state storage                  |

### 3.3 Virtual-Org Extension Points Used

| Extension Point            | Type            | Software-Org Implementation                   |
| -------------------------- | --------------- | --------------------------------------------- |
| `Department`               | Class Extension | `EngineeringDepartment`, `QADepartment`, etc. |
| `BaseOrganizationalAgent`  | Class Extension | `EngineerAgent`, `QAAgent`, etc.              |
| `CrossDepartmentEventFlow` | Interface       | 12 cross-department flows                     |
| `SecurityGate`             | Plugin          | Security policy enforcement                   |
| `AIDecisionEngine`         | Plugin          | AI-driven decision making                     |
| `WorkflowDefinition`       | Configuration   | YAML-defined workflows                        |

### 3.4 Virtual-Org Configuration Schema

Software-Org uses Virtual-Org's YAML-based configuration:

```yaml
# products/software-org/config/organization.yaml
organization:
  id: software-org
  name: "Software Organization"
  type: software-company

departments:
  - id: engineering
    name: "Engineering"
    type: development
    agents:
      - id: senior-engineer
        role: senior-engineer
        capabilities: [code-review, architecture, mentoring]
      - id: engineer
        role: engineer
        capabilities: [development, testing, debugging]

workflows:
  - id: code-review-flow
    trigger: pull_request.created
    steps:
      - department: engineering
        action: review-code
      - department: qa
        action: run-tests
```

---

## Part 4: Reusable Library Analysis (Reuse-First Policy)

### 4.1 Libraries Used ✅

| Library                    | Purpose         | Usage in Software-Org    |
| -------------------------- | --------------- | ------------------------ |
| `@ghatana/ui`              | UI components   | All pages, components    |
| `@ghatana/theme`           | Theming         | App.tsx ThemeProvider    |
| `@ghatana/charts`          | Charts          | Dashboard KPIs (partial) |
| `libs/java/auth`           | Authentication  | Persona validation       |
| `libs/java/observability`  | Metrics/tracing | Service monitoring       |
| `libs/java/ai-integration` | AI/LLM          | Agent AI capabilities    |
| `libs/java/config-runtime` | Configuration   | YAML config loading      |

### 4.2 Libraries NOT Used (Reuse Opportunities)

| Library                 | Purpose               | Should Use For               |
| ----------------------- | --------------------- | ---------------------------- |
| `@ghatana/api`          | HTTP client           | Replace custom API client    |
| `@ghatana/realtime`     | WebSocket/SSE         | Real-time updates            |
| `@ghatana/state`        | State helpers         | Enhance Jotai usage          |
| `@ghatana/test-utils`   | Testing utilities     | Unit/integration tests       |
| `libs/java/database`    | Database abstractions | If migrating to Java backend |
| `libs/java/http-server` | HTTP server           | Java API endpoints           |

### 4.3 Reuse Violations & Fixes

| Current Implementation               | Should Use                           | Priority |
| ------------------------------------ | ------------------------------------ | -------- |
| Custom API client in `services/api/` | `@ghatana/api`                       | High     |
| Custom WebSocket in `services/`      | `@ghatana/realtime`                  | Medium   |
| Local mock data fallback             | Centralized mock service             | Medium   |
| Custom error handling                | `@ghatana/ui` ErrorBoundary patterns | Low      |

---

## Part 5: Data Model Analysis

### 5.1 Backend Schema (Prisma/PostgreSQL)

| Entity              | Purpose             | Relationships                           |
| ------------------- | ------------------- | --------------------------------------- |
| `User`              | User accounts       | Has many PersonaPreferences, Workspaces |
| `Workspace`         | Org workspace       | Belongs to User (owner)                 |
| `PersonaPreference` | User's active roles | Belongs to User, Workspace              |
| `WorkspaceOverride` | Admin overrides     | Belongs to Workspace                    |

### 5.2 Frontend State (Jotai Atoms)

| Atom                     | Purpose              | Persisted    |
| ------------------------ | -------------------- | ------------ |
| `currentPersonaAtom`     | Active persona       | localStorage |
| `personaTypeAtom`        | Derived persona type | No           |
| `personaPermissionsAtom` | Derived permissions  | No           |
| `selectedTenantAtom`     | Active tenant filter | localStorage |
| `timeRangeAtom`          | Dashboard time range | localStorage |
| `compareEnabledAtom`     | Comparison mode      | No           |

### 5.3 Missing Data Models

| Entity        | Purpose               | Priority |
| ------------- | --------------------- | -------- |
| `AuditLog`    | Change history        | High     |
| `Approval`    | Approval workflow     | High     |
| `WorkItem`    | IC tasks              | High     |
| `Department`  | Department metadata   | High     |
| `Agent`       | Agent definitions     | Medium   |
| `Workflow`    | Workflow definitions  | Medium   |
| `Integration` | External integrations | Low      |

---

## Part 6: API Routes Analysis

### 6.1 Node.js Backend Routes

| Method | Route                           | Purpose               | Status |
| ------ | ------------------------------- | --------------------- | ------ |
| GET    | `/api/personas/:workspaceId`    | Fetch user persona    | ✅     |
| PUT    | `/api/personas/:workspaceId`    | Update persona        | ✅     |
| DELETE | `/api/personas/:workspaceId`    | Reset persona         | ✅     |
| GET    | `/api/workspaces/:id/overrides` | Fetch admin overrides | ✅     |
| POST   | `/api/workspaces/:id/overrides` | Create overrides      | ✅     |
| DELETE | `/api/workspaces/:id/overrides` | Remove overrides      | ✅     |

### 6.2 Java Backend Routes

| Method | Route                                        | Purpose               | Status |
| ------ | -------------------------------------------- | --------------------- | ------ |
| GET    | `/api/v1/personas/roles`                     | List role definitions | ✅     |
| GET    | `/api/v1/personas/roles/:id`                 | Get role definition   | ✅     |
| POST   | `/api/v1/personas/roles/validate`            | Validate role combo   | ✅     |
| POST   | `/api/v1/personas/roles/resolve-permissions` | Resolve permissions   | ✅     |

### 6.3 Missing API Routes

| Method | Route                              | Purpose                | Priority |
| ------ | ---------------------------------- | ---------------------- | -------- |
| GET    | `/api/v1/org/config`               | Organization config    | High     |
| GET    | `/api/v1/org/graph`                | Org hierarchy graph    | High     |
| POST   | `/api/v1/org/hierarchy/move`       | Move node in hierarchy | High     |
| GET    | `/api/v1/departments`              | List departments       | High     |
| GET    | `/api/v1/departments/:id`          | Department detail      | High     |
| GET    | `/api/v1/departments/:id/kpis`     | Department KPIs        | High     |
| GET    | `/api/v1/workflows`                | List workflows         | Medium   |
| POST   | `/api/v1/workflows/:id/run`        | Trigger workflow       | Medium   |
| GET    | `/api/v1/hitl/queue`               | HITL action queue      | High     |
| POST   | `/api/v1/hitl/actions/:id/approve` | Approve action         | High     |
| GET    | `/api/v1/audit`                    | Audit log              | Medium   |
| GET    | `/api/v1/approvals`                | Approval queue         | Medium   |

---

## Part 7: Enhancement Plan

### Phase 1: Reuse-First Compliance (Sprint 1-2)

**Goal:** Migrate to platform libraries and establish missing foundations.

#### 1.1 API Client Migration

**Priority:** High  
**Effort:** Medium

```typescript
// REPLACE: services/api/*.ts
// WITH: @ghatana/api integration

import { createApiClient } from "@ghatana/api";

export const softwareOrgApi = createApiClient({
  baseURL: "/api",
  middleware: ["auth", "tenant", "correlation", "retry"],
});
```

#### 1.2 Real-time Migration

**Priority:** Medium  
**Effort:** Medium

```typescript
// REPLACE: services/websocket.ts
// WITH: @ghatana/realtime

import { createRealtimeClient } from "@ghatana/realtime";

export const realtimeClient = createRealtimeClient({
  url: "wss://api.ghatana.com/realtime",
});
```

#### 1.3 Complete Virtual-Org Integration

**Priority:** High  
**Effort:** High

- Wire all department implementations to Virtual-Org base classes
- Connect event publisher to AEP event bus
- Implement StateStoreAdapter for org state

### Phase 2: Backend Completion (Sprint 3-5)

**Goal:** Replace mock data with real backend services.

#### 2.1 Organization API

```java
// Products: software-org/libs/java/software-org
@Path("/api/v1/org")
public class OrganizationController {
    @GET @Path("/config")
    public Promise<OrgConfig> getConfig();

    @GET @Path("/graph")
    public Promise<OrgGraph> getGraph();

    @POST @Path("/hierarchy/move")
    public Promise<MoveResult> moveNode(MoveRequest request);
}
```

#### 2.2 Department API

```java
@Path("/api/v1/departments")
public class DepartmentController {
    @GET
    public Promise<List<DepartmentSummary>> list();

    @GET @Path("/{id}")
    public Promise<DepartmentDetail> getById(@PathParam("id") String id);

    @GET @Path("/{id}/kpis")
    public Promise<DepartmentKpis> getKpis(@PathParam("id") String id);
}
```

#### 2.3 HITL API

```java
@Path("/api/v1/hitl")
public class HitlController {
    @GET @Path("/queue")
    public Promise<List<HitlAction>> getQueue();

    @POST @Path("/actions/{id}/approve")
    public Promise<HitlResult> approve(@PathParam("id") String id);

    @POST @Path("/actions/{id}/reject")
    public Promise<HitlResult> reject(@PathParam("id") String id);
}
```

### Phase 3: Feature Completion (Sprint 6-8)

**Goal:** Complete unimplemented page features.

#### 3.1 Department Detail Tabs

| Tab       | Status         | Implementation              |
| --------- | -------------- | --------------------------- |
| Overview  | ✅ Complete    | KPI grid, automation toggle |
| Agents    | ⚠️ Placeholder | Agent list, status, actions |
| Workflows | ⚠️ Placeholder | Workflow list, run history  |
| Playbooks | ⚠️ Placeholder | Playbook editor, versioning |

#### 3.2 Workflow Visualization

- Add visual flow diagram using `@ghatana/charts`
- Show step-by-step execution
- Link to Automation Engine

#### 3.3 HITL Decision History

- Add decision audit log
- Show approver, timestamp, reason
- Link to downstream effects

### Phase 4: Virtual-Org Deep Integration (Sprint 9-12)

**Goal:** Full alignment with Virtual-Org framework patterns.

#### 4.1 Event-Driven Architecture

- Emit all org changes as `OrganizationEvent`
- Subscribe to AEP for external events
- Implement event replay for audit

#### 4.2 Configuration Hot-Reload

- Implement YAML config watcher
- Auto-reload org structure changes
- Validate config against schema

#### 4.3 Cross-Department Flows

| Flow                     | From        | To          | Status         |
| ------------------------ | ----------- | ----------- | -------------- |
| EngineeringToQaFlow      | Engineering | QA          | ✅ Implemented |
| QaToDevopsFlow           | QA          | DevOps      | ✅ Implemented |
| DevopsToSupportFlow      | DevOps      | Support     | ✅ Implemented |
| SupportToEngineeringFlow | Support     | Engineering | ✅ Implemented |
| FinanceToProductFlow     | Finance     | Product     | ✅ Implemented |
| HrToEngineeringFlow      | HR          | Engineering | ✅ Implemented |
| ComplianceToDevopsFlow   | Compliance  | DevOps      | ✅ Implemented |
| SalesToFinanceFlow       | Sales       | Finance     | ✅ Implemented |
| MarketingToSalesFlow     | Marketing   | Sales       | ✅ Implemented |
| DevopsMonitoringFlow     | DevOps      | Monitor     | ✅ Implemented |

---

## Part 8: Component Mapping to @ghatana/ui

### 8.1 Components Currently Using @ghatana/ui

| Component   | @ghatana/ui Usage                            |
| ----------- | -------------------------------------------- |
| All layouts | `DashboardLayout`, `AppSidebar`, `AppHeader` |
| Pages       | `Card`, `Button`, `Badge`, `Spinner`         |
| Forms       | `Form`, `FormField`, `TextField`, `Select`   |
| Tables      | `DataGrid`, `Table`                          |
| Modals      | `Modal`, `ConfirmDialog`                     |
| Navigation  | `Tabs`, `Breadcrumb`, `NavLink`              |

### 8.2 Components to Add

| Need            | @ghatana/ui Component | Location               |
| --------------- | --------------------- | ---------------------- |
| Timeline chart  | `Timeline`            | Dashboard, Audit       |
| KPI display     | `KpiCard`             | Dashboard, Departments |
| Org hierarchy   | `TreeView`            | Org Overview           |
| Action queue    | `InteractiveList`     | HITL Console           |
| Stepper         | `Stepper`             | Approvals workflow     |
| Command palette | `CommandPalette`      | Global search          |

---

## Part 9: Testing Strategy

### 9.1 Current Test Coverage

| Area       | Files                   | Coverage |
| ---------- | ----------------------- | -------- |
| Hooks      | `hooks/__tests__/`      | Moderate |
| Components | `components/__tests__/` | Moderate |
| Services   | `services/__tests__/`   | Basic    |
| E2E        | `e2e/`                  | Basic    |

### 9.2 Required Test Additions

| Test Type   | Target          | Using                         |
| ----------- | --------------- | ----------------------------- |
| Unit        | All services    | `@ghatana/test-utils`, Vitest |
| Integration | API routes      | Fastify inject                |
| Component   | All pages       | React Testing Library         |
| E2E         | Critical flows  | Playwright                    |
| Java Unit   | Domain services | `EventloopTestBase`           |

---

## Part 10: Virtual-Org Dependency Document

### 10.1 Overview

Software-Org is a **reference implementation** of the Virtual-Org framework, demonstrating how to build organization management systems.

### 10.2 Core Dependencies

| Dependency                   | Module Path                                  | Version | Purpose               |
| ---------------------------- | -------------------------------------------- | ------- | --------------------- |
| virtual-org-framework        | `products/virtual-org/libs/java/framework`   | 1.0.0   | Base classes          |
| virtual-org-workflow         | `products/virtual-org/libs/workflow`         | 1.0.0   | Workflow engine       |
| virtual-org-operator-adapter | `products/virtual-org/libs/operator-adapter` | 1.0.0   | AEP operators         |
| virtual-org-integration      | `products/virtual-org/libs/integration`      | 1.0.0   | External integrations |

### 10.3 Contract Alignment

| Virtual-Org Contract | Software-Org Implementation       |
| -------------------- | --------------------------------- |
| `IAgent`             | All 11 agent implementations      |
| `Department`         | All 10 department implementations |
| `WorkflowDefinition` | YAML-defined workflows            |
| `OrganizationEvent`  | All org events                    |
| `TaskDefinition`     | Work items, approvals             |

### 10.4 Extension Points

| Extension Point         | Software-Org Usage        |
| ----------------------- | ------------------------- |
| Department Registration | 10 departments registered |
| Agent Registration      | 11 agents registered      |
| Flow Registration       | 12 cross-department flows |
| Security Gate           | Policy enforcement        |
| AI Decision Engine      | AI-driven decisions       |

### 10.5 Configuration Schema

Software-Org adheres to Virtual-Org's configuration schema:

```json
{
  "$schema": "virtual-org-schema.json",
  "organization": {
    "id": "string",
    "name": "string",
    "type": "software-company"
  },
  "departments": [...],
  "agents": [...],
  "workflows": [...]
}
```

---

## Part 11: Implementation Priority Matrix

### Critical (Sprint 1-2)

1. ✅ Migrate to `@ghatana/api` client
2. ✅ Complete Virtual-Org framework integration
3. ✅ Implement missing backend APIs
4. ✅ Wire HITL to real backend

### High (Sprint 3-5)

1. Complete Department detail tabs
2. Add workflow visualization
3. Implement audit log API
4. Real-time updates with `@ghatana/realtime`

### Medium (Sprint 6-8)

1. Approvals workflow completion
2. Event Simulator backend
3. Cross-department flow UI
4. Desktop app completion

### Low (Sprint 9-12)

1. ML Observatory integration
2. Advanced reporting
3. Mobile support
4. Offline capabilities

---

## Part 12: Technical Debt & Cleanup

### 12.1 Identified Debt

| Issue                         | Location                 | Fix                 |
| ----------------------------- | ------------------------ | ------------------- |
| Mock data fallback everywhere | `hooks/useVirtualOrg.ts` | Wire to real APIs   |
| Custom API client             | `services/api/`          | Use `@ghatana/api`  |
| Placeholder tabs              | Department detail        | Implement content   |
| Console.log actions           | HITL, Dashboard          | Wire to backend     |
| No error boundaries on pages  | All pages                | Add `ErrorBoundary` |

### 12.2 Code Quality Improvements

1. Enable strict TypeScript null checks
2. Add ESLint strict mode
3. Implement conventional commits
4. Add pre-commit hooks
5. Increase test coverage to 80%

---

## Conclusion

Software-Org is a **comprehensive organization management system** built on Virtual-Org framework. Key findings:

1. **Strong UI Layer** - 100% complete with 4 personas, 25+ pages
2. **Backend Gap** - 70% API completion with mock fallbacks active
3. **Virtual-Org Integration** - 80% complete, needs event bus wiring
4. **Reuse Violations** - Custom API client, WebSocket should use platform libs

### Next Steps

1. **Immediate:** Migrate to `@ghatana/api` and `@ghatana/realtime`
2. **Short-term:** Complete backend APIs, remove mock fallbacks
3. **Medium-term:** Deep Virtual-Org integration, event-driven architecture
4. **Long-term:** Desktop app, mobile support, advanced features

---

## Appendix: Quick Reference

### Key Files

- App Entry: `apps/web/src/App.tsx`
- Routes: `apps/web/src/app/routes.ts`
- Persona State: `apps/web/src/state/atoms/persona.atoms.ts`
- API Services: `apps/web/src/services/api/`
- Hooks: `apps/web/src/hooks/`
- Backend: `apps/backend/src/server.ts`
- Java Domain: `libs/java/domain-models/`

### Shared Libraries to Use

- `@ghatana/ui` - UI components ✅
- `@ghatana/theme` - Theming ✅
- `@ghatana/api` - HTTP client ❌ (should use)
- `@ghatana/realtime` - WebSocket ❌ (should use)
- `@ghatana/charts` - Charts ⚠️ (partial)
- `@ghatana/test-utils` - Testing ❌ (should use)

### Virtual-Org Dependencies

- `products/virtual-org/libs/java/framework` - Core framework
- `products/virtual-org/libs/workflow` - Workflow engine
- `products/virtual-org/libs/integration` - Integrations
- `products/virtual-org/libs/operator-adapter` - AEP operators

### Commands

```bash
# Web App
pnpm --filter @software-org/web dev
pnpm --filter @software-org/web build
pnpm --filter @software-org/web test

# Backend
pnpm --filter @software-org/backend dev
pnpm --filter @software-org/backend prisma:generate

# Java
./gradlew :products:software-org:libs:java:domain-models:build
./gradlew :products:software-org:libs:java:framework:build
```
