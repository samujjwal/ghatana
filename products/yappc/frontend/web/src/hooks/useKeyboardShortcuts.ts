/**
 * useKeyboardShortcuts - Keyboard Shortcut System
 * 
 * @doc.type hook
 * @doc.purpose Global keyboard shortcut management
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useEffect, useCallback } from 'react';
import type { Tool } from '../state/atoms/unifiedCanvasAtom';

export interface ShortcutDefinition {
    key: string;
    ctrl?: boolean;
    meta?: boolean;  // Cmd on Mac, Windows key on Windows
    shift?: boolean;
    alt?: boolean;
    description: string;
    action: () => void;
    preventDefault?: boolean;
}

export interface UseKeyboardShortcutsProps {
    // Tool shortcuts
    onToolChange?: (tool: Tool) => void;

    // Edit shortcuts
    onUndo?: () => void;
    onRedo?: () => void;
    onCopy?: () => void;
    onPaste?: () => void;
    onDuplicate?: () => void;
    onDelete?: () => void;
    onSelectAll?: () => void;

    // View shortcuts
    onZoomIn?: () => void;
    onZoomOut?: () => void;
    onResetZoom?: () => void;
    onZoomToFit?: () => void;

    // Alignment shortcuts
    onAlignLeft?: () => void;
    onAlignCenter?: () => void;
    onAlignRight?: () => void;
    onAlignTop?: () => void;
    onAlignMiddle?: () => void;
    onAlignBottom?: () => void;

    // Layer shortcuts
    onBringForward?: () => void;
    onSendBackward?: () => void;
    onBringToFront?: () => void;
    onSendToBack?: () => void;

    // Group shortcuts
    onGroup?: () => void;
    onUngroup?: () => void;

    // Navigation shortcuts
    onZoomIntoNode?: () => void;
    onZoomOutToParent?: () => void;

    // AI shortcuts
    onOpenAI?: () => void;

    // Save shortcut
    onSave?: () => void;

    // Search
    onSearch?: () => void;

    // Enabled flag
    enabled?: boolean;
}

export function useKeyboardShortcuts(props: UseKeyboardShortcutsProps = {}) {
    const { enabled = true } = props;

    // Build shortcuts map
    const shortcuts = useCallback((): ShortcutDefinition[] => {
        const isMac = typeof window !== 'undefined' && /Mac|iPhone|iPad|iPod/.test(navigator.platform);
        const modKey = isMac ? 'meta' : 'ctrl';

        return [
            // Tools
            { key: 'v', description: 'Select tool', action: () => props.onToolChange?.('select') },
            { key: 'h', description: 'Pan tool', action: () => props.onToolChange?.('pan') },
            { key: 'p', description: 'Draw tool', action: () => props.onToolChange?.('draw') },
            { key: 't', description: 'Text tool', action: () => props.onToolChange?.('text') },
            { key: 'c', description: 'Code tool', action: () => props.onToolChange?.('code') },
            { key: 'n', description: 'Sticky note', action: () => props.onToolChange?.('sticky') },
            { key: 'r', description: 'Rectangle tool', action: () => props.onToolChange?.('rectangle') },
            { key: 'o', description: 'Ellipse tool', action: () => props.onToolChange?.('ellipse') },
            { key: 'l', description: 'Line tool', action: () => props.onToolChange?.('line') },
            { key: 'a', description: 'Arrow tool', action: () => props.onToolChange?.('arrow') },

            // Edit
            { key: 'z', [modKey]: true, description: 'Undo', action: () => props.onUndo?.(), preventDefault: true },
            { key: 'z', [modKey]: true, shift: true, description: 'Redo', action: () => props.onRedo?.(), preventDefault: true },
            { key: 'c', [modKey]: true, description: 'Copy', action: () => props.onCopy?.(), preventDefault: true },
            { key: 'v', [modKey]: true, description: 'Paste', action: () => props.onPaste?.(), preventDefault: true },
            { key: 'd', [modKey]: true, description: 'Duplicate', action: () => props.onDuplicate?.(), preventDefault: true },
            { key: 'Delete', description: 'Delete', action: () => props.onDelete?.() },
            { key: 'Backspace', description: 'Delete', action: () => props.onDelete?.() },
            { key: 'a', [modKey]: true, description: 'Select all', action: () => props.onSelectAll?.(), preventDefault: true },

            // View
            { key: '+', [modKey]: true, description: 'Zoom in', action: () => props.onZoomIn?.(), preventDefault: true },
            { key: '=', [modKey]: true, description: 'Zoom in', action: () => props.onZoomIn?.(), preventDefault: true },
            { key: '-', [modKey]: true, description: 'Zoom out', action: () => props.onZoomOut?.(), preventDefault: true },
            { key: '0', [modKey]: true, description: 'Zoom to fit', action: () => props.onZoomToFit?.(), preventDefault: true },
            { key: '1', [modKey]: true, description: 'Reset zoom', action: () => props.onResetZoom?.(), preventDefault: true },

            // Layer
            { key: ']', description: 'Bring forward', action: () => props.onBringForward?.() },
            { key: '[', description: 'Send backward', action: () => props.onSendBackward?.() },
            { key: ']', [modKey]: true, description: 'Bring to front', action: () => props.onBringToFront?.(), preventDefault: true },
            { key: '[', [modKey]: true, description: 'Send to back', action: () => props.onSendToBack?.(), preventDefault: true },

            // Group
            { key: 'g', [modKey]: true, description: 'Group', action: () => props.onGroup?.(), preventDefault: true },
            { key: 'g', [modKey]: true, shift: true, description: 'Ungroup', action: () => props.onUngroup?.(), preventDefault: true },

            // Navigation
            { key: 'ArrowDown', [modKey]: true, description: 'Zoom into node', action: () => props.onZoomIntoNode?.(), preventDefault: true },
            { key: 'ArrowUp', [modKey]: true, description: 'Zoom out to parent', action: () => props.onZoomOutToParent?.(), preventDefault: true },

            // AI
            { key: 'k', [modKey]: true, description: 'Open AI', action: () => props.onOpenAI?.(), preventDefault: true },

            // Save
            { key: 's', [modKey]: true, description: 'Save', action: () => props.onSave?.(), preventDefault: true },

            // Search
            { key: 'f', [modKey]: true, description: 'Search', action: () => props.onSearch?.(), preventDefault: true },
        ].filter(s => s.action !== undefined);
    }, [props]);

    useEffect(() => {
        if (!enabled) return;

        const handleKeyDown = (event: KeyboardEvent) => {
            const activeShortcuts = shortcuts();

            // Check if we're in an input field
            const target = event.target as HTMLElement;
            const isInputField =
                target.tagName === 'INPUT' ||
                target.tagName === 'TEXTAREA' ||
                target.contentEditable === 'true';

            // Allow certain shortcuts even in input fields
            const allowInInputs = ['s', 'z', 'k', 'f'];

            for (const shortcut of activeShortcuts) {
                const keyMatches = event.key.toLowerCase() === shortcut.key.toLowerCase() ||
                    event.code === shortcut.key;

                const ctrlMatches = shortcut.ctrl ? event.ctrlKey : !event.ctrlKey;
                const metaMatches = shortcut.meta ? event.metaKey : !event.metaKey;
                const shiftMatches = shortcut.shift ? event.shiftKey : !event.shiftKey;
                const altMatches = shortcut.alt ? event.altKey : !event.altKey;

                if (keyMatches && ctrlMatches && metaMatches && shiftMatches && altMatches) {
                    // Skip if in input field and shortcut not allowed
                    if (isInputField && !allowInInputs.includes(shortcut.key.toLowerCase())) {
                        continue;
                    }

                    if (shortcut.preventDefault) {
                        event.preventDefault();
                    }

                    shortcut.action();
                    break;
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown);

        return () => {
            window.removeEventListener('keydown', handleKeyDown);
        };
    }, [enabled, shortcuts]);

    // Return shortcuts for display in help panel
    return {
        shortcuts: shortcuts()
    };
}
