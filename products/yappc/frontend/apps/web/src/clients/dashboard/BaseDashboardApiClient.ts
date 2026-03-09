/**
 * Base Dashboard API Client
 * 
 * Provides common functionality for all dashboard API clients:
 * - HTTP request handling with axios
 * - Retry logic with exponential backoff
 * - Error handling and transformation
 * - Request/response logging
 * - Tenant context injection
 * - Mock mode for testing
 * 
 * @doc.type class
 * @doc.purpose Base HTTP client for dashboard APIs
 * @doc.layer product
 * @doc.pattern Service
 */

import axios, { AxiosInstance, AxiosError } from 'axios';
import type { DashboardApiConfig, ApiResponse, ApiError } from './types';

/**
 * Default configuration
 */
const DEFAULT_CONFIG: Partial<DashboardApiConfig> = {
    timeout: 10000,
    maxRetries: 3,
    retryBackoffMultiplier: 2,
    logRequests: false,
    logResponses: false,
};

/**
 * Client operation mode
 */
export type ClientMode = 'http' | 'mock';

/**
 * Base class for all Dashboard API clients
 */
export abstract class BaseDashboardApiClient {
    protected httpClient: AxiosInstance;
    protected config: Required<DashboardApiConfig>;
    protected mode: ClientMode = 'http';
    protected mockResponses: Map<string, unknown> = new Map();

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        this.config = {
            ...DEFAULT_CONFIG,
            ...config,
            tenantId: config.tenantId || '',
            authToken: config.authToken || '',
        } as Required<DashboardApiConfig>;
        this.mode = mode;

        this.httpClient = axios.create({
            baseURL: this.config.baseUrl,
            timeout: this.config.timeout,
            headers: {
                'Content-Type': 'application/json',
                'User-Agent': 'YAPPC-Dashboard/1.0',
            },
        });

        // Add request interceptor for auth and tenant
        this.httpClient.interceptors.request.use(
            (config) => {
                if (this.config.authToken) {
                    config.headers.Authorization = `Bearer ${this.config.authToken}`;
                }
                if (this.config.tenantId) {
                    config.headers['X-Tenant-Id'] = this.config.tenantId;
                }
                if (this.config.logRequests) {
                    console.log(`[Dashboard API] ${config.method?.toUpperCase()} ${config.url}`, config.data);
                }
                return config;
            },
            (error) => Promise.reject(error)
        );

        // Add response interceptor for logging and error handling
        this.httpClient.interceptors.response.use(
            (response) => {
                if (this.config.logResponses) {
                    console.log(`[Dashboard API] Response:`, response.data);
                }
                return response;
            },
            (error) => this.handleError(error)
        );
    }

    /**
     * Set client mode
     */
    setMode(mode: ClientMode): void {
        this.mode = mode;
    }

    /**
     * Get current mode
     */
    getMode(): ClientMode {
        return this.mode;
    }

    /**
     * Update auth token
     */
    setAuthToken(token: string): void {
        this.config.authToken = token;
    }

    /**
     * Update tenant ID
     */
    setTenantId(tenantId: string): void {
        this.config.tenantId = tenantId;
    }

    /**
     * Register mock response for testing
     */
    registerMock<T>(endpoint: string, response: T): void {
        this.mockResponses.set(endpoint, response);
    }

    /**
     * Clear all mock responses
     */
    clearMocks(): void {
        this.mockResponses.clear();
    }

    /**
     * Execute GET request with retry
     */
    protected async get<T>(
        endpoint: string,
        params?: Record<string, unknown>
    ): Promise<ApiResponse<T>> {
        if (this.mode === 'mock') {
            return this.getMockResponse<T>(endpoint);
        }
        return this.executeWithRetry<T>(() =>
            this.httpClient.get<ApiResponse<T>>(endpoint, { params })
        );
    }

    /**
     * Execute POST request with retry
     */
    protected async post<T>(
        endpoint: string,
        data?: unknown
    ): Promise<ApiResponse<T>> {
        if (this.mode === 'mock') {
            return this.getMockResponse<T>(endpoint);
        }
        return this.executeWithRetry<T>(() =>
            this.httpClient.post<ApiResponse<T>>(endpoint, data)
        );
    }

    /**
     * Execute PUT request with retry
     */
    protected async put<T>(
        endpoint: string,
        data?: unknown
    ): Promise<ApiResponse<T>> {
        if (this.mode === 'mock') {
            return this.getMockResponse<T>(endpoint);
        }
        return this.executeWithRetry<T>(() =>
            this.httpClient.put<ApiResponse<T>>(endpoint, data)
        );
    }

    /**
     * Execute DELETE request with retry
     */
    protected async delete<T>(endpoint: string): Promise<ApiResponse<T>> {
        if (this.mode === 'mock') {
            return this.getMockResponse<T>(endpoint);
        }
        return this.executeWithRetry<T>(() =>
            this.httpClient.delete<ApiResponse<T>>(endpoint)
        );
    }

    /**
     * Execute request with exponential backoff retry
     */
    private async executeWithRetry<T>(
        requestFn: () => Promise<{ data: ApiResponse<T> }>,
        attempt: number = 1
    ): Promise<ApiResponse<T>> {
        try {
            const response = await requestFn();
            return response.data;
        } catch (error) {
            if (attempt < this.config.maxRetries && this.isRetryable(error)) {
                const delay = Math.pow(this.config.retryBackoffMultiplier, attempt) * 1000;
                await this.sleep(delay);
                return this.executeWithRetry(requestFn, attempt + 1);
            }
            throw error;
        }
    }

    /**
     * Check if error is retryable
     */
    private isRetryable(error: unknown): boolean {
        if (axios.isAxiosError(error)) {
            const status = error.response?.status;
            // Retry on network errors, timeouts, and 5xx errors
            return !status || status >= 500 || error.code === 'ECONNABORTED';
        }
        return false;
    }

    /**
     * Handle axios errors
     */
    private handleError(error: AxiosError): Promise<never> {
        const apiError: ApiError = {
            code: error.code || 'UNKNOWN_ERROR',
            message: error.message,
            details: {
                status: error.response?.status,
                data: error.response?.data,
            },
        };

        if (error.response) {
            switch (error.response.status) {
                case 400:
                    apiError.code = 'BAD_REQUEST';
                    apiError.message = 'Invalid request parameters';
                    break;
                case 401:
                    apiError.code = 'UNAUTHORIZED';
                    apiError.message = 'Authentication required';
                    break;
                case 403:
                    apiError.code = 'FORBIDDEN';
                    apiError.message = 'Access denied';
                    break;
                case 404:
                    apiError.code = 'NOT_FOUND';
                    apiError.message = 'Resource not found';
                    break;
                case 429:
                    apiError.code = 'RATE_LIMITED';
                    apiError.message = 'Too many requests';
                    break;
                default:
                    if (error.response.status >= 500) {
                        apiError.code = 'SERVER_ERROR';
                        apiError.message = 'Server error occurred';
                    }
            }
        } else if (error.request) {
            apiError.code = 'NETWORK_ERROR';
            apiError.message = 'Network error - please check your connection';
        }

        console.error('[Dashboard API] Error:', apiError);
        return Promise.reject(apiError);
    }

    /**
     * Get mock response
     */
    private getMockResponse<T>(endpoint: string): ApiResponse<T> {
        const mockData = this.mockResponses.get(endpoint);
        if (mockData) {
            return {
                success: true,
                data: mockData as T,
                timestamp: new Date().toISOString(),
            };
        }
        return {
            success: false,
            error: {
                code: 'MOCK_NOT_FOUND',
                message: `No mock registered for endpoint: ${endpoint}`,
            },
            timestamp: new Date().toISOString(),
        };
    }

    /**
     * Sleep utility for retry backoff
     */
    private sleep(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
