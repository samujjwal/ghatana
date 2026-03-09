/**
 * Enhanced Brain Dashboard Page
 *
 * This version integrates @ghatana/agent-framework shared components
 * with Data-Cloud specific hooks via the integration layer.
 *
 * Features:
 * - AgentDashboard for Brain agents visualization
 * - AgentInterventionConsole for human-in-the-loop
 * - AgentInteractionNetwork for agent communication
 * - Real-time streaming via integration hooks
 *
 * @doc.type page
 * @doc.purpose Glass Box Brain dashboard with shared components
 * @doc.layer frontend
 */

import React, { useState, useMemo } from 'react';
import { Brain, Activity, Zap, TrendingUp, Users, Bell } from 'lucide-react';
import { DashboardKPI } from '../components/cards/DashboardCard';
import {
    useBrainAgents,
    useBrainInterventions,
    useBrainMemory,
    useBrainInteractions,
    type BrainAgent,
} from '../lib/integrations/agent-integration';
import {
    useBrainStateStream,
} from '../lib/integrations/realtime-integration';
import {
    useDataCloudMetrics,
    useSystemHealth,
} from '../lib/integrations/visualization-integration';

type TabType = 'overview' | 'agents' | 'interventions' | 'network' | 'memory';

/** @deprecated Use InsightsPage instead. Routes now redirect /brain → InsightsPage. */
export function EnhancedBrainDashboardPage() {
    const [activeTab, setActiveTab] = useState<TabType>('overview');
    const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null);

    // Integration hooks - using correct destructuring
    const {
        brainAgents,
        isLoading: agentsLoading,
        stats: agentStats,
        pauseAgent,
        resumeAgent,
        terminateAgent,
    } = useBrainAgents();

    const {
        brainInterventions,
        isLoading: interventionsLoading,
        stats: interventionStats,
        approveIntervention,
        rejectIntervention,
    } = useBrainInterventions();

    const { memory, isLoading: memoryLoading } = useBrainMemory(selectedAgentId ?? undefined);

    const { agents: interactionAgents, interactions, isLoading: interactionsLoading } = useBrainInteractions();

    // Realtime streams
    const { updates, isConnected: brainConnected } = useBrainStateStream();

    // Metrics
    const { chartData: metricsData, isLoading: metricsLoading } = useDataCloudMetrics({
        metricIds: ['brain.decisions', 'brain.confidence', 'brain.latency', 'brain.throughput'],
        timeRange: { preset: 'last1h' },
    });

    const { health, isHealthy } = useSystemHealth();

    // Handlers
    const handleAgentAction = async (agentId: string, action: string) => {
        switch (action) {
            case 'pause':
                await pauseAgent(agentId);
                break;
            case 'resume':
                await resumeAgent(agentId);
                break;
            case 'terminate':
                await terminateAgent(agentId);
                break;
        }
    };

    const handleApproveIntervention = async (id: string, feedback?: string) => {
        await approveIntervention({ id, feedback });
    };

    const handleRejectIntervention = async (id: string, reason: string) => {
        await rejectIntervention({ id, reason });
    };

    const tabs: { id: TabType; label: string; icon: React.ReactNode; badge?: number }[] = [
        { id: 'overview', label: 'Overview', icon: <Brain className="h-4 w-4" /> },
        { id: 'agents', label: 'Agents', icon: <Users className="h-4 w-4" />, badge: agentStats.total },
        { id: 'interventions', label: 'Interventions', icon: <Bell className="h-4 w-4" />, badge: interventionStats.pending },
        { id: 'network', label: 'Network', icon: <Activity className="h-4 w-4" /> },
        { id: 'memory', label: 'Memory', icon: <Zap className="h-4 w-4" /> },
    ];

    // Calculate total entities from brain agents
    const totalEntities = useMemo(() =>
        brainAgents.reduce((sum, a) => sum + (a.entityCount ?? 0), 0),
        [brainAgents]);

    return (
        <div className="min-h-screen bg-gray-50">
            {/* Header */}
            <div className="bg-white border-b border-gray-200">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-gradient-to-br from-purple-500 to-blue-500 rounded-lg">
                                <Brain className="h-8 w-8 text-white" />
                            </div>
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900">Data Cloud Brain</h1>
                                <p className="text-sm text-gray-600 mt-1">
                                    Glass Box Intelligence with Shared Components
                                </p>
                            </div>
                        </div>
                        <div className="flex items-center gap-2">
                            <span className={`px-2 py-1 text-xs rounded-full ${brainConnected ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                                {brainConnected ? '● Live' : '○ Disconnected'}
                            </span>
                            <span className={`px-2 py-1 text-xs rounded-full ${isHealthy ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'}`}>
                                System: {health?.overall ?? 'Unknown'}
                            </span>
                        </div>
                    </div>

                    {/* Tab Navigation */}
                    <div className="mt-6 flex gap-1 border-b border-gray-200">
                        {tabs.map((tab) => (
                            <button
                                key={tab.id}
                                onClick={() => setActiveTab(tab.id)}
                                className={`flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === tab.id
                                        ? 'border-purple-500 text-purple-600'
                                        : 'border-transparent text-gray-500 hover:text-gray-700'
                                    }`}
                            >
                                {tab.icon}
                                {tab.label}
                                {tab.badge !== undefined && tab.badge > 0 && (
                                    <span className="ml-1 px-2 py-0.5 text-xs bg-purple-100 text-purple-600 rounded-full">
                                        {tab.badge}
                                    </span>
                                )}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {/* Main Content */}
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {/* Overview Tab */}
                {activeTab === 'overview' && (
                    <div className="space-y-8">
                        {/* KPI Cards */}
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                            <DashboardKPI
                                title="Active Agents"
                                value={agentStats.active}
                                icon={<Users className="h-6 w-6" />}
                                color="purple"
                            />
                            <DashboardKPI
                                title="Pending Interventions"
                                value={interventionStats.pending}
                                icon={<Bell className="h-6 w-6" />}
                                color={interventionStats.pending > 0 ? 'yellow' : 'green'}
                            />
                            <DashboardKPI
                                title="Avg Confidence"
                                value={`${(agentStats.avgConfidence * 100).toFixed(0)}%`}
                                icon={<TrendingUp className="h-6 w-6" />}
                                color="green"
                            />
                            <DashboardKPI
                                title="Entities Managed"
                                value={totalEntities.toLocaleString()}
                                icon={<Activity className="h-6 w-6" />}
                                color="blue"
                            />
                        </div>

                        {/* Metrics Dashboard */}
                        <div className="bg-white rounded-lg shadow p-6">
                            <h2 className="text-lg font-semibold text-gray-900 mb-4">Brain Metrics</h2>
                            {metricsLoading ? (
                                <div className="h-64 flex items-center justify-center">
                                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600" />
                                </div>
                            ) : (
                                <div className="grid grid-cols-2 gap-4">
                                    {metricsData.map((metric) => (
                                        <div key={metric.id} className="p-4 bg-gray-50 rounded-lg">
                                            <h3 className="text-sm font-medium text-gray-600">{metric.name}</h3>
                                            <p className="text-2xl font-bold text-gray-900 mt-1">
                                                {metric.data?.[metric.data.length - 1]?.value ?? 0} {metric.unit}
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* Brain State Stream */}
                        <div className="bg-white rounded-lg shadow p-6">
                            <h2 className="text-lg font-semibold text-gray-900 mb-4">Brain Activity Stream</h2>
                            <div className="space-y-2 max-h-64 overflow-y-auto">
                                {updates.slice(-20).map((update) => (
                                    <div key={update.id} className="flex items-center gap-3 p-2 bg-gray-50 rounded">
                                        <span className={`px-2 py-0.5 text-xs rounded ${update.type === 'decision' ? 'bg-purple-100 text-purple-700' :
                                                update.type === 'action' ? 'bg-blue-100 text-blue-700' :
                                                    update.type === 'alert' ? 'bg-yellow-100 text-yellow-700' :
                                                        'bg-gray-100 text-gray-700'
                                            }`}>
                                            {update.type}
                                        </span>
                                        <span className="text-sm text-gray-600">{update.subsystem}</span>
                                        <span className="text-sm text-gray-900">{update.payload?.title as string ?? 'Update'}</span>
                                        <span className="text-xs text-gray-400 ml-auto">
                                            {new Date(update.timestamp).toLocaleTimeString()}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                )}

                {/* Agents Tab */}
                {activeTab === 'agents' && (
                    <div className="bg-white rounded-lg shadow">
                        <div className="p-4 border-b border-gray-200">
                            <h2 className="text-lg font-semibold text-gray-900">Brain Agents</h2>
                        </div>
                        {agentsLoading ? (
                            <div className="p-8 flex items-center justify-center">
                                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600" />
                            </div>
                        ) : (
                            <div className="divide-y divide-gray-200">
                                {brainAgents.map((agent) => (
                                    <div key={agent.id} className="p-4 hover:bg-gray-50">
                                        <div className="flex items-center justify-between">
                                            <div className="flex items-center gap-3">
                                                <div className={`w-3 h-3 rounded-full ${agent.state === 'executing' || agent.state === 'thinking' ? 'bg-green-500' :
                                                        agent.state === 'paused' ? 'bg-yellow-500' :
                                                            agent.state === 'error' ? 'bg-red-500' :
                                                                'bg-gray-400'
                                                    }`} />
                                                <div>
                                                    <h3 className="font-medium text-gray-900">{agent.descriptor.name}</h3>
                                                    <p className="text-sm text-gray-500">{agent.subsystem} · {agent.state}</p>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-4">
                                                <div className="text-right">
                                                    <p className="text-sm font-medium text-gray-900">{(agent.confidence * 100).toFixed(0)}%</p>
                                                    <p className="text-xs text-gray-500">confidence</p>
                                                </div>
                                                <div className="text-right">
                                                    <p className="text-sm font-medium text-gray-900">{agent.entityCount}</p>
                                                    <p className="text-xs text-gray-500">entities</p>
                                                </div>
                                                <div className="flex gap-1">
                                                    {agent.state !== 'paused' && (
                                                        <button
                                                            onClick={() => handleAgentAction(agent.id, 'pause')}
                                                            className="px-2 py-1 text-xs bg-yellow-100 text-yellow-700 rounded hover:bg-yellow-200"
                                                        >
                                                            Pause
                                                        </button>
                                                    )}
                                                    {agent.state === 'paused' && (
                                                        <button
                                                            onClick={() => handleAgentAction(agent.id, 'resume')}
                                                            className="px-2 py-1 text-xs bg-green-100 text-green-700 rounded hover:bg-green-200"
                                                        >
                                                            Resume
                                                        </button>
                                                    )}
                                                    <button
                                                        onClick={() => {
                                                            setSelectedAgentId(agent.id);
                                                            setActiveTab('memory');
                                                        }}
                                                        className="px-2 py-1 text-xs bg-purple-100 text-purple-700 rounded hover:bg-purple-200"
                                                    >
                                                        Memory
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Interventions Tab */}
                {activeTab === 'interventions' && (
                    <div className="bg-white rounded-lg shadow">
                        <div className="p-4 border-b border-gray-200">
                            <h2 className="text-lg font-semibold text-gray-900">Pending Interventions</h2>
                        </div>
                        {interventionsLoading ? (
                            <div className="p-8 flex items-center justify-center">
                                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600" />
                            </div>
                        ) : brainInterventions.length === 0 ? (
                            <div className="p-8 text-center text-gray-500">
                                <Bell className="h-12 w-12 mx-auto mb-4 opacity-50" />
                                <p>No pending interventions</p>
                            </div>
                        ) : (
                            <div className="divide-y divide-gray-200">
                                {brainInterventions.filter(i => i.status === 'pending').map((intervention) => (
                                    <div key={intervention.id} className="p-4">
                                        <div className="flex items-start justify-between">
                                            <div className="flex-1">
                                                <div className="flex items-center gap-2">
                                                    <span className={`px-2 py-0.5 text-xs rounded ${intervention.priority === 'critical' ? 'bg-red-100 text-red-700' :
                                                            intervention.priority === 'high' ? 'bg-orange-100 text-orange-700' :
                                                                'bg-blue-100 text-blue-700'
                                                        }`}>
                                                        {intervention.priority}
                                                    </span>
                                                    <h3 className="font-medium text-gray-900">{intervention.title}</h3>
                                                </div>
                                                <p className="text-sm text-gray-600 mt-1">{intervention.description}</p>
                                                {intervention.affectedEntities && intervention.affectedEntities.length > 0 && (
                                                    <p className="text-xs text-gray-500 mt-2">
                                                        Affects {intervention.affectedEntities.length} entities
                                                    </p>
                                                )}
                                            </div>
                                            <div className="flex gap-2 ml-4">
                                                <button
                                                    onClick={() => handleRejectIntervention(intervention.id, 'Rejected by user')}
                                                    className="px-3 py-1 text-sm bg-red-100 text-red-700 rounded hover:bg-red-200"
                                                >
                                                    Reject
                                                </button>
                                                <button
                                                    onClick={() => handleApproveIntervention(intervention.id)}
                                                    className="px-3 py-1 text-sm bg-green-100 text-green-700 rounded hover:bg-green-200"
                                                >
                                                    Approve
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Network Tab */}
                {activeTab === 'network' && (
                    <div className="bg-white rounded-lg shadow p-6">
                        <h2 className="text-lg font-semibold text-gray-900 mb-4">Agent Interaction Network</h2>
                        {interactionsLoading ? (
                            <div className="h-96 flex items-center justify-center">
                                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600" />
                            </div>
                        ) : (
                            <div className="h-96 bg-gray-50 rounded-lg flex items-center justify-center">
                                <div className="text-center text-gray-500">
                                    <Activity className="h-16 w-16 mx-auto mb-4 opacity-50" />
                                    <p className="text-lg font-medium">Network Visualization</p>
                                    <p className="text-sm mt-2">
                                        {interactionAgents.length} agents · {interactions.length} interactions
                                    </p>
                                    <div className="mt-4 flex flex-wrap gap-2 justify-center">
                                        {interactionAgents.slice(0, 5).map((a) => (
                                            <span key={a.id} className="px-2 py-1 bg-purple-100 text-purple-700 rounded text-sm">
                                                {a.descriptor.name}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* Memory Tab */}
                {activeTab === 'memory' && (
                    <div className="bg-white rounded-lg shadow">
                        {selectedAgentId ? (
                            <>
                                <div className="p-4 border-b border-gray-200">
                                    <div className="flex items-center justify-between">
                                        <h2 className="text-lg font-semibold text-gray-900">
                                            Agent Memory: {brainAgents.find(a => a.id === selectedAgentId)?.descriptor.name ?? selectedAgentId}
                                        </h2>
                                        <select
                                            value={selectedAgentId}
                                            onChange={(e) => setSelectedAgentId(e.target.value)}
                                            className="px-3 py-2 border border-gray-300 rounded-md text-sm"
                                        >
                                            {brainAgents.map((a) => (
                                                <option key={a.id} value={a.id}>
                                                    {a.descriptor.name}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                </div>
                                <div className="p-4">
                                    {memoryLoading ? (
                                        <div className="flex items-center justify-center py-8">
                                            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600" />
                                        </div>
                                    ) : (
                                        <div className="space-y-4">
                                            <div>
                                                <h3 className="text-sm font-medium text-gray-700 mb-2">Short-Term Memory ({memory.shortTerm.length})</h3>
                                                <div className="space-y-1">
                                                    {memory.shortTerm.slice(0, 5).map((entry) => (
                                                        <div key={entry.id} className="p-2 bg-purple-50 rounded text-sm">
                                                            <span className="font-medium">{entry.key}:</span> {JSON.stringify(entry.value).slice(0, 100)}
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                            <div>
                                                <h3 className="text-sm font-medium text-gray-700 mb-2">Long-Term Memory ({memory.longTerm.length})</h3>
                                                <div className="space-y-1">
                                                    {memory.longTerm.slice(0, 5).map((entry) => (
                                                        <div key={entry.id} className="p-2 bg-blue-50 rounded text-sm">
                                                            <span className="font-medium">{entry.key}:</span> {JSON.stringify(entry.value).slice(0, 100)}
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                            <div>
                                                <h3 className="text-sm font-medium text-gray-700 mb-2">Working Memory ({memory.working.length})</h3>
                                                <div className="space-y-1">
                                                    {memory.working.slice(0, 5).map((entry) => (
                                                        <div key={entry.id} className="p-2 bg-green-50 rounded text-sm">
                                                            <span className="font-medium">{entry.key}:</span> {JSON.stringify(entry.value).slice(0, 100)}
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </>
                        ) : (
                            <div className="p-8 text-center text-gray-500">
                                <Brain className="h-12 w-12 mx-auto mb-4 opacity-50" />
                                <p>Select an agent to view its memory</p>
                                <button
                                    onClick={() => setActiveTab('agents')}
                                    className="mt-4 px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700"
                                >
                                    Go to Agents
                                </button>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

export default EnhancedBrainDashboardPage;
