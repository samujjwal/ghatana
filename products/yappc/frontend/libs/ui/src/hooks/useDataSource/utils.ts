import type { DataSourceConfig } from './types';

/**
 * Simple in-memory cache with TTL (Time-To-Live) support.
 *
 * Stores data with expiration times and automatically clears expired entries
 * on access.
 */
export class SimpleCache {
  private map = new Map<string, { data: unknown; expiresAt: number }>();

  /**
   * Retrieve a value from cache if it exists and hasn't expired.
   *
   * Automatically deletes expired entries on access.
   *
   * @template T - Type of cached data
   * @param key - Cache key
   * @returns Cached value or null if missing or expired
   */
  get<T>(key: string): T | null {
    const value = this.map.get(key);
    if (!value) return null as T | null;

    // Check if entry has expired
    if (Date.now() > value.expiresAt) {
      this.map.delete(key);
      return null as T | null;
    }

    return value.data as T;
  }

  /**
   * Store a value in cache with expiration time.
   *
   * @template T - Type of data to cache
   * @param key - Cache key
   * @param data - Data to cache
   * @param ttl - Time-to-live in milliseconds
   */
  set<T>(key: string, data: T, ttl: number): void {
    this.map.set(key, {
      data: data as unknown,
      expiresAt: Date.now() + ttl,
    });
  }

  /**
   * Remove a value from cache.
   *
   * @param key - Cache key to delete
   */
  delete(key: string): void {
    this.map.delete(key);
  }
}

/**
 * Utility class for data source operations (fetching and caching).
 */
export class DataSourceUtils {
  /**
   * Fetch data from a REST endpoint.
   *
   * Sends HTTP request with configured method, headers, and body.
   * Parses JSON response and applies optional transformation.
   *
   * @template TData - Type of data returned
   * @param config - REST data source configuration
   * @returns Promise resolving to typed data
   * @throws Error if URL is missing, request fails, or response is not OK
   *
   * @example
   * ```ts
   * const users = await DataSourceUtils.fetchREST({
   *   url: 'https://api.example.com/users',
   *   method: 'GET'
   * });
   * ```
   */
  static async fetchREST<TData>(
    config: DataSourceConfig<TData>
  ): Promise<TData> {
    const { url, method = 'GET', headers = {}, body } = config;

    if (!url) {
      throw new Error('URL required for REST data source');
    }

    const response = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(headers || {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    } as RequestInit);

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const json = await response.json();
    return (
      config.transformResponse
        ? config.transformResponse(json)
        : (json as unknown)
    ) as TData;
  }

  /**
   * Execute a GraphQL query.
   *
   * Sends GraphQL operation with query and variables to endpoint.
   * Parses response and checks for GraphQL errors before returning data.
   * Applies optional transformation.
   *
   * @template TData - Type of data returned
   * @param config - GraphQL data source configuration
   * @returns Promise resolving to typed data
   * @throws Error if URL/query missing, request fails, response is not OK, or GraphQL returns errors
   *
   * @example
   * ```ts
   * const user = await DataSourceUtils.fetchGraphQL({
   *   url: 'https://api.example.com/graphql',
   *   query: '{ user(id: 1) { id name } }',
   *   variables: { id: 1 }
   * });
   * ```
   */
  static async fetchGraphQL<TData>(
    config: DataSourceConfig<TData>
  ): Promise<TData> {
    const { url, query, variables, headers = {} } = config;

    if (!url || !query) {
      throw new Error('URL and query required for GraphQL data source');
    }

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(headers || {}),
      },
      body: JSON.stringify({ query, variables }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    const result = await response.json();
    const parsed = result as {
      errors?: Array<{ message: string }>;
      data?: unknown;
    };

    if (parsed.errors && parsed.errors.length > 0) {
      throw new Error(parsed.errors[0].message);
    }

    const data = parsed.data;
    return (
      config.transformResponse
        ? config.transformResponse(data)
        : (data as unknown)
    ) as TData;
  }
}
