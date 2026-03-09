import React from 'react';
import { WorkflowNode } from './WorkflowNodeDetail';
import { StatusBadge } from '@/shared/components';

/**
 * Workflow Inspector Props interface.
 */
export interface WorkflowInspectorProps {
    nodes: WorkflowNode[];
    selectedNodeId: string | null;
    onNodeSelect?: (nodeId: string) => void;
    isLoading?: boolean;
}

/**
 * Workflow Inspector - Interactive workflow node explorer.
 *
 * <p><b>Purpose</b><br>
 * Displays workflow nodes in a structured format with selection and inspection capabilities.
 *
 * <p><b>Features</b><br>
 * - Node list with type indicators
 * - Status color coding
 * - Execution time display
 * - Click-to-inspect
 * - Hierarchical display
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <WorkflowInspector
 *   nodes={workflowNodes}
 *   selectedNodeId={selectedId}
 *   onNodeSelect={handleSelect}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Workflow node inspector
 * @doc.layer product
 * @doc.pattern Molecule
 */
export const WorkflowInspector = React.memo(
    ({ nodes, selectedNodeId, onNodeSelect, isLoading }: WorkflowInspectorProps) => {
        const nodeIcons = {
            trigger: '▶',
            action: '⚙',
            decision: '◇',
            task: '▭',
            end: '⏹',
        };

        if (isLoading) {
            return (
                <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="animate-pulse space-y-2">
                        {Array.from({ length: 5 }).map((_, i) => (
                            <div key={i} className="h-10 bg-slate-200 dark:bg-neutral-700 rounded" />
                        ))}
                    </div>
                </div>
            );
        }

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-neutral-600 dark:bg-slate-900">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Workflow Nodes
                </h3>

                {nodes.length === 0 ? (
                    <p className="text-center text-slate-500 dark:text-neutral-400 py-4">
                        No nodes in workflow
                    </p>
                ) : (
                    <div className="space-y-2 max-h-96 overflow-y-auto">
                        {nodes.map((node) => (
                            <button
                                key={node.id}
                                onClick={() => onNodeSelect?.(node.id)}
                                className={`w-full p-3 rounded-lg border-2 transition-all text-left ${selectedNodeId === node.id
                                    ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30 dark:border-blue-500'
                                    : 'border-slate-200 hover:border-slate-300 dark:border-neutral-600 dark:hover:border-slate-600'
                                    }`}
                                aria-pressed={selectedNodeId === node.id}
                            >
                                <div className="flex items-center gap-3">
                                    <span className="text-xl">{nodeIcons[node.type as keyof typeof nodeIcons]}</span>
                                    <div className="flex-1 min-w-0">
                                        <p className="font-medium text-slate-900 dark:text-neutral-100 truncate">
                                            {node.name}
                                        </p>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                                            {node.type}
                                        </p>
                                    </div>
                                    {node.status && (
                                        <StatusBadge status={node.status} />
                                    )}
                                </div>

                                {node.executionTime && node.status === 'completed' && (
                                    <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">
                                        ⏱ {node.executionTime}ms
                                    </p>
                                )}
                            </button>
                        ))}
                    </div>
                )}
            </div>
        );
    }
);

WorkflowInspector.displayName = 'WorkflowInspector';

export default WorkflowInspector;
