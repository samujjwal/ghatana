/**
 * Enhanced Search Service for Flashit
 * Hybrid search combining text search with vector similarity
 *
 * @doc.type service
 * @doc.purpose Provide advanced search with semantic and text matching
 * @doc.layer product
 * @doc.pattern SearchService
 */

import { prisma } from '../../lib/prisma.js';
import { VectorEmbeddingService } from '../embeddings/vector-service.js';

// Search types
export type SearchType = 'semantic' | 'text' | 'hybrid' | 'similar';

// Search filters interface
export interface SearchFilters {
  sphereIds?: string[];
  emotions?: string[];
  tags?: string[];
  importance?: {
    min?: number;
    max?: number;
  };
  dateRange?: {
    from?: Date;
    to?: Date;
  };
  contentTypes?: ('text' | 'audio' | 'video')[];
  hasTranscript?: boolean;
  hasReflection?: boolean;
}

// Search result interface
export interface SearchResult {
  momentId: string;
  title: string;
  content: string;
  transcript?: string;
  sphereId: string;
  sphereName: string;
  capturedAt: Date;
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

// Search options interface
export interface SearchOptions {
  type: SearchType;
  query: string;
  filters?: SearchFilters;
  limit?: number;
  offset?: number;
  userId: string;
  includeHighlights?: boolean;
  includeReflections?: boolean;
  similarityThreshold?: number;
  boostFactors?: {
    recency?: number;
    importance?: number;
    emotion?: number;
  };
}

// Search analytics interface
export interface SearchAnalytics {
  query: string;
  type: SearchType;
  resultCount: number;
  processingTimeMs: number;
  filters: SearchFilters;
  userId: string;
  clickedResults?: string[];
}

/**
 * Text highlighting utility
 */
class TextHighlighter {

  /**
   * Generate highlights for search terms in text
   */
  static generateHighlights(text: string, searchTerms: string[]): string[] {
    const highlights: string[] = [];
    const words = searchTerms.flatMap(term => term.toLowerCase().split(/\s+/));

    for (const word of words) {
      const regex = new RegExp(`(.{0,50})(${word})(.{0,50})`, 'gi');
      const matches = text.matchAll(regex);

      for (const match of matches) {
        if (match[0]) {
          const highlight = match[1] + `<mark>${match[2]}</mark>` + match[3];
          highlights.push(highlight.trim());
        }
      }
    }

    return highlights.slice(0, 3); // Limit to top 3 highlights
  }

  /**
   * Extract search terms from query
   */
  static extractSearchTerms(query: string): string[] {
    // Remove quotes and split on common delimiters
    return query
      .replace(/['"]/g, '')
      .split(/[\s,;]+/)
      .filter(term => term.length > 2)
      .slice(0, 10); // Limit to prevent abuse
  }
}

/**
 * Search ranking utility
 */
class SearchRanker {

  /**
   * Calculate hybrid search score
   */
  static calculateHybridScore(
    textScore: number,
    semanticScore: number,
    weights: { text: number; semantic: number } = { text: 0.3, semantic: 0.7 }
  ): number {
    return (textScore * weights.text) + (semanticScore * weights.semantic);
  }

  /**
   * Apply boost factors to search score
   */
  static applyBoostFactors(
    baseScore: number,
    moment: any,
    boostFactors?: {
      recency?: number;
      importance?: number;
      emotion?: number;
    }
  ): number {
    let boostedScore = baseScore;

    if (boostFactors?.recency) {
      // Boost recent moments (exponential decay)
      const daysSinceCapture = (Date.now() - moment.capturedAt.getTime()) / (1000 * 60 * 60 * 24);
      const recencyMultiplier = Math.exp(-daysSinceCapture / 30) * boostFactors.recency;
      boostedScore *= (1 + recencyMultiplier);
    }

    if (boostFactors?.importance && moment.importance) {
      // Boost important moments
      const importanceMultiplier = (moment.importance / 5) * boostFactors.importance;
      boostedScore *= (1 + importanceMultiplier);
    }

    if (boostFactors?.emotion && moment.emotions?.length > 0) {
      // Boost emotionally rich moments
      const emotionMultiplier = (moment.emotions.length / 5) * boostFactors.emotion;
      boostedScore *= (1 + emotionMultiplier);
    }

    return boostedScore;
  }
}

/**
 * Enhanced Search Service
 */
export class EnhancedSearchService {

  /**
   * Perform hybrid search (text + semantic)
   */
  static async hybridSearch(options: SearchOptions): Promise<{
    results: SearchResult[];
    totalCount: number;
    analytics: SearchAnalytics;
  }> {
    const startTime = Date.now();

    try {
      // Get user's accessible spheres
      const userSpheres = await this.getUserSphereIds(options.userId, options.filters?.sphereIds);

      if (userSpheres.length === 0) {
        return {
          results: [],
          totalCount: 0,
          analytics: this.createAnalytics(options, 0, Date.now() - startTime),
        };
      }

      let results: SearchResult[] = [];

      switch (options.type) {
        case 'text':
          results = await this.performTextSearch(options, userSpheres);
          break;
        case 'semantic':
          results = await this.performSemanticSearch(options, userSpheres);
          break;
        case 'hybrid':
          results = await this.performHybridSearch(options, userSpheres);
          break;
        case 'similar':
          results = await this.performSimilaritySearch(options, userSpheres);
          break;
      }

      // Apply final ranking and filtering
      results = this.rankAndFilterResults(results, options);

      // Apply pagination
      const paginatedResults = results.slice(
        options.offset || 0,
        (options.offset || 0) + (options.limit || 20)
      );

      // Store search analytics
      const analytics = this.createAnalytics(options, results.length, Date.now() - startTime);
      await this.logSearchAnalytics(analytics);

      return {
        results: paginatedResults,
        totalCount: results.length,
        analytics,
      };

    } catch (error) {
      console.error('Search failed:', error);
      throw new Error('Search service temporarily unavailable');
    }
  }

  /**
   * Perform text-based search using PostgreSQL full-text search
   */
  private static async performTextSearch(
    options: SearchOptions,
    userSpheres: string[]
  ): Promise<SearchResult[]> {
    const whereClause = this.buildWhereClause(options.filters, userSpheres);

    // Use PostgreSQL's full-text search
    const query = `
      SELECT
        m.id,
        m.content_text,
        m.content_transcript,
        m.sphere_id,
        s.name as sphere_name,
        m.captured_at,
        m.emotions,
        m.tags,
        m.importance,
        m.metadata,
        ts_rank(
          to_tsvector('english', COALESCE(m.content_text, '') || ' ' || COALESCE(m.content_transcript, '')),
          plainto_tsquery('english', $1)
        ) as rank_score
      FROM moments m
      JOIN spheres s ON m.sphere_id = s.id
      WHERE m.sphere_id = ANY($2::uuid[])
        AND m.deleted_at IS NULL
        AND (
          to_tsvector('english', COALESCE(m.content_text, '') || ' ' || COALESCE(m.content_transcript, ''))
          @@ plainto_tsquery('english', $1)
        )
        ${this.buildAdditionalFilters(options.filters)}
      ORDER BY rank_score DESC, m.captured_at DESC
      LIMIT $3
    `;

    const rawResults = await prisma.$queryRawUnsafe(
      query,
      options.query,
      userSpheres,
      (options.limit || 20) * 2 // Get more results for hybrid ranking
    );

    return this.formatSearchResults(rawResults as any[], options);
  }

  /**
   * Perform semantic search using vector embeddings
   */
  private static async performSemanticSearch(
    options: SearchOptions,
    userSpheres: string[]
  ): Promise<SearchResult[]> {
    // Use vector similarity search
    const similarMoments = await VectorEmbeddingService.findSimilarMoments(
      options.query,
      userSpheres,
      {
        limit: (options.limit || 20) * 2,
        similarityThreshold: options.similarityThreshold || 0.5,
        contentType: 'combined',
      }
    );

    if (similarMoments.length === 0) {
      return [];
    }

    // Get full moment details
    const momentIds = similarMoments.map(m => m.momentId);
    const moments = await prisma.moment.findMany({
      where: {
        id: { in: momentIds },
        deletedAt: null,
        ...this.buildPrismaFilters(options.filters, userSpheres),
      },
      include: {
        sphere: { select: { name: true } },
      },
    });

    // Merge similarity scores
    const results = moments.map(moment => {
      const similar = similarMoments.find(s => s.momentId === moment.id);
      return this.formatMomentAsSearchResult(moment, similar?.similarity || 0, options);
    });

    return results.sort((a, b) => (b.similarity || 0) - (a.similarity || 0));
  }

  /**
   * Perform hybrid search combining text and semantic
   */
  private static async performHybridSearch(
    options: SearchOptions,
    userSpheres: string[]
  ): Promise<SearchResult[]> {
    // Run both searches in parallel
    const [textResults, semanticResults] = await Promise.all([
      this.performTextSearch(options, userSpheres),
      this.performSemanticSearch(options, userSpheres),
    ]);

    // Combine and deduplicate results
    const combinedResults = new Map<string, SearchResult>();

    // Add text search results
    for (const result of textResults) {
      combinedResults.set(result.momentId, {
        ...result,
        score: result.score * 0.3, // Text weight
      });
    }

    // Add semantic search results
    for (const result of semanticResults) {
      const existing = combinedResults.get(result.momentId);
      if (existing) {
        // Combine scores using hybrid ranking
        existing.score = SearchRanker.calculateHybridScore(
          existing.score / 0.3, // Normalize text score
          result.similarity || 0
        );
        existing.similarity = result.similarity;
      } else {
        combinedResults.set(result.momentId, {
          ...result,
          score: (result.similarity || 0) * 0.7, // Semantic weight
        });
      }
    }

    return Array.from(combinedResults.values())
      .sort((a, b) => b.score - a.score);
  }

  /**
   * Perform similarity search for finding similar moments
   */
  private static async performSimilaritySearch(
    options: SearchOptions,
    userSpheres: string[]
  ): Promise<SearchResult[]> {
    // For similarity search, the query should be a moment ID
    const targetMoment = await prisma.moment.findFirst({
      where: {
        id: options.query,
        sphere: {
          sphereAccess: {
            some: {
              userId: options.userId,
              revokedAt: null,
            },
          },
        },
      },
      select: {
        contentText: true,
        contentTranscript: true,
      },
    });

    if (!targetMoment) {
      throw new Error('Target moment not found or access denied');
    }

    // Use the moment's content as the similarity query
    const queryText = targetMoment.contentTranscript
      ? `${targetMoment.contentText} ${targetMoment.contentTranscript}`
      : targetMoment.contentText;

    return this.performSemanticSearch({
      ...options,
      query: queryText,
    }, userSpheres);
  }

  /**
   * Get user's accessible sphere IDs
   */
  private static async getUserSphereIds(userId: string, filterSphereIds?: string[]): Promise<string[]> {
    const sphereAccess = await prisma.sphereAccess.findMany({
      where: {
        userId,
        revokedAt: null,
        ...(filterSphereIds && { sphereId: { in: filterSphereIds } }),
      },
      select: { sphereId: true },
    });

    return sphereAccess.map(sa => sa.sphereId);
  }

  /**
   * Build WHERE clause for text search
   */
  private static buildWhereClause(filters?: SearchFilters, userSpheres?: string[]): string {
    let whereClause = '';

    if (userSpheres) {
      whereClause += `AND m.sphere_id = ANY($${this.getNextParamIndex()}::uuid[]) `;
    }

    if (filters?.emotions && filters.emotions.length > 0) {
      whereClause += `AND m.emotions && $${this.getNextParamIndex()}::text[] `;
    }

    if (filters?.tags && filters.tags.length > 0) {
      whereClause += `AND m.tags && $${this.getNextParamIndex()}::text[] `;
    }

    if (filters?.importance) {
      if (filters.importance.min !== undefined) {
        whereClause += `AND m.importance >= $${this.getNextParamIndex()} `;
      }
      if (filters.importance.max !== undefined) {
        whereClause += `AND m.importance <= $${this.getNextParamIndex()} `;
      }
    }

    if (filters?.dateRange) {
      if (filters.dateRange.from) {
        whereClause += `AND m.captured_at >= $${this.getNextParamIndex()} `;
      }
      if (filters.dateRange.to) {
        whereClause += `AND m.captured_at <= $${this.getNextParamIndex()} `;
      }
    }

    if (filters?.hasTranscript !== undefined) {
      whereClause += filters.hasTranscript
        ? `AND m.content_transcript IS NOT NULL `
        : `AND m.content_transcript IS NULL `;
    }

    if (filters?.hasReflection !== undefined) {
      whereClause += filters.hasReflection
        ? `AND m.metadata ? 'reflection' `
        : `AND NOT m.metadata ? 'reflection' `;
    }

    return whereClause;
  }

  private static paramIndex = 3; // Start after base params
  private static getNextParamIndex(): number {
    return ++this.paramIndex;
  }

  /**
   * Build additional filter clauses
   */
  private static buildAdditionalFilters(filters?: SearchFilters): string {
    // This is a simplified version - in production, build dynamically
    return '';
  }

  /**
   * Build Prisma filters object
   */
  private static buildPrismaFilters(filters?: SearchFilters, userSpheres?: string[]): any {
    const prismaFilters: any = {};

    if (userSpheres) {
      prismaFilters.sphereId = { in: userSpheres };
    }

    if (filters?.emotions && filters.emotions.length > 0) {
      prismaFilters.emotions = { hasSome: filters.emotions };
    }

    if (filters?.tags && filters.tags.length > 0) {
      prismaFilters.tags = { hasSome: filters.tags };
    }

    if (filters?.importance) {
      prismaFilters.importance = {};
      if (filters.importance.min !== undefined) {
        prismaFilters.importance.gte = filters.importance.min;
      }
      if (filters.importance.max !== undefined) {
        prismaFilters.importance.lte = filters.importance.max;
      }
    }

    if (filters?.dateRange) {
      prismaFilters.capturedAt = {};
      if (filters.dateRange.from) {
        prismaFilters.capturedAt.gte = filters.dateRange.from;
      }
      if (filters.dateRange.to) {
        prismaFilters.capturedAt.lte = filters.dateRange.to;
      }
    }

    if (filters?.hasTranscript !== undefined) {
      prismaFilters.contentTranscript = filters.hasTranscript
        ? { not: null }
        : null;
    }

    return prismaFilters;
  }

  /**
   * Format raw search results
   */
  private static formatSearchResults(rawResults: any[], options: SearchOptions): SearchResult[] {
    return rawResults.map(row => ({
      momentId: row.id,
      title: this.generateTitle(row.content_text),
      content: row.content_text,
      transcript: row.content_transcript,
      sphereId: row.sphere_id,
      sphereName: row.sphere_name,
      capturedAt: new Date(row.captured_at),
      emotions: row.emotions || [],
      tags: row.tags || [],
      importance: row.importance,
      score: parseFloat(row.rank_score) || 0,
      highlights: options.includeHighlights ? {
        content: TextHighlighter.generateHighlights(
          row.content_text,
          TextHighlighter.extractSearchTerms(options.query)
        ),
        transcript: row.content_transcript ? TextHighlighter.generateHighlights(
          row.content_transcript,
          TextHighlighter.extractSearchTerms(options.query)
        ) : undefined,
      } : undefined,
      reflection: options.includeReflections && row.metadata?.reflection ? {
        insights: row.metadata.reflection.insights || [],
        themes: row.metadata.reflection.themes || [],
      } : undefined,
    }));
  }

  /**
   * Format moment as search result
   */
  private static formatMomentAsSearchResult(
    moment: any,
    similarity: number,
    options: SearchOptions
  ): SearchResult {
    return {
      momentId: moment.id,
      title: this.generateTitle(moment.contentText),
      content: moment.contentText,
      transcript: moment.contentTranscript,
      sphereId: moment.sphereId,
      sphereName: moment.sphere.name,
      capturedAt: moment.capturedAt,
      emotions: moment.emotions || [],
      tags: moment.tags || [],
      importance: moment.importance,
      score: similarity,
      similarity,
      highlights: options.includeHighlights ? {
        content: TextHighlighter.generateHighlights(
          moment.contentText,
          TextHighlighter.extractSearchTerms(options.query)
        ),
        transcript: moment.contentTranscript ? TextHighlighter.generateHighlights(
          moment.contentTranscript,
          TextHighlighter.extractSearchTerms(options.query)
        ) : undefined,
      } : undefined,
      reflection: options.includeReflections && moment.metadata?.reflection ? {
        insights: moment.metadata.reflection.insights || [],
        themes: moment.metadata.reflection.themes || [],
      } : undefined,
    };
  }

  /**
   * Rank and filter final results
   */
  private static rankAndFilterResults(results: SearchResult[], options: SearchOptions): SearchResult[] {
    // Apply boost factors
    if (options.boostFactors) {
      results = results.map(result => ({
        ...result,
        score: SearchRanker.applyBoostFactors(
          result.score,
          result,
          options.boostFactors
        ),
      }));
    }

    // Final sort by boosted score
    return results.sort((a, b) => b.score - a.score);
  }

  /**
   * Generate title from content
   */
  private static generateTitle(content: string): string {
    // Extract first sentence or first 60 characters
    const firstSentence = content.split(/[.!?]/)[0];
    if (firstSentence.length <= 60) {
      return firstSentence.trim();
    }
    return content.substring(0, 57).trim() + '...';
  }

  /**
   * Create search analytics object
   */
  private static createAnalytics(
    options: SearchOptions,
    resultCount: number,
    processingTimeMs: number
  ): SearchAnalytics {
    return {
      query: options.query,
      type: options.type,
      resultCount,
      processingTimeMs,
      filters: options.filters || {},
      userId: options.userId,
    };
  }

  /**
   * Log search analytics
   */
  private static async logSearchAnalytics(analytics: SearchAnalytics): Promise<void> {
    try {
      // Store search analytics in database or send to analytics service
      await prisma.auditEvent.create({
        data: {
          eventType: 'SEMANTIC_SEARCH_PERFORMED',
          userId: analytics.userId,
          actor: analytics.userId,
          action: 'SEARCH_PERFORMED',
          resourceType: 'search',
          details: {
            query: analytics.query,
            type: analytics.type,
            resultCount: analytics.resultCount,
            processingTimeMs: analytics.processingTimeMs,
            filters: analytics.filters,
          },
        },
      });
    } catch (error) {
      console.error('Failed to log search analytics:', error);
    }
  }

  /**
   * Get search suggestions based on user history and sphere content
   */
  static async getSearchSuggestions(
    userId: string,
    partialQuery: string = '',
    limit: number = 5
  ): Promise<string[]> {
    try {
      // Get user's spheres
      const userSpheres = await this.getUserSphereIds(userId);

      if (userSpheres.length === 0) {
        return [];
      }

      // Get common tags and terms from user's moments
      const commonTerms = await prisma.$queryRaw`
        SELECT
          unnest(tags) as term,
          COUNT(*) as frequency
        FROM moments
        WHERE sphere_id = ANY(${userSpheres}::uuid[])
          AND deleted_at IS NULL
          AND array_length(tags, 1) > 0
          ${partialQuery ? `AND unnest(tags) ILIKE ${`%${partialQuery}%`}` : ''}
        GROUP BY term
        ORDER BY frequency DESC, term
        LIMIT ${limit}
      ` as any[];

      return commonTerms.map((term: any) => term.term);

    } catch (error) {
      console.error('Failed to get search suggestions:', error);
      return [];
    }
  }
}

export { SearchRanker, TextHighlighter };
