import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useQueueItems } from '@/hooks/useOperateApi';
import type { QueueItem } from '@/hooks/useOperateApi';
import { Clock, CheckCircle2, User, Package, Database, Zap } from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * Queue Explorer
 *
 * <p><b>Purpose</b><br>
 * Browse and filter work queue items awaiting approval.
 * Provides human-in-the-loop (HITL) workflow management.
 *
 * <p><b>Features</b><br>
 * - Type and priority filtering
 * - Queue item cards with context
 * - Due time tracking
 * - Stats overview
 *
 * @doc.type component
 * @doc.purpose Work queue exploration and filtering
 * @doc.layer product
 * @doc.pattern Explorer
 */
export function QueueExplorer() {
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const [filterType, setFilterType] = useState<string>('all');
    const [filterPriority, setFilterPriority] = useState<string>('all');

    const typeParam = filterType === 'all' ? undefined : filterType;
    const priorityParam = filterPriority === 'all' ? undefined : filterPriority;

    const { data: response, isLoading, error } = useQueueItems(tenantId, typeParam, priorityParam);
    const queueItems = Array.isArray(response) ? response : response?.data || [];

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="text-slate-600 dark:text-neutral-400">Loading queue items...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load queue items: {error.message}
                </div>
            </div>
        );
    }

    const typeConfig = {
        'deployment-approval': { variant: 'primary' as const, color: 'text-blue-600', bg: 'bg-blue-50 dark:bg-blue-900/20', icon: Package },
        'config-change': { variant: 'warning' as const, color: 'text-amber-600', bg: 'bg-amber-50 dark:bg-amber-900/20', icon: Database },
        'workflow-execution': { variant: 'neutral' as const, color: 'text-purple-600', bg: 'bg-purple-50 dark:bg-purple-900/20', icon: Zap },
    };

    const priorityConfig = {
        'high': { variant: 'danger' as const, label: 'High' },
        'medium': { variant: 'warning' as const, label: 'Medium' },
        'low': { variant: 'neutral' as const, label: 'Low' },
    };

    const stats = {
        total: queueItems.length,
        highPriority: queueItems.filter((item: { priority: string }) => item.priority === 'high').length,
        pending: queueItems.length,
        overdue: queueItems.filter((item: { dueIn: string }) => {
            const dueMatch = item.dueIn.match(/(\d+)([mh])/);
            if (!dueMatch) return false;
            const value = parseInt(dueMatch[1]);
            const unit = dueMatch[2];
            if (unit === 'm') return value < 0;
            if (unit === 'h') return value < 0;
            return false;
        }).length,
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Work Queue</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Review and approve pending workflow actions
                    </p>
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-4 gap-4">
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Total Items</div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mt-1">
                        {stats.total}
                    </div>
                </div>
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">High Priority</div>
                    <div className="text-2xl font-bold text-red-600 dark:text-red-400 mt-1">
                        {stats.highPriority}
                    </div>
                </div>
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Pending</div>
                    <div className="text-2xl font-bold text-amber-600 dark:text-amber-400 mt-1">
                        {stats.pending}
                    </div>
                </div>
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Overdue</div>
                    <div className="text-2xl font-bold text-orange-600 dark:text-orange-400 mt-1">
                        {stats.overdue}
                    </div>
                </div>
            </div>

            {/* Filters */}
            <div className="flex items-center gap-4">
                <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">Type:</span>
                    {['all', 'deployment-approval', 'config-change', 'workflow-execution'].map((type) => (
                        <button
                            key={type}
                            onClick={() => setFilterType(type)}
                            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                                filterType === type
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-white dark:bg-slate-800 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700'
                            }`}
                        >
                            {type === 'all' ? 'All' : type.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                        </button>
                    ))}
                </div>
                <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">Priority:</span>
                    {['all', 'high', 'medium', 'low'].map((priority) => (
                        <button
                            key={priority}
                            onClick={() => setFilterPriority(priority)}
                            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                                filterPriority === priority
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-white dark:bg-slate-800 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700'
                            }`}
                        >
                            {priority.charAt(0).toUpperCase() + priority.slice(1)}
                        </button>
                    ))}
                </div>
            </div>

            {/* Queue Items */}
            <div className="space-y-4">
                {queueItems.length === 0 ? (
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-12 text-center">
                        <CheckCircle2 className="h-12 w-12 text-green-600 mx-auto mb-3" />
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">Queue is empty</h3>
                        <p className="text-slate-600 dark:text-neutral-400 mt-1">
                            {filterType !== 'all' || filterPriority !== 'all'
                                ? 'No items match the selected filters'
                                : 'All work items have been processed'}
                        </p>
                    </div>
                ) : (
                    queueItems.map((item: QueueItem) => {
                        const TypeIcon = typeConfig[item.type as keyof typeof typeConfig].icon;

                        return (
                            <div
                                key={item.id}
                                onClick={() => navigate(`/operate/queue-item-detail/${item.id}`)}
                                className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6 hover:shadow-lg transition-shadow cursor-pointer"
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex items-start gap-4 flex-1">
                                        <div className={`p-3 rounded-lg ${typeConfig[item.type as keyof typeof typeConfig].bg}`}>
                                            <TypeIcon className={`h-5 w-5 ${typeConfig[item.type as keyof typeof typeConfig].color}`} />
                                        </div>
                                        <div className="flex-1">
                                            <div className="flex items-center gap-3 mb-2">
                                                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                                    {item.title}
                                                </h3>
                                                <Badge variant={typeConfig[item.type as keyof typeof typeConfig].variant}>
                                                    {item.type.split('-').map((w: string) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                                                </Badge>
                                                <Badge variant={priorityConfig[item.priority as keyof typeof priorityConfig].variant}>
                                                    {priorityConfig[item.priority as keyof typeof priorityConfig].label}
                                                </Badge>
                                            </div>
                                            <p className="text-slate-600 dark:text-neutral-400 mb-3">
                                                {item.description}
                                            </p>
                                            <div className="flex items-center gap-4 text-sm text-slate-600 dark:text-neutral-400">
                                                <span className="flex items-center gap-1">
                                                    <User className="h-4 w-4" />
                                                    {item.requestedBy}
                                                </span>
                                                <span className="flex items-center gap-1">
                                                    <Clock className="h-4 w-4" />
                                                    Due in {item.dueIn}
                                                </span>
                                                <span className="text-slate-500 dark:text-neutral-500">{item.id}</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })
                )}
            </div>
        </div>
    );
}
