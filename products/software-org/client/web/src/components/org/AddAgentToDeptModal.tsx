/**
 * Add Agent to Department Modal
 *
 * Modal for assigning an existing agent to a department or creating a new one.
 * Shows available agents and allows selection.
 *
 * @doc.type component
 * @doc.purpose Assign agent to department
 * @doc.layer product
 * @doc.pattern Form Modal
 */

import { useState, useMemo } from 'react';
import { Bot, Search, Loader2, UserPlus, Check, AlertCircle } from 'lucide-react';
import { Drawer, FormField, Input, Textarea, Select } from '@/components/admin';
import { Button } from '@/components/ui';
import { useAgents, useAddAgentToDepartment, orgQueryKeys } from '@/hooks';
import { useQueryClient } from '@tanstack/react-query';
import type { Agent } from '@/hooks/useOrganizationApi';

interface AddAgentToDeptModalProps {
    isOpen: boolean;
    onClose: () => void;
    departmentId: string;
    departmentName: string;
    tenantId: string;
    onSuccess?: () => void;
}

type ModalMode = 'select' | 'create';

const AGENT_ROLES = [
    { value: 'automation', label: 'Automation' },
    { value: 'analysis', label: 'Analysis' },
    { value: 'monitoring', label: 'Monitoring' },
    { value: 'security', label: 'Security' },
    { value: 'deployment', label: 'Deployment' },
    { value: 'testing', label: 'Testing' },
    { value: 'support', label: 'Support' },
    { value: 'other', label: 'Other' },
];

const AGENT_STATUS_COLORS: Record<string, string> = {
    ONLINE: 'bg-green-500',
    ACTIVE: 'bg-green-500',
    active: 'bg-green-500',
    OFFLINE: 'bg-slate-400',
    IDLE: 'bg-yellow-500',
    idle: 'bg-yellow-500',
    BUSY: 'bg-blue-500',
    busy: 'bg-blue-500',
};

export function AddAgentToDeptModal({
    isOpen,
    onClose,
    departmentId,
    departmentName,
    tenantId,
    onSuccess,
}: AddAgentToDeptModalProps) {
    const queryClient = useQueryClient();
    const addAgentMutation = useAddAgentToDepartment();

    const [mode, setMode] = useState<ModalMode>('select');
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

    // Form data for creating new agent
    const [newAgentForm, setNewAgentForm] = useState({
        name: '',
        role: 'automation',
        description: '',
    });

    // Fetch available agents (those not already in this department)
    const { data: allAgents = [], isLoading: agentsLoading } = useAgents(tenantId);

    // Filter agents not already in this department
    const availableAgents = useMemo(() => {
        return allAgents.filter(
            (agent) =>
                agent.departmentId !== departmentId &&
                (searchQuery === '' ||
                    agent.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                    agent.role?.toLowerCase().includes(searchQuery.toLowerCase()))
        );
    }, [allAgents, departmentId, searchQuery]);

    const validateCreateForm = () => {
        const newErrors: Record<string, string> = {};

        if (!newAgentForm.name.trim()) {
            newErrors.name = 'Agent name is required';
        } else if (newAgentForm.name.length < 2) {
            newErrors.name = 'Name must be at least 2 characters';
        }

        if (!newAgentForm.role) {
            newErrors.role = 'Agent role is required';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSelectAgent = async () => {
        if (!selectedAgentId) {
            setErrors({ select: 'Please select an agent' });
            return;
        }

        const selectedAgent = allAgents.find((a) => a.id === selectedAgentId);
        if (!selectedAgent) {
            setErrors({ select: 'Selected agent not found' });
            return;
        }

        try {
            await addAgentMutation.mutateAsync({
                departmentId,
                agent: selectedAgent,
            });

            queryClient.invalidateQueries({ queryKey: orgQueryKeys.agents() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });

            handleClose();
            onSuccess?.();
        } catch (error) {
            console.error('Failed to add agent:', error);
            const isApiNotImplemented = (error as any)?.response?.status === 404;
            setErrors({
                submit: isApiNotImplemented
                    ? 'This feature requires backend API implementation. The endpoint /api/v1/org/departments/{id}/agents is not yet available.'
                    : error instanceof Error
                        ? error.message
                        : 'Failed to add agent to department',
            });
        }
    };

    const handleCreateAgent = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!validateCreateForm()) {
            return;
        }

        try {
            const newAgent: Partial<Agent> = {
                name: newAgentForm.name.trim(),
                role: newAgentForm.role,
                status: 'ONLINE',
                departmentId,
                capabilities: [],
                configuration: {
                    description: newAgentForm.description.trim() || undefined,
                },
            };

            await addAgentMutation.mutateAsync({
                departmentId,
                agent: newAgent as Agent,
            });

            queryClient.invalidateQueries({ queryKey: orgQueryKeys.agents() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });

            handleClose();
            onSuccess?.();
        } catch (error) {
            console.error('Failed to create agent:', error);
            const isApiNotImplemented = (error as any)?.response?.status === 404;
            setErrors({
                submit: isApiNotImplemented
                    ? 'This feature requires backend API implementation. The endpoint /api/v1/org/departments/{id}/agents is not yet available.'
                    : error instanceof Error
                        ? error.message
                        : 'Failed to create agent',
            });
        }
    };

    const handleClose = () => {
        setMode('select');
        setSearchQuery('');
        setSelectedAgentId(null);
        setNewAgentForm({ name: '', role: 'automation', description: '' });
        setErrors({});
        onClose();
    };

    return (
        <Drawer
            isOpen={isOpen}
            onClose={handleClose}
            title={`Add Agent to ${departmentName}`}
            size="md"
        >
            {/* Mode Tabs */}
            <div className="flex gap-2 mb-6">
                <button
                    type="button"
                    onClick={() => setMode('select')}
                    className={`flex-1 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${mode === 'select'
                        ? 'bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300'
                        : 'bg-gray-100 dark:bg-slate-800 text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-slate-700'
                        }`}
                >
                    Select Existing
                </button>
                <button
                    type="button"
                    onClick={() => setMode('create')}
                    className={`flex-1 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${mode === 'create'
                        ? 'bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300'
                        : 'bg-gray-100 dark:bg-slate-800 text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-slate-700'
                        }`}
                >
                    Create New
                </button>
            </div>

            {mode === 'select' ? (
                <div className="space-y-4">
                    {/* Search */}
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                        <Input
                            type="text"
                            placeholder="Search agents..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="pl-10"
                        />
                    </div>

                    {/* Agent List */}
                    <div className="max-h-80 overflow-y-auto border border-gray-200 dark:border-slate-700 rounded-lg">
                        {agentsLoading ? (
                            <div className="flex items-center justify-center py-8">
                                <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
                            </div>
                        ) : availableAgents.length === 0 ? (
                            <div className="flex flex-col items-center justify-center py-8 text-center">
                                <Bot className="w-12 h-12 text-gray-300 dark:text-gray-600 mb-2" />
                                <p className="text-sm text-gray-500 dark:text-gray-400">
                                    {searchQuery
                                        ? 'No agents match your search'
                                        : 'No available agents to assign'}
                                </p>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="mt-2"
                                    onClick={() => setMode('create')}
                                >
                                    Create a new agent
                                </Button>
                            </div>
                        ) : (
                            <div className="divide-y divide-gray-200 dark:divide-slate-700">
                                {availableAgents.map((agent) => (
                                    <button
                                        key={agent.id}
                                        type="button"
                                        onClick={() => setSelectedAgentId(agent.id)}
                                        className={`w-full flex items-center justify-between p-3 hover:bg-gray-50 dark:hover:bg-slate-800 transition-colors ${selectedAgentId === agent.id
                                            ? 'bg-blue-50 dark:bg-blue-900/20'
                                            : ''
                                            }`}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div
                                                className={`w-2 h-2 rounded-full ${AGENT_STATUS_COLORS[agent.status] || 'bg-slate-400'
                                                    }`}
                                            />
                                            <div className="text-left">
                                                <div className="font-medium text-gray-900 dark:text-white">
                                                    {agent.name}
                                                </div>
                                                <div className="text-xs text-gray-500 dark:text-gray-400">
                                                    {typeof agent.role === 'string'
                                                        ? agent.role
                                                        : (agent.role as any)?.name || 'No role'}
                                                </div>
                                            </div>
                                        </div>
                                        {selectedAgentId === agent.id && (
                                            <Check className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                                        )}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Error */}
                    {errors.select && (
                        <div className="flex items-center gap-2 text-sm text-red-600 dark:text-red-400">
                            <AlertCircle className="w-4 h-4" />
                            {errors.select}
                        </div>
                    )}

                    {/* Actions */}
                    <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <Button
                            type="button"
                            variant="ghost"
                            onClick={handleClose}
                            disabled={addAgentMutation.isPending}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSelectAgent}
                            disabled={!selectedAgentId || addAgentMutation.isPending}
                            className="flex items-center gap-2"
                        >
                            {addAgentMutation.isPending ? (
                                <>
                                    <Loader2 className="w-4 h-4 animate-spin" />
                                    Adding...
                                </>
                            ) : (
                                <>
                                    <UserPlus className="w-4 h-4" />
                                    Add to Department
                                </>
                            )}
                        </Button>
                    </div>
                </div>
            ) : (
                <form onSubmit={handleCreateAgent} className="space-y-6">
                    {/* Header */}
                    <div className="flex items-center gap-3 p-4 bg-emerald-50 dark:bg-emerald-900/20 rounded-lg">
                        <div className="p-3 bg-emerald-100 dark:bg-emerald-900/50 rounded-lg">
                            <Bot className="w-6 h-6 text-emerald-600 dark:text-emerald-400" />
                        </div>
                        <div>
                            <h3 className="font-medium text-gray-900 dark:text-white">
                                New Agent
                            </h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                Create and assign to {departmentName}
                            </p>
                        </div>
                    </div>

                    {/* Form Fields */}
                    <FormField
                        label="Agent Name"
                        name="agentName"
                        required
                        error={errors.name}
                        helpText="A descriptive name for the agent"
                    >
                        <Input
                            id="agentName"
                            name="agentName"
                            type="text"
                            placeholder="e.g., Build Automation Agent"
                            value={newAgentForm.name}
                            onChange={(e) =>
                                setNewAgentForm((prev) => ({ ...prev, name: e.target.value }))
                            }
                            error={!!errors.name}
                            autoFocus
                        />
                    </FormField>

                    <FormField
                        label="Agent Role"
                        name="agentRole"
                        required
                        error={errors.role}
                        helpText="Primary function of this agent"
                    >
                        <Select
                            id="agentRole"
                            name="agentRole"
                            value={newAgentForm.role}
                            onChange={(e) =>
                                setNewAgentForm((prev) => ({ ...prev, role: e.target.value }))
                            }
                            options={AGENT_ROLES}
                            error={!!errors.role}
                        />
                    </FormField>

                    <FormField
                        label="Description"
                        name="agentDescription"
                        helpText="What does this agent do?"
                    >
                        <Textarea
                            id="agentDescription"
                            name="agentDescription"
                            placeholder="Describe the agent's responsibilities..."
                            value={newAgentForm.description}
                            onChange={(e) =>
                                setNewAgentForm((prev) => ({ ...prev, description: e.target.value }))
                            }
                            rows={3}
                        />
                    </FormField>

                    {/* Error */}
                    {errors.submit && (
                        <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                            <p className="text-sm text-red-600 dark:text-red-400">{errors.submit}</p>
                        </div>
                    )}

                    {/* Actions */}
                    <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                        <Button
                            type="button"
                            variant="ghost"
                            onClick={handleClose}
                            disabled={addAgentMutation.isPending}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="submit"
                            disabled={addAgentMutation.isPending}
                            className="flex items-center gap-2"
                        >
                            {addAgentMutation.isPending ? (
                                <>
                                    <Loader2 className="w-4 h-4 animate-spin" />
                                    Creating...
                                </>
                            ) : (
                                <>
                                    <Bot className="w-4 h-4" />
                                    Create Agent
                                </>
                            )}
                        </Button>
                    </div>
                </form>
            )}
        </Drawer>
    );
}

export default AddAgentToDeptModal;
