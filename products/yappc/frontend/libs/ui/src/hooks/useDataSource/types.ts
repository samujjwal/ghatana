/**
 * Type definitions for data source configuration and results.
 */

/** Type of data source backend */
export type DataSourceType = 'rest' | 'graphql' | 'static';

/** HTTP method for REST requests */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/**
 * Configuration for a data source used by useDataSource.
 *
 * Supports REST, GraphQL, and static data sources with caching,
 * deduplication, response transformation, and lifecycle callbacks.
 *
 * @template TData - Type of data returned by the data source
 *
 * @example
 * ```ts
 * const config: DataSourceConfig<User> = {
 *   type: 'rest',
 *   url: 'https://api.example.com/users',
 *   method: 'GET',
 *   cache: true,
 *   cacheTTL: 300000,
 *   transformResponse: (data) => ({
 *     ...data,
 *     fetchedAt: new Date()
 *   })
 * };
 * ```
 */
export interface DataSourceConfig<TData = unknown> {
  /** Type of data source: 'rest', 'graphql', or 'static' */
  type: DataSourceType;
  /** URL endpoint for REST or GraphQL requests */
  url?: string;
  /** HTTP method for REST requests */
  method?: HttpMethod;
  /** Custom headers for requests */
  headers?: Record<string, string>;
  /** Request body for REST requests */
  body?: unknown;
  /** GraphQL query string */
  query?: string;
  /** GraphQL query variables */
  variables?: Record<string, unknown>;
  /** Static data (used when type='static') */
  data?: TData;
  /** Whether to cache results. Default: true */
  cache?: boolean;
  /** Optional custom cache key. Auto-generated if not provided. */
  cacheKey?: string;
  /** Cache time-to-live in milliseconds. Default: 300000 (5 minutes) */
  cacheTTL?: number;
  /** Whether to deduplicate concurrent requests. Default: true */
  dedupe?: boolean;
  /** Optional transformation function applied to response data */
  transformResponse?: (data: unknown) => TData;
  /** Callback called if data fetch fails */
  onError?: (error: Error) => void;
  /** Callback called when data fetch succeeds */
  onSuccess?: (data: TData) => void;
}

/**
 * Result object returned by the useDataSource hook.
 *
 * @template TData - Type of data managed by this source
 *
 * @example
 * ```ts
 * const { data, isLoading, error, refetch } = useDataSource(config);
 * ```
 */
export interface DataSourceResult<TData = unknown> {
  /** The current data, or null if not yet loaded */
  data: TData | null;
  /** Whether the initial data fetch is in progress */
  isLoading: boolean;
  /** Error from last fetch attempt, or null if no error */
  error: Error | null;
  /** Whether a refetch/validation is in progress (background fetch) */
  isValidating: boolean;
  /** Function to refetch data, bypassing cache */
  refetch: () => Promise<void>;
  /** Function to update data directly (useful for optimistic updates) */
  mutate: (data: TData | ((currentData: TData | null) => TData)) => void;
  /** Function to clear cached data */
  clearCache: () => void;
}
