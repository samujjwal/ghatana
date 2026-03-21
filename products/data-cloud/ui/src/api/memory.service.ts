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

import { apiClient } from '../lib/api/client';

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
  /** Get memory summary (count per tier) for an agent */
  async getAgentMemorySummary(agentId: string, tenantId?: string): Promise<Record<string, number>> {
    return apiClient.get<Record<string, number>>(
      `/memory/${agentId}`,
      { params: tenantId ? { tenantId } : {} },
    );
  }

  /** List memory items for an agent by tier */
  async listMemoryByTier(agentId: string, tier: MemoryType, params: { limit?: number; offset?: number; tenantId?: string } = {}): Promise<MemoryItem[]> {
    return apiClient.get<MemoryItem[]>(
      `/memory/${agentId}/${tier.toLowerCase()}`,
      { params },
    );
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
    return apiClient.get<MemoryItem[]>('/memory', { params });
  }

  /** Delete a memory item */
  async deleteMemoryItem(agentId: string, memoryId: string): Promise<void> {
    await apiClient.delete<void>(`/memory/${agentId}/${memoryId}`);
  }

  /** Mark a memory item as retained (bypass decay) */
  async retainMemoryItem(agentId: string, memoryId: string): Promise<void> {
    await apiClient.put<void>(`/memory/${agentId}/${memoryId}/retain`);
  }

  /** Semantic search across agent memory */
  async searchMemory(agentId: string, query: string, tenantId?: string): Promise<MemoryItem[]> {
    return apiClient.post<MemoryItem[]>(
      `/memory/${agentId}/search`,
      { query, tenantId },
    );
  }

  /** Get consolidation status */
  async getConsolidationStatus(tenantId?: string): Promise<MemoryConsolidationStatus> {
    return apiClient.get<MemoryConsolidationStatus>(
      '/learning/status',
      { params: tenantId ? { tenantId } : {} },
    );
  }
}

export const memoryService = new MemoryService();
