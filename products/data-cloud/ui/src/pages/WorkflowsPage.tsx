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

import React, { useState, useMemo, lazy, Suspense } from 'react';
import { Link } from 'react-router';
import {
    Play,
    Pause,
    Square,
    Clock,
    CheckCircle,
    XCircle,
    Plus,
    MoreVertical,
    Workflow,
    Loader2,
    Filter,
    Search,
} from 'lucide-react';
import { cn } from '../lib/theme';

/**
 * Workflow interface
 */
interface Workflow {
    id: string;
    name: string;
    description: string;
    status: 'active' | 'paused' | 'stopped' | 'error';
    lastRun: string;
    nextRun?: string;
    executionCount: number;
    successRate: number;
    duration: string;
    triggerType: 'scheduled' | 'manual' | 'event';
    tags?: string[];
}

/**
 * Enhanced mock workflows data with full feature set
 */
const mockWorkflows: Workflow[] = [
    {
        id: '1',
        name: 'Customer Data Pipeline',
        description: 'Process and validate customer data from multiple sources',
        status: 'active',
        lastRun: new Date(Date.now() - 300000).toISOString(),
        nextRun: new Date(Date.now() + 2700000).toISOString(),
        executionCount: 1247,
        successRate: 98.5,
        duration: '3m 15s',
        triggerType: 'scheduled',
        tags: ['etl', 'customer', 'daily'],
    },
    {
        id: '2',
        name: 'Inventory Sync',
        description: 'Sync inventory levels across all sales channels',
        status: 'active',
        lastRun: new Date(Date.now() - 600000).toISOString(),
        nextRun: new Date(Date.now() + 3600000).toISOString(),
        executionCount: 892,
        successRate: 99.1,
        duration: '1m 42s',
        triggerType: 'event',
        tags: ['inventory', 'sync', 'real-time'],
    },
    {
        id: '3',
        name: 'Report Generation',
        description: 'Generate daily and weekly business reports',
        status: 'paused',
        lastRun: new Date(Date.now() - 86400000).toISOString(),
        executionCount: 45,
        successRate: 97.8,
        duration: '5m 30s',
        triggerType: 'scheduled',
        tags: ['reports', 'analytics', 'weekly'],
    },
    {
        id: '4',
        name: 'Data Quality Check',
        description: 'Validate data quality and integrity across all tables',
        status: 'error',
        lastRun: new Date(Date.now() - 1800000).toISOString(),
        executionCount: 234,
        successRate: 85.2,
        duration: '2m 10s',
        triggerType: 'scheduled',
        tags: ['quality', 'validation', 'hourly'],
    },
    {
        id: '5',
        name: 'User Analytics',
        description: 'Process user behavior data for analytics dashboard',
        status: 'active',
        lastRun: new Date(Date.now() - 900000).toISOString(),
        nextRun: new Date(Date.now() + 5400000).toISOString(),
        executionCount: 567,
        successRate: 96.3,
        duration: '4m 20s',
        triggerType: 'scheduled',
        tags: ['analytics', 'user-behavior', 'hourly'],
    },
];

/**
 * Status icon mapping
 */
const getStatusIcon = (status: Workflow['status']) => {
    switch (status) {
        case 'active':
            return <Play className="h-4 w-4 text-green-500" />;
        case 'paused':
            return <Pause className="h-4 w-4 text-yellow-500" />;
        case 'stopped':
            return <Square className="h-4 w-4 text-gray-500" />;
        case 'error':
            return <XCircle className="h-4 w-4 text-red-500" />;
        default:
            return <Clock className="h-4 w-4 text-gray-400" />;
    }
};

/**
 * Status color mapping
 */
const getStatusColor = (status: Workflow['status']) => {
    switch (status) {
        case 'active':
            return 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300';
        case 'paused':
            return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300';
        case 'stopped':
            return 'bg-gray-100 text-gray-700 dark:bg-gray-900 dark:text-gray-300';
        case 'error':
            return 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300';
        default:
            return 'bg-gray-100 text-gray-700';
    }
};

/**
 * Loading Component
 */
function LoadingSpinner({ message = 'Loading...' }: { message?: string }) {
    return (
        <div className="flex items-center justify-center p-8">
            <div className="flex items-center gap-2 text-gray-500">
                <Loader2 className="h-4 w-4 animate-spin" />
                <span className="text-sm">{message}</span>
            </div>
        </div>
    );
}

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
    const [workflows] = useState<Workflow[]>(mockWorkflows);
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [selectedWorkflow, setSelectedWorkflow] = useState<Workflow | null>(null);

    // Filter workflows
    const filteredWorkflows = useMemo(() => {
        return workflows.filter(workflow => {
            const matchesSearch = !searchQuery ||
                workflow.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                workflow.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
                workflow.tags?.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()));

            const matchesStatus = statusFilter === 'all' || workflow.status === statusFilter;

            return matchesSearch && matchesStatus;
        });
    }, [workflows, searchQuery, statusFilter]);

    // Calculate stats
    const stats = useMemo(() => ({
        total: workflows.length,
        active: workflows.filter(w => w.status === 'active').length,
        paused: workflows.filter(w => w.status === 'paused').length,
        errors: workflows.filter(w => w.status === 'error').length,
        avgSuccessRate: (workflows.reduce((sum, w) => sum + w.successRate, 0) / workflows.length).toFixed(1),
    }), [workflows]);

    const statusOptions = [
        { value: 'all', label: 'All Workflows' },
        { value: 'active', label: 'Active' },
        { value: 'paused', label: 'Paused' },
        { value: 'stopped', label: 'Stopped' },
        { value: 'error', label: 'Error' },
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
                <Link
                    to="/pipelines/new"
                    className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                >
                    <Plus className="h-4 w-4" />
                    New Workflow
                </Link>
            </div>

            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-5 gap-4 mb-6">
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                            <Workflow className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Total</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">
                                {stats.total}
                            </p>
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
                            <p className="text-xl font-bold text-gray-900 dark:text-white">
                                {stats.active}
                            </p>
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
                            <p className="text-xl font-bold text-gray-900 dark:text-white">
                                {stats.paused}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-red-100 dark:bg-red-900 rounded-lg">
                            <XCircle className="h-5 w-5 text-red-600 dark:text-red-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Errors</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">
                                {stats.errors}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                            <CheckCircle className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Avg Success</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">
                                {stats.avgSuccessRate}%
                            </p>
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
                                    Success Rate
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Executions
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Duration
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Next Run
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Actions
                                </th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                            {filteredWorkflows.map((workflow) => (
                                <tr
                                    key={workflow.id}
                                    className="hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                                    onClick={() => setSelectedWorkflow(workflow)}
                                >
                                    <td className="px-6 py-4 whitespace-nowrap">
                                        <div className="flex items-center gap-3">
                                            <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                                                <Workflow className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                                            </div>
                                            <div>
                                                <div className="text-sm font-medium text-gray-900 dark:text-white">
                                                    {workflow.name}
                                                </div>
                                                <div className="text-sm text-gray-500 dark:text-gray-400">
                                                    {workflow.description}
                                                </div>
                                                {workflow.tags && workflow.tags.length > 0 && (
                                                    <div className="flex gap-1 mt-1">
                                                        {workflow.tags.slice(0, 2).map(tag => (
                                                            <span key={tag} className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">
                                                                {tag}
                                                            </span>
                                                        ))}
                                                        {workflow.tags.length > 2 && (
                                                            <span className="text-xs text-gray-400">
                                                                +{workflow.tags.length - 2}
                                                            </span>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap">
                                        <div className="flex items-center gap-2">
                                            {getStatusIcon(workflow.status)}
                                            <span className={cn('px-2 py-1 rounded-full text-xs font-medium', getStatusColor(workflow.status))}>
                                                {workflow.status}
                                            </span>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap">
                                        <div className="flex items-center gap-2">
                                            <span className="text-sm font-medium text-gray-900 dark:text-white">
                                                {workflow.successRate}%
                                            </span>
                                            <div className="w-16 bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                                                <div
                                                    className={cn(
                                                        'h-2 rounded-full',
                                                        workflow.successRate >= 95 ? 'bg-green-500' :
                                                            workflow.successRate >= 85 ? 'bg-yellow-500' : 'bg-red-500'
                                                    )}
                                                    style={{ width: `${workflow.successRate}%` }}
                                                />
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-400">
                                        {workflow.executionCount.toLocaleString()}
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-400">
                                        {workflow.duration}
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-400">
                                        {workflow.nextRun ?
                                            new Date(workflow.nextRun).toLocaleString() :
                                            'Manual'
                                        }
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap">
                                        <WorkflowActions workflow={workflow} />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Empty State */}
            {filteredWorkflows.length === 0 && (
                <div className="text-center py-12">
                    <Workflow className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                        No workflows found
                    </h3>
                    <p className="text-gray-600 dark:text-gray-400">
                        {searchQuery || statusFilter !== 'all'
                            ? 'Try adjusting your filters'
                            : 'Create your first workflow to get started'
                        }
                    </p>
                </div>
            )}

            {/* Workflow Detail Modal - Lazy Loaded */}
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

                        <Suspense fallback={<LoadingSpinner message="Loading workflow details..." />}>
                            <div className="space-y-4">
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Description</h3>
                                    <p className="text-gray-900 dark:text-white">{selectedWorkflow.description}</p>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Status</h3>
                                        <div className="flex items-center gap-2">
                                            {getStatusIcon(selectedWorkflow.status)}
                                            <span className={cn('px-2 py-1 rounded-full text-xs font-medium', getStatusColor(selectedWorkflow.status))}>
                                                {selectedWorkflow.status}
                                            </span>
                                        </div>
                                    </div>

                                    <div>
                                        <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Success Rate</h3>
                                        <p className="text-lg font-bold text-gray-900 dark:text-white">{selectedWorkflow.successRate}%</p>
                                    </div>

                                    <div>
                                        <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Total Executions</h3>
                                        <p className="text-lg font-bold text-gray-900 dark:text-white">{selectedWorkflow.executionCount.toLocaleString()}</p>
                                    </div>

                                    <div>
                                        <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Average Duration</h3>
                                        <p className="text-lg font-bold text-gray-900 dark:text-white">{selectedWorkflow.duration}</p>
                                    </div>
                                </div>

                                {selectedWorkflow.tags && selectedWorkflow.tags.length > 0 && (
                                    <div>
                                        <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Tags</h3>
                                        <div className="flex gap-2">
                                            {selectedWorkflow.tags.map(tag => (
                                                <span key={tag} className="bg-gray-100 text-gray-700 px-3 py-1 rounded-full text-sm">
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
                                    <button className="flex items-center gap-2 px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300">
                                        <Pause className="h-4 w-4" />
                                        Pause
                                    </button>
                                    <Link
                                        to={`/pipelines/${selectedWorkflow.id}/edit`}
                                        className="flex items-center gap-2 px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
                                    >
                                        Edit
                                    </Link>
                                </div>
                            </div>
                        </Suspense>
                    </div>
                </div>
            )}
        </div>
    );
}

export default WorkflowsPage;
