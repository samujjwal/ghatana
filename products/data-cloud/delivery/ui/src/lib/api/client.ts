/**
 * API Client Configuration
 *
 * Centralized API client for data-cloud UI. Provides typed endpoints
 * for collections, workflows, and other resources with caching support.
 *
 * @doc.type service
 * @doc.purpose API client configuration and utilities with caching
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { TokenStorage } from "../auth/tokenStorage";
import SessionBootstrap from "../auth/session";

/**
 * API Response wrapper
 */
export interface ApiResponse<T> {
  data: T;
  status: number;
  message?: string;
}

/**
 * Paginated response
 */
export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
}

/**
 * API Error
 */
export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  status?: number;
  /** Server-echoed X-Correlation-ID for operator diagnosis. Present on all HTTP error responses. */
  correlationId?: string;
}

/**
 * API Client configuration
 */
export interface ApiClientConfig {
  baseUrl: string;
  timeout?: number;
  headers?: Record<string, string>;
  enableCache?: boolean;
  defaultCacheTTL?: number;
}

/**
 * Request options accepted by the API client.
 */
export interface ApiRequestConfig extends Omit<
  RequestInit,
  "body" | "headers"
> {
  body?: BodyInit | null;
  headers?: Record<string, string>;
  params?: object;
  responseType?: "json" | "text" | "blob";
  skipCache?: boolean;
  cacheTTL?: number;
}

/**
 * Cache entry interface
 */
interface CacheEntry<T> {
  data: T;
  timestamp: number;
  ttl: number;
  tenantId?: string;
}

/**
 * Cache statistics
 */
export interface CacheStats {
  hits: number;
  misses: number;
  evictions: number;
  size: number;
}

/**
 * Cache configuration
 */
interface CacheConfig {
  enabled: boolean;
  defaultTTL: number;
  maxSize: number;
}

/**
 * In-memory cache for API responses
 */
class ApiCache {
  private cache: Map<string, CacheEntry<unknown>>;
  private stats: CacheStats;
  private config: CacheConfig;

  constructor(config: CacheConfig) {
    this.cache = new Map();
    this.stats = { hits: 0, misses: 0, evictions: 0, size: 0 };
    this.config = config;
  }

  private generateKey(url: string, config: ApiRequestConfig): string {
    const tenantId = SessionBootstrap.getTenantId();
    const paramsStr = config.params ? JSON.stringify(config.params) : "";
    return `${tenantId}:${url}:${paramsStr}`;
  }

  get<T>(url: string, config: ApiRequestConfig): T | null {
    if (!this.config.enabled || config.skipCache) {
      return null;
    }

    const key = this.generateKey(url, config);
    const entry = this.cache.get(key);

    if (!entry) {
      this.stats.misses++;
      return null;
    }

    const now = Date.now();
    if (now - entry.timestamp > entry.ttl) {
      this.cache.delete(key);
      this.stats.evictions++;
      this.stats.size--;
      return null;
    }

    this.stats.hits++;
    return entry.data as T;
  }

  set<T>(url: string, config: ApiRequestConfig, data: T): void {
    if (!this.config.enabled || config.skipCache) {
      return;
    }

    const key = this.generateKey(url, config);
    const ttl = config.cacheTTL ?? this.config.defaultTTL;
    const tenantId = SessionBootstrap.getTenantId() ?? undefined;

    // Evict oldest entry if cache is full
    if (this.cache.size >= this.config.maxSize) {
      const oldestKey = this.cache.keys().next().value;
      if (oldestKey) {
        this.cache.delete(oldestKey);
        this.stats.evictions++;
        this.stats.size--;
      }
    }

    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl,
      tenantId,
    });
    this.stats.size++;
  }

  invalidate(url: string): void {
    if (!this.config.enabled) {
      return;
    }

    const tenantId = SessionBootstrap.getTenantId();
    for (const key of this.cache.keys()) {
      if (key.startsWith(`${tenantId}:${url}`)) {
        this.cache.delete(key);
        this.stats.evictions++;
        this.stats.size--;
      }
    }
  }

  invalidateAll(): void {
    if (!this.config.enabled) {
      return;
    }

    const tenantId = SessionBootstrap.getTenantId();
    for (const key of this.cache.keys()) {
      if (key.startsWith(`${tenantId}:`)) {
        this.cache.delete(key);
        this.stats.evictions++;
        this.stats.size--;
      }
    }
  }

  clear(): void {
    this.cache.clear();
    this.stats = { hits: 0, misses: 0, evictions: 0, size: 0 };
  }

  getStats(): CacheStats {
    return { ...this.stats };
  }

  cleanup(): void {
    const now = Date.now();
    for (const [key, entry] of this.cache.entries()) {
      if (now - entry.timestamp > entry.ttl) {
        this.cache.delete(key);
        this.stats.evictions++;
        this.stats.size--;
      }
    }
  }
}

/**
 * API Client class
 */
export class ApiClient {
  private readonly baseUrl: string;
  private readonly timeout: number;
  private readonly defaultHeaders: Record<string, string>;
  private readonly cache: ApiCache;
  private cleanupInterval?: number;

  constructor(config: ApiClientConfig) {
    this.baseUrl = config.baseUrl;
    this.timeout = config.timeout ?? 30000;
    this.defaultHeaders = {
      "Content-Type": "application/json",
      ...config.headers,
    };
    this.cache = new ApiCache({
      enabled: config.enableCache ?? true,
      defaultTTL: config.defaultCacheTTL ?? 60000, // 60 seconds default
      maxSize: 100, // Max 100 cached entries
    });

    // Periodic cleanup every 5 minutes
    this.cleanupInterval = window.setInterval(() => {
      this.cache.cleanup();
    }, 300000);
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): CacheStats {
    return this.cache.getStats();
  }

  /**
   * Derives all cache paths to invalidate for a mutated URL.
   *
   * Domain rules:
   * - Always invalidate the exact URL.
   * - For any detail endpoint (last segment is a non-slash value after a
   *   collection segment), also invalidate the parent list endpoint.
   *   e.g. /entities/dc_collections/abc   → /entities/dc_collections
   *   e.g. /tenants/t1/collections/c1/records/r1 → /tenants/t1/collections/c1/records
   * - For search/query sub-paths under a resource family, invalidate the
   *   family root so stale query results are not served.
   *   e.g. /entities/dc_collections/search → /entities/dc_collections
   */
  static deriveInvalidationTargets(url: string): readonly string[] {
    const targets = new Set<string>([url]);
    const segments = url.replace(/^\//u, "").split("/").filter(Boolean);
    if (segments.length > 1) {
      // Always bubble up to the immediate parent collection/list.
      targets.add("/" + segments.slice(0, -1).join("/"));
    }
    return Array.from(targets);
  }

  /**
   * Clear all cache entries for current tenant
   */
  clearCache(): void {
    this.cache.invalidateAll();
  }

  /**
   * Invalidate cache for a specific URL pattern
   */
  invalidateCache(url: string): void {
    this.cache.invalidate(url);
  }

  private static isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
  }

  private static parseApiError(payload: unknown, response: Response): ApiError {
    const correlationId = response.headers.get("X-Correlation-ID") ?? undefined;
    const fallbackCode =
      response.status === 401
        ? "AUTH_REQUIRED"
        : response.status === 403
          ? "ACCESS_DENIED"
          : "UNKNOWN_ERROR";

    if (ApiClient.isRecord(payload)) {
      const nestedError = ApiClient.isRecord(payload.error)
        ? payload.error
        : undefined;
      const message =
        typeof nestedError?.message === "string"
          ? nestedError.message
          : typeof payload.message === "string"
            ? payload.message
            : response.statusText;
      const code =
        typeof nestedError?.code === "string"
          ? nestedError.code
          : typeof payload.code === "string"
            ? payload.code
            : fallbackCode;
      const details = ApiClient.isRecord(nestedError?.details)
        ? nestedError.details
        : payload;

      return {
        code,
        message,
        details,
        status: response.status,
        correlationId,
      };
    }

    return {
      code: "UNKNOWN_ERROR",
      message: response.statusText,
      status: response.status,
      correlationId,
    };
  }

  private createHeaders(extraHeaders?: Record<string, string>): Headers {
    const headers = new Headers({
      ...this.defaultHeaders,
      ...extraHeaders,
      // F-048: Propagate a per-request correlation ID so every span in the
      // backend can be correlated back to the originating browser request.
      "X-Correlation-ID":
        typeof crypto !== "undefined" && crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    });
    const tenantId = SessionBootstrap.getTenantId();
    if (tenantId) {
      headers.set("X-Tenant-ID", tenantId);
    }
    // Cookie-backed sessions are preferred for browser deployments.
    // Bearer headers are only injected when an explicit token is present.
    const token = TokenStorage.get();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
    return headers;
  }

  private async request<T>(
    url: string,
    config: ApiRequestConfig = {},
  ): Promise<T> {
    const controller = new AbortController();
    const timeoutHandle = window.setTimeout(
      () => controller.abort(),
      this.timeout,
    );
    const requestUrl = new URL(`${this.baseUrl}${url}`, window.location.origin);

    if (config.params) {
      for (const [key, value] of Object.entries(config.params)) {
        if (value != null) {
          requestUrl.searchParams.set(key, String(value));
        }
      }
    }

    try {
      const response = await fetch(requestUrl.toString(), {
        ...config,
        credentials: config.credentials ?? "include",
        headers: this.createHeaders(config.headers),
        signal: controller.signal,
      });

      const responseType = config.responseType ?? "json";
      const contentType = response.headers.get("content-type") ?? "";
      const payload =
        responseType === "blob"
          ? await response.blob()
          : responseType === "text"
            ? await response.text()
            : contentType.includes("application/json")
              ? await response.json()
              : await response.text();

      if (!response.ok) {
        throw ApiClient.parseApiError(payload, response);
      }

      return payload as T;
    } catch (error) {
      if ((error as DOMException).name === "AbortError") {
        throw {
          code: "TIMEOUT",
          message: "Request timed out",
        } as ApiError;
      }

      if ((error as ApiError).code) {
        throw error;
      }

      throw {
        code: "NETWORK_ERROR",
        message:
          error instanceof Error ? error.message : "Network error occurred",
      } as ApiError;
    } finally {
      window.clearTimeout(timeoutHandle);
    }
  }

  /**
   * GET request with caching support
   */
  async get<T>(url: string, config?: ApiRequestConfig): Promise<T> {
    const cached = this.cache.get<T>(url, config ?? {});
    if (cached !== null) {
      return cached;
    }

    const data = await this.request<T>(url, {
      ...config,
      method: "GET",
    });

    this.cache.set(url, config ?? {}, data);
    return data;
  }

  /**
   * POST request with domain-aware cache invalidation.
   * Invalidates the mutated URL and its parent list endpoint.
   */
  async post<T, D = unknown>(
    url: string,
    data?: D,
    config?: ApiRequestConfig,
  ): Promise<T> {
    const result = await this.request<T>(url, {
      ...config,
      method: "POST",
      body: data == null ? null : JSON.stringify(data),
    });

    for (const target of ApiClient.deriveInvalidationTargets(url)) {
      this.cache.invalidate(target);
    }
    return result;
  }

  /**
   * PUT request with domain-aware cache invalidation.
   * Invalidates the mutated URL and its parent list endpoint.
   */
  async put<T, D = unknown>(
    url: string,
    data?: D,
    config?: ApiRequestConfig,
  ): Promise<T> {
    const result = await this.request<T>(url, {
      ...config,
      method: "PUT",
      body: data == null ? null : JSON.stringify(data),
    });

    for (const target of ApiClient.deriveInvalidationTargets(url)) {
      this.cache.invalidate(target);
    }
    return result;
  }

  /**
   * PATCH request with domain-aware cache invalidation.
   * Invalidates the mutated URL and its parent list endpoint.
   */
  async patch<T, D = unknown>(
    url: string,
    data?: D,
    config?: ApiRequestConfig,
  ): Promise<T> {
    const result = await this.request<T>(url, {
      ...config,
      method: "PATCH",
      body: data == null ? null : JSON.stringify(data),
    });

    for (const target of ApiClient.deriveInvalidationTargets(url)) {
      this.cache.invalidate(target);
    }
    return result;
  }

  /**
   * DELETE request with domain-aware cache invalidation.
   * Invalidates the mutated URL and its parent list endpoint.
   */
  async delete<T>(url: string, config?: ApiRequestConfig): Promise<T> {
    const result = await this.request<T>(url, {
      ...config,
      method: "DELETE",
    });

    for (const target of ApiClient.deriveInvalidationTargets(url)) {
      this.cache.invalidate(target);
    }
    return result;
  }
}

/**
 * Default API client instance
 */
export const apiClient = new ApiClient({
  baseUrl: import.meta.env.VITE_API_URL ?? "/api/v1",
});

export default apiClient;
