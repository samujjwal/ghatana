/**
 * AI Registry Platform Client
 *
 * Optional singleton that queries the Ghatana platform AI Registry to
 * discover available and active ML models for a given tenant. Includes a
 * 60-second in-process TTL cache to avoid hammering the registry on every
 * request.
 *
 * Usage:
 *   import { aiRegistryClient } from './ai-registry.client.js';
 *   const models = await aiRegistryClient.listModels({ tenantId: 'acme', status: 'PRODUCTION' });
 *   const model  = await aiRegistryClient.findActiveModel('acme', 'tutoring-llm');
 *
 * Environment variables:
 *   AI_REGISTRY_URL — base URL, e.g. http://ai-registry:8080
 */

const CACHE_TTL_MS = 60_000;
const REQUEST_TIMEOUT_MS = 5_000;

export interface ModelRecord {
  id: string;
  tenantId: string;
  name: string;
  version: string;
  status: 'DEVELOPMENT' | 'STAGED' | 'CANARY' | 'PRODUCTION' | 'ACTIVE' | 'DEPRECATED';
  framework?: string;
  endpoint?: string;
  createdAt?: string;
  updatedAt?: string;
  trainingMetrics?: Record<string, number>;
}

export interface ListModelsOptions {
  tenantId?: string;
  status?: string;
}

interface CacheEntry<T> {
  value: T;
  expiresAt: number;
}

class AiRegistryClient {
  private static instance: AiRegistryClient;
  private readonly baseUrl: string | undefined;
  private readonly cache = new Map<string, CacheEntry<unknown>>();

  private constructor() {
    this.baseUrl = process.env['AI_REGISTRY_URL'];
    if (!this.baseUrl) {
      console.warn('[AiRegistryClient] AI_REGISTRY_URL not configured — AI Registry queries are disabled');
    }
  }

  static getInstance(): AiRegistryClient {
    if (!AiRegistryClient.instance) {
      AiRegistryClient.instance = new AiRegistryClient();
    }
    return AiRegistryClient.instance;
  }

  private cacheGet<T>(key: string): T | undefined {
    const entry = this.cache.get(key) as CacheEntry<T> | undefined;
    if (!entry) return undefined;
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return undefined;
    }
    return entry.value;
  }

  private cacheSet<T>(key: string, value: T): void {
    this.cache.set(key, { value, expiresAt: Date.now() + CACHE_TTL_MS });
  }

  private async get<T>(path: string, cacheKey: string): Promise<T | null> {
    const cached = this.cacheGet<T>(cacheKey);
    if (cached !== undefined) return cached;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const res = await fetch(`${this.baseUrl}${path}`, { signal: controller.signal });
      if (!res.ok) {
        console.warn(`[AiRegistryClient] GET ${path} returned HTTP ${res.status}`);
        return null;
      }
      const data = (await res.json()) as T;
      this.cacheSet(cacheKey, data);
      return data;
    } catch (err: unknown) {
      console.warn(`[AiRegistryClient] GET ${path} failed: ${err instanceof Error ? err.message : String(err)}`);
      return null;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * List all models, optionally filtered by tenantId and/or status.
   * Returns an empty array when the registry is unconfigured or unreachable.
   */
  async listModels(options: ListModelsOptions = {}): Promise<ModelRecord[]> {
    if (!this.baseUrl) return [];

    const params = new URLSearchParams();
    if (options.tenantId) params.set('tenantId', options.tenantId);
    if (options.status) params.set('status', options.status);
    const qs = params.toString();
    const path = `/api/v1/models${qs ? `?${qs}` : ''}`;
    const cacheKey = `list:${qs}`;

    const result = await this.get<{ models: ModelRecord[]; total: number }>(path, cacheKey);
    return result?.models ?? [];
  }

  /**
   * Find the active model for a tenant by name.
   * Prefers PRODUCTION status, falls back to ACTIVE.
   * Returns null when not found or registry is down.
   */
  async findActiveModel(tenantId: string, modelName: string): Promise<ModelRecord | null> {
    if (!this.baseUrl) return null;

    // Try PRODUCTION first
    const production = await this.listModels({ tenantId, status: 'PRODUCTION' });
    const found = production.find((m) => m.name === modelName);
    if (found) return found;

    // Fallback to ACTIVE
    const active = await this.listModels({ tenantId, status: 'ACTIVE' });
    return active.find((m) => m.name === modelName) ?? null;
  }

  /**
   * Fetch a specific model by ID.
   * Returns null when not found or unavailable.
   */
  async getModel(modelId: string): Promise<ModelRecord | null> {
    if (!this.baseUrl) return null;
    return this.get<ModelRecord>(`/api/v1/models/${encodeURIComponent(modelId)}`, `model:${modelId}`);
  }
}

export const aiRegistryClient = AiRegistryClient.getInstance();
