# Comprehensive UX/UI & Architecture Review

> **Date:** 2025-01-05  
> **Scope:** Full review of pages, components, features, navigations, actions, and flows  
> **Goal:** Simplify UX while maintaining/increasing expressiveness; ensure visibility from ideation to delivery
> **Version:** 2.0 - Updated with Workspace→Project→Task hierarchy and Canvas View Modes

---

## Executive Summary

The YAPPC app-creator has a **rich feature set** but currently suffers from **fragmentation** - components exist but aren't always connected, and the user journey from ideation to delivery isn't fully visible in one place.

### Key Findings

| Area | Status | Issue |
|------|--------|-------|
| **Workspace→Project Hierarchy** | ⚠️ Partial | Workspace exists but navigation incomplete |
| **Project Management** | ⚠️ Partial | Create works, but no list view in app shell |
| **7-Phase Lifecycle** | ✅ Exists | Well-designed, but phases scattered across routes |
| **AI-First Entry** | ✅ Exists | Great starting point with intent parsing |
| **Task Management** | ⚠️ Partial | TaskExecutionGrid exists but not integrated in canvas |
| **Workflow System** | ⚠️ Partial | 8-step StepRail exists separately from 7-phase lifecycle |
| **Canvas View Modes** | ❌ Missing | Single view, no activity-based modes |
| **Abstraction Levels** | ❌ Missing | No high-level ↔ low-level navigation in canvas |
| **Tech Stack Visibility** | ⚠️ Partial | Detected in useAICommand but not displayed |
| **Canvas Task View** | ❌ Missing | Tasks not visible from canvas view |

---

## 0. NEW: Workspace → Project → Task Hierarchy

### Current State

The journey should be: **Workspace** → **Projects** → **Tasks** → **Canvas Work**

```
┌─────────────────────────────────────────────────────────────────────┐
│ WORKSPACE (Container - Team/Organization Level)                     │
│  ├─ Owned Projects (Full permissions)                               │
│  │   ├─ Project A                                                   │
│  │   │   ├─ Tasks (per lifecycle phase)                             │
│  │   │   ├─ Canvas (activity-based views)                           │
│  │   │   └─ Artifacts (code, docs, deployments)                     │
│  │   └─ Project B                                                   │
│  └─ Included Projects (Read-only access)                            │
│      └─ Project C (from another workspace)                          │
└─────────────────────────────────────────────────────────────────────┘
```

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `workspaceAtom.ts` | `/state/atoms/` | ✅ Full state management |
| `useWorkspaceData.ts` | `/hooks/` | ✅ API integration |
| `useWorkspaceAdmin.ts` | `/hooks/` | ✅ Admin functions |
| `workspaces.tsx` | `/routes/app/` | ✅ Workspace listing |
| Project listing | - | ⚠️ Only in sidebar, no dedicated route |
| Project creation | Via AI | ✅ Works via useAICommand |
| Project settings | `/routes/app/project/settings.tsx` | ✅ Exists |

### What's Missing

1. **Dedicated Projects Route** (`/app/projects`)
   - List all projects with filters (by phase, status, type)
   - Quick actions (open, archive, delete)
   - Project health dashboard

2. **Workspace Switcher in Shell**
   - Currently: No visible workspace indicator
   - Needed: Dropdown/switcher in top nav

3. **Project Templates**
   - Pre-configured project structures
   - Quick-start for common patterns

4. **Cross-Workspace Project Sharing**
   - Include project flow exists but no UI for it

---

## 0.1 NEW: Activity-Based Canvas Views

### Concept

The canvas should adapt based on **what the user is trying to do**:

| Activity | Canvas View | Primary Artifacts | Key Tools |
|----------|-------------|-------------------|-----------|
| **Brainstorming** | Whiteboard mode | Sticky notes, sketches, mind maps | Freeform drawing, AI suggestions |
| **Diagramming** | Flow diagram mode | Nodes, edges, swimlanes | Auto-layout, snap-to-grid |
| **Designing** | Page designer mode | Components, layouts, styles | Drag-drop, property panel |
| **Coding** | Code view mode | Files, functions, imports | Monaco editor, AI completion |
| **Testing** | Test runner mode | Test cases, coverage, results | Run/debug, assertions |
| **Deploying** | Deployment mode | Environments, configs, logs | Pipeline view, rollback |
| **Monitoring** | Observability mode | Metrics, logs, traces | Dashboards, alerts |

### Current State

```typescript
// Current: Single canvas mode in CanvasScene.tsx
// Only node types available:
const nodeTypes = {
  component: ComponentNode,
  api: ApiNode,
  data: DataNode,
  flow: FlowNode,
  page: PageNode,
};
```

### Proposed: Canvas Mode System

```typescript
// Proposed: CanvasMode type
type CanvasMode = 
  | 'brainstorm'   // Freeform ideation
  | 'diagram'      // Architecture/flow diagrams
  | 'design'       // UI/component design
  | 'code'         // Code editing
  | 'test'         // Test execution
  | 'deploy'       // Deployment pipeline
  | 'observe';     // Monitoring/observability

interface CanvasModeConfig {
  mode: CanvasMode;
  nodeTypes: Record<string, React.ComponentType>;
  tools: CanvasTool[];
  panels: PanelConfig[];
  capabilities: CanvasCapabilities;
}
```

### Implementation Plan

```tsx
// New: CanvasModeSelector.tsx
export function CanvasModeSelector({ 
  currentMode, 
  onModeChange,
  availableModes 
}: CanvasModeSelectorProps) {
  return (
    <ToggleButtonGroup
      value={currentMode}
      exclusive
      onChange={(_, mode) => onModeChange(mode)}
    >
      {availableModes.map(mode => (
        <ToggleButton key={mode.id} value={mode.id}>
          <Tooltip title={mode.description}>
            <Stack direction="row" spacing={1} alignItems="center">
              {mode.icon}
              <span>{mode.label}</span>
            </Stack>
          </Tooltip>
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  );
}

// New: useCanvasMode.ts
export function useCanvasMode(projectId: string) {
  const [mode, setMode] = useState<CanvasMode>('diagram');
  const { currentPhase } = usePhaseContext();
  
  // Auto-suggest mode based on lifecycle phase
  const suggestedMode = useMemo(() => {
    switch (currentPhase) {
      case 'INTENT': return 'brainstorm';
      case 'SHAPE': return 'diagram';
      case 'VALIDATE': return 'diagram';
      case 'GENERATE': return 'code';
      case 'RUN': return 'deploy';
      case 'OBSERVE': return 'observe';
      case 'IMPROVE': return 'code';
      default: return 'diagram';
    }
  }, [currentPhase]);
  
  return { mode, setMode, suggestedMode };
}
```

### Canvas Mode → Node Type Mapping

| Mode | Available Node Types | Palette Items |
|------|---------------------|---------------|
| **Brainstorm** | sticky, sketch, mindmap, freeform | Sticky notes, shapes, connectors |
| **Diagram** | component, api, data, flow, page | Architecture components |
| **Design** | ui-component, layout, style | UI components, layouts |
| **Code** | file, function, class, module | Code artifacts |
| **Test** | test-suite, test-case, assertion | Test components |
| **Deploy** | environment, service, config | Infrastructure |
| **Observe** | metric, log, trace, alert | Monitoring widgets |

---

## 0.2 NEW: Updated Route Structure (Proposed)

### Current Routes

```
/app                              → AI dashboard
  └─ /workspaces                  → Workspace list (exists but hidden)
  └─ /app/p/:projectId            → Project shell
       ├─ /canvas                 → Single canvas view
       ├─ /preview                → Preview
       ├─ /deploy                 → Deploy
       └─ /settings               → Settings
```

### Proposed Routes

```
/app                              → AI dashboard (unchanged)
  └─ /workspaces                  → Workspace list
  └─ /workspaces/:workspaceId     → Workspace detail
       └─ /projects               → Project list for workspace
  └─ /p/:projectId                → Project shell
       ├─ /tasks                  → Task board (NEW - integrated)
       ├─ /canvas                 → Multi-mode canvas
       │    ?mode=brainstorm      → Brainstorm view
       │    ?mode=diagram         → Diagram view (default)
       │    ?mode=design          → Design view
       │    ?mode=code            → Code view
       │    ?mode=test            → Test view
       ├─ /preview                → Preview
       ├─ /deploy                 → Deploy (or ?mode=deploy in canvas)
       ├─ /observe                → Observability (or ?mode=observe)
       └─ /settings               → Settings
```

### Key Changes

1. **Add `/workspaces/:workspaceId/projects`** - Dedicated project listing
2. **Add `/p/:projectId/tasks`** - Integrated task board
3. **Canvas mode via query param** - `?mode=diagram|design|code|test`
4. **Merge deploy/observe into canvas** - Unified experience

---

## 1. Current Route Structure Analysis

```
/app                          → AI-first dashboard ("What do you want to build?")
  └─ /app/p/:projectId        → Project shell with 4 tabs
       ├─ /canvas             → CanvasScene (main builder)
       ├─ /preview            → Live preview
       ├─ /deploy             → Deployment
       └─ /settings           → Project settings

/devsecops                    → DevSecOps dashboard (separate flow)
  └─ /task-board              → Task board view
  └─ /task/:taskId            → Task detail
```

### Issues Identified

1. **Duplicate Workflow Systems:**
   - `LifecyclePhase` (7 phases): INTENT → SHAPE → VALIDATE → GENERATE → RUN → OBSERVE → IMPROVE
   - `WorkflowStep` (8 steps): INTENT → CONTEXT → PLAN → EXECUTE → VERIFY → OBSERVE → LEARN → INSTITUTIONALIZE
   - **These overlap but aren't unified**

2. **Canvas is Isolated:**
   - Tasks live in `/devsecops/task-board`, not accessible from canvas
   - Workflow status not visible while working in canvas
   - No way to see "what's left to do" from the canvas

3. **No Abstraction Navigation:**
   - User cannot zoom between high-level (system diagram) and low-level (code/implementation)
   - Missing: Component → File → Line mapping

4. **No Workspace Context in Shell:**
   - Sidebar shows projects but no workspace indicator
   - Can't switch workspaces without going to `/workspaces`

5. **Single Canvas Mode:**
   - Same view for all activities (brainstorming, coding, testing)
   - No activity-specific tooling or node types

---

## 2. Component Inventory

### Canvas Components (`/components/canvas/`)

| Component | Purpose | Integration |
|-----------|---------|-------------|
| `CanvasScene.tsx` | Main builder | ✅ Core |
| `lifecycle/LifecycleGuidance.tsx` | Phase tips | ✅ In Drawer |
| `lifecycle/LifecyclePhaseIndicator.tsx` | Current phase | ✅ Top-right |
| `ai/AISuggestionsPanel.tsx` | AI suggestions | ✅ In Drawer |
| `validation/ValidationPanel.tsx` | Validation results | ✅ In Drawer |
| `generation/CodeGenerationPanel.tsx` | Code gen UI | ✅ In Drawer |
| `versioning/VersionHistoryPanel.tsx` | History | ✅ In Drawer |
| `collaboration/CommentsPanel.tsx` | Comments | ✅ In Drawer |
| `specialized/DevSecOpsCanvasRefactored.tsx` | DevSecOps view | ⚠️ Separate route |
| `specialized/PageDesignerRefactored.tsx` | Page designer | ⚠️ Separate route |

### Navigation Components (`/components/navigation/`)

| Component | Purpose | Integration |
|-----------|---------|-------------|
| `UnifiedPhaseRail.tsx` | 7-phase nav | ✅ Project shell header |

### Workflow Components (`/components/workflow/`)

| Component | Purpose | Integration |
|-----------|---------|-------------|
| `WorkflowShell.tsx` | 3-pane layout | ⚠️ Not used in canvas |
| `StepRail.tsx` | 8-step stepper | ⚠️ Only in devsecops |
| `steps/*.tsx` | Step-specific forms | ⚠️ Only in devsecops |

### Workspace Components

| Component | Purpose | Integration |
|-----------|---------|-------------|
| `workspaces.tsx` (route) | Workspace list | ✅ Works |
| `useWorkspaceData.ts` | Data fetching | ✅ TanStack Query |
| `useWorkspaceAdmin.ts` | Admin functions | ✅ Member management |
| `workspaceAtom.ts` | State management | ✅ Jotai atoms |

### Task Components (`/components/tasks/`)

| Component | Purpose | Integration |
|-----------|---------|-------------|
| `TaskExecutionGrid.tsx` | Task grid | ⚠️ Not connected to canvas |
| `LifecycleProgressRail.tsx` | Progress | ⚠️ Not used |
| `WorkflowContextPanel.tsx` | Context | ⚠️ Not used |

---

## 3. User Journey Gap Analysis

### User Requirement: "See all details from ideation to delivery to enhancement"

| Stage | Current State | Gap |
|-------|--------------|-----|
| **Ideation** | ✅ AI command input | None |
| **Task Creation** | ⚠️ Tasks created via StepRail | Not visible in canvas |
| **Workflow Per Task** | ⚠️ ExecuteStep exists | Disconnected from canvas |
| **Status** | ⚠️ ValidationPanel has score | No task-level status in canvas |
| **Tech Stacks** | ⚠️ Detected in useAICommand | Not displayed after parsing |
| **Work on Tasks** | ⚠️ TaskExecutionGrid exists | Canvas doesn't show tasks |
| **Abstraction** | ❌ Missing | No zoom between levels |
| **High-Level View** | ✅ Canvas nodes | - |
| **Low-Level View** | ❌ Missing | No code view integration |

---

## 4. Backend API Analysis

### Current API Integration

| Hook | Backend Connection | Status |
|------|-------------------|--------|
| `useDashboardApi` | `/api/audit`, `/api/version`, etc. | ✅ TanStack Query |
| `useAICommand` | Intent processing | ✅ useMutation |
| `useCanvasValidation` | Validation agent | ✅ Local processing |
| `useCodeGeneration` | Generation agent | ✅ Local processing |
| `useWorkspaceData` | Workspace/project data | ✅ Context provider |

### Missing Integrations

1. **Task API:** No backend tasks visible in canvas
2. **Workflow State API:** No persistent workflow state sync
3. **Real-time Updates:** No WebSocket for live updates

---

## 5. Recommendations

### Phase 1: Unify Lifecycle Systems (Priority: HIGH)

**Problem:** Two overlapping systems (7 phases vs 8 steps)

**Solution:** Consolidate into single 7-phase system with sub-tasks

```typescript
// Proposed unified structure
interface UnifiedPhase {
  phase: LifecyclePhase;          // INTENT, SHAPE, VALIDATE, etc.
  tasks: PhaseTask[];             // Tasks within this phase
  status: PhaseStatus;
  progress: number;               // 0-100%
}

interface PhaseTask {
  id: string;
  name: string;
  automationLevel: AutomationLevel;
  status: TaskStatus;
  workflow?: WorkflowStep[];      // Sub-steps for complex tasks
}
```

### Phase 2: Canvas-Integrated Task Panel (Priority: HIGH)

**Problem:** Tasks not visible from canvas

**Solution:** Add collapsible task panel to canvas

```
┌──────────────────────────────────────────────────────────────┐
│ [Phase Rail: INTENT → SHAPE → VALIDATE → GENERATE → ...]     │
├───────────┬──────────────────────────────────────┬───────────┤
│           │                                      │           │
│  Task     │         CANVAS                       │  Details  │
│  Panel    │                                      │  Panel    │
│           │     [Nodes/Edges/Design]             │           │
│  ⬡ Task 1 │                                      │ [Context] │
│  ⬡ Task 2 │                                      │ [Props]   │
│  ⬡ Task 3 │                                      │ [Code]    │
│           │                                      │           │
└───────────┴──────────────────────────────────────┴───────────┘
```

**Implementation:**

```tsx
// New: CanvasTaskPanel.tsx
export function CanvasTaskPanel({ projectId }: { projectId: string }) {
  const tasks = useProjectTasks(projectId);
  const { currentPhase } = usePhaseContext();
  
  const phaseTasks = tasks.filter(t => t.phase === currentPhase);
  
  return (
    <Box sx={{ width: 280, borderRight: 1 }}>
      <Typography variant="h6">
        {PHASE_LABELS[currentPhase]} Tasks
      </Typography>
      {phaseTasks.map(task => (
        <TaskItem 
          key={task.id}
          task={task}
          onSelect={() => selectTask(task)}
        />
      ))}
    </Box>
  );
}
```

### Phase 3: Abstraction Level Navigator (Priority: HIGH)

**Problem:** No way to zoom between high-level and low-level

**Solution:** Add abstraction level selector in canvas

```
┌─────────────────────────────────────────────────────────────┐
│  Abstraction Level:  [System] [Component] [File] [Code]     │
└─────────────────────────────────────────────────────────────┘

Level 1 - System:     App → Frontend → Backend → Database
Level 2 - Component:  AuthModule → LoginPage → SignupPage
Level 3 - File:       LoginPage.tsx → useAuth.ts → auth.service.ts
Level 4 - Code:       Line-by-line implementation
```

**Implementation:**

```typescript
// New: useAbstractionLevel.ts
export type AbstractionLevel = 'system' | 'component' | 'file' | 'code';

export function useAbstractionLevel() {
  const [level, setLevel] = useState<AbstractionLevel>('component');
  
  const drillDown = (nodeId: string) => {
    // Navigate from component to file level
    const nextLevel = LEVEL_ORDER[LEVEL_ORDER.indexOf(level) + 1];
    setLevel(nextLevel);
    focusNode(nodeId);
  };
  
  const zoomOut = () => {
    const prevLevel = LEVEL_ORDER[LEVEL_ORDER.indexOf(level) - 1];
    setLevel(prevLevel);
  };
  
  return { level, setLevel, drillDown, zoomOut };
}
```

### Phase 4: Tech Stack Visibility (Priority: MEDIUM)

**Problem:** Tech stack detected but not displayed

**Solution:** Add tech stack badge to canvas and project views

```tsx
// In CanvasScene.tsx - add to top panel
<Box sx={{ position: 'absolute', top: 24, left: 350, zIndex: 30 }}>
  <TechStackBadges
    stacks={projectTechStack}
    onStackClick={(stack) => openStackDocs(stack)}
  />
</Box>

// TechStackBadges.tsx
export function TechStackBadges({ stacks }: { stacks: string[] }) {
  return (
    <Stack direction="row" spacing={1}>
      {stacks.map(stack => (
        <Chip
          key={stack}
          icon={getTechIcon(stack)}
          label={stack}
          size="small"
          variant="outlined"
        />
      ))}
    </Stack>
  );
}
```

### Phase 5: Progress Dashboard in Canvas (Priority: MEDIUM)

**Problem:** No overall progress visibility while in canvas

**Solution:** Add minimizable progress indicator

```tsx
// New: CanvasProgressWidget.tsx
export function CanvasProgressWidget() {
  const { phases } = useProjectProgress();
  
  return (
    <Paper sx={{ position: 'fixed', bottom: 24, left: 300, zIndex: 50 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        {phases.map(phase => (
          <Tooltip key={phase.name} title={`${phase.name}: ${phase.progress}%`}>
            <Box sx={{ position: 'relative' }}>
              <CircularProgress
                variant="determinate"
                value={phase.progress}
                size={32}
                color={phase.status === 'completed' ? 'success' : 'primary'}
              />
              <PhaseIcon phase={phase.name} />
            </Box>
          </Tooltip>
        ))}
      </Stack>
    </Paper>
  );
}
```

---

## 6. Simplification Opportunities

### Remove Duplications

1. **Merge WorkflowStep and LifecyclePhase**
   - Keep 7-phase LifecyclePhase system
   - Convert 8-step workflow to tasks within phases
   
2. **Consolidate Panels**
   - Combine: LifecycleGuidance + AISuggestions into single "AI Assistant" panel
   - Single right panel with tabs: [Guidance | Suggestions | Validation | Generation]

3. **Simplify Navigation**
   - Remove separate DevSecOps route
   - Integrate task board into canvas as panel
   - One unified experience

### Streamlined User Flow

```
CURRENT:
  Workspaces → App Dashboard → Create Project → Canvas → (switch to DevSecOps) → Task Board

PROPOSED:
  Workspaces → Select Workspace → View Projects → Select Project → Canvas (multi-mode with tasks)
```

---

## 7. UPDATED Implementation Priority

| Phase | Feature | Effort | Impact | New Priority |
|-------|---------|--------|--------|--------------|
| **0** | **Workspace→Project navigation** | Medium | **Critical** | 🔴 P0 |
| **0.1** | **Canvas mode system** | High | **Critical** | 🔴 P0 |
| 1 | Unify lifecycle systems | Medium | High | 🟠 P1 |
| 2 | Canvas task panel | Medium | High | 🟠 P1 |
| 3 | Abstraction level navigator | High | High | 🟡 P2 |
| 4 | Tech stack visibility | Low | Medium | 🟡 P2 |
| 5 | Progress dashboard | Low | Medium | 🟢 P3 |
| 6 | Consolidate right panels | Medium | Medium | 🟢 P3 |
| 7 | Remove DevSecOps separation | High | High | 🟢 P3 |

### Phase 0: Workspace & Project Navigation (NEW - CRITICAL)

**Goal:** Establish proper hierarchy: Workspace → Projects → Tasks

**Implementation Steps:**

1. **Add Workspace Switcher to App Shell**
   ```tsx
   // In _shell.tsx - add to top of sidebar
   <WorkspaceSwitcher
     currentWorkspace={currentWorkspace}
     workspaces={workspaces}
     onSwitch={handleWorkspaceSwitch}
   />
   ```

2. **Create Projects List Route** (`/app/projects`)
   ```tsx
   // New: routes/app/projects.tsx
   export default function ProjectsPage() {
     const { ownedProjects, includedProjects } = useWorkspaceContext();
     
     return (
       <ProjectsGrid
         owned={ownedProjects}
         included={includedProjects}
         onProjectClick={navigateToProject}
         onCreateProject={openCreateModal}
       />
     );
   }
   ```

3. **Update Route Structure**
   ```typescript
   // In routes.ts - add projects route
   route("app", "routes/app/_shell.tsx", [
     index("routes/app/index.tsx"),
     route("workspaces", "routes/app/workspaces.tsx"),
     route("projects", "routes/app/projects.tsx"), // NEW
     route("p/:projectId", "routes/app/project/_shell.tsx", [...]),
   ]),
   ```

### Phase 0.1: Canvas Mode System (NEW - CRITICAL)

**Goal:** Activity-based canvas views

**Implementation Steps:**

1. **Create CanvasMode type and hook**
   ```typescript
   // types/canvasMode.ts
   export type CanvasMode = 
     | 'brainstorm' | 'diagram' | 'design' 
     | 'code' | 'test' | 'deploy' | 'observe';
   
   // hooks/useCanvasMode.ts
   export function useCanvasMode() { ... }
   ```

2. **Create mode-specific node type registries**
   ```typescript
   // services/canvas/modes/
   ├── brainstormMode.ts   // Sticky, sketch nodes
   ├── diagramMode.ts      // Component, API, data nodes
   ├── designMode.ts       // UI component nodes
   ├── codeMode.ts         // File, function nodes
   ├── testMode.ts         // Test case nodes
   ├── deployMode.ts       // Environment nodes
   └── observeMode.ts      // Metric, log nodes
   ```

3. **Add Mode Selector to Canvas**
   ```tsx
   // In CanvasScene.tsx
   <CanvasModeSelector
     currentMode={mode}
     onModeChange={setMode}
     suggestedMode={suggestedModeForPhase}
   />
   ```

4. **Dynamic node types based on mode**
   ```tsx
   const nodeTypes = useMemo(() => 
     getNodeTypesForMode(currentMode), 
     [currentMode]
   );
   ```

---

## 8. Files to Modify

### NEW Files (Phase 0)

| File | Purpose |
|------|---------|
| `routes/app/projects.tsx` | Projects list page |
| `components/workspace/WorkspaceSwitcher.tsx` | Workspace dropdown |
| `components/project/ProjectsGrid.tsx` | Project cards grid |
| `types/canvasMode.ts` | Canvas mode types |
| `hooks/useCanvasMode.ts` | Canvas mode management |
| `services/canvas/modes/*.ts` | Mode-specific configs |
| `components/canvas/CanvasModeSelector.tsx` | Mode switcher UI |

### Core Changes

1. **CanvasScene.tsx** - Add mode selector, task panel, abstraction nav
2. **_shell.tsx (app)** - Add workspace switcher
3. **_shell.tsx (project)** - Integrate unified navigation
4. **routes.ts** - Add projects route
5. **useAbstractionLevel.ts** - New hook for abstraction navigation
6. **useProjectTasks.ts** - New hook for canvas-integrated tasks
7. **CanvasTaskPanel.tsx** - New component
8. **TechStackBadges.tsx** - New component
9. **AbstractionLevelNav.tsx** - New component

### Deprecate/Merge

1. `WorkflowShell.tsx` → Merge into CanvasScene
2. `StepRail.tsx` → Convert to task-based PhaseRail
3. `/devsecops/*` → Merge relevant features into canvas

---

## 9. UPDATED Next Steps

### Immediate (Phase 0 - Critical)

1. [ ] **Add Workspace Switcher** to app shell sidebar
2. [ ] **Create Projects List Route** (`/app/projects`)
3. [ ] **Create CanvasMode type** and hook
4. [ ] **Implement Mode Selector** in canvas header
5. [ ] **Create mode-specific node registries**

### Short-term (Phase 1-2)

6. [ ] Unify lifecycle systems (7 phases + tasks)
7. [ ] Add CanvasTaskPanel to canvas
8. [ ] Implement AbstractionLevelNav

### Medium-term (Phase 3-5)

9. [ ] Add tech stack visibility
10. [ ] Create progress widget
11. [ ] Consolidate right panels

### Long-term (Phase 6-7)

12. [ ] Remove DevSecOps separation (merge into canvas)
13. [ ] Full integration testing
14. [ ] Documentation update

---

## 10. Visual Summary: Complete User Journey

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           COMPLETE USER JOURNEY                               │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  1. WORKSPACE LEVEL                                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ [Workspace Switcher ▼] My Team Workspace                                 │ │
│  │                                                                          │ │
│  │  📁 Owned Projects (3)        📎 Included Projects (2)                  │ │
│  │  ├── E-commerce App           ├── Shared UI Library                     │ │
│  │  ├── Mobile App               └── API Gateway                           │ │
│  │  └── Admin Dashboard                                                     │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                      ↓                                        │
│  2. PROJECT LEVEL                                                             │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ Project: E-commerce App                                                  │ │
│  │ Phase: [INTENT] → SHAPE → VALIDATE → GENERATE → RUN → OBSERVE → IMPROVE │ │
│  │ Tech: [React] [Node.js] [PostgreSQL] [Docker]                           │ │
│  │                                                                          │ │
│  │ Tabs: [Tasks] [Canvas] [Preview] [Deploy] [Settings]                    │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                      ↓                                        │
│  3. CANVAS LEVEL (Multi-Mode)                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │ Canvas Mode: [Brainstorm] [Diagram ●] [Design] [Code] [Test] [Deploy]   │ │
│  │ Abstraction: [System] [Component ●] [File] [Code]                       │ │
│  ├────────────┬─────────────────────────────────────────────┬──────────────┤ │
│  │            │                                             │              │ │
│  │  TASKS     │           CANVAS WORKSPACE                  │  DETAILS     │ │
│  │            │                                             │              │ │
│  │  ☑ Task 1  │      ┌─────┐      ┌─────┐                   │  Selected:   │ │
│  │  ◯ Task 2  │      │ API │──────│ DB  │                   │  LoginPage   │ │
│  │  ◯ Task 3  │      └──┬──┘      └─────┘                   │              │ │
│  │  ◯ Task 4  │         │                                   │  Props: ...  │ │
│  │            │      ┌──┴──┐                                │  Code: ...   │ │
│  │  Progress: │      │ UI  │                                │  Tests: ...  │ │
│  │  ████░░ 40%│      └─────┘                                │              │ │
│  │            │                                             │              │ │
│  └────────────┴─────────────────────────────────────────────┴──────────────┘ │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Appendix A: Component Dependency Graph

```
App Entry
└─ _shell.tsx (WorkflowContextProvider)
   ├─ WorkspaceSwitcher (NEW)
   ├─ ProjectsList (sidebar)
   ├─ UnifiedPhaseRail
   ├─ GuidancePanel
   ├─ CommandPalette
   └─ Main Content
      ├─ /app/projects (NEW)
      │  └─ ProjectsGrid
      └─ /app/p/:projectId
         └─ project/_shell.tsx
            └─ canvas/CanvasRoute.tsx
               └─ CanvasScene.tsx
                  ├─ CanvasModeSelector (NEW)
                  ├─ AbstractionLevelNav (NEW)
                  ├─ CanvasTaskPanel (NEW)
                  ├─ TechStackBadges (NEW)
                  ├─ ReactFlowWrapper
                  │  ├─ Mode-specific nodes
                  │  └─ Dynamic palette
                  ├─ LifecyclePhaseIndicator
                  ├─ Unified Right Panel
                  │  ├─ Tab: Guidance
                  │  ├─ Tab: AI Suggestions
                  │  ├─ Tab: Validation
                  │  └─ Tab: Generation
                  └─ ProgressWidget (NEW)
```

---

## Appendix B: Canvas Mode Details

### Brainstorm Mode
- **Purpose:** Free-form ideation, mind mapping
- **Nodes:** Sticky, Sketch, Connection, FreeText
- **Tools:** Freehand draw, shapes, text, AI suggest
- **Palette:** Colors, shapes, sticky templates

### Diagram Mode (Default)
- **Purpose:** Architecture, flow diagrams
- **Nodes:** Component, API, Data, Flow, Page
- **Tools:** Auto-layout, snap-to-grid, grouping
- **Palette:** Architecture components

### Design Mode
- **Purpose:** UI/component design
- **Nodes:** UIComponent, Layout, Style, Page
- **Tools:** Property panel, style editor, responsive preview
- **Palette:** UI component library

### Code Mode
- **Purpose:** Code viewing/editing
- **Nodes:** File, Function, Class, Module, Import
- **Tools:** Monaco editor, AI completion, refactor
- **Palette:** Code templates, snippets

### Test Mode
- **Purpose:** Test case management and execution
- **Nodes:** TestSuite, TestCase, Assertion, Mock
- **Tools:** Run tests, coverage view, debug
- **Palette:** Test templates, assertions

### Deploy Mode
- **Purpose:** Deployment pipeline management
- **Nodes:** Environment, Service, Config, Pipeline
- **Tools:** Pipeline builder, log viewer, rollback
- **Palette:** Infrastructure components

### Observe Mode
- **Purpose:** Monitoring and observability
- **Nodes:** Metric, Log, Trace, Alert, Dashboard
- **Tools:** Dashboard builder, alert config
- **Palette:** Monitoring widgets

---

*This document serves as the foundation for the UX simplification initiative.*
*Version 2.0 - Updated with Workspace hierarchy and Canvas Modes*
               ├─ VersionHistoryPanel (Drawer)
               └─ CommandPalette (Modal)
```

---

*This document serves as the foundation for the UX simplification initiative.*
