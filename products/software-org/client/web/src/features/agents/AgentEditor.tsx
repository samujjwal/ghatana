import { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import {
    useAgent,
    useCreateAgent,
    useUpdateAgent,
    type AgentCreateBody,
    type AgentUpdateBody,
} from '@/hooks/useBuildApi';
import { useTeams, useServices } from '@/hooks/useAdminApi';

/**
 * Agent Editor Component
 *
 * <p><b>Purpose</b><br>
 * Form for creating and editing agents with persona binding,
 * tool selection, and guardrails configuration.
 *
 * <p><b>Features</b><br>
 * - Agent metadata form (name, slug, description, type)
 * - Persona selector
 * - Tool multi-select with checkboxes
 * - Guardrails JSON editor
 * - Service multi-select
 * - Validation and error handling
 *
 * @doc.type component
 * @doc.purpose Agent creation and editing
 * @doc.layer product
 * @doc.pattern Form
 */
export function AgentEditor() {
    const navigate = useNavigate();
    const { agentId } = useParams<{ agentId?: string }>();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const isEditMode = !!agentId;

    // Fetch existing agent if editing
    const { data: agentData, isLoading: isLoadingAgent } = useAgent(
        agentId || '',
        tenantId
    );

    // Fetch reference data
    const { data: teamsData } = useTeams({ tenantId });
    const { data: servicesData } = useServices({ tenantId });

    // Mutations
    const createMutation = useCreateAgent();
    const updateMutation = useUpdateAgent(agentId || '', tenantId);

    // Available tools (from system catalog)
    const availableTools = [
        { id: 'metrics', name: 'Metrics', description: 'Query and analyze system metrics' },
        { id: 'incidents', name: 'Incidents', description: 'Manage and respond to incidents' },
        { id: 'logs', name: 'Logs', description: 'Search and analyze logs' },
        { id: 'ticketing', name: 'Ticketing', description: 'Create and update tickets' },
        { id: 'deployment', name: 'Deployment', description: 'Deploy and rollback services' },
        { id: 'alerts', name: 'Alerts', description: 'Configure and manage alerts' },
        { id: 'chatops', name: 'ChatOps', description: 'Send notifications and updates' },
        { id: 'knowledge-base', name: 'Knowledge Base', description: 'Access documentation and runbooks' },
    ];

    // Agent types
    const agentTypes = [
        { value: 'reactive', label: 'Reactive', description: 'Responds to events and triggers' },
        { value: 'proactive', label: 'Proactive', description: 'Monitors and takes initiative' },
        { value: 'hybrid', label: 'Hybrid', description: 'Combines reactive and proactive behaviors' },
    ];

    // Form state
    const [formData, setFormData] = useState({
        name: '',
        slug: '',
        description: '',
        type: 'reactive' as 'reactive' | 'proactive' | 'hybrid',
        ownerTeamId: '',
        personaId: '',
        tools: [] as string[],
        guardrails: {} as Record<string, unknown>,
        serviceIds: [] as string[],
    });

    const [guardrailsJson, setGuardrailsJson] = useState('{}');
    const [errors, setErrors] = useState<Record<string, string>>({});

    // Load existing agent data
    useEffect(() => {
        if (agentData) {
            const agent = agentData;
            setFormData({
                name: agent.name,
                slug: agent.slug,
                description: agent.description || '',
                type: agent.type as 'reactive' | 'proactive' | 'hybrid',
                ownerTeamId: agent.ownerTeamId || '',
                personaId: agent.personaId || '',
                tools: agent.tools.map((t: string) => t),
                guardrails: agent.guardrails || {},
                serviceIds: agent.serviceIds || [],
            });
            setGuardrailsJson(JSON.stringify(agent.guardrails || {}, null, 2));
        }
    }, [agentData]);

    // Auto-generate slug from name
    const handleNameChange = (name: string) => {
        setFormData((prev) => ({
            ...prev,
            name,
            slug: prev.slug || name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, ''),
        }));
    };

    // Toggle tool selection
    const handleToolToggle = (toolId: string) => {
        setFormData((prev) => ({
            ...prev,
            tools: prev.tools.includes(toolId)
                ? prev.tools.filter(id => id !== toolId)
                : [...prev.tools, toolId],
        }));
    };

    // Toggle service selection
    const handleServiceToggle = (serviceId: string) => {
        setFormData((prev) => ({
            ...prev,
            serviceIds: prev.serviceIds.includes(serviceId)
                ? prev.serviceIds.filter(id => id !== serviceId)
                : [...prev.serviceIds, serviceId],
        }));
    };

    // Handle guardrails JSON update
    const handleGuardrailsChange = (value: string) => {
        setGuardrailsJson(value);
        try {
            const parsed = JSON.parse(value);
            setFormData((prev) => ({ ...prev, guardrails: parsed }));
            setErrors((prev) => ({ ...prev, guardrails: '' }));
        } catch {
            setErrors((prev) => ({ ...prev, guardrails: 'Invalid JSON format' }));
        }
    };

    // Validation
    const validate = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!formData.name.trim()) {
            newErrors.name = 'Name is required';
        }
        if (!formData.slug.trim()) {
            newErrors.slug = 'Slug is required';
        }
        if (formData.tools.length === 0) {
            newErrors.tools = 'At least one tool must be selected';
        }

        // Validate guardrails JSON
        try {
            JSON.parse(guardrailsJson);
        } catch {
            newErrors.guardrails = 'Invalid JSON format';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Submit handler
    const handleSave = async () => {
        if (!validate()) {
            return;
        }

        try {
            if (isEditMode) {
                const updateBody: AgentUpdateBody = {
                    name: formData.name,
                    slug: formData.slug,
                    description: formData.description,
                    type: formData.type,
                    ownerTeamId: formData.ownerTeamId || undefined,
                    personaId: formData.personaId || undefined,
                    tools: formData.tools,
                    guardrails: formData.guardrails,
                    serviceIds: formData.serviceIds,
                };
                await updateMutation.mutateAsync(updateBody);
            } else {
                const createBody: AgentCreateBody = {
                    ...formData,
                    tenantId,
                    ownerTeamId: formData.ownerTeamId || undefined,
                    personaId: formData.personaId || undefined,
                };
                await createMutation.mutateAsync(createBody);
            }
            navigate('/build/agents');
        } catch (error) {
            console.error('Failed to save agent:', error);
            setErrors((prev) => ({ ...prev, submit: 'Failed to save agent. Please try again.' }));
        }
    };

    const handleCancel = () => {
        navigate('/build/agents');
    };

    if (isEditMode && isLoadingAgent) {
        return (
            <div className="p-6">
                <div className="text-slate-600 dark:text-neutral-400">Loading agent...</div>
            </div>
        );
    }

    const teams = teamsData?.data || [];
    const services = servicesData?.data || [];

    return (
        <div className="p-6 max-w-5xl mx-auto">
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                    {isEditMode ? 'Edit Agent' : 'Create Agent'}
                </h1>
                <p className="text-sm text-slate-600 dark:text-neutral-400">
                    {isEditMode ? 'Update agent configuration and settings' : 'Configure a new agent with persona, tools, and guardrails'}
                </p>
            </div>

            <div className="space-y-6">
                {/* Basic Information */}
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Basic Information
                    </h2>
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                Name *
                            </label>
                            <input
                                type="text"
                                value={formData.name}
                                onChange={(e) => handleNameChange(e.target.value)}
                                className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                placeholder="e.g., Incident Response Agent"
                            />
                            {errors.name && (
                                <p className="mt-1 text-sm text-red-600 dark:text-red-400">{errors.name}</p>
                            )}
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                Slug *
                            </label>
                            <input
                                type="text"
                                value={formData.slug}
                                onChange={(e) => setFormData((prev) => ({ ...prev, slug: e.target.value }))}
                                className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                                placeholder="incident-response-agent"
                            />
                            {errors.slug && (
                                <p className="mt-1 text-sm text-red-600 dark:text-red-400">{errors.slug}</p>
                            )}
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                Description
                            </label>
                            <textarea
                                value={formData.description}
                                onChange={(e) => setFormData((prev) => ({ ...prev, description: e.target.value }))}
                                rows={3}
                                className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                placeholder="Describe what this agent does..."
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Type *
                                </label>
                                <select
                                    value={formData.type}
                                    onChange={(e) => setFormData((prev) => ({ ...prev, type: e.target.value as 'reactive' | 'proactive' | 'hybrid' }))}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                >
                                    {agentTypes.map((type) => (
                                        <option key={type.value} value={type.value}>
                                            {type.label} - {type.description}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Owner Team
                                </label>
                                <select
                                    value={formData.ownerTeamId}
                                    onChange={(e) => setFormData((prev) => ({ ...prev, ownerTeamId: e.target.value }))}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                >
                                    <option value="">Select a team...</option>
                                    {teams.map((team) => (
                                        <option key={team.id} value={team.id}>
                                            {team.name}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Persona Binding */}
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Persona Binding
                    </h2>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                            Persona (Optional)
                        </label>
                        <input
                            type="text"
                            value={formData.personaId}
                            onChange={(e) => setFormData((prev) => ({ ...prev, personaId: e.target.value }))}
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                            placeholder="persona-id or leave empty"
                        />
                        <p className="mt-1 text-xs text-slate-500 dark:text-neutral-500">
                            Bind this agent to a persona for shared capabilities and context.
                        </p>
                    </div>
                </div>

                {/* Tools */}
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Tools *
                    </h2>
                    <div className="space-y-2">
                        {availableTools.map((tool) => (
                            <label
                                key={tool.id}
                                className="flex items-start gap-3 p-3 border border-slate-200 dark:border-slate-700 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer transition-colors"
                            >
                                <input
                                    type="checkbox"
                                    checked={formData.tools.includes(tool.id)}
                                    onChange={() => handleToolToggle(tool.id)}
                                    className="mt-1 w-4 h-4 text-blue-600 border-slate-300 dark:border-slate-600 rounded focus:ring-2 focus:ring-blue-500"
                                />
                                <div className="flex-1">
                                    <div className="font-medium text-slate-900 dark:text-neutral-100 text-sm">
                                        {tool.name}
                                    </div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500">
                                        {tool.description}
                                    </div>
                                </div>
                            </label>
                        ))}
                    </div>
                    {errors.tools && (
                        <p className="mt-2 text-sm text-red-600 dark:text-red-400">{errors.tools}</p>
                    )}
                </div>

                {/* Guardrails */}
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Guardrails Configuration
                    </h2>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                            Guardrails (JSON)
                        </label>
                        <textarea
                            value={guardrailsJson}
                            onChange={(e) => handleGuardrailsChange(e.target.value)}
                            rows={8}
                            className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-neutral-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
                            placeholder='{"maxActions": 10, "requireApproval": ["deployment", "deletion"]}'
                        />
                        {errors.guardrails && (
                            <p className="mt-1 text-sm text-red-600 dark:text-red-400">{errors.guardrails}</p>
                        )}
                        <p className="mt-1 text-xs text-slate-500 dark:text-neutral-500">
                            Define safety constraints and boundaries for agent actions.
                        </p>
                    </div>
                </div>

                {/* Linked Services */}
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Linked Services
                    </h2>
                    <div className="space-y-2 max-h-60 overflow-y-auto">
                        {services.length > 0 ? (
                            services.map((service) => (
                                <label
                                    key={service.id}
                                    className="flex items-center gap-3 p-2 border border-slate-200 dark:border-slate-700 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer transition-colors"
                                >
                                    <input
                                        type="checkbox"
                                        checked={formData.serviceIds.includes(service.id)}
                                        onChange={() => handleServiceToggle(service.id)}
                                        className="w-4 h-4 text-blue-600 border-slate-300 dark:border-slate-600 rounded focus:ring-2 focus:ring-blue-500"
                                    />
                                    <span className="text-sm text-slate-900 dark:text-neutral-100">
                                        {service.name}
                                    </span>
                                </label>
                            ))
                        ) : (
                            <p className="text-sm text-slate-500 dark:text-neutral-500">
                                No services available
                            </p>
                        )}
                    </div>
                </div>

                {/* Actions */}
                <div className="flex items-center justify-end gap-3 pt-4">
                    {errors.submit && (
                        <p className="text-sm text-red-600 dark:text-red-400 mr-auto">{errors.submit}</p>
                    )}
                    <button
                        onClick={handleCancel}
                        className="px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={createMutation.isPending || updateMutation.isPending}
                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {createMutation.isPending || updateMutation.isPending
                            ? 'Saving...'
                            : isEditMode
                                ? 'Update Agent'
                                : 'Create Agent'}
                    </button>
                </div>
            </div>
        </div>
    );
}
