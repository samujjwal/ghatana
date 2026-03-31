/**
 * @doc.type module
 * @doc.purpose Search service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { TenantId, ModuleId } from "@tutorputor/contracts";

// --- Types ---

export interface SearchResult {
  id: string;
  type: "module" | "thread" | "learning_path" | "classroom";
  title: string;
  description: string;
  thumbnail?: string;
  metadata: Record<string, unknown>;
  score: number;
  highlights: SearchHighlight[];
}

export interface SearchHighlight {
  field: string;
  snippet: string;
  matchPositions: Array<{ start: number; end: number }>;
}

export interface SearchFilters {
  type?: Array<"module" | "thread" | "learning_path" | "classroom">;
  category?: string[];
  difficulty?: Array<"beginner" | "intermediate" | "advanced">;
  duration?: { min?: number; max?: number };
  rating?: number;
  price?: { min?: number; max?: number; free?: boolean };
  tags?: string[];
}

export interface SearchOptions {
  tenantId: TenantId;
  query: string;
  filters?: SearchFilters;
  limit?: number;
  offset?: number;
  sortBy?: "relevance" | "newest" | "rating" | "popularity";
}

export interface AutocompleteSuggestion {
  text: string;
  type: "module" | "category" | "tag" | "author";
  id?: string;
}

export interface SearchFacets {
  types: Array<{ value: string; count: number }>;
  categories: Array<{ value: string; count: number }>;
  difficulties: Array<{ value: string; count: number }>;
  tags: Array<{ value: string; count: number }>;
  priceRanges: Array<{
    label: string;
    min: number;
    max: number;
    count: number;
  }>;
}

export interface SearchResponse {
  results: SearchResult[];
  total: number;
  facets: SearchFacets;
  took: number; // ms
}

// --- Service ---

export class SearchServiceImpl {
  constructor(private readonly prisma: PrismaClient) {}

  private toText(value: unknown): string {
    return typeof value === "string" ? value : "";
  }

  private normalizeTerms(value: string): string[] {
    return value
      .toLowerCase()
      .split(/\s+/)
      .map((term) => term.trim())
      .filter(Boolean);
  }

  private semanticScore(
    fields: Array<string | null | undefined>,
    query: string,
  ): number {
    const queryTerms = this.normalizeTerms(query);
    if (queryTerms.length === 0) return 0;

    const corpus = fields
      .filter(
        (field): field is string =>
          typeof field === "string" && field.trim().length > 0,
      )
      .join(" ")
      .toLowerCase();

    if (!corpus) return 0;

    const corpusTerms = new Set(this.normalizeTerms(corpus));
    const matchedTerms = queryTerms.filter((term) =>
      corpusTerms.has(term),
    ).length;
    const phraseBonus = corpus.includes(query.toLowerCase()) ? 0.35 : 0;
    const coverageScore = matchedTerms / queryTerms.length;

    return coverageScore + phraseBonus;
  }

  private matchScore(text: string, query: string): number {
    const lowerText = text.toLowerCase();
    const lowerQuery = query.toLowerCase();
    const terms = lowerQuery.split(/\s+/);

    let score = 0;
    for (const term of terms) {
      if (lowerText.includes(term)) {
        score += 1;
        // Bonus for exact word match
        if (new RegExp(`\\b${term}\\b`).test(lowerText)) {
          score += 0.5;
        }
        // Bonus for title match
        if (lowerText.startsWith(term)) {
          score += 1;
        }
      }
    }

    return score / terms.length;
  }

  private createHighlight(text: string, query: string): SearchHighlight {
    const terms = query.toLowerCase().split(/\s+/);
    const positions: Array<{ start: number; end: number }> = [];

    for (const term of terms) {
      const regex = new RegExp(term, "gi");
      let match;
      while ((match = regex.exec(text)) !== null) {
        positions.push({ start: match.index, end: match.index + term.length });
      }
    }

    // Create snippet around first match
    const firstPos = positions[0];
    const snippetStart = Math.max(0, (firstPos?.start ?? 0) - 50);
    const snippetEnd = Math.min(text.length, (firstPos?.end ?? 0) + 50);
    const snippet =
      (snippetStart > 0 ? "..." : "") +
      text.slice(snippetStart, snippetEnd) +
      (snippetEnd < text.length ? "..." : "");

    return {
      field: "description",
      snippet,
      matchPositions: positions,
    };
  }

  async search(options: SearchOptions): Promise<SearchResponse> {
    const startTime = Date.now();
    const {
      tenantId,
      query,
      filters,
      limit = 20,
      offset = 0,
      sortBy = "relevance",
    } = options;

    // Search modules
    const modules = await this.prisma.module.findMany({
      where: {
        tenantId,
        status: "PUBLISHED",
        OR: [
          { title: { contains: query } },
          { description: { contains: query } },
        ],
      },
    });

    // Search threads if type filter allows
    type ThreadRecord = { id: string; title: string; content: string | null };
    let threads: ThreadRecord[] = [];
    if (!filters?.type || filters.type.includes("thread")) {
      try {
        const threadDelegate = (this.prisma as unknown as {
          thread?: {
            findMany: (args: {
              where: {
                tenantId: TenantId;
                OR: Array<{ title: { contains: string } } | { content: { contains: string } }>;
              };
            }) => Promise<ThreadRecord[]>;
          };
        }).thread;

        if (threadDelegate) {
          threads = await threadDelegate.findMany({
            where: {
              tenantId,
              OR: [{ title: { contains: query } }, { content: { contains: query } }],
            },
          });
        }
      } catch (e) {
        // Ignore
      }
    }

    // Search learning paths
    type PathRecord = {
      id: string;
      title: string;
      goal: string | null;
    };
    let paths: PathRecord[] = [];
    if (!filters?.type || filters.type.includes("learning_path")) {
      try {
        paths = await this.prisma.learningPath.findMany({
          where: {
            tenantId,
            OR: [{ title: { contains: query } }, { goal: { contains: query } }],
          },
        });
      } catch (e) {
        // Ignore
      }
    }

    // Combine and score results
    let results: SearchResult[] = [];

    if (!filters?.type || filters.type.includes("module")) {
      results.push(
        ...modules.map((m) => ({
          id: m.id,
          type: "module" as const,
          title: m.title,
          description: m.description ?? "",
          metadata: {
            price: 0,
            slug: m.slug,
            category: m.domain,
            difficulty: m.difficulty,
          },
          score:
            this.matchScore(`${m.title} ${m.description}`, query) * 0.65 +
            this.semanticScore(
              [
                m.title,
                m.description,
                m.slug,
                m.domain,
              ],
              query,
            ) *
              0.35,
          highlights: [this.createHighlight(m.description ?? m.title, query)],
        })),
      );
    }

    if (!filters?.type || filters.type.includes("thread")) {
      results.push(
        ...threads.map((t) => ({
          id: t.id,
          type: "thread" as const,
          title: this.toText(t.title),
          description: this.toText(t.content),
          metadata: {},
          score: this.matchScore(`${this.toText(t.title)} ${this.toText(t.content)}`, query),
          highlights: [
            this.createHighlight(
              this.toText(t.content) || this.toText(t.title),
              query,
            ),
          ],
        })),
      );
    }

    if (!filters?.type || filters.type.includes("learning_path")) {
      results.push(
        ...paths.map((p) => ({
          id: p.id,
          type: "learning_path" as const,
          title: this.toText(p.title),
          description: this.toText(p.goal),
          metadata: {},
          score: this.matchScore(
            `${this.toText(p.title)} ${this.toText(p.goal)}`,
            query,
          ),
          highlights: [
            this.createHighlight(
              this.toText(p.goal) || this.toText(p.title),
              query,
            ),
          ],
        })),
      );
    }

    // Apply filters
    if (filters?.price?.free) {
      results = results.filter((r) => r.type !== "module" || 0 === 0);
    }

    // Sort
    if (sortBy === "relevance") {
      results.sort((a, b) => b.score - a.score);
    }

    // Calculate facets
    const facets: SearchFacets = {
      types: [
        { value: "module", count: modules.length },
        { value: "thread", count: threads.length },
        { value: "learning_path", count: paths.length },
        { value: "classroom", count: 0 },
      ],
      categories: [],
      difficulties: [],
      tags: [],
      priceRanges: [
        {
          label: "Free",
          min: 0,
          max: 0,
          count: modules.length,
        },
      ],
    };

    const total = results.length;
    const paginatedResults = results.slice(offset, offset + limit);

    return {
      results: paginatedResults,
      total,
      facets,
      took: Date.now() - startTime,
    };
  }

  async autocomplete(
    tenantId: TenantId,
    query: string,
    limit = 5,
  ): Promise<AutocompleteSuggestion[]> {
    if (query.length < 2) return [];

    const modules = await this.prisma.module.findMany({
      where: {
        tenantId,
        status: "PUBLISHED",
        title: { contains: query },
      },
      take: limit,
      select: { id: true, slug: true, title: true },
    });

    return modules.map((m) => ({
      text: m.title,
      type: "module" as const,
      id: m.slug ?? m.id,
    }));
  }

  async getPopularSearches(tenantId: TenantId, limit = 10): Promise<string[]> {
    const modules = await this.prisma.module.findMany({
      where: { tenantId, status: "PUBLISHED" },
      take: limit,
      orderBy: { createdAt: "desc" },
      select: { title: true },
    });

    return modules.map((m) => m.title);
  }

  async getSimilar(
    tenantId: TenantId,
    moduleId: ModuleId,
    limit = 5,
  ): Promise<SearchResult[]> {
    const module = await this.prisma.module.findFirst({
      where: { tenantId, id: moduleId },
    });

    if (!module) return [];

    const titleWords = module.title.toLowerCase().split(/\s+/);

    const similar = await this.prisma.module.findMany({
      where: {
        tenantId,
        status: "PUBLISHED",
        id: { not: moduleId },
        OR: titleWords.map((word: string) => ({ title: { contains: word } })),
      },
      take: limit,
    });

    return similar.map((m) => ({
      id: m.id,
      type: "module" as const,
      title: m.title,
      description: m.description ?? "",
      metadata: { price: 0, slug: m.slug },
      score:
        this.matchScore(m.title, module.title) * 0.7 +
        this.semanticScore([m.title, m.description, m.slug], module.title) *
          0.3,
      highlights: [],
    }));
  }

  async checkHealth(): Promise<boolean> {
    await this.prisma.$queryRaw`SELECT 1`;
    return true;
  }

  async indexModule(tenantId: TenantId, moduleId: ModuleId): Promise<void> {}
  async removeFromIndex(
    tenantId: TenantId,
    moduleId: ModuleId,
  ): Promise<void> {}
}
