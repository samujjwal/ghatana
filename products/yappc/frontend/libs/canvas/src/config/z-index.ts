/**
 * Z-Index Token System for Canvas UI Layers
 *
 * Centralized z-index management to prevent conflicts and ensure
 * predictable layering across all canvas UI components.
 *
 * @doc.type configuration
 * @doc.purpose Prevent z-index conflicts via centralized registry
 * @doc.layer core
 * @doc.pattern Token System
 *
 * Usage:
 * ```typescript
 * import { CANVAS_Z_INDEX } from './config/z-index';
 *
 * <div style={{ zIndex: CANVAS_Z_INDEX.CONTEXT_BAR }}>
 *   ...
 * </div>
 * ```
 */

/**
 * Canvas Z-Index Registry
 *
 * Organized in ascending order from canvas content (lowest)
 * to overlays (highest). Each layer is spaced by 10 to allow
 * for intermediate values if needed.
 */
export const CANVAS_Z_INDEX = {
  // ============================================================================
  // Canvas Content Layers (0-99)
  // ============================================================================

  /** Background grid and canvas surface */
  BACKGROUND: 0,

  /** Grid overlay for alignment */
  GRID: 1,

  /** Frame containers (below elements so elements appear on top) */
  FRAMES: 10,

  /** Edges/connections between nodes */
  EDGES: 15,

  /** Node elements, groups, and artifacts */
  ELEMENTS: 20,

  /** Annotations, comments, sticky notes */
  ANNOTATIONS: 30,

  /** Selection indicators and handles */
  SELECTION: 40,

  /** Portal nodes and nested canvas indicators */
  PORTALS: 50,

  // ============================================================================
  // UI Chrome - Persistent Panels (100-299)
  // ============================================================================

  /** Context-sensitive toolbar (appears on selection) */
  CONTEXT_BAR: 100,

  /** Top navigation bar (always visible) */
  TOP_BAR: 150,

  /** Left icon rail (always visible) */
  LEFT_RAIL: 200,

  /** Left expanding pane (outline, layers, palette, tasks) */
  LEFT_PANE: 210,

  /** Right inspector panel */
  INSPECTOR: 220,

  /** Minimap panel */
  MINIMAP: 230,

  /** Zoom level HUD (bottom-left indicator) */
  ZOOM_HUD: 240,

  /** Next best task badge */
  TASK_BADGE: 250,

  // ============================================================================
  // Floating UI Elements (300-499)
  // ============================================================================

  /** Floating action buttons */
  FAB: 300,

  /** Contextual help cards */
  HELP_CARD: 310,

  /** Onboarding coach marks */
  COACH_MARK: 320,

  /** Property popover editors */
  POPOVER: 330,

  // ============================================================================
  // Overlays - Full Screen (500-999)
  // ============================================================================

  /** Loading overlay */
  LOADING_OVERLAY: 500,

  /** Collaboration cursors and presence */
  COLLABORATION: 600,

  /** Search overlay */
  SEARCH_OVERLAY: 700,

  // ============================================================================
  // Modals & Dialogs (1000-1999)
  // ============================================================================

  /** Command palette (Cmd+K) */
  COMMAND_PALETTE: 1000,

  /** Modal dialogs (confirmation, forms) */
  MODAL: 1100,

  /** Modal backdrop */
  MODAL_BACKDROP: 1050,

  /** Drawer overlays */
  DRAWER: 1150,

  // ============================================================================
  // System UI - Highest Priority (2000+)
  // ============================================================================

  /** Toast notifications */
  TOAST: 1200,

  /** Tooltips */
  TOOLTIP: 1300,

  /** Context menus */
  CONTEXT_MENU: 1400,

  /** Dropdown menus */
  DROPDOWN: 1500,

  /** Critical error overlay */
  ERROR_OVERLAY: 2000,

  /** Debug panel (dev mode only) */
  DEBUG_PANEL: 9999,
} as const;

/**
 * Z-Index token keys
 */
export type ZIndexToken = keyof typeof CANVAS_Z_INDEX;

/**
 * Get z-index value by token name
 *
 * @param token - Z-index token name
 * @returns Numeric z-index value
 *
 * @example
 * ```typescript
 * const zIndex = getZIndex('CONTEXT_BAR'); // 100
 * ```
 */
export function getZIndex(token: ZIndexToken): number {
  return CANVAS_Z_INDEX[token];
}

/**
 * Validate that z-index values don't overlap inappropriately
 * Used in tests to ensure token integrity
 */
export function validateZIndexHierarchy(): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];
  const values = Object.entries(CANVAS_Z_INDEX);

  // Check for duplicates
  const seen = new Map<number, string>();
  values.forEach(([key, value]) => {
    const existing = seen.get(value);
    if (existing) {
      errors.push(`Duplicate z-index ${value} found: ${existing} and ${key}`);
    }
    seen.set(value, key);
  });

  // Check expected ordering
  const expectedOrder: ZIndexToken[] = [
    'BACKGROUND',
    'GRID',
    'FRAMES',
    'EDGES',
    'ELEMENTS',
    'ANNOTATIONS',
    'SELECTION',
    'CONTEXT_BAR',
    'TOP_BAR',
    'LEFT_RAIL',
    'COMMAND_PALETTE',
    'MODAL',
    'TOAST',
    'TOOLTIP',
  ];

  for (let i = 1; i < expectedOrder.length; i++) {
    const prev = CANVAS_Z_INDEX[expectedOrder[i - 1]];
    const curr = CANVAS_Z_INDEX[expectedOrder[i]];

    if (curr <= prev) {
      errors.push(
        `Z-index ordering violation: ${expectedOrder[i]} (${curr}) should be > ${expectedOrder[i - 1]} (${prev})`
      );
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Check if a token represents a modal/overlay layer
 */
export function isOverlayLayer(token: ZIndexToken): boolean {
  return CANVAS_Z_INDEX[token] >= CANVAS_Z_INDEX.COMMAND_PALETTE;
}

/**
 * Check if a token represents canvas content (vs UI chrome)
 */
export function isCanvasContentLayer(token: ZIndexToken): boolean {
  return CANVAS_Z_INDEX[token] < CANVAS_Z_INDEX.CONTEXT_BAR;
}

/**
 * Get the next available z-index above a given token
 * Useful for dynamic content that needs to appear above a layer
 */
export function getZIndexAbove(token: ZIndexToken): number {
  return CANVAS_Z_INDEX[token] + 1;
}

/**
 * Get the next available z-index below a given token
 */
export function getZIndexBelow(token: ZIndexToken): number {
  return CANVAS_Z_INDEX[token] - 1;
}
