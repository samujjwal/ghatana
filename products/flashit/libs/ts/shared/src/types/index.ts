/**
 * Shared types index - exports all type definitions
 */

export * from './user';
export * from './sphere';
export * from './moment';
export * from './media';

// Re-export commonly used types
export type { SphereType, SphereVisibility, SphereRole } from './sphere';
export type { ContentType } from './moment';

/**
 * Common API response types
 */
export interface ApiError {
    error: string;
    message: string;
    details?: unknown;
}

export interface ApiResponse<T> {
    data: T;
    error?: ApiError;
}

export interface PaginatedResponse<T> {
    items: T[];
    total: number;
    limit: number;
    offset?: number;
    cursor?: string;
    nextCursor?: string | null;
}
