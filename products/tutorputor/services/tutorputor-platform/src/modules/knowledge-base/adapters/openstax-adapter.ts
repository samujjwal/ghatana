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
    if (!this.config.apiKey) {
      throw new EvidenceSourceError(
        'OpenStax API credentials not configured. Set OPENSTAX_API_KEY to enable this adapter.',
        'OPENSTAX',
        'search',
        new Error('MISSING_API_CREDENTIALS')
      );
    }

    try {
      await this.enforceRateLimit();

      const maxResults = options?.maxResults ?? 5;
      const books = DOMAIN_BOOKS[domain.toLowerCase()] ?? [];

      // Call OpenStax API
      const searchUrl = `${this.config.baseUrl}/api/v2/pages/?type=books.BookPage&search=${encodeURIComponent(query)}&fields=title,slug,parent`;
      const response = await fetch(searchUrl, {
        headers: {
          'Authorization': `Bearer ${this.config.apiKey}`,
          'Accept': 'application/json',
          'User-Agent': 'TutorPutor/1.0 (Educational Platform)',
        },
      });

      if (!response.ok) {
        throw new Error(`OpenStax API returned ${response.status}: ${response.statusText}`);
      }

      const data = await response.json() as { items?: Array<{ id?: string; slug?: string; title?: string }> };
      const results: EvidenceSearchResult[] = [];

      if (data.items && data.items.length > 0) {
        const topResults = data.items.slice(0, maxResults);
        for (const item of topResults) {
          results.push({
            sourceUrl: `${this.config.baseUrl}/books/${books[0] || 'general'}/pages/${item.slug || item.id}`,
            title: item.title || `OpenStax ${domain} content`,
            publisher: 'OpenStax',
            excerpt: `Educational content from OpenStax about ${query}`,
            relevanceScore: 0.85,
          });
        }
      }

      return results;
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
    if (!this.config.apiKey) {
      throw new EvidenceSourceError(
        'OpenStax API credentials not configured. Set OPENSTAX_API_KEY to enable this adapter.',
        'OPENSTAX',
        'retrieve',
        new Error('MISSING_API_CREDENTIALS')
      );
    }

    try {
      // Check cache first
      if (this.config.cacheEnabled) {
        const cached = this.cache.get(url);
        if (cached) return cached;
      }

      await this.enforceRateLimit();

      // Fetch from OpenStax API
      const response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${this.config.apiKey}`,
          'Accept': 'application/json',
          'User-Agent': 'TutorPutor/1.0 (Educational Platform)',
        },
      });

      if (!response.ok) {
        throw new Error(`OpenStax API returned ${response.status}: ${response.statusText}`);
      }

      const data = await response.json() as { title?: string; content?: string; html?: string };
      const content: RetrievedContent = {
        url,
        title: data.title || this.extractTitleFromUrl(url),
        publisher: 'OpenStax',
        content: data.content || data.html || '',
        qualityIndicators: {
          isFeatured: true,
          citationCount: 0,
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
