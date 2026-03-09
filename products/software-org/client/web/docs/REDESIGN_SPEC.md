# Software-Org UI Redesign Specification

## Problem Statement

The current UI has several issues:
1. **Redundant navigation**: Departments appear in sidebar AND config page
2. **Unclear user flow**: No clear path from problem → action → outcome
3. **Too many top-level pages**: 20+ routes without clear hierarchy
4. **Persona confusion**: Multiple persona layouts that don't connect to main flow
5. **Configuration vs Operations blur**: Config entities mixed with operational views

## Design Principles

1. **Task-Oriented**: Organize by what users DO, not what data EXISTS
2. **Progressive Disclosure**: Show complexity only when needed
3. **Single Source of Truth**: Each entity accessible from ONE primary location
4. **Clear Hierarchy**: 3-level max: Section → Page → Detail

## Redesigned Information Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AI-First DevSecOps                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│
│  │   OPERATE   │  │    BUILD    │  │   OBSERVE   │  │   ADMIN     ││
│  │  (Day-to-   │  │  (Design &  │  │  (Monitor & │  │  (Configure ││
│  │   Day)      │  │   Create)   │  │   Analyze)  │  │   & Manage) ││
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘│
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1. OPERATE (Day-to-Day Operations)
**Purpose**: Handle daily operational tasks and incidents

| Page | Description | Key Actions |
|------|-------------|-------------|
| **Dashboard** | Real-time health overview | View alerts, quick actions |
| **Work Queue** | Pending tasks & approvals | Approve, reject, escalate |
| **Incidents** | Active incidents & response | Triage, assign, resolve |

### 2. BUILD (Design & Create)
**Purpose**: Design and configure automation

| Page | Description | Key Actions |
|------|-------------|-------------|
| **Workflows** | Automation workflows | Create, edit, test workflows |
| **Agents** | AI agents configuration | Configure, train, deploy agents |
| **Simulator** | Test events & scenarios | Simulate, validate, debug |

### 3. OBSERVE (Monitor & Analyze)
**Purpose**: Monitor performance and analyze trends

| Page | Description | Key Actions |
|------|-------------|-------------|
| **Metrics** | KPIs & performance data | View trends, set targets |
| **Reports** | Generated reports | Generate, schedule, export |
| **ML Observatory** | Model performance | Monitor drift, retrain |

### 4. ADMIN (Configure & Manage)
**Purpose**: System configuration and organization management

| Page | Description | Key Actions |
|------|-------------|-------------|
| **Organization** | Departments, teams, personas | Structure org, assign roles |
| **Security** | Access control & audit | Manage permissions, review logs |
| **Settings** | System preferences | Configure integrations |

## Navigation Structure

```
┌──────────────────────────────────────────────────────────────────┐
│ [Logo] AI-First DevSecOps    [Search]  [Env] [Theme] [User]     │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  OPERATE                    │                                   │
│  ├─ Dashboard               │                                   │
│  ├─ Work Queue              │         MAIN CONTENT AREA         │
│  └─ Incidents               │                                   │
│                             │                                   │
│  BUILD                      │                                   │
│  ├─ Workflows               │                                   │
│  ├─ Agents                  │                                   │
│  └─ Simulator               │                                   │
│                             │                                   │
│  OBSERVE                    │                                   │
│  ├─ Metrics                 │                                   │
│  ├─ Reports                 │                                   │
│  └─ ML Observatory          │                                   │
│                             │                                   │
│  ADMIN                      │                                   │
│  ├─ Organization            │                                   │
│  ├─ Security                │                                   │
│  └─ Settings                │                                   │
│                             │                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Route Mapping

| Old Route | New Route | Notes |
|-----------|-----------|-------|
| `/` | `/` | Dashboard (OPERATE) |
| `/dashboard` | `/` | Merged into main dashboard |
| `/config/*` | REMOVED | Entities moved to appropriate sections |
| `/departments` | `/admin/organization` | Tab within Organization |
| `/personas` | `/admin/organization` | Tab within Organization |
| `/workflows` | `/build/workflows` | Primary location |
| `/agents` | `/build/agents` | Primary location |
| `/kpis` | `/observe/metrics` | Tab within Metrics |
| `/hitl` | `/operate/queue` | Renamed to Work Queue |
| `/reports` | `/observe/reports` | Moved to OBSERVE |
| `/security` | `/admin/security` | Moved to ADMIN |
| `/simulator` | `/build/simulator` | Moved to BUILD |
| `/realtime-monitor` | `/` | Integrated into Dashboard |
| `/models` | `/observe/ml` | Merged with ML Observatory |
| `/ml-observatory` | `/observe/ml` | Primary ML page |

## Page Designs

### Dashboard (OPERATE)
```
┌─────────────────────────────────────────────────────────────────┐
│ Dashboard                                           [Refresh]   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐│
│  │ Active      │ │ Pending     │ │ Workflows   │ │ System     ││
│  │ Incidents   │ │ Approvals   │ │ Running     │ │ Health     ││
│  │    3        │ │    12       │ │    47       │ │   98%      ││
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────────┘│
│                                                                 │
│  ┌─────────────────────────────┐ ┌─────────────────────────────┐│
│  │ Recent Activity             │ │ Quick Actions               ││
│  │ ─────────────────────────── │ │ ─────────────────────────── ││
│  │ • Workflow "CI/CD" completed│ │ [+ New Workflow]            ││
│  │ • Alert: High CPU on prod-1 │ │ [Review Approvals]          ││
│  │ • Agent "Triage" updated    │ │ [View Incidents]            ││
│  └─────────────────────────────┘ └─────────────────────────────┘│
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Key Metrics                                    [View All →] ││
│  │ ─────────────────────────────────────────────────────────── ││
│  │ Deployment Freq: 12/day  │ MTTR: 45min  │ Change Fail: 2%  ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Organization (ADMIN)
```
┌─────────────────────────────────────────────────────────────────┐
│ Organization                                    [+ Add Team]    │
├─────────────────────────────────────────────────────────────────┤
│ [Departments] [Teams] [Personas] [Roles]                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Engineering                                    [Edit] [→]   ││
│  │ Core software development • 24 members • 3 teams            ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ DevOps                                         [Edit] [→]   ││
│  │ Infrastructure & deployment • 8 members • 2 teams           ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ Security                                       [Edit] [→]   ││
│  │ Application security • 5 members • 1 team                   ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ Product                                        [Edit] [→]   ││
│  │ Product management • 6 members • 2 teams                    ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Plan

### Phase 1: Restructure Routes (This Session)
1. Update `routes.ts` with new route structure
2. Create section layout components (OperateLayout, BuildLayout, etc.)
3. Update sidebar navigation

### Phase 2: Consolidate Pages
1. Merge redundant pages (config → organization)
2. Create tabbed interfaces where appropriate
3. Remove deprecated routes

### Phase 3: Polish & Consistency
1. Apply consistent styling from theme.ts
2. Add breadcrumbs for navigation context
3. Implement quick actions and cross-links

## Success Criteria

1. **No redundant navigation**: Each entity accessible from ONE location
2. **Clear user flow**: Users can complete tasks without confusion
3. **Consistent styling**: All pages use theme.ts tokens
4. **Reduced cognitive load**: Max 4 top-level sections, 3-4 items each
