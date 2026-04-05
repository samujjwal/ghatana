/**
 * Keyboard Navigation Hook
 * 
 * Provides global keyboard shortcuts for navigation actions.
 * Supports Cmd/Ctrl modifiers for cross-platform compatibility.
 * 
 * @doc.type hook
 * @doc.purpose Global keyboard navigation shortcuts
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useEffect } from 'react';
import { useNavigate } from 'react-router';

export interface KeyboardNavigationOptions {
    /** Enable Cmd/Ctrl + P for project switcher */
    enableProjectSwitcher?: boolean;
    /** Enable Cmd/Ctrl + Shift + W for workspace switcher */
    enableWorkspaceSwitcher?: boolean;
    /** Enable Cmd/Ctrl + N for new project */
    enableNewProject?: boolean;
    /** Enable Cmd/Ctrl + H for home */
    enableHome?: boolean;
    /** Callback when project switcher is triggered */
    onProjectSwitcher?: () => void;
    /** Callback when workspace switcher is triggered */
    onWorkspaceSwitcher?: () => void;
    /** Callback when new project is triggered */
    onNewProject?: () => void;
}

/**
 * useKeyboardNavigation Hook
 * 
 * Sets up global keyboard shortcuts for navigation.
 * 
 * @example
 * ```tsx
 * useKeyboardNavigation({
 *   enableProjectSwitcher: true,
 *   onProjectSwitcher: () => setShowProjectSwitcher(true),
 * });
 * ```
 */
export function useKeyboardNavigation({
    enableProjectSwitcher = true,
    enableWorkspaceSwitcher = true,
    enableNewProject = true,
    enableHome = true,
    onProjectSwitcher,
    onWorkspaceSwitcher,
    onNewProject,
}: KeyboardNavigationOptions = {}) {
    const navigate = useNavigate();

    useEffect(() => {
        function handleKeyDown(event: KeyboardEvent) {
            // Check for Cmd (Mac) or Ctrl (Windows/Linux)
            const isMod = event.metaKey || event.ctrlKey;

            // Ignore if user is typing in an input field
            const target = event.target as HTMLElement;
            if (
                target.tagName === 'INPUT' ||
                target.tagName === 'TEXTAREA' ||
                target.isContentEditable
            ) {
                return;
            }

            // Cmd/Ctrl + P: Project Switcher
            if (enableProjectSwitcher && isMod && event.key === 'p') {
                event.preventDefault();
                if (onProjectSwitcher) {
                    onProjectSwitcher();
                } else {
                    // Default: Navigate to home (which shows projects)
                    navigate('/app');
                }
                return;
            }

            // Cmd/Ctrl + Shift + W: Workspace Switcher
            if (enableWorkspaceSwitcher && isMod && event.shiftKey && event.key === 'W') {
                event.preventDefault();
                if (onWorkspaceSwitcher) {
                    onWorkspaceSwitcher();
                } else {
                    // Default: Navigate to workspaces page
                    navigate('/app/workspaces');
                }
                return;
            }

            // Cmd/Ctrl + N: New Project
            if (enableNewProject && isMod && event.key === 'n') {
                event.preventDefault();
                if (onNewProject) {
                    onNewProject();
                } else {
                    // Default: Navigate to home (new project flow)
                    navigate('/app');
                }
                return;
            }

            // Cmd/Ctrl + H: Home
            if (enableHome && isMod && event.key === 'h') {
                event.preventDefault();
                navigate('/app');
                return;
            }
        }

        // Add event listener
        document.addEventListener('keydown', handleKeyDown);

        // Cleanup
        return () => {
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [
        enableProjectSwitcher,
        enableWorkspaceSwitcher,
        enableNewProject,
        enableHome,
        onProjectSwitcher,
        onWorkspaceSwitcher,
        onNewProject,
        navigate,
    ]);
}

export default useKeyboardNavigation;
