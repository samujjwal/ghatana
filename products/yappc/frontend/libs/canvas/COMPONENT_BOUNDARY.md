# Canvas Component Boundary Rules

This document defines the clear separation between canvas **library code** (`libs/canvas`) and **application-specific code** (`apps/web/components/canvas`).

## Guiding Principle

> **Library code is reusable and framework-agnostic. Application code is specific to YAPPC web app.**

---

## What Belongs in `libs/canvas/` (Library)

### ✅ Core Canvas Engine
- **ReactFlow integration** — Node/edge types, custom nodes, layout algorithms
- **Canvas state management** — Jotai atoms for nodes, edges, viewport
- **Canvas utilities** — Coordinate transforms, bounds calculation, collision detection
- **Canvas hooks** — `useCanvas`, `useCanvasNodes`, `useCanvasEdges`, `useCanvasViewport`
- **Canvas types** — TypeScript interfaces for nodes, edges, canvas state
- **Canvas components** — Reusable UI components (Minimap, Controls, Background)

### ✅ Generic Features
- **Drawing tools** — Pen, shapes, text, sticky notes
- **Selection** — Multi-select, marquee selection, keyboard selection
- **Gestures** — Pan, zoom, pinch, rotate
- **Undo/redo** — History management
- **Keyboard shortcuts** — Generic canvas shortcuts (Ctrl+Z, Delete, etc.)
- **Accessibility** — ARIA labels, keyboard navigation, screen reader support

### ✅ Reusable Patterns
- **Node factories** — Generic node creation patterns
- **Edge factories** — Generic edge creation patterns
- **Layout algorithms** — Auto-layout, dagre, force-directed
- **Export/import** — JSON serialization, image export

### Examples
```
libs/canvas/src/
├── components/
│   ├── Minimap.tsx              ✅ Generic minimap
│   ├── Controls.tsx             ✅ Generic zoom controls
│   ├── Background.tsx           ✅ Generic background pattern
│   └── SelectionBox.tsx         ✅ Generic selection UI
├── hooks/
│   ├── useCanvas.ts             ✅ Core canvas hook
│   ├── useCanvasGestures.ts    ✅ Generic gesture handling
│   └── useCanvasHistory.ts     ✅ Generic undo/redo
├── nodes/
│   ├── BaseNode.tsx             ✅ Generic node base class
│   ├── StickyNoteNode.tsx      ✅ Generic sticky note
│   └── TextNode.tsx             ✅ Generic text node
└── utils/
    ├── layout.ts                ✅ Generic layout algorithms
    └── export.ts                ✅ Generic export utilities
```

---

## What Belongs in `apps/web/components/canvas/` (Application)

### ✅ YAPPC-Specific Features
- **YAPPC node types** — Project nodes, requirement nodes, workflow nodes
- **YAPPC integrations** — API calls, GraphQL queries, WebSocket updates
- **YAPPC business logic** — Approval workflows, AI suggestions, lifecycle phases
- **YAPPC UI customization** — Branding, theme, custom toolbars
- **YAPPC shortcuts** — App-specific keyboard shortcuts
- **YAPPC context menus** — App-specific actions (approve, reject, assign)

### ✅ Application State
- **Project context** — Current project, workspace, user
- **API integration** — Fetching/saving canvas data from backend
- **Real-time collaboration** — WebSocket sync, presence, cursors
- **Feature flags** — App-specific feature toggles

### ✅ YAPPC-Specific Components
- **Custom toolbars** — YAPPC-specific tools (AI assistant, approval panel)
- **Custom panels** — Properties panel, layers panel, outline panel
- **Custom modals** — Share dialog, export dialog, settings dialog
- **Custom overlays** — Onboarding tooltips, help overlays

### Examples
```
apps/web/components/canvas/
├── nodes/
│   ├── RequirementNode.tsx      ✅ YAPPC requirement node
│   ├── WorkflowNode.tsx         ✅ YAPPC workflow node
│   └── ApprovalNode.tsx         ✅ YAPPC approval node
├── panels/
│   ├── PropertiesPanel.tsx      ✅ YAPPC properties panel
│   ├── LayersPanel.tsx          ✅ YAPPC layers panel
│   └── AIAssistantPanel.tsx     ✅ YAPPC AI panel
├── toolbars/
│   ├── CanvasToolbar.tsx        ✅ YAPPC-specific toolbar
│   └── ApprovalToolbar.tsx      ✅ YAPPC approval toolbar
└── integration/
    ├── useCanvasSync.ts         ✅ YAPPC API sync
    └── useCanvasCollaboration.ts ✅ YAPPC WebSocket sync
```

---

## Decision Tree

When creating a new canvas component, ask:

### 1. Is it reusable across products?
- **Yes** → `libs/canvas/`
- **No** → `apps/web/components/canvas/`

### 2. Does it depend on YAPPC APIs or business logic?
- **Yes** → `apps/web/components/canvas/`
- **No** → `libs/canvas/`

### 3. Does it use YAPPC-specific types (Requirement, Workflow, Project)?
- **Yes** → `apps/web/components/canvas/`
- **No** → `libs/canvas/`

### 4. Could another product (e.g., FlashIt, TutorPutor) use this?
- **Yes** → `libs/canvas/`
- **No** → `apps/web/components/canvas/`

---

## Common Scenarios

### Scenario 1: Adding a New Node Type

**Question:** Where do I add a "Task" node?

**Answer:**
- **Generic task node** (just a box with text) → `libs/canvas/nodes/TaskNode.tsx`
- **YAPPC task node** (with status, assignee, approval) → `apps/web/components/canvas/nodes/TaskNode.tsx`

### Scenario 2: Adding a Toolbar

**Question:** Where do I add a toolbar with drawing tools?

**Answer:**
- **Generic drawing toolbar** (pen, shapes, text) → `libs/canvas/components/DrawingToolbar.tsx`
- **YAPPC toolbar** (with AI assistant, approval actions) → `apps/web/components/canvas/toolbars/CanvasToolbar.tsx`

### Scenario 3: Adding a Hook

**Question:** Where do I add a hook for saving canvas data?

**Answer:**
- **Generic save hook** (saves to localStorage/IndexedDB) → `libs/canvas/hooks/useCanvasPersistence.ts`
- **YAPPC save hook** (saves to backend API) → `apps/web/components/canvas/integration/useCanvasSave.ts`

### Scenario 4: Adding a Keyboard Shortcut

**Question:** Where do I add Ctrl+S to save?

**Answer:**
- **Generic shortcuts** (Ctrl+Z, Delete, Ctrl+A) → `libs/canvas/hooks/useCanvasShortcuts.ts`
- **YAPPC shortcuts** (Ctrl+S to save to API) → `apps/web/components/canvas/useYappcShortcuts.ts`

---

## Migration Guidelines

### Moving Code from App to Library

When you find app code that should be in the library:

1. **Extract the component** to `libs/canvas/`
2. **Remove YAPPC-specific logic** (API calls, business rules)
3. **Make it configurable** via props or hooks
4. **Update app code** to use the library component
5. **Add tests** to the library

**Example:**
```tsx
// Before (in apps/web/components/canvas/Minimap.tsx)
function Minimap() {
  const project = useProject(); // YAPPC-specific
  return <div>Minimap for {project.name}</div>;
}

// After (in libs/canvas/components/Minimap.tsx)
function Minimap({ title }: { title?: string }) {
  return <div>Minimap {title && `for ${title}`}</div>;
}

// App usage (in apps/web/components/canvas/YappcCanvas.tsx)
function YappcCanvas() {
  const project = useProject();
  return <Minimap title={project.name} />;
}
```

### Moving Code from Library to App

When you find library code that's too YAPPC-specific:

1. **Move the component** to `apps/web/components/canvas/`
2. **Keep generic parts** in the library
3. **Update imports** in app code
4. **Remove from library exports**

---

## Anti-Patterns

### ❌ DON'T: Put YAPPC API calls in library code
```tsx
// ❌ Bad (in libs/canvas/)
function useCanvasData() {
  return useQuery('/api/canvas/123'); // YAPPC API
}
```

### ✅ DO: Accept data via props/hooks
```tsx
// ✅ Good (in libs/canvas/)
function Canvas({ nodes, edges, onNodesChange }: CanvasProps) {
  // Generic canvas logic
}

// App provides data (in apps/web/)
function YappcCanvas() {
  const { data } = useQuery('/api/canvas/123');
  return <Canvas nodes={data.nodes} edges={data.edges} />;
}
```

### ❌ DON'T: Import YAPPC types in library
```tsx
// ❌ Bad (in libs/canvas/)
import { Requirement } from '@ghatana/yappc-types';
```

### ✅ DO: Use generic types
```tsx
// ✅ Good (in libs/canvas/)
interface CanvasNode {
  id: string;
  type: string;
  data: Record<string, unknown>;
}
```

### ❌ DON'T: Hardcode YAPPC branding in library
```tsx
// ❌ Bad (in libs/canvas/)
function Toolbar() {
  return <div className="bg-yappc-brand">YAPPC Canvas</div>;
}
```

### ✅ DO: Make it themeable
```tsx
// ✅ Good (in libs/canvas/)
function Toolbar({ title, className }: ToolbarProps) {
  return <div className={className}>{title}</div>;
}
```

---

## Testing Strategy

### Library Tests
- **Unit tests** for all utilities and hooks
- **Component tests** for UI components
- **Integration tests** for canvas engine
- **No mocks** for external APIs (library shouldn't call APIs)

### App Tests
- **Integration tests** for YAPPC features
- **E2E tests** for user workflows
- **Mock API calls** for backend integration
- **Test feature flags** and business logic

---

## Enforcement

### Code Review Checklist
- [ ] New canvas code is in the correct location
- [ ] Library code has no YAPPC-specific imports
- [ ] App code doesn't duplicate library functionality
- [ ] Components are properly typed
- [ ] Tests are added for new functionality

### Automated Checks
```bash
# Check for YAPPC imports in library
grep -r "@ghatana/yappc" libs/canvas/src/

# Check for API calls in library
grep -r "useQuery\|useMutation\|fetch" libs/canvas/src/

# Should return no results
```

---

## Summary

| Aspect | `libs/canvas/` | `apps/web/components/canvas/` |
|--------|----------------|-------------------------------|
| **Purpose** | Reusable canvas engine | YAPPC-specific features |
| **Dependencies** | React, ReactFlow, Jotai | YAPPC APIs, types, business logic |
| **Types** | Generic (CanvasNode, CanvasEdge) | YAPPC (Requirement, Workflow) |
| **API Calls** | ❌ None | ✅ Backend APIs, GraphQL |
| **Business Logic** | ❌ None | ✅ Approvals, AI, lifecycle |
| **Branding** | ❌ Generic | ✅ YAPPC theme, colors |
| **Reusability** | ✅ Other products can use | ❌ YAPPC-only |

---

## Questions?

If you're unsure where to put something, ask:
1. Could FlashIt or TutorPutor use this component as-is?
2. Does this component know about YAPPC's business domain?
3. Does this component call YAPPC APIs?

If the answer to #1 is **yes** and #2/#3 are **no**, it belongs in `libs/canvas/`.

Otherwise, it belongs in `apps/web/components/canvas/`.
