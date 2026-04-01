/**
 * Search System Validator
 *
 * Validates discovery quality against live published assets for a tenant.
 *
 * @doc.type service
 * @doc.purpose Validate search and recommendation quality using real services
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
type ContentAssetType = string;
type HybridSearchResult = {
  asset: { id: string; title: string };
  ranking?: { score?: number };
};
import { RecommendationEngine } from "../recommendation/recommendation-engine.js";
import { RecommendationService } from "../recommendation/recommendation-service.js";
import { SemanticSearchService } from "../semantic/semantic-search-service.js";

export interface SearchValidationResult {
  testType: "keyword_search" | "semantic_search" | "autocomplete" | "recommendations";
  query: string;
  expectedResults: number;
  actualResults: number;
  relevanceScore: number;
  passed: boolean;
  issues: string[];
  metrics: {
    responseTime: number;
    resultAccuracy: number;
    rankingQuality: number;
  };
}

export interface DiscoverySystemReport {
  overallScore: number;
  searchTests: SearchValidationResult[];
  recommendationTests: SearchValidationResult[];
  autocompleteTests: SearchValidationResult[];
  criticalIssues: string[];
  recommendations: string[];
  testedAt: Date;
}

interface ValidationAsset {
  id: string;
  title: string;
  domain: string;
  assetType: ContentAssetType;
  tags: string[];
}

export class SearchSystemValidator {
  private readonly searchService: SemanticSearchService;
  private readonly recommendationEngine: RecommendationEngine;
  private readonly recommendationService: RecommendationService;

  constructor(
    private readonly prisma: PrismaClient,
    deps: {
      searchService?: SemanticSearchService;
      recommendationEngine?: RecommendationEngine;
      recommendationService?: RecommendationService;
    } = {},
  ) {
    this.recommendationService =
      deps.recommendationService ?? new RecommendationService(prisma);
    this.recommendationEngine =
      deps.recommendationEngine ??
      new RecommendationEngine(prisma, {
        recommendationService: this.recommendationService,
      });
    this.searchService =
      deps.searchService ?? new SemanticSearchService(prisma);
  }

  async validateDiscoverySystem(tenantId: string): Promise<DiscoverySystemReport> {
    const assets = await this.loadValidationAssets(tenantId);
    if (assets.length === 0) {
      return {
        overallScore: 0,
        searchTests: [],
        recommendationTests: [],
        autocompleteTests: [],
        criticalIssues: ["No published content assets available for discovery validation"],
        recommendations: [
          "Publish canonical content assets before running discovery validation",
        ],
        testedAt: new Date(),
      };
    }

    const searchTests = await Promise.all(
      assets.slice(0, 3).map((asset, index) =>
        this.runSearchTest(tenantId, asset, index === 0 ? "keyword_search" : "semantic_search"),
      ),
    );
    const recommendationTests = await this.testRecommendationQuality(tenantId, assets);
    const autocompleteTests = await this.testAutocompleteFunctionality(tenantId, assets);

    const allTests = [...searchTests, ...recommendationTests, ...autocompleteTests];
    const criticalIssues = allTests
      .filter((test) => !test.passed && test.relevanceScore < 55)
      .map((test) => `${test.testType} failed for "${test.query}"`);

    return {
      overallScore: calculateOverallScore(allTests),
      searchTests,
      recommendationTests,
      autocompleteTests,
      criticalIssues,
      recommendations: this.generateRecommendations(allTests, criticalIssues),
      testedAt: new Date(),
    };
  }

  async testAutocompleteFunctionality(
    tenantId: string,
    assets?: ValidationAsset[],
  ): Promise<SearchValidationResult[]> {
    const seedAssets = assets ?? (await this.loadValidationAssets(tenantId));
    const targets = seedAssets.slice(0, 3);
    const results: SearchValidationResult[] = [];

    for (const asset of targets) {
      const query = deriveAutocompletePrefix(asset);
      const startedAt = Date.now();
      const suggestions = await this.getAutocompleteSuggestions(tenantId, query);
      const expectedCompletions = [asset.domain, asset.title];
      const relevanceScore = calculateAutocompleteRelevance(
        suggestions,
        expectedCompletions,
      );

      results.push({
        testType: "autocomplete",
        query,
        expectedResults: 2,
        actualResults: suggestions.length,
        relevanceScore,
        passed: suggestions.length >= 2 && relevanceScore >= 65,
        issues: [
          ...(suggestions.length < 2
            ? [`Insufficient suggestions for prefix "${query}"`]
            : []),
          ...(relevanceScore < 65
            ? [`Autocomplete relevance below threshold for "${query}"`]
            : []),
        ],
        metrics: {
          responseTime: Date.now() - startedAt,
          resultAccuracy: relevanceScore,
          rankingQuality: calculateAutocompleteRanking(suggestions),
        },
      });
    }

    return results;
  }

  async testRecommendationQuality(
    tenantId: string,
    assets?: ValidationAsset[],
  ): Promise<SearchValidationResult[]> {
    const seedAssets = assets ?? (await this.loadValidationAssets(tenantId));
    const results: SearchValidationResult[] = [];

    for (const asset of seedAssets.slice(0, 3)) {
      const startedAt = Date.now();
      let recommendations = await this.recommendationEngine.getRecommendations({
        tenantId,
        assetId: asset.id,
        limit: 6,
      });

      const totalBeforeBootstrap =
        recommendations.related.length +
        recommendations.followUps.length +
        recommendations.prerequisites.length +
        recommendations.alternatives.length;

      if (totalBeforeBootstrap === 0) {
        await this.recommendationService.bootstrapEdges(tenantId, asset.id);
        recommendations = await this.recommendationEngine.getRecommendations({
          tenantId,
          assetId: asset.id,
          limit: 6,
        });
      }

      const allSuggestions = [
        ...recommendations.related,
        ...recommendations.followUps,
        ...recommendations.prerequisites,
        ...recommendations.alternatives,
      ];
      const diversityScore = calculateRecommendationDiversity(allSuggestions);
      const relevanceScore = calculateRecommendationRelevance(allSuggestions, asset);
      const actualResults = allSuggestions.length;

      results.push({
        testType: "recommendations",
        query: asset.title,
        expectedResults: 2,
        actualResults,
        relevanceScore,
        passed: actualResults >= 2 && diversityScore >= 50 && relevanceScore >= 60,
        issues: [
          ...(actualResults < 2
            ? [`Insufficient recommendations for asset ${asset.id}`]
            : []),
          ...(diversityScore < 50
            ? [`Recommendation diversity is low for asset ${asset.id}`]
            : []),
          ...(relevanceScore < 60
            ? [`Recommendation relevance is low for asset ${asset.id}`]
            : []),
        ],
        metrics: {
          responseTime: Date.now() - startedAt,
          resultAccuracy: relevanceScore,
          rankingQuality: diversityScore,
        },
      });
    }

    return results;
  }

  private async runSearchTest(
    tenantId: string,
    asset: ValidationAsset,
    testType: "keyword_search" | "semantic_search",
  ): Promise<SearchValidationResult> {
    const query = deriveSearchQuery(asset, testType);
    const startedAt = Date.now();
    const searchResult = await this.searchService.search({
      tenantId,
      query,
      limit: 6,
      explain: true,
      ...(testType === "keyword_search" ? { assetTypes: [asset.assetType] } : {}),
    });

    const relevanceScore = calculateSearchRelevance(searchResult.results, asset);
    const resultAccuracy = calculateSearchAccuracy(searchResult.results, asset);
    const rankingQuality = calculateRankingQuality(searchResult.results);

    return {
      testType,
      query,
      expectedResults: 1,
      actualResults: searchResult.results.length,
      relevanceScore,
      passed:
        searchResult.results.length >= 1 &&
        relevanceScore >= 65 &&
        resultAccuracy >= 50,
      issues: [
        ...(searchResult.results.length === 0
          ? [`No results returned for query "${query}"`]
          : []),
        ...(relevanceScore < 65
          ? [`Low relevance for query "${query}"`]
          : []),
      ],
      metrics: {
        responseTime: Date.now() - startedAt,
        resultAccuracy,
        rankingQuality,
      },
    };
  }

  private async loadValidationAssets(tenantId: string): Promise<ValidationAsset[]> {
    const rows = await this.prisma.contentAsset.findMany({
      where: { tenantId, status: "PUBLISHED" },
      orderBy: [{ qualityScore: "desc" }, { updatedAt: "desc" }],
      take: 6,
      select: {
        id: true,
        title: true,
        domain: true,
        assetType: true,
        tags: true,
      },
    });

    return rows.map((row: Record<string, unknown>) => ({
      id: String(row.id),
      title: String(row.title),
      domain: String(row.domain),
      assetType: String(row.assetType).toLowerCase() as ContentAssetType,
      tags: Array.isArray(row.tags) ? (row.tags as string[]) : [],
    }));
  }

  private async getAutocompleteSuggestions(
    tenantId: string,
    query: string,
  ): Promise<Array<{ text: string; type: string }>> {
    const rows = await this.prisma.contentAsset.findMany({
      where: {
        tenantId,
        status: "PUBLISHED",
        OR: [
          { title: { contains: query } },
          { domain: { contains: query } },
        ],
      },
      select: { title: true, domain: true },
      take: 5,
    });

    const suggestions = new Map<string, { text: string; type: string }>();
    for (const row of rows) {
      const title = String(row.title);
      const domain = String(row.domain);
      if (title.toLowerCase().includes(query.toLowerCase())) {
        suggestions.set(`title:${title}`, { text: title, type: "title" });
      }
      if (domain.toLowerCase().includes(query.toLowerCase())) {
        suggestions.set(`domain:${domain}`, { text: domain, type: "domain" });
      }
    }

    return Array.from(suggestions.values()).slice(0, 5);
  }

  private generateRecommendations(
    tests: SearchValidationResult[],
    criticalIssues: string[],
  ): string[] {
    const recommendations: string[] = [];

    if (criticalIssues.length > 0) {
      recommendations.push(
        "Investigate low-quality discovery paths before enabling auto-publish for generated content",
      );
    }

    if (tests.some((test) => test.testType === "recommendations" && !test.passed)) {
      recommendations.push(
        "Recompute recommendation edges for recently published assets and review diversity weighting",
      );
    }

    if (tests.some((test) => test.testType === "autocomplete" && !test.passed)) {
      recommendations.push(
        "Improve prefix indexing for asset titles and domains used in learner discovery",
      );
    }

    if (recommendations.length === 0) {
      recommendations.push("Discovery quality is within target thresholds for the sampled assets");
    }

    return recommendations;
  }
}

function deriveSearchQuery(
  asset: ValidationAsset,
  testType: "keyword_search" | "semantic_search",
): string {
  const titleTerms = asset.title
    .split(/\s+/)
    .map((term) => term.replace(/[^\w]/g, ""))
    .filter((term) => term.length > 3);
  const semanticTerms = asset.tags.slice(0, 2);

  if (testType === "semantic_search" && semanticTerms.length > 0) {
    return `${asset.domain} ${semanticTerms.join(" ")}`.trim();
  }

  return `${asset.domain} ${titleTerms.slice(0, 2).join(" ")}`.trim();
}

function deriveAutocompletePrefix(asset: ValidationAsset): string {
  const source = asset.domain.length >= 4 ? asset.domain : asset.title;
  return source.slice(0, 4).toLowerCase();
}

function calculateSearchRelevance(
  results: HybridSearchResult[],
  asset: ValidationAsset,
): number {
  if (results.length === 0) return 0;

  const scores = results.map((result) => {
    const resultAsset = result.asset as {
      id: string;
      domain?: string;
      assetType?: string;
    };
    let score = (result.ranking?.score ?? 0) * 100;
    if (resultAsset.domain === asset.domain) score += 15;
    if (resultAsset.assetType === asset.assetType) score += 10;
    if (resultAsset.id === asset.id) score += 20;
    return Math.min(score, 100);
  });

  return scores.reduce((sum, score) => sum + score, 0) / scores.length;
}

function calculateSearchAccuracy(
  results: HybridSearchResult[],
  asset: ValidationAsset,
): number {
  if (results.length === 0) return 0;

  const accurate = results.filter(
    (result) => {
      const resultAsset = result.asset as {
        domain?: string;
        assetType?: string;
      };
      return (
        resultAsset.domain === asset.domain ||
        resultAsset.assetType === asset.assetType
      );
    },
  ).length;

  return (accurate / results.length) * 100;
}

function calculateRankingQuality(results: HybridSearchResult[]): number {
  if (results.length <= 1) return results.length === 1 ? 100 : 0;

  let monotonicPairs = 0;
  for (let index = 1; index < results.length; index += 1) {
    const prevScore = results[index - 1]?.ranking?.score ?? 0;
    const nextScore = results[index]?.ranking?.score ?? 0;
    if (prevScore >= nextScore) {
      monotonicPairs += 1;
    }
  }

  return (monotonicPairs / (results.length - 1)) * 100;
}

function calculateRecommendationDiversity(
  suggestions: Array<{ asset: { assetType?: string; domain?: string } }>,
): number {
  if (suggestions.length === 0) return 0;

  const types = new Set(suggestions.map((suggestion) => suggestion.asset.assetType));
  const domains = new Set(suggestions.map((suggestion) => suggestion.asset.domain));
  return Math.min(100, types.size * 25 + domains.size * 20);
}

function calculateRecommendationRelevance(
  suggestions: Array<{ asset: { domain?: string; title?: string } }>,
  asset: ValidationAsset,
): number {
  if (suggestions.length === 0) return 0;

  const matches = suggestions.map((suggestion) => {
    let score = suggestion.asset.domain === asset.domain ? 70 : 35;
    if ((suggestion.asset.title ?? "").toLowerCase().includes(asset.domain.toLowerCase())) {
      score += 10;
    }
    return Math.min(score, 100);
  });

  return matches.reduce((sum, score) => sum + score, 0) / matches.length;
}

function calculateAutocompleteRelevance(
  suggestions: Array<{ text: string }>,
  expectedCompletions: string[],
): number {
  if (suggestions.length === 0) return 0;

  let matched = 0;
  for (const suggestion of suggestions) {
    if (
      expectedCompletions.some((expected) =>
        suggestion.text.toLowerCase().includes(expected.toLowerCase()) ||
        expected.toLowerCase().includes(suggestion.text.toLowerCase()),
      )
    ) {
      matched += 1;
    }
  }

  return (matched / suggestions.length) * 100;
}

function calculateAutocompleteRanking(
  suggestions: Array<{ text: string; type: string }>,
): number {
  if (suggestions.length === 0) return 0;

  return suggestions.reduce((sum, suggestion, index) => {
    const specificityBonus = suggestion.type === "title" ? 15 : 5;
    const brevityBonus = suggestion.text.length <= 24 ? 15 : 5;
    return sum + Math.max(0, 100 - index * 12 + specificityBonus + brevityBonus);
  }, 0) / suggestions.length;
}

function calculateOverallScore(tests: SearchValidationResult[]): number {
  if (tests.length === 0) return 0;

  const relevanceAverage =
    tests.reduce((sum, test) => sum + test.relevanceScore, 0) / tests.length;
  const passRate =
    tests.filter((test) => test.passed).length / tests.length;

  return Number((relevanceAverage * 0.75 + passRate * 25).toFixed(2));
}
