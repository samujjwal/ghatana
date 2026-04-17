/**
 * Toolbar State Atoms
 * 
 * Jotai atoms for canvas toolbar state management including:
 * - Canvas mode (Brainstorm, Diagram, Design, Code, Test, Deploy, Observe)
 * - Abstraction level (System, Component, File, Code)
 * - Derived atoms for zoom capabilities
 * 
 * @doc.type module
 * @doc.purpose Toolbar state management
 * @doc.layer product
 * @doc.pattern State Atoms
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type { CanvasMode } from '../../types/canvasMode';
import type { AbstractionLevel } from '../../types/abstractionLevel';

// ============================================================================
// Canvas Mode Atoms
// ============================================================================

/**
 * Default canvas mode
 */
const DEFAULT_MODE: CanvasMode = 'diagram';

/**
 * Canvas mode atom with localStorage persistence
 * Stores the current mode per project
 */
export const canvasModeAtom = atomWithStorage<CanvasMode>(
    'canvas-toolbar-mode',
    DEFAULT_MODE
);

/**
 * All available canvas modes in order
 */
export const CANVAS_MODE_ORDER: CanvasMode[] = [
    'brainstorm',
    'diagram',
    'design',
    'code',
    'test',
    'deploy',
    'observe',
];

/**
 * Mode keyboard shortcuts mapping
 */
export const MODE_SHORTCUTS: Record<string, CanvasMode> = {
    '1': 'brainstorm',
    '2': 'diagram',
    '3': 'design',
    '4': 'code',
    '5': 'test',
    '6': 'deploy',
    '7': 'observe',
};

// ============================================================================
// Abstraction Level Atoms
// ============================================================================

/**
 * Default abstraction level
 */
const DEFAULT_LEVEL: AbstractionLevel = 'component';

/**
 * Abstraction level atom with localStorage persistence
 */
export const abstractionLevelAtom = atomWithStorage<AbstractionLevel>(
    'canvas-toolbar-level',
    DEFAULT_LEVEL
);

/**
 * All abstraction levels in order (from highest to lowest detail)
 */
export const ABSTRACTION_LEVEL_ORDER: AbstractionLevel[] = [
    'system',
    'component',
    'file',
    'code',
];

// ============================================================================
// Derived Atoms for Zoom Capabilities
// ============================================================================

/**
 * Derived atom: can zoom out to broader level?
 * Returns true if not already at System level
 */
export const canZoomOutAtom = atom((get) => {
    const level = get(abstractionLevelAtom);
    return level !== 'system';
});

/**
 * Derived atom: can drill down to more detailed level?
 * Returns true if not already at Code level
 */
export const canDrillDownAtom = atom((get) => {
    const level = get(abstractionLevelAtom);
    return level !== 'code';
});

/**
 * Derived atom: get current level index (0-3)
 */
export const currentLevelIndexAtom = atom((get) => {
    const level = get(abstractionLevelAtom);
    return ABSTRACTION_LEVEL_ORDER.indexOf(level);
});

/**
 * Derived atom: get next level (drill down target)
 */
export const nextLevelAtom = atom((get) => {
    const currentIndex = get(currentLevelIndexAtom);
    const nextIndex = currentIndex + 1;
    return nextIndex < ABSTRACTION_LEVEL_ORDER.length 
        ? ABSTRACTION_LEVEL_ORDER[nextIndex] 
        : null;
});

/**
 * Derived atom: get previous level (zoom out target)
 */
export const previousLevelAtom = atom((get) => {
    const currentIndex = get(currentLevelIndexAtom);
    const prevIndex = currentIndex - 1;
    return prevIndex >= 0 
        ? ABSTRACTION_LEVEL_ORDER[prevIndex] 
        : null;
});

// ============================================================================
// Action Atoms
// ============================================================================

/**
 * Action atom: zoom out to broader level
 */
export const zoomOutAtom = atom(
    null,
    (get, set) => {
        const prevLevel = get(previousLevelAtom);
        if (prevLevel) {
            set(abstractionLevelAtom, prevLevel);
        }
    }
);

/**
 * Action atom: drill down to more detailed level
 */
export const drillDownAtom = atom(
    null,
    (get, set) => {
        const nextLevel = get(nextLevelAtom);
        if (nextLevel) {
            set(abstractionLevelAtom, nextLevel);
        }
    }
);

/**
 * Action atom: reset to default level (Component)
 */
export const resetLevelAtom = atom(
    null,
    (_get, set) => {
        set(abstractionLevelAtom, DEFAULT_LEVEL);
    }
);

/**
 * Action atom: set mode by keyboard shortcut
 */
export const setModeByShortcutAtom = atom(
    null,
    (_get, set, key: string) => {
        const mode = MODE_SHORTCUTS[key];
        if (mode) {
            set(canvasModeAtom, mode);
        }
    }
);

// ============================================================================
// Transition State Atoms
// ============================================================================

/**
 * Transition state for animations
 */
export interface TransitionState {
    isTransitioning: boolean;
    direction: 'in' | 'out' | null;
    fromLevel: AbstractionLevel | null;
    toLevel: AbstractionLevel | null;
    fromMode: CanvasMode | null;
    toMode: CanvasMode | null;
}

/**
 * Transition state atom for level/mode change animations
 */
export const transitionStateAtom = atom<TransitionState>({
    isTransitioning: false,
    direction: null,
    fromLevel: null,
    toLevel: null,
    fromMode: null,
    toMode: null,
});

/**
 * Action atom: set level with transition animation
 */
export const setLevelWithTransitionAtom = atom(
    null,
    (get, set, newLevel: AbstractionLevel) => {
        const currentLevel = get(abstractionLevelAtom);
        if (currentLevel === newLevel) return;

        const currentIndex = ABSTRACTION_LEVEL_ORDER.indexOf(currentLevel);
        const newIndex = ABSTRACTION_LEVEL_ORDER.indexOf(newLevel);
        const direction = newIndex > currentIndex ? 'in' : 'out';

        // Start transition
        set(transitionStateAtom, {
            isTransitioning: true,
            direction,
            fromLevel: currentLevel,
            toLevel: newLevel,
            fromMode: null,
            toMode: null,
        });

        // Update level
        set(abstractionLevelAtom, newLevel);

        // End transition after animation duration (300ms)
        setTimeout(() => {
            set(transitionStateAtom, {
                isTransitioning: false,
                direction: null,
                fromLevel: null,
                toLevel: null,
                fromMode: null,
                toMode: null,
            });
        }, 300);
    }
);

/**
 * Action atom: set mode with transition animation
 */
export const setModeWithTransitionAtom = atom(
    null,
    (get, set, newMode: CanvasMode) => {
        const currentMode = get(canvasModeAtom);
        if (currentMode === newMode) return;

        // Start transition
        set(transitionStateAtom, {
            isTransitioning: true,
            direction: null,
            fromLevel: null,
            toLevel: null,
            fromMode: currentMode,
            toMode: newMode,
        });

        // Update mode
        set(canvasModeAtom, newMode);

        // End transition after animation duration (200ms for mode changes)
        setTimeout(() => {
            set(transitionStateAtom, {
                isTransitioning: false,
                direction: null,
                fromLevel: null,
                toLevel: null,
                fromMode: null,
                toMode: null,
            });
        }, 200);
    }
);

// ============================================================================
// Combined State Atom
// ============================================================================

/**
 * Combined toolbar state for convenience
 */
export interface ToolbarState {
    mode: CanvasMode;
    level: AbstractionLevel;
    canZoomOut: boolean;
    canDrillDown: boolean;
    isTransitioning: boolean;
}

/**
 * Derived atom: combined toolbar state
 */
export const toolbarStateAtom = atom<ToolbarState>((get) => ({
    mode: get(canvasModeAtom),
    level: get(abstractionLevelAtom),
    canZoomOut: get(canZoomOutAtom),
    canDrillDown: get(canDrillDownAtom),
    isTransitioning: get(transitionStateAtom).isTransitioning,
}));
