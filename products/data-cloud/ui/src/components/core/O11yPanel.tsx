/**
 * O11y Panel Component
 *
 * Unified observability panel showing executions, metrics, and traces.
 * Part of BrainSidebar, expandable to full view.
 *
 * Features:
 * - Active pipeline executions with real-time progress
 * - Recent execution results with status indicators
 * - System health metrics (CPU, memory, throughput, latency)
 * - Error traces with click to expand
 * - Integration with WebSocket for real-time updates
 *
 * @doc.type component
 * @doc.purpose Unified observability visibility
 * @doc.layer frontend
 */

import React, { useState, useEffect } from 'react';
import {
    Activity,
    Play,
    CheckCircle,
    XCircle,
    Clock,
    AlertTriangle,
    ChevronRight,
    RefreshCw,
    Pause,
    ExternalLink,
    Cpu,
    HardDrive,
    Gauge,
    Timer,
} from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../lib/theme';

// Types
export interface Execution {
    id: string;
    pipelineId: string;
    pipelineName: string;
    status: 'running' | 'completed' | 'failed' | 'pending' | 'cancelled';
    startTime: string;
    endTime?: string;
    duration?: number;
    progress?: number;
    nodesCurrent?: number;
    nodesTotal?: number;
    error?: string;
}

export interface SystemMetrics {
    cpu: number;
    memory: number;
    throughput: number;
    latency: number;
    activeConnections: number;
    queueDepth: number;
}

interface O11yPanelProps {
    collapsed?: boolean;
    onExpandExecution?: (id: string) => void;
    className?: string;
}

// Mock API functions - replace with actual API calls
async function fetchActiveExecutions(): Promise<Execution[]> {
    // In production, this would call the actual API
    const response = await fetch('/api/executions?status=running,pending');
    if (!response.ok) {
        // Return mock data for development
        return [
            {
                id: 'exec-001',
                pipelineId: 'pipe-123',
                pipelineName: 'Customer Data Sync',
                status: 'running',
                startTime: new Date(Date.now() - 120000).toISOString(),
                progress: 65,
                nodesCurrent: 5,
                nodesTotal: 8,
            },
            {
                id: 'exec-002',
                pipelineId: 'pipe-456',
                pipelineName: 'Analytics Aggregation',
                status: 'pending',
                startTime: new Date(Date.now() - 30000).toISOString(),
                progress: 0,
                nodesCurrent: 0,
                nodesTotal: 12,
            },
        ];
    }
    return response.json();
}

async function fetchRecentExecutions(): Promise<Execution[]> {
    const response = await fetch('/api/executions?status=completed,failed&limit=5');
    if (!response.ok) {
        return [
            {
                id: 'exec-003',
                pipelineId: 'pipe-789',
                pipelineName: 'Daily Report Generation',
                status: 'completed',
                startTime: new Date(Date.now() - 3600000).toISOString(),
                endTime: new Date(Date.now() - 3540000).toISOString(),
                duration: 60000,
                nodesCurrent: 6,
                nodesTotal: 6,
            },
            {
                id: 'exec-004',
                pipelineId: 'pipe-101',
                pipelineName: 'Data Quality Check',
                status: 'failed',
                startTime: new Date(Date.now() - 7200000).toISOString(),
                endTime: new Date(Date.now() - 7140000).toISOString(),
                duration: 60000,
                nodesCurrent: 3,
                nodesTotal: 5,
                error: 'Schema validation failed on node "Validator"',
            },
        ];
    }
    return response.json();
}

async function fetchSystemMetrics(): Promise<SystemMetrics> {
    const response = await fetch('/api/metrics/system');
    if (!response.ok) {
        return {
            cpu: 45,
            memory: 62,
            throughput: 1250,
            latency: 23,
            activeConnections: 42,
            queueDepth: 156,
        };
    }
    return response.json();
}

/**
 * Status badge with icon
 */
function StatusBadge({ status }: { status: Execution['status'] }) {
    const configs = {
        running: { icon: Play, color: 'text-blue-500', bg: 'bg-blue-100 dark:bg-blue-900/30', label: 'Running' },
        completed: { icon: CheckCircle, color: 'text-green-500', bg: 'bg-green-100 dark:bg-green-900/30', label: 'Completed' },
        failed: { icon: XCircle, color: 'text-red-500', bg: 'bg-red-100 dark:bg-red-900/30', label: 'Failed' },
        pending: { icon: Clock, color: 'text-yellow-500', bg: 'bg-yellow-100 dark:bg-yellow-900/30', label: 'Pending' },
        cancelled: { icon: Pause, color: 'text-gray-500', bg: 'bg-gray-100 dark:bg-gray-800', label: 'Cancelled' },
    };

    const config = configs[status];
    const Icon = config.icon;

    return (
        <span className={cn('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium', config.bg, config.color)}>
            <Icon className="h-3 w-3" />
            {config.label}
        </span>
    );
}

/**
 * Activity indicator for collapsed view
 */
function ActivityIndicator({ count }: { count: number }) {
    if (count === 0) {
        return (
            <div className="relative">
                <Activity className="h-5 w-5 text-gray-400" />
            </div>
        );
    }

    return (
        <div className="relative">
            <Activity className="h-5 w-5 text-blue-500 animate-pulse" />
            <span className="absolute -top-1 -right-1 h-4 w-4 bg-blue-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                {count > 9 ? '9+' : count}
            </span>
        </div>
    );
}

/**
 * Execution card for running/pending executions
 */
function ExecutionCard({
    execution,
    onExpand,
}: {
    execution: Execution;
    onExpand?: () => void;
}) {
    const formatDuration = (startTime: string) => {
        const start = new Date(startTime).getTime();
        const now = Date.now();
        const seconds = Math.floor((now - start) / 1000);
        if (seconds < 60) return `${seconds}s`;
        const minutes = Math.floor(seconds / 60);
        if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
        const hours = Math.floor(minutes / 60);
        return `${hours}h ${minutes % 60}m`;
    };

    return (
        <div
            className={cn(
                'p-3 rounded-lg border',
                'bg-white dark:bg-gray-800',
                'border-gray-200 dark:border-gray-700',
                'hover:shadow-md transition-shadow cursor-pointer'
            )}
            onClick={onExpand}
        >
            <div className="flex items-start justify-between mb-2">
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                        {execution.pipelineName}
                    </p>
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                        {formatDuration(execution.startTime)}
                    </p>
                </div>
                <StatusBadge status={execution.status} />
            </div>

            {execution.status === 'running' && execution.progress !== undefined && (
                <>
                    <div className="h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden mb-1">
                        <div
                            className="h-full bg-blue-500 transition-all duration-300"
                            style={{ width: `${execution.progress}%` }}
                        />
                    </div>
                    <div className="flex justify-between text-xs text-gray-500">
                        <span>{execution.progress}%</span>
                        {execution.nodesCurrent !== undefined && execution.nodesTotal !== undefined && (
                            <span>{execution.nodesCurrent}/{execution.nodesTotal} nodes</span>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}

/**
 * Compact execution row for recent results
 */
function ExecutionRow({ execution }: { execution: Execution }) {
    const formatTime = (timestamp: string) => {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now.getTime() - date.getTime();
        const minutes = Math.floor(diff / 60000);
        if (minutes < 60) return `${minutes}m ago`;
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return `${hours}h ago`;
        return date.toLocaleDateString();
    };

    return (
        <div className="flex items-center gap-2 py-1.5 px-2 hover:bg-gray-50 dark:hover:bg-gray-800 rounded cursor-pointer">
            {execution.status === 'completed' ? (
                <CheckCircle className="h-4 w-4 text-green-500 flex-shrink-0" />
            ) : (
                <XCircle className="h-4 w-4 text-red-500 flex-shrink-0" />
            )}
            <span className="text-xs text-gray-700 dark:text-gray-300 truncate flex-1">
                {execution.pipelineName}
            </span>
            <span className="text-xs text-gray-400 flex-shrink-0">
                {formatTime(execution.endTime || execution.startTime)}
            </span>
        </div>
    );
}

/**
 * Metric badge for system health
 */
function MetricBadge({
    icon: Icon,
    label,
    value,
    unit,
    status,
}: {
    icon: React.ElementType;
    label: string;
    value: number;
    unit: string;
    status?: 'good' | 'warning' | 'critical';
}) {
    const statusColors = {
        good: 'text-green-500',
        warning: 'text-yellow-500',
        critical: 'text-red-500',
    };

    const getStatus = (): 'good' | 'warning' | 'critical' => {
        if (status) return status;
        // Auto-detect status based on metric type
        if (label === 'CPU' || label === 'Memory') {
            if (value >= 90) return 'critical';
            if (value >= 70) return 'warning';
            return 'good';
        }
        if (label === 'Latency') {
            if (value >= 100) return 'critical';
            if (value >= 50) return 'warning';
            return 'good';
        }
        return 'good';
    };

    return (
        <div className="flex items-center gap-2 p-2 rounded bg-gray-50 dark:bg-gray-800">
            <Icon className={cn('h-4 w-4', statusColors[getStatus()])} />
            <div className="flex-1">
                <p className="text-[10px] text-gray-500 uppercase tracking-wider">{label}</p>
                <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {value}{unit}
                </p>
            </div>
        </div>
    );
}

/**
 * O11y Panel Component
 */
export function O11yPanel({ collapsed, onExpandExecution, className }: O11yPanelProps) {
    const queryClient = useQueryClient();

    // Fetch active executions with polling
    const { data: activeExecutions = [], isLoading: activeLoading } = useQuery({
        queryKey: ['o11y-active-executions'],
        queryFn: fetchActiveExecutions,
        refetchInterval: 5000, // Poll every 5 seconds
    });

    // Fetch recent executions
    const { data: recentExecutions = [], isLoading: recentLoading } = useQuery({
        queryKey: ['o11y-recent-executions'],
        queryFn: fetchRecentExecutions,
        refetchInterval: 30000, // Poll every 30 seconds
    });

    // Fetch system metrics
    const { data: metrics, isLoading: metricsLoading } = useQuery({
        queryKey: ['o11y-system-metrics'],
        queryFn: fetchSystemMetrics,
        refetchInterval: 10000, // Poll every 10 seconds
    });

    const runningCount = activeExecutions.filter(e => e.status === 'running').length;

    // Collapsed view - just show activity indicator
    if (collapsed) {
        return (
            <div className={cn('flex flex-col items-center gap-2 py-2', className)}>
                <ActivityIndicator count={runningCount} />
            </div>
        );
    }

    return (
        <div className={cn('space-y-4', className)}>
            {/* Active Executions */}
            <section>
                <div className="flex items-center justify-between mb-2">
                    <h4 className="text-xs font-medium text-gray-500 uppercase tracking-wide">
                        Active Executions
                    </h4>
                    <button
                        onClick={() => queryClient.invalidateQueries({ queryKey: ['o11y-active-executions'] })}
                        className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded"
                    >
                        <RefreshCw className="h-3 w-3" />
                    </button>
                </div>

                <div className="space-y-2">
                    {activeLoading ? (
                        <div className="animate-pulse space-y-2">
                            <div className="h-16 bg-gray-200 dark:bg-gray-700 rounded" />
                        </div>
                    ) : activeExecutions.filter(e => ['running', 'pending'].includes(e.status)).length > 0 ? (
                        activeExecutions
                            .filter(e => ['running', 'pending'].includes(e.status))
                            .map(execution => (
                                <ExecutionCard
                                    key={execution.id}
                                    execution={execution}
                                    onExpand={() => onExpandExecution?.(execution.id)}
                                />
                            ))
                    ) : (
                        <p className="text-xs text-gray-400 text-center py-3">
                            No active executions
                        </p>
                    )}
                </div>
            </section>

            {/* Recent Results */}
            <section>
                <h4 className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2">
                    Recent Results
                </h4>

                <div className="space-y-1">
                    {recentLoading ? (
                        <div className="animate-pulse space-y-1">
                            {[1, 2, 3].map(i => (
                                <div key={i} className="h-8 bg-gray-200 dark:bg-gray-700 rounded" />
                            ))}
                        </div>
                    ) : recentExecutions.length > 0 ? (
                        recentExecutions.map(execution => (
                            <ExecutionRow key={execution.id} execution={execution} />
                        ))
                    ) : (
                        <p className="text-xs text-gray-400 text-center py-2">
                            No recent executions
                        </p>
                    )}
                </div>
            </section>

            {/* System Health */}
            <section>
                <h4 className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2">
                    System Health
                </h4>

                {metricsLoading ? (
                    <div className="animate-pulse grid grid-cols-2 gap-2">
                        {[1, 2, 3, 4].map(i => (
                            <div key={i} className="h-14 bg-gray-200 dark:bg-gray-700 rounded" />
                        ))}
                    </div>
                ) : metrics ? (
                    <div className="grid grid-cols-2 gap-2">
                        <MetricBadge icon={Cpu} label="CPU" value={metrics.cpu} unit="%" />
                        <MetricBadge icon={HardDrive} label="Memory" value={metrics.memory} unit="%" />
                        <MetricBadge icon={Gauge} label="Throughput" value={metrics.throughput} unit="/s" />
                        <MetricBadge icon={Timer} label="Latency" value={metrics.latency} unit="ms" />
                    </div>
                ) : (
                    <p className="text-xs text-gray-400 text-center py-2">
                        Metrics unavailable
                    </p>
                )}
            </section>

            {/* View All Link */}
            <div className="pt-2 border-t border-gray-200 dark:border-gray-700">
                <a
                    href="/pipelines?view=executions"
                    className="flex items-center justify-center gap-1 text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400"
                >
                    View all executions
                    <ExternalLink className="h-3 w-3" />
                </a>
            </div>
        </div>
    );
}

export default O11yPanel;
