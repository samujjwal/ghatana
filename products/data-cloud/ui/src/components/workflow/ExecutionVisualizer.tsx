/**
 * Workflow Execution Visualizer
 * 
 * Visual representation of workflow execution progress and status.
 * Shows node execution states, timing, and data flow.
 * 
 * @doc.type component
 * @doc.purpose Workflow execution visualization
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React from 'react';
import {
    CheckCircle2,
    XCircle,
    Clock,
    Loader2,
    AlertCircle,
    Play,
    Pause,
    RotateCcw,
} from 'lucide-react';
import { cn, textStyles, cardStyles, buttonStyles, badgeStyles } from '../../lib/theme';

/**
 * Node execution status
 */
export type NodeExecutionStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

/**
 * Execution node interface
 */
export interface ExecutionNode {
    id: string;
    name: string;
    type: string;
    status: NodeExecutionStatus;
    startTime?: string;
    endTime?: string;
    duration?: number;
    error?: string;
    inputRecords?: number;
    outputRecords?: number;
}

/**
 * Workflow execution interface
 */
export interface WorkflowExecution {
    id: string;
    workflowId: string;
    workflowName: string;
    status: 'running' | 'completed' | 'failed' | 'cancelled';
    startTime: string;
    endTime?: string;
    progress: number;
    nodes: ExecutionNode[];
}

/**
 * Status icon mapping
 */
const statusIcons: Record<NodeExecutionStatus, React.ReactNode> = {
    pending: <Clock className="h-4 w-4 text-gray-400" />,
    running: <Loader2 className="h-4 w-4 text-blue-500 animate-spin" />,
    completed: <CheckCircle2 className="h-4 w-4 text-green-500" />,
    failed: <XCircle className="h-4 w-4 text-red-500" />,
    skipped: <AlertCircle className="h-4 w-4 text-yellow-500" />,
};

/**
 * Status styles
 */
const statusStyles: Record<NodeExecutionStatus, string> = {
    pending: 'border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800',
    running: 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 ring-2 ring-blue-200 dark:ring-blue-800',
    completed: 'border-green-500 bg-green-50 dark:bg-green-900/30',
    failed: 'border-red-500 bg-red-50 dark:bg-red-900/30',
    skipped: 'border-yellow-500 bg-yellow-50 dark:bg-yellow-900/30',
};

/**
 * Format duration in ms to human readable
 */
function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`;
}

interface ExecutionVisualizerProps {
    execution: WorkflowExecution;
    onPause?: () => void;
    onResume?: () => void;
    onCancel?: () => void;
    onRetry?: () => void;
}

/**
 * Workflow Execution Visualizer Component
 */
export function ExecutionVisualizer({
    execution,
    onPause,
    onResume,
    onCancel,
    onRetry,
}: ExecutionVisualizerProps): React.ReactElement {
    const isRunning = execution.status === 'running';
    const isFailed = execution.status === 'failed';
    const completedNodes = execution.nodes.filter((n) => n.status === 'completed').length;
    const totalNodes = execution.nodes.length;

    return (
        <div className={cn(cardStyles.base)}>
            {/* Header */}
            <div className={cn(cardStyles.header, 'flex items-center justify-between')}>
                <div>
                    <h3 className={textStyles.h3}>{execution.workflowName}</h3>
                    <p className={textStyles.xs}>Execution ID: {execution.id}</p>
                </div>
                <div className="flex items-center gap-2">
                    {isRunning && onPause && (
                        <button onClick={onPause} className={cn(buttonStyles.secondary, buttonStyles.sm)}>
                            <Pause className="h-4 w-4 mr-1" />
                            Pause
                        </button>
                    )}
                    {isRunning && onCancel && (
                        <button onClick={onCancel} className={cn(buttonStyles.danger, buttonStyles.sm)}>
                            <XCircle className="h-4 w-4 mr-1" />
                            Cancel
                        </button>
                    )}
                    {isFailed && onRetry && (
                        <button onClick={onRetry} className={cn(buttonStyles.primary, buttonStyles.sm)}>
                            <RotateCcw className="h-4 w-4 mr-1" />
                            Retry
                        </button>
                    )}
                </div>
            </div>

            {/* Progress Bar */}
            <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                <div className="flex items-center justify-between mb-2">
                    <span className={textStyles.small}>Progress</span>
                    <span className={textStyles.small}>
                        {completedNodes}/{totalNodes} nodes ({Math.round(execution.progress)}%)
                    </span>
                </div>
                <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                    <div
                        className={cn(
                            'h-full transition-all duration-500',
                            execution.status === 'failed' ? 'bg-red-500' : 'bg-blue-500'
                        )}
                        style={{ width: `${execution.progress}%` }}
                    />
                </div>
                <div className="flex items-center justify-between mt-2">
                    <span className={textStyles.xs}>
                        Started: {new Date(execution.startTime).toLocaleTimeString()}
                    </span>
                    {execution.endTime && (
                        <span className={textStyles.xs}>
                            Ended: {new Date(execution.endTime).toLocaleTimeString()}
                        </span>
                    )}
                </div>
            </div>

            {/* Execution Timeline */}
            <div className="p-4">
                <h4 className={cn(textStyles.h4, 'mb-4')}>Execution Timeline</h4>
                <div className="space-y-3">
                    {execution.nodes.map((node, index) => (
                        <div
                            key={node.id}
                            className={cn(
                                'flex items-start gap-4 p-3 rounded-lg border-2 transition-all',
                                statusStyles[node.status]
                            )}
                        >
                            {/* Status Icon */}
                            <div className="flex-shrink-0 mt-0.5">
                                {statusIcons[node.status]}
                            </div>

                            {/* Node Info */}
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2">
                                    <span className={textStyles.h4}>{node.name}</span>
                                    <span className={cn(
                                        'px-2 py-0.5 text-xs rounded',
                                        'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                                    )}>
                                        {node.type}
                                    </span>
                                </div>

                                {/* Timing */}
                                {node.duration !== undefined && (
                                    <p className={cn(textStyles.xs, 'mt-1')}>
                                        Duration: {formatDuration(node.duration)}
                                    </p>
                                )}

                                {/* Records */}
                                {(node.inputRecords !== undefined || node.outputRecords !== undefined) && (
                                    <p className={cn(textStyles.xs, 'mt-1')}>
                                        {node.inputRecords !== undefined && `In: ${node.inputRecords.toLocaleString()} `}
                                        {node.outputRecords !== undefined && `Out: ${node.outputRecords.toLocaleString()}`}
                                    </p>
                                )}

                                {/* Error */}
                                {node.error && (
                                    <div className="mt-2 p-2 bg-red-100 dark:bg-red-900/50 rounded text-xs text-red-700 dark:text-red-300">
                                        {node.error}
                                    </div>
                                )}
                            </div>

                            {/* Step Number */}
                            <div className="flex-shrink-0">
                                <span className={cn(
                                    'inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-medium',
                                    'bg-gray-200 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                                )}>
                                    {index + 1}
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Summary Stats */}
            <div className="px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
                <div className="grid grid-cols-4 gap-4 text-center">
                    <div>
                        <p className={textStyles.h3}>
                            {execution.nodes.filter((n) => n.status === 'completed').length}
                        </p>
                        <p className={textStyles.xs}>Completed</p>
                    </div>
                    <div>
                        <p className={textStyles.h3}>
                            {execution.nodes.filter((n) => n.status === 'running').length}
                        </p>
                        <p className={textStyles.xs}>Running</p>
                    </div>
                    <div>
                        <p className={textStyles.h3}>
                            {execution.nodes.filter((n) => n.status === 'failed').length}
                        </p>
                        <p className={textStyles.xs}>Failed</p>
                    </div>
                    <div>
                        <p className={textStyles.h3}>
                            {execution.nodes.filter((n) => n.status === 'pending').length}
                        </p>
                        <p className={textStyles.xs}>Pending</p>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default ExecutionVisualizer;
