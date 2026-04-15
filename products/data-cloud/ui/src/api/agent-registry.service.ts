/**
 * Agent Registry API Service
 *
 * Provides API client for Data Cloud Agent Registry operations.
 * Supports agent registration, lifecycle management, capability updates,
 * execution history, and SSE-based live registry event streaming.
 *
 * @doc.type service
 * @doc.purpose Agent Registry API client for agent management
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';

export const AGENT_REGISTRY_BOUNDARY_MESSAGE =
  'Agent registration, deregistration, execution history, and live registry events are not exposed by the current Data Cloud launcher API.';

// =============================================================================
// Types
// =============================================================================

export type AgentStatus = 'ACTIVE' | 'INACTIVE' | 'ERROR' | 'REGISTERING' | 'DEREGISTERING';
export type ExecutionStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'PENDING';

export interface AgentCapability {
  id: string;
  name: string;
  description: string;
  version: string;
  inputSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
}

export interface AgentDefinition {
  agentId: string;
  name: string;
  description: string;
  version: string;
  tenantId: string;
  status: AgentStatus;
  capabilities: AgentCapability[];
  registeredAt: string;
  updatedAt: string;
  endpoint?: string;
  metadata: Record<string, unknown>;
}

export interface AgentRegistrationRequest {
  name: string;
  description: string;
  version: string;
  capabilities: Omit<AgentCapability, 'id'>[];
  endpoint?: string;
  metadata?: Record<string, unknown>;
}

export interface AgentExecution {
  id: string;
  agentId: string;
  tenantId: string;
  status: ExecutionStatus;
  startedAt: string;
  completedAt?: string;
  durationMs?: number;
  inputSummary?: string;
  outputSummary?: string;
  errorMessage?: string;
  metadata: Record<string, unknown>;
}

export interface AgentListParams {
  tenantId?: string;
  status?: AgentStatus;
  limit?: number;
  offset?: number;
}

export interface ExecutionListParams {
  tenantId?: string;
  status?: ExecutionStatus;
  limit?: number;
  offset?: number;
}

export interface RegistryEvent {
  id: string;
  eventType: 'AGENT_REGISTERED' | 'AGENT_DEREGISTERED' | 'AGENT_UPDATED' | 'AGENT_STATUS_CHANGED' | 'EXECUTION_STARTED' | 'EXECUTION_COMPLETED';
  agentId: string;
  tenantId: string;
  timestamp: string;
  payload: Record<string, unknown>;
}

interface AgentCatalogEntry {
  id?: string;
  agentId?: string;
  name?: string;
  description?: string;
  version?: string;
  tenantId?: string;
  status?: string;
  capabilities?: AgentCapability[];
  registeredAt?: string;
  updatedAt?: string;
  endpoint?: string;
  metadata?: Record<string, unknown>;
}

function normalizeAgentStatus(status?: string): AgentStatus {
  switch (status) {
    case 'ACTIVE':
    case 'INACTIVE':
    case 'ERROR':
    case 'REGISTERING':
    case 'DEREGISTERING':
      return status;
    default:
      return 'INACTIVE';
  }
}

function mapCatalogEntry(entry: AgentCatalogEntry): AgentDefinition {
  const timestamp = new Date().toISOString();
  return {
    agentId: entry.agentId ?? entry.id ?? 'unknown-agent',
    name: entry.name ?? 'Unnamed Agent',
    description: entry.description ?? 'No description provided.',
    version: entry.version ?? 'unknown',
    tenantId: entry.tenantId ?? 'default',
    status: normalizeAgentStatus(entry.status),
    capabilities: entry.capabilities ?? [],
    registeredAt: entry.registeredAt ?? timestamp,
    updatedAt: entry.updatedAt ?? entry.registeredAt ?? timestamp,
    endpoint: entry.endpoint,
    metadata: entry.metadata ?? {},
  };
}

// =============================================================================
// Client
// =============================================================================

/**
 * AgentRegistryService — typed client for DC agent registry endpoints.
 *
 * @doc.type class
 * @doc.purpose REST + SSE client for Data-Cloud agent registry
 * @doc.layer frontend
 * @doc.pattern Service
 */
export class AgentRegistryService {
  // ==================== Agent Registry ====================

  /** List all registered agents for a tenant */
  async listAgents(params: AgentListParams = {}): Promise<AgentDefinition[]> {
    const entries = await apiClient.get<AgentCatalogEntry[]>('/agents/catalog', { params });
    return entries.map(mapCatalogEntry);
  }

  /** Get a specific agent by ID */
  async getAgent(agentId: string): Promise<AgentDefinition> {
    const entry = await apiClient.get<AgentCatalogEntry>(`/agents/catalog/${agentId}`);
    return mapCatalogEntry(entry);
  }

  /** Register a new agent */
  async registerAgent(request: AgentRegistrationRequest): Promise<AgentDefinition> {
    void request;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }

  /** Deregister an agent by ID */
  async deregisterAgent(agentId: string): Promise<void> {
    void agentId;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }

  /** Update an agent's capabilities */
  async updateCapabilities(
    agentId: string,
    capabilities: Omit<AgentCapability, 'id'>[]
  ): Promise<AgentDefinition> {
    void agentId;
    void capabilities;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }

  // ==================== Executions ====================

  /** List execution history for an agent */
  async listExecutions(
    agentId: string,
    params: ExecutionListParams = {}
  ): Promise<AgentExecution[]> {
    void agentId;
    void params;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }

  /** Record a new execution event for an agent */
  async recordExecution(
    agentId: string,
    execution: Partial<AgentExecution>
  ): Promise<AgentExecution> {
    void agentId;
    void execution;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }

  // ==================== SSE Stream ====================

  /**
   * Open an SSE stream for live agent registry events.
   * Returns an EventSource that emits RegistryEvent objects.
   *
   * @param tenantId optional tenant filter
   * @param onEvent callback invoked for each event
   * @param onError optional error callback
   * @returns EventSource (caller is responsible for calling .close())
   */
  streamRegistryEvents(
    tenantId: string | undefined,
    onEvent: (event: RegistryEvent) => void,
    onError?: (error: Event) => void
  ): EventSource {
    void tenantId;
    void onEvent;
    void onError;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }
}

export const agentRegistryService = new AgentRegistryService();
