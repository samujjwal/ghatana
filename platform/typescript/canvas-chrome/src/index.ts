/**
 * @ghatana/canvas-chrome
 *
 * Canvas chrome components — panels, command palette, role switcher,
 * smart context bar, collaboration cursors, and the integrated canvas shell.
 *
 * Note: these are domain-informed UI components that ship with the canvas
 * but are intentionally separated from rendering primitives so products
 * can compose their own shells without pulling in the full chrome bundle.
 *
 * @doc.type module
 * @doc.purpose Canvas shell components (panels, toolbar, overlays)
 * @doc.layer platform
 * @doc.pattern Facade
 */

// ─── Shell re-exports from @ghatana/canvas ────────────────────────────────
// These components are isolated here so consumers only pay the JSX cost
// when they actually need the chrome layer.

// React canvas hook (base, low-level)
export { useCanvas } from "@ghatana/canvas/react";

// Collaboration components
export { CollaborationCursors } from "@ghatana/canvas";

// Command palette + context menu
export { CommandPalette, EnhancedContextMenu } from "@ghatana/canvas";

// Role switcher + smart context bar
export { RoleSwitcher, SmartContextBar } from "@ghatana/canvas";

// Integrated chrome shell
export { IntegratedCanvasChrome } from "@ghatana/canvas";

// Panel suite
export {
  LayersPanel,
  MinimapPanel,
  OutlinePanel,
  PalettePanel,
  TasksPanel,
} from "@ghatana/canvas";
