/**
 * Canvas Chrome Atoms
 * 
 * Jotai state management for canvas UI chrome (panels, toolbars, etc.)
 * Implements "calm by default" progressive disclosure pattern
 * 
 * @doc.type atoms
 * @doc.purpose Canvas UI state management
 * @doc.layer state
 * @doc.pattern State
 */

import { atom } from 'jotai';

/**
 * Focus Mode (replaces Calm Mode) - distraction-free canvas
 * When true: hides all chrome except zoom HUD (dimmed 50%)
 * Keyboard shortcut: ⌘⇧F
 */
export const chromeFocusModeAtom = atom<boolean>(false);

/**
 * Calm Mode - minimal UI with progressive disclosure (deprecated, use focus mode)
 * Kept for backward compatibility
 */
export const chromeCalmModeAtom = atom<boolean>(true);

/**
 * Left rail visibility
 * When false: shows only icon strip
 * When true: shows full panel content
 */
export const chromeLeftRailVisibleAtom = atom<boolean>(false);

/**
 * Active left panel
 * Which panel content to show in left rail when expanded
 */
export type LeftPanelType = 'outline' | 'layers' | 'palette' | 'tasks' | null;
export const chromeLeftPanelAtom = atom<LeftPanelType>(null);

/**
 * Inspector (right panel) visibility
 * Auto-shows when items are selected
 */
export const chromeInspectorVisibleAtom = atom<boolean>(false);

/**
 * Minimap visibility
 * Hidden by default in calm mode
 */
export const chromeMinimapVisibleAtom = atom<boolean>(false);

/**
 * Context bar visibility
 * Shows when items are selected
 */
export const chromeContextBarVisibleAtom = atom<boolean>(false);

/**
 * Grid visibility
 */
export const chromeGridVisibleAtom = atom<boolean>(true);

/**
 * Rulers visibility
 */
export const chromeRulersVisibleAtom = atom<boolean>(false);

/**
 * Command palette open state
 */
export const chromeCommandPaletteOpenAtom = atom<boolean>(false);

/**
 * Onboarding tour state
 */
export const chromeOnboardingActiveAtom = atom<boolean>(false);
export const chromeOnboardingStepAtom = atom<number>(0);

/**
 * Feature hints state
 * Tracks which hints have been shown to user
 */
export const chromeShownHintsAtom = atom<Set<string>>(new Set<string>());

/**
 * Distraction-free mode (alias for focus mode)
 * Hides ALL chrome temporarily
 */
export const chromeDistractionFreeAtom = atom<boolean>(false);

/**
 * Floating toolbar visibility and position
 */
export const chromeFloatingToolbarVisibleAtom = atom<boolean>(false);
export const chromeFloatingToolbarPositionAtom = atom<{ x: number; y: number }>({ x: 0, y: 0 });

/**
 * Zoom state
 */
export const chromeZoomLevelAtom = atom<number>(1);
export const chromeZoomHUDVisibleAtom = atom<boolean>(true);

/**
 * Hierarchical navigation state
 */
export const chromeCurrentFramePathAtom = atom<string[]>([]);
export const chromeBreadcrumbVisibleAtom = atom<boolean>(true);

/**
 * Overview mode - shows entire lifecycle at 0.1x-0.25x zoom
 * Keyboard shortcut: ⌘⇧O
 */
export const chromeOverviewModeAtom = atom<boolean>(false);

/**
 * Phase indicator state
 */
export const chromeCurrentPhaseAtom = atom<string | null>(null);
export const chromePhaseIndicatorVisibleAtom = atom<boolean>(true);

/**
 * Hover intent detection
 */
export const chromeHoverIntentAtom = atom<{ target: string | null; timestamp: number }>({ target: null, timestamp: 0 });

/**
 * Empty canvas state
 */
export const chromeShowEmptyStateAtom = atom<boolean>(true);

/**
 * Recent actions for context menu
 */
export const chromeRecentActionsAtom = atom<Array<{ id: string; label: string; timestamp: number }>>([]);

/**
 * Derived atom: should inspector auto-open?
 * Opens inspector when items are selected (unless in calm mode)
 */
export const chromeInspectorAutoOpenAtom = atom(
  (get) => get(chromeInspectorVisibleAtom),
  (get, set, hasSelection: boolean) => {
    const calmMode = get(chromeCalmModeAtom);
    // In calm mode, inspector only shows if explicitly opened
    // Otherwise, auto-open when items selected
    if (!calmMode && hasSelection) {
      set(chromeInspectorVisibleAtom, true);
    } else if (!hasSelection) {
      set(chromeInspectorVisibleAtom, false);
    }
  }
);

/**
 * Derived atom: computed UI state
 * Combines multiple atoms for easy consumption
 */
export const chromeUIStateAtom = atom((get) => ({
  calmMode: get(chromeCalmModeAtom),
  leftRailVisible: get(chromeLeftRailVisibleAtom),
  leftPanel: get(chromeLeftPanelAtom),
  inspectorVisible: get(chromeInspectorVisibleAtom),
  minimapVisible: get(chromeMinimapVisibleAtom),
  contextBarVisible: get(chromeContextBarVisibleAtom),
  gridVisible: get(chromeGridVisibleAtom),
  rulersVisible: get(chromeRulersVisibleAtom),
  commandPaletteOpen: get(chromeCommandPaletteOpenAtom),
  distractionFree: get(chromeDistractionFreeAtom),
}));

/**
 * Action: Toggle focus mode (⌘⇧F)
 */
export const toggleFocusModeAtom = atom(
  null,
  (get, set) => {
    const current = get(chromeFocusModeAtom);
    set(chromeFocusModeAtom, !current);

    // In focus mode, hide all chrome
    if (!current) {
      set(chromeLeftRailVisibleAtom, false);
      set(chromeInspectorVisibleAtom, false);
      set(chromeMinimapVisibleAtom, false);
      set(chromeContextBarVisibleAtom, false);
      set(chromeBreadcrumbVisibleAtom, false);
      set(chromePhaseIndicatorVisibleAtom, false);
    } else {
      // Restore default visibility
      set(chromeBreadcrumbVisibleAtom, true);
      set(chromePhaseIndicatorVisibleAtom, true);
    }
  }
);

/**
 * Action: Toggle calm mode (deprecated, use focus mode)
 */
export const toggleCalmModeAtom = atom(
  null,
  (get, set) => {
    const current = get(chromeCalmModeAtom);
    set(chromeCalmModeAtom, !current);

    // When entering calm mode, collapse panels
    if (!current) {
      set(chromeLeftRailVisibleAtom, false);
      set(chromeMinimapVisibleAtom, false);
    }
  }
);

/**
 * Action: Open specific left panel
 */
export const openLeftPanelAtom = atom(
  null,
  (_get, set, panel: LeftPanelType) => {
    set(chromeLeftPanelAtom, panel);
    if (panel !== null) {
      set(chromeLeftRailVisibleAtom, true);
    }
  }
);

/**
 * Action: Toggle distraction-free mode (alias for focus mode)
 */
export const toggleDistractionFreeAtom = atom(
  null,
  (get, set) => {
    const current = get(chromeDistractionFreeAtom);
    set(chromeDistractionFreeAtom, !current);
    set(chromeFocusModeAtom, !current);

    // Hide everything in distraction-free mode
    if (!current) {
      set(chromeLeftRailVisibleAtom, false);
      set(chromeInspectorVisibleAtom, false);
      set(chromeMinimapVisibleAtom, false);
      set(chromeContextBarVisibleAtom, false);
    }
  }
);

/**
 * Action: Toggle overview mode (⌘⇧O)
 */
export const toggleOverviewModeAtom = atom(
  null,
  (get, set) => {
    const current = get(chromeOverviewModeAtom);
    set(chromeOverviewModeAtom, !current);

    // In overview mode, zoom to 0.1x-0.25x to show entire lifecycle
    if (!current) {
      set(chromeZoomLevelAtom, 0.15);
    } else {
      set(chromeZoomLevelAtom, 1);
    }
  }
);

/**
 * Action: Navigate to frame (drill-down)
 */
export const navigateToFrameAtom = atom(
  null,
  (get, set, frameId: string) => {
    const currentPath = get(chromeCurrentFramePathAtom);
    set(chromeCurrentFramePathAtom, [...currentPath, frameId]);
  }
);

/**
 * Action: Navigate up (drill-up)
 */
export const navigateUpAtom = atom(
  null,
  (get, set, levels: number = 1) => {
    const currentPath = get(chromeCurrentFramePathAtom);
    const newPath = currentPath.slice(0, -levels);
    set(chromeCurrentFramePathAtom, newPath);
  }
);

/**
 * Action: Add recent action
 */
export const addRecentActionAtom = atom(
  null,
  (get, set, action: { id: string; label: string }) => {
    const recent = get(chromeRecentActionsAtom);
    const newAction = { ...action, timestamp: Date.now() };
    const updated = [newAction, ...recent.slice(0, 9)]; // Keep last 10
    set(chromeRecentActionsAtom, updated);
  }
);
