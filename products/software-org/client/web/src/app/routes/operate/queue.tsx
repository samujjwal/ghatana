/**
 * Work Queue - Pending Tasks & Approvals
 *
 * Consolidated view of all pending actions requiring human attention:
 * - HITL decisions
 * - Deployment approvals
 * - Access requests
 * - Review items
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import type { Route } from './+types/queue';
import { MainLayout } from '@/app/Layout';
import { useState } from 'react';
import {
    CheckCircle2,
    XCircle,
    Clock,
    Filter,
    Search,
    ChevronRight,
} from 'lucide-react';

type QueueItemType = 'approval' | 'hitl' | 'review' | 'access';
type QueueItemPriority = 'high' | 'medium' | 'low';

interface QueueItem {
    id: string;
    type: QueueItemType;
    title: string;
    description: string;
    priority: QueueItemPriority;
    requestedBy: string;
    createdAt: string;
    dueIn?: string;
}

const mockQueueItems: QueueItem[] = [
    {
        id: '1',
        type: 'approval',
        title: 'Production Deployment - API v2.3.0',
        description: 'Deploy new API version with breaking changes to production',
        priority: 'high',
        requestedBy: 'CI/CD Pipeline',
        createdAt: '10 min ago',
        dueIn: '2 hours',
    },
    {
        id: '2',
        type: 'hitl',
        title: 'Security Agent Decision',
        description: 'Confirm blocking of suspicious IP range 192.168.x.x',
        priority: 'high',
        requestedBy: 'Security Agent',
        createdAt: '25 min ago',
    },
    {
        id: '3',
        type: 'access',
        title: 'Database Access Request',
        description: 'John Smith requesting read access to production database',
        priority: 'medium',
        requestedBy: 'John Smith',
        createdAt: '1 hour ago',
    },
    {
        id: '4',
        type: 'review',
        title: 'Workflow Change Review',
        description: 'Review changes to incident response workflow',
        priority: 'low',
        requestedBy: 'DevOps Team',
        createdAt: '3 hours ago',
    },
];

const typeConfig = {
    approval: { label: 'Approval', color: 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300' },
    hitl: { label: 'HITL', color: 'bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-300' },
    review: { label: 'Review', color: 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300' },
    access: { label: 'Access', color: 'bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-300' },
};

const priorityConfig = {
    high: { label: 'High', color: 'text-red-600 dark:text-red-400' },
    medium: { label: 'Medium', color: 'text-amber-600 dark:text-amber-400' },
    low: { label: 'Low', color: 'text-gray-600 dark:text-gray-400' },
};

function QueueItemCard({ item, onApprove, onReject }: {
    item: QueueItem;
    onApprove: (id: string) => void;
    onReject: (id: string) => void;
}) {
    return (
        <div className="bg-white dark:bg-slate-800 rounded-xl border border-gray-200 dark:border-slate-700 p-5 hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-2">
                        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${typeConfig[item.type].color}`}>
                            {typeConfig[item.type].label}
                        </span>
                        <span className={`text-xs font-medium ${priorityConfig[item.priority].color}`}>
                            {priorityConfig[item.priority].label} Priority
                        </span>
                        {item.dueIn && (
                            <span className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                                <Clock className="h-3 w-3" />
                                Due in {item.dueIn}
                            </span>
                        )}
                    </div>
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white truncate">
                        {item.title}
                    </h3>
                    <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                        {item.description}
                    </p>
                    <p className="text-xs text-gray-500 dark:text-gray-500 mt-2">
                        Requested by {item.requestedBy} • {item.createdAt}
                    </p>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                    <button
                        onClick={() => onApprove(item.id)}
                        className="inline-flex items-center gap-1.5 px-3 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 transition-colors"
                    >
                        <CheckCircle2 className="h-4 w-4" />
                        Approve
                    </button>
                    <button
                        onClick={() => onReject(item.id)}
                        className="inline-flex items-center gap-1.5 px-3 py-2 bg-white dark:bg-slate-700 border border-gray-300 dark:border-slate-600 text-gray-700 dark:text-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-slate-600 transition-colors"
                    >
                        <XCircle className="h-4 w-4" />
                        Reject
                    </button>
                    <button className="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
                        <ChevronRight className="h-5 w-5" />
                    </button>
                </div>
            </div>
        </div>
    );
}

export default function QueuePage() {
    const [filter, setFilter] = useState<QueueItemType | 'all'>('all');
    const [searchQuery, setSearchQuery] = useState('');

    const filteredItems = mockQueueItems.filter((item) => {
        if (filter !== 'all' && item.type !== filter) return false;
        if (searchQuery && !item.title.toLowerCase().includes(searchQuery.toLowerCase())) return false;
        return true;
    });

    const handleApprove = (id: string) => {
        console.log('Approved:', id);
    };

    const handleReject = (id: string) => {
        console.log('Rejected:', id);
    };

    return (
        <MainLayout>
            <div className="space-y-6">
                {/* Header */}
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Work Queue</h1>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                        {filteredItems.length} items pending your attention
                    </p>
                </div>

                {/* Filters */}
                <div className="flex flex-col sm:flex-row gap-4">
                    <div className="relative flex-1">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                        <input
                            type="text"
                            placeholder="Search queue..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                    </div>
                    <div className="flex items-center gap-2">
                        <Filter className="h-4 w-4 text-gray-400" />
                        <select
                            value={filter}
                            onChange={(e) => setFilter(e.target.value as QueueItemType | 'all')}
                            className="px-3 py-2 border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option value="all">All Types</option>
                            <option value="approval">Approvals</option>
                            <option value="hitl">HITL Decisions</option>
                            <option value="review">Reviews</option>
                            <option value="access">Access Requests</option>
                        </select>
                    </div>
                </div>

                {/* Queue Items */}
                <div className="space-y-4">
                    {filteredItems.length === 0 ? (
                        <div className="text-center py-12 text-gray-500 dark:text-gray-400">
                            No items in queue
                        </div>
                    ) : (
                        filteredItems.map((item) => (
                            <QueueItemCard
                                key={item.id}
                                item={item}
                                onApprove={handleApprove}
                                onReject={handleReject}
                            />
                        ))
                    )}
                </div>
            </div>
        </MainLayout>
    );
}

export function meta({}: Route.MetaArgs) {
    return [
        { title: 'Work Queue - Operate' },
        { name: 'description', content: 'Pending work items and approvals' },
    ];
}
