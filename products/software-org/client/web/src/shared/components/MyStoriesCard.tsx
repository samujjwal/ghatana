/**
 * MyStoriesCard Component
 *
 * <p><b>Purpose</b><br>
 * Displays a card with the engineer's active work items (stories, bugs, tasks).
 * Part of the Engineer persona dashboard flow, showing items that need attention.
 *
 * <p><b>Features</b><br>
 * - List of assigned work items with status badges
 * - Priority indicators
 * - Quick navigation to work item details
 * - Compact summary view
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { MyStoriesCard } from '@/shared/components/MyStoriesCard';
 *
 * <MyStoriesCard onItemClick={(id) => navigate(`/work-items/${id}`)} />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Work items list for engineer dashboard
 * @doc.layer product
 * @doc.pattern Molecule
 */

import { memo } from 'react';
import { useMyWorkItems } from '@/hooks/useMyWorkItems';
import type { WorkItemSummary, WorkItemStatus, WorkItemPriority, WorkItemType } from '@/types/workItem';

export interface MyStoriesCardProps {
    /** Maximum number of items to display */
    maxItems?: number;
    /** Callback when an item is clicked */
    onItemClick?: (id: string) => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Get status badge color classes
 */
function getStatusColor(status: WorkItemStatus): string {
    const colors: Record<WorkItemStatus, string> = {
        backlog: 'bg-slate-100 text-slate-700 dark:bg-neutral-800 dark:text-neutral-300',
        ready: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-indigo-400',
        'in-progress': 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
        'in-review': 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-violet-400',
        staging: 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/30 dark:text-cyan-400',
        deployed: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
        done: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-green-400',
        blocked: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-rose-400',
    };
    return colors[status] ?? colors.backlog;
}

/**
 * Get human-readable status label
 */
function getStatusLabel(status: WorkItemStatus): string {
    const labels: Record<WorkItemStatus, string> = {
        backlog: 'Backlog',
        ready: 'Ready',
        'in-progress': 'In Progress',
        'in-review': 'In Review',
        staging: 'Staging',
        deployed: 'Deployed',
        done: 'Done',
        blocked: 'Blocked',
    };
    return labels[status] ?? status;
}

/**
 * Get priority indicator
 */
function getPriorityIndicator(priority: WorkItemPriority): { icon: string; color: string } {
    const indicators: Record<WorkItemPriority, { icon: string; color: string }> = {
        p0: { icon: '🔴', color: 'text-red-600 dark:text-rose-400' },
        p1: { icon: '🟠', color: 'text-orange-600 dark:text-orange-400' },
        p2: { icon: '🟡', color: 'text-yellow-600 dark:text-yellow-400' },
        p3: { icon: '🟢', color: 'text-green-600 dark:text-green-400' },
    };
    return indicators[priority] ?? indicators.p3;
}

/**
 * Get type icon
 */
function getTypeIcon(type: WorkItemType): string {
    const icons: Record<WorkItemType, string> = {
        story: '📖',
        epic: '🎯',
        bug: '🐛',
        task: '📋',
        spike: '🔬',
    };
    return icons[type] ?? '📋';
}

/**
 * Format relative time
 */
function formatRelativeTime(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
}

/**
 * Single work item row
 */
const WorkItemRow = memo(function WorkItemRow({
    item,
    onClick,
}: {
    item: WorkItemSummary;
    onClick?: () => void;
}) {
    const priority = getPriorityIndicator(item.priority);

    const handleClick = (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        console.log('[MyStoriesCard] Click on item:', item.id);
        onClick?.();
    };

    return (
        <button
            type="button"
            onClick={handleClick}
            className="w-full text-left p-3 rounded-lg border border-slate-200 dark:border-neutral-600 
                       bg-white dark:bg-neutral-800 hover:bg-slate-50 dark:hover:bg-slate-700/50 
                       transition-colors group cursor-pointer"
        >
            <div className="flex items-start justify-between gap-2">
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm" title={item.type}>
                            {getTypeIcon(item.type)}
                        </span>
                        <span className="text-xs font-mono text-slate-500 dark:text-neutral-400">
                            {item.id}
                        </span>
                        <span className={priority.color} title={`Priority: ${item.priority.toUpperCase()}`}>
                            {priority.icon}
                        </span>
                    </div>
                    <h4 className="font-medium text-slate-900 dark:text-neutral-100 truncate group-hover:text-blue-600 dark:group-hover:text-blue-400">
                        {item.title}
                    </h4>
                    <div className="flex items-center gap-2 mt-1">
                        {item.service && (
                            <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-neutral-700 text-slate-600 dark:text-neutral-300">
                                {item.service}
                            </span>
                        )}
                        <span className="text-xs text-slate-400 dark:text-slate-500">
                            {formatRelativeTime(item.updatedAt)}
                        </span>
                    </div>
                </div>
                <span className={`text-xs px-2 py-1 rounded-full whitespace-nowrap ${getStatusColor(item.status)}`}>
                    {getStatusLabel(item.status)}
                </span>
            </div>
        </button>
    );
});

/**
 * Loading skeleton for work items
 */
function WorkItemSkeleton() {
    return (
        <div className="p-3 rounded-lg border border-slate-200 dark:border-neutral-600 animate-pulse">
            <div className="flex items-center gap-2 mb-2">
                <div className="w-5 h-5 bg-slate-200 dark:bg-neutral-700 rounded" />
                <div className="w-16 h-4 bg-slate-200 dark:bg-neutral-700 rounded" />
            </div>
            <div className="w-3/4 h-5 bg-slate-200 dark:bg-neutral-700 rounded mb-2" />
            <div className="w-1/3 h-4 bg-slate-200 dark:bg-neutral-700 rounded" />
        </div>
    );
}

/**
 * MyStoriesCard - Displays engineer's assigned work items
 */
export const MyStoriesCard = memo(function MyStoriesCard({
    maxItems = 5,
    onItemClick,
    className = '',
}: MyStoriesCardProps) {
    const { workItems, isLoading, isError, error } = useMyWorkItems({
        filters: {
            status: ['ready', 'in-progress', 'in-review', 'staging', 'deployed', 'blocked'],
        },
    });

    // Debug logging
    console.log('[MyStoriesCard] Rendering - isLoading:', isLoading, 'isError:', isError, 'workItems count:', workItems.length);

    // Sort by priority (p0 first) then by updated time
    const sortedItems = [...workItems]
        .sort((a, b) => {
            const priorityOrder = { p0: 0, p1: 1, p2: 2, p3: 3 };
            const pDiff = priorityOrder[a.priority] - priorityOrder[b.priority];
            if (pDiff !== 0) return pDiff;
            return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
        })
        .slice(0, maxItems);

    return (
        <div className={`bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 ${className}`}>
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                    <span className="text-xl">📋</span>
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        My Stories
                    </h3>
                    {!isLoading && workItems.length > 0 && (
                        <span className="px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-indigo-400 rounded-full">
                            {workItems.length}
                        </span>
                    )}
                </div>
                {workItems.length > maxItems && (
                    <button
                        onClick={() => onItemClick?.('all')}
                        className="text-sm text-blue-600 dark:text-indigo-400 hover:underline"
                    >
                        View all
                    </button>
                )}
            </div>

            {isLoading ? (
                <div className="space-y-3">
                    {Array.from({ length: 3 }).map((_, i) => (
                        <WorkItemSkeleton key={i} />
                    ))}
                </div>
            ) : isError ? (
                <div className="p-4 text-center text-red-600 dark:text-rose-400 bg-red-50 dark:bg-rose-600/30 rounded-lg">
                    <p className="text-sm">Failed to load work items</p>
                    <p className="text-xs mt-1 opacity-75">{error?.message}</p>
                </div>
            ) : sortedItems.length === 0 ? (
                <div className="p-8 text-center text-slate-500 dark:text-neutral-400">
                    <span className="text-3xl block mb-2">🎉</span>
                    <p className="text-sm">No active stories</p>
                    <p className="text-xs mt-1">All caught up!</p>
                </div>
            ) : (
                <div className="space-y-3">
                    {sortedItems.map(item => (
                        <WorkItemRow
                            key={item.id}
                            item={item}
                            onClick={() => onItemClick?.(item.id)}
                        />
                    ))}
                </div>
            )}
        </div>
    );
});

export default MyStoriesCard;
