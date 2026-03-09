/**
 * Web Search Service for Educational Content Fallback
 *
 * Provides web search functionality when AI service is unavailable.
 * Formats search results into educational content structure.
 *
 * @doc.type class
 * @doc.purpose Web-based content retrieval fallback for AI service
 * @doc.layer product
 * @doc.pattern Service
 */

import type { TutorResponsePayload, TutorCitation } from "@ghatana/tutorputor-contracts/v1/types";

export interface WebSearchConfig {
  apiProvider: 'duckduckgo' | 'google' | 'bing';
  apiKey?: string;
  timeout: number;
  resultCount: number;
  cacheTTL: number; // Time to live in seconds
  enabled: boolean;
}

interface SearchResult {
  title: string;
  url: string;
  snippet: string;
  domain: string;
}

interface CachedResult {
  data: SearchResult[];
  timestamp: number;
}

/**
 * Web Search Service
 *
 * Provides fallback search capability when AI service is unavailable.
 * Supports multiple search APIs (DuckDuckGo, Google Custom Search, Bing).
 * Caches results to reduce API calls.
 */
export class WebSearchService {
  private cache: Map<string, CachedResult> = new Map();
  private config: WebSearchConfig;

  constructor(config: Partial<WebSearchConfig> = {}) {
    this.config = {
      apiProvider: config.apiProvider || 'duckduckgo',
      apiKey: config.apiKey || process.env.WEB_SEARCH_API_KEY,
      timeout: config.timeout ?? 5000,
      resultCount: config.resultCount ?? 5,
      cacheTTL: config.cacheTTL ?? 3600,
      enabled: config.enabled ?? process.env.ENABLE_WEB_SEARCH_FALLBACK !== 'false',
    };
  }

  /**
   * Search the web for educational content
   */
  async search(query: string): Promise<TutorResponsePayload> {
    if (!this.config.enabled) {
      return this.getEmptyResponse("Web search is disabled");
    }

    try {
      // Check cache first
      const cached = this.getFromCache(query);
      if (cached) {
        console.log(`[Web Search] Cache hit for query: "${query}"`);
        return this.formatSearchResults(cached, query, true);
      }

      // Fetch fresh results
      console.log(`[Web Search] Searching for: "${query}"`);
      const results = await this.fetchSearchResults(query);

      // Cache results
      this.setCache(query, results);

      return this.formatSearchResults(results, query, false);
    } catch (error) {
      console.error(`[Web Search] Search failed for "${query}":`, error);
      return this.getEmptyResponse("Web search temporarily unavailable");
    }
  }

  /**
   * Search specifically for concept information
   */
  async searchConcept(conceptName: string, domain?: string): Promise<{
    description: string;
    learningObjectives: string[];
    keywords: string[];
    sources: TutorCitation[];
  }> {
    const query = domain 
      ? `${conceptName} ${domain} educational definition`
      : `${conceptName} educational definition`;

    try {
      const results = await this.fetchSearchResults(query);
      
      return {
        description: this.extractConceptDescription(results),
        learningObjectives: this.extractLearningPoints(results),
        keywords: [conceptName, ...this.extractKeywords(results)],
        sources: this.formatCitations(results),
      };
    } catch (error) {
      console.error(`[Web Search] Concept search failed for "${conceptName}":`, error);
      return {
        description: `Information about ${conceptName} from web sources`,
        learningObjectives: [],
        keywords: [conceptName],
        sources: [],
      };
    }
  }

  /**
   * Fetch search results from configured API
   */
  private async fetchSearchResults(query: string): Promise<SearchResult[]> {
    switch (this.config.apiProvider) {
      case 'duckduckgo':
        return this.fetchFromDuckDuckGo(query);
      case 'google':
        return this.fetchFromGoogle(query);
      case 'bing':
        return this.fetchFromBing(query);
      default:
        return this.fetchFromDuckDuckGo(query);
    }
  }

  /**
   * Fetch from DuckDuckGo (free, no API key required)
   */
  private async fetchFromDuckDuckGo(query: string): Promise<SearchResult[]> {
    const url = new URL('https://api.duckduckgo.com/');
    url.searchParams.set('q', query);
    url.searchParams.set('format', 'json');
    url.searchParams.set('pretty', '1');
    url.searchParams.set('no_redirect', '1');

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), this.config.timeout);

      const response = await fetch(url.toString(), {
        signal: controller.signal,
        headers: { 'User-Agent': 'TutorPutor/1.0' }
      });

      clearTimeout(timeout);

      if (!response.ok) {
        throw new Error(`DuckDuckGo API returned ${response.status}`);
      }

      const data = await response.json() as any;
      
      // Parse DuckDuckGo response format
      const results: SearchResult[] = [];
      
      // Use instant answer if available
      if (data.AbstractText) {
        results.push({
          title: data.AbstractTitle || 'Definition',
          url: data.AbstractURL || '',
          snippet: data.AbstractText,
          domain: 'duckduckgo.com',
        });
      }

      // Add related topics
      if (data.RelatedTopics && Array.isArray(data.RelatedTopics)) {
        data.RelatedTopics.slice(0, this.config.resultCount - 1).forEach((topic: any) => {
          if (topic.Text) {
            results.push({
              title: topic.FirstURL?.split('/')[2] || topic.Text.substring(0, 50),
              url: topic.FirstURL || '',
              snippet: topic.Text,
              domain: this.extractDomain(topic.FirstURL),
            });
          }
        });
      }

      return results.length > 0 ? results : this.getFallbackResults(query);
    } catch (error) {
      console.error('[Web Search] DuckDuckGo fetch failed:', error);
      return this.getFallbackResults(query);
    }
  }

  /**
   * Fetch from Google Custom Search (requires API key)
   */
  private async fetchFromGoogle(query: string): Promise<SearchResult[]> {
    if (!this.config.apiKey) {
      console.warn('[Web Search] Google Custom Search requires API key');
      return this.getFallbackResults(query);
    }

    const url = new URL('https://www.googleapis.com/customsearch/v1');
    url.searchParams.set('q', query);
    url.searchParams.set('key', this.config.apiKey);
    url.searchParams.set('cx', process.env.GOOGLE_SEARCH_CX || '');
    url.searchParams.set('num', String(this.config.resultCount));

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), this.config.timeout);

      const response = await fetch(url.toString(), {
        signal: controller.signal,
      });

      clearTimeout(timeout);

      if (!response.ok) {
        throw new Error(`Google API returned ${response.status}`);
      }

      const data = await response.json() as any;
      
      if (!data.items || data.items.length === 0) {
        return this.getFallbackResults(query);
      }

      return data.items.map((item: any) => ({
        title: item.title,
        url: item.link,
        snippet: item.snippet,
        domain: this.extractDomain(item.link),
      }));
    } catch (error) {
      console.error('[Web Search] Google Custom Search fetch failed:', error);
      return this.getFallbackResults(query);
    }
  }

  /**
   * Fetch from Bing Search (requires API key)
   */
  private async fetchFromBing(query: string): Promise<SearchResult[]> {
    if (!this.config.apiKey) {
      console.warn('[Web Search] Bing Search requires API key');
      return this.getFallbackResults(query);
    }

    const url = new URL('https://api.bing.microsoft.com/v7.0/search');
    url.searchParams.set('q', query);
    url.searchParams.set('count', String(this.config.resultCount));

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), this.config.timeout);

      const response = await fetch(url.toString(), {
        signal: controller.signal,
        headers: { 'Ocp-Apim-Subscription-Key': this.config.apiKey },
      });

      clearTimeout(timeout);

      if (!response.ok) {
        throw new Error(`Bing API returned ${response.status}`);
      }

      const data = await response.json() as any;
      
      if (!data.webPages || data.webPages.value.length === 0) {
        return this.getFallbackResults(query);
      }

      return data.webPages.value.map((item: any) => ({
        title: item.name,
        url: item.url,
        snippet: item.snippet,
        domain: this.extractDomain(item.url),
      }));
    } catch (error) {
      console.error('[Web Search] Bing Search fetch failed:', error);
      return this.getFallbackResults(query);
    }
  }

  /**
   * Fallback results when API fails
   */
  private getFallbackResults(query: string): SearchResult[] {
    return [
      {
        title: `Learn about ${query}`,
        url: `https://www.google.com/search?q=${encodeURIComponent(query)}`,
        snippet: `No specific results found. Try searching for "${query}" on Google for more information.`,
        domain: 'google.com',
      }
    ];
  }

  /**
   * Format search results into TutorResponsePayload
   */
  private formatSearchResults(
    results: SearchResult[],
    query: string,
    cached: boolean
  ): TutorResponsePayload {
    const answer = this.synthesizeAnswer(results, query);
    const citations = this.formatCitations(results);

    return {
      answer,
      followUpQuestions: [
        `What specific aspects of "${query}" would you like to explore?`,
        `Would you like more detailed information about any of the concepts mentioned?`,
        `Can I help you apply this information to your learning objectives?`,
      ],
      citations,
      safety: {
        blocked: false,
      },
      _dataSource: 'web_search',
      _cached: cached,
      _message: cached 
        ? 'Using cached web search results'
        : 'Retrieved from web search (AI service unavailable)',
    };
  }

  /**
   * Synthesize a coherent answer from search results
   */
  private synthesizeAnswer(results: SearchResult[], query: string): string {
    if (results.length === 0) {
      return `No information found about "${query}". Please try a different search.`;
    }

    const topResults = results.slice(0, 3);
    const sections = topResults.map((r, i) => 
      `**${i + 1}. ${r.title}**\n${r.snippet}`
    ).join('\n\n');

    return `Based on web search results for "${query}":\n\n${sections}\n\nFor more information, consider reviewing the sources listed below.`;
  }

  /**
   * Extract concept description from search results
   */
  private extractConceptDescription(results: SearchResult[]): string {
    if (results.length === 0) return '';
    
    const firstResult = results[0];
    // Use first 2-3 sentences of the snippet
    const sentences = firstResult.snippet.split('.').slice(0, 2).join('.') + '.';
    return sentences;
  }

  /**
   * Extract learning points from search results
   */
  private extractLearningPoints(results: SearchResult[]): string[] {
    const points: string[] = [];
    
    results.forEach(result => {
      // Extract key phrases that look like learning objectives
      const phrases = this.extractKeyPhrases(result.snippet);
      points.push(...phrases.slice(0, 2));
    });

    return points.slice(0, 5);
  }

  /**
   * Extract keywords from search results
   */
  private extractKeywords(results: SearchResult[]): string[] {
    const keywords = new Set<string>();
    
    results.forEach(result => {
      const words = result.snippet.split(/\s+/).slice(0, 10);
      words.forEach(word => {
        if (word.length > 4 && !this.isCommonWord(word)) {
          keywords.add(word.toLowerCase().replace(/[.,;:!?]/g, ''));
        }
      });
    });

    return Array.from(keywords).slice(0, 7);
  }

  /**
   * Extract key phrases that could be learning objectives
   */
  private extractKeyPhrases(text: string): string[] {
    const phrases: string[] = [];
    
    // Look for patterns like "understand X", "learn X", "explore X"
    const patterns = [
      /understand\s+([^,.]+)/gi,
      /learn\s+([^,.]+)/gi,
      /explore\s+([^,.]+)/gi,
      /discover\s+([^,.]+)/gi,
    ];

    patterns.forEach(pattern => {
      let match;
      while ((match = pattern.exec(text)) !== null) {
        const phrase = match[1].trim();
        if (phrase.length > 3 && phrase.length < 100) {
          phrases.push(`Understand ${phrase}`);
        }
      }
    });

    return phrases.slice(0, 5);
  }

  /**
   * Format search results as citations
   */
  private formatCitations(results: SearchResult[]): TutorCitation[] {
    return results.map((result, index) => ({
      type: 'web_source',
      id: `web-search-${index}`,
      label: `${result.title} (${result.domain})`,
      url: result.url,
    } as any));
  }

  /**
   * Get cached results if still valid
   */
  private getFromCache(query: string): SearchResult[] | null {
    const cached = this.cache.get(query);
    if (!cached) return null;

    const age = (Date.now() - cached.timestamp) / 1000;
    if (age > this.config.cacheTTL) {
      this.cache.delete(query);
      return null;
    }

    return cached.data;
  }

  /**
   * Store results in cache
   */
  private setCache(query: string, results: SearchResult[]): void {
    this.cache.set(query, {
      data: results,
      timestamp: Date.now(),
    });
  }

  /**
   * Extract domain from URL
   */
  private extractDomain(url: string): string {
    try {
      return new URL(url).hostname.replace('www.', '');
    } catch {
      return 'unknown';
    }
  }

  /**
   * Check if word is a common stop word
   */
  private isCommonWord(word: string): boolean {
    const stopWords = new Set([
      'the', 'is', 'at', 'which', 'on', 'a', 'an', 'and', 'or', 'but', 'in', 'of', 'to', 'for',
      'be', 'it', 'by', 'from', 'this', 'that', 'with', 'as', 'has', 'have', 'will', 'would',
    ]);
    return stopWords.has(word.toLowerCase());
  }

  /**
   * Get empty response (service unavailable)
   */
  private getEmptyResponse(message: string): TutorResponsePayload {
    return {
      answer: message,
      followUpQuestions: [
        "Would you like to try again later?",
        "Can I help you with something else?",
      ],
      citations: [],
      safety: {
        blocked: false,
      },
      _dataSource: 'fallback',
      _message: message,
    };
  }

  /**
   * Clear old cache entries
   */
  clearOldCache(): void {
    const now = Date.now();
    for (const [query, cached] of this.cache.entries()) {
      const age = (now - cached.timestamp) / 1000;
      if (age > this.config.cacheTTL) {
        this.cache.delete(query);
      }
    }
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): { size: number; entries: string[] } {
    return {
      size: this.cache.size,
      entries: Array.from(this.cache.keys()),
    };
  }
}

/**
 * Create web search service instance
 */
export function createWebSearchService(config?: Partial<WebSearchConfig>): WebSearchService {
  return new WebSearchService(config);
}
