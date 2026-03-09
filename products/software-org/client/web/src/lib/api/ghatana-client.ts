/**
 * Software-Org API Client using @ghatana/api
 *
 * @doc.type utility
 * @doc.purpose Centralized API client using platform @ghatana/api library
 * @doc.layer product
 * @doc.pattern Factory Pattern
 *
 * Purpose:
 * Replaces custom axios/fetch implementations with the platform-standard
 * @ghatana/api client. Provides middleware for auth, tenant headers, tracing,
 * and error handling following the reuse-first policy.
 *
 * Features:
 * - JWT authentication via Bearer token
 * - Automatic tenant header injection (X-Tenant-ID)
 * - Request tracing (X-Trace-ID)
 * - Retry logic with exponential backoff
 * - Type-safe error handling
 * - Mock mode support for development
 *
 * Architecture:
 * Frontend → @ghatana/api → Node.js API → Java domain service
 */

import { ApiClient, ApiRequest, ApiResponse, ApiError } from '@ghatana/api';

/**
 * Custom error types for Software-Org
 */
export class SoftwareOrgApiError extends Error {
    constructor(
        message: string,
        public status: number,
        public code?: string,
        public details?: unknown
    ) {
        super(message);
        this.name = 'SoftwareOrgApiError';
    }
}

export class ValidationError extends SoftwareOrgApiError {
    constructor(message: string, details?: unknown) {
        super(message, 400, 'VALIDATION_ERROR', details);
        this.name = 'ValidationError';
    }
}

export class AuthenticationError extends SoftwareOrgApiError {
    constructor(message: string) {
        super(message, 401, 'AUTHENTICATION_ERROR');
        this.name = 'AuthenticationError';
    }
}

export class AuthorizationError extends SoftwareOrgApiError {
    constructor(message: string) {
        super(message, 403, 'AUTHORIZATION_ERROR');
        this.name = 'AuthorizationError';
    }
}

export class NotFoundError extends SoftwareOrgApiError {
    constructor(message: string) {
        super(message, 404, 'NOT_FOUND');
        this.name = 'NotFoundError';
    }
}

/**
 * Environment configuration
 */
interface EnvironmentConfig {
    apiUrl: string;
    useMocks: boolean;
    enableTracing: boolean;
}

function getEnvironmentConfig(): EnvironmentConfig {
    // Note: Vite requires static access to import.meta.env properties for SSR compatibility
    const useMocks = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';

    // In mock mode, use relative URL for MSW interception
    // Otherwise, use configured backend URL
    const apiUrl = useMocks
        ? '/api/v1'
        : import.meta.env.VITE_API_URL || import.meta.env.REACT_APP_API_URL || 'http://localhost:3001/api/v1';

    const enableTracing = import.meta.env.VITE_ENABLE_TRACING !== 'false';

    return { apiUrl, useMocks, enableTracing };
}

/**
 * Generate unique trace ID for request correlation
 */
function generateTraceId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Get current tenant from localStorage or default
 */
function getCurrentTenant(): string {
    return localStorage.getItem('software-org:tenant') || 'all-tenants';
}

/**
 * Get JWT token from localStorage
 */
function getAuthToken(): string | null {
    return localStorage.getItem('auth_token');
}

/**
 * Set JWT token in localStorage
 */
export function setAuthToken(token: string): void {
    localStorage.setItem('auth_token', token);
}

/**
 * Clear JWT token from localStorage
 */
export function clearAuthToken(): void {
    localStorage.removeItem('auth_token');
}

/**
 * Wait for MSW to be ready (if using mocks)
 */
async function ensureMswReady(useMocks: boolean): Promise<void> {
    if (!useMocks) return;

    if ((window as any).__MSW_ACTIVE__) {
        return;
    }

    return new Promise((resolve) => {
        const start = Date.now();
        const timeout = 10000; // 10 seconds
        const interval = setInterval(() => {
            if ((window as any).__MSW_ACTIVE__) {
                clearInterval(interval);
                resolve();
            } else if (Date.now() - start > timeout) {
                console.warn('[API] MSW did not activate within timeout - proceeding anyway');
                clearInterval(interval);
                resolve();
            }
        }, 50);
    });
}

/**
 * Create and configure the Software-Org API client
 */
function createSoftwareOrgApiClient(): ApiClient {
    const config = getEnvironmentConfig();

    // Log configuration in development
    if (typeof window !== 'undefined') {
        console.log(
            `[API] Configuration: baseURL=${config.apiUrl}, useMocks=${config.useMocks}`,
            config.useMocks
                ? '(MSW mocks enabled - requests will be intercepted)'
                : '(connecting to real backend)'
        );
    }

    const client = new ApiClient({
        baseUrl: config.apiUrl,
        defaultHeaders: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        },
        timeoutMs: 30000, // 30 seconds
        retry: {
            attempts: 3,
            backoffMs: 250,
        },
    });

    // Request middleware: Add auth, tenant, and tracing headers
    client.useRequest(async (request: ApiRequest): Promise<ApiRequest> => {
        // Wait for MSW if using mocks
        await ensureMswReady(config.useMocks);

        const headers = { ...(request.headers || {}) };

        // Add authentication token
        const token = getAuthToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        // Add tenant header
        const tenant = getCurrentTenant();
        headers['X-Tenant-ID'] = tenant;

        // Add trace ID for observability
        if (config.enableTracing) {
            headers['X-Trace-ID'] = generateTraceId();
        }

        return {
            ...request,
            headers,
        };
    });

    // Response middleware: Handle errors and transform responses
    client.useResponse(async (response: ApiResponse<unknown>, request: ApiRequest): Promise<ApiResponse<unknown>> => {
        // Handle error responses
        if (response.status >= 400) {
            const data = response.data as any;
            const message = data?.message || data?.error || 'Request failed';

            let error: SoftwareOrgApiError;

            switch (response.status) {
                case 400:
                    error = new ValidationError(message, data?.details);
                    break;
                case 401:
                    error = new AuthenticationError(message);
                    // Clear token on authentication failure
                    clearAuthToken();
                    break;
                case 403:
                    error = new AuthorizationError(message);
                    break;
                case 404:
                    error = new NotFoundError(message);
                    break;
                default:
                    error = new SoftwareOrgApiError(
                        message,
                        response.status,
                        data?.code,
                        data?.details
                    );
            }

            // Log error with trace context
            console.error('[API Error]', {
                status: response.status,
                message: error.message,
                traceId: request.headers?.['X-Trace-ID'],
                url: request.url,
                method: request.method,
            });

            throw error;
        }

        return response;
    });

    return client;
}

/**
 * Singleton API client instance
 */
export const softwareOrgApi = createSoftwareOrgApiClient();

/**
 * Helper function to handle connection errors with mock fallback
 */
export function isConnectionError(error: unknown): boolean {
    if (error instanceof TypeError && error.message.includes('fetch')) {
        return true;
    }
    if (error instanceof Error) {
        const msg = error.message.toLowerCase();
        return msg.includes('network') || msg.includes('connection') || msg.includes('fetch');
    }
    return false;
}

/**
 * Helper to extract data from API response
 */
export function extractData<T>(response: ApiResponse<T>): T {
    return response.data;
}

/**
 * Re-export types for convenience
 */
export type { ApiRequest, ApiResponse, ApiError } from '@ghatana/api';
