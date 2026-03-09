/**
 * @doc.type module
 * @doc.purpose Search service implementation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { TenantId, ModuleId } from '@ghatana/tutorputor-contracts';

// --- Types ---

export interface SearchResult {
    id: string;
    type: 'module' | 'thread' | 'learning_path' | 'classroom';
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
    type?: Array<'module' | 'thread' | 'learning_path' | 'classroom'>;
    category?: string[];
    difficulty?: Array<'beginner' | 'intermediate' | 'advanced'>;
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
    sortBy?: 'relevance' | 'newest' | 'rating' | 'popularity';
}

export interface AutocompleteSuggestion {
    text: string;
    type: 'module' | 'category' | 'tag' | 'author';
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
    constructor(private readonly prisma: PrismaClient) { }

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
            const regex = new RegExp(term, 'gi');
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
            (snippetStart > 0 ? '...' : '') +
            text.slice(snippetStart, snippetEnd) +
            (snippetEnd < text.length ? '...' : '');

        return {
            field: 'description',
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
            sortBy = 'relevance',
        } = options;

        // Search modules
        const modules = await this.prisma.module.findMany({
            where: {
                tenantId,
                status: 'PUBLISHED',
                OR: [
                    { title: { contains: query } },
                    { description: { contains: query } },
                ],
            },
        });

        // Search threads if type filter allows
        let threads: any[] = [];
        if (!filters?.type || filters.type.includes('thread')) {
            try {
                threads = await (this.prisma as any).thread.findMany({
                    where: {
                        tenantId,
                        OR: [
                            { title: { contains: query } },
                            { content: { contains: query } },
                        ],
                    },
                });
            } catch (e) {
                // Ignore
            }
        }

        // Search learning paths
        let paths: any[] = [];
        if (!filters?.type || filters.type.includes('learning_path')) {
            try {
                paths = await this.prisma.learningPath.findMany({
                    where: {
                        tenantId,
                        OR: [
                            { title: { contains: query } },
                            { description: { contains: query } },
                        ],
                    },
                });
            } catch (e) {
                // Ignore
            }
        }

        // Combine and score results
        let results: SearchResult[] = [];

        if (!filters?.type || filters.type.includes('module')) {
            results.push(
                ...modules.map((m: any) => ({
                    id: m.id,
                    type: 'module' as const,
                    title: m.title,
                    description: m.description ?? '',
                    thumbnail: undefined,
                    metadata: { price: 0 },
                    score: this.matchScore(`${m.title} ${m.description}`, query),
                    highlights: [
                        this.createHighlight(m.description ?? m.title, query),
                    ],
                }))
            );
        }

        if (!filters?.type || filters.type.includes('thread')) {
            results.push(...threads.map((t: any) => ({
                id: t.id,
                type: 'thread' as const,
                title: t.title,
                description: t.content ?? "",
                thumbnail: undefined,
                metadata: {},
                score: this.matchScore(`${t.title} ${t.content}`, query),
                highlights: [this.createHighlight(t.content ?? t.title, query)]
            })));
        }

        if (!filters?.type || filters.type.includes('learning_path')) {
            results.push(...paths.map((p: any) => ({
                id: p.id,
                type: 'learning_path' as const,
                title: p.title,
                description: p.description ?? "",
                thumbnail: undefined,
                metadata: {},
                score: this.matchScore(`${p.title} ${p.description}`, query),
                highlights: [this.createHighlight(p.description ?? p.title, query)]
            })));
        }


        // Apply filters
        if (filters?.price?.free) {
            results = results.filter((r) => r.type !== 'module' || (0) === 0);
        }

        // Sort
        if (sortBy === 'relevance') {
            results.sort((a, b) => b.score - a.score);
        }

        // Calculate facets
        const facets: SearchFacets = {
            types: [
                { value: 'module', count: modules.length },
                { value: 'thread', count: threads.length },
                { value: 'learning_path', count: paths.length },
                { value: 'classroom', count: 0 },
            ],
            categories: [],
            difficulties: [],
            tags: [],
            priceRanges: [
                {
                    label: 'Free',
                    min: 0,
                    max: 0,
                    count: modules.length,
                }
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
        limit = 5
    ): Promise<AutocompleteSuggestion[]> {
        if (query.length < 2) return [];

        const modules = await this.prisma.module.findMany({
            where: {
                tenantId,
                status: 'PUBLISHED',
                title: { contains: query },
            },
            take: limit,
            select: { id: true, title: true },
        });

        return modules.map((m: any) => ({
            text: m.title,
            type: 'module' as const,
            id: m.id,
        }));
    }

    async getPopularSearches(tenantId: TenantId, limit = 10): Promise<string[]> {
        const modules = await this.prisma.module.findMany({
            where: { tenantId, status: 'PUBLISHED' },
            take: limit,
            orderBy: { createdAt: 'desc' },
            select: { title: true },
        });

        return modules.map((m: any) => m.title);
    }

    async getSimilar(
        tenantId: TenantId,
        moduleId: ModuleId,
        limit = 5
    ): Promise<SearchResult[]> {
        const module = await this.prisma.module.findFirst({
            where: { tenantId, id: moduleId },
        });

        if (!module) return [];

        const titleWords = module.title.toLowerCase().split(/\s+/);

        const similar = await this.prisma.module.findMany({
            where: {
                tenantId,
                status: 'PUBLISHED',
                id: { not: moduleId },
                OR: titleWords.map((word: string) => ({ title: { contains: word } })),
            },
            take: limit,
        });

        return similar.map((m: any) => ({
            id: m.id,
            type: 'module' as const,
            title: m.title,
            description: m.description ?? '',
            thumbnail: undefined,
            metadata: { price: 0 },
            score: this.matchScore(m.title, module.title),
            highlights: [],
        }));
    }

    async checkHealth(): Promise<boolean> {
        await this.prisma.$queryRaw`SELECT 1`;
        return true;
    }

    async indexModule(tenantId: TenantId, moduleId: ModuleId): Promise<void> { }
    async removeFromIndex(tenantId: TenantId, moduleId: ModuleId): Promise<void> { }
}
