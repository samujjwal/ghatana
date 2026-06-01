/**
 * Agent Registry API Service
 *
 * Provides API client for Data Cloud Agent Registry operations.
 * Supports launcher-backed catalog reads only.
 * Registration, lifecycle mutation, execution history, and live registry
 * events remain owned by AEP rather than this product surface.
 *
 * @doc.type service
 * @doc.purpose Agent Registry API client for agent management
 * @doc.layer frontend
 */

import { isAgentCatalogSurfaceEnabled } from "@/lib/feature-gates";
import {
  AGENT_REGISTRY_BOUNDARY_MESSAGE,
  createRuntimeBoundaryError,
} from "@/lib/runtime-boundaries";
import {
  AgentCatalogEntrySchema,
  AgentCatalogListSchema,
  type AgentCatalogEntry,
  type AgentCapability as BackendAgentCapability,
} from "../contracts/schemas";
import { apiClient } from "../lib/api/client";

export { AGENT_REGISTRY_BOUNDARY_MESSAGE } from "@/lib/runtime-boundaries";

// =============================================================================
// Types
// =============================================================================

export type AgentStatus =
  | "ACTIVE"
  | "INACTIVE"
  | "ERROR"
  | "REGISTERING"
  | "DEREGISTERING";
// DC-32: Canonical execution status aligned with OperatorState
export type ExecutionStatus =
  | "CREATED"
  | "INITIALIZED"
  | "RUNNING"
  | "STOPPED"
  | "FAILED";

export type AgentCapability = BackendAgentCapability;

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
  // WS2-18: Governed capability fields
  policyCompliance?: string;
  memoryEnabled?: boolean;
  learningLevel?: string;
  requiresApproval?: boolean;
  runtimeState?: string;
  tools?: string[];
}

export interface AgentRegistrationRequest {
  name: string;
  description: string;
  version: string;
  capabilities: Omit<AgentCapability, "id">[];
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
  eventType:
    | "AGENT_REGISTERED"
    | "AGENT_DEREGISTERED"
    | "AGENT_UPDATED"
    | "AGENT_STATUS_CHANGED"
    | "EXECUTION_STARTED"
    | "EXECUTION_COMPLETED";
  agentId: string;
  tenantId: string;
  timestamp: string;
  payload: Record<string, unknown>;
}

function normalizeAgentStatus(status?: string): AgentStatus {
  switch (status) {
    case "ACTIVE":
    case "INACTIVE":
    case "ERROR":
    case "REGISTERING":
    case "DEREGISTERING":
      return status;
    default:
      return "INACTIVE";
  }
}

function mapCatalogEntry(entry: AgentCatalogEntry): AgentDefinition {
  const timestamp = new Date().toISOString();
  return {
    agentId: entry.agentId ?? entry.id ?? "unknown-agent",
    name: entry.name ?? "Unnamed Agent",
    description: entry.description ?? "No description provided.",
    version: entry.version ?? "unknown",
    tenantId: entry.tenantId ?? "",
    status: normalizeAgentStatus(entry.status),
    capabilities: entry.capabilities ?? [],
    registeredAt: entry.registeredAt ?? timestamp,
    updatedAt: entry.updatedAt ?? entry.registeredAt ?? timestamp,
    endpoint: entry.endpoint,
    metadata: entry.metadata ?? {},
    // WS2-18: Governed capability fields from metadata
    policyCompliance: (entry.metadata?.policyCompliance as string) ?? "Compliant",
    memoryEnabled: (entry.metadata?.memoryEnabled as boolean) ?? true,
    learningLevel: (entry.metadata?.learningLevel as string) ?? "L2",
    requiresApproval: (entry.metadata?.requiresApproval as boolean) ?? false,
    runtimeState: (entry.metadata?.runtimeState as string) ?? "Ready",
    tools: (entry.metadata?.tools as string[]) ?? [],
  };
}

function assertAgentCatalogSurfaceEnabled(): void {
  if (!isAgentCatalogSurfaceEnabled()) {
    throw createRuntimeBoundaryError(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }
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
    assertAgentCatalogSurfaceEnabled();
    const rawEntries = await apiClient.get<AgentCatalogEntry[]>(
      "/agents/catalog",
      { params },
    );
    const entries = AgentCatalogListSchema.parse(rawEntries);
    return entries.map(mapCatalogEntry);
  }

  /** Get a specific agent by ID */
  async getAgent(agentId: string): Promise<AgentDefinition> {
    assertAgentCatalogSurfaceEnabled();
    const rawEntry = await apiClient.get<AgentCatalogEntry>(
      `/agents/catalog/${agentId}`,
    );
    const entry = AgentCatalogEntrySchema.parse(rawEntry);
    return mapCatalogEntry(entry);
  }

  /** Register a new agent */
  async registerAgent(
    request: AgentRegistrationRequest,
  ): Promise<AgentDefinition> {
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
    capabilities: Omit<AgentCapability, "id">[],
  ): Promise<AgentDefinition> {
    void agentId;
    void capabilities;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }

  // ==================== Executions ====================

  /** List execution history for an agent */
  async listExecutions(
    agentId: string,
    params: ExecutionListParams = {},
  ): Promise<AgentExecution[]> {
    void agentId;
    void params;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }

  /** Record a new execution event for an agent */
  async recordExecution(
    agentId: string,
    execution: Partial<AgentExecution>,
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
    onError?: (error: Event) => void,
  ): EventSource {
    void tenantId;
    void onEvent;
    void onError;
    throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
  }
}

export const agentRegistryService = new AgentRegistryService();
