/**
 * OpenStax Evidence Source Adapter
 *
 * Task 1.3: Implement Evidence Retrieval Adapters - OpenStax
 *
 * @doc.type module
 * @doc.purpose Adapter for OpenStax textbook content
 * @doc.layer adapter
 * @doc.pattern Adapter
 */

import type {
  EvidenceSourceAdapter,
  EvidenceSearchResult,
  RetrievedContent,
} from './evidence-source-adapter';
import { EvidenceSourceError } from './evidence-source-adapter';
import type { EvidenceSourceType } from '../evidence-bundle';

/**
 * Configuration for OpenStax adapter.
 */
export interface OpenStaxConfig {
  baseUrl: string;
  apiKey?: string;
  cacheEnabled: boolean;
  rateLimitPerMinute: number;
}

/**
 * OpenStax book mapping for different domains.
 */
const DOMAIN_BOOKS: Record<string, string[]> = {
  'physics': ['physics', 'university-physics', 'college-physics'],
  'algebra': ['elementary-algebra', 'intermediate-algebra', 'college-algebra'],
  'calculus': ['calculus-volumes-1-3', 'calculus-volume-1', 'calculus-volume-2'],
  'chemistry': ['chemistry', 'chemistry-atoms-first'],
  'biology': ['biology', 'concepts-biology'],
  'economics': ['economics', 'macroeconomics', 'microeconomics'],
};

/**
 * Adapter for OpenStax textbook content.
 */
export class OpenStaxAdapter implements EvidenceSourceAdapter {
  private requestTimestamps: number[] = [];
  private cache = new Map<string, RetrievedContent>();

  constructor(private readonly config: OpenStaxConfig) {}

  getSourceType(): EvidenceSourceType {
    return 'OPENSTAX';
  }

  async search(
    query: string,
    domain: string,
    options?: { maxResults?: number; gradeBand?: string }
  ): Promise<EvidenceSearchResult[]> {
    // Prevent mock generator in production
    if (process.env.NODE_ENV === 'production') {
      throw new EvidenceSourceError(
        'Mock OpenStax adapter cannot be used in production. Configure real API credentials.',
        'OPENSTAX',
        'search',
        new Error('PRODUCTION_GUARD_VIOLATION')
      );
    }

    try {
      await this.enforceRateLimit();

      const maxResults = options?.maxResults ?? 5;
      const books = DOMAIN_BOOKS[domain.toLowerCase()] ?? [];

      // In a real implementation, this would call the OpenStax API
      // For now, return mock results for demonstration
      const results: EvidenceSearchResult[] = [];

      for (const bookSlug of books.slice(0, 2)) {
        results.push({
          sourceUrl: `${this.config.baseUrl}/books/${bookSlug}/pages/search?q=${encodeURIComponent(query)}`,
          title: `OpenStax ${this.capitalize(bookSlug)} - ${query}`,
          publisher: 'OpenStax',
          excerpt: `Relevant content about ${query} from ${bookSlug}`,
          relevanceScore: 0.8,
        });
      }

      // If no domain-specific books, provide generic result
      if (results.length === 0) {
        results.push({
          sourceUrl: `${this.config.baseUrl}/search?q=${encodeURIComponent(query)}`,
          title: `OpenStax Search: ${query}`,
          publisher: 'OpenStax',
          excerpt: `Search results for ${query}`,
          relevanceScore: 0.6,
        });
      }

      return results.slice(0, maxResults);
    } catch (error) {
      throw new EvidenceSourceError(
        error instanceof Error ? error.message : 'Unknown error',
        'OPENSTAX',
        'search',
        error instanceof Error ? error : undefined
      );
    }
  }

  async retrieveContent(url: string): Promise<RetrievedContent | null> {
    // Prevent mock generator in production
    if (process.env.NODE_ENV === 'production') {
      throw new EvidenceSourceError(
        'Mock OpenStax adapter cannot be used in production. Configure real API credentials.',
        'OPENSTAX',
        'retrieve',
        new Error('PRODUCTION_GUARD_VIOLATION')
      );
    }

    try {
      // Check cache first
      if (this.config.cacheEnabled) {
        const cached = this.cache.get(url);
        if (cached) return cached;
      }

      await this.enforceRateLimit();

      // In a real implementation, fetch from OpenStax API
      // For now, return mock content
      const content: RetrievedContent = {
        url,
        title: this.extractTitleFromUrl(url),
        publisher: 'OpenStax',
        content: `This is educational content from OpenStax about ${this.extractTitleFromUrl(url)}. ` +
                 `OpenStax is a nonprofit educational initiative based at Rice University, ` +
                 `providing freely accessible, peer-reviewed textbooks.`,
        qualityIndicators: {
          isFeatured: true,
          citationCount: 10,
        },
      };

      if (this.config.cacheEnabled) {
        this.cache.set(url, content);
      }

      return content;
    } catch (error) {
      throw new EvidenceSourceError(
        error instanceof Error ? error.message : 'Unknown error',
        'OPENSTAX',
        'retrieve',
        error instanceof Error ? error : undefined
      );
    }
  }

  async healthCheck(): Promise<boolean> {
    try {
      // In a real implementation, ping OpenStax health endpoint
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Enforce rate limiting.
   */
  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();
    const oneMinuteAgo = now - 60000;

    // Remove timestamps older than 1 minute
    this.requestTimestamps = this.requestTimestamps.filter(t => t > oneMinuteAgo);

    // If at limit, wait
    if (this.requestTimestamps.length >= this.config.rateLimitPerMinute) {
      const oldestTimestamp = this.requestTimestamps[0] ?? now;
      const waitTime = 60000 - (now - oldestTimestamp);
      if (waitTime > 0) {
        await new Promise(resolve => setTimeout(resolve, waitTime));
      }
    }

    this.requestTimestamps.push(now);
  }

  /**
   * Extract a readable title from a URL.
   */
  private extractTitleFromUrl(url: string): string {
    const match = url.match(/\/([^/]+)$/);
    const slug = match?.[1] ?? 'OpenStax Content';
    return this.capitalize(slug.replace(/-/g, ' '));
  }

  /**
   * Capitalize first letter of each word.
   */
  private capitalize(str: string): string {
    return str.replace(/\b\w/g, c => c.toUpperCase());
  }
}
