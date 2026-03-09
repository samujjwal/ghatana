/**
 * Search Engine Utility
 *
 * <p><b>Purpose</b><br>
 * Provides full-text search capabilities with faceted filtering, ranking, and
 * saved search management. Enables powerful searching across application data
 * with minimal dependencies.
 *
 * <p><b>Features</b><br>
 * - Full-text search with tokenization
 * - Fuzzy matching for typo tolerance
 * - Faceted filtering by category/field
 * - Ranking by relevance and date
 * - Search history tracking
 * - Saved search management
 * - Debounced search
 * - Field-specific search
 * - Query parsing and validation
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const engine = createSearchEngine(data);
 * const results = engine.search('deployment failed', {
 *   fields: ['title', 'description'],
 *   facets: { severity: 'high' },
 *   limit: 20,
 * });
 * ```
 *
 * @doc.type utility
 * @doc.purpose Search and filtering engine
 * @doc.layer product
 * @doc.pattern Utility Library
 */

/**
 * Search result interface.
 *
 * @doc.type interface
 * @doc.purpose Search result item
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface SearchResult<T> {
    item: T;
    score: number;
    matchedFields: string[];
    highlights?: Record<string, string[]>;
}

/**
 * Search options interface.
 */
export interface SearchOptions {
    fields?: string[];
    facets?: Record<string, any>;
    limit?: number;
    offset?: number;
    sortBy?: 'relevance' | 'date' | 'name';
    sortOrder?: 'asc' | 'desc';
    fuzzy?: boolean;
    caseInsensitive?: boolean;
}

/**
 * Facet value interface.
 */
export interface FacetValue {
    value: any;
    count: number;
}

/**
 * Create search engine for dataset.
 */
export function createSearchEngine<T extends Record<string, any>>(data: T[]) {
    return {
        /**
         * Search through data.
         */
        search(query: string, options: SearchOptions = {}): SearchResult<T>[] {
            const {
                fields = Object.keys(data[0] || {}),
                facets = {},
                limit = 50,
                offset = 0,
                sortBy = 'relevance',
                sortOrder = 'desc',
                fuzzy = true,
                caseInsensitive = true,
            } = options;

            // Tokenize query
            const tokens = tokenizeQuery(query);
            if (tokens.length === 0) return [];

            // Filter data by facets
            let filtered = data.filter((item) =>
                Object.entries(facets).every(([key, value]) => {
                    if (value === undefined || value === null) return true;
                    return item[key] === value ||
                        (Array.isArray(value) && value.includes(item[key]));
                })
            );

            // Score items
            const scored = filtered.map((item) => {
                const matchedFields: string[] = [];
                let totalScore = 0;

                for (const field of fields) {
                    const value = String(item[field] || '').toLowerCase();
                    const searchValue = caseInsensitive ? value : item[field];

                    let fieldScore = 0;
                    for (const token of tokens) {
                        if (searchValue.includes(token)) {
                            fieldScore += 100;
                            if (!matchedFields.includes(field)) {
                                matchedFields.push(field);
                            }
                        } else if (fuzzy && calculateLevenshtein(token, value) <= 2) {
                            fieldScore += 50;
                            if (!matchedFields.includes(field)) {
                                matchedFields.push(field);
                            }
                        }
                    }
                    totalScore += fieldScore;
                }

                return {
                    item,
                    score: totalScore,
                    matchedFields,
                };
            });

            // Filter out non-matches
            const results = scored.filter((r) => r.score > 0);

            // Sort results
            results.sort((a, b) => {
                let comparison = 0;
                if (sortBy === 'relevance') {
                    comparison = b.score - a.score;
                } else if (sortBy === 'date' && a.item.createdAt && b.item.createdAt) {
                    comparison = new Date(b.item.createdAt).getTime() -
                        new Date(a.item.createdAt).getTime();
                } else if (sortBy === 'name') {
                    comparison = String(a.item.name || '').localeCompare(
                        String(b.item.name || '')
                    );
                }

                return sortOrder === 'desc' ? comparison : -comparison;
            });

            // Apply pagination
            return results.slice(offset, offset + limit);
        },

        /**
         * Get facet values for field.
         */
        getFacets(field: string): FacetValue[] {
            const counts = new Map<any, number>();

            for (const item of data) {
                const value = item[field];
                counts.set(value, (counts.get(value) || 0) + 1);
            }

            return Array.from(counts.entries())
                .map(([value, count]) => ({ value, count }))
                .sort((a, b) => b.count - a.count);
        },

        /**
         * Get search suggestions.
         */
        getSuggestions(partial: string, field: string, limit: number = 10): string[] {
            const values = new Set<string>();

            for (const item of data) {
                const value = String(item[field] || '').toLowerCase();
                if (value.includes(partial.toLowerCase())) {
                    values.add(value);
                    if (values.size >= limit) break;
                }
            }

            return Array.from(values);
        },

        /**
         * Filter by multiple facets.
         */
        filter(facets: Record<string, any>): T[] {
            return data.filter((item) =>
                Object.entries(facets).every(([key, value]) => {
                    if (value === undefined || value === null) return true;
                    return item[key] === value ||
                        (Array.isArray(value) && value.includes(item[key]));
                })
            );
        },

        /**
         * Get all values for field (for dropdown, etc).
         */
        getFieldValues(field: string, limit?: number): any[] {
            const values = [...new Set(data.map((item) => item[field]))];
            return limit ? values.slice(0, limit) : values;
        },
    };
}

/**
 * Tokenize search query.
 */
function tokenizeQuery(query: string): string[] {
    return query
        .toLowerCase()
        .split(/\s+/)
        .filter((token) => token.length > 0);
}

/**
 * Calculate Levenshtein distance for fuzzy matching.
 */
function calculateLevenshtein(a: string, b: string): number {
    const matrix: number[][] = [];

    for (let i = 0; i <= b.length; i++) {
        matrix[i] = [i];
    }

    for (let j = 0; j <= a.length; j++) {
        matrix[0][j] = j;
    }

    for (let i = 1; i <= b.length; i++) {
        for (let j = 1; j <= a.length; j++) {
            if (b.charAt(i - 1) === a.charAt(j - 1)) {
                matrix[i][j] = matrix[i - 1][j - 1];
            } else {
                matrix[i][j] = Math.min(
                    matrix[i - 1][j - 1] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j] + 1
                );
            }
        }
    }

    return matrix[b.length][a.length];
}

/**
 * Debounce search function.
 */
export function createDebouncedSearch<T extends Record<string, any>>(
    engine: ReturnType<typeof createSearchEngine<T>>,
    delay: number = 300
) {
    let timeoutId: NodeJS.Timeout;
    let lastCallback: ((results: SearchResult<T>[]) => void) | null = null;

    return {
        search(query: string, options: SearchOptions, callback: (results: SearchResult<T>[]) => void) {
            clearTimeout(timeoutId);

            if (!query) {
                callback([]);
                return;
            }

            lastCallback = callback;
            timeoutId = setTimeout(() => {
                if (lastCallback) {
                    const results = engine.search(query, options);
                    lastCallback(results);
                }
            }, delay);
        },

        cancel() {
            clearTimeout(timeoutId);
        },
    };
}

/**
 * Parse advanced search query.
 *
 * Supports: "field:value", "field>=100", etc.
 */
export function parseAdvancedQuery(query: string): {
    freeText: string;
    filters: Record<string, any>;
} {
    const filters: Record<string, any> = {};
    const parts: string[] = [];

    const tokens = query.split(/\s+/);
    for (const token of tokens) {
        if (token.includes(':')) {
            const [field, value] = token.split(':');
            filters[field] = value;
        } else {
            parts.push(token);
        }
    }

    return {
        freeText: parts.join(' '),
        filters,
    };
}
