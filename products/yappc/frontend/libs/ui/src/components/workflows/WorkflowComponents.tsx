/**
 * YAPPC Workflow Components
 *
 * UI components for displaying and interacting with workflows.
 * Follows existing DevSecOps component patterns.
 *
 * @module ui/workflows
 */

import React, { useMemo, useCallback, useState } from 'react';
import { useAtomValue } from 'jotai';
import type {
    WorkflowDefinition,
    WorkflowPhase,
    WorkflowExecution,
    WorkflowExecutionStatus,
    WorkflowCategory,
    LifecycleStage,
    TaskDefinition,
} from '@ghatana/types/tasks';
import {
    allWorkflowsAtom,
    workflowByIdAtom,
    workflowsByCategoryAtom,
    taskByIdAtom,
} from '@ghatana/state/tasks/taskRegistryStore';
import { LifecycleStageBadge, LifecycleStageTimeline } from './TaskComponents';

// ============================================================================
// Category Configuration
// ============================================================================

const categoryConfig: Record<WorkflowCategory, { icon: string; color: string; label: string }> = {
    discovery: { icon: 'search', color: '#2196F3', label: 'Discovery' },
    delivery: { icon: 'rocket_launch', color: '#4CAF50', label: 'Delivery' },
    maintenance: { icon: 'build', color: '#FF9800', label: 'Maintenance' },
    quality: { icon: 'verified', color: '#9C27B0', label: 'Quality' },
    operations: { icon: 'settings', color: '#00BCD4', label: 'Operations' },
    security: { icon: 'security', color: '#F44336', label: 'Security' },
    documentation: { icon: 'description', color: '#607D8B', label: 'Documentation' },
};

// ============================================================================
// Workflow Card Component
// ============================================================================

export interface WorkflowCardProps {
    workflow: WorkflowDefinition;
    onClick?: (workflow: WorkflowDefinition) => void;
    selected?: boolean;
}

/**
 * Card component displaying a single workflow
 */
export function WorkflowCard({ workflow, onClick, selected }: WorkflowCardProps) {
    const category = categoryConfig[workflow.category];

    const handleClick = useCallback(() => {
        onClick?.(workflow);
    }, [onClick, workflow]);

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
                    className="w-12 h-12 rounded-lg flex items-center justify-center"
                    style={{ backgroundColor: `${workflow.color}20` }}
                >
                    <span
                        className="material-icons text-2xl"
                        style={{ color: workflow.color }}
                    >
                        {workflow.icon}
                    </span>
                </div>

                {/* Content */}
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                        <h3 className="font-semibold text-gray-900 truncate">{workflow.name}</h3>
                        <span
                            className="px-2 py-0.5 text-xs rounded-full"
                            style={{ backgroundColor: `${category.color}20`, color: category.color }}
                        >
                            {category.label}
                        </span>
                    </div>
                    <p className="text-sm text-gray-600 line-clamp-2">{workflow.description}</p>

                    {/* Meta */}
                    <div className="flex items-center gap-4 mt-3 text-sm text-gray-500">
                        <span className="flex items-center gap-1">
                            <span className="material-icons text-base">schedule</span>
                            {workflow.estimatedDuration}
                        </span>
                        <span className="flex items-center gap-1">
                            <span className="material-icons text-base">layers</span>
                            {workflow.phases.length} phases
                        </span>
                    </div>

                    {/* Lifecycle Stages */}
                    <div className="mt-3">
                        <LifecycleStageTimeline stages={workflow.lifecycleStages} />
                    </div>
                </div>
            </div>
        </button>
    );
}

// ============================================================================
// Workflow Grid Component
// ============================================================================

export interface WorkflowGridProps {
    workflows: WorkflowDefinition[];
    onWorkflowClick?: (workflow: WorkflowDefinition) => void;
    selectedWorkflowId?: string;
}

/**
 * Grid component displaying multiple workflows
 */
export function WorkflowGrid({ workflows, onWorkflowClick, selectedWorkflowId }: WorkflowGridProps) {
    if (workflows.length === 0) {
        return (
            <div className="text-center py-8 text-gray-500">
                <span className="material-icons text-4xl mb-2">work_off</span>
                <p>No workflows found</p>
            </div>
        );
    }

    return (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {workflows.map((workflow) => (
                <WorkflowCard
                    key={workflow.id}
                    workflow={workflow}
                    onClick={onWorkflowClick}
                    selected={workflow.id === selectedWorkflowId}
                />
            ))}
        </div>
    );
}

// ============================================================================
// Workflow Category Tabs Component
// ============================================================================

export interface WorkflowCategoryTabsProps {
    selectedCategory?: WorkflowCategory;
    onCategoryChange: (category: WorkflowCategory | undefined) => void;
    workflowCounts: Record<string, number>;
}

/**
 * Tab component for filtering workflows by category
 */
export function WorkflowCategoryTabs({
    selectedCategory,
    onCategoryChange,
    workflowCounts,
}: WorkflowCategoryTabsProps) {
    const categories = Object.entries(categoryConfig) as [WorkflowCategory, typeof categoryConfig[WorkflowCategory]][];

    return (
        <div className="flex flex-wrap gap-2">
            <button
                onClick={() => onCategoryChange(undefined)}
                className={`
          px-4 py-2 rounded-lg text-sm font-medium transition-colors
          ${!selectedCategory ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}
        `}
            >
                All
            </button>
            {categories.map(([key, config]) => {
                const count = workflowCounts[key] || 0;
                if (count === 0) return null;

                return (
                    <button
                        key={key}
                        onClick={() => onCategoryChange(key)}
                        className={`
              px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2
              ${selectedCategory === key
                                ? 'text-white'
                                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }
            `}
                        style={selectedCategory === key ? { backgroundColor: config.color } : undefined}
                    >
                        <span className="material-icons text-base">{config.icon}</span>
                        {config.label}
                        <span className="px-1.5 py-0.5 rounded-full text-xs bg-white/20">
                            {count}
                        </span>
                    </button>
                );
            })}
        </div>
    );
}

// ============================================================================
// Workflow Phase Card Component
// ============================================================================

export interface WorkflowPhaseCardProps {
    phase: WorkflowPhase;
    phaseIndex: number;
    totalPhases: number;
    tasks: TaskDefinition[];
    isActive?: boolean;
    isCompleted?: boolean;
}

/**
 * Card component displaying a workflow phase
 */
export function WorkflowPhaseCard({
    phase,
    phaseIndex,
    totalPhases,
    tasks,
    isActive,
    isCompleted,
}: WorkflowPhaseCardProps) {
    const [expanded, setExpanded] = useState(isActive);

    return (
        <div
            className={`
        rounded-xl border transition-all
        ${isActive ? 'border-blue-500 bg-blue-50' : isCompleted ? 'border-green-300 bg-green-50' : 'border-gray-200 bg-white'}
      `}
        >
            {/* Phase Header */}
            <button
                onClick={() => setExpanded(!expanded)}
                className="w-full p-4 flex items-center gap-3 text-left"
            >
                {/* Phase Number */}
                <div
                    className={`
            w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm
            ${isCompleted ? 'bg-green-500 text-white' : isActive ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-600'}
          `}
                >
                    {isCompleted ? (
                        <span className="material-icons text-sm">check</span>
                    ) : (
                        phaseIndex + 1
                    )}
                </div>

                {/* Phase Info */}
                <div className="flex-1">
                    <h4 className="font-semibold text-gray-900">{phase.name}</h4>
                    <div className="flex items-center gap-2 text-sm text-gray-500">
                        <span>{tasks.length} tasks</span>
                        <span>•</span>
                        <div className="flex gap-1">
                            {phase.stages.map((stage) => (
                                <LifecycleStageBadge key={stage} stage={stage} size="sm" />
                            ))}
                        </div>
                    </div>
                </div>

                {/* Status */}
                {isActive && (
                    <span className="px-2 py-1 text-xs rounded-full bg-blue-500 text-white">
                        Active
                    </span>
                )}
                {isCompleted && (
                    <span className="px-2 py-1 text-xs rounded-full bg-green-500 text-white">
                        Completed
                    </span>
                )}

                {/* Expand */}
                <span className={`material-icons text-gray-400 transition-transform ${expanded ? 'rotate-180' : ''}`}>
                    expand_more
                </span>
            </button>

            {/* Phase Tasks */}
            {expanded && (
                <div className="px-4 pb-4 space-y-2">
                    {tasks.map((task) => (
                        <div
                            key={task.id}
                            className="flex items-center gap-2 p-2 rounded-lg bg-white border border-gray-100"
                        >
                            <span
                                className="material-icons text-lg"
                                style={{ color: task.ui.color }}
                            >
                                {task.ui.icon}
                            </span>
                            <span className="text-sm text-gray-700 flex-1">{task.name}</span>
                            <span
                                className={`
                  px-2 py-0.5 text-xs rounded-full
                  ${task.automationLevel === 'automated' ? 'bg-green-100 text-green-700' :
                                        task.automationLevel === 'assisted' ? 'bg-blue-100 text-blue-700' :
                                            'bg-gray-100 text-gray-700'}
                `}
                            >
                                {task.automationLevel}
                            </span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

// ============================================================================
// Workflow Detail Component
// ============================================================================

export interface WorkflowDetailProps {
    workflow: WorkflowDefinition;
    onClose?: () => void;
    onStart?: (workflow: WorkflowDefinition) => void;
}

/**
 * Detailed view of a workflow with phases and tasks
 */
export function WorkflowDetail({ workflow, onClose, onStart }: WorkflowDetailProps) {
    const getTask = useAtomValue(taskByIdAtom);
    const category = categoryConfig[workflow.category];

    // Get tasks for each phase
    const phasesWithTasks = useMemo(() => {
        return workflow.phases.map((phase) => ({
            phase,
            tasks: phase.tasks.map((taskId) => getTask(taskId)).filter(Boolean) as TaskDefinition[],
        }));
    }, [workflow.phases, getTask]);

    return (
        <div className="bg-white rounded-2xl shadow-xl overflow-hidden max-w-2xl w-full">
            {/* Header */}
            <div
                className="p-6"
                style={{ backgroundColor: `${workflow.color}10` }}
            >
                <div className="flex items-start gap-4">
                    <div
                        className="w-14 h-14 rounded-xl flex items-center justify-center"
                        style={{ backgroundColor: `${workflow.color}20` }}
                    >
                        <span
                            className="material-icons text-3xl"
                            style={{ color: workflow.color }}
                        >
                            {workflow.icon}
                        </span>
                    </div>
                    <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                            <h2 className="text-xl font-bold text-gray-900">{workflow.name}</h2>
                            <span
                                className="px-2 py-0.5 text-xs rounded-full"
                                style={{ backgroundColor: `${category.color}20`, color: category.color }}
                            >
                                {category.label}
                            </span>
                        </div>
                        <p className="text-gray-600">{workflow.description}</p>
                        <div className="flex items-center gap-4 mt-2 text-sm text-gray-500">
                            <span className="flex items-center gap-1">
                                <span className="material-icons text-base">schedule</span>
                                {workflow.estimatedDuration}
                            </span>
                            <span className="flex items-center gap-1">
                                <span className="material-icons text-base">layers</span>
                                {workflow.phases.length} phases
                            </span>
                        </div>
                    </div>
                    {onClose && (
                        <button
                            onClick={onClose}
                            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
                        >
                            <span className="material-icons text-gray-500">close</span>
                        </button>
                    )}
                </div>

                {/* Lifecycle Stages */}
                <div className="mt-4">
                    <p className="text-sm text-gray-500 mb-2">Lifecycle Stages</p>
                    <LifecycleStageTimeline stages={workflow.lifecycleStages} />
                </div>
            </div>

            {/* Phases */}
            <div className="p-6 space-y-3 max-h-96 overflow-y-auto">
                <h3 className="font-semibold text-gray-900 mb-3">Workflow Phases</h3>
                {phasesWithTasks.map(({ phase, tasks }, index) => (
                    <WorkflowPhaseCard
                        key={phase.id}
                        phase={phase}
                        phaseIndex={index}
                        totalPhases={workflow.phases.length}
                        tasks={tasks}
                        isActive={index === 0}
                    />
                ))}
            </div>

            {/* Actions */}
            <div className="p-6 border-t border-gray-200 flex justify-end gap-3">
                {onClose && (
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                    >
                        Cancel
                    </button>
                )}
                {onStart && (
                    <button
                        onClick={() => onStart(workflow)}
                        className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors flex items-center gap-2"
                    >
                        <span className="material-icons text-lg">play_arrow</span>
                        Start Workflow
                    </button>
                )}
            </div>
        </div>
    );
}

// ============================================================================
// Workflow Execution Status Component
// ============================================================================

export interface WorkflowExecutionStatusProps {
    execution: WorkflowExecution;
    workflow: WorkflowDefinition;
}

const statusConfig: Record<WorkflowExecutionStatus, { icon: string; color: string; label: string }> = {
    pending: { icon: 'pending', color: '#9E9E9E', label: 'Pending' },
    'in-progress': { icon: 'sync', color: '#2196F3', label: 'In Progress' },
    paused: { icon: 'pause', color: '#FF9800', label: 'Paused' },
    completed: { icon: 'check_circle', color: '#4CAF50', label: 'Completed' },
    failed: { icon: 'error', color: '#F44336', label: 'Failed' },
    cancelled: { icon: 'cancel', color: '#9E9E9E', label: 'Cancelled' },
};

/**
 * Component showing workflow execution status
 */
export function WorkflowExecutionStatusBadge({ execution, workflow }: WorkflowExecutionStatusProps) {
    const status = statusConfig[execution.status];

    // Calculate progress
    const completedTasks = execution.taskExecutions.filter(
        (t) => t.status === 'completed'
    ).length;
    const totalTasks = workflow.phases.reduce(
        (sum, phase) => sum + phase.tasks.length,
        0
    );
    const progress = totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 100) : 0;

    return (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-center gap-3 mb-3">
                <span
                    className="material-icons text-2xl"
                    style={{ color: status.color }}
                >
                    {status.icon}
                </span>
                <div className="flex-1">
                    <h4 className="font-semibold text-gray-900">{workflow.name}</h4>
                    <p className="text-sm text-gray-500">{status.label}</p>
                </div>
                <span className="text-2xl font-bold text-gray-900">{progress}%</span>
            </div>

            {/* Progress Bar */}
            <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                        width: `${progress}%`,
                        backgroundColor: status.color,
                    }}
                />
            </div>

            {/* Meta */}
            <div className="flex items-center gap-4 mt-3 text-sm text-gray-500">
                <span>
                    {completedTasks}/{totalTasks} tasks
                </span>
                {execution.currentPhase && (
                    <span className="flex items-center gap-1">
                        <span className="material-icons text-base">layers</span>
                        {execution.currentPhase}
                    </span>
                )}
                {execution.currentStage && (
                    <LifecycleStageBadge stage={execution.currentStage} size="sm" />
                )}
            </div>
        </div>
    );
}

// ============================================================================
// Export all components
// ============================================================================

export {
    WorkflowCard,
    WorkflowGrid,
    WorkflowCategoryTabs,
    WorkflowPhaseCard,
    WorkflowDetail,
    WorkflowExecutionStatusBadge,
    categoryConfig,
    statusConfig,
};
