/**
 * Keyboard Shortcuts Component
 * 
 * Modal displaying available keyboard shortcuts.
 * Opens with Cmd/Ctrl + / or ?
 * 
 * @doc.type component
 * @doc.purpose Keyboard shortcuts reference
 * @doc.layer frontend
 * @doc.pattern Modal Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import { X, Command, Keyboard } from 'lucide-react';
import { cn, textStyles, bgStyles, cardStyles } from '../../lib/theme';

/**
 * Shortcut category
 */
interface ShortcutCategory {
    name: string;
    shortcuts: Shortcut[];
}

/**
 * Individual shortcut
 */
interface Shortcut {
    keys: string[];
    description: string;
}

/**
 * Detect if Mac
 */
const isMac = typeof navigator !== 'undefined' && navigator.platform.toUpperCase().indexOf('MAC') >= 0;

/**
 * Get modifier key display
 */
const getModifierKey = () => (isMac ? '⌘' : 'Ctrl');

/**
 * Shortcut categories
 */
const shortcutCategories: ShortcutCategory[] = [
    {
        name: 'Navigation',
        shortcuts: [
            { keys: [getModifierKey(), 'K'], description: 'Open global search' },
            { keys: [getModifierKey(), '/'], description: 'Show keyboard shortcuts' },
            { keys: ['G', 'D'], description: 'Go to Dashboard' },
            { keys: ['G', 'C'], description: 'Go to Collections' },
            { keys: ['G', 'W'], description: 'Go to Workflows' },
            { keys: ['G', 'S'], description: 'Go to SQL Workspace' },
            { keys: ['G', 'L'], description: 'Go to Lineage Explorer' },
        ],
    },
    {
        name: 'Actions',
        shortcuts: [
            { keys: [getModifierKey(), 'N'], description: 'Create new item' },
            { keys: [getModifierKey(), 'S'], description: 'Save current item' },
            { keys: [getModifierKey(), 'Enter'], description: 'Run query / Execute' },
            { keys: ['Esc'], description: 'Close modal / Cancel' },
        ],
    },
    {
        name: 'SQL Workspace',
        shortcuts: [
            { keys: [getModifierKey(), 'Enter'], description: 'Run query' },
            { keys: [getModifierKey(), 'Shift', 'F'], description: 'Format SQL' },
            { keys: [getModifierKey(), 'L'], description: 'Clear editor' },
            { keys: [getModifierKey(), 'E'], description: 'Export results' },
        ],
    },
    {
        name: 'Workflow Designer',
        shortcuts: [
            { keys: [getModifierKey(), 'Z'], description: 'Undo' },
            { keys: [getModifierKey(), 'Shift', 'Z'], description: 'Redo' },
            { keys: ['Delete'], description: 'Delete selected node' },
            { keys: [getModifierKey(), 'A'], description: 'Select all nodes' },
            { keys: ['+'], description: 'Zoom in' },
            { keys: ['-'], description: 'Zoom out' },
            { keys: ['0'], description: 'Reset zoom' },
        ],
    },
    {
        name: 'General',
        shortcuts: [
            { keys: [getModifierKey(), 'Shift', 'T'], description: 'Toggle dark mode' },
            { keys: ['?'], description: 'Show help' },
            { keys: [getModifierKey(), ','], description: 'Open settings' },
        ],
    },
];

interface KeyboardShortcutsProps {
    isOpen: boolean;
    onClose: () => void;
}

/**
 * Keyboard Shortcuts Modal
 */
export function KeyboardShortcuts({ isOpen, onClose }: KeyboardShortcutsProps): React.ReactElement | null {
    // Close on Escape
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && isOpen) {
                onClose();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 overflow-y-auto">
            {/* Backdrop */}
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="relative min-h-screen flex items-center justify-center p-4">
                <div
                    className={cn(
                        'w-full max-w-2xl rounded-xl shadow-2xl overflow-hidden',
                        bgStyles.surface
                    )}
                >
                    {/* Header */}
                    <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                        <div className="flex items-center gap-3">
                            <Keyboard className="h-5 w-5 text-blue-500" />
                            <h2 className={textStyles.h2}>Keyboard Shortcuts</h2>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                        >
                            <X className="h-5 w-5" />
                        </button>
                    </div>

                    {/* Content */}
                    <div className="p-6 max-h-[60vh] overflow-y-auto">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            {shortcutCategories.map((category) => (
                                <div key={category.name}>
                                    <h3 className={cn(textStyles.h4, 'mb-3')}>{category.name}</h3>
                                    <div className="space-y-2">
                                        {category.shortcuts.map((shortcut, index) => (
                                            <div
                                                key={index}
                                                className="flex items-center justify-between py-1"
                                            >
                                                <span className={textStyles.small}>{shortcut.description}</span>
                                                <div className="flex items-center gap-1">
                                                    {shortcut.keys.map((key, keyIndex) => (
                                                        <React.Fragment key={keyIndex}>
                                                            <kbd className={cn(
                                                                'px-2 py-1 text-xs font-medium rounded',
                                                                'bg-gray-100 dark:bg-gray-700',
                                                                'border border-gray-200 dark:border-gray-600',
                                                                'text-gray-700 dark:text-gray-300'
                                                            )}>
                                                                {key}
                                                            </kbd>
                                                            {keyIndex < shortcut.keys.length - 1 && (
                                                                <span className="text-gray-400 text-xs">+</span>
                                                            )}
                                                        </React.Fragment>
                                                    ))}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Footer */}
                    <div className="px-6 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
                        <p className={cn(textStyles.xs, 'text-center')}>
                            Press <kbd className="px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 rounded text-xs">Esc</kbd> to close
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 * Hook to manage keyboard shortcuts modal
 */
export function useKeyboardShortcuts() {
    const [isOpen, setIsOpen] = useState(false);

    const open = useCallback(() => setIsOpen(true), []);
    const close = useCallback(() => setIsOpen(false), []);
    const toggle = useCallback(() => setIsOpen((prev) => !prev), []);

    // Keyboard shortcut (Cmd/Ctrl + / or ?)
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key === '/') {
                e.preventDefault();
                toggle();
            }
            if (e.key === '?' && !e.metaKey && !e.ctrlKey && !e.altKey) {
                // Only trigger if not in an input
                const target = e.target as HTMLElement;
                if (target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA') {
                    e.preventDefault();
                    toggle();
                }
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [toggle]);

    return { isOpen, open, close, toggle };
}

export default KeyboardShortcuts;
