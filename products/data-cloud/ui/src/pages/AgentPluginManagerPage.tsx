/**
 * Agent Plugin Manager Page
 *
 * Displays all registered agents with live monitoring feed.
 * Supports agent registration, deregistration, capability management,
 * and real-time registry event streaming via SSE.
 *
 * @doc.type page
 * @doc.purpose Agent Registry management interface
 * @doc.layer frontend
 * @doc.pattern Page Component
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Bot,
  Plus,
  Trash2,
  RefreshCw,
  Activity,
  CheckCircle,
  XCircle,
  AlertCircle,
  Clock,
  Radio,
  ChevronDown,
  ChevronRight,
  X,
} from 'lucide-react';
import {
  agentRegistryService,
  type AgentDefinition,
  type AgentRegistrationRequest,
  type AgentStatus,
  type RegistryEvent,
} from '../api/agent-registry.service';

// =============================================================================
// Status helpers
// =============================================================================

function statusIcon(status: AgentStatus): React.ReactElement {
  switch (status) {
    case 'ACTIVE':
      return <CheckCircle className="h-4 w-4 text-green-500" />;
    case 'ERROR':
      return <XCircle className="h-4 w-4 text-red-500" />;
    case 'REGISTERING':
    case 'DEREGISTERING':
      return <Clock className="h-4 w-4 text-yellow-500" />;
    default:
      return <AlertCircle className="h-4 w-4 text-gray-400" />;
  }
}

function statusBadge(status: AgentStatus): string {
  const base = 'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium';
  switch (status) {
    case 'ACTIVE':
      return `${base} bg-green-100 text-green-700`;
    case 'ERROR':
      return `${base} bg-red-100 text-red-700`;
    case 'REGISTERING':
    case 'DEREGISTERING':
      return `${base} bg-yellow-100 text-yellow-700`;
    default:
      return `${base} bg-gray-100 text-gray-600`;
  }
}

function eventTypeBadge(type: RegistryEvent['eventType']): string {
  const base = 'px-2 py-0.5 rounded text-xs font-mono font-medium';
  switch (type) {
    case 'AGENT_REGISTERED':
      return `${base} bg-blue-100 text-blue-700`;
    case 'AGENT_DEREGISTERED':
      return `${base} bg-red-100 text-red-700`;
    case 'AGENT_STATUS_CHANGED':
      return `${base} bg-yellow-100 text-yellow-700`;
    case 'EXECUTION_STARTED':
      return `${base} bg-purple-100 text-purple-700`;
    case 'EXECUTION_COMPLETED':
      return `${base} bg-green-100 text-green-700`;
    default:
      return `${base} bg-gray-100 text-gray-600`;
  }
}

// =============================================================================
// Agent Card
// =============================================================================

interface AgentCardProps {
  agent: AgentDefinition;
  onDeregister: (id: string) => void;
  deregistering: boolean;
}

function AgentCard({ agent, onDeregister, deregistering }: AgentCardProps): React.ReactElement {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-sm hover:shadow-md transition-shadow">
      <div className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="p-2 bg-indigo-50 rounded-lg flex-shrink-0">
              <Bot className="h-5 w-5 text-indigo-600" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h3 className="text-sm font-semibold text-gray-900 truncate">
                  {agent.name}
                </h3>
                <span className="text-xs text-gray-400 font-mono">v{agent.version}</span>
              </div>
              <p className="text-xs text-gray-500 mt-0.5 truncate">{agent.description}</p>
            </div>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <span className={statusBadge(agent.status)}>
              {statusIcon(agent.status)}
              {agent.status}
            </span>
            <button
              onClick={() => onDeregister(agent.agentId)}
              disabled={deregistering}
              className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors disabled:opacity-50"
              title="Deregister agent"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>

        <div className="mt-2 flex items-center gap-4 text-xs text-gray-500">
          <span>ID: <span className="font-mono">{agent.agentId.slice(0, 8)}…</span></span>
          <span>{agent.capabilities.length} capabilities</span>
          <span>Registered {new Date(agent.registeredAt).toLocaleDateString()}</span>
        </div>
      </div>

      {agent.capabilities.length > 0 && (
        <div className="border-t border-gray-100">
          <button
            onClick={() => setExpanded(!expanded)}
            className="w-full px-4 py-2 flex items-center gap-1.5 text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-50 transition-colors"
          >
            {expanded ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
            Capabilities ({agent.capabilities.length})
          </button>
          {expanded && (
            <div className="px-4 pb-3 space-y-1.5">
              {agent.capabilities.map((cap) => (
                <div key={cap.id} className="text-xs bg-gray-50 rounded p-2">
                  <div className="font-medium text-gray-700">{cap.name}
                    <span className="ml-1 font-normal text-gray-400 font-mono">v{cap.version}</span>
                  </div>
                  <div className="text-gray-500 mt-0.5">{cap.description}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// =============================================================================
// Registration Modal
// =============================================================================

interface RegistrationModalProps {
  onClose: () => void;
  onSubmit: (request: AgentRegistrationRequest) => void;
  submitting: boolean;
}

function AgentRegistrationModal({
  onClose,
  onSubmit,
  submitting,
}: RegistrationModalProps): React.ReactElement {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [version, setVersion] = useState('1.0.0');
  const [endpoint, setEndpoint] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      name: name.trim(),
      description: description.trim(),
      version: version.trim(),
      capabilities: [],
      endpoint: endpoint.trim() || undefined,
    });
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-base font-semibold text-gray-900">Register Agent</h2>
          <button
            onClick={onClose}
            className="p-1 text-gray-400 hover:text-gray-600 rounded transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Agent Name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="My Agent"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="What does this agent do?"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Version</label>
              <input
                type="text"
                value={version}
                onChange={(e) => setVersion(e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="1.0.0"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Endpoint</label>
              <input
                type="url"
                value={endpoint}
                onChange={(e) => setEndpoint(e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="https://…"
              />
            </div>
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !name.trim()}
              className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors disabled:opacity-50"
            >
              {submitting ? 'Registering…' : 'Register Agent'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// =============================================================================
// Registry Events Feed
// =============================================================================

interface RegistryEventsFeedProps {
  events: RegistryEvent[];
  connected: boolean;
}

function RegistryEventsFeed({ events, connected }: RegistryEventsFeedProps): React.ReactElement {
  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-sm h-full flex flex-col">
      <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Radio className="h-4 w-4 text-purple-500" />
          <span className="text-sm font-medium text-gray-900">Live Registry Events</span>
        </div>
        <span className={`flex items-center gap-1.5 text-xs font-medium ${connected ? 'text-green-600' : 'text-gray-400'}`}>
          <span className={`h-2 w-2 rounded-full ${connected ? 'bg-green-500 animate-pulse' : 'bg-gray-300'}`} />
          {connected ? 'Connected' : 'Disconnected'}
        </span>
      </div>
      <div className="flex-1 overflow-y-auto p-3 space-y-2 font-mono text-xs">
        {events.length === 0 && (
          <div className="text-gray-400 text-center py-8 font-sans text-sm">
            Waiting for registry events…
          </div>
        )}
        {events.map((event) => (
          <div key={event.id} className="flex items-start gap-2 bg-gray-50 rounded p-2">
            <span className="text-gray-400 flex-shrink-0 tabular-nums">
              {new Date(event.timestamp).toLocaleTimeString()}
            </span>
            <span className={eventTypeBadge(event.eventType)}>{event.eventType}</span>
            <span className="text-gray-600 truncate">{event.agentId.slice(0, 12)}…</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// =============================================================================
// Main Page
// =============================================================================

/**
 * AgentPluginManagerPage — registers, monitors, and manages Data-Cloud agents.
 */
export function AgentPluginManagerPage(): React.ReactElement {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [registryEvents, setRegistryEvents] = useState<RegistryEvent[]>([]);
  const [sseConnected, setSseConnected] = useState(false);
  const sseRef = useRef<EventSource | null>(null);

  // ── Fetch agents ──
  const {
    data: agents = [],
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ['agents'],
    queryFn: () => agentRegistryService.listAgents(),
    staleTime: 30_000,
  });

  // ── Register mutation ──
  const registerMutation = useMutation({
    mutationFn: (request: AgentRegistrationRequest) =>
      agentRegistryService.registerAgent(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agents'] });
      setShowModal(false);
    },
  });

  // ── Deregister mutation ──
  const deregisterMutation = useMutation({
    mutationFn: (agentId: string) => agentRegistryService.deregisterAgent(agentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['agents'] }),
  });

  // ── SSE stream ──
  useEffect(() => {
    const source = agentRegistryService.streamRegistryEvents(
      undefined,
      (event) => {
        setRegistryEvents((prev) => [event, ...prev].slice(0, 200));
      },
      () => setSseConnected(false)
    );

    source.onopen = () => setSseConnected(true);
    sseRef.current = source;

    return () => {
      source.close();
      sseRef.current = null;
    };
  }, []);

  const handleDeregister = useCallback(
    (agentId: string) => {
      if (confirm('Deregister this agent? All executions history will be retained.')) {
        deregisterMutation.mutate(agentId);
      }
    },
    [deregisterMutation]
  );

  const activeCount = agents.filter((a) => a.status === 'ACTIVE').length;
  const errorCount = agents.filter((a) => a.status === 'ERROR').length;

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-5">
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-indigo-600 rounded-lg">
                <Bot className="h-7 w-7 text-white" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-gray-900">Agent Registry</h1>
                <p className="text-sm text-gray-500 mt-0.5">
                  Monitor and manage registered agents
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={() => refetch()}
                className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                title="Refresh"
              >
                <RefreshCw className="h-4 w-4" />
              </button>
              <button
                onClick={() => setShowModal(true)}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors"
              >
                <Plus className="h-4 w-4" />
                Register Agent
              </button>
            </div>
          </div>

          {/* KPIs */}
          <div className="mt-4 flex items-center gap-6">
            <div className="flex items-center gap-1.5 text-sm">
              <Activity className="h-4 w-4 text-gray-400" />
              <span className="font-semibold text-gray-900">{agents.length}</span>
              <span className="text-gray-500">total</span>
            </div>
            <div className="flex items-center gap-1.5 text-sm">
              <CheckCircle className="h-4 w-4 text-green-500" />
              <span className="font-semibold text-green-700">{activeCount}</span>
              <span className="text-gray-500">active</span>
            </div>
            {errorCount > 0 && (
              <div className="flex items-center gap-1.5 text-sm">
                <XCircle className="h-4 w-4 text-red-500" />
                <span className="font-semibold text-red-700">{errorCount}</span>
                <span className="text-gray-500">errors</span>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Agent Grid (left 2 cols) */}
          <div className="lg:col-span-2">
            {isLoading && (
              <div className="text-center py-16 text-gray-500">Loading agents…</div>
            )}
            {isError && (
              <div className="text-center py-16 text-red-600">
                Failed to load agents.{' '}
                <button onClick={() => refetch()} className="underline">Retry</button>
              </div>
            )}
            {!isLoading && !isError && agents.length === 0 && (
              <div className="text-center py-16 bg-white rounded-lg border border-dashed border-gray-300">
                <Bot className="h-10 w-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-500">No agents registered yet.</p>
                <button
                  onClick={() => setShowModal(true)}
                  className="mt-3 text-sm text-indigo-600 hover:underline"
                >
                  Register your first agent →
                </button>
              </div>
            )}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {agents.map((agent) => (
                <AgentCard
                  key={agent.agentId}
                  agent={agent}
                  onDeregister={handleDeregister}
                  deregistering={
                    deregisterMutation.isPending &&
                    deregisterMutation.variables === agent.agentId
                  }
                />
              ))}
            </div>
          </div>

          {/* Events Feed (right col) */}
          <div className="lg:col-span-1 min-h-[400px]">
            <RegistryEventsFeed events={registryEvents} connected={sseConnected} />
          </div>
        </div>
      </div>

      {/* Registration Modal */}
      {showModal && (
        <AgentRegistrationModal
          onClose={() => setShowModal(false)}
          onSubmit={(req) => registerMutation.mutate(req)}
          submitting={registerMutation.isPending}
        />
      )}
    </div>
  );
}
