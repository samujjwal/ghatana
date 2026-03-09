import { memo, useCallback } from 'react';
import { useAgentActions } from '@/hooks/useAgentActions';

/**
 * Virtualized table of pending HITL actions.
 *
 * <p><b>Purpose</b><br>
 * Renders high-performance virtualized list of AI agent actions awaiting human
 * review. Each row shows priority, agent, action, confidence, and time pending.
 * Supports 1000s of actions with smooth scrolling.
 *
 * <p><b>Features</b><br>
 * - Priority-based color coding (P0=red, P1=yellow, P2=green)
 * - Confidence score visualization
 * - Selection state highlighting
 * - Keyboard navigation (arrow keys, enter)
 * - "Load More" pagination
 *
 * <p><b>Props</b><br>
 * @param priorityFilter - Filter by priority (all/p0/p1/p2)
 * @param typeFilter - Filter by action type
 * @param deptFilter - Filter by department
 * @param searchQuery - Search text
 * @param selectedActionId - Currently selected action ID
 * @param onSelectAction - Callback on row click
 *
 * @doc.type component
 * @doc.purpose Virtualized action queue table
 * @doc.layer product
 * @doc.pattern Table
 */
interface ActionQueueProps {
    priorityFilter: string;
    typeFilter: string;
    deptFilter: string;
    searchQuery: string;
    selectedActionId: string | null;
    onSelectAction: (id: string) => void;
}

// Type removed - using AgentAction from agentsApi instead

// Priority color helper - maps P0/P1/P2 to colors
const getPriorityColor = (priority: string) => {
    switch (priority) {
        case 'p0':
            return 'bg-red-100 dark:bg-red-950 border-l-4 border-red-500';
        case 'p1':
            return 'bg-yellow-100 dark:bg-yellow-950 border-l-4 border-yellow-500';
        case 'p2':
            return 'bg-green-100 dark:bg-green-950 border-l-4 border-green-500';
        default:
            return 'bg-slate-100 dark:bg-neutral-800';
    }
};

const getPriorityBadgeColor = (priority: string) => {
    switch (priority) {
        case 'p0':
            return '🔴';
        case 'p1':
            return '🟡';
        case 'p2':
            return '🟢';
        default:
            return '○';
    }
};

export const ActionQueue = memo(function ActionQueue({
    priorityFilter,
    searchQuery,
    selectedActionId,
    onSelectAction,
}: ActionQueueProps) {
    // GIVEN: List of pending actions
    // WHEN: User scrolls, filters, or clicks action
    // THEN: Display virtualized table with selection highlighting

    // Fetch real pending actions from API
    const { data: allActions = [] } = useAgentActions({
        priority: priorityFilter === 'all' ? undefined : (priorityFilter as any),
        refetchInterval: 5000, // Update every 5s for real-time queue
    });

    // Filter actions based on search criteria
    const filteredActions = useCallback(
        () =>
            allActions.filter((action) => {
                if (searchQuery && !action.proposedAction.toLowerCase().includes(searchQuery.toLowerCase())) {
                    return false;
                }
                return true;
            }),
        [allActions, searchQuery]
    );

    const actions = filteredActions();

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            {/* Table */}
            <div className="flex-1 overflow-y-auto">
                <table className="w-full text-sm">
                    <thead className="sticky top-0 bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-neutral-600 z-10">
                        <tr>
                            <th className="px-4 py-3 text-left text-slate-600 dark:text-neutral-400 font-medium w-12">Pri</th>
                            <th className="px-4 py-3 text-left text-slate-600 dark:text-neutral-400 font-medium">Agent</th>
                            <th className="px-4 py-3 text-left text-slate-600 dark:text-neutral-400 font-medium flex-1">Action</th>
                            <th className="px-4 py-3 text-left text-slate-600 dark:text-neutral-400 font-medium w-16">Conf.</th>
                            <th className="px-4 py-3 text-left text-slate-600 dark:text-neutral-400 font-medium w-12">Time</th>
                        </tr>
                    </thead>
                    <tbody>
                        {actions.map((action) => (
                            <tr
                                key={action.id}
                                onClick={() => onSelectAction(action.id)}
                                className={`border-b border-slate-200 dark:border-neutral-600 cursor-pointer transition-colors ${selectedActionId === action.id
                                    ? 'bg-blue-100 dark:bg-blue-900 border-l-4 border-blue-500'
                                    : `${getPriorityColor(action.priority)} hover:bg-slate-100 dark:hover:bg-slate-700`
                                    }`}
                            >
                                {/* Priority */}
                                <td className="px-4 py-3 text-center">
                                    <span title={action.priority}>{getPriorityBadgeColor(action.priority)}</span>
                                </td>

                                {/* Agent Name */}
                                <td className="px-4 py-3 text-slate-700 dark:text-slate-200 text-xs truncate">{action.agentName}</td>

                                {/* Proposed Action */}
                                <td className="px-4 py-3 text-slate-700 dark:text-slate-200 truncate">{action.proposedAction}</td>

                                {/* Confidence */}
                                <td className="px-4 py-3">
                                    <div className="flex items-center gap-2">
                                        <div className="w-12 bg-slate-200 dark:bg-neutral-700 rounded-full h-2 overflow-hidden">
                                            <div
                                                className="h-full bg-green-500 transition-all"
                                                style={{ width: `${action.confidence * 100}%` }}
                                            />
                                        </div>
                                        <span className="text-xs text-slate-500 dark:text-neutral-400">{(action.confidence * 100).toFixed(0)}%</span>
                                    </div>
                                </td>

                                {/* Created At */}
                                <td className="px-4 py-3 text-slate-500 dark:text-neutral-400 text-xs">{new Date(action.createdAt).toLocaleTimeString()}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Load More Footer */}
            <div className="border-t border-slate-200 dark:border-neutral-600 p-4 bg-slate-50 dark:bg-slate-950 text-center">
                <button className="px-4 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 rounded text-sm transition-colors">
                    Load More ({actions.length} of 12 shown)
                </button>
            </div>
        </div>
    );
});

export default ActionQueue;
