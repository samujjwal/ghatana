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

  /** List memory items for an agent/tenant */
  async listMemoryItems(params: MemorySearchParams = {}): Promise<MemoryItem[]> {
    const { data } = await this.client.get<MemoryItem[]>('/dc/memory', { params });
    return data;
  }

  /** Get a single memory item */
  async getMemoryItem(id: string, tenantId?: string): Promise<MemoryItem> {
    const { data } = await this.client.get<MemoryItem>(`/dc/memory/${id}`, {
      params: tenantId ? { tenantId } : {},
    });
    return data;
  }

  /** Delete a memory item */
  async deleteMemoryItem(id: string, tenantId?: string): Promise<void> {
    await this.client.delete(`/dc/memory/${id}`, {
      params: tenantId ? { tenantId } : {},
    });
  }

  /** Get consolidation status */
  async getConsolidationStatus(tenantId?: string): Promise<MemoryConsolidationStatus> {
    const { data } = await this.client.get<MemoryConsolidationStatus>(
      '/dc/memory/consolidation/status',
      { params: tenantId ? { tenantId } : {} },
    );
    return data;
  }
}

export const memoryService = new MemoryService(
  import.meta.env.VITE_DC_API_URL ?? '/api',
);
