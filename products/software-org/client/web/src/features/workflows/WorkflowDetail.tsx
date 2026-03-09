import { useNavigate, useParams } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useWorkflow, useActivateWorkflow } from '@/hooks/useBuildApi';
import { Badge } from "@/components/ui";
import { StatusBadge } from '@/shared/components';

/**
 * Workflow Detail Page
 *
 * <p><b>Purpose</b><br>
 * Detailed view of a single workflow showing configuration, steps, services,
 * policies, and execution history. Allows editing and activation.
 *
 * <p><b>Features</b><br>
 * - Full workflow metadata (name, description, status, owner)
 * - Step-by-step visualization with configuration
 * - Service and policy bindings
 * - Trigger configuration display
 * - Actions: Edit, Activate/Deactivate, Test in Simulator, Delete
 *
 * @doc.type component
 * @doc.purpose Workflow detail and management
 * @doc.layer product
 * @doc.pattern Page
 */
export function WorkflowDetail() {
    const navigate = useNavigate();
    const { workflowId } = useParams<{ workflowId: string }>();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const { data: workflowData, isLoading, error } = useWorkflow(workflowId!, tenantId);
    const activateMutation = useActivateWorkflow(workflowId!, tenantId);

    if (isLoading) {
        return (
            <div className="p-6">
                <div className="text-slate-600 dark:text-neutral-400">Loading workflow...</div>
            </div>
        );
    }

    if (error || !workflowData) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load workflow: {error?.message || 'Not found'}
                </div>
            </div>
        );
    }

    const workflow = workflowData;

    const handleActivate = () => {
        activateMutation.mutate(undefined, {
            onSuccess: () => {
                // Refetch will happen automatically via cache invalidation
            },
            onError: (err) => {
                console.error('Failed to activate workflow:', err);
            },
        });
    };

    return (
        <div className="space-y-6">
            {/* Breadcrumb */}
            <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400">
                <button
                    onClick={() => navigate('/build/workflows')}
                    className="hover:text-blue-600 dark:hover:text-blue-300"
                >
                    Workflows
                </button>
                <span>/</span>
                <span className="text-slate-900 dark:text-neutral-100">{workflow.name}</span>
            </div>

            {/* Header */}
            <div className="flex flex-col md:flex-row md:justify-between md:items-start gap-4">
                <div className="flex-1">
                    <div className="flex items-center gap-3">
                        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                            {workflow.name}
                        </h1>
                        <StatusBadge status={workflow.status} />
                    </div>
                    {workflow.description && (
                        <p className="text-slate-600 dark:text-neutral-400 mt-2">
                            {workflow.description}
                        </p>
                    )}
                    <div className="flex items-center gap-4 mt-3 text-sm text-slate-500 dark:text-neutral-500">
                        <span>ID: <code className="text-xs">{workflow.slug}</code></span>
                        <span>•</span>
                        <span>Steps: {workflow.steps.length}</span>
                        <span>•</span>
                        <span>Services: {workflow.serviceIds.length}</span>
                        <span>•</span>
                        <span>Policies: {workflow.policyIds.length}</span>
                    </div>
                </div>
                <div className="flex gap-2">
                    {workflow.status === 'draft' && (
                        <button
                            onClick={handleActivate}
                            disabled={activateMutation.isPending}
                            className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {activateMutation.isPending ? 'Activating...' : 'Activate'}
                        </button>
                    )}
                    <button
                        onClick={() => navigate(`/build/simulator?workflowId=${workflow.id}`)}
                        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                    >
                        Test in Simulator
                    </button>
                    <button
                        onClick={() => navigate(`/build/workflows/${workflow.id}/edit`)}
                        className="px-4 py-2 bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-300 dark:hover:bg-slate-700"
                    >
                        Edit
                    </button>
                </div>
            </div>

            {/* Workflow Configuration Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Content */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Trigger Configuration */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Trigger Configuration
                        </h2>
                        <div className="space-y-3">
                            <div className="flex justify-between text-sm">
                                <span className="text-slate-600 dark:text-neutral-400">Type</span>
                                <Badge tone="neutral" variant="neutral">
                                    {workflow.trigger.type}
                                </Badge>
                            </div>
                            {workflow.trigger.event && (
                                <div className="flex justify-between text-sm">
                                    <span className="text-slate-600 dark:text-neutral-400">Event</span>
                                    <span className="font-mono text-xs text-slate-900 dark:text-neutral-100">
                                        {workflow.trigger.event}
                                    </span>
                                </div>
                            )}
                            {workflow.trigger.schedule && (
                                <div className="flex justify-between text-sm">
                                    <span className="text-slate-600 dark:text-neutral-400">Schedule</span>
                                    <span className="font-mono text-xs text-slate-900 dark:text-neutral-100">
                                        {workflow.trigger.schedule}
                                    </span>
                                </div>
                            )}
                            {workflow.trigger.conditions && workflow.trigger.conditions.length > 0 && (
                                <div className="space-y-2">
                                    <span className="text-sm text-slate-600 dark:text-neutral-400">Conditions</span>
                                    <div className="space-y-1">
                                        {workflow.trigger.conditions.map((condition, idx) => (
                                            <div
                                                key={idx}
                                                className="text-xs font-mono bg-slate-50 dark:bg-slate-800 p-2 rounded"
                                            >
                                                {JSON.stringify(condition, null, 2)}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Workflow Steps */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Workflow Steps ({workflow.steps.length})
                        </h2>
                        <div className="space-y-4">
                            {workflow.steps.map((step, idx) => (
                                <div
                                    key={step.id}
                                    className="border border-slate-200 dark:border-slate-700 rounded-lg p-4"
                                >
                                    <div className="flex items-start gap-3">
                                        <div className="flex-shrink-0 w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 flex items-center justify-center text-sm font-semibold">
                                            {idx + 1}
                                        </div>
                                        <div className="flex-1">
                                            <div className="flex items-center justify-between">
                                                <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                    {step.name}
                                                </h3>
                                                <Badge tone="neutral" variant="neutral">
                                                    {step.type}
                                                </Badge>
                                            </div>
                                            {step.description && (
                                                <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                                    {step.description}
                                                </p>
                                            )}
                                            {step.config && Object.keys(step.config).length > 0 && (
                                                <div className="mt-3">
                                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">
                                                        Configuration:
                                                    </div>
                                                    <pre className="text-xs bg-slate-50 dark:bg-slate-800 p-2 rounded overflow-x-auto">
                                                        {JSON.stringify(step.config, null, 2)}
                                                    </pre>
                                                </div>
                                            )}
                                            {step.timeout && (
                                                <div className="text-xs text-slate-500 dark:text-neutral-500 mt-2">
                                                    Timeout: {step.timeout}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Sidebar */}
                <div className="space-y-6">
                    {/* Metadata */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Metadata
                        </h3>
                        <div className="space-y-3 text-sm">
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500">Status</div>
                                <StatusBadge status={workflow.status} />
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500">Workflow ID</div>
                                <div className="font-mono text-xs text-slate-900 dark:text-neutral-100 mt-1">
                                    {workflow.slug}
                                </div>
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500">Created</div>
                                <div className="text-slate-900 dark:text-neutral-100 mt-1">
                                    {new Date(workflow.createdAt).toLocaleDateString()}
                                </div>
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500">Updated</div>
                                <div className="text-slate-900 dark:text-neutral-100 mt-1">
                                    {new Date(workflow.updatedAt).toLocaleDateString()}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Linked Services */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Linked Services ({workflow.serviceIds.length})
                        </h3>
                        {workflow.serviceIds.length > 0 ? (
                            <div className="space-y-2">
                                {workflow.serviceIds.map((serviceId: string) => (
                                    <div
                                        key={serviceId}
                                        className="text-sm bg-slate-50 dark:bg-slate-800 p-2 rounded"
                                    >
                                        <div className="font-mono text-xs text-slate-600 dark:text-neutral-400">
                                            {serviceId}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="text-sm text-slate-500 dark:text-neutral-500">
                                No services linked
                            </div>
                        )}
                    </div>

                    {/* Linked Policies */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Linked Policies ({workflow.policyIds.length})
                        </h3>
                        {workflow.policyIds.length > 0 ? (
                            <div className="space-y-2">
                                {workflow.policyIds.map((policyId: string) => (
                                    <div
                                        key={policyId}
                                        className="text-sm bg-slate-50 dark:bg-slate-800 p-2 rounded"
                                    >
                                        <div className="font-mono text-xs text-slate-600 dark:text-neutral-400">
                                            {policyId}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="text-sm text-slate-500 dark:text-neutral-500">
                                No policies linked
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
