/**
 * Ghatana Platform AI Registry HTTP Client
 *
 * <p><b>Purpose</b><br>
 * Reads AI model metadata from the Ghatana platform AI Registry service so that
 * DCMAAR features (risk scoring, recommendation engine, threat analysis) can
 * select and describe the AI models they invoke, without managing model lifecycle
 * themselves.
 *
 * <p><b>API contract ({@code GET /api/v1/models})</b><br>
 * Response: {@code { models: ModelRecord[], total: number }}
 *
 * <p><b>API contract ({@code GET /api/v1/models/:id})</b><br>
 * Response: {@code ModelRecord} or {@code 404}
 *
 * <p><b>Design</b><br>
 * The client is optional — when {@code AI_REGISTRY_URL} is not configured,
 * methods return empty-result fallbacks so that DCMAAR degraded operation
 * continues without the platform registry.
 * Results are cached in memory for {@link AI_REGISTRY_CACHE_TTL_MS} to avoid
 * hammering the registry on every inference request.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const models = await aiRegistryClient.listModels({ tenantId, status: 'PRODUCTION' });
 * const riskModel = models.find(m => m.name === 'threat-risk-scorer');
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Platform AI registry query client with in-process cache
 * @doc.layer backend
 * @doc.pattern Adapter, Cache-Aside
 */
import { logger } from '../utils/logger';

const MODELS_PATH = '/api/v1/models';
const DEFAULT_TIMEOUT_MS = 8_000;

/** Cache TTL: 60 s — model metadata changes infrequently. */
const AI_REGISTRY_CACHE_TTL_MS = 60_000;

/** Model record as returned by the AI Registry service. */
export interface AiModelRecord {
  id: string;
  tenantId: string;
  name: string;
  version: string;
  /** Deployment lifecycle stage: STAGED | CANARY | PRODUCTION | ACTIVE | DEPRECATED */
  status: string;
}

interface ModelsListResponse {
  models: AiModelRecord[];
  total: number;
}

interface ListOptions {
  /** Filter by tenant. When absent, returns all tenants' models. */
  tenantId?: string;
  /** Filter by status (e.g. "PRODUCTION", "CANARY"). */
  status?: string;
}

/** Single-entry cache value. */
interface CacheEntry<T> {
  value: T;
  expiresAt: number;
}

export class AiRegistryClient {
  private static instance: AiRegistryClient;

  private readonly baseUrl: string | null;

  /** Lightweight in-memory TTL cache: key → CacheEntry */
  private readonly cache = new Map<string, CacheEntry<unknown>>();

  private constructor() {
    this.baseUrl = process.env['AI_REGISTRY_URL'] ?? null;

    if (this.baseUrl) {
      logger.info('AiRegistryClient initialised', { baseUrl: this.baseUrl });
    } else {
      logger.warn(
        'AI_REGISTRY_URL not configured — AI Registry integration disabled. ' +
          'Risk scoring and recommendations will use local model stubs.',
      );
    }
  }

  public static getInstance(): AiRegistryClient {
    if (!AiRegistryClient.instance) {
      AiRegistryClient.instance = new AiRegistryClient();
    }
    return AiRegistryClient.instance;
  }

  // =========================================================================
  // Public API
  // =========================================================================

  /**
   * Lists models from the registry, optionally filtered by tenant and/or status.
   *
   * @param options optional filters
   * @returns array of model records; empty array if registry is unavailable
   */
  async listModels(options: ListOptions = {}): Promise<AiModelRecord[]> {
    if (!this.baseUrl) {
      return [];
    }

    const cacheKey = `list:${options.tenantId ?? '*'}:${options.status ?? '*'}`;
    const cached = this.fromCache<AiModelRecord[]>(cacheKey);
    if (cached !== null) {
      return cached;
    }

    try {
      const params = new URLSearchParams();
      if (options.tenantId) params.set('tenantId', options.tenantId);
      if (options.status) params.set('status', options.status);

      const paramStr = params.toString();
      const url = `${this.baseUrl}${MODELS_PATH}${paramStr ? `?${paramStr}` : ''}`;

      const body = await this.get<ModelsListResponse>(url);
      const models = body.models ?? [];
      this.toCache(cacheKey, models);
      return models;
    } catch (err) {
      logger.warn('AiRegistry listModels failed — returning empty list', {
        error: err instanceof Error ? err.message : String(err),
      });
      return [];
    }
  }

  /**
   * Finds the active (PRODUCTION or ACTIVE status) model for a given name
   * within a specific tenant.
   *
   * @param tenantId  tenant identifier
   * @param modelName logical model name (e.g. {@code "threat-risk-scorer"})
   * @returns matching model record, or {@code null} if absent or registry unavailable
   */
  async findActiveModel(tenantId: string, modelName: string): Promise<AiModelRecord | null> {
    const allActive = await this.listModels({ tenantId, status: 'PRODUCTION' });
    const found = allActive.find((m) => m.name === modelName);
    if (found) return found;

    // Fallback: try ACTIVE status alias
    const activeAlias = await this.listModels({ tenantId, status: 'ACTIVE' });
    return activeAlias.find((m) => m.name === modelName) ?? null;
  }

  /**
   * Retrieves a single model by its registry ID.
   *
   * @param modelId  UUID of the model in the registry
   * @returns model record, or {@code null} if not found or registry unavailable
   */
  async getModel(modelId: string): Promise<AiModelRecord | null> {
    if (!this.baseUrl) {
      return null;
    }

    const cacheKey = `model:${modelId}`;
    const cached = this.fromCache<AiModelRecord>(cacheKey);
    if (cached !== null) {
      return cached;
    }

    try {
      const url = `${this.baseUrl}${MODELS_PATH}/${encodeURIComponent(modelId)}`;
      const model = await this.get<AiModelRecord>(url);
      this.toCache(cacheKey, model);
      return model;
    } catch (err) {
      logger.warn('AiRegistry getModel failed', {
        modelId,
        error: err instanceof Error ? err.message : String(err),
      });
      return null;
    }
  }

  /**
   * Returns {@code true} when the registry URL is configured.
   */
  isConfigured(): boolean {
    return this.baseUrl !== null;
  }

  // =========================================================================
  // Internal helpers
  // =========================================================================

  private async get<T>(url: string): Promise<T> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`AI Registry responded ${response.status} for ${url}`);
      }

      return (await response.json()) as T;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  private fromCache<T>(key: string): T | null {
    const entry = this.cache.get(key) as CacheEntry<T> | undefined;
    if (entry && Date.now() < entry.expiresAt) {
      return entry.value;
    }
    this.cache.delete(key);
    return null;
  }

  private toCache<T>(key: string, value: T): void {
    this.cache.set(key, { value, expiresAt: Date.now() + AI_REGISTRY_CACHE_TTL_MS });
  }
}

export const aiRegistryClient = AiRegistryClient.getInstance();
