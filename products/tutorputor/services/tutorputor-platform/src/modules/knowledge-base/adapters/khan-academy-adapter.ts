/**
 * Khan Academy Evidence Source Adapter
 *
 * Governed adapter for Khan Academy content retrieval with rate limiting,
 * caching, and proper error handling.
 *
 * @doc.type module
 * @doc.purpose Adapter for Khan Academy knowledge source
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
 * Configuration for Khan Academy adapter.
 */
export interface KhanAcademyConfig {
  baseUrl: string;
  apiKey?: string;
  cacheEnabled: boolean;
  rateLimitPerMinute: number;
}

/**
 * Domain to subject mapping for Khan Academy.
 */
const DOMAIN_SUBJECTS: Record<string, string[]> = {
  'mathematics': ['math', 'algebra', 'geometry', 'calculus', 'statistics'],
  'physics': ['physics', 'science'],
  'chemistry': ['chemistry', 'science'],
  'biology': ['biology', 'science'],
  'computer-science': ['computing', 'programming'],
};

/**
 * Adapter for Khan Academy content with governance controls.
 */
export class KhanAcademyAdapter implements EvidenceSourceAdapter {
  private requestTimestamps: number[] = [];
  private cache = new Map<string, RetrievedContent>();

  constructor(private readonly config: KhanAcademyConfig) {}

  getSourceType(): EvidenceSourceType {
    return 'KHAN_ACADEMY';
  }

  async search(
    query: string,
    domain: string,
    options?: { maxResults?: number; gradeBand?: string }
  ): Promise<EvidenceSearchResult[]> {
    if (!this.config.apiKey) {
      throw new EvidenceSourceError(
        'Khan Academy API credentials not configured. Set KHAN_ACADEMY_API_KEY to enable this adapter.',
        'KHAN_ACADEMY',
        'search',
        new Error('MISSING_API_CREDENTIALS')
      );
    }

    try {
      await this.enforceRateLimit();

      const maxResults = options?.maxResults ?? 5;
      const subjects = DOMAIN_SUBJECTS[domain.toLowerCase()] ?? ['math'];

      // Search across relevant subjects
      const results: EvidenceSearchResult[] = [];

      for (const subject of subjects.slice(0, 2)) { // Limit to 2 subjects
        const searchUrl = `${this.config.baseUrl}/api/v3/search?keyword=${encodeURIComponent(query)}&subject=${subject}&limit=${maxResults}`;
        const response = await fetch(searchUrl, {
          headers: {
            'Authorization': `Bearer ${this.config.apiKey}`,
            'Accept': 'application/json',
          },
        });

        if (!response.ok) {
          continue; // Try next subject
        }

        const data = await response.json() as { results?: Array<{ id?: string; title?: string; url?: string; description?: string }> };
        
        if (data.results) {
          for (const item of data.results.slice(0, maxResults)) {
            results.push({
              sourceUrl: item.url || `${this.config.baseUrl}/v1/${item.id}`,
              title: item.title || `Khan Academy: ${query}`,
              publisher: 'Khan Academy',
              excerpt: item.description || `Educational content about ${query}`,
              relevanceScore: 0.8,
            });
          }
        }

        if (results.length >= maxResults) {
          break;
        }
      }

      return results;
    } catch (error) {
      throw new EvidenceSourceError(
        error instanceof Error ? error.message : 'Unknown error',
        'KHAN_ACADEMY',
        'search',
        error instanceof Error ? error : undefined
      );
    }
  }

  async retrieveContent(url: string): Promise<RetrievedContent | null> {
    if (!this.config.apiKey) {
      throw new EvidenceSourceError(
        'Khan Academy API credentials not configured. Set KHAN_ACADEMY_API_KEY to enable this adapter.',
        'KHAN_ACADEMY',
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

      const response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${this.config.apiKey}`,
          'Accept': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Khan Academy API returned ${response.status}: ${response.statusText}`);
      }

      const data = await response.json() as { title?: string; description?: string; content?: string; translated_content?: string };
      const content: RetrievedContent = {
        url,
        title: data.title || this.extractTitleFromUrl(url),
        publisher: 'Khan Academy',
        content: data.content || data.translated_content || data.description || '',
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
        'KHAN_ACADEMY',
        'retrieve',
        error instanceof Error ? error : undefined
      );
    }
  }

  async healthCheck(): Promise<boolean> {
    try {
      if (!this.config.apiKey) {
        return false;
      }
      const response = await fetch(`${this.config.baseUrl}/api/v3/health`, {
        headers: {
          'Authorization': `Bearer ${this.config.apiKey}`,
          'Accept': 'application/json',
        },
      });
      return response.ok;
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
    const slug = match?.[1] ?? 'Khan Academy Content';
    return slug.replace(/-/g, ' ');
  }
}
