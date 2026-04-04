/**
 * Shared types index - exports all type definitions
 */

export * from "./user";
export * from "./sphere";
export * from "./moment";
export * from "./media";

// Re-export commonly used types
export type { SphereType, SphereVisibility, SphereRole } from "./sphere";
export type { ContentType } from "./moment";

/**
 * Enhanced AI Search types (vector embeddings + hybrid search)
 */
export type SearchType = "semantic" | "text" | "hybrid" | "similar";

export interface SearchParams {
  query: string;
  type?: SearchType;
  filters?: {
    sphereIds?: string[];
    emotions?: string[];
    tags?: string[];
    importance?: {
      min?: number;
      max?: number;
    };
    dateRange?: {
      from?: string;
      to?: string;
    };
    contentTypes?: Array<"text" | "audio" | "video">;
    hasTranscript?: boolean;
    hasReflection?: boolean;
  };
  limit?: number;
  offset?: number;
  includeHighlights?: boolean;
  includeReflections?: boolean;
  similarityThreshold?: number;
  boostFactors?: {
    recency?: number;
    importance?: number;
    emotion?: number;
  };
}

export interface SearchResultItem {
  momentId: string;
  title: string;
  content: string;
  transcript?: string;
  sphereId: string;
  sphereName: string;
  capturedAt: string;
  emotions: string[];
  tags: string[];
  importance?: number;
  score: number;
  similarity?: number;
  highlights?: {
    content?: string[];
    transcript?: string[];
  };
  reflection?: {
    insights: string[];
    themes: string[];
  };
}

export interface SearchResult {
  results: SearchResultItem[];
  totalCount: number;
  analytics?: {
    processingTimeMs: number;
    resultCount: number;
    type: string;
  };
  items?: SearchResultItem[];
  nextCursor?: string | null;
  total?: number;
}

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
