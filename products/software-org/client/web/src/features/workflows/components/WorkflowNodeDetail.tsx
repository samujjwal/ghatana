import React from 'react';
import { StatusBadge } from '@/shared/components';

/**
 * Workflow node interface.
 */
export interface WorkflowNode {
    id: string;
    name: string;
    type: 'trigger' | 'action' | 'decision' | 'task' | 'end';
    description?: string;
    inputCount?: number;
    outputCount?: number;
    status?: 'idle' | 'running' | 'completed' | 'failed';
    executionTime?: number;
}

/**
 * Workflow Node Detail Props interface.
 */
export interface WorkflowNodeDetailProps {
    node: WorkflowNode;
    inputs?: Array<{ name: string; type: string; value: unknown }>;
    outputs?: Array<{ name: string; type: string; value: unknown }>;
    onClose?: () => void;
    isLoading?: boolean;
}

/**
 * Workflow Node Detail - Displays detailed information about a workflow node.
 *
 * <p><b>Purpose</b><br>
 * Shows node metadata, input/output connections, execution history, and error details.
 *
 * <p><b>Features</b><br>
 * - Node type icon and status badge
 * - Input/output parameters with types
 * - Execution time tracking
 * - Error/failure details
 * - JSON preview of complex values
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <WorkflowNodeDetail 
 *   node={selectedNode}
 *   inputs={nodeInputs}
 *   outputs={nodeOutputs}
 *   onClose={handleClose}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Workflow node inspector
 * @doc.layer product
 * @doc.pattern Organism
 */
export const WorkflowNodeDetail = React.memo(
    ({ node, inputs, outputs, onClose, isLoading }: WorkflowNodeDetailProps) => {
        const nodeIcons = {
            trigger: '▶',
            action: '⚙',
            decision: '◇',
            task: '▭',
            end: '⏹',
        };

        if (isLoading) {
            return (
                <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="animate-pulse space-y-4">
                        <div className="h-8 w-full bg-slate-200 dark:bg-neutral-700 rounded" />
                        <div className="h-6 w-32 bg-slate-200 dark:bg-neutral-700 rounded" />
                    </div>
                </div>
            );
        }

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                {/* Header */}
                <div className="flex items-start justify-between mb-6">
                    <div className="flex items-center gap-3">
                        <div className="text-3xl">{nodeIcons[node.type]}</div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                {node.name}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">
                                Type: <span className="font-mono">{node.type}</span>
                            </p>
                        </div>
                    </div>
                    {onClose && (
                        <button
                            onClick={onClose}
                            className="text-slate-500 hover:text-slate-700 dark:text-neutral-400 dark:hover:text-slate-200 transition-colors"
                            aria-label="Close node detail"
                        >
                            ✕
                        </button>
                    )}
                </div>

                {/* Status and metadata */}
                <div className="flex items-center gap-3 mb-6 pb-6 border-b border-slate-200 dark:border-neutral-600">
                    {node.status && (
                        <div>
                            <StatusBadge status={node.status} />
                        </div>
                    )}
                    {node.executionTime && (
                        <span className="text-sm text-slate-600 dark:text-neutral-400">
                            Execution: <span className="font-mono font-medium">{node.executionTime}ms</span>
                        </span>
                    )}
                </div>

                {/* Description */}
                {node.description && (
                    <div className="mb-6">
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            {node.description}
                        </p>
                    </div>
                )}

                {/* Inputs section */}
                {inputs && inputs.length > 0 && (
                    <div className="mb-6">
                        <h4 className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                            Inputs ({inputs.length})
                        </h4>
                        <div className="space-y-2">
                            {inputs.map((input, idx) => (
                                <div
                                    key={idx}
                                    className="p-3 rounded-lg bg-slate-50 dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600"
                                >
                                    <div className="flex items-center justify-between mb-2">
                                        <p className="font-medium text-slate-900 dark:text-neutral-100">
                                            {input.name}
                                        </p>
                                        <span className="text-xs font-mono text-slate-600 dark:text-neutral-400">
                                            {input.type}
                                        </span>
                                    </div>
                                    <pre className="text-xs text-slate-700 dark:text-neutral-300 bg-white dark:bg-slate-900 p-2 rounded border border-slate-200 dark:border-neutral-600 overflow-auto max-h-32">
                                        {typeof input.value === 'string'
                                            ? input.value
                                            : JSON.stringify(input.value, null, 2)}
                                    </pre>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Outputs section */}
                {outputs && outputs.length > 0 && (
                    <div>
                        <h4 className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                            Outputs ({outputs.length})
                        </h4>
                        <div className="space-y-2">
                            {outputs.map((output, idx) => (
                                <div
                                    key={idx}
                                    className="p-3 rounded-lg bg-green-50 dark:bg-green-600/30 border border-green-200 dark:border-green-800"
                                >
                                    <div className="flex items-center justify-between mb-2">
                                        <p className="font-medium text-green-900 dark:text-green-100">
                                            {output.name}
                                        </p>
                                        <span className="text-xs font-mono text-green-700 dark:text-green-300">
                                            {output.type}
                                        </span>
                                    </div>
                                    <pre className="text-xs text-green-900 dark:text-green-100 bg-white dark:bg-green-950 p-2 rounded border border-green-200 dark:border-green-800 overflow-auto max-h-32">
                                        {typeof output.value === 'string'
                                            ? output.value
                                            : JSON.stringify(output.value, null, 2)}
                                    </pre>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* No inputs/outputs message */}
                {(!inputs || inputs.length === 0) && (!outputs || outputs.length === 0) && (
                    <p className="text-center text-slate-500 dark:text-neutral-400 py-8">
                        No inputs or outputs configured for this node.
                    </p>
                )}
            </div>
        );
    }
);

WorkflowNodeDetail.displayName = 'WorkflowNodeDetail';

export default WorkflowNodeDetail;
