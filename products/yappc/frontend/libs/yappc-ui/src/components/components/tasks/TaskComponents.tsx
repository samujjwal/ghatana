/**
 * YAPPC Task Components
 *
 * UI components for displaying and interacting with tasks.
 * Follows existing DevSecOps component patterns.
 *
 * @module ui/tasks
 */

import React, { useMemo, useCallback, useState } from 'react';
import { useAtomValue } from 'jotai';
import type {
    TaskDefinition,
    TaskDomain,
    LifecycleStage,
    Persona,
    AutomationLevel,
    TaskFilter,
} from '@ghatana/types/tasks';
import {
    allDomainsAtom,
    tasksByDomainAtom,
    filteredTasksAtom,
    registryStatsAtom,
} from '@ghatana/state/tasks/taskRegistryStore';

// ============================================================================
// Task Card Component
// ============================================================================

export interface TaskCardProps {
    task: TaskDefinition;
    onClick?: (task: TaskDefinition) => void;
    selected?: boolean;
    compact?: boolean;
}

/**
 * Card component displaying a single task
 */
export function TaskCard({ task, onClick, selected, compact }: TaskCardProps) {
    const automationColors: Record<AutomationLevel, string> = {
        manual: 'bg-gray-100 text-gray-700',
        assisted: 'bg-blue-100 text-blue-700',
        automated: 'bg-green-100 text-green-700',
    };

    const handleClick = useCallback(() => {
        onClick?.(task);
    }, [onClick, task]);

    if (compact) {
        return (
            <button
                onClick={handleClick}
                className={`
          w-full text-left p-3 rounded-lg border transition-all
          hover:shadow-md hover:border-blue-300
          ${selected ? 'border-blue-500 bg-blue-50' : 'border-gray-200 bg-white'}
        `}
            >
                <div className="flex items-center gap-2">
                    <span
                        className="material-icons text-lg"
                        style={{ color: task.ui.color }}
                    >
                        {task.ui.icon}
                    </span>
                    <span className="font-medium text-sm truncate">{task.name}</span>
                    <span
                        className={`ml-auto px-2 py-0.5 text-xs rounded-full ${automationColors[task.automationLevel]}`}
                    >
                        {task.automationLevel}
                    </span>
                </div>
            </button>
        );
    }

    return (
        <button
            onClick={handleClick}
            className={`
        w-full text-left p-4 rounded-xl border transition-all
        hover:shadow-lg hover:border-blue-300
        ${selected ? 'border-blue-500 bg-blue-50 shadow-md' : 'border-gray-200 bg-white'}
      `}
        >
            <div className="flex items-start gap-3">
                {/* Icon */}
                <div
                    className="w-10 h-10 rounded-lg flex items-center justify-center"
                    style={{ backgroundColor: `${task.ui.color}20` }}
                >
                    <span
                        className="material-icons text-xl"
                        style={{ color: task.ui.color }}
                    >
                        {task.ui.icon}
                    </span>
                </div>

                {/* Content */}
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                        <h3 className="font-semibold text-gray-900 truncate">{task.name}</h3>
                        <span
                            className={`px-2 py-0.5 text-xs rounded-full ${automationColors[task.automationLevel]}`}
                        >
                            {task.automationLevel}
                        </span>
                    </div>
                    <p className="text-sm text-gray-600 line-clamp-2">{task.description}</p>

                    {/* Tags */}
                    <div className="flex flex-wrap gap-1 mt-2">
                        {task.ui.tags.slice(0, 3).map((tag) => (
                            <span
                                key={tag}
                                className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600"
                            >
                                {tag}
                            </span>
                        ))}
                        {task.ui.tags.length > 3 && (
                            <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600">
                                +{task.ui.tags.length - 3}
                            </span>
                        )}
                    </div>

                    {/* Personas */}
                    <div className="flex items-center gap-2 mt-2">
                        <span className="text-xs text-gray-500">Personas:</span>
                        {task.personas.map((persona) => (
                            <span key={persona} className="text-xs text-gray-700">
                                {persona}
                            </span>
                        ))}
                    </div>
                </div>
            </div>
        </button>
    );
}

// ============================================================================
// Task List Component
// ============================================================================

export interface TaskListProps {
    tasks: TaskDefinition[];
    onTaskClick?: (task: TaskDefinition) => void;
    selectedTaskId?: string;
    compact?: boolean;
    emptyMessage?: string;
}

/**
 * List component displaying multiple tasks
 */
export function TaskList({
    tasks,
    onTaskClick,
    selectedTaskId,
    compact,
    emptyMessage = 'No tasks found',
}: TaskListProps) {
    if (tasks.length === 0) {
        return (
            <div className="text-center py-8 text-gray-500">
                <span className="material-icons text-4xl mb-2">inbox</span>
                <p>{emptyMessage}</p>
            </div>
        );
    }

    return (
        <div className={`space-y-${compact ? '2' : '3'}`}>
            {tasks.map((task) => (
                <TaskCard
                    key={task.id}
                    task={task}
                    onClick={onTaskClick}
                    selected={task.id === selectedTaskId}
                    compact={compact}
                />
            ))}
        </div>
    );
}

// ============================================================================
// Domain Card Component
// ============================================================================

export interface DomainCardProps {
    domain: TaskDomain;
    onClick?: (domain: TaskDomain) => void;
    expanded?: boolean;
}

/**
 * Card component displaying a task domain
 */
export function DomainCard({ domain, onClick, expanded }: DomainCardProps) {
    const handleClick = useCallback(() => {
        onClick?.(domain);
    }, [onClick, domain]);

    return (
        <button
            onClick={handleClick}
            className={`
        w-full text-left p-4 rounded-xl border transition-all
        hover:shadow-lg hover:border-blue-300
        ${expanded ? 'border-blue-500 bg-blue-50' : 'border-gray-200 bg-white'}
      `}
        >
            <div className="flex items-center gap-3">
                {/* Icon */}
                <div
                    className="w-12 h-12 rounded-lg flex items-center justify-center"
                    style={{ backgroundColor: `${domain.color}20` }}
                >
                    <span
                        className="material-icons text-2xl"
                        style={{ color: domain.color }}
                    >
                        {domain.icon}
                    </span>
                </div>

                {/* Content */}
                <div className="flex-1">
                    <h3 className="font-semibold text-gray-900">{domain.name}</h3>
                    <p className="text-sm text-gray-600 line-clamp-1">{domain.description}</p>
                </div>

                {/* Task count */}
                <div className="text-right">
                    <span className="text-2xl font-bold text-gray-900">{domain.tasks.length}</span>
                    <p className="text-xs text-gray-500">tasks</p>
                </div>

                {/* Chevron */}
                <span className={`material-icons text-gray-400 transition-transform ${expanded ? 'rotate-180' : ''}`}>
                    expand_more
                </span>
            </div>
        </button>
    );
}

// ============================================================================
// Domain Grid Component
// ============================================================================

export interface DomainGridProps {
    domains: TaskDomain[];
    onDomainClick?: (domain: TaskDomain) => void;
    selectedDomainId?: string;
}

/**
 * Grid component displaying multiple domains
 */
export function DomainGrid({ domains, onDomainClick, selectedDomainId }: DomainGridProps) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {domains.map((domain) => (
                <DomainCard
                    key={domain.id}
                    domain={domain}
                    onClick={onDomainClick}
                    expanded={domain.id === selectedDomainId}
                />
            ))}
        </div>
    );
}

// ============================================================================
// Task Filter Component
// ============================================================================

export interface TaskFilterPanelProps {
    filter: TaskFilter;
    onFilterChange: (filter: TaskFilter) => void;
    domains: TaskDomain[];
}

/**
 * Filter panel for task search and filtering
 */
export function TaskFilterPanel({
    filter,
    onFilterChange,
    domains,
}: TaskFilterPanelProps) {
    const stages: LifecycleStage[] = [
        'intent',
        'context',
        'plan',
        'execute',
        'verify',
        'observe',
        'learn',
        'institutionalize',
    ];

    const personas: Persona[] = ['Developer', 'Tech Lead', 'PM', 'Security', 'DevOps', 'QA'];
    const automationLevels: AutomationLevel[] = ['manual', 'assisted', 'automated'];

    return (
        <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-4">
            {/* Search */}
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                    Search
                </label>
                <div className="relative">
                    <span className="material-icons absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
                        search
                    </span>
                    <input
                        type="text"
                        value={filter.search || ''}
                        onChange={(e) =>
                            onFilterChange({ ...filter, search: e.target.value || undefined })
                        }
                        placeholder="Search tasks..."
                        className="w-full pl-10 pr-4 py-2 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    />
                </div>
            </div>

            {/* Domain */}
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                    Domain
                </label>
                <select
                    value={filter.domain || ''}
                    onChange={(e) =>
                        onFilterChange({
                            ...filter,
                            domain: (e.target.value as TaskDomain['id']) || undefined,
                        })
                    }
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500"
                >
                    <option value="">All Domains</option>
                    {domains.map((d) => (
                        <option key={d.id} value={d.id}>
                            {d.name}
                        </option>
                    ))}
                </select>
            </div>

            {/* Lifecycle Stage */}
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                    Lifecycle Stage
                </label>
                <select
                    value={filter.stage || ''}
                    onChange={(e) =>
                        onFilterChange({
                            ...filter,
                            stage: (e.target.value as LifecycleStage) || undefined,
                        })
                    }
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500"
                >
                    <option value="">All Stages</option>
                    {stages.map((s) => (
                        <option key={s} value={s}>
                            {s.charAt(0).toUpperCase() + s.slice(1)}
                        </option>
                    ))}
                </select>
            </div>

            {/* Persona */}
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                    Persona
                </label>
                <select
                    value={filter.persona || ''}
                    onChange={(e) =>
                        onFilterChange({
                            ...filter,
                            persona: (e.target.value as Persona) || undefined,
                        })
                    }
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500"
                >
                    <option value="">All Personas</option>
                    {personas.map((p) => (
                        <option key={p} value={p}>
                            {p}
                        </option>
                    ))}
                </select>
            </div>

            {/* Automation Level */}
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                    Automation Level
                </label>
                <select
                    value={filter.automationLevel || ''}
                    onChange={(e) =>
                        onFilterChange({
                            ...filter,
                            automationLevel: (e.target.value as AutomationLevel) || undefined,
                        })
                    }
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500"
                >
                    <option value="">All Levels</option>
                    {automationLevels.map((l) => (
                        <option key={l} value={l}>
                            {l.charAt(0).toUpperCase() + l.slice(1)}
                        </option>
                    ))}
                </select>
            </div>

            {/* Clear Filters */}
            <button
                onClick={() => onFilterChange({})}
                className="w-full px-4 py-2 text-sm text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
            >
                Clear Filters
            </button>
        </div>
    );
}

// ============================================================================
// Lifecycle Stage Badge Component
// ============================================================================

export interface LifecycleStageBadgeProps {
    stage: LifecycleStage;
    active?: boolean;
    completed?: boolean;
    size?: 'sm' | 'md' | 'lg';
}

const stageConfig: Record<LifecycleStage, { icon: string; color: string }> = {
    intent: { icon: 'lightbulb', color: '#FFD54F' },
    context: { icon: 'explore', color: '#4FC3F7' },
    plan: { icon: 'event_note', color: '#81C784' },
    execute: { icon: 'build', color: '#64B5F6' },
    verify: { icon: 'verified', color: '#9575CD' },
    observe: { icon: 'visibility', color: '#F06292' },
    learn: { icon: 'school', color: '#FFB74D' },
    institutionalize: { icon: 'account_balance', color: '#A1887F' },
};

/**
 * Badge component for lifecycle stages
 */
export function LifecycleStageBadge({
    stage,
    active,
    completed,
    size = 'md',
}: LifecycleStageBadgeProps) {
    const config = stageConfig[stage];
    const sizeClasses = {
        sm: 'w-6 h-6 text-sm',
        md: 'w-8 h-8 text-base',
        lg: 'w-10 h-10 text-lg',
    };

    return (
        <div
            className={`
        ${sizeClasses[size]} rounded-full flex items-center justify-center
        ${active ? 'ring-2 ring-offset-2' : ''}
        ${completed ? 'opacity-100' : 'opacity-50'}
      `}
            style={{
                backgroundColor: `${config.color}20`,
                color: config.color,
                ringColor: active ? config.color : undefined,
            }}
            title={stage.charAt(0).toUpperCase() + stage.slice(1)}
        >
            <span className="material-icons" style={{ fontSize: 'inherit' }}>
                {completed ? 'check' : config.icon}
            </span>
        </div>
    );
}

// ============================================================================
// Lifecycle Stage Timeline Component
// ============================================================================

export interface LifecycleStageTimelineProps {
    stages: LifecycleStage[];
    currentStage?: LifecycleStage;
    completedStages?: LifecycleStage[];
    onStageClick?: (stage: LifecycleStage) => void;
}

/**
 * Timeline component showing lifecycle stages
 */
export function LifecycleStageTimeline({
    stages,
    currentStage,
    completedStages = [],
    onStageClick,
}: LifecycleStageTimelineProps) {
    const allStages: LifecycleStage[] = [
        'intent',
        'context',
        'plan',
        'execute',
        'verify',
        'observe',
        'learn',
        'institutionalize',
    ];

    const relevantStages = stages.length > 0 ? stages : allStages;

    return (
        <div className="flex items-center gap-2">
            {relevantStages.map((stage, index) => {
                const isActive = stage === currentStage;
                const isCompleted = completedStages.includes(stage);

                return (
                    <React.Fragment key={stage}>
                        <button
                            onClick={() => onStageClick?.(stage)}
                            className="focus:outline-none focus:ring-2 focus:ring-blue-500 rounded-full"
                            disabled={!onStageClick}
                        >
                            <LifecycleStageBadge
                                stage={stage}
                                active={isActive}
                                completed={isCompleted}
                                size="md"
                            />
                        </button>
                        {index < relevantStages.length - 1 && (
                            <div
                                className={`flex-1 h-0.5 min-w-4 ${isCompleted ? 'bg-green-400' : 'bg-gray-200'
                                    }`}
                            />
                        )}
                    </React.Fragment>
                );
            })}
        </div>
    );
}

// ============================================================================
// Task Stats Component
// ============================================================================

export interface TaskStatsProps {
    className?: string;
}

/**
 * Component showing task registry statistics
 */
export function TaskStats({ className }: TaskStatsProps) {
    const stats = useAtomValue(registryStatsAtom);

    return (
        <div className={`grid grid-cols-2 md:grid-cols-4 gap-4 ${className || ''}`}>
            <div className="bg-white rounded-xl border border-gray-200 p-4">
                <p className="text-3xl font-bold text-gray-900">{stats.totalDomains}</p>
                <p className="text-sm text-gray-500">Domains</p>
            </div>
            <div className="bg-white rounded-xl border border-gray-200 p-4">
                <p className="text-3xl font-bold text-gray-900">{stats.totalTasks}</p>
                <p className="text-sm text-gray-500">Tasks</p>
            </div>
            <div className="bg-white rounded-xl border border-gray-200 p-4">
                <p className="text-3xl font-bold text-gray-900">{stats.totalWorkflows}</p>
                <p className="text-sm text-gray-500">Workflows</p>
            </div>
            <div className="bg-white rounded-xl border border-gray-200 p-4">
                <p className="text-3xl font-bold text-gray-900">{stats.totalStages}</p>
                <p className="text-sm text-gray-500">Stages</p>
            </div>
        </div>
    );
}

// ============================================================================
// Export all components
// ============================================================================

export {
    TaskCard,
    TaskList,
    DomainCard,
    DomainGrid,
    TaskFilterPanel,
    LifecycleStageBadge,
    LifecycleStageTimeline,
    TaskStats,
};
