# Expert UX Analysis & Implementation Plan

**Date:** 2026-01-05  
**Reviewer:** UX Expert Analysis  
**Goal:** Powerful, expressive UI with dead-simple UX while preserving all capabilities

---

## Executive Summary

The YAPPC app-creator has **solid fundamentals** with an AI-first design philosophy. However, the current implementation suffers from **visual density overload** and **cognitive fragmentation**. Users face too many simultaneously visible controls that compete for attention.

### The Core Tension

| What's Good | What Hurts |
|-------------|------------|
| ✅ AI-first philosophy | ❌ Canvas has 12+ floating controls |
| ✅ 7-phase lifecycle is clear | ❌ Phase rail shown twice (shell + project shell) |
| ✅ Workspace → Project hierarchy | ❌ Sidebar is always 224px, even when not needed |
| ✅ Powerful canvas modes (7) | ❌ Mode selector at top-left competes with sketch toolbar |
| ✅ Comprehensive task panel | ❌ Task panel on left + drawers on right = tunnel vision |
| ✅ Unified right panel | ❌ Still 4+ separate drawer triggers visible |

**Bottom Line:** The UI has accumulated features organically without a unifying visual hierarchy. Time to consolidate.

---

## Part 1: Current State Analysis

### 1.1 Route Structure ✅ GOOD

```
/app                           → AI Command Center (clean, focused)
  └── /app/projects            → Project grid (well designed)
  └── /app/p/:projectId        → Project shell
        └── /canvas            → Main canvas (too busy)
        └── /preview           → Preview (simple)
        └── /deploy            → Deploy (simple)
        └── /settings          → Settings (simple)
```

**Verdict:** Routes are well-organized. No changes needed.

---

### 1.2 Navigation: Multiple Redundant Rails

**Problem:** Users see lifecycle phases in 3 places simultaneously:

1. **App Shell** (`_shell.tsx`): `UnifiedPhaseRail` in content area when in project
2. **Project Shell** (`project/_shell.tsx`): `LifecyclePhaseNavigator` below tabs
3. **Canvas** (`CanvasScene.tsx`): `LifecyclePhaseIndicator` top-right + `CanvasProgressWidget` bottom-left

**Result:** Visual clutter. Users don't know which rail is the "real" one.

**Recommendation:** Single source of truth for phase navigation.

---

### 1.3 Canvas: Control Overload

Current canvas has **18+ visible control groups** simultaneously:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ [SketchToolbar] [CanvasModeSelector] [AbstractionNav] [AutoSaveIndicator]  │ TOP-LEFT
│                                      [TechStackPill]  [CodeGenBadge]       │ TOP-RIGHT
│                                                       [ValidationBadge]    │
│                                                       [AIBadge]            │
│                                                       [UnifiedPanel ⚡]    │
│                                                       [Guidance ?]         │
│                                                       [LifecycleIndicator] │
│ [HistoryToolbar]                                                           │ PANEL TOP-LEFT
│                                                                            │
│ [TaskPanel]            ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░          [MiniMap]  │
│ (280px)                ░░░░░░ ACTUAL CANVAS ░░░░░░░░░░                     │
│                        ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░                     │
│                                                                            │
│                                                                            │
│ [ProgressWidget]                                         [ReactFlow Ctrls] │ BOTTOM
└────────────────────────────────────────────────────────────────────────────┘

Plus: [CommandPalette], [FloatingHelpButton], 5+ Drawer triggers
```

**This is overwhelming.** Most users will never need all these controls at once.

---

### 1.4 Sidebar: Always Visible, Rarely Needed

The 224px sidebar is **always visible**, even when:
- Working on canvas (need maximum space)
- In preview mode
- In deploy flow

**Result:** Wasted horizontal space. Canvas feels cramped.

---

### 1.5 Actions: Well-Designed But Scattered

| Action | Location | Trigger | Notes |
|--------|----------|---------|-------|
| Create Project | App index, Sidebar, Projects page | Button + AI | ✅ Good |
| Switch Workspace | Sidebar dropdown | Click | ✅ Good |
| Canvas Mode | Top-left floating | Click/Keyboard 1-7 | ✅ Good |
| Phase Transition | 3+ places | Click | ❌ Confusing |
| AI Suggestions | Badge + Drawer | Click | ✅ OK |
| Validation | Badge + Drawer | Click | ✅ OK |
| Code Gen | Badge + Drawer | Click | ⚠️ Buried |
| Command Palette | Cmd+K | Keyboard | ✅ Excellent |
| Save | Auto + Cmd+S | Keyboard | ✅ Good |

**Key Insight:** Command Palette (Cmd+K) is underleveraged. It should be the primary action hub.

---

### 1.6 Styles: Consistent But Needs Polish

**What's Working:**
- Tailwind CSS with custom theme
- Consistent color palette (primary-500/600/700)
- Dark mode support
- Semantic tokens (bg-paper, text-primary, divider)

**What Needs Work:**
- Mixed styling patterns (Tailwind + MUI sx prop)
- Inconsistent spacing on canvas (24px, 16px, 100px, 180px, 260px, 280px)
- Floating elements at hardcoded positions
- No clear visual hierarchy

---

## Part 2: Design Principles for the Fix

### Principle 1: Progressive Disclosure
Show only what's needed. Advanced features emerge on demand.

### Principle 2: One Truth, Multiple Views
Phase status shown once authoritatively, referenced elsewhere visually.

### Principle 3: Collapsible Chrome
Maximize canvas space. All panels collapse to minimal states.

### Principle 4: Command-First
Power users use Cmd+K. Mouse users get clean, focused UI.

### Principle 5: Contextual Intelligence
Show controls relevant to current mode and phase only.

---

## Part 3: Implementation Plan

### Phase 1: Collapsible Sidebar (Day 1)

**Goal:** Reclaim horizontal space when working in canvas.

**Current:** 224px always visible  
**New:** 60px collapsed / 224px expanded, with smart auto-collapse

**Implementation:**

```tsx
// _shell.tsx - Add sidebar collapse state
const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

// Auto-collapse when entering canvas
useEffect(() => {
  if (location.pathname.includes('/canvas')) {
    setSidebarCollapsed(true);
  }
}, [location.pathname]);
```

**Collapsed State UI:**
```
┌────────────────┐
│ [Logo]         │
│ [Workspace ▼]  │  ← Collapse to icon
│ [+]            │  ← New project icon
│ ──────────     │
│ [📁] [📁] [📁] │  ← Project icons only
│ [📁] [📁]     │
│                │
│ [🏠] [?] [⚙️]   │  ← Icon nav
└────────────────┘
```

**Files to Modify:**
- [routes/app/_shell.tsx](routes/app/_shell.tsx)

---

### Phase 2: Unified Toolbar System (Day 1-2)

**Goal:** Replace scattered floating controls with unified top toolbar.

**Current:** 12+ floating elements at various positions  
**New:** Single unified toolbar with contextual sections

**New Canvas Layout:**

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [☰] [Undo][Redo] │ [Mode: Diagram ▼] │ [Level: Component ▼] │ [✓ 87/100] [⚡] [?] │
├────────┬─────────────────────────────────────────────────────────────────┤
│        │                                                                 │
│ TASKS  │                         CANVAS                                  │
│ (auto) │                                                                 │
│        │                                                                 │
│        │                                                                 │
│        │                                                                 │
├────────┴─────────────────────────────────────────────────────────────────┤
│ Phase: [●][●][●][○][○][○][○] VALIDATE                    [Tech: React] │
└──────────────────────────────────────────────────────────────────────────┘
```

**Key Changes:**

1. **Single Top Toolbar** - All controls in one horizontal bar
2. **Dropdowns** - Mode selector and abstraction as dropdowns (not pills)
3. **Status Bar Bottom** - Phase progress + tech stack at bottom
4. **Task Panel Auto-Width** - Expands on hover, collapses when working

**Implementation:**

```tsx
// New: components/canvas/CanvasToolbar.tsx
export function CanvasToolbar({
  mode, onModeChange,
  abstractionLevel, onLevelChange,
  validationScore,
  onOpenPanel,
}: CanvasToolbarProps) {
  return (
    <div className="h-12 px-4 flex items-center gap-4 border-b border-divider bg-bg-paper">
      {/* Left: History */}
      <div className="flex items-center gap-1">
        <IconButton size="sm"><Undo /></IconButton>
        <IconButton size="sm"><Redo /></IconButton>
        <div className="w-px h-6 bg-divider mx-2" />
      </div>

      {/* Center: Mode + Level */}
      <div className="flex items-center gap-2">
        <ModeDropdown value={mode} onChange={onModeChange} />
        <LevelDropdown value={abstractionLevel} onChange={onLevelChange} />
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Right: Status + Actions */}
      <div className="flex items-center gap-2">
        <ValidationBadge score={validationScore} onClick={() => onOpenPanel('validation')} />
        <AIBadge count={0} onClick={() => onOpenPanel('ai')} />
        <IconButton size="sm" onClick={() => onOpenPanel('unified')}>
          <AutoAwesome />
        </IconButton>
      </div>
    </div>
  );
}
```

**Files to Create:**
- `src/components/canvas/toolbar/CanvasToolbar.tsx`
- `src/components/canvas/toolbar/ModeDropdown.tsx`
- `src/components/canvas/toolbar/LevelDropdown.tsx`

**Files to Modify:**
- [routes/app/project/canvas/CanvasScene.tsx](routes/app/project/canvas/CanvasScene.tsx)

---

### Phase 3: Smart Task Panel (Day 2)

**Goal:** Task panel that enhances rather than reduces canvas space.

**Current:** Fixed 280px always visible  
**New:** 48px collapsed / 280px expanded, expands on hover or click

**Collapsed State:**
```
│ ▶ │  ← Hover to expand
│ 3 │  ← Task count badge
│   │
│   │
```

**Implementation:**

```tsx
// CanvasTaskPanel.tsx - Add smart expand behavior
const [isHovered, setIsHovered] = useState(false);
const [isPinned, setIsPinned] = useState(false);

const isExpanded = isPinned || isHovered;

return (
  <div
    className={`transition-all duration-200 ${
      isExpanded ? 'w-72' : 'w-12'
    }`}
    onMouseEnter={() => setIsHovered(true)}
    onMouseLeave={() => setIsHovered(false)}
  >
    {isExpanded ? (
      <ExpandedTaskPanel tasks={tasks} onPin={() => setIsPinned(!isPinned)} />
    ) : (
      <CollapsedTaskPanel taskCount={tasks.length} />
    )}
  </div>
);
```

**Files to Modify:**
- [src/components/canvas/tasks/CanvasTaskPanel.tsx](src/components/canvas/tasks/CanvasTaskPanel.tsx)

---

### Phase 4: Bottom Status Bar (Day 2)

**Goal:** Move phase progress and tech stack to unobtrusive bottom bar.

**Current:** CanvasProgressWidget at bottom-left floating  
**New:** Integrated status bar

**Layout:**
```
┌───────────────────────────────────────────────────────────────────────┐
│ ● Intent │ ● Shape │ ◉ Validate │ ○ Generate │ ○ Run │ ○ Observe │ ○ │
│                                              [React] [Node.js] [Prisma]│
└───────────────────────────────────────────────────────────────────────┘
```

**Implementation:**

```tsx
// New: components/canvas/CanvasStatusBar.tsx
export function CanvasStatusBar({
  phases,
  currentPhase,
  technologies,
  onPhaseClick,
}: CanvasStatusBarProps) {
  return (
    <div className="h-10 px-4 flex items-center justify-between border-t border-divider bg-bg-paper">
      {/* Left: Phase dots */}
      <div className="flex items-center gap-2">
        {phases.map(phase => (
          <Tooltip key={phase.phase} title={`${phase.phase}: ${phase.progress}%`}>
            <button
              onClick={() => onPhaseClick(phase.phase)}
              className={`w-2.5 h-2.5 rounded-full transition-all ${
                phase.phase === currentPhase
                  ? 'w-4 h-4 bg-primary-600'
                  : phase.status === 'completed'
                  ? 'bg-green-500'
                  : 'bg-grey-300'
              }`}
            />
          </Tooltip>
        ))}
        <span className="ml-2 text-sm font-medium text-text-primary">
          {PHASE_LABELS[currentPhase]}
        </span>
      </div>

      {/* Right: Tech Stack */}
      <div className="flex items-center gap-1">
        {technologies.slice(0, 4).map(tech => (
          <span
            key={tech.id}
            className="px-2 py-0.5 text-xs bg-grey-100 dark:bg-grey-800 rounded"
          >
            {tech.name}
          </span>
        ))}
        {technologies.length > 4 && (
          <span className="text-xs text-text-secondary">+{technologies.length - 4}</span>
        )}
      </div>
    </div>
  );
}
```

**Files to Create:**
- `src/components/canvas/CanvasStatusBar.tsx`

---

### Phase 5: Remove Duplicate Phase Rails (Day 3)

**Goal:** Single authoritative phase display.

**Current:**
- `_shell.tsx` shows `UnifiedPhaseRail` when in project
- `project/_shell.tsx` shows `LifecyclePhaseNavigator`
- Canvas shows `LifecyclePhaseIndicator` + `CanvasProgressWidget`

**New:**
- Remove from `_shell.tsx` (app shell shouldn't know about lifecycle)
- Keep in `project/_shell.tsx` as main interactive rail
- Canvas shows compact `CanvasStatusBar` only (non-interactive dots)

**Implementation:**

```diff
// routes/app/_shell.tsx
- {/* Phase Rail - Show when in a project */}
- {projectId && (
-   <div className="border-b border-divider bg-bg-paper px-4 py-2">
-     <UnifiedPhaseRail
-       orientation="horizontal"
-       variant="compact"
-     />
-   </div>
- )}
```

**Files to Modify:**
- [routes/app/_shell.tsx](routes/app/_shell.tsx) - Remove phase rail
- [routes/app/project/_shell.tsx](routes/app/project/_shell.tsx) - Keep as-is
- [routes/app/project/canvas/CanvasScene.tsx](routes/app/project/canvas/CanvasScene.tsx) - Replace floating indicators with status bar

---

### Phase 6: Enhance Command Palette (Day 3)

**Goal:** Make Cmd+K the power-user hub for all actions.

**Current Commands:**
- Accessibility check
- Fit view

**Add Commands:**

```typescript
// Expand command palette with full action catalog
const CANVAS_COMMANDS: CommandAction[] = [
  // Navigation
  { id: 'go-home', label: 'Go to Home', category: 'Navigation', shortcut: 'G H' },
  { id: 'go-projects', label: 'Go to Projects', category: 'Navigation', shortcut: 'G P' },
  { id: 'go-settings', label: 'Go to Settings', category: 'Navigation', shortcut: 'G S' },

  // Canvas Modes
  { id: 'mode-brainstorm', label: 'Switch to Brainstorm Mode', category: 'Mode', shortcut: '1' },
  { id: 'mode-diagram', label: 'Switch to Diagram Mode', category: 'Mode', shortcut: '2' },
  { id: 'mode-design', label: 'Switch to Design Mode', category: 'Mode', shortcut: '3' },
  { id: 'mode-code', label: 'Switch to Code Mode', category: 'Mode', shortcut: '4' },
  { id: 'mode-test', label: 'Switch to Test Mode', category: 'Mode', shortcut: '5' },
  { id: 'mode-deploy', label: 'Switch to Deploy Mode', category: 'Mode', shortcut: '6' },
  { id: 'mode-observe', label: 'Switch to Observe Mode', category: 'Mode', shortcut: '7' },

  // Abstraction
  { id: 'level-system', label: 'Zoom to System Level', category: 'Abstraction' },
  { id: 'level-component', label: 'Zoom to Component Level', category: 'Abstraction' },
  { id: 'level-file', label: 'Zoom to File Level', category: 'Abstraction' },
  { id: 'level-code', label: 'Zoom to Code Level', category: 'Abstraction' },

  // Panels
  { id: 'toggle-tasks', label: 'Toggle Task Panel', category: 'View', shortcut: 'Cmd+B' },
  { id: 'toggle-ai', label: 'Toggle AI Assistant', category: 'View', shortcut: 'Cmd+I' },
  { id: 'toggle-validation', label: 'Toggle Validation Panel', category: 'View' },
  { id: 'toggle-generation', label: 'Toggle Code Generation', category: 'View' },

  // Actions
  { id: 'validate', label: 'Run Validation', category: 'Action', shortcut: 'Cmd+Shift+V' },
  { id: 'generate', label: 'Generate Code', category: 'Action', shortcut: 'Cmd+Shift+G' },
  { id: 'export-zip', label: 'Export as ZIP', category: 'Action' },
  { id: 'create-snapshot', label: 'Create Snapshot', category: 'Action', shortcut: 'Cmd+Shift+S' },

  // Phase
  { id: 'phase-next', label: 'Advance to Next Phase', category: 'Phase' },
  { id: 'phase-prev', label: 'Go to Previous Phase', category: 'Phase' },
];
```

**Files to Modify:**
- [components/canvas/tools/CommandPalette.tsx](components/canvas/tools/CommandPalette.tsx)
- [routes/app/project/canvas/CanvasScene.tsx](routes/app/project/canvas/CanvasScene.tsx)

---

### Phase 7: Visual Consistency Pass (Day 4)

**Goal:** Unified visual language across all components.

**Standardize Spacing:**

```typescript
// New: design-tokens.ts
export const SPACING = {
  toolbar: 'h-12',        // All toolbars 48px
  sidebar: {
    expanded: 'w-56',     // 224px
    collapsed: 'w-14',    // 56px
  },
  panel: 'w-80',          // 320px for all panels
  statusBar: 'h-10',      // 40px
  gap: {
    xs: 'gap-1',          // 4px
    sm: 'gap-2',          // 8px
    md: 'gap-4',          // 16px
    lg: 'gap-6',          // 24px
  },
};
```

**Standardize Control Heights:**

| Element | Height | Class |
|---------|--------|-------|
| Button Small | 28px | `h-7` |
| Button Default | 36px | `h-9` |
| Button Large | 44px | `h-11` |
| Input | 36px | `h-9` |
| Dropdown | 36px | `h-9` |
| Badge | 24px | `h-6` |

**Standardize Border Radius:**

| Element | Radius | Class |
|---------|--------|-------|
| Button | 8px | `rounded-lg` |
| Card | 12px | `rounded-xl` |
| Badge | 9999px | `rounded-full` |
| Input | 8px | `rounded-lg` |
| Panel | 0 | `rounded-none` |

---

### Phase 8: Remove Phase Rail from App Shell (Day 4)

**Rationale:** The app shell (`_shell.tsx`) currently shows `UnifiedPhaseRail` when in a project. But the project shell (`project/_shell.tsx`) ALSO shows `LifecyclePhaseNavigator`. This is redundant.

**Fix:** Remove from app shell. Project shell is the right place.

```diff
// routes/app/_shell.tsx - Line 296-302
- {/* Phase Rail - Show when in a project */}
- {projectId && (
-   <div className="border-b border-divider bg-bg-paper px-4 py-2">
-     <UnifiedPhaseRail
-       orientation="horizontal"
-       variant="compact"
-     />
-   </div>
- )}
```

---

## Part 4: Migration Safety

### Capabilities Preserved

| Feature | Current Location | New Location | Risk |
|---------|------------------|--------------|------|
| Workspace switching | Sidebar | Sidebar (collapsed: icon) | ✅ None |
| Project creation | Sidebar + AI | Sidebar + AI + Cmd+K | ✅ None |
| Canvas modes | Floating pills | Toolbar dropdown | ✅ None |
| Abstraction levels | Floating breadcrumb | Toolbar dropdown | ✅ None |
| Task management | Left panel | Left panel (smart) | ✅ None |
| AI suggestions | Badge + drawer | Badge + drawer | ✅ None |
| Validation | Badge + drawer | Badge + drawer | ✅ None |
| Code generation | Badge + drawer | Badge + drawer | ✅ None |
| Version history | Drawer | Drawer | ✅ None |
| Phase transitions | 3 places | 1 place (project shell) | ✅ Cleaner |
| Tech stack display | Floating pill | Status bar | ✅ None |
| Progress widget | Floating bottom-left | Status bar | ✅ None |
| Keyboard shortcuts | 1-7 for modes | 1-7 + more in Cmd+K | ✅ Enhanced |
| Command palette | Cmd+K | Cmd+K (enhanced) | ✅ Enhanced |

**No capabilities lost. Several enhanced.**

---

## Part 5: Implementation Timeline

| Day | Phase | Effort | Impact |
|-----|-------|--------|--------|
| 1 | Collapsible Sidebar | 4h | High - Canvas space |
| 1-2 | Unified Toolbar | 6h | High - Visual clarity |
| 2 | Smart Task Panel | 3h | Medium - Space saving |
| 2 | Bottom Status Bar | 3h | Medium - Cleaner layout |
| 3 | Remove Duplicate Rails | 2h | High - Reduce confusion |
| 3 | Enhance Command Palette | 4h | Medium - Power users |
| 4 | Visual Consistency | 4h | Medium - Polish |
| 4 | Testing & Polish | 4h | Required |

**Total: ~30 hours (4 days)**

---

## Part 6: Visual Mockups

### Before: Current Canvas

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [Logo YAPPC]                                                                  │
│ [Workspace ▼]                                                                 │
│ [+ New Project]                                                               │
│ ─────────────                  ┌──────────────────────────────────────────┐   │
│ PROJECTS                       │ ← [Back] Project Name            [Ready]│   │
│ • Project A                    ├──────────────────────────────────────────┤   │
│ • Project B                    │ [Build] [Preview] [Deploy] [Settings]    │   │
│ • Project C                    ├──────────────────────────────────────────┤   │
│                                │ [INTENT→SHAPE→VALIDATE→GENERATE→RUN→...] │   │
│                                ├──────────────────────────────────────────┤   │
│ ─────────────                  │ [Phase Rail Again!]                      │   │
│ [Home]                         ├─────────┬──────────────────────┬────────┤   │
│ [Guidance]                     │ TASKS   │ [Sketch][Mode][Level]│[Badges]│   │
│ [Settings]                     │ ─────── │ [History]            │        │   │
│                                │ Task 1  │                      │        │   │
│                                │ Task 2  │      CANVAS          │        │   │
│                                │ Task 3  │                      │        │   │
│                                │         │                      │        │   │
│                                │         │ [ProgressWidget]     │[Ctrls] │   │
└────────────────────────────────┴─────────┴──────────────────────┴────────┘   │
```

### After: Proposed Canvas

```
┌─────┬─────────────────────────────────────────────────────────────────────────┐
│ [≡] │ ← [Back] Project Name │ [Build][Preview][Deploy] │                      │
│  ★  ├───────────────────────┴────────────────────────────────────────────────┤
│ [+] │ [↶][↷] │ [Mode: Diagram ▼] [Level: Component ▼] │    [87%] [AI] [⚡?]   │
│─────┼─────┬──────────────────────────────────────────────────────────────────┤
│ 📁  │ ▶ 3 │                                                                   │
│ 📁  │     │                                                                   │
│ 📁  │     │                         CANVAS                                    │
│     │     │                    (Maximum Space)                                │
│     │     │                                                                   │
│     │     │                                                                   │
│─────│     ├──────────────────────────────────────────────────────────────────┤
│ 🏠  │     │ ●●●◉○○○ VALIDATE                          [React][Node][Prisma]  │
│ ⚙️   │     │                                                                   │
└─────┴─────┴──────────────────────────────────────────────────────────────────┘
```

**Key Improvements:**
1. Sidebar collapsed to 60px icons
2. Single unified toolbar
3. Task panel minimized to icon + count
4. No floating controls cluttering canvas
5. Phase progress in status bar (unobtrusive)
6. Tech stack in status bar
7. Maximum canvas real estate

---

## Part 7: Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Visible controls in canvas | 18+ | ≤8 |
| Sidebar width (canvas mode) | 224px | 60px |
| Canvas usable area | ~70% | ~90% |
| Time to find mode switch | 3+ sec | <1 sec |
| Phase rails visible | 3 | 1 |
| Cmd+K command coverage | 10% | 80% |

---

## Part 8: Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Users miss collapsed controls | Medium | Tooltips + onboarding tour |
| Power users dislike change | Medium | Keep all keyboard shortcuts |
| Visual regression | Low | Component-level snapshots |
| Performance impact | Low | Measure FPS before/after |

---

## Appendix: File Change Summary

### Files to Create

1. `src/components/canvas/toolbar/CanvasToolbar.tsx`
2. `src/components/canvas/toolbar/ModeDropdown.tsx`
3. `src/components/canvas/toolbar/LevelDropdown.tsx`
4. `src/components/canvas/CanvasStatusBar.tsx`
5. `src/styles/design-tokens.ts`

### Files to Modify

1. `src/routes/app/_shell.tsx` - Collapsible sidebar, remove phase rail
2. `src/routes/app/project/canvas/CanvasScene.tsx` - Replace floating controls with toolbar/status bar
3. `src/components/canvas/tasks/CanvasTaskPanel.tsx` - Smart expand/collapse
4. `src/components/canvas/tools/CommandPalette.tsx` - Enhanced commands

### Files to Delete (Optional)

None - all current components remain available for alternate views.

---

**This plan delivers a modern, powerful, and dead-simple UX while preserving 100% of current capabilities.**
