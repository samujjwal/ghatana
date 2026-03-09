/**
 * Enhanced Search Service for Flashit Web API
 * Provides hybrid search combining full-text and semantic search
 *
 * @doc.type service
 * @doc.purpose Hybrid search with text and vector similarity
 * @doc.layer product
 * @doc.pattern SearchService
 */

import { prisma } from '../../lib/prisma.js';
import { VectorEmbeddingService } from '../embeddings/vector-service.js';

// Types
export type SearchType = 'semantic' | 'text' | 'hybrid' | 'similar';

export interface SearchOptions {
  query: string;
  type?: SearchType;
  userId: string;
  filters?: {
    sphereIds?: string[];
    emotions?: string[];
    tags?: string[];
    startDate?: string;
    endDate?: string;
    importance?: number;
  };
  limit?: number;
  offset?: number;
  includeHighlights?: boolean;
  includeReflections?: boolean;
  similarityThreshold?: number;
  boostFactors?: {
    recency?: number;
    importance?: number;
    emotions?: number;
  };
}

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

export interface SearchResponse {
  results: SearchResult[];
  totalCount: number;
  analytics: {
    processingTimeMs: number;
    resultCount: number;
    type: SearchType;
    hasMore: boolean;
  };
}

export type SuggestionResponse = string[];

/**
 * Enhanced Search Service
 * Provides hybrid search combining PostgreSQL full-text search with vector similarity
 */
export class EnhancedSearchService {
  /**
   * Main hybrid search function
   * Combines text search and semantic search for best results
   */
  static async hybridSearch(options: SearchOptions): Promise<SearchResponse> {
    const startTime = Date.now();
    const {
      query,
      type = 'hybrid',
      userId,
      filters = {},
      limit = 20,
      offset = 0,
      includeHighlights = false,
      similarityThreshold = 0.6,
      boostFactors = {},
    } = options;

    // Validate query
    if (!query || query.trim().length < 2) {
      return {
        results: [],
        totalCount: 0,
        analytics: {
          processingTimeMs: Date.now() - startTime,
          resultCount: 0,
          type,
          hasMore: false,
        },
      };
    }

    // Get user's accessible sphere IDs
    const accessibleSpheres = await this.getUserAccessibleSpheres(userId, filters.sphereIds);
    
    if (accessibleSpheres.length === 0) {
      return {
        results: [],
        totalCount: 0,
        analytics: {
          processingTimeMs: Date.now() - startTime,
          resultCount: 0,
          type,
          hasMore: false,
        },
      };
    }

    let results: SearchResult[] = [];
    let totalCount = 0;

    try {
      switch (type) {
        case 'text':
          ({ results, totalCount } = await this.textSearch(query, accessibleSpheres, filters, limit, offset, includeHighlights));
          break;
        case 'semantic':
          ({ results, totalCount } = await this.semanticSearch(query, accessibleSpheres, filters, limit, similarityThreshold));
          break;
        case 'similar':
          // For 'similar', the query should be a moment ID
          ({ results, totalCount } = await this.findSimilarMoments(query, accessibleSpheres, limit));
          break;
        case 'hybrid':
        default:
          ({ results, totalCount } = await this.hybridSearchImpl(query, accessibleSpheres, filters, limit, offset, includeHighlights, similarityThreshold, boostFactors));
          break;
      }
    } catch (error) {
      console.error('Search failed:', error);
      // Return empty results on error
    }

    return {
      results,
      totalCount,
      analytics: {
        processingTimeMs: Date.now() - startTime,
        resultCount: results.length,
        type,
        hasMore: offset + results.length < totalCount,
      },
    };
  }

  /**
   * Get spheres the user has access to
   */
  private static async getUserAccessibleSpheres(userId: string, filterSphereIds?: string[]): Promise<string[]> {
    const access = await prisma.sphereAccess.findMany({
      where: {
        userId,
        revokedAt: null,
        sphere: {
          deletedAt: null,
          ...(filterSphereIds?.length ? { id: { in: filterSphereIds } } : {}),
        },
      },
      select: { sphereId: true },
    });

    return access.map(a => a.sphereId);
  }

  /**
   * PostgreSQL full-text search
   */
  private static async textSearch(
    query: string,
    sphereIds: string[],
    filters: SearchOptions['filters'],
    limit: number,
    offset: number,
    includeHighlights: boolean
  ): Promise<{ results: SearchResult[]; totalCount: number }> {
    // Build search query for PostgreSQL full-text search
    const searchTerms = query
      .split(/\s+/)
      .filter(t => t.length > 2)
      .map(t => t.replace(/[^\w]/g, ''))
      .filter(t => t.length > 0)
      .join(' & ');

    if (!searchTerms) {
      return { results: [], totalCount: 0 };
    }

    // Build filter conditions
    const whereConditions: string[] = [
      `m.sphere_id = ANY($1::uuid[])`,
      `m.deleted_at IS NULL`,
      `(
        to_tsvector('english', COALESCE(m.content_text, '')) @@ to_tsquery('english', $2)
        OR to_tsvector('english', COALESCE(m.content_transcript, '')) @@ to_tsquery('english', $2)
      )`,
    ];

    const params: any[] = [sphereIds, searchTerms];
    let paramIndex = 3;

    if (filters?.emotions?.length) {
      whereConditions.push(`m.emotions && $${paramIndex}::text[]`);
      params.push(filters.emotions);
      paramIndex++;
    }

    if (filters?.tags?.length) {
      whereConditions.push(`m.tags && $${paramIndex}::text[]`);
      params.push(filters.tags);
      paramIndex++;
    }

    if (filters?.startDate) {
      whereConditions.push(`m.captured_at >= $${paramIndex}::timestamptz`);
      params.push(filters.startDate);
      paramIndex++;
    }

    if (filters?.endDate) {
      whereConditions.push(`m.captured_at <= $${paramIndex}::timestamptz`);
      params.push(filters.endDate);
      paramIndex++;
    }

    if (filters?.importance) {
      whereConditions.push(`m.importance >= $${paramIndex}::int`);
      params.push(filters.importance);
      paramIndex++;
    }

    const whereClause = whereConditions.join(' AND ');

    // Get total count
    const countResult = await prisma.$queryRawUnsafe<[{ count: bigint }]>(`
      SELECT COUNT(*) as count
      FROM moments m
      WHERE ${whereClause}
    `, ...params);

    const totalCount = Number(countResult[0]?.count || 0);

    if (totalCount === 0) {
      return { results: [], totalCount: 0 };
    }

    // Get results with ranking
    const highlightSelect = includeHighlights
      ? `, ts_headline('english', COALESCE(m.content_text, ''), to_tsquery('english', $2), 'MaxWords=50, MinWords=10') as content_highlight,
         ts_headline('english', COALESCE(m.content_transcript, ''), to_tsquery('english', $2), 'MaxWords=50, MinWords=10') as transcript_highlight`
      : '';

    const results = await prisma.$queryRawUnsafe<Array<{
      id: string;
      content_text: string;
      content_transcript: string | null;
      sphere_id: string;
      sphere_name: string;
      captured_at: Date;
      emotions: string[];
      tags: string[];
      importance: number | null;
      rank: number;
      content_highlight?: string;
      transcript_highlight?: string;
    }>>(`
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
        ts_rank(
          to_tsvector('english', COALESCE(m.content_text, '') || ' ' || COALESCE(m.content_transcript, '')),
          to_tsquery('english', $2)
        ) as rank
        ${highlightSelect}
      FROM moments m
      JOIN spheres s ON m.sphere_id = s.id
      WHERE ${whereClause}
      ORDER BY rank DESC, m.captured_at DESC
      LIMIT $${paramIndex} OFFSET $${paramIndex + 1}
    `, ...params, limit, offset);

    return {
      results: results.map(r => ({
        momentId: r.id,
        title: r.content_text?.substring(0, 100) || 'Untitled',
        content: r.content_text || '',
        transcript: r.content_transcript || undefined,
        sphereId: r.sphere_id,
        sphereName: r.sphere_name,
        capturedAt: r.captured_at,
        emotions: r.emotions || [],
        tags: r.tags || [],
        importance: r.importance || undefined,
        score: Number(r.rank),
        highlights: includeHighlights ? {
          content: r.content_highlight ? [r.content_highlight] : undefined,
          transcript: r.transcript_highlight ? [r.transcript_highlight] : undefined,
        } : undefined,
      })),
      totalCount,
    };
  }

  /**
   * Semantic search using vector embeddings
   */
  private static async semanticSearch(
    query: string,
    sphereIds: string[],
    filters: SearchOptions['filters'],
    limit: number,
    similarityThreshold: number
  ): Promise<{ results: SearchResult[]; totalCount: number }> {
    try {
      const similarMoments = await VectorEmbeddingService.findSimilarMoments(
        query,
        sphereIds,
        { limit, similarityThreshold }
      );

      // Apply additional filters
      let results = similarMoments.map(m => ({
        momentId: m.momentId,
        title: m.content.substring(0, 100),
        content: m.content,
        sphereId: '', // Will be populated from the vector service
        sphereName: m.sphereName,
        capturedAt: m.capturedAt,
        emotions: m.emotions,
        tags: m.tags,
        score: m.similarity,
        similarity: m.similarity,
      }));

      // Apply emotion and tag filters if specified
      if (filters?.emotions?.length) {
        results = results.filter(r => 
          r.emotions.some(e => filters.emotions!.includes(e))
        );
      }

      if (filters?.tags?.length) {
        results = results.filter(r => 
          r.tags.some(t => filters.tags!.includes(t))
        );
      }

      return {
        results,
        totalCount: results.length,
      };

    } catch (error) {
      console.error('Semantic search failed:', error);
      // Fall back to text search
      return this.textSearch(query, sphereIds, filters, limit, 0, false);
    }
  }

  /**
   * Find similar moments to a given moment ID
   */
  private static async findSimilarMoments(
    momentId: string,
    sphereIds: string[],
    limit: number
  ): Promise<{ results: SearchResult[]; totalCount: number }> {
    // Get the source moment's content
    const sourceMoment = await prisma.moment.findUnique({
      where: { id: momentId },
      select: { contentText: true, contentTranscript: true },
    });

    if (!sourceMoment) {
      return { results: [], totalCount: 0 };
    }

    const queryText = [sourceMoment.contentText, sourceMoment.contentTranscript]
      .filter(Boolean)
      .join(' ');

    return this.semanticSearch(queryText, sphereIds, {}, limit, 0.5);
  }

  /**
   * Hybrid search combining text and semantic search
   */
  private static async hybridSearchImpl(
    query: string,
    sphereIds: string[],
    filters: SearchOptions['filters'],
    limit: number,
    offset: number,
    includeHighlights: boolean,
    similarityThreshold: number,
    boostFactors: SearchOptions['boostFactors']
  ): Promise<{ results: SearchResult[]; totalCount: number }> {
    // Run both searches in parallel
    const [textResults, semanticResults] = await Promise.all([
      this.textSearch(query, sphereIds, filters, limit * 2, 0, includeHighlights),
      this.semanticSearch(query, sphereIds, filters, limit * 2, similarityThreshold),
    ]);

    // Merge and deduplicate results
    const resultMap = new Map<string, SearchResult>();

    // Add text search results
    for (const result of textResults.results) {
      resultMap.set(result.momentId, {
        ...result,
        score: result.score * 0.6, // Weight text results
      });
    }

    // Merge semantic search results
    for (const result of semanticResults.results) {
      const existing = resultMap.get(result.momentId);
      if (existing) {
        // Boost score if found in both
        existing.score += (result.similarity || 0) * 0.4;
        existing.similarity = result.similarity;
      } else {
        resultMap.set(result.momentId, {
          ...result,
          score: (result.similarity || 0) * 0.4,
        });
      }
    }

    // Apply boost factors
    let results = Array.from(resultMap.values());
    
    if (boostFactors?.recency) {
      const now = Date.now();
      const dayMs = 24 * 60 * 60 * 1000;
      results = results.map(r => {
        const ageInDays = (now - r.capturedAt.getTime()) / dayMs;
        const recencyBoost = Math.max(0, 1 - (ageInDays / 365)) * boostFactors.recency!;
        return { ...r, score: r.score + recencyBoost };
      });
    }

    if (boostFactors?.importance) {
      results = results.map(r => {
        const importanceBoost = ((r.importance || 3) / 5) * boostFactors.importance!;
        return { ...r, score: r.score + importanceBoost };
      });
    }

    // Sort by score and apply pagination
    results.sort((a, b) => b.score - a.score);
    const paginatedResults = results.slice(offset, offset + limit);

    return {
      results: paginatedResults,
      totalCount: results.length,
    };
  }

  /**
   * Get search suggestions based on user's history
   */
  static async getSearchSuggestions(userId: string, query: string, limit: number = 5): Promise<SuggestionResponse> {
    if (!query || query.length < 2) {
      return [];
    }

    try {
      // Get user's recent searches and common tags
      const [recentTags, recentEmotions] = await Promise.all([
        prisma.moment.findMany({
          where: {
            userId,
            deletedAt: null,
            tags: { isEmpty: false },
          },
          select: { tags: true },
          orderBy: { capturedAt: 'desc' },
          take: 50,
        }),
        prisma.moment.findMany({
          where: {
            userId,
            deletedAt: null,
            emotions: { isEmpty: false },
          },
          select: { emotions: true },
          orderBy: { capturedAt: 'desc' },
          take: 50,
        }),
      ]);

      // Collect all tags and emotions
      const allTags = recentTags.flatMap(m => m.tags);
      const allEmotions = recentEmotions.flatMap(m => m.emotions);

      // Count occurrences
      const tagCounts = new Map<string, number>();
      for (const tag of allTags) {
        tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
      }

      const emotionCounts = new Map<string, number>();
      for (const emotion of allEmotions) {
        emotionCounts.set(emotion, (emotionCounts.get(emotion) || 0) + 1);
      }

      // Filter by query prefix
      const queryLower = query.toLowerCase();
      const matchingTags = Array.from(tagCounts.entries())
        .filter(([tag]) => tag.toLowerCase().startsWith(queryLower))
        .sort((a, b) => b[1] - a[1])
        .slice(0, limit)
        .map(([tag]) => tag);

      const matchingEmotions = Array.from(emotionCounts.entries())
        .filter(([emotion]) => emotion.toLowerCase().startsWith(queryLower))
        .sort((a, b) => b[1] - a[1])
        .slice(0, limit - matchingTags.length)
        .map(([emotion]) => emotion);

      return [...matchingTags, ...matchingEmotions].slice(0, limit);

    } catch (error) {
      console.error('Failed to get search suggestions:', error);
      return [];
    }
  }
}

