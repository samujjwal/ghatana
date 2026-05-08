/**
 * Project List Panel
 * 
 * Dead simple sidebar panel showing projects in current workspace.
 * Visual distinction between owned (full access) and included (read-only) projects.
 * AI health scores and smart suggestions displayed inline.
 * 
 * @doc.type component
 * @doc.purpose Sidebar project navigation with AI insights
 * @doc.layer product
 * @doc.pattern Presentational Component
 */
import { useState } from 'react';
import { useAtom } from 'jotai';
import { useNavigate, useParams } from 'react-router';

import { workspaceAtom, type ProjectWithOwnership } from '../../state/atoms/workspaceAtom';
import { LifecyclePhaseBadge } from '../lifecycle/LifecyclePhaseBadge';
import { Button } from '../ui/Button';

function toLegacyLifecyclePhase(
    phase: ProjectWithOwnership['lifecyclePhase']
): import('../../types/lifecycle').LifecyclePhase {
    if (phase === 'EVOLVE') {
        return 'IMPROVE' as import('../../types/lifecycle').LifecyclePhase;
    }
    return phase as unknown as import('../../types/lifecycle').LifecyclePhase;
}

interface ProjectListPanelProps {
    onCreateProject?: () => void;
    onImportProject?: () => void;
    className?: string;
}

export function ProjectListPanel({
    onCreateProject,
    onImportProject,
    className = ''
}: ProjectListPanelProps) {
    const [state] = useAtom(workspaceAtom);
    const navigate = useNavigate();
    const { projectId: activeProjectId } = useParams<{ projectId: string }>();

    const [filter, setFilter] = useState<'all' | 'owned' | 'included'>('all');
    const [isCollapsed, setIsCollapsed] = useState(false);

    // Filter projects based on selection
    const filteredProjects = (() => {
        const owned = state.ownedProjects.map(p => ({ ...p, isOwned: true }));
        const included = state.includedProjects.map(p => ({ ...p, isOwned: false }));

        switch (filter) {
            case 'owned': return owned;
            case 'included': return included;
            default: return [...owned, ...included];
        }
    })();

    const handleSelectProject = (project: ProjectWithOwnership) => {
        navigate(`/p/${project.id}`);
    };

    // Get health color based on AI score
    const getHealthColor = (score?: number) => {
        if (score === undefined) return 'bg-grey-300 dark:bg-grey-600';
        if (score >= 80) return 'bg-success-bg';
        if (score >= 60) return 'bg-warning-bg';
        if (score >= 40) return 'bg-warning-bg';
        return 'bg-destructive-bg';
    };

    // Get status badge styling
    const getStatusStyle = (status: string) => {
        switch (status) {
            case 'ACTIVE': return 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color';
            case 'DRAFT': return 'bg-grey-100 text-grey-600 dark:bg-grey-700 dark:text-grey-400';
            case 'COMPLETED': return 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color';
            case 'ARCHIVED': return 'bg-grey-100 text-grey-500 dark:bg-grey-800 dark:text-grey-500';
            default: return 'bg-grey-100 text-grey-600';
        }
    };

    return (
        <div className={`flex flex-col ${className}`}>
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-grey-200 dark:border-grey-700">
                <Button
                    type="button"
                    onClick={() => setIsCollapsed(!isCollapsed)}
                    variant="ghost"
                    size="small"
                    className="flex items-center gap-2 text-sm font-semibold text-grey-700 dark:text-grey-300"
                >
                    <svg
                        className={`w-4 h-4 transition-transform ${isCollapsed ? '-rotate-90' : ''}`}
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                    >
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                    PROJECTS
                </Button>

                {/* Project Count Badge */}
                <span className="px-2 py-0.5 text-xs font-medium rounded-full 
          bg-grey-100 dark:bg-grey-700 text-grey-600 dark:text-grey-400">
                    {filteredProjects.length}
                </span>
            </div>

            {/* Collapsible Content */}
            {!isCollapsed && (
                <>
                    {/* Quick Filters */}
                    <div className="flex gap-1 px-3 py-2 border-b border-grey-100 dark:border-grey-800">
                        {(['all', 'owned', 'included'] as const).map((f) => (
                            <Button
                                key={f}
                                type="button"
                                onClick={() => setFilter(f)}
                                variant="ghost"
                                size="small"
                                className={`
                  px-2 py-1 text-xs font-medium rounded-md transition-colors
                  ${filter === f
                                        ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300'
                                        : 'text-grey-500 hover:bg-grey-100 dark:hover:bg-grey-800'
                                    }
                `}
                            >
                                {f === 'all' ? 'All' : f === 'owned' ? 'Owned' : 'Included'}
                            </Button>
                        ))}
                    </div>

                    {/* Project List */}
                    <div className="flex-1 overflow-y-auto">
                        {filteredProjects.length === 0 ? (
                            <div className="px-4 py-8 text-center">
                                <p className="text-sm text-grey-500 dark:text-grey-400 mb-3">
                                    {filter === 'included'
                                        ? 'No included projects yet'
                                        : 'No projects yet'
                                    }
                                </p>
                                {filter !== 'included' && onCreateProject && (
                                    <Button
                                        type="button"
                                        onClick={onCreateProject}
                                        variant="link"
                                        size="small"
                                        className="text-sm text-primary-600 dark:text-primary-400 hover:underline"
                                    >
                                        Create your first project →
                                    </Button>
                                )}
                            </div>
                        ) : (
                            <ul className="py-1">
                                {filteredProjects.map((project) => (
                                    <li key={project.id}>
                                        <Button
                                            type="button"
                                            onClick={() => handleSelectProject(project)}
                                            variant="ghost"
                                            data-testid={`project-item-${project.id}`}
                                            className={`
                        w-full flex items-start gap-3 px-4 py-3 text-left
                        transition-colors duration-100
                        ${activeProjectId === project.id
                                                    ? 'bg-primary-50 dark:bg-primary-900/20 border-r-2 border-primary-500'
                                                    : 'hover:bg-grey-50 dark:hover:bg-grey-800/50'
                                                }
                      `}
                                        >
                                            {/* Health Indicator */}
                                            <span className="flex-shrink-0 pt-1">
                                                <span
                                                    className={`w-2 h-2 rounded-full ${getHealthColor(project.aiHealthScore)}`}
                                                    title={project.aiHealthScore ? `Health: ${project.aiHealthScore}%` : 'No health data'}
                                                />
                                            </span>

                                            {/* Project Info */}
                                            <span className="flex-1 min-w-0">
                                                <span className="flex items-center gap-2">
                                                    <span className={`
                            text-sm font-medium truncate
                            ${activeProjectId === project.id
                                                            ? 'text-primary-700 dark:text-primary-300'
                                                            : 'text-grey-900 dark:text-grey-100'
                                                        }
                          `}>
                                                        {project.name}
                                                    </span>

                                                    {/* Ownership Badge */}
                                                    {!project.isOwned && (
                                                        <span className="px-1.5 py-0.5 text-[10px] font-medium 
                              bg-warning-bg dark:bg-warning-bg/30 
                              text-warning-color dark:text-warning-color 
                              rounded-full whitespace-nowrap">
                                                            read-only
                                                        </span>
                                                    )}
                                                </span>

                                                {/* Status & Type */}
                                                <span className="flex items-center gap-2 mt-1">
                                                    <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${getStatusStyle(project.status)}`}>
                                                        {project.status}
                                                    </span>
                                                    <span className="text-[10px] text-grey-400 dark:text-grey-500">
                                                        {project.type.replace('_', ' ')}
                                                    </span>
                                                </span>

                                                {/* Lifecycle Phase Badge */}
                                                <span className="mt-1.5 block">
                                                    <LifecyclePhaseBadge
                                                        phase={toLegacyLifecyclePhase(project.lifecyclePhase)}
                                                        size="sm"
                                                    />
                                                </span>

                                                {/* AI Next Action - subtle hint */}
                                                {project.aiNextActions.length > 0 && (
                                                    <p className="mt-1.5 text-[11px] text-grey-500 dark:text-grey-400 truncate">
                                                        💡 {project.aiNextActions[0]}
                                                    </p>
                                                )}
                                            </span>
                                        </Button>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>

                    {/* Actions */}
                    <div className="border-t border-grey-200 dark:border-grey-700 p-2 space-y-1">
                        {onCreateProject && (
                            <Button
                                type="button"
                                onClick={onCreateProject}
                                variant="ghost"
                                data-testid="create-project-btn"
                                className="
                  w-full flex items-center gap-2 px-3 py-2
                  text-sm font-medium text-primary-600 dark:text-primary-400
                  hover:bg-primary-50 dark:hover:bg-primary-900/20
                  rounded-md transition-colors
                "
                            >
                                <span className="text-lg">+</span>
                                <span>New Project</span>
                            </Button>
                        )}

                        {onImportProject && (
                            <Button
                                type="button"
                                onClick={onImportProject}
                                variant="ghost"
                                data-testid="import-project-btn"
                                className="
                  w-full flex items-center gap-2 px-3 py-2
                  text-sm text-grey-600 dark:text-grey-400
                  hover:bg-grey-100 dark:hover:bg-grey-800
                  rounded-md transition-colors
                "
                            >
                                <span className="text-lg">↓</span>
                                <span>Include Project</span>
                            </Button>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}
