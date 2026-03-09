import React, { useState } from "react";
import { useNavigate } from "react-router";
import { Badge } from "@/components/ui";
import { StatusBadge } from '@/shared/components';
import { WorkflowExecutionModal } from "./components/WorkflowExecutionModal";
import { useWorkflows } from '@/hooks/useBuildApi';
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { usePermissions } from '@/hooks/usePermissions';

/**
 * Workflow Explorer
 *
 * <p><b>Purpose</b><br>
 * Full-featured orchestration dashboard showing active workflows, configuration,
 * and status. Displays workflows from Build API with filtering and management actions.
 *
 * <p><b>Features</b><br>
 * - Workflow list with status and metadata
 * - Filter by status (draft/active/disabled)
 * - View workflow details (trigger, steps, services, policies)
 * - Quick actions (Activate, Edit, Test in Simulator)
 *
 * @doc.type component
 * @doc.purpose Workflow management and monitoring
 * @doc.layer product
 * @doc.pattern Page
 */
export function WorkflowExplorer() {
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const { canCreateWorkflow, canEditWorkflow, canExecuteWorkflow, canApproveWorkflow } = usePermissions();
    const [filterStatus, setFilterStatus] = useState<string>('all');
    const [selectedWorkflowId, setSelectedWorkflowId] = useState<string | null>(null);
    const [showExecutionModal, setShowExecutionModal] = useState(false);

    // Use selected tenant or fallback to seeded tenant ID
    const tenantId = selectedTenant || 'acme-payments-id';

    const { data: workflowsData, isLoading, error } = useWorkflows(
        tenantId,
        filterStatus === 'all' ? undefined : filterStatus
    );

    const workflows = workflowsData?.data || [];
    const selectedWorkflow = workflows.find((w) => w.id === selectedWorkflowId);

    const stats = {
        total: workflows.length,
        active: workflows.filter((w) => w.status === 'active').length,
        draft: workflows.filter((w) => w.status === 'draft').length,
        disabled: workflows.filter((w) => w.status === 'disabled').length,
    };

    if (error) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load workflows: {error.message}
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col md:flex-row md:justify-between md:items-start gap-4">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Workflows</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Automation workflows for deployment, incident response, and operations
                    </p>
                </div>
                <button
                    onClick={() => navigate('/build/workflows/new')}
                    disabled={!canCreateWorkflow}
                    title={!canCreateWorkflow ? 'You do not have permission to create workflows' : undefined}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-blue-600"
                >
                    <span>+</span>
                    <span>New Workflow</span>
                </button>
            </div>

            {/* Stats Bar */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard label="Total Workflows" value={stats.total} icon="🔄" />
                <StatCard label="Active" value={stats.active} icon="✅" />
                <StatCard label="Draft" value={stats.draft} icon="📝" />
                <StatCard label="Disabled" value={stats.disabled} icon="⏸️" />
            </div>

            {/* Filters */}
            <div className="flex gap-2">
                <button
                    onClick={() => setFilterStatus('all')}
                    className={`px-4 py-2 rounded text-sm ${filterStatus === 'all'
                            ? 'bg-blue-600 text-white'
                            : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300'
                        }`}
                >
                    All
                </button>
                <button
                    onClick={() => setFilterStatus('active')}
                    className={`px-4 py-2 rounded text-sm ${filterStatus === 'active'
                            ? 'bg-green-600 text-white'
                            : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300'
                        }`}
                >
                    Active
                </button>
                <button
                    onClick={() => setFilterStatus('draft')}
                    className={`px-4 py-2 rounded text-sm ${filterStatus === 'draft'
                            ? 'bg-yellow-600 text-white'
                            : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300'
                        }`}
                >
                    Draft
                </button>
                <button
                    onClick={() => setFilterStatus('disabled')}
                    className={`px-4 py-2 rounded text-sm ${filterStatus === 'disabled'
                            ? 'bg-gray-600 text-white'
                            : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300'
                        }`}
                >
                    Disabled
                </button>
            </div>

            {/* Loading State */}
            {isLoading && (
                <div className="text-center py-12 text-slate-600 dark:text-neutral-400">
                    Loading workflows...
                </div>
            )}

            {/* Empty State */}
            {!isLoading && workflows.length === 0 && (
                <div className="text-center py-12 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg">
                    <div className="text-4xl mb-4">📋</div>
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                        No workflows found
                    </h3>
                    <p className="text-slate-600 dark:text-neutral-400 mb-4">
                        {filterStatus === 'all' ? 'Get started by creating your first workflow' : `No ${filterStatus} workflows found`}
                    </p>
                    {filterStatus === 'all' && canCreateWorkflow && (
                        <button
                            onClick={() => navigate('/build/workflows/new')}
                            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                        >
                            Create Workflow
                        </button>
                    )}
                </div>
            )}

            {/* Main Grid */}
            {!isLoading && workflows.length > 0 && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Workflow List */}
                    <div className="lg:col-span-2 space-y-3">
                        {workflows.map((workflow) => (
                            <div
                                key={workflow.id}
                                onClick={() => setSelectedWorkflowId(workflow.id)}
                                className={`p-4 border rounded-lg cursor-pointer transition ${selectedWorkflowId === workflow.id
                                        ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30'
                                        : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-slate-300'
                                    }`}
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex-1">
                                        <div className="font-semibold text-slate-900 dark:text-neutral-100">
                                            {workflow.name}
                                        </div>
                                        {workflow.description && (
                                            <div className="text-sm text-slate-500 dark:text-neutral-400 mt-1">
                                                {workflow.description}
                                            </div>
                                        )}
                                    </div>
                                    <Badge
                                        tone={
                                            workflow.status === 'active'
                                                ? 'positive'
                                                : workflow.status === 'draft'
                                                    ? 'warning'
                                                    : 'neutral'
                                        }
                                        variant="neutral"
                                    >
                                        {workflow.status}
                                    </Badge>
                                </div>
                                <div className="grid grid-cols-3 gap-3 mt-3 text-xs">
                                    <div>
                                        <div className="text-slate-500 dark:text-neutral-500">Steps</div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {workflow.steps.length}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-slate-500 dark:text-neutral-500">Services</div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {workflow.serviceIds.length}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-slate-500 dark:text-neutral-500">Policies</div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {workflow.policyIds.length}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>

                    {/* Workflow Details */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        {selectedWorkflow ? (
                            <div className="space-y-4">
                                <div>
                                    <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {selectedWorkflow.name}
                                    </h3>
                                    {selectedWorkflow.description && (
                                        <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                            {selectedWorkflow.description}
                                        </p>
                                    )}
                                </div>
                                <div className="border-t border-slate-200 dark:border-slate-800 pt-4 space-y-3">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Status</span>
                                        <StatusBadge status={selectedWorkflow.status} />
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Workflow ID</span>
                                        <span className="font-mono text-xs text-slate-900 dark:text-neutral-100">
                                            {selectedWorkflow.slug}
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Steps</span>
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">
                                            {selectedWorkflow.steps.length}
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Services</span>
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">
                                            {selectedWorkflow.serviceIds.length}
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Policies</span>
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">
                                            {selectedWorkflow.policyIds.length}
                                        </span>
                                    </div>
                                </div>
                                <div className="flex flex-col gap-2 pt-4 border-t border-slate-200 dark:border-slate-800">
                                    {canExecuteWorkflow && (
                                        <button
                                            onClick={() => navigate(`/build/simulator?workflowId=${selectedWorkflow.id}`)}
                                            className="w-full px-3 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 transition-colors"
                                        >
                                            Test in Simulator
                                        </button>
                                    )}
                                    {canEditWorkflow && (
                                        <button
                                            onClick={() => navigate(`/build/workflows/${selectedWorkflow.id}`)}
                                            className="w-full px-3 py-2 bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 rounded text-sm hover:bg-slate-300 dark:hover:bg-slate-700 transition-colors"
                                        >
                                            Edit Workflow
                                        </button>
                                    )}
                                    {canApproveWorkflow && selectedWorkflow.status === 'draft' && (
                                        <button
                                            onClick={() => {
                                                /* TODO: Implement activate */
                                            }}
                                            className="w-full px-3 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700 transition-colors"
                                        >
                                            Activate
                                        </button>
                                    )}
                                    {!canEditWorkflow && !canExecuteWorkflow && (
                                        <div className="text-center text-sm text-slate-500 dark:text-neutral-400 py-2">
                                            View-only access
                                        </div>
                                    )}
                                </div>
                            </div>
                        ) : (
                            <div className="text-center text-slate-500 dark:text-neutral-400 py-8">
                                Select a workflow to view details
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Workflow Execution Modal */}
            {showExecutionModal && selectedWorkflowId && (
                <WorkflowExecutionModal
                    workflowId={selectedWorkflowId}
                    onClose={() => {
                        setShowExecutionModal(false);
                        setSelectedWorkflowId(null);
                    }}
                />
            )}
        </div>
    );
}

function StatCard({ label, value, icon }: { label: string; value: any; icon: string }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="text-2xl mb-2">{icon}</div>
            <div className="text-sm text-slate-600 dark:text-neutral-400">{label}</div>
            <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mt-1">{value}</div>
        </div>
    );
}

export default WorkflowExplorer;
