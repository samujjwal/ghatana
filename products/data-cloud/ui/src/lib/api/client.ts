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

    private static isRecord(value: unknown): value is Record<string, unknown> {
        return typeof value === 'object' && value !== null;
    }

    private static parseApiError(payload: unknown, response: Response): ApiError {
        const fallbackCode = response.status === 401
            ? 'AUTH_REQUIRED'
            : response.status === 403
                ? 'ACCESS_DENIED'
                : 'UNKNOWN_ERROR';

        if (ApiClient.isRecord(payload)) {
            const nestedError = ApiClient.isRecord(payload.error) ? payload.error : undefined;
            const message = typeof nestedError?.message === 'string'
                ? nestedError.message
                : typeof payload.message === 'string'
                    ? payload.message
                    : response.statusText;
            const code = typeof nestedError?.code === 'string'
                ? nestedError.code
                : typeof payload.code === 'string'
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
            };
        }

        return {
            code: 'UNKNOWN_ERROR',
            message: response.statusText,
            status: response.status,
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
        // Cookie-backed sessions are preferred for browser deployments.
        // Bearer headers are only injected when an explicit token is present.
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
                credentials: config.credentials ?? 'include',
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
                throw ApiClient.parseApiError(payload, response);
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
