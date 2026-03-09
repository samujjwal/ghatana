/**
 * API Service Utilities
 *
 * <p><b>Purpose</b><br>
 * Utility functions for API request/response handling, error management, retry logic,
 * and request composition across all API modules.
 *
 * <p><b>Functions</b><br>
 * - createApiClient: Initialize API client with interceptors
 * - handleApiError: Standardize error handling
 * - retryRequest: Exponential backoff retry logic
 * - buildQueryParams: Build query string
 * - createHeaders: Create authorization headers
 * - cacheResponse: Response caching with TTL
 * - validateResponse: Response validation
 *
 * @doc.type utility
 * @doc.purpose API service utilities and request handling
 * @doc.layer product
 * @doc.pattern Utility Module
 */

/**
 * API error type definition
 */
export interface ApiError {
    message: string;
    status?: number;
    code?: string;
    details?: Record<string, unknown>;
}

/**
 * API response type definition
 */
export interface ApiResponse<T> {
    data: T;
    status: number;
    headers: Record<string, string>;
}

/**
 * Retry configuration
 */
export interface RetryConfig {
    maxRetries?: number;
    delayMs?: number;
    backoffMultiplier?: number;
    retryableStatuses?: number[];
}

/**
 * Handle API errors with standardization
 *
 * @param error - Error object
 * @param defaultMessage - Default error message
 * @returns Standardized API error
 */
export function handleApiError(
    error: any,
    defaultMessage = 'An error occurred'
): ApiError {
    if (error instanceof TypeError) {
        return {
            message: 'Network error. Please check your connection.',
            code: 'NETWORK_ERROR',
        };
    }

    if (error.response) {
        return {
            message:
                error.response.data?.message ||
                error.response.statusText ||
                defaultMessage,
            status: error.response.status,
            code: `HTTP_${error.response.status}`,
            details: error.response.data,
        };
    }

    if (error.message) {
        return {
            message: error.message,
            code: error.code || 'UNKNOWN_ERROR',
        };
    }

    return {
        message: defaultMessage,
        code: 'UNKNOWN_ERROR',
    };
}

/**
 * Retry request with exponential backoff
 *
 * @param fn - Async function to retry
 * @param config - Retry configuration
 * @returns Promise of function result
 */
export async function retryRequest<T>(
    fn: () => Promise<T>,
    config: RetryConfig = {}
): Promise<T> {
    const {
        maxRetries = 3,
        delayMs = 1000,
        backoffMultiplier = 2,
        retryableStatuses = [408, 429, 500, 502, 503, 504],
    } = config;

    let lastError: any;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return await fn();
        } catch (error: any) {
            lastError = error;

            const shouldRetry =
                attempt < maxRetries &&
                (error.response?.status === undefined ||
                    retryableStatuses.includes(error.response?.status));

            if (!shouldRetry) {
                throw error;
            }

            const delay = delayMs * Math.pow(backoffMultiplier, attempt);
            await new Promise((resolve) => setTimeout(resolve, delay));
        }
    }

    throw lastError;
}

/**
 * Build query parameters string
 *
 * @param params - Parameter object
 * @returns Query string (without leading ?)
 */
export function buildQueryParams(
    params: Record<string, any>
): string {
    const entries = Object.entries(params)
        .filter(([, value]) => value !== null && value !== undefined)
        .map(([key, value]) => {
            if (Array.isArray(value)) {
                return value
                    .map((v) => `${encodeURIComponent(key)}=${encodeURIComponent(v)}`)
                    .join('&');
            }
            return `${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
        });

    return entries.join('&');
}

/**
 * Create authorization headers
 *
 * @param token - Authorization token
 * @param additionalHeaders - Additional headers
 * @returns Headers object
 */
export function createHeaders(
    token?: string,
    additionalHeaders?: Record<string, string>
): Record<string, string> {
    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...additionalHeaders,
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
}

/**
 * Validate API response structure
 *
 * @param response - Response object
 * @param requiredFields - Required fields in response
 * @returns True if valid
 * @throws Error if invalid
 */
export function validateResponse<T>(
    response: any,
    requiredFields?: (keyof T)[]
): response is T {
    if (!response) {
        throw new Error('Empty response received');
    }

    if (requiredFields && requiredFields.length > 0) {
        const missing = requiredFields.filter((field) => !(field in response));
        if (missing.length > 0) {
            throw new Error(
                `Missing required fields: ${missing.join(', ')}`
            );
        }
    }

    return true;
}

/**
 * Response cache for storing API responses with TTL
 */
class ResponseCache {
    private cache: Map<
        string,
        {
            data: any;
            timestamp: number;
            ttl: number;
        }
    > = new Map();

    /**
     * Get cached response
     *
     * @param key - Cache key
     * @returns Cached data or null
     */
    get(key: string): any | null {
        const entry = this.cache.get(key);
        if (!entry) return null;

        const age = Date.now() - entry.timestamp;
        if (age > entry.ttl) {
            this.cache.delete(key);
            return null;
        }

        return entry.data;
    }

    /**
     * Set cached response
     *
     * @param key - Cache key
     * @param data - Data to cache
     * @param ttl - Time to live in milliseconds
     */
    set(key: string, data: any, ttl: number = 60000): void {
        this.cache.set(key, {
            data,
            timestamp: Date.now(),
            ttl,
        });
    }

    /**
     * Clear cache entry
     *
     * @param key - Cache key
     */
    clear(key: string): void {
        this.cache.delete(key);
    }

    /**
     * Clear all cache
     */
    clearAll(): void {
        this.cache.clear();
    }
}

export const apiCache = new ResponseCache();

/**
 * Create API request interceptor function
 *
 * @param baseUrl - Base URL for API
 * @param defaultHeaders - Default headers
 * @returns API request function
 */
export function createApiClient(
    baseUrl: string,
    defaultHeaders?: Record<string, string>
) {
    return async function apiRequest<T>(
        endpoint: string,
        options?: {
            method?: string;
            body?: any;
            headers?: Record<string, string>;
            cache?: number;
            retry?: RetryConfig;
        }
    ): Promise<ApiResponse<T>> {
        const url = `${baseUrl}${endpoint}`;
        const method = options?.method || 'GET';
        const cacheKey = `${method}:${url}`;

        // Check cache for GET requests
        if (method === 'GET' && options?.cache) {
            const cached = apiCache.get(cacheKey);
            if (cached) {
                return cached;
            }
        }

        const makeRequest = async () => {
            const response = await fetch(url, {
                method,
                headers: {
                    ...defaultHeaders,
                    ...options?.headers,
                },
                body: options?.body ? JSON.stringify(options.body) : undefined,
            });

            if (!response.ok) {
                throw {
                    response: {
                        status: response.status,
                        statusText: response.statusText,
                        data: await response.json().catch(() => ({})),
                    },
                };
            }

            const data = await response.json();
            return {
                data,
                status: response.status,
                headers: Object.fromEntries(response.headers),
            } as ApiResponse<T>;
        };

        try {
            const result = options?.retry
                ? await retryRequest(makeRequest, options.retry)
                : await makeRequest();

            // Cache successful GET responses
            if (method === 'GET' && options?.cache) {
                apiCache.set(cacheKey, result, options.cache);
            }

            return result;
        } catch (error) {
            throw handleApiError(error);
        }
    };
}

/**
 * Format API URL with path and query parameters
 *
 * @param baseUrl - Base URL
 * @param path - URL path
 * @param params - Query parameters
 * @returns Complete URL
 */
export function formatApiUrl(
    baseUrl: string,
    path: string,
    params?: Record<string, any>
): string {
    let url = `${baseUrl}${path}`;
    if (params) {
        const queryString = buildQueryParams(params);
        if (queryString) {
            url += `?${queryString}`;
        }
    }
    return url;
}

export default {
    handleApiError,
    retryRequest,
    buildQueryParams,
    createHeaders,
    validateResponse,
    apiCache,
    createApiClient,
    formatApiUrl,
};
