/**
 * Semantic Search Service
 *
 * Thin orchestration layer over the hybrid search ranker with a controlled
 * keyword fallback for degraded operation.
 *
 * @doc.type service
 * @doc.purpose Semantic-first content discovery with hybrid-search reuse
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import { HybridSearchService } from "./hybrid-search-service.js";

type ContentAssetType = string;

interface ContentAsset {
  id: string;
  tenantId: string;
  slug: string;
  title: string;
  assetType: ContentAssetType;
  domain: string;
  status: string;
  currentVersion: number;
  tags: string[];
  targetGrades: string[];
  authorId: string;
  createdAt: string;
  updatedAt: string;
  riskLevel: string;
  conceptId?: string;
  qualityScore?: number;
  semanticIndexStatus?: "pending" | "indexed" | "stale";
  recommendationStatus?: "pending" | "computed" | "stale";
  difficultyLevel?: string;
  lastEditedBy?: string;
  publishedAt?: string;
  promptHash?: string;
  confidenceScore?: number;
  legacyModuleId?: string;
  legacyExperienceId?: string;
}

interface HybridSearchResult {
  asset: ContentAsset;
  ranking: {
    score: number;
    signals: unknown[];
    matchReason: string;
  };
  highlights: Array<{ field: string; snippet: string }>;
}

export interface SemanticSearchOptions {
  tenantId: string;
  query: string;
  assetTypes?: ContentAssetType[];
  domain?: string;
  limit?: number;
  offset?: number;
  explain?: boolean;
}

export interface SemanticSearchResult {
  results: HybridSearchResult[];
  total: number;
  queryTime: number;
  explanation?: string;
}

export class SemanticSearchService {
  private readonly hybridSearchService: HybridSearchService;

  constructor(
    private readonly prisma: PrismaClient,
    deps: { hybridSearchService?: HybridSearchService } = {},
  ) {
    this.hybridSearchService =
      deps.hybridSearchService ?? new HybridSearchService(prisma);
  }

  async search(options: SemanticSearchOptions): Promise<SemanticSearchResult> {
    const startTime = Date.now();

    try {
      const result = await this.hybridSearchService.search(options);
      return {
        results: result.results,
        total: result.total,
        queryTime: result.took,
        ...(options.explain
          ? { explanation: this.generateExplanation(result.results, options.query) }
          : {}),
      };
    } catch (_error) {
      const fallback = await this.fallbackToKeywordSearch(options);
      return {
        ...fallback,
        queryTime: Date.now() - startTime,
      };
    }
  }

  private async fallbackToKeywordSearch(
    options: SemanticSearchOptions,
  ): Promise<Omit<SemanticSearchResult, "queryTime">> {
    const where: Record<string, unknown> = {
      tenantId: options.tenantId,
      status: "PUBLISHED",
      OR: [
        { title: { contains: options.query } },
        { searchableText: { contains: options.query } },
      ],
    };

    if (options.assetTypes?.length) {
      where.assetType = { in: options.assetTypes.map((assetType) => assetType.toUpperCase()) };
    }

    if (options.domain) {
      where.domain = options.domain;
    }

    const rows = await (this.prisma as any).contentAsset.findMany({
      where,
      orderBy: { updatedAt: "desc" },
      take: options.limit ?? 20,
      skip: options.offset ?? 0,
    });

    const results = rows.map((row: Record<string, unknown>) =>
      this.createFallbackResult(row, options.query),
    );

    return {
      results,
      total: results.length,
      ...(options.explain
        ? {
            explanation:
              "Hybrid search degraded; returning keyword-ranked fallback results",
          }
        : {}),
    };
  }

  private createFallbackResult(
    raw: Record<string, unknown>,
    query: string,
  ): HybridSearchResult {
    const title = String(raw.title ?? "");
    const searchableText = String(raw.searchableText ?? "");
    const snippetSource = searchableText || title;
    const snippet = snippetSource.slice(0, 140);
    const lexicalScore = scoreLexicalFallback(title, searchableText, query);

    return {
      asset: mapContentAsset(raw),
      ranking: {
        score: lexicalScore,
        signals: [],
        matchReason: "Keyword fallback ranking",
      },
      highlights: snippet
        ? [{ field: searchableText ? "searchableText" : "title", snippet }]
        : [],
    };
  }

  private generateExplanation(
    results: HybridSearchResult[],
    query: string,
  ): string {
    if (results.length === 0) {
      return `No semantic results found for "${query}".`;
    }

    const top = results[0];
    const averageScore =
      results.reduce((sum, result) => sum + result.ranking.score, 0) /
      results.length;

    return `Found ${results.length} results for "${query}". Top result "${top!.asset.title}" scored ${(top!.ranking.score * 100).toFixed(1)} with ${(averageScore * 100).toFixed(1)} average relevance.`;
  }
}

function scoreLexicalFallback(
  title: string,
  searchableText: string,
  query: string,
): number {
  const haystack = `${title} ${searchableText}`.toLowerCase();
  const terms = query
    .toLowerCase()
    .split(/\s+/)
    .map((term) => term.trim())
    .filter(Boolean);

  if (terms.length === 0) {
    return 0;
  }

  const matches = terms.filter((term) => haystack.includes(term)).length;
  const titleBonus = title.toLowerCase().startsWith(query.toLowerCase()) ? 0.2 : 0;
  return Math.min(1, matches / terms.length + titleBonus);
}

function mapContentAsset(raw: Record<string, unknown>): ContentAsset {
  const asset: ContentAsset = {
    id: String(raw.id),
    tenantId: String(raw.tenantId),
    slug: String(raw.slug),
    title: String(raw.title),
    assetType: String(raw.assetType).toLowerCase() as ContentAssetType,
    domain: String(raw.domain),
    ...(raw.conceptId ? { conceptId: String(raw.conceptId) } : {}),
    status: String(raw.status).toLowerCase() as ContentAsset["status"],
    currentVersion: Number(raw.currentVersion ?? 1),
    tags: Array.isArray(raw.tags) ? (raw.tags as string[]) : [],
    targetGrades: Array.isArray(raw.targetGrades) ? (raw.targetGrades as string[]) : [],
    authorId: String(raw.authorId ?? "system"),
    createdAt: new Date(String(raw.createdAt ?? new Date().toISOString())).toISOString(),
    updatedAt: new Date(String(raw.updatedAt ?? new Date().toISOString())).toISOString(),
    riskLevel: String(raw.riskLevel ?? "LOW").toUpperCase() as ContentAsset["riskLevel"],
  };

  if (raw.qualityScore != null) asset.qualityScore = Number(raw.qualityScore);
  if (raw.semanticIndexStatus) {
    const semanticIndexStatus = String(raw.semanticIndexStatus).toLowerCase();
    if (
      semanticIndexStatus === "pending" ||
      semanticIndexStatus === "indexed" ||
      semanticIndexStatus === "stale"
    ) {
      asset.semanticIndexStatus = semanticIndexStatus;
    }
  }
  if (raw.recommendationStatus) {
    const recommendationStatus = String(raw.recommendationStatus).toLowerCase();
    if (
      recommendationStatus === "pending" ||
      recommendationStatus === "computed" ||
      recommendationStatus === "stale"
    ) {
      asset.recommendationStatus = recommendationStatus;
    }
  }
  if (raw.difficultyLevel) asset.difficultyLevel = String(raw.difficultyLevel);
  if (raw.lastEditedBy) asset.lastEditedBy = String(raw.lastEditedBy);
  if (raw.publishedAt) {
    asset.publishedAt = new Date(String(raw.publishedAt)).toISOString();
  }
  if (raw.promptHash) asset.promptHash = String(raw.promptHash);
  if (raw.confidenceScore != null) {
    asset.confidenceScore = Number(raw.confidenceScore);
  }
  if (raw.legacyModuleId) asset.legacyModuleId = String(raw.legacyModuleId);
  if (raw.legacyExperienceId) {
    asset.legacyExperienceId = String(raw.legacyExperienceId);
  }

  return asset;
}
