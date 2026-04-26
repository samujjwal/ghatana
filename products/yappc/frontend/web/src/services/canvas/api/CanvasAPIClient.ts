/**
 * Canvas API Client
 * 
 * HTTP client for syncing canvas data with backend server.
 * Handles authentication, retry logic, and error handling.
 * Uses cookie-based authentication (credentials: include).
 * 
 * @doc.type service
 * @doc.purpose Backend API integration for canvas persistence
 * @doc.layer product
 * @doc.pattern Repository Pattern
 * @doc.security Cookie-based auth, no localStorage tokens
 */

import { parseJsonResponse } from '@/lib/http';

// Local type definitions (mirror of canonical schema)
// Source of truth: apps/api/src/domain/canvas/canvas-schema.ts

export interface CanvasPosition {
  x: number;
  y: number;
}

export interface CanvasSize {
  width: number;
  height: number;
}

export interface CanvasViewport {
  x: number;
  y: number;
  zoom: number;
}

export interface CanvasNode {
  id: string;
  kind: 'node';
  type: string;
  position: CanvasPosition;
  size?: CanvasSize;
  data?: Record<string, unknown>;
  label?: string;
  description?: string;
}

export interface CanvasEdge {
  id: string;
  kind: 'edge';
  type: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  label?: string;
  data?: Record<string, unknown>;
}

export interface CanvasState {
  projectId: string;
  canvasId: string;
  nodes: CanvasNode[];
  connections: CanvasEdge[];
  groups: unknown[];
  layers: unknown[];
  viewport: CanvasViewport;
  lastSaved?: string | null;
}

export interface CanvasSnapshot {
  id: string;
  projectId: string;
  canvasId: string;
  version: number;
  timestamp: number;
  data: CanvasState;
  checksum: string;
  label?: string;
  description?: string;
  author?: string;
  tags?: string[];
}

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

async function readApiErrorResponse(
    response: Response,
): Promise<{ message?: string; code?: string }> {
    const raw = await response.text();

    if (!raw) {
        return {};
    }

    try {
        return JSON.parse(raw) as { message?: string; code?: string };
    } catch {
        return { message: raw.trim() || undefined };
    }
}

export class CanvasAPIClient {
    private readonly config: Required<APIConfig>;

    constructor(config: APIConfig & { baseURL?: string; maxRetries?: number }) {
        const baseUrl = config.baseURL ?? config.baseUrl ?? '';
        const retryAttempts = config.maxRetries ?? config.retryAttempts ?? 3;
        const timeout = config.timeout ?? 30000;
        const retryDelay = config.retryDelay ?? 1000;
        
        this.config = {
            baseUrl,
            retryAttempts,
            timeout,
            retryDelay,
        };
    }

    /**
     * Save snapshot to backend
     */
    public async saveSnapshot(snapshot: CanvasSnapshot): Promise<unknown> {
        return this.retry(async () => {
            const response = await this.fetch('/api/canvas/snapshots', {
                method: 'POST',
                body: JSON.stringify(snapshot),
            });

            if (!response.ok) {
                throw await this.createError(response);
            }

            try {
                return await parseJsonResponse<unknown>(response, 'save snapshot');
            } catch {
                return undefined;
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
                throw await this.createError(response);
            }

            return parseJsonResponse<CanvasSnapshot>(response, 'load snapshot');
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
                throw await this.createError(response);
            }

            return parseJsonResponse<CanvasSnapshot[]>(response, 'list snapshots');
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
                throw await this.createError(response);
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

            const data = await parseJsonResponse<{ lastSync?: number }>(
                response,
                'get last sync'
            );
            return data.lastSync || null;
        } catch (error) {
            return null;
        }
    }

    /**
     * Batch save multiple snapshots
     */
    public async batchSave(snapshots: CanvasSnapshot[]): Promise<unknown> {
        return this.retry(async () => {
            const response = await this.fetch('/api/canvas/snapshots/batch', {
                method: 'POST',
                body: JSON.stringify({ snapshots }),
            });

            if (!response.ok) {
                throw await this.createError(response);
            }

            try {
                return await parseJsonResponse<unknown>(response, 'batch save snapshots');
            } catch {
                return undefined;
            }
        });
    }

    /**
     * Fetch with timeout and cookie auth
     * Uses credentials: 'include' to send httpOnly cookies automatically
     */
    private async fetch(path: string, options: RequestInit = {}): Promise<Response> {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);

        try {
            const response = await fetch(`${this.config.baseUrl}${path}`, {
                ...options,
                credentials: 'include', // Send httpOnly cookies
                headers: {
                    'Content-Type': 'application/json',
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
                    const status = (error as unknown as { status: number }).status;
                    if (status >= 400 && status < 500) {
                        throw error;
                    }
                } else if (error !== null && typeof error === 'object' && 'status' in error) {
                    const status = (error as { status: number }).status;
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
     * Create API error from response
     */
    private async createError(response: Response): Promise<APIError> {
        let message = response.statusText;
        let code: string | undefined;

        const data = await readApiErrorResponse(response);
        message = data.message || message;
        code = data.code;

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
