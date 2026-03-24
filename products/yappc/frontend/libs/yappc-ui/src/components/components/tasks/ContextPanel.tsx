/**
 * YAPPC Context Panel
 *
 * Collapsible panel showing workflow context: stage progress, 
 * completed tasks, and generated artifacts.
 *
 * @module ui/tasks/ContextPanel
 */

import React, { useState, useMemo, useCallback } from 'react';
import type {
    TaskExecution,
    LifecycleStage,
    LifecycleStageConfig,
    WorkflowExecution,
    TaskDefinition,
} from '@ghatana/types/tasks';

// ============================================================================
// Types
// ============================================================================

export interface ContextPanelProps {
    /** Current workflow execution context */
    workflowExecution?: WorkflowExecution;
    /** Completed task executions in this workflow */
    completedTasks?: TaskExecution[];
    /** All lifecycle stages configuration */
    stages: LifecycleStageConfig[];
    /** Current stage */
    currentStage?: LifecycleStage;
    /** Task definitions for lookup */
    taskDefinitions: Map<string, TaskDefinition>;
    /** Panel position */
    position?: 'left' | 'right';
    /** Default collapsed state */
    defaultCollapsed?: boolean;
    /** On task click handler */
    onTaskClick?: (execution: TaskExecution) => void;
    /** On artifact click handler */
    onArtifactClick?: (artifact: ArtifactSummary) => void;
    /** Custom CSS classes */
    className?: string;
}

export interface ArtifactSummary {
    id: string;
    type: string;
    name: string;
    path?: string;
    taskId: string;
    taskName: string;
    timestamp: Date;
}

export interface StageProgress {
    stage: LifecycleStageConfig;
    status: 'completed' | 'current' | 'pending';
    tasksCompleted: number;
    totalTasks: number;
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * Context Panel Component
 *
 * Displays workflow context including stage progress, completed tasks,
 * and generated artifacts in a collapsible drawer.
 */
export function ContextPanel({
    workflowExecution,
    completedTasks = [],
    stages,
    currentStage,
    taskDefinitions,
    position = 'right',
    defaultCollapsed = false,
    onTaskClick,
    onArtifactClick,
    className,
}: ContextPanelProps): React.ReactElement {
    const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
    const [activeSection, setActiveSection] = useState<'progress' | 'tasks' | 'artifacts'>(
        'progress',
    );

    // Calculate stage progress
    const stageProgress = useMemo<StageProgress[]>(() => {
        const currentStageIndex = currentStage
            ? stages.findIndex((s) => s.id === currentStage)
            : -1;

        return stages.map((stage, index) => {
            const stageTasks = completedTasks.filter((task) => {
                const taskDef = taskDefinitions.get(task.taskId);
                return taskDef?.lifecycleStages.includes(stage.id);
            });

            let status: 'completed' | 'current' | 'pending';
            if (index < currentStageIndex) {
                status = 'completed';
            } else if (index === currentStageIndex) {
                status = 'current';
            } else {
                status = 'pending';
            }

            return {
                stage,
                status,
                tasksCompleted: stageTasks.length,
                totalTasks: 0, // Would need workflow template to know total
            };
        });
    }, [stages, currentStage, completedTasks, taskDefinitions]);

    // Collect all artifacts
    const allArtifacts = useMemo<ArtifactSummary[]>(() => {
        const artifacts: ArtifactSummary[] = [];

        for (const execution of completedTasks) {
            const taskDef = taskDefinitions.get(execution.taskId);
            const taskName = taskDef?.name || execution.taskId;

            if (execution.artifacts) {
                for (const artifact of execution.artifacts) {
                    artifacts.push({
                        id: `${execution.id}-${artifact.type}`,
                        type: artifact.type,
                        name: artifact.type,
                        path: artifact.path,
                        taskId: execution.taskId,
                        taskName,
                        timestamp: new Date(artifact.timestamp),
                    });
                }
            }
        }

        return artifacts.sort(
            (a, b) => b.timestamp.getTime() - a.timestamp.getTime(),
        );
    }, [completedTasks, taskDefinitions]);

    const toggleCollapsed = useCallback(() => {
        setIsCollapsed((prev) => !prev);
    }, []);

    const positionClasses =
        position === 'right'
            ? 'right-0 border-l'
            : 'left-0 border-r';

    return (
        <div
            className={`
                fixed top-0 h-full bg-white shadow-lg z-40 transition-all duration-300
                ${positionClasses}
                ${isCollapsed ? 'w-12' : 'w-80'}
                ${className || ''}
            `}
        >
            {/* Toggle Button */}
            <button
                onClick={toggleCollapsed}
                className={`
                    absolute top-1/2 -translate-y-1/2 w-6 h-12 bg-white border rounded-lg
                    flex items-center justify-center shadow-md hover:bg-gray-50
                    ${position === 'right' ? '-left-3' : '-right-3'}
                `}
            >
                <span className="material-icons text-gray-500 text-sm">
                    {isCollapsed
                        ? position === 'right'
                            ? 'chevron_left'
                            : 'chevron_right'
                        : position === 'right'
                            ? 'chevron_right'
                            : 'chevron_left'}
                </span>
            </button>

            {/* Collapsed State */}
            {isCollapsed ? (
                <CollapsedView
                    completedCount={completedTasks.length}
                    artifactCount={allArtifacts.length}
                    currentStage={currentStage}
                />
            ) : (
                <ExpandedView
                    activeSection={activeSection}
                    onSectionChange={setActiveSection}
                    stageProgress={stageProgress}
                    completedTasks={completedTasks}
                    artifacts={allArtifacts}
                    taskDefinitions={taskDefinitions}
                    workflowExecution={workflowExecution}
                    onTaskClick={onTaskClick}
                    onArtifactClick={onArtifactClick}
                />
            )}
        </div>
    );
}

// ============================================================================
// Collapsed View
// ============================================================================

interface CollapsedViewProps {
    completedCount: number;
    artifactCount: number;
    currentStage?: LifecycleStage;
}

function CollapsedView({
    completedCount,
    artifactCount,
    currentStage,
}: CollapsedViewProps) {
    return (
        <div className="h-full flex flex-col items-center py-4 gap-4">
            {/* Stage indicator */}
            {currentStage && (
                <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center">
                    <StageIcon stage={currentStage} />
                </div>
            )}

            {/* Task count */}
            <div className="text-center">
                <div className="text-lg font-semibold text-gray-700">
                    {completedCount}
                </div>
                <span className="material-icons text-gray-400 text-sm">task_alt</span>
            </div>

            {/* Artifact count */}
            <div className="text-center">
                <div className="text-lg font-semibold text-gray-700">
                    {artifactCount}
                </div>
                <span className="material-icons text-gray-400 text-sm">folder</span>
            </div>
        </div>
    );
}

// ============================================================================
// Expanded View
// ============================================================================

interface ExpandedViewProps {
    activeSection: 'progress' | 'tasks' | 'artifacts';
    onSectionChange: (section: 'progress' | 'tasks' | 'artifacts') => void;
    stageProgress: StageProgress[];
    completedTasks: TaskExecution[];
    artifacts: ArtifactSummary[];
    taskDefinitions: Map<string, TaskDefinition>;
    workflowExecution?: WorkflowExecution;
    onTaskClick?: (execution: TaskExecution) => void;
    onArtifactClick?: (artifact: ArtifactSummary) => void;
}

function ExpandedView({
    activeSection,
    onSectionChange,
    stageProgress,
    completedTasks,
    artifacts,
    taskDefinitions,
    workflowExecution,
    onTaskClick,
    onArtifactClick,
}: ExpandedViewProps) {
    const sections = [
        { id: 'progress', label: 'Progress', icon: 'timeline' },
        { id: 'tasks', label: 'Tasks', icon: 'task_alt', count: completedTasks.length },
        { id: 'artifacts', label: 'Artifacts', icon: 'folder', count: artifacts.length },
    ] as const;

    return (
        <div className="h-full flex flex-col">
            {/* Header */}
            <div className="p-4 border-b">
                <h2 className="font-semibold text-gray-900">Workflow Context</h2>
                {workflowExecution && (
                    <p className="text-sm text-gray-500 mt-1">
                        {workflowExecution.workflowId}
                    </p>
                )}
            </div>

            {/* Section Tabs */}
            <div className="flex border-b">
                {sections.map((section) => (
                    <button
                        key={section.id}
                        onClick={() => onSectionChange(section.id)}
                        className={`
                            flex-1 flex items-center justify-center gap-1 py-3 text-sm
                            border-b-2 -mb-px transition-colors
                            ${activeSection === section.id
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-500 hover:text-gray-700'
                            }
                        `}
                    >
                        <span className="material-icons text-lg">{section.icon}</span>
                        {'count' in section && section.count !== undefined && (
                            <span className="text-xs bg-gray-200 px-1.5 rounded-full">
                                {section.count}
                            </span>
                        )}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto">
                {activeSection === 'progress' && (
                    <StageProgressView stageProgress={stageProgress} />
                )}
                {activeSection === 'tasks' && (
                    <CompletedTasksView
                        tasks={completedTasks}
                        taskDefinitions={taskDefinitions}
                        onTaskClick={onTaskClick}
                    />
                )}
                {activeSection === 'artifacts' && (
                    <ArtifactsView
                        artifacts={artifacts}
                        onArtifactClick={onArtifactClick}
                    />
                )}
            </div>
        </div>
    );
}

// ============================================================================
// Stage Progress View
// ============================================================================

interface StageProgressViewProps {
    stageProgress: StageProgress[];
}

function StageProgressView({ stageProgress }: StageProgressViewProps) {
    return (
        <div className="p-4">
            <div className="space-y-3">
                {stageProgress.map((progress) => (
                    <StageProgressItem key={progress.stage.id} progress={progress} />
                ))}
            </div>
        </div>
    );
}

interface StageProgressItemProps {
    progress: StageProgress;
}

function StageProgressItem({ progress }: StageProgressItemProps) {
    const statusColors = {
        completed: 'bg-green-100 border-green-300 text-green-700',
        current: 'bg-blue-100 border-blue-300 text-blue-700',
        pending: 'bg-gray-50 border-gray-200 text-gray-400',
    };

    const iconColors = {
        completed: 'text-green-500',
        current: 'text-blue-500',
        pending: 'text-gray-300',
    };

    return (
        <div
            className={`
                p-3 rounded-lg border flex items-center gap-3
                ${statusColors[progress.status]}
            `}
        >
            <div className={`${iconColors[progress.status]}`}>
                {progress.status === 'completed' ? (
                    <span className="material-icons">check_circle</span>
                ) : progress.status === 'current' ? (
                    <span className="material-icons animate-pulse">radio_button_checked</span>
                ) : (
                    <span className="material-icons">radio_button_unchecked</span>
                )}
            </div>
            <div className="flex-1">
                <div className="font-medium text-sm">{progress.stage.name}</div>
                {progress.tasksCompleted > 0 && (
                    <div className="text-xs opacity-75">
                        {progress.tasksCompleted} task{progress.tasksCompleted !== 1 ? 's' : ''} completed
                    </div>
                )}
            </div>
            <span
                className="material-icons text-lg"
                style={{ color: progress.stage.color }}
            >
                {progress.stage.icon}
            </span>
        </div>
    );
}

// ============================================================================
// Completed Tasks View
// ============================================================================

interface CompletedTasksViewProps {
    tasks: TaskExecution[];
    taskDefinitions: Map<string, TaskDefinition>;
    onTaskClick?: (execution: TaskExecution) => void;
}

function CompletedTasksView({
    tasks,
    taskDefinitions,
    onTaskClick,
}: CompletedTasksViewProps) {
    if (tasks.length === 0) {
        return (
            <div className="p-8 text-center text-gray-500">
                <span className="material-icons text-4xl mb-2">hourglass_empty</span>
                <p>No tasks completed yet</p>
            </div>
        );
    }

    // Sort by completion time, most recent first
    const sortedTasks = [...tasks].sort((a, b) => {
        const aTime = a.completedAt ? new Date(a.completedAt).getTime() : 0;
        const bTime = b.completedAt ? new Date(b.completedAt).getTime() : 0;
        return bTime - aTime;
    });

    return (
        <div className="p-4 space-y-2">
            {sortedTasks.map((execution) => {
                const taskDef = taskDefinitions.get(execution.taskId);
                return (
                    <CompletedTaskItem
                        key={execution.id}
                        execution={execution}
                        taskDef={taskDef}
                        onClick={onTaskClick}
                    />
                );
            })}
        </div>
    );
}

interface CompletedTaskItemProps {
    execution: TaskExecution;
    taskDef?: TaskDefinition;
    onClick?: (execution: TaskExecution) => void;
}

function CompletedTaskItem({
    execution,
    taskDef,
    onClick,
}: CompletedTaskItemProps) {
    const handleClick = useCallback(() => {
        onClick?.(execution);
    }, [onClick, execution]);

    return (
        <button
            onClick={handleClick}
            className="w-full text-left p-3 rounded-lg border bg-white hover:bg-gray-50 transition-colors"
        >
            <div className="flex items-center gap-3">
                <div
                    className="w-8 h-8 rounded-lg flex items-center justify-center"
                    style={{
                        backgroundColor: taskDef ? `${taskDef.ui.color}20` : '#f3f4f6',
                    }}
                >
                    <span
                        className="material-icons text-sm"
                        style={{ color: taskDef?.ui.color || '#9ca3af' }}
                    >
                        {taskDef?.ui.icon || 'task'}
                    </span>
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-medium text-sm truncate">
                        {taskDef?.name || execution.taskId}
                    </div>
                    <div className="text-xs text-gray-500">
                        {execution.completedAt
                            ? new Date(execution.completedAt).toLocaleTimeString()
                            : 'In progress'}
                    </div>
                </div>
                <StatusIcon status={execution.status} />
            </div>
        </button>
    );
}

// ============================================================================
// Artifacts View
// ============================================================================

interface ArtifactsViewProps {
    artifacts: ArtifactSummary[];
    onArtifactClick?: (artifact: ArtifactSummary) => void;
}

function ArtifactsView({ artifacts, onArtifactClick }: ArtifactsViewProps) {
    if (artifacts.length === 0) {
        return (
            <div className="p-8 text-center text-gray-500">
                <span className="material-icons text-4xl mb-2">folder_off</span>
                <p>No artifacts yet</p>
            </div>
        );
    }

    // Group artifacts by type
    const grouped = artifacts.reduce(
        (acc, artifact) => {
            if (!acc[artifact.type]) {
                acc[artifact.type] = [];
            }
            acc[artifact.type].push(artifact);
            return acc;
        },
        {} as Record<string, ArtifactSummary[]>,
    );

    return (
        <div className="p-4 space-y-4">
            {Object.entries(grouped).map(([type, typeArtifacts]) => (
                <div key={type}>
                    <h4 className="text-xs font-medium text-gray-500 uppercase mb-2">
                        {type} ({typeArtifacts.length})
                    </h4>
                    <div className="space-y-1">
                        {typeArtifacts.map((artifact) => (
                            <ArtifactItem
                                key={artifact.id}
                                artifact={artifact}
                                onClick={onArtifactClick}
                            />
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
}

interface ArtifactItemProps {
    artifact: ArtifactSummary;
    onClick?: (artifact: ArtifactSummary) => void;
}

function ArtifactItem({ artifact, onClick }: ArtifactItemProps) {
    const handleClick = useCallback(() => {
        onClick?.(artifact);
    }, [onClick, artifact]);

    const artifactIcons: Record<string, string> = {
        InputSnapshot: 'input',
        OutputSnapshot: 'output',
        ChangeSet: 'difference',
        DecisionRecord: 'gavel',
        VerificationResult: 'verified',
        PolicyCheck: 'policy',
        IncidentRecord: 'warning',
        RootCauseReport: 'search',
        PerformanceReport: 'speed',
        SecurityReview: 'security',
        VulnerabilityReport: 'bug_report',
        ComplianceReport: 'assignment_turned_in',
        ReviewComments: 'rate_review',
        ADR: 'architecture',
        SchemaDesign: 'schema',
        DataModel: 'table_chart',
        PipelineDesign: 'account_tree',
        BackupPlan: 'backup',
        MigrationRecord: 'swap_horiz',
        BugFixRecord: 'pest_control',
        TechDebtRecord: 'construction',
        DependencyReport: 'hub',
        ProgressRecord: 'trending_up',
        AIGeneratedCode: 'smart_toy',
        AIAnalysis: 'psychology',
        AuditTrail: 'history',
    };

    return (
        <button
            onClick={handleClick}
            className="w-full text-left p-2 rounded hover:bg-gray-50 flex items-center gap-2"
        >
            <span className="material-icons text-gray-400 text-sm">
                {artifactIcons[artifact.type] || 'description'}
            </span>
            <div className="flex-1 min-w-0">
                <div className="text-sm truncate">{artifact.name}</div>
                <div className="text-xs text-gray-400 truncate">
                    {artifact.taskName}
                </div>
            </div>
        </button>
    );
}

// ============================================================================
// Helper Components
// ============================================================================

interface StageIconProps {
    stage: LifecycleStage;
}

function StageIcon({ stage }: StageIconProps) {
    const icons: Record<LifecycleStage, string> = {
        intent: 'lightbulb',
        context: 'explore',
        plan: 'map',
        execute: 'rocket_launch',
        verify: 'verified',
        observe: 'visibility',
        learn: 'school',
        institutionalize: 'account_balance',
    };

    return (
        <span className="material-icons text-blue-600 text-sm">
            {icons[stage] || 'circle'}
        </span>
    );
}

interface StatusIconProps {
    status: string;
}

function StatusIcon({ status }: StatusIconProps) {
    const statusConfig: Record<string, { icon: string; color: string }> = {
        completed: { icon: 'check_circle', color: 'text-green-500' },
        failed: { icon: 'error', color: 'text-red-500' },
        running: { icon: 'autorenew', color: 'text-blue-500 animate-spin' },
        pending: { icon: 'schedule', color: 'text-gray-400' },
        cancelled: { icon: 'cancel', color: 'text-gray-500' },
    };

    const config = statusConfig[status] || statusConfig.pending;

    return (
        <span className={`material-icons text-lg ${config.color}`}>
            {config.icon}
        </span>
    );
}

export default ContextPanel;
