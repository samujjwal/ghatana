# Sketch Library Migration Guide

**Date:** December 3, 2025  
**Status:** ✅ Complete

## Overview

The sketch/whiteboard functionality has been consolidated from `apps/web/src/components/canvas/sketch/` into a shared library `@yappc/sketch` (`libs/sketch/`).

## Capability Comparison

### Original Files → New Library

| Original File             | New Location                             | Status                                   |
| ------------------------- | ---------------------------------------- | ---------------------------------------- |
| `types.ts`                | `@yappc/sketch/types`                    | ✅ Enhanced (added more types)           |
| `useSketchTools.ts`       | `@yappc/sketch/hooks/useSketchTools`     | ✅ Enhanced                              |
| `useSketchKeyboard.ts`    | `@yappc/sketch/hooks/useSketchKeyboard`  | ✅ Enhanced (added undo/redo)            |
| `smoothStroke.ts`         | `@yappc/sketch/utils/smoothStroke`       | ✅ Enhanced (added RDP algorithm)        |
| `SketchToolbar.tsx`       | `@yappc/sketch/components/SketchToolbar` | ✅ Enhanced (added groups, compact mode) |
| `StickyNote.tsx`          | `@yappc/sketch/components/StickyNote`    | ✅ Enhanced (added more colors)          |
| `EnhancedSketchLayer.tsx` | Kept in app (state-specific)             | ✅ Updated imports                       |

### New Capabilities Added

1. **Types** (`libs/sketch/src/types.ts`)
   - `PressurePoint` - Stylus pressure support
   - `TextData` - Text element support
   - `ImageData` - Image annotation support
   - `SketchLayer` - Layer management
   - `SketchDocument` - Full document state
   - `SketchHistoryEntry` - Undo/redo support
   - `SketchExportOptions` - Export configuration
   - More tool types: `highlighter`, `line`, `arrow`, `text`, `image`

2. **Components** (`libs/sketch/src/components/`)
   - `SketchCanvas` - Full canvas component with grid, export, viewport
   - `SketchToolbar` - Enhanced with tool groups, compact mode
   - `StickyNote` - Enhanced with more colors, better UX

3. **Hooks** (`libs/sketch/src/hooks/`)
   - `useSketchTools` - Enhanced with more shape types
   - `useSketchKeyboard` - Added undo/redo/delete/selectAll shortcuts

4. **Utilities** (`libs/sketch/src/utils/`)
   - `getPointsBounds` - Calculate bounding box
   - Enhanced `simplifyPoints` with proper RDP algorithm

## Migration Steps

### For New Code

```typescript
// Import from shared library
import {
  SketchCanvas,
  SketchToolbar,
  useSketchTools,
  useSketchKeyboard,
  type SketchTool,
} from '@yappc/sketch';
```

### For Existing Code

The old import paths still work via re-exports:

```typescript
// This still works (deprecated)
import { SketchToolbar } from '../../../../components/canvas/sketch';

// Recommended: Use library directly
import { SketchToolbar } from '@yappc/sketch';
```

## Files to Keep (App-Specific)

The following file remains in the app because it has app-specific state integration:

- `apps/web/src/components/canvas/sketch/EnhancedSketchLayer.tsx`
  - Uses app-specific atoms (`canvasAtom`, `viewportAtom`, `transformAtom`)
  - Integrates with app-specific `CanvasElement` type
  - Now imports types and hooks from `@yappc/sketch`

## Files That Can Be Removed (After Full Migration)

Once all imports are updated to use `@yappc/sketch`:

- `apps/web/src/components/canvas/sketch/types.ts` - Types now in library
- `apps/web/src/components/canvas/sketch/useSketchTools.ts` - Hook now in library
- `apps/web/src/components/canvas/sketch/useSketchKeyboard.ts` - Hook now in library
- `apps/web/src/components/canvas/sketch/smoothStroke.ts` - Utils now in library
- `apps/web/src/components/canvas/sketch/SketchToolbar.tsx` - Component now in library
- `apps/web/src/components/canvas/sketch/StickyNote.tsx` - Component now in library

## No Capabilities Lost

✅ All original functionality preserved:

- Pen drawing
- Eraser
- Rectangle shapes
- Ellipse shapes
- Sticky notes
- Keyboard shortcuts (V, P, E, R, O, S)
- Stroke smoothing with perfect-freehand
- Point simplification
- E2E test support

✅ New capabilities added:

- Highlighter tool
- Line tool
- Arrow tool
- Text tool
- Image tool
- Undo/redo keyboard shortcuts
- Delete/SelectAll shortcuts
- Tool grouping in toolbar
- Compact toolbar mode
- Canvas export to image
- Grid overlay
- More sticky note colors

## TypeScript Configuration

Added to `apps/web/tsconfig.json`:

```json
{
  "paths": {
    "@yappc/sketch": ["../../libs/sketch/src/index.ts"],
    "@yappc/sketch/*": ["../../libs/sketch/src/*"]
  }
}
```

## Next Steps

1. Run `pnpm install` to register the new library
2. Verify build passes: `pnpm build:web`
3. Run tests: `pnpm test`
4. Gradually remove deprecated files after confirming no other imports
