/**
 * Workflow Template Card Component
 *
 * <p><b>Purpose</b><br>
 * Displays a workflow template with metadata, status, and quick actions.
 * Used in Automation Engine for workflow management.
 *
 * <p><b>Features</b><br>
 * - Workflow metadata display
 * - Task count visualization
 * - Enabled/disabled status
 * - Quick action buttons (Execute, Edit, Delete)
 * - Selection state
 * - Dark mode support
 * - Trigger information
 *
 * @doc.type component
 * @doc.purpose Workflow template card display component
 * @doc.layer product
 * @doc.pattern Display Component
 */

import { memo } from 'react';

export interface Workflow {
    id: string;
    name: string;
    description: string;
    enabled: boolean;
    taskCount: number;
    triggers: number;
    lastExecuted?: Date;
    successRate?: number;
}

interface WorkflowTemplateCardProps {
    workflow: Workflow;
    isSelected: boolean;
    onSelect: (workflowId: string) => void;
    onEdit: (workflowId: string) => void;
    onExecute: (workflowId: string, inputs: Record<string, unknown>) => void;
    onDelete?: (workflowId: string) => void;
}

/**
 * WorkflowTemplateCard component - displays workflow template with actions.
 *
 * GIVEN: Workflow template definition
 * WHEN: Component renders
 * THEN: Display workflow details with action buttons
 *
 * @param workflow - Workflow template data
 * @param isSelected - Whether workflow is currently selected
 * @param onSelect - Callback when workflow is selected
 * @param onEdit - Callback to edit workflow
 * @param onExecute - Callback to execute workflow
 * @param onDelete - Optional callback to delete workflow
 * @returns Rendered workflow card component
 */
const WorkflowTemplateCard = memo(
    ({
        workflow,
        isSelected,
        onSelect,
        onEdit,
        onExecute,
        onDelete,
    }: WorkflowTemplateCardProps) => {
        return (
            <div
                className={`rounded-lg border-2 transition-all cursor-pointer ${isSelected
                        ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30 shadow-lg'
                        : 'border-slate-200 dark:border-neutral-600 bg-white dark:bg-neutral-800 hover:shadow-md'
                    }`}
                onClick={() => onSelect(workflow.id)}
                role="button"
                tabIndex={0}
                aria-label={`Workflow: ${workflow.name}`}
                aria-pressed={isSelected}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onSelect(workflow.id);
                    }
                }}
            >
                {/* Card Content */}
                <div className="p-4">
                    {/* Header */}
                    <div className="flex justify-between items-start mb-3">
                        <div className="flex-1">
                            <div className="flex items-center gap-2">
                                <h3 className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                    {workflow.name}
                                </h3>
                                <span
                                    className={`px-2 py-1 text-xs font-medium rounded-full ${workflow.enabled
                                            ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
                                            : 'bg-slate-100 dark:bg-neutral-700 text-slate-600 dark:text-neutral-400'
                                        }`}
                                    role="status"
                                    aria-label={workflow.enabled ? 'Enabled' : 'Disabled'}
                                >
                                    {workflow.enabled ? '✓ Active' : '○ Inactive'}
                                </span>
                            </div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                {workflow.description}
                            </p>
                        </div>
                    </div>

                    {/* Metadata Grid */}
                    <div className="grid grid-cols-3 gap-3 mb-4 text-sm">
                        <div className="bg-slate-50 dark:bg-neutral-700 rounded p-2">
                            <div className="text-xs text-slate-600 dark:text-neutral-400 font-medium">
                                Tasks
                            </div>
                            <div className="text-xl font-bold text-slate-900 dark:text-neutral-100">
                                {workflow.taskCount}
                            </div>
                        </div>
                        <div className="bg-slate-50 dark:bg-neutral-700 rounded p-2">
                            <div className="text-xs text-slate-600 dark:text-neutral-400 font-medium">
                                Triggers
                            </div>
                            <div className="text-xl font-bold text-slate-900 dark:text-neutral-100">
                                {workflow.triggers}
                            </div>
                        </div>
                        <div className="bg-slate-50 dark:bg-neutral-700 rounded p-2">
                            <div className="text-xs text-slate-600 dark:text-neutral-400 font-medium">
                                Success Rate
                            </div>
                            <div className="text-xl font-bold text-slate-900 dark:text-neutral-100">
                                {workflow.successRate ? `${(workflow.successRate * 100).toFixed(0)}%` : 'N/A'}
                            </div>
                        </div>
                    </div>

                    {/* Metadata */}
                    {workflow.lastExecuted && (
                        <div className="text-xs text-slate-600 dark:text-neutral-400 mb-4">
                            Last executed: {new Date(workflow.lastExecuted).toLocaleDateString()}
                        </div>
                    )}

                    {/* Actions */}
                    <div className="flex gap-2">
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onExecute(workflow.id, {});
                            }}
                            className="flex-1 px-3 py-2 bg-blue-500 text-white rounded text-sm font-medium hover:bg-blue-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            disabled={!workflow.enabled}
                            aria-label={`Execute ${workflow.name}`}
                            title={!workflow.enabled ? 'Workflow is disabled' : ''}
                        >
                            Execute
                        </button>
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onEdit(workflow.id);
                            }}
                            className="px-3 py-2 bg-slate-200 dark:bg-neutral-700 text-slate-900 dark:text-neutral-100 rounded text-sm font-medium hover:bg-slate-300 dark:hover:bg-slate-600 transition-colors"
                            aria-label={`Edit ${workflow.name}`}
                        >
                            Edit
                        </button>
                        {onDelete && (
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    if (confirm(`Delete workflow "${workflow.name}"?`)) {
                                        onDelete(workflow.id);
                                    }
                                }}
                                className="px-3 py-2 bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-rose-400 rounded text-sm font-medium hover:bg-red-200 dark:hover:bg-red-900/50 transition-colors"
                                aria-label={`Delete ${workflow.name}`}
                            >
                                Delete
                            </button>
                        )}
                    </div>
                </div>
            </div>
        );
    }
);

WorkflowTemplateCard.displayName = 'WorkflowTemplateCard';

export default WorkflowTemplateCard;
