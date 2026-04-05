/**
 * Global Keyboard Shortcuts
 * 
 * Single source of truth for all keyboard shortcuts in the application.
 * Used by CommandPalette, KeyboardShortcutsPanel, and event handlers.
 * 
 * @doc.type constant
 * @doc.purpose Keyboard shortcut definitions
 * @doc.layer product
 * @doc.pattern Constant
 */

export const KEYBOARD_SHORTCUTS = {
    GENERAL: {
        COMMAND_PALETTE: {
            keys: ['Meta+K', 'Ctrl+K'],
            description: 'Open Command Palette'
        },
        HELP: {
            keys: ['?', 'Meta+/', 'Ctrl+/'],
            description: 'Show Keyboard Shortcuts'
        },
        ESCAPE: {
            keys: ['Escape'],
            description: 'Cancel / Deselect / Close'
        },
        SEARCH: {
            keys: ['Meta+F', 'Ctrl+F'],
            description: 'Search in current page'
        },
        SETTINGS: {
            keys: ['Meta+,', 'Ctrl+,'],
            description: 'Open Settings'
        }
    },
    NAVIGATION: {
        DASHBOARD: {
            keys: ['G then D'],
            description: 'Go to Dashboard'
        },
        PROJECTS: {
            keys: ['G then P'],
            description: 'Go to Projects'
        },
        OVERVIEW: {
            keys: ['Meta+1', 'Ctrl+1'],
            description: 'Go to Overview'
        },
        CANVAS: {
            keys: ['Meta+2', 'Ctrl+2'],
            description: 'Go to Canvas'
        },
        BACKLOG: {
            keys: ['Meta+3', 'Ctrl+3'],
            description: 'Go to Backlog'
        },
        BUILD: {
            keys: ['Meta+4', 'Ctrl+4'],
            description: 'Go to Build'
        },
        DEPLOY: {
            keys: ['Meta+5', 'Ctrl+5'],
            description: 'Go to Deploy'
        },
        MONITOR: {
            keys: ['Meta+6', 'Ctrl+6'],
            description: 'Go to Monitor'
        },
        NEXT_TAB: {
            keys: ['Meta+Alt+Right', 'Ctrl+Alt+Right'],
            description: 'Next Tab'
        },
        PREV_TAB: {
            keys: ['Meta+Alt+Left', 'Ctrl+Alt+Left'],
            description: 'Previous Tab'
        }
    },
    CANVAS: {
        UNDO: {
            keys: ['Meta+Z', 'Ctrl+Z'],
            description: 'Undo last action'
        },
        REDO: {
            keys: ['Meta+Shift+Z', 'Ctrl+Shift+Z', 'Ctrl+Y'],
            description: 'Redo last action'
        },
        COPY: {
            keys: ['Meta+C', 'Ctrl+C'],
            description: 'Copy selected elements'
        },
        CUT: {
            keys: ['Meta+X', 'Ctrl+X'],
            description: 'Cut selected elements'
        },
        PASTE: {
            keys: ['Meta+V', 'Ctrl+V'],
            description: 'Paste elements'
        },
        DUPLICATE: {
            keys: ['Meta+D', 'Ctrl+D'],
            description: 'Duplicate selected elements'
        },
        DELETE: {
            keys: ['Delete', 'Backspace'],
            description: 'Delete selected elements'
        },
        SELECT_ALL: {
            keys: ['Meta+A', 'Ctrl+A'],
            description: 'Select all elements'
        },
        SAVE: {
            keys: ['Meta+S', 'Ctrl+S'],
            description: 'Save canvas'
        },
        ZOOM_IN: {
            keys: ['Meta++', 'Ctrl++'],
            description: 'Zoom in'
        },
        ZOOM_OUT: {
            keys: ['Meta+-', 'Ctrl+-'],
            description: 'Zoom out'
        },
        ZOOM_RESET: {
            keys: ['Meta+0', 'Ctrl+0'],
            description: 'Reset zoom'
        },
        FIT_VIEW: {
            keys: ['Meta+Shift+1', 'Ctrl+Shift+1'],
            description: 'Fit canvas to view'
        },
        GROUP: {
            keys: ['Meta+G', 'Ctrl+G'],
            description: 'Group selected elements'
        },
        UNGROUP: {
            keys: ['Meta+Shift+G', 'Ctrl+Shift+G'],
            description: 'Ungroup elements'
        }
    },
    PANELS: {
        TOGGLE_LEFT_PANEL: {
            keys: ['Meta+B', 'Ctrl+B'],
            description: 'Toggle Left Panel'
        },
        TOGGLE_RIGHT_PANEL: {
            keys: ['Meta+Alt+B', 'Ctrl+Alt+B'],
            description: 'Toggle Right Panel'
        },
        TOGGLE_AI: {
            keys: ['Meta+Shift+A', 'Ctrl+Shift+A'],
            description: 'Toggle AI Assistant'
        },
        TOGGLE_VALIDATION: {
            keys: ['Meta+Shift+V', 'Ctrl+Shift+V'],
            description: 'Toggle Validation Panel'
        },
        TOGGLE_CODE: {
            keys: ['Meta+Shift+C', 'Ctrl+Shift+C'],
            description: 'Toggle Code Generation'
        },
        TOGGLE_CONSOLE: {
            keys: ['Meta+Shift+J', 'Ctrl+Shift+J'],
            description: 'Toggle Console'
        }
    },
    ACTIONS: {
        NEW_PROJECT: {
            keys: ['Meta+N', 'Ctrl+N'],
            description: 'Create New Project'
        },
        NEW_WORKSPACE: {
            keys: ['Meta+Shift+N', 'Ctrl+Shift+N'],
            description: 'Create New Workspace'
        },
        SAVE: {
            keys: ['Meta+S', 'Ctrl+S'],
            description: 'Save'
        },
        REFRESH: {
            keys: ['Meta+R', 'Ctrl+R'],
            description: 'Refresh Page'
        },
        OPEN_FILE: {
            keys: ['Meta+O', 'Ctrl+O'],
            description: 'Open File'
        },
        QUICK_ACTIONS: {
            keys: ['Meta+Shift+P', 'Ctrl+Shift+P'],
            description: 'Quick Actions'
        }
    },
    TEXT_EDITING: {
        BOLD: {
            keys: ['Meta+B', 'Ctrl+B'],
            description: 'Bold text (in editors)'
        },
        ITALIC: {
            keys: ['Meta+I', 'Ctrl+I'],
            description: 'Italic text (in editors)'
        },
        UNDERLINE: {
            keys: ['Meta+U', 'Ctrl+U'],
            description: 'Underline text (in editors)'
        },
        FIND: {
            keys: ['Meta+F', 'Ctrl+F'],
            description: 'Find in text'
        },
        REPLACE: {
            keys: ['Meta+H', 'Ctrl+H'],
            description: 'Find and replace'
        }
    }
} as const;

/**
 * Shortcut categories for organized display
 */
export const SHORTCUT_CATEGORIES = [
    { id: 'GENERAL', label: 'General', icon: '⌨️' },
    { id: 'NAVIGATION', label: 'Navigation', icon: '🧭' },
    { id: 'CANVAS', label: 'Canvas', icon: '🎨' },
    { id: 'PANELS', label: 'Panels', icon: '📱' },
    { id: 'ACTIONS', label: 'Actions', icon: '⚡' },
    { id: 'TEXT_EDITING', label: 'Text Editing', icon: '✏️' }
] as const;

/**
 * Get formatted shortcut key for display (e.g., "⌘K" on Mac, "Ctrl+K" on Windows)
 */
export function formatShortcutKey(keys: string[]): string {
    const isMac = typeof navigator !== 'undefined' && navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    const key = keys[isMac ? 0 : 1] || keys[0];

    return key
        .replace('Meta', isMac ? '⌘' : 'Ctrl')
        .replace('Alt', isMac ? '⌥' : 'Alt')
        .replace('Shift', isMac ? '⇧' : 'Shift')
        .replace('Control', 'Ctrl')
        .replace('+', isMac ? '' : '+');
}

/**
 * Check if a key matches the shortcut
 */
export function matchesShortcut(
    event: KeyboardEvent,
    shortcut: { keys: string[] }
): boolean {
    const isMac = typeof navigator !== 'undefined' && navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    const targetKey = shortcut.keys[isMac ? 0 : 1] || shortcut.keys[0];

    const [modifiers, key] = targetKey.includes('+')
        ? [targetKey.slice(0, targetKey.lastIndexOf('+')), targetKey.slice(targetKey.lastIndexOf('+') + 1)]
        : ['', targetKey];

    const hasCtrl = modifiers.includes('Ctrl') || modifiers.includes('Meta');
    const hasAlt = modifiers.includes('Alt');
    const hasShift = modifiers.includes('Shift');

    return (
        event.key === key &&
        (hasCtrl ? (event.ctrlKey || event.metaKey) : !event.ctrlKey && !event.metaKey) &&
        (hasAlt ? event.altKey : !event.altKey) &&
        (hasShift ? event.shiftKey : !event.shiftKey)
    );
}

