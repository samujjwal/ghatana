# @ghatana/canvas-chrome

Canvas chrome components — panels, command palette, role switcher, and the integrated canvas shell.

## Usage

```tsx
import { IntegratedCanvasChrome, LayersPanel, CommandPalette } from '@ghatana/canvas-chrome';
import { RoleSwitcher, CollaborationCursors } from '@ghatana/canvas-chrome';
```

## API Surface

- `IntegratedCanvasChrome` — full chrome shell composition
- `CommandPalette`, `EnhancedContextMenu` — command and context overlays
- `RoleSwitcher`, `SmartContextBar` — role-aware toolbar
- `LayersPanel`, `MinimapPanel`, `OutlinePanel`, `PalettePanel`, `TasksPanel` — panel suite
- `CollaborationCursors` — real-time cursors overlay
