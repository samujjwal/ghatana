/**
 * Saved Queries Component
 * 
 * Manages saved SQL queries with CRUD operations.
 * Supports folders, search, and quick access.
 * 
 * @doc.type component
 * @doc.purpose Saved queries management
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React, { useState, useCallback } from 'react';
import {
    FolderOpen,
    File,
    Plus,
    Search,
    MoreVertical,
    Trash2,
    Edit2,
    Copy,
    Star,
    StarOff,
    Clock,
} from 'lucide-react';
import { cn, textStyles, buttonStyles, inputStyles, cardStyles } from '../../lib/theme';

/**
 * Saved query interface
 */
export interface SavedQuery {
    id: string;
    name: string;
    description?: string;
    sql: string;
    folderId?: string;
    isFavorite: boolean;
    createdAt: string;
    updatedAt: string;
    executionCount: number;
    lastExecutedAt?: string;
}

/**
 * Query folder interface
 */
export interface QueryFolder {
    id: string;
    name: string;
    parentId?: string;
}

/**
 * Mock saved queries
 */
const mockQueries: SavedQuery[] = [
    {
        id: 'q1',
        name: 'Active Users Last 7 Days',
        description: 'Count of unique active users in the last week',
        sql: 'SELECT COUNT(DISTINCT user_id) as active_users\nFROM user_events\nWHERE event_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY);',
        isFavorite: true,
        createdAt: '2024-01-15T10:00:00Z',
        updatedAt: '2024-01-20T14:30:00Z',
        executionCount: 45,
        lastExecutedAt: '2024-01-20T14:30:00Z',
    },
    {
        id: 'q2',
        name: 'Revenue by Product',
        description: 'Total revenue grouped by product category',
        sql: 'SELECT product_category, SUM(amount) as total_revenue\nFROM transactions\nGROUP BY product_category\nORDER BY total_revenue DESC;',
        isFavorite: false,
        createdAt: '2024-01-10T09:00:00Z',
        updatedAt: '2024-01-18T11:00:00Z',
        executionCount: 23,
        lastExecutedAt: '2024-01-18T11:00:00Z',
    },
    {
        id: 'q3',
        name: 'Failed Workflows',
        description: 'List of failed workflow executions',
        sql: 'SELECT workflow_id, workflow_name, error_message, failed_at\nFROM workflow_executions\nWHERE status = \'failed\'\nORDER BY failed_at DESC\nLIMIT 100;',
        isFavorite: true,
        createdAt: '2024-01-05T08:00:00Z',
        updatedAt: '2024-01-19T16:00:00Z',
        executionCount: 67,
        lastExecutedAt: '2024-01-19T16:00:00Z',
    },
    {
        id: 'q4',
        name: 'Data Quality Score',
        sql: 'SELECT collection_name, quality_score, last_checked\nFROM data_quality_metrics\nWHERE quality_score < 0.9\nORDER BY quality_score ASC;',
        isFavorite: false,
        createdAt: '2024-01-12T12:00:00Z',
        updatedAt: '2024-01-12T12:00:00Z',
        executionCount: 8,
    },
];

interface SavedQueriesProps {
    onSelect: (query: SavedQuery) => void;
    onSave?: (query: Omit<SavedQuery, 'id' | 'createdAt' | 'updatedAt' | 'executionCount'>) => void;
    currentSql?: string;
    className?: string;
}

/**
 * Saved Queries Component
 */
export function SavedQueries({
    onSelect,
    onSave,
    currentSql,
    className
}: SavedQueriesProps) {
    const [queries, setQueries] = useState<SavedQuery[]>(mockQueries);
    const [searchQuery, setSearchQuery] = useState('');
    const [showFavoritesOnly, setShowFavoritesOnly] = useState(false);
    const [activeMenu, setActiveMenu] = useState<string | null>(null);
    const [isCreating, setIsCreating] = useState(false);
    const [newQueryName, setNewQueryName] = useState('');

    // Filter queries
    const filteredQueries = queries.filter((query) => {
        if (showFavoritesOnly && !query.isFavorite) return false;
        if (searchQuery) {
            const search = searchQuery.toLowerCase();
            return (
                query.name.toLowerCase().includes(search) ||
                query.description?.toLowerCase().includes(search) ||
                query.sql.toLowerCase().includes(search)
            );
        }
        return true;
    });

    // Toggle favorite
    const toggleFavorite = useCallback((id: string) => {
        setQueries((prev) =>
            prev.map((q) => (q.id === id ? { ...q, isFavorite: !q.isFavorite } : q))
        );
    }, []);

    // Delete query
    const deleteQuery = useCallback((id: string) => {
        setQueries((prev) => prev.filter((q) => q.id !== id));
        setActiveMenu(null);
    }, []);

    // Duplicate query
    const duplicateQuery = useCallback((query: SavedQuery) => {
        const newQuery: SavedQuery = {
            ...query,
            id: `q${Date.now()}`,
            name: `${query.name} (Copy)`,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            executionCount: 0,
            lastExecutedAt: undefined,
        };
        setQueries((prev) => [newQuery, ...prev]);
        setActiveMenu(null);
    }, []);

    // Save current query
    const handleSaveCurrentQuery = useCallback(() => {
        if (!currentSql || !newQueryName.trim()) return;

        const newQuery: SavedQuery = {
            id: `q${Date.now()}`,
            name: newQueryName.trim(),
            sql: currentSql,
            isFavorite: false,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            executionCount: 0,
        };

        setQueries((prev) => [newQuery, ...prev]);
        setNewQueryName('');
        setIsCreating(false);
        onSave?.(newQuery);
    }, [currentSql, newQueryName, onSave]);

    // Format relative time
    const formatRelativeTime = (dateStr?: string) => {
        if (!dateStr) return 'Never';
        const date = new Date(dateStr);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

        if (diffDays === 0) return 'Today';
        if (diffDays === 1) return 'Yesterday';
        if (diffDays < 7) return `${diffDays} days ago`;
        if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
        return date.toLocaleDateString();
    };

    return (
        <div className={cn('flex flex-col h-full', className)}>
            {/* Header */}
            <div className="p-3 border-b border-gray-200 dark:border-gray-700">
                <div className="flex items-center justify-between mb-3">
                    <h3 className={textStyles.h4}>Saved Queries</h3>
                    <button
                        onClick={() => setIsCreating(true)}
                        disabled={!currentSql}
                        className={cn(
                            buttonStyles.sm,
                            currentSql ? buttonStyles.primary : 'opacity-50 cursor-not-allowed bg-gray-300'
                        )}
                    >
                        <Plus className="h-4 w-4" />
                    </button>
                </div>

                {/* Save new query form */}
                {isCreating && (
                    <div className="mb-3 p-2 bg-blue-50 dark:bg-blue-900/30 rounded-lg">
                        <input
                            type="text"
                            value={newQueryName}
                            onChange={(e) => setNewQueryName(e.target.value)}
                            placeholder="Query name..."
                            className={cn(inputStyles.base, 'mb-2')}
                            autoFocus
                        />
                        <div className="flex gap-2">
                            <button
                                onClick={handleSaveCurrentQuery}
                                disabled={!newQueryName.trim()}
                                className={cn(buttonStyles.primary, buttonStyles.sm, 'flex-1')}
                            >
                                Save
                            </button>
                            <button
                                onClick={() => {
                                    setIsCreating(false);
                                    setNewQueryName('');
                                }}
                                className={cn(buttonStyles.secondary, buttonStyles.sm)}
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                )}

                {/* Search */}
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Search queries..."
                        className={cn(inputStyles.base, 'pl-9')}
                    />
                </div>

                {/* Filter */}
                <div className="flex items-center gap-2 mt-2">
                    <button
                        onClick={() => setShowFavoritesOnly(!showFavoritesOnly)}
                        className={cn(
                            'flex items-center gap-1 px-2 py-1 text-xs rounded-full transition-colors',
                            showFavoritesOnly
                                ? 'bg-yellow-100 dark:bg-yellow-900 text-yellow-700 dark:text-yellow-300'
                                : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                        )}
                    >
                        <Star className="h-3 w-3" />
                        Favorites
                    </button>
                </div>
            </div>

            {/* Query List */}
            <div className="flex-1 overflow-y-auto">
                {filteredQueries.length === 0 ? (
                    <div className="p-4 text-center">
                        <File className="h-8 w-8 mx-auto text-gray-400 mb-2" />
                        <p className={textStyles.muted}>
                            {searchQuery ? 'No queries found' : 'No saved queries yet'}
                        </p>
                    </div>
                ) : (
                    <div className="divide-y divide-gray-100 dark:divide-gray-800">
                        {filteredQueries.map((query) => (
                            <div
                                key={query.id}
                                className="p-3 hover:bg-gray-50 dark:hover:bg-gray-800 cursor-pointer group relative"
                                onClick={() => onSelect(query)}
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2">
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    toggleFavorite(query.id);
                                                }}
                                                className="flex-shrink-0"
                                            >
                                                {query.isFavorite ? (
                                                    <Star className="h-4 w-4 text-yellow-500 fill-yellow-500" />
                                                ) : (
                                                    <StarOff className="h-4 w-4 text-gray-400 opacity-0 group-hover:opacity-100" />
                                                )}
                                            </button>
                                            <span className={cn(textStyles.body, 'font-medium truncate')}>
                                                {query.name}
                                            </span>
                                        </div>
                                        {query.description && (
                                            <p className={cn(textStyles.xs, 'mt-0.5 truncate')}>
                                                {query.description}
                                            </p>
                                        )}
                                        <div className="flex items-center gap-3 mt-1">
                                            <span className={cn(textStyles.xs, 'flex items-center gap-1')}>
                                                <Clock className="h-3 w-3" />
                                                {formatRelativeTime(query.lastExecutedAt)}
                                            </span>
                                            <span className={textStyles.xs}>
                                                {query.executionCount} runs
                                            </span>
                                        </div>
                                    </div>

                                    {/* Actions menu */}
                                    <div className="relative">
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                setActiveMenu(activeMenu === query.id ? null : query.id);
                                            }}
                                            className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-700 opacity-0 group-hover:opacity-100"
                                        >
                                            <MoreVertical className="h-4 w-4" />
                                        </button>

                                        {activeMenu === query.id && (
                                            <div className={cn(
                                                'absolute right-0 top-full mt-1 w-36 rounded-lg shadow-lg z-10',
                                                'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700'
                                            )}>
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        duplicateQuery(query);
                                                    }}
                                                    className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                                                >
                                                    <Copy className="h-4 w-4" />
                                                    Duplicate
                                                </button>
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        // TODO: Implement edit
                                                        setActiveMenu(null);
                                                    }}
                                                    className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                                                >
                                                    <Edit2 className="h-4 w-4" />
                                                    Rename
                                                </button>
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        deleteQuery(query.id);
                                                    }}
                                                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/30"
                                                >
                                                    <Trash2 className="h-4 w-4" />
                                                    Delete
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}

export default SavedQueries;
