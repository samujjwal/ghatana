/**
 * API Client Configuration
 *
 * Centralized API client for data-cloud UI. Provides typed endpoints
 * for collections, workflows, and other resources.
 *
 * @doc.type service
 * @doc.purpose API client configuration and utilities
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { TokenStorage } from '../auth/tokenStorage';
import SessionBootstrap from '../auth/session';

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
}

/**
 * API Client configuration
 */
export interface ApiClientConfig {
    baseUrl: string;
    timeout?: number;
    headers?: Record<string, string>;
}

/**
 * Request options accepted by the API client.
 */
export interface ApiRequestConfig extends Omit<RequestInit, 'body' | 'headers'> {
    body?: BodyInit | null;
    headers?: Record<string, string>;
    params?: object;
    responseType?: 'json' | 'text' | 'blob';
}

/**
 * API Client class
 */
export class ApiClient {
    private readonly baseUrl: string;
    private readonly timeout: number;
    private readonly defaultHeaders: Record<string, string>;

    constructor(config: ApiClientConfig) {
        this.baseUrl = config.baseUrl;
        this.timeout = config.timeout ?? 30000;
        this.defaultHeaders = {
            'Content-Type': 'application/json',
            ...config.headers,
        };
    }

    private createHeaders(extraHeaders?: Record<string, string>): Headers {
        const headers = new Headers({
            ...this.defaultHeaders,
            ...extraHeaders,
        });
        const tenantId = SessionBootstrap.getTenantId();
        if (tenantId) {
            headers.set('X-Tenant-ID', tenantId);
        }
        // Use TokenStorage instead of direct localStorage access.
        // TokenStorage uses memory-first storage with sessionStorage fallback,
        // reducing XSS token-theft risk. See lib/auth/tokenStorage.ts for the
        // migration path to httpOnly cookies (recommended for production).
        const token = TokenStorage.get();
        if (token) {
            headers.set('Authorization', `Bearer ${token}`);
        }
        return headers;
    }

    private async request<T>(url: string, config: ApiRequestConfig = {}): Promise<T> {
        const controller = new AbortController();
        const timeoutHandle = window.setTimeout(() => controller.abort(), this.timeout);
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
                headers: this.createHeaders(config.headers),
                signal: controller.signal,
            });

            const responseType = config.responseType ?? 'json';
            const contentType = response.headers.get('content-type') ?? '';
            const payload = responseType === 'blob'
                ? await response.blob()
                : responseType === 'text'
                    ? await response.text()
                    : contentType.includes('application/json')
                        ? await response.json()
                        : await response.text();

            if (!response.ok) {
                const details = typeof payload === 'object' && payload !== null ? payload as Record<string, unknown> : undefined;
                throw {
                    code: details?.code ?? 'UNKNOWN_ERROR',
                    message: (details?.message as string | undefined) ?? response.statusText,
                    details,
                    status: response.status,
                } as ApiError;
            }

            return payload as T;
        } catch (error) {
            if ((error as DOMException).name === 'AbortError') {
                throw {
                    code: 'TIMEOUT',
                    message: 'Request timed out',
                } as ApiError;
            }

            if ((error as ApiError).code) {
                throw error;
            }

            throw {
                code: 'NETWORK_ERROR',
                message: error instanceof Error ? error.message : 'Network error occurred',
            } as ApiError;
        } finally {
            window.clearTimeout(timeoutHandle);
        }
    }

    /**
     * GET request
     */
    async get<T>(url: string, config?: ApiRequestConfig): Promise<T> {
        return this.request<T>(url, {
            ...config,
            method: 'GET',
        });
    }

    /**
     * POST request
     */
    async post<T, D = unknown>(url: string, data?: D, config?: ApiRequestConfig): Promise<T> {
        return this.request<T>(url, {
            ...config,
            method: 'POST',
            body: data == null ? null : JSON.stringify(data),
        });
    }

    /**
     * PUT request
     */
    async put<T, D = unknown>(url: string, data?: D, config?: ApiRequestConfig): Promise<T> {
        return this.request<T>(url, {
            ...config,
            method: 'PUT',
            body: data == null ? null : JSON.stringify(data),
        });
    }

    /**
     * PATCH request
     */
    async patch<T, D = unknown>(url: string, data?: D, config?: ApiRequestConfig): Promise<T> {
        return this.request<T>(url, {
            ...config,
            method: 'PATCH',
            body: data == null ? null : JSON.stringify(data),
        });
    }

    /**
     * DELETE request
     */
    async delete<T>(url: string, config?: ApiRequestConfig): Promise<T> {
        return this.request<T>(url, {
            ...config,
            method: 'DELETE',
        });
    }
}

/**
 * Default API client instance
 */
export const apiClient = new ApiClient({
    baseUrl: import.meta.env.VITE_API_URL ?? '/api/v1',
});

export default apiClient;
