/**
 * Toolbar Keyboard Shortcuts Hook
 * 
 * Unified keyboard shortcut handling for canvas toolbar:
 * - 1-7 keys for mode switching
 * - Alt+Up/Down for zoom level navigation
 * - Cmd/Ctrl+Z for undo, Cmd/Ctrl+Shift+Z for redo
 * - Home to reset to default level
 * 
 * @doc.type hook
 * @doc.purpose Toolbar keyboard shortcuts
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useCallback } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
    canvasModeAtom,
    abstractionLevelAtom,
    canZoomOutAtom,
    canDrillDownAtom,
    setModeByShortcutAtom,
    zoomOutAtom,
    drillDownAtom,
    resetLevelAtom,
    setLevelWithTransitionAtom,
    setModeWithTransitionAtom,
    MODE_SHORTCUTS,
} from '../state/atoms/toolbarAtom';
import type { CanvasMode } from '../types/canvasMode';
import type { AbstractionLevel } from '../types/abstractionLevel';

// ============================================================================
// Types
// ============================================================================

export interface UseToolbarKeyboardShortcutsOptions {
    /** Enable mode shortcuts (1-7 keys) */
    enableModeShortcuts?: boolean;
    /** Enable level shortcuts (Alt+Up/Down) */
    enableLevelShortcuts?: boolean;
    /** Enable undo/redo shortcuts (Cmd+Z, Cmd+Shift+Z) */
    enableHistoryShortcuts?: boolean;
    /** Callback when mode changes via keyboard */
    onModeChange?: (mode: CanvasMode) => void;
    /** Callback when level changes via keyboard */
    onLevelChange?: (level: AbstractionLevel) => void;
    /** Callback for undo action */
    onUndo?: () => void;
    /** Callback for redo action */
    onRedo?: () => void;
    /** Whether shortcuts are disabled (e.g., when modal is open) */
    disabled?: boolean;
}

export interface UseToolbarKeyboardShortcutsResult {
    /** Current canvas mode */
    mode: CanvasMode;
    /** Current abstraction level */
    level: AbstractionLevel;
    /** Whether can zoom out */
    canZoomOut: boolean;
    /** Whether can drill down */
    canDrillDown: boolean;
    /** Set mode programmatically */
    setMode: (mode: CanvasMode) => void;
    /** Set level programmatically */
    setLevel: (level: AbstractionLevel) => void;
    /** Zoom out action */
    zoomOut: () => void;
    /** Drill down action */
    drillDown: () => void;
    /** Reset to default level */
    resetLevel: () => void;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Hook for unified toolbar keyboard shortcuts.
 * 
 * Handles:
 * - Mode switching via 1-7 keys
 * - Level navigation via Alt+Up/Down
 * - Undo/Redo via Cmd+Z / Cmd+Shift+Z
 * - Reset to default level via Home key
 * 
 * @example
 * ```tsx
 * const { mode, level, canZoomOut, canDrillDown } = useToolbarKeyboardShortcuts({
 *   onModeChange: (mode) => console.log('Mode changed:', mode),
 *   onLevelChange: (level) => console.log('Level changed:', level),
 *   onUndo: () => historyManager.undo(),
 *   onRedo: () => historyManager.redo(),
 * });
 * ```
 */
export function useToolbarKeyboardShortcuts(
    options: UseToolbarKeyboardShortcutsOptions = {}
): UseToolbarKeyboardShortcutsResult {
    const {
        enableModeShortcuts = true,
        enableLevelShortcuts = true,
        enableHistoryShortcuts = true,
        onModeChange,
        onLevelChange,
        onUndo,
        onRedo,
        disabled = false,
    } = options;

    // Atoms
    const [mode, setModeAtom] = useAtom(canvasModeAtom);
    const [level, setLevelAtom] = useAtom(abstractionLevelAtom);
    const [canZoomOutValue] = useAtom(canZoomOutAtom);
    const [canDrillDownValue] = useAtom(canDrillDownAtom);
    
    // Action atoms
    const setModeByShortcut = useSetAtom(setModeByShortcutAtom);
    const zoomOutAction = useSetAtom(zoomOutAtom);
    const drillDownAction = useSetAtom(drillDownAtom);
    const resetLevelAction = useSetAtom(resetLevelAtom);
    const setLevelWithTransition = useSetAtom(setLevelWithTransitionAtom);
    const setModeWithTransition = useSetAtom(setModeWithTransitionAtom);

    // Wrapped actions with callbacks
    const setMode = useCallback((newMode: CanvasMode) => {
        setModeWithTransition(newMode);
        onModeChange?.(newMode);
    }, [setModeWithTransition, onModeChange]);

    const setLevel = useCallback((newLevel: AbstractionLevel) => {
        setLevelWithTransition(newLevel);
        onLevelChange?.(newLevel);
    }, [setLevelWithTransition, onLevelChange]);

    const zoomOut = useCallback(() => {
        if (canZoomOutValue) {
            zoomOutAction();
            // Get the new level after zoom out
            const levels: AbstractionLevel[] = ['system', 'component', 'file', 'code'];
            const currentIndex = levels.indexOf(level);
            if (currentIndex > 0) {
                onLevelChange?.(levels[currentIndex - 1]);
            }
        }
    }, [canZoomOutValue, zoomOutAction, level, onLevelChange]);

    const drillDown = useCallback(() => {
        if (canDrillDownValue) {
            drillDownAction();
            // Get the new level after drill down
            const levels: AbstractionLevel[] = ['system', 'component', 'file', 'code'];
            const currentIndex = levels.indexOf(level);
            if (currentIndex < levels.length - 1) {
                onLevelChange?.(levels[currentIndex + 1]);
            }
        }
    }, [canDrillDownValue, drillDownAction, level, onLevelChange]);

    const resetLevel = useCallback(() => {
        resetLevelAction();
        onLevelChange?.('component');
    }, [resetLevelAction, onLevelChange]);

    // Keyboard event handler
    useEffect(() => {
        if (disabled) return;

        const handleKeyDown = (event: KeyboardEvent) => {
            // Skip if in input/textarea/select
            const target = event.target as HTMLElement;
            if (
                target.tagName === 'INPUT' ||
                target.tagName === 'TEXTAREA' ||
                target.tagName === 'SELECT' ||
                target.isContentEditable
            ) {
                return;
            }

            const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
            const cmdKey = isMac ? event.metaKey : event.ctrlKey;

            // Mode shortcuts (1-7 keys, no modifiers)
            if (enableModeShortcuts && !event.altKey && !cmdKey && !event.shiftKey) {
                const modeKey = event.key;
                if (MODE_SHORTCUTS[modeKey]) {
                    event.preventDefault();
                    const newMode = MODE_SHORTCUTS[modeKey];
                    setModeByShortcut(modeKey);
                    onModeChange?.(newMode);
                    return;
                }
            }

            // Level shortcuts (Alt+Up/Down)
            if (enableLevelShortcuts && event.altKey && !cmdKey && !event.shiftKey) {
                if (event.key === 'ArrowUp') {
                    event.preventDefault();
                    zoomOut();
                    return;
                }
                if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    drillDown();
                    return;
                }
            }

            // Reset to default level (Home key)
            if (enableLevelShortcuts && event.key === 'Home' && !event.altKey && !cmdKey && !event.shiftKey) {
                event.preventDefault();
                resetLevel();
                return;
            }

            // Undo/Redo shortcuts
            if (enableHistoryShortcuts && cmdKey) {
                if (event.key === 'z' && !event.shiftKey) {
                    event.preventDefault();
                    onUndo?.();
                    return;
                }
                if ((event.key === 'z' && event.shiftKey) || event.key === 'Z') {
                    event.preventDefault();
                    onRedo?.();
                    return;
                }
                // Also support Cmd+Y for redo on Windows
                if (event.key === 'y' && !isMac) {
                    event.preventDefault();
                    onRedo?.();
                    return;
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [
        disabled,
        enableModeShortcuts,
        enableLevelShortcuts,
        enableHistoryShortcuts,
        setModeByShortcut,
        zoomOut,
        drillDown,
        resetLevel,
        onModeChange,
        onUndo,
        onRedo,
    ]);

    return {
        mode,
        level,
        canZoomOut: canZoomOutValue,
        canDrillDown: canDrillDownValue,
        setMode,
        setLevel,
        zoomOut,
        drillDown,
        resetLevel,
    };
}

// ============================================================================
// Shortcut Display Helpers
// ============================================================================

/**
 * Get display string for a keyboard shortcut
 */
export function getShortcutDisplay(shortcut: string): string {
    const isMac = typeof navigator !== 'undefined' && 
        navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    
    return shortcut
        .replace('Cmd', isMac ? '⌘' : 'Ctrl')
        .replace('Ctrl', isMac ? '⌃' : 'Ctrl')
        .replace('Alt', isMac ? '⌥' : 'Alt')
        .replace('Shift', isMac ? '⇧' : 'Shift')
        .replace('ArrowUp', '↑')
        .replace('ArrowDown', '↓')
        .replace('ArrowLeft', '←')
        .replace('ArrowRight', '→');
}

/**
 * All toolbar keyboard shortcuts for help display
 */
export const TOOLBAR_SHORTCUTS = [
    { key: '1', action: 'Brainstorm mode', category: 'Mode' },
    { key: '2', action: 'Diagram mode', category: 'Mode' },
    { key: '3', action: 'Design mode', category: 'Mode' },
    { key: '4', action: 'Code mode', category: 'Mode' },
    { key: '5', action: 'Test mode', category: 'Mode' },
    { key: '6', action: 'Deploy mode', category: 'Mode' },
    { key: '7', action: 'Observe mode', category: 'Mode' },
    { key: 'Alt+↑', action: 'Zoom out (broader level)', category: 'Level' },
    { key: 'Alt+↓', action: 'Drill down (detailed level)', category: 'Level' },
    { key: 'Home', action: 'Reset to Component level', category: 'Level' },
    { key: 'Cmd+Z', action: 'Undo', category: 'History' },
    { key: 'Cmd+Shift+Z', action: 'Redo', category: 'History' },
] as const;
