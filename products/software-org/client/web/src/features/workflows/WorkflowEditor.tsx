import { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import {
    useWorkflow,
    useCreateWorkflow,
    useUpdateWorkflow,
    type WorkflowTrigger,
    type WorkflowStep,
    type WorkflowCreateBody,
    type WorkflowUpdateBody,
} from '@/hooks/useBuildApi';
import { useTeams, useServices, usePolicies } from '@/hooks/useAdminApi';

/**
 * Workflow Editor Component
 *
 * <p><b>Purpose</b><br>
 * Form for creating and editing workflows with trigger configuration,
 * step builder, and service/policy binding.
 *
 * <p><b>Features</b><br>
 * - Workflow metadata form (name, slug, description)
 * - Trigger configuration (type, event, schedule, conditions)
 * - Step builder (add, edit, remove, reorder)
 * - Service multi-select
 * - Policy multi-select
 * - Save as draft or publish
 *
 * @doc.type component
 * @doc.purpose Workflow creation and editing
 * @doc.layer product
 * @doc.pattern Form
 */
export function WorkflowEditor() {
    const navigate = useNavigate();
    const { workflowId } = useParams<{ workflowId?: string }>();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const isEditMode = !!workflowId;

    // Fetch existing workflow if editing
    const { data: workflowData, isLoading: isLoadingWorkflow } = useWorkflow(
        workflowId || '',
        tenantId
    );

    // Fetch reference data
    const { data: teamsData } = useTeams({ tenantId });
    const { data: servicesData } = useServices({ tenantId });
    const { data: policiesData } = usePolicies({ tenantId });

    // Mutations
    const createMutation = useCreateWorkflow();
    const updateMutation = useUpdateWorkflow(workflowId || '', tenantId);

    // Form state
    const [formData, setFormData] = useState({
        name: '',
        slug: '',
        description: '',
        ownerTeamId: '',
        trigger: {
            type: 'event',
            event: '',
            schedule: '',
            conditions: [] as Array<Record<string, unknown>>,
        } as WorkflowTrigger,
        steps: [] as WorkflowStep[],
        serviceIds: [] as string[],
        policyIds: [] as string[],
    });

    const [errors, setErrors] = useState<Record<string, string>>({});

    // Load existing workflow data
    useEffect(() => {
        if (workflowData) {
            setFormData({
                name: workflowData.name,
                slug: workflowData.slug,
                description: workflowData.description || '',
                ownerTeamId: workflowData.ownerTeamId || '',
                trigger: workflowData.trigger,
                steps: workflowData.steps,
                serviceIds: workflowData.serviceIds,
                policyIds: workflowData.policyIds,
            });
        }
    }, [workflowData]);

    // Auto-generate slug from name
    const handleNameChange = (name: string) => {
        setFormData((prev) => ({
            ...prev,
            name,
            slug: prev.slug || name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, ''),
        }));
    };

    // Step management
    const addStep = () => {
        const newStep: WorkflowStep = {
            id: `step-${Date.now()}`,
            name: '',
            type: 'action',
            description: '',
            config: {},
        };
        setFormData((prev) => ({ ...prev, steps: [...prev.steps, newStep] }));
    };

    const updateStep = (index: number, updates: Partial<WorkflowStep>) => {
        setFormData((prev) => ({
            ...prev,
            steps: prev.steps.map((step, i) => (i === index ? { ...step, ...updates } : step)),
        }));
    };

    const removeStep = (index: number) => {
        setFormData((prev) => ({
            ...prev,
            steps: prev.steps.filter((_, i) => i !== index),
        }));
    };

    const moveStep = (index: number, direction: 'up' | 'down') => {
        const newSteps = [...formData.steps];
        const targetIndex = direction === 'up' ? index - 1 : index + 1;
        if (targetIndex < 0 || targetIndex >= newSteps.length) return;
        [newSteps[index], newSteps[targetIndex]] = [newSteps[targetIndex], newSteps[index]];
        setFormData((prev) => ({ ...prev, steps: newSteps }));
    };

    // Condition management
    const addCondition = () => {
        setFormData((prev) => ({
            ...prev,
            trigger: {
                ...prev.trigger,
                conditions: [...(prev.trigger.conditions || []), { field: '', operator: '', value: '' }],
            },
        }));
    };

    const removeCondition = (index: number) => {
        setFormData((prev) => ({
            ...prev,
            trigger: {
                ...prev.trigger,
                conditions: prev.trigger.conditions?.filter((_, i) => i !== index) || [],
            },
        }));
    };

    // Validation
    const validate = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!formData.name) newErrors.name = 'Name is required';
        if (!formData.slug) newErrors.slug = 'Slug is required';
        if (formData.steps.length === 0) newErrors.steps = 'At least one step is required';
        formData.steps.forEach((step, i) => {
            if (!step.name) newErrors[`step-${i}-name`] = 'Step name is required';
            if (!step.type) newErrors[`step-${i}-type`] = 'Step type is required';
        });

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Submit handlers
    const handleSave = async () => {
        if (!validate()) return;

        try {
            if (isEditMode) {
                const updateBody: WorkflowUpdateBody = {
                    name: formData.name,
                    description: formData.description || undefined,
                    ownerTeamId: formData.ownerTeamId || undefined,
                    trigger: formData.trigger,
                    steps: formData.steps,
                    serviceIds: formData.serviceIds,
                    policyIds: formData.policyIds,
                };
                await updateMutation.mutateAsync(updateBody);
            } else {
                const createBody: WorkflowCreateBody = {
                    tenantId,
                    name: formData.name,
                    slug: formData.slug,
                    description: formData.description || undefined,
                    ownerTeamId: formData.ownerTeamId || undefined,
                    trigger: formData.trigger,
                    steps: formData.steps,
                };
                await createMutation.mutateAsync(createBody);
            }
            navigate('/build/workflows');
        } catch (error) {
            console.error('Failed to save workflow:', error);
        }
    };

    if (isEditMode && isLoadingWorkflow) {
        return (
            <div className="p-6">
                <div className="text-slate-600 dark:text-neutral-400">Loading workflow...</div>
            </div>
        );
    }

    const teams = teamsData?.data || [];
    const services = servicesData?.data || [];
    const policies = policiesData?.data || [];

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                        {isEditMode ? 'Edit Workflow' : 'Create Workflow'}
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Configure automation workflow with trigger, steps, and policies
                    </p>
                </div>
                <button
                    onClick={() => navigate('/build/workflows')}
                    className="px-4 py-2 text-sm text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100"
                >
                    Cancel
                </button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Form */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Basic Information */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Basic Information
                        </h2>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                    Name <span className="text-red-500">*</span>
                                </label>
                                <input
                                    type="text"
                                    value={formData.name}
                                    onChange={(e) => handleNameChange(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                    placeholder="Production Deployment"
                                />
                                {errors.name && <p className="text-sm text-red-600 mt-1">{errors.name}</p>}
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                    Slug <span className="text-red-500">*</span>
                                </label>
                                <input
                                    type="text"
                                    value={formData.slug}
                                    onChange={(e) =>
                                        setFormData((prev) => ({ ...prev, slug: e.target.value }))
                                    }
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 font-mono text-sm"
                                    placeholder="production-deployment"
                                    disabled={isEditMode}
                                />
                                {errors.slug && <p className="text-sm text-red-600 mt-1">{errors.slug}</p>}
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                    Description
                                </label>
                                <textarea
                                    value={formData.description}
                                    onChange={(e) =>
                                        setFormData((prev) => ({ ...prev, description: e.target.value }))
                                    }
                                    rows={3}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                    placeholder="Describe the workflow purpose and usage"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                    Owner Team
                                </label>
                                <select
                                    value={formData.ownerTeamId}
                                    onChange={(e) =>
                                        setFormData((prev) => ({ ...prev, ownerTeamId: e.target.value }))
                                    }
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                >
                                    <option value="">No owner</option>
                                    {teams.map((team) => (
                                        <option key={team.id} value={team.id}>
                                            {team.name}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>

                    {/* Trigger Configuration */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Trigger Configuration
                        </h2>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                    Trigger Type
                                </label>
                                <select
                                    value={formData.trigger.type}
                                    onChange={(e) =>
                                        setFormData((prev) => ({
                                            ...prev,
                                            trigger: { ...prev.trigger, type: e.target.value },
                                        }))
                                    }
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                >
                                    <option value="event">Event</option>
                                    <option value="schedule">Schedule</option>
                                    <option value="manual">Manual</option>
                                </select>
                            </div>
                            {formData.trigger.type === 'event' && (
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                        Event Name
                                    </label>
                                    <input
                                        type="text"
                                        value={formData.trigger.event || ''}
                                        onChange={(e) =>
                                            setFormData((prev) => ({
                                                ...prev,
                                                trigger: { ...prev.trigger, event: e.target.value },
                                            }))
                                        }
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                        placeholder="deployment.requested"
                                    />
                                </div>
                            )}
                            {formData.trigger.type === 'schedule' && (
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                        Cron Schedule
                                    </label>
                                    <input
                                        type="text"
                                        value={formData.trigger.schedule || ''}
                                        onChange={(e) =>
                                            setFormData((prev) => ({
                                                ...prev,
                                                trigger: { ...prev.trigger, schedule: e.target.value },
                                            }))
                                        }
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 font-mono text-sm"
                                        placeholder="0 0 * * *"
                                    />
                                </div>
                            )}
                            <div>
                                <div className="flex items-center justify-between mb-2">
                                    <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300">
                                        Conditions
                                    </label>
                                    <button
                                        onClick={addCondition}
                                        className="text-sm text-blue-600 hover:text-blue-700"
                                    >
                                        + Add Condition
                                    </button>
                                </div>
                                {formData.trigger.conditions && formData.trigger.conditions.length > 0 ? (
                                    <div className="space-y-2">
                                        {formData.trigger.conditions.map((_, idx) => (
                                            <div key={idx} className="flex gap-2">
                                                <input
                                                    type="text"
                                                    placeholder="Field"
                                                    className="flex-1 px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                                />
                                                <input
                                                    type="text"
                                                    placeholder="Operator"
                                                    className="flex-1 px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                                />
                                                <input
                                                    type="text"
                                                    placeholder="Value"
                                                    className="flex-1 px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                                />
                                                <button
                                                    onClick={() => removeCondition(idx)}
                                                    className="px-3 py-2 text-red-600 hover:text-red-700"
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="text-sm text-slate-500 dark:text-neutral-500">
                                        No conditions defined
                                    </p>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Workflow Steps */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                Workflow Steps
                            </h2>
                            <button
                                onClick={addStep}
                                className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700"
                            >
                                + Add Step
                            </button>
                        </div>
                        {errors.steps && <p className="text-sm text-red-600 mb-4">{errors.steps}</p>}
                        {formData.steps.length > 0 ? (
                            <div className="space-y-4">
                                {formData.steps.map((step, idx) => (
                                    <div
                                        key={step.id}
                                        className="border border-slate-200 dark:border-slate-700 rounded-lg p-4"
                                    >
                                        <div className="flex items-start gap-4">
                                            <div className="flex-shrink-0 flex flex-col gap-1">
                                                <button
                                                    onClick={() => moveStep(idx, 'up')}
                                                    disabled={idx === 0}
                                                    className="p-1 text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100 disabled:opacity-30"
                                                >
                                                    ▲
                                                </button>
                                                <div className="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 flex items-center justify-center text-sm font-semibold">
                                                    {idx + 1}
                                                </div>
                                                <button
                                                    onClick={() => moveStep(idx, 'down')}
                                                    disabled={idx === formData.steps.length - 1}
                                                    className="p-1 text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100 disabled:opacity-30"
                                                >
                                                    ▼
                                                </button>
                                            </div>
                                            <div className="flex-1 space-y-3">
                                                <div className="grid grid-cols-2 gap-3">
                                                    <div>
                                                        <label className="block text-xs font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                                            Name <span className="text-red-500">*</span>
                                                        </label>
                                                        <input
                                                            type="text"
                                                            value={step.name}
                                                            onChange={(e) =>
                                                                updateStep(idx, { name: e.target.value })
                                                            }
                                                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                                            placeholder="Validate deployment"
                                                        />
                                                        {errors[`step-${idx}-name`] && (
                                                            <p className="text-xs text-red-600 mt-1">
                                                                {errors[`step-${idx}-name`]}
                                                            </p>
                                                        )}
                                                    </div>
                                                    <div>
                                                        <label className="block text-xs font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                                            Type <span className="text-red-500">*</span>
                                                        </label>
                                                        <select
                                                            value={step.type}
                                                            onChange={(e) =>
                                                                updateStep(idx, { type: e.target.value })
                                                            }
                                                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                                        >
                                                            <option value="action">Action</option>
                                                            <option value="approval">Approval</option>
                                                            <option value="condition">Condition</option>
                                                            <option value="notification">Notification</option>
                                                        </select>
                                                    </div>
                                                </div>
                                                <div>
                                                    <label className="block text-xs font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                                        Description
                                                    </label>
                                                    <textarea
                                                        value={step.description || ''}
                                                        onChange={(e) =>
                                                            updateStep(idx, { description: e.target.value })
                                                        }
                                                        rows={2}
                                                        className="w-full px-3 py-2 border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                                        placeholder="Describe what this step does"
                                                    />
                                                </div>
                                            </div>
                                            <button
                                                onClick={() => removeStep(idx)}
                                                className="flex-shrink-0 p-2 text-red-600 hover:text-red-700"
                                            >
                                                🗑️
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <p className="text-sm text-slate-500 dark:text-neutral-500 text-center py-8">
                                No steps defined. Click "Add Step" to create your first step.
                            </p>
                        )}
                    </div>
                </div>

                {/* Sidebar */}
                <div className="space-y-6">
                    {/* Actions */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">Actions</h3>
                        <div className="space-y-2">
                            <button
                                onClick={() => handleSave()}
                                disabled={createMutation.isPending || updateMutation.isPending}
                                className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                {createMutation.isPending || updateMutation.isPending
                                    ? 'Saving...'
                                    : isEditMode
                                    ? 'Update Workflow'
                                    : 'Create Workflow'}
                            </button>
                            <button
                                onClick={() => navigate('/build/workflows')}
                                className="w-full px-4 py-2 bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-300 dark:hover:bg-slate-700"
                            >
                                Cancel
                            </button>
                        </div>
                    </div>

                    {/* Linked Services */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Linked Services
                        </h3>
                        <div className="space-y-2 max-h-60 overflow-y-auto">
                            {services.map((service) => (
                                <label
                                    key={service.id}
                                    className="flex items-center gap-2 p-2 hover:bg-slate-50 dark:hover:bg-slate-800 rounded cursor-pointer"
                                >
                                    <input
                                        type="checkbox"
                                        checked={formData.serviceIds.includes(service.id)}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                setFormData((prev) => ({
                                                    ...prev,
                                                    serviceIds: [...prev.serviceIds, service.id],
                                                }));
                                            } else {
                                                setFormData((prev) => ({
                                                    ...prev,
                                                    serviceIds: prev.serviceIds.filter((id) => id !== service.id),
                                                }));
                                            }
                                        }}
                                        className="rounded"
                                    />
                                    <span className="text-sm text-slate-900 dark:text-neutral-100">
                                        {service.name}
                                    </span>
                                </label>
                            ))}
                        </div>
                    </div>

                    {/* Linked Policies */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Linked Policies
                        </h3>
                        <div className="space-y-2 max-h-60 overflow-y-auto">
                            {policies.map((policy) => (
                                <label
                                    key={policy.id}
                                    className="flex items-center gap-2 p-2 hover:bg-slate-50 dark:hover:bg-slate-800 rounded cursor-pointer"
                                >
                                    <input
                                        type="checkbox"
                                        checked={formData.policyIds.includes(policy.id)}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                setFormData((prev) => ({
                                                    ...prev,
                                                    policyIds: [...prev.policyIds, policy.id],
                                                }));
                                            } else {
                                                setFormData((prev) => ({
                                                    ...prev,
                                                    policyIds: prev.policyIds.filter((id) => id !== policy.id),
                                                }));
                                            }
                                        }}
                                        className="rounded"
                                    />
                                    <span className="text-sm text-slate-900 dark:text-neutral-100">
                                        {policy.name}
                                    </span>
                                </label>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
