/**
 * Hybrid Search Service
 *
 * Combines lexical, semantic similarity, quality, and recency signals
 * into a unified ranking model for content asset discovery. Returns
 * ranking explanation metadata for transparency.
 *
 * Heavy embedding / vector similarity is computed by Java services and
 * stored in EmbeddingVector. This service orchestrates the ranking
 * composition from pre-computed signals.
 *
 * @doc.type class
 * @doc.purpose Hybrid search ranking across content assets
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";

type ContentAsset = any;
type ContentAssetType = any;
type HybridSearchOptions = any;
type HybridSearchResponse = any;
type HybridSearchResult = any;
type RankingExplanation = any;
type RankingSignal =
  | "lexical"
  | "semantic"
  | "quality"
  | "recency"
  | "popularity"
  | "learner_fit";

// ---------------------------------------------------------------------------
// Default weights
// ---------------------------------------------------------------------------

const DEFAULT_WEIGHTS: Record<RankingSignal, number> = {
  lexical: 0.35,
  semantic: 0.3,
  quality: 0.15,
  recency: 0.1,
  popularity: 0.05,
  learner_fit: 0.05,
};

// ---------------------------------------------------------------------------
// Scoring helpers
// ---------------------------------------------------------------------------

function normalizeTerms(text: string): string[] {
  return text
    .toLowerCase()
    .replace(/[^\w\s]/g, " ")
    .split(/\s+/)
    .filter(Boolean);
}

/**
 * Compute a lexical score [0..1] using term coverage + phrase bonus.
 */
function lexicalScore(
  fields: Array<string | null | undefined>,
  query: string,
): number {
  const queryTerms = normalizeTerms(query);
  if (queryTerms.length === 0) return 0;

  const corpus = fields
    .filter((f): f is string => typeof f === "string" && f.length > 0)
    .join(" ")
    .toLowerCase();

  if (!corpus) return 0;

  const corpusTerms = new Set(normalizeTerms(corpus));
  const matched = queryTerms.filter((t: any) => corpusTerms.has(t)).length;
  const coverage = matched / queryTerms.length;

  // Phrase bonus: full query as substring
  const phraseBonus = corpus.includes(query.toLowerCase()) ? 0.2 : 0;

  // Title exact-start bonus
  const firstField = (fields[0] ?? "").toLowerCase();
  const titleBonus = firstField.startsWith(query.toLowerCase()) ? 0.15 : 0;

  return Math.min(1, coverage * 0.65 + phraseBonus + titleBonus);
}

/**
 * Compute a semantic similarity score [0..1] by matching against
 * pre-computed chunk embeddings. If vectors are available, a cosine
 * similarity is computed by Java and stored; here we use the
 * chunk-level text overlap as a fallback.
 */
function semanticOverlapScore(chunkTexts: string[], query: string): number {
  if (chunkTexts.length === 0) return 0;

  const queryTerms = new Set(normalizeTerms(query));
  if (queryTerms.size === 0) return 0;

  let bestScore = 0;
  for (const text of chunkTexts) {
    const chunkTerms = new Set(normalizeTerms(text));
    const intersection = [...queryTerms].filter((t: any) =>
      chunkTerms.has(t),
    ).length;
    const score = intersection / queryTerms.size;
    if (score > bestScore) bestScore = score;
  }

  return bestScore;
}

/**
 * Quality score normalized to [0..1].
 */
function qualityScore(raw: number | null | undefined): number {
  if (raw == null) return 0.5; // neutral default
  return Math.max(0, Math.min(1, raw / 100));
}

/**
 * Recency score decaying from 1 (today) towards 0 over 365 days.
 */
function recencyScore(updatedAt: Date | string | null | undefined): number {
  if (!updatedAt) return 0;
  const date = typeof updatedAt === "string" ? new Date(updatedAt) : updatedAt;
  const ageMs = Date.now() - date.getTime();
  const ageDays = ageMs / (1000 * 60 * 60 * 24);
  return Math.max(0, 1 - ageDays / 365);
}

/**
 * Create highlight snippets for matched terms.
 */
function createHighlights(
  fields: Record<string, string | null | undefined>,
  query: string,
): Array<{ field: string; snippet: string }> {
  const terms = normalizeTerms(query);
  const highlights: Array<{ field: string; snippet: string }> = [];

  for (const [field, text] of Object.entries(fields)) {
    if (!text) continue;
    const lower = text.toLowerCase();
    const matched = terms.some((t: any) => lower.includes(t));
    if (!matched) continue;

    // Extract snippet around first match
    const firstTerm = terms.find((t: any) => lower.includes(t))!;
    const idx = lower.indexOf(firstTerm);
    const start = Math.max(0, idx - 60);
    const end = Math.min(text.length, idx + firstTerm.length + 60);
    const snippet =
      (start > 0 ? "..." : "") +
      text.slice(start, end) +
      (end < text.length ? "..." : "");

    highlights.push({ field, snippet });
  }

  return highlights;
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class HybridSearchService {
  constructor(private readonly prisma: PrismaClient) {}

  async search(options: HybridSearchOptions): Promise<HybridSearchResponse> {
    const startTime = Date.now();
    const {
      tenantId,
      query,
      assetTypes,
      domain,
      limit = 20,
      offset = 0,
      explain = false,
      weights: userWeights,
    } = options;

    const weights = { ...DEFAULT_WEIGHTS, ...userWeights };

    // 1. Lexical candidate retrieval from ContentAsset
    const where: Record<string, unknown> = {
      tenantId,
      status: "PUBLISHED",
      OR: [
        { title: { contains: query } },
        { searchableText: { contains: query } },
      ],
    };

    if (assetTypes && assetTypes.length > 0) {
      where.assetType = { in: assetTypes.map((t: any) => t.toUpperCase()) };
    }
    if (domain) {
      where.domain = domain;
    }

    const [candidates, total] = await Promise.all([
      (this.prisma as any).contentAsset.findMany({
        where,
        take: Math.min(limit * 3, 100), // over-fetch for re-ranking
        orderBy: { updatedAt: "desc" },
      }),
      (this.prisma as any).contentAsset.count({ where }),
    ]);

    if (candidates.length === 0) {
      return {
        results: [],
        total: 0,
        took: Date.now() - startTime,
        rankingSignals: Object.keys(weights) as RankingSignal[],
      };
    }

    // 2. Fetch semantic chunks for candidates (for overlap scoring)
    const candidateIds = candidates.map((c: any) => c.id);
    const chunks = await (this.prisma as any).semanticChunk.findMany({
      where: {
        assetId: { in: candidateIds },
        embeddingStatus: { in: ["READY", "PENDING"] },
      },
      select: { assetId: true, text: true },
    });

    const chunksByAsset = new Map<string, string[]>();
    for (const chunk of chunks) {
      const texts = chunksByAsset.get(chunk.assetId) ?? [];
      texts.push(chunk.text);
      chunksByAsset.set(chunk.assetId, texts);
    }

    // 3. Score and rank
    const scored: Array<{ raw: any; ranking: RankingExplanation }> = [];

    for (const candidate of candidates) {
      const lex = lexicalScore(
        [candidate.title, candidate.searchableText, candidate.domain],
        query,
      );
      const sem = semanticOverlapScore(
        chunksByAsset.get(candidate.id) ?? [],
        query,
      );
      const qual = qualityScore(candidate.qualityScore);
      const rec = recencyScore(candidate.updatedAt);
      // popularity and learner_fit would come from telemetry (stub at 0.5)
      const pop = 0.5;
      const fit = 0.5;

      const signals: RankingExplanation["signals"] = [
        {
          source: "lexical" as const,
          weight: weights.lexical,
          rawScore: lex,
          contribution: lex * weights.lexical,
        },
        {
          source: "semantic" as const,
          weight: weights.semantic,
          rawScore: sem,
          contribution: sem * weights.semantic,
        },
        {
          source: "quality" as const,
          weight: weights.quality,
          rawScore: qual,
          contribution: qual * weights.quality,
        },
        {
          source: "recency" as const,
          weight: weights.recency,
          rawScore: rec,
          contribution: rec * weights.recency,
        },
        {
          source: "popularity" as const,
          weight: weights.popularity,
          rawScore: pop,
          contribution: pop * weights.popularity,
        },
        {
          source: "learner_fit" as const,
          weight: weights.learner_fit,
          rawScore: fit,
          contribution: fit * weights.learner_fit,
        },
      ];

      const score = signals.reduce((sum: any, s: any) => sum + s.contribution, 0);

      const topSignal = signals.reduce((a: any, b: any) =>
        b.contribution > a.contribution ? b : a,
      );

      scored.push({
        raw: candidate,
        ranking: {
          score,
          signals,
          matchReason: `Primarily matched via ${topSignal.source} (${(topSignal.contribution * 100).toFixed(1)}%)`,
        },
      });
    }

    // Sort by combined score desc
    scored.sort((a: any, b: any) => b.ranking.score - a.ranking.score);

    // 4. Paginate and build response
    const paged = scored.slice(offset, offset + limit);

    const results: HybridSearchResult[] = paged.map(({ raw, ranking }) => {
      const asset = this.mapAsset(raw);
      return {
        asset,
        ranking: explain
          ? ranking
          : {
              score: ranking.score,
              signals: [],
              matchReason: ranking.matchReason,
            },
        highlights: createHighlights(
          { title: raw.title, searchableText: raw.searchableText },
          query,
        ),
      };
    });

    return {
      results,
      total,
      took: Date.now() - startTime,
      rankingSignals: Object.keys(weights) as RankingSignal[],
    };
  }

  // -------------------------------------------------------------------------
  // Mapping helper
  // -------------------------------------------------------------------------

  private mapAsset(raw: any): ContentAsset {
    return {
      id: raw.id as string,
      tenantId: raw.tenantId as string,
      slug: raw.slug as string,
      title: raw.title as string,
      assetType: (raw.assetType as string).toLowerCase() as ContentAssetType,
      domain: raw.domain as string,
      conceptId: (raw.conceptId as string) ?? undefined,
      status: (raw.status as string).toLowerCase() as ContentAsset["status"],
      currentVersion: raw.currentVersion as number,
      qualityScore: (raw.qualityScore as number) ?? undefined,
      semanticIndexStatus:
        ((
          raw.semanticIndexStatus as string | null | undefined
        )?.toLowerCase() as ContentAsset["semanticIndexStatus"]) ?? undefined,
      recommendationStatus:
        ((
          raw.recommendationStatus as string | null | undefined
        )?.toLowerCase() as ContentAsset["recommendationStatus"]) ?? undefined,
      tags: (raw.tags as string[]) ?? undefined,
      targetGrades: Array.isArray(raw.targetGrades)
        ? (raw.targetGrades as string[])
        : [],
      difficultyLevel: (raw.difficultyLevel as string) ?? undefined,
      authorId: raw.authorId as string,
      lastEditedBy: (raw.lastEditedBy as string) ?? undefined,
      publishedAt: raw.publishedAt
        ? (raw.publishedAt as Date).toISOString()
        : undefined,
      createdAt: raw.createdAt
        ? (raw.createdAt as Date).toISOString()
        : new Date().toISOString(),
      updatedAt: raw.updatedAt
        ? (raw.updatedAt as Date).toISOString()
        : new Date().toISOString(),
      promptHash: (raw.promptHash as string) ?? undefined,
      riskLevel: (
        (raw.riskLevel as string | null | undefined) ?? "LOW"
      ).toUpperCase() as ContentAsset["riskLevel"],
      confidenceScore: (raw.confidenceScore as number) ?? undefined,
      legacyModuleId: (raw.legacyModuleId as string) ?? undefined,
      legacyExperienceId: (raw.legacyExperienceId as string) ?? undefined,
    };
  }
}
