import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { ChevronRight, Code as CodeIcon, ClipboardList as AssignmentIcon, CloudUpload as DeployIcon, ArrowRight as ArrowForwardIcon, CheckCircle as CheckCircleOutline, Clock as AccessTime, Ban as Block, Check, X, Loader2, AlertCircle } from 'lucide-react';
import { Typography, Button, Chip, Box, Surface as Paper } from '@ghatana/design-system';
import { useI18n } from '../../i18n/I18nProvider';

type CheckboxControlProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'>;

function CheckboxControl(props: CheckboxControlProps): React.ReactElement {
    return React.createElement('input', { ...props, type: 'checkbox' });
}

interface NativeButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    children: React.ReactNode;
}

function NativeButton({ children, type = 'button', ...props }: NativeButtonProps): React.ReactElement {
    return React.createElement('button', { ...props, type }, children);
}

/**
 * Priority Task interface 
 */
export interface PriorityTask {
    id: string;
    title: string;
    project: string;
    projectId: string;
    type: 'Design' | 'Code' | 'Deploy';
    priority: 'High' | 'Urgent' | 'Medium' | 'Low';
    persona: string;
    dueDate?: string;
    isBlocked?: boolean;
    status?: 'pending' | 'in-progress' | 'blocked' | 'completed' | 'skipped';
}

/**
 * Status indicator component
 */
function StatusIndicator({ status }: { status?: 'pending' | 'in-progress' | 'blocked' | 'completed' | 'skipped' }) {
    if (!status || status === 'pending') {
        return null;
    }

    const statusConfig = {
        'in-progress': {
            icon: <Loader2 className="w-4 h-4 animate-spin" />,
            color: 'text-info-color',
            bgColor: 'bg-info-bg',
            label: 'In Progress'
        },
        'completed': {
            icon: <Check className="w-4 h-4" />,
            color: 'text-success-color',
            bgColor: 'bg-success-bg',
            label: 'Completed'
        },
        'blocked': {
            icon: <AlertCircle className="w-4 h-4" />,
            color: 'text-destructive',
            bgColor: 'bg-destructive-bg',
            label: 'Blocked'
        },
        'skipped': {
            icon: <X className="w-4 h-4" />,
            color: 'text-fg-muted',
            bgColor: 'bg-surface-muted',
            label: 'Skipped'
        }
    };

    const config = statusConfig[status];

    return (
        <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full ${config.bgColor} ${config.color} text-xs font-medium`}>
            {config.icon}
            <span>{config.label}</span>
        </div>
    );
}

interface PriorityTasksListProps {
    tasks: PriorityTask[];
    onTaskClick: (task: PriorityTask) => void;
    onViewAll: () => void;
    onApprove?: (taskId: string) => Promise<void>;
    onReject?: (taskId: string) => Promise<void>;
    onBulkApprove?: (taskIds: string[]) => Promise<void>;
    onBulkReject?: (taskIds: string[]) => Promise<void>;
}

/**
 * PriorityTasksList component
 * 
 * Displays a list of actionable high-priority tasks with inline approve/reject actions.
 */
export function PriorityTasksList({ 
    tasks, 
    onTaskClick, 
    onViewAll, 
    onApprove, 
    onReject, 
    onBulkApprove, 
    onBulkReject 
}: PriorityTasksListProps) {
    const { t } = useI18n();
    const [selectedTasks, setSelectedTasks] = useState<Set<string>>(new Set());
    const [processingTasks, setProcessingTasks] = useState<Set<string>>(new Set());

    const hasActions = Boolean(onApprove || onReject || onBulkApprove || onBulkReject);
    const isBulkActionEnabled = selectedTasks.size > 0 && (onBulkApprove || onBulkReject);

    const toggleSelection = (taskId: string, e: React.MouseEvent | React.ChangeEvent<HTMLInputElement>) => {
        e.stopPropagation();
        setSelectedTasks(prev => {
            const next = new Set(prev);
            if (next.has(taskId)) {
                next.delete(taskId);
            } else {
                next.add(taskId);
            }
            return next;
        });
    };

    const handleApprove = async (taskId: string, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!onApprove || processingTasks.has(taskId)) return;
        
        setProcessingTasks(prev => new Set(prev).add(taskId));
        try {
            await onApprove(taskId);
        } catch (error) {
            // Keep UI stable when task actions fail; caller can surface errors externally.
            console.error('[PriorityTasksList] approve failed', error);
        } finally {
            setProcessingTasks(prev => {
                const next = new Set(prev);
                next.delete(taskId);
                return next;
            });
        }
    };

    const handleReject = async (taskId: string, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!onReject || processingTasks.has(taskId)) return;
        
        setProcessingTasks(prev => new Set(prev).add(taskId));
        try {
            await onReject(taskId);
        } catch (error) {
            // Keep UI stable when task actions fail; caller can surface errors externally.
            console.error('[PriorityTasksList] reject failed', error);
        } finally {
            setProcessingTasks(prev => {
                const next = new Set(prev);
                next.delete(taskId);
                return next;
            });
        }
    };

    const handleBulkApprove = async () => {
        if (!onBulkApprove) return;
        const taskIds = Array.from(selectedTasks);
        setProcessingTasks(new Set(taskIds));
        try {
            await onBulkApprove(taskIds);
            setSelectedTasks(new Set());
        } finally {
            setProcessingTasks(new Set());
        }
    };

    const handleBulkReject = async () => {
        if (!onBulkReject) return;
        const taskIds = Array.from(selectedTasks);
        setProcessingTasks(new Set(taskIds));
        try {
            await onBulkReject(taskIds);
            setSelectedTasks(new Set());
        } finally {
            setProcessingTasks(new Set());
        }
    };

    const selectAll = () => {
        setSelectedTasks(new Set(tasks.map(t => t.id)));
    };

    const clearSelection = () => {
        setSelectedTasks(new Set());
    };

    return (
        <div className="mb-10">
            <div className="flex justify-between items-center mb-2">
                <div className="flex items-center gap-2">
                    <Typography className="flex items-center gap-2 font-bold text-lg">
                        My Priority Tasks
                        <Chip label={tasks.length} size="sm" />
                    </Typography>
                    {hasActions && (
                        <div className="flex gap-1">
                            <NativeButton
                                className="px-2 py-1 text-sm text-info-color hover:text-info-color disabled:opacity-50 disabled:cursor-not-allowed"
                                onClick={selectAll}
                                disabled={selectedTasks.size === tasks.length}
                            >
                                Select All
                            </NativeButton>
                            <NativeButton
                                className="px-2 py-1 text-sm text-fg-muted hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed"
                                onClick={clearSelection}
                                disabled={selectedTasks.size === 0}
                            >
                                Clear
                            </NativeButton>
                        </div>
                    )}
                </div>
                <div className="flex gap-2">
                    {isBulkActionEnabled && (
                        <div className="flex gap-1">
                            {onBulkApprove && (
                                <Button 
                                    size="sm" 
                                    onClick={handleBulkApprove}
                                    disabled={processingTasks.size > 0}
                                >
                                    <Check className="w-4 h-4 mr-1" />
                                    Approve ({selectedTasks.size})
                                </Button>
                            )}
                            {onBulkReject && (
                                <Button 
                                    size="sm" 
                                    onClick={handleBulkReject}
                                    disabled={processingTasks.size > 0}
                                >
                                    <X className="w-4 h-4 mr-1" />
                                    Reject ({selectedTasks.size})
                                </Button>
                            )}
                        </div>
                    )}
                    <Button size="sm" endIcon={<ArrowForwardIcon />} onClick={onViewAll}>View all</Button>
                </div>
            </div>
            <Paper className="rounded-lg overflow-hidden border">
                {tasks.length === 0 ? (
                    <div className="p-8 text-center text-fg-muted dark:text-fg-muted">
                        <CheckCircleOutline className="mb-2 w-20 h-20 opacity-50 mx-auto" />
                        <Typography>
                            No priority tasks. You're all caught up!
                        </Typography>
                    </div>
                ) : (
                    <>
                        <div className="divide-y divide-gray-200 dark:divide-gray-700">
                            {tasks.map((task) => (
                                <div 
                                    key={task.id}
                                    onClick={() => onTaskClick(task)}
                                    className={`py-4 px-4 hover:bg-surface-muted dark:hover:bg-surface cursor-pointer transition-colors ${selectedTasks.has(task.id) ? 'bg-info-bg dark:bg-info-bg' : ''}`}
                                >
                                    <div className="flex items-center gap-4">
                                        {hasActions && (
                                            <CheckboxControl
                                                checked={selectedTasks.has(task.id)}
                                                onChange={(e) => toggleSelection(task.id, e)}
                                                onClick={(e) => e.stopPropagation()}
                                                className="w-4 h-4 rounded border-border text-info-color focus:ring-blue-500"
                                            />
                                        )}
                                        <div className="flex-shrink-0">
                                            {task.type === 'Code' && <CodeIcon className="w-5 h-5 text-info-color" />}
                                            {task.type === 'Design' && <AssignmentIcon className="w-5 h-5 text-info-color" />}
                                            {task.type === 'Deploy' && <DeployIcon className="w-5 h-5 text-success-color" />}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <Typography className="text-lg font-medium truncate">
                                                {task.title}
                                            </Typography>
                                            <Typography className="text-sm text-fg-muted dark:text-fg-muted mt-1">
                                                {task.project}
                                            </Typography>
                                            <div className="flex items-center gap-4 mt-1">
                                                <span className={`text-sm font-medium ${
                                                    task.priority === 'Urgent' ? 'text-destructive' :
                                                    task.priority === 'High' ? 'text-warning-color' :
                                                    'text-fg-muted'
                                                }`}>
                                                    {task.priority}
                                                </span>
                                                {task.dueDate && (
                                                    <span className="flex items-center gap-1 text-sm text-fg-muted">
                                                        <AccessTime className="w-4 h-4" />
                                                        {new Date(task.dueDate).toLocaleDateString('en-US', { timeZone: 'UTC' })}
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <StatusIndicator status={task.status} />
                                            {hasActions && task.status === 'pending' && (
                                                <div className="flex gap-1">
                                                    <Button
                                                        variant="ghost"
                                                        size="sm"
                                                        onClick={(e) => handleApprove(task.id, e)}
                                                        disabled={processingTasks.has(task.id)}
                                                        className="p-2 rounded-md bg-success-bg text-success-color hover:bg-success-bg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                                                        title={t('priority.approveTask')}
                                                        aria-label={t('priority.approveTask')}
                                                    >
                                                        <Check className="w-4 h-4" />
                                                    </Button>
                                                    <Button
                                                        variant="ghost"
                                                        size="sm"
                                                        onClick={(e) => handleReject(task.id, e)}
                                                        disabled={processingTasks.has(task.id)}
                                                        className="p-2 rounded-md bg-destructive-bg text-destructive hover:bg-destructive-bg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                                                        title={t('priority.rejectTask')}
                                                        aria-label={t('priority.rejectTask')}
                                                    >
                                                        <X className="w-4 h-4" />
                                                    </Button>
                                                </div>
                                            )}
                                            <Chip label={task.type} size="sm" variant="outlined" />
                                            <Chip
                                                label={task.persona}
                                                size="sm"
                                                className="h-5 text-xs bg-surface-muted dark:bg-surface"
                                            />
                                            <ChevronRight className="w-5 h-5 text-fg-muted" />
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                        <div className="p-2 text-center bg-surface-muted dark:bg-surface">
                            <Button size="sm" endIcon={<ArrowForwardIcon />} onClick={onViewAll}>Go to Inbox</Button>
                        </div>
                    </>
                )}
            </Paper>
        </div>
    );
}
