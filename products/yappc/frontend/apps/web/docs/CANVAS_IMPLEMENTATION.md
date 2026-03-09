# Canvas Implementation Guide

**Last Updated**: January 29, 2026  
**Status**: Production

---

## Overview

The YAPPC Canvas is a unified, production-ready implementation combining all features from Epics 1-10:

- Core canvas operations (drag, zoom, pan)
- Multi-mode support (diagram, sketch, workspace)
- Real-time collaboration
- AI-assisted features
- Lifecycle phase integration

## Routes

### Primary Canvas

**Route**: `/p/:projectId/canvas`  
**Component**: `apps/web/src/routes/app/project/canvas.tsx`  
**Purpose**: Main canvas interface for all project work

### Workspace Canvas

**Route**: `/p/:projectId/canvas-workspace`  
**Component**: `apps/web/src/routes/app/project/canvas-workspace.tsx`  
**Purpose**: Specialized workspace view with artifact management

## Architecture

### Component Structure

```
Canvas
├── CanvasToolbar (tools, modes, actions)
├── UnifiedLeftPanel (phases, tasks, AI)
├── ReactFlow (node rendering)
├── UnifiedRightPanel (properties, layers)
└── CanvasStatusBar (zoom, stats)
```

### State Management

- **Global State**: Jotai atoms in `libs/ui/src/state/`
- **Canvas State**: `apps/web/src/state/atoms/canvasAtoms.ts`
- **URL State**: `CanvasURLIntegration` for deep linking

### Canvas Modes

1. **Diagram Mode**: Flowcharts, architecture diagrams
2. **Sketch Mode**: Freehand drawing, annotations
3. **Workspace Mode**: Lifecycle-based artifact management

## Key Features

### Zoom & Navigation

- Zoom range: 1% to 1000%
- Progressive disclosure at different zoom levels
- Keyboard shortcuts: `Cmd +/-/0`

### Node Operations

- 7 node types: API, Data, Component, Service, Page, Frame, Group
- Drag & drop from palette
- Multi-select (Shift+Click or drag selection)
- Copy/paste, duplicate (`Cmd+D`)

### Tools

- **Selection (V)**: Default tool
- **Pen (P)**: Freehand drawing
- **Text (T)**: Text annotations
- **Rectangle (R)**: Shape creation
- **Ellipse (E)**: Circle/ellipse shapes
- **Line (L)**: Connector lines

### Alignment & Distribution

- Align: Left, Center, Right, Top, Middle, Bottom
- Distribute: Horizontal, Vertical
- Smart guides: Auto-snap to nearby elements
- Grid snapping: Optional 10px grid

### Layer Management

- Bring to front / Send to back
- Bring forward / Send backward
- Lock layers to prevent editing
- Hide/show layers

### Undo/Redo

- 50 operation history
- Keyboard shortcuts: `Cmd+Z` (undo), `Cmd+Shift+Z` (redo)
- Works across all modes

### Export/Import

- **Export**: JSON, SVG, PNG
- **Import**: JSON (restore canvas state)
- Preserves all node data, edges, and metadata

### Collaboration

- Real-time cursor sync
- Selection indicators for other users
- Optimistic updates with conflict resolution
- WebSocket-based (port 7002 Gateway)

## Component Reference

### Core Components

- `Canvas.tsx` - Main canvas orchestrator
- `CanvasToolbar.tsx` - Tool selection and actions
- `ComponentPalette.tsx` - Draggable node palette
- `NodePropertiesPanel.tsx` - Edit selected nodes

### Unified Components

- `UnifiedLeftPanel.tsx` - Phase navigation, tasks, AI
- `UnifiedRightPanel.tsx` - Properties, layers, history
- `UnifiedCanvasToolbar.tsx` - Mode switcher, zoom controls

### Specialized Components

- `MermaidDiagram.tsx` - Diagram mode rendering
- `SketchLayer.tsx` - Drawing layer
- `GhostNodes.tsx` - Phase templates
- `SmartGuides.tsx` - Alignment guides

## Testing

### E2E Tests

- `e2e/canvas-complete.spec.ts` - Comprehensive suite (479 lines)
- `e2e/canvas-collaboration-mvp.spec.ts` - Real-time features
- 12 canvas-specific test files total

### Unit Tests

- `apps/web/src/components/canvas/__tests__/` - Component tests
- `libs/canvas/src/__tests__/` - Library tests (148 files)

### Running Tests

```bash
# E2E tests
pnpm test:e2e:canvas

# Unit tests
pnpm test:canvas

# All tests
pnpm test
```

## Performance

### Targets

- 60fps at all zoom levels
- Load time: < 3s for 500 nodes
- Handle up to 1000 nodes without degradation

### Optimizations

- Virtual rendering for large canvases
- Debounced save operations (500ms)
- Memoized components
- Web Worker for heavy computations

## Keyboard Shortcuts

### Essential

- `V` - Selection tool
- `P` - Pen tool
- `T` - Text tool
- `R` - Rectangle
- `E` - Ellipse
- `L` - Line

### Actions

- `Cmd+Z` - Undo
- `Cmd+Shift+Z` - Redo
- `Cmd+A` - Select all
- `Cmd+D` - Duplicate
- `Cmd+G` - Group
- `Cmd+Shift+G` - Ungroup
- `Delete/Backspace` - Delete selection

### View

- `Cmd +` - Zoom in
- `Cmd -` - Zoom out
- `Cmd 0` - Reset zoom
- `Cmd K` - Command palette

## API Integration

### Endpoints

- `GET /api/canvas/:projectId` - Load canvas
- `POST /api/canvas/:projectId` - Save canvas
- `GET /api/canvas/:projectId/history` - Version history
- `POST /api/canvas/:projectId/export` - Export formats

### WebSocket Events

- `canvas:cursor` - Cursor position updates
- `canvas:selection` - Selection changes
- `canvas:node:add` - Node additions
- `canvas:node:update` - Node updates
- `canvas:node:delete` - Node deletions

## Troubleshooting

### Canvas Not Loading

1. Check network tab for API errors
2. Verify WebSocket connection (port 7002)
3. Clear browser cache and reload

### Performance Issues

1. Check node count (> 1000 may cause lag)
2. Disable real-time collaboration temporarily
3. Export canvas and reimport clean

### State Corruption

1. Use undo to revert recent changes
2. Export canvas JSON as backup
3. Reload page to restore from server

## Development

### Adding New Node Types

1. Define node type in `apps/web/src/types/canvas.ts`
2. Create node component in `apps/web/src/components/canvas/nodes/`
3. Register in `nodeTypes` map
4. Add to ComponentPalette

### Adding New Tools

1. Add tool to toolbar in `CanvasToolbar.tsx`
2. Implement tool logic in `apps/web/src/lib/canvas/`
3. Add keyboard shortcut handler
4. Update TESTING_GUIDE.md with new test cases

## Migration Notes

### From Legacy Canvases

All previous canvas implementations have been consolidated:

- `/miro-canvas` → Merged into unified canvas
- `/yappc-enhanced-canvas` → Merged into unified canvas
- `/yappc-task-canvas` → Merged into unified canvas

See `.archive/docs/2026-01/DEPRECATED_CANVAS_MIGRATION.md` for details.

## Resources

- [Architecture Diagrams](../../docs/architecture/)
- [Testing Guide](./TESTING_GUIDE.md)
- [API Documentation](../../../apps/api/README.md)
- [Design System](../../../libs/ui/README.md)

---

**For Issues**: See [GitHub Issues](https://github.com/your-org/yappc/issues)  
**For Questions**: Contact dev team on Slack #yappc-canvas
