/**
 * Canvas Chrome State Atoms
 *
 * Jotai atoms for managing canvas chrome visibility and behavior.
 *
 * @doc.type state
 * @doc.purpose Chrome UI state management
 * @doc.layer core
 * @doc.pattern State
 */

import { atom } from 'jotai';

/**
 * Calm Mode
 *
 * When enabled, UI chrome is hidden by default and only
 * revealed on hover or interaction.
 */
export const chromeCalmModeAtom = atom<boolean>(true);

/**
 * Left Rail Visibility
 *
 * Controls visibility of the left rail (Palette, Connectors).
 */
export const chromeLeftRailVisibleAtom = atom<boolean>(true);

/**
 * Context Bar Visibility
 *
 * Controls visibility of the floating context bar.
 * Auto-shown on selection, hidden on Escape.
 */
export const chromeContextBarVisibleAtom = atom<boolean>(false);

/**
 * Inspector Panel Visibility
 *
 * Controls visibility of the right inspector panel.
 */
export const chromeInspectorVisibleAtom = atom<boolean>(true);

/**
 * Outline Panel Visibility
 *
 * Controls visibility of the outline/navigator panel.
 */
export const chromeOutlineVisibleAtom = atom<boolean>(false);

/**
 * Minimap Visibility
 *
 * Controls visibility of the minimap.
 */
export const chromeMinimapVisibleAtom = atom<boolean>(true);

/**
 * Palette Panel Visibility
 *
 * Controls visibility of the palette panel within left rail.
 */
export const chromePaletteVisibleAtom = atom<boolean>(true);

/**
 * Connectors Panel Visibility
 *
 * Controls visibility of the connectors panel within left rail.
 */
export const chromeConnectorsVisibleAtom = atom<boolean>(false);

/**
 * Layer Panel Visibility
 *
 * Controls visibility of the layer management panel.
 */
export const chromeLayersVisibleAtom = atom<boolean>(false);

/**
 * Computed: Any Panel Visible
 *
 * Derived atom that returns true if any chrome panel is visible.
 */
export const chromeAnyPanelVisibleAtom = atom((get) => {
  return (
    get(chromeLeftRailVisibleAtom) ||
    get(chromeInspectorVisibleAtom) ||
    get(chromeOutlineVisibleAtom) ||
    get(chromeMinimapVisibleAtom)
  );
});

/**
 * Computed: Canvas Content Inset
 *
 * Calculates the inset (padding) for canvas content based on
 * visible panels. Used for centering and viewport calculations.
 */
export const chromeContentInsetAtom = atom((get) => {
  const leftRailVisible = get(chromeLeftRailVisibleAtom);
  const outlineVisible = get(chromeOutlineVisibleAtom);
  const inspectorVisible = get(chromeInspectorVisibleAtom);

  return {
    left: (leftRailVisible ? 280 : 0) + (outlineVisible ? 240 : 0),
    right: inspectorVisible ? 320 : 0,
    top: 0,
    bottom: 0,
  };
});

/**
 * Chrome Panel Dimensions
 *
 * Fixed dimensions for chrome panels (in pixels).
 */
export const CHROME_DIMENSIONS = {
  LEFT_RAIL_WIDTH: 280,
  OUTLINE_WIDTH: 240,
  INSPECTOR_WIDTH: 320,
  MINIMAP_WIDTH: 200,
  MINIMAP_HEIGHT: 150,
  CONTEXT_BAR_MIN_WIDTH: 400,
  CONTEXT_BAR_MAX_WIDTH: 800,
} as const;

/**
 * Chrome Animation Duration
 *
 * Duration for panel slide animations (in ms).
 */
export const CHROME_ANIMATION_DURATION = 200;

/**
 * Chrome Hover Delay
 *
 * Delay before showing chrome on hover in calm mode (in ms).
 */
export const CHROME_HOVER_DELAY = 300;

/**
 * Chrome Auto-Hide Delay
 *
 * Delay before hiding chrome after mouse leaves in calm mode (in ms).
 */
export const CHROME_AUTO_HIDE_DELAY = 1000;
