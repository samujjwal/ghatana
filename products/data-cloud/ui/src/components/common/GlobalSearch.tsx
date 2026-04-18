/**
 * Global Search Component
 * 
 * Command palette style global search for navigating and finding resources.
 * Supports keyboard shortcuts (Cmd/Ctrl + K) to open.
 * 
 * @doc.type component
 * @doc.purpose Global search and navigation
 * @doc.layer frontend
 * @doc.pattern Modal Component
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import {
    Search,
    Database,
    Workflow,
    FileText,
    Settings,
    BarChart3,
    Shield,
    ArrowRight,
    Loader2,
} from 'lucide-react';
import { cn, textStyles, bgStyles } from '../../lib/theme';
import { collectionsApi } from '../../lib/api/collections';
import { workflowsApi } from '../../lib/api/workflows';
import SessionBootstrap, { type ShellRole, canAccessShellRole } from '../../lib/auth/session';

/**
 * Search result item
 */
interface SearchResult {
    id: string;
    title: string;
    description?: string;
    type: 'collection' | 'workflow' | 'page' | 'action';
    icon: React.ReactNode;
    path?: string;
    action?: () => void;
    minimumShellRole?: ShellRole;
}

/**
 * Quick navigation items (static)
 */
const quickNavItems: SearchResult[] = [
    { id: 'nav-home', title: 'Home', type: 'page', icon: <BarChart3 className="h-4 w-4" />, path: '/' },
    { id: 'nav-data', title: 'Data', type: 'page', icon: <Database className="h-4 w-4" />, path: '/data' },
    { id: 'nav-pipelines', title: 'Pipelines', type: 'page', icon: <Workflow className="h-4 w-4" />, path: '/pipelines' },
    { id: 'nav-query', title: 'Query', type: 'page', icon: <FileText className="h-4 w-4" />, path: '/query' },
    { id: 'nav-lineage', title: 'Lineage Preview', description: 'Open the Data Explorer lineage preview', type: 'page', icon: <Workflow className="h-4 w-4" />, path: '/data?view=lineage' },
    { id: 'nav-insights', title: 'Insights', type: 'page', icon: <BarChart3 className="h-4 w-4" />, path: '/insights', minimumShellRole: 'operator' },
    { id: 'nav-trust', title: 'Trust', type: 'page', icon: <Shield className="h-4 w-4" />, path: '/trust', minimumShellRole: 'operator' },
    { id: 'nav-events', title: 'Events', type: 'page', icon: <Workflow className="h-4 w-4" />, path: '/events', minimumShellRole: 'operator' },
    { id: 'nav-settings', title: 'Settings', type: 'page', icon: <Settings className="h-4 w-4" />, path: '/settings', minimumShellRole: 'admin' },
];

function getVisibleQuickNavItems(shellRole: ShellRole): SearchResult[] {
    return quickNavItems.filter((item) =>
        canAccessShellRole(shellRole, item.minimumShellRole ?? 'primary-user')
    );
}

interface GlobalSearchProps {
    isOpen: boolean;
    onClose: () => void;
}

/**
 * Global Search Component
 */
export function GlobalSearch({ isOpen, onClose }: GlobalSearchProps): React.ReactElement | null {
    const [query, setQuery] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);
    const inputRef = useRef<HTMLInputElement>(null);
    const navigate = useNavigate();
    const shellRole = SessionBootstrap.getShellRole();

    const searchEnabled = query.length >= 2;

    const { data: collectionsPage, isFetching: collectionsLoading } = useQuery({
        queryKey: ['global-search-collections', query],
        queryFn: () => collectionsApi.list({ search: query, pageSize: 5 }),
        enabled: searchEnabled,
        staleTime: 30_000,
    });

    const { data: workflowsPage, isFetching: workflowsLoading } = useQuery({
        queryKey: ['global-search-workflows', query],
        queryFn: () => workflowsApi.list({ search: query, pageSize: 5 }),
        enabled: searchEnabled,
        staleTime: 30_000,
    });

    const isLoading = collectionsLoading || workflowsLoading;
    const visibleQuickNavItems = getVisibleQuickNavItems(shellRole);

    // Build results from API data + static quick nav items
    const getResults = useCallback((): SearchResult[] => {
        if (!query.trim()) {
            return visibleQuickNavItems;
        }

        const lowerQuery = query.toLowerCase();
        const matchedNav = visibleQuickNavItems.filter(
            (item) =>
                item.title.toLowerCase().includes(lowerQuery) ||
                item.description?.toLowerCase().includes(lowerQuery)
        );

        const collectionResults: SearchResult[] = (collectionsPage?.items ?? []).map((col) => ({
            id: col.id,
            title: col.name,
            description: col.description,
            type: 'collection' as const,
            icon: <Database className="h-4 w-4" />,
            path: `/data/${col.id}`,
        }));

        const workflowResults: SearchResult[] = (workflowsPage?.items ?? []).map((wf) => ({
            id: wf.id,
            title: wf.name,
            description: wf.description,
            type: 'workflow' as const,
            icon: <Workflow className="h-4 w-4" />,
            path: `/pipelines/${wf.id}`,
        }));

        return [...matchedNav, ...collectionResults, ...workflowResults];
    }, [query, collectionsPage, visibleQuickNavItems, workflowsPage]);

    const results = getResults();

    // Focus input when opened
    useEffect(() => {
        if (isOpen) {
            inputRef.current?.focus();
            setQuery('');
            setSelectedIndex(0);
        }
    }, [isOpen]);

    // Handle keyboard navigation
    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent) => {
            switch (e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    setSelectedIndex((i) => Math.min(i + 1, results.length - 1));
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    setSelectedIndex((i) => Math.max(i - 1, 0));
                    break;
                case 'Enter':
                    e.preventDefault();
                    const selected = results[selectedIndex];
                    if (selected) {
                        if (selected.path) {
                            navigate(selected.path);
                        } else if (selected.action) {
                            selected.action();
                        }
                        onClose();
                    }
                    break;
                case 'Escape':
                    onClose();
                    break;
            }
        },
        [results, selectedIndex, navigate, onClose]
    );

    // Handle result click
    const handleResultClick = (result: SearchResult) => {
        if (result.path) {
            navigate(result.path);
        } else if (result.action) {
            result.action();
        }
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 overflow-y-auto">
            {/* Backdrop */}
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="relative min-h-screen flex items-start justify-center pt-[15vh] px-4">
                <div
                    className={cn(
                        'w-full max-w-xl rounded-xl shadow-2xl overflow-hidden',
                        bgStyles.surface
                    )}
                >
                    {/* Search Input */}
                    <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                        <Search className="h-5 w-5 text-gray-400" />
                        <input
                            ref={inputRef}
                            type="text"
                            value={query}
                            onChange={(e) => {
                                setQuery(e.target.value);
                                setSelectedIndex(0);
                            }}
                            onKeyDown={handleKeyDown}
                            placeholder="Search data, pipelines, and pages..."
                            className={cn(
                                'flex-1 bg-transparent border-none outline-none',
                                'text-gray-900 dark:text-white placeholder-gray-400'
                            )}
                        />
                        <kbd className="hidden sm:inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-gray-400 bg-gray-100 dark:bg-gray-700 rounded">
                            ESC
                        </kbd>
                    </div>

                    {/* Results */}
                    <div className="max-h-[400px] overflow-y-auto py-2">
                        {isLoading && searchEnabled ? (
                            <div className="px-4 py-6 flex items-center justify-center gap-2 text-gray-400">
                                <Loader2 className="h-4 w-4 animate-spin" />
                                <span className="text-sm">Searching…</span>
                            </div>
                        ) : results.length === 0 ? (
                            <div className="px-4 py-8 text-center">
                                <p className={textStyles.muted}>No results found for "{query}"</p>
                            </div>
                        ) : (
                            <>
                                {!query && (
                                    <p className={cn(textStyles.xs, 'px-4 py-2')}>Quick Navigation</p>
                                )}
                                {results.map((result, index) => (
                                    <button
                                        key={result.id}
                                        onClick={() => handleResultClick(result)}
                                        onMouseEnter={() => setSelectedIndex(index)}
                                        className={cn(
                                            'w-full flex items-center gap-3 px-4 py-2 text-left',
                                            index === selectedIndex
                                                ? 'bg-blue-50 dark:bg-blue-900/30'
                                                : 'hover:bg-gray-50 dark:hover:bg-gray-700'
                                        )}
                                    >
                                        <div className={cn(
                                            'p-2 rounded-lg',
                                            result.type === 'collection' && 'bg-green-100 dark:bg-green-900 text-green-600 dark:text-green-400',
                                            result.type === 'workflow' && 'bg-purple-100 dark:bg-purple-900 text-purple-600 dark:text-purple-400',
                                            result.type === 'page' && 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400',
                                            result.type === 'action' && 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-400'
                                        )}>
                                            {result.icon}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <p className={textStyles.h4}>{result.title}</p>
                                            {result.description && (
                                                <p className={cn(textStyles.xs, 'truncate')}>{result.description}</p>
                                            )}
                                        </div>
                                        {index === selectedIndex && (
                                            <ArrowRight className="h-4 w-4 text-gray-400" />
                                        )}
                                    </button>
                                ))}
                            </>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="px-4 py-2 border-t border-gray-200 dark:border-gray-700 flex items-center justify-between">
                        <div className="flex items-center gap-4 text-xs text-gray-400">
                            <span className="flex items-center gap-1">
                                <kbd className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded">↑</kbd>
                                <kbd className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded">↓</kbd>
                                to navigate
                            </span>
                            <span className="flex items-center gap-1">
                                <kbd className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded">↵</kbd>
                                to select
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 * Hook to manage global search state
 */
export function useGlobalSearch() {
    const [isOpen, setIsOpen] = useState(false);

    const open = useCallback(() => setIsOpen(true), []);
    const close = useCallback(() => setIsOpen(false), []);
    const toggle = useCallback(() => setIsOpen((prev) => !prev), []);

    // Keyboard shortcut (Cmd/Ctrl + K)
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                toggle();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [toggle]);

    return { isOpen, open, close, toggle };
}

export default GlobalSearch;
