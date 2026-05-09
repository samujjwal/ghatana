/**
 * Wikipedia Evidence Source Adapter
 *
 * Governed adapter for Wikipedia content retrieval with rate limiting,
 * caching, and proper error handling.
 *
 * @doc.type module
 * @doc.purpose Adapter for Wikipedia knowledge source
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
 * Configuration for Wikipedia adapter.
 */
export interface WikipediaConfig {
  apiUrl: string;
  userAgent: string;
  cacheEnabled: boolean;
  rateLimitPerSecond: number;
}

/**
 * Adapter for Wikipedia content with governance controls.
 */
export class WikipediaAdapter implements EvidenceSourceAdapter {
  private requestTimestamps: number[] = [];
  private cache = new Map<string, RetrievedContent>();

  constructor(private readonly config: WikipediaConfig) {}

  getSourceType(): EvidenceSourceType {
    return 'WIKIPEDIA';
  }

  async search(
    query: string,
    domain: string,
    options?: { maxResults?: number; gradeBand?: string }
  ): Promise<EvidenceSearchResult[]> {
    try {
      await this.enforceRateLimit();

      const maxResults = options?.maxResults ?? 5;
      const searchParams = new URLSearchParams({
        action: 'query',
        list: 'search',
        srsearch: `${query} ${domain}`,
        format: 'json',
        srlimit: String(maxResults),
        srprop: 'snippet|timestamp|wordcount',
      });

      const response = await fetch(
        `${this.config.apiUrl}?${searchParams.toString()}`,
        {
          headers: {
            'User-Agent': this.config.userAgent,
            'Accept': 'application/json',
          },
        }
      );

      if (!response.ok) {
        throw new Error(`Wikipedia API returned ${response.status}: ${response.statusText}`);
      }

      const data = await response.json() as { query?: { search?: Array<{ pageid: number; title: string; snippet: string; timestamp?: string }> } };
      const results: EvidenceSearchResult[] = [];

      if (data.query?.search) {
        for (const item of data.query.search) {
          results.push({
            sourceUrl: `https://en.wikipedia.org/wiki/${encodeURIComponent(item.title.replace(/ /g, '_'))}`,
            title: item.title,
            publisher: 'Wikipedia',
            excerpt: this.cleanSnippet(item.snippet),
            relevanceScore: this.calculateRelevance(item, query, domain),
          });
        }
      }

      return results;
    } catch (error) {
      throw new EvidenceSourceError(
        error instanceof Error ? error.message : 'Unknown error',
        'WIKIPEDIA',
        'search',
        error instanceof Error ? error : undefined
      );
    }
  }

  async retrieveContent(url: string): Promise<RetrievedContent | null> {
    try {
      // Check cache first
      if (this.config.cacheEnabled) {
        const cached = this.cache.get(url);
        if (cached) return cached;
      }

      await this.enforceRateLimit();

      // Extract page title from URL
      const titleMatch = url.match(/\/wiki\/([^/]+)$/);
      if (!titleMatch) {
        throw new Error('Invalid Wikipedia URL format');
      }

      const title = decodeURIComponent(titleMatch[1]).replace(/_/g, ' ');

      const searchParams = new URLSearchParams({
        action: 'query',
        prop: 'extracts|pageprops',
        exintro: 'true',
        explaintext: 'true',
        ppprop: 'wikibase_item',
        titles: title,
        format: 'json',
      });

      const response = await fetch(
        `${this.config.apiUrl}?${searchParams.toString()}`,
        {
          headers: {
            'User-Agent': this.config.userAgent,
            'Accept': 'application/json',
          },
        }
      );

      if (!response.ok) {
        throw new Error(`Wikipedia API returned ${response.status}: ${response.statusText}`);
      }

      const data = await response.json() as { query?: { pages?: Record<string, { extract?: string; pageid?: number }> } };

      const pages = data.query?.pages;
      if (!pages) {
        return null;
      }

      const pageId = Object.keys(pages)[0];
      const page = pages[pageId];

      if (!page || !page.extract) {
        return null;
      }

      const content: RetrievedContent = {
        url,
        title,
        publisher: 'Wikipedia',
        content: page.extract,
        citations: [`Wikipedia: ${title}`],
        qualityIndicators: {
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
        'WIKIPEDIA',
        'retrieve',
        error instanceof Error ? error : undefined
      );
    }
  }

  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.config.apiUrl}?action=query&meta=siteinfo&format=json`, {
        headers: {
          'User-Agent': this.config.userAgent,
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
    const oneSecondAgo = now - 1000;

    // Remove timestamps older than 1 second
    this.requestTimestamps = this.requestTimestamps.filter(t => t > oneSecondAgo);

    // If at limit, wait
    if (this.requestTimestamps.length >= this.config.rateLimitPerSecond) {
      const oldestTimestamp = this.requestTimestamps[0] ?? now;
      const waitTime = 1000 - (now - oldestTimestamp);
      if (waitTime > 0) {
        await new Promise(resolve => setTimeout(resolve, waitTime));
      }
    }

    this.requestTimestamps.push(now);
  }

  /**
   * Clean HTML snippet from Wikipedia.
   */
  private cleanSnippet(snippet: string): string {
    return snippet
      .replace(/<span class="searchmatch">/g, '')
      .replace(/<\/span>/g, '')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/&amp;/g, '&');
  }

  /**
   * Calculate relevance score based on word count and query match.
   */
  private calculateRelevance(
    item: { wordcount?: number; title: string },
    query: string,
    domain: string
  ): number {
    const titleLower = item.title.toLowerCase();
    const queryLower = query.toLowerCase();
    const domainLower = domain.toLowerCase();

    let score = 0.5; // Base score

    // Boost for query match in title
    if (titleLower.includes(queryLower)) {
      score += 0.3;
    }

    // Boost for domain match in title
    if (titleLower.includes(domainLower)) {
      score += 0.2;
    }

    // Cap at 1.0
    return Math.min(score, 1.0);
  }
}
