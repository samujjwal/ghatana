/**
 * useDataTableAI Hook
 *
 * React hook for AI-powered data table operations including
 * natural language query parsing, smart filtering, and suggestions.
 *
 * @module DevSecOps/DataTable
 * @doc.type hook
 * @doc.purpose AI data table operations
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo, useRef } from 'react';
import type { FilterConfig, FilterValue, SortConfig } from './types';

/**
 * Query intent from natural language parsing
 */
export interface QueryIntent {
    intent: 'search' | 'filter' | 'sort' | 'navigate' | 'aggregate' | 'unknown';
    filters: Record<string, FilterValue>;
    searchTerm?: string;
    sortBy?: SortConfig;
    aggregation?: {
        type: 'count' | 'sum' | 'avg' | 'group';
        field?: string;
    };
    confidence: number;
    interpretation: string;
    suggestions?: string[];
}

/**
 * Field mapping configuration
 */
export interface FieldMapping {
    /** Natural language phrase */
    phrase: string;
    /** Target field name */
    field: string;
    /** Target value */
    value: FilterValue;
    /** Aliases for the phrase */
    aliases?: string[];
}

/**
 * Hook options
 */
export interface UseDataTableAIOptions {
    /**
     * Custom field mappings
     */
    fieldMappings?: FieldMapping[];

    /**
     * Enable learning from user corrections
     * @default false
     */
    enableLearning?: boolean;

    /**
     * Callback when AI parses a query
     */
    onQueryParsed?: (query: string, intent: QueryIntent) => void;

    /**
     * Initial suggestions
     */
    initialSuggestions?: string[];
}

/**
 * Built-in field mappings for common patterns
 */
const BUILTIN_MAPPINGS: FieldMapping[] = [
    // Status
    { phrase: 'open', field: 'status', value: 'open', aliases: ['active', 'ongoing'] },
    { phrase: 'closed', field: 'status', value: 'closed', aliases: ['finished', 'resolved'] },
    { phrase: 'pending', field: 'status', value: 'pending', aliases: ['waiting', 'on hold'] },
    { phrase: 'in progress', field: 'status', value: 'in_progress', aliases: ['working on', 'doing'] },
    { phrase: 'blocked', field: 'status', value: 'blocked', aliases: ['stuck', 'impeded'] },

    // Priority
    { phrase: 'high priority', field: 'priority', value: 'high', aliases: ['important', 'urgent'] },
    { phrase: 'critical', field: 'priority', value: 'critical', aliases: ['p0', 'highest'] },
    { phrase: 'medium priority', field: 'priority', value: 'medium', aliases: ['normal', 'p1'] },
    { phrase: 'low priority', field: 'priority', value: 'low', aliases: ['minor', 'p2'] },

    // Time
    { phrase: 'today', field: 'date', value: 'today', aliases: ['now'] },
    { phrase: 'yesterday', field: 'date', value: 'yesterday' },
    { phrase: 'this week', field: 'date', value: 'this_week', aliases: ['current week'] },
    { phrase: 'last week', field: 'date', value: 'last_week', aliases: ['previous week'] },
    { phrase: 'this month', field: 'date', value: 'this_month', aliases: ['current month'] },
    { phrase: 'last month', field: 'date', value: 'last_month', aliases: ['previous month'] },
    { phrase: 'overdue', field: 'dueDate', value: 'overdue', aliases: ['past due', 'late'] },

    // Assignment
    { phrase: 'my items', field: 'assignee', value: 'me', aliases: ['assigned to me', 'mine'] },
    { phrase: 'unassigned', field: 'assignee', value: 'unassigned', aliases: ['no assignee', 'nobody'] },

    // Types
    { phrase: 'bugs', field: 'type', value: 'bug', aliases: ['defects', 'issues'] },
    { phrase: 'features', field: 'type', value: 'feature', aliases: ['enhancements', 'stories'] },
    { phrase: 'tasks', field: 'type', value: 'task', aliases: ['work items', 'todos'] },
];

/**
 * Stop words to filter out of search queries
 */
const STOP_WORDS = new Set([
    'show', 'me', 'find', 'get', 'all', 'the', 'with', 'from', 'that', 'are', 'is',
    'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'by',
    'please', 'can', 'you', 'list', 'display', 'give', 'want', 'need',
]);

/**
 * Parse a natural language query into structured intent
 */
function parseQuery(
    query: string,
    mappings: FieldMapping[]
): QueryIntent {
    const normalizedQuery = query.toLowerCase().trim();
    const filters: Record<string, FilterValue> = {};
    const matchedPhrases: string[] = [];
    let confidence = 0.3;

    // Check for field mappings (including aliases)
    for (const mapping of mappings) {
        const phrasesToCheck = [mapping.phrase, ...(mapping.aliases || [])];

        for (const phrase of phrasesToCheck) {
            if (normalizedQuery.includes(phrase)) {
                filters[mapping.field] = mapping.value;
                matchedPhrases.push(phrase);
                confidence += 0.15;
                break; // Only match one alias per mapping
            }
        }
    }

    // Extract remaining search term
    let remainingQuery = normalizedQuery;
    for (const phrase of matchedPhrases) {
        remainingQuery = remainingQuery.replace(new RegExp(phrase, 'gi'), '').trim();
    }

    // Filter stop words
    const words = remainingQuery
        .split(/\s+/)
        .filter(w => !STOP_WORDS.has(w) && w.length > 1);

    const searchTerm = words.length > 0 ? words.join(' ') : undefined;

    // Detect sort intent
    let sortBy: SortConfig | undefined;
    const sortPatterns = [
        { pattern: /sort(?:ed)?\s+by\s+(\w+)/i, extract: (m: RegExpMatchArray) => m[1] },
        { pattern: /order(?:ed)?\s+by\s+(\w+)/i, extract: (m: RegExpMatchArray) => m[1] },
        { pattern: /(\w+)\s+first/i, extract: (m: RegExpMatchArray) => m[1] },
    ];

    for (const { pattern, extract } of sortPatterns) {
        const match = normalizedQuery.match(pattern);
        if (match) {
            const field = extract(match);
            const isDescending = normalizedQuery.includes('desc') ||
                normalizedQuery.includes('newest') ||
                normalizedQuery.includes('latest') ||
                normalizedQuery.includes('highest');

            sortBy = {
                column: field,
                direction: isDescending ? 'desc' : 'asc',
            };
            confidence += 0.1;
            break;
        }
    }

    // Detect aggregation intent
    let aggregation: QueryIntent['aggregation'] | undefined;
    if (normalizedQuery.includes('count') || normalizedQuery.includes('how many')) {
        aggregation = { type: 'count' };
        confidence += 0.1;
    } else if (normalizedQuery.includes('group by') || normalizedQuery.includes('grouped')) {
        aggregation = { type: 'group' };
        confidence += 0.1;
    }

    // Build interpretation
    const filterDescriptions: string[] = [];
    for (const [field, value] of Object.entries(filters)) {
        filterDescriptions.push(`${field} = "${value}"`);
    }
    if (searchTerm) {
        filterDescriptions.push(`search "${searchTerm}"`);
    }
    if (sortBy) {
        filterDescriptions.push(`sorted by ${sortBy.column} (${sortBy.direction})`);
    }
    if (aggregation) {
        filterDescriptions.push(`${aggregation.type} items`);
    }

    const interpretation = filterDescriptions.length > 0
        ? `Showing items where ${filterDescriptions.join(' AND ')}`
        : 'Showing all items';

    // Determine intent
    let intent: QueryIntent['intent'] = 'unknown';
    if (Object.keys(filters).length > 0) {
        intent = 'filter';
    } else if (searchTerm) {
        intent = 'search';
    } else if (sortBy) {
        intent = 'sort';
    } else if (aggregation) {
        intent = 'aggregate';
    }

    // Generate suggestions based on partial matches
    const suggestions: string[] = [];
    if (intent === 'unknown' && normalizedQuery.length > 2) {
        // Suggest similar phrases
        for (const mapping of mappings.slice(0, 5)) {
            if (mapping.phrase.includes(normalizedQuery) || normalizedQuery.includes(mapping.phrase.substring(0, 3))) {
                suggestions.push(`Show ${mapping.phrase} items`);
            }
        }
    }

    return {
        intent,
        filters,
        searchTerm,
        sortBy,
        aggregation,
        confidence: Math.min(confidence, 0.95),
        interpretation,
        suggestions: suggestions.length > 0 ? suggestions : undefined,
    };
}

/**
 * Hook for AI-powered data table operations
 *
 * @example
 * ```tsx
 * const { parse, filterConfig, sortConfig, suggestions } = useDataTableAI({
 *   fieldMappings: [
 *     { phrase: 'my team', field: 'team', value: 'engineering' },
 *   ],
 * });
 *
 * // Handle search input
 * const handleSearch = (query: string) => {
 *   const intent = parse(query);
 *   console.log(intent.interpretation);
 * };
 * ```
 */
export function useDataTableAI(options: UseDataTableAIOptions = {}) {
    const {
        fieldMappings = [],
        enableLearning = false,
        onQueryParsed,
        initialSuggestions = [
            'Show high priority items',
            'Find items from this week',
            'Show my assigned items',
            'Show bugs in progress',
            'Find overdue tasks',
        ],
    } = options;

    // Merge built-in and custom mappings
    const allMappings = useMemo(() => [
        ...BUILTIN_MAPPINGS,
        ...fieldMappings,
    ], [fieldMappings]);

    // State
    const [filterConfig, setFilterConfig] = useState<FilterConfig | undefined>();
    const [sortConfig, setSortConfig] = useState<SortConfig | undefined>();
    const [lastIntent, setLastIntent] = useState<QueryIntent | null>(null);
    const [queryHistory, setQueryHistory] = useState<string[]>([]);

    // Learning storage (for future ML enhancements)
    const learnedMappingsRef = useRef<FieldMapping[]>([]);

    // Parse a natural language query
    const parse = useCallback((query: string): QueryIntent => {
        const effectiveMappings = enableLearning
            ? [...allMappings, ...learnedMappingsRef.current]
            : allMappings;

        const intent = parseQuery(query, effectiveMappings);

        setLastIntent(intent);

        // Update filter config if filters were extracted
        // FilterConfig is Record<string, FilterValue>, use _searchTerm as special key
        if (Object.keys(intent.filters).length > 0 || intent.searchTerm) {
            setFilterConfig({
                ...intent.filters,
                ...(intent.searchTerm ? { _searchTerm: intent.searchTerm } : {}),
            });
        } else {
            setFilterConfig(undefined);
        }

        // Update sort config if sorting was detected
        if (intent.sortBy) {
            setSortConfig(intent.sortBy);
        }

        // Track query history
        setQueryHistory(prev => [query, ...prev.slice(0, 9)]);

        // Notify callback
        onQueryParsed?.(query, intent);

        return intent;
    }, [allMappings, enableLearning, onQueryParsed]);

    // Clear all filters
    const clearFilters = useCallback(() => {
        setFilterConfig(undefined);
        setSortConfig(undefined);
        setLastIntent(null);
    }, []);

    // Record a correction (for learning)
    const recordCorrection = useCallback((
        originalQuery: string,
        correctedFilters: Record<string, FilterValue>
    ) => {
        if (!enableLearning) return;

        // Extract new mapping from correction
        // This is a simplified version - real implementation would use ML
        const queryWords = originalQuery.toLowerCase().split(/\s+/);

        for (const [field, value] of Object.entries(correctedFilters)) {
            // Look for words that might be new phrases
            for (const word of queryWords) {
                if (word.length > 3 && !STOP_WORDS.has(word)) {
                    const exists = allMappings.some(m =>
                        m.phrase === word || m.aliases?.includes(word)
                    );

                    if (!exists) {
                        learnedMappingsRef.current.push({
                            phrase: word,
                            field,
                            value,
                        });
                    }
                }
            }
        }
    }, [enableLearning, allMappings]);

    // Generate suggestions based on context
    const suggestions = useMemo(() => {
        if (lastIntent?.suggestions && lastIntent.suggestions.length > 0) {
            return lastIntent.suggestions;
        }
        return initialSuggestions;
    }, [lastIntent, initialSuggestions]);

    return {
        // Core parsing
        parse,

        // Current state
        filterConfig,
        sortConfig,
        lastIntent,

        // Actions
        clearFilters,
        setFilterConfig,
        setSortConfig,

        // Learning
        recordCorrection,

        // Helpers
        suggestions,
        queryHistory,

        // Mappings (for customization UI)
        availableMappings: allMappings,
    };
}

export default useDataTableAI;
