/**
 * @ghatana/yappc-sketch - useSketchKeyboard Hook
 *
 * Production-grade hook for keyboard shortcuts in sketch mode.
 *
 * @doc.type hook
 * @doc.purpose Keyboard shortcut handling for sketch tools
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useCallback } from 'react';

import type { SketchTool } from '../types';

/**
 * Keyboard shortcut mapping
 */
const TOOL_SHORTCUTS: Record<string, SketchTool> = {
  v: 'select',
  p: 'pen',
  h: 'highlighter',
  e: 'eraser',
  r: 'rectangle',
  o: 'ellipse',
  l: 'line',
  a: 'arrow',
  t: 'text',
  s: 'sticky',
  i: 'image',
};

/**
 * Parameters for useSketchKeyboard hook
 */
export interface UseSketchKeyboardParams {
  /** Whether keyboard shortcuts are enabled */
  enabled?: boolean;
  /** Callback when tool changes via keyboard */
  onToolChange: (tool: SketchTool) => void;
  /** Callback for undo action (Ctrl/Cmd+Z) */
  onUndo?: () => void;
  /** Callback for redo action (Ctrl/Cmd+Shift+Z or Ctrl/Cmd+Y) */
  onRedo?: () => void;
  /** Callback for delete action (Delete/Backspace) */
  onDelete?: () => void;
  /** Callback for select all action (Ctrl/Cmd+A) */
  onSelectAll?: () => void;
  /** Callback for escape action */
  onEscape?: () => void;
}

/**
 * Hook for handling keyboard shortcuts in sketch mode.
 *
 * Features:
 * - Tool switching via single key press
 * - Undo/redo support
 * - Delete selected elements
 * - Select all
 * - Escape to deselect
 * - Ignores input when typing in text fields
 *
 * @param params - Hook parameters
 *
 * @example
 * ```tsx
 * useSketchKeyboard({
 *   enabled: true,
 *   onToolChange: (tool) => setActiveTool(tool),
 *   onUndo: () => history.undo(),
 *   onRedo: () => history.redo(),
 *   onDelete: () => deleteSelected(),
 * });
 * ```
 */
export function useSketchKeyboard({
  enabled = true,
  onToolChange,
  onUndo,
  onRedo,
  onDelete,
  onSelectAll,
  onEscape,
}: UseSketchKeyboardParams): void {
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (!enabled) {
        return;
      }

      // Ignore if typing in input/textarea/contenteditable
      const target = event.target as HTMLElement;
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable
      ) {
        // Allow escape even in text fields
        if (event.key === 'Escape' && onEscape) {
          onEscape();
        }
        return;
      }

      const key = event.key.toLowerCase();
      const isMeta = event.metaKey || event.ctrlKey;
      const isShift = event.shiftKey;

      // Handle modifier key combinations
      if (isMeta) {
        switch (key) {
          case 'z':
            event.preventDefault();
            if (isShift) {
              onRedo?.();
            } else {
              onUndo?.();
            }
            return;
          case 'y':
            event.preventDefault();
            onRedo?.();
            return;
          case 'a':
            event.preventDefault();
            onSelectAll?.();
            return;
        }
      }

      // Handle single key shortcuts (only when no modifier)
      if (!isMeta && !isShift && !event.altKey) {
        // Tool shortcuts
        const tool = TOOL_SHORTCUTS[key];
        if (tool) {
          event.preventDefault();
          onToolChange(tool);
          return;
        }

        // Delete key
        if (key === 'delete' || key === 'backspace') {
          event.preventDefault();
          onDelete?.();
          return;
        }

        // Escape key
        if (key === 'escape') {
          event.preventDefault();
          onEscape?.();
          return;
        }
      }
    },
    [enabled, onToolChange, onUndo, onRedo, onDelete, onSelectAll, onEscape]
  );

  useEffect(() => {
    if (!enabled) {
      return;
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled, handleKeyDown]);
}

export default useSketchKeyboard;
