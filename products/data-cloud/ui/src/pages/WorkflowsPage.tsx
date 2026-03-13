/**
 * Optimized Workflows Page
 *
 * Full-featured Workflows Page with progressive loading and performance optimizations.
 * Maintains all original features while improving load times.
 *
 * @doc.type page
 * @doc.purpose Feature-complete workflows page with optimized loading
 * @doc.layer frontend
 */

import React, { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import {
    Play,
    Pause,
    Square,
    Clock,
    XCircle,
    Plus,
    MoreVertical,
    Workflow as WorkflowIcon,
    Loader2,
    Filter,
    Search,
    RefreshCw,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { workflowsApi, type Workflow } from '../lib/api/workflows';

/**
 * Status icon mapping — maps API status to icon
 */
const getStatusIcon = (status: Workflow['status']) => {
    switch (status) {
        case 'active':   return <Play   className="h-4 w-4 text-green-500" />;
        case 'paused':   return <Pause  className="h-4 w-4 text-yellow-500" />;
        case 'archived': return <Square className="h-4 w-4 text-gray-500" />;
        case 'draft':    return <Clock  className="h-4 w-4 text-blue-400" />;
        // no-op to satisfy exhaustive check
        default:         return <Clock  className="h-4 w-4 text-gray-400" />;
    }
};

/**
 * Status color mapping
 */
const getStatusColor = (status: Workflow['status']) => {
    switch (status) {
        case 'active':   return 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300';
        case 'paused':   return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300';
        case 'archived': return 'bg-gray-100 text-gray-700 dark:bg-gray-900 dark:text-gray-300';
        case 'draft':    return 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300';
        default:         return 'bg-gray-100 text-gray-700';
    }
};

/**
 * Workflow Actions Component
 */
function WorkflowActions({ workflow }: { workflow: Workflow }) {
    const [showActions, setShowActions] = useState(false);

    return (
        <div className="relative">
            <button
                onClick={() => setShowActions(!showActions)}
                className="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
            >
                <MoreVertical className="h-4 w-4 text-gray-400" />
            </button>

            {showActions && (
                <div className="absolute right-0 top-8 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-10 min-w-[150px]">
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700">
                        Run Now
                    </button>
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700">
                        Edit
                    </button>
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700">
                        View Logs
                    </button>
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700 text-red-600">
                        Delete
                    </button>
                </div>
            )}
        </div>
    );
}

/**
 * Optimized Workflows Page Component
 */
export function WorkflowsPage() {
    const navigate = useNavigate();
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [selectedWorkflow, setSelectedWorkflow] = useState<Workflow | null>(null);

    const { data: workflowsPage, isLoading, refetch } = useQuery({
        queryKey: ['workflows', searchQuery, statusFilter],
        queryFn: () => workflowsApi.list({
            search: searchQuery || undefined,
            status: statusFilter !== 'all' ? statusFilter as Workflow['status'] : undefined,
            pageSize: 50,
        }),
        staleTime: 60_000,
    });

    const workflows = workflowsPage?.items ?? [];

    const stats = useMemo(() => ({
        total: workflowsPage?.total ?? workflows.length,
        active:   workflows.filter(w => w.status === 'active').length,
        paused:   workflows.filter(w => w.status === 'paused').length,
        draft:    workflows.filter(w => w.status === 'draft').length,
        archived: workflows.filter(w => w.status === 'archived').length,
    }), [workflows, workflowsPage]);

    const statusOptions = [
        { value: 'all',      label: 'All Workflows' },
        { value: 'active',   label: 'Active' },
        { value: 'paused',   label: 'Paused' },
        { value: 'draft',    label: 'Draft' },
        { value: 'archived', label: 'Archived' },
    ];

    return (
        <div className="p-6">
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                        Workflows
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400">
                        Monitor and manage your automated workflows
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => refetch()}
                        className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg text-gray-500"
                        title="Refresh"
                    >
                        <RefreshCw className="h-4 w-4" />
                    </button>
                    <Link
                        to="/pipelines/new"
                        className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                    >
                        <Plus className="h-4 w-4" />
                        New Workflow
                    </Link>
                </div>
            </div>

            {/* Stats Cards */}
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-6">
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                            <WorkflowIcon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Total</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.total}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-green-100 dark:bg-green-900 rounded-lg">
                            <Play className="h-5 w-5 text-green-600 dark:text-green-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Active</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.active}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-yellow-100 dark:bg-yellow-900 rounded-lg">
                            <Pause className="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Paused</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.paused}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                            <Clock className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Draft</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.draft}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
                            <Square className="h-5 w-5 text-gray-500 dark:text-gray-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Archived</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.archived}</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Filters */}
            <div className="flex items-center gap-4 mb-6">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
                    <input
                        type="text"
                        placeholder="Search workflows..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>
                <div className="flex items-center gap-2">
                    <Filter className="h-4 w-4 text-gray-500" />
                    <select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                        className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm"
                    >
                        {statusOptions.map(option => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Workflows List */}
            <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg">
                {isLoading ? (
                    <div className="flex items-center justify-center p-8">
                        <div className="flex items-center gap-2 text-gray-500">
                            <Loader2 className="h-4 w-4 animate-spin" />
                            <span className="text-sm">Loading workflows...</span>
                        </div>
                    </div>
                ) : workflows.length === 0 ? (
                    <div className="text-center py-12">
                        <WorkflowIcon className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                        <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                            No workflows found
                        </h3>
                        <p className="text-gray-600 dark:text-gray-400">
                            {searchQuery || statusFilter !== 'all'
                                ? 'Try adjusting your filters'
                                : 'Create your first workflow to get started'}
                        </p>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 dark:bg-gray-700">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Workflow
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Status
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Last Executed
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Schedule
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Actions
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                                {workflows.map((workflow) => (
                                    <tr
                                        key={workflow.id}
                                        className="hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                                        onClick={() => setSelectedWorkflow(workflow)}
                                    >
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-3">
                                                <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg shrink-0">
                                                    <WorkflowIcon className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                                                </div>
                                                <div>
                                                    <div className="text-sm font-medium text-gray-900 dark:text-white">
                                                        {workflow.name}
                                                    </div>
                                                    {workflow.description && (
                                                        <div className="text-sm text-gray-500 dark:text-gray-400 truncate max-w-xs">
                                                            {workflow.description}
                                                        </div>
                                                    )}
                                                    {workflow.tags && workflow.tags.length > 0 && (
                                                        <div className="flex gap-1 mt-1">
                                                            {workflow.tags.slice(0, 2).map((tag: string) => (
                                                                <span key={tag} className="text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400 px-2 py-0.5 rounded">
                                                                    {tag}
                                                                </span>
                                                            ))}
                                                            {workflow.tags.length > 2 && (
                                                                <span className="text-xs text-gray-400">+{workflow.tags.length - 2}</span>
                                                            )}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="flex items-center gap-2">
                                                {getStatusIcon(workflow.status)}
                                                <span className={cn('px-2 py-1 rounded-full text-xs font-medium capitalize', getStatusColor(workflow.status))}>
                                                    {workflow.status}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-400">
                                            {workflow.lastExecutedAt
                                                ? new Date(workflow.lastExecutedAt).toLocaleString()
                                                : '—'}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-400">
                                            {workflow.schedule ?? '—'}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
                                            <WorkflowActions workflow={workflow} />
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* Workflow Detail Modal */}
            {selectedWorkflow && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-2xl w-full mx-4">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                                {selectedWorkflow.name}
                            </h2>
                            <button
                                onClick={() => setSelectedWorkflow(null)}
                                className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
                            >
                                <XCircle className="h-5 w-5 text-gray-400" />
                            </button>
                        </div>

                        <div className="space-y-4">
                            {selectedWorkflow.description && (
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Description</h3>
                                    <p className="text-gray-900 dark:text-white">{selectedWorkflow.description}</p>
                                </div>
                            )}

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Status</h3>
                                    <div className="flex items-center gap-2">
                                        {getStatusIcon(selectedWorkflow.status)}
                                        <span className={cn('px-2 py-1 rounded-full text-xs font-medium capitalize', getStatusColor(selectedWorkflow.status))}>
                                            {selectedWorkflow.status}
                                        </span>
                                    </div>
                                </div>
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Last Executed</h3>
                                    <p className="text-gray-900 dark:text-white text-sm">
                                        {selectedWorkflow.lastExecutedAt
                                            ? new Date(selectedWorkflow.lastExecutedAt).toLocaleString()
                                            : '—'}
                                    </p>
                                </div>
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Schedule</h3>
                                    <p className="text-gray-900 dark:text-white text-sm">{selectedWorkflow.schedule ?? 'Manual'}</p>
                                </div>
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Created</h3>
                                    <p className="text-gray-900 dark:text-white text-sm">
                                        {new Date(selectedWorkflow.createdAt).toLocaleDateString()}
                                    </p>
                                </div>
                            </div>

                            {selectedWorkflow.tags && selectedWorkflow.tags.length > 0 && (
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Tags</h3>
                                    <div className="flex gap-2 flex-wrap">
                                        {selectedWorkflow.tags.map((tag: string) => (
                                            <span key={tag} className="bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 px-3 py-1 rounded-full text-sm">
                                                {tag}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}

                            <div className="flex gap-2 pt-4">
                                <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                                    <Play className="h-4 w-4" />
                                    Run Now
                                </button>
                                <button className="flex items-center gap-2 px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600">
                                    <Pause className="h-4 w-4" />
                                    Pause
                                </button>
                                <button
                                    onClick={() => navigate(`/pipelines/${selectedWorkflow.id}`)}
                                    className="flex items-center gap-2 px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600"
                                >
                                    Edit
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default WorkflowsPage;
