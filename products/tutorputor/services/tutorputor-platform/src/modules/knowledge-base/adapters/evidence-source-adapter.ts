/**
 * Evidence Source Adapter Interface
 *
 * Task 1.3: Implement Evidence Retrieval Adapters
 *
 * @doc.type module
 * @doc.purpose Interface for evidence source adapters
 * @doc.layer adapter
 * @doc.pattern Adapter
 */

import type { EvidenceSourceType, LearningEvidence } from '../evidence-bundle';

/**
 * Search result from an evidence source.
 */
export interface EvidenceSearchResult {
  sourceUrl: string;
  title: string;
  publisher?: string;
  publicationDate?: Date;
  excerpt: string;
  relevanceScore: number; // 0.0 - 1.0
}

/**
 * Content retrieved from an evidence source.
 */
export interface RetrievedContent {
  url: string;
  title: string;
  publisher?: string;
  publicationDate?: Date;
  content: string;
  citations?: string[];
  qualityIndicators?: {
    isFeatured?: boolean;
    isGoodArticle?: boolean;
    citationCount?: number;
  };
}

/**
 * Adapter interface for evidence sources.
 */
export interface EvidenceSourceAdapter {
  /**
   * Get the source type this adapter handles.
   */
  getSourceType(): EvidenceSourceType;

  /**
   * Search the source for relevant content.
   */
  search(
    query: string,
    domain: string,
    options?: { maxResults?: number; gradeBand?: string }
  ): Promise<EvidenceSearchResult[]>;

  /**
   * Retrieve full content from a URL.
   */
  retrieveContent(url: string): Promise<RetrievedContent | null>;

  /**
   * Check if the source is available.
   */
  healthCheck(): Promise<boolean>;
}

/**
 * Error thrown when evidence source operations fail.
 */
export class EvidenceSourceError extends Error {
  constructor(
    message: string,
    public readonly sourceType: EvidenceSourceType,
    public readonly operation: 'search' | 'retrieve' | 'health',
    public readonly underlyingError?: Error
  ) {
    super(`[${sourceType}] ${operation} failed: ${message}`);
    Object.setPrototypeOf(this, EvidenceSourceError.prototype);
  }
}
