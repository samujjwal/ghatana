import { useNavigate, useParams } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useAgent, useActivateAgent } from '@/hooks/useBuildApi';
import { Badge } from "@/components/ui";
import { StatusBadge } from '@/shared/components';

/**
 * Agent Detail Page
 *
 * <p><b>Purpose</b><br>
 * Detailed view of a single agent showing configuration, persona binding,
 * tools, guardrails, and linked services. Allows editing and activation.
 *
 * <p><b>Features</b><br>
 * - Full agent metadata (name, description, status, type)
 * - Persona binding details
 * - Tools list with descriptions
 * - Guardrails configuration display
 * - Linked services
 * - Actions: Edit, Activate, Test
 *
 * @doc.type component
 * @doc.purpose Agent detail and management
 * @doc.layer product
 * @doc.pattern Page
 */
export function AgentDetail() {
    const navigate = useNavigate();
    const { agentId } = useParams<{ agentId: string }>();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const { data: agentData, isLoading, error } = useAgent(agentId!, tenantId);
    const activateMutation = useActivateAgent(agentId!, tenantId);

    if (isLoading) {
        return (
            <div className="p-6">
                <div className="text-slate-600 dark:text-neutral-400">Loading agent...</div>
            </div>
        );
    }

    if (error || !agentData) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load agent: {error?.message || 'Not found'}
                </div>
            </div>
        );
    }

    const agent = agentData;

    const handleEdit = () => {
        navigate(`/build/agents/${agentId}/edit`);
    };

    const handleActivate = async () => {
        try {
            await activateMutation.mutateAsync();
            // Success handled by mutation (cache invalidation)
        } catch (err) {
            console.error('Failed to activate agent:', err);
        }
    };

    const handleTest = () => {
        navigate(`/build/simulator?agentId=${agentId}`);
    };

    const handleBack = () => {
        navigate('/build/agents');
    };

    return (
        <div className="p-6 space-y-6">
            {/* Breadcrumb / Back Navigation */}
            <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400">
                <button 
                    onClick={handleBack}
                    className="hover:text-slate-900 dark:hover:text-neutral-100 transition-colors"
                >
                    ← Agents
                </button>
                <span>/</span>
                <span className="text-slate-900 dark:text-neutral-100">{agent.name}</span>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Content */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Header */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <div className="flex items-start justify-between mb-4">
                            <div>
                                <h1 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                                    {agent.name}
                                </h1>
                                <p className="text-slate-600 dark:text-neutral-400 text-sm">
                                    {agent.description || 'No description provided'}
                                </p>
                            </div>
                            <StatusBadge status={agent.status} />
                        </div>
                        <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                                <span className="text-slate-500 dark:text-neutral-500">Slug:</span>
                                <span className="ml-2 font-mono text-slate-700 dark:text-neutral-300">{agent.slug}</span>
                            </div>
                            <div>
                                <span className="text-slate-500 dark:text-neutral-500">Type:</span>
                                <span className="ml-2 text-slate-700 dark:text-neutral-300 capitalize">{agent.type}</span>
                            </div>
                            <div>
                                <span className="text-slate-500 dark:text-neutral-500">Owner Team:</span>
                                <span className="ml-2 text-slate-700 dark:text-neutral-300">{agent.ownerTeamId || 'Unassigned'}</span>
                            </div>
                            <div>
                                <span className="text-slate-500 dark:text-neutral-500">Created:</span>
                                <span className="ml-2 text-slate-700 dark:text-neutral-300">
                                    {new Date(agent.createdAt).toLocaleDateString()}
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* Persona Binding */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Persona Binding
                        </h2>
                        {agent.personaId ? (
                            <div className="space-y-2">
                                <div className="flex items-center gap-2">
                                    <Badge variant="primary">Persona</Badge>
                                    <span className="text-sm font-mono text-slate-700 dark:text-neutral-300">
                                        {agent.personaId}
                                    </span>
                                </div>
                                <p className="text-sm text-slate-600 dark:text-neutral-400">
                                    This agent inherits capabilities and context from the bound persona.
                                </p>
                            </div>
                        ) : (
                            <p className="text-sm text-slate-500 dark:text-neutral-500">
                                No persona bound to this agent.
                            </p>
                        )}
                    </div>

                    {/* Tools */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Tools ({agent.tools.length})
                        </h2>
                        {agent.tools.length > 0 ? (
                            <div className="space-y-3">
                                {agent.tools.map((tool, index) => (
                                    <div 
                                        key={index}
                                        className="border border-slate-200 dark:border-slate-700 rounded-md p-3 bg-slate-50 dark:bg-slate-800"
                                    >
                                        <div className="flex items-center gap-2 mb-1">
                                            <Badge variant="neutral">
                                                {typeof tool === 'string' ? tool : (tool as {name?: string})?.name || 'Tool'}
                                            </Badge>
                                        </div>
                                        {typeof tool === 'object' && (tool as {description?: string})?.description && (
                                            <p className="text-xs text-slate-600 dark:text-neutral-400">
                                                {(tool as {description?: string}).description}
                                            </p>
                                        )}
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <p className="text-sm text-slate-500 dark:text-neutral-500">
                                No tools configured for this agent.
                            </p>
                        )}
                    </div>

                    {/* Guardrails */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Guardrails Configuration
                        </h2>
                        {agent.guardrails && Object.keys(agent.guardrails).length > 0 ? (
                            <div className="space-y-3">
                                <pre className="bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md p-3 text-xs font-mono text-slate-700 dark:text-neutral-300 overflow-x-auto">
{JSON.stringify(agent.guardrails, null, 2)}
                                </pre>
                                <p className="text-xs text-slate-500 dark:text-neutral-500">
                                    Guardrails define boundaries and safety constraints for agent actions.
                                </p>
                            </div>
                        ) : (
                            <p className="text-sm text-slate-500 dark:text-neutral-500">
                                No guardrails configured for this agent.
                            </p>
                        )}
                    </div>
                </div>

                {/* Sidebar */}
                <div className="space-y-6">
                    {/* Actions */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Actions
                        </h2>
                        <div className="space-y-3">
                            <button
                                onClick={handleEdit}
                                className="w-full px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors text-sm font-medium"
                            >
                                Edit Agent
                            </button>
                            {agent.status === 'draft' && (
                                <button
                                    onClick={handleActivate}
                                    disabled={activateMutation.isPending}
                                    className="w-full px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium"
                                >
                                    {activateMutation.isPending ? 'Activating...' : 'Activate Agent'}
                                </button>
                            )}
                            <button
                                onClick={handleTest}
                                className="w-full px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-sm font-medium"
                            >
                                Test in Simulator
                            </button>
                        </div>
                    </div>

                    {/* Metadata */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Metadata
                        </h2>
                        <div className="space-y-3 text-sm">
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500 mb-1">ID</div>
                                <div className="font-mono text-xs text-slate-700 dark:text-neutral-300 break-all">
                                    {agent.id}
                                </div>
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500 mb-1">Last Updated</div>
                                <div className="text-slate-700 dark:text-neutral-300">
                                    {new Date(agent.updatedAt).toLocaleString()}
                                </div>
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500 mb-1">Version</div>
                                <div className="text-slate-700 dark:text-neutral-300">
                                    {agent.version || 'v1.0.0'}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Linked Services */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Linked Services ({agent.services?.length || 0})
                        </h2>
                        {agent.services && agent.services.length > 0 ? (
                            <div className="space-y-2">
                                {agent.services.map((service) => (
                                    <div 
                                        key={service.id}
                                        className="flex items-center gap-2 text-sm"
                                    >
                                        <div className="w-2 h-2 rounded-full bg-blue-500"></div>
                                        <span className="text-slate-700 dark:text-neutral-300">{service.name}</span>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <p className="text-sm text-slate-500 dark:text-neutral-500">
                                No services linked to this agent.
                            </p>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
