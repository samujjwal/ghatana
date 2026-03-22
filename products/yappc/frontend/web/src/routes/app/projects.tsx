/**
 * Projects Route
 * 
 * Displays all projects across workspaces in a grid view.
 * Supports filtering, sorting, and quick actions.
 * 
 * @doc.type route
 * @doc.purpose Projects overview and navigation
 * @doc.layer product
 * @doc.pattern Route Component
 */

import { useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { Plus as Add, Filter as FilterList, LayoutGrid as ViewModule, List as ViewList, Search, Folder, Clock as Schedule, User as Person } from 'lucide-react';

import { useWorkspaceContext } from '../../hooks/useWorkspaceData';
import { RouteLoadingSpinner } from '../../components/route/LoadingSpinner';
import { LifecyclePhaseBadge } from '../../components/lifecycle/LifecyclePhaseBadge';
import { CreateProjectDialog } from '../../components/workspace';
import { ApiUnavailableFallback } from '../../components/route/ApiUnavailableFallback';
import type { LifecyclePhase } from '../../types/lifecycle';

type ViewMode = 'grid' | 'list';
type FilterType = 'all' | 'owned' | 'included';
type SortBy = 'updated' | 'name' | 'phase';

interface ProjectItem {
    id: string;
    name: string;
    description?: string;
    status: string;
    currentPhase?: LifecyclePhase;
    aiHealthScore?: number;
    lastActivityAt?: string;
    isOwned: boolean;
    workspaceName?: string;
}

export default function ProjectsRoute() {
    const navigate = useNavigate();
    const {
        ownedProjects,
        includedProjects,
        currentWorkspace,
        isLoading,
        error
    } = useWorkspaceContext();

    const [viewMode, setViewMode] = useState<ViewMode>('grid');
    const [filter, setFilter] = useState<FilterType>('all');
    const [sortBy, setSortBy] = useState<SortBy>('updated');
    const [searchQuery, setSearchQuery] = useState('');
    const [showCreateDialog, setShowCreateDialog] = useState(false);

    const handleRetry = useCallback(() => {
        window.location.reload();
    }, []);

    // Combine and normalize projects
    const allProjects = useMemo<ProjectItem[]>(() => {
        const owned = ownedProjects.map(p => ({
            ...p,
            isOwned: true,
            workspaceName: currentWorkspace?.name
        }));
        const included = includedProjects.map(p => ({
            ...p,
            isOwned: false,
            workspaceName: currentWorkspace?.name
        }));
        return [...owned, ...included];
    }, [ownedProjects, includedProjects, currentWorkspace]);

    // Filter projects
    const filteredProjects = useMemo(() => {
        let result = allProjects;

        // Apply ownership filter
        if (filter === 'owned') {
            result = result.filter(p => p.isOwned);
        } else if (filter === 'included') {
            result = result.filter(p => !p.isOwned);
        }

        // Apply search filter
        if (searchQuery.trim()) {
            const query = searchQuery.toLowerCase();
            result = result.filter(p =>
                p.name.toLowerCase().includes(query) ||
                p.description?.toLowerCase().includes(query)
            );
        }

        // Apply sorting
        result.sort((a, b) => {
            switch (sortBy) {
                case 'name':
                    return a.name.localeCompare(b.name);
                case 'phase':
                    return (a.currentPhase || 'INTENT').localeCompare(b.currentPhase || 'INTENT');
                case 'updated':
                default:
                    return (b.lastActivityAt || '').localeCompare(a.lastActivityAt || '');
            }
        });

        return result;
    }, [allProjects, filter, searchQuery, sortBy]);

    // Health color based on AI score
    const getHealthColor = (score?: number) => {
        if (score === undefined) return 'bg-grey-300 dark:bg-grey-600';
        if (score >= 80) return 'bg-green-500';
        if (score >= 60) return 'bg-yellow-500';
        if (score >= 40) return 'bg-orange-500';
        return 'bg-red-500';
    };

    if (isLoading) {
        return (
            <div className="flex h-full items-center justify-center">
                <RouteLoadingSpinner />
            </div>
        );
    }

    // Check if error is a service unavailability error
    if (error) {
        const errorMessage = typeof error === 'string' ? error : error.message || String(error);
        if (errorMessage.includes('unavailable') || errorMessage.includes('connection') || errorMessage.includes('database')) {
            return (
                <ApiUnavailableFallback
                    error={errorMessage}
                    onRetry={handleRetry}
                    isRetrying={false}
                />
            );
        }
    }

    return (
        <div className="h-full flex flex-col bg-bg-default">
            {/* Header */}
            <header className="flex-shrink-0 border-b border-divider bg-bg-paper px-6 py-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-xl font-bold text-text-primary">Projects</h1>
                        <p className="text-sm text-text-secondary mt-1">
                            {filteredProjects.length} project{filteredProjects.length !== 1 ? 's' : ''} in {currentWorkspace?.name || 'workspace'}
                        </p>
                    </div>
                    <button
                        onClick={() => setShowCreateDialog(true)}
                        className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700 transition-colors"
                    >
                        <Add className="w-5 h-5" />
                        <span>New Project</span>
                    </button>
                </div>

                {/* Toolbar */}
                <div className="flex items-center gap-4 mt-4">
                    {/* Search */}
                    <div className="relative flex-1 max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-text-secondary" />
                        <input
                            type="text"
                            placeholder="Search projects..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2 bg-bg-default border border-divider rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500"
                        />
                    </div>

                    {/* Filter Buttons */}
                    <div className="flex items-center gap-1 bg-bg-default rounded-lg p-1 border border-divider">
                        {(['all', 'owned', 'included'] as const).map((f) => (
                            <button
                                key={f}
                                onClick={() => setFilter(f)}
                                className={`
                                    px-3 py-1.5 text-xs font-medium rounded-md transition-colors
                                    ${filter === f
                                        ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300'
                                        : 'text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800'
                                    }
                                `}
                            >
                                {f === 'all' ? 'All' : f === 'owned' ? '✏️ Mine' : '👁️ Shared'}
                            </button>
                        ))}
                    </div>

                    {/* Sort */}
                    <div className="flex items-center gap-2">
                        <FilterList className="w-4 h-4 text-text-secondary" />
                        <select
                            value={sortBy}
                            onChange={(e) => setSortBy(e.target.value as SortBy)}
                            className="text-sm bg-bg-default border border-divider rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
                        >
                            <option value="updated">Last Updated</option>
                            <option value="name">Name</option>
                            <option value="phase">Phase</option>
                        </select>
                    </div>

                    {/* View Toggle */}
                    <div className="flex items-center gap-1 border border-divider rounded-lg p-0.5">
                        <button
                            onClick={() => setViewMode('grid')}
                            className={`p-1.5 rounded ${viewMode === 'grid' ? 'bg-grey-100 dark:bg-grey-800' : ''}`}
                            title="Grid view"
                        >
                            <ViewModule className="w-4 h-4 text-text-secondary" />
                        </button>
                        <button
                            onClick={() => setViewMode('list')}
                            className={`p-1.5 rounded ${viewMode === 'list' ? 'bg-grey-100 dark:bg-grey-800' : ''}`}
                            title="List view"
                        >
                            <ViewList className="w-4 h-4 text-text-secondary" />
                        </button>
                    </div>
                </div>
            </header>

            {/* Content */}
            <main className="flex-1 overflow-auto p-6">
                {filteredProjects.length === 0 ? (
                    <EmptyState
                        filter={filter}
                        searchQuery={searchQuery}
                        onCreateNew={() => setShowCreateDialog(true)}
                    />
                ) : viewMode === 'grid' ? (
                    <ProjectGrid
                        projects={filteredProjects}
                        getHealthColor={getHealthColor}
                        onSelect={(id) => navigate(`/app/p/${id}`)}
                    />
                ) : (
                    <ProjectList
                        projects={filteredProjects}
                        getHealthColor={getHealthColor}
                        onSelect={(id) => navigate(`/app/p/${id}`)}
                    />
                )}
            </main>

            {/* Create Dialog */}
            <CreateProjectDialog
                isOpen={showCreateDialog}
                onClose={() => setShowCreateDialog(false)}
                onCreated={(project) => {
                    setShowCreateDialog(false);
                    navigate(`/app/p/${project.id}`);
                }}
            />
        </div>
    );
}

// ============================================================================
// Sub-components
// ============================================================================

function EmptyState({
    filter,
    searchQuery,
    onCreateNew
}: {
    filter: FilterType;
    searchQuery: string;
    onCreateNew: () => void;
}) {
    if (searchQuery) {
        return (
            <div className="flex flex-col items-center justify-center h-64 text-center">
                <Search className="w-12 h-12 text-grey-300 dark:text-grey-600 mb-4" />
                <h3 className="text-lg font-medium text-text-primary mb-2">No matching projects</h3>
                <p className="text-sm text-text-secondary">
                    Try a different search term
                </p>
            </div>
        );
    }

    return (
        <div className="flex flex-col items-center justify-center h-64 text-center">
            <Folder className="w-12 h-12 text-grey-300 dark:text-grey-600 mb-4" />
            <h3 className="text-lg font-medium text-text-primary mb-2">
                {filter === 'included' ? 'No shared projects' : 'No projects yet'}
            </h3>
            <p className="text-sm text-text-secondary mb-4">
                {filter === 'included'
                    ? 'Projects shared with you will appear here'
                    : 'Create your first project to get started'
                }
            </p>
            {filter !== 'included' && (
                <button
                    onClick={onCreateNew}
                    className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700 transition-colors"
                >
                    <Add className="w-5 h-5" />
                    <span>Create Project</span>
                </button>
            )}
        </div>
    );
}

function ProjectGrid({
    projects,
    getHealthColor,
    onSelect
}: {
    projects: ProjectItem[];
    getHealthColor: (score?: number) => string;
    onSelect: (id: string) => void;
}) {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {projects.map((project) => (
                <button
                    key={project.id}
                    onClick={() => onSelect(project.id)}
                    className="text-left p-4 bg-bg-paper border border-divider rounded-xl hover:shadow-md hover:border-primary-200 dark:hover:border-primary-800 transition-all group"
                >
                    {/* Header */}
                    <div className="flex items-start justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <div className="w-10 h-10 rounded-lg bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center">
                                <Folder className="w-5 h-5 text-primary-600 dark:text-primary-400" />
                            </div>
                            {!project.isOwned && (
                                <span className="px-1.5 py-0.5 text-[10px] font-medium bg-grey-100 dark:bg-grey-700 text-grey-500 rounded">
                                    Shared
                                </span>
                            )}
                        </div>
                        {/* Health Indicator */}
                        <div className={`w-2 h-2 rounded-full ${getHealthColor(project.aiHealthScore)}`} title={`Health: ${project.aiHealthScore ?? 'N/A'}%`} />
                    </div>

                    {/* Title */}
                    <h3 className="font-semibold text-text-primary group-hover:text-primary-600 dark:group-hover:text-primary-400 truncate mb-1">
                        {project.name}
                    </h3>

                    {/* Description */}
                    {project.description && (
                        <p className="text-sm text-text-secondary line-clamp-2 mb-3">
                            {project.description}
                        </p>
                    )}

                    {/* Footer */}
                    <div className="flex items-center justify-between mt-auto pt-3 border-t border-divider">
                        {project.currentPhase && (
                            <LifecyclePhaseBadge phase={project.currentPhase} size="sm" />
                        )}
                        {project.lastActivityAt && (
                            <span className="flex items-center gap-1 text-xs text-text-secondary">
                                <Schedule className="w-3 h-3" />
                                {formatRelativeTime(project.lastActivityAt)}
                            </span>
                        )}
                    </div>
                </button>
            ))}
        </div>
    );
}

function ProjectList({
    projects,
    getHealthColor,
    onSelect
}: {
    projects: ProjectItem[];
    getHealthColor: (score?: number) => string;
    onSelect: (id: string) => void;
}) {
    return (
        <div className="bg-bg-paper border border-divider rounded-xl overflow-hidden">
            <table className="w-full">
                <thead>
                    <tr className="border-b border-divider bg-grey-50 dark:bg-grey-800/50">
                        <th className="text-left px-4 py-3 text-xs font-medium text-text-secondary uppercase tracking-wider">Project</th>
                        <th className="text-left px-4 py-3 text-xs font-medium text-text-secondary uppercase tracking-wider">Phase</th>
                        <th className="text-left px-4 py-3 text-xs font-medium text-text-secondary uppercase tracking-wider">Health</th>
                        <th className="text-left px-4 py-3 text-xs font-medium text-text-secondary uppercase tracking-wider">Owner</th>
                        <th className="text-left px-4 py-3 text-xs font-medium text-text-secondary uppercase tracking-wider">Updated</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-divider">
                    {projects.map((project) => (
                        <tr
                            key={project.id}
                            onClick={() => onSelect(project.id)}
                            className="hover:bg-grey-50 dark:hover:bg-grey-800/50 cursor-pointer transition-colors"
                        >
                            <td className="px-4 py-3">
                                <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 rounded-lg bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center flex-shrink-0">
                                        <Folder className="w-4 h-4 text-primary-600 dark:text-primary-400" />
                                    </div>
                                    <div>
                                        <p className="font-medium text-text-primary">{project.name}</p>
                                        {project.description && (
                                            <p className="text-xs text-text-secondary truncate max-w-xs">{project.description}</p>
                                        )}
                                    </div>
                                </div>
                            </td>
                            <td className="px-4 py-3">
                                {project.currentPhase && <LifecyclePhaseBadge phase={project.currentPhase} size="sm" />}
                            </td>
                            <td className="px-4 py-3">
                                <div className="flex items-center gap-2">
                                    <div className={`w-2 h-2 rounded-full ${getHealthColor(project.aiHealthScore)}`} />
                                    <span className="text-sm text-text-secondary">{project.aiHealthScore ?? '—'}%</span>
                                </div>
                            </td>
                            <td className="px-4 py-3">
                                <div className="flex items-center gap-1">
                                    <Person className="w-4 h-4 text-text-secondary" />
                                    <span className="text-sm text-text-secondary">
                                        {project.isOwned ? 'You' : 'Shared'}
                                    </span>
                                </div>
                            </td>
                            <td className="px-4 py-3">
                                <span className="text-sm text-text-secondary">
                                    {project.lastActivityAt ? formatRelativeTime(project.lastActivityAt) : '—'}
                                </span>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

// ============================================================================
// Utilities
// ============================================================================

function formatRelativeTime(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
