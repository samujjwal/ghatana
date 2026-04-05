/**
 * Enhanced Breadcrumb Component
 * 
 * Modern breadcrumb navigation with dropdown menus for workspace/project switching.
 * Replaces sidebar navigation to maximize canvas space while maintaining full functionality.
 * 
 * Features:
 * - Dropdown menus for workspace and project switching
 * - Quick create actions (+ New Workspace, + New Project)
 * - Search functionality in project dropdown
 * - Keyboard navigation support
 * - Responsive design (mobile/tablet/desktop)
 * - Recent items tracking
 * 
 * @doc.type component
 * @doc.purpose Enhanced breadcrumb navigation with dropdowns
 * @doc.layer product
 * @doc.pattern Navigation Component
 */

import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { Link, useNavigate } from 'react-router';
import { Home, ChevronRight, ChevronDown as KeyboardArrowDown, Plus as Add, Folder, Search, Check, Settings } from 'lucide-react';

import { LifecyclePhase } from '../../types/lifecycle';
import { PHASE_LABELS } from '../../styles/design-tokens';

export interface BreadcrumbSegment {
    type: 'home' | 'workspace' | 'project' | 'section';
    label: string;
    icon?: React.ReactNode;
    href?: string;
    onClick?: () => void;
    dropdown?: BreadcrumbDropdownConfig;
    badge?: {
        text: string;
        variant: 'owner' | 'member' | 'shared';
    };
}

export interface BreadcrumbDropdownConfig {
    items: Array<{
        id: string;
        label: string;
        icon?: React.ReactNode;
        badge?: string;
        href?: string;
        onClick?: () => void;
        isActive?: boolean;
    }>;
    sections?: Array<{
        title: string;
        items: Array<{
            id: string;
            label: string;
            icon?: React.ReactNode;
            badge?: string;
            href?: string;
            onClick?: () => void;
        }>;
    }>;
    footer?: Array<{
        label: string;
        icon?: React.ReactNode;
        onClick: () => void;
    }>;
    searchable?: boolean;
    searchPlaceholder?: string;
}

export interface EnhancedBreadcrumbProps {
    /** Current workspace context */
    workspace?: {
        id: string;
        name: string;
        isOwner: boolean;
    };
    /** Current project context */
    project?: {
        id: string;
        name: string;
        type: string;
        isOwner: boolean;
    };
    /** Current section/page */
    section?: string;
    /** All available workspaces */
    workspaces?: Array<{
        id: string;
        name: string;
        description?: string;
        isOwner: boolean;
        projectCount?: number;
    }>;
    /** All available projects in current workspace */
    projects?: Array<{
        id: string;
        name: string;
        workspaceId: string;
        type?: string;
        lastOpened?: string;
    }>;
    /** Show create actions in dropdowns */
    showCreateActions?: boolean;
    /** Max items to show before collapsing */
    maxItems?: number;
    /** Responsive behavior */
    responsive?: boolean;
    /** Custom CSS class */
    className?: string;
    /** Callback when navigation occurs */
    onNavigate?: (segment: BreadcrumbSegment) => void;
    /** Callback when creating new workspace */
    onCreateWorkspace?: () => void;
    /** Callback when creating new project */
    onCreateProject?: () => void;
}

/**
 * Dropdown Menu Component
 */
function DropdownMenu({
    isOpen,
    onClose,
    config,
    anchorEl,
}: {
    isOpen: boolean;
    onClose: () => void;
    config: BreadcrumbDropdownConfig;
    anchorEl: HTMLElement | null;
}) {
    const [searchQuery, setSearchQuery] = useState('');
    const dropdownRef = useRef<HTMLDivElement>(null);
    const searchInputRef = useRef<HTMLInputElement>(null);

    // Handle clicks outside dropdown
    useEffect(() => {
        if (!isOpen) return;

        function handleClickOutside(event: MouseEvent) {
            if (
                dropdownRef.current &&
                !dropdownRef.current.contains(event.target as Node) &&
                anchorEl &&
                !anchorEl.contains(event.target as Node)
            ) {
                onClose();
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen, onClose, anchorEl]);

    // Handle escape key
    useEffect(() => {
        if (!isOpen) return;

        function handleEscape(event: KeyboardEvent) {
            if (event.key === 'Escape') {
                onClose();
            }
        }

        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [isOpen, onClose]);

    // Focus search input when dropdown opens
    useEffect(() => {
        if (isOpen && config.searchable && searchInputRef.current) {
            // Small delay to ensure dropdown is rendered
            setTimeout(() => {
                searchInputRef.current?.focus();
            }, 100);
        }
    }, [isOpen, config.searchable]);

    // Filter items based on search query
    const filteredItems = useMemo(() => {
        if (!config.searchable || !searchQuery.trim()) {
            return config.items;
        }
        return config.items.filter(item =>
            item.label.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [config.items, config.searchable, searchQuery]);

    if (!isOpen || !anchorEl) return null;

    // Calculate position
    const rect = anchorEl.getBoundingClientRect();
    const dropdownStyle: React.CSSProperties = {
        position: 'fixed',
        top: rect.bottom + 8,
        left: rect.left,
        zIndex: 9999,
    };

    return createPortal(
        <div
            ref={dropdownRef}
            style={dropdownStyle}
            className="bg-bg-paper border border-divider rounded-lg shadow-lg min-w-[280px] max-w-[400px] max-h-[480px] overflow-hidden flex flex-col"
            role="menu"
            aria-orientation="vertical"
        >
            {(() => {
                console.log('EnhancedBreadcrumb portal rendered with style:', dropdownStyle);
                return null;
            })()}
            {/* Search Input */}
            {config.searchable && (
                <div className="p-2 border-b border-divider">
                    <div className="relative">
                        <Search className="absolute left-2 top-1/2 -translate-y-1/2 w-4 h-4 text-text-secondary" />
                        <input
                            ref={searchInputRef}
                            type="text"
                            placeholder={config.searchPlaceholder || 'Search...'}
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-8 pr-3 py-1.5 text-sm bg-bg-default border border-divider rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                        />
                    </div>
                </div>
            )}

            {/* Items List */}
            <div className="overflow-y-auto flex-1">
                {/* Current Item (if any) */}
                {config.items.filter(item => item.isActive).map(item => (
                    <div
                        key={item.id}
                        className="px-3 py-2 text-sm bg-primary-50 dark:bg-primary-900/20 border-b border-divider"
                    >
                        <div className="flex items-center gap-2">
                            <Check className="w-4 h-4 text-primary-600 dark:text-primary-400 flex-shrink-0" />
                            <span className="font-medium text-text-primary truncate">
                                {item.label}
                            </span>
                            {item.badge && (
                                <span className="ml-auto px-2 py-0.5 text-xs bg-success-color/10 text-success-color rounded-full whitespace-nowrap">
                                    {item.badge}
                                </span>
                            )}
                        </div>
                    </div>
                ))}

                {/* Sections */}
                {config.sections ? (
                    config.sections.map(section => (
                        <div key={section.title}>
                            <div className="px-3 py-2 text-xs font-medium text-text-secondary uppercase tracking-wider bg-bg-default">
                                {section.title}
                            </div>
                            {section.items.map(item => (
                                <DropdownMenuItem
                                    key={item.id}
                                    item={item}
                                    onClose={onClose}
                                />
                            ))}
                        </div>
                    ))
                ) : (
                    <>
                        {filteredItems.filter(item => !item.isActive).map(item => (
                            <DropdownMenuItem
                                key={item.id}
                                item={item}
                                onClose={onClose}
                            />
                        ))}
                        {filteredItems.length === 0 && searchQuery && (
                            <div className="px-3 py-8 text-center text-sm text-text-secondary">
                                No results found for "{searchQuery}"
                            </div>
                        )}
                    </>
                )}
            </div>

            {/* Footer Actions */}
            {config.footer && config.footer.length > 0 && (
                <div className="border-t border-divider">
                    {config.footer.map((action, index) => (
                        <button
                            key={index}
                            onClick={() => {
                                action.onClick();
                                onClose();
                            }}
                            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-secondary hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
                            role="menuitem"
                        >
                            {action.icon}
                            <span>{action.label}</span>
                        </button>
                    ))}
                </div>
            )}
        </div>,
        document.body
    );
}

/**
 * Dropdown Menu Item Component
 */
function DropdownMenuItem({
    item,
    onClose,
}: {
    item: {
        id: string;
        label: string;
        icon?: React.ReactNode;
        badge?: string;
        href?: string;
        onClick?: () => void;
    };
    onClose: () => void;
}) {
    const navigate = useNavigate();

    const handleClick = useCallback(() => {
        if (item.onClick) {
            item.onClick();
        }
        if (item.href) {
            navigate(item.href);
        }
        onClose();
    }, [item, navigate, onClose]);

    return (
        <button
            onClick={handleClick}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-secondary hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
            role="menuitem"
        >
            {item.icon && (
                <span className="flex-shrink-0 w-4 h-4 flex items-center justify-center">
                    {item.icon}
                </span>
            )}
            <span className="truncate flex-1 text-left">{item.label}</span>
            {item.badge && (
                <span className="ml-auto px-2 py-0.5 text-xs bg-grey-100 dark:bg-grey-800 text-text-secondary rounded-full whitespace-nowrap">
                    {item.badge}
                </span>
            )}
        </button>
    );
}

/**
 * Enhanced Breadcrumb Component
 */
export function EnhancedBreadcrumb({
    workspace,
    project,
    section,
    workspaces = [],
    projects = [],
    showCreateActions = true,
    className = '',
    onNavigate,
    onCreateWorkspace,
    onCreateProject,
}: EnhancedBreadcrumbProps) {
    const navigate = useNavigate();
    const [openDropdown, setOpenDropdown] = useState<string | null>(null);
    const [anchorElements, setAnchorElements] = useState<Record<string, HTMLElement>>({});

    // Build workspace dropdown config
    const workspaceDropdown: BreadcrumbDropdownConfig | undefined = useMemo(() => {
        if (!workspace || workspaces.length === 0) return undefined;

        return {
            items: workspaces.map(ws => ({
                id: ws.id,
                label: ws.name,
                icon: <Folder className="w-4 h-4" />,
                badge: ws.isOwner ? 'Owner' : 'Member',
                href: `/app`,
                isActive: ws.id === workspace.id,
            })),
            footer: showCreateActions ? [
                {
                    label: 'Create New Workspace',
                    icon: <Add className="w-4 h-4" />,
                    onClick: onCreateWorkspace || (() => { }),
                },
                {
                    label: 'Manage Workspaces',
                    icon: <Settings className="w-4 h-4" />,
                    onClick: () => navigate('/app/workspaces'),
                },
            ] : undefined,
        };
    }, [workspace, workspaces, showCreateActions, onCreateWorkspace, navigate]);

    // Build project dropdown config
    const projectDropdown: BreadcrumbDropdownConfig | undefined = useMemo(() => {
        if (!project || !workspace) return undefined;

        // Get projects for current workspace
        const workspaceProjects = projects.filter(p => p.workspaceId === workspace.id);

        // Separate into recent and all
        const recentProjects = workspaceProjects
            .filter(p => p.lastOpened)
            .sort((a, b) => {
                const dateA = new Date(a.lastOpened!).getTime();
                const dateB = new Date(b.lastOpened!).getTime();
                return dateB - dateA;
            })
            .slice(0, 5);

        const hasRecent = recentProjects.length > 0;

        return {
            items: workspaceProjects.map(p => ({
                id: p.id,
                label: p.name,
                icon: <Folder className="w-4 h-4" />,
                href: `/app/p/${p.id}`,
                isActive: p.id === project.id,
            })),
            sections: hasRecent ? [
                {
                    title: 'Recent Projects',
                    items: recentProjects.map(p => ({
                        id: p.id,
                        label: p.name,
                        icon: <Folder className="w-4 h-4" />,
                        href: `/app/p/${p.id}`,
                    })),
                },
                {
                    title: `All Projects (${workspaceProjects.length})`,
                    items: workspaceProjects.map(p => ({
                        id: p.id,
                        label: p.name,
                        icon: <Folder className="w-4 h-4" />,
                        href: `/app/p/${p.id}`,
                    })),
                },
            ] : undefined,
            searchable: workspaceProjects.length > 5,
            searchPlaceholder: 'Search projects...',
            footer: showCreateActions ? [
                {
                    label: 'Create New Project',
                    icon: <Add className="w-4 h-4" />,
                    onClick: onCreateProject || (() => navigate('/app')),
                },
                {
                    label: 'Browse All Projects',
                    icon: <Folder className="w-4 h-4" />,
                    onClick: () => navigate('/app/projects'),
                },
            ] : undefined,
        };
    }, [project, workspace, projects, showCreateActions, onCreateProject, navigate]);

    // Build breadcrumb segments
    const segments: BreadcrumbSegment[] = useMemo(() => {
        const result: BreadcrumbSegment[] = [
            {
                type: 'home',
                label: 'Home',
                icon: <Home className="w-4 h-4" />,
                href: '/app',
            },
        ];

        if (workspace) {
            result.push({
                type: 'workspace',
                label: workspace.name,
                dropdown: workspaceDropdown,
                badge: workspace.isOwner ? {
                    text: 'Owner',
                    variant: 'owner',
                } : undefined,
            });
        }

        if (project) {
            result.push({
                type: 'project',
                label: project.name,
                dropdown: projectDropdown,
                badge: project.isOwner ? {
                    text: 'Owner',
                    variant: 'owner',
                } : undefined,
            });
        }

        if (section) {
            // Check if section matches a phase key (case-insensitive)
            const phaseKey = section.toUpperCase() as keyof typeof LifecyclePhase;
            const officialLabel = PHASE_LABELS[phaseKey];

            result.push({
                type: 'section',
                label: officialLabel || section.charAt(0).toUpperCase() + section.slice(1),
            });
        }

        return result;
    }, [workspace, project, section, workspaceDropdown, projectDropdown]);

    // Handle segment click
    const handleSegmentClick = useCallback((segment: BreadcrumbSegment, segmentId: string, anchorEl: HTMLElement) => {
        if (segment.dropdown) {
            setAnchorElements(prev => ({ ...prev, [segmentId]: anchorEl }));
            setOpenDropdown(openDropdown === segmentId ? null : segmentId);
        } else if (segment.href) {
            navigate(segment.href);
            onNavigate?.(segment);
        } else if (segment.onClick) {
            segment.onClick();
            onNavigate?.(segment);
        }
    }, [openDropdown, navigate, onNavigate]);

    // Close dropdown
    const handleCloseDropdown = useCallback(() => {
        setOpenDropdown(null);
    }, []);

    return (
        <nav
            aria-label="Breadcrumb navigation"
            className={`flex items-center gap-1 px-4 py-2 bg-bg-default border-b border-divider ${className}`}
        >
            {segments.map((segment, index) => {
                const segmentId = `${segment.type}-${index}`;
                const isLast = index === segments.length - 1;
                const hasDropdown = !!segment.dropdown;

                return (
                    <div key={segmentId} className="flex items-center gap-1">
                        {/* Separator */}
                        {index > 0 && (
                            <ChevronRight className="w-4 h-4 text-text-secondary flex-shrink-0" aria-hidden="true" />
                        )}

                        {/* Segment Button/Link */}
                        {segment.type === 'home' ? (
                            <Link
                                to={segment.href!}
                                className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500"
                                aria-label="Home"
                                onClick={() => onNavigate?.(segment)}
                            >
                                {segment.icon}
                            </Link>
                        ) : (
                            <button
                                onClick={(e) => handleSegmentClick(segment, segmentId, e.currentTarget)}
                                disabled={!hasDropdown && !segment.href && !segment.onClick}
                                className={`
                                    flex items-center gap-1 px-2 py-1 rounded-md text-sm transition-colors
                                    focus:outline-none focus:ring-2 focus:ring-primary-500
                                    ${isLast
                                        ? 'text-text-primary font-medium'
                                        : 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800'
                                    }
                                    ${hasDropdown ? 'cursor-pointer' : segment.href || segment.onClick ? 'cursor-pointer' : 'cursor-default'}
                                `}
                                aria-current={isLast ? 'page' : undefined}
                                aria-haspopup={hasDropdown ? 'menu' : undefined}
                                aria-expanded={hasDropdown && openDropdown === segmentId}
                            >
                                <span className="truncate max-w-[200px]">{segment.label}</span>
                                {hasDropdown && (
                                    <KeyboardArrowDown className={`w-4 h-4 transition-transform ${openDropdown === segmentId ? 'rotate-180' : ''}`} />
                                )}
                                {segment.badge && (
                                    <span
                                        className={`ml-1 px-1.5 py-0.5 text-xs font-medium rounded-full whitespace-nowrap ${segment.badge.variant === 'owner'
                                            ? 'bg-success-color/10 text-success-color'
                                            : 'bg-warning-color/10 text-warning-color'
                                            }`}
                                    >
                                        {segment.badge.text}
                                    </span>
                                )}
                            </button>
                        )}

                        {/* Dropdown Menu */}
                        {hasDropdown && segment.dropdown && (
                            <DropdownMenu
                                isOpen={openDropdown === segmentId}
                                onClose={handleCloseDropdown}
                                config={segment.dropdown}
                                anchorEl={anchorElements[segmentId]}
                            />
                        )}
                    </div>
                );
            })}
        </nav>
    );
}

export default EnhancedBreadcrumb;
