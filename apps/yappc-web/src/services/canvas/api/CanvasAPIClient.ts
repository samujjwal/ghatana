/**
 * Canvas API Client
 * 
 * HTTP client for syncing canvas data with backend server.
 * Handles authentication, retry logic, and error handling.
 * 
 * @doc.type service
 * @doc.purpose Backend API integration for canvas persistence
 * @doc.layer product
 * @doc.pattern Repository Pattern
 */

import type { CanvasSnapshot } from '../CanvasPersistence';

export interface APIConfig {
    baseUrl: string;
    timeout?: number;
    retryAttempts?: number;
    retryDelay?: number;
}

export interface APIError {
    status: number;
    message: string;
    code?: string;
}

export class CanvasAPIClient {
    private readonly config: Required<APIConfig>;

    constructor(config: APIConfig) {
        this.config = {
            timeout: 30000,
            retryAttempts: 3,
            retryDelay: 1000,
            ...config,
        };
    }

    /**
     * Save snapshot to backend
     */
    public async saveSnapshot(snapshot: CanvasSnapshot): Promise<void> {
        return this.retry(async () => {
            const response = await this.fetch('/api/canvas/snapshots', {
                method: 'POST',
                body: JSON.stringify(snapshot),
            });

            if (!response.ok) {
                throw this.createError(response);
            }
        });
    }

    /**
     * Load snapshot from backend
     */
    public async loadSnapshot(id: string): Promise<CanvasSnapshot | null> {
        return this.retry(async () => {
            const response = await this.fetch(`/api/canvas/snapshots/${id}`);

            if (response.status === 404) {
                return null;
            }

            if (!response.ok) {
                throw this.createError(response);
            }

            return response.json();
        });
    }

    /**
     * List snapshots for a project/canvas
     */
    public async listSnapshots(
        projectId: string,
        canvasId: string
    ): Promise<CanvasSnapshot[]> {
        return this.retry(async () => {
            const params = new URLSearchParams({ projectId, canvasId });
            const response = await this.fetch(`/api/canvas/snapshots?${params}`);

            if (!response.ok) {
                throw this.createError(response);
            }

            return response.json();
        });
    }

    /**
     * Delete snapshot from backend
     */
    public async deleteSnapshot(id: string): Promise<void> {
        return this.retry(async () => {
            const response = await this.fetch(`/api/canvas/snapshots/${id}`, {
                method: 'DELETE',
            });

            if (!response.ok) {
                throw this.createError(response);
            }
        });
    }

    /**
     * Check if snapshot exists on backend
     */
    public async exists(id: string): Promise<boolean> {
        try {
            const response = await this.fetch(`/api/canvas/snapshots/${id}`, {
                method: 'HEAD',
            });
            return response.ok;
        } catch (error) {
            return false;
        }
    }

    /**
     * Get last sync timestamp for a canvas
     */
    public async getLastSync(projectId: string, canvasId: string): Promise<number | null> {
        try {
            const response = await this.fetch(
                `/api/canvas/sync-status?projectId=${projectId}&canvasId=${canvasId}`
            );

            if (!response.ok) return null;

            const data = await response.json();
            return data.lastSync || null;
        } catch (error) {
            return null;
        }
    }

    /**
     * Batch save multiple snapshots
     */
    public async batchSave(snapshots: CanvasSnapshot[]): Promise<void> {
        return this.retry(async () => {
            const response = await this.fetch('/api/canvas/snapshots/batch', {
                method: 'POST',
                body: JSON.stringify({ snapshots }),
            });

            if (!response.ok) {
                throw this.createError(response);
            }
        });
    }

    /**
     * Fetch with timeout and auth
     */
    private async fetch(path: string, options: RequestInit = {}): Promise<Response> {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);

        try {
            const response = await fetch(`${this.config.baseUrl}${path}`, {
                ...options,
                headers: {
                    'Content-Type': 'application/json',
                    ...this.getAuthHeaders(),
                    ...options.headers,
                },
                signal: controller.signal,
            });

            return response;
        } finally {
            clearTimeout(timeoutId);
        }
    }

    /**
     * Retry logic for transient failures
     */
    private async retry<T>(fn: () => Promise<T>): Promise<T> {
        let lastError: Error | null = null;

        for (let attempt = 0; attempt < this.config.retryAttempts; attempt++) {
            try {
                return await fn();
            } catch (error) {
                lastError = error as Error;

                // Don't retry on client errors (4xx)
                if (error instanceof Error && 'status' in error) {
                    const status = (error as unknown).status;
                    if (status >= 400 && status < 500) {
                        throw error;
                    }
                }

                // Wait before retrying (exponential backoff)
                if (attempt < this.config.retryAttempts - 1) {
                    const delay = this.config.retryDelay * Math.pow(2, attempt);
                    await this.sleep(delay);
                }
            }
        }

        throw lastError;
    }

    /**
     * Get authentication headers
     */
    private getAuthHeaders(): Record<string, string> {
        const headers: Record<string, string> = {};

        // Get JWT token from localStorage or auth service
        const token = this.getAuthToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        // Add API key if available
        const apiKey = this.getApiKey();
        if (apiKey) {
            headers['X-API-Key'] = apiKey;
        }

        // Add content type
        headers['Content-Type'] = 'application/json';

        return headers;
    }

    /**
     * Get authentication token from various sources
     */
    private getAuthToken(): string | null {
        // Try localStorage first
        const localToken = localStorage.getItem('auth_token');
        if (localToken) return localToken;

        // Try sessionStorage
        const sessionToken = sessionStorage.getItem('auth_token');
        if (sessionToken) return sessionToken;

        // Try cookie (if accessible)
        const cookies = document.cookie.split(';');
        for (const cookie of cookies) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'auth_token') return value;
        }

        return null;
    }

    /**
     * Get API key from environment or storage
     */
    private getApiKey(): string | null {
        // Try environment variable (in development)
        if (import.meta.env.VITE_API_KEY) {
            return import.meta.env.VITE_API_KEY;
        }

        // Try localStorage
        const storedKey = localStorage.getItem('api_key');
        if (storedKey) return storedKey;

        return null;
    }

    /**
     * Create API error from response
     */
    private async createError(response: Response): Promise<APIError> {
        let message = response.statusText;
        let code: string | undefined;

        try {
            const data = await response.json();
            message = data.message || message;
            code = data.code;
        } catch {
            // Ignore JSON parse errors
        }

        return {
            status: response.status,
            message,
            code,
        };
    }

    /**
     * Sleep helper
     */
    private sleep(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
