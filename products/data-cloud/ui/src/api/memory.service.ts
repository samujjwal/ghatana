/**
 * Memory Plane API Service
 *
 * Provides API client for Data Cloud agent memory plane operations.
 * Supports browsing episodic, semantic, and procedural memory items.
 *
 * @doc.type service
 * @doc.purpose Memory Plane API client for agent memory browsing
 * @doc.layer frontend
 */

import axios, { type AxiosInstance } from 'axios';

// =============================================================================
// Types
// =============================================================================

export type MemoryType = 'EPISODIC' | 'SEMANTIC' | 'PROCEDURAL' | 'PREFERENCE';

export interface MemoryItem {
  id: string;
  tenantId: string;
  agentId: string;
  type: MemoryType;
  content: string;
  tags: string[];
  salience: number;
  createdAt: string;
  expiresAt?: string;
  metadata: Record<string, unknown>;
}

export interface MemorySearchParams {
  tenantId?: string;
  agentId?: string;
  type?: MemoryType;
  query?: string;
  limit?: number;
}

export interface MemoryConsolidationStatus {
  lastRun?: string;
  episodesProcessed: number;
  policiesExtracted: number;
  nextScheduled?: string;
}

// =============================================================================
// Client
// =============================================================================

/**
 * MemoryService — typed client for DC memory plane endpoints.
 *
 * @doc.type class
 * @doc.purpose REST client for Data-Cloud agent memory plane
 * @doc.layer frontend
 * @doc.pattern Service
 */
export class MemoryService {
  private client: AxiosInstance;

  constructor(baseURL: string = '/api') {
    this.client = axios.create({
      baseURL,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  /** Get memory summary (count per tier) for an agent */
  async getAgentMemorySummary(agentId: string, tenantId?: string): Promise<Record<string, number>> {
    const { data } = await this.client.get<Record<string, number>>(
      `/v1/memory/${agentId}`,
      { params: tenantId ? { tenantId } : {} },
    );
    return data;
  }

  /** List memory items for an agent by tier */
  async listMemoryByTier(agentId: string, tier: MemoryType, params: { limit?: number; offset?: number; tenantId?: string } = {}): Promise<MemoryItem[]> {
    const { data } = await this.client.get<MemoryItem[]>(
      `/v1/memory/${agentId}/${tier.toLowerCase()}`,
      { params },
    );
    return data;
  }

  /** List memory items for an agent/tenant (all tiers) */
  async listMemoryItems(params: MemorySearchParams = {}): Promise<MemoryItem[]> {
    const { agentId, type, ...rest } = params;
    if (agentId && type) {
      return this.listMemoryByTier(agentId, type, rest);
    }
    if (agentId) {
      // Default to episodic if no type specified
      return this.listMemoryByTier(agentId, 'EPISODIC', rest);
    }
    const { data } = await this.client.get<MemoryItem[]>('/v1/memory', { params });
    return data;
  }

  /** Delete a memory item */
  async deleteMemoryItem(agentId: string, memoryId: string): Promise<void> {
    await this.client.delete(`/v1/memory/${agentId}/${memoryId}`);
  }

  /** Mark a memory item as retained (bypass decay) */
  async retainMemoryItem(agentId: string, memoryId: string): Promise<void> {
    await this.client.put(`/v1/memory/${agentId}/${memoryId}/retain`);
  }

  /** Semantic search across agent memory */
  async searchMemory(agentId: string, query: string, tenantId?: string): Promise<MemoryItem[]> {
    const { data } = await this.client.post<MemoryItem[]>(
      `/v1/memory/${agentId}/search`,
      { query, tenantId },
    );
    return data;
  }

  /** Get consolidation status */
  async getConsolidationStatus(tenantId?: string): Promise<MemoryConsolidationStatus> {
    const { data } = await this.client.get<MemoryConsolidationStatus>(
      '/v1/learning/status',
      { params: tenantId ? { tenantId } : {} },
    );
    return data;
  }
}

export const memoryService = new MemoryService(
  import.meta.env.VITE_DC_API_URL ?? '/api',
);
