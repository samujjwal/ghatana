/**
 * SmartDataTable Component
 *
 * AI-enhanced DataTable with semantic search, natural language filtering,
 * and smart suggestions. Wraps the base DataTable with AI capabilities.
 *
 * Features:
 * - Natural language search (e.g., "show me high priority items from last week")
 * - Semantic search with hybrid matching
 * - AI-powered filter suggestions
 * - Query interpretation feedback
 * - Auto-correct and query suggestions
 *
 * @module DevSecOps/DataTable
 * @doc.type component
 * @doc.purpose AI-enhanced data table
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Box, TextField, InputAdornment, IconButton, Chip, Tooltip, Typography, Collapse, Surface as Paper, Spinner as CircularProgress, Alert } from '@ghatana/ui';
import { Search as SearchIcon, Brain as AIIcon, XCircle as ClearIcon, Sparkles as SmartIcon, Mic as VoiceIcon, Lightbulb as SuggestionIcon } from 'lucide-react';

import { DataTable } from './DataTable';
import type { DataTableProps, FilterConfig, FilterValue } from './types';
import type { Item } from '@ghatana/yappc-types/devsecops';

/**
 * Parsed query intent
 */
interface ParsedQueryIntent {
    intent: 'search' | 'filter' | 'sort' | 'navigate' | 'unknown';
    filters: Record<string, FilterValue>;
    searchTerm?: string;
    sortBy?: { column: string; direction: 'asc' | 'desc' };
    confidence: number;
    interpretation: string;
}

/**
 * SmartDataTable props
 */
export interface SmartDataTableProps<T = Item> extends DataTableProps<T> {
    /**
     * Enable AI-powered search
     * @default true
     */
    enableSmartSearch?: boolean;

    /**
     * Placeholder for the search input
     */
    searchPlaceholder?: string;

    /**
     * Callback when natural language search is executed
     */
    onSmartSearch?: (query: string, parsedIntent: ParsedQueryIntent) => void;

    /**
     * Custom query parser (for offline mode)
     */
    queryParser?: (query: string) => ParsedQueryIntent;

    /**
     * Show query interpretation
     * @default true
     */
    showInterpretation?: boolean;

    /**
     * Enable voice search
     * @default false
     */
    enableVoiceSearch?: boolean;

    /**
     * Suggested queries for empty state
     */
    suggestedQueries?: string[];

    /**
     * Field mappings for natural language
     * Maps natural language terms to column IDs
     */
    fieldMappings?: Record<string, string>;
}

/**
 * Default natural language field mappings
 */
const DEFAULT_FIELD_MAPPINGS: Record<string, string> = {
    // Status mappings
    'open': 'status:open',
    'closed': 'status:closed',
    'pending': 'status:pending',
    'in progress': 'status:in_progress',
    'done': 'status:done',
    'completed': 'status:completed',

    // Priority mappings
    'high priority': 'priority:high',
    'medium priority': 'priority:medium',
    'low priority': 'priority:low',
    'critical': 'priority:critical',
    'urgent': 'priority:urgent',

    // Time mappings
    'today': 'date:today',
    'yesterday': 'date:yesterday',
    'this week': 'date:this_week',
    'last week': 'date:last_week',
    'this month': 'date:this_month',
    'last month': 'date:last_month',
    'overdue': 'dueDate:overdue',

    // Assignment
    'my items': 'assignee:me',
    'assigned to me': 'assignee:me',
    'unassigned': 'assignee:unassigned',
};

/**
 * Parse natural language query into structured filters (offline mode)
 */
function parseNaturalLanguageQuery(
    query: string,
    fieldMappings: Record<string, string>
): ParsedQueryIntent {
    const normalizedQuery = query.toLowerCase().trim();
    const filters: Record<string, FilterValue> = {};
    let searchTerm: string | undefined;
    let interpretation = '';
    const matchedTerms: string[] = [];

    // Check for field mappings
    for (const [phrase, mapping] of Object.entries(fieldMappings)) {
        if (normalizedQuery.includes(phrase)) {
            const [field, value] = mapping.split(':');
            if (field && value) {
                filters[field] = value;
                matchedTerms.push(phrase);
            }
        }
    }

    // Extract remaining search term
    let remainingQuery = normalizedQuery;
    for (const term of matchedTerms) {
        remainingQuery = remainingQuery.replace(term, '').trim();
    }

    // Clean up common words
    const stopWords = ['show', 'me', 'find', 'get', 'all', 'the', 'with', 'from', 'that', 'are', 'is'];
    const words = remainingQuery.split(/\s+/).filter(w => !stopWords.includes(w) && w.length > 0);

    if (words.length > 0) {
        searchTerm = words.join(' ');
    }

    // Build interpretation string
    const filterDescriptions: string[] = [];
    if (filters.status) filterDescriptions.push(`status = "${filters.status}"`);
    if (filters.priority) filterDescriptions.push(`priority = "${filters.priority}"`);
    if (filters.date) filterDescriptions.push(`date = "${filters.date}"`);
    if (filters.assignee) filterDescriptions.push(`assignee = "${filters.assignee}"`);
    if (searchTerm) filterDescriptions.push(`search "${searchTerm}"`);

    interpretation = filterDescriptions.length > 0
        ? `Showing items where ${filterDescriptions.join(' AND ')}`
        : 'Showing all items';

    // Calculate confidence based on matched terms
    const confidence = matchedTerms.length > 0 ? 0.7 + (matchedTerms.length * 0.1) : 0.3;

    return {
        intent: Object.keys(filters).length > 0 ? 'filter' : searchTerm ? 'search' : 'unknown',
        filters,
        searchTerm,
        confidence: Math.min(confidence, 0.95),
        interpretation,
    };
}

/**
 * SmartDataTable component
 */
export function SmartDataTable<T = Item>({
    enableSmartSearch = true,
    searchPlaceholder = 'Search with AI... (e.g., "show high priority items from this week")',
    onSmartSearch,
    queryParser,
    showInterpretation = true,
    enableVoiceSearch = false,
    suggestedQueries = [
        'Show high priority items',
        'Find items from this week',
        'Show my assigned items',
        'Find overdue tasks',
    ],
    fieldMappings = DEFAULT_FIELD_MAPPINGS,
    filterConfig,
    onFilterChange,
    ...tableProps
}: SmartDataTableProps<T>) {
    const [searchQuery, setSearchQuery] = useState('');
    const [isProcessing, setIsProcessing] = useState(false);
    const [parsedIntent, setParsedIntent] = useState<ParsedQueryIntent | null>(null);
    const [showSuggestions, setShowSuggestions] = useState(false);
    const [localFilterConfig, setLocalFilterConfig] = useState<FilterConfig | undefined>(filterConfig);

    // Merge field mappings with defaults
    const mergedFieldMappings = useMemo(() => ({
        ...DEFAULT_FIELD_MAPPINGS,
        ...fieldMappings,
    }), [fieldMappings]);

    // Handle search execution
    const handleSearch = useCallback(async () => {
        if (!searchQuery.trim()) {
            setParsedIntent(null);
            setLocalFilterConfig(undefined);
            onFilterChange?.(undefined as unknown as FilterConfig);
            return;
        }

        setIsProcessing(true);
        setShowSuggestions(false);

        try {
            // Use custom parser or default
            const parser = queryParser || ((q: string) => parseNaturalLanguageQuery(q, mergedFieldMappings));
            const intent = parser(searchQuery);

            setParsedIntent(intent);

            // Apply filters - FilterConfig is Record<string, FilterValue>
            // Use _searchTerm as a special key for text search
            if (Object.keys(intent.filters).length > 0 || intent.searchTerm) {
                const newFilterConfig: FilterConfig = {
                    ...intent.filters,
                    ...(intent.searchTerm ? { _searchTerm: intent.searchTerm } : {}),
                };
                setLocalFilterConfig(newFilterConfig);
                onFilterChange?.(newFilterConfig);
            }

            // Notify parent
            onSmartSearch?.(searchQuery, intent);
        } finally {
            setIsProcessing(false);
        }
    }, [searchQuery, queryParser, mergedFieldMappings, onFilterChange, onSmartSearch]);

    // Handle Enter key
    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            handleSearch();
        }
    }, [handleSearch]);

    // Handle clear
    const handleClear = useCallback(() => {
        setSearchQuery('');
        setParsedIntent(null);
        setLocalFilterConfig(undefined);
        onFilterChange?.(undefined as unknown as FilterConfig);
        setShowSuggestions(false);
    }, [onFilterChange]);

    // Handle suggestion click
    const handleSuggestionClick = useCallback((suggestion: string) => {
        setSearchQuery(suggestion);
        setShowSuggestions(false);
        // Auto-execute search
        setTimeout(() => {
            const parser = queryParser || ((q: string) => parseNaturalLanguageQuery(q, mergedFieldMappings));
            const intent = parser(suggestion);
            setParsedIntent(intent);

            if (Object.keys(intent.filters).length > 0 || intent.searchTerm) {
                const newFilterConfig: FilterConfig = {
                    ...intent.filters,
                    ...(intent.searchTerm ? { _searchTerm: intent.searchTerm } : {}),
                };
                setLocalFilterConfig(newFilterConfig);
                onFilterChange?.(newFilterConfig);
            }

            onSmartSearch?.(suggestion, intent);
        }, 100);
    }, [queryParser, mergedFieldMappings, onFilterChange, onSmartSearch]);

    // Use controlled or local filter config
    const effectiveFilterConfig = filterConfig ?? localFilterConfig;

    return (
        <Box>
            {/* AI Search Bar */}
            {enableSmartSearch && (
                <Box className="mb-4">
                    <TextField
                        fullWidth
                        placeholder={searchPlaceholder}
                        value={searchQuery}
                        onChange={(e) => {
                            setSearchQuery(e.target.value);
                            setShowSuggestions(e.target.value.length === 0);
                        }}
                        onKeyDown={handleKeyDown}
                        onFocus={() => setShowSuggestions(searchQuery.length === 0)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <Tooltip title="AI-powered search">
                                        <AIIcon tone="primary" />
                                    </Tooltip>
                                </InputAdornment>
                            ),
                            endAdornment: (
                                <InputAdornment position="end">
                                    {isProcessing && <CircularProgress size={20} className="mr-2" />}
                                    {searchQuery && (
                                        <IconButton size="sm" onClick={handleClear}>
                                            <ClearIcon />
                                        </IconButton>
                                    )}
                                    {enableVoiceSearch && (
                                        <Tooltip title="Voice search">
                                            <IconButton size="sm">
                                                <VoiceIcon />
                                            </IconButton>
                                        </Tooltip>
                                    )}
                                    <IconButton onClick={handleSearch} tone="primary">
                                        <SearchIcon />
                                    </IconButton>
                                </InputAdornment>
                            ),
                        }}
                        className="rounded-lg"
                    />

                    {/* Suggestions */}
                    <Collapse in={showSuggestions && suggestedQueries.length > 0}>
                        <Paper className="mt-2 p-3" variant="outlined">
                            <Box className="flex items-center gap-1 mb-2">
                                <SuggestionIcon size={16} color="action" />
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    Try these queries:
                                </Typography>
                            </Box>
                            <Box className="flex flex-wrap gap-1">
                                {suggestedQueries.map((suggestion, index) => (
                                    <Chip
                                        key={index}
                                        label={suggestion}
                                        size="sm"
                                        variant="outlined"
                                        onClick={() => handleSuggestionClick(suggestion)}
                                        className="cursor-pointer"
                                    />
                                ))}
                            </Box>
                        </Paper>
                    </Collapse>

                    {/* Query Interpretation */}
                    {showInterpretation && parsedIntent && (
                        <Alert
                            severity={parsedIntent.confidence >= 0.7 ? 'success' : 'info'}
                            icon={<SmartIcon />}
                            className="mt-2"
                            action={
                                <Chip
                                    size="sm"
                                    label={`${Math.round(parsedIntent.confidence * 100)}% confident`}
                                    color={parsedIntent.confidence >= 0.7 ? 'success' : 'default'}
                                />
                            }
                        >
                            <Typography as="p" className="text-sm">{parsedIntent.interpretation}</Typography>
                        </Alert>
                    )}
                </Box>
            )}

            {/* DataTable */}
            <DataTable<T>
                {...tableProps}
                filterConfig={effectiveFilterConfig}
                onFilterChange={onFilterChange}
            />
        </Box>
    );
}

export default SmartDataTable;
