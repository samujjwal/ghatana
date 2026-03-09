# YAPPC App Creator - Comprehensive UI/UX Specification

> **Version:** 2.0.0  
> **Last Updated:** January 8, 2026  
> **Status:** Design System & Component Consolidation

---

## Table of Contents

1. [Design Philosophy](#1-design-philosophy)
2. [Visual Design System](#2-visual-design-system)
3. [Component Architecture](#3-component-architecture)
4. [Route & Page Specifications](#4-route--page-specifications)
5. [State Matrix & Combinations](#5-state-matrix--combinations)
6. [Component Consolidation Plan](#6-component-consolidation-plan)
7. [Accessibility Standards](#7-accessibility-standards)
8. [Responsive Breakpoints](#8-responsive-breakpoints)

---

## 1. Design Philosophy

### Core Principles

| Principle | Description |
|-----------|-------------|
| **One Input, Infinite Possibilities** | AI-first interface with single command entry point |
| **Progressive Disclosure** | Show only what's needed at each moment |
| **Consistent Patterns** | Same action = same component everywhere |
| **Zero Redundancy** | One way to do each thing, not three |
| **Lifecycle-Driven** | Every UI element maps to a development phase |

### Design Tokens

```typescript
// Spacing Scale
const SPACING = {
  xs: '4px',   // micro-spacing
  sm: '8px',   // tight
  md: '16px',  // standard
  lg: '24px',  // comfortable
  xl: '32px',  // section breaks
  '2xl': '48px' // major sections
};

// Layout Dimensions
const LAYOUT = {
  SIDEBAR: {
    expanded: '224px',
    collapsed: '60px',
    transition: '200ms ease-in-out'
  },
  PANEL: {
    standard: '320px',
    wide: '420px'
  },
  TOOLBAR: {
    height: '48px',
    compact: '40px'
  },
  STATUSBAR: {
    height: '32px'
  }
};

// Color Semantics
const COLORS = {
  phases: {
    intent: 'indigo',
    shape: 'violet', 
    validate: 'amber',
    generate: 'emerald',
    run: 'blue',
    observe: 'cyan',
    improve: 'pink'
  },
  status: {
    success: 'green',
    warning: 'amber',
    error: 'red',
    info: 'blue',
    neutral: 'gray'
  }
};
```

---

## 2. Visual Design System

### 2.1 Typography Hierarchy

| Level | Usage | Font Weight | Size |
|-------|-------|-------------|------|
| **H1** | Page titles | 700 | 2.25rem |
| **H2** | Section headers | 600 | 1.5rem |
| **H3** | Card titles | 600 | 1.25rem |
| **H4** | Panel headers | 600 | 1rem |
| **Body** | Content | 400 | 0.875rem |
| **Caption** | Hints, labels | 400 | 0.75rem |
| **Code** | Monospace | 500 | 0.875rem |

### 2.2 Elevation & Shadows

| Level | CSS | Usage |
|-------|-----|-------|
| **0** | None | Flat elements |
| **1** | `shadow-sm` | Cards, buttons |
| **2** | `shadow-md` | Dropdowns, tooltips |
| **3** | `shadow-lg` | Modals, drawers |
| **4** | `shadow-xl` | Command palette |

### 2.3 Border Radius

| Size | Value | Usage |
|------|-------|-------|
| **sm** | 4px | Buttons, inputs |
| **md** | 8px | Cards |
| **lg** | 12px | Panels |
| **xl** | 16px | Dialogs |
| **full** | 9999px | Pills, avatars |

---

## 3. Component Architecture

### 3.1 Unified Component Tree

```
App Root
├── RootLayout (_root.tsx)
│   ├── HydrateFallback (loading)
│   ├── ErrorBoundary (error)
│   └── Main Content
│
├── AppShell (_shell.tsx)
│   ├── CollapsibleSidebar ← CANONICAL
│   │   ├── WorkspaceSelector
│   │   ├── NavLinks
│   │   └── QuickActions
│   ├── BreadcrumbBar ← CANONICAL
│   ├── GuidancePanel (collapsible)
│   └── MainContent <Outlet />
│
├── ProjectShell (project/_shell.tsx)
│   ├── ProjectHeader
│   ├── TabNavigation [Build|Preview|Deploy|Settings]
│   └── TabContent <Outlet />
│
└── CanvasScene (canvas/CanvasScene.tsx)
    ├── UnifiedLeftPanel ← CANONICAL
    │   ├── TaskPanel
    │   └── ComponentPalette
    ├── CanvasManager
    │   ├── UnifiedCanvasToolbar ← CANONICAL
    │   ├── ModeContentRenderer
    │   ├── CanvasStatusBar
    │   └── EmptyState (conditional)
    └── CanvasPanels
        ├── UnifiedRightPanel ← CANONICAL
        ├── CommandPalette ← CANONICAL (global)
        └── Dialogs/Drawers
```

### 3.2 Canonical Components (Single Source of Truth)

| Component | Location | Purpose |
|-----------|----------|---------|
| `CommandPalette` | `@yappc/ui` | Global actions (⌘K) |
| `BreadcrumbBar` | `components/navigation` | Navigation trail |
| `UnifiedCanvasToolbar` | `components/canvas` | Canvas controls |
| `UnifiedLeftPanel` | `components/canvas` | Tasks + Components |
| `UnifiedRightPanel` | `components/canvas` | AI/Validate/Generate |
| `EmptyState` | `components/common` | Empty state pattern |
| `LoadingFallback` | `components/route` | Loading states |
| `ErrorBoundary` | `components/route` | Error handling |

---

## 4. Route & Page Specifications

### 4.1 Landing Page (`/`)

```
┌─────────────────────────────────────────────────────────────┐
│  [Logo] YAPPC                              [🌙] [Login]     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│           ╔═══════════════════════════════════╗             │
│           ║      Welcome to YAPPC             ║             │
│           ║  Design • Build • Deploy          ║             │
│           ╚═══════════════════════════════════╝             │
│                                                             │
│    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│    │   ✨ Create  │  │  📁 Projects │  │  🔀 Workflows│    │
│    │     New      │  │    My Work   │  │   Automation │    │
│    │              │  │              │  │              │    │
│    │  [Start →]   │  │   [View →]   │  │   [Open →]   │    │
│    └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘

States:
├── Default: 3 action cards
├── Authenticated: Show user avatar instead of Login
└── Loading: Skeleton placeholders
```

### 4.2 App Shell (`/app/*`)

```
┌─────────────────────────────────────────────────────────────┐
│ Sidebar (224px/60px)  │  Main Content Area                  │
│ ┌───────────────────┐ │ ┌─────────────────────────────────┐ │
│ │ [Workspace ▾]     │ │ │ [Home] / [Workspace] / [Page]   │ │
│ │ ─────────────────│ │ │ ───────────────────────────────│ │
│ │ 🏠 Home           │ │ │                                 │ │
│ │ ✨ New Project    │ │ │     <Outlet /> Content          │ │
│ │ ─────────────────│ │ │                                 │ │
│ │ 📁 Project 1      │ │ │                                 │ │
│ │ 📁 Project 2      │ │ │                                 │ │
│ │ → View all        │ │ │                                 │ │
│ │ ─────────────────│ │ │                                 │ │
│ │ [?] [🌙] [≡/×]    │ │ │                                 │ │
│ └───────────────────┘ │ └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

States:
├── Sidebar Expanded (224px): Full labels visible
├── Sidebar Collapsed (60px): Icons only, tooltips on hover
├── Canvas Mode: Auto-collapse sidebar
├── Guidance Panel: Opens right of main content
└── Loading: Breadcrumb skeleton, content skeleton
```

### 4.3 AI Command Center (`/app` index)

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│              ╔═══════════════════════════════╗              │
│              ║  What would you like to build? ║              │
│              ╠═══════════════════════════════╣              │
│              ║ [🎤] Type or speak...         ║              │
│              ╚═══════════════════════════════╝              │
│                                                             │
│   Recent:  [E-commerce App] [Blog CMS] [Dashboard]          │
│                                                             │
│   ┌─────────────────────────────────────────┐               │
│   │  💡 AI Response Card                    │               │
│   │  ─────────────────────────────────────  │               │
│   │  [Suggested Action]                     │               │
│   │                                         │               │
│   │  [✓ Accept]  [✕ Reject]  [✎ Modify]    │               │
│   └─────────────────────────────────────────┘               │
│                                                             │
└─────────────────────────────────────────────────────────────┘

States:
├── Welcome (first visit): Toast notification
├── Idle: Command input focused
├── Processing: Spinner in input, disabled
├── Response Ready: AI card appears with actions
├── Confirming: Accept/Reject buttons enabled
└── Error: Error toast, retry option
```

### 4.4 Projects List (`/app/projects`)

```
┌─────────────────────────────────────────────────────────────┐
│  Projects                                   [+ New Project] │
├─────────────────────────────────────────────────────────────┤
│  [🔍 Search...]  [All ▾] [Updated ▾]  [▦ Grid] [☰ List]    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ 📁 Proj 1  │  │ 📁 Proj 2  │  │ 📁 Proj 3  │            │
│  │ ──────────│  │ ──────────│  │ ──────────│            │
│  │ [🟢 85%]  │  │ [🟡 65%]  │  │ [🔴 35%]  │            │
│  │ Shape     │  │ Validate  │  │ Intent    │            │
│  │ 2h ago    │  │ 1d ago    │  │ 3d ago    │            │
│  └────────────┘  └────────────┘  └────────────┘            │
│                                                             │
│  [Load More...]                                             │
└─────────────────────────────────────────────────────────────┘

States:
├── Loading: Skeleton grid/list
├── Empty: EmptyState with "Create your first project"
├── Filtered: Shows matching projects
├── Grid View: Card layout
├── List View: Row layout
└── Error: Error message with retry
```

### 4.5 Project Canvas (`/app/p/:id/canvas`)

```
┌─────────────────────────────────────────────────────────────┐
│ Left Panel (300px/48px) │  Canvas Area        │ Right (320px)│
│ ┌─────────────────────┐ │ ┌───────────────────────────────┐ │
│ │ [Tasks][Components] │ │ │[◀][▶]│[Mode▾][Level▾]│[Stat]│ │
│ │ ─────────────────── │ │ ├───────────────────────────────┤ │
│ │                     │ │ │                               │ │
│ │ Task Panel or       │ │ │                               │ │
│ │ Component Palette   │ │ │     React Flow Canvas         │ │
│ │                     │ │ │     (or Empty State)          │ │
│ │                     │ │ │                               │ │
│ │                     │ │ │                               │ │
│ │                     │ │ ├───────────────────────────────┤ │
│ │                     │ │ │ Status Bar: Phase Progress    │ │
│ └─────────────────────┘ │ └───────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

Canvas States:
├── Empty: Centered ImprovedEmptyState card
├── Loaded: React Flow with nodes/edges
├── Mode × Level: 7 modes × 4 levels = 28 combinations
├── Loading: Skeleton canvas
├── Auto-saving: Status indicator in toolbar
└── Error: Error toast with retry

Toolbar States:
├── History: [Undo][Redo] - visible when nodes > 0
├── Mode: Dropdown with 7 options + shortcuts
├── Level: Dropdown with 4 options
├── Save: idle → saving → saved → error
├── AI Badge: Shows suggestion count
└── Mobile: Overflow menu for extra actions
```

### 4.6 Empty State Pattern (Reusable)

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│              ┌───────────────────────────────┐              │
│              │  ┌─────────────────────────┐  │              │
│              │  │    [64×64 Icon]         │  │              │
│              │  └─────────────────────────┘  │              │
│              │                               │              │
│              │  Title (h5, 600 weight)       │              │
│              │  Description (body1, secondary)│              │
│              │                               │              │
│              │  [Primary CTA] [Secondary] [Text]│           │
│              │                               │              │
│              │  💡 Tip: Helpful hint          │              │
│              └───────────────────────────────┘              │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Props:
├── icon: ReactNode
├── title: string
├── description: string
├── primaryAction: { label, onClick, icon }
├── secondaryAction?: { label, onClick }
├── tertiaryAction?: { label, onClick }
└── tip?: string
```

### 4.7 Preview Page (`/app/p/:id/preview`)

```
┌─────────────────────────────────────────────────────────────┐
│  Preview                    [📱][⬜][💻]  [🔄][↗️]          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│       ┌─────────────────────────────────────┐               │
│       │                                     │               │
│       │         Preview iFrame              │               │
│       │         (device dimensions)         │               │
│       │                                     │               │
│       └─────────────────────────────────────┘               │
│                                                             │
└─────────────────────────────────────────────────────────────┘

States:
├── Mobile (375px)
├── Tablet (768px)
├── Desktop (1280px)
└── Loading: Skeleton frame
```

### 4.8 Deploy Page (`/app/p/:id/deploy`)

```
┌─────────────────────────────────────────────────────────────┐
│  Deploy                              [↩️ Rollback] [🚀 Deploy]│
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Current Deployment                                         │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Version: v1.2.3    Status: [🟢 Live]    2h ago         ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  Deployment History                                         │
│  ┌────────┬──────────┬─────────┬─────────────────┐         │
│  │ Version│ Status   │ Date    │ Actions         │         │
│  ├────────┼──────────┼─────────┼─────────────────┤         │
│  │ v1.2.3 │ 🟢 Live  │ 2h ago  │ [View] [Logs]   │         │
│  │ v1.2.2 │ ⚪ Old   │ 1d ago  │ [View] [Rollback]│         │
│  └────────┴──────────┴─────────┴─────────────────┘         │
│                                                             │
└─────────────────────────────────────────────────────────────┘

States:
├── No deployments: EmptyState "Deploy your first version"
├── Deploying: Progress bar, cancel option
├── Deployed: Success toast, live badge
└── Error: Error message, retry option
```

### 4.9 Settings Page (`/app/p/:id/settings`)

```
┌─────────────────────────────────────────────────────────────┐
│  Settings                                                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Project Configuration                                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Name:        [________________]                         ││
│  │ Description: [________________]                         ││
│  │ Status:      [Active ▾]                                 ││
│  │                                           [Save Changes]││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  Team                              [+ Invite Member]        │
│  ┌────────┬─────────┬──────────┬─────────┐                 │
│  │ Member │ Role    │ Joined   │ Actions │                 │
│  ├────────┼─────────┼──────────┼─────────┤                 │
│  │ @user1 │ Owner   │ Jan 1    │         │                 │
│  │ @user2 │ Editor  │ Jan 5    │ [Remove]│                 │
│  └────────┴─────────┴──────────┴─────────┘                 │
│                                                             │
│  [Danger Zone ▾]                                            │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Archive Project: [Archive]                              ││
│  │ Delete Project:  [Delete] ← Requires confirmation       ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘

Sections:
├── Project Configuration (always visible)
├── Team Management (always visible)
├── API Tokens (collapsible)
├── Webhooks (collapsible)
├── Audit Logs (collapsible)
└── Danger Zone (collapsible, red accent)
```

### 4.10 Workflows List (`/workflows`)

```
┌─────────────────────────────────────────────────────────────┐
│  Workflows                                  [+ New Workflow] │
├─────────────────────────────────────────────────────────────┤
│  [🔍 Search...]  [All Status ▾]  [All Types ▾]              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────────────┬───────────┬──────────┬─────────┬─────────┐ │
│  │ Name       │ Type      │ Status   │ Updated │ Actions │ │
│  ├────────────┼───────────┼──────────┼─────────┼─────────┤ │
│  │ Fix Bug #1 │ 🐛 BUG    │ 🟢 Active│ 2h ago  │ [Open]  │ │
│  │ Feature X  │ ✨ FEATURE│ 🟡 Draft │ 1d ago  │ [Open]  │ │
│  └────────────┴───────────┴──────────┴─────────┴─────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Workflow Types:
├── 🐛 BUG_FIX
├── ✨ FEATURE
├── 🚨 INCIDENT
├── 📦 RELEASE
├── 🔒 SECURITY
├── 📚 DOCUMENTATION
├── ⚡ OPTIMIZATION
├── 🔄 MIGRATION
├── 🏗️ INFRASTRUCTURE
└── 🆘 SUPPORT
```

### 4.11 New Workflow Wizard (`/workflows/new`)

```
┌─────────────────────────────────────────────────────────────┐
│  New Workflow                                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Step 1: Choose Type  ──●──  Step 2  ──○──  Step 3          │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Select Workflow Type                                   ││
│  │                                                         ││
│  │  [🐛 Bug Fix  ]  [✨ Feature  ]  [🚨 Incident]          ││
│  │  [📦 Release  ]  [🔒 Security ]  [📚 Docs    ]          ││
│  │  [⚡ Optimize ]  [🔄 Migration]  [🏗️ Infra   ]          ││
│  │                                                         ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│                                         [Cancel] [Next →]   │
└─────────────────────────────────────────────────────────────┘

Steps:
├── Step 1: Choose Type (grid selection)
├── Step 2: Set AI Mode (AI_AUTONOMOUS|AI_ASSISTED|HUMAN_ONLY)
└── Step 3: Name & Create (form with name input)
```

### 4.12 404 Not Found (`/*`)

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                         404                                 │
│                                                             │
│                    Page Not Found                           │
│                                                             │
│          The page you're looking for doesn't exist          │
│          or has been moved.                                 │
│                                                             │
│       [🏠 Dashboard]  [← Go Back]  [📧 Contact Support]     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. State Matrix & Combinations

### 5.1 Canvas Mode × Abstraction Level Matrix

| Mode | System | Component | File | Code |
|------|--------|-----------|------|------|
| **brainstorm** | Idea clusters | Note groups | Note detail | Text edit |
| **diagram** | Architecture | Services | APIs | Endpoints |
| **design** | App layout | Screens | Components | Widgets |
| **code** | Module map | Classes | Functions | Lines |
| **test** | Test suites | Test files | Test cases | Assertions |
| **deploy** | Environments | Pipelines | Stages | Tasks |
| **observe** | Dashboards | Metrics | Alerts | Logs |

### 5.2 UI State Combinations

```typescript
type CanvasState = {
  mode: 'brainstorm' | 'diagram' | 'design' | 'code' | 'test' | 'deploy' | 'observe';
  level: 'system' | 'component' | 'file' | 'code';
  phase: 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'improve';
  saveStatus: 'idle' | 'saving' | 'saved' | 'error';
  sidebar: 'expanded' | 'collapsed';
  leftPanel: { tab: 'tasks' | 'components'; collapsed: boolean };
  rightPanel: { open: boolean; tab: 0 | 1 | 2 | 3 };
  dialogs: {
    commandPalette: boolean;
    templateGallery: boolean;
    saveTemplate: boolean;
    welcome: boolean;
    autoLayout: boolean;
  };
  content: 'empty' | 'loading' | 'loaded' | 'error';
};

// Valid combinations: 7 × 4 × 7 × 4 × 2 × 4 × 10 × 32 × 4 = millions
// But most are constrained by business logic
```

### 5.3 Constraint Rules

```typescript
const constraints = {
  // Auto-collapse sidebar in canvas mode
  'inCanvas && nodes.length > 0': 'sidebar = collapsed',
  
  // Show status bar only when content exists
  'nodes.length === 0': 'statusBar = hidden',
  
  // Progressive disclosure for toolbar
  'nodes.length === 0': 'historyButtons = hidden',
  
  // Mode-level affinity
  'mode === brainstorm': 'preferLevel = system',
  'mode === code': 'preferLevel = code',
  
  // Phase-mode alignment
  'phase === intent': 'preferMode = brainstorm',
  'phase === generate': 'preferMode = code',
};
```

---

## 6. Component Consolidation Plan

### 6.1 Identified Redundancies

| Issue | Current | Target |
|-------|---------|--------|
| 3× Command Palette | `components/command`, `libs/ui`, `libs/ui/Shortcuts` | Single `@yappc/ui/CommandPalette` |
| 3× Breadcrumbs | `navigation/BreadcrumbBar`, `workspace/Header`, `canvas/Breadcrumbs` | Single `navigation/BreadcrumbBar` |
| 2× Empty State | `ImprovedEmptyState`, `CanvasEmptyState` | Single `common/EmptyState` |
| 2× History Toolbar | `HistoryToolbar`, buttons in `UnifiedCanvasToolbar` | Toolbar only (progressive) |
| 2× Quick Actions | `CanvasQuickActions`, `PersonaQuickActions` | REMOVE (deprecated) |

### 6.2 Consolidation Actions

```typescript
// Phase 1: Remove deprecated components
const removeFiles = [
  'components/canvas/panels/CanvasEmptyState.tsx',
  'components/canvas/controls/CanvasQuickActions.tsx',
  'components/persona/PersonaQuickActions.tsx',
];

// Phase 2: Consolidate to canonical
const consolidateMap = {
  'libs/ui/CommandPalette': 'CANONICAL - keep',
  'libs/ui/Shortcuts/CommandPalette': 'MERGE into canonical',
  'components/command/CommandPalette': 'MIGRATE to canonical',
  
  'components/navigation/BreadcrumbBar': 'CANONICAL - keep',
  'components/workspace/HeaderWithBreadcrumb': 'REMOVE - use BreadcrumbBar',
  'components/canvas/navigation/Breadcrumbs': 'REMOVE - use BreadcrumbBar',
};

// Phase 3: Create shared utilities
const sharedUtils = [
  'utils/fuzzySearch.ts', // Extract from CommandPalette
  'utils/phaseColors.ts', // Consistent phase coloring
  'utils/statusColors.ts', // Consistent status coloring
];
```

### 6.3 Component Variants (Not Duplicates)

These are **intentional variants** using composition:

```typescript
// EmptyState variants via props
<EmptyState 
  variant="canvas" 
  icon={<AccountTree />}
  title="Start Building"
  primaryAction={{ label: "AI Assistant", onClick: openAI }}
/>

<EmptyState
  variant="list"
  icon={<FolderOpen />}
  title="No Projects"
  primaryAction={{ label: "Create Project", onClick: createProject }}
/>

// PersonaSwitcher variants
<PersonaSwitcher variant="compact" />
<PersonaSwitcher variant="expanded" />
```

---

## 7. Accessibility Standards

### 7.1 WCAG 2.1 AA Compliance

| Requirement | Implementation |
|-------------|----------------|
| **Keyboard Navigation** | All interactive elements focusable via Tab |
| **Skip Links** | "Skip to main content" in root layout |
| **Focus Indicators** | 2px ring on focus-visible |
| **Color Contrast** | 4.5:1 text, 3:1 UI elements |
| **Screen Reader** | ARIA labels on icons, live regions for updates |
| **Reduced Motion** | Respect `prefers-reduced-motion` |

### 7.2 Keyboard Shortcuts

| Context | Shortcut | Action |
|---------|----------|--------|
| Global | `⌘K` | Command palette |
| Global | `⌘S` | Save |
| Canvas | `⌘Z` | Undo |
| Canvas | `⌘⇧Z` | Redo |
| Canvas | `1-7` | Switch mode |
| Canvas | `Esc` | Deselect/close |
| Canvas | `Del` | Delete selection |
| Dialog | `Esc` | Close dialog |
| List | `↑↓` | Navigate items |
| List | `↵` | Select item |

### 7.3 Focus Management

```typescript
// Dialog focus trap
const DialogWrapper = ({ children, open }) => (
  <FocusTrap active={open}>
    <Dialog open={open}>
      {children}
    </Dialog>
  </FocusTrap>
);

// Return focus after dialog closes
const useDialogFocus = (isOpen: boolean) => {
  const triggerRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!isOpen && triggerRef.current) {
      triggerRef.current.focus();
    }
  }, [isOpen]);
  return triggerRef;
};
```

---

## 8. Responsive Breakpoints

### 8.1 Breakpoint Definitions

| Name | Min Width | Max Width | Sidebar | Panels |
|------|-----------|-----------|---------|--------|
| **xs** | 0 | 639px | Hidden | Bottom drawer |
| **sm** | 640px | 767px | Collapsed | Bottom drawer |
| **md** | 768px | 1023px | Collapsed | Side drawer 280px |
| **lg** | 1024px | 1279px | Expandable | Side drawer 320px |
| **xl** | 1280px | ∞ | Expanded | Side drawer 420px |

### 8.2 Component Behavior

```typescript
const responsiveBehavior = {
  sidebar: {
    xs: 'hidden',
    sm: 'collapsed',
    md: 'collapsed',
    lg: 'toggleable',
    xl: 'expanded'
  },
  leftPanel: {
    xs: 'bottom-sheet',
    sm: 'bottom-sheet',
    md: 'side-48px',
    lg: 'side-300px',
    xl: 'side-300px'
  },
  rightPanel: {
    xs: 'bottom-sheet',
    sm: 'bottom-sheet',
    md: 'side-280px',
    lg: 'side-320px',
    xl: 'side-420px'
  },
  toolbar: {
    xs: 'compact + overflow',
    sm: 'compact + overflow',
    md: 'standard',
    lg: 'standard',
    xl: 'expanded'
  }
};
```

### 8.3 Mobile-First Patterns

```tsx
// Hide on mobile, show on desktop
<Box sx={{ display: { xs: 'none', md: 'flex' } }}>
  <LevelDropdown />
</Box>

// Mobile overflow menu
<Box sx={{ display: { xs: 'flex', md: 'none' } }}>
  <IconButton onClick={openMobileMenu}>
    <MoreVert />
  </IconButton>
</Box>
```

---

## Appendix: Quick Reference

### A. Icon Usage

| Context | Icon | Meaning |
|---------|------|---------|
| Navigation | `Home` | Go home |
| Navigation | `ChevronRight` | Expand/navigate |
| Action | `Add` | Create new |
| Action | `AutoAwesome` | AI-powered |
| Status | `CheckCircle` | Success/complete |
| Status | `Warning` | Warning state |
| Status | `Error` | Error state |
| Mode | `Lightbulb` | Brainstorm |
| Mode | `AccountTree` | Diagram |
| Mode | `Palette` | Design |
| Mode | `Code` | Code |
| Mode | `BugReport` | Test |
| Mode | `RocketLaunch` | Deploy |
| Mode | `Visibility` | Observe |

### B. Color Palette Quick Reference

```css
/* Phase colors */
--intent: #6366f1;    /* indigo-500 */
--shape: #8b5cf6;     /* violet-500 */
--validate: #f59e0b;  /* amber-500 */
--generate: #10b981;  /* emerald-500 */
--run: #3b82f6;       /* blue-500 */
--observe: #06b6d4;   /* cyan-500 */
--improve: #ec4899;   /* pink-500 */

/* Status colors */
--success: #22c55e;   /* green-500 */
--warning: #f59e0b;   /* amber-500 */
--error: #ef4444;     /* red-500 */
--info: #3b82f6;      /* blue-500 */
```

### C. Animation Tokens

```css
/* Transitions */
--transition-fast: 150ms ease-out;
--transition-default: 200ms ease-in-out;
--transition-slow: 300ms ease-in-out;

/* Animations */
@keyframes spin { ... }
@keyframes pulse { ... }
@keyframes bounce { ... }
@keyframes fadeIn { ... }
@keyframes slideUp { ... }
```

---

**End of Specification**

*This document serves as the canonical reference for YAPPC App Creator UI/UX implementation. All new components should follow these patterns and reference this specification.*
