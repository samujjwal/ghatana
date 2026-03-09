/**
 * Global Shortcut Context Provider
 * 
 * Provides global keyboard shortcut management and command palette integration
 * for the entire application. Manages context-aware shortcuts and power user features.
 */

import CommandPalette from '@ghatana/yappc-ui/components/CommandPalette';
import { ShortcutHelper } from '@ghatana/yappc-ui';
import { useKeyboardShortcuts, Command } from '@ghatana/yappc-ui';
import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

/**
 *
 */
export interface ShortcutContextValue {
    showCommandPalette: () => void;
    hideCommandPalette: () => void;
    showShortcutHelper: () => void;
    hideShortcutHelper: () => void;
    registerCommand: (command: Command) => () => void;
    isCommandPaletteOpen: boolean;
    isShortcutHelperOpen: boolean;
}

const ShortcutContext = createContext<ShortcutContextValue | null>(null);

/**
 *
 */
export function useShortcutContext(): ShortcutContextValue {
    const context = useContext(ShortcutContext);
    if (!context) {
        throw new Error('useShortcutContext must be used within ShortcutProvider');
    }
    return context;
}

/**
 *
 */
export interface ShortcutProviderProps {
    children: React.ReactNode;
}

/**
 *
 */
export function ShortcutProvider({ children }: ShortcutProviderProps) {
    const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);
    const [isShortcutHelperOpen, setIsShortcutHelperOpen] = useState(false);
    const [commands, setCommands] = useState<Command[]>([]);

    const navigate = useNavigate();
    const location = useLocation();

    const { registerShortcut } = useKeyboardShortcuts({
        context: 'global'
    });

    // Determine current context from route
    const getCurrentContext = useCallback(() => {
        const path = location.pathname;
        if (path.includes('/build')) return 'build';
        if (path.includes('/deploy')) return 'deploy';
        if (path.includes('/monitor')) return 'monitor';
        if (path.includes('/design')) return 'design';
        if (path.includes('/canvas')) return 'canvas';
        if (path.includes('/backlog')) return 'backlog';
        if (path.includes('/overview')) return 'overview';
        if (path.includes('/project')) return 'project';
        return 'global';
    }, [location.pathname]);

    // Command palette actions
    const showCommandPalette = useCallback(() => {
        setIsCommandPaletteOpen(true);
    }, []);

    const hideCommandPalette = useCallback(() => {
        setIsCommandPaletteOpen(false);
    }, []);

    const showShortcutHelper = useCallback(() => {
        setIsShortcutHelperOpen(true);
    }, []);

    const hideShortcutHelper = useCallback(() => {
        setIsShortcutHelperOpen(false);
    }, []);

    // Register command and return cleanup function
    const registerCommand = useCallback((command: Command): (() => void) => {
        setCommands(prev => [...prev, command]);

        return () => {
            setCommands(prev => prev.filter(cmd => cmd.id !== command.id));
        };
    }, []);

    // Register global shortcuts
    useEffect(() => {
        const registrations: (() => void)[] = [];

        // Command palette (⌘K)
        registrations.push(registerShortcut({
            key: 'k',
            modifiers: ['cmd'],
            description: 'Open command palette',
            category: 'Global',
            handler: showCommandPalette
        }));

        // Shortcut helper (? or ⌘/)
        registrations.push(registerShortcut({
            key: '?',
            description: 'Show keyboard shortcuts',
            category: 'Help',
            handler: showShortcutHelper
        }));

        registrations.push(registerShortcut({
            key: '/',
            modifiers: ['cmd'],
            description: 'Show keyboard shortcuts',
            category: 'Help',
            handler: showShortcutHelper
        }));

        // Settings (⌘,)
        registrations.push(registerShortcut({
            key: ',',
            modifiers: ['cmd'],
            description: 'Open settings',
            category: 'Global',
            handler: () => navigate('/app/settings')
        }));

        // Navigation shortcuts (⌘1-6)
        const projectMatch = location.pathname.match(/\/app\/workspace\/([^/]+)\/project\/([^/]+)/);
        if (projectMatch) {
            const [, workspaceId, projectId] = projectMatch;
            const basePath = `/app/workspace/${workspaceId}/project/${projectId}`;

            const navShortcuts = [
                { key: '1', path: '/overview', label: 'Overview' },
                { key: '2', path: '/canvas', label: 'Canvas' },
                { key: '3', path: '/backlog', label: 'Backlog' },
                { key: '4', path: '/build', label: 'Build' },
                { key: '5', path: '/deploy', label: 'Deploy' },
                { key: '6', path: '/monitor', label: 'Monitor' },
            ];

            navShortcuts.forEach(({ key, path, label }) => {
                registrations.push(registerShortcut({
                    key,
                    modifiers: ['cmd'],
                    description: `Go to ${label}`,
                    category: 'Navigation',
                    handler: () => navigate(`${basePath}${path}`)
                }));
            });
        }

        // New project (⌘N)
        registrations.push(registerShortcut({
            key: 'n',
            modifiers: ['cmd'],
            description: 'Create new project',
            category: 'Actions',
            handler: () => {
                // NOTE: Open create project dialog
                showCommandPalette();
            }
        }));

        // Quick actions (⌘⇧P)
        registrations.push(registerShortcut({
            key: 'p',
            modifiers: ['cmd', 'shift'],
            description: 'Quick actions',
            category: 'Actions',
            handler: showCommandPalette
        }));

        // Global Escape handler
        registrations.push(registerShortcut({
            key: 'Escape',
            description: 'Close modals and overlays',
            category: 'Global',
            handler: () => {
                if (isCommandPaletteOpen) {
                    hideCommandPalette();
                } else if (isShortcutHelperOpen) {
                    hideShortcutHelper();
                }
            }
        }));

        // Cleanup all registrations on unmount
        return () => {
            registrations.forEach(cleanup => cleanup());
        };
    }, [
        registerShortcut,
        navigate,
        location.pathname,
        showCommandPalette,
        showShortcutHelper,
        isCommandPaletteOpen,
        isShortcutHelperOpen,
        hideCommandPalette,
        hideShortcutHelper
    ]);

    // Default application commands
    const defaultCommands: Command[] = [
        {
            id: 'nav-overview',
            title: 'Go to Overview',
            description: 'Navigate to project overview',
            category: 'navigation',
            icon: '🏠',
            shortcut: '⌘1',
            keywords: ['overview', 'home', 'dashboard'],
            action: () => {
                const projectMatch = location.pathname.match(/\/app\/workspace\/([^/]+)\/project\/([^/]+)/);
                if (projectMatch) {
                    navigate(`/app/workspace/${projectMatch[1]}/project/${projectMatch[2]}/overview`);
                }
            }
        },
        {
            id: 'help-shortcuts',
            title: 'Show Keyboard Shortcuts',
            description: 'Display available keyboard shortcuts',
            category: 'system',
            icon: '❓',
            shortcut: '?',
            keywords: ['help', 'shortcuts', 'keyboard'],
            action: showShortcutHelper
        }
    ];

    const allCommands = [...defaultCommands, ...commands];

    return (
        <ShortcutContext.Provider
            value={{
                showCommandPalette,
                hideCommandPalette,
                showShortcutHelper,
                hideShortcutHelper,
                registerCommand,
                isCommandPaletteOpen,
                isShortcutHelperOpen
            }}
        >
            {children}

            <CommandPalette
                isOpen={isCommandPaletteOpen}
                onClose={hideCommandPalette}
                commands={allCommands}
                placeholder="Type a command or search..."
            />

            <ShortcutHelper
                isVisible={isShortcutHelperOpen}
                onClose={hideShortcutHelper}
                context={getCurrentContext()}
                showCategories={true}
            />
        </ShortcutContext.Provider>
    );
}