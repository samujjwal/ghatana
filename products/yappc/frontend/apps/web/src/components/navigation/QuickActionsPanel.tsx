/**
 * Quick Actions Panel
 * 
 * Dropdown menu for quick create and common actions.
 * Replaces the sidebar "New Project" button with more comprehensive options.
 * 
 * @doc.type component
 * @doc.purpose Quick action menu
 * @doc.layer product
 * @doc.pattern Dropdown Component
 */

import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router';
import { Plus as Add, Folder, Briefcase as WorkOutline, Grid3x3 as GridView, Settings, Share2 as Share, Download as GetApp, History, HelpCircle as HelpOutline, MoreVertical as MoreVert } from 'lucide-react';

import { Z_INDEX } from '../../styles/design-tokens';

interface QuickAction {
    id: string;
    label: string;
    icon: React.ReactNode;
    onClick: () => void;
    shortcut?: string;
    divider?: boolean;
}

interface QuickActionsPanelProps {
    /** Callback when creating new project */
    onCreateProject?: () => void;
    /** Callback when creating new workflow */
    onCreateWorkflow?: () => void;
    /** Callback when creating new workspace */
    onCreateWorkspace?: () => void;
    /** Current project ID (for project-specific actions) */
    projectId?: string;
    /** Additional CSS classes */
    className?: string;
}

export function QuickActionsPanel({
    onCreateProject,
    onCreateWorkflow,
    onCreateWorkspace,
    projectId,
    className = '',
}: QuickActionsPanelProps) {
    const navigate = useNavigate();
    const [isOpen, setIsOpen] = useState(false);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
    const buttonRef = useRef<HTMLButtonElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);

    // Calculate dropdown position when opening
    const updateDropdownPosition = useCallback(() => {
        if (buttonRef.current && typeof document !== 'undefined') {
            const rect = buttonRef.current.getBoundingClientRect();
            const position = {
                top: rect.bottom + window.scrollY + 4, // 4px gap
                left: rect.right + window.scrollX - 224, // Align right edge (224px width)
            };
            setDropdownPosition(position);
        }
    }, []);

    // Handle clicks outside
    useEffect(() => {
        if (!isOpen) return;

        function handleClickOutside(event: MouseEvent) {
            if (
                dropdownRef.current &&
                !dropdownRef.current.contains(event.target as Node) &&
                buttonRef.current &&
                !buttonRef.current.contains(event.target as Node)
            ) {
                setIsOpen(false);
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen]);

    // Handle escape key
    useEffect(() => {
        if (!isOpen) return;

        function handleEscape(event: KeyboardEvent) {
            if (event.key === 'Escape') {
                setIsOpen(false);
            }
        }

        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [isOpen]);

    // Create actions
    const createActions: QuickAction[] = [
        {
            id: 'new-project',
            label: 'New Project',
            icon: <Folder className="w-4 h-4" />,
            onClick: () => {
                onCreateProject ? onCreateProject() : navigate('/app');
                setIsOpen(false);
            },
            shortcut: '⌘N',
        },
        {
            id: 'new-workflow',
            label: 'New Workflow',
            icon: <WorkOutline className="w-4 h-4" />,
            onClick: () => {
                onCreateWorkflow ? onCreateWorkflow() : navigate('/app/workflows');
                setIsOpen(false);
            },
        },
        {
            id: 'new-workspace',
            label: 'New Workspace',
            icon: <GridView className="w-4 h-4" />,
            onClick: () => {
                if (onCreateWorkspace) {
                    onCreateWorkspace();
                } else {
                    navigate('/app/workspaces');
                }
                setIsOpen(false);
            },
            divider: true,
        },
    ];

    // Project-specific actions (only show when in project context)
    const projectActions: QuickAction[] = projectId ? [
        {
            id: 'project-settings',
            label: 'Project Settings',
            icon: <Settings className="w-4 h-4" />,
            onClick: () => {
                navigate(`/app/p/${projectId}/settings`);
                setIsOpen(false);
            },
        },
        {
            id: 'share-project',
            label: 'Share Project',
            icon: <Share className="w-4 h-4" />,
            onClick: () => {
                navigate(`/app/p/${projectId}/share`);
                setIsOpen(false);
            },
        },
        {
            id: 'export-project',
            label: 'Export Project',
            icon: <GetApp className="w-4 h-4" />,
            onClick: () => {
                void (async () => {
                    try {
                        const res = await fetch(`/api/projects/${projectId}/export`);
                        if (!res.ok) throw new Error(`Export failed: ${res.status}`);
                        const blob = await res.blob();
                        const url = URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = `project-${projectId}.zip`;
                        a.click();
                        URL.revokeObjectURL(url);
                    } catch (err) {
                        console.error('[QuickActionsPanel] Export failed:', err);
                    }
                })();
                setIsOpen(false);
            },
        },
        {
            id: 'view-history',
            label: 'View History',
            icon: <History className="w-4 h-4" />,
            onClick: () => {
                navigate(`/app/p/${projectId}/history`);
                setIsOpen(false);
            },
            divider: true,
        },
    ] : [];

    // Help actions
    const helpActions: QuickAction[] = [
        {
            id: 'help',
            label: 'Help & Shortcuts',
            icon: <HelpOutline className="w-4 h-4" />,
            onClick: () => {
                // NOTE: Open keyboard shortcuts panel
                setIsOpen(false);
            },
            shortcut: '⌘/',
        },
    ];

    const allActions = [...createActions, ...projectActions, ...helpActions];

    return (
        <div className={`relative ${className}`}>
            {/* Trigger Button */}
            <button
                ref={buttonRef}
                onClick={() => {
                    updateDropdownPosition();
                    setIsOpen(!isOpen);
                }}
                className="p-2 rounded-md text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500"
                aria-label="Quick actions menu"
                aria-haspopup="menu"
                aria-expanded={isOpen}
                title="Quick Actions"
            >
                <MoreVert className="w-5 h-5" />
            </button>

            {/* Dropdown Menu - Rendered via portal to avoid overflow clipping */}
            {isOpen && typeof document !== 'undefined' && createPortal(
                <div
                    ref={dropdownRef}
                    className="fixed w-56 bg-bg-paper border border-divider rounded-lg shadow-lg overflow-hidden"
                    style={{
                        top: `${dropdownPosition.top}px`,
                        left: `${dropdownPosition.left}px`,
                        zIndex: 1200,
                    }}
                    role="menu"
                    aria-orientation="vertical"
                >
                    {allActions.map((action, index) => (
                        <div key={action.id}>
                            <button
                                onClick={action.onClick}
                                className="w-full flex items-center gap-3 px-3 py-2 text-sm text-text-secondary hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
                                role="menuitem"
                            >
                                <span className="flex-shrink-0">{action.icon}</span>
                                <span className="flex-1 text-left">{action.label}</span>
                                {action.shortcut && (
                                    <span className="text-xs text-text-secondary/60 font-mono">
                                        {action.shortcut}
                                    </span>
                                )}
                            </button>
                            {action.divider && index < allActions.length - 1 && (
                                <div className="h-px bg-divider my-1" />
                            )}
                        </div>
                    ))}
                </div>,
                document.body
            )}
        </div>
    );
}

/**
 * New Button Component
 * 
 * Primary "New" button with dropdown for creating projects/workflows/workspaces.
 */
interface NewButtonProps {
    /** Callback when creating new project */
    onCreateProject?: () => void;
    /** Callback when creating new workflow */
    onCreateWorkflow?: () => void;
    /** Callback when creating new workspace */
    onCreateWorkspace?: () => void;
    /** Button variant */
    variant?: 'default' | 'compact';
    /** Additional CSS classes */
    className?: string;
}

export function NewButton({
    onCreateProject,
    onCreateWorkflow,
    onCreateWorkspace,
    variant = 'default',
    className = '',
}: NewButtonProps) {
    const navigate = useNavigate();
    const [isOpen, setIsOpen] = useState(false);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
    const buttonRef = useRef<HTMLButtonElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);

    // Calculate dropdown position when opening
    const updateDropdownPosition = useCallback(() => {
        if (buttonRef.current && typeof document !== 'undefined') {
            const rect = buttonRef.current.getBoundingClientRect();
            setDropdownPosition({
                top: rect.bottom + window.scrollY + 4, // 4px gap
                left: rect.right + window.scrollX - 192, // Align right edge (192px width)
            });
        }
    }, []);

    // Handle clicks outside
    useEffect(() => {
        if (!isOpen) return;

        function handleClickOutside(event: MouseEvent) {
            if (
                dropdownRef.current &&
                !dropdownRef.current.contains(event.target as Node) &&
                buttonRef.current &&
                !buttonRef.current.contains(event.target as Node)
            ) {
                setIsOpen(false);
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen]);

    const createActions: QuickAction[] = [
        {
            id: 'new-project',
            label: 'Project',
            icon: <Folder className="w-4 h-4" />,
            onClick: () => {
                onCreateProject ? onCreateProject() : navigate('/app');
                setIsOpen(false);
            },
            shortcut: '⌘N',
        },
        {
            id: 'new-workflow',
            label: 'Workflow',
            icon: <WorkOutline className="w-4 h-4" />,
            onClick: () => {
                onCreateWorkflow ? onCreateWorkflow() : navigate('/app/workflows');
                setIsOpen(false);
            },
        },
        {
            id: 'new-workspace',
            label: 'Workspace',
            icon: <GridView className="w-4 h-4" />,
            onClick: () => {
                if (onCreateWorkspace) {
                    onCreateWorkspace();
                } else {
                    navigate('/app/workspaces');
                }
                setIsOpen(false);
            },
        },
    ];

    return (
        <div className={`relative ${className}`}>
            {/* Button */}
            <button
                ref={buttonRef}
                onClick={() => {
                    updateDropdownPosition();
                    setIsOpen(!isOpen);
                }}
                className={`
                    flex items-center gap-2 px-3 py-1.5 rounded-md font-medium transition-colors
                    bg-primary-600 text-white hover:bg-primary-700
                    focus:outline-none focus:ring-2 focus:ring-primary-500
                    ${variant === 'compact' ? 'text-sm' : 'text-sm'}
                `}
                aria-label="Create new"
                aria-haspopup="menu"
                aria-expanded={isOpen}
            >
                <Add className="w-4 h-4" />
                {variant !== 'compact' && <span>New</span>}
            </button>

            {/* Dropdown - Rendered via portal to avoid overflow clipping */}
            {isOpen && typeof document !== 'undefined' && createPortal(
                <div
                    ref={dropdownRef}
                    className="fixed w-48 bg-bg-paper border border-divider rounded-lg shadow-lg overflow-hidden"
                    style={{
                        top: `${dropdownPosition.top}px`,
                        left: `${dropdownPosition.left}px`,
                        zIndex: 1200,
                    }}
                    role="menu"
                    aria-orientation="vertical"
                >
                    {createActions.map(action => (
                        <button
                            key={action.id}
                            onClick={action.onClick}
                            className="w-full flex items-center gap-3 px-3 py-2 text-sm text-text-secondary hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
                            role="menuitem"
                        >
                            <span className="flex-shrink-0">{action.icon}</span>
                            <span className="flex-1 text-left">{action.label}</span>
                            {action.shortcut && (
                                <span className="text-xs text-text-secondary/60 font-mono">
                                    {action.shortcut}
                                </span>
                            )}
                        </button>
                    ))}
                </div>,
                document.body
            )}
        </div>
    );
}

export default QuickActionsPanel;
