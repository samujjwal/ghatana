# Navigation Alignment Review - Landing Page & Site Map

**Date:** November 22, 2025  
**Status:** Review Complete - Misalignment Identified

---

## Executive Summary

The current landing page (`App.tsx`) is **significantly misaligned** with the comprehensive web page specifications in `web-page-specs/`. 

**Current State:**
- ✅ **4 routes implemented** (Dashboard, Backlog, Sprint Planning, Release)
- ❌ **13 routes missing** (Departments, Workflows, HITL, Simulator, Reports, AI, Security, ML Observatory, Real-Time Monitor, Model Catalog, Settings, Help, Data Export, Automation Engine)
- ❌ **Sidebar navigation incomplete** (only shows implemented routes)
- ❌ **Missing secondary/contextual navigation** for help center, settings, data export

---

## Complete Page Inventory from Specs

### PRIMARY ROUTES (Always-visible in sidebar)

| Order | Route | Page Title | Status | Spec File |
|-------|-------|-----------|--------|-----------|
| 1 | `/` | **Control Tower** (Dashboard) | ✅ Implemented | `01_dashboard_control_tower.md` |
| 2 | `/departments` | **Departments Directory** | ❌ Missing | `02_departments_directory.md` |
| 3 | `/departments/:id` | **Department Detail** | ❌ Missing | `03_department_detail.md` |
| 4 | `/workflows` | **Workflow Explorer** | ❌ Missing | `04_workflow_explorer.md` |
| 5 | `/hitl` | **HITL Console** | ❌ Missing | `05_hitl_console.md` |
| 6 | `/simulator` | **Event Simulator** | ❌ Missing | `06_event_simulator.md` |
| 7 | `/reports` | **Reporting Dashboard** | ❌ Missing | `07_reporting_dashboard.md` |
| 8 | `/ai` | **AI Intelligence** | ❌ Missing | `09_ai_intelligence.md` |
| 9 | `/security` | **Security & Compliance** | ❌ Missing | `08_security_dashboard.md` |

### SECONDARY ROUTES (Contextual / Header Navigation)

| Order | Route | Page Title | Status | Spec File | Accessible From |
|-------|-------|-----------|--------|-----------|-----------------|
| 10 | `/ml-observatory` | **ML Observatory** | ❌ Missing | `14_ml_observatory.md` | AI Intelligence, Reports |
| 11 | `/realtime-monitor` | **Real-Time Monitor** | ❌ Missing | `15_real_time_monitor.md` | Header Icon, HITL |
| 12 | `/automation-engine` | **Automation Engine** | ❌ Missing | `16_automation_engine.md` | Workflow Explorer |
| 13 | `/models` | **Model Catalog** | ❌ Missing | `10_model_catalog.md` | AI Intelligence |
| 14 | `/settings` | **Settings & Preferences** | ❌ Missing | `11_settings_page.md` | Header Menu |
| 15 | `/help` | **Help Center** | ❌ Missing | `12_help_center.md` | Header Icon |
| 16 | `/export` | **Data Export Utility** | ❌ Missing | `13_data_export.md` | Reports |

### CURRENTLY IMPLEMENTED BUT NOT IN SPECS

| Route | Status | Notes |
|-------|--------|-------|
| `/backlog` | ⚠️ Not in specs | Custom implementation |
| `/sprint-planning` | ⚠️ Not in specs | Custom implementation |
| `/release` | ⚠️ Not in specs | Custom implementation |

---

## Global Navigation Architecture (From Specs)

### 1. Primary Sidebar Navigation
**Always visible, persistent navigation bar on the left side**

```
📊 Dashboard              → /
🏢 Departments            → /departments
🔄 Workflows              → /workflows
✋ HITL Console           → /hitl
⚡ Event Simulator       → /simulator
📈 Reports               → /reports
🤖 AI Intelligence       → /ai
🔒 Security              → /security
```

**Key Principle:** Primary navigation reflects the main user workflows and should remain stable.

### 2. Secondary Navigation (Header & Contextual)
**Accessible via header icons, buttons, or drill-down from primary pages**

**Header Icons/Menu:**
- ⏱️ Real-Time Monitor (top-right icon, always accessible)
- ⚙️ Settings (user menu)
- ❓ Help Center (question mark icon)

**Contextual Routes (accessed via CTAs in pages):**
- Departments → Department Detail → ML Observatory
- AI Intelligence → ML Observatory
- AI Intelligence → Model Catalog
- Workflows → Automation Engine
- Reports → Data Export Utility

### 3. Navigation Hierarchy

```
App Shell (Header + Sidebar + Global Context Banner)
│
├── Primary Routes (Always in sidebar)
│   ├── / (Dashboard)
│   ├── /departments (Departments Directory)
│   ├── /workflows (Workflow Explorer)
│   ├── /hitl (HITL Console)
│   ├── /simulator (Event Simulator)
│   ├── /reports (Reports)
│   ├── /ai (AI Intelligence)
│   └── /security (Security Dashboard)
│
├── Secondary Routes (Header + Contextual)
│   ├── /realtime-monitor (Real-Time Monitor)
│   ├── /automation-engine (Automation Engine)
│   ├── /models (Model Catalog)
│   ├── /ml-observatory (ML Observatory)
│   ├── /settings (Settings)
│   ├── /help (Help Center)
│   └── /export (Data Export)
│
└── Detail Routes (Dynamic)
    └── /departments/:id (Department Detail)
```

---

## Current Implementation Gaps

### Missing Core Features

1. **Navigation Structure**
   - Sidebar only shows 3 items (Dashboard, Backlog, Sprint-Planning, Release)
   - 13 items from spec are missing

2. **Route Definitions**
   - `App.tsx` only defines 4 routes
   - 13 route handlers needed

3. **Secondary Navigation**
   - No header icons for Real-Time Monitor, Help, Settings
   - No contextual navigation from pages to secondary surfaces
   - No data export CTA in Reports

4. **Department Detail Routes**
   - No route pattern for `/departments/:id`

---

## Recommended Implementation Plan

### Phase 1: Update Navigation UI (Layout.tsx)

**Goal:** Create a more visible, organized navigation that shows both primary and secondary routes.

**Changes:**
1. Enhance `SidebarContent` to show:
   - **Primary section:** 8 main routes
   - **Secondary section:** 6 secondary routes with "More" label
   - **Collapsible submenu** on mobile for secondary routes

2. Add header icons for:
   - Real-Time Monitor (⏱️ icon, top-right)
   - Help (❓ icon)
   - Settings (already present as 👤)

### Phase 2: Create Route Definitions (App.tsx)

**Goal:** Add all 13 missing routes with placeholder components.

**Changes:**
```tsx
import Dashboard from "./features/dashboard/Dashboard.tsx"
import DepartmentList from "./features/departments/DepartmentList.tsx"
import DepartmentDetail from "./features/departments/DepartmentDetail.tsx"
import WorkflowExplorer from "./features/workflows/WorkflowExplorer.tsx"
import HITLConsole from "./features/hitl/HITLConsole.tsx"
import EventSimulator from "./features/simulator/EventSimulator.tsx"
import ReportingDashboard from "./features/reports/ReportingDashboard.tsx"
import SecurityDashboard from "./features/security/SecurityDashboard.tsx"
import AIIntelligence from "./features/ai/AIIntelligence.tsx"
import RealTimeMonitor from "./features/monitor/RealTimeMonitor.tsx"
import AutomationEngine from "./features/automation/AutomationEngine.tsx"
import ModelCatalog from "./features/models/ModelCatalog.tsx"
import MLObservatory from "./features/ml-observatory/MLObservatory.tsx"
import Settings from "./features/settings/Settings.tsx"
import HelpCenter from "./features/help/HelpCenter.tsx"
import DataExport from "./features/export/DataExport.tsx"
```

### Phase 3: Create Page Components

**Goal:** Implement skeleton/basic page components for all 13 missing pages.

### Phase 4: Add Navigation Links

**Goal:** Connect pages with contextual navigation (CTAs, buttons, drill-down routes).

---

## Navigation Best Practices from Specs

### 1. Tenant & Environment Context

**Contract:** Tenant and environment selectors appear in header and remain persistent across navigation.

**Current Status:** ✅ Implemented in Layout.tsx
- Tenant selector present
- Environment selector present
- Global context banner shows selected context

### 2. Page Header Pattern

**Contract:** Every page follows:
```
H1: Clear title (e.g., "Control Tower", "Departments")
Subtitle: One-line description
Optional CTAs: Primary and secondary actions
```

**Current Status:** ❌ Needs implementation in all missing pages

### 3. Loading & Error States

**Contract:** All page transitions should show loading skeletons or spinners.

**Current Status:** ⚠️ Partially implemented (Dashboard has error states)

### 4. Responsive Navigation

**Contract:** Sidebar should collapse on mobile; secondary nav should become accessible via menu.

**Current Status:** ✅ Partially implemented (sidebar collapse works)
**Needs:** Better mobile UX for secondary routes

---

## Specific Alignment Issues

### Issue 1: Sidebar Missing Secondary Routes
**Current:**
```
📊 Dashboard
🏢 Departments
🔄 Workflows
✋ HITL Console
⚡ Event Simulator
📈 Reports
🤖 AI Intelligence
🔒 Security
```

**Should Also Show (with "More" indicator):**
```
⏱️ Real-Time Monitor
🤖 Automation Engine
📦 Model Catalog
🔬 ML Observatory
⚙️ Settings
❓ Help Center
📤 Data Export
```

### Issue 2: Header Icons Missing
**Current:** Only theme, tenant, environment, user menu

**Should Add:**
- ⏱️ Real-Time Monitor (prominent, always accessible for monitoring incidents)
- ❓ Help (contextual help for current page)
- Already has 👤 for Settings

### Issue 3: No Dynamic Routes
**Current:** No `/departments/:id` route

**Should Add:**
```tsx
<Route path="/departments/:id" element={<DepartmentDetail />} />
```

### Issue 4: Missing Page Header Subtitles
**Current:** Dashboard has them, others missing

**Alignment needed:** All pages should follow the pattern from spec

---

## Quick Navigation Map for Developers

### How to Add a New Primary Route

1. **Create component:** `src/features/<feature>/<Feature>.tsx`
2. **Add to Layout.tsx sidebar:** 
   ```tsx
   <NavLinkItem to="/feature" icon="📋" label="Feature" collapsed={sidebarCollapsed} />
   ```
3. **Add to App.tsx routes:**
   ```tsx
   <Route path="/feature" element={<Feature />} />
   ```

### How to Add a Secondary Route

1. **Create component:** `src/features/<feature>/<Feature>.tsx`
2. **Add to App.tsx routes** (do NOT add to sidebar)
3. **Add contextual CTA** from related primary page

### How to Add a Dynamic Route

1. **Create detail component:** `src/features/<feature>/<FeatureDetail>.tsx`
2. **Add to App.tsx routes:**
   ```tsx
   <Route path="/<feature>/:id" element={<FeatureDetail />} />
   ```
3. **Update list component** to include navigation links:
   ```tsx
   <Link to={`/departments/${dept.id}`}>{dept.name}</Link>
   ```

---

## Summary of Required Changes

| Component | Change | Priority | Effort |
|-----------|--------|----------|--------|
| `Layout.tsx` | Add secondary route section in sidebar | High | Small |
| `App.tsx` | Add 13 missing routes | High | Medium |
| Page Components | Create skeleton pages | High | Large |
| Header Icons | Add Real-Time Monitor, Help icons | Medium | Small |
| Page Headers | Add H1 + Subtitle to each page | Medium | Small |
| Navigation Links | Add CTAs between related pages | Medium | Medium |

---

## Files to Create

**New Components Needed:**
```
src/features/departments/DepartmentDetail.tsx
src/features/workflows/WorkflowExplorer.tsx
src/features/hitl/HITLConsole.tsx
src/features/simulator/EventSimulator.tsx
src/features/reports/ReportingDashboard.tsx
src/features/security/SecurityDashboard.tsx
src/features/ai/AIIntelligence.tsx
src/features/monitor/RealTimeMonitor.tsx
src/features/automation/AutomationEngine.tsx
src/features/models/ModelCatalog.tsx
src/features/ml-observatory/MLObservatory.tsx
src/features/settings/Settings.tsx
src/features/help/HelpCenter.tsx
src/features/export/DataExport.tsx
```

---

## Next Steps

1. **Review this alignment** with the team
2. **Prioritize:** Which pages are critical for MVP?
3. **Create skeleton pages** with proper headers and navigation
4. **Update routing** in App.tsx
5. **Enhance navigation UI** in Layout.tsx to show all routes
6. **Add contextual links** between related pages
