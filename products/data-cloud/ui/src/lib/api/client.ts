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

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';

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
 * Create configured axios instance
 */
function createAxiosInstance(config: ApiClientConfig): AxiosInstance {
    const instance = axios.create({
        baseURL: config.baseUrl,
        timeout: config.timeout ?? 30000,
        headers: {
            'Content-Type': 'application/json',
            ...config.headers,
        },
    });

    // Request interceptor for auth
    instance.interceptors.request.use(
        (requestConfig) => {
            // Add auth token if available
            const token = localStorage.getItem('auth_token');
            if (token && requestConfig.headers) {
                requestConfig.headers.Authorization = `Bearer ${token}`;
            }
            return requestConfig;
        },
        (error) => Promise.reject(error)
    );

    // Response interceptor for error handling
    instance.interceptors.response.use(
        (response) => response,
        (error) => {
            if (error.response) {
                const apiError: ApiError = {
                    code: error.response.data?.code ?? 'UNKNOWN_ERROR',
                    message: error.response.data?.message ?? error.message,
                    details: error.response.data?.details,
                };
                return Promise.reject(apiError);
            }
            return Promise.reject({
                code: 'NETWORK_ERROR',
                message: error.message ?? 'Network error occurred',
            });
        }
    );

    return instance;
}

/**
 * API Client class
 */
export class ApiClient {
    private axios: AxiosInstance;

    constructor(config: ApiClientConfig) {
        this.axios = createAxiosInstance(config);
    }

    /**
     * GET request
     */
    async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
        const response: AxiosResponse<T> = await this.axios.get(url, config);
        return response.data;
    }

    /**
     * POST request
     */
    async post<T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig): Promise<T> {
        const response: AxiosResponse<T> = await this.axios.post(url, data, config);
        return response.data;
    }

    /**
     * PUT request
     */
    async put<T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig): Promise<T> {
        const response: AxiosResponse<T> = await this.axios.put(url, data, config);
        return response.data;
    }

    /**
     * PATCH request
     */
    async patch<T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig): Promise<T> {
        const response: AxiosResponse<T> = await this.axios.patch(url, data, config);
        return response.data;
    }

    /**
     * DELETE request
     */
    async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
        const response: AxiosResponse<T> = await this.axios.delete(url, config);
        return response.data;
    }
}

/**
 * Default API client instance
 */
export const apiClient = new ApiClient({
    baseUrl: import.meta.env.VITE_API_URL ?? '/api/v1',
});

export default apiClient;
