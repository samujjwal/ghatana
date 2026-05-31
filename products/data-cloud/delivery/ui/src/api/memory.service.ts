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

import type { components } from "../contracts/generated/data-cloud";
import {
  AgentMemorySummaryResponseSchema,
  LearningStatusResponseSchema,
  MemoryRootListResponseSchema,
  MemorySearchRequestSchema,
  MemorySearchResponseSchema,
  MemoryTierResponseSchema,
  type MemoryItem as BackendMemoryItem,
  type LearningStatusResponse,
} from "../contracts/schemas";
import { apiClient } from "../lib/api/client";
import { isMemorySurfaceEnabled } from "../lib/feature-gates";
import {
  MEMORY_SURFACE_BOUNDARY_MESSAGE,
  createRuntimeBoundaryError,
} from "../lib/runtime-boundaries";

// =============================================================================
// Types
// =============================================================================

// DC-P1-006: Migrated to use generated type from OpenAPI spec
export type MemoryType = components["schemas"]["MemoryType"];

export type MemoryItem = BackendMemoryItem;

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

function assertMemorySurfaceEnabled(): void {
  if (!isMemorySurfaceEnabled()) {
    throw createRuntimeBoundaryError(MEMORY_SURFACE_BOUNDARY_MESSAGE);
  }
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
  private mapConsolidationStatus(
    response: LearningStatusResponse,
  ): MemoryConsolidationStatus {
    return {
      lastRun:
        response.lastRunTime !== "never"
          ? response.lastRunTime
          : response.lastResult?.ranAt,
      episodesProcessed: response.lastResult?.recordsAnalyzed ?? 0,
      policiesExtracted: response.lastResult?.patternsDiscovered ?? 0,
      nextScheduled:
        response.nextScheduledRun !== "not started"
          ? response.nextScheduledRun
          : undefined,
    };
  }

  /** Get memory summary (count per tier) for an agent */
  async getAgentMemorySummary(
    agentId: string,
    tenantId?: string,
  ): Promise<Record<string, number>> {
    assertMemorySurfaceEnabled();
    const rawResponse = await apiClient.get(`/memory/${agentId}`, {
      params: tenantId ? { tenantId } : {},
    });
    const response = AgentMemorySummaryResponseSchema.parse(rawResponse);
    return response.byType;
  }

  /** List memory items for an agent by tier */
  async listMemoryByTier(
    agentId: string,
    tier: MemoryType,
    params: { limit?: number; offset?: number; tenantId?: string } = {},
  ): Promise<MemoryItem[]> {
    assertMemorySurfaceEnabled();
    const rawResponse = await apiClient.get(
      `/memory/${agentId}/${tier.toLowerCase()}`,
      { params },
    );
    const response = MemoryTierResponseSchema.parse(rawResponse);
    return response.items;
  }

  /** List memory items for an agent/tenant (all tiers) */
  async listMemoryItems(
    params: MemorySearchParams = {},
  ): Promise<MemoryItem[]> {
    assertMemorySurfaceEnabled();
    const { agentId, type, ...rest } = params;
    if (agentId && type) {
      return this.listMemoryByTier(agentId, type, rest);
    }
    if (agentId && !type) {
      const rawResponse = await apiClient.get(`/memory/${agentId}`, {
        params: rest,
      });
      const response = MemoryRootListResponseSchema.parse(rawResponse);
      return response.items;
    }
    const rawResponse = await apiClient.get("/memory", { params });
    const response = MemoryRootListResponseSchema.parse(rawResponse);
    return response.items;
  }

  /** Delete a memory item */
  async deleteMemoryItem(agentId: string, memoryId: string): Promise<void> {
    assertMemorySurfaceEnabled();
    await apiClient.delete<void>(`/memory/${agentId}/${memoryId}`);
  }

  /** Mark a memory item as retained (bypass decay) */
  async retainMemoryItem(agentId: string, memoryId: string): Promise<void> {
    assertMemorySurfaceEnabled();
    await apiClient.put<void>(`/memory/${agentId}/${memoryId}/retain`, {});
  }

  /** Semantic search across agent memory */
  async searchMemory(
    agentId: string,
    query: string,
    tenantId?: string,
  ): Promise<MemoryItem[]> {
    assertMemorySurfaceEnabled();
    const request = MemorySearchRequestSchema.parse({ query });
    const rawResponse = await apiClient.post(
      `/memory/${agentId}/search`,
      request,
      { params: tenantId ? { tenantId } : {} },
    );
    const response = MemorySearchResponseSchema.parse(rawResponse);
    return response.results;
  }

  /** Get consolidation status */
  async getConsolidationStatus(
    tenantId?: string,
  ): Promise<MemoryConsolidationStatus> {
    assertMemorySurfaceEnabled();
    const rawResponse = await apiClient.get("/learning/status", {
      params: tenantId ? { tenantId } : {},
    });
    const response = LearningStatusResponseSchema.parse(rawResponse);
    return this.mapConsolidationStatus(response);
  }
}

export const memoryService = new MemoryService();
