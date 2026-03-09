/**
 * RecentProjectsStrip Component
 *
 * Minimal horizontal strip showing recent projects.
 * Provides quick access without taking focus from AI input.
 * Shows lifecycle phase and AI next actions.
 *
 * @doc.type component
 * @doc.purpose Quick access to recent projects with lifecycle info
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { Link } from 'react-router';
import { Folder, Clock as AccessTime, CheckCircle, TrendingUp } from 'lucide-react';
import type { Project } from '../../state/atoms/workspaceAtom';
import type { LifecyclePhase } from '@/shared/types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export interface RecentProjectsStripProps {
    projects: Project[];
    maxItems?: number;
    className?: string;
}

// ============================================================================
// Component
// ============================================================================

export function RecentProjectsStrip({
    projects,
    maxItems = 5,
    className = '',
}: RecentProjectsStripProps) {
    const recentProjects = projects.slice(0, maxItems);

    if (recentProjects.length === 0) {
        return null;
    }

    return (
        <div className={`w-full max-w-2xl ${className}`}>
            <div className="flex items-center gap-2 mb-3 text-text-secondary">
                <AccessTime className="w-4 h-4" />
                <span className="text-sm font-medium">Recent Projects</span>
            </div>

            <div className="flex gap-3 overflow-x-auto pb-2">
                {recentProjects.map((project) => (
                    <Link
                        key={project.id}
                        to={`/app/p/${project.id}`}
                        className="group flex-shrink-0 flex flex-col gap-2 px-4 py-3 bg-bg-paper border border-divider rounded-lg hover:border-primary-300 dark:hover:border-primary-700 hover:shadow-sm transition-all no-underline w-[220px]"
                    >
                        <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center">
                                <Folder className="w-4 h-4 text-primary-600 dark:text-primary-400" />
                            </div>
                            <div className="min-w-0 flex-1">
                                <div className="font-medium text-text-primary group-hover:text-primary-600 transition-colors truncate">
                                    {project.name}
                                </div>
                                <div className="text-xs text-text-secondary capitalize">
                                    {project.type || 'Project'}
                                </div>
                            </div>
                        </div>

                        {/* Lifecycle Phase Badge */}
                        <div className="flex items-center gap-2">
                            <div className={`px-2 py-0.5 rounded text-xs font-medium ${getLifecycleColor(project.lifecyclePhase)}`}>
                                {formatLifecyclePhase(project.lifecyclePhase)}
                            </div>
                            {project.aiHealthScore !== undefined && (
                                <div className="flex items-center gap-1 text-xs text-text-secondary">
                                    <TrendingUp className="w-3 h-3" />
                                    {project.aiHealthScore}%
                                </div>
                            )}
                        </div>

                        {/* Next Action Preview */}
                        {project.aiNextActions && project.aiNextActions.length > 0 && (
                            <div className="flex items-start gap-1.5 text-xs text-text-secondary">
                                <CheckCircle className="w-3 h-3 mt-0.5 flex-shrink-0" />
                                <span className="truncate">{project.aiNextActions[0]}</span>
                            </div>
                        )}
                    </Link>
                ))}
            </div>
        </div>
    );
}

// ============================================================================
// Helpers
// ============================================================================

function formatLifecyclePhase(phase: LifecyclePhase): string {
    const labels: Record<LifecyclePhase, string> = {
        [LifecyclePhase.INTENT]: 'Intent',
        [LifecyclePhase.SHAPE]: 'Shape',
        [LifecyclePhase.VALIDATE]: 'Validate',
        [LifecyclePhase.GENERATE]: 'Generate',
        [LifecyclePhase.RUN]: 'Run',
        [LifecyclePhase.OBSERVE]: 'Observe',
        [LifecyclePhase.IMPROVE]: 'Improve',
    };
    return labels[phase] || phase;
}

function getLifecycleColor(phase: LifecyclePhase): string {
    const colors: Record<LifecyclePhase, string> = {
        [LifecyclePhase.INTENT]: 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300',
        [LifecyclePhase.SHAPE]: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300',
        [LifecyclePhase.VALIDATE]: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300',
        [LifecyclePhase.GENERATE]: 'bg-cyan-100 dark:bg-cyan-900/30 text-cyan-700 dark:text-cyan-300',
        [LifecyclePhase.RUN]: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300',
        [LifecyclePhase.OBSERVE]: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300',
        [LifecyclePhase.IMPROVE]: 'bg-pink-100 dark:bg-pink-900/30 text-pink-700 dark:text-pink-300',
    };
    return colors[phase] || 'bg-grey-100 dark:bg-grey-900/30 text-grey-700 dark:text-grey-300';
}

export default RecentProjectsStrip;
