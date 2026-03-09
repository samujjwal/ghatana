import { useState } from "react";
import { useNavigate } from "react-router";
import { Badge } from "@/components/ui";
import { StatusBadge } from '@/shared/components';
import { useAgents } from '@/hooks/useBuildApi';
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { usePermissions } from '@/hooks/usePermissions';

/**
 * Agent Explorer
 *
 * <p><b>Purpose</b><br>
 * Full-featured agent dashboard showing active AI agents, configuration,
 * and status. Displays agents from Build API with filtering and management actions.
 *
 * <p><b>Features</b><br>
 * - Agent list with status and metadata
 * - Filter by status (draft/active/disabled)
 * - View agent details (type, persona, tools, guardrails)
 * - Quick actions (Activate, Edit, Test)
 *
 * @doc.type component
 * @doc.purpose Agent management and monitoring
 * @doc.layer product
 * @doc.pattern Page
 */
export function AgentExplorer() {
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const { canCreateAgent, canEditAgent, canExecuteAgent } = usePermissions();
    const [filterStatus, setFilterStatus] = useState<string>('all');
    const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null);
    
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const { data: agentsData, isLoading, error } = useAgents(
        tenantId,
        filterStatus === 'all' ? undefined : filterStatus
    );

    const agents = agentsData?.data || [];
    const selectedAgent = agents.find((a) => a.id === selectedAgentId);

    const stats = {
        total: agents.length,
        active: agents.filter((a) => a.status === 'active').length,
        draft: agents.filter((a) => a.status === 'draft').length,
        disabled: agents.filter((a) => a.status === 'disabled').length,
    };

    if (error) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load agents: {error.message}
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col md:flex-row md:justify-between md:items-start gap-4">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">AI Agents</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Configure AI agents for incident triage, deployment advice, and automation
                    </p>
                </div>
                <button
                    onClick={() => navigate('/build/agents/new')}
                    disabled={!canCreateAgent}
                    title={!canCreateAgent ? 'You do not have permission to create agents' : undefined}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-blue-600"
                >
                    <span>+</span>
                    <span>New Agent</span>
                </button>
            </div>

            {/* Stats Bar */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard label="Total Agents" value={stats.total} icon="🤖" />
                <StatCard label="Active" value={stats.active} icon="✅" />
                <StatCard label="Draft" value={stats.draft} icon="📝" />
                <StatCard label="Disabled" value={stats.disabled} icon="⏸️" />
            </div>

            {/* Filters */}
            <div className="flex gap-2">
                <button
                    onClick={() => setFilterStatus('all')}
                    className={`px-4 py-2 rounded text-sm ${
                        filterStatus === 'all'
                            ? 'bg-blue-600 text-white'
                            : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300'
                    }`}
                >
                    All
                </button>
                <button
                    onClick={() => setFilterStatus('active')}
                    className={`px-4 py-2 rounded text-sm ${
                        filterStatus === 'active'
                            ? 'bg-green-600 text-white'
                            : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300'
                    }`}
                >
                    Active
                </button>
                <button
                    onClick={() => setFilterStatus('draft')}
                    className={`px-4 py-2 rounded text-sm ${
                        filterStatus === 'draft'
                            ? 'bg-yellow-600 text-white'
                            : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300'
                    }`}
                >
                    Draft
                </button>
                <button
                    onClick={() => setFilterStatus('disabled')}
                    className={`px-4 py-2 rounded text-sm ${
                        filterStatus === 'disabled'
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
                    Loading agents...
                </div>
            )}

            {/* Empty State */}
            {!isLoading && agents.length === 0 && (
                <div className="text-center py-12 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg">
                    <div className="text-4xl mb-4">🤖</div>
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                        No agents found
                    </h3>
                    <p className="text-slate-600 dark:text-neutral-400 mb-4">
                        {filterStatus === 'all'
                            ? 'Get started by creating your first AI agent'
                            : `No ${filterStatus} agents`}
                    </p>
                    {filterStatus === 'all' && canCreateAgent && (
                        <button
                            onClick={() => navigate('/build/agents/new')}
                            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                        >
                            Create Agent
                        </button>
                    )}
                </div>
            )}

            {/* Main Grid */}
            {!isLoading && agents.length > 0 && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Agent List */}
                    <div className="lg:col-span-2 space-y-3">
                        {agents.map((agent) => (
                            <div
                                key={agent.id}
                                onClick={() => setSelectedAgentId(agent.id)}
                                className={`p-4 border rounded-lg cursor-pointer transition ${
                                    selectedAgentId === agent.id
                                        ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30'
                                        : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-slate-300'
                                }`}
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex-1">
                                        <div className="font-semibold text-slate-900 dark:text-neutral-100">
                                            {agent.name}
                                        </div>
                                        {agent.description && (
                                            <div className="text-sm text-slate-500 dark:text-neutral-400 mt-1">
                                                {agent.description}
                                            </div>
                                        )}
                                    </div>
                                    <Badge
                                        tone={
                                            agent.status === 'active'
                                                ? 'positive'
                                                : agent.status === 'draft'
                                                ? 'warning'
                                                : 'neutral'
                                        }
                                        variant="neutral"
                                    >
                                        {agent.status}
                                    </Badge>
                                </div>
                                <div className="grid grid-cols-3 gap-3 mt-3 text-xs">
                                    <div>
                                        <div className="text-slate-500 dark:text-neutral-500">Type</div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {agent.type}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-slate-500 dark:text-neutral-500">Tools</div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {agent.tools.length}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-slate-500 dark:text-neutral-500">Services</div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {agent.serviceIds.length}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>

                    {/* Agent Details */}
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                        {selectedAgent ? (
                            <div className="space-y-4">
                                <div>
                                    <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {selectedAgent.name}
                                    </h3>
                                    {selectedAgent.description && (
                                        <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                            {selectedAgent.description}
                                        </p>
                                    )}
                                </div>
                                <div className="border-t border-slate-200 dark:border-slate-800 pt-4 space-y-3">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Status</span>
                                        <StatusBadge status={selectedAgent.status} />
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Agent ID</span>
                                        <span className="font-mono text-xs text-slate-900 dark:text-neutral-100">
                                            {selectedAgent.slug}
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Type</span>
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">
                                            {selectedAgent.type}
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Tools</span>
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">
                                            {selectedAgent.tools.length}
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-slate-600 dark:text-neutral-400">Services</span>
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">
                                            {selectedAgent.serviceIds.length}
                                        </span>
                                    </div>
                                </div>
                                <div className="flex flex-col gap-2 pt-4 border-t border-slate-200 dark:border-slate-800">
                                    <button
                                        onClick={() => navigate(`/build/agents/${selectedAgent.id}`)}
                                        className="w-full px-3 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 transition-colors"
                                    >
                                        View Details
                                    </button>
                                    {canEditAgent && (
                                        <button
                                            onClick={() => navigate(`/build/agents/${selectedAgent.id}/edit`)}
                                            className="w-full px-3 py-2 bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 rounded text-sm hover:bg-slate-300 dark:hover:bg-slate-700 transition-colors"
                                        >
                                            Edit Agent
                                        </button>
                                    )}
                                    {canExecuteAgent && selectedAgent.status === 'draft' && (
                                        <button
                                            onClick={() => {
                                                /* TODO: Implement activate */
                                            }}
                                            className="w-full px-3 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700 transition-colors"
                                        >
                                            Activate
                                        </button>
                                    )}
                                    {!canEditAgent && (
                                        <div className="text-center text-sm text-slate-500 dark:text-neutral-400 py-2">
                                            View-only access
                                        </div>
                                    )}
                                </div>
                            </div>
                        ) : (
                            <div className="text-center text-slate-500 dark:text-neutral-400 py-8">
                                Select an agent to view details
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

// Helper component
function StatCard({ label, value, icon }: { label: string; value: number; icon: string }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center gap-3">
                <div className="text-2xl">{icon}</div>
                <div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
                    <div className="text-sm text-slate-600 dark:text-neutral-400">{label}</div>
                </div>
            </div>
        </div>
    );
}
