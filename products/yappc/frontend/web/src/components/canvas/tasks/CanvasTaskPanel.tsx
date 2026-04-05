/**
 * Canvas Task Panel
 * 
 * Smart collapsible side panel showing tasks for the current lifecycle phase.
 * Features:
 * - Collapsed state: 48px with task count badge
 * - Expanded state: 280px full task list
 * - Hover-to-expand for quick peek
 * - Pin to keep expanded
 * - Displays task status, progress, and allows quick actions
 * 
 * @doc.type component
 * @doc.purpose Task visibility in canvas context
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useState, useMemo, useCallback, useEffect } from 'react';
import { CheckCircle, Circle as RadioButtonUnchecked, Play as PlayArrow, Pause, Plus as Add, ChevronRight, Sparkles as AutoAwesome, Clock as Schedule, Flag } from 'lucide-react';

import { useProjectTasks, type ProjectTask, type TaskStatus } from '../../../hooks/useProjectTasks';
import { useLifecyclePhase } from '../../../hooks/useLifecyclePhase';
import { PHASE_LABELS } from '../../../types/lifecycle';
import { PANELS, TRANSITIONS, RADIUS } from '../../../styles/design-tokens';

// ============================================================================
// Types
// ============================================================================

export interface CanvasTaskPanelProps {
    /** Project ID */
    projectId: string;
    /** Whether the panel is collapsed */
    collapsed?: boolean;
    /** Callback when collapse state changes */
    onCollapseChange?: (collapsed: boolean) => void;
    /** Whether the panel is pinned (stays expanded) */
    pinned?: boolean;
    /** Callback when pin state changes */
    onPinChange?: (pinned: boolean) => void;
    /** Enable hover-to-expand behavior */
    hoverExpand?: boolean;
    /** Additional CSS classes */
    className?: string;
}

// Local storage key for panel state persistence
const TASK_PANEL_STATE_KEY = 'yappc_task_panel_state';

// ============================================================================
// Component
// ============================================================================

export function CanvasTaskPanel({
    projectId,
    collapsed: controlledCollapsed,
    onCollapseChange,
    pinned: controlledPinned,
    onPinChange,
    hoverExpand = true,
    className = '',
}: CanvasTaskPanelProps) {
    const { currentPhase } = useLifecyclePhase();
    const {
        tasks,
        getTasksByPhase,
        setTaskStatus,
        createTask,
        getPhaseProgress,
        initializeDefaultTasks,
    } = useProjectTasks(projectId);

    // Load initial state from localStorage
    const [internalState, setInternalState] = useState(() => {
        try {
            const stored = localStorage.getItem(TASK_PANEL_STATE_KEY);
            if (stored) {
                return JSON.parse(stored);
            }
        } catch (e) {
            // Ignore parse errors
        }
        return { collapsed: true, pinned: false };
    });

    // Use controlled or internal state
    const isCollapsed = controlledCollapsed ?? internalState.collapsed;
    const isPinned = controlledPinned ?? internalState.pinned;

    // Hover state for hover-to-expand
    const [isHovered, setIsHovered] = useState(false);

    // Calculate effective expanded state
    const isExpanded = isPinned || (hoverExpand && isHovered) || !isCollapsed;

    const [showAddTask, setShowAddTask] = useState(false);
    const [newTaskName, setNewTaskName] = useState('');

    // Get tasks for current phase
    const phaseTasks = useMemo(() => {
        if (!currentPhase) return [];
        return getTasksByPhase(currentPhase);
    }, [currentPhase, getTasksByPhase]);

    // Phase progress
    const progress = useMemo(() => {
        if (!currentPhase) return { completed: 0, total: 0, percentage: 0 };
        return getPhaseProgress(currentPhase);
    }, [currentPhase, getPhaseProgress]);

    // Persist state changes
    useEffect(() => {
        const newState = { collapsed: isCollapsed, pinned: isPinned };
        localStorage.setItem(TASK_PANEL_STATE_KEY, JSON.stringify(newState));
    }, [isCollapsed, isPinned]);

    // Toggle collapse
    const handleToggleCollapse = useCallback(() => {
        const newCollapsed = !isCollapsed;
        if (onCollapseChange) {
            onCollapseChange(newCollapsed);
        } else {
            setInternalState((prev: { collapsed: boolean; pinned: boolean }) => ({ ...prev, collapsed: newCollapsed }));
        }
    }, [isCollapsed, onCollapseChange]);

    // Toggle pin
    const handleTogglePin = useCallback(() => {
        const newPinned = !isPinned;
        if (onPinChange) {
            onPinChange(newPinned);
        } else {
            setInternalState((prev: { collapsed: boolean; pinned: boolean }) => ({ ...prev, pinned: newPinned }));
        }
    }, [isPinned, onPinChange]);

    // Initialize tasks if none exist
    const handleInitialize = () => {
        initializeDefaultTasks();
    };

    // Add new task
    const handleAddTask = () => {
        if (!newTaskName.trim() || !currentPhase) return;
        createTask({
            name: newTaskName.trim(),
            phase: currentPhase,
        });
        setNewTaskName('');
        setShowAddTask(false);
    };

    // Task status cycle: pending → in-progress → completed
    const handleTaskClick = (task: ProjectTask) => {
        const statusCycle: Record<TaskStatus, TaskStatus> = {
            'pending': 'in-progress',
            'in-progress': 'completed',
            'completed': 'pending',
            'blocked': 'pending',
            'skipped': 'pending',
        };
        setTaskStatus(task.id, statusCycle[task.status]);
    };

    // Collapsed view - minimal with hover preview
    if (!isExpanded) {
        return (
            <div
                className={`
                    ${PANELS.taskCollapsedWidth} ${TRANSITIONS.default}
                    flex flex-col items-center py-3 bg-bg-paper border-r border-divider
                    ${className}
                `}
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
                data-testid="task-panel-collapsed"
            >
                {/* Expand Button */}
                <button
                    onClick={handleToggleCollapse}
                    className={`p-2 ${RADIUS.button} hover:bg-grey-100 dark:hover:bg-grey-800 ${TRANSITIONS.fast}`}
                    title="Expand task panel (hover to preview)"
                >
                    <ChevronRight className="w-5 h-5 text-text-secondary" />
                </button>

                {/* Progress Circle */}
                <div className="mt-4 flex flex-col items-center gap-2">
                    <div
                        className="relative w-9 h-9 rounded-full flex items-center justify-center"
                        style={{
                            background: `conic-gradient(
                                var(--primary-500) ${progress.percentage}%,
                                var(--grey-200) ${progress.percentage}%
                            )`,
                        }}
                    >
                        <div className="w-7 h-7 rounded-full bg-bg-paper flex items-center justify-center">
                            <span className="text-[10px] font-bold text-primary-600">
                                {progress.percentage}%
                            </span>
                        </div>
                    </div>

                    {/* Task Count */}
                    <div className="text-center">
                        <span className="text-sm font-bold text-text-primary">{phaseTasks.length}</span>
                        <p className="text-[9px] text-text-secondary">tasks</p>
                    </div>
                </div>

                {/* Pending Count Badge */}
                {phaseTasks.filter(t => t.status === 'pending').length > 0 && (
                    <div className="mt-2 px-1.5 py-0.5 text-[10px] font-medium bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300 rounded-full">
                        {phaseTasks.filter(t => t.status === 'pending').length}
                    </div>
                )}
            </div>
        );
    }

    // Expanded view
    return (
        <div
            className={`
                flex flex-col bg-bg-paper overflow-hidden h-full
                ${TRANSITIONS.default}
                ${className}
            `}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            data-testid="task-panel-expanded"
        >
            {/* Header */}
            <div className="flex items-center justify-between px-3 py-2.5 border-b border-divider">
                <div className="flex items-center gap-2">
                    <h3 className="font-semibold text-text-primary text-sm">
                        Current Phase Tasks
                    </h3>
                    <span className="px-1.5 py-0.5 text-xs bg-grey-100 dark:bg-grey-700 text-text-secondary rounded">
                        {phaseTasks.length}
                    </span>
                </div>
            </div>

            {/* Progress Bar */}
            <div className="px-3 py-2 border-b border-divider bg-grey-50 dark:bg-grey-800/50">
                <div className="flex items-center justify-between text-xs mb-1">
                    <span className="text-text-secondary">Progress</span>
                    <span className="font-medium text-text-primary">
                        {progress.completed}/{progress.total}
                    </span>
                </div>
                <div className="h-1.5 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                    <div
                        className={`h-full bg-primary-500 rounded-full ${TRANSITIONS.slow}`}
                        style={{ width: `${progress.percentage}%` }}
                    />
                </div>
            </div>

            {/* Task List */}
            <div className="flex-1 overflow-auto">
                {tasks.length === 0 ? (
                    <div className="px-3 py-6 text-center">
                        <p className="text-sm text-text-secondary mb-3">
                            No tasks yet
                        </p>
                        <button
                            onClick={handleInitialize}
                            className={`
                                text-sm text-primary-600 dark:text-primary-400 
                                hover:underline ${TRANSITIONS.fast}
                            `}
                        >
                            Initialize default tasks
                        </button>
                    </div>
                ) : phaseTasks.length === 0 ? (
                    <div className="px-3 py-6 text-center">
                        <p className="text-sm text-text-secondary">
                            No tasks for this phase
                        </p>
                    </div>
                ) : (
                    <ul className="py-1">
                        {phaseTasks.map((task) => (
                            <TaskItem
                                key={task.id}
                                task={task}
                                onClick={() => handleTaskClick(task)}
                            />
                        ))}
                    </ul>
                )}
            </div>

            {/* Add Task */}
            <div className="border-t border-divider p-2">
                {showAddTask ? (
                    <div className="flex gap-2">
                        <input
                            type="text"
                            value={newTaskName}
                            onChange={(e) => setNewTaskName(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter') handleAddTask();
                                if (e.key === 'Escape') setShowAddTask(false);
                            }}
                            placeholder="Task name..."
                            autoFocus
                            className={`
                                flex-1 px-2 py-1 text-sm border border-divider 
                                ${RADIUS.input} focus:outline-none focus:ring-1 focus:ring-primary-500 
                                bg-bg-default
                            `}
                        />
                        <button
                            onClick={handleAddTask}
                            disabled={!newTaskName.trim()}
                            className={`
                                px-2 py-1 text-sm bg-primary-600 text-white 
                                ${RADIUS.button} disabled:opacity-50 
                                hover:bg-primary-700 ${TRANSITIONS.fast}
                            `}
                        >
                            Add
                        </button>
                    </div>
                ) : (
                    <button
                        onClick={() => setShowAddTask(true)}
                        className={`
                            flex items-center gap-2 w-full px-2 py-1.5 text-sm 
                            text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800 
                            ${RADIUS.button} ${TRANSITIONS.fast}
                        `}
                    >
                        <Add className="w-4 h-4" />
                        <span>Add task</span>
                    </button>
                )}
            </div>
        </div>
    );
}

// ============================================================================
// Sub-components
// ============================================================================

interface TaskItemProps {
    task: ProjectTask;
    onClick: () => void;
}

function TaskItem({ task, onClick }: TaskItemProps) {
    const StatusIcon = getStatusIcon(task.status);
    const statusColor = getStatusColor(task.status);
    const priorityColor = getPriorityColor(task.priority);

    return (
        <li>
            <button
                onClick={onClick}
                className={`
                    w-full text-left px-4 py-2.5 hover:bg-grey-50 dark:hover:bg-grey-800/50 
                    transition-colors group
                    ${task.status === 'completed' ? 'opacity-60' : ''}
                `}
            >
                <div className="flex items-start gap-2">
                    {/* Status Icon */}
                    <span className={`flex-shrink-0 mt-0.5 ${statusColor}`}>
                        <StatusIcon className="w-4 h-4" />
                    </span>

                    {/* Content */}
                    <div className="flex-1 min-w-0">
                        <p className={`text-sm font-medium text-text-primary truncate ${task.status === 'completed' ? 'line-through' : ''}`}>
                            {task.name}
                        </p>

                        {/* Meta */}
                        <div className="flex items-center gap-2 mt-1">
                            {/* Priority */}
                            <span className={`flex items-center gap-0.5 text-[10px] ${priorityColor}`}>
                                <Flag className="w-3 h-3" />
                                {task.priority}
                            </span>

                            {/* Automation */}
                            {task.automationLevel === 'automated' && (
                                <span className="flex items-center gap-0.5 text-[10px] text-purple-500">
                                    <AutoAwesome className="w-3 h-3" />
                                    auto
                                </span>
                            )}

                            {/* Due date */}
                            {task.dueDate && (
                                <span className="flex items-center gap-0.5 text-[10px] text-text-secondary">
                                    <Schedule className="w-3 h-3" />
                                    {formatDueDate(task.dueDate)}
                                </span>
                            )}
                        </div>

                        {/* Progress bar for in-progress tasks */}
                        {task.status === 'in-progress' && task.progress > 0 && task.progress < 100 && (
                            <div className="mt-1.5 h-1 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                                <div
                                    className="h-full bg-blue-500 rounded-full"
                                    style={{ width: `${task.progress}%` }}
                                />
                            </div>
                        )}
                    </div>
                </div>
            </button>
        </li>
    );
}

// ============================================================================
// Utilities
// ============================================================================

function getStatusIcon(status: TaskStatus) {
    switch (status) {
        case 'completed':
            return CheckCircle;
        case 'in-progress':
            return PlayArrow;
        case 'blocked':
            return Pause;
        default:
            return RadioButtonUnchecked;
    }
}

function getStatusColor(status: TaskStatus): string {
    switch (status) {
        case 'completed':
            return 'text-green-500';
        case 'in-progress':
            return 'text-blue-500';
        case 'blocked':
            return 'text-red-500';
        default:
            return 'text-grey-400';
    }
}

function getPriorityColor(priority: string): string {
    switch (priority) {
        case 'critical':
            return 'text-red-600';
        case 'high':
            return 'text-orange-500';
        case 'medium':
            return 'text-yellow-500';
        default:
            return 'text-grey-400';
    }
}

function formatDueDate(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffDays = Math.ceil((date.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));

    if (diffDays < 0) return 'overdue';
    if (diffDays === 0) return 'today';
    if (diffDays === 1) return 'tomorrow';
    if (diffDays < 7) return `${diffDays}d`;

    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
