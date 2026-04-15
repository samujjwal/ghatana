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

interface LearningStatusResponse {
  running: boolean;
  lastRunTime: string;
  nextScheduledRun: string;
  intervalMinutes: number;
  pendingReviews: number;
  lastResult?: {
    status?: string;
    tenantId?: string;
    manual?: boolean;
    durationMs?: number;
    patternsDiscovered?: number;
    patternsUpdated?: number;
    recordsAnalyzed?: number;
    ranAt?: string;
  };
  timestamp?: string;
}

interface MemoryListResponse {
  items: MemoryItem[];
  total: number;
}

interface MemoryTierResponse {
  items: MemoryItem[];
  count: number;
}

interface MemorySummaryResponse {
  byType: Record<string, number>;
}

interface MemorySearchResponse {
  results: MemoryItem[];
  count: number;
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
  private mapConsolidationStatus(response: LearningStatusResponse): MemoryConsolidationStatus {
    return {
      lastRun: response.lastRunTime !== 'never' ? response.lastRunTime : response.lastResult?.ranAt,
      episodesProcessed: response.lastResult?.recordsAnalyzed ?? 0,
      policiesExtracted: response.lastResult?.patternsDiscovered ?? 0,
      nextScheduled: response.nextScheduledRun !== 'not started' ? response.nextScheduledRun : undefined,
    };
  }

  /** Get memory summary (count per tier) for an agent */
  async getAgentMemorySummary(agentId: string, tenantId?: string): Promise<Record<string, number>> {
    const response = await apiClient.get<MemorySummaryResponse>(
      `/memory/${agentId}`,
      { params: tenantId ? { tenantId } : {} },
    );
    return response.byType;
  }

  /** List memory items for an agent by tier */
  async listMemoryByTier(agentId: string, tier: MemoryType, params: { limit?: number; offset?: number; tenantId?: string } = {}): Promise<MemoryItem[]> {
    const response = await apiClient.get<MemoryTierResponse>(
      `/memory/${agentId}/${tier.toLowerCase()}`,
      { params },
    );
    return response.items;
  }

  /** List memory items for an agent/tenant (all tiers) */
  async listMemoryItems(params: MemorySearchParams = {}): Promise<MemoryItem[]> {
    const { agentId, type, ...rest } = params;
    if (agentId && type) {
      return this.listMemoryByTier(agentId, type, rest);
    }
    if (agentId && !type) {
      const response = await apiClient.get<MemoryListResponse>(`/memory/${agentId}`, { params: rest });
      return response.items;
    }
    const response = await apiClient.get<MemoryListResponse>('/memory', { params });
    return response.items;
  }

  /** Delete a memory item */
  async deleteMemoryItem(agentId: string, memoryId: string): Promise<void> {
    await apiClient.delete<void>(`/memory/${agentId}/${memoryId}`);
  }

  /** Mark a memory item as retained (bypass decay) */
  async retainMemoryItem(agentId: string, memoryId: string): Promise<void> {
    await apiClient.put<void>(`/memory/${agentId}/${memoryId}/retain`, {});
  }

  /** Semantic search across agent memory */
  async searchMemory(agentId: string, query: string, tenantId?: string): Promise<MemoryItem[]> {
    const response = await apiClient.post<MemorySearchResponse>(
      `/memory/${agentId}/search`,
      { query },
      { params: tenantId ? { tenantId } : {} },
    );
    return response.results;
  }

  /** Get consolidation status */
  async getConsolidationStatus(tenantId?: string): Promise<MemoryConsolidationStatus> {
    const response = await apiClient.get<LearningStatusResponse>(
      '/learning/status',
      { params: tenantId ? { tenantId } : {} },
    );
    return this.mapConsolidationStatus(response);
  }
}

export const memoryService = new MemoryService();
