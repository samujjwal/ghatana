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

import axios, { type AxiosInstance } from 'axios';

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
  private client: AxiosInstance;
  private readonly base: string;

  constructor(baseURL: string = '/api/v1') {
    this.base = baseURL;
    this.client = axios.create({
      baseURL,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  // ==================== Agent Registry ====================

  /** List all registered agents for a tenant */
  async listAgents(params: AgentListParams = {}): Promise<AgentDefinition[]> {
    const { data } = await this.client.get<AgentDefinition[]>('/agents', { params });
    return data;
  }

  /** Get a specific agent by ID */
  async getAgent(agentId: string): Promise<AgentDefinition> {
    const { data } = await this.client.get<AgentDefinition>(`/agents/${agentId}`);
    return data;
  }

  /** Register a new agent */
  async registerAgent(request: AgentRegistrationRequest): Promise<AgentDefinition> {
    const { data } = await this.client.post<AgentDefinition>('/agents/register', request);
    return data;
  }

  /** Deregister an agent by ID */
  async deregisterAgent(agentId: string): Promise<void> {
    await this.client.delete(`/agents/${agentId}`);
  }

  /** Update an agent's capabilities */
  async updateCapabilities(
    agentId: string,
    capabilities: Omit<AgentCapability, 'id'>[]
  ): Promise<AgentDefinition> {
    const { data } = await this.client.put<AgentDefinition>(
      `/agents/${agentId}/capabilities`,
      { capabilities }
    );
    return data;
  }

  // ==================== Executions ====================

  /** List execution history for an agent */
  async listExecutions(
    agentId: string,
    params: ExecutionListParams = {}
  ): Promise<AgentExecution[]> {
    const { data } = await this.client.get<AgentExecution[]>(
      `/agents/${agentId}/executions`,
      { params }
    );
    return data;
  }

  /** Record a new execution event for an agent */
  async recordExecution(
    agentId: string,
    execution: Partial<AgentExecution>
  ): Promise<AgentExecution> {
    const { data } = await this.client.post<AgentExecution>(
      `/agents/${agentId}/executions`,
      execution
    );
    return data;
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
    const url = new URL(`${this.base}/agents/events/stream`, window.location.origin);
    if (tenantId) url.searchParams.set('tenantId', tenantId);

    const source = new EventSource(url.toString());
    source.onmessage = (e) => {
      try {
        onEvent(JSON.parse(e.data) as RegistryEvent);
      } catch {
        // Ignore malformed events
      }
    };
    if (onError) source.onerror = onError;
    return source;
  }
}

export const agentRegistryService = new AgentRegistryService();
