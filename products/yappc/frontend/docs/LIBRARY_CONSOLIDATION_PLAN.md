# Frontend Library Consolidation Plan

## Overview
This document outlines the plan to consolidate the 35 frontend libraries into ~20 libraries by removing duplication and merging overlapping functionality.

## Current State
- **Total Libraries:** 35
- **Target Libraries:** ~20 (43% reduction)
- **Primary Duplication:** Canvas and IDE libraries have overlapping panel, editor, and toolbar components

## Consolidation Strategy

### Phase 1: Canvas + IDE Merge (Immediate)
**Merge the `ide` library into `canvas`**

**Rationale:**
- Canvas library already contains 84 components including editors and panels
- IDE library has 36 components with overlapping functionality
- Both serve the "development environment" use case
- Single library reduces maintenance and cognitive load

**Components to Migrate from IDE to Canvas:**

| IDE Component | Canvas Target | Action |
|--------------|--------------|--------|
| EditorPanel | CanvasEditorPanel | Migrate with rename |
| FileExplorer | CanvasFileExplorer | Migrate with rename |
| IDEFileTree | FileTree | Merge with existing |
| ContextMenu | CanvasContextMenu | Migrate |
| TabBar | CanvasTabBar | Migrate |
| AdvancedSearchPanel | SearchPanel | Merge functionality |
| BulkOperationsToolbar | OperationsToolbar | Migrate |
| CursorOverlay | CollaborationCursor | Merge with collab |
| RealTimeCursorTracking | RealTimeCursors | Merge with collab |
| KeyboardShortcutsManager | ShortcutsManager | Migrate |
| CodeGeneration | CodeGenPanel | Merge with existing |
| CodeCompletion | AutoComplete | Merge with existing |
| LoadingStates | LoadingOverlay | Use existing canvas |
| IDEShell | CanvasChromeLayout | Merge - IDE shell features into canvas layout |
| ProfessionalIDELayout | UnifiedCanvasApp | Merge - layout patterns into unified app |

### Phase 2: Shared Component Extraction (Short-term)
**Extract common UI primitives to `ui` library**

**Components to Move:**
- Panels (PropertyPanels, OutlinePanel, MinimapPanel)
- Toolbars (GroupingToolbar, TestGenToolbar, OperationsToolbar)
- Menus (ContextMenu, Menu components)
- Layout primitives (SplitPanel, ResizablePanel)
- Loading states
- Dialogs and modals

### Phase 3: Library Renaming (Medium-term)
**Clear naming convention:**
- `@ghatana/canvas` → Core canvas functionality
- `@ghatana/ui` → Design system and shared components
- `@ghatana/ide` → DEPRECATED (merged into canvas)

## Implementation Steps

### Step 1: Create Migration Compatibility Layer
- Export IDE components from canvas library
- Maintain backward compatibility during transition
- Deprecation warnings for old imports

### Step 2: Update Import Paths
- Replace `@ghatana/ide` imports with `@ghatana/canvas`
- Use codemod scripts for bulk updates

### Step 3: Merge Component Logic
- Consolidate duplicate implementations
- Keep best features from both libraries
- Unified API design

### Step 4: Remove IDE Library
- Remove from workspace configuration
- Archive source code
- Update documentation

## Migration Timeline

**Week 1-2:** Set up compatibility exports and deprecation warnings
**Week 3-4:** Migrate imports and test thoroughly
**Week 5-6:** Merge component implementations
**Week 7-8:** Remove IDE library and finalize

## Risk Mitigation

### Risks:
1. Breaking changes for consumers
2. Loss of IDE-specific features
3. Bundle size increase

### Mitigations:
1. Gradual deprecation with compatibility layer
2. Feature audit before merging
3. Tree-shaking verification

## Success Metrics
- Library count reduced from 35 to ~20
- Zero breaking changes for consumers
- Bundle size maintained or reduced
- Simplified mental model for developers

## Post-Migration
- Single source of truth for canvas/IDE functionality
- Reduced maintenance overhead
- Clearer component boundaries
- Better developer experience
