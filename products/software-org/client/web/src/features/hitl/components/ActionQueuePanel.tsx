import React from 'react';
import { StatusBadge } from '@/shared/components';

// StatusBadge used for action priority display

/**
 * Action interface.
 */
export interface Action {
    id: string;
    title: string;
    description: string;
    type: 'approval' | 'input' | 'review' | 'intervention';
    priority: 'critical' | 'high' | 'medium' | 'low';
    status: 'pending' | 'in_progress' | 'completed' | 'skipped';
    createdAt: Date;
    dueAt?: Date;
    assignedTo?: string;
    context?: Record<string, unknown>;
}

/**
 * Action Queue Panel Props interface.
 */
export interface ActionQueuePanelProps {
    actions: Action[];
    onActionClick?: (action: Action) => void;
    onActionComplete?: (action: Action) => void;
    onActionSkip?: (action: Action) => void;
    isLoading?: boolean;
    title?: string;
}

/**
 * Action Queue Panel - Displays queue of human-in-the-loop actions.
 *
 * <p><b>Purpose</b><br>
 * Shows pending actions requiring human intervention with priority sorting and quick actions.
 *
 * <p><b>Features</b><br>
 * - Action list sorted by priority and due date
 * - Type badges (approval/input/review/intervention)
 * - Status indicators
 * - Due date tracking with overdue highlighting
 * - Quick complete/skip buttons
 * - Empty state message
 * - Dark mode support
 * - Responsive layout
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <ActionQueuePanel 
 *   actions={pendingActions}
 *   onActionClick={handleActionClick}
 *   onActionComplete={handleComplete}
 *   onActionSkip={handleSkip}
 *   title="Pending Approvals"
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose HITL action queue display
 * @doc.layer product
 * @doc.pattern Organism
 */
export const ActionQueuePanel = React.memo(
    ({
        actions,
        onActionClick,
        onActionComplete,
        onActionSkip,
        isLoading,
        title = 'Action Queue',
    }: ActionQueuePanelProps) => {
        const sortedActions = React.useMemo(() => {
            return [...actions].sort((a, b) => {
                // Sort by status (pending first)
                const statusOrder = { pending: 0, in_progress: 1, completed: 2, skipped: 3 };
                if (statusOrder[a.status] !== statusOrder[b.status]) {
                    return statusOrder[a.status] - statusOrder[b.status];
                }
                // Then by priority
                const priorityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
                if (priorityOrder[a.priority] !== priorityOrder[b.priority]) {
                    return priorityOrder[a.priority] - priorityOrder[b.priority];
                }
                // Then by due date
                if (a.dueAt && b.dueAt) {
                    return a.dueAt.getTime() - b.dueAt.getTime();
                }
                return 0;
            });
        }, [actions]);

        const priorityColors = {
            critical: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
            high: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
            medium: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
            low: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
        };

        const typeIcons = {
            approval: '✓',
            input: '📝',
            review: '👁',
            intervention: '⚡',
        };

        const statusColors = {
            pending: 'bg-slate-100 text-slate-800 dark:bg-neutral-800 dark:text-slate-200',
            in_progress: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
            completed: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
            skipped: 'bg-slate-100 text-slate-600 dark:bg-neutral-800 dark:text-neutral-400',
        };

        const isOverdue = (dueAt: Date | undefined) => {
            if (!dueAt) return false;
            return new Date() > dueAt;
        };

        const formatDueDate = (dueAt: Date | undefined) => {
            if (!dueAt) return 'No due date';
            const now = new Date();
            const diff = dueAt.getTime() - now.getTime();
            const hours = Math.floor(diff / (1000 * 60 * 60));
            const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));

            if (diff < 0) return 'OVERDUE';
            if (hours <= 0) return `${minutes}m remaining`;
            if (hours < 24) return `${hours}h remaining`;
            return `${Math.floor(diff / (1000 * 60 * 60 * 24))}d remaining`;
        };

        if (isLoading) {
            return (
                <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="animate-pulse space-y-4">
                        <div className="h-8 w-32 bg-slate-200 dark:bg-neutral-700 rounded" />
                        {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className="h-20 bg-slate-200 dark:bg-neutral-700 rounded" />
                        ))}
                    </div>
                </div>
            );
        }

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    {title}
                </h3>

                {sortedActions.length === 0 ? (
                    <div className="py-8 text-center">
                        <p className="text-slate-600 dark:text-neutral-400 mb-2">✓ All caught up!</p>
                        <p className="text-sm text-slate-500 dark:text-slate-500">
                            No pending actions at this time.
                        </p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {sortedActions.map((action) => (
                            <div
                                key={action.id}
                                onClick={() => onActionClick?.(action)}
                                role={onActionClick ? 'button' : undefined}
                                tabIndex={onActionClick ? 0 : undefined}
                                onKeyDown={(e) => {
                                    if ((e.key === 'Enter' || e.key === ' ') && onActionClick) {
                                        onActionClick(action);
                                    }
                                }}
                                className={`rounded-lg border-2 p-4 transition-all ${onActionClick ? 'cursor-pointer hover:shadow-md' : ''
                                    } ${action.status === 'pending'
                                        ? 'border-slate-200 bg-white dark:border-neutral-600 dark:bg-slate-900'
                                        : 'border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-neutral-800'
                                    }`}
                            >
                                <div className="flex items-start justify-between mb-2">
                                    <div className="flex items-start gap-3 flex-1">
                                        {/* Type icon */}
                                        <div className="text-lg flex-shrink-0">
                                            {typeIcons[action.type]}
                                        </div>
                                        {/* Content */}
                                        <div className="flex-1 min-w-0">
                                            <h4 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                {action.title}
                                            </h4>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400 mt-0.5">
                                                {action.description}
                                            </p>
                                        </div>
                                    </div>
                                    {/* Priority badge */}
                                    <span
                                        className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-semibold whitespace-nowrap ml-2 ${priorityColors[action.priority]
                                            }`}
                                    >
                                        {action.priority}
                                    </span>
                                </div>

                                {/* Metadata */}
                                <div className="flex items-center gap-3 mb-3 text-xs text-slate-600 dark:text-neutral-400">
                                    <span
                                        className={`inline-flex px-2 py-1 rounded-full font-medium ${statusColors[action.status]
                                            }`}
                                    >
                                        {action.status.replace('_', ' ')}
                                    </span>
                                    {action.dueAt && (
                                        <span
                                            className={
                                                isOverdue(action.dueAt)
                                                    ? 'text-red-600 dark:text-rose-400 font-bold'
                                                    : ''
                                            }
                                        >
                                            {formatDueDate(action.dueAt)}
                                        </span>
                                    )}
                                    {action.assignedTo && (
                                        <>
                                            <span>•</span>
                                            <span>{action.assignedTo}</span>
                                        </>
                                    )}
                                </div>

                                {/* Quick actions */}
                                {action.status === 'pending' && (onActionComplete || onActionSkip) && (
                                    <div className="flex gap-2 pt-2 border-t border-slate-200 dark:border-neutral-600">
                                        {onActionComplete && (
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    onActionComplete(action);
                                                }}
                                                className="flex-1 py-1.5 px-3 rounded-md text-sm font-medium bg-green-600 text-white hover:bg-green-700 dark:hover:bg-green-500 transition-colors"
                                                aria-label={`Complete action ${action.id}`}
                                            >
                                                ✓ Complete
                                            </button>
                                        )}
                                        {onActionSkip && (
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    onActionSkip(action);
                                                }}
                                                className="flex-1 py-1.5 px-3 rounded-md text-sm font-medium bg-slate-300 text-slate-800 hover:bg-slate-400 dark:bg-neutral-700 dark:text-slate-200 dark:hover:bg-slate-600 transition-colors"
                                                aria-label={`Skip action ${action.id}`}
                                            >
                                                Skip
                                            </button>
                                        )}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}

                {/* Summary */}
                {sortedActions.length > 0 && (
                    <div className="mt-4 pt-4 border-t border-slate-200 dark:border-neutral-600 text-sm text-slate-600 dark:text-neutral-400">
                        <p>
                            <strong>{sortedActions.filter((a) => a.status === 'pending').length}</strong> pending •{' '}
                            <strong>{sortedActions.filter((a) => a.status === 'in_progress').length}</strong> in progress •{' '}
                            <strong>{sortedActions.filter((a) => a.status === 'completed').length}</strong> completed
                        </p>
                    </div>
                )}
            </div>
        );
    }
);

ActionQueuePanel.displayName = 'ActionQueuePanel';

export default ActionQueuePanel;
