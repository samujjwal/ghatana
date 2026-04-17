/**
 * Keyboard Shortcuts Hook
 * 
 * Provides keyboard shortcut support for canvas operations.
 * Handles undo/redo, export, and custom shortcuts.
 * 
 * @doc.type utility
 * @doc.purpose Keyboard shortcuts for canvas operations
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useEffect, useCallback } from 'react';
import type { CanvasHistoryManager } from './canvasHistory';
import type { ExportFormat } from './canvasExport';

export interface ShortcutConfig {
    undo?: boolean;
    redo?: boolean;
    export?: boolean;
    save?: boolean;
    custom?: Record<string, () => void>;
}

const DEFAULT_CONFIG: ShortcutConfig = {
    undo: true,
    redo: true,
    export: true,
    save: true,
};

/**
 * Hook for keyboard shortcuts
 */
export function useKeyboardShortcuts<T>(
    history: CanvasHistoryManager<T>,
    callbacks: {
        onUndo?: (state: T | null) => void;
        onRedo?: (state: T | null) => void;
        onExport?: (format: ExportFormat) => void;
        onSave?: () => void;
    },
    config: ShortcutConfig = DEFAULT_CONFIG
) {
    const handleKeyDown = useCallback(
        (event: KeyboardEvent) => {
            const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
            const modifier = isMac ? event.metaKey : event.ctrlKey;

            // Undo: Cmd/Ctrl + Z
            if (config.undo && modifier && event.key === 'z' && !event.shiftKey) {
                event.preventDefault();
                if (history.canUndo() && callbacks.onUndo) {
                    const newState = history.undo();
                    callbacks.onUndo(newState);
                }
                return;
            }

            // Redo: Cmd/Ctrl + Shift + Z
            if (config.redo && modifier && event.key === 'z' && event.shiftKey) {
                event.preventDefault();
                if (history.canRedo() && callbacks.onRedo) {
                    const newState = history.redo();
                    callbacks.onRedo(newState);
                }
                return;
            }

            // Save: Cmd/Ctrl + S
            if (config.save && modifier && event.key === 's') {
                event.preventDefault();
                if (callbacks.onSave) {
                    callbacks.onSave();
                }
                return;
            }

            // Export: Cmd/Ctrl + E
            if (config.export && modifier && event.key === 'e') {
                event.preventDefault();
                if (callbacks.onExport) {
                    callbacks.onExport('png'); // Default to PNG
                }
                return;
            }

            // Custom shortcuts
            if (config.custom) {
                for (const [key, handler] of Object.entries(config.custom)) {
                    const [modifiers, keyName] = key.split('+').map(k => k.trim().toLowerCase());
                    const hasModifier = modifiers === 'cmd' || modifiers === 'ctrl';

                    if (hasModifier && modifier && event.key.toLowerCase() === keyName.toLowerCase()) {
                        event.preventDefault();
                        handler();
                        return;
                    }
                }
            }
        },
        [history, callbacks, config]
    );

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);
}

/**
 * Get keyboard shortcut display text
 */
export function getShortcutText(shortcut: string): string {
    const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    const modifier = isMac ? '⌘' : 'Ctrl';

    const shortcuts: Record<string, string> = {
        'undo': `${modifier}+Z`,
        'redo': `${modifier}+Shift+Z`,
        'save': `${modifier}+S`,
        'export': `${modifier}+E`,
    };

    return shortcuts[shortcut] || shortcut;
}

/**
 * Shortcut display component
 * Note: This component requires JSX. If needed, move to a .tsx file or
 * implement as a React component in a separate file.
 */
/*
export function ShortcutBadge({ shortcut }: { shortcut: string }) {
    return (
        <span
            style={{
                fontSize: '0.75rem',
                opacity: 0.7,
                fontFamily: 'monospace',
                backgroundColor: 'rgba(0, 0, 0, 0.1)',
                padding: '2px 6px',
                borderRadius: '4px',
                marginLeft: '8px',
            }}
        >
            {getShortcutText(shortcut)}
        </span>
    );
}
*/
