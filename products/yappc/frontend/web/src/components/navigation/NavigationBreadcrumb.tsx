/**
 * Navigation Breadcrumb Component
 * 
 * Intelligent breadcrumb that adapts to context:
 * - Workspace > Project > Section
 * - Shows dropdowns for workspace/project switching
 * - Canvas mode pills when on canvas
 * - Smooth transitions between contexts
 * 
 * @doc.type component
 * @doc.purpose Context-aware navigation breadcrumb
 * @doc.layer components
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { LayoutDashboard as Dashboard, Folder, ChevronDown as KeyboardArrowDown, Palette, Building2 as Architecture, Code, Rocket, ChevronRight } from 'lucide-react';
import { Box, MenuItem, ListItemIcon, ListItemText, Divider, Typography } from '@ghatana/design-system';
import { Button } from '../design-system';
import { cn } from '../../lib/utils';
import { useI18n } from '../../i18n/I18nProvider';

export type CanvasMode = 'design' | 'architecture' | 'code' | 'deploy';

export interface WorkspaceInfo {
    id: string;
    name: string;
    isOwner?: boolean;
}

export interface ProjectInfo {
    id: string;
    name: string;
    type?: string;
    isOwner?: boolean;
}

export interface NavigationBreadcrumbProps {
    /** Current workspace */
    workspace?: WorkspaceInfo;
    /** Current project */
    project?: ProjectInfo;
    /** Current section/page name */
    section?: string;
    /** Canvas mode (when on canvas) */
    canvasMode?: CanvasMode;
    /** Available workspaces for switching */
    workspaces?: WorkspaceInfo[];
    /** Available projects for switching */
    projects?: ProjectInfo[];
    /** Show canvas mode selector */
    showCanvasMode?: boolean;
    /** Callback when canvas mode changes */
    onCanvasModeChange?: (mode: CanvasMode) => void;
    /** Additional CSS classes */
    className?: string;
}

const CANVAS_MODES: Array<{
    mode: CanvasMode;
    label: string;
    icon: React.ComponentType;
}> = [
        { mode: 'design', label: 'Design', icon: Palette },
        { mode: 'architecture', label: 'Architecture', icon: Architecture },
        { mode: 'code', label: 'Code', icon: Code },
        { mode: 'deploy', label: 'Deploy', icon: Rocket },
    ];

/**
 * Navigation Breadcrumb Component
 */
export function NavigationBreadcrumb({
    workspace,
    project,
    section,
    canvasMode,
    workspaces = [],
    projects = [],
    showCanvasMode = false,
    onCanvasModeChange,
    className,
}: NavigationBreadcrumbProps) {
    const { t } = useI18n();
    const navigate = useNavigate();
    const [workspaceMenuAnchor, setWorkspaceMenuAnchor] = useState<null | HTMLElement>(null);
    const [projectMenuAnchor, setProjectMenuAnchor] = useState<null | HTMLElement>(null);

    const handleWorkspaceClick = (event: React.MouseEvent<HTMLButtonElement>) => {
        if (workspaces.length > 1) {
            setWorkspaceMenuAnchor(event.currentTarget);
        }
    };

    const handleWorkspaceSelect = (workspaceId: string) => {
        void workspaceId;
        navigate('/workspaces');
        setWorkspaceMenuAnchor(null);
    };

    const handleProjectClick = (event: React.MouseEvent<HTMLButtonElement>) => {
        if (projects.length > 0) {
            setProjectMenuAnchor(event.currentTarget);
        }
    };

    const handleProjectSelect = (projectId: string) => {
        navigate(`/p/${projectId}`);
        setProjectMenuAnchor(null);
    };

    return (
        <nav
            className={cn('flex items-center gap-2 flex-1 overflow-x-auto', className)}
            aria-label={t('nav.breadcrumbNavigation')}
            style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                flex: 1,
                overflowX: 'auto',
                minWidth: 0,
            }}
        >
            {/* Workspace */}
            {workspace && (
                <>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleWorkspaceClick}
                        className="max-w-[150px] font-semibold"
                        aria-label={t('nav.workspaceSelector')}
                    >
                        <span className="truncate">{workspace.name}</span>
                        {workspaces.length > 1 && <KeyboardArrowDown className="w-4 h-4 ml-1 opacity-50" />}
                    </Button>

                    {/* Workspace Menu */}
                    {workspaceMenuAnchor ? (
                      <Box className="absolute left-0 top-full z-50 mt-2 min-w-[220px] rounded-md border border-divider bg-white p-1 shadow-lg dark:bg-surface">
                        {workspaces.map((ws) => (
                            <MenuItem
                                key={ws.id}
                                selected={ws.id === workspace.id}
                                onClick={() => handleWorkspaceSelect(ws.id)}
                            >
                                <ListItemIcon>
                                    <Dashboard size={16} />
                                </ListItemIcon>
                                <ListItemText>{ws.name}</ListItemText>
                            </MenuItem>
                        ))}
                      </Box>
                    ) : null}

                    <ChevronRight className="w-4 h-4 text-text-disabled flex-shrink-0" />
                </>
            )}

            {/* Project */}
            {project && (
                <>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleProjectClick}
                        className="max-w-[180px] font-semibold"
                        aria-label={t('nav.projectSelector')}
                    >
                        <span className="truncate">{project.name}</span>
                        {projects.length > 0 && <KeyboardArrowDown className="w-4 h-4 ml-1 opacity-50" />}
                    </Button>

                    {/* Project Menu */}
                    {projectMenuAnchor ? (
                      <Box className="absolute left-0 top-full z-50 mt-2 min-w-[280px] rounded-md border border-divider bg-white p-1 shadow-lg dark:bg-surface">
                        {projects.length > 0 ? (
                            projects.map((proj) => (
                                <MenuItem
                                    key={proj.id}
                                    selected={proj.id === project.id}
                                    onClick={() => handleProjectSelect(proj.id)}
                                >
                                    <ListItemIcon>
                                        <Folder size={16} />
                                    </ListItemIcon>
                                    <Box>
                                        <Typography variant="body2" className="truncate">
                                            {proj.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            {proj.type}
                                        </Typography>
                                    </Box>
                                </MenuItem>
                            ))
                        ) : (
                            <MenuItem disabled>No projects available</MenuItem>
                        )}
                        <Divider />
                        <MenuItem onClick={() => navigate('/projects')}>
                            <Typography variant="body2">View all projects</Typography>
                        </MenuItem>
                      </Box>
                    ) : null}

                    {(section || showCanvasMode) && (
                        <ChevronRight className="w-4 h-4 text-text-disabled flex-shrink-0" />
                    )}
                </>
            )}

            {/* Canvas Mode Selector */}
            {showCanvasMode && canvasMode && (
                <div className="flex items-center gap-1 bg-grey-100 dark:bg-grey-800 rounded-lg p-1">
                    {CANVAS_MODES.map(({ mode, label, icon: Icon }) => (
                        <Button
                            key={mode}
                            variant="ghost"
                            size="sm"
                            onClick={() => onCanvasModeChange?.(mode)}
                            className={cn(
                                'flex items-center gap-1.5 px-3 py-1.5 rounded-md',
                                'text-sm font-medium transition-all duration-200',
                                'focus:outline-none focus:ring-2 focus:ring-primary-500',
                                canvasMode === mode
                                    ? 'bg-white dark:bg-grey-700 text-text-primary shadow-sm'
                                    : 'text-text-secondary hover:text-text-primary hover:bg-white/50 dark:hover:bg-grey-700/50'
                            )}
                            aria-label={`Switch to ${label} mode`}
                            aria-pressed={canvasMode === mode}
                        >
                            <span className="w-4 h-4 flex items-center justify-center" style={{ fontSize: '16px' }}>
                                {React.createElement(Icon)}
                            </span>
                            <span className="hidden sm:inline">{label}</span>
                        </Button>
                    ))}
                </div>
            )}

            {/* Section Name */}
            {!showCanvasMode && section && (
                <span className="text-sm font-medium text-text-secondary truncate max-w-[150px]">
                    {section}
                </span>
            )}
        </nav>
    );
}

export default NavigationBreadcrumb;
