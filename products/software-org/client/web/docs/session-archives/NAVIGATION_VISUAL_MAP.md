# Navigation Architecture Diagram

## Visual Site Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          HEADER (Persistent)                                │
│  Logo  |  Theme  |  Tenant  |  Environment  |  ⏱️ Monitor  |  ❓  |  ⋯ More │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐  ┌──────────────────────────────────────────────────────┐
│    SIDEBAR       │  │               MAIN CONTENT AREA                       │
│   (Collapsible)  │  │                                                        │
│                  │  │  ┌────────────────────────────────────────────────┐   │
│ MAIN             │  │  │  Page Header (H1 + Subtitle + CTAs)            │   │
│ ───────────────  │  │  └────────────────────────────────────────────────┘   │
│  📊 Dashboard    │  │                                                        │
│  🏢 Departments  │  │  ┌────────────────────────────────────────────────┐   │
│  🔄 Workflows    │  │  │  Page-Specific Content                         │   │
│  ✋ HITL Console │  │  │  (Components, Forms, Tables, etc.)             │   │
│  ⚡ Simulator    │  │  │                                                │   │
│  📈 Reports      │  │  │  Contextual Navigation CTAs:                   │   │
│  🤖 AI           │  │  │  - Link to related pages                      │   │
│  🔒 Security     │  │  │  - "Next step" actions                         │   │
│                  │  │  └────────────────────────────────────────────────┘   │
│ MORE             │  │                                                        │
│ ───────────────  │  │                                                        │
│ ⏱️ Real-Time    │  │                                                        │
│ ⚙️ Automation   │  │                                                        │
│ 📦 Models       │  │                                                        │
│ 🔬 ML Obs.      │  │                                                        │
│                  │  │                                                        │
│ → (collapse)     │  │                                                        │
└──────────────────┘  └──────────────────────────────────────────────────────┘
```

## Route Hierarchy

```
APP ROOT
│
├── Primary Routes (Always in sidebar)
│   ├── / Dashboard
│   │   └── Control Tower - Organization KPIs + insights
│   │
│   ├── /departments Departments Directory
│   │   ├── List all teams with automation status
│   │   └── → /departments/:id Department Detail
│   │       └── Deep dive into specific department
│   │
│   ├── /workflows Workflow Explorer
│   │   ├── List and manage automation workflows
│   │   └── → /automation-engine Automation Engine
│   │       └── Execute and monitor workflow runs
│   │
│   ├── /hitl HITL Console
│   │   └── Review and approve AI recommendations
│   │
│   ├── /simulator Event Simulator
│   │   └── Test events and pipeline behavior
│   │
│   ├── /reports Reporting Dashboard
│   │   └── → /export Data Export
│   │       └── Export data and reports
│   │
│   ├── /ai AI Intelligence
│   │   ├── AI insights and recommendations
│   │   ├── → /models Model Catalog
│   │   │   └── ML models and versions
│   │   └── → /ml-observatory ML Observatory
│   │       └── Model performance metrics
│   │
│   └── /security Security & Compliance
│       └── Access, audit, compliance status
│
├── Secondary Routes (Header "More" menu)
│   ├── /realtime-monitor Real-Time Monitor
│   │   └── Live metrics, anomalies, alerts
│   │
│   ├── /automation-engine Automation Engine
│   │   └── (Also accessible from Workflows)
│   │
│   ├── /models Model Catalog
│   │   └── (Also accessible from AI Intelligence)
│   │
│   ├── /ml-observatory ML Observatory
│   │   └── (Also accessible from AI Intelligence)
│   │
│   ├── /settings Settings & Preferences
│   │   └── User and organization configuration
│   │
│   ├── /help Help Center
│   │   └── Documentation and support
│   │
│   └── /export Data Export
│       └── (Also accessible from Reports)
│
└── Global Context (Header)
    ├── Tenant selector
    ├── Environment selector
    └── Theme toggle
```

## Navigation Flow - Key User Paths

### Path 1: Leadership Review
```
Dashboard
  ↓ (filter by tenant/time)
  ↓ (see KPIs + AI insights)
  → Departments
      ↓ (select specific department)
      → Department Detail
          ↓ (see deep metrics)
          → AI Intelligence
              ↓ (view recommendations)
              → HITL Console (approve action)
```

### Path 2: SRE During Incident
```
Real-Time Monitor (⏱️ in header)
  ↓ (see critical alerts)
  → Automation Engine (from alert CTA)
      ↓ (run remediation)
      → HITL Console (review bot actions)
      ↓ (approve)
  → Reports
      ↓ (check historical patterns)
```

### Path 3: ML/Data Engineer
```
AI Intelligence
  ↓ (view current models)
  → Model Catalog
      ↓ (evaluate model versions)
      → ML Observatory
          ↓ (monitor model drift)
  → Event Simulator
      ↓ (test new scenarios)
```

### Path 4: Compliance/Security Review
```
Security
  ↓ (audit access + incidents)
  → Reports
      ↓ (detailed trends)
      → Data Export
          ↓ (export for compliance)
```

## Component Relationships

```
Layout.tsx (Main Layout with sidebar + header)
│
├── HeaderContent
│   ├── Theme selector
│   ├── Tenant selector
│   ├── Environment selector
│   ├── Real-Time Monitor icon (⏱️)
│   ├── Help icon (❓)
│   └── More menu (⋯)
│       ├── Settings
│       ├── Data Export
│       └── Account
│
├── SidebarContent
│   ├── Primary Section
│   │   ├── Dashboard
│   │   ├── Departments
│   │   ├── Workflows
│   │   ├── HITL Console
│   │   ├── Event Simulator
│   │   ├── Reports
│   │   ├── AI Intelligence
│   │   └── Security
│   │
│   └── Secondary Section (Collapsible)
│       ├── Real-Time Monitor
│       ├── Automation Engine
│       ├── Model Catalog
│       └── ML Observatory
│
└── GlobalContextBanner
    └── Shows current tenant + environment
```

## Route Configuration

```
routes.config.ts
│
├── ROUTES object (all 16 routes)
│   ├── dashboard
│   ├── departments
│   ├── departmentDetail
│   ├── workflows
│   ├── hitl
│   ├── simulator
│   ├── reports
│   ├── ai
│   ├── security
│   ├── realtimeMonitor
│   ├── automationEngine
│   ├── modelCatalog
│   ├── mlObservatory
│   ├── settings
│   ├── help
│   └── dataExport
│
├── getPrimaryRoutes()
│   └── Returns 8 primary routes for sidebar
│
├── getSecondaryRoutes()
│   └── Returns 7 secondary routes for header menu
│
├── getDetailRoutes()
│   └── Returns dynamic routes (e.g., /departments/:id)
│
└── Utility functions
    ├── getRouteByPath(path)
    └── getRouteByLabel(label)
```

## Mobile Navigation

```
┌─────────────────────────────────┐
│  ← | Logo | ⏱️ ❓ ⋯              │  ← Sidebar toggle
└─────────────────────────────────┘

On sidebar toggle:
┌──────────────────┐
│ 📊 Dashboard     │
│ 🏢 Departments   │
│ 🔄 Workflows     │
│ ✋ HITL Console  │
│ ⚡ Simulator     │
│ 📈 Reports       │
│ 🤖 AI            │
│ 🔒 Security      │
│                  │
│ MORE             │
│ ⏱️ Real-Time    │
│ ⚙️ Automation   │
│ 📦 Models        │
│ 🔬 ML Obs.       │
│                  │
│ ← (close)        │
└──────────────────┘
```

## Adding a New Route

```
1. Update routes.config.ts
   ├── Add new route definition to ROUTES object
   ├── Set category: "primary" or "secondary"
   └── Provide all metadata (path, label, icon, etc.)

2. Create component
   └── src/features/my-feature/MyFeature.tsx

3. Update App.tsx
   └── Add <Route path="/my-feature" element={<MyFeature />} />

4. Navigation automatically updates!
   ├── If primary: appears in sidebar
   ├── If secondary: appears in header menu
   └── No manual layout changes needed
```

## Real-Time Monitor Access Pattern

The Real-Time Monitor is always 1-click away:

```
From ANY page:
├── Click ⏱️ in header
│   └── Go to Real-Time Monitor
│       └── Click alert
│           └── Jump to Automation Engine
│               └── Execute remediation
│
Or directly from sidebar:
├── Click "Real-Time Monitor" in "More"
│   └── Go to Real-Time Monitor
```

## Help Access Pattern

Help is always available:

```
From ANY page:
├── Click ❓ in header
│   └── Go to Help Center
│       └── Search or browse docs
│
Or from header menu:
├── Click ⋯ (More)
│   └── Click ❓ Help
│       └── Go to Help Center
```

## Tenant & Environment Context

Every action is scoped by tenant + environment:

```
┌────────────────────────────────┐
│ Tenant: Tenant A               │
│ Environment: Production         │
│ Time Range: Last 7 days        │
└────────────────────────────────┘
        ↓ (affects all pages)
┌────────────────────────────────┐
│ Dashboard (filtered)            │
│ Departments (filtered)          │
│ Reports (filtered)              │
│ Real-Time Monitor (filtered)    │
│ etc.                            │
└────────────────────────────────┘
```

---

## Summary

- ✅ **Primary Routes:** 8 stable navigation items in sidebar
- ✅ **Secondary Routes:** 7 contextual items in header "More" menu
- ✅ **Detail Routes:** Dynamic routes with parameters (e.g., `/departments/:id`)
- ✅ **Quick Access:** Real-Time Monitor and Help always 1 click away
- ✅ **Context Persistence:** Tenant, environment, theme consistent across all pages
- ✅ **Mobile Responsive:** Sidebar collapses, menu stays accessible
- ✅ **Maintainable:** Single source of truth in `routes.config.ts`
