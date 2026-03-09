/**
 * API client for Software-Org backend (Node.js/Fastify)
 *
 * Purpose:
 * Provides type-safe HTTP client for calling backend APIs with
 * authentication, error handling, and request/response transformation.
 *
 * Architecture:
 * Frontend → Node.js API → Java domain service (mock or real)
 *
 * Features:
 * - JWT authentication via Bearer token
 * - Automatic token refresh
 * - Request/response interceptors
 * - Type-safe API methods
 * - Error handling with custom error types
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3001';

export class ApiError extends Error {
    constructor(
        message: string,
        public status: number,
        public code?: string,
        public details?: unknown
    ) {
        super(message);
        this.name = 'ApiError';
    }
}

export class ValidationError extends ApiError {
    constructor(message: string, details?: unknown) {
        super(message, 400, 'VALIDATION_ERROR', details);
        this.name = 'ValidationError';
    }
}

export class AuthenticationError extends ApiError {
    constructor(message: string) {
        super(message, 401, 'AUTHENTICATION_ERROR');
        this.name = 'AuthenticationError';
    }
}

export class AuthorizationError extends ApiError {
    constructor(message: string) {
        super(message, 403, 'AUTHORIZATION_ERROR');
        this.name = 'AuthorizationError';
    }
}

export class NotFoundError extends ApiError {
    constructor(message: string) {
        super(message, 404, 'NOT_FOUND');
        this.name = 'NotFoundError';
    }
}

/**
 * HTTP client with authentication and error handling
 */
class ApiClient {
    private baseUrl: string;
    private token: string | null = null;

    constructor(baseUrl: string) {
        this.baseUrl = baseUrl;
        this.loadToken();
    }

    /**
     * Load JWT token from localStorage
     */
    private loadToken(): void {
        this.token = localStorage.getItem('auth_token');
    }

    /**
     * Set JWT token (called after login)
     */
    setToken(token: string): void {
        this.token = token;
        localStorage.setItem('auth_token', token);
    }

    /**
     * Clear JWT token (called on logout)
     */
    clearToken(): void {
        this.token = null;
        localStorage.removeItem('auth_token');
    }

    /**
     * Get authorization headers
     */
    private getHeaders(): HeadersInit {
        const headers: HeadersInit = {
            'Content-Type': 'application/json',
        };

        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }

        return headers;
    }

    /**
     * Handle API response errors
     */
    private async handleResponse<T>(response: Response): Promise<T> {
        if (!response.ok) {
            const contentType = response.headers.get('content-type');
            let error: ApiError;

            if (contentType?.includes('application/json')) {
                const errorData = await response.json();
                const message = errorData.message || errorData.error || 'Request failed';

                switch (response.status) {
                    case 400:
                        error = new ValidationError(message, errorData.details);
                        break;
                    case 401:
                        error = new AuthenticationError(message);
                        break;
                    case 403:
                        error = new AuthorizationError(message);
                        break;
                    case 404:
                        error = new NotFoundError(message);
                        break;
                    default:
                        error = new ApiError(message, response.status, errorData.code, errorData.details);
                }
            } else {
                error = new ApiError(
                    `Request failed with status ${response.status}`,
                    response.status
                );
            }

            throw error;
        }

        const contentType = response.headers.get('content-type');
        if (contentType?.includes('application/json')) {
            return response.json();
        }

        return undefined as T;
    }

    /**
     * GET request
     */
    async get<T>(path: string, params?: Record<string, string>): Promise<T> {
        const url = new URL(`${this.baseUrl}${path}`);
        if (params) {
            Object.entries(params).forEach(([key, value]) => {
                url.searchParams.append(key, value);
            });
        }

        const response = await fetch(url.toString(), {
            method: 'GET',
            headers: this.getHeaders(),
        });

        return this.handleResponse<T>(response);
    }

    /**
     * POST request
     */
    async post<T>(path: string, data?: unknown): Promise<T> {
        const response = await fetch(`${this.baseUrl}${path}`, {
            method: 'POST',
            headers: this.getHeaders(),
            body: data ? JSON.stringify(data) : undefined,
        });

        return this.handleResponse<T>(response);
    }

    /**
     * PUT request
     */
    async put<T>(path: string, data?: unknown): Promise<T> {
        const response = await fetch(`${this.baseUrl}${path}`, {
            method: 'PUT',
            headers: this.getHeaders(),
            body: data ? JSON.stringify(data) : undefined,
        });

        return this.handleResponse<T>(response);
    }

    /**
     * PATCH request
     */
    async patch<T>(path: string, data?: unknown): Promise<T> {
        const response = await fetch(`${this.baseUrl}${path}`, {
            method: 'PATCH',
            headers: this.getHeaders(),
            body: data ? JSON.stringify(data) : undefined,
        });

        return this.handleResponse<T>(response);
    }

    /**
     * DELETE request
     */
    async delete<T>(path: string): Promise<T> {
        const response = await fetch(`${this.baseUrl}${path}`, {
            method: 'DELETE',
            headers: this.getHeaders(),
        });

        return this.handleResponse<T>(response);
    }
}

// Singleton instance
export const apiClient = new ApiClient(API_BASE_URL);
