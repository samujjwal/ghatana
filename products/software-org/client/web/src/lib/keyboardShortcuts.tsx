/**
 * Keyboard Shortcuts System
 *
 * <p><b>Purpose</b><br>
 * Global keyboard shortcuts for persona dashboard navigation and actions.
 * Provides power-user features with accessible keyboard navigation.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useKeyboardShortcuts } from '@/lib/keyboardShortcuts';
 *
 * useKeyboardShortcuts({
 *   'Ctrl+1': () => navigate('/hitl'),
 *   'Ctrl+2': () => navigate('/workflows'),
 *   'Ctrl+R': () => refetchData(),
 * });
 * }</pre>
 *
 * @doc.type utility
 * @doc.purpose Keyboard shortcuts system
 * @doc.layer product
 * @doc.pattern Hook + Event Listener
 */

import { useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useAtom } from 'jotai';
import { userProfileAtom, personaConfigAtom } from '@/state/jotai/atoms';
import { useToast } from './toast';

/**
 * Keyboard shortcut handler type
 */
export type KeyboardShortcutHandler = () => void;

/**
 * Keyboard shortcut map
 */
export type KeyboardShortcuts = Record<string, KeyboardShortcutHandler>;

/**
 * Parse keyboard event into shortcut string
 */
function getShortcutString(event: KeyboardEvent): string {
    const parts: string[] = [];

    if (event.ctrlKey || event.metaKey) parts.push('Ctrl');
    if (event.altKey) parts.push('Alt');
    if (event.shiftKey) parts.push('Shift');

    // Get the actual key
    const key = event.key.toUpperCase();
    if (key !== 'CONTROL' && key !== 'ALT' && key !== 'SHIFT' && key !== 'META') {
        parts.push(key);
    }

    return parts.join('+');
}

/**
 * Hook for registering keyboard shortcuts
 */
export function useKeyboardShortcuts(shortcuts: KeyboardShortcuts) {
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            // Don't trigger shortcuts when typing in inputs
            const target = event.target as HTMLElement;
            if (
                target.tagName === 'INPUT' ||
                target.tagName === 'TEXTAREA' ||
                target.isContentEditable
            ) {
                return;
            }

            const shortcutString = getShortcutString(event);
            const handler = shortcuts[shortcutString];

            if (handler) {
                event.preventDefault();
                handler();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [shortcuts]);
}

/**
 * Persona-specific keyboard shortcuts
 */
export function usePersonaKeyboardShortcuts() {
    const navigate = useNavigate();
    const [userProfile] = useAtom(userProfileAtom);
    const [personaConfig] = useAtom(personaConfigAtom);
    const { showInfo } = useToast();

    const shortcuts = useCallback((): KeyboardShortcuts => {
        if (!userProfile || !personaConfig) {
            return {
                '/': () => {
                    showInfo('Press Ctrl+H to see all keyboard shortcuts', 3000);
                },
            };
        }

        const quickActions = personaConfig.quickActions.slice(0, 6);
        const actionShortcuts: KeyboardShortcuts = {};

        // Map Ctrl+1 through Ctrl+6 to quick actions
        quickActions.forEach((action, index) => {
            actionShortcuts[`Ctrl+${index + 1}`] = () => {
                showInfo(`Navigating to ${action.title}...`, 2000);
                navigate(action.href);
            };
        });

        return {
            ...actionShortcuts,
            'Ctrl+H': () => navigate('/'),
            'Ctrl+R': () => {
                window.location.reload();
            },
            'Ctrl+K': () => {
                // Future: Open command palette
                showInfo('Command palette coming soon!', 3000);
            },
            '/': () => {
                // Show shortcut help
                showInfo('Press Ctrl+H to go home, Ctrl+1-6 for quick actions', 4000);
            },
            'ESCAPE': () => {
                // Close any open modals/dialogs
                document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
            },
        };
    }, [userProfile, personaConfig, navigate, showInfo]);

    useKeyboardShortcuts(shortcuts());
}

/**
 * Keyboard shortcut help component
 */
export interface ShortcutHelpProps {
    /**
     * Whether to show the help overlay
     */
    isOpen: boolean;

    /**
     * Close handler
     */
    onClose: () => void;
}

/**
 * Keyboard shortcuts help overlay
 */
export function KeyboardShortcutHelp({ isOpen, onClose }: ShortcutHelpProps) {
    const [userProfile] = useAtom(userProfileAtom);
    const [personaConfig] = useAtom(personaConfigAtom);

    useEffect(() => {
        if (!isOpen) return;

        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                onClose();
            }
        };

        window.addEventListener('keydown', handleEscape);
        return () => window.removeEventListener('keydown', handleEscape);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    const quickActions = personaConfig?.quickActions.slice(0, 6) || [];

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
            onClick={onClose}
        >
            <div
                className="bg-white dark:bg-neutral-800 rounded-2xl p-8 max-w-2xl w-full mx-4 shadow-2xl animate-fade-in"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center justify-between mb-6">
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-200">
                        Keyboard Shortcuts
                    </h2>
                    <button
                        onClick={onClose}
                        className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                        aria-label="Close"
                    >
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                <div className="space-y-6">
                    {/* Quick Actions */}
                    {userProfile && quickActions.length > 0 && (
                        <div>
                            <h3 className="text-sm font-semibold text-slate-600 dark:text-neutral-400 uppercase mb-3">
                                Quick Actions
                            </h3>
                            <div className="space-y-2">
                                {quickActions.map((action, index) => (
                                    <div key={action.title} className="flex items-center justify-between py-2">
                                        <span className="text-slate-700 dark:text-neutral-300">{action.title}</span>
                                        <kbd className="px-3 py-1 text-sm font-mono bg-slate-100 dark:bg-neutral-700 rounded border border-slate-300 dark:border-neutral-600">
                                            Ctrl+{index + 1}
                                        </kbd>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Navigation */}
                    <div>
                        <h3 className="text-sm font-semibold text-slate-600 dark:text-neutral-400 uppercase mb-3">
                            Navigation
                        </h3>
                        <div className="space-y-2">
                            <div className="flex items-center justify-between py-2">
                                <span className="text-slate-700 dark:text-neutral-300">Go to Home</span>
                                <kbd className="px-3 py-1 text-sm font-mono bg-slate-100 dark:bg-neutral-700 rounded border border-slate-300 dark:border-neutral-600">
                                    Ctrl+H
                                </kbd>
                            </div>
                            <div className="flex items-center justify-between py-2">
                                <span className="text-slate-700 dark:text-neutral-300">Reload Page</span>
                                <kbd className="px-3 py-1 text-sm font-mono bg-slate-100 dark:bg-neutral-700 rounded border border-slate-300 dark:border-neutral-600">
                                    Ctrl+R
                                </kbd>
                            </div>
                            <div className="flex items-center justify-between py-2">
                                <span className="text-slate-700 dark:text-neutral-300">Command Palette</span>
                                <kbd className="px-3 py-1 text-sm font-mono bg-slate-100 dark:bg-neutral-700 rounded border border-slate-300 dark:border-neutral-600">
                                    Ctrl+K
                                </kbd>
                            </div>
                        </div>
                    </div>

                    {/* General */}
                    <div>
                        <h3 className="text-sm font-semibold text-slate-600 dark:text-neutral-400 uppercase mb-3">
                            General
                        </h3>
                        <div className="space-y-2">
                            <div className="flex items-center justify-between py-2">
                                <span className="text-slate-700 dark:text-neutral-300">Show Help</span>
                                <kbd className="px-3 py-1 text-sm font-mono bg-slate-100 dark:bg-neutral-700 rounded border border-slate-300 dark:border-neutral-600">
                                    /
                                </kbd>
                            </div>
                            <div className="flex items-center justify-between py-2">
                                <span className="text-slate-700 dark:text-neutral-300">Close Modal</span>
                                <kbd className="px-3 py-1 text-sm font-mono bg-slate-100 dark:bg-neutral-700 rounded border border-slate-300 dark:border-neutral-600">
                                    Esc
                                </kbd>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="mt-6 pt-6 border-t border-slate-200 dark:border-neutral-600">
                    <p className="text-sm text-slate-600 dark:text-neutral-400 text-center">
                        Tip: Use <kbd className="px-2 py-0.5 text-xs font-mono bg-slate-100 dark:bg-neutral-700 rounded">Ctrl</kbd> + number keys for quick navigation
                    </p>
                </div>
            </div>
        </div>
    );
}

/**
 * Hook for showing keyboard shortcut help
 */
export function useKeyboardShortcutHelp() {
    const [isOpen, setIsOpen] = useState(false);

    const show = useCallback(() => setIsOpen(true), []);
    const hide = useCallback(() => setIsOpen(false), []);
    const toggle = useCallback(() => setIsOpen(prev => !prev), []);

    return {
        isOpen,
        show,
        hide,
        toggle,
        HelpComponent: () => <KeyboardShortcutHelp isOpen={isOpen} onClose={hide} />,
    };
}

// Need to import useState
import { useState } from 'react';
