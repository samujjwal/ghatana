/**
 * Faceted Search Service for Flashit
 * Provides multi-faceted filtering with aggregations for search refinement
 *
 * @doc.type service
 * @doc.purpose Context-aware faceted search with dynamic filters
 * @doc.layer product
 * @doc.pattern SearchService
 */

import { prisma } from '../../lib/prisma.js';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface FacetValue {
  value: string;
  count: number;
  selected: boolean;
}

export interface DateRangeFacet {
  min: Date;
  max: Date;
  buckets: Array<{
    start: Date;
    end: Date;
    count: number;
  }>;
}

export interface NumericFacet {
  min: number;
  max: number;
  average: number;
  distribution: Array<{
    range: string;
    count: number;
  }>;
}

export interface Facets {
  emotions: FacetValue[];
  tags: FacetValue[];
  spheres: FacetValue[];
  contentTypes: FacetValue[];
  dateRange: DateRangeFacet;
  importance: NumericFacet;
  hasMedia: FacetValue[];
  hasTranscript: FacetValue[];
}

export interface FacetedSearchFilters {
  query?: string;
  sphereIds?: string[];
  emotions?: string[];
  tags?: string[];
  contentTypes?: string[];
  startDate?: Date;
  endDate?: Date;
  importanceMin?: number;
  importanceMax?: number;
  hasMedia?: boolean;
  hasTranscript?: boolean;
}

export interface FacetedSearchOptions {
  userId: string;
  filters: FacetedSearchFilters;
  limit?: number;
  offset?: number;
  sortBy?: 'relevance' | 'date' | 'importance';
  sortOrder?: 'asc' | 'desc';
  includeFacets?: boolean;
  facetLimit?: number;
}

export interface FacetedSearchResult {
  id: string;
  content: string;
  transcript?: string | null;
  sphereId: string;
  sphereName: string;
  capturedAt: Date;
  emotions: string[];
  tags: string[];
  importance: number;
  contentType: string;
  hasMedia: boolean;
  hasTranscript: boolean;
  score?: number;
}

export interface FacetedSearchResponse {
  results: FacetedSearchResult[];
  totalCount: number;
  facets?: Facets;
  appliedFilters: FacetedSearchFilters;
  analytics: {
    processingTimeMs: number;
    facetComputeTimeMs: number;
    queryTimeMs: number;
  };
}

export interface SavedSearch {
  id: string;
  userId: string;
  name: string;
  filters: FacetedSearchFilters;
  isDefault: boolean;
  createdAt: Date;
  lastUsedAt: Date;
  useCount: number;
}

export interface SearchHistory {
  id: string;
  userId: string;
  query: string;
  filters: FacetedSearchFilters;
  resultCount: number;
  searchedAt: Date;
}

// ============================================================================
// Faceted Search Service
// ============================================================================

/**
 * FacetedSearchService provides multi-faceted filtering with aggregations
 */
export class FacetedSearchService {
  private static readonly FACET_LIMIT_DEFAULT = 20;
  private static readonly SEARCH_LIMIT_DEFAULT = 20;

  /**
   * Execute a faceted search with dynamic filter aggregations
   */
  static async search(options: FacetedSearchOptions): Promise<FacetedSearchResponse> {
    const startTime = Date.now();
    const {
      userId,
      filters,
      limit = this.SEARCH_LIMIT_DEFAULT,
      offset = 0,
      sortBy = 'relevance',
      sortOrder = 'desc',
      includeFacets = true,
      facetLimit = this.FACET_LIMIT_DEFAULT,
    } = options;

    // Get accessible spheres for user
    const accessibleSphereIds = await this.getAccessibleSpheres(userId, filters.sphereIds);
    
    if (accessibleSphereIds.length === 0) {
      return this.emptyResponse(filters, startTime);
    }

    // Build base query conditions
    const whereConditions = this.buildWhereConditions(filters, accessibleSphereIds);

    // Execute search query
    const queryStart = Date.now();
    const [results, totalCount] = await Promise.all([
      this.executeSearchQuery(whereConditions, filters.query, limit, offset, sortBy, sortOrder),
      this.getResultCount(whereConditions),
    ]);
    const queryTimeMs = Date.now() - queryStart;

    // Compute facets if requested
    let facets: Facets | undefined;
    let facetComputeTimeMs = 0;
    
    if (includeFacets) {
      const facetStart = Date.now();
      facets = await this.computeFacets(whereConditions, filters, accessibleSphereIds, facetLimit);
      facetComputeTimeMs = Date.now() - facetStart;
    }

    return {
      results,
      totalCount,
      facets,
      appliedFilters: filters,
      analytics: {
        processingTimeMs: Date.now() - startTime,
        facetComputeTimeMs,
        queryTimeMs,
      },
    };
  }

  /**
   * Get accessible sphere IDs for the user
   */
  private static async getAccessibleSpheres(userId: string, filterSphereIds?: string[]): Promise<string[]> {
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
   * Build WHERE conditions for the search query
   */
  private static buildWhereConditions(
    filters: FacetedSearchFilters,
    sphereIds: string[]
  ): Record<string, unknown> {
    const conditions: Record<string, unknown> = {
      sphereId: { in: sphereIds },
      deletedAt: null,
    };

    // Text search
    if (filters.query && filters.query.trim().length > 0) {
      conditions.OR = [
        { contentText: { contains: filters.query, mode: 'insensitive' } },
        { contentTranscript: { contains: filters.query, mode: 'insensitive' } },
      ];
    }

    // Emotion filter
    if (filters.emotions?.length) {
      conditions.emotions = { hasSome: filters.emotions };
    }

    // Tag filter
    if (filters.tags?.length) {
      conditions.tags = { hasSome: filters.tags };
    }

    // Content type filter
    if (filters.contentTypes?.length) {
      conditions.type = { in: filters.contentTypes };
    }

    // Date range filter
    if (filters.startDate || filters.endDate) {
      conditions.capturedAt = {
        ...(filters.startDate && { gte: filters.startDate }),
        ...(filters.endDate && { lte: filters.endDate }),
      };
    }

    // Importance filter
    if (filters.importanceMin !== undefined || filters.importanceMax !== undefined) {
      conditions.importance = {
        ...(filters.importanceMin !== undefined && { gte: filters.importanceMin }),
        ...(filters.importanceMax !== undefined && { lte: filters.importanceMax }),
      };
    }

    // Media filter
    if (filters.hasMedia !== undefined) {
      if (filters.hasMedia) {
        conditions.OR = [
          { voiceUrl: { not: null } },
          { imageUrl: { not: null } },
          { videoUrl: { not: null } },
        ];
      }
    }

    // Transcript filter
    if (filters.hasTranscript !== undefined) {
      conditions.contentTranscript = filters.hasTranscript 
        ? { not: null }
        : null;
    }

    return conditions;
  }

  /**
   * Execute the main search query
   */
  private static async executeSearchQuery(
    whereConditions: Record<string, unknown>,
    query: string | undefined,
    limit: number,
    offset: number,
    sortBy: 'relevance' | 'date' | 'importance',
    sortOrder: 'asc' | 'desc'
  ): Promise<FacetedSearchResult[]> {
    // Build sort order
    let orderBy: Record<string, string>[];
    switch (sortBy) {
      case 'date':
        orderBy = [{ capturedAt: sortOrder }];
        break;
      case 'importance':
        orderBy = [{ importance: sortOrder }, { capturedAt: 'desc' }];
        break;
      case 'relevance':
      default:
        // For relevance, we sort by a combination of factors
        orderBy = [{ importance: 'desc' }, { capturedAt: 'desc' }];
        break;
    }

    const moments = await prisma.moment.findMany({
      where: whereConditions,
      orderBy,
      take: limit,
      skip: offset,
      include: {
        sphere: {
          select: { name: true },
        },
      },
    });

    return moments.map(m => ({
      id: m.id,
      content: m.contentText || '',
      transcript: m.contentTranscript,
      sphereId: m.sphereId,
      sphereName: m.sphere.name,
      capturedAt: m.capturedAt,
      emotions: m.emotions,
      tags: m.tags,
      importance: m.importance || 0,
      contentType: m.type,
      hasMedia: !!(m.voiceUrl || m.imageUrl || m.videoUrl),
      hasTranscript: !!m.contentTranscript,
    }));
  }

  /**
   * Get total count for the query
   */
  private static async getResultCount(whereConditions: Record<string, unknown>): Promise<number> {
    return prisma.moment.count({ where: whereConditions });
  }

  /**
   * Compute all facets for the current search
   */
  private static async computeFacets(
    whereConditions: Record<string, unknown>,
    appliedFilters: FacetedSearchFilters,
    sphereIds: string[],
    facetLimit: number
  ): Promise<Facets> {
    // Get base conditions without specific filters for facet computation
    const baseConditions = {
      sphereId: { in: sphereIds },
      deletedAt: null,
    };

    // Compute all facets in parallel
    const [
      emotionFacets,
      tagFacets,
      sphereFacets,
      contentTypeFacets,
      dateRangeFacet,
      importanceFacet,
      mediaFacets,
      transcriptFacets,
    ] = await Promise.all([
      this.computeArrayFacet('emotions', whereConditions, appliedFilters.emotions, facetLimit),
      this.computeArrayFacet('tags', whereConditions, appliedFilters.tags, facetLimit),
      this.computeSphereFacet(baseConditions, sphereIds, appliedFilters.sphereIds, facetLimit),
      this.computeTypeFacet(whereConditions, appliedFilters.contentTypes, facetLimit),
      this.computeDateRangeFacet(whereConditions),
      this.computeImportanceFacet(whereConditions),
      this.computeBooleanFacet('hasMedia', whereConditions, appliedFilters.hasMedia),
      this.computeTranscriptFacet(whereConditions, appliedFilters.hasTranscript),
    ]);

    return {
      emotions: emotionFacets,
      tags: tagFacets,
      spheres: sphereFacets,
      contentTypes: contentTypeFacets,
      dateRange: dateRangeFacet,
      importance: importanceFacet,
      hasMedia: mediaFacets,
      hasTranscript: transcriptFacets,
    };
  }

  /**
   * Compute facet for array fields (emotions, tags)
   */
  private static async computeArrayFacet(
    field: 'emotions' | 'tags',
    whereConditions: Record<string, unknown>,
    selectedValues: string[] | undefined,
    limit: number
  ): Promise<FacetValue[]> {
    const selectedSet = new Set(selectedValues || []);

    // Use raw SQL for array aggregation
    const result = await prisma.$queryRaw<Array<{ value: string; count: bigint }>>`
      SELECT unnest(${field === 'emotions' ? prisma.$queryRaw`emotions` : prisma.$queryRaw`tags`}) as value, 
             COUNT(*) as count
      FROM "Moment"
      WHERE deleted_at IS NULL
      GROUP BY value
      ORDER BY count DESC
      LIMIT ${limit}
    `;

    return result.map(r => ({
      value: r.value,
      count: Number(r.count),
      selected: selectedSet.has(r.value),
    }));
  }

  /**
   * Compute sphere facet
   */
  private static async computeSphereFacet(
    baseConditions: Record<string, unknown>,
    accessibleSphereIds: string[],
    selectedSphereIds: string[] | undefined,
    limit: number
  ): Promise<FacetValue[]> {
    const selectedSet = new Set(selectedSphereIds || []);

    const sphereCounts = await prisma.moment.groupBy({
      by: ['sphereId'],
      where: {
        ...baseConditions,
        sphereId: { in: accessibleSphereIds },
      },
      _count: { id: true },
      orderBy: { _count: { id: 'desc' } },
      take: limit,
    });

    // Get sphere names
    const spheres = await prisma.sphere.findMany({
      where: { id: { in: sphereCounts.map(s => s.sphereId) } },
      select: { id: true, name: true },
    });

    const sphereNameMap = new Map(spheres.map(s => [s.id, s.name]));

    return sphereCounts.map(sc => ({
      value: sphereNameMap.get(sc.sphereId) || sc.sphereId,
      count: sc._count.id,
      selected: selectedSet.has(sc.sphereId),
    }));
  }

  /**
   * Compute content type facet
   */
  private static async computeTypeFacet(
    whereConditions: Record<string, unknown>,
    selectedTypes: string[] | undefined,
    limit: number
  ): Promise<FacetValue[]> {
    const selectedSet = new Set(selectedTypes || []);

    const typeCounts = await prisma.moment.groupBy({
      by: ['type'],
      where: whereConditions,
      _count: { id: true },
      orderBy: { _count: { id: 'desc' } },
      take: limit,
    });

    return typeCounts.map(tc => ({
      value: tc.type,
      count: tc._count.id,
      selected: selectedSet.has(tc.type),
    }));
  }

  /**
   * Compute date range facet with buckets
   */
  private static async computeDateRangeFacet(
    whereConditions: Record<string, unknown>
  ): Promise<DateRangeFacet> {
    const dateStats = await prisma.moment.aggregate({
      where: whereConditions,
      _min: { capturedAt: true },
      _max: { capturedAt: true },
    });

    const minDate = dateStats._min.capturedAt || new Date();
    const maxDate = dateStats._max.capturedAt || new Date();

    // Create monthly buckets
    const buckets: DateRangeFacet['buckets'] = [];
    const current = new Date(minDate);
    current.setDate(1); // Start of month

    while (current <= maxDate) {
      const start = new Date(current);
      const end = new Date(current);
      end.setMonth(end.getMonth() + 1);
      end.setDate(0); // End of month

      const count = await prisma.moment.count({
        where: {
          ...whereConditions,
          capturedAt: {
            gte: start,
            lte: end,
          },
        },
      });

      buckets.push({ start, end, count });
      current.setMonth(current.getMonth() + 1);
    }

    return { min: minDate, max: maxDate, buckets };
  }

  /**
   * Compute importance facet
   */
  private static async computeImportanceFacet(
    whereConditions: Record<string, unknown>
  ): Promise<NumericFacet> {
    const stats = await prisma.moment.aggregate({
      where: whereConditions,
      _min: { importance: true },
      _max: { importance: true },
      _avg: { importance: true },
    });

    // Create distribution buckets (1-2, 3-4, 5-6, 7-8, 9-10)
    const distribution: NumericFacet['distribution'] = [];
    const ranges = [
      { range: '1-2', min: 1, max: 2 },
      { range: '3-4', min: 3, max: 4 },
      { range: '5-6', min: 5, max: 6 },
      { range: '7-8', min: 7, max: 8 },
      { range: '9-10', min: 9, max: 10 },
    ];

    for (const { range, min, max } of ranges) {
      const count = await prisma.moment.count({
        where: {
          ...whereConditions,
          importance: { gte: min, lte: max },
        },
      });
      distribution.push({ range, count });
    }

    return {
      min: stats._min.importance || 0,
      max: stats._max.importance || 10,
      average: stats._avg.importance || 5,
      distribution,
    };
  }

  /**
   * Compute boolean facet for media presence
   */
  private static async computeBooleanFacet(
    field: string,
    whereConditions: Record<string, unknown>,
    selectedValue: boolean | undefined
  ): Promise<FacetValue[]> {
    const withMedia = await prisma.moment.count({
      where: {
        ...whereConditions,
        OR: [
          { voiceUrl: { not: null } },
          { imageUrl: { not: null } },
          { videoUrl: { not: null } },
        ],
      },
    });

    const withoutMedia = await prisma.moment.count({
      where: {
        ...whereConditions,
        voiceUrl: null,
        imageUrl: null,
        videoUrl: null,
      },
    });

    return [
      { value: 'With Media', count: withMedia, selected: selectedValue === true },
      { value: 'Without Media', count: withoutMedia, selected: selectedValue === false },
    ];
  }

  /**
   * Compute transcript presence facet
   */
  private static async computeTranscriptFacet(
    whereConditions: Record<string, unknown>,
    selectedValue: boolean | undefined
  ): Promise<FacetValue[]> {
    const withTranscript = await prisma.moment.count({
      where: {
        ...whereConditions,
        contentTranscript: { not: null },
      },
    });

    const withoutTranscript = await prisma.moment.count({
      where: {
        ...whereConditions,
        contentTranscript: null,
      },
    });

    return [
      { value: 'With Transcript', count: withTranscript, selected: selectedValue === true },
      { value: 'Without Transcript', count: withoutTranscript, selected: selectedValue === false },
    ];
  }

  /**
   * Empty response helper
   */
  private static emptyResponse(filters: FacetedSearchFilters, startTime: number): FacetedSearchResponse {
    return {
      results: [],
      totalCount: 0,
      appliedFilters: filters,
      analytics: {
        processingTimeMs: Date.now() - startTime,
        facetComputeTimeMs: 0,
        queryTimeMs: 0,
      },
    };
  }

  // ============================================================================
  // Saved Searches
  // ============================================================================

  /**
   * Save a search for later use
   */
  static async saveSearch(
    userId: string,
    name: string,
    filters: FacetedSearchFilters,
    isDefault: boolean = false
  ): Promise<SavedSearch> {
    // If setting as default, unset other defaults
    if (isDefault) {
      await prisma.savedSearch.updateMany({
        where: { userId, isDefault: true },
        data: { isDefault: false },
      });
    }

    const saved = await prisma.savedSearch.create({
      data: {
        userId,
        name,
        filters: filters as any,
        isDefault,
        useCount: 0,
      },
    });

    return {
      id: saved.id,
      userId: saved.userId,
      name: saved.name,
      filters: saved.filters as unknown as FacetedSearchFilters,
      isDefault: saved.isDefault,
      createdAt: saved.createdAt,
      lastUsedAt: saved.lastUsedAt || saved.createdAt,
      useCount: saved.useCount,
    };
  }

  /**
   * Get user's saved searches
   */
  static async getSavedSearches(userId: string): Promise<SavedSearch[]> {
    const searches = await prisma.savedSearch.findMany({
      where: { userId },
      orderBy: [{ isDefault: 'desc' }, { lastUsedAt: 'desc' }],
    });

    return searches.map(s => ({
      id: s.id,
      userId: s.userId,
      name: s.name,
      filters: s.filters as unknown as FacetedSearchFilters,
      isDefault: s.isDefault,
      createdAt: s.createdAt,
      lastUsedAt: s.lastUsedAt || s.createdAt,
      useCount: s.useCount,
    }));
  }

  /**
   * Execute a saved search
   */
  static async executeSavedSearch(
    savedSearchId: string,
    userId: string,
    options?: Partial<FacetedSearchOptions>
  ): Promise<FacetedSearchResponse> {
    const saved = await prisma.savedSearch.findFirst({
      where: { id: savedSearchId, userId },
    });

    if (!saved) {
      throw new Error('Saved search not found');
    }

    // Update usage stats
    await prisma.savedSearch.update({
      where: { id: savedSearchId },
      data: {
        useCount: { increment: 1 },
        lastUsedAt: new Date(),
      },
    });

    return this.search({
      userId,
      filters: saved.filters as unknown as FacetedSearchFilters,
      ...options,
    });
  }

  /**
   * Delete a saved search
   */
  static async deleteSavedSearch(savedSearchId: string, userId: string): Promise<void> {
    await prisma.savedSearch.deleteMany({
      where: { id: savedSearchId, userId },
    });
  }

  // ============================================================================
  // Search History
  // ============================================================================

  /**
   * Record a search in history
   */
  static async recordSearch(
    userId: string,
    query: string,
    filters: FacetedSearchFilters,
    resultCount: number
  ): Promise<void> {
    await prisma.searchHistory.create({
      data: {
        userId,
        query,
        filters: filters as any,
        resultCount,
      },
    });

    // Keep only last 100 searches per user
    const oldSearches = await prisma.searchHistory.findMany({
      where: { userId },
      orderBy: { searchedAt: 'desc' },
      skip: 100,
      select: { id: true },
    });

    if (oldSearches.length > 0) {
      await prisma.searchHistory.deleteMany({
        where: { id: { in: oldSearches.map(s => s.id) } },
      });
    }
  }

  /**
   * Get user's search history
   */
  static async getSearchHistory(userId: string, limit: number = 20): Promise<SearchHistory[]> {
    const history = await prisma.searchHistory.findMany({
      where: { userId },
      orderBy: { searchedAt: 'desc' },
      take: limit,
    });

    return history.map(h => ({
      id: h.id,
      userId: h.userId,
      query: h.query,
      filters: h.filters as unknown as FacetedSearchFilters,
      resultCount: h.resultCount,
      searchedAt: h.searchedAt,
    }));
  }

  /**
   * Clear search history
   */
  static async clearSearchHistory(userId: string): Promise<void> {
    await prisma.searchHistory.deleteMany({
      where: { userId },
    });
  }
}

export default FacetedSearchService;
