/**
 * Unified Canvas Shortcuts Hook
 * 
 * SINGLE authoritative keyboard handler for the entire canvas workspace.
 * Replaces: inline useEffect in CanvasWorkspace, useCanvasNavigation, useCanvasKeyboard.
 * 
 * Shortcuts:
 * - ⌘K → Command Palette
 * - ⌘⇧K → AI Assistant
 * - ⌘F → Search (via Command Palette)
 * - ⌘Z → Undo
 * - ⌘⇧Z / ⌘Y → Redo
 * - ⌘C → Copy selected nodes
 * - ⌘V → Paste copied nodes
 * - ⌘A → Select all
 * - ⌘0 → Fit view
 * - ⌘1-7 → Jump to lifecycle phase zone
 * - ⌘← / ⌘→ → Previous / Next phase
 * - Delete/Backspace → Delete selected nodes
 * - Escape → Close modals, blur inputs
 * - Tab / Shift+Tab → Navigate between nodes (accessibility)
 * - Arrow keys → Move selected nodes (when no modifier)
 * 
 * @doc.type hook
 * @doc.purpose Unified keyboard shortcut handler for canvas
 * @doc.layer product
 * @doc.pattern Keyboard Shortcut
 */

import { useEffect, useRef, useCallback } from 'react';
import { LifecyclePhase } from '@/types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export interface CanvasShortcutsConfig {
    /** Whether shortcuts are enabled */
    enabled: boolean;

    // --- Undo/Redo ---
    canUndo: boolean;
    canRedo: boolean;
    onUndo: () => void;
    onRedo: () => void;

    // --- Clipboard ---
    onCopy: () => void;
    onPaste: () => void;

    // --- Selection ---
    onSelectAll: () => void;
    onDeleteSelected: () => void;

    // --- Navigation ---
    onZoomToPhase: (phase: LifecyclePhase) => void;
    onFitView: () => void;
    onPrevPhase: () => void;
    onNextPhase: () => void;

    // --- Modals ---
    onOpenCommandPalette: () => void;
    onOpenAI: () => void;
    onCloseModals: () => void;

    // --- Node Movement (Accessibility) ---
    onMoveSelectedNodes?: (dx: number, dy: number) => void;

    // --- Node Navigation (Accessibility) ---
    onFocusNextNode?: () => void;
    onFocusPrevNode?: () => void;

    // --- Inspector ---
    onOpenInspector?: () => void;

    // --- Fit Selection ---
    /** Fit the viewport to the current selection (F key); falls back to fit-all if nothing selected. */
    onFitSelection?: () => void;

    // --- Help ---
    /** Open the keyboard shortcut reference sheet (? key). */
    onShowShortcuts?: () => void;
}

// ============================================================================
// Constants
// ============================================================================

const PHASE_ORDER: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

const isMac = typeof navigator !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(navigator.platform);
const modKey = (e: KeyboardEvent) => (isMac ? e.metaKey : e.ctrlKey);
/** Nudge step matches the canvas snapGrid (16px grid) so Arrow-key moves land on-grid. */
const GRID_STEP = 16;

// ============================================================================
// Hook
// ============================================================================

/**
 * Single unified keyboard shortcut handler.
 * 
 * Designed to be used ONCE at the workspace root. All previous keyboard
 * systems (inline useEffect, useCanvasNavigation, useCanvasKeyboard)
 * are replaced by this hook.
 */
export function useCanvasShortcuts(config: CanvasShortcutsConfig): void {
    // Use a ref to always have the latest config without re-registering the listener
    const configRef = useRef(config);
    configRef.current = config;

    const handleKeyDown = useCallback((event: KeyboardEvent) => {
        const c = configRef.current;
        if (!c.enabled) return;

        // Input guard: skip if user is typing in a form element
        const el = document.activeElement as HTMLElement;
        const isTyping =
            el?.tagName === 'INPUT' ||
            el?.tagName === 'TEXTAREA' ||
            el?.tagName === 'SELECT' ||
            el?.isContentEditable ||
            el?.getAttribute('role') === 'textbox' ||
            // Monaco Editor embeds a hidden <textarea> inside .monaco-editor;
            // the focused element may be the textarea or a child div.
            el?.closest('.monaco-editor') !== null ||
            // CodeMirror 6 uses a contenteditable div inside .cm-editor.
            el?.closest('.cm-editor') !== null;

        if (isTyping) {
            if (event.key === 'Escape') {
                el.blur();
                event.preventDefault();
            }
            return;
        }

        const { key } = event;
        const mod = modKey(event);

        // ⌘K → Command Palette (no shift)
        if (mod && key.toLowerCase() === 'k' && !event.shiftKey) {
            event.preventDefault();
            c.onOpenCommandPalette();
            return;
        }

        // ⌘⇧K → AI Assistant
        if (mod && key.toLowerCase() === 'k' && event.shiftKey) {
            event.preventDefault();
            c.onOpenAI();
            return;
        }

        // / → Search / Command Palette (Figma / Linear convention).
        // ⌘F intentionally removed — it conflicts with the browser's native
        // find-in-page shortcut and silently swallows it.
        if (key === '/' && !mod && !event.shiftKey) {
            event.preventDefault();
            c.onOpenCommandPalette();
            return;
        }

        // ? → Keyboard shortcut reference sheet
        if (key === '?' && !mod) {
            event.preventDefault();
            c.onShowShortcuts?.();
            return;
        }

        // I → Toggle Inspector panel
        if (key === 'i' && !mod && !event.shiftKey) {
            event.preventDefault();
            c.onOpenInspector?.();
            return;
        }

        // F → Fit selection (falls back to fit-all when nothing selected)
        if (key === 'f' && !mod && !event.shiftKey) {
            event.preventDefault();
            // onFitSelection prefers selected nodes; falls back to onFitView
            if (c.onFitSelection) c.onFitSelection();
            else c.onFitView();
            return;
        }

        // ⌘Z → Undo
        if (mod && key === 'z' && !event.shiftKey) {
            event.preventDefault();
            if (c.canUndo) c.onUndo();
            return;
        }

        // ⌘⇧Z or ⌘Y → Redo
        if (mod && ((event.shiftKey && key === 'z') || key === 'y')) {
            event.preventDefault();
            if (c.canRedo) c.onRedo();
            return;
        }

        // ⌘A → Select all
        if (mod && key === 'a') {
            event.preventDefault();
            c.onSelectAll();
            return;
        }

        // ⌘C → Copy
        if (mod && key === 'c') {
            event.preventDefault();
            c.onCopy();
            return;
        }

        // ⌘V → Paste
        if (mod && key === 'v') {
            event.preventDefault();
            c.onPaste();
            return;
        }

        // ⌘0 → Fit view
        if (mod && key === '0') {
            event.preventDefault();
            c.onFitView();
            return;
        }

        // ⌘1-7 → Jump to lifecycle phase
        if (mod && /^[1-7]$/.test(key)) {
            event.preventDefault();
            const phaseIndex = parseInt(key) - 1;
            c.onZoomToPhase(PHASE_ORDER[phaseIndex]);
            return;
        }

        // ⌘← → Previous phase
        if (mod && key === 'ArrowLeft') {
            event.preventDefault();
            c.onPrevPhase();
            return;
        }

        // ⌘→ → Next phase
        if (mod && key === 'ArrowRight') {
            event.preventDefault();
            c.onNextPhase();
            return;
        }

        // Delete / Backspace → Delete selected
        if (key === 'Delete' || key === 'Backspace') {
            event.preventDefault();
            c.onDeleteSelected();
            return;
        }

        // Escape → Close modals
        if (key === 'Escape') {
            c.onCloseModals();
            return;
        }

        // Tab → Navigate between nodes (accessibility)
        if (key === 'Tab' && !mod) {
            const handler = event.shiftKey ? c.onFocusPrevNode : c.onFocusNextNode;
            if (handler) {
                event.preventDefault();
                handler();
            }
            return;
        }

        // Arrow keys (without modifier) → Nudge selected nodes
        if (!mod && !event.shiftKey && ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(key)) {
            if (c.onMoveSelectedNodes) {
                event.preventDefault();
                const dx = key === 'ArrowLeft' ? -GRID_STEP : key === 'ArrowRight' ? GRID_STEP : 0;
                const dy = key === 'ArrowUp' ? -GRID_STEP : key === 'ArrowDown' ? GRID_STEP : 0;
                c.onMoveSelectedNodes(dx, dy);
            }
            return;
        }
    }, []);

    useEffect(() => {
        if (!configRef.current.enabled) return;

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown, config.enabled]);
}

export default useCanvasShortcuts;
